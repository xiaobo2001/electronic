package com.xiaobo.electronic.ui;

import android.content.Context;
import android.widget.RemoteViews;

import com.xiaobo.electronic.R;

/** 心境快捷面板：烟花 / 告白 / 孔明灯三个直达按钮 */
public class QuickActionsWidgetProvider extends BaseActionWidget {

    @Override
    int layoutRes() {
        return R.layout.widget_quick_actions;
    }

    @Override
    void bind(Context ctx, RemoteViews views) {
        // 纯按钮面板，无动态内容
    }

    @Override
    int[][] buttons() {
        return new int[][]{
                {R.id.widget_firework, 1},  // 烟花
                {R.id.widget_romantic, 2},  // 告白
                {R.id.widget_lantern, 3},   // 孔明灯
        };
    }
}
