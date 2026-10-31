package com.xiaobo.electronic;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import java.io.File;
import java.io.FileOutputStream;

/**
 * 手写签名的本地留存。
 *
 * <p>签一次名不容易，写完只炸一次就丢太可惜：存下来之后，
 * 以后每一场演出、每一张纪念卡片都能带上这枚亲手写的名字。
 * 存 PNG 到私有目录，不申请任何存储权限。
 */
public final class SignatureStore {

    private static final String NAME = "signature.png";

    private SignatureStore() {
    }

    public static File file(Context ctx) {
        return new File(ctx.getFilesDir(), NAME);
    }

    public static boolean save(Context ctx, Bitmap bmp) {
        if (bmp == null) return false;
        try (FileOutputStream os = new FileOutputStream(file(ctx))) {
            return bmp.compress(Bitmap.CompressFormat.PNG, 100, os);
        } catch (Exception e) {
            return false;
        }
    }

    /** 读回签名；不存在或已损坏返回 null。 */
    public static Bitmap load(Context ctx) {
        File f = file(ctx);
        if (!f.exists()) return null;
        try {
            Bitmap b = BitmapFactory.decodeFile(f.getAbsolutePath());
            return (b == null || b.getWidth() <= 0) ? null : b;
        } catch (Exception e) {
            return null;
        }
    }

    public static boolean has(Context ctx) {
        return file(ctx).exists();
    }

    public static void clear(Context ctx) {
        File f = file(ctx);
        if (f.exists()) f.delete();
    }
}
