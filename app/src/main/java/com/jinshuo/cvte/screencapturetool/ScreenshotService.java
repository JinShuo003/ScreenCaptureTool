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
import android.media.MediaRecorder;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.SystemClock;
import android.util.DisplayMetrics;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

public class ScreenshotService extends Service {
    private static final String TAG = "ScreenshotService";

    MediaProjection mediaProjection;
    VirtualDisplay virtualDisplay;

    int resultCode;
    Intent data;
    private int mScreenWidth;
    private int mScreenHeight;
    private int mScreenDensity;

    public ScreenshotService() {
    }

    @Override
    public void onCreate() {
        super.onCreate();

        Log.d(TAG, "screen capture service created");
    }

    private void createNotificationChannel() {
        Notification.Builder builder = new Notification.Builder(this.getApplicationContext()); //获取一个Notification构造器
        Intent nfIntent = new Intent(this, ScreenshotService.class); //点击后跳转的界面，可以设置跳转数据

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
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss");
        Date curDate = new Date(System.currentTimeMillis());
        String curTime = formatter.format(curDate).replace(" ", "");
        File file=new File(getExternalFilesDir("")+"/ScreenCapture");
        if(!file.exists()){
            file.mkdirs();
        }

        Log.i(TAG, "Create MediaRecorder");
        MediaRecorder mediaRecorder = new MediaRecorder();
        mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
        mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
        mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_WB);  //after setOutputFormat()
        mediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);  //after setOutputFormat()
        mediaRecorder.setVideoSize(mScreenWidth, mScreenHeight);  //after setVideoSource(), setOutFormat()
        mediaRecorder.setVideoFrameRate(60);
        mediaRecorder.setOutputFile(file.getAbsolutePath() + "/ScreenCapture_" + curTime + ".mp4");
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

    private Bitmap screenshot() {
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
//        virtualDisplay.release();
        return bitmap;
    }

    private void saveScreenshot(Bitmap bitmap) {
        String fileName = null;
        try {
            Date currentDate = new Date();
            SimpleDateFormat date = new SimpleDateFormat("yyyyMMddhhmmss");
            File dir = getExternalFilesDir(null);
            fileName = dir.getAbsolutePath() + "/" + date.format(currentDate) + ".png";
            FileOutputStream fos = new FileOutputStream(fileName);
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
            fos.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    @Override
    public void onDestroy() {
        stopForeground(true);
        if (virtualDisplay != null) {
            virtualDisplay.release();
            virtualDisplay = null;
        }
        if (mediaProjection != null) {
            mediaProjection.stop();
            mediaProjection = null;
        }
        super.onDestroy();
    }

    class ScreenshotBinder extends Binder {
        public Bitmap screenshot() {
            return ScreenshotService.this.screenshot();
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
        return new ScreenshotBinder();
    }
}