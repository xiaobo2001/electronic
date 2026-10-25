package com.xiaobo.electronic;

import android.app.ActivityManager;
import android.content.Context;
import android.os.Build;
import android.util.DisplayMetrics;
import android.view.WindowManager;

/**
 * 设备性能分级与渲染参数调度。
 *
 * 启动时根据内存、CPU 核心数、系统低内存模式、屏幕密度综合判定设备档位，
 * 星空 / 粒子 / 壁纸绘制全部读取本类参数，从而在低端机型上自动降载、提升流畅度。
 *
 * 档位判定只做一次，进程内复用。
 */
public final class DevicePerf {

    public enum Tier {
        HIGH,   // 旗舰/中高端，全特效
        MEDIUM, // 中端机，轻微降载
        LOW     // 低端机/低内存设备，明显降载保流畅
    }

    private static volatile DevicePerf instance;

    private final Tier tier;
    private final String deviceSummary;

    // ===== 渲染参数（按档区分）=====
    /** 星空背景星星数量 */
    public final int starCount;
    /** 粒子文字采样间隔（像素），越大粒子越少 */
    public final int particleSampleSize;
    /** 壁纸绘制帧间隔 ms：16≈60fps，20≈50fps，33≈30fps */
    public final int wallpaperFrameDelayMs;
    /** 壁纸随机烟花间隔基数 ms（在此基础上再加随机扰动） */
    public final int wallpaperFireBaseMs;
    /** 壁纸随机烟花间隔随机幅度 ms */
    public final int wallpaperFireRandomMs;
    /** 星星是否开抗锯齿（小尺寸圆在低端机关闭可省 GPU/CPU） */
    public final boolean starAntiAlias;
    /** 浪漫模式花瓣同时存活上限 */
    public final int maxPetalCount;
    /** 自定义背景图加载采样率 inSampleSize，1=原图 */
    public final int backgroundInSampleSize;
    /** 烟花粒子数量缩放系数（乘以 FireworkConfig.count） */
    public final float fireworkCountScale;
    /** 星空动画帧间隔 ms：0=满帧跟手（旗舰，与原版帧节奏完全一致），33≈30fps（中低端降耗）。
     *  配合 StarView 内的时间补偿，任何实际帧率下星星的移动/闪烁速率都与 60fps 基准一致 */
    public final int starFrameIntervalMs;
    /** 环境流星生成间隔基数 ms（在此之上叠加随机幅度） */
    public final int meteorBaseMs;
    /** 环境流星生成间隔随机幅度 ms */
    public final int meteorRandomMs;
    /** 同屏环境流星数上限（流星仅随机触发） */
    public final int maxMeteors;

    private DevicePerf(Context ctx) {
        Context app = ctx.getApplicationContext();
        ActivityManager am = (ActivityManager) app.getSystemService(Context.ACTIVITY_SERVICE);

        long totalMemBytes = 0;
        boolean lowMemory = false;
        if (am != null) {
            ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
            am.getMemoryInfo(mi);
            totalMemBytes = mi.totalMem;
            lowMemory = mi.lowMemory;
        }
        int cores = Runtime.getRuntime().availableProcessors();

        DisplayMetrics dm = new DisplayMetrics();
        WindowManager wm = (WindowManager) app.getSystemService(Context.WINDOW_SERVICE);
        if (wm != null) {
            wm.getDefaultDisplay().getRealMetrics(dm);
        }
        long totalMb = totalMemBytes / (1024 * 1024);

        // ===== 综合打分：判定档位 =====
        int score = 0;
        // 内存权重
        if (totalMb >= 6 * 1024) score += 2;
        else if (totalMb >= 3 * 1024) score += 1;
        // CPU 核心数权重
        if (cores >= 8) score += 2;
        else if (cores >= 4) score += 1;
        // 系统声明的低内存模式
        if (lowMemory) score -= 2;
        // 屏幕分辨率越高填充压力越大，适度扣分
        long pixels = (long) dm.widthPixels * dm.heightPixels;
        if (pixels >= 1920L * 1080) score -= 1;

        if (score >= 4) {
            tier = Tier.HIGH;
        } else if (score >= 1) {
            tier = Tier.MEDIUM;
        } else {
            tier = Tier.LOW;
        }

        // ===== 按档下发参数 =====
        switch (tier) {
            case HIGH:
            default:
                starCount = 120;
                particleSampleSize = 5;
                wallpaperFrameDelayMs = 16;
                wallpaperFireBaseMs = 400;
                wallpaperFireRandomMs = 1200;
                starAntiAlias = true;
                maxPetalCount = 30;
                backgroundInSampleSize = 1;
                fireworkCountScale = 1.0f;
                starFrameIntervalMs = 0;
                meteorBaseMs = 5000;
                meteorRandomMs = 5000;
                maxMeteors = 2;
                break;
            case MEDIUM:
                starCount = 75;
                particleSampleSize = 6;
                wallpaperFrameDelayMs = 20;
                wallpaperFireBaseMs = 700;
                wallpaperFireRandomMs = 1400;
                starAntiAlias = true;
                maxPetalCount = 18;
                backgroundInSampleSize = 2;
                fireworkCountScale = 0.8f;
                starFrameIntervalMs = 33;
                meteorBaseMs = 7000;
                meteorRandomMs = 6000;
                maxMeteors = 2;
                break;
            case LOW:
                starCount = 40;
                particleSampleSize = 8;
                wallpaperFrameDelayMs = 33;   // 锁 30fps，避免掉帧卡顿
                wallpaperFireBaseMs = 1100;
                wallpaperFireRandomMs = 1800;
                starAntiAlias = false;
                maxPetalCount = 10;
                backgroundInSampleSize = 4;
                fireworkCountScale = 0.55f;
                starFrameIntervalMs = 33;
                meteorBaseMs = 10000;
                meteorRandomMs = 10000;
                maxMeteors = 1;
                break;
        }

        deviceSummary = "DevicePerf{tier=" + tier
                + ", memMB=" + totalMb
                + ", cores=" + cores
                + ", lowMem=" + lowMemory
                + ", screen=" + dm.widthPixels + "x" + dm.heightPixels
                + ", model=" + Build.MODEL + "}";
    }

    public static DevicePerf get(Context ctx) {
        if (instance == null) {
            synchronized (DevicePerf.class) {
                if (instance == null) {
                    instance = new DevicePerf(ctx);
                }
            }
        }
        return instance;
    }

    public Tier tier() {
        return tier;
    }

    public boolean isLowEnd() {
        return tier == Tier.LOW;
    }

    public String summary() {
        return deviceSummary;
    }
}
