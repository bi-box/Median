package com.xinyv.median;

import android.content.Context;
import android.graphics.Bitmap;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;

/** Tiny on-device favicon cache populated from icons WebView already downloaded. */
final class FaviconStore {
    private final File directory;

    FaviconStore(Context context) {
        directory = new File(context.getCacheDir(), "favicons");
    }

    void put(String host, Bitmap icon) {
        String key = key(host);
        if (key.length() == 0 || icon == null) return;
        if (!directory.exists() && !directory.mkdirs()) return;
        Bitmap scaled = icon;
        try {
            if (icon.getWidth() > 64 || icon.getHeight() > 64)
                scaled = Bitmap.createScaledBitmap(icon, 64, 64, true);
            File target = new File(directory, key + ".png");
            File temporary = new File(directory, key + ".tmp");
            FileOutputStream output = new FileOutputStream(temporary);
            try { scaled.compress(Bitmap.CompressFormat.PNG, 100, output); output.getFD().sync(); }
            finally { output.close(); }
            if (!temporary.renameTo(target)) temporary.delete();
        } catch (Exception ignored) {
        } finally {
            if (scaled != icon) scaled.recycle();
        }
    }

    InputStream open(String host) {
        String key = key(host);
        if (key.length() == 0) return null;
        try {
            File file = new File(directory, key + ".png");
            return file.isFile() ? new FileInputStream(file) : null;
        } catch (Exception ignored) { return null; }
    }

    private static String key(String host) {
        String value = host == null ? "" : host.trim().toLowerCase(java.util.Locale.US);
        return value.matches("[a-z0-9._-]{1,253}") ? UrlCleaner.stableId(value) : "";
    }
}
