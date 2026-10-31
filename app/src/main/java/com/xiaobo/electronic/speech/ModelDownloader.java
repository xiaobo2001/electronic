package com.xiaobo.electronic.speech;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;
import com.xiaobo.electronic.App;

/**
 * 按需下载模型的通用引擎（ASR 识别模型 / TTS 音色模型共用）。
 *
 * <p><b>为什么是"按需下载"而不是内置。</b>内置一套模型就多 20MB 以上的 APK 体积，
 * 而这些能力多数人一辈子只用一两次；但完全不做，国内 ROM 上点了就是静默失败
 * （Google 的服务用不了）。所以折中：确认需要时才下，下完永久离线可用。
 *
 * <p><b>三条硬约束：</b>
 * <ul>
 *   <li>先写 {@code .tmp} 再 rename——断网不会留下半截文件被误判成"已就绪"；</li>
 *   <li>{@link Progress#isCancelled()} 每文件检查——用户点了取消，后台不许继续偷跑；</li>
 *   <li>主源失败自动换备用源——国内直连 HuggingFace 原站时好时坏。</li>
 * </ul>
 */
public final class ModelDownloader {

    /** 下载进度与结果回调（一律回主线程，UI 可直接操作控件） */
    public interface Progress {
        /** done/total 为字节数 */
        void onProgress(long done, long total);

        /**
         * 切换正在下载的文件时回调，用于在界面上写清"正在下第几个/哪个文件"。
         * 没有它，20MB 的下载在慢网下就是一根一动不动的进度条，
         * 用户分不清是"在下载"还是"已经死了"。
         */
        default void onStage(String fileName, int index, int count) {
        }

        void onDone(boolean ok, String message);

        /**
         * 是否已取消。返回 true 时下载线程会在当前文件收尾后停下。
         * 不做这个的话，用户点了"取消"，后台还会把剩下的 20MB 悄悄下完。
         */
        default boolean isCancelled() {
            return false;
        }
    }

    /** 一套模型的完整描述：放哪儿、有哪些文件、多大、从哪儿下 */
    public static final class Pack {
        final String dir;
        final String[] files;
        final long[] sizes;
        final String[] bases;
        /**
         * 每个文件优先用哪个源（下标指向 {@link #bases}）。
         * 为 null 表示全部用 bases[0]。
         * 存在的原因是有的模型要借别家仓库的文件（例如 AISHELL3 仓库不带
         * 中文分词词典，得从另一个仓库取），硬塞一个 base 解决不了。
         */
        final int[] baseIdx;
        /**
         * 整包 zip 直链（放国内网盘，如蓝奏云）。null 表示只有逐文件下载。
         *
         * <p><b>为什么要有整包这条路。</b>HuggingFace 的镜像在国内是"能下但慢，
         * 还时不时断"，20MB 的模型要下好几分钟。整包放在国内网盘上是
         * 一个文件、一次连接、满速下载，体验完全不同。
         *
         * <p><b>为什么又不能只留整包。</b>网盘直链是逆向接口，域名一年换好几次，
         * 写死在 App 里，链接一失效所有用户都下不了，而修只能靠发版。
         * 所以整包只是"加速源"，失败就静默回退逐文件下载——那个源是官方的，
         * 只要仓库还在就一直能用。
         */
        final String[] zipUrls;
        /** zip 包体积，用于进度条分母；0 表示不校验 */
        final long zipBytes;

        Pack(String dir, String[] files, long[] sizes, String... bases) {
            this(dir, files, sizes, bases, null);
        }

        Pack(String dir, String[] files, long[] sizes, String[] bases, int[] baseIdx) {
            this(dir, files, sizes, bases, baseIdx, null, 0L);
        }

        Pack(String dir, String[] files, long[] sizes, String[] bases, int[] baseIdx,
             String[] zipUrls, long zipBytes) {
            this.dir = dir;
            this.files = files;
            this.sizes = sizes;
            this.bases = bases;
            this.baseIdx = baseIdx;
            this.zipUrls = zipUrls;
            this.zipBytes = zipBytes;
        }
    }

    private ModelDownloader() {
    }

    static File dir(Context ctx, String name) {
        return new File(ctx.getFilesDir(), name);
    }

    /** 单文件连接/读取超时：慢网下 20 秒没数据就换源，别让用户对着静止的进度条发呆 */
    private static final int CONNECT_TIMEOUT = 12_000;
    private static final int READ_TIMEOUT = 20_000;
    /** 同一个源最多试几次（中途断连靠 Range 续传接上，不是从头再来） */
    private static final int TRIES_PER_BASE = 2;

    /**
     * 判断一个文件是否已经完整。
     *
     * <p><b>为什么不能写死"大于 1024 字节"。</b>模型包里最小的文件是 tokens.txt，
     * 只有 485 字节。早期版本用固定 1024 阈值当"非空文件"判据，结果
     * 20MB 的 onnx 和 2MB 的词典都下完了，偏偏卡在这个 485 字节的小文件上：
     * 下载明明成功，却被判成空文件、抛异常、换源重来，最后报"下载失败"。
     * 用户看到的就是"进度停在 19.8MB 然后失败"——前面 19.8MB 其实一直是好的。
     *
     * <p>现在改为按清单里的期望大小校验：容差 2%（最小 64 字节），
     * 既能容忍上游文件微调，也能挡住只下了半截的残缺文件。
     */
    public static boolean complete(File f, long expect) {
        if (f == null || !f.isFile()) return false;
        long len = f.length();
        if (len <= 0) return false;
        if (expect <= 0) return len >= 1024L;      // 没给期望值，退回保守阈值
        long tol = Math.max(64L, expect / 50L);
        return len >= expect - tol && len <= expect + tol;
    }

    /** 所有文件都在、且每个都完整（下载中断会留下半截文件，必须按大小校验） */
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
     * 下载整套模型（后台线程，回调回主线程）。
     * 已存在且大小与预期一致的文件会跳过，重复调用不会白跑流量。
     */
    static void download(Context ctx, Pack pack, Progress cb) {
        final Handler ui = new Handler(Looper.getMainLooper());
        final long[] done = {0L};
        final long total = totalBytes(pack);
        final int n = pack.files.length;
        new Thread(() -> {
            File d = dir(ctx, pack.dir);
            if (!d.exists() && !d.mkdirs()) {
                ui.post(() -> cb.onDone(false, "存储空间不足"));
                return;
            }
            // 先试国内网盘整包：一次连接拿全套，通常几秒到几十秒
            if (!isReady(ctx, pack)) {
                tryBundle(ctx, pack, ui, cb);
                if (cb.isCancelled()) {
                    ui.post(() -> cb.onDone(false, "已取消下载"));
                    return;
                }
                if (isReady(ctx, pack)) {
                    ui.post(() -> cb.onProgress(total, total));
                    ui.post(() -> cb.onDone(true, "模型就绪"));
                    return;
                }
            }
            if (isReady(ctx, pack)) {
                ui.post(() -> cb.onDone(true, "模型就绪"));
                return;
            }

            // 走到这里说明整包没成（没配、链接失效、或包不对）：逐文件下载兜底
            // 已下完的、以及上次中断残留的半成品，都计入起始进度：
            // 界面不会从 0 重新开始，也不会白下第二遍
            long[] have = new long[n];
            for (int i = 0; i < n; i++) {
                File f = new File(d, pack.files[i]);
                if (complete(f, size(pack, i))) {
                    have[i] = f.length();
                } else {
                    File t = new File(f.getAbsolutePath() + ".tmp");
                    if (t.isFile()) have[i] = t.length();   // 断点续传的起点
                }
                done[0] += Math.min(have[i], Math.max(size(pack, i), 1L));
            }

            for (int i = 0; i < n; i++) {
                if (cb.isCancelled()) {
                    ui.post(() -> cb.onDone(false, "已取消下载"));
                    return;
                }
                long expect = size(pack, i);
                File out = new File(d, pack.files[i]);
                if (complete(out, expect)) continue;   // 断点续跑

                final String name = pack.files[i];
                final int idx = i;
                ui.post(() -> cb.onStage(name, idx + 1, n));

                // base：本文件之前累计完成的字节数。
                // 换成源或重试时，进度必须回到这个基准，否则会把同一个文件算两遍。
                long base = done[0] - Math.min(have[i], Math.max(expect, 1L));

                String err = null;
                for (int k = 0; k < pack.bases.length; k++) {
                    int preferred = (pack.baseIdx != null && i < pack.baseIdx.length)
                            ? pack.baseIdx[i] : 0;
                    int bi;
                    if (k == 0) {
                        bi = preferred;
                    } else if (pack.baseIdx != null && i < pack.baseIdx.length) {
                        // 这个文件指定了专属仓库（例如从别家借的词典）：
                        // 只在"镜像 ↔ 原站"之间切，跳去别的仓库必然 404
                        if (k > 1) break;
                        bi = preferred ^ 1;
                    } else {
                        bi = (preferred + k) % pack.bases.length;
                    }
                    if (bi < 0 || bi >= pack.bases.length) continue;
                    for (int t = 0; t < TRIES_PER_BASE; t++) {
                        if (cb.isCancelled()) {
                            ui.post(() -> cb.onDone(false, "已取消下载"));
                            return;
                        }
                        try {
                            fetchOne(pack.bases[bi] + name, out, expect,
                                    base, done, total, ui, cb);
                            err = null;
                            break;
                        } catch (Exception e) {
                            err = describe(e);
                            String m = e.getMessage();
                            // 416 = 续传偏移越界（多半是上次残留的 tmp 比真实文件还长）
                            // 这种情况扔掉半成品从头下，否则换多少个源都是同一个错
                            if (m != null && m.contains("416")) {
                                dropPartial(out);
                                have[i] = 0L;
                            }
                            if (fatal(e)) break;          // 404 之类，换源也没用
                            sleepQuiet(800L * (t + 1));   // 抖动一下就退避重试
                        }
                    }
                    if (err == null) break;
                }
                if (err != null) {
                    // 说清楚卡在哪个文件、为什么：只有"下载失败"四个字没法排查
                    final String msg = name + "（" + err + "）";
                    ui.post(() -> cb.onDone(false, msg));
                    return;
                }
            }
            ui.post(() -> cb.onDone(true, "模型就绪"));
        }, "model-download").start();
    }

    /**
     * 走整包 zip（国内网盘直链）：下一次连接拿一个压缩包，解压即得全套模型。
     *
     * <p>失败一律静默——调用方会接着走逐文件下载，用户最多是慢一点，
     * 不会看到"下载失败"。这是把它当加速源而不是唯一源的意义所在。
     */
    private static void tryBundle(Context ctx, Pack pack, Handler ui, Progress cb) {
        if (pack.zipUrls == null || pack.zipUrls.length == 0) return;
        File zip = new File(ctx.getCacheDir(), pack.dir + ".zip");
        if (zip.exists()) zip.delete();
        for (String url : pack.zipUrls) {
            if (url == null || url.isEmpty()) continue;
            if (cb.isCancelled()) return;
            final long[] one = {0L};
            final long total = pack.zipBytes > 0 ? pack.zipBytes : 1L;
            ui.post(() -> cb.onStage("整包 " + pack.dir + ".zip", 1, 1));
            try {
                fetchOne(url, zip, pack.zipBytes, 0L, one, total, ui, cb, BROWSER_HEADERS);
                File dest = dir(ctx, pack.dir);
                if (!dest.exists() && !dest.mkdirs()) continue;
                unzip(zip, dest, MAX_UNZIP_BYTES);
                if (isReady(ctx, pack)) {
                    zip.delete();     // 解压成功就别留着占空间
                    return;
                }
                // 包里的内容不对（链接换了/包坏了）：清干净，交给逐文件下载兜底
                delete(ctx, pack);
            } catch (Exception ignored) {
                // 换下一个直链；都失败就回退
            }
        }
        if (zip.exists()) zip.delete();
    }

    /** 网盘直链常见的校验：UA 不是浏览器就给你返回一个下载页的 HTML，而不是文件 */
    private static final String[] BROWSER_HEADERS = {
            "User-Agent",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36",
    };

    /** 解压上限：压缩包再怎么异常也不能把用户存储写满 */
    private static final long MAX_UNZIP_BYTES = 512L * 1024 * 1024;

    /**
     * 解压 zip 到目标目录。
     *
     * <p>两条必须做的防护：
     * <ul>
     *   <li><b>路径穿越</b>：zip 里的 {@code ../../etc/passwd} 会让解压写到目录外面去，
     *       每解压一个条目都要确认它落在目标目录内；</li>
     *   <li><b>解压炸弹</b>：一个 20MB 的包声称能解出 50GB，不设上限能把存储写满。</li>
     * </ul>
     */
    public static void unzip(File zipFile, File destDir, long maxBytes) throws IOException {
        byte[] buf = new byte[64 * 1024];
        long written = 0L;
        String destRoot = destDir.getCanonicalPath();
        try (java.util.zip.ZipInputStream zis =
                     new java.util.zip.ZipInputStream(new java.io.BufferedInputStream(
                             new java.io.FileInputStream(zipFile)))) {
            java.util.zip.ZipEntry e;
            while ((e = zis.getNextEntry()) != null) {
                if (e.isDirectory()) continue;
                File out = new File(destDir, e.getName());
                String outPath = out.getCanonicalPath();
                if (!outPath.startsWith(destRoot + File.separator)
                        && !outPath.equals(destRoot)) {
                    throw new IOException("zip 条目越界: " + e.getName());
                }
                File parent = out.getParentFile();
                if (parent != null && !parent.exists() && !parent.mkdirs()) {
                    throw new IOException("mkdirs failed: " + parent);
                }
                try (java.io.FileOutputStream fos = new java.io.FileOutputStream(out)) {
                    int n;
                    while ((n = zis.read(buf)) > 0) {
                        fos.write(buf, 0, n);
                        written += n;
                        if (written > maxBytes) {
                            throw new IOException("解压体积异常，已中止");
                        }
                    }
                }
                zis.closeEntry();
            }
        }
    }

    private static long size(Pack pack, int i) {
        return (i < pack.sizes.length) ? pack.sizes[i] : 0L;
    }

    /** 丢掉某个文件的半成品，下次从头下载 */
    private static void dropPartial(File out) {
        File t = new File(out.getAbsolutePath() + ".tmp");
        if (t.exists()) t.delete();
    }

    private static void sleepQuiet(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** 4xx（除 408/429）换源也没用，别白等一轮超时 */
    public static boolean fatal(Exception e) {
        String m = e.getMessage();
        if (m == null || !m.startsWith("HTTP ")) return false;
        try {
            int c = Integer.parseInt(m.substring(5).trim());
            if (c == 408 || c == 429) return false;
            return c >= 400 && c < 500;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    private static String describe(Exception e) {
        String m = e.getMessage();
        if (m == null || m.isEmpty()) return e.getClass().getSimpleName();
        if (m.startsWith("HTTP ")) return "服务器 " + m.substring(5).trim();
        if (m.contains("timeout") || m.contains("timed out")) return "网络超时";
        if (m.contains("UnknownHost") || m.contains("ConnectException")
                || m.contains("ECONNREFUSED")) return "连不上服务器";
        return m;
    }

    /**
     * 下载单个文件，支持断点续传。
     *
     * <p>已有半成品 {@code .tmp} 时带 {@code Range: bytes=<len>-} 请求，
     * 服务端返回 206 就接着写；返回 200（不支持 Range）则删掉从头下。
     * 20MB 的文件在国内网络下断一次很常见，没有续传就得从头再来一遍。
     *
     * @param base 进入本文件前已累计的字节数，用于重试时把进度拨回正确位置
     */
    private static void fetchOne(String url, File out, long expect, long base,
                                 long[] done, long total, Handler ui,
                                 Progress cb) throws Exception {
        fetchOne(url, out, expect, base, done, total, ui, cb, null);
    }

    /**
     * @param headers 额外的请求头（k/v 交替），用于网盘直链需要的 UA / Referer
     */
    private static void fetchOne(String url, File out, long expect, long base,
                                 long[] done, long total, Handler ui,
                                 Progress cb, String[] headers) throws Exception {
        File tmp = new File(out.getAbsolutePath() + ".tmp");
        File parent = out.getParentFile();
        // 文件可能带子目录（如 dict/jieba.dict.utf8），父目录要先建出来
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new java.io.IOException("mkdirs failed: " + parent);
        }

        // 上次已经下完整、只是还没来得及改名（进程被杀 / 断电）的半成品，直接认下来
        if (tmp.isFile() && complete(tmp, expect)) {
            if (out.exists()) out.delete();
            if (!tmp.renameTo(out)) {
                throw new java.io.IOException("rename failed");
            }
            final long d = base + Math.max(expect, out.length());
            ui.post(() -> cb.onProgress(d, total));
            return;
        }

        long start = 0L;
        if (tmp.isFile()) {
            long len = tmp.length();
            if (len > 0 && (expect <= 0 || len < expect)) {
                start = len;                       // 接着上次下
            } else if (!tmp.delete()) {
                start = 0L;
            }
        }

        HttpURLConnection conn = null;
        InputStream in = null;
        FileOutputStream fos = null;
        boolean renamed = false;
        boolean interrupted = false;
        try {
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setConnectTimeout(CONNECT_TIMEOUT);
            conn.setReadTimeout(READ_TIMEOUT);
            conn.setInstanceFollowRedirects(true);   // hf 的 resolve 会 302 到 CDN
            // 明确不要 gzip：Content-Length 与 Range 组合再叠一层压缩最容易算错长度
            conn.setRequestProperty("Accept-Encoding", "identity");
            if (headers != null) {
                for (int h = 0; h + 1 < headers.length; h += 2) {
                    conn.setRequestProperty(headers[h], headers[h + 1]);
                }
            }
            if (start > 0) conn.setRequestProperty("Range", "bytes=" + start + "-");

            int code = conn.getResponseCode();
            boolean append = false;
            if (code == 206) {
                append = start > 0;
            } else if (code == HttpURLConnection.HTTP_OK) {
                // 服务端不支持续传，只能从头来，先把旧的半成品扔掉
                if (tmp.exists()) tmp.delete();
                start = 0L;
            } else {
                throw new java.io.IOException("HTTP " + code);
            }

            in = conn.getInputStream();
            fos = new FileOutputStream(tmp, append);
            byte[] buf = new byte[64 * 1024];
            int rd;
            long got = start;
            long lastPost = 0L;
            while ((rd = in.read(buf)) > 0) {
                if (cb.isCancelled()) {
                    interrupted = true;
                    break;
                }
                fos.write(buf, 0, rd);
                got += rd;
                // 进度别刷太勤，每 120ms 一次足够，省得主线程被 post 淹了
                long now = System.currentTimeMillis();
                if (now - lastPost > 120L) {
                    lastPost = now;
                    final long d = base + Math.min(got, Math.max(expect, 1L));
                    ui.post(() -> cb.onProgress(d, total));
                }
            }
            fos.flush();
            try {
                fos.getFD().sync();     // 落盘，避免拔电源后剩个空文件
            } catch (Exception ignored) {
            }
            fos.close();
            fos = null;

            if (interrupted) {
                // 用户取消：留着半成品，下次点下载能接着下
                throw new java.io.InterruptedIOException("cancelled");
            }
            if (!complete(tmp, expect)) {
                throw new java.io.IOException("文件不完整 " + tmp.length() + "/" + expect);
            }
            if (out.exists()) out.delete();
            if (!tmp.renameTo(out)) {
                throw new java.io.IOException("rename failed");
            }
            renamed = true;
            final long d = base + Math.max(expect, out.length());
            ui.post(() -> cb.onProgress(d, total));
        } finally {
            if (in != null) try {
                in.close();
            } catch (Exception ignored) {
            }
            if (fos != null) try {
                fos.close();
            } catch (Exception ignored) {
            }
            if (conn != null) conn.disconnect();
            // 只有"中断/取消"才保留半成品供续传；
            // 其它失败（内容校验不过、rename 失败）说明数据本身有问题，删掉重来
            if (!renamed && !interrupted && tmp.exists()) {
                tmp.delete();
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
