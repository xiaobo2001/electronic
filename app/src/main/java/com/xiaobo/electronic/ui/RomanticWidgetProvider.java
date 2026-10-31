package com.xiaobo.electronic.ui;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;
import com.xiaobo.electronic.R;

/**
 * 【#19】桌面小组件：心形按钮一键进入浪漫模式。
 */
public class RomanticWidgetProvider extends AppWidgetProvider {

    public static final String ACTION_ROMANTIC = "com.xiaobo.electronic.ACTION_ROMANTIC";

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        for (int appWidgetId : appWidgetIds) {
            RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_romantic);
            Intent intent = new Intent(context, MainActivity.class);
            intent.setAction(ACTION_ROMANTIC);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            views.setOnClickPendingIntent(R.id.widget_btn,
                    android.app.PendingIntent.getActivity(context, 0, intent,
                            android.app.PendingIntent.FLAG_UPDATE_CURRENT | android.app.PendingIntent.FLAG_IMMUTABLE));
            appWidgetManager.updateAppWidget(appWidgetId, views);
        }
    }
}
