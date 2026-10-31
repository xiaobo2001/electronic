package com.xiaobo.electronic.speech;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;

import com.hjq.toast.Toaster;
import com.tencent.mmkv.MMKV;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 台词朗读统一门面。
 *
 * <p>对外只暴露 {@link #speak(String)} / {@link #stop()} / {@link #shutdown()}，
 * 内部按 MODE 选择音源，并在本地引擎不可用时自动降级到系统 TTS。
 *
 * <p>三档音源：
 * <ul>
 *   <li>{@link #MODE_LOCAL}  本地神经网络（VITS，离线、音质最好，需模型就位）</li>
 *   <li>{@link #MODE_SYSTEM} 系统 TTS 引擎（零依赖，音色取决于用户手机）</li>
 *   <li>{@link #MODE_OFF}    不朗读</li>
 * </ul>
 *
 * <p>朗读期间通过 {@link Ducking} 回调让背景音乐闪避（降低音量），
 * 避免人声被 BGM 盖住——原先两者共用 STREAM_MUSIC 会互相打架。
 */
public class SpeechEngine {

    /**
     * 自动：优先用手机内置的系统 TTS，系统不可用（缺中文语音包 / 引擎初始化失败 /
     * 合成报错）时才启用随包的本地离线音源。绝大多数手机走这条，内存与启动都最省。
     */
    public static final int MODE_AUTO = 0;
    /** 只用系统内置引擎，不可用时宁可不读也不用本地 */
    public static final int MODE_SYSTEM = 1;
    /** 只用随包的本地离线神经网络音源（小雅） */
    public static final int MODE_LOCAL = 2;
    /** 不朗读 */
    public static final int MODE_OFF = 3;

    private static final String KEY_SPEECH_MODE = "speech_mode2";
    /**
     * 上次启动时检测到的"系统引擎能否读中文"：-1 未知 / 0 不能 / 1 能。
     * 存下来是为了不必每次冷启动都弹一次下载询问——用户已经说过"暂时不用"的话，
     * 再问就是骚扰。系统装了语音包后下一次检测会把它刷回 1。
     */
    private static final String KEY_SYS_ZH = "tts_sys_zh";
    /** 用户在"要不要下载离线音色"弹窗里选过"暂时不用" */
    private static final String KEY_TTS_PROMPT_OFF = "tts_prompt_off";
    /** 告白台词偏慢更打动人；系统引擎默认 1.0 偏快 */
    private static final float SPEECH_RATE = 0.85f;
    /** 略提音调，听起来更柔和（1.0 为原音高） */
    private static final float SPEECH_PITCH = 1.04f;
    /**
     * 自动档下系统引擎尚未初始化完成时，最多给它这么久的机会。
     * 演出有 3 秒倒计时且 warmUp 会提前初始化，正常情况根本等不到。
     */
    private static final long SYS_INIT_GRACE_MS = 800;

    /** BGM 闪避：朗读时把音乐压低，读完恢复 */
    public interface Ducking {
        void onDuck(boolean active);
    }

    private final Context appContext;
    private final android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private Ducking ducking;

    private LocalVitsTts localTts;
    private boolean localFailed;

    private TextToSpeech sysTts;
    private boolean sysReady;
    /** 系统引擎已判定不可用（初始化失败 / 缺中文语音包 / 引擎缺失） */
    private boolean sysFailed;
    /** 系统引擎初始化是否已有结论（无论成功失败） */
    private boolean sysChecked;
    private boolean sysInitCalled;

    /** 自动档下等待系统初始化的排队任务，stop() 时要撤掉，避免迟到的声音 */
    private Runnable pendingAutoProbe;
    /** 最近一次朗读的文本：系统朗读失败时用它切本地重读这一句 */
    private String lastText;

    /** 中文语音包缺失时只提示一次，不反复骚扰 */
    private boolean warnedNoChinese;

    public SpeechEngine(Context context) {
        this.appContext = context.getApplicationContext();
    }

    public void setDucking(Ducking d) {
        this.ducking = d;
    }

    public static int getMode() {
        try {
            int m = MMKV.defaultMMKV().decodeInt(KEY_SPEECH_MODE, MODE_AUTO);
            return (m < MODE_AUTO || m > MODE_OFF) ? MODE_AUTO : m;
        } catch (Throwable t) {
            return MODE_AUTO;
        }
    }

    public static void setMode(int mode) {
        try {
            MMKV.defaultMMKV().encode(KEY_SPEECH_MODE, mode);
        } catch (Throwable ignored) {
        }
    }

    // ===== 系统引擎中文能力检测：结论存下来，启动只问一次 =====

    /** -1 未知（还没检测过）/ 0 读不了中文 / 1 能读中文 */
    public static int cachedSystemChinese() {
        try {
            return MMKV.defaultMMKV().decodeInt(KEY_SYS_ZH, -1);
        } catch (Throwable t) {
            return -1;
        }
    }

    public static void cacheSystemChinese(boolean ok) {
        try {
            MMKV.defaultMMKV().encode(KEY_SYS_ZH, ok ? 1 : 0);
            if (ok) MMKV.defaultMMKV().encode(KEY_TTS_PROMPT_OFF, false);
        } catch (Throwable ignored) {
        }
    }

    /** 用户选过"暂时不用"：之后启动不再自动问，但菜单里仍可主动下载 */
    public static boolean ttsPromptDisabled() {
        try {
            return MMKV.defaultMMKV().decodeBool(KEY_TTS_PROMPT_OFF, false);
        } catch (Throwable t) {
            return false;
        }
    }

    public static void setTtsPromptDisabled(boolean off) {
        try {
            MMKV.defaultMMKV().encode(KEY_TTS_PROMPT_OFF, off);
        } catch (Throwable ignored) {
        }
    }

    /** 离线音色模型是否已下载（当前选中的音色） */
    public boolean isLocalModelReady() {
        return TtsModelStore.isReady(appContext, TtsVoice.current());
    }

    /** 当前音色 */
    public TtsVoice currentVoice() {
        return TtsVoice.current();
    }

    /** 换音色：必须重建引擎，否则还在用旧模型的会话 */
    public void setVoice(TtsVoice v) {
        if (v == null || v == TtsVoice.current()) return;
        stop();
        TtsVoice.setCurrent(v);
        resetLocal();
    }

    /**
     * 模型实际支持的说话人数（多说话人包才有意义）。
     * 引擎还没加载完时返回 0，UI 上据此先不显示"换声音"入口。
     */
    public int numSpeakers() {
        return localTts != null ? localTts.numSpeakers() : 0;
    }

    /** 模型下载/删除/换音色后重建本地引擎：否则 failed 标记会一直把这路堵死 */
    public void resetLocal() {
        if (localTts != null) {
            try {
                localTts.release();
            } catch (Throwable ignored) {
            }
            localTts = null;
        }
        localFailed = false;
    }

    /**
     * 异步检测系统引擎能否朗读中文，结论同时写入缓存。
     *
     * <p>TextToSpeech 的初始化是异步的，这里轮询它的状态位而不是干等——
     * 引擎缺失时回调可能根本不来，那就按"不支持"处理（4 秒封顶）。
     */
    public void probeSystemChinese(java.util.function.Consumer<Boolean> cb) {
        if (sysReady) {
            cacheSystemChinese(true);
            cb.accept(true);
            return;
        }
        if (sysFailed) {
            cacheSystemChinese(false);
            cb.accept(false);
            return;
        }
        ensureSystem();
        final long start = android.os.SystemClock.elapsedRealtime();
        mainHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (sysReady) {
                    cacheSystemChinese(true);
                    cb.accept(true);
                    return;
                }
                if (sysFailed) {
                    cacheSystemChinese(false);
                    cb.accept(false);
                    return;
                }
                if (android.os.SystemClock.elapsedRealtime() - start > 4000L) {
                    // 迟迟不给结论：视作不可用，宁可让用户多下 20MB，也不能演出时一声不出
                    sysFailed = true;
                    sysChecked = true;
                    cacheSystemChinese(false);
                    cb.accept(false);
                    return;
                }
                mainHandler.postDelayed(this, 120L);
            }
        }, 120L);
    }

    /** 供菜单显示当前生效音源 */
    public String currentSourceName() {
        switch (getMode()) {
            case MODE_OFF:
                return "已关闭";
            case MODE_LOCAL:
                if (localFailed) {
                    return localTts != null && localTts.isModelMissing() ? "本地音色未下载" : "本地不可用";
                }
                return localTts != null && localTts.isReady() ? localLabel() : localLabel() + "（准备中）";
            case MODE_SYSTEM:
                return sysReady ? "系统引擎" : (sysFailed ? "系统引擎不可用" : "系统引擎（准备中）");
            default:
                // 自动档：显示真正会发声的那个
                if (sysReady) return "系统引擎";
                if (sysFailed) {
                    if (!isLocalModelReady()) return "需下载离线音色";
                    return localTts != null && localTts.isReady() ? localLabel() + "（系统不可用）"
                            : localLabel() + "（准备中）";
                }
                return "自动（检测中）";
        }
    }

    /** "本地小雅·低沉男声"这样的组合标签；变声档为原声时省掉后半截 */
    private static String localLabel() {
        TtsVoice v = TtsVoice.current();
        String s = "本地" + v.name;
        if (TtsVoice.currentPitch() != 0) s += "·" + TtsVoice.pitchName();
        return s;
    }

    public void warmUp() {
        int mode = getMode();
        if (mode == MODE_OFF) return;
        if (mode == MODE_LOCAL) {
            ensureLocalStarted();
        } else {
            // 自动档 / 系统档：只预热系统引擎。
            // 自动档下本地模型（18MB）保持懒加载——系统能读就不占这份内存与加载耗时，
            // 只有确认系统不可用时才真正建会话。
            ensureSystem();
        }
    }

    /**
     * 朗读一句台词。
     *
     * <p>自动档（默认）顺序：<b>系统内置 → 本地离线</b>。系统引擎不可用或朗读报错时，
     * 当句切本地重读；本地也失败则静默——演出不能因为朗读挂掉。
     */
    public void speak(String text) {
        if (text == null || text.trim().isEmpty()) return;
        lastText = text;
        int mode = getMode();
        if (mode == MODE_OFF) return;

        if (mode == MODE_AUTO) {
            speakAuto(text);
            return;
        }
        if (mode == MODE_SYSTEM) {
            speakSystem(text);
            return;
        }
        speakLocal(text);
    }

    /** 自动档：能用系统就用系统，否则本地兜底 */
    private void speakAuto(String text) {
        if (sysReady && sysTts != null) {
            if (speakSystem(text)) return;
            speakLocal(text);
            return;
        }
        if (sysFailed) {
            speakLocal(text);
            return;
        }
        // 系统引擎还在初始化（首次进入演出常见）：给它一个很短的机会窗口，
        // 到点仍无结论就走本地，绝不干等。
        ensureSystem();
        cancelPendingProbe();
        pendingAutoProbe = () -> {
            pendingAutoProbe = null;
            if (sysReady) {
                if (!speakSystem(text)) speakLocal(text);
            } else {
                sysFailed = true;
                speakLocal(text);
            }
        };
        mainHandler.postDelayed(pendingAutoProbe, SYS_INIT_GRACE_MS);
    }

    private void cancelPendingProbe() {
        if (pendingAutoProbe != null) {
            mainHandler.removeCallbacks(pendingAutoProbe);
            pendingAutoProbe = null;
        }
    }

    /** 用系统引擎朗读；返回 false 表示没读成（未就绪 / 缺语音包 / 引擎报错） */
    private boolean speakSystem(String text) {
        if (!ensureSystem()) return false;
        try {
            Bundle params = new Bundle();
            params.putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, android.media.AudioManager.STREAM_MUSIC);
            int r = sysTts.speak(text, TextToSpeech.QUEUE_FLUSH, params, "romantic_line");
            if (r == TextToSpeech.SUCCESS) {
                duck(true);
                return true;
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private void speakLocal(String text) {
        // 本地引擎只要还没"彻底失败"就交给它：初始化未完成时会排队，
        // 等 ONNX 会话建好再合成，而不是一上来就放弃。
        if (!ensureLocalStarted()) {
            duck(false);
            return;
        }
        if (localTts.speak(text, new LocalVitsTts.Callback() {
            @Override
            public void onStart() {
                duck(true);
            }

            @Override
            public void onDone() {
                duck(false);
            }

            @Override
            public void onError() {
                duck(false);
                // 本地也失败：自动档下再试一次系统引擎（可能只是这一句合成异常），
                // 仍不行就静默，演出照常往下走。
                if (getMode() == MODE_AUTO && !sysFailed && speakSystem(text)) {
                    return;
                }
                if (!warnedNoChinese) {
                    warnedNoChinese = true;
                    // 区分"没下载"和"坏了"：前者是可以一键补救的，得说清去哪儿补
                    boolean missing = localTts != null && localTts.isModelMissing();
                    Toaster.showShort(missing
                            ? "未下载离线音色，可在「台词朗读」里下载"
                            : "台词朗读不可用，演出继续");
                }
            }
        })) {
            return;
        }
        if (getMode() == MODE_AUTO && speakSystem(text)) return;
        duck(false);
    }

    /** 本地引擎只要没彻底失败就可用：初始化是异步的，未完成由它自己排队等待 */
    private boolean ensureLocalStarted() {
        if (localFailed) return false;
        if (localTts == null) {
            try {
                localTts = new LocalVitsTts(appContext);
            } catch (Throwable t) {
                localFailed = true;
                return false;
            }
        }
        localTts.prepare();
        return true;
    }

    private boolean ensureSystem() {
        if (sysReady && sysTts != null) return true;
        if (sysFailed) return false;
        if (sysInitCalled) return false; // 初始化中：结论未出，不重复创建实例
        sysInitCalled = true;
        try {
            sysTts = new TextToSpeech(appContext, status -> {
                if (status != TextToSpeech.SUCCESS) {
                    sysChecked = true;
                    sysFailed = true;
                    return;
                }
                // 中文语音包缺失时 setLanguage 会返回 LANG_MISSING_DATA / LANG_NOT_SUPPORTED，
                // 原代码忽略返回值，结果一句都读不出来还不报错。
                int r = sysTts.setLanguage(Locale.SIMPLIFIED_CHINESE);
                if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) {
                    sysChecked = true;
                    sysFailed = true;
                    if (!warnedNoChinese) {
                        warnedNoChinese = true;
                        Toaster.showShort("系统缺少中文语音包，已改用本地离线音色");
                    }
                    return;
                }
                sysTts.setSpeechRate(SPEECH_RATE);
                sysTts.setPitch(SPEECH_PITCH);
                sysTts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                    @Override
                    public void onStart(String utteranceId) {
                        duck(true);
                    }

                    @Override
                    public void onDone(String utteranceId) {
                        duck(false);
                    }

                    @Override
                    @SuppressWarnings("deprecation")
                    public void onError(String utteranceId) {
                        duck(false);
                        // 系统引擎朗读中途报错（引擎被杀、语音包被清）：自动档下切本地补读这一句
                        if (getMode() == MODE_AUTO && lastText != null) {
                            sysFailed = true;
                            speakLocal(lastText);
                        }
                    }
                });
                sysChecked = true;
                sysReady = true;
            });
        } catch (Throwable t) {
            sysChecked = true;
            sysFailed = true;
            return false;
        }
        return sysReady;
    }

    private void duck(boolean active) {
        if (ducking == null) return;
        // 本地合成的回调来自后台线程，而调用方的闪避实现用属性动画（必须在主线程），
        // 这里统一切回主线程，避免 Animators may only be run on Looper threads 崩溃。
        mainHandler.post(() -> {
            try {
                ducking.onDuck(active);
            } catch (Throwable ignored) {
            }
        });
    }

    public void stop() {
        cancelPendingProbe();
        lastText = null;
        if (localTts != null) {
            try {
                localTts.stop();
            } catch (Throwable ignored) {
            }
        }
        if (sysTts != null && sysReady) {
            try {
                sysTts.stop();
            } catch (Throwable ignored) {
            }
        }
        duck(false);
    }

    public void shutdown() {
        stop();
        if (localTts != null) {
            try {
                localTts.release();
            } catch (Throwable ignored) {
            }
            localTts = null;
        }
        if (sysTts != null) {
            try {
                sysTts.shutdown();
            } catch (Throwable ignored) {
            }
            sysTts = null;
        }
        sysReady = false;
        sysChecked = false;
        sysInitCalled = false;
    }

    /** 自动档下系统引擎是否已被判定不可用（供 UI 提示用） */
    public boolean isSystemUnavailable() {
        return sysFailed;
    }

    /** 列出已安装的 TTS 引擎（供用户选择；Android 11+ 需在 Manifest 声明 queries） */
    public List<EngineInfo> systemEngines() {
        List<EngineInfo> out = new ArrayList<>();
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                for (TextToSpeech.EngineInfo info : getEngines()) {
                    out.add(new EngineInfo(info.name, info.label));
                }
            }
        } catch (Throwable ignored) {
        }
        if (out.isEmpty()) {
            out.add(new EngineInfo("com.google.android.tts", "系统默认引擎"));
        }
        return out;
    }

    private List<TextToSpeech.EngineInfo> getEngines() {
        // 需要一个 TextToSpeech 实例才能查询；用轻量临时实例，查完即弃
        final List<TextToSpeech.EngineInfo> result = new ArrayList<>();
        try {
            TextToSpeech probe = new TextToSpeech(appContext, status -> {
            });
            result.addAll(probe.getEngines());
            probe.shutdown();
        } catch (Throwable ignored) {
        }
        return result;
    }

    public static void openSystemTtsSettings(Context c) {
        try {
            c.startActivity(new Intent("com.android.settings.TTS_SETTINGS"));
        } catch (Exception e) {
            try {
                c.startActivity(new Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS));
            } catch (Exception ignored) {
            }
        }
    }

    public static class EngineInfo {
        public final String name;
        public final String label;

        EngineInfo(String name, String label) {
            this.name = name;
            this.label = label;
        }
    }
}
