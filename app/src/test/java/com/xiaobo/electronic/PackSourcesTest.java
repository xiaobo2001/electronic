package com.xiaobo.electronic;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertNull;
import com.xiaobo.electronic.speech.PackSources;

/**
 * 源清单合并逻辑的测试。
 *
 * <p>这份清单存在的意义是"网盘直链失效时不用发版"，所以顺序很关键：
 * 清单里的链接必须排在内置链接前面——它才是随时可改、代表"当前正确"的那份。
 */
public class PackSourcesTest {

    @Test
    public void merge_remoteComesFirst() {
        String[] out = PackSources.merge(
                new String[]{"https://new/a.zip"},
                new String[]{"https://builtin/a.zip"});
        assertArrayEquals(new String[]{"https://new/a.zip", "https://builtin/a.zip"}, out);
    }

    @Test
    public void merge_dropsEmptyAndDuplicates() {
        String[] out = PackSources.merge(
                new String[]{"https://a/x.zip", "", "  ", null},
                new String[]{"https://a/x.zip", "https://b/y.zip"});
        assertArrayEquals(new String[]{"https://a/x.zip", "https://b/y.zip"}, out);
    }

    @Test
    public void merge_allEmpty_returnsNull() {
        assertNull(PackSources.merge(null, null));
        assertNull(PackSources.merge(new String[]{}, new String[]{}));
        assertNull(PackSources.merge(null, new String[]{""}));
    }

    @Test
    public void merge_remoteOnly() {
        String[] out = PackSources.merge(new String[]{"https://a/x.zip"}, null);
        assertArrayEquals(new String[]{"https://a/x.zip"}, out);
    }
}
