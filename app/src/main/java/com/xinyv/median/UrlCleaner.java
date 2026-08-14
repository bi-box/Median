package com.xinyv.median;

import android.net.Uri;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** URL security helpers and conservative removal of high-confidence tracking parameters. */
final class UrlCleaner {
    private static final Set<String> TRACKING = new HashSet<String>();
    static {
        String[] names = new String[] {
                "fbclid", "gclid", "dclid", "msclkid", "twclid", "ttclid", "igshid",
                "gbraid", "wbraid", "gad_source", "mc_cid", "mc_eid", "mkt_tok",
                "_hsenc", "_hsmi", "vero_conv", "vero_id", "oly_anon_id", "oly_enc_id",
                "rb_clickid", "wickedid", "s_cid", "si"
        };
        for (String name : names) TRACKING.add(name);
    }

    private UrlCleaner() {}

    static boolean isHttpsOrigin(String raw, String expectedHost) {
        try {
            Uri uri = Uri.parse(raw);
            int port = uri.getPort();
            return "https".equalsIgnoreCase(uri.getScheme()) && expectedHost.equalsIgnoreCase(uri.getHost()) &&
                    (port == -1 || port == 443) && uri.getUserInfo() == null;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    static boolean isInternalPage(String raw, String expectedHost) {
        if (!isHttpsOrigin(raw, expectedHost)) return false;
        try {
            Uri uri = Uri.parse(raw);
            String path = value(uri.getPath());
            return (path.length() == 0 || "/".equals(path)) && uri.getQuery() == null;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    static boolean isSecureWebUrl(String raw) {
        try {
            Uri uri = Uri.parse(raw);
            return "https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null &&
                    uri.getHost().length() > 0 && uri.getUserInfo() == null;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    static boolean sameOrigin(String left, String right) {
        try {
            Uri a = Uri.parse(left);
            Uri b = Uri.parse(right);
            return value(a.getScheme()).equalsIgnoreCase(value(b.getScheme())) &&
                    value(a.getHost()).equalsIgnoreCase(value(b.getHost())) && effectivePort(a) == effectivePort(b);
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    static String cleanTracking(String raw) {
        if (raw == null || raw.length() == 0) return raw;
        try {
            Uri uri = Uri.parse(raw);
            String scheme = value(uri.getScheme()).toLowerCase(Locale.US);
            if (!"http".equals(scheme) && !"https".equals(scheme)) return raw;
            String query = uri.getEncodedQuery();
            if (query == null || query.length() == 0 || hasSignedQuery(query)) return raw;
            String[] pairs = query.split("&", -1);
            List<String> kept = new ArrayList<String>(pairs.length);
            boolean changed = false;
            for (String pair : pairs) {
                int equals = pair.indexOf('=');
                String encodedName = equals < 0 ? pair : pair.substring(0, equals);
                String name = Uri.decode(encodedName).trim().toLowerCase(Locale.US);
                if (name.startsWith("utm_") || TRACKING.contains(name)) changed = true;
                else kept.add(pair);
            }
            if (!changed) return raw;
            StringBuilder cleaned = new StringBuilder();
            for (String pair : kept) {
                if (cleaned.length() > 0) cleaned.append('&');
                cleaned.append(pair);
            }
            return uri.buildUpon().encodedQuery(cleaned.length() == 0 ? null : cleaned.toString()).build().toString();
        } catch (RuntimeException ignored) {
            return raw;
        }
    }

    static String stableId(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value(value).toLowerCase(Locale.US)
                    .getBytes(StandardCharsets.UTF_8));
            return HexCodec.encode(digest, 12, false, false);
        } catch (Exception ignored) {
            return Integer.toHexString(value(value).hashCode());
        }
    }

    static String randomToken() {
        byte[] bytes = new byte[16];
        new SecureRandom().nextBytes(bytes);
        return HexCodec.encode(bytes, bytes.length, false, false);
    }

    private static boolean hasSignedQuery(String query) {
        String lower = Uri.decode(query).toLowerCase(Locale.US);
        return lower.contains("x-amz-") || lower.contains("signature=") || lower.contains("x-goog-signature=") ||
                lower.contains("sig=") || lower.contains("hmac=") || lower.contains("access_token=") ||
                lower.contains("auth_token=") || lower.contains("token=") || lower.contains("expires=") ||
                lower.contains("key-pair-id=") || lower.contains("policy=");
    }

    private static int effectivePort(Uri uri) {
        if (uri.getPort() >= 0) return uri.getPort();
        return "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
    }

    private static String value(String value) { return value == null ? "" : value; }
}
