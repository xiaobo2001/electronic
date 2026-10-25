package com.xiaobo.electronic;

import android.Manifest;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
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
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
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
import android.widget.PopupMenu;

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
    private static final String KEY_AUTO_PATTERN = "auto_pattern"; // 0经典 1波浪 2聚拢 3节拍
    // ===== BGM（#7旧） =====
    private static final String KEY_BGM_URI = "bgm_uri";
    private static final String KEY_BGM_VOLUME = "bgm_volume";
    private float bgmVolume = 0.7f;
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
    private static final String KEY_GUIDE_SHOWN = "guide_shown";
    private static final String KEY_THEME = "theme_id"; // 0默认 1春节 2生日 3圣诞
    private int currentTheme = ThemeManager.THEME_DEFAULT;

    // ===== 【#7】浪漫 runnable 统一清理：中途停止时一次清光所有排队演出 =====
    private final java.util.List<Runnable> romanticCues = new java.util.ArrayList<>();
    private boolean wasPureBeforeRomantic = false;
    private Runnable recordFullGuard;
    // ===== 【#16】摇一摇放烟花 =====
    private ShakeDetector shakeDetector;
    // ===== 【#18】台词 TTS 朗读 =====
    private android.speech.tts.TextToSpeech tts;
    private boolean ttsReady = false;
    // emoji 图形位图缓存（样式扩展包 #4新）
    private final java.util.HashMap<String, android.graphics.Bitmap> emojiShapeCache = new java.util.HashMap<>();
    private static final String KEY_WEATHER = "weather_mode";
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
    private String pendingExportMessage = "遇见你真好";
    private static final long RECORD_DURATION_MS = 12000; // 自动录制 12 秒
    private AlertDialog recordingDialog;

    private final BroadcastReceiver recordStoppedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(android.content.Context context, Intent intent) {
            isRecording = false;
            dismissRecordingDialog();
            if (ScreenRecorderService.ACTION_FAILED.equals(intent.getAction())) {
                String err = intent.getStringExtra(ScreenRecorderService.EXTRA_ERROR);
                new com.google.android.material.dialog.MaterialAlertDialogBuilder(MainActivity.this)
                        .setTitle("录制失败")
                        .setMessage(err)
                        .setPositiveButton("知道了", null)
                        .show();
                return;
            }
            String path = intent.getStringExtra(ScreenRecorderService.EXTRA_OUTPUT_PATH);
            if (path != null) {
                new com.google.android.material.dialog.MaterialAlertDialogBuilder(MainActivity.this)
                        .setTitle("录制完成")
                        .setMessage("视频已保存：" + path)
                        .setPositiveButton("分享", (d, w) -> shareVideo(path))
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
            // 首次启动玩法引导（一次性）
            mainHandler.postDelayed(() -> showGuide(false), 1200);
            if (loadingOverlay != null) loadingOverlay.animate().alpha(0f).setDuration(200).withEndAction(() -> loadingOverlay.setVisibility(View.GONE)).start();
        });
        initFireworkConfigs();

        // 启动性能检测：统计首帧与首屏前 30 帧的掉帧情况
        tracer.trackFirstFrame(this);

        // 触摸事件：单击烟花/放灯 + 长按分级蓄力（0.5s/1s/1.5s → 中/大/超大全屏）
        rootLayout.setOnTouchListener((v, event) -> {
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
                            if (!fireworkReady) {
                                long now = System.currentTimeMillis();
                                if (now - lastNotReadyToast > 1500) {
                                    lastNotReadyToast = now;
                                    Toast.makeText(MainActivity.this, "烟花引擎加载中，请稍候...", Toast.LENGTH_SHORT).show();
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
                                GlobalFirework.getInstance().bloom(event.getX(), event.getY(), cfg);
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

        // 按钮点击事件
        btnRomantic.setOnClickListener(v -> showRomanticDialog());
        btnAutoPlay.setOnClickListener(v -> toggleAutoPlay());
        btnMore.setOnClickListener(v -> showMoreMenu(v)); // 更多按钮弹出菜单
    }


//    private void initFireworkConfigs() {
//        // 浪漫模式：爱心形状 + 粉金配色
//        romanticHeartConfig = new FireworkConfig.Builder()
//                .shape(FireworkConfig.Shape.HEART)
//                .colors(Color.RED, 0xFFFF69B4, 0xFFFFD700)
//                .count(80)
//                .range(0.8f)
//                .trail(1.2f)
//                .build();
//
//        // 点击/随机播放的烟花池：4种样式随机出现
//        randomFireworkPool = new FireworkConfig[]{
//                // 经典圆形
//                new FireworkConfig.Builder().build(),
//                // 爱心形状
//                new FireworkConfig.Builder()
//                        .shape(FireworkConfig.Shape.HEART)
//                        .range(0.9f)
//                        .build(),
//                // 星形烟花
//                new FireworkConfig.Builder()
//                        .shape(FireworkConfig.Shape.STAR)
//                        .colors(Color.CYAN, Color.MAGENTA)
//                        .range(1.1f)
//                        .build(),
//                // 带火箭升空的圆形烟花
//                new FireworkConfig.Builder()
//                        .rocket(true)
//                        .colors(Color.YELLOW, Color.RED)
//                        .range(1.2f)
//                        .build()
//        };
//    }

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
                for (int i = 0; i < themed.length && i < emojis.length; i++) {
                    ThemeManager.withEmoji(this, themed[i], emojis[i]);
                }
                randomFireworkPool = themed;
                return;
            }
        }

        // ===== 样式扩展包（#4新）：原有5样 + 玫瑰/戒指/钻石头图形 + 金雨/彩虹圈主题 =====
        randomFireworkPool = new FireworkConfig[]{
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
                // 自定义文字（沿用用户设置）
                new FireworkConfig.Builder()
                        .shape(FireworkConfig.Shape.TEXT)
                        .text(customFireworkText)
                        .colors(Color.RED, Color.YELLOW)
                        .range(1.2f)
                        .count(scaledCount(160))
                        .textSize(100f)
                        .build(),
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
        Toast.makeText(this, "摇出满屏烟花 🎆", Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleRomanticShortcut(intent);
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

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopRomanticMode();
        stopMusic();
        isAutoPlaying = false;
        mainHandler.removeCallbacksAndMessages(null);
        petalPool.clear();
        if (rhythmOn) RhythmFirework.getInstance().stop();
        if (shakeDetector != null) shakeDetector.unregister();
        if (tts != null) {
            try {
                tts.shutdown();
            } catch (Exception ignored) {
            }
            tts = null;
        }
        starView.stopSky();
        try { unregisterReceiver(recordStoppedReceiver); } catch (Exception ignored) {}
    }

//    private void showRomanticDialog() {
//        if (isRomanticMode) {
//            stopRomanticMode();
//            return;
//        }
//        final EditText input = new EditText(this);
//        input.setHint("输入她的名字");
//        input.setText("小美 遇见你真好");
//
//        new AlertDialog.Builder(this)
//                .setTitle("开启顶级浪漫模式")
//                .setMessage("即将开始粒子倒计时与花雨特效...")
//                .setView(input)
//                .setPositiveButton("开始", (dialog, which) -> startRomanticMode(input.getText().toString()))
//                .setNegativeButton("取消", null)
//                .show();
//    }

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

        String preset = MMKV.defaultMMKV().decodeString("last_romantic_lines", "小美 遇见你真好\n往后余生，请多指教");
        msgInput.setText(preset);
        timeInput.setText(String.valueOf(countdownSeconds));
        volumeSlider.setValue(Math.max(0f, Math.min(100f, bgmVolume * 100f)));
        volumeLabel.setText("背景音乐音量 " + (int) volumeSlider.getValue() + "%");
        volumeSlider.addOnChangeListener((slider, value, fromUser) ->
                volumeLabel.setText("背景音乐音量 " + (int) value + "%"));
        bgmPick.setText(bgmUri == null ? "🎵 背景音乐：内置浪漫曲（点击换歌）" : "🎵 背景音乐：已选自手机（点击重新选）");
        bgmPick.setOnClickListener(v -> {
            Intent it = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            it.addCategory(Intent.CATEGORY_OPENABLE);
            it.setType("audio/*");
            pickBgmLauncher.launch(it);
        });

        androidx.appcompat.app.AlertDialog dialog = new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle("开启顶级浪漫模式")
                .setMessage("三幕剧：告白台词 → 爱心轨迹与花海 → 终章绽放，结尾有应答时刻")
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
                Toast.makeText(this, "生成失败", Toast.LENGTH_SHORT).show();
                return;
            }
            copyToClipboard(code);
            Toast.makeText(this, "分享码已复制（含多段台词与音量），发给对方即可复刻", Toast.LENGTH_LONG).show();
        });

        // 导入分享码（自动识别 v1/v2）
        btnImport.setOnClickListener(v -> {
            String clipText = getClipboardText();
            final EditText importInput = new EditText(this);
            importInput.setHint("粘贴分享码");
            importInput.setTextColor(Color.WHITE);
            importInput.setPadding(48, 24, 48, 24);
            if (RomanticShareCodec.isValidCode(clipText)) {
                importInput.setText(clipText);
            }

            new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                    .setTitle("导入浪漫分享码")
                    .setView(importInput)
                    .setPositiveButton("导入", (d, which) -> {
                        String code = importInput.getText().toString().trim();
                        RomanticShareCodec.RomanticInfo info = RomanticShareCodec.decode(code);
                        if (info == null) {
                            Toast.makeText(this, "分享码格式无效", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        msgInput.setText(info.message);
                        timeInput.setText(String.valueOf(info.seconds));
                        if (info.volume >= 0f) {
                            volumeSlider.setValue(Math.max(0f, Math.min(100f, info.volume * 100f)));
                        }
                        Toast.makeText(this, "导入成功", Toast.LENGTH_SHORT).show();
                    })
                    .setNegativeButton("取消", null)
                    .show();
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
        btnRomantic.setText("停止浪漫");        // 粒子文字切换为粉紫色系
        particleTextView.setTextColorHue(320, 350);
        rootLayout.setBackground(null);
        rootLayout.setBackgroundColor(Color.rgb(10, 0, 15));
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

        // ===== 【#18】TTS 初始化（懒，失败静默） =====
        ensureTts();

        // 开场仪式（#4）：引言字 → 四方小心理聚心 → 倒计时
        particleTextView.setTextColorHue(45, 60);
        particleTextView.showText("这一刻，只为你说", () -> {
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

    /** 多段台词依次演出（#3/#8）：聚拢→心脏搏动两次→爆开成星散，逐句推进 */
    private void playConfessionLines(int lineIdx) {
        if (!isRomanticMode) return;
        if (lineIdx >= romanticLines.size()) {
            startActOne();
            return;
        }
        romanticStage = 1;
        String line = romanticLines.get(lineIdx);
        speakLine(line); // 【#18】TTS 同步朗读台词
        particleTextView.showText(line, () -> {
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
            postRomanticCue(() -> {
                if (!isRomanticMode) return;
                GlobalFirework.getInstance().playHeartbeatVibration();
                boolean last = (lineIdx == romanticLines.size() - 1);
                if (last) {
                    // 最后一句不爆开，保留在幕尾由应答前收束
                    startActOne();
                } else {
                    particleTextView.explode(() -> {
                        if (!isRomanticMode) return;
                        postRomanticCue(() -> playConfessionLines(lineIdx + 1), 350);
                    });
                }
            }, 2200);
        });
    }

    /** 幕一：三波爱心绽放 + 花瓣雨，持续约 14 秒后进幕二 */
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
        for (int i = 0; i < 4; i++) {
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
        postRomanticCue(this::startActTwo, 14000);
    }

    /** 幕二：花海（彩色爱心波次）+ 满屏流星雨时刻（#9） */
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
                FireworkConfig waveHeart = new FireworkConfig.Builder()
                        .shape(FireworkConfig.Shape.HEART)
                        .colors(hueColorApp(hueBase), hueColorApp((hueBase + 40) % 360), Color.WHITE)
                        .count(scaledCount(70)).range(0.85f).trail(1.2f).build();
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
                    .textSize(110f).rocket(true).build());
            mainHandler.postDelayed(() -> {
                if (!isRomanticMode) return;
                GlobalFirework.getInstance().bloom(cx, cy, new FireworkConfig.Builder()
                        .shape(FireworkConfig.Shape.HEART)
                        .colors(Color.RED, 0xFFFF69B4, Color.WHITE)
                        .count(scaledCount(180)).range(1.2f).trail(1.5f).build());
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
        TextView question = new TextView(this);
        question.setText(romanticLines.get(romanticLines.size() - 1));
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
        Button yesBtn = new Button(this);
        yesBtn.setText("我愿意 💗");
        yesBtn.setTextColor(Color.WHITE);
        yesBtn.setBackgroundTintList(ColorStateList.valueOf(0xFFE91E63));
        Button thinkBtn = new Button(this);
        thinkBtn.setText("再想想 🤔");
        thinkBtn.setTextColor(Color.WHITE);
        thinkBtn.setBackgroundTintList(ColorStateList.valueOf(0x80000000));
        btnRow.addView(yesBtn);
        btnRow.addView(thinkBtn);
        android.widget.FrameLayout.LayoutParams rowLp = new android.widget.FrameLayout.LayoutParams(-2, -2);
        rowLp.topMargin = rootLayout.getHeight() / 2;
        rowLp.gravity = android.view.Gravity.CENTER_HORIZONTAL;
        answerOverlay.addView(btnRow, rowLp);

        // 【#4】应答时刻浮出小停止键（演出中底部栏已隐藏）
        Button miniStop = new Button(this);
        miniStop.setText("结束演出");
        miniStop.setTextColor(0xFF99AABB);
        miniStop.setBackgroundColor(0x00000000);
        miniStop.setTextSize(12);
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
        yesBtn.setOnClickListener(v -> onAnswerYes());
        thinkBtn.setOnClickListener(v -> onAnswerThink());
        // 【#6】应答无响应保护：30 秒未选择则温柔收场
        postRomanticCue(() -> {
            if (isRomanticMode && answerOverlay != null) {
                Toast.makeText(this, "无论多久，我等你", Toast.LENGTH_SHORT).show();
                onAnswerThink();
            }
        }, 30000);
    }

    /** 应答：我愿意——终极爆发 + 金色雨 + 纪念日 + 纪念卡片 */
    private void onAnswerYes() {
        dismissAnswerOverlay();
        romanticStage = 5;
        int w = rootLayout.getWidth();
        int h = rootLayout.getHeight();
        float cx = w / 2f, cy = h / 2f - 80f;
        // 金色雨：切换银杏精灵雨（全屏金色飘叶）
        starView.setWeather(SkyView.WEATHER_GINKGO);
        GlobalFirework.getInstance().playHeartbeatVibration();
        // 16 连发庆祝
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
            }, fi * 280);
        }
        // 记入纪念日（#14）+ 生成纪念卡片（#13）
        saveAnniversary("表白成功纪念日");
        postRomanticCue(() -> {
            float age = moonAgeNow();
            String path = MementoCard.generate(this, romanticLines,
                    starView.getCurrentZodiacName(), age);
            if (path != null) {
                Toast.makeText(this, "纪念卡片已保存：" + path, Toast.LENGTH_LONG).show();
            }
            if (pendingRecordFull && isRecording) {
                stopRecordingService(); // 录整场：庆祝完成后自动收尾
            }
            postRomanticCue(() -> {
                if (isRomanticMode) stopRomanticMode();
            }, 4500);
        }, 16 * 280 + 500);
    }

    /** 应答：再想想——温柔收场 */
    private void onAnswerThink() {
        dismissAnswerOverlay();
        int w = rootLayout.getWidth();
        int h = rootLayout.getHeight();
        GlobalFirework.getInstance().bloom(w / 2f, h / 2f - 80f, new FireworkConfig.Builder()
                .shape(FireworkConfig.Shape.TEXT).text("没关系，我等你")
                .colors(0xFF9FB8D8, Color.WHITE)
                .count(scaledCount(160)).range(1.0f).textSize(90f).build());
        postRomanticCue(() -> {
            if (isRomanticMode) stopRomanticMode();
            if (pendingRecordFull && isRecording) stopRecordingService();
        }, 3200);
    }

    private void dismissAnswerOverlay() {
        if (answerOverlay != null) {
            // 停掉按钮脉动动画
            View yesBtn = answerOverlay.findViewWithTag(null);
            android.widget.FrameLayout ov = answerOverlay;
            answerOverlay = null;
            ov.animate().alpha(0f).setDuration(500).withEndAction(() -> rootLayout.removeView(ov)).start();
        }
    }

    /** 纪念日存储（#14）：JSON 列表 [{date,label}] */
    private void saveAnniversary(String label) {
        try {
            org.json.JSONArray arr = new org.json.JSONArray(
                    MMKV.defaultMMKV().decodeString(KEY_ANNIVERSARY, "[]"));
            org.json.JSONObject o = new org.json.JSONObject();
            o.put("date", new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.CHINA)
                    .format(new java.util.Date()));
            o.put("label", label);
            arr.put(o);
            MMKV.defaultMMKV().encode(KEY_ANNIVERSARY, arr.toString());
        } catch (Exception ignored) {
        }
    }

    private static int hueColorApp(int hue) {
        float[] hsv = {hue, 0.7f, 1f};
        return Color.HSVToColor(hsv);
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
                Toast.makeText(this, "无法加载音乐，请检查 res/raw/romantic_bgm.mp3", Toast.LENGTH_SHORT).show();
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
            Toast.makeText(this, "音乐播放失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
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

//    private void stopRomanticMode() {
//        isRomanticMode = false;
//        btnRomantic.setText("💖 顶级浪漫");
//
//        stopMusic();
//
//        mainHandler.removeCallbacks(heartFireworkTask);
//        mainHandler.removeCallbacks(floatingPetalsTask);
//
//        floatingContainer.removeAllViews();
//        particleTextView.explode(null);
//        restoreBackground();
//    }


    private void stopRomanticMode() {
        isRomanticMode = false;
        btnRomantic.setText("💖 顶级浪漫");

        // ===== 优雅退场（#17）：不再瞬间清场 =====
        cancelCharging();
        dismissAnswerOverlay();
        clearRomanticCues(); // 【#7】清光全部排队演出
        if (recordFullGuard != null) {
            mainHandler.removeCallbacks(recordFullGuard);
        }
        stopTtsSpeak();
        RhythmFirework.getInstance().setOnBeatListener(null);
        mainHandler.removeCallbacks(floatingPetalsTask);

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
        starView.stopSky();
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

//    private void startCountdownSequence(String finalMessage) {
//        particleTextView.showText("3", () -> {
//            mainHandler.postDelayed(() -> {
//                if (!isRomanticMode) return;
//                particleTextView.explode(() -> {
//                    particleTextView.showText("2", () -> {
//                        mainHandler.postDelayed(() -> {
//                            if (!isRomanticMode) return;
//                            particleTextView.explode(() -> {
//                                particleTextView.showText("1", () -> {
//                                    mainHandler.postDelayed(() -> {
//                                        if (!isRomanticMode) return;
//                                        particleTextView.explode(() -> {
//                                            showFinalConfession(finalMessage);
//                                        });
//                                    }, 500);
//                                });
//                            });
//                        }, 500);
//                    });
//                });
//            }, 500);
//        });
//    }


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


//    private void showFinalConfession(String message) {
//        if (!isRomanticMode) return;
//        particleTextView.showText(message, null);
//
//        // 告白时刻中心发射一枚带火箭的大爱心
//        mainHandler.postDelayed(() -> {
//            if (!isRomanticMode) return;
//            FireworkConfig finaleConfig = new FireworkConfig.Builder()
//                    .shape(FireworkConfig.Shape.HEART)
//                    .rocket(true)
//                    .colors(Color.RED, 0xFFFF69B4, Color.WHITE)
//                    .count(120)
//                    .range(1.3f)
//                    .build();
//            GlobalFirework.getInstance().bloom(rootLayout.getWidth() / 2f, rootLayout.getHeight() / 2f - 100, finaleConfig);
//        }, 800);
//
//        mainHandler.postDelayed(() -> {
//            heartAngle = 0;
//            mainHandler.post(heartFireworkTask);
//            mainHandler.post(floatingPetalsTask);
//        }, 1000);
//    }

    // 原单幕 showFinalConfession 已被三幕剧（playConfessionLines → startActOne/Two/Three → 应答）替代，
    // 其中的双层旋转爱心轨迹（heartFireworkTask）亦按需移除。


//    private final Runnable heartFireworkTask = new Runnable() {
//        @Override
//        public void run() {
//            if (!isRomanticMode) return;
//            int w = rootLayout.getWidth();
//            int h = rootLayout.getHeight();
//            if (w > 0) {
//                float cx = w / 2f;
//                float cy = h / 2f - 100;
//                fireHeartPoint(cx, cy, w / 28f, heartAngle);
//                fireHeartPoint(cx, cy, w / 50f, -heartAngle);
//                heartAngle += 0.15;
//                if (heartAngle > Math.PI * 2) heartAngle = 0;
//            }
//            mainHandler.postDelayed(this, 60);
//        }
//    };

    // 原双层旋转爱心轨迹（heartFireworkTask）已按需移除：三波爱心绽放 + 花瓣雨构成幕一

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

    /**
     * 库 v1.2.0 正式 API：无反馈发射（粒子路径与 bloom 完全一致，不触发逐次音效/震动）。
     * 替代早期通过 findViewWithTag 直取渲染器的临时方案。保留供高频场景使用。
     */
    private void launchOnFireworkView(float x, float y, FireworkConfig config) {
        GlobalFirework.getInstance().bloomSilently(x, y, config);
    }

    /** 【#18】TTS 懒初始化与台词朗读 */
    private void ensureTts() {
        if (tts != null) return;
        try {
            tts = new android.speech.tts.TextToSpeech(this, status -> {
                ttsReady = (status == android.speech.tts.TextToSpeech.SUCCESS);
                if (ttsReady) {
                    try {
                        tts.setLanguage(java.util.Locale.CHINA);
                    } catch (Exception ignored) {
                    }
                }
            });
        } catch (Exception e) {
            tts = null;
        }
    }

    private void speakLine(String line) {
        if (ttsReady && tts != null) {
            try {
                tts.speak(line, android.speech.tts.TextToSpeech.QUEUE_FLUSH, null, "romantic_line");
            } catch (Exception ignored) {
            }
        }
    }

    private void stopTtsSpeak() {
        if (ttsReady && tts != null) {
            try {
                tts.stop();
            } catch (Exception ignored) {
            }
        }
    }

    private void clearRomanticCues() {
        for (Runnable r : romanticCues) mainHandler.removeCallbacks(r);
        romanticCues.clear();
    }

    /** 【#7】浪漫排队统一入口：停止时逐个移除，杜绝打断后的残影演出 */
    private void postRomanticCue(Runnable r, long delayMs) {
        romanticCues.add(r);
        mainHandler.postDelayed(r, delayMs);
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
        GlobalFirework.getInstance().bloom(x, y, main);
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
                Toast.makeText(this, "节拍剧本需先开启音乐律动，已改用波浪剧本", Toast.LENGTH_SHORT).show();
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
            else Toast.makeText(this, "需相册权限", Toast.LENGTH_SHORT).show();
        });
        pickBgmLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() == RESULT_OK && result.getData() != null && result.getData().getData() != null) {
                try {
                    getContentResolver().takePersistableUriPermission(result.getData().getData(), Intent.FLAG_GRANT_READ_URI_PERMISSION);
                } catch (Exception ignored) {
                }
                bgmUri = result.getData().getData().toString();
                MMKV.defaultMMKV().encode(KEY_BGM_URI, bgmUri);
                Toast.makeText(this, "背景音乐已更新，将在下次浪漫模式生效", Toast.LENGTH_SHORT).show();
            }
        });
        micPermissionLauncher = registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
            if (isGranted) startRhythmNow();
            else Toast.makeText(this, "需要麦克风权限才能随音乐绽放", Toast.LENGTH_SHORT).show();
        });
        // 录屏授权回调
        projectionPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        onProjectionGranted(result.getResultCode(), result.getData());
                    } else {
                        Toast.makeText(this, "未授权录屏，无法导出视频", Toast.LENGTH_SHORT).show();
                    }
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

    private void showColorPickerDialog() {
        String[] names = {"纯黑", "深蓝", "深紫", "暗红", "墨绿", "深灰"};
        int[] colors = {Color.BLACK, Color.rgb(10, 10, 50), Color.rgb(40, 0, 60), Color.rgb(60, 0, 0), Color.rgb(0, 40, 20), Color.rgb(30, 30, 30)};
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this).setTitle("背景颜色").setItems(names, (d, w) -> {
            MMKV.defaultMMKV().encode(KEY_BG_TYPE, 0);
            MMKV.defaultMMKV().encode(KEY_BG_COLOR, colors[w]);
            rootLayout.setBackground(null);
            rootLayout.setBackgroundColor(colors[w]);
        }).show();
    }

    private void showCustomTextDialog() {
        final EditText input = new EditText(this);
        input.setHint("输入烟花文字，如：恭喜发财");
        input.setTextColor(Color.WHITE);
        input.setPadding(48, 28, 48, 8);
        input.setText(customFireworkText);
        // 限制文字长度，避免过长导致显示效果差
        input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(8)});

        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle("自定义文字烟花")
                .setMessage("输入后点击屏幕、随机播放都会触发该文字烟花")
                .setView(input)
                .setPositiveButton("确定", (dialog, which) -> {
                    String text = input.getText().toString().trim();
                    if (text.isEmpty()) {
                        Toast.makeText(this, "文字不能为空", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    // 更新全局文字
                    customFireworkText = text;
                    // 持久化保存到MMKV，供壁纸服务读取
                    MMKV.defaultMMKV().encode(KEY_CUSTOM_FIREWORK_TEXT, text);
                    // 重新生成烟花配置池
                    initFireworkConfigs();
                    // 输入完成后，在屏幕中心发射一枚预览
                    float centerX = rootLayout.getWidth() / 2f;
                    float centerY = rootLayout.getHeight() / 2f - 100;
                    launchTextFirework(centerX, centerY, customFireworkText);
                    Toast.makeText(this, "已更新烟花文字：" + text, Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    /**
     * 发射指定文字的烟花（复用工具方法）
     */
    private void launchTextFirework(float x, float y, String text) {
        FireworkConfig textConfig = new FireworkConfig.Builder()
                .shape(FireworkConfig.Shape.TEXT)
                .text(text)
                .colors(Color.RED, Color.YELLOW)
                .range(1.2f)
                .build();
        GlobalFirework.getInstance().bloom(x, y, textConfig);
    }

    private void togglePureMode() {
        // 冷却时间内直接拒绝，防止快速反复触发
        long now = System.currentTimeMillis();
        if (now - lastPureModeSwitchTime < PURE_MODE_SWITCH_COOLDOWN) {
            return;
        }
        lastPureModeSwitchTime = now;

        // 先中断当前正在执行的动画，避免叠加闪烁
        llBottomBar.animate().cancel();
        llBottomBar.clearAnimation();

        isPureMode = !isPureMode;
        if (isPureMode) {
            // 进入纯净模式：淡出后隐藏
            llBottomBar.animate()
                    .alpha(0f)
                    .setDuration(200)
                    .withEndAction(() -> llBottomBar.setVisibility(View.GONE))
                    .start();
            Toast.makeText(this, isPureMode ? "已进入纯净模式（更多菜单可切回）" : "已退出纯净模式", Toast.LENGTH_SHORT).show();
        } else {
            // 退出纯净模式：先显示再淡入
            llBottomBar.setVisibility(View.VISIBLE);
            llBottomBar.setAlpha(0f);
            llBottomBar.animate()
                    .alpha(1f)
                    .setDuration(200)
                    .start();
            Toast.makeText(this, "已退出纯净模式", Toast.LENGTH_SHORT).show();
        }
        // 持久化保存状态
        MMKV.defaultMMKV().encode(KEY_PURE_MODE, isPureMode);
    }

    /**
     * 弹出更多功能菜单
     */
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
                .setDismissWhenClicked(true)
                .setDismissWhenTouchOutside(true)
                .setLifecycleOwner(this) // 自动绑定生命周期，防泄漏
                .build();

        // 绑定点击事件
        View menuView = balloon.getContentView();
        menuView.findViewById(R.id.tv_custom_text).setOnClickListener(v -> {
            balloon.dismiss();
            showCustomTextDialog();
        });
        menuView.findViewById(R.id.tv_change_bg).setOnClickListener(v -> {
            balloon.dismiss();
            showColorPickerDialog();
        });
        menuView.findViewById(R.id.tv_pick_image).setOnClickListener(v -> {
            balloon.dismiss();
            checkPermissionAndOpenGallery();
        });
        menuView.findViewById(R.id.tv_import_share_code).setOnClickListener(v -> {
            balloon.dismiss();
            importRomanticCodeFromClipboard();
        });
        menuView.findViewById(R.id.tv_export_video).setOnClickListener(v -> {
            balloon.dismiss();
            requestExportVideo();
        });
        menuView.findViewById(R.id.tv_set_wallpaper).setOnClickListener(v -> {
            balloon.dismiss();
            goSetLiveWallpaper();
        });

        menuView.findViewById(R.id.tv_weather).setOnClickListener(v -> {
            balloon.dismiss();
            showWeatherPicker();
        });
        menuView.findViewById(R.id.tv_lantern).setOnClickListener(v -> {
            balloon.dismiss();
            toggleLanternTapMode();
        });
        menuView.findViewById(R.id.tv_moon).setOnClickListener(v -> {
            balloon.dismiss();
            toggleMoonConstellation();
        });
        menuView.findViewById(R.id.tv_rhythm).setOnClickListener(v -> {
            balloon.dismiss();
            toggleMusicRhythm();
        });

        menuView.findViewById(R.id.tv_pure_mode).setOnClickListener(v -> {
            balloon.dismiss();
            togglePureMode();
        });
        menuView.findViewById(R.id.tv_rocket_mode).setOnClickListener(v -> {
            balloon.dismiss();
            rocketMode = !rocketMode;
            MMKV.defaultMMKV().encode(KEY_ROCKET_MODE, rocketMode);
            Toast.makeText(this, rocketMode ? "指尖发射已开启：点击后火箭升空再炸开 🚀" : "已恢复原地绽放", Toast.LENGTH_SHORT).show();
        });
        menuView.findViewById(R.id.tv_auto_pattern).setOnClickListener(v -> {
            balloon.dismiss();
            showAutoPatternPicker();
        });
        menuView.findViewById(R.id.tv_afterglow).setOnClickListener(v -> {
            balloon.dismiss();
            toggleAfterglow();
        });
        menuView.findViewById(R.id.tv_anniversary).setOnClickListener(v -> {
            balloon.dismiss();
            showAnniversaryList();
        });
        menuView.findViewById(R.id.tv_theme).setOnClickListener(v -> {
            balloon.dismiss();
            showThemePicker();
        });
        menuView.findViewById(R.id.tv_export_gif).setOnClickListener(v -> {
            balloon.dismiss();
            exportLatestVideoAsGif();
        });
        menuView.findViewById(R.id.tv_guide).setOnClickListener(v -> {
            balloon.dismiss();
            showGuide(true);
        });

        // 开关状态点（#9新）：一眼可见当前生效项
        updateMenuStateDots(menuView);

        // 显示在按钮上方
        balloon.showAsDropDown(anchor, 0, -16);
    }

    /** 【#17】节日主题选择 */
    private void showThemePicker() {
        int cur = MMKV.defaultMMKV().decodeInt(KEY_THEME, ThemeManager.THEME_DEFAULT);
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this).setTitle("节日主题")
                .setSingleChoiceItems(ThemeManager.NAMES, cur, (d, w) -> {
                    applyTheme(w);
                    d.dismiss();
                    Toast.makeText(this, "主题：" + ThemeManager.name(w) + " 🎉", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("取消", null).show();
    }

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
        // 找最近一次的录制文件
        File dir = new File(android.os.Environment
                .getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_MOVIES), "RomanticFirework");
        File[] files = dir.listFiles((f, n) -> n.endsWith(".mp4"));
        if (files == null || files.length == 0) {
            Toast.makeText(this, "还没有录制的视频，请先导出浪漫视频", Toast.LENGTH_SHORT).show();
            return;
        }
        java.util.Arrays.sort(files, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
        final File latest = files[0];
        Toast.makeText(this, "正在转换：" + latest.getName(), Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            String out = GifExporter.convert(this, latest.getAbsolutePath());
            mainHandler.post(() -> {
                if (out != null) {
                    Toast.makeText(this, "GIF 已保存：" + out, Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(this, "转换失败（视频可能过长或格式异常）", Toast.LENGTH_SHORT).show();
                }
            });
        }, "gif-export").start();
    }

    private void updateMenuStateDots(View menuView) {
        dot(menuView, R.id.tv_weather, starView.getWeather() != SkyView.WEATHER_NONE);
        dot(menuView, R.id.tv_moon, MMKV.defaultMMKV().decodeBool(KEY_MOON, true));
        dot(menuView, R.id.tv_lantern, lanternTapMode);
        dot(menuView, R.id.tv_rhythm, rhythmOn);
        dot(menuView, R.id.tv_rocket_mode, rocketMode);
        dot(menuView, R.id.tv_afterglow, MMKV.defaultMMKV().decodeBool(KEY_AFTERGLOW, true));
        dot(menuView, R.id.tv_pure_mode, isPureMode);
        TextView themeTv = menuView.findViewById(R.id.tv_theme);
        if (themeTv != null) {
            themeTv.setText("节日主题（当前：" + ThemeManager.name(currentTheme) + "）");
        }
    }

    private void dot(View menuView, int id, boolean on) {
        TextView tv = menuView.findViewById(id);
        if (tv == null) return;
        String base = tv.getText().toString().replace(" ●", "").replace(" ○", "");
        tv.setText(base + (on ? "  ●" : "  ○"));
        tv.setTextColor(on ? 0xFFFFE08A : 0xFFFFFFFF);
    }

    /** 随机剧本（#5新）：调用库编舞引擎 */
    private void showAutoPatternPicker() {
        String[] names = {"经典随机", "波浪扫射", "环形聚拢", "音乐节拍"};
        int cur = MMKV.defaultMMKV().decodeInt(KEY_AUTO_PATTERN, GlobalFirework.PATTERN_WAVE);
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this).setTitle("随机播放剧本").setSingleChoiceItems(names, cur, (d, w) -> {
            MMKV.defaultMMKV().encode(KEY_AUTO_PATTERN, w);
            if (isAutoPlaying) {
                GlobalFirework.getInstance().stopAutoShow();
                GlobalFirework.getInstance().startAutoShow(w, 700);
            }
            d.dismiss();
            Toast.makeText(this, "剧本：" + names[w] + "（点随机播放生效）", Toast.LENGTH_SHORT).show();
        }).setNegativeButton("取消", null).show();
    }

    private void toggleAfterglow() {
        boolean on = !MMKV.defaultMMKV().decodeBool(KEY_AFTERGLOW, true);
        MMKV.defaultMMKV().encode(KEY_AFTERGLOW, on);
        applyAfterglow(on);
        Toast.makeText(this, on ? "烟花余辉已开启：薄纱残影 ✨" : "烟花余辉已关闭", Toast.LENGTH_SHORT).show();
    }

    private void applyAfterglow(boolean on) {
        FireworkView fv = GlobalFirework.getInstance().getFireworkView();
        if (fv != null) fv.setAfterglow(on);
    }

    /** 纪念日列表（#14） */
    private void showAnniversaryList() {
        try {
            org.json.JSONArray arr = new org.json.JSONArray(
                    MMKV.defaultMMKV().decodeString(KEY_ANNIVERSARY, "[]"));
            if (arr.length() == 0) {
                Toast.makeText(this, "还没有纪念日，表白成功后会自动记录", Toast.LENGTH_SHORT).show();
                return;
            }
            String[] items = new String[arr.length()];
            for (int i = 0; i < arr.length(); i++) {
                org.json.JSONObject o = arr.getJSONObject(i);
                items[i] = o.optString("date") + "  " + o.optString("label");
            }
            new com.google.android.material.dialog.MaterialAlertDialogBuilder(this).setTitle("纪念日")
                    .setItems(items, null)
                    .setPositiveButton("清空", (d, w) -> MMKV.defaultMMKV().encode(KEY_ANNIVERSARY, "[]"))
                    .show();
        } catch (Exception ignored) {
        }
    }

    /** 新手引导（#11新）：一次性，可从菜单重看 */
    private void showGuide(boolean force) {
        if (!force && MMKV.defaultMMKV().decodeBool(KEY_GUIDE_SHOWN, false)) return;
        MMKV.defaultMMKV().encode(KEY_GUIDE_SHOWN, true);
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle("玩法速览")
                .setMessage("🔥 点击屏幕：随机烟花绽放\n⏳ 长按屏幕蓄力：松开放中/大/超大全屏烟花\n🏮 更多菜单：孔明灯模式、下雪天气、月亮星座、音乐律动\n💖 顶级浪漫：三幕剧告白，结尾有应答时刻，可录制整场视频\n🎥 长按不再切纯净模式，纯净开关在更多菜单里")
                .setPositiveButton("知道了", null)
                .show();
    }

    /** 天气粒子：雪/樱花/银杏（库 SkyView WEATHER_* 模式） */
    private void showWeatherPicker() {
        String[] names = {"关闭", "下雪 ❄", "樱花 🌸", "银杏 🍂"};
        int[] modes = {SkyView.WEATHER_NONE, SkyView.WEATHER_SNOW, SkyView.WEATHER_SAKURA, SkyView.WEATHER_GINKGO};
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this).setTitle("天气粒子").setItems(names, (d, w) -> {
            MMKV.defaultMMKV().encode(KEY_WEATHER, modes[w]);
            starView.setWeather(modes[w]);
            Toast.makeText(this, "天气：" + names[w], Toast.LENGTH_SHORT).show();
        }).show();
    }

    /** 孔明灯模式：开启后点击屏幕放灯，再点菜单项切回烟花 */
    private void toggleLanternTapMode() {
        lanternTapMode = !lanternTapMode;
        MMKV.defaultMMKV().encode(KEY_LANTERN_MODE, lanternTapMode);
        Toast.makeText(this, lanternTapMode ? "孔明灯模式已开启：点击屏幕放灯 🏮" : "已恢复点击放烟花", Toast.LENGTH_SHORT).show();
    }

    /** 月亮（真实月相）与当日星座连线开关 */
    private void toggleMoonConstellation() {
        boolean on = !MMKV.defaultMMKV().decodeBool(KEY_MOON, true);
        MMKV.defaultMMKV().encode(KEY_MOON, on);
        MMKV.defaultMMKV().encode(KEY_CONSTELLATION, on);
        starView.setMoonEnabled(on);
        starView.setConstellationEnabled(on);
        Toast.makeText(this, on ? "月亮与星座已显示（今日：" + starView.getCurrentZodiacName() + "座）"
                : "月亮与星座已隐藏", Toast.LENGTH_SHORT).show();
    }

    /** 音乐律动：麦克风监听节拍自动绽放（需 RECORD_AUDIO 运行时权限） */
    private void toggleMusicRhythm() {
        if (rhythmOn) {
            rhythmOn = false;
            RhythmFirework.getInstance().stop();
            Toast.makeText(this, "音乐律动已关闭", Toast.LENGTH_SHORT).show();
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
            Toast.makeText(this, "音乐律动已开启：播放音乐试试 🎵", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "麦克风不可用，律动未开启", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * 跳转到系统动态壁纸预览设置页
     */
    private void goSetLiveWallpaper() {
        try {
            Intent intent = new Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER);
            intent.putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                    new ComponentName(this, FireworkWallpaperService.class));
            startActivity(intent);
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "请在系统壁纸设置中选择「烟花动态壁纸」", Toast.LENGTH_SHORT).show();
        }
    }

    private void importRomanticCodeFromClipboard() {
        String clipText = getClipboardText();
        RomanticShareCodec.RomanticInfo info = RomanticShareCodec.decode(clipText);
        if (info == null) {
            Toast.makeText(this, "剪贴板没有找到有效分享码", Toast.LENGTH_SHORT).show();
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
            new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
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
            Toast.makeText(this, "正在录制中...", Toast.LENGTH_SHORT).show();
            return;
        }
        // 先输入告白文案，再申请录屏授权
        final EditText input = new EditText(this);
        input.setHint("输入告白文案");
        input.setText(pendingExportMessage);
        input.setTextColor(Color.WHITE);
        input.setPadding(48, 28, 48, 8);
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle("导出浪漫视频")
                .setMessage("将自动播放 " + (RECORD_DURATION_MS / 1000) + " 秒浪漫特效并录制成 MP4")
                .setView(input)
                .setPositiveButton("开始录制", (d, w) -> {
                    String msg = input.getText().toString().trim();
                    pendingExportMessage = msg.isEmpty() ? "遇见你真好" : msg;
                    MediaProjectionManager pm = (MediaProjectionManager)
                            getSystemService(Context.MEDIA_PROJECTION_SERVICE);
                    projectionPermissionLauncher.launch(pm.createScreenCaptureIntent());
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void onProjectionGranted(int resultCode, Intent data) {
        // 1. 启动录屏服务
        Intent svc = new Intent(this, ScreenRecorderService.class);
        svc.putExtra(ScreenRecorderService.EXTRA_RESULT_CODE, resultCode);
        svc.putExtra(ScreenRecorderService.EXTRA_RESULT_DATA, data);
        ContextCompat.startForegroundService(this, svc);
        isRecording = true;

        showRecordingDialog();

        if (pendingRecordFull) {
            // ===== 录整场（#12旧）：从浪漫模式开始录到应答庆祝结束 =====
            pendingRecordFull = false;
            postRomanticCue(() -> startRomanticMode(pendingExportMessage, countdownSeconds), 600);
            // 【#8】录整场保护：最多 3 分钟自动停，防忘停录爆存储
            recordFullGuard = () -> {
                if (isRecording) {
                    Toast.makeText(this, "录制已达 3 分钟上限，自动保存", Toast.LENGTH_SHORT).show();
                    stopRecordingService();
                }
            };
            mainHandler.postDelayed(recordFullGuard, 180000);
            return;
        }

        // 2. 进入纯净模式隐藏按钮栏，录出来更干净
        if (!isPureMode) togglePureMode();

        // 3. 启动浪漫模式（用倒计时 3 秒 + 用户文案）
        mainHandler.postDelayed(() -> startRomanticMode(pendingExportMessage, 3), 400);

        // 4. 到点自动停止录制
        mainHandler.postDelayed(this::stopRecordingService, RECORD_DURATION_MS);
    }

    private void showRecordingDialog() {
        dismissRecordingDialog();
        View v = getLayoutInflater().inflate(R.layout.loading_dialog, null);
        recordingDialog = new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setView(v)
                .setCancelable(false)
                .create();
        recordingDialog.show();
    }

    private void dismissRecordingDialog() {
        if (recordingDialog != null && recordingDialog.isShowing()) {
            recordingDialog.dismiss();
        }
        recordingDialog = null;
    }

    private void stopRecordingService() {
        if (!isRecording) return;
        Intent i = new Intent(this, ScreenRecorderService.class);
        i.setAction(ScreenRecorderService.ACTION_STOP);
        ContextCompat.startForegroundService(this, i);
        // 录完退出浪漫模式
        mainHandler.postDelayed(this::stopRomanticMode, 800);
    }

    private void shareVideo(String path) {
        try {
            File f = new File(path);
            Uri uri = androidx.core.content.FileProvider.getUriForFile(
                    this, getPackageName() + ".fileprovider", f);
            Intent share = new Intent(Intent.ACTION_SEND);
            share.setType("video/mp4");
            share.putExtra(Intent.EXTRA_STREAM, uri);
            share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(share, "分享浪漫视频"));
        } catch (Exception e) {
            // FileProvider 未配置就只提示路径
            e.printStackTrace();
        }
    }

}