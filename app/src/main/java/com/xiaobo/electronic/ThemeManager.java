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
            case THEME_SPRING: return new String[]{"🧧", null, null, null};
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
            default: return "小美 遇见你真好\n往后余生，请多指教";
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
