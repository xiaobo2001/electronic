package com.xiaobo.electronic;

import static org.junit.Assert.assertFalse;

import com.xiaobo.electronic.data.AnniversaryStore;

import org.junit.Test;

/**
 * 手动新增纪念日的入参校验。
 *
 * <p>只覆盖"格式不合法直接拒绝"的分支——这些分支在碰 MMKV 之前就返回，
 * 所以能在 JVM 单测里跑（MMKV 依赖 native，不能在单测里读写）。
 */
public class AnniversaryStoreTest {

    @Test
    public void invalidDate_isRejected() {
        assertFalse(AnniversaryStore.add("2026-1-1", "她的生日", ""));   // 必须补零
        assertFalse(AnniversaryStore.add("2026-13-01", "她的生日", ""));  // 月份越界
        assertFalse(AnniversaryStore.add("2026-00-10", "她的生日", ""));  // 月份为 0
        assertFalse(AnniversaryStore.add("2026-12-32", "她的生日", ""));  // 日期越界
        assertFalse(AnniversaryStore.add("hello", "她的生日", ""));
        assertFalse(AnniversaryStore.add("", "她的生日", ""));
        assertFalse(AnniversaryStore.add(null, "她的生日", ""));
    }
}
