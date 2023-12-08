package com.jinshuo.cvte.screencapturetool.observer;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.util.Log;
import android.view.Surface;

import com.jinshuo.cvte.screencapturetool.FrameData;

import java.io.IOException;
import java.nio.Buffer;
import java.nio.ByteBuffer;

public class PreviewManager implements VideoStreamObserver {
    private static final String TAG = "PreviewManager";
    private MediaCodec previewDecoder;

    int displayWidth = 0;
    int displayHeight = 0;
    Surface surface;

    public void registerPreviewInfo(int displayWidth, int displayHeight, Surface surface) {
        this.displayWidth = displayWidth;
        this.displayHeight = displayHeight;
        this.surface = surface;
    }

    public void initVideoDecoder() {
        if (null != previewDecoder) {
            return;
        }
        Log.d(TAG, "initVideoDecoder: ");
        try {
            previewDecoder = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_VIDEO_AVC);
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }
//        previewDecoder.setCallback(decoderCallback);

        MediaFormat format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, displayWidth, displayHeight);
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        format.setInteger(MediaFormat.KEY_BIT_RATE, displayWidth * displayHeight);
        format.setInteger(MediaFormat.KEY_FRAME_RATE, 20);
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);

        previewDecoder.configure(format, surface, null, 0);
        Log.d(TAG, "initVideoDecoder: videoDecoder ready, ");
    }

    public void releaseVideoDecoder() {
        previewDecoder.stop();
        previewDecoder.release();
        previewDecoder = null;
    }
    /**
     * 保存文件
     */
    private void doDecodeFrame(FrameData frameData) {
        Log.d(TAG, "doDecodeFrame: ThreadId: " + Thread.currentThread().getId());
        try {
            int inputBufferIndex = previewDecoder.dequeueInputBuffer(0);
            ByteBuffer inputBuffer;
            if (inputBufferIndex >= 0) {
                inputBuffer = previewDecoder.getInputBuffer(inputBufferIndex);
                inputBuffer.clear();
                inputBuffer.put(frameData.getData(), 0, frameData.getInfo().size);
                previewDecoder.queueInputBuffer(inputBufferIndex, 0, frameData.getInfo().size, computePresentationTime(inputBufferIndex), 0);
                Log.d(TAG, "doDecodeFrame: send a frame to decoder");
            }
            MediaCodec.BufferInfo _bufferInfo = new MediaCodec.BufferInfo();
            int outputBufferIndex = previewDecoder.dequeueOutputBuffer(_bufferInfo, 0);
            while (outputBufferIndex >= 0) {
                previewDecoder.releaseOutputBuffer(outputBufferIndex, true);
                Log.d(TAG, "doDecodeFrame: render a frame to surface");
                outputBufferIndex = previewDecoder.dequeueOutputBuffer(_bufferInfo, 0);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private long computePresentationTime(long frameIndex) {
        return 132 + frameIndex * 1000000 / 20;
    }

    @Override
    public void start() {
        initVideoDecoder();
        previewDecoder.start();
        Log.d(TAG, "initVideoDecoder: videoDecoder begin work");
    }

    @Override
    public void dataReady(FrameData frameData) {
        doDecodeFrame(frameData);
    }

    @Override
    public void stop() {
        releaseVideoDecoder();
    }

    @Override
    public void encoderEncodeMediaFormatChange(MediaFormat format) {

    }

    @Override
    public void encoderOutputMediaFormatChange(MediaFormat format) {

    }
}
