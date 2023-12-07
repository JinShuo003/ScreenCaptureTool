package com.jinshuo.cvte.screencapturetool.observer;

import android.graphics.Bitmap;

import com.jinshuo.cvte.screencapturetool.MainActivity;
import com.jinshuo.cvte.screencapturetool.ScreenCaptureApplication;
import com.jinshuo.cvte.screencapturetool.observerInterface.FrameDataObserver;
import com.jinshuo.cvte.screencapturetool.utils.StorageUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

public class ScreenshotManager implements FrameDataObserver {
    private static final String TAG = "ScreenshotManager";
    byte[] latestFrame;

    @Override
    public void start() {

    }

    @Override
    public void update(byte[] data) {
        latestFrame = data;
    }

    @Override
    public void stop() {

    }

    private Bitmap frameToBitmap(byte[] data) {
        return null;
    }

    MainActivity.OnScreenshotReadyListener listener;

    public void setOnScreenshotReadyListener(MainActivity.OnScreenshotReadyListener listener) {
        this.listener = listener;
    }

    public void requestScreenshot() {
        Bitmap screenshot = frameToBitmap(latestFrame);
        saveImage(screenshot);
        listener.onScreenshotReady(screenshot);
    }

    public void saveImage(Bitmap screenshot) {
        // 创建输出目录
        String outputDirectory = ScreenCaptureApplication.getInstance().getExternalFilesDir("") + "/Screenshot";
        StorageUtils.makeDirectory(outputDirectory);

        // 获取文件名
        String filename = StorageUtils.generateFilename("Screenshot") + ".png";

        File saveFile = new File(outputDirectory, filename);
        try {
            FileOutputStream saveImgOut = new FileOutputStream(saveFile);
            screenshot.compress(Bitmap.CompressFormat.PNG, 100, saveImgOut);
            saveImgOut.flush();
            saveImgOut.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
