package com.xiaobo.electronic;

import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import com.xiaobo.electronic.speech.ModelDownloader;

/**
 * zip 整包解压的测试。
 *
 * <p>整包下载（国内网盘直链）是加速手段，不是唯一来源：链接失效会自动回退
 * 逐文件下载。但解压这一步必须自己把住，因为包来自第三方网盘——
 * 路径穿越和解压炸弹是这类"下载一个压缩包再解开"流程最常见的两个事故。
 */
public class ZipBundleTest {

    private static File zipWith(String... entryNames) throws IOException {
        File z = File.createTempFile("bundle", ".zip");
        z.deleteOnExit();
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(z))) {
            for (String n : entryNames) {
                zos.putNextEntry(new ZipEntry(n));
                zos.write("hello".getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();
            }
        }
        return z;
    }

    private static File tempDir() throws IOException {
        File d = File.createTempFile("unzip", ".d");
        d.delete();
        if (!d.mkdirs()) throw new IOException("mkdirs failed");
        d.deleteOnExit();
        return d;
    }

    /** 正常包：含子目录的条目也要按原结构解出来 */
    @Test
    public void unzip_keepsDirectoryStructure() throws Exception {
        File z = zipWith("tokens.txt", "dict/jieba.dict.utf8", "dict/pos_dict/prob_emit.utf8");
        File dest = tempDir();
        ModelDownloader.unzip(z, dest, 1024 * 1024L);

        assertTrue(new File(dest, "tokens.txt").isFile());
        assertTrue(new File(dest, "dict/jieba.dict.utf8").isFile());
        assertTrue(new File(dest, "dict/pos_dict/prob_emit.utf8").isFile());
        assertEquals(5L, new File(dest, "tokens.txt").length());
    }

    /** 路径穿越：zip 里的 ../ 不许写到目标目录外面 */
    @Test
    public void unzip_rejectsPathTraversal() throws Exception {
        File z = zipWith("../../evil.txt");
        File dest = tempDir();
        try {
            ModelDownloader.unzip(z, dest, 1024 * 1024L);
            fail("含有 ../ 的条目必须被拒绝");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("越界"));
        }
        assertFalse("目标目录外不该出现文件",
                new File(dest.getParentFile().getParentFile(), "evil.txt").exists());
    }

    /** 解压炸弹：声称能解出天量的包，必须在写满存储前停下 */
    @Test
    public void unzip_stopsAtSizeLimit() throws Exception {
        File z = File.createTempFile("bomb", ".zip");
        z.deleteOnExit();
        // DEFLATED 下 10MB 的零字节压完极小，正好模拟"小包解出大文件"
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(z));
             OutputStream ignored = zos) {
            zos.putNextEntry(new ZipEntry("big.bin"));
            byte[] chunk = new byte[64 * 1024];
            for (int i = 0; i < 160; i++) zos.write(chunk);   // 10 MB
            zos.closeEntry();
        }
        File dest = tempDir();
        try {
            ModelDownloader.unzip(z, dest, 64 * 1024L);   // 上限远小于实际体积
            fail("超过体积上限必须中止");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("体积异常"));
        }
    }
}
