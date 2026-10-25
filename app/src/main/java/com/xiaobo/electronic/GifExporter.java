package com.xiaobo.electronic;

import android.content.Context;
import android.graphics.Bitmap;
import android.media.MediaMetadataRetriever;
import android.media.MediaScannerConnection;
import android.os.Environment;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

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
            long durationMs = Long.parseLong(
                    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION));
            int srcW = Integer.parseInt(
                    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH));
            int srcH = Integer.parseInt(
                    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT));
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

            String outName = "romantic_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date()) + ".gif";
            File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "RomanticFirework");
            if (!dir.exists()) dir.mkdirs();
            File out = new File(dir, outName);
            writeGif(new FileOutputStream(out), rgbFrames, gifW, gifH, 100);
            MediaScannerConnection.scanFile(ctx, new String[]{out.getAbsolutePath()}, new String[]{"image/gif"}, null);
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
            // Netscape Loop Extension
            os.write(0x21); os.write(0xFF); os.write(11);
            os.write("NETSCAPE2.0".getBytes("US-ASCII"));
            os.write(3); os.write(1); os.write(0); os.write(0); os.write(0);

            byte[] pixels = new byte[w * h];
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
            }
            os.write(0x3B); // trailer
        } finally {
            try { os.close(); } catch (Exception ignored) { }
        }
    }

    /** 标准 GIF LZW（可变码长，含 Clear/EOI） */
    private static void lzwEncode(OutputStream os, byte[] pixels, int minCodeSize) throws Exception {
        os.write(minCodeSize);
        int clear = 1 << minCodeSize;
        int eoi = clear + 1;
        int codeSize = minCodeSize + 1;
        int nextCode = eoi + 1;

        java.util.HashMap<java.util.ArrayList<Byte>, Integer> dict = new java.util.HashMap<>();
        java.util.ArrayList<Byte> cur = new java.util.ArrayList<>();
        BitWriter bits = new BitWriter(os);

        int resetDict = 0;
        for (byte b : pixels) {
            if (resetDict == 1) {
                bits.write(clear, codeSize);
                codeSize = minCodeSize + 1;
                nextCode = eoi + 1;
                dict.clear();
                resetDict = 0;
            }
            java.util.ArrayList<Byte> next = new java.util.ArrayList<>(cur);
            next.add(b);
            Integer code = dict.get(next);
            if (code != null) {
                cur = next;
            } else {
                int outCode;
                if (cur.isEmpty()) outCode = (b & 0xFF);
                else outCode = dict.get(cur);
                bits.write(outCode, codeSize);
                if (nextCode < 4096) {
                    dict.put(next, nextCode);
                    if (nextCode == (1 << codeSize) && codeSize < 12) codeSize++;
                    nextCode++;
                } else {
                    resetDict = 1;
                }
                cur = new java.util.ArrayList<>();
                cur.add(b);
            }
        }
        if (!cur.isEmpty()) {
            Integer outCode = dict.get(cur);
            bits.write(outCode != null ? outCode : cur.get(0) & 0xFF, codeSize);
        }
        bits.write(eoi, codeSize);
        bits.flush();

        // 打包成 255 字节数据块
        // BitWriter 已直接按块输出，这里无额外操作
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
