package com.xiaobo.electronic.ui;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

import com.tencent.mmkv.MMKV;
import com.xiaobo.electronic.R;
import com.xiaobo.electronic.data.AnniversaryCountdown;
import com.xiaobo.electronic.data.AnniversaryStore;

/**
 * 基础小组件：一行纪念日倒计时 + 一个「放一场烟花」按钮。
 *
 * <p>倒计时算法在 {@link AnniversaryCountdown}，与其它小组件共用。
 * AppWidget 可能在独立进程被系统拉起，所以这里必须自己初始化 MMKV。
 */
public class RomanticWidgetProvider extends AppWidgetProvider {

    public static final String ACTION_ROMANTIC = "com.xiaobo.electronic.ACTION_ROMANTIC";

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        MMKV.initialize(context);
        String countdown = AnniversaryCountdown.oneLine(
                AnniversaryStore.load(), System.currentTimeMillis());
        for (int appWidgetId : appWidgetIds) {
            RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_romantic);
            Intent intent = new Intent(context, MainActivity.class);
            intent.setAction(ACTION_ROMANTIC);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            views.setOnClickPendingIntent(R.id.widget_btn,
                    android.app.PendingIntent.getActivity(context, 0, intent,
                            android.app.PendingIntent.FLAG_UPDATE_CURRENT | android.app.PendingIntent.FLAG_IMMUTABLE));
            views.setTextViewText(R.id.widget_countdown,
                    countdown == null ? "💗 想你，就放场烟花" : "💗 " + countdown);
            appWidgetManager.updateAppWidget(appWidgetId, views);
        }
    }
}
