package com.jinshuo.cvte.screencapturetool;

import android.Manifest;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
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

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.ArrayBlockingQueue;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private Button btnRecordControl;
    private Button btnScreenshot;
    private ImageView ivScreenshot;
    private SurfaceView surfaceView;
    private SurfaceHolder surfaceHolder;

    private boolean isScreenCaptureServiceConnected = false;

    /**
     * 用于接收编码数据的callback
     */
    ByteBuffer encoderConfigDataBuffer;
    MediaFormat mediaFormat;
    MediaCodec.BufferInfo outputBufferInfo;
    ByteBuffer outputBuffer;
    byte[] outputData;

    public interface EncoderStatusListener {
        void onEncoderCreated(MediaFormat format);

        void onConfigReady(ByteBuffer configBuffer);

        void onDataReady(MediaCodec.BufferInfo bufferInfo, ByteBuffer outputBuffer);
    }

    public void putData(byte[] data){
        if(queue.size()>=MAX_QUEUE_SIZE){
            queue.poll();
        }
        queue.add(data.clone());
    }

    EncoderStatusListener listener = new EncoderStatusListener() {
        @Override
        public void onEncoderCreated(MediaFormat mediaFormat) {
            MainActivity.this.mediaFormat = mediaFormat;
        }

        @Override
        public void onConfigReady(ByteBuffer configBuffer) {
            encoderConfigDataBuffer = configBuffer;
        }

        @Override
        public void onDataReady(MediaCodec.BufferInfo outputBufferInfo, ByteBuffer outputBuffer) {
            MainActivity.this.outputBufferInfo = outputBufferInfo;
            MainActivity.this.outputBuffer = outputBuffer;
            outputData = new byte[outputBufferInfo.size];
            outputBuffer.get(outputData);
            putData(outputData);
            doSaveVideo();
            doScreenshot();
        }
    };
    private ScreenCaptureService.ScreenCaptureBinder screenCaptureBinder;

    private boolean isRecording = false;
    private boolean isPreviewing = false;
    private MediaProjectionManager mediaProjectionManager;
    private MediaCodec previewDecoder;
    OutputStream outputStream;

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
                Log.d(TAG, "surfaceCreated: ");
            }

            @Override
            public void surfaceDestroyed(@NonNull SurfaceHolder surfaceHolder) {
                Log.d(TAG, "surfaceCreated: ");
            }
        });

        mediaProjectionManager = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        initDisplayInfo();

        btnRecordControl.setOnClickListener(startRecordOnClickListener);
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
            screenCaptureBinder.setEncoderStatusListener(listener);

        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            isScreenCaptureServiceConnected = false;
        }
    };

    /**
     * 异步解码，Input可用时写入最新的h264数据，Output可用时渲染到Surface上
     */
    MediaCodec.Callback decoderCallback = new MediaCodec.Callback() {
        @Override
        public void onInputBufferAvailable(@NonNull MediaCodec mediaCodec, int i) {
            Log.d(TAG, "onInputBufferAvailable: decoder, inputBuffer: " + i);
            if (i >= 0 && outputBuffer != null) {
                ByteBuffer buffer = mediaCodec.getInputBuffer(i);
                buffer.put(outputBuffer);
                mediaCodec.queueInputBuffer(i, 0, outputBufferInfo.size, computePresentationTime(i), 0);
            } else {
                mediaCodec.queueInputBuffer(i, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
            }
        }

        @Override
        public void onOutputBufferAvailable(@NonNull MediaCodec mediaCodec, int i, @NonNull MediaCodec.BufferInfo bufferInfo) {
            Log.d(TAG, "onOutputBufferAvailable: decoder, outputBuffer: " + i);
            ByteBuffer outputBuffer = mediaCodec.getOutputBuffer(i);
            mediaCodec.releaseOutputBuffer(i, true);
        }

        @Override
        public void onError(@NonNull MediaCodec mediaCodec, @NonNull MediaCodec.CodecException e) {
        }

        @Override
        public void onOutputFormatChanged(@NonNull MediaCodec mediaCodec, @NonNull MediaFormat mediaFormat) {
        }
    };

    private void startPreviewVideoDecoder() {
        try {
//            String mime = mediaFormat.getString(MediaFormat.KEY_MIME);
//            previewDecoder = MediaCodec.createDecoderByType(mime);
            previewDecoder = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_VIDEO_AVC);
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }
//        previewDecoder.setCallback(decoderCallback);

        final MediaFormat format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, displayWidth, displayHeight);
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        format.setInteger(MediaFormat.KEY_BIT_RATE, displayWidth * displayHeight);
        format.setInteger(MediaFormat.KEY_FRAME_RATE, 20);
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);
//        format.setByteBuffer("csd-0", encoderConfigDataBuffer);

        previewDecoder.configure(format, surfaceHolder.getSurface(), null, 0);
        previewDecoder.start();
    }

    /**
     * 创建h264文件，初始化输出流
     */
    private void initOutputStream() {
        // 创建输出目录
        String outputDirectory = getExternalFilesDir("") + "/ScreenCapture";
        StorageUtils.makeDirectory(outputDirectory);

        // 创建h264文件
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss");
        Date curDate = new Date(System.currentTimeMillis());
        String curTime = formatter.format(curDate).replace(" ", "");
        String screenCaptureFilename = "ScreenCapture_" + curTime + ".h264";

        File file = new File(outputDirectory, screenCaptureFilename);
        try {
            outputStream = new FileOutputStream(file);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 开始录屏
     */
    private void startRecordScreen() {
        initOutputStream();

        Intent screenCaptureIntent = mediaProjectionManager.createScreenCaptureIntent();
        startActivityForResult(screenCaptureIntent, SCREEN_CAPTURE_INTENT_REQUEST_CODE);

        btnRecordControl.setOnClickListener(stopRecordOnClickListener);
        btnRecordControl.setText(R.string.stop_record);
//        btnRecordControl.setBackground(getDrawable(R.drawable.stop_record_btn_background));

        isRecording = true;
        Toast.makeText(this, R.string.start_record, Toast.LENGTH_LONG).show();
    }

    public void stopDecoderThread(){
        isRunning =false;
    }
    /**
     * 结束录屏
     */
    private void stopRecordScreen() {
        stopDecoderThread();
        screenCaptureBinder.stopCapture();
        previewDecoder.stop();
        previewDecoder.release();

        btnRecordControl.setOnClickListener(startRecordOnClickListener);
        btnRecordControl.setText(R.string.start_record);

//        btnRecordControl.setBackground(getDrawable(R.drawable.start_record_btn_background));

        isRecording = false;
        Toast.makeText(this, R.string.start_record, Toast.LENGTH_SHORT).show();
    }

    /**
     * 保存文件
     */
    private void doSaveVideo() {
//        byte[] outData = new byte[outputBufferInfo.size];
//        outputBuffer.get(outData);
        try {
            outputStream.write(outputData);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private long computePresentationTime(long frameIndex) {
        return 132 + frameIndex * 1000000 / 20;
    }

    /**
     * 截屏
     */
    private void doScreenshot() {
        if (!isRecording) {

        } else {

        }
    }

    private boolean isRunning = false;
    private static final int MAX_QUEUE_SIZE = 1000;
    private ArrayBlockingQueue queue = new ArrayBlockingQueue<>(MAX_QUEUE_SIZE);


    public void startDecoderThread() {
        Thread decoderThread = new Thread(new Runnable() {
            @Override
            public void run() {
                isRunning = true;
                byte[] input = null;
                while (isRunning) {
                    if (queue.size() > 0) {
                        input = (byte[]) queue.poll();
                    }
                    if (input != null) {
                        try {
                            int inputBufferIndex = previewDecoder.dequeueInputBuffer(0);
                            ByteBuffer inputBuffer;
                            if (inputBufferIndex >= 0) {
                                inputBuffer = previewDecoder.getInputBuffer(inputBufferIndex);
                                inputBuffer.clear();
                                inputBuffer.put(input, 0, input.length);
                                previewDecoder.queueInputBuffer(inputBufferIndex, 0, input.length, computePresentationTime(inputBufferIndex), 0);
                            }
                            MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
                            int outputBufferIndex = previewDecoder.dequeueOutputBuffer(bufferInfo, 0);
                            while (outputBufferIndex >= 0) {
                                previewDecoder.releaseOutputBuffer(outputBufferIndex, true);
                                outputBufferIndex = previewDecoder.dequeueOutputBuffer(bufferInfo, 0);
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    } else {
                        try {
                            Thread.sleep(500);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        });
        decoderThread.start();
    }

    /**
     * 接收授权结果
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == SCREEN_CAPTURE_INTENT_REQUEST_CODE) {
            if (resultCode == RESULT_OK) {
                startPreviewVideoDecoder();
                startDecoderThread();

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
}