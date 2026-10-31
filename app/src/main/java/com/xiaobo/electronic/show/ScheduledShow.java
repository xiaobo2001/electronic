package com.xiaobo.electronic.show;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import java.util.ArrayList;
import java.util.List;
import com.xiaobo.electronic.App;

/**
 * 定时自动演出：定好日子和钟点，到点自动把那场烟花放给她看。
 *
 * <p>典型场景是生日零点——你要么熬夜守着，要么错过。闹钟在 0 点响一次，
 * 手机自己亮起来开演，人只要在旁边就行了。
 *
 * <p>两个必须处理的现实问题：
 * <ul>
 *   <li><b>重启即失效</b>：AlarmManager 的闹钟不跨开机，所以
 *       {@code BOOT_COMPLETED} 与每次 App 启动都要重排（{@link #reschedule}）。</li>
 *   <li><b>后台起不了 Activity</b>：Android 10+ 限制后台启动页面，
 *       所以走"高优先级通知 + 全屏意图"：亮屏时直接全屏开演，息屏时是一条能点开的通知，
 *       不会像 startActivity 那样被系统静默吞掉。</li>
 * </ul>
 */
public final class ScheduledShow {

    public static final String ACTION = "com.xiaobo.electronic.ACTION_SCHEDULED_SHOW";
    public static final String EXTRA_ID = "sched_id";
    public static final String EXTRA_LINES = "sched_lines";
    public static final String EXTRA_NAME = "sched_name";

    private static final String KEY = "scheduled_show_list";
    public static final int MAX = 10;

    public static final class Entry {
        public final long id;
        public final long timeMillis;
        public final String name;
        public final String lines;

        Entry(long id, long timeMillis, String name, String lines) {
            this.id = id;
            this.timeMillis = timeMillis;
            this.name = name;
            this.lines = lines;
        }
    }

    private ScheduledShow() {
    }

    public static List<Entry> load() {
        List<Entry> out = new ArrayList<>();
        try {
            org.json.JSONArray arr = new org.json.JSONArray(
                    com.tencent.mmkv.MMKV.defaultMMKV().decodeString(KEY, "[]"));
            for (int i = 0; i < arr.length(); i++) {
                org.json.JSONObject o = arr.getJSONObject(i);
                out.add(new Entry(o.optLong("id"),
                        o.optLong("time"),
                        o.optString("name", "演出"),
                        o.optString("lines", "")));
            }
        } catch (Exception ignored) {
        }
        // 过期的丢掉：留着只会让用户下次打开看到一堆早就响过的记录
        long now = System.currentTimeMillis();
        List<Entry> fresh = new ArrayList<>();
        for (Entry e : out) {
            if (e.timeMillis > now - 60_000L) fresh.add(e);
        }
        if (fresh.size() != out.size()) save(fresh);
        return fresh;
    }

    private static void save(List<Entry> list) {
        try {
            org.json.JSONArray arr = new org.json.JSONArray();
            int n = Math.min(list.size(), MAX);
            for (int i = 0; i < n; i++) {
                Entry e = list.get(i);
                org.json.JSONObject o = new org.json.JSONObject();
                o.put("id", e.id);
                o.put("time", e.timeMillis);
                o.put("name", e.name);
                o.put("lines", e.lines);
                arr.put(o);
            }
            com.tencent.mmkv.MMKV.defaultMMKV().encode(KEY, arr.toString());
        } catch (Exception ignored) {
        }
    }

    /** 新增一条定时并排闹钟。返回是否成功（超过上限或时间已过会失败）。 */
    public static boolean add(Context ctx, long timeMillis, String name, String lines) {
        if (timeMillis <= System.currentTimeMillis() + 30_000L) return false;
        List<Entry> list = load();
        if (list.size() >= MAX) return false;
        Entry e = new Entry(System.currentTimeMillis(), timeMillis,
                (name == null || name.trim().isEmpty()) ? "定时演出" : name.trim(),
                lines == null ? "" : lines);
        list.add(e);
        save(list);
        schedule(ctx, e);
        return true;
    }

    public static void remove(Context ctx, long id) {
        List<Entry> list = load();
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).id == id) {
                cancel(ctx, list.get(i));
                list.remove(i);
                save(list);
                return;
            }
        }
    }

    static void schedule(Context ctx, Entry e) {
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        PendingIntent pi = pending(ctx, e);
        try {
            // setWindow 而不是 setExact：到点前后的 30 秒窗口内触发即可，
            // 既不会被省电策略无限推迟，也不像精确定时那样耗电
            am.setWindow(AlarmManager.RTC_WAKEUP, e.timeMillis, 30_000L, pi);
        } catch (SecurityException se) {
            // 极少数 ROM 对精确闹钟做了限制，退化为宽松闹钟，至少不会彻底不响
            am.set(AlarmManager.RTC_WAKEUP, e.timeMillis, pi);
        }
    }

    static void cancel(Context ctx, Entry e) {
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (am != null) am.cancel(pending(ctx, e));
    }

    /** 全部重排：开机、App 启动、以及每次触发后调用，保证链条不断。 */
    public static void reschedule(Context ctx) {
        for (Entry e : load()) schedule(ctx, e);
    }

    static PendingIntent pending(Context ctx, Entry e) {
        Intent it = new Intent(ctx, ScheduledShowReceiver.class)
                .setAction(ACTION)
                .putExtra(EXTRA_ID, e.id)
                .putExtra(EXTRA_LINES, e.lines)
                .putExtra(EXTRA_NAME, e.name);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        return PendingIntent.getBroadcast(ctx, (int) (e.id & 0x7FFFFFFF), it, flags);
    }

    /** 列表展示用：11月5日 00:00（还有 3 天）。按自然日算，"今天下午 5 点"不会显示成"还有 1 天"。 */
    public static String describe(long timeMillis) {
        java.text.SimpleDateFormat fmt =
                new java.text.SimpleDateFormat("M月d日 HH:mm", java.util.Locale.CHINA);
        String when = fmt.format(new java.util.Date(timeMillis));
        java.util.Calendar today = java.util.Calendar.getInstance();
        java.util.Calendar target = java.util.Calendar.getInstance();
        target.setTimeInMillis(timeMillis);
        int days = daysBetween(today, target);
        if (days <= 0) return when + "（今天）";
        if (days == 1) return when + "（明天）";
        return when + "（还有 " + days + " 天）";
    }

    /** 两个时间点的自然日差（各自归零到当天 0 点再相减）。 */
    public static int daysBetween(long fromMillis, long toMillis) {
        java.util.Calendar a = java.util.Calendar.getInstance();
        java.util.Calendar b = java.util.Calendar.getInstance();
        a.setTimeInMillis(fromMillis);
        b.setTimeInMillis(toMillis);
        return daysBetween(a, b);
    }

    private static int daysBetween(java.util.Calendar from, java.util.Calendar to) {
        java.util.Calendar a = (java.util.Calendar) from.clone();
        java.util.Calendar b = (java.util.Calendar) to.clone();
        a.set(java.util.Calendar.HOUR_OF_DAY, 0);
        a.set(java.util.Calendar.MINUTE, 0);
        a.set(java.util.Calendar.SECOND, 0);
        a.set(java.util.Calendar.MILLISECOND, 0);
        b.set(java.util.Calendar.HOUR_OF_DAY, 0);
        b.set(java.util.Calendar.MINUTE, 0);
        b.set(java.util.Calendar.SECOND, 0);
        b.set(java.util.Calendar.MILLISECOND, 0);
        return (int) ((b.getTimeInMillis() - a.getTimeInMillis()) / 86_400_000L);
    }
}
