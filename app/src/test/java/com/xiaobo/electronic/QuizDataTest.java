package com.xiaobo.electronic;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.xiaobo.electronic.data.QuizData;

import org.junit.Test;

import java.util.List;

/** 默契问答：题库自洽与计分/烟花档位 */
public class QuizDataTest {

    @Test
    public void questions_wellFormed() {
        List<QuizData.Question> qs = QuizData.make("小美");
        assertEquals(4, qs.size());
        for (QuizData.Question q : qs) {
            assertTrue("题目不该为空", q.text != null && !q.text.isEmpty());
            assertTrue("选项至少 2 个", q.options.length >= 2);
            assertTrue("答案下标越界", q.answer >= 0 && q.answer < q.options.length);
            for (String opt : q.options) {
                assertTrue("选项不该为空", opt != null && !opt.isEmpty());
            }
        }
    }

    @Test
    public void defaultName_fallsBackToHer() {
        List<QuizData.Question> qs = QuizData.make(null);
        assertTrue(qs.get(0).text.contains("她"));
    }

    @Test
    public void verdict_threeLevels() {
        assertTrue(QuizData.verdict(4, 4).contains("心有灵犀"));
        assertTrue(QuizData.verdict(2, 4).contains("默契"));
        assertTrue(QuizData.verdict(0, 4).contains("余生"));
    }

    @Test
    public void fireworks_increasesWithCorrect() {
        assertTrue(QuizData.fireworksFor(4) > QuizData.fireworksFor(0));
        assertTrue(QuizData.fireworksFor(0) >= 4);
        assertTrue(QuizData.fireworksFor(4) <= 16);
    }
}
