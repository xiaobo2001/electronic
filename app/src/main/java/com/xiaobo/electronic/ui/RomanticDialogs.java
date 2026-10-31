package com.xiaobo.electronic.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.DatePicker;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.TimePicker;

import androidx.appcompat.app.AlertDialog;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.slider.Slider;
import com.google.android.material.textfield.TextInputEditText;
import com.hjq.toast.Toaster;
import com.tencent.mmkv.MMKV;
import com.xiaobo.fireworks.SkyView;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import com.xiaobo.electronic.data.AnniversaryReminder;
import com.xiaobo.electronic.App;
import com.xiaobo.electronic.data.FireworkTuning;
import com.xiaobo.electronic.util.FontKit;
import com.xiaobo.electronic.util.QrCodeUtil;
import com.xiaobo.electronic.R;
import com.xiaobo.electronic.show.RomanticPresets;
import com.xiaobo.electronic.data.RomanticShareCodec;
import com.xiaobo.electronic.show.ScheduledShow;
import com.xiaobo.electronic.data.ScriptStore;
import com.xiaobo.electronic.show.ShowTemplate;
import com.xiaobo.electronic.view.SignaturePadView;
import com.xiaobo.electronic.util.SliderSafe;
import com.xiaobo.electronic.media.SoundFx;
import com.xiaobo.electronic.speech.SpeechEngine;
import com.xiaobo.electronic.speech.TtsModelStore;
import com.xiaobo.electronic.speech.TtsVoice;

/**
 * 弹窗层：把 MainActivity 里那堆"纯 UI 选择"对话框搬出来。
 *
 * <p>这些对话框的共同点是——只负责让用户选一个值，选完交给外部应用。
 * 因此这里不持有任何业务状态，当前值通过 {@link Callbacks} 问、选中结果通过它回传，
 * 这样这部分 UI 才能脱离那个三千行的 Activity 独立存在。
 */
public class RomanticDialogs {

    public interface Callbacks {
        void onWeatherPicked(int mode);

        void onThemePicked(int theme);

        void onPatternPicked(int pattern);

        /** 朗读音源切换（含预热与试听） */
        void onSpeechModePicked(int mode);

        /** 离线音色模型：download=true 下载，false 清理（释放约 20MB） */
        void onTtsModelManage(boolean download);

        /** 下载指定音色包 */
        void onTtsVoiceDownload(TtsVoice v);

        /** 选用某个音色（已下载的）并试听 */
        void onTtsVoicePicked(TtsVoice v);

        /** 变声档（下标对应 TtsVoice.PITCH_NAMES） */
        void onTtsPitchPicked(int idx);

        /** 多说话人音色：随机换一个声音 */
        void onTtsSpeakerShuffle();

        /** 背景色（含写入 MMKV 与应用到根布局） */
        void onBgColorPicked(int color);

        /** 自定义文字烟花确认（含持久化、重建烟花池与预览发射） */
        void onCustomTextConfirmed(String text);

        /** 【新增】演出模板：playNow=true 表示套用后立刻开演 */
        void onTemplatePicked(int index, boolean playNow);

        String currentCustomText();

        int currentWeather();

        int currentTheme();

        int currentPattern();

        /** 音效/震动开关与音量变化（应用后如需额外同步由实现方决定） */
        default void onSoundFxChanged() {
        }

        /** 手写签名确认：bmp 为笔迹形状位图，save=true 表示同时存为常用签名 */
        default void onSignatureReady(android.graphics.Bitmap bmp, boolean save) {
        }

        /** 当前台词（\n 分句），剧本库保存与二维码生成用 */
        default String currentLines() {
            return "";
        }

        /** 剧本库：直接拿这套台词开演 */
        default void onScriptPlay(String lines) {
        }

        /** 定时演出：到点时间 + 名称 + 台词 */
        default void onScheduleAdded(long timeMillis, String name, String lines) {
        }

        /** 定时演出：删除一条 */
        default void onScheduleRemoved(long id) {
        }

        /** 文字烟花字体切换（放一朵作为试放预览） */
        default void onFontPicked(int index) {
        }

        /**
         * 烟花手感调参变化（重建烟花池后生效）。
         *
         * @param preview true 表示要立刻放一朵给用户看
         * @return 这一朵大概持续多少毫秒（preview=false 时返回 0）；
         *         弹窗靠它决定"让位多久再还原"，避免固定延时导致早收或干等
         */
        default long onTuningChanged(boolean preview) {
            return 0;
        }

        /** 照片回忆墙：mode=0 飘落，mode=1 拼心形 */
        default void onPhotoWallMode(int mode, java.util.List<android.net.Uri> uris) {
        }

        /** 星空许愿：在 (x,y) 比例坐标处挂一颗星 */
        default void onWishAdded(String text, float x, float y) {
        }
    }

    private static final String[] WEATHER_NAMES = {"关闭", "下雪 ❄", "樱花 🌸", "银杏 🍂"};
    private static final int[] WEATHER_MODES = {
            SkyView.WEATHER_NONE, SkyView.WEATHER_SNOW,
            SkyView.WEATHER_SAKURA, SkyView.WEATHER_GINKGO
    };
    private static final String[] PATTERN_NAMES = {"经典随机", "波浪扫射", "环形聚拢", "音乐节拍"};

    private final Context ctx;
    private final Callbacks cb;

    public RomanticDialogs(Context ctx, Callbacks cb) {
        this.ctx = ctx;
        this.cb = cb;
    }

    /** 新手引导（#11新）：一次性，可从菜单重看 */
    public void showGuide(boolean force) {
        if (!force && MMKV.defaultMMKV().decodeBool(MainActivity.KEY_GUIDE_SHOWN, false)) return;
        MMKV.defaultMMKV().encode(MainActivity.KEY_GUIDE_SHOWN, true);
        new UiGuard.SafeBuilder(ctx)
                .setTitle("玩法速览")
                .setMessage("🔥 点击屏幕：随机烟花绽放\n⏳ 长按屏幕蓄力：松开放中/大/超大全屏烟花\n"
                        + "🏮 更多菜单：孔明灯模式、下雪天气、月亮星座、音乐律动\n"
                        + "💖 顶级浪漫：三幕剧告白，结尾有应答时刻，可录制整场视频\n"
                        + "🎬 演出中底部有控制条：可跳过当前句、暂停或结束\n"
                        + "🎥 长按不再切纯净模式，纯净开关在更多菜单里")
                .setPositiveButton("知道了", null)
                .show();
    }

    /** 告白文案库：先选场景，再选一套，选中后填入台词框（仍可自由修改） */
    public void showPresetPicker(final TextInputEditText target) {
        new UiGuard.SafeBuilder(ctx)
                .setTitle("挑一套告白文案")
                .setItems(RomanticPresets.CATEGORIES, (d, which) -> {
                    final String[] set = RomanticPresets.PRESETS[which];
                    String[] preview = new String[set.length];
                    for (int i = 0; i < set.length; i++) {
                        preview[i] = RomanticPresets.summary(set[i]);
                    }
                    new UiGuard.SafeBuilder(ctx)
                            .setTitle(RomanticPresets.CATEGORIES[which])
                            .setItems(preview, (d2, w2) -> {
                                target.setText(set[w2]);
                                // 光标移到末尾，方便接着改
                                target.setSelection(target.getText() == null ? 0 : target.getText().length());
                                com.hjq.toast.Toaster.showShort("已填入，可随意改成你想说的话");
                            })
                            .setNegativeButton("返回", null)
                            .show();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    /** 背景色选择 */
    public void showColorPickerDialog() {
        String[] names = {"纯黑", "深蓝", "深紫", "暗红", "墨绿", "深灰"};
        final int[] colors = {
                android.graphics.Color.BLACK, android.graphics.Color.rgb(10, 10, 50),
                android.graphics.Color.rgb(40, 0, 60), android.graphics.Color.rgb(60, 0, 0),
                android.graphics.Color.rgb(0, 40, 20), android.graphics.Color.rgb(30, 30, 30)
        };
        new UiGuard.SafeBuilder(ctx).setTitle("背景颜色")
                .setItems(names, (d, w) -> cb.onBgColorPicked(colors[w])).show();
    }

    /** 自定义文字烟花 */
    public void showCustomTextDialog() {
        // 【UI 优化】改用 MD3 TextInputLayout（浮动标签 + 字数计数），并换成显式按钮：
        // 横屏下 AlertDialog 的系统按钮栏会被挤成 0 高度，确定/取消点了没反应
        android.view.View view = android.view.LayoutInflater.from(ctx)
                .inflate(R.layout.dialog_input_text, null);
        final com.google.android.material.textfield.TextInputLayout til =
                view.findViewById(R.id.til_input);
        final TextInputEditText input = view.findViewById(R.id.et_input);
        til.setHint("烟花文字（最多 8 个字）");
        til.setCounterEnabled(true);
        til.setCounterMaxLength(8);
        // 限制文字长度，避免过长导致显示效果差
        input.setFilters(new android.text.InputFilter[]{
                new android.text.InputFilter.LengthFilter(8)});
        input.setText(cb.currentCustomText());
        if (input.getText() != null) input.setSelection(input.getText().length());

        final androidx.appcompat.app.AlertDialog dialog =
                new UiGuard.SafeBuilder(ctx)
                        .setTitle("自定义文字烟花")
                        .setMessage("输入后点击屏幕、随机播放都会触发该文字烟花")
                        .setView(view)
                        .show();
        UiGuard.focusInput(dialog, input);

        view.findViewById(R.id.btn_confirm).setOnClickListener(v -> {
            String text = input.getText() == null ? "" : input.getText().toString().trim();
            if (text.isEmpty()) {
                com.hjq.toast.Toaster.showShort("文字不能为空");
                return;
            }
            cb.onCustomTextConfirmed(text);
            dialog.dismiss();
        });
        view.findViewById(R.id.btn_cancel).setOnClickListener(v -> dialog.dismiss());
    }

    public void showWeatherPicker() {
        new UiGuard.SafeBuilder(ctx).setTitle("天气粒子")
                .setItems(WEATHER_NAMES, (d, w) -> {
                    cb.onWeatherPicked(WEATHER_MODES[w]);
                    com.hjq.toast.Toaster.showShort("天气：" + WEATHER_NAMES[w]);
                }).show();
    }

    /** 【#17】节日主题选择 */
    public void showThemePicker() {
        int cur = cb.currentTheme();
        new UiGuard.SafeBuilder(ctx).setTitle("节日主题")
                .setSingleChoiceItems(ThemeManager.NAMES, cur, (d, w) -> {
                    cb.onThemePicked(w);
                    d.dismiss();
                    com.hjq.toast.Toaster.showShort("主题：" + ThemeManager.name(w) + " 🎉");
                })
                .setNegativeButton("取消", null).show();
    }

    /**
     * 台词朗读音源选择。
     *
     * <p>本地音源（离线 VITS）优先推荐：不联网、音色稳定，
     * 不会像系统引擎那样因为用户手机没装中文语音包而一声不出。
     */
    public void showSpeechPicker() {
        String[] items = {
                "🤖 自动（系统优先，缺失时转本地）",
                "📱 只用系统内置引擎",
                "🌟 只用离线音色（神经网络合成）",
                "🔇 关闭朗读",
                "🎭 音色与变声（" + TtsVoice.ALL.length + " 种可下载）",
                "⚙ 系统语音设置"
        };
        new UiGuard.SafeBuilder(ctx).setTitle("台词朗读音色")
                .setSingleChoiceItems(items, SpeechEngine.getMode(), (d, w) -> {
                    d.dismiss();
                    if (w == 4) {
                        showVoicePicker();
                        return;
                    }
                    if (w == 5) {
                        // 装语音包 / 换引擎：跳系统设置（列表项而非底部按钮，横屏下按钮会被挤没）
                        SpeechEngine.openSystemTtsSettings(ctx);
                        return;
                    }
                    SpeechEngine.setMode(w);
                    cb.onSpeechModePicked(w);
                    com.hjq.toast.Toaster.showShort(
                            w == SpeechEngine.MODE_OFF ? "已关闭台词朗读"
                                    : "朗读音源：" + items[w]);
                })
                .setNegativeButton("取消", null)
                .show();
    }

    /**
     * 音色包选择：三套独立音色，下了哪个用哪个，没下的现场下。
     * 变声、多说话人换声音、清理入口也挂在这层。
     */
    public void showVoicePicker() {
        java.util.List<String> items = new java.util.ArrayList<>();
        java.util.List<TtsVoice> voices = new java.util.ArrayList<>();
        TtsVoice cur = TtsVoice.current();
        for (TtsVoice v : TtsVoice.ALL) {
            String state;
            if (v == cur) state = "✓ 使用中";
            else if (TtsModelStore.isReady(ctx, v)) state = "已下载";
            else state = "下载 " + TtsModelStore.sizeText(v.totalBytes());
            items.add(v.name + " · " + v.desc + "\n     " + state);
            voices.add(v);
        }
        // 变声档：改音调不改时长，零下载
        items.add("🎚 变声：" + TtsVoice.pitchName() + "（不用下载）");
        final int pitchIdx = items.size() - 1;
        // 多说话人包：换个 sid 就是一个新声音，不用重新下载
        final int shuffleIdx;
        if (cur.multiSpeaker() && TtsModelStore.isReady(ctx, cur)) {
            items.add("🎲 随机换一个声音（当前 #" + TtsVoice.currentSpeaker() + "）");
            shuffleIdx = items.size() - 1;
        } else {
            shuffleIdx = -1;
        }
        // 清理入口：只清当前选中的那套，别的一根手指头都不碰
        final int deleteIdx;
        if (TtsModelStore.isReady(ctx, cur)) {
            items.add("🗑 清理当前音色（" + cur.name + " · 释放 "
                    + TtsModelStore.sizeText(cur.totalBytes()) + "）");
            deleteIdx = items.size() - 1;
        } else {
            deleteIdx = -1;
        }

        new UiGuard.SafeBuilder(ctx).setTitle("🎭 音色与变声")
                .setItems(items.toArray(new String[0]), (d, w) -> {
                    d.dismiss();
                    if (w == pitchIdx) {
                        showPitchPicker();
                        return;
                    }
                    if (w == shuffleIdx) {
                        cb.onTtsSpeakerShuffle();
                        return;
                    }
                    if (w == deleteIdx) {
                        cb.onTtsModelManage(false);
                        return;
                    }
                    if (w < voices.size()) {
                        TtsVoice v = voices.get(w);
                        if (TtsModelStore.isReady(ctx, v)) {
                            cb.onTtsVoicePicked(v);
                        } else {
                            cb.onTtsVoiceDownload(v);
                        }
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    /** 变声档：基于已下载音色做音调变体，一份模型多种听感 */
    public void showPitchPicker() {
        new UiGuard.SafeBuilder(ctx).setTitle("🎚 变声（不额外下载）")
                .setSingleChoiceItems(TtsVoice.PITCH_NAMES, TtsVoice.currentPitch(),
                        (d, w) -> {
                            d.dismiss();
                            cb.onTtsPitchPicked(w);
                        })
                .setNegativeButton("取消", null)
                .show();
    }

    /**
     * 【新增】演出模板：一键套用 文案 + 节日主题 + 随机剧本。
     *
     * <p>两级列表而非"选择 + 底部按钮"：横屏下 AlertDialog 的按钮栏会被挤成 0 高度，
     * 用户点了没反应（这个坑在纪念日对话框上踩过一次）。
     */
    public void showShowTemplatePicker() {
        new UiGuard.SafeBuilder(ctx).setTitle("演出模板（一键整场风格）")
                .setItems(ShowTemplate.titles(), (d, w) -> {
                    d.dismiss();
                    // 前一个 dialog 的 dismiss 是异步 post 的，紧接着 show 新弹窗偶尔会
                    // 出现"点了一下没弹出"的观感。延后一帧再弹，稳。
                    new android.os.Handler(android.os.Looper.getMainLooper())
                            .post(() -> showTemplateAction(w));
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showTemplateAction(int index) {
        ShowTemplate t = ShowTemplate.ALL[index];
        String[] acts = {
                "▶ 立即开演（" + t.emoji + " " + t.name + "）",
                "🎨 只套用风格，先不开演",
                "取消"
        };
        new UiGuard.SafeBuilder(ctx)
                .setTitle(t.emoji + " " + t.name)
                .setMessage("主题：" + ThemeManager.name(t.theme)
                        + "\n剧本：" + PATTERN_NAMES[patternIndex(t.pattern)]
                        + "\n台词：" + t.lines.replace('\n', '／'))
                .setItems(acts, (d, w) -> {
                    d.dismiss();
                    if (w == 2) return;
                    cb.onTemplatePicked(index, w == 0);
                })
                .show();
    }

    private static int patternIndex(int pattern) {
        if (pattern == com.xiaobo.fireworks.GlobalFirework.PATTERN_CLASSIC) return 0;
        if (pattern == com.xiaobo.fireworks.GlobalFirework.PATTERN_WAVE) return 1;
        if (pattern == com.xiaobo.fireworks.GlobalFirework.PATTERN_CONVERGE) return 2;
        return 3;
    }

    /** 【新增】纪念日提醒设置。
     *
     * <p>纪念日一年只有一次，靠自己记几乎必忘；这里让用户选提前量，
     * 开关由 {@link AnniversaryReminder} 转成系统闹钟（开机也会自动重排）。
     */
    public void showAnniversaryReminderPicker() {
        new UiGuard.SafeBuilder(ctx).setTitle("纪念日提醒")
                .setMessage("提醒会在当天上午 9 点发出，点开通知可直接重演那一天的演出。"
                        + "重启手机后会自动恢复。")
                .setSingleChoiceItems(AnniversaryReminder.DAY_LABELS,
                        currentRemindIndex(), (d, w) -> {
                            d.dismiss();
                            AnniversaryReminder.setEnabled(ctx, true);
                            AnniversaryReminder.setDays(ctx, AnniversaryReminder.DAY_OPTIONS[w]);
                            com.hjq.toast.Toaster.showShort(
                                    "已开启 · " + AnniversaryReminder.DAY_LABELS[w]);
                        })
                .setNeutralButton("关闭提醒", (d, w) -> {
                    AnniversaryReminder.setEnabled(ctx, false);
                    com.hjq.toast.Toaster.showShort("已关闭纪念日提醒");
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private static int currentRemindIndex() {
        int days = AnniversaryReminder.getDays();
        for (int i = 0; i < AnniversaryReminder.DAY_OPTIONS.length; i++) {
            if (AnniversaryReminder.DAY_OPTIONS[i] == days) return i;
        }
        return 0;
    }

    /** 随机剧本（#5新）：调用库编舞引擎 */
    public void showAutoPatternPicker() {
        new UiGuard.SafeBuilder(ctx).setTitle("随机播放剧本")
                .setSingleChoiceItems(PATTERN_NAMES, cb.currentPattern(), (d, w) -> {
                    cb.onPatternPicked(w);
                    d.dismiss();
                    Toaster.showShort("剧本：" + PATTERN_NAMES[w] + "（点随机播放生效）");
                })
                .setNegativeButton("取消", null).show();
    }

    // ==================== 音效与触感 ====================

    /**
     * 音效与触感开关。
     *
     * <p>确认按钮放在布局里而不是 setPositiveButton：本 App 固定横屏，
     * 系统按钮栏会被挤成 0 高度而完全不可见（样式开关对话框踩过这个坑）。
     */
    public void showSoundFxPicker() {
        View v = LayoutInflater.from(ctx).inflate(R.layout.dialog_sound_fx, null);
        MaterialCheckBox boxSound = v.findViewById(R.id.sw_sound);
        MaterialCheckBox boxVib = v.findViewById(R.id.sw_vibrate);
        TextView tvVol = v.findViewById(R.id.tv_volume_label);
        Slider slider = v.findViewById(R.id.slider_volume);

        boxSound.setChecked(SoundFx.isEnabled());
        boxVib.setChecked(SoundFx.isVibrateEnabled());
        SliderSafe.setValue(slider, SoundFx.volume() * 100f);
        tvVol.setText("音量  " + Math.round(SoundFx.volume() * 100) + "%");

        AlertDialog dlg = new UiGuard.SafeBuilder(ctx)
                .setTitle("🔔 音效与触感")
                .setView(v)
                .show();
        v.findViewById(R.id.btn_close).setOnClickListener(x -> dlg.dismiss());
        v.findViewById(R.id.btn_preview).setOnClickListener(x -> SoundFx.preview());
        boxSound.setOnCheckedChangeListener((b, on) -> {
            SoundFx.setEnabled(on);
            cb.onSoundFxChanged();
            Toaster.showShort(on ? "烟花音效已开启" : "烟花音效已关闭");
        });
        boxVib.setOnCheckedChangeListener((b, on) -> {
            SoundFx.setVibrateEnabled(on);
            cb.onSoundFxChanged();
            Toaster.showShort(on ? "震动反馈已开启" : "震动反馈已关闭");
        });
        slider.addOnChangeListener((s, value, fromUser) -> {
            SoundFx.setVolume(value / 100f);
            tvVol.setText("音量  " + Math.round(value) + "%");
            cb.onSoundFxChanged();
        });
    }

    // ==================== 手写签名烟花 ====================

    /**
     * 签名导出位图的边长。
     *
     * <p>不是越大越好：库按 {@code step = size/26} 的网格采样，粒子数上限固定
     * （约 26×26），尺寸变大只会让每个粒子在网格内的随机抖动变大，轮廓反而更散。
     * 300 时 step=11，抖动 ±5px，是"能看清笔画"和"不糊成一团"的平衡点。
     */
    private static final int PAD_EXPORT_PX = 300;

    /** 手写画板：写完可以直接炸成烟花，也可以存下来给以后每一场演出用。 */
    public void showSignatureDialog() {
        View v = LayoutInflater.from(ctx).inflate(R.layout.dialog_signature, null);
        SignaturePadView pad = v.findViewById(R.id.pad);
        AlertDialog dlg = new UiGuard.SafeBuilder(ctx)
                .setTitle("✍ 手写签名烟花")
                .setView(v)
                .show();
        v.findViewById(R.id.btn_undo).setOnClickListener(x -> pad.undo());
        v.findViewById(R.id.btn_clear).setOnClickListener(x -> pad.clear());
        v.findViewById(R.id.btn_save).setOnClickListener(x -> {
            Bitmap bmp = pad.toShapeBitmap(PAD_EXPORT_PX);
            if (bmp == null) {
                Toaster.showShort("先写下点什么吧 ✍");
                return;
            }
            cb.onSignatureReady(bmp, true);
        });
        v.findViewById(R.id.btn_bloom).setOnClickListener(x -> {
            Bitmap bmp = pad.toShapeBitmap(PAD_EXPORT_PX);
            if (bmp == null) {
                Toaster.showShort("先写下点什么吧 ✍");
                return;
            }
            cb.onSignatureReady(bmp, false);
            dlg.dismiss();
        });
    }

    // ==================== 剧本库 ====================

    public void showScriptLibrary() {
        List<ScriptStore.Entry> list = ScriptStore.load();
        List<String> items = new ArrayList<>();
        items.add("💾 把当前台词存成剧本");
        if (list.isEmpty()) {
            items.add("（还没有剧本，先存一条）");
        } else {
            for (ScriptStore.Entry e : list) {
                items.add("📜 " + e.name + " — " + e.preview());
            }
        }
        new UiGuard.SafeBuilder(ctx)
                .setTitle("我的剧本库（" + list.size() + "/" + ScriptStore.MAX + "）")
                .setItems(items.toArray(new String[0]), (d, w) -> {
                    d.dismiss();
                    if (w == 0) {
                        saveCurrentAsScript();
                    } else if (!list.isEmpty()) {
                        showScriptAction(list.get(w - 1));
                    }
                })
                .setNegativeButton("关闭", null)
                .show();
    }

    private void saveCurrentAsScript() {
        String lines = cb.currentLines();
        if (lines == null || lines.trim().isEmpty()) {
            Toaster.showShort("当前还没有台词，先去浪漫设置里写几句");
            return;
        }
        View v = LayoutInflater.from(ctx).inflate(R.layout.dialog_input_text, null);
        TextInputEditText et = v.findViewById(R.id.et_input);
        ((com.google.android.material.textfield.TextInputLayout) v.findViewById(R.id.til_input))
                .setHint("剧本名字");
        et.setText(ScriptStore.defaultName(lines));
        AlertDialog dlg = new UiGuard.SafeBuilder(ctx)
                .setTitle("存为新剧本")
                .setView(v)
                .show();
        UiGuard.focusInput(dlg, et);
        v.findViewById(R.id.btn_cancel).setOnClickListener(x -> dlg.dismiss());
        v.findViewById(R.id.btn_confirm).setOnClickListener(x -> {
            String name = et.getText() == null ? "" : et.getText().toString().trim();
            boolean ok = ScriptStore.add(name, lines);
            dlg.dismiss();
            if (ok) {
                Toaster.showShort("已存入剧本库：" + name);
                // 存完必须回到列表并刷新条数：只弹一句 Toast 的话，
                // 用户看不到剧本出现在列表里，会以为"没保存成功"
                new android.os.Handler(android.os.Looper.getMainLooper())
                        .post(this::showScriptLibrary);
            } else {
                Toaster.showShort("剧本库满了，先删掉几条吧");
            }
        });
    }

    private void showScriptAction(ScriptStore.Entry e) {
        String[] acts = {
                "▶ 立刻用它开演",
                "📷 生成二维码分享给她",
                "✏ 改名字",
                "🗑 删除这个剧本",
                "取消"
        };
        new UiGuard.SafeBuilder(ctx)
                .setTitle(e.name)
                .setMessage(e.lines.replace('\n', '／'))
                .setItems(acts, (d, w) -> {
                    d.dismiss();
                    switch (w) {
                        case 0:
                            cb.onScriptPlay(e.lines);
                            break;
                        case 1:
                            String code = RomanticShareCodec.encode(splitLines(e.lines), 5, 0.7f);
                            showQrCode(code, e.name);
                            break;
                        case 2:
                            renameScript(e);
                            break;
                        case 3:
                            ScriptStore.remove(e.id);
                            Toaster.showShort("已删除：" + e.name);
                            break;
                        default:
                            break;
                    }
                })
                .show();
    }

    private void renameScript(ScriptStore.Entry e) {
        View v = LayoutInflater.from(ctx).inflate(R.layout.dialog_input_text, null);
        TextInputEditText et = v.findViewById(R.id.et_input);
        ((com.google.android.material.textfield.TextInputLayout) v.findViewById(R.id.til_input))
                .setHint("新的名字");
        et.setText(e.name);
        AlertDialog dlg = new UiGuard.SafeBuilder(ctx).setTitle("改名字").setView(v).show();
        UiGuard.focusInput(dlg, et);
        v.findViewById(R.id.btn_cancel).setOnClickListener(x -> dlg.dismiss());
        v.findViewById(R.id.btn_confirm).setOnClickListener(x -> {
            String name = et.getText() == null ? "" : et.getText().toString().trim();
            if (!name.isEmpty()) {
                ScriptStore.rename(e.id, name);
                Toaster.showShort("已改名");
            }
            dlg.dismiss();
        });
    }

    /** 把当前设置生成二维码（菜单入口用）。 */
    public void showQrForCurrentLines() {
        String lines = cb.currentLines();
        if (lines == null || lines.trim().isEmpty()) {
            Toaster.showShort("当前还没有台词，先去浪漫设置里写几句");
            return;
        }
        showQrCode(RomanticShareCodec.encode(splitLines(lines), 5, 0.7f), "这场烟花");
    }

    public void showQrCode(String code, String title) {
        if (code == null || code.isEmpty()) {
            Toaster.showShort("生成分享码失败，试试少写几句");
            return;
        }
        Bitmap bmp = QrCodeUtil.encode(code, 720);
        if (bmp == null) {
            Toaster.showShort("生成二维码失败，试试少写几句");
            return;
        }
        View v = LayoutInflater.from(ctx).inflate(R.layout.dialog_qr_code, null);
        ((ImageView) v.findViewById(R.id.img_qr)).setImageBitmap(bmp);
        AlertDialog dlg = new UiGuard.SafeBuilder(ctx)
                .setTitle("📷 " + (title == null ? "扫码领取这场烟花" : title))
                .setView(v)
                .show();
        v.findViewById(R.id.btn_close_qr).setOnClickListener(x -> dlg.dismiss());
        v.findViewById(R.id.btn_save_qr).setOnClickListener(x -> {
            Uri uri = QrCodeUtil.saveToGallery(ctx, bmp, title == null ? "firework" : title);
            Toaster.showShort(uri == null ? "保存失败" : "已保存到相册（Pictures/烟花）");
        });
    }

    private static List<String> splitLines(String lines) {
        List<String> out = new ArrayList<>();
        if (lines == null) return out;
        for (String s : lines.split("\n")) {
            if (!s.trim().isEmpty()) out.add(s.trim());
        }
        return out;
    }

    // ==================== 定时自动演出 ====================

    public void showScheduleManager() {
        List<ScheduledShow.Entry> list = ScheduledShow.load();
        List<String> items = new ArrayList<>();
        items.add("➕ 新增定时演出");
        for (ScheduledShow.Entry e : list) {
            items.add("⏰ " + e.name + " — " + ScheduledShow.describe(e.timeMillis));
        }
        new UiGuard.SafeBuilder(ctx)
                .setTitle("定时自动演出")
                .setItems(items.toArray(new String[0]), (d, w) -> {
                    d.dismiss();
                    if (w == 0) {
                        showScheduleDialog(null, null);
                    } else {
                        askRemoveSchedule(list.get(w - 1));
                    }
                })
                .setNegativeButton("关闭", null)
                .show();
    }

    private void askRemoveSchedule(ScheduledShow.Entry e) {
        String[] acts = {"🗑 取消这条定时", "留着"};
        new UiGuard.SafeBuilder(ctx)
                .setTitle(e.name)
                .setMessage(ScheduledShow.describe(e.timeMillis))
                .setItems(acts, (d, w) -> {
                    d.dismiss();
                    if (w == 0) {
                        cb.onScheduleRemoved(e.id);
                        Toaster.showShort("已取消这条定时");
                    }
                })
                .show();
    }

    public void showScheduleDialog(String presetName, String presetLines) {
        View v = LayoutInflater.from(ctx).inflate(R.layout.dialog_schedule_show, null);
        DatePicker dp = v.findViewById(R.id.date_picker);
        TimePicker tp = v.findViewById(R.id.time_picker);
        TextView hint = v.findViewById(R.id.tv_hint);
        tp.setIs24HourView(true);

        final String[] name = {presetName == null ? "定时演出" : presetName};
        final String[] lines = {presetLines == null ? cb.currentLines() : presetLines};
        hint.setText("将使用：" + firstLine(lines[0]));

        AlertDialog dlg = new UiGuard.SafeBuilder(ctx)
                .setTitle("⏰ 定一个时间")
                .setView(v)
                .show();
        v.findViewById(R.id.btn_pick_script).setOnClickListener(x ->
                pickScriptForSchedule(name, lines, hint));
        v.findViewById(R.id.btn_cancel).setOnClickListener(x -> dlg.dismiss());
        v.findViewById(R.id.btn_save).setOnClickListener(x -> {
            Calendar cal = Calendar.getInstance();
            cal.set(dp.getYear(), dp.getMonth(), dp.getDayOfMonth(), tp.getHour(), tp.getMinute(), 0);
            cal.set(Calendar.MILLISECOND, 0);
            long millis = cal.getTimeInMillis();
            if (millis <= System.currentTimeMillis()) {
                Toaster.showShort("这个时间已经过去了，换个时间吧");
                return;
            }
            cb.onScheduleAdded(millis, name[0], lines[0]);
            dlg.dismiss();
        });
    }

    private void pickScriptForSchedule(String[] name, String[] lines, TextView hint) {
        List<ScriptStore.Entry> list = ScriptStore.load();
        if (list.isEmpty()) {
            Toaster.showShort("剧本库还是空的，先去存一条");
            return;
        }
        String[] items = new String[list.size()];
        for (int i = 0; i < list.size(); i++) {
            items[i] = list.get(i).name + " — " + list.get(i).preview();
        }
        new UiGuard.SafeBuilder(ctx)
                .setTitle("选一套台词")
                .setItems(items, (d, w) -> {
                    d.dismiss();
                    name[0] = list.get(w).name;
                    lines[0] = list.get(w).lines;
                    hint.setText("将使用剧本：" + list.get(w).name);
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private static String firstLine(String lines) {
        if (lines == null || lines.trim().isEmpty()) return "（当前还没有台词）";
        String s = lines.trim().split("\n")[0].trim();
        return s.length() > 16 ? s.substring(0, 16) + "…" : s;
    }

    // ==================== 烟花手感调参 ====================

    public void showTuningDialog() {
        View v = LayoutInflater.from(ctx).inflate(R.layout.dialog_firework_tuning, null);
        Slider sDensity = v.findViewById(R.id.slider_density);
        Slider sSpeed = v.findViewById(R.id.slider_speed);
        Slider sFall = v.findViewById(R.id.slider_fall);
        Slider sTrail = v.findViewById(R.id.slider_trail);
        TextView tDensity = v.findViewById(R.id.tv_density_label);
        TextView tSpeed = v.findViewById(R.id.tv_speed_label);
        TextView tFall = v.findViewById(R.id.tv_fall_label);
        TextView tTrail = v.findViewById(R.id.tv_trail_label);

        // 必须走 SliderSafe：默认系数换算出来是 33.333336，而 stepSize=5，
        // 直接 setValue 会在布局阶段抛 IllegalStateException（弹窗一开就崩）
        SliderSafe.setValue(sDensity, toSlider(FireworkTuning.density(),
                FireworkTuning.MIN_DENSITY, FireworkTuning.MAX_DENSITY));
        SliderSafe.setValue(sSpeed, toSlider(FireworkTuning.speed(),
                FireworkTuning.MIN_SPEED, FireworkTuning.MAX_SPEED));
        SliderSafe.setValue(sFall, toSlider(FireworkTuning.fall(),
                FireworkTuning.MIN_FALL, FireworkTuning.MAX_FALL));
        SliderSafe.setValue(sTrail, toSlider(FireworkTuning.trail(),
                FireworkTuning.MIN_TRAIL, FireworkTuning.MAX_TRAIL));
        tDensity.setText("粒子密度  " + pct(sDensity.getValue()));
        tSpeed.setText("爆炸速度  " + pct(sSpeed.getValue()));
        tFall.setText("下坠感  " + pct(sFall.getValue()) + "（越大越快坠下）");
        tTrail.setText("拖尾长度  " + pct(sTrail.getValue()));

        AlertDialog dlg = new UiGuard.SafeBuilder(ctx)
                .setTitle("🎛 烟花手感")
                .setView(v)
                .show();
        // 滑块拖动时不重建烟花池：手指每动一帧都整池重建会明显掉帧，
        // 而且中间那些值用户根本不看。改成只写参数 + 更新标签，松手才重建一次。
        sDensity.addOnChangeListener((s, val, fromUser) -> {
            FireworkTuning.setDensity(fromSlider(val,
                    FireworkTuning.MIN_DENSITY, FireworkTuning.MAX_DENSITY));
            tDensity.setText("粒子密度  " + pct(val));
        });
        sSpeed.addOnChangeListener((s, val, fromUser) -> {
            FireworkTuning.setSpeed(fromSlider(val,
                    FireworkTuning.MIN_SPEED, FireworkTuning.MAX_SPEED));
            tSpeed.setText("爆炸速度  " + pct(val));
        });
        sFall.addOnChangeListener((s, val, fromUser) -> {
            FireworkTuning.setFall(fromSlider(val,
                    FireworkTuning.MIN_FALL, FireworkTuning.MAX_FALL));
            tFall.setText("下坠感  " + pct(val) + "（越大越快坠下）");
        });
        sTrail.addOnChangeListener((s, val, fromUser) -> {
            FireworkTuning.setTrail(fromSlider(val,
                    FireworkTuning.MIN_TRAIL, FireworkTuning.MAX_TRAIL));
            tTrail.setText("拖尾长度  " + pct(val));
        });
        Runnable rebuild = () -> cb.onTuningChanged(false);
        onSettled(sDensity, rebuild);
        onSettled(sSpeed, rebuild);
        onSettled(sFall, rebuild);
        onSettled(sTrail, rebuild);
        v.findViewById(R.id.btn_reset).setOnClickListener(x -> {
            FireworkTuning.reset();
            cb.onTuningChanged(false);
            Toaster.showShort("已恢复默认手感");
            dlg.dismiss();
        });

        // 「放一朵看看」：弹窗先让位——淡到近乎透明、下移到屏幕下方，
        // 并去掉背景压暗，否则整朵烟花都被挡在卡片后面，等于白调。
        android.view.Window win = dlg.getWindow();
        android.view.View decor = win == null ? null : win.getDecorView();
        android.os.Handler ui = new android.os.Handler(android.os.Looper.getMainLooper());
        final boolean[] showing = {true};
        final boolean[] previewing = {false};
        final Runnable[] restore = {null};
        MaterialButton btnPreview = v.findViewById(R.id.btn_preview);

        btnPreview.setOnClickListener(x -> {
            if (previewing[0]) return;          // 上一朵还没放完，别叠加
            long ms = cb.onTuningChanged(true);
            previewing[0] = true;
            btnPreview.setText("放着呢…");
            v.findViewById(R.id.btn_reset).setEnabled(false);
            v.findViewById(R.id.btn_done).setEnabled(false);
            btnPreview.setEnabled(false);        // 透明期看不见，防误触

            if (decor != null && win != null) {
                float oldDim = win.getAttributes().dimAmount;
                win.setDimAmount(0f);            // 去掉背景压暗，夜空才看得清
                int[] loc = new int[2];
                decor.getLocationOnScreen(loc);
                int screenH = ctx.getResources().getDisplayMetrics().heightPixels;
                // 挪到 70% 屏高以下：绽放点在 45% 屏高，这样一定不挡
                float dy = Math.max(0f, screenH * 0.70f - loc[1]);
                decor.animate().alpha(0.10f).translationY(dy).setDuration(220).start();
                restore[0] = () -> {
                    if (!showing[0]) return;
                    decor.animate().alpha(1f).translationY(0f).setDuration(260).start();
                    try {
                        win.setDimAmount(oldDim);
                    } catch (Throwable ignored) {
                        // window 已销毁时属性写入会抛，忽略即可
                    }
                };
            }
            // 先放再看：150ms 延迟 + 粒子寿命，再多留 700ms 余量
            ui.postDelayed(() -> {
                previewing[0] = false;
                if (!showing[0]) return;
                if (restore[0] != null) restore[0].run();
                btnPreview.setText("放一朵看看");
                v.findViewById(R.id.btn_reset).setEnabled(true);
                v.findViewById(R.id.btn_done).setEnabled(true);
                btnPreview.setEnabled(true);
            }, Math.max(1200L, ms));
        });

        dlg.setOnDismissListener(di -> {
            showing[0] = false;
            ui.removeCallbacksAndMessages(null);   // 别在已销毁的 window 上做还原动画
        });

        v.findViewById(R.id.btn_done).setOnClickListener(x -> dlg.dismiss());
    }

    /** 滑块松手（或点选落定）时才执行：调参的重活都挂在这里，拖动过程不打扰。 */
    private static void onSettled(Slider s, Runnable apply) {
        s.addOnSliderTouchListener(new Slider.OnSliderTouchListener() {
            @Override
            public void onStartTrackingTouch(Slider slider) {
            }

            @Override
            public void onStopTrackingTouch(Slider slider) {
                apply.run();
            }
        });
    }

    private static float toSlider(float v, float min, float max) {
        return (v - min) / (max - min) * 100f;
    }

    private static float fromSlider(float v, float min, float max) {
        return min + (max - min) * v / 100f;
    }

    private static String pct(float v) {
        return Math.round(v) + "%";
    }

    // ==================== 文字烟花字体 ====================

    public void showFontPicker() {
        new UiGuard.SafeBuilder(ctx)
                .setTitle("✍ 文字烟花的字体")
                .setMessage("换了字体后，文字烟花会按你选的字形炸开（内置毛笔与圆体为开源字体）。")
                .setSingleChoiceItems(FontKit.NAMES, FontKit.current(), (d, w) -> {
                    d.dismiss();
                    FontKit.set(w);
                    cb.onFontPicked(w);
                    Toaster.showShort("字体：" + FontKit.NAMES[w]);
                })
                .setNegativeButton("取消", null)
                .show();
    }

    // ==================== 照片回忆墙 ====================

    public void showPhotoWallMode(List<Uri> uris) {
        if (uris == null || uris.isEmpty()) return;
        String[] acts = {
                "🍂 让照片从夜空飘落",
                "💖 拼成一颗心（收尾最杀）",
                "取消"
        };
        new UiGuard.SafeBuilder(ctx)
                .setTitle("已选 " + uris.size() + " 张照片")
                .setItems(acts, (d, w) -> {
                    d.dismiss();
                    if (w == 2) return;
                    cb.onPhotoWallMode(w, uris);
                })
                .show();
    }

    // ==================== 星空许愿 ====================

    /** 在指定位置挂一颗愿望星。 */
    public void showWishInput(float x, float y) {
        View v = LayoutInflater.from(ctx).inflate(R.layout.dialog_input_text, null);
        TextInputEditText et = v.findViewById(R.id.et_input);
        ((com.google.android.material.textfield.TextInputLayout) v.findViewById(R.id.til_input))
                .setHint("许个愿吧（只有你们两个人看得见）");
        AlertDialog dlg = new UiGuard.SafeBuilder(ctx)
                .setTitle("🌟 许个愿")
                .setView(v)
                .show();
        UiGuard.focusInput(dlg, et);
        v.findViewById(R.id.btn_cancel).setOnClickListener(z -> dlg.dismiss());
        v.findViewById(R.id.btn_confirm).setOnClickListener(z -> {
            String text = et.getText() == null ? "" : et.getText().toString().trim();
            if (text.isEmpty()) {
                Toaster.showShort("写点什么再挂上去吧");
                return;
            }
            cb.onWishAdded(text, x, y);
            dlg.dismiss();
        });
    }
}
