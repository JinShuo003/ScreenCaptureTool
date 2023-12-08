package com.jinshuo.cvte.screencapturetool;

import android.media.MediaCodec;

public class FrameData {
    private byte[] data;
    private MediaCodec.BufferInfo info;

    public FrameData(byte[] data, MediaCodec.BufferInfo info) {
        this.data = data;
        this.info = info;
    }

    public byte[] getData() {
        return data;
    }

    public MediaCodec.BufferInfo getInfo() {
        return info;
    }
}
