// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Median Browser contributors

package com.xinyv.median;

import java.net.URL;

/** Keeps meaningful path/query slashes while canonicalizing the optional slash on an origin root. */
final class UrlStoragePolicy {
    static String normalize(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (!value.endsWith("/")) return value;
        try {
            URL parsed = new URL(value);
            if ("/".equals(parsed.getPath()) && parsed.getQuery() == null && parsed.getRef() == null)
                return value.substring(0, value.length() - 1);
        } catch (Exception ignored) {}
        return value;
    }

    private UrlStoragePolicy() {}
}
