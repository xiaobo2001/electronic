package com.xiaobo.electronic.view;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;

import androidx.core.graphics.drawable.RoundedBitmapDrawable;
import androidx.core.graphics.drawable.RoundedBitmapDrawableFactory;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 照片回忆墙：把相册里的照片放进这场烟花。
 *
 * <p>两种形态：
 * <ul>
 *   <li><b>飘落</b>：照片像花瓣一样从夜空落下，适合演出中当背景情绪；</li>
 *   <li><b>拼心形</b>：照片沿心形轮廓聚拢成型，适合收尾时"这些是我们"的一击。</li>
 * </ul>
 *
 * <p>两个必须守住的点：
 * <ul>
 *   <li><b>内存</b>：相册原图动辄 4000×3000，直接解码十几张必 OOM。
 *       这里按目标显示尺寸算 inSampleSize 采样，单张控制在 ~120dp。</li>
 *   <li><b>收尾</b>：所有 View 与 Runnable 都登记在案，stop() / 退出时一次性清干净，
 *       否则 Activity 销毁后 handler 还在往已分离的容器里加 View。</li>
 * </ul>
 */
public final class PhotoWall {

    private final Context ctx;
    private final FrameLayout container;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<ImageView> active = new ArrayList<>();
    private final Random random = new Random();
    private boolean running = false;

    public PhotoWall(Context ctx, FrameLayout container) {
        this.ctx = ctx.getApplicationContext();
        this.container = container;
    }

    public boolean isRunning() {
        return running;
    }

    /** 照片飘落：每隔 intervalMs 落下一张，持续 durationMs 后自动收场。 */
    public void showFalling(List<Uri> uris, int durationMs, int intervalMs) {
        if (uris == null || uris.isEmpty()) return;
        stop();
        running = true;

        long[] until = {System.currentTimeMillis() + durationMs};
        Runnable[] task = new Runnable[1];
        int[] idx = {0};
        task[0] = () -> {
            if (!running) return;
            if (System.currentTimeMillis() > until[0]) {
                stop();
                return;
            }
            Uri uri = uris.get(idx[0] % uris.size());
            idx[0]++;
            dropOne(uri);
            handler.postDelayed(task[0], intervalMs);
        };
        handler.post(task[0]);
    }

    private void dropOne(Uri uri) {
        Bitmap bmp = decode(uri, 240);
        if (bmp == null) return;
        // 尺寸必须在落下这一刻才取：首次进入时容器可能还没布局完，
        // 提前缓存宽高会得到 0，照片就"从原地落回原地"不动了
        int w = container.getWidth();
        int h = container.getHeight();
        if (h <= 0) h = ctx.getResources().getDisplayMetrics().heightPixels;
        if (w <= 0) w = ctx.getResources().getDisplayMetrics().widthPixels;
        int size = (int) (72 + random.nextFloat() * 56); // dp
        int px = (int) (size * ctx.getResources().getDisplayMetrics().density);
        ImageView iv = new ImageView(ctx);
        iv.setImageDrawable(circle(bmp));
        iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
        iv.setAlpha(0f);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(px, px);
        iv.setLayoutParams(lp);

        float startX = random.nextFloat() * Math.max(1, w - px);
        iv.setTranslationX(startX);
        iv.setTranslationY(-px * 1.5f);
        iv.setRotation(-18f + random.nextFloat() * 36f);
        add(iv);

        iv.animate()
                .alpha(0.92f)
                .setDuration(700)
                .start();
        iv.animate()
                .translationY(h + px * 1.5f)
                .rotationBy(-20f + random.nextFloat() * 40f)
                .setDuration(6500 + random.nextInt(3000))
                .setStartDelay(0)
                .withEndAction(() -> remove(iv))
                .start();
    }

    /** 拼成心形：照片沿心形轮廓依次弹入，停留 holdMs 后淡出。 */
    public void showHeart(List<Uri> uris, int holdMs) {
        if (uris == null || uris.isEmpty()) return;
        stop();
        running = true;

        int w = container.getWidth();
        int h = container.getHeight();
        int n = Math.min(uris.size(), 26);
        float cx = w / 2f;
        float cy = h * 0.48f;
        float scale = Math.min(w, h) * 0.42f;
        float density = ctx.getResources().getDisplayMetrics().density;

        for (int i = 0; i < n; i++) {
            Bitmap bmp = decode(uris.get(i % uris.size()), 200);
            if (bmp == null) continue;
            // 心形参数方程（同纪念卡片画心用的那条曲线），t 均匀分布取整圈
            double t = Math.PI * 2.0 * i / n;
            double hx = 16 * Math.pow(Math.sin(t), 3);
            double hy = 13 * Math.cos(t) - 5 * Math.cos(2 * t) - 2 * Math.cos(3 * t) - Math.cos(4 * t);
            float px = cx + (float) (hx / 16.0) * scale;
            float py = cy - (float) (hy / 16.0) * scale;

            int sizePx = (int) ((64 + random.nextFloat() * 26) * density);
            ImageView iv = new ImageView(ctx);
            iv.setImageDrawable(circle(bmp));
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            iv.setLayoutParams(new FrameLayout.LayoutParams(sizePx, sizePx));
            iv.setTranslationX(px - sizePx / 2f);
            iv.setTranslationY(py - sizePx / 2f);
            iv.setScaleX(0f);
            iv.setScaleY(0f);
            iv.setAlpha(0f);
            add(iv);

            iv.animate()
                    .scaleX(1f).scaleY(1f).alpha(0.95f)
                    .setStartDelay(i * 90L)
                    .setDuration(520)
                    .setInterpolator(new OvershootInterpolator(1.4f))
                    .start();
        }

        handler.postDelayed(() -> {
            if (!running) return;
            for (ImageView iv : new ArrayList<>(active)) {
                iv.animate().alpha(0f).scaleX(0.6f).scaleY(0.6f).setDuration(600)
                        .withEndAction(() -> remove(iv)).start();
            }
            handler.postDelayed(this::stop, 900);
        }, holdMs);
    }

    /** 收场：清掉所有照片与排队任务。 */
    public void stop() {
        running = false;
        handler.removeCallbacksAndMessages(null);
        for (ImageView iv : new ArrayList<>(active)) {
            iv.animate().cancel();
            if (container != null) container.removeView(iv);
            recycle(iv);
        }
        active.clear();
    }

    private void add(ImageView iv) {
        if (container == null) return;
        active.add(iv);
        container.addView(iv);
    }

    private void remove(ImageView iv) {
        if (container != null) container.removeView(iv);
        active.remove(iv);
        recycle(iv);
    }

    private static void recycle(ImageView iv) {
        Drawable d = iv.getDrawable();
        if (d instanceof RoundedBitmapDrawable) {
            Bitmap b = ((RoundedBitmapDrawable) d).getBitmap();
            if (b != null && !b.isRecycled()) b.recycle();
        }
    }

    /** 圆形照片：方图裁成圆，边缘更柔和，也不容易看出原图比例不一。 */
    private Drawable circle(Bitmap src) {
        int size = Math.min(src.getWidth(), src.getHeight());
        Bitmap sq = Bitmap.createBitmap(src,
                (src.getWidth() - size) / 2, (src.getHeight() - size) / 2, size, size);
        if (sq != src) src.recycle();
        RoundedBitmapDrawable d = RoundedBitmapDrawableFactory.create(ctx.getResources(), sq);
        d.setCircular(true);
        d.setGravity(android.view.Gravity.CENTER);
        return d;
    }

    /** 按目标像素尺寸采样解码，避免把整张原图读进内存。 */
    private Bitmap decode(Uri uri, int reqPx) {
        if (uri == null) return null;
        try {
            BitmapFactory.Options opt = new BitmapFactory.Options();
            opt.inJustDecodeBounds = true;
            try (InputStream in = ctx.getContentResolver().openInputStream(uri)) {
                if (in == null) return null;
                BitmapFactory.decodeStream(in, null, opt);
            }
            int w = opt.outWidth, h = opt.outHeight;
            if (w <= 0 || h <= 0) return null;
            int sample = 1;
            int max = Math.max(w, h);
            while (max / sample > reqPx) sample *= 2;
            opt.inJustDecodeBounds = false;
            opt.inSampleSize = sample;
            opt.inPreferredConfig = Bitmap.Config.RGB_565; // 照片不需要透明通道，省一半内存
            try (InputStream in = ctx.getContentResolver().openInputStream(uri)) {
                if (in == null) return null;
                return BitmapFactory.decodeStream(in, null, opt);
            }
        } catch (Exception e) {
            return null;
        }
    }
}
