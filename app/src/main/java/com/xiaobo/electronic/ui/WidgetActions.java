package com.xiaobo.electronic.ui;

/** 桌面小组件发往 MainActivity 的 action 集合 */
public final class WidgetActions {

    private WidgetActions() {
    }

    public static final String FIREWORK = "com.xiaobo.electronic.ACTION_WIDGET_FIREWORK";
    public static final String ROMANTIC = "com.xiaobo.electronic.ACTION_WIDGET_ROMANTIC";
    public static final String LANTERN = "com.xiaobo.electronic.ACTION_WIDGET_LANTERN";

    /** 今日烟花签：按当天日期取一句话，同一天稳定不跳 */
    private static final String[] QUOTES = {
            "遇见你，是今晚最好的烟花。",
            "想把整个夜空，都点亮给你看。",
            "你笑一下，星星就都亮了。",
            "这一刻，只为你说。",
            "往后余生，请多指教。",
            "所有的浪漫，都想和你分享。",
            "风很轻，夜很静，我很想你。",
            "有你在的地方，就是最好的风景。",
            "愿每年的今天，都有烟花和你。",
    };

    public static String dailyQuote(long nowMillis) {
        int day = (int) (nowMillis / 86400000L);
        return QUOTES[Math.abs(day) % QUOTES.length];
    }
}
