package com.LM.pack.util;

import android.content.Context;
import android.content.res.Resources;

public final class CommonUtils {

    private CommonUtils() {
    }

    public static int dp(Context context, float value) {
        return (int) (value * context.getResources().getDisplayMetrics().density);
    }

    public static int dp(Resources resources, float value) {
        return (int) (value * resources.getDisplayMetrics().density);
    }

    public static int dpSystem(float value) {
        return (int) (value * Resources.getSystem().getDisplayMetrics().density);
    }

    public static String safeText(String value) {
        return value == null ? "" : value.trim();
    }

    public static String safeText(String value, String fallback) {
        if (value == null || value.trim().length() == 0) {
            return fallback;
        }
        return value.trim();
    }

    public static int parseIntSafe(String value) {
        try {
            return Integer.parseInt(value);
        } catch (Exception e) {
            return -1;
        }
    }
}
