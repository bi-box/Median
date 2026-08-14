package com.xinyv.median;

public final class HomeOpenPolicySelfTest {
    public static void main(String[] args) {
        require(HomeOpenPolicy.KEEP_LAST.equals(HomeOpenPolicy.normalize("", true)), "legacy restore migration");
        require(HomeOpenPolicy.OPEN_HOME.equals(HomeOpenPolicy.normalize("bad", false)), "legacy home migration");
        require(HomeOpenPolicy.restoresLast(HomeOpenPolicy.KEEP_LAST, false), "last mode");
        require(!HomeOpenPolicy.restoresLast(HomeOpenPolicy.OPEN_HOME, true), "home mode");
        require(HomeOpenPolicy.usesCustomUrl(HomeOpenPolicy.OPEN_CUSTOM_URL, true, "https://example.com/"), "custom URL");
        require(!HomeOpenPolicy.usesCustomUrl(HomeOpenPolicy.OPEN_CUSTOM_URL, true, ""), "missing URL");
        System.out.println("HomeOpenPolicySelfTest passed");
    }

    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
