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
import android.media.MediaRecorder;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.util.DisplayMetrics;
import android.util.Log;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

public class ScreenCaptureService extends Service {
    private static final String TAG = "ScreenCaptureService";

    MediaProjection mediaProjection;
    MediaRecorder mediaRecorder;
    VirtualDisplay virtualDisplay;

    boolean isRecording = false;
    boolean isPausing = false;

    int resultCode;
    Intent data;
    private int mScreenWidth;
    private int mScreenHeight;
    private int mScreenDensity;

    public ScreenCaptureService() {
    }

    @Override
    public void onCreate() {
        super.onCreate();

        Log.d(TAG, "screen capture service created");
    }

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
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder.setChannelId("notification_id");
        }
        //前台服务notification适配
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager notificationManager = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
            NotificationChannel channel = new NotificationChannel("notification_id", "notification_name", NotificationManager.IMPORTANCE_LOW);
            notificationManager.createNotificationChannel(channel);
        }

        Notification notification = builder.build(); // 获取构建好的Notification
        notification.defaults = Notification.DEFAULT_SOUND; //设置为默认的声音
        startForeground(110, notification);

    }

    /**
     * 获取MediaProjection实例
     * @return MediaProjection instance
     */
    private MediaProjection createMediaProjection() {
        return ((MediaProjectionManager)getSystemService(MEDIA_PROJECTION_SERVICE)).getMediaProjection(resultCode, data);
    }

    /**
     * 获取MediaRecorder实例
     * @return MediaRecorder instance
     */
    private MediaRecorder createMediaRecorder() {
        String videoOutputDirectory = getExternalFilesDir("") + "/ScreenCapture";
        StorageUtils.makeDirectory(videoOutputDirectory);

        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss");
        Date curDate = new Date(System.currentTimeMillis());
        String curTime = formatter.format(curDate).replace(" ", "");

        Log.i(TAG, "Create MediaRecorder");
        MediaRecorder mediaRecorder = new MediaRecorder();
//        mediaRecorder.setPreviewDisplay(surface);
        mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
        mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_WB);  //after setOutputFormat()
        mediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);  //after setOutputFormat()
        mediaRecorder.setVideoSize(mScreenWidth, mScreenHeight);  //after setVideoSource(), setOutFormat()
        mediaRecorder.setVideoFrameRate(60);
        mediaRecorder.setOutputFile(videoOutputDirectory + "/ScreenCapture_" + curTime + ".mp4");
        int bitRate = 3 * mScreenWidth * mScreenHeight;
        mediaRecorder.setVideoEncodingBitRate(3 * mScreenWidth * mScreenHeight);
        Log.d(TAG, "createMediaRecorder: bigRate: " + bitRate);

        try {
            mediaRecorder.prepare();
        } catch (IllegalStateException | IOException e) {
            e.printStackTrace();
        }

        return mediaRecorder;
    }

    /**
     * 获取VirtualDisplay实例
     * @return VirtualDisplay instance
     */
    private VirtualDisplay createVirtualDisplay() {
        return mediaProjection.createVirtualDisplay(TAG, mScreenWidth, mScreenHeight, mScreenDensity,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, mediaRecorder.getSurface(), null, null);
    }

    /**
     * 开始录制屏幕
     */
    private void startCapture() {
        mediaRecorder = createMediaRecorder();
        virtualDisplay = createVirtualDisplay();
        mediaRecorder.start();
        isRecording = true;
    }

    /**
     * 结束录制屏幕
     */
    public void stopCapture() {
        if (isRecording) {
            mediaRecorder.stop();
            mediaRecorder.reset();
        }
    }

    @Override
    public void onDestroy() {
        stopForeground(true);
        if (virtualDisplay != null) {
            virtualDisplay.release();
            virtualDisplay = null;
        }
        if (mediaRecorder != null) {
            if (isRecording) {
                mediaRecorder.stop();
            }
            mediaRecorder.release();
            mediaRecorder = null;
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
    }
    @Override
    public IBinder onBind(Intent intent) {
        return new ScreenCaptureBinder();
    }

}