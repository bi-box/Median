/*
 * Copyright 2018 The Android Open Source Project
 * Licensed under the Apache License, Version 2.0.
 */
package androidx.webkit;

import android.os.Build;
import android.webkit.WebSettings;

import androidx.webkit.internal.WebViewGlueCommunicator;

/** Focused AndroidX-compatible WebSettings surface used by Median. */
public final class WebSettingsCompat {
    private WebSettingsCompat() {}

    public static void setAlgorithmicDarkeningAllowed(WebSettings settings, boolean allow) {
        if (settings == null) throw new NullPointerException("settings");
        if (Build.VERSION.SDK_INT >= 33) settings.setAlgorithmicDarkeningAllowed(allow);
        else WebViewGlueCommunicator.setAlgorithmicDarkeningAllowed(settings, allow);
    }
}
