package com.jinshuo.cvte.screencapturetool.observerInterface;

public interface FrameDataPublisher {
    void addObserver(FrameDataObserver observer);
    void removeObserver(FrameDataObserver observer);
    void notifyStart();
    void notifyDataReady(byte[] data);
    void notifyStop();

}
