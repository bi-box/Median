/* Copyright 2026. Uses Chromium's public AndroidX support-library boundary protocol. */
package androidx.webkit;

import android.os.Handler;
import android.os.Looper;

import androidx.webkit.internal.WebViewGlueCommunicator;

import org.chromium.support_lib_boundary.ProfileBoundaryInterface;
import org.chromium.support_lib_boundary.ProfileStoreBoundaryInterface;
import org.chromium.support_lib_boundary.StaticsBoundaryInterface;
import org.chromium.support_lib_boundary.WebViewProviderFactoryBoundaryInterface;
import org.chromium.support_lib_boundary.util.BoundaryInterfaceReflectionUtil;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

/**
 * Optional connection hints for a navigation the user has already chosen. No HTTP request,
 * hidden WebView, page prefetch, URL path, search query, cookies or page body is sent by this API.
 * Call on the UI thread. Never call for suggestions, typed-but-unsubmitted text or inferred sites.
 */
public final class WebViewNavigationWarmupCompat {
    private static final String PRECONNECT = "PRECONNECT";
    private static final String ENQUEUE_PRECONNECT = "ENQUEUE_PRECONNECT";
    private static final String MULTI_PROFILE = "MULTI_PROFILE";
    private static final String MULTI_PROCESS_QUERY = "MULTI_PROCESS_QUERY";
    private static final long REUSE_WINDOW_NANOS = 10_000_000_000L;
    private static final int RECENT_ORIGIN_LIMIT = 20;
    private static final int MAX_NAVIGATION_ORIGINS = 5;
    private static final String[] recentOrigins = new String[RECENT_ORIGIN_LIMIT];
    private static final long[] recentConnectAt = new long[RECENT_ORIGIN_LIMIT];
    private static int replacementSlot;
    private static String[] pendingStartupOrigins;
    private static volatile String lastStatus = "unused";
    private static volatile int dispatched;

    private WebViewNavigationWarmupCompat() {}

    /** Destination policy supplied by the browser after its user protection settings are ready. */
    public interface OriginFilter {
        boolean allow(String origin);
    }

    /**
     * Records only the chosen origin until background provider discovery finishes. This method
     * never loads the provider or waits for its discovery lock. A provider must explicitly
     * advertise ENQUEUE_PRECONNECT: older PRECONNECT-only providers are skipped here because
     * accessing their ProfileStore could synchronously initialize native Chromium.
     */
    public static void enqueueForStartup(String confirmedNavigationUrl) {
        if (!onUiThread()) return;
        String origin = originOf(confirmedNavigationUrl);
        pendingStartupOrigins = origin == null ? null : new String[] { origin };
        if (pendingStartupOrigins == null) {
            lastStatus = "invalid-origin";
            return;
        }
        lastStatus = "pending-discovery";
        enqueuePendingIfReady();
    }

    /** Removes a pending startup hint when the chosen navigation is cancelled. */
    public static void cancelStartupPreconnect() {
        if (!onUiThread()) return;
        pendingStartupOrigins = null;
    }

    /**
     * For an already initialized browser, dispatch a connection-only hint before navigation
     * preparation. Returns false when unsupported, invalid, already hinted or not yet ready.
     * This method also never initializes the provider itself.
     */
    public static boolean preconnectForNavigation(String confirmedNavigationUrl) {
        return preconnectForNavigation(confirmedNavigationUrl, null);
    }

    /**
     * Adds a bounded list of render dependencies previously observed for this same destination.
     * The caller must supply only actual critical static-resource origins, never predictions,
     * advertisements or tracking endpoints. Full resource URLs and HTTPS downgrades are rejected.
     * Main, fixed dependencies and learned dependencies together never exceed five connections.
     */
    public static boolean preconnectForNavigation(String confirmedNavigationUrl, String[] learnedOrigins) {
        return preconnectForNavigation(confirmedNavigationUrl, learnedOrigins, null);
    }

    /** Filters every extra dependency through the browser's current ad/HTTPS access policy. */
    public static boolean preconnectForNavigation(String confirmedNavigationUrl, String[] learnedOrigins,
            OriginFilter extraOriginFilter) {
        if (!onUiThread()) return false;
        String[] origins = originsForNavigation(confirmedNavigationUrl, learnedOrigins, extraOriginFilter);
        if (origins == null) {
            lastStatus = "invalid-origin";
            return false;
        }
        return dispatch(origins, false);
    }

    /** Called only after the startup worker has finished discovering the support boundary. */
    static void onStartupBoundaryReady() {
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override public void run() { enqueuePendingIfReady(); }
        });
    }

    private static void enqueuePendingIfReady() {
        if (pendingStartupOrigins == null
                || WebViewGlueCommunicator.getFactoryIfLoaded() == null) return;
        String[] origins = pendingStartupOrigins;
        pendingStartupOrigins = null;
        dispatch(origins, true);
    }

    private static boolean dispatch(String[] origins, boolean enqueue) {
        WebViewProviderFactoryBoundaryInterface factory =
                WebViewGlueCommunicator.getFactoryIfLoaded();
        if (factory == null) {
            lastStatus = "boundary-not-ready";
            return false;
        }
        String feature = enqueue ? ENQUEUE_PRECONNECT : PRECONNECT;
        if (!WebViewGlueCommunicator.isFeatureSupportedIfLoaded(feature)
                || !WebViewGlueCommunicator.isFeatureSupportedIfLoaded(MULTI_PROFILE)
                || !WebViewGlueCommunicator.isFeatureSupportedIfLoaded(MULTI_PROCESS_QUERY)) {
            lastStatus = "unsupported";
            return false;
        }
        long now = System.nanoTime();
        boolean hasNewOrigin = false;
        for (String origin : origins) {
            if (!recentlyConnected(origin, now)) { hasNewOrigin = true; break; }
        }
        if (!hasNewOrigin) {
            lastStatus = "recent-origin";
            return false;
        }
        boolean connected = false;
        try {
            // AndroidX MULTI_PROFILE additionally requires enabled multi-process mode.
            StaticsBoundaryInterface statics = BoundaryInterfaceReflectionUtil.castToSuppLibClass(
                    StaticsBoundaryInterface.class, factory.getStatics());
            if (statics == null || !statics.isMultiProcessEnabled()) {
                lastStatus = "single-process";
                return false;
            }
            ProfileStoreBoundaryInterface store = BoundaryInterfaceReflectionUtil.castToSuppLibClass(
                    ProfileStoreBoundaryInterface.class, factory.getProfileStore());
            // AndroidX Profile.DEFAULT_PROFILE_NAME is "Default". getOrCreateProfile obtains
            // a lazy Java wrapper without initializing native state. Do not use getProfile:
            // Chromium 155's similarly named lookup synchronously waits for native startup.
            // No custom profile is created and the WebView keeps its default cookies/cache.
            ProfileBoundaryInterface profile = store == null ? null
                    : BoundaryInterfaceReflectionUtil.castToSuppLibClass(
                            ProfileBoundaryInterface.class, store.getOrCreateProfile("Default"));
            if (profile == null) {
                lastStatus = "default-profile-unavailable";
                return false;
            }
            for (String origin : origins) {
                if (recentlyConnected(origin, now)) continue;
                if (enqueue) profile.enqueuePreconnect(origin);
                else profile.preconnect(origin);
                rememberConnection(origin, now);
                dispatched++;
                connected = true;
            }
            lastStatus = enqueue ? "enqueued" : "preconnected";
            return connected;
        } catch (RuntimeException error) {
            lastStatus = "provider-rejected";
        } catch (LinkageError error) {
            lastStatus = "provider-incompatible";
        }
        // Hints must never block navigation or fall back to a separate HTTP/TLS stack.
        return connected;
    }

    /** Small, bounded per-origin window: a batch must not evict its own preceding origin. */
    private static boolean recentlyConnected(String origin, long now) {
        for (int i = 0; i < RECENT_ORIGIN_LIMIT; i++) {
            if (origin.equals(recentOrigins[i]) && now - recentConnectAt[i] < REUSE_WINDOW_NANOS)
                return true;
        }
        return false;
    }

    private static void rememberConnection(String origin, long now) {
        for (int i = 0; i < RECENT_ORIGIN_LIMIT; i++) {
            if (origin.equals(recentOrigins[i]) || recentOrigins[i] == null
                    || now - recentConnectAt[i] >= REUSE_WINDOW_NANOS) {
                recentOrigins[i] = origin;
                recentConnectAt[i] = now;
                return;
            }
        }
        recentOrigins[replacementSlot] = origin;
        recentConnectAt[replacementSlot] = now;
        replacementSlot = (replacementSlot + 1) % RECENT_ORIGIN_LIMIT;
    }

    /**
     * Fixed, evidence-based render dependencies for exact common entry points only. These script,
     * style, font and visible-image origins were observed through CDP on the actual device. The
     * mobile Baidu script chain included two serial DNS waits. Connect bounded dependencies
     * together before the chosen page discovers them. No inferred or advertising endpoints.
     */
    static String[] originsForNavigation(String url) {
        return originsForNavigation(url, null);
    }

    static String[] originsForNavigation(String url, String[] learnedOrigins) {
        return originsForNavigation(url, learnedOrigins, null);
    }

    /** Same bounded, policy-filtered origins for hints issued in the destination document. */
    public static String[] preconnectOriginsForNavigation(String url, String[] learnedOrigins,
                                                        OriginFilter extraOriginFilter) {
        return originsForNavigation(url, learnedOrigins, extraOriginFilter);
    }

    static String[] originsForNavigation(String url, String[] learnedOrigins, OriginFilter extraOriginFilter) {
        String origin = originOf(url);
        if (origin == null) return null;
        String[] origins = new String[MAX_NAVIGATION_ORIGINS];
        int count = appendOrigin(origins, 0, origin);
        if (learnedOrigins != null) {
            // Actual completed dependencies take priority over entry-point defaults. Otherwise
            // fixed hints can fill all slots before the image origin that caused a load tail.
            for (int i = 0; i < learnedOrigins.length && i < RECENT_ORIGIN_LIMIT
                    && count < MAX_NAVIGATION_ORIGINS; i++) {
                String dependency = validatedDependencyOrigin(learnedOrigins[i], origin);
                if (dependency != null) count = appendExtraOrigin(origins, count, dependency, extraOriginFilter);
            }
        }
        try {
            String path = new URI(url).getRawPath();
            if ("https://www.baidu.com".equals(origin) && "/s".equals(path)) {
                count = appendExtraOrigin(origins, count, "https://gss3.bdstatic.com", extraOriginFilter);
                count = appendExtraOrigin(origins, count, "https://mms-static.cdn.bcebos.com", extraOriginFilter);
                count = appendExtraOrigin(origins, count, "https://ms.bdstatic.com", extraOriginFilter);
            } else if ("https://www.google.com".equals(origin) && "/search".equals(path)) {
                count = appendExtraOrigin(origins, count, "https://www.gstatic.com", extraOriginFilter);
                count = appendExtraOrigin(origins, count, "https://encrypted-tbn0.gstatic.com", extraOriginFilter);
                count = appendExtraOrigin(origins, count, "https://ssl.gstatic.com", extraOriginFilter);
            } else if ("https://m.youtube.com".equals(origin)
                    && (path == null || path.length() == 0 || "/".equals(path))) {
                count = appendExtraOrigin(origins, count, "https://i.ytimg.com", extraOriginFilter);
                count = appendExtraOrigin(origins, count, "https://yt3.ggpht.com", extraOriginFilter);
                count = appendExtraOrigin(origins, count, "https://fonts.gstatic.com", extraOriginFilter);
                count = appendExtraOrigin(origins, count, "https://fonts.googleapis.com", extraOriginFilter);
            } else if ("https://www.bing.com".equals(origin) && "/search".equals(path)) {
                count = appendExtraOrigin(origins, count, "https://th.bing.com", extraOriginFilter);
            }
        } catch (URISyntaxException ignored) {
            // originOf already validated this URL; retain the safe main-origin path.
        }
        String[] selected = new String[count];
        System.arraycopy(origins, 0, selected, 0, count);
        return selected;
    }

    private static int appendOrigin(String[] origins, int count, String origin) {
        for (int i = 0; i < count; i++) if (origin.equals(origins[i])) return count;
        if (count < origins.length) origins[count++] = origin;
        return count;
    }

    private static int appendExtraOrigin(String[] origins, int count, String origin, OriginFilter filter) {
        if (count >= origins.length) return count;
        for (int i = 0; i < count; i++) if (origin.equals(origins[i])) return count;
        if (filter != null) {
            try {
                if (!filter.allow(origin)) return count;
            } catch (RuntimeException rejected) {
                return count;
            } catch (LinkageError incompatible) {
                return count;
            }
        }
        return appendOrigin(origins, count, origin);
    }

    private static String validatedDependencyOrigin(String candidate, String navigationOrigin) {
        String origin = originOf(candidate);
        if (origin == null || (navigationOrigin.startsWith("https://")
                && !origin.startsWith("https://"))) return null;
        try {
            URI dependency = new URI(candidate);
            String path = dependency.getRawPath();
            if ((path != null && path.length() != 0 && !"/".equals(path))
                    || dependency.getRawQuery() != null || dependency.getRawFragment() != null)
                return null;
            return origin;
        } catch (URISyntaxException invalid) {
            return null;
        }
    }

    private static boolean onUiThread() {
        if (Looper.myLooper() == Looper.getMainLooper()) return true;
        lastStatus = "wrong-thread";
        return false;
    }

    /** A strict parser: only a bounded HTTP(S) origin is ever retained or passed to Chromium. */
    static String originOf(String url) {
        if (url == null || url.length() == 0 || url.length() > 16384) return null;
        try {
            URI uri = new URI(url);
            String scheme = uri.getScheme();
            String host = uri.getHost();
            if (scheme == null || host == null || host.length() == 0
                    || uri.getRawUserInfo() != null || uri.isOpaque()) return null;
            scheme = scheme.toLowerCase(Locale.US);
            if (!("https".equals(scheme) || "http".equals(scheme))) return null;
            int port = uri.getPort();
            if (port == 0 || port < -1 || port > 65535) return null;
            if (("https".equals(scheme) && port == 443)
                    || ("http".equals(scheme) && port == 80)) port = -1;
            return scheme + "://" + host.toLowerCase(Locale.US) + (port < 0 ? "" : ":" + port);
        } catch (URISyntaxException invalid) {
            return null;
        }
    }

    /** Contains capability/status counters only, never a navigation URL, origin or search text. */
    public static String getDiagnosticReport() {
        return "Boundary=" + (WebViewGlueCommunicator.getFactoryIfLoaded() == null ? "not-ready" : "ready")
                + "; PRECONNECT=" + WebViewGlueCommunicator.isFeatureSupportedIfLoaded(PRECONNECT)
                + "; ENQUEUE_PRECONNECT=" + WebViewGlueCommunicator.isFeatureSupportedIfLoaded(ENQUEUE_PRECONNECT)
                + "; dispatched=" + dispatched + "; status=" + lastStatus;
    }
}
