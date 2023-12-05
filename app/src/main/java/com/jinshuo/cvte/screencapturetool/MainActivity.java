package com.jinshuo.cvte.screencapturetool;

import android.Manifest;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.util.DisplayMetrics;
import android.util.Log;
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

import java.io.IOException;
import java.nio.ByteBuffer;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private Button btnStartCapture;
    private Button btnStopCapture;
    private Button btnStartPreview;
    private Button btnScreenshot;
    private ImageView ivScreenshot;
    private SurfaceView surfaceView;
    private boolean isScreenCaptureServiceConnected = false;

    private ScreenCaptureService.ScreenCaptureBinder screenCaptureBinder;

    private boolean isRecording = false;
    private MediaProjectionManager mediaProjectionManager;
    private MediaCodec previewDecoder;
    private MediaCodec saveVideoDecoder;

    private static final int SCREEN_CAPTURE_INTENT_REQUEST_CODE = 1000;

    private static final int PERMISSION_REQUEST_CODE = 200;

    private DisplayMetrics displayMetrics;
    private int displayWidth = 0;
    private int displayHeight = 0;
    private int displayDensity = 0;

    String[] permissions = new String[]{
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.READ_EXTERNAL_STORAGE};

    ByteBuffer byteBuffer;
    public static final int BUFFER_DATA_READY = 0;
    private Handler mainHandler = new Handler(new Handler.Callback() {
        @Override
        public boolean handleMessage(@NonNull Message message) {
            switch (message.what) {
                case BUFFER_DATA_READY:
                    byteBuffer = MainActivity.this.screenCaptureBinder.acquireByteBuffer();
                    Message subThreadMessage = Message.obtain();
                    subThreadMessage.what = BUFFER_DATA_READY;
                    previewHandler.sendMessage(subThreadMessage);
                    saveVideoHandler.sendMessage(subThreadMessage);
            }
            return false;
        }
    });
    private Handler previewHandler;
    private Handler saveVideoHandler;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        init();
        checkPermissions();
        connectService();
    }

    /**
     * 初始化
     */
    private void init() {
        btnStartCapture = findViewById(R.id.start_capture);
        btnStopCapture = findViewById(R.id.end_capture);
        btnStartPreview = findViewById(R.id.start_preview);
        btnScreenshot = findViewById(R.id.screenshot);
        ivScreenshot = findViewById(R.id.screenshot_image);
        surfaceView = findViewById(R.id.surface);

        mediaProjectionManager = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        initDisplayInfo();

        View.OnClickListener startCaptureOnClickListener = view -> {
            startCaptureScreen();
        };
        View.OnClickListener endCaptureOnClickListener = view -> {
            stopCaptureScreen();
        };
        View.OnClickListener startPreviewOnClickListener = view -> {
            startPreview();
        };
        View.OnClickListener screenshotOnClickListener = view -> {
            doScreenshot();
        };

        btnStartCapture.setOnClickListener(startCaptureOnClickListener);
        btnStopCapture.setOnClickListener(endCaptureOnClickListener);
        btnScreenshot.setOnClickListener(screenshotOnClickListener);
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
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            isScreenCaptureServiceConnected = false;
        }
    };

    private void startPreviewVideoDecoder() {
        try {
            previewDecoder = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_VIDEO_AVC);
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }
        final MediaFormat format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, displayWidth, displayHeight);
        format.setInteger(MediaFormat.KEY_BIT_RATE,  displayWidth * displayHeight);
        format.setInteger(MediaFormat.KEY_FRAME_RATE, 30);
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);
//        //横屏
//        byte[] header_sps = {0, 0, 0, 1, 103, 66, -128, 31, -38, 1, 64, 22, -24, 6, -48, -95, 53};
//        byte[] header_pps = {0, 0 ,0, 1, 104, -50, 6, -30};
        //竖屏
        byte[] header_sps = {0, 0, 0, 1, 103, 66, -128, 31, -38, 2, -48, 40, 104, 6, -48, -95, 53};
        byte[] header_pps = {0, 0 ,0, 1, 104, -50, 6, -30};
//        format.setByteBuffer("csd-0", ByteBuffer.wrap(header_sps));
//        format.setByteBuffer("csd-1", ByteBuffer.wrap(header_pps));
        previewDecoder.configure(format, surfaceView.getHolder().getSurface(), null, 0);//mSurface对应需要展示surfaceview的surface
        previewDecoder.start();
    }

    private Thread previewThread = new Thread(new Runnable() {
        @Override
        public void run() {
            startPreviewVideoDecoder();
            Looper.prepare();
            previewHandler = new Handler(new Handler.Callback() {
                @Override
                public boolean handleMessage(@NonNull Message message) {
                    switch (message.what) {
                        case BUFFER_DATA_READY:
                            return false;
                        default:
                            return false;
                    }
                }
            });
            Looper.loop();
        }
    });

    private Thread saveVideoThread = new Thread(new Runnable() {
        @Override
        public void run() {
            Looper.prepare();
            saveVideoHandler = new Handler(new Handler.Callback() {
                @Override
                public boolean handleMessage(@NonNull Message message) {
                    switch (message.what) {
                        case BUFFER_DATA_READY:
                            return false;
                        default:
                            return false;
                    }
                }
            });
            Looper.loop();
        }
    });
    /**
     * 开始录屏
     */
    private void startCaptureScreen() {
        Intent screenCaptureIntent = mediaProjectionManager.createScreenCaptureIntent();
        startActivityForResult(screenCaptureIntent, SCREEN_CAPTURE_INTENT_REQUEST_CODE);

        btnStartCapture.setEnabled(false);
        btnStartCapture.setEnabled(true);
        btnStopCapture.setEnabled(true);

        // 启动预览解码器线程
        previewThread.start();
        isRecording = true;
        Toast.makeText(this, R.string.start_capture, Toast.LENGTH_SHORT).show();
    }

    /**
     * 结束录屏
     */
    private void stopCaptureScreen() {
        screenCaptureBinder.stopCapture();

        btnStartCapture.setEnabled(true);
        btnStopCapture.setEnabled(false);
        btnStartCapture.setEnabled(false);

        isRecording = false;
        Toast.makeText(this, R.string.stop_capture, Toast.LENGTH_SHORT).show();
    }

    /**
     * 开启预览
     */
    private void startPreview() {

    }

    /**
     * 截屏
     */
    private void doScreenshot() {
        if (!isRecording) {

        } else {

        }
    }

    /**
     * 接收授权结果
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if(requestCode == SCREEN_CAPTURE_INTENT_REQUEST_CODE) {
            if(resultCode == RESULT_OK) {
                Bundle bundle = new Bundle();
                bundle.putInt("code", resultCode);
                bundle.putParcelable("data", data);
                screenCaptureBinder.registerScreenCaptureRequestInfo(bundle);
                screenCaptureBinder.prepareCaptureEnviroment();
                screenCaptureBinder.startCapture();
                Log.i(TAG, "Started screen recording");
            } else {
                Log.i(TAG, "User cancelled");
            }
        }
    }

    private void handleVideoStream() {

    }
}