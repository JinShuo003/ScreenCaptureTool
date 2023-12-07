package com.jinshuo.cvte.screencapturetool.observer;

public interface VideoStreamObserver {
    void start();
    void update(byte[] data);
    void stop();
}
