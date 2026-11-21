package com.xiaobo.electronic.data;

import java.util.Calendar;
import java.util.List;

/** 纪念日倒计时计算（纯逻辑，供桌面小组件与单测复用） */
public final class AnniversaryCountdown {

    private AnniversaryCountdown() {
    }

    /** 一句话倒计时，如"距 表白纪念日 还有 12 天"；无纪念日返回 null */
    public static String oneLine(List<AnniversaryStore.Entry> entries, long now) {
        EntryHit hit = nearest(entries, now);
        if (hit == null) return null;
        long days = (hit.nextMillis - now) / 86400000L;
        String label = hit.entry.label == null || hit.entry.label.trim().isEmpty()
                ? "纪念日" : hit.entry.label;
        if (days <= 0) return label + " 就是今天 💗";
        return "距 " + label + " 还有 " + days + " 天";
    }

    /** 距今天的天数（0=今天） */
    public static int days(List<AnniversaryStore.Entry> entries, long now) {
        EntryHit hit = nearest(entries, now);
        return hit == null ? -1 : (int) ((hit.nextMillis - now) / 86400000L);
    }

    /** 命中的纪念日标题 */
    public static String label(List<AnniversaryStore.Entry> entries) {
        EntryHit hit = nearest(entries, System.currentTimeMillis());
        if (hit == null) return "";
        return hit.entry.label == null || hit.entry.label.trim().isEmpty()
                ? "纪念日" : hit.entry.label;
    }

    private static final class EntryHit {
        final AnniversaryStore.Entry entry;
        final long nextMillis;

        EntryHit(AnniversaryStore.Entry entry, long nextMillis) {
            this.entry = entry;
            this.nextMillis = nextMillis;
        }
    }

    /** 最近的一个"下一次周年"，已过去的自动滚到明年 */
    private static EntryHit nearest(List<AnniversaryStore.Entry> entries, long now) {
        if (entries == null) return null;
        EntryHit best = null;
        for (AnniversaryStore.Entry e : entries) {
            long t = parseMillis(e.date);
            if (t <= 0) continue;
            long next = nextOccurrence(t, now);
            if (best == null || next < best.nextMillis) best = new EntryHit(e, next);
        }
        return best;
    }

    private static long parseMillis(String yyyyMmDd) {
        try {
            String[] p = yyyyMmDd.split("-");
            if (p.length != 3) return 0;
            Calendar c = Calendar.getInstance();
            c.clear();
            c.set(Integer.parseInt(p[0]), Integer.parseInt(p[1]) - 1, Integer.parseInt(p[2]));
            return c.getTimeInMillis();
        } catch (Exception e) {
            return 0;
        }
    }

    private static long nextOccurrence(long original, long now) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(original);
        int month = c.get(Calendar.MONTH);
        int day = c.get(Calendar.DAY_OF_MONTH);
        // 从"今年"起算（不是纪念日的年份），否则老纪念日会滚到 2021 这种过去年份
        Calendar current = Calendar.getInstance();
        current.setTimeInMillis(now);
        int thisYear = current.get(Calendar.YEAR);
        Calendar next = Calendar.getInstance();
        next.clear();
        next.set(thisYear, month, day);
        long t = next.getTimeInMillis();
        if (t < now) {
            next.set(thisYear + 1, month, day);
            t = next.getTimeInMillis();
        }
        return t;
    }
}
