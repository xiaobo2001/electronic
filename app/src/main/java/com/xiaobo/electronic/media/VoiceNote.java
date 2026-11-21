package com.xiaobo.electronic.media;

import android.content.Context;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.os.Handler;
import android.os.Looper;

import java.io.File;
import java.io.IOException;

/**
 * 真人语音留声：录一段自己的告白原声，演出时替代 TTS 念出来。
 *
 * <p><b>为什么要这个。</b>合成音再自然也是机器，她想听的是"你的声音"。
 * 录一句"我爱你，余生请多指教"，演出时在台词阶段原声播放——
 * 声音本身就成了告白的一部分。全程存本机私有目录，不上传。
 *
 * <p>录音上限 30 秒（防止误触录出超长文件占空间），M4A/AAC 编码，
 * 单个文件约 1MB 以内。重录直接覆盖同一个文件。
 */
public final class VoiceNote {

    private static final String FILE_NAME = "voice_note.m4a";
    private static final int MAX_DURATION_MS = 30_000;

    /** 录音时长上限，供 UI 显示计时与自动收尾 */
    public static long MAX_DURATION_MS_PLUS() {
        return MAX_DURATION_MS;
    }

    /** 录音/播放状态回调，一律回主线程 */
    public interface Callback {
        void onState(String message);

        void onFinished(boolean ok, String message);
    }

    private VoiceNote() {
    }

    public static File file(Context ctx) {
        return new File(ctx.getFilesDir(), FILE_NAME);
    }

    public static boolean exists(Context ctx) {
        File f = file(ctx);
        return f.isFile() && f.length() > 0;
    }

    /** 录音时长（毫秒），读 MediaPlayer 元数据 */
    public static long durationMs(Context ctx) {
        if (!exists(ctx)) return 0;
        MediaPlayer mp = new MediaPlayer();
        try {
            mp.setDataSource(file(ctx).getAbsolutePath());
            mp.prepare();
            return mp.getDuration();
        } catch (Exception e) {
            return 0;
        } finally {
            mp.release();
        }
    }

    /** 缓存过的时长：演出每句都会问，别在主线程反复 prepare 一个 MediaPlayer */
    private static long cachedDurationMs = -1;

    public static long durationMsCached(Context ctx) {
        if (cachedDurationMs >= 0) return cachedDurationMs;
        cachedDurationMs = Math.max(0, durationMs(ctx));
        return cachedDurationMs;
    }

    // ==================== 录音 ====================

    private static MediaRecorder recorder;

    /** 开始录音；30 秒后自动收尾。重复调用会先停掉上一次。 */
    public static boolean startRecord(Context ctx, Callback cb) {
        stopRecord();
        File out = file(ctx);
        MediaRecorder r = new MediaRecorder();
        try {
            r.setAudioSource(MediaRecorder.AudioSource.MIC);
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            r.setAudioEncodingBitRate(96_000);
            r.setAudioSamplingRate(44_100);
            r.setOutputFile(out.getAbsolutePath());
            r.prepare();
            r.start();
            recorder = r;
            cachedDurationMs = -1;   // 新录音，旧时长作废
            if (cb != null) {
                final Handler ui = new Handler(Looper.getMainLooper());
                ui.post(() -> cb.onState("正在录音，说点真心话…（最多 30 秒）"));
            }
            // 到时自动收尾，并通知调用方（让 UI 关掉录音弹层）
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                if (recorder == r) {
                    stopRecord();
                    if (cb != null) cb.onFinished(true, "录音已满 30 秒，自动保存");
                }
            }, MAX_DURATION_MS);
            return true;
        } catch (Exception e) {
            try {
                r.release();
            } catch (Exception ignored) {
            }
            uiMessage(cb, "录音启动失败：" + e.getMessage());
            return false;
        }
    }

    /** 停止录音并落盘；返回是否成功 */
    public static boolean stopRecord() {
        MediaRecorder r = recorder;
        recorder = null;
        if (r == null) return false;
        try {
            r.stop();
            return true;
        } catch (Exception e) {
            return false;
        } finally {
            r.release();
        }
    }

    public static boolean isRecording() {
        return recorder != null;
    }

    /** 删除录音 */
    public static boolean delete(Context ctx) {
        File f = file(ctx);
        cachedDurationMs = -1;
        return f.isFile() && f.delete();
    }

    // ==================== 播放 ====================

    private static MediaPlayer player;

    /**
     * 播放录音。onDuck 用来压低 BGM（true 开始压、false 恢复）。
     * onDone 在播放结束/失败时回调，演出据此推进到下一句。
     */
    public static void play(Context ctx, final android.media.AudioAttributes attrs,
                            final Runnable onDuckStart, final Runnable onDuckEnd,
                            final Callback cb) {
        stopPlay();
        if (!exists(ctx)) {
            uiFinish(cb, false, "还没有录音");
            return;
        }
        MediaPlayer mp = new MediaPlayer();
        try {
            mp.setDataSource(file(ctx).getAbsolutePath());
            if (attrs != null) mp.setAudioAttributes(attrs);
            mp.setOnPreparedListener(mediaPlayer -> {
                onDuckStart.run();
                mediaPlayer.start();
                uiMessage(cb, "正在播放你的原声…");
            });
            mp.setOnCompletionListener(mediaPlayer -> {
                onDuckEnd.run();
                stopPlay();
                uiFinish(cb, true, "播放完毕");
            });
            mp.setOnErrorListener((mediaPlayer, what, extra) -> {
                onDuckEnd.run();
                stopPlay();
                uiFinish(cb, false, "播放失败");
                return true;
            });
            mp.prepareAsync();
            player = mp;
        } catch (IOException e) {
            try {
                mp.release();
            } catch (Exception ignored) {
            }
            uiFinish(cb, false, "播放失败：" + e.getMessage());
        }
    }

    public static void stopPlay() {
        MediaPlayer p = player;
        player = null;
        if (p == null) return;
        try {
            if (p.isPlaying()) p.stop();
        } catch (Exception ignored) {
        }
        p.release();
    }

    public static boolean isPlaying() {
        return player != null;
    }

    private static void uiMessage(Callback cb, String msg) {
        if (cb == null) return;
        new Handler(Looper.getMainLooper()).post(() -> cb.onState(msg));
    }

    private static void uiFinish(Callback cb, boolean ok, String msg) {
        if (cb == null) return;
        new Handler(Looper.getMainLooper()).post(() -> cb.onFinished(ok, msg));
    }
}
