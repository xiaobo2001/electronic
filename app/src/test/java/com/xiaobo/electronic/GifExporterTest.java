package com.xiaobo.electronic;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.xiaobo.electronic.media.GifExporter;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.lang.reflect.Method;

/**
 * GIF 导出的边界测试。
 *
 * <p>这里的每一条都对应一次真实崩溃：调色板建表越界（ExceptionInInitializerError，
 * 线程 gif-export 直接挂掉）、以及量化索引越界导致写出非法 GIF。
 */
public class GifExporterTest {

    private static int[] palette() throws Exception {
        Method m = GifExporter.class.getDeclaredMethod("buildPalette");
        m.setAccessible(true);
        return (int[]) m.invoke(null);
    }

    private static int nearest(int argb, int x, int y) throws Exception {
        Method m = GifExporter.class.getDeclaredMethod("nearest", int.class, int.class, int.class);
        m.setAccessible(true);
        return (Integer) m.invoke(null, argb, x, y);
    }

    /** 建表必须在 256 容量内完成，不得越界（旧版 6×7×6 + 32 灰阶 = 284，静态初始化就崩） */
    @Test
    public void palette_fitsIn256() throws Exception {
        int[] p = palette();
        assertEquals(256, p.length);
        for (int c : p) {
            assertTrue("调色板含非法项", (c >>> 24) != 0);
        }
    }

    /** 任意颜色量化后的索引必须落在 0..255，否则 GIF 色表越界、图像花屏 */
    @Test
    public void nearestIndex_alwaysInRange() throws Exception {
        for (int r = 0; r < 256; r += 17) {
            for (int g = 0; g < 256; g += 17) {
                for (int b = 0; b < 256; b += 17) {
                    int idx = nearest(0xFF000000 | (r << 16) | (g << 8) | b, r & 7, g & 7);
                    assertTrue("彩色索引越界: " + idx, idx >= 0 && idx <= 255);
                }
            }
        }
        // 灰阶：含纯黑与纯白两端
        for (int v = 0; v <= 255; v++) {
            int idx = nearest(0xFF000000 | (v << 16) | (v << 8) | v, 0, 0);
            assertTrue("灰阶索引越界: " + idx, idx >= 0 && idx <= 255);
        }
        // 半透明（烟花余晖常见）应落到透明槽，不产生非法索引
        assertTrue(nearest(0x10000000, 0, 0) >= 0);
    }

    /** 灰阶应该有足够级数：旧版 nearest 只返回 252..255 四个槽，夜空渐变会压成四段色块 */
    @Test
    public void gray_hasEnoughLevels() throws Exception {
        int lo = nearest(0xFF000000, 0, 0);              // 纯黑
        int hi = nearest(0xFFFFFFFF, 0, 0);              // 纯白
        assertTrue("黑到白之间灰阶级数不足，会出色带", Math.abs(hi - lo) >= 16);
    }

    /** 端到端：写出合法 GIF89a 流，以 trailer 0x3B 收尾 */
    @Test
    public void writeGif_producesValidStream() throws Exception {
        int w = 8, h = 8;
        int[][] frames = new int[3][];
        for (int i = 0; i < frames.length; i++) {
            int[] px = new int[w * h];
            for (int j = 0; j < px.length; j++) {
                px[j] = 0xFF000000 | ((j * 7) & 0xFF) << 16 | ((i * 40) & 0xFF);
            }
            frames[i] = px;
        }
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        Method m = GifExporter.class.getDeclaredMethod(
                "writeGif", java.io.OutputStream.class, int[][].class, int.class, int.class, int.class);
        m.setAccessible(true);
        m.invoke(null, bos, frames, w, h, 100);

        byte[] out = bos.toByteArray();
        assertTrue("缺少 GIF89a 头", new String(out, 0, 6, "US-ASCII").equals("GIF89a"));
        assertEquals("未以 trailer 结尾", 0x3B, out[out.length - 1] & 0xFF);
        assertTrue("输出过小，疑似没写帧数据", out.length > 100);
    }
}
