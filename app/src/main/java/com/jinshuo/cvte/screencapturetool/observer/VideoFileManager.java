package com.jinshuo.cvte.screencapturetool.observer;

import android.app.Application;
import android.content.Context;
import android.os.Environment;
import android.util.Log;

import com.jinshuo.cvte.screencapturetool.ScreenCaptureApplication;
import com.jinshuo.cvte.screencapturetool.utils.StorageUtils;
import com.jinshuo.cvte.screencapturetool.observerInterface.FrameDataObserver;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;

public class VideoFileManager implements FrameDataObserver {
    private static final String TAG = "VideoFileManager";
    OutputStream outputStream;

    @Override
    public void start() {
        initOutputStream();
    }

    @Override
    public void update(byte[] data) {
        doSaveVideo(data);
    }

    @Override
    public void stop() {
        releaseOutputStream();
    }

    /**
     * 创建h264文件，初始化输出流
     */
    private void initOutputStream() {
        Log.d(TAG, "initOutputStream: ");
        // 创建输出目录
        String outputDirectory = ScreenCaptureApplication.getInstance().getExternalFilesDir("") + "/ScreenCapture";
        StorageUtils.makeDirectory(outputDirectory);

        // 创建h264文件
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss");
        Date curDate = new Date(System.currentTimeMillis());
        String curTime = formatter.format(curDate).replace(" ", "");
        String screenCaptureFilename = "ScreenCapture_" + curTime + ".h264";

        // 创建文件输出流
        File file = new File(outputDirectory, screenCaptureFilename);
        try {
            outputStream = new FileOutputStream(file);
        } catch (Exception e) {
            e.printStackTrace();
        }
        Log.d(TAG, "initOutputStream: outputStream ready");
    }

    private void releaseOutputStream() {
        Log.d(TAG, "releaseOutputStream: ");
        try {
            outputStream.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        if (null != outputStream) {
            outputStream = null;
            Log.d(TAG, "releaseOutputStream: outputstream released");
        }
    }

    /**
     * 保存文件
     */
    private void doSaveVideo(byte[] frameData) {
        try {
            outputStream.write(frameData);
            Log.d(TAG, "doSaveVideo: write a frame to file");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
