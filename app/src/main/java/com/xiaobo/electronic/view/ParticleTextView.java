package com.xiaobo.electronic.view;

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
import android.view.animation.OvershootInterpolator;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import com.xiaobo.electronic.util.DevicePerf;

/**
 * 魔幻粒子文字控件 - 修复显示不全问题
 */
public class ParticleTextView extends View {

    /** 入场特效风格（随机特效#20）：像 PPT 翻页动画，每句台词随机抽一种 */
    public enum EntranceStyle {
        GATHER,      // 四面八方聚拢（原版）
        FLY_LEFT,    // 左侧飞入
        FLY_RIGHT,   // 右侧飞入
        DROP,        // 自上而下落下
        RISE,        // 自下而上升起
        FADE_CENTER, // 原地淡入放大
        WAVE,        // 波浪式从左到右擦除
        BOUNCE       // 弹跳聚拢（过冲回弹）
    }

    private final List<Particle> particles = new ArrayList<>();
    private final Paint paint = new Paint();
    private ValueAnimator animator;
    private float progress = 0f;
    private final Random random = new Random();

    // 当前入场风格与派生开关（showText 主线程设置后，后台生成粒子时读取）
    private EntranceStyle entranceStyle = EntranceStyle.GATHER;
    private boolean scaleIn = false; // FADE_CENTER：半径随进度放大

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
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    // Color.HSVToColor 临时数组复用，避免逐粒子分配
    private final float[] hsvTmp = new float[3];
    // 【性能#22】文字包围盒复用（采样范围裁剪用，仅后台采样线程访问）
    private final Rect textBBox = new Rect();
    // 档位查询移出每帧循环（onDraw 每帧只读一次字段）
    private boolean lowEndDevice;

    public ParticleTextView(Context context) {
        this(context, null);
    }

    public ParticleTextView(Context context, AttributeSet attrs) {
        super(context, attrs);
        paint.setAntiAlias(true);
        lowEndDevice = DevicePerf.get(context).isLowEnd();
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
        showText(text, EntranceStyle.GATHER, onEnd);
    }

    public void showText(String text, EntranceStyle style, Runnable onEnd) {
        entranceStyle = style;
        scaleIn = (style == EntranceStyle.FADE_CENTER);
        if (getWidth() == 0 || getHeight() == 0) {
            post(() -> showText(text, style, onEnd));
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
        // BOUNCE：过冲回弹；其余：减速滑入
        animator.setInterpolator(entranceStyle == EntranceStyle.BOUNCE
                ? new OvershootInterpolator(1.6f) : new DecelerateInterpolator());
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

        // ===== 【性能#22】采样范围裁剪：只扫文字实际包围盒，跳过全屏空白 =====
        // 【修复#25】CENTER 对齐时 drawText(width/2, y) 的墨迹起点是 width/2 - W/2（W 为实宽），
        // 裁剪窗口必须左移 W/2；此前窗口右移半行字宽，台词左半的粒子被裁掉（文字丢一半）。
        // 注意 W 必须用缩放后字号的 measureText（textWidth 变量是缩放前测量值，不能用）
        textPaint.getTextBounds(text, 0, text.length(), textBBox);
        float drawW = textPaint.measureText(text); // 缩放后字号的真实文字宽度
        float drawX0 = width / 2f - drawW / 2f;    // CENTER 对齐下文字左端
        int bx0 = Math.max(0, (int) (drawX0 + textBBox.left) - 4);
        int bx1 = Math.min(width, (int) (drawX0 + textBBox.right) + 4);
        int by0 = Math.max(0, (int) (y + textBBox.top) - 4);
        int by1 = Math.min(height, (int) (y + textBBox.bottom) + 4);
        if (bx1 <= bx0 || by1 <= by0) {
            mainHandler.post(() -> particles.clear()); // 空内容保护：不产生粒子
            return;
        }

        // 【清晰度#25】采样步长随最终字号自适应：长句缩放后字号变小，同步加密采样，
        // 保证中文笔画连续可认；字小面积小，粒子总量反而下降（性能无损）
        float finalTs = textPaint.getTextSize();
        int step = Math.max(2, Math.min(sampleSize, (int) (finalTs / 28f)));

        // 【性能】只取文字包围盒内的像素。原实现整屏 getPixels：1080×2400 ≈ 259 万 int
        // （≈10MB）每次台词拷贝一遍，而文字只占其中一小块。改为按 bbox 取值后省 80%+
        // 内存带宽，栅格化结果与整屏取值完全一致。
        int bw = bx1 - bx0;
        int bh = by1 - by0;
        if (reusablePixels == null || reusablePixels.length < bw * bh) {
            reusablePixels = new int[bw * bh];
        }
        int[] pixels = reusablePixels;
        sampleBitmap.getPixels(pixels, 0, bw, bx0, by0, bw, bh);

        for (int i = bx0; i < bx1; i += step) {
            for (int j = by0; j < by1; j += step) {
                if (pixels[(j - by0) * bw + (i - bx0)] != 0) {
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

                    applyEntrance(p, width, height); // 【随机特效#20】按风格定起点/延迟

                    tempParticles.add(p);
                }
            }
        }

        // ===== 【性能保护】超大文字粒子上限：超限时加大采样步长稀释（按比例，形状不变） =====
        int maxParticles = DevicePerf.get(getContext()).maxTextParticles;
        if (tempParticles.size() > maxParticles) {
            int newStep = (int) Math.ceil(step * Math.sqrt(
                    (double) tempParticles.size() / maxParticles));
            if (newStep > step) {
                tempParticles.clear();
                for (int i = bx0; i < bx1; i += newStep) {
                    for (int j = by0; j < by1; j += newStep) {
                        if (pixels[(j - by0) * bw + (i - bx0)] != 0) {
                            Particle p = new Particle();
                            p.targetX = i;
                            p.targetY = j;
                            p.radius = 2f + random.nextFloat() * 3f;
                            int hue = random.nextInt(hueMax - hueMin) + hueMin;
                            hsvTmp[0] = hue;
                            hsvTmp[1] = 0.6f;
                            hsvTmp[2] = 1f;
                            p.color = Color.HSVToColor(hsvTmp);
                            applyEntrance(p, width, height);
                            tempParticles.add(p);
                        }
                    }
                }
            }
        }

        // 色相桶中心颜色（生成时重算，渲染时直接取）——已随桶化渲染回退一并移除

        mainHandler.post(() -> {
            particles.clear();
            particles.addAll(tempParticles);
        });
    }

    /** 【随机特效#20】按入场风格确定粒子起点与出场延迟 */
    private void applyEntrance(Particle p, int width, int height) {
        p.speedFactor = 0.5f + random.nextFloat();
        if (entranceStyle == EntranceStyle.BOUNCE) {
            p.speedFactor = 1f; // 【修复#25】弹跳幕统一速度：过冲由插值器整体提供，
            // 各粒子速度不同且不截断时，弹跳全程聚不成字（有一种特效看不到文字）
        }
        double angle = random.nextDouble() * 2 * Math.PI;
        float r = Math.max(width, height);
        switch (entranceStyle) {
            case FLY_LEFT:
                p.startX = -width * 0.2f;
                p.startY = p.targetY;
                break;
            case FLY_RIGHT:
                p.startX = width * 1.2f;
                p.startY = p.targetY;
                break;
            case DROP: // 自上而下：从目标点上方落下，保留少量横向飘移
                p.startX = p.targetX + (random.nextFloat() - 0.5f) * 40f;
                p.startY = p.targetY - height * (0.5f + random.nextFloat() * 0.4f);
                break;
            case RISE: // 自下而上
                p.startX = p.targetX + (random.nextFloat() - 0.5f) * 40f;
                p.startY = p.targetY + height * (0.5f + random.nextFloat() * 0.4f);
                break;
            case FADE_CENTER: // 原地淡入放大：起点=终点，靠 onDraw 半径/透明度渐变
                p.startX = p.targetX;
                p.startY = p.targetY;
                break;
            case WAVE: // 波浪擦除：圆周起点 + 从左到右依次点亮
                p.startX = (float) (width / 2f + Math.cos(angle) * r);
                p.startY = (float) (height / 2f + Math.sin(angle) * r);
                p.delay = Math.min(0.5f, (p.targetX / (float) width) * 0.5f);
                break;
            default: // GATHER / BOUNCE：四面八方聚拢（BOUNCE 靠插值器过冲回弹）
                p.startX = (float) (width / 2f + Math.cos(angle) * r);
                p.startY = (float) (height / 2f + Math.sin(angle) * r);
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float currentProgress = progress;
        if (particles.isEmpty()) return;

        // 低端机关闭光晕，省填充（档位在构造器缓存，避免每帧查询）
        boolean glow = !lowEndDevice;
        boolean bounce = (entranceStyle == EntranceStyle.BOUNCE); // 过冲允许 curr>1

        // 渲染路径保持原版逐粒子 drawCircle（软渲染环境实测为最优）；
        // 性能优化点：每粒子仅一次 setColor（原版 glow/core 各设一次颜色，第二次冗余）
        for (int i = 0; i < particles.size(); i++) {
            Particle p = particles.get(i);
            // 【随机特效#20】带出场延迟的粒子：延迟过后才出发（波浪擦除用）
            float t = currentProgress;
            if (p.delay > 0f) {
                t = (t - p.delay) / (1f - p.delay);
                if (t < 0f) t = 0f;
            }
            float curr;
            if (bounce) {
                curr = t; // 【修复#25】BOUNCE 统一进度，允许整体过冲>1 再回弹聚字
            } else if (currentProgress >= 1f) {
                curr = 1f; // 【修复#25】动画终点强制到位：慢粒子不再半路停靠，聚字清晰可认
            } else {
                curr = t * p.speedFactor;
            }
            if (curr < 0f) curr = 0f;
            if (curr > 1f && !bounce) curr = 1f;

            float x = p.startX + (p.targetX - p.startX) * curr;
            float y = p.startY + (p.targetY - p.startY) * curr;

            int alpha = (int) (255 * curr);
            if (alpha > 255) alpha = 255;
            if (alpha < 0) alpha = 0;
            float radius = p.radius;
            if (scaleIn) { // FADE_CENTER：半径随进度从 15% 放大到 100%
                float rr = Math.min(1f, curr);
                radius = p.radius * (0.15f + 0.85f * rr);
            }
            paint.setColor(p.color);
            if (glow) {
                // 外层光晕：同色、大半径、低 alpha，营造发光感
                paint.setAlpha(alpha / 5);
                canvas.drawCircle(x, y, radius * 2.6f, paint);
            }
            // 实心核心
            paint.setAlpha(alpha);
            canvas.drawCircle(x, y, radius, paint);
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
        float delay; // 【随机特效#20】归一化出场延迟（0~0.5，波浪擦除用）
    }
}