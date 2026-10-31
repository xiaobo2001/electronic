package com.xiaobo.electronic;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import com.xiaobo.electronic.data.AnniversaryReminder;
import com.xiaobo.electronic.data.AnniversaryStore;

/**
 * 纪念日提醒的日期计算测试。
 *
 * <p>纪念日是"每年这一天"，牵涉跨年、提前天数、2 月末等边界，
 * 靠手工点对话框验证既慢又容易漏，这里用纯 JVM 计算锁住行为。
 */
public class AnniversaryReminderTest {

    private static AnniversaryStore.Entry entry(String date, String label) {
        return new AnniversaryStore.Entry(date, label, "遇见你真好");
    }

    private static String monthDayOf(long millis) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(millis);
        return (c.get(Calendar.MONTH) + 1) + "-" + c.get(Calendar.DAY_OF_MONTH);
    }

    /** 未来的纪念日：下一次提醒应落在今年的那一天（提前 N 天） */
    @Test
    public void nextTrigger_futureDateThisYear() {
        Calendar t = Calendar.getInstance();
        t.add(Calendar.DAY_OF_MONTH, 20); // 20 天后
        String md = String.format("%04d-%02d-%02d",
                t.get(Calendar.YEAR), t.get(Calendar.MONTH) + 1, t.get(Calendar.DAY_OF_MONTH));
        List<AnniversaryStore.Entry> list = new ArrayList<>();
        list.add(entry(md, "未来"));

        long at = AnniversaryReminder.nextTriggerMillis(list, 3); // 提前 3 天
        assertTrue(at > System.currentTimeMillis());

        Calendar expect = Calendar.getInstance();
        expect.add(Calendar.DAY_OF_MONTH, 20 - 3);
        assertEquals(monthDayOf(expect.getTimeInMillis()), monthDayOf(at));
        // 提醒统一落在上午 9 点，不会半夜弹
        assertEquals(9, hourOf(at));
    }

    /** 已经过去的纪念日：今年份作废，下一次应落到明年 */
    @Test
    public void nextTrigger_pastDateRollsToNextYear() {
        Calendar t = Calendar.getInstance();
        t.add(Calendar.YEAR, -2);
        t.add(Calendar.DAY_OF_MONTH, -10); // 两年前的今天再往前 10 天
        String md = String.format("%04d-%02d-%02d",
                t.get(Calendar.YEAR), t.get(Calendar.MONTH) + 1, t.get(Calendar.DAY_OF_MONTH));
        List<AnniversaryStore.Entry> list = new ArrayList<>();
        list.add(entry(md, "过去"));

        long at = AnniversaryReminder.nextTriggerMillis(list, 0);
        int thisYear = Calendar.getInstance().get(Calendar.YEAR);
        Calendar expect = Calendar.getInstance();
        expect.set(Calendar.YEAR, thisYear + 1);
        expect.set(Calendar.MONTH, t.get(Calendar.MONTH));
        expect.set(Calendar.DAY_OF_MONTH, t.get(Calendar.DAY_OF_MONTH));
        AnniversaryReminder.trimToDay(expect);

        assertEquals(monthDayOf(expect.getTimeInMillis()), monthDayOf(at));
        assertEquals(thisYear + 1, yearOf(at));
    }

    /** dueOn：advance=0 时应命中"就是今天"的那条 */
    @Test
    public void dueOn_matchesToday() {
        Calendar now = Calendar.getInstance();
        String md = String.format("%04d-%02d-%02d",
                now.get(Calendar.YEAR), now.get(Calendar.MONTH) + 1, now.get(Calendar.DAY_OF_MONTH));
        List<AnniversaryStore.Entry> list = new ArrayList<>();
        list.add(entry(md, "今天"));
        list.add(entry("1999-01-01", "无关"));

        List<AnniversaryStore.Entry> due = AnniversaryReminder.dueOn(list, 0);
        assertEquals(1, due.size());
        assertEquals("今天", due.get(0).label);

        // 提前 1 天时今天不该被命中（要命中的是"明天到期"的那条）
        assertTrue(AnniversaryReminder.dueOn(list, 1).isEmpty());
    }

    /** 非法日期不能参与计算，也不能抛异常 */
    @Test
    public void invalidDateIgnored() {
        List<AnniversaryStore.Entry> list = new ArrayList<>();
        list.add(entry("不是日期", "坏数据"));
        assertEquals(0, AnniversaryReminder.nextTriggerMillis(list, 0));
        assertTrue(AnniversaryReminder.dueOn(list, 0).isEmpty());
    }

    private static int hourOf(long millis) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(millis);
        return c.get(Calendar.HOUR_OF_DAY);
    }

    private static int yearOf(long millis) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(millis);
        return c.get(Calendar.YEAR);
    }
}
