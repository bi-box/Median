// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Median Browser contributors

package com.xinyv.median;

/** Chooses the single user-directed navigation that may run during cold-start completion. */
final class StartupNavigationPolicy {
    static String preferredInput(String pendingInput, String externalUrl) {
        if (hasText(pendingInput)) return pendingInput;
        return hasText(externalUrl) ? externalUrl : "";
    }

    private static boolean hasText(String value) {
        return value != null && value.trim().length() > 0;
    }

    private StartupNavigationPolicy() {}
}
