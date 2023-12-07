package com.jinshuo.cvte.screencapturetool;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Surface;

import androidx.annotation.NonNull;

import java.io.IOException;
import java.nio.ByteBuffer;

public class ScreenCaptureService extends Service {
    private static final String TAG = "ScreenCaptureService";

    MediaProjection mediaProjection;
    MediaFormat format;
    MediaCodec mediaCodec;
    VirtualDisplay virtualDisplay;
    ByteBuffer outputBuffer;
    Surface surface;

    boolean isRecording = false;

    int resultCode;
    Intent data;
    private int mScreenWidth;
    private int mScreenHeight;
    private int mScreenDensity;

    private MainActivity.EncoderStatusListener encoderStatusListener;

    public ScreenCaptureService() {
    }

    @Override
    public void onCreate() {
        super.onCreate();

        Log.d(TAG, "screen capture service created");
    }

    /**
     * 前台服务通知
     */
    private void createNotificationChannel() {
        Notification.Builder builder = new Notification.Builder(this.getApplicationContext()); //获取一个Notification构造器
        Intent nfIntent = new Intent(this, ScreenCaptureService.class); //点击后跳转的界面，可以设置跳转数据

        builder.setContentIntent(PendingIntent.getActivity(this, 0, nfIntent, PendingIntent.FLAG_IMMUTABLE)) // 设置PendingIntent
                .setLargeIcon(BitmapFactory.decodeResource(this.getResources(), R.mipmap.ic_launcher)) // 设置下拉列表中的图标(大图标)
                //.setContentTitle("SMI InstantView") // 设置下拉列表里的标题
                .setSmallIcon(R.mipmap.ic_launcher) // 设置状态栏内的小图标
                .setContentText("is running......") // 设置上下文内容
                .setWhen(System.currentTimeMillis()); // 设置该通知发生的时间

        /*以下是对Android 8.0的适配*/
        //普通notification适配
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder.setChannelId("notification_id");
        }
        //前台服务notification适配
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            NotificationChannel channel = new NotificationChannel("notification_id", "notification_name", NotificationManager.IMPORTANCE_LOW);
            notificationManager.createNotificationChannel(channel);
        }

        Notification notification = builder.build(); // 获取构建好的Notification
        notification.defaults = Notification.DEFAULT_SOUND; //设置为默认的声音
        startForeground(110, notification);
    }

    /**
     * 获取MediaProjection实例
     *
     * @return MediaProjection instance
     */
    private MediaProjection createMediaProjection() {
        return ((MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE)).getMediaProjection(resultCode, data);
    }

    /**
     * 开始录制屏幕
     */
    private void startCapture() {
        startVideoEncoder();
        isRecording = true;
    }

    /**
     * 异步编码，Output可用时发起数据可用的通知
     */
    MediaCodec.Callback encoderCallback = new MediaCodec.Callback() {
        @Override
        public void onInputBufferAvailable(@NonNull MediaCodec mediaCodec, int i) {
        }

        @Override
        public void onOutputBufferAvailable(@NonNull MediaCodec mediaCodec, int i, @NonNull MediaCodec.BufferInfo bufferInfo) {
            if (i >= 0) {
                outputBuffer = mediaCodec.getOutputBuffer(i);
                if (bufferInfo.flags == MediaCodec.BUFFER_FLAG_CODEC_CONFIG) {
                    encoderStatusListener.onConfigReady(outputBuffer);
                }
                encoderStatusListener.onDataReady(bufferInfo, outputBuffer);
                mediaCodec.releaseOutputBuffer(i, false);
            } else {
                Log.d(TAG, "onOutputBufferAvailable: outputBuffer index < 0");
            }
        }

        @Override
        public void onError(@NonNull MediaCodec mediaCodec, @NonNull MediaCodec.CodecException e) {
        }

        @Override
        public void onOutputFormatChanged(@NonNull MediaCodec mediaCodec, @NonNull MediaFormat mediaFormat) {
        }
    };

    /**
     * 开启编码器
     */
    private void startVideoEncoder() {
        try {
            mediaCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC);
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }
        mediaCodec.setCallback(encoderCallback);
        format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, mScreenWidth, mScreenHeight);
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        format.setInteger(MediaFormat.KEY_BIT_RATE, mScreenWidth * mScreenHeight);
        format.setInteger(MediaFormat.KEY_FRAME_RATE, 20);
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);
        mediaCodec.configure(
                format,
                null,
                null,
                MediaCodec.CONFIGURE_FLAG_ENCODE);
        encoderStatusListener.onEncoderCreated(format);
        surface = mediaCodec.createInputSurface();
        virtualDisplay = mediaProjection.createVirtualDisplay(TAG, mScreenWidth, mScreenHeight, mScreenDensity,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, surface, null, null);
        mediaCodec.start();
    }

    /**
     * 结束录制屏幕
     */
    public void stopCapture() {
        isRecording = false;
        mediaCodec.stop();
        mediaCodec.release();
    }

    @Override
    public void onDestroy() {
        stopForeground(true);
        if (virtualDisplay != null) {
            virtualDisplay.release();
            virtualDisplay = null;
        }
        if (mediaCodec != null) {
            if (isRecording) {
                mediaCodec.stop();
            }
            mediaCodec.release();
            mediaCodec = null;
        }
        if (mediaProjection != null) {
            mediaProjection.stop();
            mediaProjection = null;
        }
        super.onDestroy();
    }

    class ScreenCaptureBinder extends Binder {
        public void startCapture() {
            ScreenCaptureService.this.startCapture();
        }

        public void stopCapture() {
            ScreenCaptureService.this.stopCapture();
        }

        public void registerScreenInfo(DisplayMetrics metrics) {
//            mScreenWidth = metrics.widthPixels;
//            mScreenHeight = metrics.heightPixels;
            mScreenWidth = 720;
            mScreenHeight = 1280;
            mScreenDensity = metrics.densityDpi;
        }

        public void registerScreenCaptureRequestInfo(Bundle bundle) {
            resultCode = bundle.getInt("code");
            data = bundle.getParcelable("data");
        }

        public void prepareCaptureEnviroment() {
            createNotificationChannel();
            mediaProjection = createMediaProjection();
        }

        public void setEncoderStatusListener(MainActivity.EncoderStatusListener listener) {
            ScreenCaptureService.this.encoderStatusListener = listener;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return new ScreenCaptureBinder();
    }

}