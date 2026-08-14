package com.xinyv.median;

/** Progress-qualified acknowledgement and one recovery for the first network navigation. */
final class InitialNavigationGuard {
    private long generation;
    private String pendingUrl = "";
    private boolean retryUsed;
    private boolean acknowledged;

    long arm(String url) {
        if (acknowledged || !isNetworkUrl(url)) return 0L;
        if (url.equals(pendingUrl)) return 0L;
        generation++;
        pendingUrl = url;
        retryUsed = false;
        return generation;
    }

    boolean acknowledge(String url) {
        if (!acknowledged && isNetworkUrl(url)) {
            acknowledged = true;
            pendingUrl = "";
            return true;
        }
        return false;
    }

    String claimRetry(long expectedGeneration) {
        if (acknowledged || retryUsed || expectedGeneration != generation || pendingUrl.length() == 0) return "";
        retryUsed = true;
        return pendingUrl;
    }

    long pendingGeneration() {
        return acknowledged || retryUsed || pendingUrl.length() == 0 ? 0L : generation;
    }

    boolean isAcknowledged() { return acknowledged; }

    private static boolean isNetworkUrl(String url) {
        return url != null && (url.startsWith("https://") || url.startsWith("http://"));
    }
}
