package com.xiaobo.electronic.util;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import com.xiaobo.electronic.App;

/**
 * 崩溃日志本地留存。
 *
 * <p>这个 App 的使用场景是表白、求婚——大概率一辈子就用一次，失败一次就永久失去这个用户，
 * 而且你永远不会知道。<b>线上没有任何可观测性是目前唯一的 P0</b>：接入第三方监控需要
 * 申请账号与 Key，在那之前，至少要把崩溃现场留在用户手机里，让他能一键发给你。
 *
 * <p>只做三件事：捕获未处理异常写文件、保留最近若干份、供 UI 查看与分享。
 * 不做上报（不联网、不申请任何权限）。
 */
public final class CrashLogger {

    private static final String DIR = "crash";
    private static final int MAX_FILES = 5;

    private static Context appContext;
    private static Thread.UncaughtExceptionHandler defaultHandler;
    private static boolean installed;

    private CrashLogger() {
    }

    public static void install(Context context) {
        if (installed) return;
        appContext = context.getApplicationContext();
        defaultHandler = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler(CrashLogger::handle);
        installed = true;
    }

    private static void handle(Thread thread, Throwable ex) {
        try {
            write(thread, ex);
        } catch (Throwable ignored) {
            // 写日志失败绝不能再抛：否则会把原始异常吞掉
        }
        // 交给系统默认处理（弹"已停止运行"并结束进程），不改变原有行为
        if (defaultHandler != null) {
            defaultHandler.uncaughtException(thread, ex);
        }
    }

    private static void write(Thread thread, Throwable ex) {
        File dir = new File(appContext.getFilesDir(), DIR);
        if (!dir.exists() && !dir.mkdirs()) return;
        String name = "crash_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
                .format(new Date()) + ".txt";
        File out = new File(dir, name);

        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        pw.println("=== 设备 ===");
        pw.println("机型: " + Build.MANUFACTURER + " " + Build.MODEL);
        pw.println("系统: Android " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")");
        pw.println("应用: " + versionName());
        pw.println("时间: " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA)
                .format(new Date()));
        pw.println("线程: " + (thread == null ? "?" : thread.getName()));
        pw.println();
        pw.println("=== 异常 ===");
        if (ex != null) {
            ex.printStackTrace(pw);
            Throwable cause = ex.getCause();
            while (cause != null) {
                pw.println();
                pw.println("--- Caused by ---");
                cause.printStackTrace(pw);
                cause = cause.getCause();
            }
        }
        pw.close();

        FileOutputStream fos = null;
        try {
            fos = new FileOutputStream(out);
            try {
                fos.write(sw.toString().getBytes("UTF-8"));
            } catch (Throwable ignored) {
            }
        } catch (Throwable ignored) {
            // 写不进去就算了，绝不能因为日志把进程二次搞崩
        } finally {
            if (fos != null) {
                try {
                    fos.close();
                } catch (Throwable ignored) {
                }
            }
        }
        trimOld(dir);
    }

    /** 只保留最近 MAX_FILES 份，避免日志文件无限堆积 */
    private static void trimOld(File dir) {
        File[] fs = dir.listFiles();
        if (fs == null || fs.length <= MAX_FILES) return;
        Arrays.sort(fs, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
        for (int i = MAX_FILES; i < fs.length; i++) {
            try {
                fs[i].delete();
            } catch (Throwable ignored) {
            }
        }
    }

    public static List<File> files() {
        List<File> out = new ArrayList<>();
        if (appContext == null) return out;
        File dir = new File(appContext.getFilesDir(), DIR);
        File[] fs = dir.listFiles();
        if (fs == null) return out;
        out.addAll(Arrays.asList(fs));
        out.sort(Comparator.comparingLong(File::lastModified).reversed());
        return out;
    }

    public static boolean hasCrash() {
        return !files().isEmpty();
    }

    /** 最近一次崩溃的完整文本（设备信息 + 堆栈） */
    public static String latestText() {
        List<File> fs = files();
        if (fs.isEmpty()) return "";
        try {
            java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
            java.io.FileInputStream in = new java.io.FileInputStream(fs.get(0));
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
            in.close();
            return new String(bos.toByteArray(), "UTF-8");
        } catch (Throwable t) {
            return "读取失败：" + t;
        }
    }

    public static void clearAll() {
        for (File f : files()) {
            try {
                f.delete();
            } catch (Throwable ignored) {
            }
        }
    }

    private static String versionName() {
        try {
            PackageManager pm = appContext.getPackageManager();
            PackageInfo pi = pm.getPackageInfo(appContext.getPackageName(), 0);
            return pi.versionName + " (" + pi.versionCode + ")";
        } catch (Throwable t) {
            return "未知";
        }
    }
}
