package com.xinyv.median;

import java.net.IDN;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.util.Locale;

/** Deterministic address-vs-search classification shared by both browser profiles. */
final class OmniboxInput {
    private OmniboxInput() {}

    static boolean isExplicitHttpUrl(String raw) {
        String value = raw == null ? "" : raw.trim();
        return value.regionMatches(true, 0, "http://", 0, 7) ||
                value.regionMatches(true, 0, "https://", 0, 8);
    }

    static String withDefaultHttpsScheme(String raw) {
        String value = raw == null ? "" : raw.trim();
        while (value.startsWith("//")) value = value.substring(2);
        return "https://" + value;
    }

    static boolean looksLikeWebAddress(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (value.length() == 0 || containsWhitespace(value)) return false;
        if (isExplicitHttpUrl(value)) return true;
        if (value.contains("://")) return false;
        if (value.startsWith("//")) value = value.substring(2);

        int end = firstIndexOf(value, '/', '?', '#');
        String authority = end < 0 ? value : value.substring(0, end);
        if (authority.length() == 0 || authority.indexOf('@') >= 0) return false;

        if (authority.charAt(0) == '[') {
            int close = authority.indexOf(']');
            if (close <= 1 || !validPortSuffix(authority.substring(close + 1))) return false;
            return validIpv6(authority.substring(1, close));
        }

        String host = authority;
        int colon = authority.lastIndexOf(':');
        if (colon >= 0) {
            if (authority.indexOf(':') != colon || !validPortSuffix(authority.substring(colon))) return false;
            host = authority.substring(0, colon);
        }
        while (host.endsWith(".")) host = host.substring(0, host.length() - 1);
        if (host.length() == 0) return false;
        String lower = host.toLowerCase(Locale.US);
        if ("localhost".equals(lower) || lower.endsWith(".localhost")) return true;
        if (digitsAndDots(host)) return validIpv4(host);
        if (host.indexOf('.') < 0) return false;
        return validDomain(host);
    }

    private static boolean validDomain(String host) {
        final String ascii;
        try { ascii = IDN.toASCII(host, IDN.USE_STD3_ASCII_RULES).toLowerCase(Locale.US); }
        catch (IllegalArgumentException invalid) { return false; }
        if (ascii.length() == 0 || ascii.length() > 253) return false;
        String[] labels = ascii.split("\\.", -1);
        if (labels.length < 2) return false;
        for (String label : labels) {
            if (label.length() == 0 || label.length() > 63 || label.charAt(0) == '-' ||
                    label.charAt(label.length() - 1) == '-') return false;
            for (int i = 0; i < label.length(); i++) {
                char c = label.charAt(i);
                if (!((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '-')) return false;
            }
        }
        String top = labels[labels.length - 1];
        if (top.length() < 2) return false;
        for (int i = 0; i < top.length(); i++) if (!Character.isDigit(top.charAt(i))) return true;
        return false;
    }

    private static boolean validIpv4(String host) {
        String[] parts = host.split("\\.", -1);
        if (parts.length != 4) return false;
        for (String part : parts) {
            if (part.length() == 0 || part.length() > 3) return false;
            int value = 0;
            for (int i = 0; i < part.length(); i++) {
                char c = part.charAt(i);
                if (c < '0' || c > '9') return false;
                value = value * 10 + c - '0';
            }
            if (value > 255) return false;
        }
        return true;
    }

    private static boolean validIpv6(String host) {
        if (host.indexOf(':') < 0) return false;
        try { return InetAddress.getByName(host) instanceof Inet6Address; }
        catch (Exception invalid) { return false; }
    }

    private static boolean validPortSuffix(String suffix) {
        if (suffix.length() == 0) return true;
        if (suffix.charAt(0) != ':' || suffix.length() == 1 || suffix.length() > 6) return false;
        int port = 0;
        for (int i = 1; i < suffix.length(); i++) {
            char c = suffix.charAt(i);
            if (c < '0' || c > '9') return false;
            port = port * 10 + c - '0';
        }
        return port >= 1 && port <= 65535;
    }

    private static boolean digitsAndDots(String value) {
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if ((c < '0' || c > '9') && c != '.') return false;
        }
        return true;
    }

    private static boolean containsWhitespace(String value) {
        for (int i = 0; i < value.length(); i++) if (Character.isWhitespace(value.charAt(i))) return true;
        return false;
    }

    private static int firstIndexOf(String value, char a, char b, char c) {
        int result = -1;
        for (int i = 0; i < value.length(); i++) {
            char current = value.charAt(i);
            if (current == a || current == b || current == c) { result = i; break; }
        }
        return result;
    }
}
