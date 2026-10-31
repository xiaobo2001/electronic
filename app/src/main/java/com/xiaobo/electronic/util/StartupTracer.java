package com.xiaobo.electronic.util;

import android.app.Activity;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Choreographer;
import android.widget.Toast;

import java.util.ArrayDeque;
import java.util.Deque;
import com.xiaobo.electronic.App;

/**
 * App 启动性能检测。
 *
 * 用法：
 *   StartupTracer tracer = StartupTracer.start();           // 在 Application.attachBaseContext / Activity.attach 调用
 *   tracer.begin("inflate"); ... tracer.end("inflate");
 *   tracer.trackFirstFrame(activity);                        // 首帧绘制完成后回调
 *
 * 结果统一通过 logcat tag=PerfTrace 输出，并在低端机首启时弹一次 Toast 提示档位与耗时。
 */
public final class StartupTracer {

    private static final String TAG = "PerfTrace";
    private static volatile StartupTracer instance;

    private final long processStartMs = System.currentTimeMillis();
    private final long processStartNanos = System.nanoTime();
    private final Deque<Section> stack = new ArrayDeque<>();
    private long firstFrameNanos = -1;
    private long frameCountAfterFirst = 0;
    private long jankFrames = 0;
    private long totalFrameNanos = 0;
    private boolean jankMonitoring = false;

    private StartupTracer() {}

    public static StartupTracer start() {
        if (instance == null) {
            synchronized (StartupTracer.class) {
                if (instance == null) {
                    instance = new StartupTracer();
                }
            }
        }
        return instance;
    }

    public static StartupTracer get() {
        return instance;
    }

    /** 埋点一段同步任务，配对 begin/end 使用 */
    public void begin(String label) {
        stack.push(new Section(label, System.nanoTime()));
    }

    public void end(String label) {
        Section s = stack.peek();
        if (s == null || !s.label.equals(label)) {
            Log.w(TAG, "unmatched end: " + label);
            return;
        }
        stack.pop();
        long costMs = (System.nanoTime() - s.startNanos) / 1_000_000;
        Log.i(TAG, "  ⏱ " + label + " = " + costMs + " ms");
    }

    /** 记录一个已经做完的命名步骤 */
    public void mark(String label, long costMs) {
        Log.i(TAG, "  ⏱ " + label + " = " + costMs + " ms");
    }

    /**
     * 跟踪首帧绘制，并在首屏后短暂采样帧率，统计掉帧情况。
     */
    public void trackFirstFrame(final Activity activity) {
        Choreographer.getInstance().postFrameCallback(new Choreographer.FrameCallback() {
            @Override
            public void doFrame(long frameTimeNanos) {
                if (firstFrameNanos < 0) {
                    firstFrameNanos = frameTimeNanos;
                    long toFirstFrame = (firstFrameNanos - processStartNanos) / 1_000_000;
                    Log.i(TAG, "🚀 first frame drawn in " + toFirstFrame + " ms");
                    // 首帧之后开始采样约 30 帧，统计 jank
                    startJankSampling(activity);
                }
            }
        });
    }

    private void startJankSampling(final Activity activity) {
        if (jankMonitoring) return;
        jankMonitoring = true;
        final long[] lastFrameNanos = {0};
        Choreographer.FrameCallback cb = new Choreographer.FrameCallback() {
            @Override
            public void doFrame(long frameTimeNanos) {
                if (lastFrameNanos[0] != 0) {
                    long delta = frameTimeNanos - lastFrameNanos[0];
                    totalFrameNanos += delta;
                    frameCountAfterFirst++;
                    // 超过 24ms（≈低于 42fps）记为一次卡顿
                    if (delta > 24_000_000L) {
                        jankFrames++;
                    }
                }
                lastFrameNanos[0] = frameTimeNanos;
                if (frameCountAfterFirst < 30) {
                    Choreographer.getInstance().postFrameCallback(this);
                } else {
                    reportJank(activity);
                }
            }
        };
        Choreographer.getInstance().postFrameCallback(cb);
    }

    private void reportJank(final Activity activity) {
        float avgFps = frameCountAfterFirst * 1_000_000_000f / (float) totalFrameNanos;
        Log.i(TAG, "📊 first 30 frames: avgFps=" + String.format("%.1f", avgFps)
                + ", jankFrames=" + jankFrames + "/" + frameCountAfterFirst);

        final DevicePerf perf = DevicePerf.get(activity);
        Log.i(TAG, "📱 " + perf.summary());

        // 启动耗时与档位信息仅输出 logcat（#10新：移除用户可见的调试 Toast）
        long total = (System.nanoTime() - processStartNanos) / 1_000_000;
        Log.i(TAG, "🚀 total " + total + "ms · tier " + perf.tier() + " · 首屏均帧 " + String.format("%.0f", avgFps) + "fps");
    }

    /** 供外部在关键路径手动记录耗时 */
    public long nowNanos() {
        return System.nanoTime();
    }

    public long elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }

    private static final class Section {
        final String label;
        final long startNanos;
        Section(String label, long startNanos) {
            this.label = label;
            this.startNanos = startNanos;
        }
    }
}
