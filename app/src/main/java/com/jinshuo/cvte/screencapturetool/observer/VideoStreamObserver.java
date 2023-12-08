package com.jinshuo.cvte.screencapturetool.observer;

import android.media.MediaFormat;

import com.jinshuo.cvte.screencapturetool.FrameData;

public interface VideoStreamObserver {
    void start();
    void dataReady(FrameData data);
    void encoderEncodeMediaFormatChange(MediaFormat format);
    void encoderOutputMediaFormatChange(MediaFormat format);
    void stop();
}
