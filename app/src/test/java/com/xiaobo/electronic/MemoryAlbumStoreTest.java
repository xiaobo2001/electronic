package com.xiaobo.electronic;

import static org.junit.Assert.assertTrue;

import com.xiaobo.electronic.data.MemoryAlbumStore;

import org.junit.Test;

/** 回忆册条目的展示文案（不依赖 MMKV 的纯逻辑部分） */
public class MemoryAlbumStoreTest {

    @Test
    public void title_showsDateAndResult() {
        long ts = 1763702400000L; // 2025-11-21 附近
        MemoryAlbumStore.Entry yes = new MemoryAlbumStore.Entry(
                ts, "遇见你真好", "天蝎", 12.3f, "我愿意", "小美", "我");
        MemoryAlbumStore.Entry think = new MemoryAlbumStore.Entry(
                ts, "再想想", "天蝎", 12.3f, "再想想", "小美", "我");
        assertTrue(yes.title().contains("我愿意"));
        assertTrue(yes.title().matches(".*\\d{4}\\.\\d{2}\\.\\d{2}.*"));
        assertTrue(think.title().contains("再想想"));
    }
}
