package com.xinyv.median;

/** Immutable, validated presentation options for the fully local start page. */
final class HomePageConfig {
    static final String DEFAULT_TITLE = "Median";

    final String title;
    final String subtitle;
    final String logoStyle;
    final String logoCode;
    final String logoMode;
    final int logoLetterSpacing;
    final int logoGradientAngle;
    final int logoFontSize;
    final int logoFontWeight;
    final int logoImageWidth;
    final int logoImageHeight;
    final int logoImageRadius;
    final String accent;
    final int wallpaperDim;
    final int wallpaperBlur;
    final String wallpaperFit;
    final String searchStyle;
    final String layout;
    final String tileShape;
    final int shortcutColumns;
    final boolean showSearch;
    final boolean showEngines;
    final boolean showShortcuts;
    final boolean showCornerBrand;
    final boolean showClock;
    final String customCss;
    final boolean customHtmlEnabled;
    final boolean hasWallpaper;
    final boolean hasLogo;
    final long customHtmlVersion;
    final long wallpaperVersion;
    final long logoVersion;

    private HomePageConfig(String title, String subtitle, String logoStyle, String logoCode,
                           int logoLetterSpacing, int logoGradientAngle,
                           String accent, int wallpaperDim,
                           int wallpaperBlur, String wallpaperFit, String searchStyle,
                           String layout, String tileShape, int shortcutColumns,
                           boolean showSearch, boolean showEngines, boolean showShortcuts,
                           boolean showCornerBrand, boolean showClock, boolean customHtmlEnabled,
                           boolean hasWallpaper, boolean hasLogo, long customHtmlVersion,
                           long wallpaperVersion, long logoVersion, String logoMode,
                           int logoFontSize, int logoFontWeight, int logoImageWidth,
                           int logoImageHeight, int logoImageRadius, String customCss) {
        this.title = cleanTitle(title);
        this.subtitle = cleanSubtitle(subtitle);
        this.logoStyle = oneOf(logoStyle, "median", "google", "aurora", "sunset", "ocean", "rose_gold", "custom");
        String cleanCode = LogoMarkup.clean(logoCode);
        this.logoCode = LogoMarkup.parse(cleanCode).valid() ? cleanCode : "";
        this.logoMode = oneOf(logoMode, "text", "text", "image", "none");
        this.logoLetterSpacing = clamp(logoLetterSpacing, -3, 10);
        this.logoGradientAngle = clamp(logoGradientAngle, 0, 360);
        this.logoFontSize = clamp(logoFontSize, 24, 88);
        this.logoFontWeight = clamp(logoFontWeight, 300, 900);
        this.logoImageWidth = clamp(logoImageWidth, 40, 260);
        this.logoImageHeight = clamp(logoImageHeight, 32, 180);
        this.logoImageRadius = clamp(logoImageRadius, 0, 50);
        this.accent = oneOf(accent, "blue", "violet", "green", "orange", "rose", "teal");
        this.wallpaperDim = clamp(wallpaperDim, 0, 70);
        this.wallpaperBlur = clamp(wallpaperBlur, 0, 12);
        this.wallpaperFit = oneOf(wallpaperFit, "cover", "contain");
        this.searchStyle = oneOf(searchStyle, "solid", "glass");
        this.layout = oneOf(layout, "center", "compact");
        this.tileShape = oneOf(tileShape, "rounded", "circle", "square");
        this.shortcutColumns = clamp(shortcutColumns, 3, 5);
        this.showSearch = showSearch;
        this.showEngines = showEngines;
        this.showShortcuts = showShortcuts;
        this.showCornerBrand = showCornerBrand;
        this.showClock = showClock;
        this.customCss = CustomHomeCss.valid(customCss) ? CustomHomeCss.clean(customCss) : "";
        this.customHtmlEnabled = customHtmlEnabled;
        this.hasWallpaper = hasWallpaper;
        this.hasLogo = hasLogo;
        this.customHtmlVersion = Math.max(0L, customHtmlVersion);
        this.wallpaperVersion = Math.max(0L, wallpaperVersion);
        this.logoVersion = Math.max(0L, logoVersion);
    }

    static HomePageConfig create(String title, String subtitle, String logoStyle, String logoCode,
                                 int logoLetterSpacing, int logoGradientAngle,
                                 String accent, int wallpaperDim,
                                 int wallpaperBlur, String wallpaperFit, String searchStyle,
                                 String layout, String tileShape, int shortcutColumns,
                                 boolean showSearch, boolean showEngines, boolean showShortcuts,
                                 boolean showCornerBrand, boolean showClock, boolean customHtmlEnabled,
                                 boolean hasWallpaper, boolean hasLogo, long customHtmlVersion,
                                 long wallpaperVersion, long logoVersion) {
        return new HomePageConfig(title, subtitle, logoStyle, logoCode, logoLetterSpacing, logoGradientAngle,
                accent, wallpaperDim, wallpaperBlur,
                wallpaperFit, searchStyle, layout, tileShape, shortcutColumns, showSearch,
                showEngines, showShortcuts, showCornerBrand, showClock, customHtmlEnabled,
                hasWallpaper, hasLogo, customHtmlVersion, wallpaperVersion, logoVersion,
                hasLogo ? "image" : "text", 47, 720, 132, 96, 0, "");
    }

    static HomePageConfig createPersonalized(String title, String subtitle, String logoStyle, String logoCode,
                                 int logoLetterSpacing, int logoGradientAngle,
                                 String accent, int wallpaperDim,
                                 int wallpaperBlur, String wallpaperFit, String searchStyle,
                                 String layout, String tileShape, int shortcutColumns,
                                 boolean showSearch, boolean showEngines, boolean showShortcuts,
                                 boolean showCornerBrand, boolean showClock, boolean customHtmlEnabled,
                                 boolean hasWallpaper, boolean hasLogo, long customHtmlVersion,
                                 long wallpaperVersion, long logoVersion, String logoMode,
                                 int logoFontSize, int logoFontWeight, int logoImageWidth,
                                 int logoImageHeight, int logoImageRadius, String customCss) {
        return new HomePageConfig(title, subtitle, logoStyle, logoCode, logoLetterSpacing, logoGradientAngle,
                accent, wallpaperDim, wallpaperBlur, wallpaperFit, searchStyle, layout, tileShape,
                shortcutColumns, showSearch, showEngines, showShortcuts, showCornerBrand, showClock,
                customHtmlEnabled, hasWallpaper, hasLogo, customHtmlVersion, wallpaperVersion, logoVersion,
                logoMode, logoFontSize, logoFontWeight, logoImageWidth, logoImageHeight, logoImageRadius,
                customCss);
    }

    static HomePageConfig defaults() {
        return create(DEFAULT_TITLE, "", "median", "", 0, 90, "blue", 28, 0, "cover", "solid", "center",
                "rounded", 4, true, true, true, true, false, false, false, false, 0L, 0L, 0L);
    }

    String accentColor() {
        if ("violet".equals(accent)) return "#7c4dff";
        if ("green".equals(accent)) return "#168a55";
        if ("orange".equals(accent)) return "#e86f00";
        if ("rose".equals(accent)) return "#d83b67";
        if ("teal".equals(accent)) return "#008b95";
        return "#1a73e8";
    }

    static String cleanTitle(String value) {
        String clean = limit(value, 28);
        return clean.length() == 0 ? DEFAULT_TITLE : clean;
    }

    static String cleanSubtitle(String value) { return limit(value, 64); }

    private static String limit(String value, int maxCodePoints) {
        String clean = value == null ? "" : value.trim().replace('\n', ' ').replace('\r', ' ');
        int count = clean.codePointCount(0, clean.length());
        if (count <= maxCodePoints) return clean;
        return clean.substring(0, clean.offsetByCodePoints(0, maxCodePoints));
    }

    private static String oneOf(String value, String fallback, String... allowed) {
        if (value != null) for (String item : allowed) if (item.equals(value)) return item;
        return fallback;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
