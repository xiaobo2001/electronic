package com.xiaobo.electronic;

import com.xiaobo.fireworks.GlobalFirework;

/**
 * 演出模板：把"整场演出"打包成一个选项。
 *
 * <p>文案库解决的是"写什么"，模板解决的是"整场怎么搭"——
 * 主题、随机剧本、台词是要配合的：生日主题配新年文案、波浪扫射配求婚台词，
 * 怎么看都别扭。用户一个一个调四个开关太累，这里一次套好。
 *
 * <p>每套台词控制在 6~8 句：一句一行对应一朵文字烟花，太短撑不满一场，
 * 太长要演很久（上限与 {@link RomanticShareCodec#MAX_LINES} 一致）。
 */
public final class ShowTemplate {

    public final String emoji;
    public final String name;
    /** 一句话说明这套适合什么时候用 */
    public final String desc;
    /** 台词，\n 分句 */
    public final String lines;
    public final int theme;
    public final int pattern;

    ShowTemplate(String emoji, String name, String desc, String lines, int theme, int pattern) {
        this.emoji = emoji;
        this.name = name;
        this.desc = desc;
        this.lines = lines;
        this.theme = theme;
        this.pattern = pattern;
    }

    public static final ShowTemplate[] ALL = {
            new ShowTemplate("💗", "深情告白",
                    "第一次说出口 · 波浪式连绵绽放",
                    "有些话藏在心里很久了\n今天想借这片烟花\n说给你听\n我喜欢你\n不是一时兴起\n是攒了很久的认真",
                    ThemeManager.THEME_DEFAULT, GlobalFirework.PATTERN_WAVE),

            new ShowTemplate("💍", "求婚时刻",
                    "单膝跪地那一刻 · 烟花向中心聚拢",
                    "我想过很多种未来\n每一种里面都有你\n你愿意\n嫁给我吗\n让我用余生\n把这句话\n再证明一遍",
                    ThemeManager.THEME_DEFAULT, GlobalFirework.PATTERN_CONVERGE),

            new ShowTemplate("🎂", "生日惊喜",
                    "生日主题烟花 · 蛋糕与蜡烛",
                    "生日快乐\n愿你所有的愿望\n都能实现\n愿你被这个世界\n温柔以待\n而我\n会一直站在你这边",
                    ThemeManager.THEME_BIRTHDAY, GlobalFirework.PATTERN_CLASSIC),

            new ShowTemplate("🧧", "新年烟花",
                    "春节主题烟花 · 福字与红包",
                    "新年快乐\n烟花起 好运来\n愿新的一年\n你我都在彼此身边\n阖家安康\n万事如意\n一起看更多烟花",
                    ThemeManager.THEME_SPRING, GlobalFirework.PATTERN_WAVE),
    };

    /** 菜单/列表用的一行标题 */
    public static String[] titles() {
        String[] out = new String[ALL.length];
        for (int i = 0; i < ALL.length; i++) {
            out[i] = ALL[i].emoji + " " + ALL[i].name + "\n" + ALL[i].desc;
        }
        return out;
    }
}
