package com.xinyv.median;

public final class CustomHomeHtmlSelfTest {
    public static void main(String[] args) {
        require(CustomHomeHtml.valid("<h1>Hello</h1>"), "valid fragment");
        require(CustomHomeHtml.document("<h1>Hello</h1>").contains("<base target=\"_top\">"), "base target");
        String page = CustomHomeHtml.document("<html><head><title>X</title></head><body>Y</body></html>");
        require(page.indexOf("<base target=\"_top\">") < page.indexOf("<title>"), "head injection order");
        require(page.contains("default-src 'none'") && page.contains("background:transparent!important"),
                "local isolation or wallpaper transparency missing");
        require(page.contains("script-src 'unsafe-inline'") && page.contains("connect-src 'none'"),
                "sandbox script policy missing");
        require(!CustomHomeHtml.valid(repeat('x', CustomHomeHtml.MAX_LENGTH + 1)), "oversize accepted");
        require(!CustomHomeHtml.valid(repeat('中', CustomHomeHtml.MAX_LENGTH / 3 + 1)), "UTF-8 byte limit ignored");
        System.out.println("CustomHomeHtmlSelfTest passed");
    }

    private static String repeat(char value, int count) {
        StringBuilder result = new StringBuilder(count);
        for (int i = 0; i < count; i++) result.append(value);
        return result.toString();
    }

    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
