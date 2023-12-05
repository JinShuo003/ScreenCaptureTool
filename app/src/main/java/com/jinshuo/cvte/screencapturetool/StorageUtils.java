package com.jinshuo.cvte.screencapturetool;

import android.util.Log;

import java.io.File;

public class StorageUtils {
    private static final String TAG = "StorageUtils";
    public static void makeDirectory(String path){
        File file = new File(path);
        if (!file.exists()) {
            if(!file.mkdirs()) {
                Log.d(TAG, "makeDirectory: make directory failed");
            }
        }
    }
}
