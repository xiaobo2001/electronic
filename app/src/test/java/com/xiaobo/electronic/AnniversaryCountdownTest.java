package com.xiaobo.electronic;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.xiaobo.electronic.data.AnniversaryCountdown;
import com.xiaobo.electronic.data.AnniversaryStore;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/** 纪念日倒计时逻辑（桌面大号组件用） */
public class AnniversaryCountdownTest {

    private static long dateMillis(int year, int month, int day) {
        Calendar c = Calendar.getInstance();
        c.clear();
        c.set(year, month - 1, day);
        return c.getTimeInMillis();
    }

    private static String ymd(int year, int month, int day) {
        return String.format("%04d-%02d-%02d", year, month, day);
    }

    @Test
    public void empty_returnsNullAndMinusOne() {
        List<AnniversaryStore.Entry> empty = new ArrayList<>();
        assertEquals(-1, AnniversaryCountdown.days(empty, System.currentTimeMillis()));
        assertTrue(AnniversaryCountdown.oneLine(empty, System.currentTimeMillis()) == null);
    }

    @Test
    public void nextAnniversary_rollsToNextYear() {
        // 2020-05-20 的纪念日，在 2026 年看应滚到 2026-05-20 或 2027（取决于今天）
        List<AnniversaryStore.Entry> list = new ArrayList<>();
        list.add(new AnniversaryStore.Entry("2020-05-20", "表白纪念日", "遇见你真好"));
        long now = dateMillis(2026, 1, 1);
        int days = AnniversaryCountdown.days(list, now);
        assertTrue("应算出未来某一天，不是负数", days >= 0);
        assertTrue("距离应在一年内", days <= 365);
        assertTrue(AnniversaryCountdown.oneLine(list, now).contains("表白纪念日"));
    }

    @Test
    public void today_showsZeroDay() {
        List<AnniversaryStore.Entry> list = new ArrayList<>();
        long today = dateMillis(2026, 5, 20);
        list.add(new AnniversaryStore.Entry(ymd(2026, 5, 20), "就是今天", ""));
        assertEquals(0, AnniversaryCountdown.days(list, today));
        assertTrue(AnniversaryCountdown.oneLine(list, today).contains("就是今天"));
    }

    /** 回归：当天晚上看，不能因为"零点已过"就滚到明年显示 365 天 */
    @Test
    public void sameDayLateEvening_stillToday() {
        List<AnniversaryStore.Entry> list = new ArrayList<>();
        list.add(new AnniversaryStore.Entry(ymd(2026, 5, 20), "就是今天", ""));
        Calendar late = Calendar.getInstance();
        late.clear();
        late.set(2026, 4, 20, 23, 30);
        long now = late.getTimeInMillis();
        assertEquals(0, AnniversaryCountdown.days(list, now));
        assertTrue(AnniversaryCountdown.oneLine(list, now).contains("就是今天"));
    }

    /** 回归：明天的纪念日，今晚看应该是 1 天，而不是 0 天 */
    @Test
    public void tomorrowEvening_isOneDay() {
        List<AnniversaryStore.Entry> list = new ArrayList<>();
        list.add(new AnniversaryStore.Entry(ymd(2026, 5, 21), "明天", ""));
        Calendar tonight = Calendar.getInstance();
        tonight.clear();
        tonight.set(2026, 4, 20, 22, 0);
        assertEquals(1, AnniversaryCountdown.days(list, tonight.getTimeInMillis()));
    }

    @Test
    public void nearest_wins() {
        List<AnniversaryStore.Entry> list = new ArrayList<>();
        list.add(new AnniversaryStore.Entry(ymd(2026, 5, 20), "远的", ""));
        list.add(new AnniversaryStore.Entry(ymd(2026, 1, 10), "近的", ""));
        long now = dateMillis(2026, 1, 1);
        assertTrue(AnniversaryCountdown.oneLine(list, now).contains("近的"));
    }
}
