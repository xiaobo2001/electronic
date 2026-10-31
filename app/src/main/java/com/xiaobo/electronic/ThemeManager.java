package com.xiaobo.electronic;

import android.graphics.Color;

import com.xiaobo.fireworks.EmojiBitmapCache;
import com.xiaobo.fireworks.FireworkConfig;
import com.xiaobo.fireworks.SkyView;

/**
 * 【#17】节日主题包：一键切换背景色 / 天气 / 专属图形烟花池 / 台词预设。
 * 主题定义纯数据，应用逻辑在 MainActivity.applyTheme()。
 */
public class ThemeManager {

    public static final int THEME_DEFAULT = 0;
    public static final int THEME_SPRING = 1;   // 春节
    public static final int THEME_BIRTHDAY = 2; // 生日
    public static final int THEME_CHRISTMAS = 3;// 圣诞

    public static final String[] NAMES = {"默认夜空", "春节 🧧", "生日 🎂", "圣诞 🎄"};

    /** 各主题烟花池的样式名（与 fireworkPool 返回顺序一致，供样式开关显示） */
    public static final String[][] THEME_POOL_NAMES = {
            {},
            {"福字烟花", "红金圆", "红包🧧", "火箭金"},
            {"蛋糕🎂", "生日快乐字", "气球🎈"},
            {"圣诞树🎄", "雪花", "Merry Xmas字"}
    };

    public static String name(int theme) {
        return NAMES[theme];
    }

    /** 主题背景色 */
    public static int bgColor(int theme) {
        switch (theme) {
            case THEME_SPRING: return Color.rgb(60, 8, 8);     // 深红
            case THEME_BIRTHDAY: return Color.rgb(40, 8, 45);  // 深紫粉
            case THEME_CHRISTMAS: return Color.rgb(6, 22, 16);  // 墨绿
            default: return Color.BLACK;
        }
    }

    /** 主题天气 */
    public static int weather(int theme) {
        switch (theme) {
            case THEME_CHRISTMAS: return SkyView.WEATHER_SNOW;
            case THEME_BIRTHDAY: return SkyView.WEATHER_SAKURA;
            default: return SkyView.WEATHER_NONE;
        }
    }

    /** 主题专属烟花池（图形用库 EmojiBitmapCache 统一缓存） */
    public static FireworkConfig[] fireworkPool(int theme, int count, String customText) {
        switch (theme) {
            case THEME_SPRING: return new FireworkConfig[]{
                    // 福
                    new FireworkConfig.Builder()
                            .shape(FireworkConfig.Shape.TEXT).text("福")
                            .colors(0xFFFFD700, 0xFFFF2D2D).count(count).range(1.2f).textSize(110f).build(),
                    // 红金圆
                    new FireworkConfig.Builder()
                            .colors(0xFFFF2D2D, 0xFFFFD700).count(count).range(1.2f).trail(1.5f).build(),
                    // 红包 🧧
                    new FireworkConfig.Builder()
                            .shape(FireworkConfig.Shape.CUSTOM)
                            .shapeBitmap(null) // 由 MainActivity 注入（需要 Context）
                            .colors(0xFFFF2D2D, 0xFFFFD700).count(count).range(1.2f).build(),
                    // 火箭金
                    new FireworkConfig.Builder()
                            .colors(0xFFFFD700, 0xFFFF2D2D).count(count).range(1.2f).trail(1.6f).rocket(true).build(),
            };
            case THEME_BIRTHDAY: return new FireworkConfig[]{
                    new FireworkConfig.Builder()
                            .shape(FireworkConfig.Shape.CUSTOM)
                            .shapeBitmap(null)
                            .colors(0xFFFFD700, 0xFFFF69B4).count(count).range(1.2f).build(),
                    new FireworkConfig.Builder()
                            .shape(FireworkConfig.Shape.TEXT).text("生日快乐")
                            .colors(0xFFFF69B4, 0xFFFFD700).count(count).range(1.2f).textSize(100f).build(),
                    new FireworkConfig.Builder()
                            .shape(FireworkConfig.Shape.CUSTOM)
                            .shapeBitmap(null)
                            .colors(0xFF7FD4FF, 0xFFB6E5FF).count(count).range(1.2f).build(),
            };
            case THEME_CHRISTMAS: return new FireworkConfig[]{
                    new FireworkConfig.Builder()
                            .shape(FireworkConfig.Shape.CUSTOM)
                            .shapeBitmap(null)
                            .colors(0xFF2ECC71, 0xFFFF4444).count(count).range(1.2f).build(),
                    new FireworkConfig.Builder()
                            .colors(0xFF7FD4FF, Color.WHITE).count(count).range(1.3f).trail(0.8f).build(),
                    new FireworkConfig.Builder()
                            .shape(FireworkConfig.Shape.TEXT).text("Merry Xmas")
                            .colors(0xFF2ECC71, 0xFFFFFFFF).count(count).range(1.2f).textSize(90f).build(),
            };
            default: return null; // 默认主题用 App 原生十样式池
        }
    }

    /** 各主题的图形 emoji（与 fireworkPool 顺序对应，shapeBitmap(null) 的槽位在此顺序注入） */
    public static String[] poolEmojis(int theme) {
        switch (theme) {
            // 下标必须与 fireworkPool(THEME_SPRING) 的槽位一一对应：
            // [0]=福字(TEXT) [1]=红金圆(CIRCLE) [2]=红包(CUSTOM) [3]=火箭金(CIRCLE)
            // 此前 🧧 放在下标 0，红包图形被塞给了"福"字烟花，而真正的红包位仍是
            // shapeBitmap=null 的 CUSTOM（渲染空白或崩溃）
            case THEME_SPRING: return new String[]{null, null, "🧧", null};
            case THEME_BIRTHDAY: return new String[]{"🎂", null, "🎈", null};
            case THEME_CHRISTMAS: return new String[]{"🎄", null, null, null};
            default: return null;
        }
    }

    /** 主题告白台词预设 */
    public static String romanticLines(int theme) {
        switch (theme) {
            case THEME_SPRING: return "新的一年\n愿与你岁岁常相见";
            case THEME_BIRTHDAY: return "生日快乐\n愿你所有愿望都实现";
            case THEME_CHRISTMAS: return "圣诞快乐\n平安喜乐 与你有关";
            default: return "小美，遇见你真好\n往后余生，请多指教\n朝暮四季，我都在旁\n柴米烟火，和你共享\n风雨来时，我来抵挡\n平凡日常，慢慢珍藏\n所有温柔，尽数予你\n心有所属，便是远方\n不问喧嚣，只守身旁\n岁岁年年，不负相逢";
        }
    }

    /** 供注入 emoji 位图的快捷方法 */
    public static FireworkConfig withEmoji(android.content.Context ctx, FireworkConfig cfg, String emoji) {
        if (emoji != null) {
            cfg.shapeBitmap = EmojiBitmapCache.get(ctx, emoji, 220);
        }
        return cfg;
    }
}
