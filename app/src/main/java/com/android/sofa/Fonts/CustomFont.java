package com.android.sofa.Fonts;

import android.app.Application;

public class CustomFont extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        FontsOverride.setDefaultFont(this, "DEFAULT", "font/poppins_regular.ttf");
        FontsOverride.setDefaultFont(this, "MONOSPACE", "font/poppins_regular.ttf");
    }
}
