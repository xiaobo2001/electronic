package com.xiaobo.electronic.media;

import android.content.ContentValues;
import android.content.Context;
import android.graphics.Bitmap;
import android.media.MediaMetadataRetriever;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import com.tencent.mmkv.MMKV;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import com.xiaobo.electronic.App;

/**
 * 【#20】视频转 GIF 动图：MP4 抽帧 + 固定调色板量化 + LZW 编码，纯 Java 实现零依赖。
 * 输出 320 宽、10fps、最长 6 秒的 GIF，可直接微信发送。
 */
public class GifExporter {

    /** 视频转 GIF，返回保存路径（失败返回 null） */
    public static String convert(Context ctx, String videoPath) {
            MediaMetadataRetriever retriever = null;
        try {
            retriever = new MediaMetadataRetriever();
            retriever.setDataSource(videoPath);
            String durStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            String wStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH);
            String hStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT);
            if (durStr == null || wStr == null || hStr == null) return null; // 【修复】元数据缺失时 parse null 会 NPE
            long durationMs = Long.parseLong(durStr);
            int srcW = Integer.parseInt(wStr);
            int srcH = Integer.parseInt(hStr);
            if (durationMs <= 0 || srcW <= 0) return null;

            // 采样：10fps，最长 6 秒
            int clipMs = (int) Math.min(durationMs, 6000);
            int frames = Math.min(60, clipMs / 100);
            if (frames < 3) return null;
            int gifW = 320;
            int gifH = Math.max(2, srcH * gifW / srcW);

            int[][] rgbFrames = new int[frames][];
            int got = 0;
            for (int i = 0; i < frames; i++) {
                long t = (long) ((double) clipMs * i / frames) * 1000; // μs
                Bitmap bmp = retriever.getFrameAtTime(t, MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
                // 部分机型/编码只能给出非关键帧，退一档再要一次，别让整段导出空手而归
                if (bmp == null) {
                    bmp = retriever.getFrameAtTime(t, MediaMetadataRetriever.OPTION_CLOSEST);
                }
                if (bmp == null) continue;
                Bitmap scaled = Bitmap.createScaledBitmap(bmp, gifW, gifH, true);
                if (scaled != bmp) bmp.recycle();
                int[] px = new int[gifW * gifH];
                scaled.getPixels(px, 0, gifW, 0, 0, gifW, gifH);
                scaled.recycle();
                rgbFrames[i] = px;
                got++;
            }
            if (got < 2) return null;   // 抽不出帧就别产出废文件

            String her = "";
            try {
                // 【#24】GIF 文件名同步带她的名字，与录屏 MP4 命名一致
                String n = MMKV.defaultMMKV().decodeString("romantic_her_name", "");
                if (n != null && !n.trim().isEmpty()) her = "_" + n.trim();
            } catch (Throwable ignored) {
            }
            String outName = "romantic" + her + "_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date()) + ".gif";

            // Android 10+ 分区存储：必须经 MediaStore 写入公共 Pictures/，
            // 直接 new File(getExternalStoragePublicDirectory(...)) 在 targetSdk 35 上会抛异常。
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContentValues cv = new ContentValues();
                cv.put(MediaStore.Images.Media.DISPLAY_NAME, outName);
                cv.put(MediaStore.Images.Media.MIME_TYPE, "image/gif");
                cv.put(MediaStore.Images.Media.RELATIVE_PATH,
                        Environment.DIRECTORY_PICTURES + "/RomanticFirework");
                cv.put(MediaStore.Images.Media.IS_PENDING, 1);
                Uri uri = ctx.getContentResolver()
                        .insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cv);
                if (uri == null) return null;
                OutputStream os = ctx.getContentResolver().openOutputStream(uri);
                if (os == null) return null;
                writeGif(os, rgbFrames, gifW, gifH, 100);
                ContentValues done = new ContentValues();
                done.put(MediaStore.Images.Media.IS_PENDING, 0);
                ctx.getContentResolver().update(uri, done, null, null);
                return Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                        + "/RomanticFirework/" + outName;
            }

            // Android 9 及以下：写入 App 私有目录（无需任何存储权限）
            File dir = new File(ctx.getExternalFilesDir(Environment.DIRECTORY_PICTURES),
                    "RomanticFirework");
            if (!dir.exists() && !dir.mkdirs()) return null;
            File out = new File(dir, outName);
            writeGif(new FileOutputStream(out), rgbFrames, gifW, gifH, 100);
            MediaScannerConnection.scanFile(ctx, new String[]{out.getAbsolutePath()},
                    new String[]{"image/gif"}, null);
            return out.getAbsolutePath();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        } finally {
            try {
                if (retriever != null) retriever.release();
            } catch (Exception ignored) {
            }
        }
    }

    // ==================== 调色板 ====================
    // 【修复崩溃】旧版按「6×7×6 + 32 灰阶」建表，实际 6×7×6=252，252+32=284 > 256，
    // 静态初始化时 p[256] 直接越界 → ExceptionInInitializerError（线程 gif-export 必崩）。
    // 且 nearest() 的灰阶只返回 252..255 四个槽，与建表的 32 个灰阶对不上，灰阶映射本来也是错的。
    // 现改为 6×6×6=216 色 + 32 级灰阶 + 8 个保留槽，总容量严格等于 256。

    /** 全局色表固定 256 色 */
    private static final int PALETTE_SIZE = 256;
    /** 彩色立方每通道级数：6×6×6 = 216（网络安全色网格） */
    private static final int LEVELS = 6;
    private static final int COLOR_COUNT = LEVELS * LEVELS * LEVELS;   // 216
    /** 灰阶：32 级，下标 216..247，剩下 8 个槽（248..255）填黑备用 */
    private static final int GRAY_COUNT = 32;
    private static final int GRAY_BASE = COLOR_COUNT;                  // 216

    /** 8×8 Bayer 有序抖动矩阵（0..63）：夜空渐变在 256 色下必然出色带，抖动可打散 */
    private static final int[] BAYER8 = {
            0, 32, 8, 40, 2, 34, 10, 42,
            48, 16, 56, 24, 50, 18, 58, 26,
            12, 44, 4, 36, 14, 46, 6, 38,
            60, 28, 52, 20, 62, 30, 54, 22,
            3, 35, 11, 43, 1, 33, 9, 41,
            51, 19, 59, 27, 49, 17, 57, 25,
            15, 47, 7, 39, 13, 45, 5, 37,
            63, 31, 55, 23, 61, 29, 53, 21,
    };

    private static final int[] PALETTE = buildPalette();

    private static int[] buildPalette() {
        int[] p = new int[PALETTE_SIZE];
        int idx = 0;
        for (int r = 0; r < LEVELS; r++) {
            for (int g = 0; g < LEVELS; g++) {
                for (int b = 0; b < LEVELS; b++) {
                    p[idx++] = 0xFF000000 | ((r * 255 / 5) << 16) | ((g * 255 / 5) << 8) | (b * 255 / 5);
                }
            }
        }
        for (int i = 0; i < GRAY_COUNT; i++) {
            int v = i * 255 / (GRAY_COUNT - 1);
            p[idx++] = 0xFF000000 | (v << 16) | (v << 8) | v;
        }
        while (idx < PALETTE_SIZE) p[idx++] = 0xFF000000;
        return p;
    }

    private static int clamp(int v) {
        return v < 0 ? 0 : (v > 255 ? 255 : v);
    }

    /** 取最近色索引；x/y 用于查抖动矩阵，同一像素位置每帧抖动图案一致，避免帧间闪烁 */
    private static int nearest(int argb, int x, int y) {
        int a = (argb >>> 24) & 0xFF;
        if (a < 32) return 0;
        int r = (argb >> 16) & 0xFF, g = (argb >> 8) & 0xFF, b = argb & 0xFF;

        if (r == g && g == b) { // 纯灰阶：走 32 级灰阶表，比彩色立方细腻得多
            int d = (BAYER8[(y & 7) << 3 | (x & 7)] - 32) * 3 / 4;
            int gi = (clamp(r + d) * (GRAY_COUNT - 1) + 128) / 255;
            if (gi > GRAY_COUNT - 1) gi = GRAY_COUNT - 1;
            return GRAY_BASE + gi;
        }

        int d = (BAYER8[(y & 7) << 3 | (x & 7)] - 32) * 3 / 4;   // 约 ±24 ≈ 半个量化步长
        int ri = (clamp(r + d) * (LEVELS - 1) + 128) / 255;
        int gi2 = (clamp(g + d) * (LEVELS - 1) + 128) / 255;
        int bi = (clamp(b + d) * (LEVELS - 1) + 128) / 255;
        if (ri > LEVELS - 1) ri = LEVELS - 1;
        if (gi2 > LEVELS - 1) gi2 = LEVELS - 1;
        if (bi > LEVELS - 1) bi = LEVELS - 1;
        return (ri * LEVELS + gi2) * LEVELS + bi;
    }

    /** GIF89a 写出 */
    private static void writeGif(OutputStream os, int[][] frames, int w, int h, int delayMs) throws Exception {
        try {
            os.write("GIF89a".getBytes("US-ASCII"));
            // Logical Screen Descriptor
            os.write(w & 0xFF); os.write((w >> 8) & 0xFF);
            os.write(h & 0xFF); os.write((h >> 8) & 0xFF);
            os.write(0xF7); // 全局调色板 256 色
            os.write(0);    // bg
            os.write(0);    // aspect
            // Global Color Table
            for (int c : PALETTE) {
                os.write((c >> 16) & 0xFF); os.write((c >> 8) & 0xFF); os.write(c & 0xFF);
            }
            // Netscape Loop Extension（循环）
            os.write(0x21); os.write(0xFF); os.write(11);
            os.write("NETSCAPE2.0".getBytes("US-ASCII"));
            os.write(3); os.write(1); os.write(0); os.write(0); os.write(0);

            byte[] pixels = new byte[w * h];
            int written = 0;
            for (int[] frame : frames) {
                if (frame == null) continue;
                for (int y = 0; y < h; y++) {
                    int row = y * w;
                    for (int x = 0; x < w; x++) {
                        pixels[row + x] = (byte) nearest(frame[row + x], x, y);
                    }
                }
                // Graphic Control Extension
                os.write(0x21); os.write(0xF9); os.write(4);
                os.write(4); // disposal=1
                os.write(delayMs / 10 & 0xFF); os.write((delayMs / 10 >> 8) & 0xFF);
                os.write(0xFF); os.write(0);
                // Image Descriptor
                os.write(0x2C);
                os.write(0); os.write(0); os.write(0); os.write(0);
                os.write(w & 0xFF); os.write((w >> 8) & 0xFF);
                os.write(h & 0xFF); os.write((h >> 8) & 0xFF);
                os.write(0);
                // LZW
                lzwEncode(os, pixels, 8);
                written++;
            }
            os.write(0x3B); // trailer
        } finally {
            try { os.close(); } catch (Exception ignored) { }
        }
    }

    /** 哈希表容量：字典最多 4096 项，取 2 倍以上的 2 的幂，开放寻址基本不冲突 */
    private static final int HASH_SIZE = 8192;
    private static final int HASH_MASK = HASH_SIZE - 1;

    /**
     * 标准 GIF89a LZW 编码（可变码长，含 Clear / EOI）。
     *
     * <p><b>性能重写</b>：旧版拿 {@code String} 拼接当字典键（HashMap + StringBuilder），
     * 一帧 320×568 ≈ 18 万像素、60 帧就是上千万次字符串拼接与哈希——慢到几十秒，还疯狂 GC。
     * 现改为 int 键 {@code (prefix << 8) | suffix} + 开放寻址哈希表，全程零对象分配。
     */
    private static void lzwEncode(OutputStream os, byte[] pixels, int minCodeSize) throws Exception {
        os.write(minCodeSize);
        final int clear = 1 << minCodeSize;      // 256
        final int eoi = clear + 1;               // 257
        BitWriter bits = new BitWriter(os);

        int codeSize = minCodeSize + 1;          // 初始 9 位
        int nextCode = eoi + 1;                  // 第一个可用码 258

        int[] hKey = new int[HASH_SIZE];
        int[] hVal = new int[HASH_SIZE];
        java.util.Arrays.fill(hKey, -1);

        bits.write(clear, codeSize);
        if (pixels.length == 0) {
            bits.write(eoi, codeSize);
            bits.flush();
            return;
        }

        int prefix = pixels[0] & 0xFF;
        for (int i = 1; i < pixels.length; i++) {
            int c = pixels[i] & 0xFF;
            int key = (prefix << 8) | c;
            int h = (key ^ (key >>> 8) ^ (key >>> 16)) & HASH_MASK;
            int hit = -1;
            while (hKey[h] >= 0) {
                if (hKey[h] == key) { hit = hVal[h]; break; }
                h = (h + 1) & HASH_MASK;
            }
            if (hit >= 0) {
                prefix = hit;                    // 序列仍可延长，继续吞下一个字节
                continue;
            }

            bits.write(prefix, codeSize);
            if (nextCode < 4096) {
                hKey[h] = key;
                hVal[h] = nextCode;
                // 先增码长再自增，保证与解码器同步（旧版同样语义，保持不变）
                if (nextCode >= (1 << codeSize) && codeSize < 12) codeSize++;
                nextCode++;
            } else {
                // 字典满：发 Clear 重置，符合标准 GIF 解码器语义
                bits.write(clear, codeSize);
                codeSize = minCodeSize + 1;
                nextCode = eoi + 1;
                java.util.Arrays.fill(hKey, -1);
            }
            prefix = c;
        }
        bits.write(prefix, codeSize);
        bits.write(eoi, codeSize);
        bits.flush();
    }

    private static class BitWriter {
        private final OutputStream os;
        private int bitBuffer = 0, bitCount = 0;
        private final byte[] block = new byte[255];
        private int blockLen = 0;

        BitWriter(OutputStream os) { this.os = os; }

        void write(int code, int size) throws Exception {
            bitBuffer |= code << bitCount;
            bitCount += size;
            while (bitCount >= 8) {
                block[blockLen++] = (byte) (bitBuffer & 0xFF);
                bitBuffer >>>= 8;
                bitCount -= 8;
                if (blockLen == 255) {
                    os.write(255); os.write(block, 0, 255); blockLen = 0;
                }
            }
        }

        void flush() throws Exception {
            if (bitCount > 0) {
                block[blockLen++] = (byte) (bitBuffer & 0xFF);
                bitBuffer = 0; bitCount = 0;
            }
            if (blockLen > 0) {
                os.write(blockLen); os.write(block, 0, blockLen); blockLen = 0;
            }
            os.write(0); // block terminator
        }
    }
}
