package com.xiaobo.electronic;

import android.app.Application;
import android.view.Gravity;

import com.hjq.toast.Toaster;
import com.hjq.toast.style.CustomToastStyle;
import com.xiaobo.electronic.data.AnniversaryReceiver;
import com.xiaobo.electronic.data.AnniversaryReminder;
import com.xiaobo.electronic.util.CrashLogger;
import com.xiaobo.electronic.show.ScheduledShow;
import com.xiaobo.electronic.show.ScheduledShowReceiver;

/**
 * 应用入口。
 *
 * <p>目前只负责初始化 Toast 框架：项目里有 49 处提示用到了系统 Toast，
 * 原生黑块样式和夜空浪漫主题严重割裂，这里统一换成全局自定义样式
 * （{@code R.layout.toast_romantic}，夜空渐变 + 金边胶囊）。
 * Toaster 还顺带解决了三个原生 Toast 的老问题：Android 7.1 WindowToken 崩溃、
 * 通知栏权限被关闭时 Toast 不显示、以及子线程调用需要 Looper。
 */
public class App extends Application {

    @Override
    public void onCreate() {
        super.onCreate();

        // 横屏应用：Toast 落在底部居中，yOffset 抬高以避开底栏与悬浮停止键
        int yOffset = (int) (72 * getResources().getDisplayMetrics().density);
        Toaster.init(this, new CustomToastStyle(
                R.layout.toast_romantic,
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL,
                0,
                yOffset));

        // 崩溃现场留存到手机：这个 App 常用于表白、求婚，失败一次就永久失去用户，
        // 而没有监控就永远不知道它崩在哪。用户可在菜单里一键把日志发给我。
        CrashLogger.install(this);

        // 纪念日提醒：App 每次启动都补排一次闹钟（开机由 AnniversaryReceiver 负责），
        // 避免"装完还没重启过手机"这段时间内提醒不生效。
        com.tencent.mmkv.MMKV.initialize(this);
        AnniversaryReminder.reschedule(this);
        // 定时演出同样靠 AlarmManager：开机重排由 ScheduledShowReceiver 负责，
        // 这里补的是"装完还没重启过手机"这一段的重排。
        ScheduledShow.reschedule(this);
    }
}
