package com.jinshuo.cvte.screencapturetool.publishier;

import com.jinshuo.cvte.screencapturetool.observer.VideoStreamObserver;

public interface VideoStreamPublisher {
    void addObserver(VideoStreamObserver observer);
    void removeObserver(VideoStreamObserver observer);
    void notifyStart();
    void notifyDataReady(byte[] data);
    void notifyStop();

}
