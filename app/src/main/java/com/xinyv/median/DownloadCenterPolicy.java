package com.xinyv.median;

/** Pure task-state rules shared by the download center UI and its self-test. */
final class DownloadCenterPolicy {
    private DownloadCenterPolicy() {}

    static boolean isActive(String status) {
        return DownloadStore.STATUS_PENDING.equals(status) || DownloadStore.STATUS_WAITING.equals(status) ||
                DownloadStore.STATUS_DOWNLOADING.equals(status);
    }

    static boolean isProblem(String status) {
        return DownloadStore.STATUS_FAILED.equals(status) || DownloadStore.STATUS_CANCELLED.equals(status);
    }

    static boolean canResume(String status) {
        return DownloadStore.STATUS_PAUSED.equals(status) ||
                DownloadStore.STATUS_FAILED.equals(status);
    }

    static boolean canOpen(String status) { return DownloadStore.STATUS_COMPLETED.equals(status); }

    /** Keeps a previously discovered size when a later telemetry tick has no size information. */
    static long resolvedTotal(long knownTotal, long reportedTotal) {
        return reportedTotal > 0L ? reportedTotal : Math.max(0L, knownTotal);
    }

    /** Overflow-safe progress in tenths of one percent, clamped to 0..1000. */
    static int progressPermille(long downloaded, long total) {
        if (total <= 0L || downloaded <= 0L) return 0;
        if (downloaded >= total) return 1000;
        return (int) Math.min(999L, (long) Math.floor((downloaded / (double) total) * 1000d));
    }
}
