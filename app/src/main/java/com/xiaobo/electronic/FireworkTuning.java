package com.xiaobo.electronic;

import com.tencent.mmkv.MMKV;
import com.xiaobo.fireworks.FireworkConfig;

/**
 * 烟花手感调参：密度 / 速度 / 下坠感 / 拖尾。
 *
 * <p>库把重力写死在粒子步进里（每帧 {@code vy += 0.15f}），没有对外接口，
 * 所以"重力"这一项用<b>滞空时长</b>近似：下坠感越强，粒子寿命越短、越快熄灭，
 * 观感上就是"炸开后迅速坠下来"。四项都是乘法系数，
 * 默认值 1.0 与调参前完全一致——不想要的人完全察觉不到这个功能存在。
 */
public final class FireworkTuning {

    private static final String KEY_DENSITY = "tune_density";
    private static final String KEY_SPEED = "tune_speed";
    private static final String KEY_FALL = "tune_fall";
    private static final String KEY_TRAIL = "tune_trail";

    public static final float MIN_DENSITY = 0.5f;
    public static final float MAX_DENSITY = 2.0f;
    public static final float MIN_SPEED = 0.6f;
    public static final float MAX_SPEED = 1.6f;
    public static final float MIN_FALL = 0.6f;
    public static final float MAX_FALL = 1.6f;
    public static final float MIN_TRAIL = 0.2f;
    public static final float MAX_TRAIL = 2.5f;

    private FireworkTuning() {
    }

    public static float density() {
        return clamp(MMKV.defaultMMKV().decodeFloat(KEY_DENSITY, 1f), MIN_DENSITY, MAX_DENSITY);
    }

    public static float speed() {
        return clamp(MMKV.defaultMMKV().decodeFloat(KEY_SPEED, 1f), MIN_SPEED, MAX_SPEED);
    }

    public static float fall() {
        return clamp(MMKV.defaultMMKV().decodeFloat(KEY_FALL, 1f), MIN_FALL, MAX_FALL);
    }

    public static float trail() {
        return clamp(MMKV.defaultMMKV().decodeFloat(KEY_TRAIL, 1f), MIN_TRAIL, MAX_TRAIL);
    }

    public static void setDensity(float v) {
        MMKV.defaultMMKV().encode(KEY_DENSITY, clamp(v, MIN_DENSITY, MAX_DENSITY));
    }

    public static void setSpeed(float v) {
        MMKV.defaultMMKV().encode(KEY_SPEED, clamp(v, MIN_SPEED, MAX_SPEED));
    }

    public static void setFall(float v) {
        MMKV.defaultMMKV().encode(KEY_FALL, clamp(v, MIN_FALL, MAX_FALL));
    }

    public static void setTrail(float v) {
        MMKV.defaultMMKV().encode(KEY_TRAIL, clamp(v, MIN_TRAIL, MAX_TRAIL));
    }

    public static void reset() {
        MMKV mmkv = MMKV.defaultMMKV();
        mmkv.encode(KEY_DENSITY, 1f);
        mmkv.encode(KEY_SPEED, 1f);
        mmkv.encode(KEY_FALL, 1f);
        mmkv.encode(KEY_TRAIL, 1f);
    }

    public static boolean isDefault() {
        return density() == 1f && speed() == 1f && fall() == 1f && trail() == 1f;
    }

    /**
     * 把系数作用到一个配置上。
     *
     * <p><b>只对新建的配置调用一次</b>：这是乘法，重复作用会让粒子数指数增长。
     * call 点统一收在烟花池重建之后。
     */
    public static void apply(FireworkConfig cfg) {
        if (cfg == null) return;
        cfg.particleCount = Math.max(16, Math.round(cfg.particleCount * density()));
        cfg.explosionRange = Math.max(0.2f, cfg.explosionRange * speed());
        cfg.duration = Math.max(0.6f, cfg.duration / fall());
        cfg.trailLength = Math.max(0f, cfg.trailLength * trail());
    }

    public static void applyAll(FireworkConfig[] cfgs) {
        if (cfgs == null) return;
        for (FireworkConfig c : cfgs) apply(c);
    }

    /** 菜单副标题：默认档只写"标准"，改过才显示具体档位。 */
    public static String summary() {
        if (isDefault()) return "标准";
        return "密" + pct(density(), MIN_DENSITY, MAX_DENSITY)
                + " 快" + pct(speed(), MIN_SPEED, MAX_SPEED)
                + " 坠" + pct(fall(), MIN_FALL, MAX_FALL)
                + " 尾" + pct(trail(), MIN_TRAIL, MAX_TRAIL);
    }

    private static int pct(float v, float min, float max) {
        return Math.round((v - min) / (max - min) * 100);
    }

    private static float clamp(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
