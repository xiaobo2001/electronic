package com.xiaobo.electronic.data;

import java.util.Arrays;
import java.util.List;

/**
 * 默契问答题库（暖场互动）。
 *
 * <p>告白前让对方答几道"你懂不懂我"的小题——题目故意都不是真考知识，
 * 选项怎么选都很甜，重点是给她一个参与进来的台阶，把气氛从"看表演"
 * 变成"我们在玩"。答完按答对数量触发不同热闹程度的专属烟花。
 *
 * <p>题目和答案都可自定义（调用方传入 herName 拼进题干），纯本地数据。
 */
public final class QuizData {

    private QuizData() {
    }

    public static final class Question {
        public final String text;
        public final String[] options;
        /** 正确选项下标（0-based）；暖场题答错也无妨，只是烟花少一点 */
        public final int answer;

        public Question(String text, String[] options, int answer) {
            this.text = text;
            this.options = options;
            this.answer = answer;
        }
    }

    /**
     * 生成一套题。herName 为空时用"她"。
     * 题干四两拨千斤，答案都藏在"爱她"这件事里。
     */
    public static List<Question> make(String herName) {
        String she = (herName == null || herName.trim().isEmpty()) ? "她" : herName.trim();
        return Arrays.asList(
                new Question("第一次见 " + she + " 的时候，我最先注意到的是？",
                        new String[]{"她的眼睛", "她的笑容", "她说的话", "以上都是"}, 3),
                new Question(she + " 心情不好时，我第一件该做的事是？",
                        new String[]{"讲道理", "陪着她", "买礼物", "给她空间"}, 1),
                new Question("我们之间，我最想说却总没说出口的是？",
                        new String[]{"谢谢你", "对不起", "我爱你", "下次见"}, 2),
                new Question("如果用一个词形容 " + she + " 在我心里的位置？",
                        new String[]{"特别", "唯一", "重要", "无可替代"}, 3)
        );
    }

    /** 答对几题给几句评语——不打击人，答错也是"还要继续了解" */
    public static String verdict(int correct, int total) {
        if (correct == total) return "心有灵犀，全对！这份默契，值得一场烟花。";
        if (correct >= total / 2) return "很有默契了！剩下的，用余生慢慢答对。";
        return "没关系，答案就是——余生都是你。";
    }

    /** 答对数量决定专属烟花的档次：越多越热闹 */
    public static int fireworksFor(int correct) {
        return 4 + correct * 3;   // 4~16 发
    }
}
