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
            for (int i = 0; i < frames; i++) {
                long t = (long) ((double) clipMs * i / frames) * 1000; // μs
                Bitmap bmp = retriever.getFrameAtTime(t, MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
                if (bmp == null) continue;
                Bitmap scaled = Bitmap.createScaledBitmap(bmp, gifW, gifH, true);
                if (scaled != bmp) bmp.recycle();
                int[] px = new int[gifW * gifH];
                scaled.getPixels(px, 0, gifW, 0, 0, gifW, gifH);
                scaled.recycle();
                rgbFrames[i] = px;
            }

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

    /** 固定 6x7x6 调色板量化（红0-5×绿0-6×蓝0-5 + 32 灰阶，共 244 色 + 12 余量） */
    private static final int[] PALETTE = buildPalette();
    private static final int PALETTE_SIZE = 256;

    private static int[] buildPalette() {
        int[] p = new int[PALETTE_SIZE];
        int idx = 0;
        for (int r = 0; r < 6; r++) {
            for (int g = 0; g < 7; g++) {
                for (int b = 0; b < 6; b++) {
                    p[idx++] = 0xFF000000 | ((r * 255 / 5) << 16) | ((g * 255 / 6) << 8) | (b * 255 / 5);
                }
            }
        }
        for (int i = 0; i < 32; i++) {
            int v = i * 255 / 31;
            p[idx++] = 0xFF000000 | (v << 16) | (v << 8) | v;
        }
        while (idx < PALETTE_SIZE) p[idx++] = 0xFF000000;
        return p;
    }

    private static int nearest(int argb) {
        int a = (argb >>> 24) & 0xFF;
        if (a < 32) return 0;
        int r = (argb >> 16) & 0xFF, g = (argb >> 8) & 0xFF, b = argb & 0xFF;
        if (r == g && g == b) { // 灰阶
            int gi = (r * 31 + 128) / 255;
            return 252 + Math.min(3, gi * 32 / 256);
        }
        int ri = (r * 5 + 128) / 255, gi2 = (g * 6 + 128) / 255, bi = (b * 5 + 128) / 255;
        return (ri * 7 + gi2) * 6 + bi;
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
                for (int i = 0; i < frame.length; i++) pixels[i] = (byte) nearest(frame[i]);
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

    /** 标准 GIF LZW（可变码长，含 Clear/EOI） */
    /** 标准 GIF89a LZW 编码（重写修复：字典未初始化导致首轮 NPE 崩溃） */
    private static void lzwEncode(OutputStream os, byte[] pixels, int minCodeSize) throws Exception {
        os.write(minCodeSize);
        int clear = 1 << minCodeSize;
        int eoi = clear + 1;
        BitWriter bits = new BitWriter(os);
        java.util.HashMap<String, Integer> dict = new java.util.HashMap<>();

        // 码长增长规则：初始 codeSize = minCodeSize+1 (9)
        int[] codeSize = {minCodeSize + 1};
        int[] nextCode = {eoi + 1};
        java.util.ArrayList<Runnable> noop = null; // (占位避免未使用告警)

        // 输出初始 Clear 码
        bits.write(clear, codeSize[0]);

        // cur：当前序列（字符串形式，每字节一个 char）。单字节序列的码 = 字节值本身（隐式，不入字典）
        StringBuilder cur = new StringBuilder();
        for (byte b : pixels) {
            char c = (char) (b & 0xFF);
            if (cur.length() == 0) {
                cur.append(c);
                continue;
            }
            String cand = cur.toString() + c;
            Integer hit = dict.get(cand);
            if (hit != null) {
                cur = new StringBuilder(cand);
                continue;
            }
            // 输出当前序列的码
            int outCode = (cur.length() == 1) ? (cur.charAt(0) & 0xFF) : dict.get(cur.toString());
            bits.write(outCode, codeSize[0]);
            // 新序列入字典（先增码长后存码，保证解码器同步）
            if (nextCode[0] >= (1 << codeSize[0]) && codeSize[0] < 12) {
                codeSize[0]++;
            }
            if (nextCode[0] < 4096) {
                dict.put(cand, nextCode[0]);
                nextCode[0]++;
            } else {
                // 字典满：发 Clear 重置（符合标准 GIF 解码器语义）
                bits.write(clear, codeSize[0]);
                codeSize[0] = minCodeSize + 1;
                nextCode[0] = eoi + 1;
                dict.clear();
            }
            cur = new StringBuilder().append(c);
        }
        if (cur.length() > 0) {
            int outCode = (cur.length() == 1) ? (cur.charAt(0) & 0xFF) : dict.get(cur.toString());
            bits.write(outCode, codeSize[0]);
        }
        bits.write(eoi, codeSize[0]);
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
