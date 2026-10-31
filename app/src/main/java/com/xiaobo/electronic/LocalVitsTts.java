package com.xiaobo.electronic;

import android.content.Context;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;

import com.k2fsa.sherpa.onnx.GeneratedAudio;
import com.k2fsa.sherpa.onnx.OfflineTts;
import com.k2fsa.sherpa.onnx.OfflineTtsConfig;
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig;
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 本地离线神经网络 TTS：sherpa-onnx + VITS 中文（小雅，int8 量化）。
 *
 * <p>模型<b>按需下载</b>到 {@code filesDir/tts}（见 {@link TtsModelStore}），
 * 通过文件路径加载：{@code new OfflineTts(null, cfg)}——AssetManager 传 null 时
 * sherpa-onnx 会走 {@code newFromFile} 分支。不联网、不需要任何 API Key。
 * 模型没下载时本类直接判失败，由 {@link SpeechEngine} 引导用户下载。
 *
 * <p><b>为什么用本地而不是云端</b>：微软 Edge 朗读端点在 2026 年 10 月做了一次迁移，
 * 大量依赖它的第三方工具一夜之间全部 403。表白这种"一辈子就这一次"的场景，
 * 不能押在一个随时会失效的逆向接口上。
 *
 * <p><b>降级约定</b>：任何一步失败都只置 {@code failed} 并回调 {@link Callback#onError()}，
 * 由 {@link SpeechEngine} 切到系统 TTS。绝不让演出因为朗读挂掉。
 */
public class LocalVitsTts {

    /** 播放/合成回调：用于 BGM 闪避与失败降级 */
    public interface Callback {
        void onStart();

        void onDone();

        void onError();
    }

    private final Context ctx;
    private final ExecutorService exec;

    private volatile OfflineTts tts;
    private volatile boolean ready;
    /** 模型上报的说话人数（多说话人包才有意义） */
    private volatile int numSpeakers;
    private volatile boolean failed;
    /** 失败原因是"模型还没下载"——UI 要据此引导下载，而不是笼统报"朗读不可用" */
    private volatile boolean modelMissing;
    private volatile boolean initStarted;

    private AudioTrack track;
    /** 当前这句是否被 stop() 打断 */
    private final AtomicBoolean interrupted = new AtomicBoolean(false);

    public LocalVitsTts(Context context) {
        this.ctx = context.getApplicationContext();
        this.exec = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "local-tts");
            t.setPriority(Thread.NORM_PRIORITY - 1); // 不与渲染抢 CPU
            return t;
        });
    }

    public boolean isReady() {
        return ready;
    }

    /** 是否因为"模型没下载"而不可用（供 UI 引导下载） */
    public boolean isModelMissing() {
        return modelMissing;
    }

    /**
     * 后台预热：把 onnx 读进内存并建立会话，约 1~3 秒。
     * 立即返回，不阻塞调用线程。
     */
    public boolean prepare() {
        if (ready || failed) return ready;
        if (!TtsModelStore.isReady(ctx, TtsVoice.current())) {
            // 没模型就别去拉起 ONNX 会话：既加载不出来，又白占一次内存峰值
            failed = true;
            modelMissing = true;
            return false;
        }
        if (!initStarted) {
            initStarted = true;
            exec.submit(this::init);
        }
        return false;
    }

    private void init() {
        try {
            TtsVoice v = TtsVoice.current();
            // 注意：路径必须是绝对路径。传 null 走 newFromFile 分支，
            // 此时 config 里所有相对路径都会被当成文件系统路径解析。
            OfflineTtsVitsModelConfig vits = new OfflineTtsVitsModelConfig();
            vits.setModel(TtsModelStore.modelPath(ctx, v));
            vits.setLexicon(TtsModelStore.lexiconPath(ctx, v));
            vits.setTokens(TtsModelStore.tokensPath(ctx, v));
            vits.setDataDir("");                    // 不需要 espeak-ng-data
            vits.setDictDir(TtsModelStore.dictPath(ctx, v));   // piper 为空串，aishell3 是 jieba 词典
            vits.setLengthScale(v.lengthScale);     // 略放慢，读告白比日常播报慢一点更动人

            OfflineTtsModelConfig model = new OfflineTtsModelConfig();
            model.setVits(vits);
            model.setNumThreads(2);

            OfflineTtsConfig cfg = new OfflineTtsConfig();
            cfg.setModel(model);
            cfg.setMaxNumSentences(1);
            cfg.setRuleFsts(TtsModelStore.ruleFstsPath(ctx, v));

            OfflineTts engine = new OfflineTts(null, cfg);
            numSpeakers = engine.numSpeakers();     // 模型自己报的说话人数，比清单上的准
            tts = engine;
            ready = true;
        } catch (Throwable t) {
            failed = true;
        }
    }

    /** 模型实际支持的说话人数（1 = 单一音色）；未加载完成时为 0 */
    public int numSpeakers() {
        return numSpeakers;
    }

    /**
     * 合成并播放一句。整条链路（可能未初始化则先初始化 → 合成 → 变调 → 播放）
     * 都在后台线程，立即返回 true 表示"已受理"；真正出问题时通过
     * {@link Callback#onError()} 通知降级。
     */
    public boolean speak(String text, Callback cb) {
        if (failed || text == null || text.trim().isEmpty()) return false;
        exec.submit(() -> {
            try {
                if (!ready) init();
                if (!ready || tts == null) {
                    cb.onError();
                    return;
                }
                // 说话人编号：单音色模型传 0；多说话人包用用户挑的那个
                int sid = TtsVoice.currentSpeaker();
                if (numSpeakers > 0 && sid >= numSpeakers) sid = numSpeakers - 1;
                GeneratedAudio audio = tts.generate(text, sid, 1.0f);
                if (audio == null || audio.getSamples() == null || audio.getSamples().length == 0) {
                    cb.onError();
                    return;
                }
                play(audio, cb);
            } catch (Throwable t) {
                cb.onError();
            }
        });
        return true;
    }

    /** 用 AudioTrack 播放合成出的 PCM；播放结束（或被 stop 打断）后回调 onDone */
    private void play(GeneratedAudio audio, Callback cb) {
        float[] samples = audio.getSamples();
        int sampleRate = audio.getSampleRate();

        // 变声：把采样重采样到 N*p 个，再用 sampleRate*p 播放。
        // 时长 = (N*p)/(sr*p) = N/sr 保持不变，只有音调按 p 缩放——
        // 直接改播放采样率虽然更简单，但会连带把语速改掉（男声变慢吞吞）。
        float pitch = TtsVoice.pitchFactor();
        float[] outSamples = samples;
        int outRate = sampleRate;
        if (Math.abs(pitch - 1.0f) > 0.01f) {
            outSamples = resample(samples, pitch);
            outRate = Math.round(sampleRate * pitch);
            if (outRate < 4000 || outRate > 192_000) {   // AudioTrack 的合法范围
                outSamples = samples;
                outRate = sampleRate;
            }
        }
        byte[] pcm = toPcm16(outSamples);

        int minBuf = AudioTrack.getMinBufferSize(outRate,
                AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);
        if (minBuf <= 0) {
            cb.onError();
            return;
        }
        interrupted.set(false);
        AudioTrack at = new AudioTrack(AudioManager.STREAM_MUSIC, outRate,
                AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT,
                Math.max(minBuf, pcm.length), AudioTrack.MODE_STREAM);
        track = at;
        try {
            at.play();
            at.write(pcm, 0, pcm.length);
            cb.onStart();

            // 等播放到尾（按采样率估算时长），期间可被 stop() 打断
            long totalMs = (long) (outSamples.length * 1000L / (double) outRate) + 100;
            long step = 60;
            for (long waited = 0; waited < totalMs; waited += step) {
                if (interrupted.get()) break;
                try {
                    Thread.sleep(step);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            if (!interrupted.get()) cb.onDone();
        } catch (Throwable t) {
            cb.onError();
        } finally {
            try {
                at.stop();
                at.release();
            } catch (Throwable ignored) {
            }
            if (track == at) track = null;
        }
    }

    public void stop() {
        interrupted.set(true);
        AudioTrack at = track;
        if (at != null) {
            try {
                at.pause();
                at.flush();
            } catch (Throwable ignored) {
            }
        }
    }

    public void release() {
        stop();
        exec.shutdownNow();
        OfflineTts t = tts;
        if (t != null) {
            try {
                t.release();
            } catch (Throwable ignored) {
            }
            tts = null;
        }
        ready = false;
    }

    /**
     * 线性插值重采样：输出 {@code round(n * ratio)} 个采样点。
     * 配合"播放采样率也乘 ratio"使用，就能只改音调、不改时长。
     */
    private static float[] resample(float[] src, double ratio) {
        int n = src.length;
        int m = Math.max(1, (int) Math.round(n * ratio));
        float[] out = new float[m];
        for (int i = 0; i < m; i++) {
            double pos = i / ratio;
            int i0 = (int) pos;
            if (i0 >= n - 1) {
                out[i] = src[n - 1];
                continue;
            }
            float frac = (float) (pos - i0);
            out[i] = src[i0] * (1f - frac) + src[i0 + 1] * frac;
        }
        return out;
    }

    /** float [-1,1] → 16bit 小端 PCM */
    private static byte[] toPcm16(float[] samples) {
        byte[] out = new byte[samples.length * 2];
        for (int i = 0; i < samples.length; i++) {
            int v = Math.round(samples[i] * 32767f);
            if (v > 32767) v = 32767;
            if (v < -32768) v = -32768;
            out[i * 2] = (byte) (v & 0xff);
            out[i * 2 + 1] = (byte) ((v >> 8) & 0xff);
        }
        return out;
    }
}
