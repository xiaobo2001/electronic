package com.xiaobo.electronic.show;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import com.xiaobo.electronic.ui.MainActivity;
import com.xiaobo.electronic.R;

/**
 * 定时演出的到点处理：发一条能直接全屏开演的通知。
 *
 * <p>为什么不直接 startActivity：Android 10 之后后台启动 Activity 会被系统拦下
 * （页面不弹、只在日志里留一条），用户看到的是"定时没生效"。
 * 全屏意图（fullScreenIntent）是系统留给闹钟、来电这类场景的正规通道：
 * 亮屏时自动全屏展开，息屏时留一条通知，点一下同样直接开演。
 */
public class ScheduledShowReceiver extends BroadcastReceiver {

    private static final String CHANNEL_ID = "scheduled_show";

    @Override
    public void onReceive(Context c, Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        if (Intent.ACTION_BOOT_COMPLETED.equals(action)
                || Intent.ACTION_LOCKED_BOOT_COMPLETED.equals(action)
                || Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) {
            com.tencent.mmkv.MMKV.initialize(c);
            ScheduledShow.reschedule(c);
            return;
        }
        if (!ScheduledShow.ACTION.equals(action)) return;

        long id = intent.getLongExtra(ScheduledShow.EXTRA_ID, 0L);
        String lines = intent.getStringExtra(ScheduledShow.EXTRA_LINES);
        String name = intent.getStringExtra(ScheduledShow.EXTRA_NAME);

        // 一次性演出：响了就出列，再重排剩下的
        ScheduledShow.remove(c, id);
        ScheduledShow.reschedule(c);

        notifyShow(c, name, lines);
    }

    private void notifyShow(Context c, String name, String lines) {
        NotificationManager nm =
                (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        ensureChannel(nm);

        Intent open = new Intent(c, MainActivity.class)
                .setAction(ScheduledShow.ACTION)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP
                        | Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(ScheduledShow.EXTRA_LINES, lines == null ? "" : lines)
                .putExtra(ScheduledShow.EXTRA_NAME, name == null ? "定时演出" : name);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        PendingIntent pi = PendingIntent.getActivity(c, 3000, open, flags);

        Notification n = new Notification.Builder(c, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification_heart)
                .setContentTitle("⏰ " + (name == null ? "定时演出" : name))
                .setContentText("时间到了，点开为她放一场烟花")
                .setContentIntent(pi)
                .setFullScreenIntent(pi, true)
                .setCategory(Notification.CATEGORY_ALARM)
                .setAutoCancel(true)
                .setPriority(Notification.PRIORITY_MAX)
                .build();
        nm.notify(3001, n);
    }

    private static void ensureChannel(NotificationManager nm) {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.O) return;
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return;
        NotificationChannel ch = new NotificationChannel(CHANNEL_ID, "定时演出",
                NotificationManager.IMPORTANCE_HIGH);
        ch.setDescription("到点自动提醒并开演，可全屏展开");
        nm.createNotificationChannel(ch);
    }
}
