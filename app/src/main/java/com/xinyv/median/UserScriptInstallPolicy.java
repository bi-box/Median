package com.xinyv.median;

import java.io.EOFException;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.Locale;

import javax.net.ssl.SSLException;

/** Small, Android-free rules for userscript input detection and transient fetch retries. */
final class UserScriptInstallPolicy {
    static final int MAX_FETCH_ATTEMPTS = 2;

    private UserScriptInstallPolicy() {}

    static boolean isSourceText(String value) {
        if (value == null) return false;
        String source = value.trim();
        int start = source.indexOf("==UserScript==");
        int end = source.indexOf("==/UserScript==");
        return start >= 0 && end > start;
    }

    static boolean looksLikeInstallUrl(String value) {
        if (value == null) return false;
        String lower = value.trim().toLowerCase(Locale.US);
        if (!lower.startsWith("https://")) return false;
        int fragment = lower.indexOf('#');
        if (fragment >= 0) lower = lower.substring(0, fragment);
        return lower.endsWith(".user.js") || lower.contains(".user.js?") ||
                lower.contains("update.greasyfork.org/scripts/") ||
                lower.contains("update.sleazyfork.org/scripts/") ||
                lower.contains("openuserjs.org/install/");
    }

    static boolean retryableHttpStatus(int status) {
        return status == 408 || status == 425 || status == 429 ||
                status == 500 || status == 502 || status == 503 || status == 504;
    }

    static boolean retryableFailure(Throwable failure) {
        Throwable current = failure;
        while (current != null) {
            if (current instanceof SSLException) return false;
            if (current instanceof SocketTimeoutException || current instanceof ConnectException ||
                    current instanceof UnknownHostException || current instanceof EOFException ||
                    current instanceof InterruptedIOException) return true;
            current = current.getCause();
        }
        return failure instanceof IOException;
    }

    static int connectTimeoutMs(int attempt) { return attempt <= 0 ? 10000 : 18000; }
    static int readTimeoutMs(int attempt) { return attempt <= 0 ? 22000 : 36000; }
    static long retryDelayMs(int attempt) { return attempt <= 0 ? 280L : 700L; }
}
