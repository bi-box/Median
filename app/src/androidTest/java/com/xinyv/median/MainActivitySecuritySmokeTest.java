package com.xinyv.median;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.webkit.WebSettings;
import android.webkit.WebView;

import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicReference;

@RunWith(AndroidJUnit4.class)
public final class MainActivitySecuritySmokeTest {
    @Test
    public void webViewSecurityDefaultsRemainHardened() throws Exception {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            AtomicReference<Throwable> failure = new AtomicReference<Throwable>();
            scenario.onActivity(activity -> {
                try {
                    WebView webView = webView(activity);
                    WebSettings settings = webView.getSettings();
                    assertFalse(settings.getAllowFileAccess());
                    assertFalse(settings.getAllowContentAccess());
                    assertFalse(settings.getSaveFormData());
                    assertTrue(settings.getMediaPlaybackRequiresUserGesture());
                    assertFalse(settings.getJavaScriptCanOpenWindowsAutomatically());
                    assertTrue(settings.getSafeBrowsingEnabled());
                    assertTrue(settings.getMixedContentMode() == WebSettings.MIXED_CONTENT_NEVER_ALLOW);
                } catch (Throwable error) {
                    failure.set(error);
                }
            });
            if (failure.get() != null) throw new AssertionError(failure.get());
        }
    }

    @Test
    public void exportedActivityRejectsNonHttpExplicitIntent() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        Intent malicious = new Intent(Intent.ACTION_VIEW, Uri.parse("file:///sdcard/private.txt"));
        malicious.setClass(context, MainActivity.class);
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(malicious)) {
            AtomicReference<Throwable> failure = new AtomicReference<Throwable>();
            scenario.onActivity(activity -> {
                try {
                    String current = currentPageUrl(activity);
                    assertNotNull(current);
                    String lower = current.toLowerCase();
                    assertFalse(lower.startsWith("file:"));
                    assertFalse(lower.startsWith("content:"));
                    assertFalse(lower.startsWith("data:"));
                    assertFalse(lower.startsWith("javascript:"));
                } catch (Throwable error) {
                    failure.set(error);
                }
            });
            if (failure.get() != null) throw new AssertionError(failure.get());
        }
    }

    private static String currentPageUrl(MainActivity activity) throws Exception {
        Field field = MainActivity.class.getDeclaredField("currentPageUrl");
        field.setAccessible(true);
        Object value = field.get(activity);
        return value == null ? null : value.toString();
    }

    private static WebView webView(MainActivity activity) throws Exception {
        Field field = MainActivity.class.getDeclaredField("webView");
        field.setAccessible(true);
        WebView value = (WebView) field.get(activity);
        assertNotNull(value);
        return value;
    }
}
