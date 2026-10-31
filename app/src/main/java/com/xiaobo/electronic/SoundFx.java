package com.xiaobo.electronic;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;

import com.hjq.toast.Toaster;
import com.tencent.mmkv.MMKV;
import com.xiaobo.fireworks.GlobalFirework;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 音效与触感引擎。
 *
 * <p>烟花库自带一个爆炸音效（{@code R.raw.firework}）和每次绽放的 15ms 震动，
 * 但 App 层一直没有任何开关：想安静看烟花的人没法关，想更沉浸的人也没法调。
 * 这里把开关、音量、层次补齐：
 *
 * <ul>
 *   <li><b>升空"咻"</b>与<b>庆祝"叮"</b>：库里没有，这里用 AudioTrack 实时合成。
 *       不引入音频素材，一是省 APK 体积，二是音色可以按音量参数线性调整。</li>
 *   <li><b>震动节奏</b>：爆炸是"轻—重"两段波形，比单次 15ms 单点更有打击感。</li>
 *   <li><b>闪避</b>：台词朗读时整体压低（与 BGM 闪避同一套逻辑），人声不被盖住。</li>
 * </ul>
 *
 * <p>合成全部在子线程，主线程只做一次开关判断，不会拖慢渲染帧。
 */
public final class SoundFx {

    private static final int SR = 44100;
    private static final String KEY_ENABLED = "sfx_enabled";
    private static final String KEY_VIBRATE = "sfx_vibrate";
    private static final String KEY_VOLUME = "sfx_volume";

    private static Context app;
    private static final ExecutorService pool = Executors.newSingleThreadExecutor();
    private static final AtomicBoolean ducked = new AtomicBoolean(false);

    private static boolean enabled = true;
    private static boolean vibrateEnabled = true;
    private static float volume = 0.6f;

    // 合成的 PCM 缓存：首次播放时生成，之后复用（生成一次约 2~3ms）
    private static volatile short[] pcmLaunch;
    private static volatile short[] pcmBoom;
    private static volatile short[] pcmChime;

    private SoundFx() {
    }

    /** 在 {@code GlobalFirework.init} 之后调用：读持久化开关并同步给库。 */
    public static void init(Context ctx) {
        app = ctx.getApplicationContext();
        MMKV kv = MMKV.defaultMMKV();
        enabled = kv.decodeBool(KEY_ENABLED, true);
        vibrateEnabled = kv.decodeBool(KEY_VIBRATE, true);
        volume = kv.decodeFloat(KEY_VOLUME, 0.6f);
        applyToLibrary();
        // 预热：第一次放烟花时才合成会晚半拍，提前在后台算好
        pool.execute(() -> {
            synthLaunch();
            synthBoom();
        });
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static boolean isVibrateEnabled() {
        return vibrateEnabled;
    }

    public static float volume() {
        return volume;
    }

    public static void setEnabled(boolean on) {
        enabled = on;
        MMKV.defaultMMKV().encode(KEY_ENABLED, on);
        applyToLibrary();
    }

    public static void setVibrateEnabled(boolean on) {
        vibrateEnabled = on;
        MMKV.defaultMMKV().encode(KEY_VIBRATE, on);
        applyToLibrary();
    }

    public static void setVolume(float v) {
        volume = Math.min(1f, Math.max(0f, v));
        MMKV.defaultMMKV().encode(KEY_VOLUME, volume);
        applyToLibrary();
    }

    /** 台词朗读时压低音效：与 BGM 闪避同一时机调用。 */
    public static void setDucked(boolean on) {
        ducked.set(on);
        applyToLibrary();
    }

    private static void applyToLibrary() {
        try {
            GlobalFirework g = GlobalFirework.getInstance();
            g.setSoundEnabled(enabled && !ducked.get());
            g.setVibrationEnabled(vibrateEnabled);
            // 闪避时降到 35%，不静音——完全静音会让"放烟花"这件事失去反馈
            g.setSoundVolume(volume * (ducked.get() ? 0.35f : 1f));
        } catch (Throwable ignored) {
            // 库未初始化（壁纸进程等）时忽略，音效开关只是附加能力
        }
    }

    // ==================== 播放入口 ====================

    /** 火箭升空的"咻"：在 bloom 之前调用，声画同步。 */
    public static void playLaunch() {
        play(pcmLaunch != null ? pcmLaunch : synthLaunch());
    }

    /** 自研爆炸声：库自带音效只在库内部触发，蓄力爆发等 App 侧动作需要自己补。 */
    public static void playBoom() {
        play(pcmBoom != null ? pcmBoom : synthBoom());
    }

    /** 庆祝/应答时刻的三音琶音。 */
    public static void playChime() {
        play(pcmChime != null ? pcmChime : synthChime());
    }

    /** 升空 + 爆炸（火箭模式的完整声画）。 */
    public static void playRocket() {
        playLaunch();
        if (vibrateEnabled) vibrate(new long[]{0, 22}, -1);
    }

    private static void play(short[] pcm) {
        if (!enabled || pcm == null || app == null) return;
        final float vol = volume * (ducked.get() ? 0.35f : 1f);
        if (vol <= 0f) return;
        pool.execute(() -> playInternal(pcm, vol));
    }

    private static void playInternal(short[] pcm, float vol) {
        AudioTrack track = null;
        try {
            int minBuf = AudioTrack.getMinBufferSize(SR,
                    AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);
            AudioAttributes aa = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build();
            AudioFormat af = new AudioFormat.Builder()
                    .setSampleRate(SR)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build();
            track = new AudioTrack(aa, af, Math.max(minBuf, pcm.length * 2),
                    AudioTrack.MODE_STATIC, AudioManager.AUDIO_SESSION_ID_GENERATE);
            track.setVolume(vol);
            if (track.write(pcm, 0, pcm.length) < 0) return;
            track.play();
            Thread.sleep((long) (pcm.length * 1000L / SR) + 80L);
        } catch (Throwable ignored) {
            // 音频设备被占用（通话中/TTS 独占）时静默失败，绝不打断演出
        } finally {
            if (track != null) {
                try {
                    track.stop();
                    track.release();
                } catch (Throwable ignored) {
                }
            }
        }
    }

    // ==================== 触感 ====================

    /** 爆炸震动：轻—重两段，比单次短震更像真实的"嘭"。 */
    public static void vibrateBurst() {
        vibrate(new long[]{0, 18, 26, 46}, -1);
    }

    /** 蓄力大爆炸：更长的渐强。 */
    public static void vibrateHeavy() {
        vibrate(new long[]{0, 40, 30, 90}, -1);
    }

    public static void vibrateTick() {
        vibrate(new long[]{0, 12}, -1);
    }

    private static void vibrate(long[] pattern, int repeat) {
        if (!vibrateEnabled || app == null) return;
        try {
            Vibrator v = vibrator();
            if (v == null || !v.hasVibrator()) return;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                v.vibrate(VibrationEffect.createWaveform(pattern, repeat));
            } else {
                v.vibrate(pattern, repeat);
            }
        } catch (Throwable ignored) {
        }
    }

    private static Vibrator vibrator() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            VibratorManager vm = (VibratorManager) app.getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
            return vm == null ? null : vm.getDefaultVibrator();
        }
        return (Vibrator) app.getSystemService(Context.VIBRATOR_SERVICE);
    }

    // ==================== 合成 ====================

    /** 升空"咻"：频率指数上扫 + 起落包络，尾部一点气声噪声。 */
    private static short[] synthLaunch() {
        float dur = 0.42f;
        int n = (int) (SR * dur);
        short[] out = new short[n];
        double phase = 0.0;
        double prevNoise = 0.0;
        for (int i = 0; i < n; i++) {
            float t = i / (float) SR;
            float x = t / dur;
            double f = 240.0 * Math.pow(7.0, x); // 240Hz → 1680Hz
            phase += 2.0 * Math.PI * f / SR;
            // 包络：前 15% 快速起音，之后平滑收尾
            float env = (float) (Math.sin(Math.PI * Math.pow(x, 0.65f)) * 0.62f);
            double s = Math.sin(phase) * env;
            // 高通噪声：差分去掉低频，只留"咻"的气声
            double noise = Math.random() * 2.0 - 1.0;
            double hp = noise - prevNoise;
            prevNoise = noise;
            s += hp * env * 0.28;
            out[i] = clampShort(s);
        }
        pcmLaunch = out;
        return out;
    }

    /** 爆炸"嘭"：低通噪声指数衰减 + 55Hz 低频冲击。 */
    private static short[] synthBoom() {
        float dur = 0.75f;
        int n = (int) (SR * dur);
        short[] out = new short[n];
        double lp = 0.0;
        double phase = 0.0;
        for (int i = 0; i < n; i++) {
            float t = i / (float) SR;
            double noise = Math.random() * 2.0 - 1.0;
            lp += 0.09 * (noise - lp); // 一阶低通，去掉刺耳高频
            double body = lp * Math.exp(-t * 7.5) * 0.85;
            phase += 2.0 * Math.PI * 55.0 / SR;
            double thump = Math.sin(phase) * Math.exp(-t * 9.0) * 0.55;
            double s = (body + thump) * 0.72;
            out[i] = clampShort(s);
        }
        pcmBoom = out;
        return out;
    }

    /** 庆祝"叮咚"：C6-E6-G6 琶音，用于她点"我愿意"与演出收尾。 */
    private static short[] synthChime() {
        float dur = 1.05f;
        int n = (int) (SR * dur);
        short[] out = new short[n];
        double[] freqs = {1046.5, 1318.5, 1568.0};
        for (int k = 0; k < freqs.length; k++) {
            double startT = k * 0.09;
            double phase = 0.0;
            for (int i = (int) (startT * SR); i < n; i++) {
                float t = i / (float) SR - (float) startT;
                phase += 2.0 * Math.PI * freqs[k] / SR;
                double env = Math.exp(-t * 3.4);
                // 基音 + 弱二次谐波，听感更像金属铃而不是电子哔声
                double s = (Math.sin(phase) + 0.22 * Math.sin(phase * 2.0)) * env * 0.3;
                out[i] = clampShort((out[i] / 32768.0) + s);
            }
        }
        pcmChime = out;
        return out;
    }

    private static short clampShort(double v) {
        if (v > 1.0) v = 1.0;
        if (v < -1.0) v = -1.0;
        return (short) (v * 32767.0);
    }

    /** 开关变更后的统一提示文案（菜单里复用，避免各处写法不一致）。 */
    public static String statusText() {
        if (!enabled && !vibrateEnabled) return "已静音";
        return (enabled ? "音效 " + Math.round(volume * 100) + "%" : "音效关")
                + " · " + (vibrateEnabled ? "震动开" : "震动关");
    }

    /** 试听：菜单里点了开关立刻听一声，不用退出菜单去放烟花。 */
    public static void preview() {
        if (!enabled) {
            Toaster.showShort("音效已关闭（开启后可试听）");
            return;
        }
        playRocket();
        mainPost(() -> playBoom());
        if (vibrateEnabled) vibrateBurst();
    }

    private static void mainPost(Runnable r) {
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(r, 420L);
    }
}
