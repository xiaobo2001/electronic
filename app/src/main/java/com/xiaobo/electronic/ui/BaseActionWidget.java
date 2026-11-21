package com.xiaobo.electronic.ui;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

import com.tencent.mmkv.MMKV;

/** 小组件基类：封装"点按钮带 action 打开 MainActivity"的公共逻辑 */
abstract class BaseActionWidget extends AppWidgetProvider {

    /** 返回本组件的布局 */
    abstract int layoutRes();

    /** 给具体组件填充内容（倒计时、文案等） */
    abstract void bind(Context ctx, RemoteViews views);

    /** 绑定每个按钮：viewId → action */
    abstract int[][] buttons();

    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        MMKV.initialize(context);
        for (int id : ids) {
            RemoteViews views = new RemoteViews(context.getPackageName(), layoutRes());
            bind(context, views);
            for (int[] pair : buttons()) {
                Intent intent = new Intent(context, MainActivity.class);
                intent.setAction(pair[1] == 0 ? RomanticWidgetProvider.ACTION_ROMANTIC
                        : actionOf(pair[1]));
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                PendingIntent pi = PendingIntent.getActivity(context, pair[0], intent,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                views.setOnClickPendingIntent(pair[0], pi);
            }
            manager.updateAppWidget(id, views);
        }
    }

    /** buttons() 里的 action 编码：1=烟花 2=告白 3=孔明灯（0=沿用老的一键浪漫） */
    private static String actionOf(int code) {
        switch (code) {
            case 1:
                return WidgetActions.FIREWORK;
            case 2:
                return WidgetActions.ROMANTIC;
            case 3:
                return WidgetActions.LANTERN;
            default:
                return RomanticWidgetProvider.ACTION_ROMANTIC;
        }
    }
}
