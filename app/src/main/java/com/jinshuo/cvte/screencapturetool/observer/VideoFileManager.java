package com.jinshuo.cvte.screencapturetool.observer;

import android.media.MediaCodec;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.util.Log;

import com.jinshuo.cvte.screencapturetool.FrameData;
import com.jinshuo.cvte.screencapturetool.ScreenCaptureApplication;
import com.jinshuo.cvte.screencapturetool.utils.StorageUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;

public class VideoFileManager implements VideoStreamObserver {
    private static final String TAG = "VideoFileManager";
    OutputStream outputStream;
    MediaMuxer mediaMuxer;

    MediaFormat format;
    MediaFormat outputMediaFormat;

    int trackId = -1;

    @Override
    public void start() {
        initOutputStream();
        mediaMuxer.start();
    }

    @Override
    public void dataReady(FrameData frameData) {
        doSaveVideo(frameData);
    }

    @Override
    public void stop() {
        releaseOutputStream();
        mediaMuxer.stop();
        mediaMuxer.release();
    }

    @Override
    public void encoderEncodeMediaFormatChange(MediaFormat format) {
    }

    @Override
    public void encoderOutputMediaFormatChange(MediaFormat format) {
        outputMediaFormat = format;
    }

    /**
     * 创建h264文件，初始化输出流
     */
    private void initOutputStream() {
        Log.d(TAG, "initOutputStream: ");
        // 创建输出目录
        String outputDirectory = ScreenCaptureApplication.getInstance().getExternalFilesDir("") + "/ScreenCapture";
        StorageUtils.makeDirectory(outputDirectory);

        // 创建文件输出流
        String filenameBase = StorageUtils.generateFilename("ScreenCapture");
        String filenameH264 = filenameBase + ".h264";
        String filenameMp4 = filenameBase + ".mp4";
        File fileH264 = new File(outputDirectory, filenameH264);
        File fileMp4 = new File(outputDirectory, filenameMp4);
        try {
            outputStream = new FileOutputStream(fileH264);
            mediaMuxer = new MediaMuxer(fileMp4.getPath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            trackId = mediaMuxer.addTrack(outputMediaFormat);
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
    private void doSaveVideo(FrameData frameData) {
        try {
            ByteBuffer byteBuffer = ByteBuffer.allocate(frameData.getInfo().size);
            byteBuffer.put(frameData.getData());
            mediaMuxer.writeSampleData(trackId, byteBuffer, frameData.getInfo());
            outputStream.write(frameData.getData());
            Log.d(TAG, "doSaveVideo: write a frame to file");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
