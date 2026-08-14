package com.xinyv.median;

public final class OmniboxInputSelfTest {
    public static void main(String[] args) {
        search("12");
        search("123456");
        search("天气");
        search("median browser");
        search("版本 1.2");
        search("12.34");
        search("999.1.1.1");
        search("user@example.com");
        search("ftp://example.com");

        address("https://example.com/a");
        address("HTTP://example.com");
        address("example.com");
        address("example.com:8443/path?q=1");
        address("www.baidu.com");
        address("localhost:3000");
        address("127.0.0.1:8080");
        address("[::1]:8080");
        address("例子.中国");
        address("//example.com/path");
        if (!"https://example.com/path".equals(OmniboxInput.withDefaultHttpsScheme(" //example.com/path "))) {
            throw new AssertionError("scheme-relative address normalization failed");
        }
        System.out.println("OmniboxInputSelfTest passed");
    }

    private static void address(String value) {
        if (!OmniboxInput.looksLikeWebAddress(value)) throw new AssertionError("address misclassified: " + value);
    }

    private static void search(String value) {
        if (OmniboxInput.looksLikeWebAddress(value)) throw new AssertionError("query misclassified: " + value);
    }
}
