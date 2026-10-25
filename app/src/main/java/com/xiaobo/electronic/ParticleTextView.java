package com.xiaobo.electronic;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

/**
 * 魔幻粒子文字控件 - 修复显示不全问题
 */
public class ParticleTextView extends View {

    private final List<Particle> particles = new ArrayList<>();
    private final Paint paint = new Paint();
    private ValueAnimator animator;
    private float progress = 0f;
    private final Random random = new Random();

    // 线程池处理计算
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private int hueMin = 30;
    private int hueMax = 70;
    private final int sampleSize;
    private int[] reusablePixels; // 复用像素数组，避免每次全屏分配 int[width*height]

    // 采样位图复用：倒计时/告白每个文字都要全屏采样一次，
    // 复用可避免每次分辨率级的 10MB 分配，降低 GC 压力；
    // 尺寸不变时重复使用同一张位图，栅格化结果与新建完全一致
    private Bitmap sampleBitmap;
    private Canvas sampleCanvas;
    // 文字画笔参数每次完全相同，提升为成员复用，绘制结果不变
    private final Paint textPaint = new Paint();
    // Color.HSVToColor 临时数组复用，避免逐粒子分配
    private final float[] hsvTmp = new float[3];

    public ParticleTextView(Context context) {
        this(context, null);
    }

    public ParticleTextView(Context context, AttributeSet attrs) {
        super(context, attrs);
        paint.setAntiAlias(true);
        // 保持性能优化：不使用 ShadowLayer，依靠粒子重叠发光
        sampleSize = DevicePerf.get(context).particleSampleSize;
        textPaint.setColor(Color.WHITE);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setFakeBoldText(true);
    }


    public void setTextColorHue(int min, int max) {
        hueMin = min;
        hueMax = max;
    }

    public void showText(String text, Runnable onEnd) {
        if (getWidth() == 0 || getHeight() == 0) {
            post(() -> showText(text, onEnd));
            return;
        }

        // 异步生成粒子；视图已销毁（executor 已关闭）时静默放弃，不影响主流程
        try {
            executor.execute(() -> {
                generateParticles(text);
                mainHandler.post(() -> startGatherAnimation(onEnd));
            });
        } catch (RejectedExecutionException ignored) {
        }
    }

    private void startGatherAnimation(Runnable onEnd) {
        if (animator != null) animator.cancel();
        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(1200);
        animator.setInterpolator(new DecelerateInterpolator());
        animator.addUpdateListener(animation -> {
            progress = (float) animation.getAnimatedValue();
            invalidate();
        });
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (onEnd != null) onEnd.run();
            }
        });
        animator.start();
    }

    public void explode(Runnable onEnd) {
        if (animator != null) animator.cancel();
        animator = ValueAnimator.ofFloat(1f, 0f);
        animator.setDuration(600);
        animator.setInterpolator(new DecelerateInterpolator());
        animator.addUpdateListener(animation -> {
            progress = (float) animation.getAnimatedValue();
            invalidate();
        });
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                particles.clear();
                invalidate();
                if (onEnd != null) onEnd.run();
            }
        });
        animator.start();
    }

    // 核心修复：后台线程生成粒子逻辑（位图/画笔/像素数组全部复用，绘制结果与原版一致）
    private void generateParticles(String text) {
        List<Particle> tempParticles = new ArrayList<>();

        int width = getWidth();
        int height = getHeight();

        // 复用采样位图：仅在尺寸变化时重建，每次先清透明，等价于新建的零填充位图
        if (sampleBitmap == null || sampleBitmap.getWidth() != width || sampleBitmap.getHeight() != height) {
            if (sampleBitmap != null) sampleBitmap.recycle();
            sampleBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            sampleCanvas = new Canvas(sampleBitmap);
        }
        sampleBitmap.eraseColor(Color.TRANSPARENT);
        Canvas canvas = sampleCanvas;
        Paint textPaint = this.textPaint; // 复用成员画笔，参数与原逐次新建完全相同

        // --- 修复开始：动态计算字号，确保文字完整显示 ---

        // 1. 设置一个初始的大字号
        float textSize = width / 4f;
        textPaint.setTextSize(textSize);

        // 2. 测量文字的实际宽度
        float textWidth = textPaint.measureText(text);

        // 3. 定义最大允许宽度（屏幕宽度的 85%，留出左右边距）
        float maxAllowedWidth = width * 0.85f;

        // 4. 如果文字宽度超过了允许宽度，按比例缩小字号
        if (textWidth > maxAllowedWidth) {
            float scale = maxAllowedWidth / textWidth;
            textPaint.setTextSize(textSize * scale);
        }

        // 5. 垂直居中计算 (基于新的字号)
        Paint.FontMetrics metrics = textPaint.getFontMetrics();
        float textHeight = metrics.descent - metrics.ascent;
        float y = height / 2f - (metrics.descent + metrics.ascent) / 2f;

        // 绘制文字到 Bitmap
        canvas.drawText(text, width / 2f, y, textPaint);


        int step = sampleSize; // 采样密度（低端机自动加大，减少粒子数）
        // 直接读取像素数组优化性能；复用数组避免反复分配 width*height 大小的 int[]
        if (reusablePixels == null || reusablePixels.length != width * height) {
            reusablePixels = new int[width * height];
        }
        int[] pixels = reusablePixels;
        sampleBitmap.getPixels(pixels, 0, width, 0, 0, width, height);

        for (int i = 0; i < width; i += step) {
            for (int j = 0; j < height; j += step) {
                if (pixels[j * width + i] != 0) {
                    Particle p = new Particle();
                    p.targetX = i;
                    p.targetY = j;
                    p.radius = 2f + random.nextFloat() * 3f;

                    // 颜色逻辑 - 动态色相范围（复用临时数组，避免逐粒子分配）
                    int hue = random.nextInt(hueMax - hueMin) + hueMin;
                    hsvTmp[0] = hue;
                    hsvTmp[1] = 0.6f;
                    hsvTmp[2] = 1f;
                    p.color = Color.HSVToColor(hsvTmp);


                    // 随机起始点
                    double angle = random.nextDouble() * 2 * Math.PI;
                    float r = Math.max(width, height);
                    p.startX = (float) (width / 2f + Math.cos(angle) * r);
                    p.startY = (float) (height / 2f + Math.sin(angle) * r);
                    p.speedFactor = 0.5f + random.nextFloat();

                    tempParticles.add(p);
                }
            }
        }

        mainHandler.post(() -> {
            particles.clear();
            particles.addAll(tempParticles);
        });
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float currentProgress = progress;
        if (particles.isEmpty()) return;

        // 低端机关闭光晕，省填充
        boolean glow = !DevicePerf.get(getContext()).isLowEnd();

        for (int i = 0; i < particles.size(); i++) {
            Particle p = particles.get(i);
            float curr = currentProgress * p.speedFactor;
            if (curr > 1f) curr = 1f;

            float x = p.startX + (p.targetX - p.startX) * curr;
            float y = p.startY + (p.targetY - p.startY) * curr;

            int alpha = (int) (255 * curr);
            if (glow) {
                // 外层光晕：同色、大半径、低 alpha，营造发光感
                paint.setColor(p.color);
                paint.setAlpha(alpha / 5);
                canvas.drawCircle(x, y, p.radius * 2.6f, paint);
            }
            // 实心核心
            paint.setColor(p.color);
            paint.setAlpha(alpha);
            canvas.drawCircle(x, y, p.radius, paint);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        executor.shutdownNow();
    }

    private static class Particle {
        float startX, startY;
        float targetX, targetY;
        float radius;
        int color;
        float speedFactor;
    }
}