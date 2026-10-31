package com.xiaobo.electronic.media;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.net.Uri;
import android.provider.MediaStore;
import android.content.ContentValues;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.media.MediaMuxer;
import android.media.MediaScannerConnection;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import com.tencent.mmkv.MMKV;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Surface;
import android.view.WindowManager;
import com.hjq.toast.Toaster;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import com.xiaobo.electronic.App;
import com.xiaobo.electronic.R;

/**
 * 屏幕录制前台服务：把当前画面编码成 H.264 MP4。
 */
public class ScreenRecorderService extends Service {

    private static final String TAG = "ScreenRecorder";
    public static final String ACTION_STOP = "com.xiaobo.electronic.STOP_RECORD";
    public static final String ACTION_STOPPED = "com.xiaobo.electronic.RECORD_STOPPED";
    public static final String ACTION_FAILED = "com.xiaobo.electronic.RECORD_FAILED";
    public static final String EXTRA_RESULT_CODE = "result_code";
    public static final String EXTRA_RESULT_DATA = "result_data";
    public static final String EXTRA_OUTPUT_PATH = "output_path";
    /** 写入公共相册后的 content:// Uri（可能为 null：Android 10 以下或未入册成功） */
    public static final String EXTRA_OUTPUT_URI = "output_uri";
    public static final String EXTRA_ERROR = "error";

    private static final String CHANNEL_ID = "romantic_recorder";
    private static final int NOTI_ID = 20240501;
    private static final int FRAME_RATE = 30;
    private static final int IFRAME_INTERVAL = 2;

    private MediaProjection mediaProjection;
    private MediaCodec mediaCodec;
    private VirtualDisplay virtualDisplay;
    private Surface inputSurface;
    private MediaMuxer mediaMuxer;
    private HandlerThread encoderThread;
    private Handler encoderHandler;
    // 【修复】Android 14+（API 34）强制要求：createVirtualDisplay 前必须注册 Callback，
    // 否则抛 IllegalStateException: Must register a callback before starting capture
    private MediaProjection.Callback projectionCallback;
    private boolean callbackRegistered = false;

    private int videoWidth;
    private int videoHeight;
    private boolean muxerStarted = false;
    private int videoTrackIndex = -1;
    private boolean isStopping = false;
    private int framesWritten = 0;
    private String errorMsg = null;
    private File outputFile;

    private final MediaCodec.Callback encoderCallback = new MediaCodec.Callback() {
        @Override
        public void onInputBufferAvailable(MediaCodec codec, int index) { }

        @Override
        public void onOutputBufferAvailable(MediaCodec codec, int index, MediaCodec.BufferInfo info) {
            try {
                ByteBuffer out = codec.getOutputBuffer(index);
                if (out == null) return;
                if ((info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                    info.size = 0;
                }
                if (info.size > 0 && out != null) {
                    if (!muxerStarted) {
                        Log.w(TAG, "muxer not started yet, drop frame");
                    } else {
                        out.position(info.offset);
                        out.limit(info.offset + info.size);
                        mediaMuxer.writeSampleData(videoTrackIndex, out, info);
                        framesWritten++;
                    }
                }
                codec.releaseOutputBuffer(index, false);
                if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    Log.i(TAG, "EOS reached, framesWritten=" + framesWritten);
                    finishRecording();
                }
            } catch (Exception e) {
                Log.e(TAG, "encoder output error", e);
            }
        }

        @Override
        public void onError(MediaCodec codec, MediaCodec.CodecException e) {
            Log.e(TAG, "encoder error", e);
            errorMsg = "编码错误: " + e.getMessage();
            finishRecording();
        }

        @Override
        public void onOutputFormatChanged(MediaCodec codec, MediaFormat format) {
            try {
                videoTrackIndex = mediaMuxer.addTrack(format);
                mediaMuxer.start();
                muxerStarted = true;
                Log.i(TAG, "muxer started, track=" + videoTrackIndex);
            } catch (Exception e) {
                Log.e(TAG, "muxer start failed", e);
                errorMsg = "muxer 启动失败";
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        encoderThread = new HandlerThread("RecorderEncoder");
        encoderThread.start();
        encoderHandler = new Handler(encoderThread.getLooper());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopRecording();
            return START_NOT_STICKY;
        }

        startForegroundNotified();

        int resultCode = intent != null ? intent.getIntExtra(EXTRA_RESULT_CODE, 0) : 0;
        Intent resultData = intent != null ? intent.getParcelableExtra(EXTRA_RESULT_DATA) : null;

        if (resultData == null) {
            Toaster.showShort("录屏授权无效");
            stopSelf();
            return START_NOT_STICKY;
        }

        try {
            MediaProjectionManager pm =
                    (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
            mediaProjection = pm.getMediaProjection(resultCode, resultData);
            if (mediaProjection == null) {
                Toaster.showShort("无法获得录屏权限");
                stopSelf();
                return START_NOT_STICKY;
            }
            // 【修复】注册系统要求的回调（Android 14+ 强制），系统停止投屏时安全收尾
            projectionCallback = new MediaProjection.Callback() {
                @Override
                public void onStop() {
                    Log.w(TAG, "MediaProjection stopped by system, finishing recording");
                    finishRecording();
                }
            };
            mediaProjection.registerCallback(projectionCallback, null);
            callbackRegistered = true;
            prepareOutput();
            setupEncoder();
            startVirtualDisplay();
            Log.i(TAG, "recording started -> " + outputFile.getAbsolutePath()
                    + " (" + videoWidth + "x" + videoHeight + ")");
        } catch (Exception e) {
            Log.e(TAG, "start failed", e);
            errorMsg = e.getMessage();
            finishRecording();
        }
        return START_NOT_STICKY;
    }

    private void startForegroundNotified() {
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(CHANNEL_ID, "浪漫视频录制",
                    NotificationManager.IMPORTANCE_LOW);
            nm.createNotificationChannel(ch);
        }
        Notification n = new Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("正在录制浪漫视频")
                .setContentText("录制完成后自动保存")
                .setSmallIcon(R.drawable.ic_heart_petal)
                .setOngoing(true)
                .build();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTI_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
        } else {
            startForeground(NOTI_ID, n);
        }
    }

    /**
     * 输出文件准备。
     *
     * Android 10（API 29）起分区存储生效：App 不能直接往公共 Movies/ 目录 new File() 写东西，
     * 原实现在 targetSdk 35 上必然抛 FileNotFoundException，录制 100% 失败。
     * 改为先编码到 App 私有目录（免权限、始终可写），录制成功后再经 MediaStore
     * 复制一份到公共 Movies/RomanticFirework，保证系统相册可见、卸载前不丢。
     */
    private void prepareOutput() throws IOException {
        File dir = new File(getExternalFilesDir(Environment.DIRECTORY_MOVIES), "RomanticFirework");
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IOException("无法创建输出目录：" + dir.getAbsolutePath());
        }
        String name = "romantic" + herNameSuffix() + "_" + timeStamp() + ".mp4";
        outputFile = new File(dir, name);
        mediaMuxer = new MediaMuxer(outputFile.getAbsolutePath(),
                MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
        Log.i(TAG, "output -> " + outputFile.getAbsolutePath());
    }

    /** 【#23】文件名带她的名字+日期：录完不用改名就能直接发给她 */
    private String herNameSuffix() {
        try {
            String n = MMKV.defaultMMKV().decodeString("romantic_her_name", "");
            if (n != null && !n.trim().isEmpty()) return "_" + n.trim();
        } catch (Throwable ignored) {
        }
        return "";
    }

    private String timeStamp() {
        return new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
    }

    /**
     * 把私有目录的成品复制到公共相册（仅 API 29+）。
     * 失败不影响录制本身——视频仍在 App 私有目录，可正常分享。
     */
    private Uri publishToGallery(File src) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null;
        Uri uri = null;
        try {
            ContentValues cv = new ContentValues();
            cv.put(MediaStore.Video.Media.DISPLAY_NAME, src.getName());
            cv.put(MediaStore.Video.Media.MIME_TYPE, "video/mp4");
            cv.put(MediaStore.Video.Media.RELATIVE_PATH,
                    Environment.DIRECTORY_MOVIES + "/RomanticFirework");
            cv.put(MediaStore.Video.Media.IS_PENDING, 1);
            uri = getContentResolver().insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, cv);
            if (uri == null) return null;

            OutputStream out = getContentResolver().openOutputStream(uri);
            if (out == null) return null;
            InputStream in = new FileInputStream(src);
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            in.close();
            out.close();

            ContentValues done = new ContentValues();
            done.put(MediaStore.Video.Media.IS_PENDING, 0);
            getContentResolver().update(uri, done, null, null);
            Log.i(TAG, "published to gallery: " + uri);
            return uri;
        } catch (Exception e) {
            Log.e(TAG, "publish to gallery failed", e);
            if (uri != null) {
                try {
                    getContentResolver().delete(uri, null, null);
                } catch (Exception ignored) {
                }
            }
            return null;
        }
    }

    private void setupEncoder() {
        WindowManager wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        DisplayMetrics dm = new DisplayMetrics();
        wm.getDefaultDisplay().getRealMetrics(dm);

        videoWidth = dm.widthPixels & ~1;
        videoHeight = dm.heightPixels & ~1;
        int bitrate = Math.max(4_000_000, videoWidth * videoHeight * FRAME_RATE * 3 / 5);

        MediaFormat format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC,
                videoWidth, videoHeight);
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        format.setInteger(MediaFormat.KEY_BIT_RATE, bitrate);
        format.setInteger(MediaFormat.KEY_FRAME_RATE, FRAME_RATE);
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, IFRAME_INTERVAL);

        try {
            mediaCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC);
            mediaCodec.setCallback(encoderCallback, encoderHandler);
            mediaCodec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            inputSurface = mediaCodec.createInputSurface();
            mediaCodec.start();
        } catch (IOException e) {
            throw new RuntimeException("create encoder failed", e);
        }
    }

    private void startVirtualDisplay() {
        WindowManager wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        DisplayMetrics dm = new DisplayMetrics();
        wm.getDefaultDisplay().getRealMetrics(dm);
        virtualDisplay = mediaProjection.createVirtualDisplay(
                "RomanticRecorder",
                videoWidth, videoHeight, dm.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                inputSurface, null, null);
        if (virtualDisplay == null) {
            errorMsg = "VirtualDisplay 创建失败";
            finishRecording();
        }
    }

    public void stopRecording() {
        if (isStopping) return;
        isStopping = true;
        Log.i(TAG, "stopRecording requested, framesWritten=" + framesWritten);
        encoderHandler.post(() -> {
            try {
                if (mediaCodec != null) {
                    mediaCodec.signalEndOfInputStream();
                } else {
                    finishRecording();
                }
            } catch (Exception e) {
                finishRecording();
            }
        });
    }

    private void finishRecording() {
        try {
            if (mediaCodec != null) {
                try { mediaCodec.stop(); } catch (Exception ignored) {}
                mediaCodec.release();
                mediaCodec = null;
            }
            if (inputSurface != null) {
                inputSurface.release();
                inputSurface = null;
            }
            if (virtualDisplay != null) {
                virtualDisplay.release();
                virtualDisplay = null;
            }
            boolean ok = false;
            if (muxerStarted && mediaMuxer != null) {
                try {
                    mediaMuxer.stop();
                    ok = true;
                } catch (Exception e) {
                    Log.e(TAG, "muxer stop failed", e);
                }
                mediaMuxer.release();
            } else if (mediaMuxer != null) {
                mediaMuxer.release();
            }
            mediaMuxer = null;
            muxerStarted = false;

            if (mediaProjection != null) {
                if (callbackRegistered && projectionCallback != null) {
                    try {
                        mediaProjection.unregisterCallback(projectionCallback);
                    } catch (Exception ignored) {
                    }
                    callbackRegistered = false;
                }
                try { mediaProjection.stop(); } catch (Exception ignored) {}
                mediaProjection = null;
            }

            long size = outputFile != null && outputFile.exists() ? outputFile.length() : 0;
            Log.i(TAG, "finishRecording: ok=" + ok + ", frames=" + framesWritten + ", size=" + size);

            // 校验：必须有足够帧且文件 > 50KB 才算成功；否则删掉空文件并上报失败
            if (!ok || framesWritten < 10 || size < 50 * 1024) {
                if (outputFile != null && outputFile.exists()) outputFile.delete();
                broadcastFailure(errorMsg != null ? errorMsg : "未捕获到画面（请确认授权后保持 App 在前台）");
            } else {
                // 先入公共相册；未入册成功时退化为扫描私有文件（尽力而为，失败无副作用）
                Uri galleryUri = publishToGallery(outputFile);
                if (galleryUri == null) {
                    MediaScannerConnection.scanFile(this,
                            new String[]{outputFile.getAbsolutePath()},
                            new String[]{"video/mp4"}, null);
                }
                broadcastSuccess(galleryUri);
            }
        } catch (Exception e) {
            Log.e(TAG, "finish error", e);
        } finally {
            stopForeground(STOP_FOREGROUND_REMOVE);
            stopSelf();
        }
    }

    private void broadcastSuccess(Uri galleryUri) {
        Intent i = new Intent(ACTION_STOPPED);
        i.setPackage(getPackageName());
        i.putExtra(EXTRA_OUTPUT_PATH, outputFile.getAbsolutePath());
        if (galleryUri != null) i.putExtra(EXTRA_OUTPUT_URI, galleryUri.toString());
        sendBroadcast(i);
    }

    private void broadcastFailure(String msg) {
        Intent i = new Intent(ACTION_FAILED);
        i.setPackage(getPackageName());
        i.putExtra(EXTRA_ERROR, msg);
        sendBroadcast(i);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        finishRecording();
        if (encoderThread != null) {
            encoderThread.quitSafely();
            encoderThread = null;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
