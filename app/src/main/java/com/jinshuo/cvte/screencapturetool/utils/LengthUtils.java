package com.jinshuo.cvte.screencapturetool.utils;

import android.content.Context;

public class LengthUtils {
    public static int px2dp(Context context, int px) {
        return px * 160 / context.getResources().getDisplayMetrics().densityDpi;
    }

    public static int dp2px(Context context, int dp) {
        return dp * context.getResources().getDisplayMetrics().densityDpi / 160;
    }
}
