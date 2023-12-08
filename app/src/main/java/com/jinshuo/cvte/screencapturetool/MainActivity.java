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
import android.graphics.SurfaceTexture;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.jinshuo.cvte.screencapturetool.observer.PreviewManager;
import com.jinshuo.cvte.screencapturetool.observer.ScreenshotManager;
import com.jinshuo.cvte.screencapturetool.observer.VideoFileManager;
import com.jinshuo.cvte.screencapturetool.utils.LengthUtils;
import com.jinshuo.cvte.screencapturetool.utils.ScreenUtils;

import java.nio.ByteBuffer;
import java.util.concurrent.LinkedTransferQueue;
import java.util.concurrent.TransferQueue;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private Button btnRecordControl;
//    private Button btnClearPreview;
    private Button btnScreenshot;
    private ImageView ivScreenshot;
    private TextureView textureView;
    private Surface surface;

    private boolean isScreenCaptureServiceConnected = false;
    private boolean isRecording = false;

    /**
     * 基于LinkedTransferQueue实现无限大缓冲区的生产者-消费者，生产者
     * 装入数据后立即返回，消费者无数据可消费时阻塞在取操作
     * 基于发布-订阅模式实现了数据获取与录屏、预览、截屏等操作的解耦
     */
    private TransferQueue<FrameData> frameQueue = new LinkedTransferQueue<>();
    // 消费者，负责消费解码出的数据
    VideoStreamConsumer consumer = new VideoStreamConsumer();
    VideoFileManager videoFileManager = new VideoFileManager();
    PreviewManager previewManager = new PreviewManager();
    ScreenshotManager screenshotManager = new ScreenshotManager();

    /**
     * 用于接收编码数据的callback
     */
    public void putNewFrame(MediaCodec.BufferInfo outputBufferInfo, ByteBuffer outputBuffer){
        byte[] data = new byte[outputBufferInfo.size];
        outputBuffer.get(data);
        frameQueue.offer(new FrameData(data.clone(), outputBufferInfo));
        Log.d(TAG, "putNewFrame: frameQueue.size: " + frameQueue.size());
    }

    ScreenCaptureService.EncoderStatusListener listener = new ScreenCaptureService.EncoderStatusListener() {

        @Override
        public void onMediaFormatChanged(MediaFormat mediaFormat) {
            previewManager.encoderEncodeMediaFormatChange(mediaFormat);
            screenshotManager.encoderEncodeMediaFormatChange(mediaFormat);
            videoFileManager.encoderEncodeMediaFormatChange(mediaFormat);
        }

        @Override
        public void onOutputFormatInited(MediaFormat mediaFormat) {
            previewManager.encoderOutputMediaFormatChange(mediaFormat);
            screenshotManager.encoderOutputMediaFormatChange(mediaFormat);
            videoFileManager.encoderOutputMediaFormatChange(mediaFormat);

            consumer.addObserver(videoFileManager);
            consumer.addObserver(previewManager);
            consumer.startConsume();
        }

        @Override
        public void onOutputFormatChanged(MediaFormat outputMediaFormat) {
//            previewManager.encoderOutputMediaFormatChange(outputMediaFormat);
//            screenshotManager.encoderOutputMediaFormatChange(outputMediaFormat);
//            videoFileManager.encoderOutputMediaFormatChange(outputMediaFormat);
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
        Log.d(TAG, "onCreate: MainThreadId: " + Thread.currentThread().getId());
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
    View.OnClickListener clearPreviewOnClickListener = view -> {
        clearPreview();
    };
    View.OnClickListener screenshotOnClickListener = view -> {
        screenshot();
    };

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        LinearLayout display_layout = findViewById(R.id.display_layout);
        int width = display_layout.getWidth()/2-LengthUtils.dp2px(this, 10);
        int height = (int)(width*1.77);

        FrameLayout surfaceLayout = findViewById(R.id.texture_layout);
        FrameLayout imageLayout = findViewById(R.id.image_layout);
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(width, height);
        int margin = LengthUtils.dp2px(this, 5);
        layoutParams.setMargins(margin, margin, margin, margin);

        surfaceLayout.setLayoutParams(layoutParams);
        imageLayout.setLayoutParams(layoutParams);

        int padding = LengthUtils.dp2px(this, 5);
        surfaceLayout.setPadding(padding, padding, padding, padding);
        imageLayout.setPadding(padding, padding, padding, padding);

    }

    /**
     * 初始化
     */
    private void init() {
        btnRecordControl = findViewById(R.id.btn_record_control);
//        btnClearPreview = findViewById(R.id.btn_clear_surface);
        btnScreenshot = findViewById(R.id.btn_screenshot);
        ivScreenshot = findViewById(R.id.screenshot_image);
        textureView = findViewById(R.id.textureView);
        textureView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(@NonNull SurfaceTexture surfaceTexture, int i, int i1) {
                Log.d(TAG, "onSurfaceTextureAvailable: ");
                surface = new Surface(surfaceTexture);
                previewManager.registerPreviewInfo(displayWidth, displayHeight, surface);

            }

            @Override
            public void onSurfaceTextureSizeChanged(@NonNull SurfaceTexture surfaceTexture, int i, int i1) {
                Log.d(TAG, "onSurfaceTextureSizeChanged: ");
            }

            @Override
            public boolean onSurfaceTextureDestroyed(@NonNull SurfaceTexture surfaceTexture) {
                Log.d(TAG, "onSurfaceTextureDestroyed: ");
                return false;
            }

            @Override
            public void onSurfaceTextureUpdated(@NonNull SurfaceTexture surfaceTexture) {

            }
        });

        mediaProjectionManager = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        initDisplayInfo();

        btnRecordControl.setOnClickListener(startRecordOnClickListener);
//        btnClearPreview.setOnClickListener(clearPreviewOnClickListener);
        btnScreenshot.setOnClickListener(screenshotOnClickListener);

        consumer.setBuffer(frameQueue);
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
        btnRecordControl.setBackground(getDrawable(R.drawable.start_record_btn_background));

//        btnClearPreview.setEnabled(true);
//        btnClearPreview.setBackground(getDrawable(R.drawable.clear_surface_btn_background));

        isRecording = false;
        Toast.makeText(this, R.string.stop_record, Toast.LENGTH_LONG).show();
    }

    private void clearPreview() {
        Canvas canvas = textureView.lockCanvas();
        canvas.drawColor(getResources().getColor(R.color.black_FF000000));
        textureView.unlockCanvasAndPost(canvas);
        ivScreenshot.setImageDrawable(getDrawable(R.drawable.screenshot_preview_background));
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
        Canvas canvas = textureView.lockCanvas();
        if (null != canvas) {
            canvas.drawColor(Color.BLACK, PorterDuff.Mode.CLEAR);
        }
//        textureView.unlockCanvasAndPost(canvas);
    }

    /**
     * 接收授权结果
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK) {
            Log.i(TAG, "onActivityResult: user denied");
            return;
        }

        Bundle bundle = new Bundle();
        bundle.putInt("code", resultCode);
        bundle.putParcelable("data", data);

        if (requestCode == SCREEN_CAPTURE_INTENT_REQUEST_CODE) {
            screenCaptureBinder.startCapture(bundle);

            btnRecordControl.setOnClickListener(stopRecordOnClickListener);
            btnRecordControl.setText(R.string.stop_record);
            btnRecordControl.setBackground(getDrawable(R.drawable.stop_record_btn_background));

//            btnClearPreview.setEnabled(false);
//            btnClearPreview.setBackground(getDrawable(R.drawable.btn_enable_background));

            Toast.makeText(this, R.string.start_record, Toast.LENGTH_LONG).show();
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