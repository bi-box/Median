package com.xinyv.median;

/** Pure policy for cold-start and new-home behavior. */
final class HomeOpenPolicy {
    static final String OPEN_HOME = "home";
    static final String OPEN_CUSTOM_URL = "custom_url";
    static final String KEEP_LAST = "last";

    private HomeOpenPolicy() {}

    static String normalize(String value, boolean legacyRestoreTabs) {
        if (OPEN_HOME.equals(value) || OPEN_CUSTOM_URL.equals(value) || KEEP_LAST.equals(value)) return value;
        return legacyRestoreTabs ? KEEP_LAST : OPEN_HOME;
    }

    static boolean restoresLast(String value, boolean legacyRestoreTabs) {
        return KEEP_LAST.equals(normalize(value, legacyRestoreTabs));
    }

    static boolean usesCustomUrl(String value, boolean legacyRestoreTabs, String customUrl) {
        return OPEN_CUSTOM_URL.equals(normalize(value, legacyRestoreTabs)) &&
                customUrl != null && customUrl.trim().length() > 0;
    }
}
