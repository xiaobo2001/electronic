package com.xiaobo.electronic;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;

import com.tencent.mmkv.MMKV;

/**
 * 文字烟花的字体。
 *
 * <p>库的 TEXT 形状用自己的画笔栅格化，字体写死，没法换。
 * 所以非默认字体走另一条路：这里先把文字画成位图，再用
 * {@code FireworkConfig.Shape.CUSTOM + shapeBitmap} 让库按位图布点——
 * 与手写签名烟花同一套机制，字体想换什么就换什么。
 *
 * <p>内置两款开源中文字体（见 assets/fonts/README.txt）：
 * 马善政毛笔楷书写"我喜欢你"和黑体写的，完全不是一回事；
 * 站酷快乐体圆润，适合生日、纪念日这类轻松的场合。
 */
public final class FontKit {

    public static final int DEFAULT = 0;
    public static final int BRUSH = 1;
    public static final int ROUND = 2;
    public static final int SERIF = 3;
    public static final int BOLD = 4;

    public static final String[] NAMES = {
            "黑体（默认）", "毛笔楷书 ✍", "圆体手写 🧸", "宋体（衬线）", "粗黑（醒目）"
    };

    private static final String KEY = "firework_font";
    private static final Typeface[] CACHE = new Typeface[NAMES.length];

    private FontKit() {
    }

    public static int current() {
        int v = MMKV.defaultMMKV().decodeInt(KEY, DEFAULT);
        return (v < 0 || v >= NAMES.length) ? DEFAULT : v;
    }

    public static void set(int index) {
        MMKV.defaultMMKV().encode(KEY, index);
    }

    /** 非默认字体必须自己栅格化（库不认外部字体）。 */
    public static boolean needsBitmap(int index) {
        return index != DEFAULT;
    }

    /** 取字体（asset 字体懒加载并缓存，避免每次放烟花都解一遍 5MB 的 TTF）。 */
    public static Typeface typeface(Context ctx, int index) {
        if (index < 0 || index >= NAMES.length) index = DEFAULT;
        Typeface cached = CACHE[index];
        if (cached != null) return cached;
        Typeface tf = null;
        try {
            switch (index) {
                case BRUSH:
                    tf = Typeface.createFromAsset(ctx.getAssets(), "fonts/mashanzheng.ttf");
                    break;
                case ROUND:
                    tf = Typeface.createFromAsset(ctx.getAssets(), "fonts/zcoolkuaile.ttf");
                    break;
                case SERIF:
                    tf = Typeface.create(Typeface.SERIF, Typeface.NORMAL);
                    break;
                case BOLD:
                    tf = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD);
                    break;
                default:
                    tf = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL);
                    break;
            }
        } catch (Exception e) {
            // 字体文件缺失/损坏时退回系统黑体，绝不让"换个字体"把烟花搞没了
            tf = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL);
        }
        CACHE[index] = tf;
        return tf;
    }

    /**
     * 把文字渲染成烟花可用的形状位图（透明底 + 描边发光）。
     *
     * @param text    支持 \n 多行
     * @param maxPx   位图最大边长，超出就整体缩字号（宁可字小也不截断）
     */
    public static Bitmap render(Context ctx, String text, int index, int maxPx) {
        if (text == null || text.trim().isEmpty()) return null;
        String[] lines = text.split("\n");
        Typeface tf = typeface(ctx, index);

        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setTypeface(tf);
        p.setTextAlign(Paint.Align.LEFT);
        float size = 160f;
        Rect bounds = new Rect();

        // 字号自适应：以 160px 起测，超过画布就等比缩
        float widest = 0f;
        for (String s : lines) {
            p.setTextSize(size);
            p.getTextBounds(s, 0, s.length(), bounds);
            widest = Math.max(widest, bounds.width());
        }
        if (widest > maxPx * 0.9f) size *= (maxPx * 0.9f) / widest;
        if (size < 24f) size = 24f;

        p.setTextSize(size);
        Paint.FontMetrics fm = p.getFontMetrics();
        float lineH = (fm.descent - fm.ascent) * 1.25f;
        int w = (int) Math.min(maxPx, widest + size * 0.3f);
        int h = (int) Math.min(maxPx, lineH * lines.length + size * 0.4f);
        if (w <= 0 || h <= 0) return null;

        Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        // 先画一层光晕：夜空里纯色字会被烟花吃掉，有光晕才立得住
        p.setShadowLayer(size * 0.25f, 0f, 0f, 0xFFFFFFFF);
        p.setColor(0xFFFFFFFF);
        p.setStyle(Paint.Style.FILL);
        float y = -fm.ascent + size * 0.2f;
        for (String s : lines) {
            float lw = p.measureText(s);
            c.drawText(s, (w - lw) / 2f, y, p);
            y += lineH;
        }
        return bmp;
    }

    /** 位图转烟花配置（与签名烟花一致：CUSTOM 采样）。 */
    public static com.xiaobo.fireworks.FireworkConfig config(Bitmap bmp, float textSizeHint) {
        // 与签名同理：自定义位图的粒子数由库的采样步长决定，撑太散笔画就断了
        return new com.xiaobo.fireworks.FireworkConfig.Builder()
                .shape(com.xiaobo.fireworks.FireworkConfig.Shape.CUSTOM)
                .shapeBitmap(bmp)
                .colors(0xFFFFD700, Color.RED, 0xFFFF69B4)
                .count(240)
                .range(1.15f)
                .trail(1.0f)
                .duration(3.0f)
                .sparkle(true)
                .build();
    }
}
