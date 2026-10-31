package com.xiaobo.electronic;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.LinearInterpolator;

import java.util.List;

/**
 * 星空许愿墙的星星层。
 *
 * <p>覆盖在夜空之上：每颗星是一个愿望，点一下能看到当时写了什么，
 * 点空白处就再挂一颗新的。星星缓慢明暗——静态的圆点看着像噪点，
 * 有呼吸感才像"许愿"。
 *
 * <p>闪烁用 ValueAnimator 驱动而不是逐帧 postInvalidate：
 * 动画在离开屏幕时随 View detach 一起取消，不会在后台空转耗电。
 */
public class WishSkyView extends View {

    public interface Listener {
        /** 点到已有星星 */
        void onWishTapped(WishStore.Wish w);

        /** 点到空白（坐标为 0~1 比例） */
        void onBlankTapped(float xRatio, float yRatio);
    }

    private static final float HIT_RADIUS_DP = 26f;

    private final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint core = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ray = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path rayPath = new Path();

    private List<WishStore.Wish> wishes;
    private Listener listener;
    private ValueAnimator pulse;
    private float breathe = 0f;

    public WishSkyView(Context c) {
        super(c);
        wishes = WishStore.load();
        glow.setStyle(Paint.Style.FILL);
        core.setStyle(Paint.Style.FILL);
        core.setColor(0xFFFFFFFF);
        ray.setStyle(Paint.Style.STROKE);
        ray.setColor(0xAAFFF3C4);
        ray.setStrokeCap(Paint.Cap.ROUND);
    }

    public void setListener(Listener l) {
        this.listener = l;
    }

    public void refresh() {
        wishes = WishStore.load();
        invalidate();
    }

    public int count() {
        return wishes == null ? 0 : wishes.size();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        startBreathing();
    }

    @Override
    protected void onDetachedFromWindow() {
        stopBreathing();
        super.onDetachedFromWindow();
    }

    private void startBreathing() {
        stopBreathing();
        pulse = ValueAnimator.ofFloat(0f, 1f);
        pulse.setDuration(2600);
        pulse.setRepeatCount(ValueAnimator.INFINITE);
        pulse.setRepeatMode(ValueAnimator.REVERSE);
        pulse.setInterpolator(new LinearInterpolator());
        pulse.addUpdateListener(a -> {
            breathe = (float) a.getAnimatedValue();
            invalidate();
        });
        pulse.start();
    }

    private void stopBreathing() {
        if (pulse != null) {
            pulse.cancel();
            pulse = null;
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (wishes == null) return;
        float w = getWidth(), h = getHeight();
        float density = getResources().getDisplayMetrics().density;
        if (wishes.isEmpty()) {
            // 空星空什么都不画，用户会以为"功能没加上"。给一句落地的引导。
            hint.setTextAlign(Paint.Align.CENTER);
            hint.setTextSize(15f * density);
            hint.setColor(0xCCFFD700);
            canvas.drawText("点一下夜空，挂上第一颗愿望星", w / 2f, h * 0.5f, hint);
            hint.setTextSize(12f * density);
            hint.setColor(0x88FFFFFF);
            canvas.drawText("以后点星星就能看到当时写的话", w / 2f, h * 0.5f + 26f * density, hint);
            return;
        }
        for (int i = 0; i < wishes.size(); i++) {
            WishStore.Wish item = wishes.get(i);
            float cx = item.x * w;
            float cy = item.y * h;
            // 每颗星错开相位，整片星空才不会齐刷刷一起亮
            float phase = breathe + i * 0.37f;
            float b = 0.55f + 0.45f * (float) Math.abs(Math.sin(phase * Math.PI));
            float r = (5f + 4f * b) * density;

            glow.setColor(0xFFFFF3C4);
            glow.setAlpha((int) (70 * b));
            canvas.drawCircle(cx, cy, r * 3.2f, glow);
            glow.setAlpha((int) (130 * b));
            canvas.drawCircle(cx, cy, r * 1.7f, glow);

            core.setAlpha((int) (200 + 55 * b));
            canvas.drawCircle(cx, cy, r * 0.55f, core);

            // 四芒星：愿望越大越亮，光刺越长
            ray.setStrokeWidth(1.6f * density);
            ray.setAlpha((int) (150 * b));
            float len = r * (2.4f + b);
            canvas.drawLine(cx - len, cy, cx + len, cy, ray);
            canvas.drawLine(cx, cy - len, cx, cy + len, ray);
        }
        rayPath.rewind();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() != MotionEvent.ACTION_UP) return true;
        float w = getWidth(), h = getHeight();
        float density = getResources().getDisplayMetrics().density;
        float hit = HIT_RADIUS_DP * density;
        float best = Float.MAX_VALUE;
        WishStore.Wish target = null;
        if (wishes != null) {
            for (WishStore.Wish item : wishes) {
                float dx = item.x * w - event.getX();
                float dy = item.y * h - event.getY();
                float d = dx * dx + dy * dy;
                if (d < best) {
                    best = d;
                    target = item;
                }
            }
        }
        if (target != null && best <= hit * hit && listener != null) {
            listener.onWishTapped(target);
        } else if (listener != null) {
            // 太靠边不让挂星，否则星星会被系统栏和底栏挡住
            float xr = Math.max(0.06f, Math.min(0.94f, event.getX() / w));
            float yr = Math.max(0.10f, Math.min(0.86f, event.getY() / h));
            listener.onBlankTapped(xr, yr);
        }
        return true;
    }
}
