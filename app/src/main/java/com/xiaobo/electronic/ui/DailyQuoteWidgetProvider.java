package com.xiaobo.electronic.ui;

import android.content.Context;
import android.widget.RemoteViews;

import com.xiaobo.electronic.R;

/** 今日烟花签：按日期取一句浪漫话，点「放一朵」开烟花 */
public class DailyQuoteWidgetProvider extends BaseActionWidget {

    @Override
    int layoutRes() {
        return R.layout.widget_daily_quote;
    }

    @Override
    void bind(Context ctx, RemoteViews views) {
        views.setTextViewText(R.id.widget_quote, WidgetActions.dailyQuote(System.currentTimeMillis()));
    }

    @Override
    int[][] buttons() {
        return new int[][]{{R.id.widget_btn, 1}};  // 放一朵烟花
    }
}
