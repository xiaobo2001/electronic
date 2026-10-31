package com.xiaobo.electronic;

import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import com.xiaobo.electronic.speech.ModelDownloader;

/**
 * 下载完整性判据的回归测试。
 *
 * <p><b>这里锁住的是一个真实事故。</b>早期用"文件大于 1024 字节"当完整性判据，
 * 而模型包里的 tokens.txt 只有 485 字节。结果 20MB 的 onnx、2MB 的词典都下好了，
 * 偏偏卡在这个 485 字节的小文件上：下载成功却被判成空文件，换源重来，最后报失败。
 * 用户看到的现象是"进度停在 19.8MB 然后下载失败"——前面 19.8MB 其实一直是好的。
 *
 * <p>判据改成按清单里的期望大小校验后，这几条用例保证同样的坑不会再回来。
 */
public class ModelDownloaderTest {

    private static File makeFile(String name, int bytes) throws IOException {
        File f = File.createTempFile(name, ".bin");
        f.deleteOnExit();
        try (FileOutputStream o = new FileOutputStream(f)) {
            byte[] buf = new byte[bytes];
            o.write(buf);
        }
        return f;
    }

    /** 事故现场：485 字节的 tokens.txt 必须被判为完整 */
    @Test
    public void smallFileUnderOldThreshold_isComplete() throws Exception {
        File f = makeFile("tokens", 485);
        assertTrue("485 字节的 tokens.txt 应判为完整",
                ModelDownloader.complete(f, 485L));
    }

    /** 49 字节的 user.dict.utf8（AISHELL3 词典里最小的文件）同理 */
    @Test
    public void tinyDictFile_isComplete() throws Exception {
        File f = makeFile("user_dict", 49);
        assertTrue(ModelDownloader.complete(f, 49L));
    }

    /** 只下了一半：必须判为不完整，否则会拿残缺文件去加载模型 */
    @Test
    public void truncatedFile_isNotComplete() throws Exception {
        File f = makeFile("half", 100);
        assertFalse(ModelDownloader.complete(f, 485L));
        assertFalse("20MB 的模型只下了 10MB 不能算完整",
                ModelDownloader.complete(f, 18_652_795L));
    }

    /** 空文件、不存在的文件都不是完整 */
    @Test
    public void emptyOrMissing_isNotComplete() throws Exception {
        File f = makeFile("empty", 0);
        assertFalse(ModelDownloader.complete(f, 485L));
        assertFalse(ModelDownloader.complete(new File("/nonexistent/nope.bin"), 100L));
        assertFalse(ModelDownloader.complete(null, 100L));
    }

    /** 期望大小允许 2% 容差，上游微调文件不至于让用户反复重下 */
    @Test
    public void toleranceWithinTwoPercent() throws Exception {
        File f = makeFile("near", 500);
        assertTrue("500 字节对期望 485 应在容差内",
                ModelDownloader.complete(f, 485L));
        File g = makeFile("far", 700);
        assertFalse("700 字节对期望 485 已明显不符",
                ModelDownloader.complete(g, 485L));
    }

    /** 没给期望大小时退回保守阈值，不能把 0 字节当成完整 */
    @Test
    public void unknownExpectSize_fallsBackToThreshold() throws Exception {
        File small = makeFile("small", 600);
        assertFalse(ModelDownloader.complete(small, 0L));
        File big = makeFile("big", 2048);
        assertTrue(ModelDownloader.complete(big, 0L));
    }

    /** 4xx 换源也救不回来，别让用户在每个源上都白等一轮超时 */
    @Test
    public void httpErrors_fatalDecidesWhetherToRetry() {
        assertTrue(ModelDownloader.fatal(new java.io.IOException("HTTP 404")));
        assertTrue(ModelDownloader.fatal(new java.io.IOException("HTTP 416")));
        assertFalse("超时应当重试",
                ModelDownloader.fatal(new java.io.IOException("Read timed out")));
        assertFalse("408 可以重试",
                ModelDownloader.fatal(new java.io.IOException("HTTP 408")));
    }
}
