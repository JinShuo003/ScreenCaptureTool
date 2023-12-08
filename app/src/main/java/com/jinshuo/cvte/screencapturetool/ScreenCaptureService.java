package com.jinshuo.cvte.screencapturetool;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.SystemClock;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Surface;

import androidx.annotation.NonNull;

import com.jinshuo.cvte.screencapturetool.utils.ImageUtils;

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

    boolean outputFormatInited = false;
    int resultCode;
    Intent data;
    private int mScreenWidth;
    private int mScreenHeight;
    private int mScreenDensity;

    public interface EncoderStatusListener {
        void onOutputFormatInited(MediaFormat mediaFormat);
        void onOutputFormatChanged(MediaFormat mediaFormat);
        void onConfigReady(ByteBuffer configBuffer);
        void onMediaFormatChanged(MediaFormat format);
        void onDataReady(MediaCodec.BufferInfo bufferInfo, ByteBuffer outputBuffer);
    }
    private EncoderStatusListener encoderStatusListener;

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
     * 准备MediaProjection
     * @param bundle
     */
    private void prepareMediaProjection(Bundle bundle) {
        createNotificationChannel();
        resultCode = bundle.getInt("code");
        data = bundle.getParcelable("data");
        mediaProjection = ((MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE)).getMediaProjection(resultCode, data);
    }

    /**
     * 开始录制屏幕
     */
    private void startCapture(Bundle bundle) {
        prepareMediaProjection(bundle);
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                Log.d(TAG, "run: ThreadId: " + Thread.currentThread().getId());
                initVideoEncoder();
                mediaCodec.start();
            }
        });
        thread.start();
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
                Log.d(TAG, "onOutputBufferAvailable: ThreadId: " + Thread.currentThread().getId());
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
            if (!outputFormatInited) {
                encoderStatusListener.onOutputFormatInited(mediaCodec.getOutputFormat());
            } else {
                encoderStatusListener.onOutputFormatChanged(mediaCodec.getOutputFormat());

            }
        }
    };

    /**
     * 开启编码器
     */
    private void initVideoEncoder() {
        if (mediaCodec == null) {
            try {
                mediaCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC);
                format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, mScreenWidth, mScreenHeight);
                format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
                format.setInteger(MediaFormat.KEY_BIT_RATE, mScreenWidth * mScreenHeight);
                format.setInteger(MediaFormat.KEY_FRAME_RATE, 20);
                format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);
                encoderStatusListener.onMediaFormatChanged(format);
            } catch (IOException e) {
                e.printStackTrace();
                return;
            }
        }
        mediaCodec.setCallback(encoderCallback);
        mediaCodec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        surface = mediaCodec.createInputSurface();
        virtualDisplay = mediaProjection.createVirtualDisplay(TAG, mScreenWidth, mScreenHeight, mScreenDensity,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, surface, null, null);
        Log.d(TAG, "startVideoEncoder: ThreadId: " + Thread.currentThread().getId());
    }

    /**
     * 结束录制屏幕
     */
    private void stopCapture() {
        isRecording = false;
        mediaCodec.stop();
        mediaCodec.reset();
    }

    /**
     * 截屏
     */
    private Bitmap screenshot(Bundle bundle) {
        prepareMediaProjection(bundle);

        @SuppressLint("WrongConstant") ImageReader imageReader = ImageReader.newInstance(
                mScreenWidth,
                mScreenHeight,
                PixelFormat.RGBA_8888, 1);
        virtualDisplay = mediaProjection.createVirtualDisplay("screen-mirror",
                mScreenWidth,
                mScreenHeight,
                mScreenDensity,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader.getSurface(), null, null);

        SystemClock.sleep(200);
        Image image = imageReader.acquireLatestImage();
        if (image == null) {
            return null;
        }
        Bitmap bitmap = ImageUtils.Image2Bitmap(image, mScreenWidth, mScreenHeight);
        image.close();
        virtualDisplay.release();
        return bitmap;
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
        public void startCapture(Bundle bundle) {
            ScreenCaptureService.this.startCapture(bundle);
        }

        public void stopCapture() {
            ScreenCaptureService.this.stopCapture();
        }

        public Bitmap screenshot(Bundle bundle) {
            return ScreenCaptureService.this.screenshot(bundle);
        }

        public void registerScreenInfo(DisplayMetrics metrics) {
//            mScreenWidth = metrics.widthPixels;
//            mScreenHeight = metrics.heightPixels;
            mScreenWidth = 720;
            mScreenHeight = 1280;
            mScreenDensity = metrics.densityDpi;
        }

        public void setEncoderStatusListener(EncoderStatusListener listener) {
            ScreenCaptureService.this.encoderStatusListener = listener;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return new ScreenCaptureBinder();
    }

}