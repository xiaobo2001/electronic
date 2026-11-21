package com.xiaobo.electronic.util;

import android.content.ContentValues;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.EnumMap;
import java.util.Map;

/**
 * 二维码工具：把分享码画成二维码，对方扫一下就能拿到完整台词。
 *
 * <p>只做"生成"不做"识别"：识别要拉起相机、申请权限、处理预览帧，
 * 对一次表白来说成本远超收益。而扫码这个动作，微信/系统相机都能做，
 * 扫出来的文本再粘进"导入分享码"即可——功能闭环，零权限。
 *
 * <p>纠错级别用 H（30%）：分享码里全是 Base64 与中文转义，一旦被手指挡住一角就扫不出来，
 * 宁可码密一点也要扫得稳。
 */
public final class QrCodeUtil {

    private QrCodeUtil() {
    }

    /** 生成二维码位图（白底黑码，四周留 quiet zone）。 */
    public static Bitmap encode(String content, int sizePx) {
        if (content == null || content.isEmpty()) return null;
        try {
            Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
            hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
            hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.H);
            hints.put(EncodeHintType.MARGIN, 1);
            BitMatrix m = new QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx, hints);
            int w = m.getWidth(), h = m.getHeight();
            int[] pixels = new int[w * h];
            for (int y = 0; y < h; y++) {
                int offset = y * w;
                for (int x = 0; x < w; x++) {
                    pixels[offset + x] = m.get(x, y) ? Color.BLACK : Color.WHITE;
                }
            }
            Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            bmp.setPixels(pixels, 0, w, 0, 0, w, h);
            return bmp;
        } catch (WriterException | IllegalArgumentException e) {
            return null;
        }
    }

    /** 存到相册（Pictures/烟花），返回可直接分享的 Uri；失败返回 null。 */
    public static Uri saveToGallery(Context ctx, Bitmap bmp, String title) {
        if (bmp == null) return null;
        String name = (title == null || title.trim().isEmpty() ? "firework_qr" : title.trim())
                .replaceAll("[\\\\/:*?\"<>|]", "_") + "_" + System.currentTimeMillis() + ".png";
        try {
            // Android 10+：分区存储，经 MediaStore 写公共相册
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContentValues cv = new ContentValues();
                cv.put(MediaStore.Images.Media.DISPLAY_NAME, name);
                cv.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
                cv.put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/烟花");
                cv.put(MediaStore.Images.Media.IS_PENDING, 1);
                Uri uri = ctx.getContentResolver()
                        .insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cv);
                if (uri == null) return null;
                try (OutputStream os = ctx.getContentResolver().openOutputStream(uri)) {
                    if (os == null) return null;
                    if (!bmp.compress(Bitmap.CompressFormat.PNG, 100, os)) return null;
                }
                ContentValues done = new ContentValues();
                done.put(MediaStore.Images.Media.IS_PENDING, 0);
                ctx.getContentResolver().update(uri, done, null, null);
                return uri;
            }

            // Android 9 及以下：没有 WRITE_EXTERNAL_STORAGE（本应用不申请），
            // 直接 insert 公共相册会抛 SecurityException；改写应用私有图片目录，
            // 再用 FileProvider 暴露成可分享的 content Uri。
            File dir = new File(ctx.getExternalFilesDir(Environment.DIRECTORY_PICTURES), "烟花");
            if (!dir.exists() && !dir.mkdirs()) return null;
            File out = new File(dir, name);
            try (OutputStream os = new FileOutputStream(out)) {
                if (!bmp.compress(Bitmap.CompressFormat.PNG, 100, os)) return null;
            }
            return androidx.core.content.FileProvider.getUriForFile(
                    ctx, ctx.getPackageName() + ".fileprovider", out);
        } catch (Exception e) {
            return null;
        }
    }
}
