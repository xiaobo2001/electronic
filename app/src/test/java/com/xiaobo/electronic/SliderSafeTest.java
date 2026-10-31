package com.xiaobo.electronic;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * 滑块刻度对齐：Material Slider 带 stepSize 时只接受 valueFrom + n×stepSize，
 * 差一点就在布局阶段抛 IllegalStateException（弹窗一开就崩，try/catch 兜不住）。
 */
public class SliderSafeTest {

    private static final float FROM = 0f;
    private static final float TO = 100f;
    private static final float STEP = 5f;

    @Test
    public void snapsDefaultTuningValue() {
        // 烟花手感默认值 1.0 → (1.0-0.5)/(2.0-0.5)*100 = 33.333336，就是它崩的
        assertEquals(35f, SliderSafe.snapToStep(33.333336f, FROM, TO, STEP), 0.0001f);
    }

    @Test
    public void snapsFloatRoundingError() {
        // 0.35f*100f 实际是 34.999996，同样不在刻度上
        assertEquals(35f, SliderSafe.snapToStep(0.35f * 100f, FROM, TO, STEP), 0.0001f);
    }

    @Test
    public void keepsValueAlreadyOnTick() {
        assertEquals(40f, SliderSafe.snapToStep(40f, FROM, TO, STEP), 0.0001f);
        assertEquals(0f, SliderSafe.snapToStep(0f, FROM, TO, STEP), 0.0001f);
        assertEquals(100f, SliderSafe.snapToStep(100f, FROM, TO, STEP), 0.0001f);
    }

    @Test
    public void clampsOutOfRange() {
        assertEquals(0f, SliderSafe.snapToStep(-20f, FROM, TO, STEP), 0.0001f);
        assertEquals(100f, SliderSafe.snapToStep(180f, FROM, TO, STEP), 0.0001f);
    }

    @Test
    public void handlesBadInputAndNoStep() {
        assertEquals(0f, SliderSafe.snapToStep(Float.NaN, FROM, TO, STEP), 0.0001f);
        // stepSize 为 0 表示连续取值，不该被改动
        assertEquals(33.333336f, SliderSafe.snapToStep(33.333336f, FROM, TO, 0f), 0.0001f);
    }
}
