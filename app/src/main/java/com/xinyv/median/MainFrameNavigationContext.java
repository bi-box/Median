package com.xinyv.median;

import java.net.URI;
import java.util.Locale;

/** One outstanding main-frame request's initiating policy; safe across UI/request threads. */
final class MainFrameNavigationContext {
    static final class Snapshot {
        final String sourceHost;
        final boolean protectionEnabled;

        Snapshot(String sourceHost, boolean protectionEnabled) {
            this.sourceHost = sourceHost;
            this.protectionEnabled = protectionEnabled;
        }
    }

    private String targetIdentity = "";
    private Snapshot pending;

    synchronized void arm(String targetUrl, String sourceHost, boolean protectionEnabled) {
        clear();
        String identity = identity(targetUrl);
        if (identity.length() == 0) return;
        targetIdentity = identity;
        pending = new Snapshot(sourceHost == null ? "" : sourceHost, protectionEnabled);
    }

    synchronized Snapshot take(String requestUrl, boolean mainFrame) {
        // A stale request or a subresource must not consume a newer navigation's policy.
        if (!mainFrame || pending == null || !targetIdentity.equals(identity(requestUrl))) return null;
        Snapshot result = pending;
        clear();
        return result;
    }

    synchronized void onPageStarted(String url) {
        // Providers may dispatch page start either before or after shouldInterceptRequest.
        // Keep the matching ticket; a replacement document must retire an unmatched ticket.
        if (pending != null && !targetIdentity.equals(identity(url))) clear();
    }

    synchronized void clearForUrl(String url) {
        if (pending != null && targetIdentity.equals(identity(url))) clear();
    }

    synchronized void clear() {
        targetIdentity = "";
        pending = null;
    }

    private static String identity(String raw) {
        if (raw == null) return "";
        try {
            URI uri = new URI(raw.trim()).normalize();
            String scheme = uri.getScheme();
            if (scheme == null || !("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))) return "";
            String host = uri.getHost();
            if (host == null || host.length() == 0) return "";
            scheme = scheme.toLowerCase(Locale.US);
            host = host.toLowerCase(Locale.US);
            int port = uri.getPort();
            if (("http".equals(scheme) && port == 80) || ("https".equals(scheme) && port == 443)) port = -1;
            String path = uri.getRawPath();
            if (path == null || path.length() == 0) path = "/";
            String userInfo = uri.getRawUserInfo();
            String query = uri.getRawQuery();
            return scheme + "://" + (userInfo == null ? "" : userInfo + "@") + host +
                    (port < 0 ? "" : ":" + port) + path + (query == null ? "" : "?" + query);
        } catch (Exception ignored) {
            return "";
        }
    }
}
