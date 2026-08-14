package com.xinyv.median;

public final class DownloadRetryPolicySelfTest {
    public static void main(String[] args) {
        assertFalse(DownloadRetryPolicy.isRetryableHttp(401), "authorization failures must fail fast");
        assertFalse(DownloadRetryPolicy.isRetryableHttp(403), "forbidden downloads must fail fast");
        assertFalse(DownloadRetryPolicy.isRetryableHttp(404), "missing files must fail fast");
        assertFalse(DownloadRetryPolicy.isRetryableHttp(416), "invalid ranges are handled separately");
        assertTrue(DownloadRetryPolicy.isRetryableHttp(408), "timeouts should retry");
        assertTrue(DownloadRetryPolicy.isRetryableHttp(429), "rate limits should retry");
        assertTrue(DownloadRetryPolicy.isRetryableHttp(503), "temporary server failures should retry");
        assertTrue(DownloadRetryPolicy.messageForHttp(403).contains("拒绝访问"), "403 should be readable");
        System.out.println("DownloadRetryPolicySelfTest passed");
    }

    private static void assertTrue(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    private static void assertFalse(boolean value, String message) { assertTrue(!value, message); }
}
