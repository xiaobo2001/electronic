package com.xiaobo.electronic.show;

import android.os.Handler;
import android.os.SystemClock;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * 浪漫演出的调度器（导演）。
 *
 * <p>以前演出是"散落的 {@code mainHandler.postDelayed} 回调链"：每个回调自己再排队下一个，
 * 全靠 {@code isRomanticMode} 一个 boolean 兜底。这种写法有三个必然的后果——
 * <ul>
 *   <li>无法跳过：没有任何地方知道"下一步"是谁；</li>
 *   <li>无法暂停：只能整体 cancel，不能停在原地再续上；</li>
 *   <li>容易出环：停止逻辑和录制逻辑互相调用过，就出现 800ms 无限循环。</li>
 * </ul>
 *
 * <p>这里把"排队"这件事收口成一个可控对象：所有演出步骤都经 {@link #post} 登记，
 * 于是"下一步"变得可见，跳过就是立刻执行它，暂停就是把它连同剩余时间一起挂起。
 * 演出代码本身不用改，只把 {@code postRomanticCue} 的实现换成本类即可。
 */
public class RomanticShowDirector {

    /** 演出是否仍然有效（Activity 销毁或用户结束演出后应为 false） */
    public interface AliveCheck {
        boolean isShowAlive();
    }

    /** 暂停/恢复通知，用于同步暂停音乐与朗读 */
    public interface PauseListener {
        void onPauseStateChanged(boolean paused);
    }

    /** 一个演出步骤 */
    private static final class Cue {
        Runnable action;
        long delayMs;
        long dueAtMs;
        long remainMs;
        Runnable runner;
    }

    private static final Comparator<Cue> BY_DUE_TIME = new Comparator<Cue>() {
        @Override
        public int compare(Cue a, Cue b) {
            return Long.compare(a.dueAtMs, b.dueAtMs);
        }
    };

    private final Handler handler;
    private final AliveCheck aliveCheck;
    private final List<Cue> pending = new ArrayList<>();
    private PauseListener pauseListener;
    private boolean paused;

    public RomanticShowDirector(Handler handler, AliveCheck aliveCheck) {
        this.handler = handler;
        this.aliveCheck = aliveCheck;
    }

    public void setPauseListener(PauseListener listener) {
        this.pauseListener = listener;
    }

    /**
     * 登记一个演出步骤（替代原先的 postRomanticCue）。
     * 多个步骤可以并存（比如开场同时排了心跳与开场仪式），暂停中登记的步骤会被挂起。
     */
    public void post(Runnable action, long delayMs) {
        if (action == null) return;
        Cue cue = new Cue();
        cue.action = action;
        cue.delayMs = delayMs;
        pending.add(cue);
        if (paused) {
            cue.remainMs = delayMs;
            return;
        }
        schedule(cue, delayMs);
    }

    /**
     * 跳过：立刻执行当前所有等待中的步骤（按到期顺序）。
     *
     * <p>台词阶段通常只有"停留结束→爆开→下一句"这一个待办，所以效果就是跳过当前这句；
     * 开场阶段同时排了心跳与开场仪式，效果就是快进到开场。
     */
    public boolean skip() {
        if (pending.isEmpty()) return false;
        List<Cue> snapshot = new ArrayList<>(pending);
        Collections.sort(snapshot, BY_DUE_TIME);
        pending.clear();
        for (Cue cue : snapshot) {
            if (cue.runner != null) handler.removeCallbacks(cue.runner);
        }
        boolean ran = false;
        for (Cue cue : snapshot) {
            if (aliveCheck.isShowAlive()) {
                cue.action.run();
                ran = true;
            }
        }
        if (paused) {
            paused = false;
            notifyPause(false);
        }
        return ran;
    }

    /** 暂停：挂起所有等待中的步骤并记住剩余时间，画面停在当前这一步 */
    public void pause() {
        if (paused) return;
        long now = SystemClock.uptimeMillis();
        for (Cue cue : pending) {
            if (cue.runner != null) {
                handler.removeCallbacks(cue.runner);
                cue.runner = null;
            }
            cue.remainMs = Math.max(0L, cue.dueAtMs - now);
        }
        paused = true;
        notifyPause(true);
    }

    /** 恢复：按剩余时间重新投递 */
    public void resume() {
        if (!paused) return;
        paused = false;
        for (Cue cue : pending) {
            schedule(cue, cue.remainMs);
        }
        notifyPause(false);
    }

    public boolean togglePause() {
        if (paused) resume();
        else pause();
        return paused;
    }

    /** 清空所有排队步骤（结束演出时调用），并复位暂停状态以免影响下一场演出 */
    public void clear() {
        for (Cue cue : pending) {
            if (cue.runner != null) handler.removeCallbacks(cue.runner);
        }
        pending.clear();
        // 上一场若停在暂停态就结束，这里必须复位，否则下一场所有步骤会被挂起。
        // 不回调 PauseListener：音乐由 stopMusic() 统一处理，避免把已停的音乐又拉起来。
        paused = false;
    }

    public boolean isPaused() {
        return paused;
    }

    public boolean hasPending() {
        return !pending.isEmpty();
    }

    private void schedule(final Cue cue, long delayMs) {
        cue.delayMs = delayMs;
        cue.dueAtMs = SystemClock.uptimeMillis() + delayMs;
        cue.runner = new Runnable() {
            @Override
            public void run() {
                pending.remove(cue);
                if (aliveCheck.isShowAlive()) cue.action.run();
            }
        };
        handler.postDelayed(cue.runner, delayMs);
    }

    private void notifyPause(boolean paused) {
        if (pauseListener != null) pauseListener.onPauseStateChanged(paused);
    }
}
