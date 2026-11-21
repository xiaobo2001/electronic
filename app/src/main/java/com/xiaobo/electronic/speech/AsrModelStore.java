package com.xiaobo.electronic.speech;

import android.content.Context;

import java.io.File;

/**
 * 离线语音识别模型：APK 内置，首次使用时解压到本地。
 *
 * <p><b>为什么内置。</b>完全离线是这个应用的底线——国内 ROM 上没有 Google
 * 语音识别，只依赖 {@code RecognizerIntent} 点了就是静默失败。模型整包
 * 随 APK 分发，首次点「语音输入」时由 {@link ModelInstaller} 解压到 filesDir，
 * 之后断网、飞行模式也照样能识别，全程不发网络请求。
 *
 * <p><b>模型选型。</b>sherpa-onnx 的 streaming-zipformer 中文 14M（int8 量化），
 * 四个文件合计约 25MB。对比另一个候选 paraformer-zh-small 的 int8 是 82MB——
 * 差 3 倍多，而这两者在"说一句话"的场景里准确率接近。
 *
 * <p><b>加载方式。</b>{@code OnlineRecognizer(AssetManager, config)} 这个构造器
 * 在 AssetManager 传 null 时会走 {@code newFromFile}，也就是按文件路径加载。
 * 正因为有这条分支，解压到 {@code filesDir} 的模型才能直接用。
 *
 * <p>安装逻辑与 {@link TtsModelStore} 共用 {@link ModelInstaller}。
 */
public final class AsrModelStore {

    /** 仓库内的文件名（四个文件是一套，缺任何一个都无法加载） */
    private static final String[] FILES = {
            "encoder-epoch-99-avg-1.int8.onnx",
            "decoder-epoch-99-avg-1.int8.onnx",
            "joiner-epoch-99-avg-1.int8.onnx",
            "tokens.txt",
    };

    /** 各文件体积（字节，实测值），用于进度条分母与"还剩多少"的提示 */
    private static final long[] SIZES = {
            21_621_684L, 1_888_682L, 1_795_562L, 48_697L,   // 合计约 24.3 MB
    };

    private static final String DIR = "asr";

    private static final ModelInstaller.Pack PACK =
            new ModelInstaller.Pack(DIR, FILES, SIZES);

    private AsrModelStore() {
    }

    /** 与 {@link ModelInstaller.Progress} 同一套契约，保留本名是为了调用方不改代码 */
    public interface Progress extends ModelInstaller.Progress {
    }

    /** 启动预装用：把本模型的安装描述交给{@link ModelInstaller} */
    public static ModelInstaller.Pack pack() {
        return PACK;
    }

    /** 模型存放目录 */
    public static File dir(Context ctx) {
        return ModelInstaller.dir(ctx, DIR);
    }

    /** 四个文件都在、且都不是空壳（解压中断会留下 0 字节文件，必须查长度） */
    public static boolean isReady(Context ctx) {
        return ModelInstaller.isReady(ctx, PACK);
    }

    public static String path(Context ctx, int index) {
        return ModelInstaller.path(ctx, PACK, index);
    }

    public static String encoderPath(Context ctx) {
        return path(ctx, 0);
    }

    public static String decoderPath(Context ctx) {
        return path(ctx, 1);
    }

    public static String joinerPath(Context ctx) {
        return path(ctx, 2);
    }

    public static String tokensPath(Context ctx) {
        return path(ctx, 3);
    }

    /** 估算总字节，用于进度条分母与"还剩多少"的提示 */
    public static long totalBytes() {
        return ModelInstaller.totalBytes(PACK);
    }

    /**
     * 安装模型（后台线程，回调回主线程）：从 APK 内置整包解压，不发网络请求。
     * 参数用父接口 {@link ModelInstaller.Progress}，两个模型的安装 UI 才能共用一套。
     */
    public static void download(Context ctx, ModelInstaller.Progress cb) {
        ModelInstaller.download(ctx, PACK, cb);
    }

    /** 删掉模型，释放约 25MB（菜单里"清理语音模型"用） */
    public static boolean delete(Context ctx) {
        return ModelInstaller.delete(ctx, PACK);
    }

    public static String sizeText(long bytes) {
        return ModelInstaller.sizeText(bytes);
    }
}
