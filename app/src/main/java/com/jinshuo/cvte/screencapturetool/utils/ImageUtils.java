package com.jinshuo.cvte.screencapturetool.utils;

import android.graphics.Bitmap;
import android.media.Image;

import java.nio.ByteBuffer;

public class ImageUtils {
    public static Bitmap Image2Bitmap(Image image, int screenWidth, int screenHeight) {
        Image.Plane[] planes = image.getPlanes();
        ByteBuffer buffer = planes[0].getBuffer();
        int pixelStride = planes[0].getPixelStride();
        int rowStride = planes[0].getRowStride();
        int rowPadding = rowStride - pixelStride * screenWidth;

        Bitmap bitmap = Bitmap.createBitmap(screenWidth + rowPadding / pixelStride,
                screenHeight, Bitmap.Config.ARGB_8888);
        bitmap.copyPixelsFromBuffer(buffer);
        return bitmap;
    }
}
