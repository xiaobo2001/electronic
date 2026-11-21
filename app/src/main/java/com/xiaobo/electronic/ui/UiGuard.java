package com.xiaobo.electronic.ui;

import android.app.Activity;
import android.content.Context;
import android.view.View;

import androidx.appcompat.app.AlertDialog;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/**
 * UI 细节守卫：把两类"桌面上撞不到、真机上天天发生"的问题收口到一处。
 *
 * <p><b>1. 弹窗时机。</b>Activity 已经 finishing / destroyed 之后再 show()，
 * 系统会抛 {@code BadTokenException: Unable to add window -- token is invalid}。
 * 它的触发时序很刁钻——用户刚按下返回键、手又快点了一下菜单，
 * 或者后台通知把 Activity 拉起来又立刻回收。测试时几乎复现不了，但线上实打实会崩。
 *
 * <p><b>2. 连点。</b>同一项在 600ms 内被点两次，会叠出两层弹窗；
 * 录屏、导出、开演这类重操作被触发两次更麻烦（重复写文件、重复排闹钟）。
 */
public final class UiGuard {

    /** 连点判定窗口：小于它算手抖，大于它才算用户真想再点一次 */
    private static final long TAP_WINDOW_MS = 600L;

    private static long lastTapMs = 0L;

    private UiGuard() {
    }

    /** 还活着的弹窗（弱引用，不延长它们的生命周期） */
    private static final java.util.List<java.lang.ref.WeakReference<android.app.Dialog>> LIVE =
            new java.util.ArrayList<>();

    /** 记一笔，供 {@link #dismissAll()} 收尾 */
    private static void track(android.app.Dialog d) {
        if (d == null) return;
        synchronized (LIVE) {
            LIVE.removeIf(r -> r.get() == null);
            LIVE.add(new java.lang.ref.WeakReference<>(d));
        }
    }

    /**
     * Activity 销毁时收掉所有还开着的弹窗。
     *
     * <p>不做的话会抛 {@code WindowLeaked: Activity has leaked window}——
     * 弹窗是挂在 Activity 的 window 上的，Activity 没了它还开着，系统就会报错并打日志。
     * 演出中途按返回退出、或者定时演出把 Activity 拉起来又回收，都容易撞上。
     */
    public static void dismissAll() {
        synchronized (LIVE) {
            for (java.lang.ref.WeakReference<android.app.Dialog> r : LIVE) {
                android.app.Dialog d = r.get();
                if (d == null) continue;
                try {
                    if (d.isShowing()) d.dismiss();
                } catch (Throwable ignored) {
                    // 已经销毁的 window 上 dismiss 会抛，忽略
                }
            }
            LIVE.clear();
        }
    }

    /** 这个 Context 现在还能不能安全地弹窗 */
    public static boolean canShow(Context ctx) {
        if (ctx == null) return false;
        if (ctx instanceof Activity) {
            Activity a = (Activity) ctx;
            return !a.isFinishing() && !a.isDestroyed();
        }
        return true;
    }

    /** 内容区之外还要留给标题栏、底部按钮栏和窗口边距的高度（dp） */
    private static final int BODY_RESERVE_DP = 168;

    /** 内容区最低也要留这么多（dp），压太狠等于把字全藏起来 */
    private static final int BODY_MIN_DP = 140;

    /**
     * 把弹窗的内容区压到"屏幕装得下"的高度，让里面的 ScrollView 真正能滚。
     *
     * <p>这个 App 固定横屏，可视高度只有 360dp 上下；而 AlertDialog 给自定义内容的
     * 区域是 {@code wrap_content}，外层不会自己收缩——内容一高，整个窗口就比屏幕还高，
     * 底部那排按钮直接落在屏幕外。用户看到的就是"弹窗没有确认/取消按钮"，
     * 而且因为窗口比屏幕高，里面的 ScrollView 还以为自己空间够，怎么划都不动。
     *
     * <p>压到 {@code 屏高 - 标题/按钮预留} 之后：窗口回到屏幕内、内容可以滚、按钮可见。
     */
    public static void limitBodyHeight(View body) {
        if (body == null) return;
        clampBody(body, 0);
    }

    private static void clampBody(View body, int attempt) {
        body.post(() -> {
            try {
                if (body.getHeight() <= 0) {
                    // 还没测量完，再等一帧；最多重试 3 次，别无限 post
                    if (attempt < 3) body.postDelayed(() -> clampBody(body, attempt + 1), 60);
                    return;
                }
                android.util.DisplayMetrics dm = body.getResources().getDisplayMetrics();
                int max = dm.heightPixels - (int) (BODY_RESERVE_DP * dm.density);
                int floor = (int) (BODY_MIN_DP * dm.density);
                if (max < floor) max = floor;
                if (body.getHeight() <= max) return;
                android.view.ViewGroup.LayoutParams lp = body.getLayoutParams();
                if (lp == null) return;
                lp.height = max;
                body.setLayoutParams(lp);
            } catch (Throwable ignored) {
                // 高度压不下来也不该把弹窗搞崩，保持原样即可
            }
        });
    }

    /** 判定一次点击是否有效（顺便记下时间），无效说明是连点，调用方直接丢弃 */
    public static boolean tap() {
        long now = android.os.SystemClock.elapsedRealtime();
        if (now - lastTapMs < TAP_WINDOW_MS) return false;
        lastTapMs = now;
        return true;
    }

    /**
     * 清掉连点计时，让下一次点击必定生效。
     *
     * <p>防连点是全局的：点「更多」开菜单本身也算一次点击并记下时间。
     * 用户手快时会在 600ms 内接着点菜单项，这一次会被判定为手抖直接丢弃——
     * 表现为"点了没反应"，且因为弹窗根本没弹出来，用户只会以为是这个功能坏了。
     * 菜单展开时调一次，保证展开后的第一下一定算数。
     */
    public static void resetTap() {
        lastTapMs = 0L;
    }

    /** 带防连点的点击绑定 */
    public static void onClick(View v, Runnable action) {
        if (v == null || action == null) return;
        v.setOnClickListener(x -> {
            if (tap()) action.run();
        });
    }

    /**
     * 让弹窗里的输入框一打开就能直接打字。
     *
     * <p>不做的话用户得多点一下输入框才弹得出版键盘——尤其是「自定义文字」
     * 这种打开就是为了输入的弹窗，等于每次都浪费一次点击。
     *
     * <p>关键在 {@code FLAG_ALT_FOCUSABLE_IM}：AlertDialog 的 window 默认带这个标记，
     * 它会挡住输入法，只 requestFocus 是弹不出键盘的，必须先清掉。
     */
    public static void focusInput(android.app.Dialog dlg, View input) {
        if (dlg == null || input == null) return;
        android.view.Window w = dlg.getWindow();
        if (w != null) {
            w.clearFlags(android.view.WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);
        }
        input.post(() -> {
            input.requestFocus();
            android.view.inputmethod.InputMethodManager imm =
                    (android.view.inputmethod.InputMethodManager) input.getContext()
                            .getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.showSoftInput(input, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
            }
        });
    }

    /**
     * 安全版 {@link MaterialAlertDialogBuilder}。
     *
     * <p>关键点：Activity 即将销毁时是 {@code create()} 而不是 {@code show()}——
     * 用 create() 既不抛异常，返回的还是非空对象，
     * 调用方后面那句 {@code dlg.dismiss()} 就不会 NPE。
     * 直接 return null 会把崩溃从一个地方搬到另一个地方。
     */
    public static class SafeBuilder extends MaterialAlertDialogBuilder {

        public SafeBuilder(Context ctx) {
            super(ctx);
        }

        @Override
        public AlertDialog show() {
            AlertDialog d;
            if (!canShow(getContext())) {
                try {
                    d = create();
                } catch (Throwable t) {
                    // 【修复】静默吞异常=用户眼里就是"点了没反应"，至少留下线索
                    android.util.Log.w("UiGuard", "dialog create failed", t);
                    return null;
                }
            } else {
                try {
                    d = super.show();
                } catch (Throwable t) {
                    android.util.Log.w("UiGuard", "dialog show failed", t);
                    return null;
                }
            }
            track(d);
            // 【统一修复】内容区限高：横屏可视高度只有 360dp 上下，不限高的话
            // 弹窗会比屏幕还高，底部的「确定 / 取消」直接被裁到屏幕外。
            // mtrl_alert_dialog 自带一个 NestedScrollView 包住标题下的内容，
            // 压住它就能让内容滚起来、窗口回到屏幕内。
            limitBodyHeight(d.findViewById(com.google.android.material.R.id.scrollView));
            return d;
        }
    }
}
