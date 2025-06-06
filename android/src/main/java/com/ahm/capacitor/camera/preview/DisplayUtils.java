package com.ahm.capacitor.camera.preview;

import android.app.Activity;
import android.graphics.Insets;
import android.graphics.Point;
import android.os.Build;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowMetrics;

/**
 * Utility class for retrieving accurate screen dimensions, supporting
 * edge-to-edge layouts introduced in Android 11+ and used by Capacitor 7.
 */
public class DisplayUtils {

    /**
     * Gets the actual screen height in pixels, optionally excluding system insets
     * such as the status bar and navigation bar.
     *
     * @param activity       The activity from which to retrieve screen metrics.
     * @param useSafeArea  If true, excludes system bars (safe area) from the result.
     * @return The usable screen height in pixels.
     */
    public static int getActualScreenHeight(Activity activity, boolean useSafeArea) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowMetrics metrics = activity.getWindowManager().getCurrentWindowMetrics();
            Insets insets = metrics.getWindowInsets().getInsetsIgnoringVisibility(WindowInsets.Type.systemBars());

            int height = metrics.getBounds().height();
            return useSafeArea ? height - insets.top - insets.bottom : height;
        } else {
            View contentView = activity.getWindow().getDecorView().findViewById(android.R.id.content);
            if (contentView != null) {
                return contentView.getHeight();
            }

            // Fallback
            Point size = new Point();
            activity.getWindowManager().getDefaultDisplay().getSize(size);
            return size.y;
        }
    }

    /**
     * Gets the actual screen width in pixels, optionally excluding system insets
     * such as gesture bars on the sides.
     *
     * @param activity       The activity from which to retrieve screen metrics.
     * @param useSafeArea  If true, excludes system bars (safe area) from the result.
     * @return The usable screen width in pixels.
     */
    public static int getActualScreenWidth(Activity activity, boolean useSafeArea) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowMetrics metrics = activity.getWindowManager().getCurrentWindowMetrics();
            Insets insets = metrics.getWindowInsets().getInsetsIgnoringVisibility(WindowInsets.Type.systemBars());

            int width = metrics.getBounds().width();
            return useSafeArea ? width - insets.left - insets.right : width;
        } else {
            View contentView = activity.getWindow().getDecorView().findViewById(android.R.id.content);
            if (contentView != null) {
                return contentView.getWidth();
            }

            // Fallback
            Point size = new Point();
            activity.getWindowManager().getDefaultDisplay().getSize(size);
            return size.x;
        }
    }
}
