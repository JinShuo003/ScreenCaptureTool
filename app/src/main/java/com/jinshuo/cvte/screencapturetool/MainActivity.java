package com.jinshuo.cvte.screencapturetool;

import android.Manifest;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "ScreenCaptureTool_MAIN";
    private Button btnStartCapture;
    private Button btnStopCapture;
    private ScreenCaptureService.ScreenCaptureBinder screenCaptureBinder;
    private MediaProjectionManager mediaProjectionManager;
    private static final int SCREEN_CAPTURE_REQUEST_CODE = 1000;
    private DisplayMetrics displayMetrics;

    boolean isUserAuthorized = false;

    public void checkPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            String[] permissions = new String[]{
                    Manifest.permission.RECORD_AUDIO,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    Manifest.permission.READ_EXTERNAL_STORAGE};
            for (String permission : permissions) {
                if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(this, permissions, 200);
                    return;
                }
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        init();
        checkPermission();
    }

    /**
     * 初始化
     */
    private void init() {
        btnStartCapture = findViewById(R.id.start_capture);
        btnStopCapture = findViewById(R.id.end_capture);
        mediaProjectionManager = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        displayMetrics = ScreenUtils.getScreenMetrics(this);

        connectService();

        View.OnClickListener startCaptureOnClickListener = view -> {
            startCaptureScreen();
        };
        View.OnClickListener endCaptureOnClickListener = view -> {
            stopCaptureScreen();
        };
        btnStartCapture.setOnClickListener(startCaptureOnClickListener);
        btnStopCapture.setOnClickListener(endCaptureOnClickListener);
    }

    /**
     * 绑定到截屏服务
     */
    private void connectService() {
        Intent intent = new Intent(this, ScreenCaptureService.class);
        bindService(intent, serviceConnection, BIND_AUTO_CREATE);
    }

    /**
     * 绑定截屏服务的connection对象
     */
    private ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            screenCaptureBinder = (ScreenCaptureService.ScreenCaptureBinder) iBinder;
            screenCaptureBinder.registerScreenInfo(displayMetrics);
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {

        }
    };

    /**
     * 开始录屏
     */
    private void startCaptureScreen() {
        btnStartCapture.setEnabled(false);
        btnStopCapture.setEnabled(true);
        if (!isUserAuthorized) {
            Intent screenCaptureIntent = mediaProjectionManager.createScreenCaptureIntent();
            startActivityForResult(screenCaptureIntent, SCREEN_CAPTURE_REQUEST_CODE);
        } else {
            screenCaptureBinder.startCapture();
        }
        Toast.makeText(this, R.string.start_capture, Toast.LENGTH_SHORT).show();
    }

    /**
     * 结束录屏
     */
    private void stopCaptureScreen() {
        screenCaptureBinder.stopCapture();
        Toast.makeText(this, R.string.stop_capture, Toast.LENGTH_SHORT).show();
        btnStartCapture.setEnabled(true);
        btnStopCapture.setEnabled(false);
    }

    /**
     * 接收用户授权结果
     * @param requestCode
     * @param resultCode
     * @param data
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if(requestCode == SCREEN_CAPTURE_REQUEST_CODE) {
            if(resultCode == RESULT_OK) {
                isUserAuthorized = true;
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