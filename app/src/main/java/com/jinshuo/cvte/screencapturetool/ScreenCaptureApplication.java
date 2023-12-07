package com.jinshuo.cvte.screencapturetool;

import android.app.Application;

public class ScreenCaptureApplication extends Application {
    private static Application instance;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
    }

    public static Application getInstance() {
        return instance;
    }
}
