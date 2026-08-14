package com.xinyv.median;

public final class DownloadCenterPolicySelfTest {
    public static void main(String[] args) {
        assertTrue(DownloadCenterPolicy.isActive(DownloadStore.STATUS_PENDING), "pending must be active");
        assertTrue(DownloadCenterPolicy.isActive(DownloadStore.STATUS_DOWNLOADING), "downloading must be active");
        assertFalse(DownloadCenterPolicy.isActive(DownloadStore.STATUS_PAUSED), "paused must not be active");
        assertTrue(DownloadCenterPolicy.isProblem(DownloadStore.STATUS_FAILED), "failed must be a problem");
        assertFalse(DownloadCenterPolicy.isProblem(DownloadStore.STATUS_COMPLETED), "completed must not be a problem");
        assertTrue(DownloadCenterPolicy.canResume(DownloadStore.STATUS_FAILED),
                "ordinary failures should offer retry");
        assertTrue(DownloadCenterPolicy.canResume(DownloadStore.STATUS_PAUSED),
                "paused tasks should offer resume");
        assertTrue(DownloadCenterPolicy.canOpen(DownloadStore.STATUS_COMPLETED), "completed must open");
        assertEquals(0L, DownloadCenterPolicy.resolvedTotal(0L, 0L), "unknown total");
        assertEquals(4096L, DownloadCenterPolicy.resolvedTotal(4096L, 0L), "known total must survive empty telemetry");
        assertEquals(8192L, DownloadCenterPolicy.resolvedTotal(4096L, 8192L), "response total must replace hint");
        assertEquals(0, DownloadCenterPolicy.progressPermille(512L, 0L), "unknown progress");
        assertEquals(500, DownloadCenterPolicy.progressPermille(512L, 1024L), "half progress");
        assertEquals(1000, DownloadCenterPolicy.progressPermille(2048L, 1024L), "overrun progress");
        assertEquals(999, DownloadCenterPolicy.progressPermille(Long.MAX_VALUE - 1L, Long.MAX_VALUE), "large progress");
        DownloadStore.Item item = new DownloadStore.Item();
        item.filename = "one.bin";
        item.downloadedBytes = 7L;
        DownloadStore.Item copy = item.copy();
        copy.filename = "two.bin";
        assertTrue("one.bin".equals(item.filename) && copy.downloadedBytes == 7L, "download snapshot clone");
        System.out.println("DownloadCenterPolicySelfTest passed");
    }

    private static void assertTrue(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    private static void assertFalse(boolean value, String message) { assertTrue(!value, message); }

    private static void assertEquals(long expected, long actual, String message) {
        if (expected != actual) throw new AssertionError(message + ": expected " + expected + ", got " + actual);
    }
}
