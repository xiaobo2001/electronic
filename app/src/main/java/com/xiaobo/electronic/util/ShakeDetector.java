package com.xiaobo.electronic.util;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

/**
 * 【#16】摇一摇检测：加速度瞬时幅度超阈值（考虑重力）判定为摇晃。
 * 使用后必须 unregister。
 */
public class ShakeDetector implements SensorEventListener {

    public interface OnShakeListener {
        void onShake();
    }

    private static final float THRESHOLD = 23f;    // m/s²（减去重力后的合力）
    private static final long COOLDOWN_MS = 600L; // 两次摇晃最小间隔

    private final SensorManager sensorManager;
    private final Sensor accelerometer;
    private final OnShakeListener listener;
    private long lastShakeMs = 0L;
    private boolean registered = false;

    public ShakeDetector(Context context, OnShakeListener listener) {
        this.sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        this.accelerometer = sensorManager != null
                ? sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) : null;
        this.listener = listener;
    }

    public void register() {
        if (registered || accelerometer == null || sensorManager == null) return;
        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI);
        registered = true;
    }

    public void unregister() {
        if (!registered || sensorManager == null) return;
        sensorManager.unregisterListener(this);
        registered = false;
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() != Sensor.TYPE_ACCELEROMETER) return;
        float x = event.values[0], y = event.values[1], z = event.values[2];
        // 去重力的合力幅度
        float g = SensorManager.STANDARD_GRAVITY;
        float dx = x - 0, dy = y, dz = z - g;
        float magnitude = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (magnitude < THRESHOLD) return;
        long now = System.currentTimeMillis();
        if (now - lastShakeMs < COOLDOWN_MS) return;
        lastShakeMs = now;
        if (listener != null) listener.onShake();
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }
}
