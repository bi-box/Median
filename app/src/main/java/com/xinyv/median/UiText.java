package com.xinyv.median;

/** Small Unicode-safe helpers for labels that are persisted or rendered outside TextView. */
final class UiText {
    static String ellipsize(String raw, int maxCodePoints) {
        String value = raw == null ? "" : raw;
        int limit = Math.max(0, maxCodePoints);
        int count = value.codePointCount(0, value.length());
        if (count <= limit) return value;
        if (limit == 0) return "…";
        return truncate(value, limit) + "…";
    }

    static String truncate(String raw, int maxCodePoints) {
        String value = raw == null ? "" : raw;
        int limit = Math.max(0, maxCodePoints);
        if (value.codePointCount(0, value.length()) <= limit) return value;
        return value.substring(0, value.offsetByCodePoints(0, limit));
    }

    static String firstCodePoint(String raw, String fallback) {
        String value = raw == null ? "" : raw;
        if (value.length() == 0) return fallback == null ? "" : fallback;
        int first = value.codePointAt(0);
        int end = value.offsetByCodePoints(0, 1);
        if (isRegionalIndicator(first) && end < value.length() && isRegionalIndicator(value.codePointAt(end)))
            end += Character.charCount(value.codePointAt(end));
        while (end < value.length()) {
            int codePoint = value.codePointAt(end);
            int type = Character.getType(codePoint);
            boolean modifier = type == Character.NON_SPACING_MARK || type == Character.COMBINING_SPACING_MARK ||
                    type == Character.ENCLOSING_MARK ||
                    codePoint == 0xfe0e || codePoint == 0xfe0f ||
                    (codePoint >= 0x1f3fb && codePoint <= 0x1f3ff) ||
                    (codePoint >= 0xe0020 && codePoint <= 0xe007f);
            if (modifier) {
                end += Character.charCount(codePoint);
                continue;
            }
            if (codePoint == 0x200d) {
                end += Character.charCount(codePoint);
                if (end < value.length()) end += Character.charCount(value.codePointAt(end));
                continue;
            }
            break;
        }
        return value.substring(0, end);
    }

    private static boolean isRegionalIndicator(int codePoint) {
        return codePoint >= 0x1f1e6 && codePoint <= 0x1f1ff;
    }

    private UiText() {}
}
