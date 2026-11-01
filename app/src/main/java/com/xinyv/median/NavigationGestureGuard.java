package com.xinyv.median;

import java.net.URI;
import java.util.Locale;

/**
 * Small, view-local guard for touch-driven cross-site navigations.
 *
 * <p>Native touch state distinguishes a tap from navigation emitted while a finger is held or
 * dragged. A suspicious navigation retains its source across accepted hops and blocked
 * retries until an accepted document commits, a new touch, an explicit reset, or the bounded
 * chain lifetime. A redirect flag cannot erase that provenance. Callers still evaluate network
 * rules and explicit exceptions for every hop.</p>
 */
final class NavigationGestureGuard {
    static final long RECENT_DRAG_WINDOW_MS = 700L;
    static final long NAVIGATION_CHAIN_WINDOW_MS = 10_000L;
    // Match the bounded common-suffix policy used by network third-party rules, including
    // services whose independently operated tenants must not receive a same-site exemption.
    private static final String TWO_LEVEL_SUFFIXES =
            "|ac.uk|co.uk|gov.uk|org.uk|me.uk|com.cn|net.cn|org.cn|gov.cn|com.tw|net.tw|org.tw|" +
            "com.au|net.au|org.au|edu.au|co.jp|ne.jp|or.jp|co.kr|or.kr|co.nz|org.nz|co.in|firm.in|" +
            "com.br|com.mx|com.sg|com.hk|com.my|com.ph|com.tr|com.ar|com.pl|com.ua|co.za|co.il|" +
            "github.io|appspot.com|blogspot.com|wordpress.com|pages.dev|workers.dev|vercel.app|netlify.app|" +
            "herokuapp.com|firebaseapp.com|web.app|azurewebsites.net|cloudfront.net|notion.site|duckdns.org|";

    private final float touchSlop;
    private boolean active;
    private boolean dragging;
    private float downX;
    private float downY;
    private String activeDocument = "";
    private long lastTouchAt;
    private String recentDocument = "";
    private long recentDragAt;
    private String chainDocument = "";
    private String chainTarget = "";
    private long chainStartedAt;

    NavigationGestureGuard(float touchSlop) {
        this.touchSlop = Math.max(0f, touchSlop);
    }

    synchronized void onDown(String documentUrl, long now, float x, float y, int pointerCount) {
        // Every new gesture retires a prior drag. This prevents an unrelated later tap from
        // inheriting the short-lived protection window.
        reset();
        activeDocument = documentIdentity(documentUrl);
        if (activeDocument.length() == 0 || pointerCount != 1 || now < 0L) {
            activeDocument = "";
            return;
        }
        active = true;
        lastTouchAt = now;
        downX = x;
        downY = y;
    }

    synchronized void onMove(String documentUrl, long now, float x, float y, int pointerCount) {
        if (pointerCount != 1) {
            reset();
            return;
        }
        if (!active) return;
        if (now < lastTouchAt || !activeDocument.equals(documentIdentity(documentUrl))) {
            resetActive();
            return;
        }
        lastTouchAt = now;
        float dx = x - downX;
        float dy = y - downY;
        if (dx * dx + dy * dy > touchSlop * touchSlop) dragging = true;
    }

    synchronized void onUp(String documentUrl, long now, int pointerCount) {
        if (pointerCount != 1) {
            reset();
            return;
        }
        if (!active) return;
        String document = documentIdentity(documentUrl);
        if (pointerCount == 1 && dragging && activeDocument.equals(document) && now >= lastTouchAt) {
            recentDocument = activeDocument;
            recentDragAt = now;
        }
        resetActive();
    }

    /** Legacy callers without an event timestamp can only explicitly discard their state. */
    synchronized void onCancel() { reset(); }

    synchronized void onCancel(String documentUrl, long now) {
        if (active && dragging && now >= lastTouchAt &&
                activeDocument.equals(documentIdentity(documentUrl))) {
            recentDocument = activeDocument;
            recentDragAt = now;
        }
        // A viewport/layout cancellation does not turn a completed or active drag into a tap.
        resetActive();
    }

    synchronized void reset() {
        resetTouch();
        clearChain();
    }

    synchronized boolean hasRecentDrag(String documentUrl, long now) {
        String document = documentIdentity(documentUrl);
        // A held finger remains the same gesture even when MOVE events pause. Only a
        // completed/cancelled drag uses the short post-touch expiry window.
        if (active && dragging && document.equals(activeDocument) && now >= lastTouchAt) return true;
        if (document.length() == 0 || !document.equals(recentDocument) || now < recentDragAt) return false;
        return now - recentDragAt <= RECENT_DRAG_WINDOW_MS;
    }

    synchronized boolean shouldBlockNavigation(String documentUrl, String targetUrl, boolean hasGesture,
                                  boolean serverRedirect, long now) {
        boolean chained = hasNavigationChain(now);
        String source = chained ? chainDocument : documentIdentity(documentUrl);
        boolean protectedTouch = hasRecentDrag(documentUrl, now) ||
                (!hasGesture && hasActivePress(documentUrl, now));
        // hasGesture is advisory after a drag; redirects must retain its initiating source.
        return (chained || protectedTouch) && isCrossSite(source, targetUrl);
    }

    synchronized void onNavigationAccepted(String documentUrl, String targetUrl, boolean hasGesture,
                                           boolean serverRedirect, long now) {
        String target = navigationIdentity(targetUrl);
        if (target.length() == 0) return;
        // A request thread can sample its timestamp before the UI creates a newer chain.
        // An older accepted callback must not replace that chain using an older drag.
        if (chainDocument.length() > 0 && now < chainStartedAt) return;
        if (hasNavigationChain(now)) {
            // An explicit allow applies to this target only. Never grant the rest of a chain.
            chainTarget = target;
            return;
        }
        String document = documentIdentity(documentUrl);
        if (document.length() == 0 || !(hasRecentDrag(document, now) ||
                (!hasGesture && hasActivePress(document, now)))) return;
        chainDocument = document;
        chainTarget = target;
        chainStartedAt = now;
    }

    synchronized void onNavigationBlocked(String documentUrl, String targetUrl, long now) {
        // Called only after the touch heuristic matched and a scoped allow did not apply.
        // Rejected attempts retain their source without pretending that their target was
        // accepted. Repeated attempts must not extend the original protection deadline.
        if (chainDocument.length() > 0 && now < chainStartedAt) return;
        if (hasNavigationChain(now)) return;
        String document = documentIdentity(documentUrl);
        if (document.length() == 0 || navigationIdentity(targetUrl).length() == 0 ||
                !isCrossSite(document, targetUrl) ||
                !(hasRecentDrag(document, now) || hasActivePress(document, now))) return;
        chainDocument = document;
        chainTarget = "";
        chainStartedAt = now;
    }

    synchronized void onPageStarted(String url) {
        // A provider may start an unaccepted POST before its request callback. Replacing
        // touch state cannot retire the initiating chain before that request is checked.
        resetTouch();
    }

    synchronized void onNavigationCommitted(String url) {
        if (chainDocument.length() > 0 && chainTarget.length() > 0 &&
                chainTarget.equals(navigationIdentity(url))) clearChain();
    }

    synchronized String navigationSourceDocument(String fallback, long now) {
        return hasNavigationChain(now) ? chainDocument : documentIdentity(fallback);
    }

    synchronized boolean migrateDocument(String oldUrl, String newUrl) {
        String oldDocument = documentIdentity(oldUrl);
        String newDocument = documentIdentity(newUrl);
        String oldOrigin = httpOrigin(oldDocument);
        if (oldOrigin.length() == 0 || !oldOrigin.equals(httpOrigin(newDocument))) {
            // A matching page-start chain may already represent the replacement document.
            // Cross-origin history changes retire touch state, not that navigation chain.
            resetTouch();
            return false;
        }
        if (activeDocument.equals(oldDocument)) activeDocument = newDocument;
        if (recentDocument.equals(oldDocument)) recentDocument = newDocument;
        return true;
    }

    private synchronized boolean hasActivePress(String documentUrl, long now) {
        return active && now >= lastTouchAt && activeDocument.equals(documentIdentity(documentUrl));
    }

    private synchronized boolean hasNavigationChain(long now) {
        if (chainDocument.length() == 0) return false;
        // A pre-sampled request timestamp may be older than a UI callback's chain. It
        // cannot use that newer state, and must not retire it for subsequent requests.
        if (now < chainStartedAt) return false;
        if (now - chainStartedAt > NAVIGATION_CHAIN_WINDOW_MS) {
            clearChain();
            return false;
        }
        return true;
    }

    private synchronized void clearChain() {
        chainDocument = "";
        chainTarget = "";
        chainStartedAt = 0L;
    }

    private synchronized void resetTouch() {
        resetActive();
        lastTouchAt = 0L;
        recentDocument = "";
        recentDragAt = 0L;
    }

    private synchronized void resetActive() {
        active = false;
        dragging = false;
        activeDocument = "";
    }

    private static String httpOrigin(String raw) {
        if (raw.length() == 0) return "";
        try {
            URI uri = new URI(raw);
            String scheme = uri.getScheme();
            String host = uri.getHost();
            if (scheme == null || host == null || host.length() == 0 ||
                    !("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))) return "";
            scheme = scheme.toLowerCase(Locale.US);
            int port = uri.getPort();
            if (port < 0) port = "https".equals(scheme) ? 443 : 80;
            return scheme + "://" + host.toLowerCase(Locale.US) + ":" + port;
        } catch (Exception ignored) {
            return "";
        }
    }

    private static String navigationIdentity(String raw) {
        String document = documentIdentity(raw);
        if (document.length() == 0) return "";
        try {
            URI uri = new URI(document).normalize();
            String origin = httpOrigin(document);
            if (origin.length() == 0) return "";
            String path = uri.getRawPath();
            String query = uri.getRawQuery();
            String userInfo = uri.getRawUserInfo();
            return origin + (userInfo == null ? "" : "@" + userInfo) +
                    (path == null || path.length() == 0 ? "/" : path) +
                    (query == null ? "" : "?" + query);
        } catch (Exception ignored) {
            return "";
        }
    }

    private static boolean isCrossSite(String documentUrl, String targetUrl) {
        String source = siteKey(documentUrl);
        String target = siteKey(targetUrl);
        return source.length() > 0 && target.length() > 0 && !source.equals(target);
    }

    private static String documentIdentity(String raw) {
        if (raw == null || !(raw.regionMatches(true, 0, "https://", 0, 8) ||
                raw.regionMatches(true, 0, "http://", 0, 7))) return "";
        // Touch MOVE is a frequent path. Keep binding an exact URL comparison and parse sites
        // only when a navigation actually needs a decision. Query strings remain significant.
        int fragment = raw.indexOf('#');
        return fragment < 0 ? raw : raw.substring(0, fragment);
    }

    private static String siteKey(String raw) {
        String host = httpHost(raw);
        if (host.length() == 0) return "";
        if (isIpLiteral(host) || host.indexOf(':') >= 0) return host;
        int lastDot = host.lastIndexOf('.');
        if (lastDot <= 0 || lastDot == host.length() - 1) return host;
        int secondDot = host.lastIndexOf('.', lastDot - 1);
        if (secondDot < 0) return host;
        String suffix = host.substring(secondDot + 1);
        if (TWO_LEVEL_SUFFIXES.contains("|" + suffix + "|")) {
            int thirdDot = host.lastIndexOf('.', secondDot - 1);
            return host.substring(thirdDot + 1);
        }
        return suffix;
    }

    private static String httpHost(String raw) {
        if (raw == null) return "";
        try {
            URI uri = new URI(raw.trim());
            String scheme = uri.getScheme();
            if (scheme == null || !("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))) return "";
            String host = uri.getHost();
            if (host == null || host.length() == 0) return "";
            host = host.toLowerCase(Locale.US);
            while (host.endsWith(".") && host.length() > 1) host = host.substring(0, host.length() - 1);
            return host;
        } catch (Exception ignored) {
            return "";
        }
    }

    private static boolean isIpLiteral(String host) {
        if (host.indexOf(':') >= 0) return true;
        String[] pieces = host.split("\\.");
        if (pieces.length != 4) return false;
        for (String piece : pieces) {
            if (piece.length() == 0 || piece.length() > 3) return false;
            int value = 0;
            for (int i = 0; i < piece.length(); i++) {
                char c = piece.charAt(i);
                if (c < '0' || c > '9') return false;
                value = value * 10 + c - '0';
            }
            if (value > 255) return false;
        }
        return true;
    }

}
