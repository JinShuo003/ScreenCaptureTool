package com.jinshuo.cvte.screencapturetool;

import android.util.Log;

import com.jinshuo.cvte.screencapturetool.observer.VideoStreamObserver;
import com.jinshuo.cvte.screencapturetool.observerInterface.VideoStreamPublisher;

import java.util.HashSet;
import java.util.concurrent.TransferQueue;

public class VideoStreamConsumer implements VideoStreamPublisher {
    private static final String TAG = "FrameDataComsumer";
    boolean allowWork = true;
    TransferQueue frameQueue;
    HashSet<VideoStreamObserver> observerSet = new HashSet<>();

    /**
     * 开启线程
     */
    public void startConsume() {
        Log.d(TAG, "startConsume: ");
        allowWork = true;
        notifyStart();
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                byte[] input;
                while (allowWork) {
                    try {
                        input = (byte[]) frameQueue.take();
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                    if (input != null) {
                        notifyDataReady(input);
                        Log.d(TAG, "startDecoderThread: current frame consumed, frameQueue.size: " + frameQueue.size());
                    }
                }
            }
        });
        thread.start();
    }

    /**
     * 终止线程
     */
    public void stopConsume() {
        Log.d(TAG, "stopConsume: ");
        allowWork = false;
        notifyStop();
    }

    /**
     * 注册缓冲区
     * @param frameQueue
     */
    public void setBuffer(TransferQueue frameQueue) {
        this.frameQueue = frameQueue;
    }

    @Override
    public void addObserver(VideoStreamObserver observer) {
        observerSet.add(observer);
    }

    @Override
    public void removeObserver(VideoStreamObserver observer) {
        observerSet.remove(observer);
    }

    @Override
    public void notifyStart() {
        for (VideoStreamObserver observer: observerSet) {
            observer.start();
        }
    }

    @Override
    public void notifyDataReady(byte[] data) {
        for (VideoStreamObserver observer: observerSet) {
            observer.update(data);
        }
    }

    @Override
    public void notifyStop() {
        for (VideoStreamObserver observer: observerSet) {
            observer.stop();
        }
    }
}
