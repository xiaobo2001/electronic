package com.xiaobo.electronic;

import android.content.Context;

import java.io.File;

/**
 * 离线语音识别模型：按需下载、就地存放。
 *
 * <p><b>为什么是下载而不是内置。</b>内置会让 APK 从 52MB 直接涨到 77MB，
 * 而这个功能多数人一辈子只用一两次；但完全不做、只依赖 Google 的
 * {@code RecognizerIntent}，国内 ROM 上点了就是静默失败。
 * 所以折中：第一次点「语音输入」时才下载，下载完永久离线可用，
 * 断网、飞行模式也照样能识别。
 *
 * <p><b>模型选型。</b>sherpa-onnx 的 streaming-zipformer 中文 14M（int8 量化），
 * 四个文件合计约 25MB。对比另一个候选 paraformer-zh-small 的 int8 是 82MB——
 * 差 3 倍多，移动网络下体验差别很大，而这两者在"说一句话"的场景里准确率接近。
 *
 * <p><b>加载方式。</b>{@code OnlineRecognizer(AssetManager, config)} 这个构造器
 * 在 AssetManager 传 null 时会走 {@code newFromFile}，也就是按文件路径加载。
 * 正因为有这条分支，下载到 {@code filesDir} 的模型才能直接用，
 * 不必塞进 assets（assets 是只读的，运行时根本写不进去）。
 *
 * <p>下载逻辑与 {@link TtsModelStore} 共用 {@link ModelDownloader}。
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

    /** 主源走国内镜像（hf-mirror），备用源走 huggingface 原站 */
    private static final String BASE_MIRROR =
            "https://hf-mirror.com/csukuangfj/sherpa-onnx-streaming-zipformer-zh-14M-2023-02-23/resolve/main/";
    private static final String BASE_ORIGIN =
            "https://huggingface.co/csukuangfj/sherpa-onnx-streaming-zipformer-zh-14M-2023-02-23/resolve/main/";

    private static final String DIR = "asr";

    /** 整包直链（国内网盘，上传后填入）。留空则走官方源逐文件下载 */
    private static final String[] ZIP_URLS = {
            ModelRepo.url("asr.zip"),
    };
    private static final long ZIP_BYTES = 20_945_582L;

    private static final ModelDownloader.Pack PACK =
            new ModelDownloader.Pack(DIR, FILES, SIZES,
                    new String[]{BASE_MIRROR, BASE_ORIGIN}, null,
                    ZIP_URLS, ZIP_BYTES);

    private AsrModelStore() {
    }

    /** 与 {@link ModelDownloader.Progress} 同一套契约，保留本名是为了调用方不改代码 */
    public interface Progress extends ModelDownloader.Progress {
    }

    /** 模型存放目录 */
    public static File dir(Context ctx) {
        return ModelDownloader.dir(ctx, DIR);
    }

    /** 四个文件都在、且都不是空壳（下载中断会留下 0 字节文件，必须查长度） */
    public static boolean isReady(Context ctx) {
        return ModelDownloader.isReady(ctx, PACK);
    }

    public static String path(Context ctx, int index) {
        return ModelDownloader.path(ctx, PACK, index);
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
        return ModelDownloader.totalBytes(PACK);
    }

    /**
     * 下载模型（后台线程，回调回主线程）。
     * 参数用父接口 {@link ModelDownloader.Progress}，两个模型的下载 UI 才能共用一套。
     */
    public static void download(Context ctx, ModelDownloader.Progress cb) {
        ModelDownloader.download(ctx, PackSources.apply(PACK), cb);
    }

    /** 删掉模型，释放约 25MB（菜单里"清理语音模型"用） */
    public static boolean delete(Context ctx) {
        return ModelDownloader.delete(ctx, PACK);
    }

    public static String sizeText(long bytes) {
        return ModelDownloader.sizeText(bytes);
    }
}
