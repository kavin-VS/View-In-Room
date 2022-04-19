package com.android.sofa;

import android.app.Activity;
import android.graphics.Bitmap;
import android.view.View;
import android.view.View.MeasureSpec;

public class ScreenshotUtil {
    private static ScreenshotUtil mInstance;

    private ScreenshotUtil() {
    }

    public static ScreenshotUtil getInstance() {
        if (mInstance == null) {
            synchronized (ScreenshotUtil.class) {
                if (mInstance == null) {
                    mInstance = new ScreenshotUtil();
                }
            }
        }
        return mInstance;
    }

    public Bitmap takeScreenshotForView(View view) {
        view.measure(MeasureSpec.makeMeasureSpec(view.getWidth(), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(view.getHeight(), MeasureSpec.EXACTLY));
        view.layout((int) view.getX(), (int) view.getY(), (int) view.getX() + view.getMeasuredWidth(), (int) view.getY() + view.getMeasuredHeight());

        view.setDrawingCacheEnabled(true);
        view.buildDrawingCache(true);
        Bitmap bitmap = Bitmap.createBitmap(view.getDrawingCache());
        view.setDrawingCacheEnabled(false);

        return bitmap;
    }

    public Bitmap takeScreenshotForScreen(Activity activity) {
        return takeScreenshotForView(activity.getWindow().getDecorView().getRootView());
    }
}