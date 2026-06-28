package com.LM.pack.util;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;

public final class DrawableUtils {

    private DrawableUtils() {
    }

    public static GradientDrawable roundedDrawable(Context context, String fillColor, String strokeColor, float radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setCornerRadius(CommonUtils.dp(context, radiusDp));
        if (fillColor != null) {
            drawable.setColor(android.graphics.Color.parseColor(fillColor));
        }
        if (strokeColor != null) {
            drawable.setStroke(CommonUtils.dp(context, 1f), android.graphics.Color.parseColor(strokeColor));
        }
        return drawable;
    }
}
