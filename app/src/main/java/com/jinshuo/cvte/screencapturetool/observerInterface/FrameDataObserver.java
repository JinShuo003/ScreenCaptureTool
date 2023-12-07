package com.jinshuo.cvte.screencapturetool.observerInterface;

public interface FrameDataObserver {
    void start();
    void update(byte[] data);
    void stop();
}
