package com.xinyv.median;

import android.content.Context;

import java.io.File;
import java.util.Locale;

final class StoragePolicy {
    static final class Snapshot {
        final long totalBytes;
        final long transientBytes;
        final long siteBytes;

        Snapshot(long totalBytes, long transientBytes) {
            this.totalBytes = Math.max(0L, totalBytes);
            this.transientBytes = Math.max(0L, Math.min(transientBytes, totalBytes));
            this.siteBytes = Math.max(0L, totalBytes - this.transientBytes);
        }
    }

    static Snapshot snapshot(Context context) {
        File data = new File(context.getApplicationInfo().dataDir);
        long total = sizeOf(data);
        long transientSize = 0L;
        transientSize += sizeOf(context.getCacheDir());
        transientSize += sizeOf(context.getCodeCacheDir());
        transientSize += sizeOfNamedCaches(new File(data, "app_webview"));
        return new Snapshot(total, transientSize);
    }

    static long budgetBytes(Context context, String mode) {
        long mb = "performance".equals(mode) ? 192L : ("power_save".equals(mode) ? 64L : 96L);
        long free = context.getFilesDir().getUsableSpace();
        if (free > 0L && free < 1024L * 1024L * 1024L) mb /= 2L;
        else if (free > 0L && free < 4L * 1024L * 1024L * 1024L) mb = Math.max(16L, mb * 3L / 4L);
        return mb * 1024L * 1024L;
    }

    static String format(long bytes) {
        if (bytes < 1024L) return bytes + " B";
        double kb = bytes / 1024d;
        if (kb < 1024d) return String.format(Locale.US, "%.1f KB", kb);
        double mb = kb / 1024d;
        if (mb < 1024d) return String.format(Locale.US, "%.1f MB", mb);
        return String.format(Locale.US, "%.2f GB", mb / 1024d);
    }

    private static long sizeOfNamedCaches(File file) {
        if (file == null || !file.exists() || file.isFile()) return 0L;
        String name = file.getName().toLowerCase(Locale.US);
        if (name.equals("cache") || name.equals("code cache") || name.equals("gpucache") ||
                name.equals("grshadercache") || name.equals("dawncache") || name.equals("shadercache")) {
            return sizeOf(file);
        }
        long total = 0L;
        File[] children = file.listFiles();
        if (children == null) return 0L;
        for (File child : children) total += sizeOfNamedCaches(child);
        return total;
    }

    private static long sizeOf(File file) {
        if (file == null || !file.exists()) return 0L;
        if (file.isFile()) return file.length();
        long total = 0L;
        File[] children = file.listFiles();
        if (children == null) return 0L;
        for (File child : children) total += sizeOf(child);
        return total;
    }

    private StoragePolicy() {}
}
