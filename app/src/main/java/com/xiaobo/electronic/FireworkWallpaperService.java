package com.xiaobo.electronic;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Color;
import android.os.Handler;
import android.os.HandlerThread;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.service.wallpaper.WallpaperService;
import android.view.View;

import com.tencent.mmkv.MMKV;
import com.xiaobo.fireworks.FireworkConfig;
import com.xiaobo.fireworks.FireworkView;
import com.xiaobo.fireworks.GlobalFirework;
import com.xiaobo.fireworks.SkyView;

import java.util.Random;

public class FireworkWallpaperService extends WallpaperService {

    private static final String KEY_CUSTOM_FIREWORK_TEXT = "custom_firework_text";
    private static final String DEFAULT_FIREWORK_TEXT = "新年快乐";

    @Override
    public Engine onCreateEngine() {
        return new FireworkEngine();
    }

    private class FireworkEngine extends Engine {
        private HandlerThread drawThread;
        private Handler drawHandler;
        private final Random random = new Random();
        private FireworkConfig[] fireworkPool;

        // 每个Engine独立持有渲染器，互不干扰
        private SkyView starView;
        private FireworkView fireworkView;

        private boolean isSurfaceReady = false;
        private boolean isVisible = false;
        private boolean isDrawing = false;
        private long frameDelay;

        private long lastFireTime = 0;
        private long fireInterval = 800;
        private int fireBaseMs;
        private int fireRandomMs;

        private final Runnable drawTask = this::drawFrame;

        @Override
        public void onCreate(SurfaceHolder surfaceHolder) {
            super.onCreate(surfaceHolder);
            // 开启触摸事件
            setTouchEventsEnabled(true);
            MMKV.initialize(getApplicationContext());

            // 按设备性能档调整绘制节奏：低端机锁 30fps，降低随机烟花频率
            DevicePerf perf = DevicePerf.get(getApplicationContext());
            frameDelay = perf.wallpaperFrameDelayMs;
            fireBaseMs = perf.wallpaperFireBaseMs;
            fireRandomMs = perf.wallpaperFireRandomMs;

            // 初始化公共资源（音效、震动），重复调用会自动去重
            GlobalFirework.getInstance().init(getApplication(), -1);

            // 【关键】每个Engine独立创建自己的烟花渲染器
            fireworkView = new FireworkView(getApplicationContext());
            fireworkView.resume();

            starView = new SkyView(getApplicationContext());
            // 复用库 SkyView 的夜空效果：按档位与用户设置（与 App 共用 MMKV）下发
            // ===== 壁纸独立省电档（#12新）：比 App 更保守的帧率/粒子预算 =====
            starView.setStarCount(Math.max(24, perf.starCount * 3 / 5));
            starView.setStarAntiAlias(perf.starAntiAlias);
            starView.setAutoMeteorIntervalMs(Math.max(8000, perf.meteorBaseMs), perf.meteorRandomMs);
            starView.setAutoLanternIntervalMs(
                    perf.tier() == DevicePerf.Tier.LOW ? 0 : 90000,
                    60000);
            starView.setWeather(MMKV.defaultMMKV().decodeInt("weather_mode", 0));
            starView.setMoonEnabled(MMKV.defaultMMKV().decodeBool("moon_enabled", true));
            starView.setConstellationEnabled(MMKV.defaultMMKV().decodeBool("constellation_enabled", true));
            // 壁纸帧率：独立档（低端 33ms、中端 33ms、高端 25ms）
            frameDelay = Math.max(33, perf.wallpaperFrameDelayMs);
            fireBaseMs = (int) (perf.wallpaperFireBaseMs * 1.5f); // 壁纸更省：频率降 1/3
            fireRandomMs = perf.wallpaperFireRandomMs;
            fireworkView.setMaxParticles(3000);
            // 原始帧率参数从 DevicePerf 读取（壁纸省电档已覆盖为更保守值）
            initFireworkPool();

            // 启动独立绘制线程
            drawThread = new HandlerThread("FireworkWallpaper_" + hashCode());
            drawThread.start();
            drawHandler = new Handler(drawThread.getLooper());
        }

        @Override
        public void onSurfaceCreated(SurfaceHolder holder) {
            super.onSurfaceCreated(holder);
            isSurfaceReady = true;
            checkAndStartDraw();
        }

        @Override
        public void onSurfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            super.onSurfaceChanged(holder, format, width, height);
            // 手动测量布局，确保渲染器获得正确尺寸
            int widthSpec = View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY);
            int heightSpec = View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY);
            fireworkView.measure(widthSpec, heightSpec);
            fireworkView.layout(0, 0, width, height);
            starView.setSize(width, height);
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);
            isVisible = visible;
            if (visible) {
                checkAndStartDraw();
            } else {
                stopDraw();
            }
        }

        @Override
        public void onSurfaceDestroyed(SurfaceHolder holder) {
            super.onSurfaceDestroyed(holder);
            isSurfaceReady = false;
            stopDraw();
            // 只销毁当前Engine自己的渲染器，不碰全局资源
            if (fireworkView != null) {
                fireworkView.stop();
                fireworkView = null;
            }
        }

        @Override
        public void onDestroy() {
            super.onDestroy();
            stopDraw();
            if (drawThread != null) {
                drawThread.quitSafely();
                drawThread = null;
                drawHandler = null;
            }
            // 【移除清理#24】回收 emoji 位图缓存：壁纸预览/切换反复重建 Engine 时不攒垃圾
            for (android.graphics.Bitmap b : wpEmojiCache.values()) {
                if (b != null && !b.isRecycled()) b.recycle();
            }
            wpEmojiCache.clear();
            starView = null;
        }

        /**
         * 双条件校验：Surface就绪 + 桌面可见，才启动绘制循环
         */
        private void checkAndStartDraw() {
            if (isSurfaceReady && isVisible && !isDrawing && drawHandler != null) {
                isDrawing = true;
                startDrawLoop();
            }
        }

        private void stopDraw() {
            isDrawing = false;
            if (drawHandler != null) {
                drawHandler.removeCallbacks(drawTask);
            }
        }

        private long lastWallTapMs = 0L;
        private float lastWallTapX, lastWallTapY;

        @Override
        public void onTouchEvent(MotionEvent event) {
            super.onTouchEvent(event);
            if (event.getAction() != MotionEvent.ACTION_UP) return;
            long now = System.currentTimeMillis();
            float dx = event.getX() - lastWallTapX;
            float dy = event.getY() - lastWallTapY;
            boolean doubleTap = (now - lastWallTapMs < 400) && (dx * dx + dy * dy < 10000);
            lastWallTapMs = now;
            lastWallTapX = event.getX();
            lastWallTapY = event.getY();
            if (doubleTap) {
                // ===== #7新：壁纸双击放孔明灯（与 App 交互一致） =====
                if (starView != null) {
                    starView.releaseLantern(event.getX(), event.getY());
                }
                return;
            }
            if (fireworkView != null) {
                int index = random.nextInt(fireworkPool.length);
                FireworkConfig config = getEffectiveConfig(fireworkPool[index], surfaceWidth());
                fireworkView.launch(event.getX(), event.getY(), config);
            }
        }

        /** 当前 Surface 宽度（自适应字号用；Surface 未就绪时退回屏幕宽） */
        private int surfaceWidth() {
            try {
                int w = getSurfaceHolder().getSurfaceFrame().width();
                return w > 0 ? w : getResources().getDisplayMetrics().widthPixels;
            } catch (Exception e) {
                return getResources().getDisplayMetrics().widthPixels;
            }
        }

        private void initFireworkPool() {
            fireworkPool = new FireworkConfig[]{
                    new FireworkConfig.Builder().build(),
                    new FireworkConfig.Builder()
                            .shape(FireworkConfig.Shape.HEART)
                            .range(0.9f)
                            .build(),
                    new FireworkConfig.Builder()
                            .shape(FireworkConfig.Shape.STAR)
                            .colors(Color.CYAN, Color.MAGENTA)
                            .range(1.1f)
                            .build(),
                    new FireworkConfig.Builder()
                            .rocket(true)
                            .colors(Color.YELLOW, Color.RED)
                            .range(1.2f)
                            .build(),
                    new FireworkConfig.Builder()
                            .shape(FireworkConfig.Shape.TEXT)
                            .text(DEFAULT_FIREWORK_TEXT)
                            .colors(Color.RED, Color.YELLOW)
                            .range(1.2f)
                            .build(),
                    // ===== #8新：图形烟花进壁纸随机池 =====
                    new FireworkConfig.Builder()
                            .shape(FireworkConfig.Shape.CUSTOM)
                            .shapeBitmap(wallpaperEmojiBitmap("🌹"))
                            .colors(0xFFFF2D55, 0xFFFF69B4).range(1.1f).build(),
                    new FireworkConfig.Builder()
                            .shape(FireworkConfig.Shape.CUSTOM)
                            .shapeBitmap(wallpaperEmojiBitmap("🎁"))
                            .colors(0xFFFFD700, Color.WHITE).range(1.1f).build(),
                    new FireworkConfig.Builder()
                            .shape(FireworkConfig.Shape.CUSTOM)
                            .shapeBitmap(wallpaperEmojiBitmap("🎂"))
                            .colors(0xFFFFD700, 0xFFFF69B4).range(1.1f).build()
            };
        }

        private final java.util.HashMap<String, android.graphics.Bitmap> wpEmojiCache = new java.util.HashMap<>();

        private android.graphics.Bitmap wallpaperEmojiBitmap(String emoji) {
            android.graphics.Bitmap cached = wpEmojiCache.get(emoji);
            if (cached != null) return cached;
            int px = 200;
            android.graphics.Bitmap bmp = android.graphics.Bitmap.createBitmap(px, px, android.graphics.Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(bmp);
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            p.setTextAlign(Paint.Align.CENTER);
            p.setTextSize(px * 0.8f);
            c.drawText(emoji, px / 2f, px * 0.76f, p);
            wpEmojiCache.put(emoji, bmp);
            return bmp;
        }

        private void startDrawLoop() {
            if (drawHandler == null) return;
            drawHandler.removeCallbacks(drawTask);
            drawHandler.post(drawTask);
        }

        private void drawFrame() {
            if (!isDrawing || !isSurfaceReady || !isVisible || drawHandler == null) return;

            SurfaceHolder holder = getSurfaceHolder();
            Canvas canvas = null;
            try {
                canvas = holder.lockCanvas();
                if (canvas != null && fireworkView != null) {
                    // 1. 黑色背景
                    canvas.drawColor(Color.BLACK);

                    // 2. 星空背景（含流星）：按壁纸帧间隔做时间补偿，各档位下运动速率一致
                    starView.updateSky(frameDelay / 16.667f);
                    starView.drawSky(canvas);

                    // 3. 随机发射烟花（频率按设备性能档自适应）
                    long now = System.currentTimeMillis();
                    if (now - lastFireTime > fireInterval) {
                        launchRandomFirework(canvas.getWidth(), canvas.getHeight());
                        lastFireTime = now;
                        fireInterval = fireBaseMs + random.nextInt(fireRandomMs);
                    }

                    // 4. 更新并绘制烟花
                    fireworkView.update();
                    fireworkView.drawToCanvas(canvas);
                }
            } catch (Exception e) {
                // 单帧异常不终止整个循环，兜底保活
            } finally {
                if (canvas != null) {
                    try {
                        holder.unlockCanvasAndPost(canvas);
                    } catch (Exception e) {
                        // Surface销毁异常兜底
                    }
                }
            }

            // 调度下一帧（按性能档控制帧率）
            if (drawHandler != null && isDrawing) {
                drawHandler.postDelayed(drawTask, frameDelay);
            }
        }

        private void launchRandomFirework(int width, int height) {
            if (fireworkView == null) return;
            // 四周留边，避免烟花贴边显示不全，适配竖屏桌面
            float margin = 120f;
            float x = margin + random.nextFloat() * (width - margin * 2);
            float y = margin + random.nextFloat() * (height - margin * 2);
            int index = random.nextInt(fireworkPool.length);
            FireworkConfig config = getEffectiveConfig(fireworkPool[index], width);
            fireworkView.launch(x, y, config);
        }

        private FireworkConfig getEffectiveConfig(FireworkConfig origin, int widthPx) {
            if (origin.shape == FireworkConfig.Shape.TEXT) {
                String customText = MMKV.defaultMMKV()
                        .decodeString(KEY_CUSTOM_FIREWORK_TEXT, DEFAULT_FIREWORK_TEXT);
                return new FireworkConfig.Builder()
                        .shape(FireworkConfig.Shape.TEXT)
                        .text(customText)
                        .colors(Color.RED, Color.YELLOW)
                        .range(origin.explosionRange)
                        .textSize(fitWallTextSize(customText, 100f, widthPx)) // 【溢出修复#24】长文字不溢出壁纸
                        .build();
            }
            return origin;
        }

        /** 壁纸版字号自适应：按 Surface 宽 82% 等比缩小（Java 11 内部类禁静态成员，改实例方法） */
        private float fitWallTextSize(String text, float maxSize, int widthPx) {
            if (text == null || text.isEmpty() || widthPx <= 0) return maxSize;
            Paint tp = new Paint();
            tp.setTextSize(maxSize);
            float tw = tp.measureText(text);
            float allowed = widthPx * 0.82f;
            return tw <= allowed ? maxSize : Math.max(36f, maxSize * allowed / tw);
        }
    }
}
