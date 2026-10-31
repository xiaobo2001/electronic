package com.xiaobo.electronic;

import android.content.ContentValues;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 【表白优化#13】表白纪念卡片：生成竖版纪念图（日期 + 告白台词 + 当晚月相与星座），
 * 保存到系统相册（Pictures/RomanticFirework），可微信直发。
 */
public class MementoCard {

    /** 生成并保存纪念卡片，返回保存路径（失败返回 null）。
     *  【#23】herName/myName：专属落款（“给她”+落款人），有名字才有落款行 */
    public static String generate(Context ctx, List<String> lines, String zodiacName, float moonAge,
                                   String herName, String myName) {
        try {
            int w = 1080, h = 1350;
            Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(bmp);
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

            // 深紫夜空渐变背景
            p.setShader(new LinearGradient(0, 0, 0, h,
                    new int[]{0xFF1A0B2E, 0xFF0E0620, 0xFF050310},
                    null, Shader.TileMode.CLAMP));
            c.drawRect(0, 0, w, h, p);
            p.setShader(null);

            // 散点星空
            java.util.Random rnd = new java.util.Random(42);
            p.setColor(0xFFFFFFFF);
            for (int i = 0; i < 90; i++) {
                p.setAlpha(40 + rnd.nextInt(160));
                c.drawCircle(rnd.nextFloat() * w, rnd.nextFloat() * h, rnd.nextFloat() * 2.2f + 0.6f, p);
            }

            // 顶部月亮（按真实月相亮面）
            p.setAlpha(26);
            c.drawCircle(w * 0.82f, h * 0.16f, 60f, p);
            p.setAlpha(255);
            float frac = (1f - (float) Math.cos(2 * Math.PI * moonAge / 29.530588853f)) / 2f;
            p.setColor(0xFFF5EFCE);
            c.drawCircle(w * 0.82f, h * 0.16f, 46f, p);
            if (frac < 0.98f) {
                float off = (1f - frac) * 92f;
                boolean waxing = moonAge < 14.765f;
                p.setColor(0xFF1A0B2E);
                c.drawCircle(w * 0.82f + (waxing ? -off : off), h * 0.16f, 46f, p);
            }

            // 日期
            p.setColor(0xFFFFD700);
            p.setAlpha(220);
            p.setTextSize(52f);
            p.setTextAlign(Paint.Align.CENTER);
            p.setTypeface(Typeface.DEFAULT_BOLD);
            c.drawText(new SimpleDateFormat("yyyy.MM.dd", Locale.CHINA).format(new Date()),
                    w / 2f, h * 0.26f, p);

            // 分隔心
            drawHeart(c, w / 2f, h * 0.30f, 26f, 0xFFFF69B4, p);

            // 告白台词（多行，竖排居中）
            // 【修复】此前写死最多 6 行、且每行硬截 14 字，而台词上限是 10 行，
            // 结果第 7~10 行被静默丢弃、长句被砍掉尾巴 —— 卡片上的告白是不完整的。
            // 改为按行数自适应行距与字号：行多就压缩行距，长句就整体缩小字号，
            // 宁可字小也不丢字，保证全部台词完整落在卡片内。
            java.util.List<String> poem = (lines == null)
                    ? java.util.Collections.<String>emptyList() : lines;
            int n = Math.min(poem.size(), RomanticShareCodec.MAX_LINES);
            if (n > 0) {
                float topY = h * 0.42f;
                float bottomLimit = h * 0.80f; // 底部要留给星座行、专属落款
                // n<=6 时维持原本 96px 行距（观感不变），超过 6 行才按比例压缩
                float lineH = Math.min(96f, (bottomLimit - topY) / Math.max(1, n - 1));

                p.setColor(0xFFFFFFFF);
                p.setTypeface(Typeface.DEFAULT_BOLD);
                float textSize = Math.min(64f, lineH * 0.72f);
                p.setTextSize(textSize);

                // 按最长的一行适配宽度：超出可用宽度就整体等比缩小字号，而不是截断文字
                float maxW = w * 0.86f;
                float widest = 0f;
                for (int i = 0; i < n; i++) {
                    String s = poem.get(i);
                    if (s == null || s.isEmpty()) continue;
                    widest = Math.max(widest, p.measureText(s));
                }
                if (widest > maxW && widest > 0f) {
                    textSize *= maxW / widest;
                    p.setTextSize(textSize);
                }

                float textY = topY;
                for (int i = 0; i < n; i++) {
                    String line = poem.get(i);
                    if (line == null || line.isEmpty()) continue;
                    p.setAlpha(245);
                    c.drawText(line, w / 2f, textY, p);
                    textY += lineH;
                }
            }

            // 星座与月相落款
            p.setTextSize(40f);
            p.setAlpha(200);
            p.setTypeface(Typeface.DEFAULT);
            c.drawText(zodiacName + "座之夜 · " + moonPhaseName(moonAge), w / 2f, h * 0.84f, p);
            p.setTextSize(30f);
            p.setAlpha(140);

            // 【手写签名】有亲笔签名时顶掉"遇见你真好"这行——
            // 签名本身就是落款，两者同框既挤又重复。没有签名则维持原样。
            Bitmap sign = SignatureStore.load(ctx);
            if (sign != null && sign.getWidth() > 0 && sign.getHeight() > 0) {
                float targetH = 82f;
                float sx = targetH / sign.getHeight();
                float dw = sign.getWidth() * sx;
                float maxW = w * 0.46f;
                if (dw > maxW) {
                    sx *= maxW / dw;
                    dw = maxW;
                    targetH = sign.getHeight() * sx;
                }
                Paint sp = new Paint(Paint.ANTI_ALIAS_FLAG);
                sp.setAlpha(225);
                // 白色描边染成金色：与日期、落款的金色调一致，也更像"签在卡片上的字"
                sp.setColorFilter(new android.graphics.PorterDuffColorFilter(
                        0xFFFFD700, android.graphics.PorterDuff.Mode.SRC_ATOP));
                c.drawBitmap(sign, null,
                        new android.graphics.RectF(w / 2f - dw / 2f, h * 0.895f - targetH / 2f,
                                w / 2f + dw / 2f, h * 0.895f + targetH / 2f), sp);
            } else {
                c.drawText("—— 遇见你真好 ——", w / 2f, h * 0.90f, p);
            }

            // 【#23】专属落款：给她 + 你的名字（有名字才有落款行）
            if (herName != null && !herName.isEmpty()) {
                p.setColor(0xFFFFD700);
                p.setAlpha(235);
                p.setTextSize(46f);
                p.setTypeface(Typeface.DEFAULT_BOLD);
                c.drawText("给 " + herName, w / 2f, h * 0.945f, p);
            }
            if (myName != null && !myName.isEmpty()) {
                p.setColor(0xFFFFFFFF);
                p.setAlpha(150);
                p.setTextSize(30f);
                p.setTypeface(Typeface.DEFAULT);
                c.drawText("—— " + myName + " ——", w / 2f, h * 0.975f, p);
            }

            return save(ctx, bmp);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private static void drawHeart(Canvas c, float cx, float cy, float size, int color, Paint p) {
        Paint hp = new Paint(Paint.ANTI_ALIAS_FLAG);
        hp.setColor(color);
        android.graphics.Path path = new android.graphics.Path();
        path.moveTo(cx, cy + size);
        for (double t = 0; t <= Math.PI * 2 + 0.05; t += 0.05) {
            float x = (float) (16 * Math.pow(Math.sin(t), 3)) / 16f * size;
            float y = (float) -(13 * Math.cos(t) - 5 * Math.cos(2 * t) - 2 * Math.cos(3 * t) - Math.cos(4 * t)) / 16f * size;
            if (t == 0) path.moveTo(cx + x, cy + y); else path.lineTo(cx + x, cy + y);
        }
        path.close();
        c.drawPath(path, hp);
    }

    private static String moonPhaseName(float age) {
        if (age < 1.8f || age > 27.7f) return "新月";
        if (age < 6.4f) return "娥眉月";
        if (age < 8.4f) return "上弦月";
        if (age < 13.5f) return "盈凸月";
        if (age < 16.0f) return "满月";
        if (age < 21.2f) return "亏凸月";
        if (age < 23.2f) return "下弦月";
        return "残月";
    }

    private static String save(Context ctx, Bitmap bmp) {
        try {
            String name = "card_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date()) + ".png";
            ContentValues cv = new ContentValues();
            cv.put(MediaStore.Images.Media.DISPLAY_NAME, name);
            cv.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
            cv.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/RomanticFirework");
            Uri uri = ctx.getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cv);
            if (uri == null) return null;
            java.io.OutputStream os = ctx.getContentResolver().openOutputStream(uri);
            bmp.compress(Bitmap.CompressFormat.PNG, 100, os);
            if (os != null) os.close();
            return Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                    + "/RomanticFirework/" + name;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}
