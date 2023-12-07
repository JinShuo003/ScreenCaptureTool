package com.jinshuo.cvte.screencapturetool;

import android.Manifest;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.jinshuo.cvte.screencapturetool.observer.PreviewManager;
import com.jinshuo.cvte.screencapturetool.observer.ScreenshotManager;
import com.jinshuo.cvte.screencapturetool.observer.VideoFileManager;
import com.jinshuo.cvte.screencapturetool.utils.ScreenUtils;

import java.nio.ByteBuffer;
import java.util.concurrent.LinkedTransferQueue;
import java.util.concurrent.TransferQueue;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private Button btnRecordControl;
    private Button btnScreenshot;
    private ImageView ivScreenshot;
    private SurfaceView surfaceView;
    private SurfaceHolder surfaceHolder;

    private boolean isScreenCaptureServiceConnected = false;
    private boolean isRecording = false;

    /**
     * 基于LinkedTransferQueue实现无限大缓冲区的生产者-消费者，生产者
     * 装入数据后立即返回，消费者无数据可消费时阻塞在取操作
     * 基于发布-订阅模式实现了数据获取与录屏、预览、截屏等操作的解耦
     */
    private TransferQueue frameQueue = new LinkedTransferQueue<>();
    // 消费者，负责消费解码出的数据
    FrameDataConsumer consumer = new FrameDataConsumer();
    VideoFileManager videoFileManager = new VideoFileManager();
    PreviewManager previewManager = new PreviewManager();
    ScreenshotManager screenshotManager = new ScreenshotManager();

    /**
     * 用于接收编码数据的callback
     */
    public interface EncoderStatusListener {
        void onEncoderCreated(MediaFormat format);

        void onConfigReady(ByteBuffer configBuffer);

        void onDataReady(MediaCodec.BufferInfo bufferInfo, ByteBuffer outputBuffer);
    }

    public void putNewFrame(MediaCodec.BufferInfo outputBufferInfo, ByteBuffer outputBuffer){
        byte[] frameData = new byte[outputBufferInfo.size];
        outputBuffer.get(frameData);
        frameQueue.offer(frameData.clone());
        Log.d(TAG, "putNewFrame: frameQueue.size: " + frameQueue.size());
    }

    EncoderStatusListener listener = new EncoderStatusListener() {
        @Override
        public void onEncoderCreated(MediaFormat mediaFormat) {
        }

        @Override
        public void onConfigReady(ByteBuffer configBuffer) {
        }

        @Override
        public void onDataReady(MediaCodec.BufferInfo outputBufferInfo, ByteBuffer outputBuffer) {
            putNewFrame(outputBufferInfo, outputBuffer);
        }
    };
    private ScreenCaptureService.ScreenCaptureBinder screenCaptureBinder;

    private MediaProjectionManager mediaProjectionManager;

    private static final int SCREEN_CAPTURE_INTENT_REQUEST_CODE = 1000;
    private static final int SCREENSHOT_INTENT_REQUEST_CODE = 1001;

    private static final int PERMISSION_REQUEST_CODE = 200;

    private DisplayMetrics displayMetrics;
    private int displayWidth = 0;
    private int displayHeight = 0;
    private int displayDensity = 0;

    String[] permissions = new String[]{
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.READ_EXTERNAL_STORAGE};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        init();
        checkPermissions();
        connectService();
    }

    View.OnClickListener startRecordOnClickListener = view -> {
        startRecordScreen();
    };
    View.OnClickListener stopRecordOnClickListener = view -> {
        stopRecordScreen();
    };
    View.OnClickListener screenshotOnClickListener = view -> {
        screenshot();
    };

    /**
     * 初始化
     */
    private void init() {
        btnRecordControl = findViewById(R.id.btn_record_control);
        btnScreenshot = findViewById(R.id.btn_screenshot);
        ivScreenshot = findViewById(R.id.screenshot_image);
        surfaceView = findViewById(R.id.surface);
        surfaceHolder = surfaceView.getHolder();
        surfaceHolder.addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(@NonNull SurfaceHolder surfaceHolder) {
                Log.d(TAG, "surfaceCreated: ");
            }

            @Override
            public void surfaceChanged(@NonNull SurfaceHolder surfaceHolder, int i, int i1, int i2) {
                Log.d(TAG, "surfaceChanged: ");
            }

            @Override
            public void surfaceDestroyed(@NonNull SurfaceHolder surfaceHolder) {
                Log.d(TAG, "surfaceDestroyed: ");
            }
        });

        mediaProjectionManager = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        initDisplayInfo();

        btnRecordControl.setOnClickListener(startRecordOnClickListener);
        btnScreenshot.setOnClickListener(screenshotOnClickListener);

        consumer.setBuffer(frameQueue);

        previewManager.registerPreviewInfo(displayWidth, displayHeight, surfaceHolder.getSurface());
    }

    private void initDisplayInfo() {
        displayMetrics = ScreenUtils.getScreenMetrics(this);
        displayWidth = 720;
        displayHeight = 1280;
//        displayWidth = displayMetrics.widthPixels;
//        displayHeight = displayMetrics.heightPixels;
        displayDensity = displayMetrics.densityDpi;
    }

    /**
     * 绑定到录屏服务
     */
    private void connectService() {
        if (!isScreenCaptureServiceConnected) {
            Intent intent = new Intent(this, ScreenCaptureService.class);
            bindService(intent, screenCaptureServiceConnection, BIND_AUTO_CREATE);
        }
    }

    /**
     * 检查权限情况
     */
    public void checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            for (String permission : permissions) {
                if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(this, permissions, PERMISSION_REQUEST_CODE);
                    return;
                }
            }
        }
    }

    /**
     * 请求权限的回调，如果未授权则拒绝服务
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[]
            grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        System.out.println(TAG + "_onRequestPermissionsResult");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && requestCode == PERMISSION_REQUEST_CODE) {
            for (int i = 0; i < permissions.length; i++) {
                if (grantResults[i] != PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(this, R.string.permission_not_enough, Toast.LENGTH_LONG);
                    return;
                }
            }
        }
    }

    /**
     * 绑定录屏服务的connection对象
     */
    private ServiceConnection screenCaptureServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            isScreenCaptureServiceConnected = true;
            screenCaptureBinder = (ScreenCaptureService.ScreenCaptureBinder) iBinder;
            screenCaptureBinder.registerScreenInfo(displayMetrics);
            screenCaptureBinder.setEncoderStatusListener(listener);
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            isScreenCaptureServiceConnected = false;
        }
    };

//    /**
//     * 异步解码，Input可用时写入最新的h264数据，Output可用时渲染到Surface上
//     */
//    MediaCodec.Callback decoderCallback = new MediaCodec.Callback() {
//        @Override
//        public void onInputBufferAvailable(@NonNull MediaCodec mediaCodec, int i) {
//            Log.d(TAG, "onInputBufferAvailable: decoder, inputBuffer: " + i);
//            if (i >= 0 && outputBuffer != null) {
//                ByteBuffer buffer = mediaCodec.getInputBuffer(i);
//                buffer.put(outputBuffer);
//                mediaCodec.queueInputBuffer(i, 0, outputBufferInfo.size, computePresentationTime(i), 0);
//            } else {
//                mediaCodec.queueInputBuffer(i, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
//            }
//        }
//
//        @Override
//        public void onOutputBufferAvailable(@NonNull MediaCodec mediaCodec, int i, @NonNull MediaCodec.BufferInfo bufferInfo) {
//            Log.d(TAG, "onOutputBufferAvailable: decoder, outputBuffer: " + i);
//            ByteBuffer outputBuffer = mediaCodec.getOutputBuffer(i);
//            mediaCodec.releaseOutputBuffer(i, true);
//        }
//
//        @Override
//        public void onError(@NonNull MediaCodec mediaCodec, @NonNull MediaCodec.CodecException e) {
//        }
//
//        @Override
//        public void onOutputFormatChanged(@NonNull MediaCodec mediaCodec, @NonNull MediaFormat mediaFormat) {
//        }
//    };

    /**
     * 开始录屏
     */
    private void startRecordScreen() {
        Intent screenCaptureIntent = mediaProjectionManager.createScreenCaptureIntent();
        startActivityForResult(screenCaptureIntent, SCREEN_CAPTURE_INTENT_REQUEST_CODE);

        btnRecordControl.setOnClickListener(stopRecordOnClickListener);
        btnRecordControl.setText(R.string.stop_record);
        Toast.makeText(this, R.string.start_record, Toast.LENGTH_LONG).show();
    }

    /**
     * 结束录屏
     */
    private void stopRecordScreen() {
        screenCaptureBinder.stopCapture();
        consumer.stopConsume();
        consumer.removeObserver(videoFileManager);
        consumer.removeObserver(previewManager);

        btnRecordControl.setOnClickListener(startRecordOnClickListener);
        btnRecordControl.setText(R.string.start_record);
        isRecording = false;
        Toast.makeText(this, R.string.stop_record, Toast.LENGTH_LONG).show();
    }

    /**
     * 截屏，如果当前正在录屏则将ScreenshotManager注册为观察者，从数据中截取一帧并保存
     * 如果当前不在录屏，则开启MediaProjection+ImageReader
     */
    public interface OnScreenshotReadyListener {
        void onScreenshotReady(Bitmap screenshot);
    }
    OnScreenshotReadyListener onScreenshotReadyListener = new OnScreenshotReadyListener() {
        @Override
        public void onScreenshotReady(Bitmap screenshot) {
            screenshotManager.saveImage(screenshot);
        }
    };
    private void screenshot() {
        if (isRecording) {
            View v = getWindow().getDecorView();
            Bitmap screenshot = Bitmap.createBitmap(v.getWidth(), v.getHeight(), Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas();
            canvas.setBitmap(screenshot);
            v.draw(canvas);
            ivScreenshot.setImageBitmap(screenshot);
            onScreenshotReadyListener.onScreenshotReady(screenshot);
        } else {
            Intent screenCaptureIntent = mediaProjectionManager.createScreenCaptureIntent();
            startActivityForResult(screenCaptureIntent, SCREENSHOT_INTENT_REQUEST_CODE);
        }
    }

    /**
     * 清空预览画布
     */
    private void clearSurface() {
        Canvas canvas = surfaceHolder.lockCanvas();
        if (null != canvas) {
            canvas.drawColor(Color.BLACK, PorterDuff.Mode.CLEAR);
        }
    }

    /**
     * 接收授权结果
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK) {
            Log.i(TAG, "onActivityResult: user denied");
        }

        Bundle bundle = new Bundle();
        bundle.putInt("code", resultCode);
        bundle.putParcelable("data", data);

        if (requestCode == SCREEN_CAPTURE_INTENT_REQUEST_CODE) {
            consumer.addObserver(videoFileManager);
            consumer.addObserver(previewManager);
            consumer.startConsume();
            clearSurface();
            screenCaptureBinder.startCapture(bundle);
            isRecording = true;
            Log.i(TAG, "Started screen recording");
        }
        if (requestCode == SCREENSHOT_INTENT_REQUEST_CODE) {
            Bitmap screenshot = screenCaptureBinder.screenshot(bundle);
            ivScreenshot.setImageBitmap(screenshot);
            onScreenshotReadyListener.onScreenshotReady(screenshot);
        }
    }
}