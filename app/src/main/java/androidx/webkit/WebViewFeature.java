/*
 * Copyright 2018 The Android Open Source Project
 * Licensed under the Apache License, Version 2.0.
 */
package androidx.webkit;

import android.os.Build;

import androidx.webkit.internal.WebViewGlueCommunicator;

/** AndroidX-compatible feature facade used by Median. */
public final class WebViewFeature {
    public static final String DOCUMENT_START_SCRIPT = "DOCUMENT_START_SCRIPT";
    public static final String ALGORITHMIC_DARKENING = "ALGORITHMIC_DARKENING";

    private WebViewFeature() {}

    public static boolean isFeatureSupported(String feature) {
        if (feature == null) throw new NullPointerException("feature");
        if (DOCUMENT_START_SCRIPT.equals(feature))
            return WebViewGlueCommunicator.isFeatureSupported("DOCUMENT_START_SCRIPT:1");
        if (ALGORITHMIC_DARKENING.equals(feature))
            return Build.VERSION.SDK_INT >= 33 ||
                    WebViewGlueCommunicator.isFeatureSupported(ALGORITHMIC_DARKENING);
        throw new RuntimeException("Unknown feature " + feature);
    }
}
