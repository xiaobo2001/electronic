package com.xiaobo.electronic.ui;

import android.Manifest;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputFilter;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.view.MotionEvent;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.LinearInterpolator;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.hjq.toast.Toaster;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.skydoves.balloon.ArrowOrientation;
import com.skydoves.balloon.Balloon;
import com.skydoves.balloon.BalloonSizeSpec;
import com.tencent.mmkv.MMKV;
import com.xiaobo.fireworks.EmojiBitmapCache;
import com.xiaobo.fireworks.FireworkConfig;
import com.xiaobo.fireworks.FireworkView;
import com.xiaobo.fireworks.GlobalFirework;
import com.xiaobo.fireworks.RhythmFirework;
import com.xiaobo.fireworks.SkyView;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Random;

import android.app.WallpaperManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.IntentFilter;
import android.media.projection.MediaProjectionManager;
import com.xiaobo.electronic.data.AnniversaryReceiver;
import com.xiaobo.electronic.data.AnniversaryReminder;
import com.xiaobo.electronic.data.AnniversaryStore;
import com.xiaobo.electronic.App;
import com.xiaobo.electronic.speech.AsrModelStore;
import com.xiaobo.electronic.util.CrashLogger;
import com.xiaobo.electronic.util.DevicePerf;
import com.xiaobo.electronic.data.FireworkTuning;
import com.xiaobo.electronic.view.FireworkWallpaperService;
import com.xiaobo.electronic.util.FontKit;
import com.xiaobo.electronic.media.GifExporter;
import com.xiaobo.electronic.media.MementoCard;
import com.xiaobo.electronic.speech.ModelDownloader;
import com.xiaobo.electronic.speech.OfflineAsr;
import com.xiaobo.electronic.speech.PackSources;
import com.xiaobo.electronic.view.ParticleTextView;
import com.xiaobo.electronic.view.PhotoWall;
import com.xiaobo.electronic.R;
import com.xiaobo.electronic.data.RomanticShareCodec;
import com.xiaobo.electronic.show.RomanticShowDirector;
import com.xiaobo.electronic.show.ScheduledShow;
import com.xiaobo.electronic.media.ScreenRecorderService;
import com.xiaobo.electronic.data.ScriptStore;
import com.xiaobo.electronic.util.ShakeDetector;
import com.xiaobo.electronic.show.ShowTemplate;
import com.xiaobo.electronic.data.SignatureStore;
import com.xiaobo.electronic.util.SliderSafe;
import com.xiaobo.electronic.media.SoundFx;
import com.xiaobo.electronic.speech.SpeechEngine;
import com.xiaobo.electronic.util.StartupTracer;
import com.xiaobo.electronic.speech.TtsModelStore;
import com.xiaobo.electronic.speech.TtsVoice;
import com.xiaobo.electronic.view.WishSkyView;
import com.xiaobo.electronic.data.WishStore;

public class MainActivity extends AppCompatActivity {

    private ConstraintLayout rootLayout;
    private FrameLayout floatingContainer;
    private ParticleTextView particleTextView;
    // 成员变量区只保留这两个按钮声明即可
    private Button btnAutoPlay, btnRomantic, btnMore;

    private MediaPlayer mediaPlayer;
    private ActivityResultLauncher<String> requestPermissionLauncher;
    private ActivityResultLauncher<Intent> openGalleryLauncher;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Random random = new Random();

    private boolean isAutoPlaying = false;
    private boolean isRomanticMode = false;

    private static final String KEY_BG_TYPE = "bg_type";
    private static final String KEY_BG_COLOR = "bg_color";
    private static final String KEY_BG_URI = "bg_uri";

    private final Queue<ImageView> petalPool = new LinkedList<>();

    // 烟花预设配置
    private FireworkConfig romanticHeartConfig;
    // 爱心轨迹节拍计数：按时间间隔保留带音效/震动的 bloom 作为反馈锚点
    private int heartTick = 0;
    // 爱心轨迹节奏（按设备档位）：拍间隔、角度/呼吸时间补偿系数、锚点拍数（≈1 秒 1 次轰鸣）
    private int heartTraceIntervalMs = 80;
    private float heartStepScale = 1f;
    private int heartAnchorEveryTicks = 12;
    private long lastHeartTickMs = 0L;
    private FireworkConfig[] randomFireworkPool;
    // 自定义烟花文字，默认值为“新年快乐”
    private String customFireworkText = "新年快乐";

    private LinearLayout llBottomBar;
    private boolean isPureMode = false;
    private float downX, downY;
    private static final int TOUCH_SLOP = 20;
    private static final String KEY_PURE_MODE = "pure_mode";

    // 浪漫模式新增配置
    private int countdownSeconds = 3;
    private float heartBreathPhase = 0f; // 爱心呼吸相位
    private int heartLoopCount = 0;      // 爱心旋转圈数计数
    private double heartAngle = 0;
    private long lastPureModeSwitchTime = 0;
    private static final long PURE_MODE_SWITCH_COOLDOWN = 1000; // 1秒内只能切换一次

    private boolean isLongPressTriggered = false;
    private static final long LONG_PRESS_TIMEOUT = 1500;
    // ===== 蓄力烟花（#1新）：长按分级蓄力，松开放对应大小 =====
    private int chargeLevel = 0;
    private final java.util.ArrayList<Runnable> chargeTasks = new java.util.ArrayList<>();
    // ===== 指尖发射（#2新）：开启后点击/蓄力走火箭升空 =====
    private static final String KEY_ROCKET_MODE = "rocket_mode";
    private boolean rocketMode;
    // ===== 余辉（#3新） =====
    private static final String KEY_AFTERGLOW = "afterglow";
    // ===== 随机剧本（#5新） =====
    static final String KEY_AUTO_PATTERN = "auto_pattern"; // 0经典 1波浪 2聚拢 3节拍
    // ===== BGM（#7旧） =====
    private static final String KEY_BGM_URI = "bgm_uri";
    private static final String KEY_BGM_VOLUME = "bgm_volume";
    private float bgmVolume = 0.7f;
    // 【#23】她的名字/你的名字：贯穿全场与卡片落款
    private static final String KEY_HER_NAME = "romantic_her_name";
    private static final String KEY_MY_NAME = "romantic_my_name";
    private String herName = "";
    private String myName = "";
    // 【#23】戒指落下定格视图（stopRomanticMode 时强制移除，防残留）
    private TextView ringDropView;
    private String bgmUri;
    private ActivityResultLauncher<Intent> pickBgmLauncher;
    // ===== 录整场（#12旧） =====
    private boolean pendingRecordFull = false;
    // ===== 表白三幕剧/多段台词/应答（#1/#2/#3旧） =====
    private java.util.List<String> romanticLines = new java.util.ArrayList<>();
    private int romanticStage = 0; // 0开场 1幕一 2幕二 3幕三 4应答 5庆祝
    private int romanticBeatCounter = 0;
    private android.widget.FrameLayout answerOverlay;
    // ===== 纪念日（#14旧） =====
    private static final String KEY_ANNIVERSARY = "anniversary_list";
    static final String KEY_GUIDE_SHOWN = "guide_shown";
    static final String KEY_THEME = "theme_id"; // 0默认 1春节 2生日 3圣诞
    private int currentTheme = ThemeManager.THEME_DEFAULT;

    // ===== 【#7】浪漫 runnable 统一清理：中途停止时一次清光所有排队演出 =====
    // 【架构】演出导演：所有浪漫演出步骤经它登记，从而可被跳过、暂停、清空
    private RomanticShowDirector showDirector;
    // 【新增】演出控制条（跳过 / 暂停 / 结束）
    private android.widget.LinearLayout showControls;
    private com.google.android.material.button.MaterialButton btnPauseShow;
    // 【架构】弹窗层：纯 UI 选择类对话框迁出 Activity
    private RomanticDialogs dialogs;
    private boolean wasPureBeforeRomantic = false;
    private Runnable recordFullGuard;
    // ===== 【#16】摇一摇放烟花 =====
    private ShakeDetector shakeDetector;
    // ===== 台词朗读：本地离线神经网络优先，自动降级系统引擎 =====
    private SpeechEngine speech;
    /** 离线语音识别（模型按需下载，不用时为 null） */
    private OfflineAsr asr;
    // ===== 照片回忆墙 / 星空许愿 =====
    private PhotoWall photoWall;
    private WishSkyView wishSky;
    private androidx.activity.result.ActivityResultLauncher<android.content.Intent> photoWallLauncher;
    private androidx.activity.result.ActivityResultLauncher<android.content.Intent> voiceLauncher;
    /** 等麦克风权限到手后再发起的语音识别意图（权限未授予时暂存） */
    private Intent pendingVoiceIntent;
    /** 麦克风权限拿到后要执行的离线识别动作（下载确认或直接开听） */
    private Runnable pendingVoiceAction;
    static final String KEY_WEATHER = "weather_mode";
    /** 【新增】壁纸设置说明是否已看过（看过之后菜单点击直接跳预览页） */
    private static final String KEY_WALLPAPER_GUIDED = "wallpaper_guided";
    private static final String KEY_MOON = "moon_enabled";
    private static final String KEY_CONSTELLATION = "constellation_enabled";
    private static final String KEY_LANTERN_MODE = "lantern_mode";

    private SkyView starView;
    // v1.2.0 夜空/律动状态
    private boolean lanternTapMode;   // 孔明灯模式：点击放灯
    private boolean rhythmOn;         // 音乐律动
    private ActivityResultLauncher<String> micPermissionLauncher;
    // 星空后台零绘制：onPause 时停掉无限循环动画避免后台空耗 CPU；
    // 记录暂停时是否可见，回前台仅恢复原可见的那份，
    // 保持“退出浪漫模式后星空保持隐藏”的既有表现不变
    private boolean starsVisibleOnPause = true;
    private View loadingOverlay;
    private boolean fireworkReady = false;
    private long lastNotReadyToast = 0;
    // 统一的自定义文字存储 Key，和壁纸服务共用
    private static final String KEY_CUSTOM_FIREWORK_TEXT = "custom_firework_text";
    // 标记是否已经检测过分享码，避免重复弹窗
    private boolean hasCheckedShareCode = false;

        // ===== 视频导出 =====
    private ActivityResultLauncher<Intent> projectionPermissionLauncher;
    private boolean isRecording = false;
    /** 菜单上次滚动到的位置，下次打开接着看 */
    private int lastMenuScrollY = 0;
    private String pendingExportMessage = "遇见你真好";
    private static final long RECORD_DURATION_MS = 12000; // 自动录制 12 秒
    // 【修复】录制中悬浮“停止录制”按钮：随时可控，演出中途退出也能手动停
    private Button stopRecordBtn;
    // 【UI 优化】浪漫演出的台词进度指示（第 N / M 句）
    private TextView stageProgressTv;

    private final BroadcastReceiver recordStoppedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(android.content.Context context, Intent intent) {
            isRecording = false;
            hideStopRecordButton(); // 【修复】录制结束移除悬浮停止按钮
            if (isFinishing() || isDestroyed()) return;
            if (ScreenRecorderService.ACTION_FAILED.equals(intent.getAction())) {
                String err = intent.getStringExtra(ScreenRecorderService.EXTRA_ERROR);
                new UiGuard.SafeBuilder(MainActivity.this)
                        .setTitle("录制失败")
                        .setMessage(err)
                        .setPositiveButton("知道了", null)
                        .show();
                return;
            }
            String path = intent.getStringExtra(ScreenRecorderService.EXTRA_OUTPUT_PATH);
            String galleryUri = intent.getStringExtra(ScreenRecorderService.EXTRA_OUTPUT_URI);
            if (path != null) {
                new UiGuard.SafeBuilder(MainActivity.this)
                        .setTitle("录制完成")
                        .setMessage(galleryUri != null
                                ? "视频已保存到相册：Movies/RomanticFirework"
                                : "视频已保存：" + path)
                        .setPositiveButton("分享", (d, w) -> shareVideo(path, galleryUri))
                        .setNegativeButton("关闭", null)
                        .show();
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        StartupTracer tracer = StartupTracer.start();
        long onCreateStart = tracer.nowNanos();
        super.onCreate(savedInstanceState);
        tracer.mark("super.onCreate", tracer.elapsedMs(onCreateStart));

        long t0 = tracer.nowNanos();
        MMKV.initialize(this);
        tracer.mark("MMKV.initialize", tracer.elapsedMs(t0));

        // 【修复启动点击不放烟花】GlobalFirework 依赖 onActivityResumed 回调把全屏
        // FireworkView 挂载到当前 Activity，bloom() 才能渲染。冷启动时该回调先于
        // 首帧派发，init 必须赶在 onResume 之前完成注册；此前放到 rootLayout.post()
        // （首帧后）执行，本页 resume 事件已错过，currentViewRef 恒为空，
        // 点击屏幕 bloom() 静默无效，切后台再回来才恢复。
        // SoundPool.load 本身异步加载，放回 onCreate 不会阻塞启动。
        t0 = tracer.nowNanos();
        try {
            GlobalFirework.getInstance().init(getApplication(), R.raw.firework);
        } catch (Exception e) {
            e.printStackTrace(); // 引擎初始化异常不拖垮启动，首帧后再兜底重试
        }
        tracer.mark("GlobalFirework.init(烟花引擎)", tracer.elapsedMs(t0));
        // 音效与触感：读持久化开关并同步给库（必须在库的 init 之后，否则设置会被覆盖）
        SoundFx.init(this);

        EdgeToEdge.enable(this);
        customFireworkText = MMKV.defaultMMKV().decodeString(KEY_CUSTOM_FIREWORK_TEXT, "新年快乐");
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);

        t0 = tracer.nowNanos();
        setContentView(R.layout.activity_main);
        tracer.mark("setContentView", tracer.elapsedMs(t0));

        rootLayout = findViewById(R.id.root_layout);
        floatingContainer = findViewById(R.id.fl_floating_container);
        particleTextView = findViewById(R.id.particle_text_view);
        btnRomantic = findViewById(R.id.btn_romantic);
        btnAutoPlay = findViewById(R.id.btn_auto_play);
        btnMore = findViewById(R.id.btn_more); // 新增更多按钮
        llBottomBar = findViewById(R.id.ll_bottom_bar);

        starView = findViewById(R.id.star_view);
        loadingOverlay = findViewById(R.id.loading_overlay);
        applySkySettings();
        lanternTapMode = MMKV.defaultMMKV().decodeBool(KEY_LANTERN_MODE, false);
        rocketMode = MMKV.defaultMMKV().decodeBool(KEY_ROCKET_MODE, false);
        bgmUri = MMKV.defaultMMKV().decodeString(KEY_BGM_URI, null);
        bgmVolume = MMKV.defaultMMKV().decodeFloat(KEY_BGM_VOLUME, 0.7f);
        currentTheme = MMKV.defaultMMKV().decodeInt(KEY_THEME, ThemeManager.THEME_DEFAULT);
        starView.startSky();

        // 【#17】主题恢复：背景/天气由主题接管（用户无自选背景时）
        if (MMKV.defaultMMKV().decodeInt(KEY_BG_TYPE, 0) == 0
                && currentTheme != ThemeManager.THEME_DEFAULT) {
            rootLayout.setBackgroundColor(ThemeManager.bgColor(currentTheme));
            starView.setWeather(ThemeManager.weather(currentTheme));
        } else {
            restoreBackground();
        }
        handleRomanticShortcut(getIntent()); // 【#19】小组件一键浪漫
        handleAnniversaryReminder(getIntent()); // 【新增】纪念日提醒通知直达
        // 【修复#24】名字冷启动恢复：小组件一键浪漫不经设置弹窗，不恢复则名字功能失效
        herName = MMKV.defaultMMKV().decodeString(KEY_HER_NAME, "");
        myName = MMKV.defaultMMKV().decodeString(KEY_MY_NAME, "");
        isPureMode = MMKV.defaultMMKV().decodeBool(KEY_PURE_MODE, false);
        if (isPureMode) {
            llBottomBar.setVisibility(View.GONE);
            llBottomBar.setAlpha(0f);
        }

        ViewCompat.setOnApplyWindowInsetsListener(rootLayout, (v, insets) -> {
            var systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // 首帧后兜底：幂等重试引擎初始化（onCreate 已成功时为库内空操作），
        // 随后隐藏加载遮罩。无论引擎状态如何都必须置 ready 并撤掉遮罩，
        // 避免极端异常时遮罩永久挡住屏幕、触摸彻底失效。
        rootLayout.post(() -> {
            long t = tracer.nowNanos();
            try {
                GlobalFirework.getInstance().init(getApplication(), R.raw.firework);
            } catch (Exception e) {
                e.printStackTrace();
            }
            tracer.mark("GlobalFirework.init(兑底)", tracer.elapsedMs(t));
            fireworkReady = true;
            // 初始化完成后应用余辉设置（v1.3.0）
            applyAfterglow(MMKV.defaultMMKV().decodeBool(KEY_AFTERGLOW, true));
            // 首次启动玩法引导（一次性）；小组件一键浪漫时演出已开场，不弹窗打断
            mainHandler.postDelayed(() -> {
                if (!isRomanticMode) dialogs.showGuide(false);
            }, 1200);
            hideLoadingOverlay();
        });
        initFireworkConfigs();

        // 【UI 优化】加载遮罩延迟 400ms 再决定是否显示：引擎通常在首帧前后就已就绪，
        // 立刻显示再淡出会造成一次明显的"黑块闪烁"。超时仍不就绪才真正露出来。
        mainHandler.postDelayed(() -> {
            if (!fireworkReady && loadingOverlay != null && !isFinishing()) {
                loadingOverlay.setAlpha(0f);
                loadingOverlay.setVisibility(View.VISIBLE);
                loadingOverlay.animate().alpha(1f).setDuration(150).start();
            }
        }, 400);

        // 启动性能检测：统计首帧与首屏前 30 帧的掉帧情况
        tracer.trackFirstFrame(this);

        // 触摸事件：单击烟花/放灯 + 长按分级蓄力（0.5s/1s/1.5s → 中/大/超大全屏）
        rootLayout.setOnTouchListener((v, event) -> {
            // 许愿模式：把触摸让给星星层，否则这里 return true 会吞掉所有点击
            if (wishSky != null) return false;
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    downX = event.getX();
                    downY = event.getY();
                    isLongPressTriggered = false;
                    startCharging(downX, downY);
                    break;
                case MotionEvent.ACTION_MOVE:
                    float moveX = Math.abs(event.getX() - downX);
                    float moveY = Math.abs(event.getY() - downY);
                    if (moveX > TOUCH_SLOP || moveY > TOUCH_SLOP) {
                        cancelCharging();
                    }
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    int finalCharge = chargeLevel;
                    cancelCharging();
                    if (event.getAction() == MotionEvent.ACTION_UP && finalCharge == 0
                            && !isLongPressTriggered) {
                        float moveXX = Math.abs(event.getX() - downX);
                        float moveYY = Math.abs(event.getY() - downY);
                        if (moveXX <= TOUCH_SLOP && moveYY <= TOUCH_SLOP) {
                            if (isRomanticMode) {
                                // 【#23】演出中她点击屏幕：放一枚小爱心，可参与不扰流程
                                GlobalFirework.getInstance().bloomSilently(event.getX(), event.getY(),
                                        new FireworkConfig.Builder()
                                                .shape(FireworkConfig.Shape.HEART)
                                                .colors(0xFFFFB6C1, Color.WHITE)
                                                .count(scaledCount(45)).range(0.45f).trail(1.0f).build());
                                return true;
                            }
                            if (!fireworkReady) {
                                long now = System.currentTimeMillis();
                                if (now - lastNotReadyToast > 1500) {
                                    lastNotReadyToast = now;
                                    Toaster.showShort("烟花引擎加载中，请稍候...");
                                }
                                return true;
                            }
                            if (lanternTapMode) {
                                // 孔明灯模式：点击放灯（库 SkyView 渲染，暖光缓缓升空）
                                starView.releaseLantern(event.getX(), event.getY());
                            } else {
                                int index = random.nextInt(randomFireworkPool.length);
                                FireworkConfig cfg = randomFireworkPool[index];
                                if (rocketMode) cfg = asRocket(cfg);
                                bloomWithSound(event.getX(), event.getY(), cfg);
                            }
                        }
                    } else if (event.getAction() == MotionEvent.ACTION_UP && finalCharge > 0) {
                        fireChargedBurst(finalCharge, downX, downY);
                    }
                    break;
            }
            return true;
        });

        initLaunchers();
        setupBackGuard();

        // 【架构】台词朗读：本地离线音源优先，不可用时自动降级系统引擎
        TtsModelStore.migrateLegacy(this);   // 旧版单音色目录 → 按音色分目录，老用户不用重下
        // 后台拉一次模型源清单：网盘直链会失效，有了它换链接不用发版
        PackSources.refreshAsync(this);
        speech = new SpeechEngine(this);
        // 朗读时压低 BGM：两者共用 STREAM_MUSIC，不闪避的话人声会被音乐盖住
        speech.setDucking(this::duckBgm);

        // 启动自检：系统读不了中文才引导下载离线音色（延后 2.5s，别和入场动画抢注意力）
        mainHandler.postDelayed(this::maybePromptTtsVoice, 2500);

        // 【架构】演出调度器：把散落的 postDelayed 收口，演出因此可被跳过/暂停
        showDirector = new RomanticShowDirector(mainHandler, () -> isRomanticMode);
        showDirector.setPauseListener(paused -> {
            setMusicPaused(paused);
            if (paused && speech != null) {
                speech.stop(); // 朗读停在当前句，恢复后由下一句自然接续
            }
        });

        // 【架构】弹窗层：当前值由 Activity 提供，选中结果回传给 Activity 应用
        dialogs = new RomanticDialogs(this, new RomanticDialogs.Callbacks() {
            @Override
            public void onWeatherPicked(int mode) {
                MMKV.defaultMMKV().encode(KEY_WEATHER, mode);
                starView.setWeather(mode);
            }

            @Override
            public void onThemePicked(int theme) {
                applyTheme(theme);
            }

            @Override
            public void onPatternPicked(int pattern) {
                MMKV.defaultMMKV().encode(KEY_AUTO_PATTERN, pattern);
                if (isAutoPlaying) {
                    GlobalFirework.getInstance().stopAutoShow();
                    GlobalFirework.getInstance().startAutoShow(pattern, 700);
                }
            }

            @Override
            public void onTemplatePicked(int index, boolean playNow) {
                applyShowTemplate(ShowTemplate.ALL[index], playNow);
            }

            @Override
            public void onSpeechModePicked(int mode) {
                if (speech == null) return;
                speech.stop();
                // 选了"只用本地"却没下载音色：直接引导下载，别让演出静默无声
                if (mode == SpeechEngine.MODE_LOCAL && !speech.isLocalModelReady()) {
                    showTtsDownloadOffer();
                    return;
                }
                // 自动/系统档：已知系统读不了中文就没必要试听了，直接给方案
                if (mode != SpeechEngine.MODE_OFF && !speech.isLocalModelReady()) {
                    int cached = SpeechEngine.cachedSystemChinese();
                    if (cached == 0) {
                        showTtsDownloadOffer();
                        return;
                    }
                    if (cached < 0) {
                        // 还没结论：先照常试听，顺手把检测做了，下次启动就不用猜
                        speech.probeSystemChinese(ok -> {
                            if (!ok && !isFinishing() && !isDestroyed()) {
                                mainHandler.postDelayed(MainActivity.this::showTtsDownloadOffer, 1200);
                            }
                        });
                    }
                }
                speech.warmUp();
                // 选完立刻听一句，音色对比只靠文字描述说不清
                if (mode != SpeechEngine.MODE_OFF) {
                    mainHandler.postDelayed(() -> speech.speak("遇见你真好"), 700);
                }
            }

            /** 下载当前音色 / 清理当前音色 */
            @Override
            public void onTtsModelManage(boolean download) {
                if (download) startTtsDownload(TtsVoice.current());
                else confirmDeleteTtsModel();
            }

            @Override
            public void onTtsVoiceDownload(TtsVoice v) {
                startTtsDownload(v);
            }

            @Override
            public void onTtsVoicePicked(TtsVoice v) {
                applyTtsVoice(v);
            }

            @Override
            public void onTtsPitchPicked(int idx) {
                if (speech == null) return;
                speech.stop();
                TtsVoice.setPitch(idx);
                Toaster.showShort("变声：" + TtsVoice.pitchName());
                mainHandler.postDelayed(() -> speech.speak("遇见你真好"), 400);
            }

            @Override
            public void onTtsSpeakerShuffle() {
                if (speech == null) return;
                speech.stop();
                // 多说话人包：换 sid 就是换一个人念，模型不用重新加载
                int n = Math.max(1, speech.numSpeakers());
                int old = TtsVoice.currentSpeaker();
                int next = old;
                if (n > 1) {
                    while (next == old) next = (int) (Math.random() * n);
                }
                TtsVoice.setSpeaker(next);
                Toaster.showShort("换到声音 #" + next);
                mainHandler.postDelayed(() -> speech.speak("遇见你真好"), 400);
            }

            @Override
            public int currentWeather() {
                return starView != null ? starView.getWeather() : SkyView.WEATHER_NONE;
            }

            @Override
            public void onBgColorPicked(int color) {
                MMKV.defaultMMKV().encode(KEY_BG_TYPE, 0);
                MMKV.defaultMMKV().encode(KEY_BG_COLOR, color);
                rootLayout.setBackground(null);
                rootLayout.setBackgroundColor(color);
            }

            @Override
            public void onCustomTextConfirmed(String text) {
                customFireworkText = text;
                MMKV.defaultMMKV().encode(KEY_CUSTOM_FIREWORK_TEXT, text);
                initFireworkConfigs(); // 重新生成烟花配置池
                // 输入完成后，在屏幕中心发射一枚预览
                float centerX = rootLayout.getWidth() / 2f;
                float centerY = rootLayout.getHeight() / 2f - 100;
                launchTextFirework(centerX, centerY, customFireworkText);
                Toaster.showShort("已更新烟花文字：" + text);
            }

            @Override
            public String currentCustomText() {
                return customFireworkText;
            }

            @Override
            public int currentTheme() {
                return currentTheme;
            }

            @Override
            public int currentPattern() {
                return MMKV.defaultMMKV().decodeInt(KEY_AUTO_PATTERN, GlobalFirework.PATTERN_WAVE);
            }

            @Override
            public void onSoundFxChanged() {
                // 开关与音量已经写进 MMKV 并同步给库，这里没有额外状态要联动
            }

            @Override
            public void onSignatureReady(android.graphics.Bitmap bmp, boolean save) {
                bloomSignature(bmp, save);
            }

            @Override
            public String currentLines() {
                String s = android.text.TextUtils.join("\n", romanticLines);
                if (!s.trim().isEmpty()) return s;
                // 没打开过浪漫设置时 romanticLines 是空列表，直接返回会让"存成剧本"
                // 永远提示"当前还没有台词"，看起来像功能坏了。逐级回退：
                // 上次演出用过的台词 → 当前主题的默认台词。
                String last = MMKV.defaultMMKV().decodeString("last_romantic_lines", "");
                if (last != null && !last.trim().isEmpty()) return last;
                return ThemeManager.romanticLines(currentTheme);
            }

            @Override
            public void onScriptPlay(String lines) {
                if (isRomanticMode) stopRomanticMode();
                postRomanticCue(() -> {
                    if (isRomanticMode || !fireworkReady) return;
                    startRomanticMode(lines, countdownSeconds);
                }, 400);
            }

            @Override
            public void onScheduleAdded(long timeMillis, String name, String lines) {
                if (ScheduledShow.add(MainActivity.this, timeMillis, name, lines)) {
                    Toaster.showShort("已定好：" + ScheduledShow.describe(timeMillis));
                } else {
                    Toaster.showShort("这个时间已经过去了，或者定时已满（最多 "
                            + ScheduledShow.MAX + " 条）");
                }
            }

            @Override
            public void onScheduleRemoved(long id) {
                ScheduledShow.remove(MainActivity.this, id);
            }

            @Override
            public void onFontPicked(int index) {
                // 池里的文字烟花要用新字体重新栅格化
                initFireworkConfigs();
                mainHandler.postDelayed(() -> launchTextFirework(
                        rootLayout.getWidth() / 2f, rootLayout.getHeight() * 0.40f,
                        customFireworkText), 200);
            }

            @Override
            public long onTuningChanged(boolean preview) {
                // 调参是乘法系数，必须整池重建，不能就地叠加
                initFireworkConfigs();
                if (preview && randomFireworkPool.length > 0) {
                    FireworkConfig cfg = randomFireworkPool[random.nextInt(randomFireworkPool.length)];
                    // 库的粒子寿命：每帧衰减 (0.006~0.016)/duration，60fps 下
                    // 满寿命约 duration × (1.6~2.8) 秒，据此告诉弹窗让位多久
                    long ms = 150L + Math.round(cfg.duration * 2600f) + 700L;
                    mainHandler.postDelayed(() -> bloomWithSound(rootLayout.getWidth() / 2f,
                            rootLayout.getHeight() * 0.45f, cfg), 150);
                    return ms;
                }
                return 0;
            }

            @Override
            public void onPhotoWallMode(int mode, java.util.List<android.net.Uri> uris) {
                if (photoWall == null) photoWall = new PhotoWall(MainActivity.this, floatingContainer);
                if (mode == 0) {
                    photoWall.showFalling(uris, 14000, 700);
                    Toaster.showShort("照片开始从夜空飘落 🍂");
                } else {
                    photoWall.showHeart(uris, 9000);
                    Toaster.showShort("照片正聚成一颗心 💖");
                }
            }

            @Override
            public void onWishAdded(String text, float x, float y) {
                if (WishStore.add(text, x, y)) {
                    if (wishSky != null) wishSky.refresh();
                    Toaster.showShort("🌟 愿望已挂上星空");
                } else {
                    Toaster.showShort("星空满了（最多 " + WishStore.MAX + " 个愿望）");
                }
            }
        });

        // 按钮点击事件
        // 走 UiGuard：600ms 内的连点丢掉，避免"开始演出/随机播放"被触发两次
        UiGuard.onClick(btnRomantic, this::showRomanticDialog);
        UiGuard.onClick(btnAutoPlay, this::toggleAutoPlay);
        UiGuard.onClick(btnMore, () -> showMoreMenu(btnMore));
    }

    /** 撤掉加载遮罩：引擎就绪或异常兜底都必须执行，避免遮罩永久挡住屏幕导致触摸失效 */
    private void hideLoadingOverlay() {
        if (loadingOverlay == null || loadingOverlay.getVisibility() != View.VISIBLE) return;
        loadingOverlay.animate().alpha(0f).setDuration(200)
                .withEndAction(() -> loadingOverlay.setVisibility(View.GONE)).start();
    }

    private void initFireworkConfigs() {
        romanticHeartConfig = new FireworkConfig.Builder()
                .shape(FireworkConfig.Shape.HEART)
                .colors(0xFFFF69B4, Color.RED)
                .count(scaledCount(80))
                .range(0.55f)
                .trail(1.0f)
                .duration(1.6f)
                .build();

        // ===== 【#17】节日主题：切换时替换为主题专属池 =====
        if (currentTheme != ThemeManager.THEME_DEFAULT) {
            FireworkConfig[] themed = ThemeManager.fireworkPool(currentTheme, scaledCount(150), customFireworkText);
            String[] emojis = ThemeManager.poolEmojis(currentTheme);
            if (themed != null) {
                int screenW = getResources().getDisplayMetrics().widthPixels;
                // poolEmojis 在未知主题上返回 null，且两个数组长度可能不一致，取安全交集
                int injectCount = emojis == null ? 0 : Math.min(themed.length, emojis.length);
                for (int i = 0; i < injectCount; i++) {
                    ThemeManager.withEmoji(this, themed[i], emojis[i]);
                }
                // 【溢出修复#24】主题文字烟花（福/生日快乐/Merry Xmas）按屏宽自适应，窄屏不溢出
                for (FireworkConfig cfg : themed) {
                    if (cfg.shape == FireworkConfig.Shape.TEXT) {
                        cfg.textSize = fitTextSizePx(cfg.text, cfg.textSize, screenW);
                    }
                }
                allPoolConfigs = themed;
                String[][] allNames = ThemeManager.THEME_POOL_NAMES;
                allPoolNames = (currentTheme >= 0 && currentTheme < allNames.length)
                        ? allNames[currentTheme] : new String[0];
                applyStyleFilter();
                applyFireworkTuning();
                return;
            }
        }

        // ===== 样式扩展包（#4新）：原有5样 + 玫瑰/戒指/钻石头图形 + 金雨/彩虹圈主题 =====
        allPoolConfigs = new FireworkConfig[]{
                // 经典圆形
                new FireworkConfig.Builder()
                        .colors(hueColorApp(random.nextInt(360)))
                        .count(scaledCount(70))
                        .range(1.0f)
                        .build(),
                // 爱心
                new FireworkConfig.Builder()
                        .shape(FireworkConfig.Shape.HEART)
                        .colors(0xFFFF69B4, 0xFFFF1493)
                        .count(scaledCount(80))
                        .range(0.9f)
                        .build(),
                // 五角星
                new FireworkConfig.Builder()
                        .shape(FireworkConfig.Shape.STAR)
                        .colors(0xFFFFD700, Color.WHITE)
                        .count(scaledCount(90))
                        .range(1.1f)
                        .build(),
                // 火箭升空圆形
                new FireworkConfig.Builder()
                        .colors(hueColorApp(random.nextInt(360)))
                        .count(scaledCount(80))
                        .range(1.1f)
                        .trail(1.6f)
                        .rocket(true)
                        .build(),
                // 自定义文字（沿用用户设置；换过字体时改走位图渲染，见 buildTextFireworkConfig）
                buildTextFireworkConfig(customFireworkText,
                        getResources().getDisplayMetrics().widthPixels),
                // 玫瑰 🌹
                new FireworkConfig.Builder()
                        .shape(FireworkConfig.Shape.CUSTOM)
                        .shapeBitmap(emojiShapeBitmap("🌹"))
                        .colors(0xFFFF2D55, 0xFFFF69B4)
                        .count(scaledCount(150)).range(1.3f).trail(1.1f)
                        .build(),
                // 戒指 💍（求婚专属）
                new FireworkConfig.Builder()
                        .shape(FireworkConfig.Shape.CUSTOM)
                        .shapeBitmap(emojiShapeBitmap("💍"))
                        .colors(0xFFFFD700, Color.WHITE)
                        .count(scaledCount(150)).range(1.3f).trail(1.1f)
                        .build(),
                // 钻石 💎
                new FireworkConfig.Builder()
                        .shape(FireworkConfig.Shape.CUSTOM)
                        .shapeBitmap(emojiShapeBitmap("💎"))
                        .colors(0xFF7FD4FF, 0xFFB6E5FF)
                        .count(scaledCount(150)).range(1.3f).trail(1.1f)
                        .build(),
                // 金色雨
                new FireworkConfig.Builder()
                        .colors(0xFFFFD700, 0xFFE8C24A, Color.WHITE)
                        .count(scaledCount(200)).range(1.4f).trail(0.5f)
                        .build(),
                // 彩虹圈（不传颜色随机全谱）
                new FireworkConfig.Builder()
                        .count(scaledCount(180)).range(1.2f).trail(1.2f)
                        .build()
        };
        allPoolNames = new String[]{"圆形", "爱心", "五角星", "火箭升空", "文字烟花",
                "玫瑰🌹", "戒指💍", "钻石💎", "金色雨", "彩虹圈"};
        applyStyleFilter();
        applyFireworkTuning();
    }

    /**
     * 调参系数只在池重建后作用一次。
     *
     * <p>系数是乘法，重复作用会让粒子数指数膨胀——所以调用点严格收在这一个方法里，
     * 任何"临时改一下参数"的场景都得另走 {@link FireworkTuning#apply}。
     */
    private void applyFireworkTuning() {
        FireworkTuning.applyAll(allPoolConfigs);
        if (romanticHeartConfig != null) FireworkTuning.apply(romanticHeartConfig);
    }

    /**
     * 文字烟花配置：默认字体走库的 TEXT 栅格化（快、省内存）；
     * 换过字体就必须自己把字画成位图，否则字形不会变。
     */
    private FireworkConfig buildTextFireworkConfig(String text, int screenW) {
        int font = FontKit.current();
        if (FontKit.needsBitmap(font)) {
            android.graphics.Bitmap bmp = FontKit.render(this, text, font, 420);
            if (bmp != null) {
                return FontKit.config(bmp, 0f);
            }
        }
        return new FireworkConfig.Builder()
                .shape(FireworkConfig.Shape.TEXT)
                .text(text)
                .colors(Color.RED, Color.YELLOW)
                .range(1.2f)
                .count(scaledCount(160))
                .textSize(fitTextSizePx(text, 100f, screenW))
                .build();
    }

    // ===== 【图形样式开关】：每个烟花样式独立开关，默认全部开启 =====
    /** 完整样式池（含名字，与 allPoolConfigs 同索引） */
    private FireworkConfig[] allPoolConfigs = new FireworkConfig[0];
    private String[] allPoolNames = new String[0];

    /** 样式开关存储 key */
    private static String styleKey(String name) {
        return "style_enabled_" + name;
    }

    private boolean isStyleEnabled(String name) {
        return MMKV.defaultMMKV().decodeBool(styleKey(name), true);
    }

    /** 按开关过滤出生效池：点击/随机播放只从已开启样式中随机 */
    private void applyStyleFilter() {
        if (allPoolConfigs.length == 0) {
            randomFireworkPool = new FireworkConfig[0];
            return;
        }
        java.util.List<FireworkConfig> filtered = new java.util.ArrayList<>();
        for (int i = 0; i < allPoolConfigs.length; i++) {
            if (i >= allPoolNames.length || isStyleEnabled(allPoolNames[i])) {
                filtered.add(allPoolConfigs[i]);
            }
        }
        if (filtered.isEmpty()) {
            // 兑底：全部关闭时保留第一个样式，避免点不出烟花
            filtered.add(allPoolConfigs[0]);
        }
        randomFireworkPool = filtered.toArray(new FireworkConfig[0]);
    }

    /**
     * 样式开关对话框（可滚动复选框，横屏适配）。
     * 交互安全设计：
     * - 勾选只改临时状态，点【确定】才写入 MMKV 持久化并生效；点【取消】完全丢弃；
     * - 不允许取消最后一个样式（至少保留一种，防止点不出烟花）；
     * - 确定后 Toast 明确反馈"已保存"，避免不确定是否生效。
     */
    private void showStyleSwitchDialog() {
        if (allPoolNames.length == 0) initFireworkConfigs();
        final String[] names = allPoolNames;
        // 临时状态（从 MMKV 加载），确定时才整体写回
        final boolean[] pending = new boolean[names.length];
        int enabledCount = 0;
        for (int i = 0; i < names.length; i++) {
            pending[i] = isStyleEnabled(names[i]);
            if (pending[i]) enabledCount++;
        }

        android.widget.ScrollView scroll = new android.widget.ScrollView(this);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(56, 16, 56, 16);
        final com.google.android.material.checkbox.MaterialCheckBox[] checks =
                new com.google.android.material.checkbox.MaterialCheckBox[names.length];
        for (int i = 0; i < names.length; i++) {
            final int idx = i;
            com.google.android.material.checkbox.MaterialCheckBox cb =
                    new com.google.android.material.checkbox.MaterialCheckBox(this);
            cb.setText(names[i]);
            cb.setTextColor(Color.WHITE);
            cb.setTextSize(14f);
            cb.setChecked(pending[i]);
            cb.setOnCheckedChangeListener((btn, isChecked) -> {
                if (isChecked) {
                    pending[idx] = true;
                    return;
                }
                // 【确认安全】尝试取消：若这是唯一启用的样式则阻止并提示
                int on = 0;
                for (boolean b : pending) if (b) on++;
                if (on <= 1) {
                    btn.setChecked(true); // 阻止取消，恢复勾选
                    Toaster.showShort("至少保留一种样式（当前仅剩：" + names[idx] + "）");
                } else {
                    pending[idx] = false;
                }
            });
            checks[i] = cb;
            box.addView(cb, new LinearLayout.LayoutParams(-1, -2));
        }
        scroll.addView(box);
        scroll.setLayoutParams(new android.widget.ScrollView.LayoutParams(-1, Math.min(
                (int) (getDisplayHeightPx() * 0.42f), 480)));

        // 【修复】横屏下 AlertDialog 系统按钮栏会被挤压为 0 高度不可见（同浪漫设置弹窗的老病），
        // 不再依赖 setPositiveButton，改在列表底部放显式的保存/取消按钮
        LinearLayout btnRow = new LinearLayout(this);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        com.google.android.material.button.MaterialButton saveBtn =
                new com.google.android.material.button.MaterialButton(this);
        saveBtn.setText("保存");
        saveBtn.setTextColor(Color.WHITE);
        saveBtn.setBackgroundTintList(ColorStateList.valueOf(0xFFE91E63));
        com.google.android.material.button.MaterialButton cancelBtn =
                new com.google.android.material.button.MaterialButton(this);
        cancelBtn.setText("取消");
        cancelBtn.setTextColor(Color.WHITE);
        cancelBtn.setStrokeColor(ColorStateList.valueOf(0xFFFF69B4)); // 边框按钮样式（setStyle 非公开 API 改用描边属性）
        LinearLayout.LayoutParams saveLp = new LinearLayout.LayoutParams(0, -2, 1f);
        LinearLayout.LayoutParams cancelLp = new LinearLayout.LayoutParams(0, -2, 1f);
        cancelLp.leftMargin = 24;
        btnRow.addView(saveBtn, saveLp);
        btnRow.addView(cancelBtn, cancelLp);
        box.addView(btnRow, new LinearLayout.LayoutParams(-1, -2));

        final androidx.appcompat.app.AlertDialog dialog =
                new UiGuard.SafeBuilder(this)
                        .setTitle("烟花样式开关（启用 " + enabledCount + "/" + names.length + "）")
                        .setMessage("勾选的样式参与点击与随机播放")
                        .setView(scroll)
                        .show();

        saveBtn.setOnClickListener(v -> {
            // 整体写回 MMKV 持久化
            for (int i = 0; i < names.length; i++) {
                MMKV.defaultMMKV().encode(styleKey(names[i]), pending[i]);
            }
            applyStyleFilter();
            int on = 0;
            StringBuilder onNames = new StringBuilder();
            for (int i = 0; i < names.length; i++) {
                if (pending[i]) {
                    on++;
                    if (onNames.length() > 0) onNames.append("、");
                    onNames.append(names[i]);
                }
            }
            Toaster.showShort("已保存：" + on + " 种样式\n" + onNames);
            dialog.dismiss();
        });
        cancelBtn.setOnClickListener(v -> {
            Toaster.showShort("已取消，未保存修改");
            dialog.dismiss();
        });
    }

    private int getDisplayHeightPx() {
        android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
        return Math.max(dm.heightPixels, 600);
    }

    /** 复用库 v1.2.0 SkyView：按设备档位 + 用户设置下发夜空参数 */
    private void applySkySettings() {
        DevicePerf perf = DevicePerf.get(this);
        MMKV mmkv = MMKV.defaultMMKV();
        starView.setStarCount(perf.starCount);
        starView.setStarAntiAlias(perf.starAntiAlias);
        starView.setFrameIntervalMs(perf.starFrameIntervalMs);
        starView.setAutoMeteorIntervalMs(perf.meteorBaseMs, perf.meteorRandomMs);
        starView.setAutoLanternIntervalMs(
                perf.tier() == DevicePerf.Tier.LOW ? 0 : (perf.tier() == DevicePerf.Tier.MEDIUM ? 60000 : 40000),
                30000);
        starView.setWeather(mmkv.decodeInt(KEY_WEATHER, 0));
        starView.setMoonEnabled(mmkv.decodeBool(KEY_MOON, true));
        starView.setConstellationEnabled(mmkv.decodeBool(KEY_CONSTELLATION, true));
        starView.setTimeTintAuto(mmkv.decodeBool(KEY_MOON, true)); // 时段叠色随月亮开关
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mediaPlayer != null && mediaPlayer.isPlaying()) {
            mediaPlayer.pause();
        }
        // 星空动画切后台即停（用户看不到，帧回调不再空耗）；返回时按原状态恢复
        if (starView != null) {
            starsVisibleOnPause = starView.getVisibility() == View.VISIBLE;
            if (starsVisibleOnPause) {
                starView.stopSky();
            }
        }
        // 律动切后台即停（麦克风不再监听）
        if (rhythmOn) RhythmFirework.getInstance().stop();
        // 【#16】摇一摇切后台注销传感器
        if (shakeDetector != null) shakeDetector.unregister();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (isRomanticMode && mediaPlayer != null && !mediaPlayer.isPlaying()) {
            mediaPlayer.start();
        }
        if (starView != null && starsVisibleOnPause) {
            starView.startSky();
        }
        // 前台恢复：如开着律动且权限仍在，重新监听
        if (rhythmOn && checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            RhythmFirework.getInstance().start(this);
        }
        // 【#16】摇一摇前台注册
        if (shakeDetector == null) {
            shakeDetector = new ShakeDetector(this, this::onShake);
        }
        shakeDetector.register();
    }

    /** 【#16】摇一摇：满屏连发烟花（浪漫模式中不触发，避免打断演出） */
    private void onShake() {
        if (isRomanticMode || !fireworkReady) return;
        int w = Math.max(1, rootLayout.getWidth());
        int h = Math.max(1, rootLayout.getHeight());
        FireworkView fv = GlobalFirework.getInstance().getFireworkView();
        if (fv == null) return;
        for (int i = 0; i < 8; i++) {
            final int fi = i;
            mainHandler.postDelayed(() -> {
                GlobalFirework.getInstance().bloomSilently(
                        w * (0.1f + random.nextFloat() * 0.8f),
                        h * (0.12f + random.nextFloat() * 0.45f),
                        new FireworkConfig.Builder()
                                .colors(hueColorApp(random.nextInt(360)), Color.WHITE)
                                .count(scaledCount(80)).range(1.0f + random.nextFloat() * 0.5f)
                                .trail(1.2f).build());
            }, fi * 110);
        }
        GlobalFirework.getInstance().playHeartbeatVibration();
        Toaster.showShort("摇出满屏烟花 🎆");
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleRomanticShortcut(intent);
        handleAnniversaryReminder(intent);
        handleScheduledShow(intent);
    }

    /**
     * 定时演出到点：从全屏通知进来直接开演。
     * 与纪念日一样走排队的 cue，等引擎就绪再开始。
     */
    private void handleScheduledShow(Intent intent) {
        if (intent == null) return;
        if (!ScheduledShow.ACTION.equals(intent.getAction())) return;
        String lines = intent.getStringExtra(ScheduledShow.EXTRA_LINES);
        String name = intent.getStringExtra(ScheduledShow.EXTRA_NAME);
        intent.removeExtra(ScheduledShow.EXTRA_LINES); // 转屏重建后不再重复开演
        final String useLines = (lines == null || lines.isEmpty())
                ? ThemeManager.romanticLines(currentTheme) : lines;
        postRomanticCue(() -> {
            if (isRomanticMode || !fireworkReady) return;
            // 到点那一刻先给一声铃音：手机可能刚从息屏唤醒，声音比画面先到
            SoundFx.playChime();
            Toaster.showShort("⏰ " + (name == null ? "时间到了" : name) + " · 为她放这场烟花");
            startRomanticMode(useLines, countdownSeconds);
        }, 900);
    }

    /** 【#19】桌面小组件一键浪漫入口 */
    private void handleRomanticShortcut(Intent intent) {
        if (intent == null) return;
        if (RomanticWidgetProvider.ACTION_ROMANTIC.equals(intent.getAction())) {
            postRomanticCue(() -> {
                if (!isRomanticMode && fireworkReady) {
                    startRomanticMode(ThemeManager.romanticLines(currentTheme), 3);
                }
            }, 800);
        }
    }

    /**
     * 【新增】纪念日提醒通知的落地点：从通知进来直接重演那一天的演出。
     * 演出要等引擎就绪，所以这里排队而不是立刻开演。
     */
    private void handleAnniversaryReminder(Intent intent) {
        if (intent == null) return;
        String lines = intent.getStringExtra(AnniversaryReceiver.EXTRA_LINES);
        if (lines == null || lines.isEmpty()) return;
        final String label = intent.getStringExtra(AnniversaryReceiver.EXTRA_TITLE);
        intent.removeExtra(AnniversaryReceiver.EXTRA_LINES); // 转屏重建后不再重演
        postRomanticCue(() -> {
            if (isRomanticMode || !fireworkReady) return;
            Toaster.showShort("重演：" + (label == null ? "纪念日" : label));
            startRomanticMode(lines, countdownSeconds);
        }, 900);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // 照片墙持有容器引用与排队任务，不收尾会在 Activity 销毁后继续往里加 View
        if (photoWall != null) photoWall.stop();
        closeWishSky();
        stopRomanticMode();
        stopMusic();
        isAutoPlaying = false;
        mainHandler.removeCallbacksAndMessages(null);
        petalPool.clear();
        if (rhythmOn) RhythmFirework.getInstance().stop();
        if (shakeDetector != null) shakeDetector.unregister();
        if (speech != null) speech.shutdown();
        if (asr != null) asr.release();   // 不释放会一直占着麦克风
        starView.stopSky();
        UiGuard.dismissAll();   // 还有弹窗开着就关掉，否则 Activity 会 leak window
        try { unregisterReceiver(recordStoppedReceiver); } catch (Exception ignored) {}
    }

    private void showSpeechPicker() {
        dialogs.showSpeechPicker();
    }

    private void showRomanticDialog() {
        if (isRomanticMode) {
            stopRomanticMode();
            return;
        }

        // ===== 弹窗重构：Material MD3 组件 XML 布局（dialog_romantic_settings.xml）=====
        // 告别代码手拼控件：TextInputLayout/Slider/MaterialButton/MaterialCheckBox 自动获得 MD3 样式
        View view = getLayoutInflater().inflate(R.layout.dialog_romantic_settings, null);
        final com.google.android.material.textfield.TextInputEditText msgInput =
                view.findViewById(R.id.et_lines);
        final com.google.android.material.textfield.TextInputEditText timeInput =
                view.findViewById(R.id.et_seconds);
        final com.google.android.material.slider.Slider volumeSlider =
                view.findViewById(R.id.slider_volume);
        final TextView volumeLabel = view.findViewById(R.id.tv_volume_label);
        final com.google.android.material.button.MaterialButton btnPickPreset =
                view.findViewById(R.id.btn_pick_preset);
        final com.google.android.material.button.MaterialButton bgmPick =
                view.findViewById(R.id.btn_pick_bgm);
        final com.google.android.material.checkbox.MaterialCheckBox recordFull =
                view.findViewById(R.id.cb_record_full);
        final com.google.android.material.button.MaterialButton btnGenerate =
                view.findViewById(R.id.btn_gen_code);
        final com.google.android.material.button.MaterialButton btnImport =
                view.findViewById(R.id.btn_import_code);
        final com.google.android.material.button.MaterialButton btnStart =
                view.findViewById(R.id.btn_start);

        // 【#26】默认台词升级：十句完整告白（用户指定的默认预设，写满更有仪式感）
        String preset = MMKV.defaultMMKV().decodeString("last_romantic_lines",
                "小美，遇见你真好\n往后余生，请多指教\n朝暮四季，我都在旁\n柴米烟火，和你共享\n风雨来时，我来抵挡\n平凡日常，慢慢珍藏\n所有温柔，尽数予你\n心有所属，便是远方\n不问喧嚣，只守身旁\n岁岁年年，不负相逢");
        msgInput.setText(preset);
        // 【#23】名字输入：她的名字贯穿全场，你的名字用于卡片落款
        final com.google.android.material.textfield.TextInputEditText herNameInput =
                view.findViewById(R.id.et_her_name);
        final com.google.android.material.textfield.TextInputEditText myNameInput =
                view.findViewById(R.id.et_my_name);
        herName = MMKV.defaultMMKV().decodeString(KEY_HER_NAME, "");
        myName = MMKV.defaultMMKV().decodeString(KEY_MY_NAME, "");
        herNameInput.setText(herName);
        myNameInput.setText(myName);
        timeInput.setText(String.valueOf(countdownSeconds));
        SliderSafe.setValue(volumeSlider, bgmVolume * 100f);
        volumeLabel.setText("背景音乐音量 " + (int) volumeSlider.getValue() + "%");
        volumeSlider.addOnChangeListener((slider, value, fromUser) ->
                volumeLabel.setText("背景音乐音量 " + (int) value + "%"));
        // 【新增】告白文案库：对着空台词框发呆是这个 App 最大的流失点，给成套现成文案
        btnPickPreset.setOnClickListener(v -> dialogs.showPresetPicker(msgInput));
        bgmPick.setText(bgmUri == null ? "🎵 背景音乐：内置浪漫曲（点击换歌）" : "🎵 背景音乐：已选自手机（点击重新选）");
        bgmPick.setOnClickListener(v -> {
            Intent it = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            it.addCategory(Intent.CATEGORY_OPENABLE);
            it.setType("audio/*");
            pickBgmLauncher.launch(it);
        });

        androidx.appcompat.app.AlertDialog dialog = new UiGuard.SafeBuilder(this)
                .setTitle("开启顶级浪漫模式")
                .setMessage("三幕剧：多句告白依次绽放 → 爱心花海 → 终章绽放，结尾有应答时刻")
                .setView(view)
                .show();

        btnStart.setOnClickListener(v -> {
            String message = msgInput.getText().toString().trim();
            String timeStr = timeInput.getText().toString().trim();
            int seconds = 3;
            try {
                seconds = Integer.parseInt(timeStr);
                seconds = Math.max(1, Math.min(60, seconds)); // 放宽到 60 秒（求婚长倒数）
            } catch (Exception e) {
                seconds = 3;
            }
            countdownSeconds = seconds;
            bgmVolume = Math.max(0f, volumeSlider.getValue() / 100f);
            MMKV.defaultMMKV().encode(KEY_BGM_VOLUME, bgmVolume);
            herName = herNameInput.getText().toString().trim();
            myName = myNameInput.getText().toString().trim();
            MMKV.defaultMMKV().encode(KEY_HER_NAME, herName);
            MMKV.defaultMMKV().encode(KEY_MY_NAME, myName);
            pendingRecordFull = recordFull.isChecked();
            dialog.dismiss();
            MMKV.defaultMMKV().encode("last_romantic_lines", message);
            if (pendingRecordFull && !isRecording) {
                // 先拿录屏授权，回调后再正式开始
                pendingExportMessage = message;
                MediaProjectionManager pm = (MediaProjectionManager)
                        getSystemService(Context.MEDIA_PROJECTION_SERVICE);
                projectionPermissionLauncher.launch(pm.createScreenCaptureIntent());
            } else {
                startRomanticMode(message, seconds);
            }
        });

        // 生成分享码（v2：多段台词 + 秒数 + 音量）
        btnGenerate.setOnClickListener(v -> {
            String message = msgInput.getText().toString().trim();
            int seconds = 3;
            try {
                seconds = Math.max(1, Math.min(60, Integer.parseInt(timeInput.getText().toString().trim())));
            } catch (Exception ignored) {
            }
            String code = RomanticShareCodec.encodeLines(message, seconds, bgmVolume);
            if (code.isEmpty()) {
                Toaster.showShort("生成失败");
                return;
            }
            copyToClipboard(code);
            Toaster.showLong("分享码已复制（含多段台词与音量），发给对方即可复刻");
        });

        // 导入分享码（自动识别 v1/v2）
        btnImport.setOnClickListener(v -> {
            String clipText = getClipboardText();
        // 【UI 优化】同样改用 MD3 输入框 + 显式按钮，避免横屏下系统按钮栏不可见
        View importView = getLayoutInflater().inflate(R.layout.dialog_input_text, null);
        final com.google.android.material.textfield.TextInputLayout til =
                importView.findViewById(R.id.til_input);
        final com.google.android.material.textfield.TextInputEditText importInput =
                importView.findViewById(R.id.et_input);
        til.setHint("分享码");
        til.setCounterEnabled(false);
        importInput.setTextColor(Color.WHITE);
        if (RomanticShareCodec.isValidCode(clipText)) {
            importInput.setText(clipText);
        }

        final androidx.appcompat.app.AlertDialog importDialog =
                new UiGuard.SafeBuilder(this)
                        .setTitle("导入浪漫分享码")
                        .setView(importView)
                        .show();

        com.google.android.material.button.MaterialButton confirmBtn =
                importView.findViewById(R.id.btn_confirm);
        confirmBtn.setText("导入");
        confirmBtn.setOnClickListener(cv -> {
            String code = importInput.getText() == null ? "" : importInput.getText().toString().trim();
            RomanticShareCodec.RomanticInfo info = RomanticShareCodec.decode(code);
            if (info == null) {
                Toaster.showLong("分享码无效：" + RomanticShareCodec.lastDecodeError);
                return;
            }
            msgInput.setText(info.message);
            timeInput.setText(String.valueOf(info.seconds));
            if (info.volume >= 0f) {
                // 分享码里的音量是外部数据（0.33 → 33.0），不落在 stepSize=5 的刻度上会崩
                SliderSafe.setValue(volumeSlider, info.volume * 100f);
            }
            Toaster.showShort("导入成功");
            importDialog.dismiss();
        });
        importView.findViewById(R.id.btn_cancel).setOnClickListener(cv -> importDialog.dismiss());
        });
    }

    /**
     * 复制文本到剪贴板
     */
    private void copyToClipboard(String text) {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("romantic_code", text);
        clipboard.setPrimaryClip(clip);
    }

    /**
     * 读取剪贴板文本
     */
    private String getClipboardText() {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard.hasPrimaryClip() && clipboard.getPrimaryClip() != null) {
            ClipData.Item item = clipboard.getPrimaryClip().getItemAt(0);
            return item.getText() != null ? item.getText().toString() : "";
        }
        return "";
    }

    private void startRomanticMode(String message, int seconds) {
        if (isAutoPlaying) toggleAutoPlay();
        isRomanticMode = true;
        heartTick = 0;
        // 【架构】开演前清空上一场残留（含暂停态），避免上一场的排队步骤串到这一场
        if (showDirector != null) showDirector.clear();
        btnRomantic.setText("停止浪漫");
        // 【新增】演出控制条（录制中不显示，避免入镜）
        if (!isRecording) showShowControls();        // 粒子文字切换为粉紫色系
        particleTextView.setTextColorHue(320, 350);
        rootLayout.setBackground(null);
        rootLayout.setBackgroundColor(Color.rgb(10, 0, 15));
        wasSkyVisibleBeforeRomantic = starView.getVisibility() == View.VISIBLE; // 【修复】记录退出后要恢复的星空状态
        starView.startSky();

        // ===== 表白优化重制：多段台词 + 星空染色 + 樱花精灵雨 + 跟鼓点 + 开场仪式 =====
        romanticLines = RomanticShareCodec.splitLines(message);
        romanticStage = 0;
        romanticBeatCounter = 0;
        // 星空氛围染色（#10）：粉紫调
        starView.setMoodStarColors(new int[]{0xFFFFB6C1, 0xFFD8B6FF, 0xFFFFC2E0});
        // 花瓣雨升级（#9）：夜空层切樱花精灵（库 SkyView 已重制的渐变花瓣），心形纸瓣保留
        prevWeatherDuringRomantic = starView.getWeather();
        starView.setWeather(SkyView.WEATHER_SAKURA);
        // 高潮跟鼓点（#6）：律动开启时订阅节拍，每第 4 拍中心大爱心 + 轰鸣锚点
        if (rhythmOn && RhythmFirework.getInstance().isRunning()) {
            RhythmFirework.getInstance().setOnBeatListener(amp -> onRomanticBeat());
        }

        startMusic();

        // ===== 【#4】演出中隐藏底部按钮栏：防对方误触“停止浪漫”打断表白 =====
        wasPureBeforeRomantic = isPureMode;
        llBottomBar.animate().alpha(0f).setDuration(400)
                .withEndAction(() -> llBottomBar.setVisibility(View.GONE)).start();

        // ===== 台词朗读：预热音源（本地模型解压/系统引擎初始化都耗时，提前做） =====
        if (speech != null) speech.warmUp();

        // ===== 【#23】开场留白：暗场 2 秒只剩星空与心跳，屏息之后再起引言 =====
        GlobalFirework.getInstance().playHeartbeatVibration();
        postRomanticCue(() -> {
            if (!isRomanticMode) return;
            GlobalFirework.getInstance().playHeartbeatVibration();
        }, 1000);

        // 开场仪式（#4）：引言字 → 四方小心理聚心 → 倒计时；【#23】名字贯穿：开场即只属于她
        String openLine = herName.isEmpty() ? "这一刻，只为你说"
                : herName + "，这一刻，只为你";
        postRomanticCue(() -> {
        if (!isRomanticMode) return;
        particleTextView.setTextColorHue(45, 60);
        particleTextView.showText(openLine, () -> {
            if (!isRomanticMode) return;
            postRomanticCue(() -> {
                if (!isRomanticMode) return;
                particleTextView.explode(() -> {
                    if (!isRomanticMode) return;
                    float cx = rootLayout.getWidth() / 2f;
                    float cy = rootLayout.getHeight() / 2f - 80f;
                    FireworkConfig smallHeart = new FireworkConfig.Builder()
                            .shape(FireworkConfig.Shape.HEART)
                            .colors(0xFFFF69B4, Color.WHITE)
                            .count(scaledCount(50))
                            .range(0.55f).trail(1.1f).build();
                    float d = Math.min(rootLayout.getWidth(), rootLayout.getHeight()) * 0.22f;
                    GlobalFirework.getInstance().bloomSilently(cx - d, cy - d, smallHeart);
                    GlobalFirework.getInstance().bloomSilently(cx + d, cy - d, smallHeart);
                    GlobalFirework.getInstance().bloomSilently(cx - d, cy + d, smallHeart);
                    GlobalFirework.getInstance().bloomSilently(cx + d, cy + d, smallHeart);
                    postRomanticCue(() -> {
                        if (!isRomanticMode) return;
                        GlobalFirework.getInstance().bloom(cx, cy, new FireworkConfig.Builder()
                                .shape(FireworkConfig.Shape.HEART)
                                .colors(Color.RED, 0xFFFF69B4)
                                .count(scaledCount(90)).range(0.9f).trail(1.3f).build());
                        particleTextView.setTextColorHue(320, 350);
                        postRomanticCue(() -> {
                            if (!isRomanticMode) return;
                            startCountdownSequence(seconds, message);
                        }, 900);
                    }, 700);
                });
            }, 1600);
        });
        }, 2000); // 【#23】留白 2 秒后起开场仪式
    }

    /** 鼓点高潮（#6）：每第 4 拍在中心叠一枚大爱心，踩在音乐重音上 */
    private void onRomanticBeat() {
        if (!isRomanticMode || romanticStage < 1 || romanticStage > 2) return;
        if (++romanticBeatCounter % 4 != 0) return;
        float cx = rootLayout.getWidth() / 2f;
        float cy = rootLayout.getHeight() / 2f - 100f;
        GlobalFirework.getInstance().bloomSilently(cx, cy, new FireworkConfig.Builder()
                .shape(FireworkConfig.Shape.HEART)
                .colors(0xFFFFD700, 0xFFFF69B4, Color.WHITE)
                .count(scaledCount(110)).range(1.1f).trail(1.4f).build());
    }

    private int prevWeatherDuringRomantic = 0;
    // 【修复】记录进入浪漫前星空是否可见，退出时恢复（否则星空永久消失，点击也无烟花）
    private boolean wasSkyVisibleBeforeRomantic = true;

    /** 【UI 优化】多句演出时顶部显示"第 N / M 句"，单句不显示 */
    private void showStageProgress(int idx, int total) {
        if (total <= 1) return;
        if (stageProgressTv == null) {
            stageProgressTv = new TextView(this);
            stageProgressTv.setTextColor(0x99FFFFFF);
            stageProgressTv.setTextSize(12f);
            stageProgressTv.setLetterSpacing(0.08f);
            androidx.constraintlayout.widget.ConstraintLayout.LayoutParams lp =
                    new androidx.constraintlayout.widget.ConstraintLayout.LayoutParams(-2, -2);
            lp.topToTop = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID;
            lp.startToStart = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID;
            lp.endToEnd = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID;
            lp.topMargin = (int) (18 * getResources().getDisplayMetrics().density);
            rootLayout.addView(stageProgressTv, lp);
        }
        stageProgressTv.setText("第 " + (idx + 1) + " / " + total + " 句");
        stageProgressTv.setVisibility(View.VISIBLE);
    }

    private void hideStageProgress() {
        if (stageProgressTv != null && stageProgressTv.getVisibility() == View.VISIBLE) {
            stageProgressTv.animate().alpha(0f).setDuration(300)
                    .withEndAction(() -> stageProgressTv.setVisibility(View.GONE)).start();
        }
    }

    /** 多段台词依次演出（#3/#8）：聚拢→心脏搏动两次→爆开成星散，逐句推进 */
    private void playConfessionLines(int lineIdx) {
        if (!isRomanticMode) return;
        if (lineIdx >= romanticLines.size()) {
            hideStageProgress();
            startActOne();
            return;
        }
        romanticStage = 1;
        // 【UI 优化】多句演出时给出进度，否则用户不知道还要等多久（尤其 10 句的长告白）
        showStageProgress(lineIdx, romanticLines.size());
        String line = romanticLines.get(lineIdx);
        speech.speak(line); // 【#18】TTS 同步朗读台词
        // 【随机特效#20】每句台词随机入场（像 PPT 翻页动画：聚拢/飞入/落下/升起/淡入/波浪/弹跳）
        ParticleTextView.EntranceStyle entrance =
                LINE_ENTRANCES[random.nextInt(LINE_ENTRANCES.length)];
        particleTextView.showText(line, entrance, () -> {
            if (!isRomanticMode) return;
            // 心跳震动（#11）：咚-咚
            GlobalFirework.getInstance().playHeartbeatVibration();
            // 强调演出（#8）：心脏搏动两次
            particleTextView.animate().scaleX(1.14f).scaleY(1.14f).setDuration(260)
                    .withEndAction(() -> {
                        particleTextView.animate().scaleX(1f).scaleY(1f).setDuration(240)
                                .withEndAction(() -> {
                                    particleTextView.animate().scaleX(1.14f).scaleY(1.14f).setDuration(260)
                                            .withEndAction(() -> particleTextView.animate().scaleX(1f).scaleY(1f).setDuration(240).start())
                                            .start();
                                }).start();
                    }).start();
            // 【演出拉长#19】停留时长按字数动态：短句不拖沓、长句读得完，TTS 朗读留足余量
            postRomanticCue(() -> {
                if (!isRomanticMode) return;
                GlobalFirework.getInstance().playHeartbeatVibration();
                boolean last = (lineIdx == romanticLines.size() - 1);
                if (last) {
                    // 最后一句不爆开，保留在幕尾由应答前收束
                    hideStageProgress();
                    startActOne();
                } else {
                    bloomLineAccent(lineIdx, line); // 【演出拉长#19】句尾配特效烟花，文字段不空场
                    particleTextView.explode(() -> {
                        if (!isRomanticMode) return;
                        postRomanticCue(() -> playConfessionLines(lineIdx + 1), 350);
                    });
                }
            }, lineDwellMs(line, lineIdx, romanticLines.size()));
        });
    }

    /** 台词停留时长（#19 + 情绪曲线#23）：按字数动态 + 前段慢而柔、越往后越紧凑 */
    private long lineDwellMs(String line, int idx, int total) {
        int len = line == null ? 0 : line.trim().length();
        long base = Math.max(2800, Math.min(5400, 1900 + len * 260L));
        float mood = 1.15f - 0.25f * (total <= 1 ? 0f : (float) idx / (total - 1)); // 1.15 → 0.9 渐进
        return Math.round(base * mood);
    }

    /** 台词入场特效池（随机特效#20）：每句随机抽取，像 PPT 翻页动画一样不重样 */
    private static final ParticleTextView.EntranceStyle[] LINE_ENTRANCES =
            ParticleTextView.EntranceStyle.values();

    /** 句尾特效（#19/#21/#23）：关键词优先联动，未命中再按句序轮换；每三句补流星 */
    private void bloomLineAccent(int lineIdx, String line) {
        int w = rootLayout.getWidth();
        int h = rootLayout.getHeight();
        float px = w * (0.2f + random.nextFloat() * 0.6f);
        float py = h * (0.16f + random.nextFloat() * 0.3f);
        FireworkConfig.Builder b = new FireworkConfig.Builder()
                .colors(hueColorApp((320 + lineIdx * 14) % 360), 0xFFFFD700, Color.WHITE)
                .count(scaledCount(85)).range(0.8f).trail(1.2f);
        // 【#23】台词关键词联动：她念到什么，天上就开什么（优先级最高）
        if (!keywordAccent(line, b)) {
            switch (lineIdx % 5) {
                case 0: b.shape(FireworkConfig.Shape.HEART); break;
                case 1: b.shape(FireworkConfig.Shape.CUSTOM).shapeBitmap(emojiShapeBitmap("🌹")); break;
                case 2: b.shape(FireworkConfig.Shape.STAR); break;
                case 3: b.shape(FireworkConfig.Shape.CUSTOM).shapeBitmap(emojiShapeBitmap("💍")); break;
                default: b.shape(FireworkConfig.Shape.CIRCLE); break;
            }
        }
        GlobalFirework.getInstance().bloomSilently(px, py, b.build());
        if (lineIdx % 3 == 2) starView.spawnMeteor(); // 台词段穿插流星
    }

    /** 【#23】台词关键词联动：嫁给我→戒指、玫瑰/花→玫瑰、钻石→钻石、星→五角星、月→满月金圆、爱/心→爱心 */
    private boolean keywordAccent(String line, FireworkConfig.Builder b) {
        if (line == null || line.isEmpty()) return false;
        if (line.contains("嫁给我") || line.contains("求婚") || line.contains("戒指")
                || line.contains("钻戒") || line.contains("娶你") || line.contains("嫁")) {
            b.shape(FireworkConfig.Shape.CUSTOM).shapeBitmap(emojiShapeBitmap("💍"))
                    .colors(0xFFFFD700, Color.WHITE);
            return true;
        }
        if (line.contains("玫瑰") || line.contains("花")) {
            b.shape(FireworkConfig.Shape.CUSTOM).shapeBitmap(emojiShapeBitmap("🌹"))
                    .colors(0xFFFF2D55, 0xFFFF69B4);
            return true;
        }
        if (line.contains("钻石")) {
            b.shape(FireworkConfig.Shape.CUSTOM).shapeBitmap(emojiShapeBitmap("💎"))
                    .colors(0xFF7FD4FF, 0xFFB6E5FF);
            return true;
        }
        if (line.contains("星")) {
            b.shape(FireworkConfig.Shape.STAR).colors(0xFFFFD700, Color.WHITE);
            return true;
        }
        if (line.contains("月")) {
            b.shape(FireworkConfig.Shape.CIRCLE).colors(0xFFF5EFCE, 0xFFFFFFFF); // 月亮意象：温润金白圆盘
            return true;
        }
        if (line.contains("爱") || line.contains("心")) {
            b.shape(FireworkConfig.Shape.HEART).colors(0xFFFF69B4, Color.RED);
            return true;
        }
        return false;
    }

    /** 幕一：三波爱心绽放 + 花瓣雨 + 五角星尾声，持续约 16 秒后进幕二（#19 拉长） */
    private void startActOne() {
        if (!isRomanticMode) return;
        romanticStage = 1;
        float cx = rootLayout.getWidth() / 2f;
        float cy = rootLayout.getHeight() / 2f - 100f;
        int h = rootLayout.getHeight();
        int w = rootLayout.getWidth();
        int barHeight = llBottomBar.getHeight();
        final float topMargin = 50f;
        final float bottomReserved = barHeight > 0 ? barHeight + 60f : 220f;
        float safeRange = Math.min(1.0f, (h - bottomReserved - topMargin) / (h * 0.9f));
        float sideRange = safeRange * 0.7f;
        float sideOffset = Math.min(220f, w * 0.22f);

        // 第1波：中心升空爱心
        postRomanticCue(() -> {
            if (!isRomanticMode) return;
            GlobalFirework.getInstance().bloom(cx, cy, new FireworkConfig.Builder()
                    .shape(FireworkConfig.Shape.HEART).rocket(true)
                    .colors(Color.RED, 0xFFFF69B4, Color.WHITE)
                    .count(scaledCount(120)).range(safeRange).trail(1.5f).build());
        }, 600);
        // 第2波：左右呼应
        postRomanticCue(() -> {
            if (!isRomanticMode) return;
            FireworkConfig sideConfig = new FireworkConfig.Builder()
                    .shape(FireworkConfig.Shape.HEART)
                    .colors(0xFFFF69B4, 0xFFFFD700)
                    .count(scaledCount(90)).range(sideRange).trail(1.2f).build();
            GlobalFirework.getInstance().bloom(cx - sideOffset, cy, sideConfig);
            GlobalFirework.getInstance().bloom(cx + sideOffset, cy, sideConfig);
        }, 1200);
        // 第3波：花瓣雨开启（原双层旋转爱心轨迹已按需移除，三波爱心绽放本身构成完整幕一）
        postRomanticCue(() -> {
            if (!isRomanticMode) return;
            mainHandler.post(floatingPetalsTask);
        }, 1800);
        // ===== 【#1】幕一空档填补：中段每 2.5 秒一枚随机大爱心点缀 =====
        for (int i = 0; i < 5; i++) {
            final int fi = i;
            postRomanticCue(() -> {
                if (!isRomanticMode) return;
                int ww = rootLayout.getWidth();
                int hh = rootLayout.getHeight();
                GlobalFirework.getInstance().bloomSilently(
                        ww * (0.18f + random.nextFloat() * 0.64f),
                        hh * (0.18f + random.nextFloat() * 0.4f),
                        new FireworkConfig.Builder()
                                .shape(FireworkConfig.Shape.HEART)
                                .colors(hueColorApp(330 + fi * 12), 0xFFFFD700, Color.WHITE)
                                .count(scaledCount(100)).range(0.95f).trail(1.3f).build());
                if (fi % 2 == 1) {
                    GlobalFirework.getInstance().playHeartbeatVibration();
                }
            }, 4500 + i * 2500L);
        }
        // ===== 【演出拉长#19】幕一尾声：金色五角星升空，平稳接住幕二开场 =====
        postRomanticCue(() -> {
            if (!isRomanticMode) return;
            GlobalFirework.getInstance().bloom(cx, cy - h * 0.06f, new FireworkConfig.Builder()
                    .shape(FireworkConfig.Shape.STAR).rocket(true)
                    .colors(0xFFFFD700, Color.WHITE)
                    .count(scaledCount(110)).range(1.0f).trail(1.4f).build());
        }, 14000);
        postRomanticCue(this::startActTwo, 16000);
    }

    /** 幕二：玫瑰花海（玫瑰/爱心波次交替）+ 满屏流星雨时刻（#9 + 女生心动向#21） */
    private void startActTwo() {
        if (!isRomanticMode) return;
        romanticStage = 2;
        int w = rootLayout.getWidth();
        int h = rootLayout.getHeight();
        for (int wave = 0; wave < 6; wave++) {
            final int fi = wave;
            postRomanticCue(() -> {
                if (!isRomanticMode) return;
                int hueBase = fi * 45;
                FireworkConfig.Builder b = new FireworkConfig.Builder()
                        .colors(hueColorApp(hueBase), hueColorApp((hueBase + 40) % 360), Color.WHITE)
                        .count(scaledCount(70)).range(0.85f).trail(1.2f);
                if (fi % 2 == 1) {
                    // 女生心动向#21：奇数波绽放玫瑰（真·玫瑰花海），末波钻石收尾
                    b.shape(FireworkConfig.Shape.CUSTOM)
                            .shapeBitmap(emojiShapeBitmap(fi == 5 ? "💎" : "🌹"));
                } else {
                    b.shape(FireworkConfig.Shape.HEART);
                }
                FireworkConfig waveHeart = b.build();
                float px = w * (0.2f + 0.6f * (fi % 3) / 2f);
                float py = h * (0.25f + 0.2f * ((fi + 1) % 3) / 2f);
                GlobalFirework.getInstance().bloomSilently(px, py, waveHeart);
                GlobalFirework.getInstance().bloomSilently(w - px, h - py - h * 0.1f, waveHeart);
                // 流星雨时刻：每波伴两颗流星
                starView.spawnMeteor();
                starView.spawnMeteor();
            }, wave * 1300);
        }
        postRomanticCue(this::startActThree, 6 * 1300 + 600);
    }

    /** 幕三：终章——同心圆绽放 + 最后一句文字烟花收尾 */
    private void startActThree() {
        if (!isRomanticMode) return;
        romanticStage = 3;
        particleTextView.explode(null);
        int w = rootLayout.getWidth();
        int h = rootLayout.getHeight();
        float cx = w / 2f, cy = h / 2f - 80f;
        for (int ring = 0; ring < 3; ring++) {
            final int rIdx = ring;
            postRomanticCue(() -> {
                if (!isRomanticMode) return;
                int n = 6 + rIdx * 3;
                float radius = Math.min(w, h) * (0.14f + rIdx * 0.07f);
                FireworkConfig ringCfg = new FireworkConfig.Builder()
                        .colors(hueColorApp(280 + rIdx * 30), hueColorApp(320 + rIdx * 30), Color.WHITE)
                        .count(scaledCount(45)).range(0.7f + rIdx * 0.15f).trail(1.2f).build();
                for (int i = 0; i < n; i++) {
                    double a = 2 * Math.PI * i / n;
                    GlobalFirework.getInstance().bloomSilently(
                            cx + (float) Math.cos(a) * radius,
                            cy + (float) Math.sin(a) * radius, ringCfg);
                }
            }, ring * 900);
        }
        postRomanticCue(() -> {
            if (!isRomanticMode) return;
            String lastLine = romanticLines.get(romanticLines.size() - 1);
            GlobalFirework.getInstance().bloom(cx, cy - h * 0.05f, new FireworkConfig.Builder()
                    .shape(FireworkConfig.Shape.TEXT).text(lastLine)
                    .colors(0xFFFFD700, 0xFFFF69B4)
                    .count(scaledCount(200)).range(1.2f).trail(1.4f)
                    .textSize(fitFireworkTextSize(lastLine, 110f)).rocket(true).build());
            mainHandler.postDelayed(() -> {
                if (!isRomanticMode) return;
                GlobalFirework.getInstance().bloom(cx, cy, new FireworkConfig.Builder()
                        .shape(FireworkConfig.Shape.HEART)
                        .colors(Color.RED, 0xFFFF69B4, Color.WHITE)
                        .count(scaledCount(180)).range(1.2f).trail(1.5f).build());
                // 【女生心动向#21】终章求婚意象：戒指升空 + 左右玫瑰簇拥，把“那句问话”的氛围拉满
                GlobalFirework.getInstance().bloomSilently(cx, cy - h * 0.18f, new FireworkConfig.Builder()
                        .shape(FireworkConfig.Shape.CUSTOM).shapeBitmap(emojiShapeBitmap("💍")).rocket(true)
                        .colors(0xFFFFD700, Color.WHITE)
                        .count(scaledCount(140)).range(1.0f).trail(1.3f).build());
                GlobalFirework.getInstance().bloomSilently(cx - w * 0.26f, cy, new FireworkConfig.Builder()
                        .shape(FireworkConfig.Shape.CUSTOM).shapeBitmap(emojiShapeBitmap("🌹"))
                        .colors(0xFFFF2D55, 0xFFFF69B4)
                        .count(scaledCount(130)).range(0.9f).trail(1.2f).build());
                GlobalFirework.getInstance().bloomSilently(cx + w * 0.26f, cy, new FireworkConfig.Builder()
                        .shape(FireworkConfig.Shape.CUSTOM).shapeBitmap(emojiShapeBitmap("🌹"))
                        .colors(0xFFFF2D55, 0xFFFF69B4)
                        .count(scaledCount(130)).range(0.9f).trail(1.2f).build());
                postRomanticCue(this::showAnswerOverlay, 1200);
            }, 1500);
        }, 3 * 900);
    }
    /** 应答时刻（#1）：我愿意 / 再想想 */
    private void showAnswerOverlay() {
        if (!isRomanticMode) return;
        romanticStage = 4;
        answerOverlay = new android.widget.FrameLayout(this);
        answerOverlay.setBackgroundColor(0x66000000);
        answerOverlay.setClickable(true); // 【#23】遮罩吃掉点击：防穿透到烟花层误触放爱心/蓄力
        TextView question = new TextView(this);
        // 【#23】名字贯穿：问题前加她的名字，这一刻只属于她（台词已含名字则不重复）
        String qLine = romanticLines.get(romanticLines.size() - 1);
        if (!herName.isEmpty() && !qLine.contains(herName)) qLine = herName + "，" + qLine;
        question.setText(qLine);
        question.setTextSize(26);
        question.setTextColor(Color.WHITE);
        question.setGravity(android.view.Gravity.CENTER);
        question.setTypeface(android.graphics.Typeface.DEFAULT_BOLD, android.graphics.Typeface.BOLD);
        android.widget.FrameLayout.LayoutParams qLp = new android.widget.FrameLayout.LayoutParams(-1, -2);
        qLp.topMargin = rootLayout.getHeight() / 4;
        qLp.leftMargin = 60; qLp.rightMargin = 60;
        answerOverlay.addView(question, qLp);

        LinearLayout btnRow = new LinearLayout(this);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        btnRow.setGravity(android.view.Gravity.CENTER_HORIZONTAL);
        // 应答双按钮改用 MaterialButton：圆角与涟漪触感与底栏统一；
        // 主按钮实心玫红、副按钮半透明描边，视觉主次分明——这一刻主角是"我愿意"
        float density = getResources().getDisplayMetrics().density;
        int btnCorner = (int) (26 * density);
        int btnPadH = (int) (30 * density);
        int btnPadV = (int) (16 * density);

        com.google.android.material.button.MaterialButton yesBtn =
                new com.google.android.material.button.MaterialButton(this);
        yesBtn.setText("我愿意 💗");
        yesBtn.setTextColor(Color.WHITE);
        yesBtn.setTextSize(18); // 【#21】主按钮加大：这一刻她只需要看这颗心
        yesBtn.setCornerRadius(btnCorner);
        yesBtn.setBackgroundTintList(ColorStateList.valueOf(0xFFE91E63));
        yesBtn.setPadding(btnPadH, btnPadV, btnPadH, btnPadV);
        yesBtn.setInsetTop(0);
        yesBtn.setInsetBottom(0);

        com.google.android.material.button.MaterialButton thinkBtn =
                new com.google.android.material.button.MaterialButton(this);
        thinkBtn.setText("再想想 🤔");
        thinkBtn.setTextColor(Color.WHITE);
        thinkBtn.setTextSize(13); // 【#21】副按钮弱化：不给压力，但主角是"我愿意"
        thinkBtn.setAlpha(0.8f);
        thinkBtn.setCornerRadius(btnCorner);
        thinkBtn.setBackgroundTintList(ColorStateList.valueOf(0x80000000));
        thinkBtn.setStrokeColor(ColorStateList.valueOf(0x66FFFFFF));
        thinkBtn.setStrokeWidth((int) density);
        thinkBtn.setPadding(btnPadH, btnPadV, btnPadH, btnPadV);
        thinkBtn.setInsetTop(0);
        thinkBtn.setInsetBottom(0);

        LinearLayout.LayoutParams yesLp = new LinearLayout.LayoutParams(-2, -2);
        yesLp.rightMargin = (int) (18 * density);
        btnRow.addView(yesBtn, yesLp);
        btnRow.addView(thinkBtn);
        // 【#23】长按引导：她第一次看到不会知道要长按，明确告诉她怎么说出答案
        TextView holdHint = new TextView(this);
        holdHint.setText("（长按“我愿意” 2 秒，说出你的答案）");
        holdHint.setTextSize(12);
        holdHint.setTextColor(0xB3FFFFFF);
        holdHint.setGravity(android.view.Gravity.CENTER);
        android.widget.FrameLayout.LayoutParams hintLp = new android.widget.FrameLayout.LayoutParams(-1, -2);
        hintLp.topMargin = rootLayout.getHeight() / 2 + 190;
        answerOverlay.addView(holdHint, hintLp);
        final TextView fHoldHint = holdHint;
        android.widget.FrameLayout.LayoutParams rowLp = new android.widget.FrameLayout.LayoutParams(-2, -2);
        rowLp.topMargin = rootLayout.getHeight() / 2;
        rowLp.gravity = android.view.Gravity.CENTER_HORIZONTAL;
        answerOverlay.addView(btnRow, rowLp);

        // 【#4】应答时刻浮出小停止键（演出中底部栏已隐藏）：描边弱化，不抢主按钮的注意力
        com.google.android.material.button.MaterialButton miniStop =
                new com.google.android.material.button.MaterialButton(this);
        miniStop.setText("结束演出");
        miniStop.setTextColor(0xFF99AABB);
        miniStop.setTextSize(12);
        miniStop.setCornerRadius((int) (20 * density));
        miniStop.setBackgroundTintList(ColorStateList.valueOf(0x00000000));
        miniStop.setStrokeColor(ColorStateList.valueOf(0x33FFFFFF));
        miniStop.setStrokeWidth((int) density);
        miniStop.setInsetTop(0);
        miniStop.setInsetBottom(0);
        android.widget.FrameLayout.LayoutParams stopLp = new android.widget.FrameLayout.LayoutParams(-2, -2);
        stopLp.gravity = android.view.Gravity.BOTTOM | android.view.Gravity.CENTER_HORIZONTAL;
        stopLp.bottomMargin = 30;
        answerOverlay.addView(miniStop, stopLp);
        miniStop.setOnClickListener(v -> stopRomanticMode());

        rootLayout.addView(answerOverlay, new android.widget.FrameLayout.LayoutParams(-1, -1));
        answerOverlay.setAlpha(0f);
        answerOverlay.animate().alpha(1f).setDuration(700).start();
        // 【#3】“我愿意”按钮呼吸脉动，吸引点击
        ObjectAnimator pulse = ObjectAnimator.ofFloat(yesBtn, "alpha", 1f, 0.65f, 1f);
        pulse.setDuration(1200);
        pulse.setRepeatCount(ObjectAnimator.INFINITE);
        pulse.start();
        yesBtn.setTag(pulse);
        yesPulseAnimator = pulse; // 【#21】存字段，收场时可靠停掉
        // ===== 【#23】长按“我愿意”2 秒才生效：郑重的动作防手滑，松早了轻声挽留 =====
        yesBtn.setOnTouchListener(new View.OnTouchListener() {
            private boolean fired = false;
            private boolean hintSwitched = false;
            @Override
            public boolean onTouch(View v, android.view.MotionEvent me) {
                switch (me.getAction()) {
                    case android.view.MotionEvent.ACTION_DOWN:
                        fired = false;
                        if (yesPulseAnimator != null) { yesPulseAnimator.cancel(); yesPulseAnimator = null; }
                        yesBtn.setAlpha(1f);
                        if (!hintSwitched) {
                            hintSwitched = true;
                            fHoldHint.setText("按住我，别松手 💗"); // 【#23】松手后引导换文案，下一步她就懂了
                        }
                        yesHoldAnimator = ValueAnimator.ofFloat(0f, 1f);
                        yesHoldAnimator.setDuration(2000);
                        yesHoldAnimator.addUpdateListener(a -> {
                            float f = (float) a.getAnimatedValue();
                            yesBtn.setScaleX(1f + f * 0.18f);
                            yesBtn.setScaleY(1f + f * 0.18f);
                        });
                        yesHoldAnimator.addListener(new AnimatorListenerAdapter() {
                            private boolean cancelledByUser = false;
                            @Override public void onAnimationCancel(Animator a) { cancelledByUser = true; }
                            @Override public void onAnimationEnd(Animator a) {
                                if (!cancelledByUser && !fired) {
                                    fired = true;
                                    // 【移除保护】overlay 已被超时收场/结束演出移除时不再触发庆祝
                                    if (isRomanticMode && answerOverlay != null) {
                                        onAnswerYes(); // 按满 2 秒：这就是她的答案
                                    }
                                }
                            }
                        });
                        yesHoldAnimator.start();
                        return true;
                    case android.view.MotionEvent.ACTION_UP:
                    case android.view.MotionEvent.ACTION_CANCEL:
                        if (yesHoldAnimator != null) {
                            if (yesHoldAnimator.isRunning() && !fired) {
                                fired = true;
                                yesHoldAnimator.cancel();
                                yesBtn.setScaleX(1f);
                                yesBtn.setScaleY(1f);
                                Toaster.showShort("再确认一下？我等你 💗");
                            }
                            yesHoldAnimator = null;
                        }
                        return true;
                }
                return false;
            }
        });
        thinkBtn.setOnClickListener(v -> onAnswerThink());
        startAnswerHeartRain(); // 【#21】等待她回答时，满屏缓缓飘落小爱心
        // ===== 【#23】等待台词轮播：10 秒一句轻声等她，比干等温柔 =====
        postRomanticCue(() -> {
            if (isRomanticMode && answerOverlay != null)
                Toaster.showShort("不着急，我等你");
        }, 10000);
        postRomanticCue(() -> {
            if (isRomanticMode && answerOverlay != null)
                Toaster.showShort("你点头就好");
        }, 20000);
        // 【#6】应答无响应保护：30 秒未选择则温柔收场
        postRomanticCue(() -> {
            if (isRomanticMode && answerOverlay != null) {
                Toaster.showShort("无论多久，我等你");
                onAnswerThink();
            }
        }, 30000);
    }

    // ===== 应答爱心雨（女生心动向#21）：等她回答的时刻，爱心/花辫/星光缓缓飘落 =====
    private Runnable answerHeartRainTask;
    private ObjectAnimator yesPulseAnimator;
    private ValueAnimator yesHoldAnimator; // 【#23】长按动画：收场时可靠停掉，防空转死引用

    private void startAnswerHeartRain() {
        stopAnswerHeartRain();
        answerHeartRainTask = new Runnable() {
            @Override
            public void run() {
                if (!isRomanticMode || answerOverlay == null) return;
                String[] em = {"💗", "💕", "💖", "🌸", "✨"};
                TextView hv = new TextView(MainActivity.this);
                hv.setText(em[random.nextInt(em.length)]);
                hv.setTextSize(16 + random.nextInt(10));
                int ow = answerOverlay.getWidth() > 0 ? answerOverlay.getWidth() : rootLayout.getWidth();
                android.widget.FrameLayout.LayoutParams lp = new android.widget.FrameLayout.LayoutParams(-2, -2);
                lp.leftMargin = ow > 120 ? random.nextInt(ow - 120) : 0;
                answerOverlay.addView(hv, lp);
                hv.setAlpha(0f);
                hv.animate().alpha(0.85f)
                        .translationY(rootLayout.getHeight() * (0.6f + random.nextFloat() * 0.25f))
                        .setDuration(2600 + random.nextInt(1400))
                        .withEndAction(() -> {
                            if (answerOverlay != null) answerOverlay.removeView(hv);
                        }).start();
                mainHandler.postDelayed(this, 600);
            }
        };
        mainHandler.post(answerHeartRainTask);
    }

    private void stopAnswerHeartRain() {
        if (answerHeartRainTask != null) {
            mainHandler.removeCallbacks(answerHeartRainTask);
            answerHeartRainTask = null;
        }
    }

    /** 应答：我愿意——钻戒时刻 + 终极爆发 + 金色雨 + 纪念日 + 纪念卡片（女生心动向#21） */
    private void onAnswerYes() {
        dismissAnswerOverlay();
        // 她点下"我愿意"的那一刻，先来一段铃音——这一声比任何特效都先到心里
        SoundFx.playChime();
        romanticStage = 5;
        int w = rootLayout.getWidth();
        int h = rootLayout.getHeight();
        float cx = w / 2f, cy = h / 2f - 80f;
        // 金色雨：切换银杏精灵雨（全屏金色飘叶）
        starView.setWeather(SkyView.WEATHER_GINKGO);
        // ===== 女生心动向#21+#23：钻戒时刻——戒指炸开→从天而降定格→玫瑰绽放→庆祝 =====
        postRomanticCue(() -> {
            if (!isRomanticMode) return;
            GlobalFirework.getInstance().bloom(cx, cy, new FireworkConfig.Builder()
                    .shape(FireworkConfig.Shape.CUSTOM).shapeBitmap(emojiShapeBitmap("💍")).rocket(true)
                    .colors(0xFFFFD700, Color.WHITE)
                    .count(scaledCount(150)).range(1.2f).trail(1.3f).build());
        }, 300);
        // 【#23】戒指落下定格：炸开后一枚戒指从天而降，落到中心放大——“为她戴上”
        postRomanticCue(() -> {
            if (isRomanticMode) dropRingMoment();
        }, 1200);
        postRomanticCue(() -> {
            if (!isRomanticMode) return;
            GlobalFirework.getInstance().bloom(cx, cy - h * 0.06f, new FireworkConfig.Builder()
                    .shape(FireworkConfig.Shape.CUSTOM).shapeBitmap(emojiShapeBitmap("🌹"))
                    .colors(0xFFFF2D55, 0xFFFF69B4)
                    .count(scaledCount(150)).range(1.1f).trail(1.2f).build());
        }, 5200);
        GlobalFirework.getInstance().playHeartbeatVibration();
        // 16 连发庆祝（戒指定格落幕后）
        for (int i = 0; i < 16; i++) {
            final int fi = i;
            postRomanticCue(() -> {
                if (!isRomanticMode) return;
                FireworkConfig cfg;
                if (fi % 4 == 0) {
                    cfg = new FireworkConfig.Builder().shape(FireworkConfig.Shape.HEART)
                            .colors(0xFFFFD700, 0xFFFF69B4, Color.WHITE)
                            .count(scaledCount(130)).range(1.1f).trail(1.5f).build();
                } else {
                    cfg = new FireworkConfig.Builder()
                            .colors(0xFFFFD700, Color.WHITE, hueColorApp(fi * 22))
                            .count(scaledCount(80)).range(1.0f + (fi % 3) * 0.2f).trail(1.2f).build();
                }
                GlobalFirework.getInstance().bloomSilently(
                        w * (0.1f + random.nextFloat() * 0.8f),
                        h * (0.12f + random.nextFloat() * 0.45f), cfg);
                if (fi % 4 == 0) GlobalFirework.getInstance().playHeartbeatVibration();
            }, 5800 + fi * 280);
        }
        // 【女生心动向#21+#23】庆祝尾声：深情文字烟花定格这一刻（带她的名字）
        postRomanticCue(() -> {
            if (!isRomanticMode) return;
            String vowLine = herName.isEmpty() ? "余生，请多指教" : herName + "，余生请多指教";
            GlobalFirework.getInstance().bloom(cx, cy - h * 0.08f, new FireworkConfig.Builder()
                    .shape(FireworkConfig.Shape.TEXT).text(vowLine)
                    .colors(0xFFFFD700, 0xFFFF69B4)
                    .count(scaledCount(180)).range(1.1f).trail(1.3f)
                    .textSize(fitFireworkTextSize(vowLine, 90f)).rocket(true).build());
        }, 5800 + 16 * 280 + 300);
        // 记入纪念日（#14）+ 生成纪念卡片（#13）
        saveAnniversary("表白成功纪念日");
        postRomanticCue(() -> {
            // 【性能#24】纪念卡片生成后台化：1080×1350 位图 + PNG 压缩约 0.5~2s，
            // 原在主线程会卡庆祝高峰帧；后台生成，完成后回主线程 Toast（快照防演出状态变化）
            final float age = moonAgeNow();
            final String zodiac = starView.getCurrentZodiacName();
            final java.util.List<String> linesSnapshot = new java.util.ArrayList<>(romanticLines);
            final String hn = herName, mn = myName;
            new Thread(() -> {
                String path = MementoCard.generate(this, linesSnapshot, zodiac, age, hn, mn);
                mainHandler.post(() -> {
                    if (path != null && !isFinishing()) {
                        Toaster.showLong("纪念卡片已保存：" + path);
                    }
                });
            }, "memento-card").start();
            if (pendingRecordFull && isRecording) {
                stopRecordingService(); // 录整场：庆祝完成后自动收尾
            }
            postRomanticCue(() -> {
                if (isRomanticMode) stopRomanticMode();
            }, 8000); // 【女生心动向#21】庆祝绽放更久，给她时间拍照与感动
        }, 5800 + 16 * 280 + 500);
    }

    /** 【#23】戒指落下定格：答愿意后一枚💍从天而降，落到屏幕中心放大定格再淡出，
     *  “为她戴上”的仪式感；stopRomanticMode 强制移除防残留 */
    private void dropRingMoment() {
        int w = rootLayout.getWidth();
        int h = rootLayout.getHeight();
        TextView ring = new TextView(this);
        ring.setText("💍");
        ring.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, Math.min(w, h) * 0.15f);
        android.widget.FrameLayout.LayoutParams lp = new android.widget.FrameLayout.LayoutParams(-2, -2);
        lp.gravity = android.view.Gravity.CENTER;
        rootLayout.addView(ring, lp);
        ringDropView = ring;
        ring.setAlpha(0f);
        ring.setScaleX(0.2f);
        ring.setScaleY(0.2f);
        ring.setTranslationY(-h * 0.3f);
        ring.animate().alpha(1f).translationY(0f).scaleX(1.15f).scaleY(1.15f)
                .setDuration(1500)
                .withEndAction(() -> {
                    if (ringDropView == null) return;
                    ring.postDelayed(() -> {
                        if (ringDropView == null) return;
                        ringDropView.animate().alpha(0f).setDuration(600)
                                .withEndAction(() -> {
                                    if (ringDropView != null) {
                                        rootLayout.removeView(ringDropView);
                                        ringDropView = null;
                                    }
                                }).start();
                    }, 1600);
                })
                .start();
    }

    /** 应答：再想想——温柔收场（女生心动向#21：添一枚柔粉爱心陪她慢慢想） */
    private void onAnswerThink() {
        dismissAnswerOverlay();
        int w = rootLayout.getWidth();
        int h = rootLayout.getHeight();
        GlobalFirework.getInstance().bloom(w / 2f, h / 2f - 80f, new FireworkConfig.Builder()
                .shape(FireworkConfig.Shape.TEXT)
                .text(herName.isEmpty() ? "没关系，我等你" : herName + "，没关系，我等你")
                .colors(0xFF9FB8D8, Color.WHITE)
                .count(scaledCount(160)).range(1.0f)
                .textSize(fitFireworkTextSize(herName.isEmpty() ? "没关系，我等你" : herName + "，没关系，我等你", 90f)).build());
        GlobalFirework.getInstance().bloomSilently(w / 2f, h * 0.28f, new FireworkConfig.Builder()
                .shape(FireworkConfig.Shape.HEART)
                .colors(0xFFFFB6C1, 0xFFD8B6FF, Color.WHITE)
                .count(scaledCount(110)).range(0.8f).trail(1.2f).build());
        postRomanticCue(() -> {
            if (isRomanticMode) stopRomanticMode();
            if (pendingRecordFull && isRecording) stopRecordingService();
        }, 4500);
    }

    private void dismissAnswerOverlay() {
        stopAnswerHeartRain(); // 【#21】应答爱心雨同步停止
        if (yesPulseAnimator != null) {
            yesPulseAnimator.cancel();
            yesPulseAnimator = null;
        }
        if (yesHoldAnimator != null) { // 【#23】长按动画同步停掉，防空转死引用
            yesHoldAnimator.cancel();
            yesHoldAnimator = null;
        }
        if (answerOverlay != null) {
            android.widget.FrameLayout ov = answerOverlay;
            answerOverlay = null;
            ov.animate().alpha(0f).setDuration(500).withEndAction(() -> rootLayout.removeView(ov)).start();
        }
    }

    /** 纪念日存储（#14）：【架构】读写下沉到 AnniversaryStore，Activity 只管展示 */
    private void saveAnniversary(String label) {
        // 【重播】存当天台词（用换行连接），点击纪念日时按原文重演
        StringBuilder lines = new StringBuilder();
        for (String s : romanticLines) {
            if (lines.length() > 0) lines.append('\n');
            lines.append(s);
        }
        AnniversaryStore.add(label, lines.toString());
        // 【新增】新增纪念日要让提醒跟着生效，否则开关开了也不会响
        AnniversaryReminder.reschedule(this);
    }

    private static int hueColorApp(int hue) {
        float[] hsv = {hue, 0.7f, 1f};
        return Color.HSVToColor(hsv);
    }

    /** 【溢出修复#22】文字烟花字号自适应：按屏幕宽度等比缩小，长台词不溢出屏幕；
     *  同时减小采样位图面积，长文本的粒子生成开销同步下降（性能兼得） */
    private float fitFireworkTextSize(String text, float maxSize) {
        if (text == null || text.isEmpty() || rootLayout.getWidth() == 0) return maxSize;
        return fitTextSizePx(text, maxSize, rootLayout.getWidth());
    }

    /** 静态版：构池时（rootLayout 尚无宽度）用系统屏宽自适应，运行时用 view 宽 */
    private static float fitTextSizePx(String text, float maxSize, int widthPx) {
        if (text == null || text.isEmpty() || widthPx <= 0) return maxSize;
        android.graphics.Paint tp = new android.graphics.Paint();
        tp.setTextSize(maxSize);
        float textWidth = tp.measureText(text);
        float maxAllowed = widthPx * 0.82f;
        if (textWidth <= maxAllowed) return maxSize;
        return Math.max(36f, maxSize * maxAllowed / textWidth);
    }

    /** 朔望月月龄（App 端自算，与库同公式） */
    private static float moonAgeNow() {
        long epochMs = 947182440000L; // 2000-01-06 18:14 UTC 已知朔日
        double age = ((System.currentTimeMillis() - epochMs) / 86400000.0) % 29.530588853;
        if (age < 0) age += 29.530588853;
        return (float) age;
    }

    private void startMusic() {
        try {
            stopMusic();
            // BGM 可选（#7）：优先用户从手机选的歌，否则内置浪漫曲
            if (bgmUri != null) {
                try {
                    mediaPlayer = new MediaPlayer();
                    mediaPlayer.setAudioStreamType(android.media.AudioManager.STREAM_MUSIC);
                    mediaPlayer.setDataSource(this, Uri.parse(bgmUri));
                    mediaPlayer.prepare();
                } catch (Exception e) {
                    mediaPlayer.release();
                    mediaPlayer = null;
                    mediaPlayer = MediaPlayer.create(this, R.raw.romantic_bgm);
                }
            } else {
                mediaPlayer = MediaPlayer.create(this, R.raw.romantic_bgm);
            }

            if (mediaPlayer == null) {
                Toaster.showShort("无法加载音乐，请检查 res/raw/romantic_bgm.mp3");
                return;
            }

            mediaPlayer.setLooping(true);
            // 【#5】BGM 淡入 1.5 秒（结尾淡出已有）
            mediaPlayer.setVolume(0f, 0f);
            mediaPlayer.setOnErrorListener((mp, what, extra) -> {
                try {
                    mp.reset();
                } catch (Exception e) {
                    e.printStackTrace();
                }
                return true;
            });

            mediaPlayer.start();
            // 【#5】淡入动画
            final MediaPlayer mpFade = mediaPlayer;
            android.animation.ValueAnimator fadeIn = android.animation.ValueAnimator.ofFloat(0f, bgmVolume);
            fadeIn.setDuration(1500);
            fadeIn.addUpdateListener(a -> {
                try {
                    float v = (float) a.getAnimatedValue();
                    mpFade.setVolume(v, v);
                } catch (Exception ignored) {
                }
            });
            fadeIn.start();

        } catch (Exception e) {
            e.printStackTrace();
            Toaster.showShort("音乐播放失败: " + e.getMessage());
        }
    }

    private void stopMusic() {
        if (mediaPlayer != null) {
            try {
                if (mediaPlayer.isPlaying()) {
                    mediaPlayer.stop();
                }
                mediaPlayer.release();
            } catch (Exception e) {
                e.printStackTrace();
            }
            mediaPlayer = null;
        }
    }

    private void stopRomanticMode() {
        isRomanticMode = false;
        btnRomantic.setText("💖 顶级浪漫");
        hideStageProgress(); // 中途结束也要收掉进度指示
        hideShowControls();  // 收掉跳过/暂停控制条

        // ===== 优雅退场（#17）：不再瞬间清场 =====
        cancelCharging();
        dismissAnswerOverlay();
        if (ringDropView != null) { // 【#23】戒指落下动画强制移除防残留
            rootLayout.removeView(ringDropView);
            ringDropView = null;
        }
        clearRomanticCues(); // 【#7】清光全部排队演出
        if (recordFullGuard != null) {
            mainHandler.removeCallbacks(recordFullGuard);
        }
        speech.stop();
        RhythmFirework.getInstance().setOnBeatListener(null);
        mainHandler.removeCallbacks(floatingPetalsTask);

        // 【修复】演出结束（含手动停止）自动停录：只发停止指令，不触发"800ms 后再退出浪漫"，
        // 切断 stopRomanticMode ↔ stopRecordingService 互相调用的 800ms 无限循环（演出结束后卡死的根因）
        if (isRecording) {
            requestStopRecording();
        }

        // 音乐淡停 800ms
        final MediaPlayer mp = mediaPlayer;
        if (mp != null) {
            try {
                android.animation.ValueAnimator fade = android.animation.ValueAnimator.ofFloat(bgmVolume, 0f);
                fade.setDuration(800);
                fade.addUpdateListener(a -> {
                    try {
                        float v = (float) a.getAnimatedValue();
                        mp.setVolume(v, v);
                    } catch (Exception ignored) {
                    }
                });
                fade.addListener(new AnimatorListenerAdapter() {
                    @Override public void onAnimationEnd(android.animation.Animator a) {
                        stopMusic();
                    }
                });
                fade.start();
                mediaPlayer = null; // 交由淡停流程释放
            } catch (Exception e) {
                stopMusic();
            }
        }
        // 花瓣淡出后清空
        floatingContainer.animate().alpha(0f).setDuration(600)
                .withEndAction(() -> {
                    floatingContainer.removeAllViews();
                    floatingContainer.setAlpha(1f);
                }).start();
        particleTextView.explode(null);

        // 星空恢复：天气/染色还原
        starView.setWeather(prevWeatherDuringRomantic);
        starView.setMoodStarColors(null);

        restoreBackground();
        // 【修复】星空恢复：进入前可见则重新启动（此前无条件 stopSky 导致退出浪漫后星星永久消失）
        if (wasSkyVisibleBeforeRomantic) {
            starView.startSky();
        } else {
            starView.stopSky();
        }
        particleTextView.setTextColorHue(30, 70);

        // 【#4】恢复底部按钮栏（若进入前非纯净模式）
        if (!wasPureBeforeRomantic) {
            llBottomBar.setVisibility(View.VISIBLE);
            llBottomBar.setAlpha(0f);
            llBottomBar.animate().alpha(1f).setDuration(500).start();
        }

        heartAngle = 0;
        heartBreathPhase = 0;
        heartLoopCount = 0;
        romanticStage = 0;
    }

    private void startCountdownSequence(int seconds, String finalMessage) {
        if (!isRomanticMode || seconds <= 0) {
            playConfessionLines(0);
            return;
        }
        final int currentSec = seconds;
        // 【#2】倒计时数字升级：颜色递进（3金→2粉→1红）+ 每个数字配中心小烟花
        int hue = currentSec >= 3 ? 45 : (currentSec == 2 ? 330 : 0);
        int satColor = currentSec >= 3 ? 0xFFFFD700 : (currentSec == 2 ? 0xFFFF69B4 : 0xFFFF2D55);
        particleTextView.setTextColorHue(hue, Math.min(360, hue + 20));
        GlobalFirework.getInstance().bloomSilently(rootLayout.getWidth() / 2f,
                rootLayout.getHeight() / 2f - 240f,
                new FireworkConfig.Builder()
                        .colors(satColor, Color.WHITE)
                        .count(scaledCount(45)).range(0.7f).trail(1.1f).build());
        particleTextView.showText(String.valueOf(currentSec), () -> {
            postRomanticCue(() -> {
                if (!isRomanticMode) return;
                particleTextView.explode(() -> {
                    startCountdownSequence(currentSec - 1, finalMessage);
                });
            }, 450);
        });
    }

    // 其中的双层旋转爱心轨迹（heartFireworkTask）亦按需移除。

    private final Runnable floatingPetalsTask = new Runnable() {
        @Override
        public void run() {
            if (!isRomanticMode) return;
            createFloatingPetal();
            mainHandler.postDelayed(this, 150 + random.nextInt(250));
        }
    };

    private ImageView obtainPetalView() {
        ImageView iv = petalPool.poll();
        if (iv == null) {
            iv = new ImageView(this);
        }
        iv.setTranslationX(0);
        iv.setTranslationY(0);
        iv.setRotation(0);
        iv.setScaleX(1f);
        iv.setScaleY(1f);
        iv.setAlpha(1f);
        return iv;
    }

    private void recyclePetalView(ImageView iv) {
        floatingContainer.removeView(iv);
        int cap = DevicePerf.get(this).maxPetalCount;
        if (petalPool.size() < cap) {
            petalPool.offer(iv);
        }
    }

    private void createFloatingPetal() {
        if (rootLayout.getWidth() == 0) return;

        ImageView petal = obtainPetalView();

        int type = random.nextInt(3);
        if (type == 0) petal.setImageResource(R.drawable.ic_heart_petal);
        else if (type == 1) petal.setImageResource(R.drawable.ic_sakura);
        else {
            petal.setImageResource(R.drawable.ic_heart_petal);
            petal.setColorFilter(Color.WHITE);
        }

        int size = 60 + random.nextInt(80);
        petal.setAlpha(0.6f + random.nextFloat() * 0.4f);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(size, size);
        params.topMargin = -200;

        petal.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        floatingContainer.addView(petal, params);

        float startX = random.nextFloat() * rootLayout.getWidth();
        petal.setTranslationX(startX);

        int duration = 6000 + random.nextInt(4000);
        float endY = rootLayout.getHeight() + 300;

        ObjectAnimator animY = ObjectAnimator.ofFloat(petal, "translationY", 0, endY);
        animY.setDuration(duration);
        animY.setInterpolator(new LinearInterpolator());

        final float swayMag = 50f + random.nextFloat() * 150f;
        final float swayFreq = 0.002f + random.nextFloat() * 0.003f;

        animY.addUpdateListener(animation -> {
            float y = (float) animation.getAnimatedValue();
            float x = startX + (float) Math.sin(y * swayFreq) * swayMag;
            petal.setTranslationX(x);
            petal.setRotation(y * 0.1f);
        });

        animY.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                recyclePetalView(petal);
            }
        });
        animY.start();
    }

    private void clearRomanticCues() {
        // 【架构】改由导演统一清空
        if (showDirector != null) showDirector.clear();
    }

    /** 【#7】浪漫排队统一入口：停止时逐个移除，杜绝打断后的残影演出 */
    private void postRomanticCue(Runnable r, long delayMs) {
        if (showDirector != null) showDirector.post(r, delayMs);
    }

    /** 【新增】跳过当前演出步骤（台词阶段即"跳到下一句"） */
    private void skipShowStep() {
        if (showDirector == null || !isRomanticMode) return;
        if (showDirector.skip()) {
            Toaster.showShort("已跳过 ⏭");
            updateShowControls();
        } else {
            Toaster.showShort("当前没有可跳过的步骤");
        }
    }

    /** 【新增】暂停/继续演出 */
    private void toggleShowPause() {
        if (showDirector == null || !isRomanticMode) return;
        boolean paused = showDirector.togglePause();
        updateShowControls();
        Toaster.showShort(paused ? "演出已暂停（点继续接着演）" : "演出继续");
    }

    /** 暂停/恢复背景音乐 */
    private void setMusicPaused(boolean paused) {
        try {
            if (mediaPlayer == null) return;
            if (paused) {
                if (mediaPlayer.isPlaying()) mediaPlayer.pause();
            } else if (!mediaPlayer.isPlaying()) {
                mediaPlayer.start();
            }
        } catch (Exception ignored) {
            // MediaPlayer 状态机很脆（stop 后未重新 prepare 就 start 会抛），失败就静默
        }
    }

    /**
     * 【新增】朗读闪避（ducking）：台词发声期间把 BGM 压到约两成，
     * 读完平滑恢复。不做处理的话人声和音乐都在 STREAM_MUSIC 上互相盖。
     */
    private void duckBgm(boolean active) {
        // 音效同样要闪避：TTS 人声和"嘭"声撞在一起，谁都听不清
        SoundFx.setDucked(active);
        try {
            if (mediaPlayer == null || !mediaPlayer.isPlaying()) return;
            final float from = active ? bgmVolume : bgmVolume * 0.22f;
            final float to = active ? bgmVolume * 0.22f : bgmVolume;
            android.animation.ValueAnimator va = android.animation.ValueAnimator.ofFloat(from, to);
            va.setDuration(active ? 180 : 420); // 压得快、放得慢，避免音乐突然冒出来
            va.addUpdateListener(a -> {
                try {
                    float v = (float) a.getAnimatedValue();
                    mediaPlayer.setVolume(v, v);
                } catch (Exception ignored) {
                }
            });
            va.start();
        } catch (Exception ignored) {
        }
    }

    /** 【新增】演出控制条：演出中浮出底部，跳过 / 暂停 / 结束 */
    private void showShowControls() {
        if (showControls == null) {
            float density = getResources().getDisplayMetrics().density;
            android.widget.LinearLayout bar = new android.widget.LinearLayout(this);
            bar.setOrientation(android.widget.LinearLayout.HORIZONTAL);
            bar.setGravity(android.view.Gravity.CENTER);
            int pad = (int) (6 * density);
            bar.setPadding(pad, pad, pad, pad);
            bar.setBackgroundResource(R.drawable.bg_bottom_bar);

            bar.addView(makeShowControlButton("⏭ 跳过", v -> skipShowStep()));
            btnPauseShow = makeShowControlButton("⏸ 暂停", v -> toggleShowPause());
            bar.addView(btnPauseShow);
            bar.addView(makeShowControlButton("✕ 结束", v -> stopRomanticMode()));

            androidx.constraintlayout.widget.ConstraintLayout.LayoutParams lp =
                    new androidx.constraintlayout.widget.ConstraintLayout.LayoutParams(-2, -2);
            lp.bottomToBottom = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID;
            lp.startToStart = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID;
            lp.endToEnd = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID;
            lp.bottomMargin = (int) (16 * density);
            rootLayout.addView(bar, lp);
            showControls = bar;
        }
        showControls.setVisibility(View.VISIBLE);
        showControls.setAlpha(0f);
        showControls.animate().alpha(1f).setDuration(300).start();
        updateShowControls();
    }

    private com.google.android.material.button.MaterialButton makeShowControlButton(
            String text, View.OnClickListener l) {
        float density = getResources().getDisplayMetrics().density;
        com.google.android.material.button.MaterialButton btn =
                new com.google.android.material.button.MaterialButton(this);
        btn.setText(text);
        btn.setTextColor(Color.WHITE);
        btn.setTextSize(12f);
        btn.setCornerRadius((int) (20 * density));
        btn.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0x99000000));
        btn.setInsetTop(0);
        btn.setInsetBottom(0);
        btn.setMinHeight(0);
        btn.setPadding((int) (14 * density), 0, (int) (14 * density), 0);
        // 走防连点：连点"跳过"会一口气跳掉好几步，连点"暂停"等于没暂停
        btn.setOnClickListener(v -> {
            if (UiGuard.tap()) l.onClick(v);
        });
        return btn;
    }

    private void updateShowControls() {
        if (btnPauseShow == null) return;
        boolean paused = showDirector != null && showDirector.isPaused();
        btnPauseShow.setText(paused ? "▶ 继续" : "⏸ 暂停");
    }

    private void hideShowControls() {
        if (showControls != null && showControls.getVisibility() == View.VISIBLE) {
            showControls.animate().alpha(0f).setDuration(250)
                    .withEndAction(() -> showControls.setVisibility(View.GONE)).start();
        }
    }

    /** 按设备性能档缩放粒子数，低端机降载 */
    private int scaledCount(int base) {
        return Math.max(20, Math.round(base * DevicePerf.get(this).fireworkCountScale));
    }

    private final Runnable autoPlayTask = new Runnable() {
        @Override
        public void run() {
            if (!isAutoPlaying) return;
            float x = 100 + random.nextFloat() * (rootLayout.getWidth() - 200);
            float y = 100 + random.nextFloat() * (rootLayout.getHeight() - 200);

            int index = random.nextInt(randomFireworkPool.length);
            GlobalFirework.getInstance().bloom(x, y, randomFireworkPool[index]);

            mainHandler.postDelayed(this, 300 + random.nextInt(900));
        }
    };

    /**
     * 长按分级蓄力（#1新）：0.5s→中、1.0s→大、1.5s→超大全屏；每级点亮火花提示
     */
    private void startCharging(float x, float y) {
        cancelCharging();
        chargeLevel = 0;
        // 【修复】纯净模式长按 = 退出纯净模式（纯净模式下按钮栏已隐藏，
        // 没有任何入口再打开菜单，长按是唯一自然的手势恢复方式）
        if (isPureMode) {
            Runnable exitPure = () -> {
                isLongPressTriggered = true; // 抑制松手时误放烟花
                if (isPureMode) togglePureMode();
            };
            chargeTasks.add(exitPure);
            mainHandler.postDelayed(exitPure, 600);
            return;
        }
        for (int lv = 1; lv <= 3; lv++) {
            final int level = lv;
            Runnable r = () -> {
                if (isRomanticMode) return; // 浪漫模式不蓄力，保持演出节奏
                chargeLevel = level;
                FireworkView fv = GlobalFirework.getInstance().getFireworkView();
                FireworkConfig spark = new FireworkConfig.Builder()
                        .colors(0xFFFFD700, Color.WHITE)
                        .count(10 + level * 6).range(0.35f).trail(0.8f).build();
                if (fv != null) fv.launch(downX, downY, spark);
            };
            chargeTasks.add(r);
            mainHandler.postDelayed(r, lv * 500);
        }
    }

    private void cancelCharging() {
        for (Runnable r : chargeTasks) mainHandler.removeCallbacks(r);
        chargeTasks.clear();
        chargeLevel = 0;
    }

    /** 蓄力释放：中/大/超大全屏 */
    private void fireChargedBurst(int level, float x, float y) {
        int w = rootLayout.getWidth();
        int h = rootLayout.getHeight();
        FireworkConfig main;
        if (level >= 3) {
            main = new FireworkConfig.Builder()
                    .shape(FireworkConfig.Shape.HEART)
                    .colors(Color.RED, 0xFFFF69B4, 0xFFFFD700)
                    .count(scaledCount(260)).range(2.2f).trail(1.8f).build();
            // 超全屏：中心巨型 + 八向环射
            FireworkView fv = GlobalFirework.getInstance().getFireworkView();
            if (fv != null) {
                FireworkConfig ring = new FireworkConfig.Builder()
                        .colors(0xFFFFD700, Color.WHITE)
                        .count(scaledCount(90)).range(1.6f).trail(1.4f).build();
                for (int i = 0; i < 8; i++) {
                    double a = Math.PI / 4 * i;
                    fv.launch(w / 2f + (float) Math.cos(a) * w * 0.28f,
                            h / 2f + (float) Math.sin(a) * h * 0.26f, ring);
                }
            }
            GlobalFirework.getInstance().playHeartbeatVibration();
        } else if (level == 2) {
            main = new FireworkConfig.Builder()
                    .shape(FireworkConfig.Shape.STAR)
                    .colors(0xFFFFD700, 0xFFFF69B4, Color.WHITE)
                    .count(scaledCount(170)).range(1.5f).trail(1.4f).build();
        } else {
            main = new FireworkConfig.Builder()
                    .shape(FireworkConfig.Shape.HEART)
                    .colors(0xFFFF69B4, Color.WHITE)
                    .count(scaledCount(100)).range(1.0f).trail(1.1f).build();
        }
        if (rocketMode) main = asRocket(main);
        bloomWithSound(x, y, main);
        // 蓄力越久，手感越重：两秒以上换成更长的渐强震动
        if (level >= 2) SoundFx.vibrateHeavy();
    }

    /**
     * 带声画的绽放。
     *
     * <p>库在 bloom() 调用的瞬间就播爆炸声，这对原地绽放是对的，
     * 但火箭模式要飞 0.5 秒才炸——声音比画面早半拍，听着很假。
     * 所以火箭模式改用无反馈发射，由我们自己排"咻 → 0.5s → 嘭 + 震动"，
     * 声画对上；原地绽放沿用库自带反馈，避免重复出声。
     */
    private void bloomWithSound(float x, float y, FireworkConfig cfg) {
        if (cfg != null && cfg.launchRocket) {
            GlobalFirework.getInstance().bloomSilently(x, y, cfg);
            SoundFx.playLaunch();
            mainHandler.postDelayed(() -> {
                SoundFx.playBoom();
                SoundFx.vibrateBurst();
            }, 480);
        } else {
            GlobalFirework.getInstance().bloom(x, y, cfg);
        }
    }

    /** 指尖发射（#2新）：复制配置并开启火箭升空（不改池内共享对象） */
    private FireworkConfig asRocket(FireworkConfig src) {
        FireworkConfig.Builder b = new FireworkConfig.Builder()
                .count(src.particleCount)
                .range(src.explosionRange)
                .trail(src.trailLength)
                .duration(src.duration)
                .sparkle(src.hasSparkle)
                .shape(src.shape)
                .text(src.text)
                .textSize(src.textSize)
                .rocket(true);
        if (src.colors != null && src.colors.length > 0) {
            b.colors(src.colors);
        }
        if (src.shapeBitmap != null) {
            b.shapeBitmap(src.shapeBitmap);
        }
        return b.build();
    }

    /** emoji 渲染成 CUSTOM 图形位图（样式扩展包 #4新） */
    /** 【#11】emoji 位图改用库统一 EmojiBitmapCache（App/壁纸共享一份） */
    private android.graphics.Bitmap emojiShapeBitmap(String emoji) {
        return EmojiBitmapCache.get(this, emoji, 220);
    }

    private void toggleAutoPlay() {
        if (isRomanticMode) stopRomanticMode();
        if (isAutoPlaying) {
            isAutoPlaying = false;
            GlobalFirework.getInstance().stopAutoShow();
            btnAutoPlay.setText("随机播放");
        } else {
            int pattern = MMKV.defaultMMKV().decodeInt(KEY_AUTO_PATTERN, GlobalFirework.PATTERN_WAVE);
            if (pattern == GlobalFirework.PATTERN_RHYTHM && !RhythmFirework.getInstance().isRunning()) {
                Toaster.showShort("节拍剧本需先开启音乐律动，已改用波浪剧本");
                pattern = GlobalFirework.PATTERN_WAVE;
            }
            isAutoPlaying = true;
            GlobalFirework.getInstance().startAutoShow(pattern, 700);
            btnAutoPlay.setText("停止播放");
        }
    }

    private void initLaunchers() {
        openGalleryLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() == RESULT_OK && result.getData() != null)
                saveAndApplyImage(result.getData().getData());
        });
        requestPermissionLauncher = registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
            if (isGranted) openGallery();
            else Toaster.showShort("需相册权限");
        });
        pickBgmLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() == RESULT_OK && result.getData() != null && result.getData().getData() != null) {
                try {
                    getContentResolver().takePersistableUriPermission(result.getData().getData(), Intent.FLAG_GRANT_READ_URI_PERMISSION);
                } catch (Exception ignored) {
                }
                bgmUri = result.getData().getData().toString();
                MMKV.defaultMMKV().encode(KEY_BGM_URI, bgmUri);
                Toaster.showShort("背景音乐已更新，将在下次浪漫模式生效");
            }
        });
        micPermissionLauncher = registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
            if (isGranted) {
                // 离线识别流程优先：模型就绪后走本地识别，不再依赖系统识别器
                if (pendingVoiceAction != null) {
                    Runnable r = pendingVoiceAction;
                    pendingVoiceAction = null;
                    r.run();
                    return;
                }
                // 同一个入口被音乐律动和语音输入复用：按待办意图分派
                if (pendingVoiceIntent != null) {
                    Intent it = pendingVoiceIntent;
                    pendingVoiceIntent = null;
                    try {
                        voiceLauncher.launch(it);
                    } catch (Exception e) {
                        Toaster.showShort("这台设备没有可用的语音识别");
                    }
                    return;
                }
                startRhythmNow();
            } else {
                pendingVoiceIntent = null;
                pendingVoiceAction = null;
                Toaster.showShort("需要麦克风权限才能录音识别");
            }
        });
        // 录屏授权回调
        projectionPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        onProjectionGranted(result.getResultCode(), result.getData());
                    } else {
                        Toaster.showShort("未授权录屏，无法导出视频");
                    }
                });

        // 照片回忆墙：多选照片后挑呈现方式
        photoWallLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() != RESULT_OK || result.getData() == null) return;
                    Intent data = result.getData();
                    java.util.List<android.net.Uri> uris = new java.util.ArrayList<>();
                    if (data.getClipData() != null) {
                        int n = Math.min(data.getClipData().getItemCount(), 24);
                        for (int i = 0; i < n; i++) {
                            android.net.Uri u = data.getClipData().getItemAt(i).getUri();
                            if (u == null) continue;
                            grantPersistable(u);
                            uris.add(u);
                        }
                    } else if (data.getData() != null) {
                        grantPersistable(data.getData());
                        uris.add(data.getData());
                    }
                    if (uris.isEmpty()) return;
                    dialogs.showPhotoWallMode(uris);
                });

        // 语音输入台词：系统识别器（国内 ROM 自带，无需联网库）
        voiceLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() != RESULT_OK || result.getData() == null) return;
                    java.util.ArrayList<String> matches = result.getData()
                            .getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS);
                    if (matches == null || matches.isEmpty()) {
                        Toaster.showShort("没听清，再说一遍？");
                        return;
                    }
                    applyVoiceResult(matches.get(0));
                });

        // 注册录制完成广播
        IntentFilter f = new IntentFilter();
        f.addAction(ScreenRecorderService.ACTION_STOPPED);
        f.addAction(ScreenRecorderService.ACTION_FAILED);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(recordStoppedReceiver, f, Context.RECEIVER_EXPORTED);
        } else {
            registerReceiver(recordStoppedReceiver, f);
        }
    }

    private void grantPersistable(android.net.Uri uri) {
        // 相册返回的 Uri 授权默认只到当前 Activity：不申请持久授权，
        // 转屏或下次再选同一批照片时就会报"权限拒绝"
        try {
            getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (Exception ignored) {
        }
    }

    /** 这台手机有没有系统语音识别器（国内 ROM 大多没有，RecognizerIntent 会静默失败）。 */
    private boolean hasSystemRecognizer() {
        Intent it = new Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        return it.resolveActivity(getPackageManager()) != null;
    }

    /** 照片回忆墙：打开相册多选（上限 24 张，再多内存与观感都吃不消）。 */
    private void openPhotoWallPicker() {
        Intent it = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        it.addCategory(Intent.CATEGORY_OPENABLE);
        it.setType("image/*");
        it.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        it.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        try {
            photoWallLauncher.launch(it);
        } catch (Exception e) {
            Toaster.showShort("这台设备打不开相册选择器");
        }
    }

    /** 语音输入台词：说出来比打字快，也更像"亲口说"。 */
    /**
     * 语音输入入口：本地离线识别优先，其次系统识别器，最后退回输入法语音键盘。
     *
     * <p>国内 ROM 基本没有 Google 语音识别，所以真正好用的第一条路是 sherpa-onnx
     * 的离线模型——但它有 25MB，塞进 APK 会让体积从 52MB 涨到 77MB，
     * 而这个功能是低频的，所以改成<b>第一次点的时候才下载</b>，下完永久离线可用。
     */
    private void startVoiceInput() {
        // ① 模型已就绪：直接开听，不联网、不依赖任何系统服务
        if (AsrModelStore.isReady(this)) {
            ensureMicPermission(this::startOfflineAsr);
            return;
        }
        // ② 没模型：先问一句要不要下（25MB 不是小数目，得让用户知道）
        ensureMicPermission(this::showAsrDownloadConfirm);
    }

    private void ensureMicPermission(Runnable onGranted) {
        if (androidx.core.content.ContextCompat.checkSelfPermission(this,
                android.Manifest.permission.RECORD_AUDIO)
                == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            onGranted.run();
            return;
        }
        pendingVoiceAction = onGranted;
        micPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO);
    }

    /** 首次下载确认：把体积、离线、隐私三件事讲清楚再让用户决定 */
    private void showAsrDownloadConfirm() {
        long bytes = AsrModelStore.totalBytes();
        new UiGuard.SafeBuilder(this)
                .setTitle("🎤 离线语音识别")
                .setMessage("首次使用需要下载语音模型，约 " + AsrModelStore.sizeText(bytes)
                        + "（只需这一次）。\n\n"
                        + "下载后完全离线可用：断网、飞行模式也能识别，"
                        + "录音全程在本机处理，不上传任何服务器。\n\n"
                        + "不想下载也可以用输入法自带的🎤语音键盘直接说。")
                .setPositiveButton("下载并识别", (d, w) -> startAsrDownload())
                .setNegativeButton("用输入法说", (d, w) -> fallbackToImeVoice())
                .show();
    }

    /**
     * 模型下载对话框（识别模型 / 音色模型共用）：进度条 + 已下/总量 + 可取消。
     *
     * <p>抽出来是因为两个模型的下载 UI 完全一样，只有"下完之后干什么"不同。
     */
    private void runModelDownload(String title,
                                  java.util.function.Consumer<ModelDownloader.Progress> starter,
                                  Runnable onOk,
                                  java.util.function.Consumer<String> onFail) {
        android.widget.ProgressBar bar = new android.widget.ProgressBar(this, null,
                android.R.attr.progressBarStyleHorizontal);
        bar.setMax(100);
        android.widget.TextView tv = new android.widget.TextView(this);
        tv.setTextColor(0xFF8899AA);
        tv.setTextSize(12f);
        tv.setPadding(0, 12, 0, 0);
        tv.setText("准备中…");
        // 单独一行说明"正在下第几个文件"：慢网下进度条一动不动时，
        // 有这行字用户才知道还活着，而不是以为卡死了
        android.widget.TextView tvStage = new android.widget.TextView(this);
        tvStage.setTextColor(0xFF66788A);
        tvStage.setTextSize(11f);
        tvStage.setPadding(0, 4, 0, 0);
        tvStage.setText("正在连接…");
        android.widget.LinearLayout box = new android.widget.LinearLayout(this);
        box.setOrientation(android.widget.LinearLayout.VERTICAL);
        float dp = getResources().getDisplayMetrics().density;
        box.setPadding((int) (22 * dp), (int) (8 * dp), (int) (22 * dp), (int) (8 * dp));
        box.addView(bar);
        box.addView(tv);
        box.addView(tvStage);

        final boolean[] cancelled = {false};
        androidx.appcompat.app.AlertDialog dlg = new UiGuard.SafeBuilder(this)
                .setTitle(title)
                .setView(box)
                .setCancelable(false)
                .setNegativeButton("取消", (d, w) -> {
                    cancelled[0] = true;
                    Toaster.showShort("已取消下载");
                })
                .show();

        starter.accept(new ModelDownloader.Progress() {
            @Override
            public boolean isCancelled() {
                return cancelled[0];
            }

            @Override
            public void onStage(String fileName, int index, int count) {
                if (!dlg.isShowing()) return;
                tvStage.setText("文件 " + index + "/" + count + "：" + fileName);
            }

            @Override
            public void onProgress(long done, long total) {
                if (!dlg.isShowing()) return;   // 用户取消了，别再往已关的窗口里塞数据
                int pct = (int) Math.min(99, done * 100 / Math.max(1, total));
                bar.setProgress(pct);
                tv.setText(ModelDownloader.sizeText(done) + " / " + ModelDownloader.sizeText(total)
                        + "   " + pct + "%");
            }

            @Override
            public void onDone(boolean ok, String message) {
                if (!dlg.isShowing()) return;
                dlg.dismiss();
                if (ok) {
                    onOk.run();
                } else {
                    onFail.accept(message);
                }
            }
        });
    }

    /** 下载识别模型，带进度；成功后立刻进入识别，失败则退回输入法方案 */
    private void startAsrDownload() {
        runModelDownload("正在下载语音模型",
                p -> AsrModelStore.download(this, p),
                () -> {
                    Toaster.showShort("模型就绪，开始听你说 🎤");
                    startOfflineAsr();
                },
                msg -> new UiGuard.SafeBuilder(this)
                        .setTitle("语音模型下载失败")
                        .setMessage(msg + "\n\n可以换个网络（比如 Wi-Fi）再试；"
                                + "已经下好的文件不会重复下载。也可以先改用输入法的 🎤 说话。")
                        .setPositiveButton("重试", (d, w) -> startAsrDownload())
                        .setNegativeButton("用输入法说", (d, w) -> fallbackToImeVoice())
                        .show());
    }

    // ===== 离线朗读音色：启动检测 → 不支持才让用户决定要不要下 =====

    /**
     * 启动后检测这台手机能不能读中文；读不了才问用户要不要下载离线音色。
     *
     * <p>顺序上刻意"能不弹就不弹"：本地音色已下载 / 系统能读中文 / 用户说过暂时不用，
     * 三种情况都直接跳过。只有确认读不出来时才打扰——表白前跳个下载弹窗很败兴。
     */
    private void maybePromptTtsVoice() {
        if (speech == null || isFinishing() || isDestroyed()) return;
        if (SpeechEngine.getMode() == SpeechEngine.MODE_OFF) return;
        if (speech.isLocalModelReady()) return;              // 已有本地音色，一定能读
        if (SpeechEngine.ttsPromptDisabled()) return;        // 用户选过"暂时不用"

        boolean needLocal = SpeechEngine.getMode() == SpeechEngine.MODE_LOCAL;
        if (needLocal) {
            // 用户点名只要本地音色：没什么好探的，直接引导下载
            showTtsDownloadOffer();
            return;
        }
        if (SpeechEngine.cachedSystemChinese() == 1) return;   // 上次确认能读中文，省一次引擎初始化
        // 其余情况（没探过 / 上次探到不支持）都重探一次：
        // 用户中途装了中文语音包时，结论要能自己翻回来，不能一辈子缠着人家下载
        speech.probeSystemChinese(ok -> {
            if (!ok && !isFinishing() && !isDestroyed() && !isRomanticMode) showTtsDownloadOffer();
        });
    }

    /** 系统读不了中文时的下载询问：先讲清楚为什么，再让用户选 */
    private void showTtsDownloadOffer() {
        if (isFinishing() || isDestroyed()) return;
        TtsVoice v = TtsVoice.current();
        String size = TtsModelStore.sizeText(v.totalBytes());
        new UiGuard.SafeBuilder(this)
                .setTitle("🔊 台词朗读需要音色")
                .setMessage("检测到这台手机的系统语音引擎没有中文语音包，"
                        + "演出时台词会一句都读不出来。\n\n"
                        + "可以下载离线音色「" + v.name + "」（约 " + size + "，只需这一次）：\n"
                        + "· 下载后完全离线，断网、飞行模式也能读；\n"
                        + "· 音色统一稳定，不随手机品牌变化。\n\n"
                        + "之后还可在「台词朗读」里换其他音色、"
                        + "或用变声档调出男声/娃娃音（不用再下载）。\n\n"
                        + "不想下载，也可以到系统设置里装中文语音包（不占本 App 空间）。")
                .setPositiveButton("下载 " + size, (d, w) -> startTtsDownload(v))
                .setNegativeButton("去系统设置", (d, w) -> SpeechEngine.openSystemTtsSettings(this))
                .setNeutralButton("暂时不用", (d, w) -> {
                    // 记下来：下次启动不再问，但菜单里随时可以自己下
                    SpeechEngine.setTtsPromptDisabled(true);
                    Toaster.showShort("已跳过，之后可在「台词朗读」里下载");
                })
                .show();
    }

    /** 选用一个已下载的音色：切过去、预热、试听 */
    private void applyTtsVoice(TtsVoice v) {
        if (speech == null) return;
        speech.stop();
        TtsVoice.setCurrent(v);
        speech.resetLocal();
        speech.warmUp();
        Toaster.showShort("音色：" + v.name);
        mainHandler.postDelayed(() -> speech.speak("遇见你真好"), 800);
    }

    private void startTtsDownload(TtsVoice v) {
        runModelDownload("正在下载音色「" + v.name + "」",
                p -> TtsModelStore.download(this, v, p),
                () -> {
                    Toaster.showShort("音色就绪，给你试听一句 🔊");
                    if (speech == null) return;
                    speech.resetLocal();       // 之前 failed 的引擎要重建，否则仍被判不可用
                    speech.warmUp();
                    mainHandler.postDelayed(() -> speech.speak("遇见你真好"), 900);
                },
                msg -> showDownloadFailed("音色「" + v.name + "」下载失败", msg,
                        () -> startTtsDownload(v)));
    }

    /**
     * 下载失败后的重试入口。
     *
     * <p>以前只弹一句"下载失败"，用户得自己回菜单重新点一遍，而且看着像要重下 20MB。
     * 现在明确告诉他失败在哪个文件、为什么，并且说明已下好的部分会留着——
     * 有了断点续传，重试是真的接着下，不是从头再来。
     */
    private void showDownloadFailed(String title, String reason, Runnable retry) {
        new UiGuard.SafeBuilder(this)
                .setTitle(title)
                .setMessage(reason + "\n\n可以换个网络（比如 Wi-Fi）再试；"
                        + "已经下好的模型文件不会重复下载。")
                .setPositiveButton("重试", (d, w) -> retry.run())
                .setNegativeButton("以后再说", null)
                .show();
    }

    /** 清理已下载的音色，释放空间 */
    private void confirmDeleteTtsModel() {
        TtsVoice v = TtsVoice.current();
        new UiGuard.SafeBuilder(this)
                .setTitle("清理音色「" + v.name + "」")
                .setMessage("将删除已下载的这套音色（约 "
                        + TtsModelStore.sizeText(v.totalBytes())
                        + "），台词朗读改由系统引擎承担。\n\n需要时可随时重新下载。")
                .setPositiveButton("删除", (d, w) -> {
                    if (TtsModelStore.delete(this, v)) {
                        if (speech != null) speech.resetLocal();
                        Toaster.showShort("已清理「" + v.name + "」");
                    } else {
                        Toaster.showShort("清理失败");
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    /** 打开离线识别对话框：边说边出字，停顿自动结束 */
    private void startOfflineAsr() {
        if (asr == null) asr = new OfflineAsr();
        if (!asr.load(this)) {
            Toaster.showLong("语音模型加载失败，改用输入法的🎤说");
            fallbackToImeVoice();
            return;
        }
        View v = getLayoutInflater().inflate(R.layout.dialog_voice_asr, null);
        android.widget.TextView tvText = v.findViewById(R.id.tv_asr_text);
        androidx.appcompat.app.AlertDialog dlg = new UiGuard.SafeBuilder(this)
                .setView(v)
                .setCancelable(false)
                .show();
        if (dlg == null) {
            asr.release();
            return;
        }

        asr.setListener(new OfflineAsr.Listener() {
            @Override
            public void onPartial(String text) {
                tvText.setText(text.isEmpty() ? "（在听…）" : text);
            }

            @Override
            public void onFinal(String text) {
                if (dlg.isShowing()) dlg.dismiss();
                duckBgm(false);
                asr.release();
                if (text.isEmpty()) {
                    Toaster.showShort("没听清，再说一次 🎤");
                } else {
                    applyVoiceResult(text);
                }
            }

            @Override
            public void onError(String message) {
                if (dlg.isShowing()) dlg.dismiss();
                duckBgm(false);
                asr.release();
                Toaster.showShort(message);
            }
        });
        v.findViewById(R.id.btn_asr_done).setOnClickListener(x -> {
            if (UiGuard.tap()) asr.stop();
        });
        v.findViewById(R.id.btn_asr_cancel).setOnClickListener(x -> {
            asr.cancel();
            if (dlg.isShowing()) dlg.dismiss();
            duckBgm(false);
            asr.release();
        });
        // 录音期间压低背景音乐：不然 BGM 会被一起录进去，识别结果全是噪音
        duckBgm(true);
        asr.start();
    }

    /**
     * 兜底方案：打开台词输入框，让用户用输入法的语音键盘说。
     * 百度/讯飞/搜狗/微信输入法都带，零依赖、国内必可用。
     */
    private void fallbackToImeVoice() {
        Toaster.showLong("已打开台词框：\n把输入法切到语音键盘（长按空格或点🎤键）直接说");
        showRomanticDialog();
    }

    /** 识别结果按标点断句：一句话一朵烟花，比整段糊在一起好看得多。 */
    private void applyVoiceResult(String text) {
        if (text == null || text.trim().isEmpty()) return;
        String t = text.trim();
        java.util.List<String> lines = new java.util.ArrayList<>();
        for (String p : t.split("[，,。、！!？?；;：:]")) {
            if (!p.trim().isEmpty()) lines.add(p.trim());
        }
        if (lines.isEmpty()) lines.add(t);
        if (lines.size() > RomanticShareCodec.MAX_LINES) {
            lines = new java.util.ArrayList<>(lines.subList(0, RomanticShareCodec.MAX_LINES));
        }
        romanticLines = lines;
        Toaster.showShort("听到了：" + t + "（可在剧本库存下来）");
    }

    /** 星空许愿墙：开时可以点星星看愿望、点空白许新愿。 */
    private void toggleWishSky() {
        if (wishSky != null) {
            closeWishSky();
            return;
        }
        wishSky = new WishSkyView(this);
        wishSky.setListener(new WishSkyView.Listener() {
            @Override
            public void onWishTapped(WishStore.Wish w) {
                Toaster.showShort("🌟 " + w.text + "（" + WishStore.ago(w.ts) + "）");
            }

            @Override
            public void onBlankTapped(float xRatio, float yRatio) {
                dialogs.showWishInput(xRatio, yRatio);
            }
        });
        // 必须高于 ParticleTextView（10dp）：否则整层被粒子文字压在下面，
        // 触摸被吃掉、星星也看不清，表现就是"点了没反应"。
        wishSky.setElevation(12f);
        rootLayout.addView(wishSky, new androidx.constraintlayout.widget.ConstraintLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT));
        Toaster.showShort("许愿模式：点星星看愿望，点空白许个新的 🌟");
    }

    private void closeWishSky() {
        if (wishSky == null) return;
        rootLayout.removeView(wishSky);
        wishSky = null;
        Toaster.showShort("已收起许愿星空");
    }

    private void checkPermissionAndOpenGallery() {
        String p = Build.VERSION.SDK_INT >= 33 ? Manifest.permission.READ_MEDIA_IMAGES : Manifest.permission.READ_EXTERNAL_STORAGE;
        if (ContextCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_GRANTED)
            openGallery();
        else requestPermissionLauncher.launch(p);
    }

    private void openGallery() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        openGalleryLauncher.launch(intent);
    }

    private void saveAndApplyImage(Uri uri) {
        try {
            getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (Exception e) {
        }
        MMKV.defaultMMKV().encode(KEY_BG_TYPE, 1);
        MMKV.defaultMMKV().encode(KEY_BG_URI, uri.toString());
        setBackgroundFromUri(uri);
    }

    private void restoreBackground() {
        MMKV mmkv = MMKV.defaultMMKV();
        if (mmkv.decodeInt(KEY_BG_TYPE, 0) == 0) {
            int color = mmkv.decodeInt(KEY_BG_COLOR, Color.BLACK);
            rootLayout.setBackground(null);
            rootLayout.setBackgroundColor(color);
        } else {
            try {
                setBackgroundFromUri(Uri.parse(mmkv.decodeString(KEY_BG_URI, "")));
            } catch (Exception e) {
                rootLayout.setBackgroundColor(Color.BLACK);
            }
        }
    }

    private void setBackgroundFromUri(Uri uri) {
        // 低端机：按档位采样压缩后再后台解码，避免全屏大图在主线程 OOM / 卡顿
        final int inSample = DevicePerf.get(this).backgroundInSampleSize;
        final Uri target = uri;
        new Thread(() -> {
            try {
                android.graphics.BitmapFactory.Options opts = new android.graphics.BitmapFactory.Options();
                opts.inSampleSize = inSample;
                InputStream is = getContentResolver().openInputStream(target);
                android.graphics.Bitmap bmp = android.graphics.BitmapFactory.decodeStream(is, null, opts);
                if (is != null) try { is.close(); } catch (Exception ignored) {}
                if (bmp == null) {
                    mainHandler.post(() -> rootLayout.setBackgroundColor(Color.BLACK));
                    return;
                }
                final android.graphics.drawable.BitmapDrawable d =
                        new android.graphics.drawable.BitmapDrawable(getResources(), bmp);
                mainHandler.post(() -> {
                    if (rootLayout != null) rootLayout.setBackground(d);
                });
            } catch (Exception e) {
                mainHandler.post(() -> rootLayout.setBackgroundColor(Color.BLACK));
            }
        }, "bg-decode").start();
    }

    /**
     * 【新增】告白文案库：先选场景，再选具体一套，选中后填入台词框（仍可自由修改）。
     *
     * @param target 浪漫设置弹窗里的台词输入框
     */

    /**
     * 发射指定文字的烟花（复用工具方法）
     */
    private void launchTextFirework(float x, float y, String text) {
        // 非默认字体：库不认外部 Typeface，只能先栅格化再按位图布点
        int font = FontKit.current();
        if (FontKit.needsBitmap(font)) {
            android.graphics.Bitmap bmp = FontKit.render(this, text, font, 420);
            if (bmp != null) {
                FireworkConfig cfg = FontKit.config(bmp, 0f);
                FireworkTuning.apply(cfg);
                GlobalFirework.getInstance().bloom(x, y, cfg);
                return;
            }
        }
        FireworkConfig textConfig = new FireworkConfig.Builder()
                .shape(FireworkConfig.Shape.TEXT)
                .text(text)
                .colors(Color.RED, Color.YELLOW)
                .range(1.2f)
                .build();
        FireworkTuning.apply(textConfig);
        GlobalFirework.getInstance().bloom(x, y, textConfig);
    }

    /**
     * 手写签名烟花：笔迹位图交给库的 CUSTOM 采样器，火箭升空后按笔迹形状炸开。
     *
     * <p>save=true 时同时存成常用签名：之后每一张纪念卡片都会印上这枚亲笔签名，
     * 也算留下"这场烟花是他亲手写的"的证据。
     */
    private void bloomSignature(android.graphics.Bitmap bmp, boolean save) {
        if (bmp == null) return;
        if (save) SignatureStore.save(this, bmp);

        // 自定义形状的粒子数由库的采样步长决定（count 不生效），
        // 所以这里能做的是别把字撑太散、并让它多停留一会儿——
        // range 从 1.7 收到 1.2，粒子间距小，笔画才连得住。
        FireworkConfig cfg = new FireworkConfig.Builder()
                .shape(FireworkConfig.Shape.CUSTOM)
                .shapeBitmap(bmp)
                .colors(0xFFFFD700, 0xFFFF69B4, Color.WHITE)
                .count(scaledCount(280))
                .range(1.2f)
                .trail(1.1f)
                .duration(3.4f)
                .sparkle(true)
                .build();
        bloomWithSound(rootLayout.getWidth() / 2f, rootLayout.getHeight() * 0.42f, asRocket(cfg));
        Toaster.showShort(save ? "签名已保存，也正在为你炸开 ✍" : "你写的字，炸成烟花了 ✍");

        // 签名散去后补三朵爱心，给这一笔一个收尾（不走演出队列，避免干扰正式演出）
        for (int i = 0; i < 3; i++) {
            final int idx = i;
            mainHandler.postDelayed(() -> {
                if (isFinishing() || isDestroyed()) return;
                bloomWithSound(rootLayout.getWidth() * (0.28f + 0.22f * idx),
                        rootLayout.getHeight() * (0.35f + 0.12f * (idx % 2)),
                        romanticHeartConfig);
            }, 3200L + idx * 320L);
        }
    }

    /** 用户主动切换：保留 1 秒冷却，防止快速反复触发导致动画叠加闪烁 */
    private void togglePureMode() {
        long now = System.currentTimeMillis();
        if (now - lastPureModeSwitchTime < PURE_MODE_SWITCH_COOLDOWN) {
            return;
        }
        lastPureModeSwitchTime = now;
        setPureMode(!isPureMode, true);
    }

    /**
     * 直接设置纯净模式，不走冷却（录屏等自动化场景需要可靠生效）。
     *
     * @param persist 是否写入 MMKV：录屏只是临时收起底栏，不应覆盖用户的长期偏好
     */
    private void setPureMode(boolean on, boolean persist) {
        if (isPureMode == on) return;
        isPureMode = on;

        llBottomBar.animate().cancel();
        llBottomBar.clearAnimation();

        if (on) {
            llBottomBar.animate()
                    .alpha(0f)
                    .setDuration(200)
                    .withEndAction(() -> llBottomBar.setVisibility(View.GONE))
                    .start();
        } else {
            llBottomBar.setVisibility(View.VISIBLE);
            llBottomBar.setAlpha(0f);
            llBottomBar.animate()
                    .alpha(1f)
                    .setDuration(200)
                    .start();
        }
        if (persist) {
            MMKV.defaultMMKV().encode(KEY_PURE_MODE, isPureMode);
            Toaster.showShort(on ? "已进入纯净模式（长按屏幕退出）" : "已退出纯净模式");
        }
    }

    /**
     * 弹出更多功能菜单
     */
    /**
     * 返回键守卫：演出/录制进行中，第一次按返回只提示，两秒内再按才真退出。
     *
     * <p>表白场景里这个误触代价太高——手机从口袋掏出来、或者对方随手一按，
     * 整场演出就没了，还得重开一遍。非演出状态不打扰，按了就直接退。
     */
    private void setupBackGuard() {
        final boolean[] armed = {false};
        getOnBackPressedDispatcher().addCallback(this,
                new androidx.activity.OnBackPressedCallback(true) {
                    @Override
                    public void handleOnBackPressed() {
                        boolean busy = isRomanticMode || isAutoPlaying || isRecording;
                        if (!busy || !armed[0]) {
                            if (!busy) {
                                finish();
                                return;
                            }
                            armed[0] = true;
                            Toaster.showShort("再按一次退出（演出还在进行）");
                            mainHandler.postDelayed(() -> armed[0] = false, 2000);
                            return;
                        }
                        finish();
                    }
                });
    }

    /**
     * 菜单项统一绑定。
     *
     * <p>三件小事都收在这里，免得 33 个菜单项各写一遍：
     * ① 600ms 内的连点直接丢掉（手快点两下会叠出两层弹窗）；
     * ② 先收气泡，再延后一帧执行——气泡收起和弹窗弹出抢焦点的话，
     *    表现就是"点了没反应"；
     * ③ 找不到这个 id 就静默跳过，别让某个菜单项的 id 写错就整页崩。
     */
    private void bindMenu(Balloon balloon, View menuView, int id, Runnable action) {
        View v = menuView.findViewById(id);
        if (v == null) return;
        v.setOnClickListener(x -> {
            if (!UiGuard.tap()) return;
            balloon.dismiss();
            mainHandler.post(action);
        });
    }

    private void showMoreMenu(View anchor) {
        Balloon balloon = new Balloon.Builder(this)
                .setLayout(R.layout.layout_more_menu)
                .setWidthRatio(0.35f) // 菜单宽度占屏幕比例，横屏适配友好
                .setHeight(BalloonSizeSpec.WRAP)
                .setBackgroundColor(Color.parseColor("#CC111111")) // 半透明深黑，和按钮风格统一
                .setCornerRadius(16f) // 圆角
                .setArrowSize(10) // 指向箭头大小
                .setArrowOrientation(ArrowOrientation.BOTTOM) // 箭头指向底部的「更多」按钮
                .setElevation(8) // 阴影
                .setDismissWhenClicked(false) // 【修复】滚动菜单的手势会被误判为点击而关闭弹窗；各菜单项自行 dismiss
                .setDismissWhenTouchOutside(true)
                .setLifecycleOwner(this) // 自动绑定生命周期，防泄漏
                .build();

        // 绑定点击事件
        View menuView = balloon.getContentView();

        // ===== 【修复】菜单无法滑动 =====
        // 根因：ScrollView 的 android:maxHeight 属性无效（ScrollView 不支持该属性），
        // 菜单全高展开后弹窗超出屏幕，ScrollView 因内容不超自身高度而永不滚动。
        // 修复：代码里动态限制为屏高 60%，内容超出后即可正常滚动。
        android.widget.ScrollView menuScroll = menuView.findViewById(R.id.menu_scroll);
        if (menuScroll != null) {
            int maxHeight = (int) (getResources().getDisplayMetrics().heightPixels * 0.60f);
            menuScroll.getLayoutParams().height = maxHeight;
            menuScroll.setLayoutParams(menuScroll.getLayoutParams());
            // 菜单有 33 项，每次打开都从顶部开始滚太累：回到上次看到的位置
            menuScroll.post(() -> menuScroll.scrollTo(0, lastMenuScrollY));
            menuScroll.setOnScrollChangeListener((v, sx, sy, ox, oy) -> lastMenuScrollY = sy);
        }
        bindMenu(balloon, menuView, R.id.tv_custom_text, () -> {
            mainHandler.post(() -> dialogs.showCustomTextDialog());
        });

        bindMenu(balloon, menuView, R.id.tv_change_bg, () -> {
            mainHandler.post(() -> dialogs.showColorPickerDialog());
        });

        bindMenu(balloon, menuView, R.id.tv_pick_image, () -> {
            checkPermissionAndOpenGallery();
        });

        bindMenu(balloon, menuView, R.id.tv_import_share_code, () -> {
            importRomanticCodeFromClipboard();
        });

        bindMenu(balloon, menuView, R.id.tv_export_video, () -> {
            requestExportVideo();
        });

        bindMenu(balloon, menuView, R.id.tv_set_wallpaper, () -> {
            goSetLiveWallpaper();
        });

        bindMenu(balloon, menuView, R.id.tv_weather, () -> {
            mainHandler.post(() -> dialogs.showWeatherPicker());
        });

        bindMenu(balloon, menuView, R.id.tv_lantern, () -> {
            toggleLanternTapMode();
        });

        bindMenu(balloon, menuView, R.id.tv_moon, () -> {
            toggleMoonConstellation();
        });

        bindMenu(balloon, menuView, R.id.tv_rhythm, () -> {
            toggleMusicRhythm();
        });

        bindMenu(balloon, menuView, R.id.tv_pure_mode, () -> {
            togglePureMode();
        });

        bindMenu(balloon, menuView, R.id.tv_presets, () -> {
            mainHandler.post(() -> showRomanticDialog()); // 打开的弹窗里就能挑文案
        });

        bindMenu(balloon, menuView, R.id.tv_rocket_mode, () -> {
            rocketMode = !rocketMode;
            MMKV.defaultMMKV().encode(KEY_ROCKET_MODE, rocketMode);
            Toaster.showShort(rocketMode ? "指尖发射已开启：点击后火箭升空再炸开 🚀" : "已恢复原地绽放");
        });

        bindMenu(balloon, menuView, R.id.tv_auto_pattern, () -> {
            mainHandler.post(() -> dialogs.showAutoPatternPicker());
        });

        bindMenu(balloon, menuView, R.id.tv_afterglow, () -> {
            toggleAfterglow();
        });

        bindMenu(balloon, menuView, R.id.tv_templates, () -> {
            // Balloon 收起与弹窗弹出同一帧发生会抢焦点，表现为"点了没反应"。延后一帧。
            mainHandler.post(() -> dialogs.showShowTemplatePicker());
        });

        bindMenu(balloon, menuView, R.id.tv_photos, () -> {
            openPhotoWallPicker();
        });

        bindMenu(balloon, menuView, R.id.tv_font, () -> {
            mainHandler.post(() -> dialogs.showFontPicker());
        });

        bindMenu(balloon, menuView, R.id.tv_tuning, () -> {
            mainHandler.post(() -> dialogs.showTuningDialog());
        });

        bindMenu(balloon, menuView, R.id.tv_voice, () -> {
            startVoiceInput();
        });

        bindMenu(balloon, menuView, R.id.tv_wish, () -> {
            toggleWishSky();
        });

        bindMenu(balloon, menuView, R.id.tv_signature, () -> {
            mainHandler.post(() -> dialogs.showSignatureDialog());
        });

        bindMenu(balloon, menuView, R.id.tv_scripts, () -> {
            mainHandler.post(() -> dialogs.showScriptLibrary());
        });

        bindMenu(balloon, menuView, R.id.tv_qr_code, () -> {
            mainHandler.post(() -> dialogs.showQrForCurrentLines());
        });

        bindMenu(balloon, menuView, R.id.tv_schedule, () -> {
            mainHandler.post(() -> dialogs.showScheduleManager());
        });

        bindMenu(balloon, menuView, R.id.tv_sound_fx, () -> {
            mainHandler.post(() -> dialogs.showSoundFxPicker());
        });

        bindMenu(balloon, menuView, R.id.tv_anniversary, () -> {
            mainHandler.post(() -> showAnniversaryList());
        });

        bindMenu(balloon, menuView, R.id.tv_speech, () -> {
            showSpeechPicker();
        });

        bindMenu(balloon, menuView, R.id.tv_theme, () -> {
            mainHandler.post(() -> dialogs.showThemePicker());
        });

        bindMenu(balloon, menuView, R.id.tv_export_gif, () -> {
            exportLatestVideoAsGif();
        });

        bindMenu(balloon, menuView, R.id.tv_style_switch, () -> {
            showStyleSwitchDialog();
        });

        bindMenu(balloon, menuView, R.id.tv_crash_log, () -> {
            mainHandler.post(() -> showCrashLogDialog());
        });

        bindMenu(balloon, menuView, R.id.tv_guide, () -> {
            mainHandler.post(() -> dialogs.showGuide(true));
        });

        // 菜单状态（UI 优化）：开关项显示"已开启/已关闭"，选择项显示当前值，
        // 取代原来的 ●/○ 小圆点 —— 圆点无法表达"没点亮=关闭"的语义，用户看不懂。
        updateMenuStates(menuView);

        // 显示在按钮上方
        balloon.showAsDropDown(anchor, 0, -16);
    }

    /** 【#17】节日主题选择 */

    /** 【#17】应用主题：背景色 + 天气 + 专属烟花池 */
    private void applyTheme(int theme) {
        currentTheme = theme;
        MMKV.defaultMMKV().encode(KEY_THEME, theme);
        if (theme == ThemeManager.THEME_DEFAULT) {
            MMKV.defaultMMKV().encode(KEY_BG_TYPE, 0);
            MMKV.defaultMMKV().encode(KEY_BG_COLOR, Color.BLACK);
            rootLayout.setBackground(null);
            rootLayout.setBackgroundColor(Color.BLACK);
            starView.setWeather(SkyView.WEATHER_NONE);
            MMKV.defaultMMKV().encode(KEY_WEATHER, SkyView.WEATHER_NONE);
        } else {
            MMKV.defaultMMKV().encode(KEY_BG_TYPE, 0);
            MMKV.defaultMMKV().encode(KEY_BG_COLOR, ThemeManager.bgColor(theme));
            rootLayout.setBackground(null);
            rootLayout.setBackgroundColor(ThemeManager.bgColor(theme));
            starView.setWeather(ThemeManager.weather(theme));
            MMKV.defaultMMKV().encode(KEY_WEATHER, ThemeManager.weather(theme));
        }
        initFireworkConfigs(); // 重建主题烟花池
    }

    /** 【#20】把最近一次录制的 MP4 转成 GIF 动图 */
    private void exportLatestVideoAsGif() {
        // 录屏成品在 App 私有目录（分区存储改造后），成功入册后公共相册也有一份，两处都找
        File latest = findLatestRecording();
        if (latest == null) {
            Toaster.showShort("还没有录制的视频，请先导出浪漫视频");
            return;
        }
        Toaster.showShort("正在转换：" + latest.getName());
        new Thread(() -> {
            String out = GifExporter.convert(this, latest.getAbsolutePath());
            mainHandler.post(() -> {
                if (!isFinishing() && !isDestroyed()) {
                    if (out != null) {
                        Toaster.showLong("GIF 已保存：" + out);
                    } else {
                        Toaster.showShort("转换失败（视频可能过长或格式异常）");
                    }
                }
            });
        }, "gif-export").start();
    }

    /** 按修改时间找出最近一次录制的 MP4（私有目录优先，兼容历史公共目录文件） */
    private File findLatestRecording() {
        File best = null;
        File[] dirs = {
                new File(getExternalFilesDir(android.os.Environment.DIRECTORY_MOVIES), "RomanticFirework"),
                new File(android.os.Environment.getExternalStoragePublicDirectory(
                        android.os.Environment.DIRECTORY_MOVIES), "RomanticFirework")
        };
        for (File dir : dirs) {
            File[] files = dir.listFiles((f, n) -> n.endsWith(".mp4"));
            if (files == null) continue;
            for (File f : files) {
                if (best == null || f.lastModified() > best.lastModified()) best = f;
            }
        }
        return best;
    }

    /** 当前天气名称（用于菜单状态显示） */
    private String currentWeatherName() {
        String[] names = {"关闭", "下雪", "樱花", "银杏"};
        int w = starView != null ? starView.getWeather() : 0;
        return (w >= 0 && w < names.length) ? names[w] : "关闭";
    }

    /** 当前随机剧本名称 */
    private String currentPatternName() {
        String[] names = {"经典随机", "波浪扫射", "环形聚拢", "音乐节拍"};
        int p = MMKV.defaultMMKV().decodeInt(KEY_AUTO_PATTERN, 1);
        return (p >= 0 && p < names.length) ? names[p] : names[1];
    }

    /** 已启用的烟花样式数量 */
    private String currentStyleSummary() {
        if (allPoolNames == null || allPoolNames.length == 0) return "点击配置";
        int on = 0;
        for (String n : allPoolNames) {
            if (isStyleEnabled(n)) on++;
        }
        return on + "/" + allPoolNames.length + " 种";
    }

    private void updateMenuStates(View menuView) {
        // ===== 开关类：尾部状态文字，开=金色，关=灰色 =====
        setMenuToggle(menuView, R.id.tv_lantern, "🏮 孔明灯模式", lanternTapMode);
        setMenuToggle(menuView, R.id.tv_moon, "🌙 月亮与星座",
                MMKV.defaultMMKV().decodeBool(KEY_MOON, true));
        setMenuToggle(menuView, R.id.tv_rhythm, "🎵 音乐律动", rhythmOn);
        setMenuToggle(menuView, R.id.tv_rocket_mode, "🚀 指尖发射", rocketMode);
        setMenuToggle(menuView, R.id.tv_afterglow, "✨ 烟花余辉",
                MMKV.defaultMMKV().decodeBool(KEY_AFTERGLOW, true));
        setMenuToggle(menuView, R.id.tv_pure_mode, "🌌 纯净模式", isPureMode);

        // ===== 选择类：尾部显示当前值 =====
        setMenuValue(menuView, R.id.tv_weather, "🌦 天气粒子", currentWeatherName());
        setMenuValue(menuView, R.id.tv_theme, "🎊 节日主题", ThemeManager.name(currentTheme));
        setMenuValue(menuView, R.id.tv_auto_pattern, "🎞 随机剧本", currentPatternName());
        setMenuValue(menuView, R.id.tv_style_switch, "🎆 烟花样式", currentStyleSummary());
        setMenuValue(menuView, R.id.tv_speech, "🔊 台词朗读",
                speech != null ? speech.currentSourceName() : "已关闭");
        setMenuValue(menuView, R.id.tv_anniversary, "📅 纪念日", AnniversaryReminder.summary());
        setMenuValue(menuView, R.id.tv_crash_log, "🐞 崩溃日志",
                CrashLogger.hasCrash() ? "有记录 · 点开发给我" : "无记录");

        // ===== 创作与自动化 =====
        setMenuValue(menuView, R.id.tv_sound_fx, "🔔 音效与触感", SoundFx.statusText());
        setMenuValue(menuView, R.id.tv_signature, "✍ 手写签名烟花",
                SignatureStore.has(this) ? "已有签名" : "还没写");
        setMenuValue(menuView, R.id.tv_photos, "🖼 照片回忆墙",
                photoWall != null && photoWall.isRunning() ? "正在播放" : "选照片");
        setMenuValue(menuView, R.id.tv_font, "✍ 文字烟花字体", FontKit.NAMES[FontKit.current()]);
        setMenuValue(menuView, R.id.tv_tuning, "🎛 烟花手感", FireworkTuning.summary());
        setMenuValue(menuView, R.id.tv_voice, "🎤 语音输入台词",
                AsrModelStore.isReady(this) ? "离线识别 · 已就绪"
                        : "首次需下载 " + AsrModelStore.sizeText(AsrModelStore.totalBytes()));
        setMenuValue(menuView, R.id.tv_wish, "🌟 星空许愿墙",
                (wishSky != null ? "已展开 · " : "") + WishStore.load().size() + " 个愿望");
        setMenuValue(menuView, R.id.tv_scripts, "📜 我的剧本库", ScriptStore.load().size() + " 套");
        java.util.List<ScheduledShow.Entry> sched = ScheduledShow.load();
        setMenuValue(menuView, R.id.tv_schedule, "⏰ 定时自动演出",
                sched.isEmpty() ? "未设置"
                        : sched.size() + " 个 · " + ScheduledShow.describe(sched.get(0).timeMillis));

        // ===== 动作类：给一句"点了会发生什么"，别让用户靠猜 =====
        String txt = customFireworkText == null ? "" : customFireworkText.trim();
        setMenuValue(menuView, R.id.tv_custom_text, "✍ 自定义文字",
                txt.isEmpty() ? "还没设置" : txt);
        setMenuValue(menuView, R.id.tv_change_bg, "🎨 换背景颜色",
                MMKV.defaultMMKV().decodeInt(KEY_BG_TYPE, 0) == 1 ? "当前：相册图" : "当前：纯色");
        setMenuValue(menuView, R.id.tv_pick_image, "📷 相册选图",
                MMKV.defaultMMKV().decodeInt(KEY_BG_TYPE, 0) == 1 ? "已选 · 可换一张" : "选一张当背景");
        setMenuValue(menuView, R.id.tv_templates, "🎬 演出模板（一键整场风格）",
                "4 套 · 含台词");
        setMenuValue(menuView, R.id.tv_presets, "💌 告白文案库（7 大场景）", "挑一句直接用");
        setMenuValue(menuView, R.id.tv_qr_code, "📷 生成二维码给她扫", "她扫一下就能看");
        setMenuValue(menuView, R.id.tv_import_share_code, "📥 导入浪漫分享码", "读剪贴板");
        setMenuValue(menuView, R.id.tv_export_gif, "🎞 视频转 GIF 动图", "先导出一段视频");
        setMenuValue(menuView, R.id.tv_set_wallpaper, "📱 设置为桌面壁纸", "到壁纸列表里选它");
        setMenuValue(menuView, R.id.tv_guide, "❓ 玩法说明", "第一次用先看");
    }

    /** 开关态菜单项：主标题白色 + 尾部状态（开=金/关=灰） */
    private void setMenuToggle(View menuView, int id, String title, boolean on) {
        TextView tv = menuView.findViewById(id);
        if (tv == null) return;
        String state = on ? "已开启" : "已关闭";
        SpannableString ss = new SpannableString(title + "  " + state);
        int start = ss.length() - state.length();
        ss.setSpan(new ForegroundColorSpan(on ? 0xFFFFD700 : 0xFF8899AA),
                start, ss.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        ss.setSpan(new RelativeSizeSpan(0.82f), start, ss.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        tv.setText(ss);
    }

    /** 选择类菜单项：主标题白色 + 尾部当前值（金色小字） */
    private void setMenuValue(View menuView, int id, String title, String value) {
        TextView tv = menuView.findViewById(id);
        if (tv == null) return;
        String v = "  " + value;
        SpannableString ss = new SpannableString(title + v);
        int start = ss.length() - v.length();
        ss.setSpan(new ForegroundColorSpan(0xFFFFD700), start, ss.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        ss.setSpan(new RelativeSizeSpan(0.82f), start, ss.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        tv.setText(ss);
    }

    /** 随机剧本（#5新）：调用库编舞引擎 */

    private void toggleAfterglow() {
        boolean on = !MMKV.defaultMMKV().decodeBool(KEY_AFTERGLOW, true);
        MMKV.defaultMMKV().encode(KEY_AFTERGLOW, on);
        applyAfterglow(on);
        Toaster.showShort(on ? "烟花余辉已开启：薄纱残影 ✨" : "烟花余辉已关闭");
    }

    private void applyAfterglow(boolean on) {
        FireworkView fv = GlobalFirework.getInstance().getFireworkView();
        if (fv != null) fv.setAfterglow(on);
    }

    /** 纪念日列表（#14）：点击条目用保存的台词重演当天的浪漫演出 */
    private void showAnniversaryList() {
        // 【架构】数据读取交给 AnniversaryStore
        final java.util.List<AnniversaryStore.Entry> list = AnniversaryStore.load();
        if (list.isEmpty()) {
            Toaster.showShort("还没有纪念日，表白成功后会自动记录");
            return;
        }
        // 末位放"提醒设置/清空"条目而非底部按钮：纪念日列表可以很长，横屏下 AlertDialog
        // 系统按钮栏会被挤压成 0 高度，点了没反应
        final String[] items = new String[list.size() + 2];
        final String[] linesArr = new String[list.size() + 2];
        for (int i = 0; i < list.size(); i++) {
            AnniversaryStore.Entry e = list.get(i);
            // 【新增】显示距今天数与周年：纪念日只有日期没有"过了多久"，仪式感就少了一半
            items[i] = e.date + "  " + e.label + "  · " + AnniversaryStore.daysLabel(e.date);
            linesArr[i] = e.lines;
        }
        items[list.size()] = "⏰ 提醒设置（当前：" + AnniversaryReminder.summary() + "）";
        linesArr[list.size()] = null;
        items[list.size() + 1] = "🗑 清空全部纪念日";
        linesArr[list.size() + 1] = null;

        new UiGuard.SafeBuilder(this)
                .setTitle("纪念日（点击条目重演当天的演出）")
                .setItems(items, (d, w) -> {
                    if (w == list.size()) {
                        showAnniversaryReminderPicker();
                        return;
                    }
                    if (w > list.size()) {
                        AnniversaryStore.clearAll();
                        AnniversaryReminder.reschedule(this);
                        Toaster.showShort("已清空纪念日");
                        return;
                    }
                    if (isRomanticMode) stopRomanticMode();
                    Toaster.showShort("重演：" + items[w]);
                    startRomanticMode(linesArr[w], countdownSeconds);
                })
                .show();
    }

    /**
     * 【新增】套用一整套演出模板：节日主题 + 随机剧本 + 台词一次配好。
     *
     * @param playNow true 则立刻开演；false 只把风格与台词存下来，下次打开浪漫设置就是它
     */
    private void applyShowTemplate(ShowTemplate t, boolean playNow) {
        // 1. 主题（会连带切换背景色、天气粒子与主题烟花池）
        applyTheme(t.theme);
        // 2. 随机剧本
        MMKV.defaultMMKV().encode(KEY_AUTO_PATTERN, t.pattern);
        if (isAutoPlaying) {
            GlobalFirework.getInstance().stopAutoShow();
            GlobalFirework.getInstance().startAutoShow(t.pattern, 700);
        }
        // 3. 台词：写入"上次台词"，下次打开浪漫设置弹窗就是这套
        MMKV.defaultMMKV().encode("last_romantic_lines", t.lines);
        pendingExportMessage = t.lines;

        if (playNow) {
            if (isRomanticMode) stopRomanticMode();
            // 【可感知】引擎没就绪时原来的写法是静默 return——用户连点几次都没反应，
            // 只会以为"模板坏了"。这里明确说出来，并且别再空排一次队列。
            if (!fireworkReady) {
                Toaster.showShort("烟花引擎还在加载，稍等两秒再点一次");
                return;
            }
            postRomanticCue(() -> {
                if (isRomanticMode) return;
                if (!fireworkReady) {
                    Toaster.showShort("烟花引擎还没好，等加载完再试");
                    return;
                }
                startRomanticMode(t.lines, countdownSeconds);
            }, 500);
            Toaster.showShort("已套用：" + t.emoji + " " + t.name + "，马上开始");
        } else {
            Toaster.showShort("已套用风格与台词：" + t.emoji + " " + t.name);
        }
    }

    /**
     * 【新增】崩溃日志查看：把留在手机上的崩溃现场给用户看，并一键发给我。
     *
     * <p>按钮全部放在自定义布局里——横屏下 AlertDialog 的系统按钮栏会被挤成 0 高度，
     * 点了没反应，这个坑在纪念日对话框上踩过。
     */
    private void showCrashLogDialog() {
        View v = getLayoutInflater().inflate(R.layout.dialog_crash_log, null);
        android.widget.TextView tv = v.findViewById(R.id.tv_crash);
        String text = CrashLogger.latestText();
        if (text.isEmpty()) {
            text = "暂无崩溃记录 🎉\n\n"
                    + "如果 App 曾闪退过，重启后回到这里就能看到现场日志：\n"
                    + "机型、系统版本、发生时间、异常堆栈都在里面。\n"
                    + "点「发送给我」把日志文件发出来，就能定位问题。";
        }
        tv.setText(text);

        final androidx.appcompat.app.AlertDialog dialog =
                new UiGuard.SafeBuilder(this)
                        .setTitle("崩溃日志")
                        .setView(v)
                        .show();

        final String crashText = text;
        v.findViewById(R.id.btn_crash_copy).setOnClickListener(b -> {
            android.content.ClipboardManager cm =
                    (android.content.ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null) {
                cm.setPrimaryClip(android.content.ClipData.newPlainText("crash", crashText));
                Toaster.showShort("已复制崩溃日志");
            }
        });
        v.findViewById(R.id.btn_crash_share).setOnClickListener(b -> shareLatestCrash());
        v.findViewById(R.id.btn_crash_clear).setOnClickListener(b -> {
            CrashLogger.clearAll();
            Toaster.showShort("已清空崩溃日志");
            dialog.dismiss();
        });
        v.findViewById(R.id.btn_crash_close).setOnClickListener(b -> dialog.dismiss());
    }

    /** 【新增】把最近一份崩溃日志以文件形式分享出去（比复制文本更完整） */
    private void shareLatestCrash() {
        java.util.List<java.io.File> fs = CrashLogger.files();
        if (fs.isEmpty()) {
            Toaster.showShort("暂无崩溃日志可发送");
            return;
        }
        try {
            android.net.Uri uri = androidx.core.content.FileProvider.getUriForFile(
                    this, getPackageName() + ".fileprovider", fs.get(0));
            Intent share = new Intent(Intent.ACTION_SEND);
            share.setType("text/plain");
            share.putExtra(Intent.EXTRA_STREAM, uri);
            share.putExtra(Intent.EXTRA_SUBJECT, "烟花 App 崩溃日志");
            share.putExtra(Intent.EXTRA_TEXT, CrashLogger.latestText());
            share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(share, "发送崩溃日志"));
        } catch (Exception e) {
            // FileProvider 拿不到就退化为纯文本分享
            Intent share = new Intent(Intent.ACTION_SEND);
            share.setType("text/plain");
            share.putExtra(Intent.EXTRA_TEXT, CrashLogger.latestText());
            startActivity(Intent.createChooser(share, "发送崩溃日志"));
        }
    }

    /** 【新增】纪念日提醒设置：开启后还要确认通知权限，否则 Android 13+ 静默不弹 */
    private void showAnniversaryReminderPicker() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU
                && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS);
            Toaster.showShort("请先允许通知权限，否则提醒不会弹出");
            return;
        }
        dialogs.showAnniversaryReminderPicker();
    }

    /** 新手引导（#11新）：一次性，可从菜单重看 */

    /** 天气粒子：雪/樱花/银杏（库 SkyView WEATHER_* 模式） */

    /** 孔明灯模式：开启后点击屏幕放灯，再点菜单项切回烟花 */
    private void toggleLanternTapMode() {
        lanternTapMode = !lanternTapMode;
        MMKV.defaultMMKV().encode(KEY_LANTERN_MODE, lanternTapMode);
        Toaster.showShort(lanternTapMode ? "孔明灯模式已开启：点击屏幕放灯 🏮" : "已恢复点击放烟花");
    }

    /** 月亮（真实月相）与当日星座连线开关 */
    private void toggleMoonConstellation() {
        boolean on = !MMKV.defaultMMKV().decodeBool(KEY_MOON, true);
        MMKV.defaultMMKV().encode(KEY_MOON, on);
        MMKV.defaultMMKV().encode(KEY_CONSTELLATION, on);
        starView.setMoonEnabled(on);
        starView.setConstellationEnabled(on);
        starView.setTimeTintAuto(on); // 【修复】背景灰调治理：时段叠色随月亮开关一并控制，关掉后背景纯黑
        Toaster.showShort(on ? "月亮与星座已显示（今日：" + starView.getCurrentZodiacName() + "座）" : "月亮与星座已隐藏");
    }

    /** 音乐律动：麦克风监听节拍自动绽放（需 RECORD_AUDIO 运行时权限） */
    private void toggleMusicRhythm() {
        if (rhythmOn) {
            rhythmOn = false;
            RhythmFirework.getInstance().stop();
            Toaster.showShort("音乐律动已关闭");
            return;
        }
        String p = Manifest.permission.RECORD_AUDIO;
        if (checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED) {
            startRhythmNow();
        } else {
            micPermissionLauncher.launch(p);
        }
    }

    private void startRhythmNow() {
        RhythmFirework rhythm = RhythmFirework.getInstance();
        rhythm.setSensitivity(1.2f);
        rhythm.setAutoBloom(true);
        if (rhythm.start(this)) {
            rhythmOn = true;
            Toaster.showShort("音乐律动已开启：播放音乐试试 🎵");
        } else {
            Toaster.showShort("麦克风不可用，律动未开启");
        }
    }

    /**
     * 跳转到系统动态壁纸预览设置页
     */
    /**
     * 【新增】设为动态壁纸。
     *
     * <p>Android 不允许第三方 App 静默设置动态壁纸（没有系统签名拿不到
     * SET_WALLPAPER_COMPONENT），所以"一键"能做到的极限就是<b>直达系统预览页</b>，
     * 用户只需再点一次「设置壁纸」。第一次会说明这一步，之后直接跳转。
     */
    private void goSetLiveWallpaper() {
        boolean guided = MMKV.defaultMMKV().decodeBool(KEY_WALLPAPER_GUIDED, false);
        if (!guided) {
            String[] items = {
                    "▶ 打开系统预览页（下一步点「设置壁纸」）",
                    "以后直接跳转，不再显示说明"
            };
            new UiGuard.SafeBuilder(this)
                    .setTitle("设为动态壁纸")
                    .setMessage("桌面和锁屏会一直放着烟花。\n"
                            + "系统会打开预览页，点一下「设置壁纸」就生效。\n"
                            + "（安卓不允许 App 直接替你设置，这一步必须由你确认）")
                    .setItems(items, (d, w) -> {
                        d.dismiss();
                        if (w == 1) {
                            MMKV.defaultMMKV().encode(KEY_WALLPAPER_GUIDED, true);
                        }
                        openLiveWallpaperPreview();
                    })
                    .show();
            return;
        }
        openLiveWallpaperPreview();
    }

    private void openLiveWallpaperPreview() {
        // 优先：直接指定本 App 的壁纸服务打开预览（Android 7+）
        try {
            Intent intent = new Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER);
            intent.putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                    new ComponentName(this, FireworkWallpaperService.class));
            startActivity(intent);
            return;
        } catch (Exception ignored) {
        }
        // 兜底：打开动态壁纸选择列表
        try {
            startActivity(new Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER));
            return;
        } catch (Exception ignored) {
        }
        // 最后兜底：普通壁纸设置页，用户手动找「动态壁纸」
        try {
            startActivity(new Intent(Intent.ACTION_SET_WALLPAPER));
        } catch (Exception e) {
            Toaster.showShort("请在系统壁纸设置中选择「烟花动态壁纸」");
        }
    }

    private void importRomanticCodeFromClipboard() {
        String clipText = getClipboardText();
        RomanticShareCodec.RomanticInfo info = RomanticShareCodec.decode(clipText);
        if (info == null) {
            String reason = RomanticShareCodec.lastDecodeError;
            Toaster.showLong(reason.isEmpty() ? "剪贴板没有找到有效分享码" : "导入失败：" + reason);
            return;
        }
        countdownSeconds = info.seconds;
        startRomanticMode(info.message, info.seconds);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        // 窗口获得焦点 + 首次检测 + 不在浪漫模式中，才执行检测
        if (hasFocus && !hasCheckedShareCode && !isRomanticMode) {
            // 延迟200ms，确保窗口完全稳定、剪贴板服务就绪
            mainHandler.postDelayed(() -> {
                checkAndShowShareCodeDialog();
                hasCheckedShareCode = true;
            }, 200);
        }
    }

    /**
     * 检测剪贴板并弹出分享码导入提示
     */
    private void checkAndShowShareCodeDialog() {
        String clipText = getClipboardText();
        if (RomanticShareCodec.isValidCode(clipText)) {
            new UiGuard.SafeBuilder(this)
                    .setTitle("检测到浪漫分享码")
                    .setMessage("是否立即导入并开启浪漫模式？")
                    .setPositiveButton("立即导入", (d, w) -> importRomanticCodeFromClipboard())
                    .setNegativeButton("取消", null)
                    .show();
        }
    }

    // ====================== 视频导出 ======================

    private void requestExportVideo() {
        if (isRecording) {
            Toaster.showShort("正在录制中...");
            return;
        }
        // 先输入告白文案，再申请录屏授权
        // 【UI 优化】复用 MD3 输入框布局 + 显式按钮（横屏下系统按钮栏不可见）
        View view = getLayoutInflater().inflate(R.layout.dialog_input_text, null);
        final com.google.android.material.textfield.TextInputLayout til =
                view.findViewById(R.id.til_input);
        final com.google.android.material.textfield.TextInputEditText input =
                view.findViewById(R.id.et_input);
        til.setHint("告白文案");
        input.setText(pendingExportMessage);
        if (input.getText() != null) input.setSelection(input.getText().length());
        // 录制文案可长可短，不做字数计数
        til.setCounterEnabled(false);

        final androidx.appcompat.app.AlertDialog dialog =
                new UiGuard.SafeBuilder(this)
                        .setTitle("导出浪漫视频")
                        .setMessage("将自动播放 " + (RECORD_DURATION_MS / 1000) + " 秒浪漫特效并录制成 MP4")
                        .setView(view)
                        .show();

        ((com.google.android.material.button.MaterialButton) view.findViewById(R.id.btn_confirm))
                .setText("开始录制");
        view.findViewById(R.id.btn_confirm).setOnClickListener(v -> {
            String msg = input.getText() == null ? "" : input.getText().toString().trim();
            pendingExportMessage = msg.isEmpty() ? "遇见你真好" : msg;
            dialog.dismiss();
            MediaProjectionManager pm = (MediaProjectionManager)
                    getSystemService(Context.MEDIA_PROJECTION_SERVICE);
            projectionPermissionLauncher.launch(pm.createScreenCaptureIntent());
        });
        view.findViewById(R.id.btn_cancel).setOnClickListener(v -> dialog.dismiss());
    }

    private void onProjectionGranted(int resultCode, Intent data) {
        // 1. 启动录屏服务
        Intent svc = new Intent(this, ScreenRecorderService.class);
        svc.putExtra(ScreenRecorderService.EXTRA_RESULT_CODE, resultCode);
        svc.putExtra(ScreenRecorderService.EXTRA_RESULT_DATA, data);
        ContextCompat.startForegroundService(this, svc);
        isRecording = true;

        // 【UI】不再弹模态"正在录制"对话框：它会挡住演出画面、且录制期间无法点击屏幕互动。
        // 改为轻量 Toast 提示，录制状态由右上角悬浮"停止录制"按钮与前台服务通知承担。
        Toaster.showShort("开始录制浪漫视频…（点右上角按钮可提前结束）");
        showStopRecordButton(); // 【修复】录制中显示停止按钮

        // ===== 录整场（#12旧）：从浪漫模式开始录到应答庆祝结束 =====
        if (pendingRecordFull) {
            pendingRecordFull = false;
            // 录整场同样先收起底栏，用 setPureMode 而非 toggle：
            // toggle 有 1 秒冷却，刚手动切过会被静默拒绝，导致按钮栏入镜
            setPureMode(true, false);
            postRomanticCue(() -> startRomanticMode(pendingExportMessage, countdownSeconds), 600);
            // 【#8】录整场保护：最多 3 分钟自动停，防忘停录爆存储
            recordFullGuard = () -> {
                if (isRecording) {
                    Toaster.showShort("录制已达 3 分钟上限，自动保存");
                    stopRecordingService();
                }
            };
            mainHandler.postDelayed(recordFullGuard, 180000);
            return;
        }

        // 2. 进入纯净模式隐藏按钮栏，录出来更干净
        setPureMode(true, false);

        // 3. 启动浪漫模式（用倒计时 3 秒 + 用户文案）
        // 【修复】此前此处有一句 "if (isRecording) stopRecordingService();"，
        // 而 isRecording 刚在上面被置为 true，必然立刻停录 → 帧数不足直接报"未捕获到画面"；
        // 它内部还会 postDelayed(stopRomanticMode, 800)，把 400ms 后才开场的演出一并掐掉。
        // 视频导出整条链路因此完全不可用，删除该误加的分支。
        mainHandler.postDelayed(() -> {
            if (isRecording) startRomanticMode(pendingExportMessage, 3);
        }, 400);

        // 4. 到点自动停止录制
        mainHandler.postDelayed(() -> {
            if (isRecording) stopRecordingService();
        }, RECORD_DURATION_MS);
    }

    /** 【修复】录制中的悬浮“停止录制”按钮（右上角） */
    private void showStopRecordButton() {
        if (stopRecordBtn == null) {
            float density = getResources().getDisplayMetrics().density;
            // MaterialButton：圆角 + 涟漪，与底栏/应答按钮视觉语言统一
            com.google.android.material.button.MaterialButton btn =
                    new com.google.android.material.button.MaterialButton(this);
            btn.setText("⏹ 停止录制");
            btn.setTextColor(Color.WHITE);
            btn.setTextSize(12f);
            btn.setCornerRadius((int) (20 * density));
            btn.setBackgroundTintList(ColorStateList.valueOf(0xB3B31313));
            btn.setInsetTop(0);
            btn.setInsetBottom(0);
            btn.setOnClickListener(v -> {
                Toaster.showShort("正在保存视频…");
                stopRecordingService();
            });
            stopRecordBtn = btn;
            android.widget.FrameLayout.LayoutParams lp =
                    new android.widget.FrameLayout.LayoutParams(-2, -2);
            lp.gravity = android.view.Gravity.TOP | android.view.Gravity.END;
            lp.topMargin = 90;
            lp.rightMargin = 40;
            rootLayout.addView(stopRecordBtn, lp);
        }
        stopRecordBtn.bringToFront();
        stopRecordBtn.setVisibility(View.VISIBLE);
        stopRecordBtn.setAlpha(0f);
        stopRecordBtn.animate().alpha(1f).setDuration(300).start();
    }

    private void hideStopRecordButton() {
        if (stopRecordBtn != null) {
            stopRecordBtn.animate().alpha(0f).setDuration(300)
                    .withEndAction(() -> stopRecordBtn.setVisibility(View.GONE)).start();
        }
    }

    /** 只发停止指令，不触发后续逻辑（供 stopRomanticMode 内部安全调用，切断递归循环） */
    private void requestStopRecording() {
        if (!isRecording) return;
        Intent i = new Intent(this, ScreenRecorderService.class);
        i.setAction(ScreenRecorderService.ACTION_STOP);
        ContextCompat.startForegroundService(this, i);
    }

    /** 外部入口：停止录制，并在收尾后自动退出浪漫模式（用于 12 秒/录整场自然结束） */
    private void stopRecordingService() {
        requestStopRecording();
        if (isRomanticMode) {
            mainHandler.postDelayed(this::stopRomanticMode, 800);
        }
    }

    /**
     * 分享录制的视频。
     *
     * @param galleryUri 已入册到公共相册的 content:// Uri（优先，兼容性最好）
     * @param path       App 私有目录下的文件路径（未入册时经 FileProvider 分享）
     */
    private void shareVideo(String path, String galleryUri) {
        try {
            Intent share = new Intent(Intent.ACTION_SEND);
            share.setType("video/mp4");
            Uri uri;
            if (galleryUri != null) {
                uri = Uri.parse(galleryUri);
            } else {
                File f = new File(path);
                uri = androidx.core.content.FileProvider.getUriForFile(
                        this, getPackageName() + ".fileprovider", f);
            }
            share.putExtra(Intent.EXTRA_STREAM, uri);
            share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(share, "分享浪漫视频"));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}