package com.xinyv.median;

import java.util.Locale;

/** Shared allocation-light byte/rate formatting for browser and download surfaces. */
final class ByteFormat {
    private ByteFormat() {}

    static String humanBytes(long bytes) {
        if (bytes <= 0L) return "0 B";
        if (bytes < 1024L) return bytes + " B";
        if (bytes < 1024L * 1024L) return String.format(Locale.US, "%.1f KB", bytes / 1024d);
        if (bytes < 1024L * 1024L * 1024L)
            return String.format(Locale.US, "%.1f MB", bytes / (1024d * 1024d));
        return String.format(Locale.US, "%.2f GB", bytes / (1024d * 1024d * 1024d));
    }

    static String humanSpeed(long bytesPerSecond) { return humanBytes(bytesPerSecond) + "/s"; }
}
