package com.xiaobo.electronic.speech;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * 离线模型安装器。
 *
 * <p>四套模型（ASR 识别 + 三个 TTS 音色）的整包已随 APK 内置在
 * {@code assets/models/} 下。首次使用时解压到 {@code filesDir}，
 * sherpa-onnx 按文件路径加载——与原下载方案的落盘布局完全一致，
 * 所以 {@link LocalVitsTts} / {@link OfflineAsr} 一行都不用改。
 *
 * <p><b>全程不发网络请求</b>：没有 URL、没有连接超时、没有断点续传、
 * 没有在线源清单。模型是应用自带的资源，装好即永久离线可用。
 *
 * <p><b>并发安全</b>：按模型目录加锁，避免"启动时预装"与"用户点击安装"
 * 同时解压同一套文件时互相覆盖。重复调用是幂等的，文件完整就直接返回。
 */
public final class ModelInstaller {

    /** assets 中模型整包所在目录（整包名 = 模型目录名 + .zip） */
    private static final String ASSET_DIR = "models/";
    /** 解压上限：异常包再大也不能把用户存储写满 */
    private static final long MAX_UNZIP_BYTES = 512L * 1024 * 1024;
    /** 每个模型目录一把锁，见类注释 */
    private static final ConcurrentHashMap<String, Object> LOCKS = new ConcurrentHashMap<>();

    private ModelInstaller() {
    }

    /** 安装进度与结果回调（一律回主线程，UI 可直接操作控件） */
    public interface Progress {
        /** done/total 为字节数 */
        void onProgress(long done, long total);

        /** 切换正在解压的条目时回调，用于在界面上写清"正在装第几个/哪个文件" */
        default void onStage(String fileName, int index, int count) {
        }

        void onDone(boolean ok, String message);

        /** 是否已取消。返回 true 时安装线程会在当前文件收尾后停下。 */
        default boolean isCancelled() {
            return false;
        }
    }

    /** 一套模型的完整描述：放哪儿、有哪些文件、多大 */
    public static final class Pack {
        final String dir;
        final String[] files;
        final long[] sizes;

        public Pack(String dir, String[] files, long[] sizes) {
            this.dir = dir;
            this.files = files;
            this.sizes = sizes;
        }
    }

    static File dir(Context ctx, String name) {
        return new File(ctx.getFilesDir(), name);
    }

    /**
     * 判断一个文件是否已经完整。
     *
     * <p>按清单里的期望大小校验，容差 2%（最小 64 字节）：既能容忍上游
     * 文件微调，也能挡住只解压了一半的残缺文件。没有期望值时退回 1KB 阈值。
     */
    public static boolean complete(File f, long expect) {
        if (f == null || !f.isFile()) return false;
        long len = f.length();
        if (len <= 0) return false;
        if (expect <= 0) return len >= 1024L;
        long tol = Math.max(64L, expect / 50L);
        return len >= expect - tol && len <= expect + tol;
    }

    /** 所有文件都在、且每个都完整 */
    static boolean isReady(Context ctx, Pack pack) {
        File d = dir(ctx, pack.dir);
        for (int i = 0; i < pack.files.length; i++) {
            if (!complete(new File(d, pack.files[i]),
                    i < pack.sizes.length ? pack.sizes[i] : 0)) return false;
        }
        return true;
    }

    static String path(Context ctx, Pack pack, int index) {
        return new File(dir(ctx, pack.dir), pack.files[index]).getAbsolutePath();
    }

    static long totalBytes(Pack pack) {
        long t = 0;
        for (long s : pack.sizes) t += s;
        return t;
    }

    /**
     * 安装整套模型（后台线程，回调回主线程）。
     *
     * <p>已存在且大小与预期一致的文件会整体跳过——重复调用不会白解压。
     * 方法名沿用旧版的 download，是为了让调用方少改代码；
     * 它现在做的是纯本地解压，不碰网络。
     */
    public static void download(Context ctx, Pack pack, Progress cb) {
        final Handler ui = new Handler(Looper.getMainLooper());
        final long total = totalBytes(pack);
        final Context app = ctx.getApplicationContext();
        new Thread(() -> {
            Object lock = LOCKS.computeIfAbsent(pack.dir, k -> new Object());
            synchronized (lock) {
                if (isReady(app, pack)) {
                    ui.post(() -> cb.onProgress(total, total));
                    ui.post(() -> cb.onDone(true, "模型就绪"));
                    return;
                }
                File d = dir(app, pack.dir);
                if (!d.exists() && !d.mkdirs()) {
                    ui.post(() -> cb.onDone(false, "存储空间不足"));
                    return;
                }
                try {
                    unzipAssets(app, ASSET_DIR + pack.dir + ".zip", d, pack, ui, cb, total);
                    if (isReady(app, pack)) {
                        ui.post(() -> cb.onProgress(total, total));
                        ui.post(() -> cb.onDone(true, "模型就绪"));
                    } else {
                        ui.post(() -> cb.onDone(false, "内置模型校验失败，请重新安装应用"));
                    }
                } catch (Throwable t) {
                    String msg = t.getMessage();
                    ui.post(() -> cb.onDone(false,
                            "模型安装失败" + (msg == null ? "" : "：" + msg)));
                }
            }
        }, "model-install-" + pack.dir).start();
    }

    /**
     * 静默预装：启动时后台调用，失败也不打扰用户。
     * 只装调用方传入的包；已经完整的直接跳过。
     */
    public static void preinstallAsync(Context ctx, Pack[] packs) {
        final Context app = ctx.getApplicationContext();
        new Thread(() -> {
            for (Pack p : packs) {
                if (isReady(app, p)) continue;
                Object lock = LOCKS.computeIfAbsent(p.dir, k -> new Object());
                synchronized (lock) {
                    if (isReady(app, p)) continue;
                    File d = dir(app, p.dir);
                    if (!d.exists() && !d.mkdirs()) continue;
                    try {
                        unzipAssets(app, ASSET_DIR + p.dir + ".zip", d, p,
                                null, null, totalBytes(p));
                    } catch (Throwable ignored) {
                        // 静默预装失败无害：用户真正用到时走 download() 带进度的那条路，
                        // 失败会在对话框里明确说出来。
                    }
                }
            }
        }, "model-preinstall").start();
    }

    /**
     * 从 assets 解压 zip 到目标目录。
     *
     * <p>两条必须的防护：
     * <ul>
     *   <li><b>路径穿越</b>：逐个确认条目落在目标目录内；</li>
     *   <li><b>解压炸弹</b>：累计写入超过上限立即中止。</li>
     * </ul>
     *
     * @param ui 进度回调所在的线程 Handler，传 null 表示静默安装
     * @param cb 进度回调，传 null 表示静默安装
     */
    private static void unzipAssets(Context ctx, String assetPath, File destDir, Pack pack,
                                    Handler ui, Progress cb, long total) throws IOException {
        final int count = pack.files.length;
        final UnzipProgress up;
        if (ui != null && cb != null) {
            up = new UnzipProgress() {
                @Override
                public void onEntry(String name, int index) {
                    ui.post(() -> cb.onStage(name, Math.min(index, count), count));
                }

                @Override
                public void onBytes(long written) {
                    ui.post(() -> cb.onProgress(Math.min(written, total), total));
                }
            };
        } else {
            up = null;
        }
        unzip(ctx.getAssets().open(assetPath), destDir, MAX_UNZIP_BYTES, up);
    }

    /** 解压进度回调（可选），条目索引从 1 开始 */
    public interface UnzipProgress {
        void onEntry(String name, int index);

        void onBytes(long written);
    }

    /**
     * 解压 zip 输入流到目标目录。
     *
     * <p>两条必须的防护：
     * <ul>
     *   <li><b>路径穿越</b>：逐个确认条目落在目标目录内；</li>
     *   <li><b>解压炸弹</b>：累计写入超过上限立即中止。</li>
     * </ul>
     *
     * @param in        zip 输入流，方法会负责关闭
     * @param maxBytes  解压总字节上限，超过即中止
     * @param progress  可选进度回调，传 null 表示静默
     */
    public static void unzip(InputStream in, File destDir, long maxBytes,
                             UnzipProgress progress) throws IOException {
        String destRoot = destDir.getCanonicalPath();
        byte[] buf = new byte[64 * 1024];
        long written = 0L;
        long lastPost = 0L;
        int index = 0;
        try (ZipInputStream zis = new ZipInputStream(new BufferedInputStream(in))) {
            ZipEntry e;
            while ((e = zis.getNextEntry()) != null) {
                if (e.isDirectory()) continue;
                if (progress != null) progress.onEntry(e.getName(), ++index);
                File out = new File(destDir, e.getName());
                String outPath = out.getCanonicalPath();
                if (!outPath.startsWith(destRoot + File.separator) && !outPath.equals(destRoot)) {
                    throw new IOException("zip 条目越界: " + e.getName());
                }
                File parent = out.getParentFile();
                if (parent != null && !parent.exists() && !parent.mkdirs()) {
                    throw new IOException("mkdirs failed: " + parent);
                }
                try (FileOutputStream fos = new FileOutputStream(out)) {
                    int n;
                    while ((n = zis.read(buf)) > 0) {
                        fos.write(buf, 0, n);
                        written += n;
                        if (written > maxBytes) {
                            throw new IOException("解压体积异常，已中止");
                        }
                        if (progress != null) {
                            long now = System.currentTimeMillis();
                            if (now - lastPost > 100L) {
                                lastPost = now;
                                progress.onBytes(written);
                            }
                        }
                    }
                    try {
                        fos.getFD().sync();   // 落盘，避免拔电源后剩个空文件
                    } catch (Exception ignored) {
                    }
                }
                zis.closeEntry();
            }
        }
    }

    /** 删掉整套模型，释放空间（菜单里"清理模型"用） */
    static boolean delete(Context ctx, Pack pack) {
        File d = dir(ctx, pack.dir);
        boolean all = true;
        for (String f : pack.files) {
            File file = new File(d, f);
            if (file.exists() && !file.delete()) all = false;
        }
        for (String f : pack.files) {
            new File(d, f + ".tmp").delete();
        }
        return all;
    }

    public static String sizeText(long bytes) {
        if (bytes <= 0) return "0 MB";
        return String.format(Locale.CHINA, "%.1f MB", bytes / 1048576.0);
    }
}
