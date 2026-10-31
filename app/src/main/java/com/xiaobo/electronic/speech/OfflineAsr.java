package com.xiaobo.electronic.speech;

import android.content.Context;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Handler;
import android.os.Looper;

import com.k2fsa.sherpa.onnx.FeatureConfig;
import com.k2fsa.sherpa.onnx.OnlineModelConfig;
import com.k2fsa.sherpa.onnx.OnlineRecognizer;
import com.k2fsa.sherpa.onnx.OnlineRecognizerConfig;
import com.k2fsa.sherpa.onnx.OnlineStream;
import com.k2fsa.sherpa.onnx.OnlineTransducerModelConfig;
import com.xiaobo.electronic.App;

/**
 * 离线语音识别引擎：麦克风 → sherpa-onnx 流式识别 → 实时出字。
 *
 * <p>选流式模型（streaming-zipformer）而不是非流式 paraformer，图的是两件事：
 * 体积小三倍（25MB vs 82MB），以及能边说边出字——屏幕上能看到字一个个蹦出来，
 * 用户就知道"它听见了"，不会对着没反应的界面干说。
 *
 * <p>结束时机做了双保险：说完停顿 1.8 秒自动收尾，也可以手动点"说完了"。
 * 判停用的是音量能量而不是 sherpa 自带的 endpoint 规则——后者要配
 * EndpointConfig 的三条规则，默认值随版本变，不如自己算 RMS 来得可控。
 */
public final class OfflineAsr {

    private static final int SAMPLE_RATE = 16000;
    /** 每帧 100ms（16000 × 0.1），太小会让 decode 太频繁，太大会让出字有延迟 */
    private static final int FRAME = 1600;
    /** 归一化后低于这个 RMS 视为静音（约 -40dB，能盖过一般室内底噪） */
    private static final float SILENCE_RMS = 0.012f;
    /** 静音持续这么久、且已经识别出内容，就认为说完了 */
    private static final long AUTO_STOP_MS = 1800L;

    private final Handler ui = new Handler(Looper.getMainLooper());

    private OnlineRecognizer recognizer;
    private OnlineStream stream;
    private AudioRecord record;
    private Thread worker;
    private volatile boolean running = false;
    private volatile boolean cancelled = false;
    private String lastText = "";

    public interface Listener {
        /** 实时文字（可能随后续音频被修正，只用于显示） */
        void onPartial(String text);

        /** 最终结果（主线程） */
        void onFinal(String text);

        void onError(String message);
    }

    private Listener listener;

    /** 加载模型。文件不全或 ABI 不匹配都会失败，调用方据此降级。 */
    public synchronized boolean load(Context ctx) {
        if (recognizer != null) return true;
        if (!AsrModelStore.isReady(ctx)) return false;
        try {
            FeatureConfig feat = new FeatureConfig();
            feat.setSampleRate(SAMPLE_RATE);

            OnlineTransducerModelConfig transducer = new OnlineTransducerModelConfig();
            transducer.setEncoder(AsrModelStore.encoderPath(ctx));
            transducer.setDecoder(AsrModelStore.decoderPath(ctx));
            transducer.setJoiner(AsrModelStore.joinerPath(ctx));

            OnlineModelConfig model = new OnlineModelConfig();
            model.setTransducer(transducer);
            model.setTokens(AsrModelStore.tokensPath(ctx));
            model.setNumThreads(2);
            model.setProvider("cpu");

            OnlineRecognizerConfig cfg = new OnlineRecognizerConfig();
            cfg.setFeatConfig(feat);
            cfg.setModelConfig(model);
            cfg.setDecodingMethod("greedy_search");

            // 第一个参数传 null：AssetManager 为空时走 newFromFile，按文件路径加载，
            // 这样放在 filesDir 的下载模型才能用（assets 是只读的，写不进去）
            recognizer = new OnlineRecognizer(null, cfg);
            return true;
        } catch (Throwable t) {
            recognizer = null;
            return false;
        }
    }

    public void setListener(Listener l) {
        this.listener = l;
    }

    /** 开始录音识别 */
    public void start() {
        if (recognizer == null) {
            postError("语音模型还没准备好");
            return;
        }
        if (running) return;
        running = true;
        cancelled = false;
        lastText = "";
        // Kotlin 的默认参数在 Java 里不可见，必须显式传空 hotwords
        stream = recognizer.createStream("");

        int min = AudioRecord.getMinBufferSize(SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
        if (min <= 0) min = FRAME * 4;
        try {
            record = new AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
                    Math.max(min, FRAME * 4));
        } catch (Throwable t) {
            running = false;
            postError("打不开麦克风");
            return;
        }
        if (record.getState() != AudioRecord.STATE_INITIALIZED) {
            running = false;
            postError("麦克风被占用了");
            return;
        }

        worker = new Thread(this::loop, "asr-record");
        worker.start();
    }

    private void loop() {
        short[] buf = new short[FRAME];
        float[] fbuf = new float[FRAME];
        long silenceMs = 0L;
        try {
            record.startRecording();
            while (running) {
                int n = record.read(buf, 0, FRAME);
                if (n <= 0) continue;

                double sum = 0;
                for (int i = 0; i < n; i++) {
                    fbuf[i] = buf[i] / 32768f;
                    sum += (double) fbuf[i] * fbuf[i];
                }
                float rms = (float) Math.sqrt(sum / Math.max(1, n));

                stream.acceptWaveform(fbuf, SAMPLE_RATE);
                while (recognizer.isReady(stream)) {
                    recognizer.decode(stream);
                    String text = recognizer.getResult(stream).getText();
                    if (text == null) text = "";
                    text = text.trim();
                    if (!text.equals(lastText)) {
                        lastText = text;
                        final String t = text;
                        ui.post(() -> {
                            if (listener != null) listener.onPartial(t);
                        });
                    }
                }

                if (rms < SILENCE_RMS) {
                    silenceMs += (FRAME * 1000L / SAMPLE_RATE);
                } else {
                    silenceMs = 0L;
                }
                // 停顿够久、又确实说了东西，就自动收尾，不用用户再点一下
                if (silenceMs >= AUTO_STOP_MS && !lastText.isEmpty()) {
                    finish();
                    return;
                }
            }
            finish();   // 走 stop() 退出：把已识别的内容交出去
        } catch (Throwable t) {
            postError("识别中断了");
        }
    }

    /** 收尾：补一次解码后把最终结果交出去 */
    private void finish() {
        running = false;
        String text = lastText;
        try {
            stream.inputFinished();
            while (recognizer.isReady(stream)) {
                recognizer.decode(stream);
            }
            String t = recognizer.getResult(stream).getText();
            if (t != null && !t.trim().isEmpty()) text = t.trim();
        } catch (Throwable ignored) {
            // 已经拿到 lastText 就用它，不因收尾失败丢掉内容
        }
        if (record != null) {
            try {
                record.stop();
            } catch (Throwable ignored) {
            }
        }
        if (cancelled || text == null || text.isEmpty()) {
            final String none = "";
            ui.post(() -> {
                if (listener != null && !cancelled) listener.onFinal(none);
            });
            return;
        }
        final String out = text;
        ui.post(() -> {
            if (listener != null) listener.onFinal(out);
        });
    }

    /** 手动结束（用户点"说完了"） */
    public void stop() {
        if (!running) return;
        running = false;
        if (record != null) {
            try {
                record.stop();   // 让阻塞中的 read 返回，线程才能退出循环去 finish()
            } catch (Throwable ignored) {
            }
        }
    }

    /** 放弃这次识别 */
    public void cancel() {
        cancelled = true;
        stop();
    }

    public boolean isRunning() {
        return running;
    }

    /** 释放模型与麦克风；不调用会一直占着 MIC，别的 App 录不了音 */
    public void release() {
        stop();
        if (worker != null) {
            try {
                worker.join(300);
            } catch (InterruptedException ignored) {
            }
            worker = null;
        }
        if (record != null) {
            try {
                record.release();
            } catch (Throwable ignored) {
            }
            record = null;
        }
        if (stream != null) {
            try {
                stream.release();
            } catch (Throwable ignored) {
            }
            stream = null;
        }
        if (recognizer != null) {
            try {
                recognizer.release();
            } catch (Throwable ignored) {
            }
            recognizer = null;
        }
    }

    private void postError(String msg) {
        ui.post(() -> {
            if (listener != null) listener.onError(msg);
        });
    }
}
