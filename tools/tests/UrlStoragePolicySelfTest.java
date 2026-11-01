package com.xinyv.median;

public final class UrlStoragePolicySelfTest {
    public static void main(String[] args) {
        check("https://example.com", UrlStoragePolicy.normalize(" https://example.com/ "), "root slash");
        check("https://a", UrlStoragePolicy.normalize("https://a/"), "single-letter host");
        check("https://example.com/path/", UrlStoragePolicy.normalize("https://example.com/path/"), "path slash");
        check("https://example.com/?next=/", UrlStoragePolicy.normalize("https://example.com/?next=/"), "query slash");
        check("https://example.com/#part/", UrlStoragePolicy.normalize("https://example.com/#part/"), "fragment slash");
        System.out.println("URL storage policy self-test passed");
    }

    private static void check(String expected, String actual, String label) {
        if (!expected.equals(actual)) throw new AssertionError(label + ": " + actual);
    }
}
