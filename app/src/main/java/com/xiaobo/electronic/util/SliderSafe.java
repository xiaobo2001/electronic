package com.xiaobo.electronic.util;

import com.google.android.material.slider.Slider;

/**
 * MaterialSlider 的刻度对齐。
 *
 * <p><b>为什么要这个东西</b>：Material 的 Slider 只要设了 {@code stepSize}，
 * 就会强制校验"当前值必须落在 valueFrom + n × stepSize 的刻度上"，
 * 差一点点就直接在 {@code onSizeChanged} 里抛
 * {@code IllegalStateException}——注意是在<b>布局阶段</b>抛，
 * 所以 try/catch 包 setValue 根本兜不住，弹窗一打开就崩。
 *
 * <p>踩过两次：
 * <ol>
 *   <li>烟花手感：默认系数 1.0 换算成滑块是 (1.0-0.5)/(2.0-0.5)×100 = 33.333336，
 *       而 stepSize=5 → 崩。XML 里写死的 {@code android:value="33.0"} 同样非法。</li>
 *   <li>导入分享码：音量是外部数据（0.33 → 33.0），不在刻度上 → 崩。</li>
 * </ol>
 *
 * <p><b>先除后乘</b>是关键：{@code 0.35f * 100f} 得到的是 34.999996，
 * 直接乘出来的值同样过不了校验；先 {@code round(v / step)} 取整再乘 step，
 * 结果是"整数 × step"，浮点数能精确表示，必过。
 */
public final class SliderSafe {

    private SliderSafe() {
    }

    /** 对齐到刻度（纯函数，可单测）。 */
    public static float snapToStep(float value, float from, float to, float step) {
        float v = value;
        if (Float.isNaN(v) || Float.isInfinite(v)) v = from;
        v = Math.max(from, Math.min(to, v));
        if (step <= 0f) return v;
        float snapped = from + Math.round((v - from) / step) * step;
        // 极端情况下取整会越界（如 to 不是刻度整数倍），再夹一次
        return Math.max(from, Math.min(to, snapped));
    }

    /** 按 Slider 自己的 valueFrom/valueTo/stepSize 对齐。 */
    public static float snap(Slider slider, float value) {
        if (slider == null) return value;
        return snapToStep(value, slider.getValueFrom(), slider.getValueTo(), slider.getStepSize());
    }

    /** 安全赋值：先对齐刻度再 setValue。 */
    public static void setValue(Slider slider, float value) {
        if (slider == null) return;
        slider.setValue(snap(slider, value));
    }
}
