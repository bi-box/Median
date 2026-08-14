package com.xinyv.median;

public final class LogoMarkupSelfTest {
    public static void main(String[] args) {
        LogoMarkup.Result google = LogoMarkup.parse(LogoMarkup.GOOGLE_CODE);
        require(google.valid() && "Google".equals(google.plainText), "Google preset");
        require(google.html.contains("#4285F4") && google.html.contains("#34A853"), "Google colors");

        LogoMarkup.Result gradient = LogoMarkup.parse(LogoMarkup.GRADIENT_EXAMPLE);
        require(gradient.valid() && gradient.html.contains("linear-gradient"), "gradient preset");
        LogoMarkup.Result dynamicExample = LogoMarkup.parse(LogoMarkup.gradientExample("Median"));
        require(dynamicExample.valid() && "Median".equals(dynamicExample.plainText), "dynamic gradient example");
        require(!LogoMarkup.GRADIENT_EXAMPLE.contains("我的主页"), "legacy title leaked into current example");
        LogoMarkup.Result spaced = LogoMarkup.parse("A[space=6]B");
        require(spaced.valid() && spaced.html.contains("width:6px") && "A B".equals(spaced.plainText), "custom spacing");
        require(!LogoMarkup.parse("A[space=25]B").valid(), "oversized spacing accepted");
        require(!LogoMarkup.parse("[color=red]X[/color]").valid(), "named color accepted");
        require(!LogoMarkup.parse("[gradient=#112233]X[/gradient]").valid(), "one-color gradient accepted");
        require(!LogoMarkup.parse("<script>alert(1)</script>[bad]X[/bad]").valid(), "raw markup accepted");

        LogoMarkup.Result escaped = LogoMarkup.parse("A < B");
        require(escaped.valid() && escaped.html.contains("&lt;"), "plain text escaping");
        require(LogoMarkup.renderPreset("google", "ignored", "").contains("#EA4335"), "Google render");
        require(LogoMarkup.renderPreset("aurora", "Hello", "", 135).contains("135deg"), "gradient angle render");
        require(LogoMarkup.renderPreset("aurora", "Hello", "").contains("#8B5CF6"), "gradient palette render");
        require(LogoMarkup.renderPreset("rose_gold", "Hello", "").contains("#B76E79"), "rose gold render");
        require(LogoMarkup.renderPreset("median", "Median", "").contains("#D6A23A"), "Median identity accent");
        require("Hello".equals(LogoMarkup.renderPreset("median", "Hello", "")), "custom plain title render");
        System.out.println("LogoMarkupSelfTest passed");
    }

    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
