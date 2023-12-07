package com.jinshuo.cvte.screencapturetool.utils;

import android.util.Log;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;

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

    /**
     * 以$tag$_$current_datetime$为格式生成文件名
     */
    public static String generateFilename(String tag) {
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss");
        Date curDate = new Date(System.currentTimeMillis());
        String curTime = formatter.format(curDate).replace(" ", "");
        String filename = tag + "_" + curTime;

        return filename;
    }
}
