package com.xiaobo.electronic.data;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import com.tencent.mmkv.MMKV;

import java.util.Calendar;
import java.util.List;
import com.xiaobo.electronic.App;

/**
 * 纪念日到期提醒的调度层。
 *
 * <p>纪念日是"每年这一天"，所以提醒不能是一次性闹钟：这里算出
 * <b>下一个</b>该提醒的时刻（今年份已过就取明年份，减去提前天数），
 * 用 {@code setWindow} 排一个单次闹钟，触发后由 {@link AnniversaryReceiver}
 * 再排下一次。开机与 App 升级会重排，避免重启后提醒全部丢失。
 *
 * <p>为什么不用 WorkManager：项目没有引入 androidx.work，为一个每年一次的提醒
 * 增加一个依赖不划算；{@code setWindow} 自带 30 分钟对齐窗口，比精确闹钟省电，
 * 也绕开了 Android 12+ 的 SCHEDULE_EXACT_ALARM 权限。
 */
public final class AnniversaryReminder {

    static final String KEY_ENABLED = "anniv_remind_enabled";
    static final String KEY_DAYS = "anniv_remind_days";

    static final String ACTION_ALARM = "com.xiaobo.electronic.action.ANNIVERSARY_ALARM";
    private static final int REQ_ALARM = 0x51A1;

    /** 可选的提前天数：当天 / 提前 1 天 / 3 天 / 7 天 */
    public static final int[] DAY_OPTIONS = {0, 1, 3, 7};
    public static final String[] DAY_LABELS = {"当天提醒", "提前 1 天", "提前 3 天", "提前 7 天"};

    /** 提醒落在这个钟点，避免半夜弹通知 */
    private static final int REMIND_HOUR = 9;

    private AnniversaryReminder() {
    }

    public static boolean isEnabled() {
        try {
            return MMKV.defaultMMKV().decodeBool(KEY_ENABLED, false);
        } catch (Throwable t) {
            return false;
        }
    }

    public static void setEnabled(Context c, boolean on) {
        try {
            MMKV.defaultMMKV().encode(KEY_ENABLED, on);
        } catch (Throwable ignored) {
        }
        reschedule(c);
    }

    public static int getDays() {
        try {
            return MMKV.defaultMMKV().decodeInt(KEY_DAYS, 0);
        } catch (Throwable t) {
            return 0;
        }
    }

    public static void setDays(Context c, int days) {
        try {
            MMKV.defaultMMKV().encode(KEY_DAYS, days);
        } catch (Throwable ignored) {
        }
        reschedule(c);
    }

    public static String summary() {
        if (AnniversaryStore.load().isEmpty()) return "暂无记录";
        if (!isEnabled()) return "提醒已关闭";
        int d = getDays();
        for (int i = 0; i < DAY_OPTIONS.length; i++) {
            if (DAY_OPTIONS[i] == d) return DAY_LABELS[i];
        }
        return "提前 " + d + " 天";
    }

    static PendingIntent alarmIntent(Context c) {
        Intent i = new Intent(c, AnniversaryReceiver.class).setAction(ACTION_ALARM);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        return PendingIntent.getBroadcast(c, REQ_ALARM, i, flags);
    }

    /** 重排下一次提醒（开关变化、新增纪念日、开机、触发后都调用） */
    public static void reschedule(Context c) {
        try {
            AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
            if (am == null) return;
            PendingIntent pi = alarmIntent(c);
            if (!isEnabled()) {
                am.cancel(pi);
                return;
            }
            long at = nextTriggerMillis(AnniversaryStore.load(), getDays());
            if (at <= 0) {
                am.cancel(pi);
                return;
            }
            // 30 分钟对齐窗口：Doze 下允许延后，比精确闹钟省电且不需要额外权限
            am.setWindow(AlarmManager.RTC_WAKEUP, at, 30 * 60 * 1000L, pi);
        } catch (Throwable ignored) {
        }
    }

    /**
     * 下一个需要提醒的时刻：取所有纪念日里最近的一个未来时间点。
     *
     * @return 毫秒时间戳；没有任何可排的纪念日时返回 0
     */
    public static long nextTriggerMillis(List<AnniversaryStore.Entry> list, int advanceDays) {
        long now = System.currentTimeMillis();
        long best = 0;
        Calendar today = Calendar.getInstance();
        trimToDay(today);
        for (AnniversaryStore.Entry e : list) {
            int[] md = monthDay(e.date);
            if (md == null) continue;
            // 今年份与明年份都算一遍：今年已过就自然落到明年
            for (int addYear = 0; addYear <= 1; addYear++) {
                Calendar t = Calendar.getInstance();
                t.set(Calendar.YEAR, today.get(Calendar.YEAR) + addYear);
                t.set(Calendar.MONTH, md[0] - 1);
                t.set(Calendar.DAY_OF_MONTH, md[1]);
                trimToDay(t);
                // 2 月 29 日在平年会被 Calendar 归一化到 3 月 1 日，可接受
                t.add(Calendar.DAY_OF_MONTH, -advanceDays);
                t.set(Calendar.HOUR_OF_DAY, REMIND_HOUR);
                long ms = t.getTimeInMillis();
                if (ms > now && (best == 0 || ms < best)) best = ms;
            }
        }
        return best;
    }

    /**
     * 找出"从今天起 advanceDays 天后正好到期"的纪念日。
     * 与 {@link #nextTriggerMillis} 对应：闹钟落在那一天时用来生成通知内容。
     */
    public static List<AnniversaryStore.Entry> dueOn(List<AnniversaryStore.Entry> list, int advanceDays) {
        List<AnniversaryStore.Entry> out = new java.util.ArrayList<>();
        Calendar target = Calendar.getInstance();
        trimToDay(target);
        target.add(Calendar.DAY_OF_MONTH, advanceDays);
        int tm = target.get(Calendar.MONTH) + 1;
        int td = target.get(Calendar.DAY_OF_MONTH);
        for (AnniversaryStore.Entry e : list) {
            int[] md = monthDay(e.date);
            if (md != null && md[0] == tm && md[1] == td) out.add(e);
        }
        return out;
    }

    /** 解析 yyyy-MM-dd 的月、日；格式不对返回 null */
    private static int[] monthDay(String date) {
        if (date == null) return null;
        String[] p = date.split("-");
        if (p.length < 3) return null;
        try {
            int m = Integer.parseInt(p[1].trim());
            int d = Integer.parseInt(p[2].trim());
            if (m < 1 || m > 12 || d < 1 || d > 31) return null;
            return new int[]{m, d};
        } catch (Exception e) {
            return null;
        }
    }

    public static void trimToDay(Calendar c) {
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
    }
}
