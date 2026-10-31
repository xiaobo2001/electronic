package com.xiaobo.electronic;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import java.util.List;

/**
 * 纪念日提醒的接收端：负责发通知，并把下一次提醒排回去。
 *
 * <p>同时也接 {@code BOOT_COMPLETED}：AlarmManager 的闹钟重启即失效，
 * 不重排的话用户重启手机后所有纪念日提醒都会静默消失——这是这类
 * "一年一次"提醒最容易踩的坑。
 */
public class AnniversaryReceiver extends BroadcastReceiver {

    private static final String CHANNEL_ID = "anniversary";
    static final String EXTRA_LINES = "anniv_lines";
    static final String EXTRA_TITLE = "anniv_title";

    @Override
    public void onReceive(Context c, Intent intent) {
        String action = intent == null ? null : intent.getAction();
        if (Intent.ACTION_BOOT_COMPLETED.equals(action)
                || Intent.ACTION_LOCKED_BOOT_COMPLETED.equals(action)
                || Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) {
            AnniversaryReminder.reschedule(c);
            return;
        }
        if (!AnniversaryReminder.ACTION_ALARM.equals(action)) return;

        // 触发后先排下一次（无论这次是否真的有纪念日到期），保证链条不断
        AnniversaryReminder.reschedule(c);

        int advance = AnniversaryReminder.getDays();
        List<AnniversaryStore.Entry> due =
                AnniversaryReminder.dueOn(AnniversaryStore.load(), advance);
        if (due.isEmpty()) return;

        notifyAnniversary(c, due, advance);
    }

    private void notifyAnniversary(Context c, List<AnniversaryStore.Entry> due, int advance) {
        NotificationManager nm =
                (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        ensureChannel(nm);

        for (int i = 0; i < due.size(); i++) {
            AnniversaryStore.Entry e = due.get(i);
            int years = yearsSince(e.date);
            String title = advance == 0
                    ? ("今天是 " + e.label + (years >= 1 ? " · 第 " + years + " 周年" : ""))
                    : ("还有 " + advance + " 天：" + e.label
                    + (years >= 1 ? " · 第 " + (years + 1) + " 周年" : ""));
            String text = advance == 0
                    ? "点开重演那一天的烟花与告白"
                    : AnniversaryStore.daysLabel(e.date) + "，点开可先重温一遍";

            Intent open = new Intent(c, MainActivity.class)
                    .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    .putExtra(EXTRA_LINES, e.lines)
                    .putExtra(EXTRA_TITLE, e.label);
            int flags = PendingIntent.FLAG_UPDATE_CURRENT;
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                flags |= PendingIntent.FLAG_IMMUTABLE;
            }
            PendingIntent pi = PendingIntent.getActivity(c, 1000 + i, open, flags);

            Notification n = new Notification.Builder(c, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_notification_heart)
                    .setContentTitle(title)
                    .setContentText(text)
                    .setContentIntent(pi)
                    .setAutoCancel(true)
                    .build();
            nm.notify(2000 + i, n);
        }
    }

    private static void ensureChannel(NotificationManager nm) {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.O) return;
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return;
        NotificationChannel ch = new NotificationChannel(CHANNEL_ID, "纪念日提醒",
                NotificationManager.IMPORTANCE_DEFAULT);
        ch.setDescription("在纪念日当天或提前几天提醒你，可点开重演那场演出");
        nm.createNotificationChannel(ch);
    }

    private static int yearsSince(String date) {
        try {
            String[] p = date.split("-");
            int y = Integer.parseInt(p[0].trim());
            return Math.max(0, java.util.Calendar.getInstance().get(java.util.Calendar.YEAR) - y);
        } catch (Exception e) {
            return 0;
        }
    }
}
