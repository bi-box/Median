package com.xinyv.median;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.AtomicFile;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;

/** Tiny persistent favicon cache populated from WebView and a bounded lazy HTTPS fallback. */
final class FaviconStore {
    private static final int MAX_ICON_BYTES = 64 * 1024;
    private static final int MAX_ICON_DIMENSION = 64;
    private final File directory;

    FaviconStore(Context context) {
        // Favicons are tiny and user-visible; cacheDir can be evicted between launches.
        directory = new File(context.getFilesDir(), "favicons");
    }

    boolean put(String host, Bitmap icon) {
        String key = key(host);
        if (key.length() == 0 || icon == null) return false;
        if (!directory.isDirectory() && !directory.mkdirs() && !directory.isDirectory()) return false;
        Bitmap source = icon;
        if (icon.getWidth() > MAX_ICON_DIMENSION || icon.getHeight() > MAX_ICON_DIMENSION) {
            float ratio = Math.min(MAX_ICON_DIMENSION / (float) icon.getWidth(),
                    MAX_ICON_DIMENSION / (float) icon.getHeight());
            int width = Math.max(1, Math.round(icon.getWidth() * ratio));
            int height = Math.max(1, Math.round(icon.getHeight() * ratio));
            try {
                source = Bitmap.createScaledBitmap(icon, width, height, true);
            } catch (RuntimeException ignored) { return false; }
        }
        FileOutputStream output = null;
        AtomicFile atomic = null;
        try {
            File target = new File(directory, key + ".png");
            atomic = new AtomicFile(target);
            output = atomic.startWrite();
            if (!source.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                atomic.failWrite(output);
                output = null;
                return false;
            }
            atomic.finishWrite(output);
            output = null;
            return true;
        } catch (Exception ignored) {
            if (atomic != null && output != null) atomic.failWrite(output);
        } finally {
            if (source != icon) source.recycle();
        }
        return false;
    }

    InputStream open(String host) {
        File file = fileFor(host);
        if (file == null) return null;
        try {
            return file.isFile() && file.length() > 0L && file.length() <= MAX_ICON_BYTES
                    ? new FileInputStream(file) : null;
        } catch (Exception ignored) { return null; }
    }

    boolean has(String host) {
        File file = fileFor(host);
        if (file == null) return false;
        try {
            return file.isFile() && file.length() > 0L && file.length() <= MAX_ICON_BYTES;
        } catch (RuntimeException ignored) { return false; }
    }

    /** Fetches one missing icon without following untrusted/local redirects or reading unbounded data. */
    boolean fetchAndStore(String host) {
        String normalized = normalizeHost(host);
        if (normalized.length() == 0) return false;
        if (has(normalized)) return true;
        HttpURLConnection connection = null;
        try {
            URL endpoint = new URL("https", normalized, -1, "/favicon.ico");
            connection = NetworkSecurity.openPublicHttpsGetFollowingRedirects(endpoint, 900, 1800,
                    null);
            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) return false;
            long declared = connection.getContentLengthLong();
            if (declared > MAX_ICON_BYTES) return false;
            byte[] bytes = NetworkSecurity.readBounded(connection.getInputStream(), MAX_ICON_BYTES,
                    "");
            Bitmap icon = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
            if (icon == null) return false;
            try {
                return put(normalized, icon);
            }
            finally { icon.recycle(); }
        } catch (Exception ignored) {
            return false;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private static String key(String host) {
        String value = normalizeHost(host);
        return value.length() == 0 ? "" : UrlCleaner.stableId(value);
    }

    private File fileFor(String host) {
        String value = key(host);
        return value.length() == 0 ? null : new File(directory, value + ".png");
    }

    private static String normalizeHost(String host) {
        String value = host == null ? "" : host.trim().toLowerCase(Locale.US);
        if (value.length() < 3 || value.length() > 253 || value.indexOf('.') <= 0 ||
                value.indexOf('/') >= 0) return "";
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (!(c >= 'a' && c <= 'z') && !(c >= '0' && c <= '9') && c != '.' && c != '-' && c != '_') return "";
        }
        return value;
    }
}
