package com.xinyv.median;

public final class HomePageConfigSelfTest {
    public static void main(String[] args) {
        HomePageConfig defaults = HomePageConfig.defaults();
        require("Median".equals(defaults.title), "default title");
        require("median".equals(defaults.logoStyle), "default logo style");
        require("#1a73e8".equals(defaults.accentColor()), "default accent");
        require(defaults.shortcutColumns == 4 && defaults.wallpaperDim == 28, "default layout");

        HomePageConfig value = HomePageConfig.createPersonalized("  我的\n主页  ", repeat("副", 80), "invalid", "bad-code[",
                99, -5, "invalid", 99, -3,
                "bad", "bad", "bad", "bad", 9, true, false, true, false, true,
                true, true, true, -3L, -1L, -2L, "none", 99, 999, 999, 1, 99,
                ".brand{opacity:.9}");
        require("我的 主页".equals(value.title), "single-line title");
        require(value.subtitle.codePointCount(0, value.subtitle.length()) == 64, "subtitle bound");
        require("blue".equals(value.accent) && value.wallpaperDim == 70 && value.wallpaperBlur == 0, "range validation");
        require("cover".equals(value.wallpaperFit) && "solid".equals(value.searchStyle), "id validation");
        require("median".equals(value.logoStyle) && value.logoCode.length() == 0, "logo validation");
        require(value.logoLetterSpacing == 10 && value.logoGradientAngle == 0, "logo layout validation");
        require(value.shortcutColumns == 5 && value.customHtmlEnabled && value.customHtmlVersion == 0L &&
                value.wallpaperVersion == 0L, "column/version validation");
        require("none".equals(value.logoMode) && value.logoFontSize == 88 && value.logoFontWeight == 900,
                "logo text validation");
        require(value.logoImageWidth == 260 && value.logoImageHeight == 32 && value.logoImageRadius == 50 &&
                value.customCss.length() > 0, "logo image/CSS validation");
        System.out.println("HomePageConfigSelfTest passed");
    }

    private static String repeat(String value, int count) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < count; i++) result.append(value);
        return result.toString();
    }

    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
