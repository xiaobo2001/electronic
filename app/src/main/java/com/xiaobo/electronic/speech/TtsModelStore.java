package com.xiaobo.electronic.speech;

import android.content.Context;

import java.io.File;

/**
 * 离线朗读音色的存放与安装（一套音色一个目录）。
 *
 * <p>音色整包随 APK 内置在 {@code assets/models/}，首次使用时由
 * {@link ModelInstaller} 解压到 filesDir。全程不联网，装好即永久离线可用。
 * 当前只内置小雅一套（音质最好），换听感用变声档即可，无需换模型。
 *
 * <p><b>加载方式。</b>{@code OfflineTts(AssetManager, config)} 在 AssetManager 传 null
 * 时会走 {@code newFromFile}，按文件路径加载——正因为有这条分支，
 * 解压到 {@code filesDir} 的模型才能直接用（assets 是只读的，运行时写不进去）。
 *
 * <p>各文件体积与整包逐一核对过，详见 {@link TtsVoice}。
 * 安装逻辑与 {@link AsrModelStore} 共用 {@link ModelInstaller}。
 */
public final class TtsModelStore {

    private TtsModelStore() {
    }

    public static File dir(Context ctx, TtsVoice v) {
        return ModelInstaller.dir(ctx, "tts_" + v.id);
    }

    public static boolean isReady(Context ctx, TtsVoice v) {
        return ModelInstaller.isReady(ctx, v.pack());
    }

    /** 当前选中音色是否已安装 */
    public static boolean isReady(Context ctx) {
        return isReady(ctx, TtsVoice.current());
    }

    public static String modelPath(Context ctx, TtsVoice v) {
        return new File(dir(ctx, v), v.modelFile).getAbsolutePath();
    }

    public static String lexiconPath(Context ctx, TtsVoice v) {
        return new File(dir(ctx, v), v.lexiconFile).getAbsolutePath();
    }

    public static String tokensPath(Context ctx, TtsVoice v) {
        return new File(dir(ctx, v), v.tokensFile).getAbsolutePath();
    }

    /** 分词词典目录；不需要词典的音色（piper）返回空串 */
    public static String dictPath(Context ctx, TtsVoice v) {
        return v.dictDir.isEmpty() ? "" : new File(dir(ctx, v), v.dictDir).getAbsolutePath();
    }

    /**
     * 规则 FST 列表：sherpa-onnx 要求逗号分隔的多个路径，顺序即优先级。
     * 日期 / 数字 / 电话 / 多音字各一条。
     */
    public static String ruleFstsPath(Context ctx, TtsVoice v) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < v.ruleFsts.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(new File(dir(ctx, v), v.ruleFsts[i]).getAbsolutePath());
        }
        return sb.toString();
    }

    public static long totalBytes(TtsVoice v) {
        return v.totalBytes();
    }

    public static void download(Context ctx, TtsVoice v, ModelInstaller.Progress cb) {
        ModelInstaller.download(ctx, v.pack(), cb);
    }

    /** 删除某套音色，释放空间 */
    public static boolean delete(Context ctx, TtsVoice v) {
        return ModelInstaller.delete(ctx, v.pack());
    }

    /** 已安装的音色数（菜单里显示"已装 2/3"） */
    public static int installedCount(Context ctx) {
        int n = 0;
        for (TtsVoice v : TtsVoice.ALL) {
            if (isReady(ctx, v)) n++;
        }
        return n;
    }

    /**
     * 旧版迁移：1.2.8 及之前音色固定下载到 {@code filesDir/tts}；
     * 多音色版按音色分目录（{@code tts_xiaoya} 等）。文件名与小雅包完全一致，
     * 直接搬过去，老用户不用为了升级重新下 20MB。
     */
    public static void migrateLegacy(Context ctx) {
        try {
            File legacy = new File(ctx.getFilesDir(), "tts");
            File dest = dir(ctx, TtsVoice.XIAOYA);
            if (!legacy.isDirectory() || dest.isDirectory()) return;
            //noinspection ResultOfMethodCallIgnored
            dest.mkdirs();
            for (String f : TtsVoice.XIAOYA.files) {
                File from = new File(legacy, f);
                if (from.isFile()) {
                    //noinspection ResultOfMethodCallIgnored
                    from.renameTo(new File(dest, f));
                }
            }
            // 搬空的旧目录清掉，留着只会让人误会还有一套模型
            String[] rest = legacy.list();
            if (rest == null || rest.length == 0) {
                //noinspection ResultOfMethodCallIgnored
                legacy.delete();
            }
        } catch (Throwable ignored) {
        }
    }

    public static String sizeText(long bytes) {
        return ModelInstaller.sizeText(bytes);
    }
}
