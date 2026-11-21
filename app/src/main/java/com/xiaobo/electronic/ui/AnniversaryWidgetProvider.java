package com.xiaobo.electronic.ui;

import android.content.Context;
import android.widget.RemoteViews;

import com.xiaobo.electronic.R;
import com.xiaobo.electronic.data.AnniversaryCountdown;
import com.xiaobo.electronic.data.AnniversaryStore;

/** 纪念日大号小组件：大号天数 + 标题，一眼看完 */
public class AnniversaryWidgetProvider extends BaseActionWidget {

    @Override
    int layoutRes() {
        return R.layout.widget_anniversary;
    }

    @Override
    void bind(Context ctx, RemoteViews views) {
        java.util.List<AnniversaryStore.Entry> entries = AnniversaryStore.load();
        long now = System.currentTimeMillis();
        int days = AnniversaryCountdown.days(entries, now);
        if (days < 0) {
            views.setTextViewText(R.id.widget_days, "∞");
            views.setTextViewText(R.id.widget_days_label, "还没有纪念日");
            views.setTextViewText(R.id.widget_name, "告白成功后会自动记下");
        } else {
            views.setTextViewText(R.id.widget_days, String.valueOf(days));
            views.setTextViewText(R.id.widget_days_label, days == 0 ? "就是今天 💗" : "天");
            views.setTextViewText(R.id.widget_name, AnniversaryCountdown.label(entries));
        }
    }

    @Override
    int[][] buttons() {
        return new int[][]{{R.id.widget_btn, 0}};
    }
}
