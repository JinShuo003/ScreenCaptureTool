package com.jinshuo.cvte.screencapturetool;

import android.util.Log;

import com.jinshuo.cvte.screencapturetool.observerInterface.FrameDataObserver;
import com.jinshuo.cvte.screencapturetool.observerInterface.FrameDataPublisher;

import java.util.HashSet;
import java.util.concurrent.TransferQueue;

public class FrameDataConsumer implements FrameDataPublisher {
    private static final String TAG = "FrameDataComsumer";
    boolean allowWork = true;
    TransferQueue frameQueue;
    HashSet<FrameDataObserver> observerSet = new HashSet<>();

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
    public void addObserver(FrameDataObserver observer) {
        observerSet.add(observer);
    }

    @Override
    public void removeObserver(FrameDataObserver observer) {
        observerSet.remove(observer);
    }

    @Override
    public void notifyStart() {
        for (FrameDataObserver observer: observerSet) {
            observer.start();
        }
    }

    @Override
    public void notifyDataReady(byte[] data) {
        for (FrameDataObserver observer: observerSet) {
            observer.update(data);
        }
    }

    @Override
    public void notifyStop() {
        for (FrameDataObserver observer: observerSet) {
            observer.stop();
        }
    }
}
