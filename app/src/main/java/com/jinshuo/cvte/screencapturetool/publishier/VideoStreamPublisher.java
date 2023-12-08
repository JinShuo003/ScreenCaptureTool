package com.jinshuo.cvte.screencapturetool.publishier;

import android.media.MediaCodec;
import android.media.MediaFormat;

import com.jinshuo.cvte.screencapturetool.FrameData;
import com.jinshuo.cvte.screencapturetool.observer.VideoStreamObserver;

public interface VideoStreamPublisher {
    void addObserver(VideoStreamObserver observer);
    void removeObserver(VideoStreamObserver observer);
    void notifyStart();
    void notifyDataReady(FrameData frameData);
    void notifyStop();

}
