package com.xiaobo.electronic.media;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.net.Uri;

import com.xiaobo.electronic.util.FontKit;
import com.xiaobo.electronic.util.QrCodeUtil;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * 情书长图：把几段告白文字排成一张唯美信纸，存进相册可直接发。
 *
 * <p><b>为什么要自己画而不是截图。</b>长图是竖版信纸，高度随文字量变，
 * 用 View 截图在低端机上容易 OOM；这里直接按段落算好高度再用 Canvas 画，
 * 内存可控。字体走 {@link FontKit}（内置毛笔体/圆体），中文用 breakText 自动折行。
 *
 * <p><b>配色</b>沿用纪念卡片那套：深紫夜空渐变 + 金色字 + 粉色心，
 * 两处视觉是一家，用户不会觉得突然换了风格。
 *
 * <p>纯本地绘制与保存，不联网。
 */
public final class LoveLetterCard {

    private static final int W = 1080;
    /** 单张 bitmap 高度上限，超过就缩字号，防止 OOM */
    private static final int MAX_H = 6000;
    private static final int PAD = 96;

    private LoveLetterCard() {
    }

    /**
     * 生成并保存到相册。
     *
     * @param paragraphs 正文段落（已分好段，段与段之间留白）
     * @param herName    收信人（落款"给 XX"）
     * @param myName     署名
     * @param fontIndex  {@link FontKit} 的字体索引
     * @return 相册 Uri，失败返回 null
     */
    public static Uri generateAndSave(Context ctx, List<String> paragraphs,
                                      String herName, String myName, int fontIndex) {
        Bitmap bmp = render(ctx, paragraphs, herName, myName, fontIndex);
        if (bmp == null) return null;
        try {
            String title = "情书_给" + (herName == null || herName.trim().isEmpty()
                    ? "亲爱的" : herName.trim());
            return QrCodeUtil.saveToGallery(ctx, bmp, title);
        } finally {
            bmp.recycle();
        }
    }

    /** 渲染成 bitmap（不保存），方便预览 */
    public static Bitmap render(Context ctx, List<String> paragraphs,
                                String herName, String myName, int fontIndex) {
        List<String> para = clean(paragraphs);
        if (para.isEmpty()) return null;

        Typeface tf = FontKit.typeface(ctx, fontIndex);

        // 字号从大到小试，直到总高落进上限——长信也不炸内存
        int fontSize;
        List<List<String>> wrapped;
        int h;
        for (fontSize = 46; fontSize >= 26; fontSize -= 2) {
            Paint probe = new Paint(Paint.ANTI_ALIAS_FLAG);
            probe.setTypeface(tf);
            probe.setTextSize(fontSize);
            wrapped = wrapAll(probe, para, W - PAD * 2);
            h = measureHeight(wrapped, fontSize, tf);
            if (h <= MAX_H) return draw(ctx, wrapped, herName, myName, tf, fontSize, h);
        }
        // 极端长文本：用最小字号再画一次（高度可能超上限，但至少不返回 null）
        Paint probe = new Paint(Paint.ANTI_ALIAS_FLAG);
        probe.setTypeface(tf);
        probe.setTextSize(26);
        wrapped = wrapAll(probe, para, W - PAD * 2);
        h = measureHeight(wrapped, 26, tf);
        return draw(ctx, wrapped, herName, myName, tf, 26, h);
    }

    // ==================== 文字处理 ====================

    private static List<String> clean(List<String> paragraphs) {
        List<String> out = new ArrayList<>();
        if (paragraphs == null) return out;
        for (String s : paragraphs) {
            if (s == null) continue;
            String t = s.trim();
            if (!t.isEmpty()) out.add(t);
        }
        return out;
    }

    /** 每段按最大宽度折成若干行；空行保留为段落间距占位 */
    private static List<List<String>> wrapAll(Paint p, List<String> paragraphs, float maxWidth) {
        List<List<String>> all = new ArrayList<>();
        for (String para : paragraphs) {
            List<String> lines = new ArrayList<>();
            for (String seg : para.split("\n")) {
                String rest = seg.trim();
                if (rest.isEmpty()) continue;
                while (!rest.isEmpty()) {
                    int n = p.breakText(rest, true, maxWidth, null);
                    if (n <= 0) {
                        // 一个字都放不下（极端大字号），至少吞一个字符防死循环
                        lines.add(rest.substring(0, 1));
                        rest = rest.substring(1);
                    } else {
                        lines.add(rest.substring(0, n));
                        rest = rest.substring(n);
                    }
                }
            }
            if (!lines.isEmpty()) all.add(lines);
        }
        return all;
    }

    private static int measureHeight(List<List<String>> wrapped, int fontSize, Typeface tf) {
        Paint.FontMetrics fm;
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setTypeface(tf);
        p.setTextSize(fontSize);
        fm = p.getFontMetrics();
        int lineH = Math.round((fm.descent - fm.ascent) * 1.5f);
        int totalLines = 0;
        for (List<String> seg : wrapped) totalLines += seg.size();
        // 顶部标题区 + 正文 + 段落间距 + 落款区 + 底部留白
        return 420 + totalLines * lineH + wrapped.size() * 56 + 420;
    }

    // ==================== 绘制 ====================

    private static Bitmap draw(Context ctx, List<List<String>> wrapped, String herName,
                               String myName, Typeface tf, int fontSize, int h) {
        Bitmap bmp = Bitmap.createBitmap(W, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        // 夜空渐变背景
        LinearGradient bg = new LinearGradient(0, 0, 0, h,
                new int[]{0xFF1A0B2E, 0xFF0E0620, 0xFF050310}, null, Shader.TileMode.CLAMP);
        p.setShader(bg);
        c.drawRect(0, 0, W, h, p);
        p.setShader(null);

        // 固定种子星空：每次生成都是同一片天，不会每次重排
        Random rnd = new Random(20261121L);
        p.setStyle(Paint.Style.FILL);
        for (int i = 0; i < 110; i++) {
            p.setColor(0xFFFFFFFF);
            p.setAlpha(40 + rnd.nextInt(170));
            float x = rnd.nextFloat() * W;
            float y = rnd.nextFloat() * h;
            c.drawCircle(x, y, 0.6f + rnd.nextFloat() * 2.4f, p);
        }
        p.setAlpha(255);

        // 右上角的月亮
        drawMoon(c, W - 190, 210, 74, p);

        // 标题
        p.setColor(0xFFFFD700);
        p.setTextAlign(Paint.Align.CENTER);
        p.setTypeface(tf);
        p.setTextSize(78);
        c.drawText("情 书", W / 2f, 250, p);

        String date = new SimpleDateFormat("yyyy 年 M 月 d 日", Locale.CHINA).format(new Date());
        p.setTextSize(34);
        p.setAlpha(210);
        c.drawText(date, W / 2f, 320, p);
        p.setAlpha(255);

        // 标题下的粉色心
        drawHeart(c, W / 2f, 388, 26, 0xFFFF69B4, p);

        // 正文
        p.setTypeface(tf);
        p.setTextSize(fontSize);
        p.setTextAlign(Paint.Align.CENTER);
        p.setColor(0xFFFFFFFF);
        Paint.FontMetrics fm = p.getFontMetrics();
        float lineH = (fm.descent - fm.ascent) * 1.5f;
        float y = 470 - fm.ascent;
        for (List<String> seg : wrapped) {
            for (String line : seg) {
                c.drawText(line, W / 2f, y, p);
                y += lineH;
            }
            y += 56;   // 段落间距
        }

        // 落款区：给 XX / —— XX ——
        p.setColor(0xFFFFD700);
        p.setTextSize(50);
        String to = "给 " + (herName == null || herName.trim().isEmpty() ? "亲爱的" : herName.trim());
        c.drawText(to, W / 2f, y + 40, p);

        p.setColor(0xFFFFFFFF);
        p.setAlpha(170);
        p.setTextSize(36);
        String from = "—— " + (myName == null || myName.trim().isEmpty() ? "爱你的我" : myName.trim()) + " ——";
        c.drawText(from, W / 2f, y + 110, p);
        p.setAlpha(255);

        return bmp;
    }

    /** 画一个满月：浅黄圆盘 + 几块淡淡月海，不依赖真实月相 */
    private static void drawMoon(Canvas c, float cx, float cy, float r, Paint p) {
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFFF5EFCE);
        p.setAlpha(230);
        c.drawCircle(cx, cy, r, p);
        p.setColor(0xFFE4D9A8);
        p.setAlpha(60);
        c.drawCircle(cx - r * 0.3f, cy - r * 0.2f, r * 0.28f, p);
        c.drawCircle(cx + r * 0.32f, cy + r * 0.25f, r * 0.2f, p);
        c.drawCircle(cx + r * 0.05f, cy - r * 0.45f, r * 0.14f, p);
        p.setAlpha(255);
    }

    private static void drawHeart(Canvas c, float cx, float cy, float size, int color, Paint p) {
        Paint hp = new Paint(Paint.ANTI_ALIAS_FLAG);
        hp.setColor(color);
        Path path = new Path();
        for (double t = 0; t <= Math.PI * 2 + 0.05; t += 0.05) {
            float x = (float) (16 * Math.pow(Math.sin(t), 3)) / 16f * size;
            float y = (float) -(13 * Math.cos(t) - 5 * Math.cos(2 * t)
                    - 2 * Math.cos(3 * t) - Math.cos(4 * t)) / 16f * size;
            if (t == 0) path.moveTo(cx + x, cy + y); else path.lineTo(cx + x, cy + y);
        }
        path.close();
        c.drawPath(path, hp);
    }
}
