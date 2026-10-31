package com.xiaobo.electronic;

import org.junit.Test;

import java.util.Calendar;

import static org.junit.Assert.assertEquals;

/**
 * 定时演出与许愿墙的时间口径测试。
 *
 * <p>这两处最容易错的是"还差几天"：直接拿毫秒差除一天，会把
 * "今天 23:50 → 明天 00:10"算成 0 天（同日）而不是 1 天（明天）。
 * 这里把边界钉死，改算法时不会悄悄回退。
 */
public class ScheduledShowTest {

    private static long at(int year, int month, int day, int hour, int minute) {
        Calendar c = Calendar.getInstance();
        c.clear();
        c.set(year, month - 1, day, hour, minute, 0);
        return c.getTimeInMillis();
    }

    @Test
    public void sameDay_isZero() {
        long from = at(2026, 11, 1, 9, 0);
        long to = at(2026, 11, 1, 23, 50);
        assertEquals(0, ScheduledShow.daysBetween(from, to));
    }

    @Test
    public void crossingMidnight_isOneDay() {
        long from = at(2026, 11, 1, 23, 50);
        long to = at(2026, 11, 2, 0, 10);
        assertEquals(1, ScheduledShow.daysBetween(from, to));
    }

    @Test
    public void acrossMonth_isCorrect() {
        long from = at(2026, 11, 30, 10, 0);
        long to = at(2026, 12, 2, 10, 0);
        assertEquals(2, ScheduledShow.daysBetween(from, to));
    }

    @Test
    public void describe_usesNaturalDay() {
        Calendar now = Calendar.getInstance();
        Calendar target = Calendar.getInstance();
        target.add(Calendar.DAY_OF_YEAR, 3);
        String text = ScheduledShow.describe(target.getTimeInMillis());
        assertEquals(true, text.contains("还有 3 天"));
        // 只有分钟不同时应算今天
        Calendar soon = (Calendar) now.clone();
        soon.add(Calendar.MINUTE, 30);
        assertEquals(true, ScheduledShow.describe(soon.getTimeInMillis()).contains("今天"));
    }

    @Test
    public void wishAgo_formats() {
        long now = System.currentTimeMillis();
        assertEquals("刚刚", WishStore.ago(now - 5_000L));
        assertEquals(true, WishStore.ago(now - 3 * 3_600_000L).endsWith("小时前"));
        assertEquals(true, WishStore.ago(now - 5 * 86_400_000L).endsWith("天前"));
    }
}
