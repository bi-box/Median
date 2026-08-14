package com.xinyv.median;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;

/** Bounded, metadata-only media URL index populated from requests and DOM media elements. */
final class MediaResourceSniffer {
    static final class Resource implements Cloneable {
        String url;
        String mime;
        String kind;
        String pageHost;
        String source;
        long seenAt;
        int priority;
        int width;
        int height;
        double duration;

        Resource copy() {
            try { return (Resource) super.clone(); }
            catch (CloneNotSupportedException impossible) { throw new AssertionError(impossible); }
        }
    }

    private static final int MAX_ITEMS = 120;
    private static final int MAX_SEGMENTS = 24;
    private static final String[] HLS_HINTS = { ".m3u8", ".m3u", "format=m3u8", "type=hls", "format=hls" };
    private static final String[] HLS_PATHS = { "/hls/", "playlist.m3u", "master.m3u", "hls_manifest", "/live/playlist" };
    private static final String[] DASH_HINTS = { ".mpd", "format=mpd", "type=dash", "format=dash" };
    private static final String[] DASH_PATHS = { "/dash/", "dashmanifest" };
    private static final String[] SMOOTH_HINTS = { ".ism/manifest", ".isml/manifest", "format=ism", "type=smooth" };
    private static final String[] SMOOTH_PATHS = { "/smooth/", "smoothstreaming" };
    private static final String[] VIDEO_HINTS = { ".mp4", ".webm", ".mkv", ".mov", ".m4v", ".flv", ".ogv", ".3gp", ".3g2", ".avi", ".wmv", ".asf", ".mpg", ".mpeg", ".m2v", ".m2ts", ".mts", ".vob", ".rmvb", ".rm" };
    private static final String[] VIDEO_PATHS = { "mime=video", "mime_type=video", "content_type=video", "type=video", "format=mp4", "playback_url=", "video_url=", "/video/" };
    private static final String[] AUDIO_HINTS = { ".mp3", ".m4a", ".aac", ".ogg", ".oga", ".opus", ".flac", ".wav", ".weba", ".amr", ".ape", ".wma", ".alac", ".mpga", ".ac3", ".eac3", ".aiff", ".aif" };
    private static final String[] AUDIO_PATHS = { "mime=audio", "mime_type=audio", "content_type=audio", "type=audio", "format=mp3", "audio_url=", "/audio/" };
    private static final String[] SEGMENT_HINTS = { ".m4s", ".cmfv", ".cmfa", ".fmp4", ".ts", ".mp2t", ".aacp" };
    private static final String[] ISO_SEGMENT_HINTS = { ".m4s", ".cmfv", ".fmp4" };
    private static final String[] SEGMENT_PATHS = { "/segment/", "/segments/", "segmentnumber=" };
    private static final String[] STATIC_SUFFIXES = { ".png", ".jpg", ".jpeg", ".gif", ".webp", ".svg", ".css", ".js", ".mjs", ".json", ".woff", ".woff2", ".ttf", ".otf", ".ico", ".avif", ".pdf", ".zip", ".rar", ".7z", ".apk", ".doc", ".docx", ".xls", ".xlsx" };
    private final LinkedHashMap<String, Resource> items = new LinkedHashMap<String, Resource>();
    private String pageUrl = "";
    private int opaqueMediaCount;

    synchronized void beginPage(String url) {
        String next = value(url);
        if (next.equals(pageUrl)) return;
        pageUrl = next;
        items.clear();
        opaqueMediaCount = 0;
    }

    void observe(String url, String declaredMime, String pageHost) {
        observe(url, declaredMime, pageHost, "network", 0, 0, 0d);
    }

    void observe(String url, String declaredMime, String pageHost, String source,
                 int width, int height, double duration) {
        if (!isHttpUrl(url)) return;
        String decoded = searchable(url);
        String mime = cleanMime(declaredMime);
        String kind = kindOf(decoded, mime);
        if (kind.length() == 0) return;
        addMedia(url, decoded, mime, kind, pageHost, source, width, height, duration);
    }

    synchronized void observeOpaque(String url, String declaredMime) {
        String value = value(url).toLowerCase(Locale.US);
        String mime = cleanMime(declaredMime);
        if ((value.startsWith("blob:") || value.startsWith("mediasource:")) &&
                (mime.startsWith("video/") || mime.startsWith("audio/") || value.startsWith("blob:")))
            opaqueMediaCount = Math.min(99, opaqueMediaCount + 1);
    }

    synchronized void noteOpaqueCount(int count) {
        opaqueMediaCount = Math.max(opaqueMediaCount, Math.min(99, Math.max(0, count)));
    }

    /** Fast shared filter used by WebView request interception and regression tests. */
    static boolean isCandidate(String url, String declaredMime) {
        return isHttpUrl(url) && kindOf(searchable(url), cleanMime(declaredMime)).length() > 0;
    }

    /** Cheap UI-thread gate for WebView's redundant onLoadResource callback. */
    static boolean isObviousLoadResource(String url) {
        return isHttpUrl(url) && (containsAnyIgnoreCase(url, HLS_HINTS) || containsAnyIgnoreCase(url, HLS_PATHS) ||
                containsAnyIgnoreCase(url, DASH_HINTS) || containsAnyIgnoreCase(url, DASH_PATHS) ||
                containsAnyIgnoreCase(url, SMOOTH_HINTS) || containsAnyIgnoreCase(url, SMOOTH_PATHS) ||
                containsAnyIgnoreCase(url, VIDEO_HINTS) || containsAnyIgnoreCase(url, VIDEO_PATHS) ||
                containsAnyIgnoreCase(url, AUDIO_HINTS) || containsAnyIgnoreCase(url, AUDIO_PATHS) ||
                containsAnyIgnoreCase(url, SEGMENT_HINTS) || containsAnyIgnoreCase(url, SEGMENT_PATHS));
    }

    /**
     * Allocation-free negative gate for WebView's per-request hot path. It only rejects a known
     * static extension when neither its path nor query can match the full classifier. Ambiguous,
     * encoded and extensionless URLs always continue to the complete detector.
     */
    static boolean shouldInspectRequest(String encodedPath, String encodedQuery, String declaredMime) {
        String mime = cleanMime(declaredMime);
        if (isMediaMime(mime)) return true;
        String path = value(encodedPath);
        if (!endsWithAnyIgnoreCase(path, STATIC_SUFFIXES)) return true;
        String query = value(encodedQuery);
        if (path.indexOf('%') >= 0 || query.indexOf('%') >= 0 || path.indexOf("&amp;") >= 0 || query.indexOf("&amp;") >= 0)
            return true;
        return hasPotentialMediaMarker(path) || hasPotentialMediaMarker(query);
    }

    synchronized List<Resource> getAll() {
        ArrayList<Resource> result = new ArrayList<Resource>(items.size());
        for (Resource source : items.values()) result.add(copy(source));
        Collections.sort(result, new Comparator<Resource>() {
            @Override public int compare(Resource left, Resource right) {
                if (left.priority != right.priority) return right.priority - left.priority;
                return left.seenAt == right.seenAt ? 0 : (left.seenAt < right.seenAt ? 1 : -1);
            }
        });
        return result;
    }

    synchronized int size() { return items.size(); }
    synchronized int opaqueCount() { return opaqueMediaCount; }

    private void addMedia(String url, String normalizedUrl, String mime, String kind, String pageHost,
                          String source, int width, int height, double duration) {
        String key = normalizeKey(url);
        synchronized (this) {
            Resource existing = items.remove(key);
            Resource item = existing == null ? new Resource() : existing;
            item.url = stripHtmlAmp(url);
            item.kind = kind;
            item.mime = normalizedMime(normalizedUrl, mime, kind);
            item.pageHost = value(pageHost);
            item.source = value(source);
            if (width > 0) item.width = width;
            if (height > 0) item.height = height;
            if (duration > 0 && !Double.isInfinite(duration) && !Double.isNaN(duration)) item.duration = duration;
            item.seenAt = System.currentTimeMillis();
            item.priority = priorityOf(kind);
            items.put(key, item);
            if (item.priority == 1) trimSegments();
            while (items.size() > MAX_ITEMS) removeLowestPriorityOldest();
        }
    }

    private void trimSegments() {
        int count = 0;
        for (Resource item : items.values()) if (item.priority == 1) count++;
        while (count > MAX_SEGMENTS) {
            String victim = null;
            for (java.util.Map.Entry<String, Resource> entry : items.entrySet()) {
                if (entry.getValue().priority == 1) { victim = entry.getKey(); break; }
            }
            if (victim == null) return;
            items.remove(victim);
            count--;
        }
    }

    private void removeLowestPriorityOldest() {
        String victim = null;
        int lowest = Integer.MAX_VALUE;
        for (java.util.Map.Entry<String, Resource> entry : items.entrySet()) {
            if (entry.getValue().priority < lowest) {
                lowest = entry.getValue().priority;
                victim = entry.getKey();
            }
        }
        if (victim != null) items.remove(victim);
    }

    private static String kindOf(String url, String mime) {
        if (mime.contains("mpegurl") || hasAny(url, HLS_HINTS) || containsAny(url, HLS_PATHS)) return "HLS 流";
        if (mime.contains("dash+xml") || hasAny(url, DASH_HINTS) || containsAny(url, DASH_PATHS)) return "DASH 流";
        if (mime.contains("vnd.ms-sstr") || hasAny(url, SMOOTH_HINTS) || containsAny(url, SMOOTH_PATHS)) return "Smooth 流";
        if (mime.startsWith("video/") || hasAny(url, VIDEO_HINTS) || containsAny(url, VIDEO_PATHS)) return "视频";
        if (mime.startsWith("audio/") || hasAny(url, AUDIO_HINTS) || containsAny(url, AUDIO_PATHS)) return "音频";
        if (mime.contains("x-median-range")) return "范围媒体候选";
        if (hasAny(url, SEGMENT_HINTS) || containsAny(url, SEGMENT_PATHS)) return "媒体分片";
        if (hasPathMarker(url, "manifest") || hasPathMarker(url, "playlist")) return "媒体清单";
        return "";
    }

    private static String normalizedMime(String url, String mime, String kind) {
        if (mime.startsWith("video/") || mime.startsWith("audio/") || mime.startsWith("application/")) return mime;
        if ("HLS 流".equals(kind)) return "application/vnd.apple.mpegurl";
        if ("DASH 流".equals(kind)) return "application/dash+xml";
        if ("Smooth 流".equals(kind)) return "application/vnd.ms-sstr+xml";
        if ("媒体清单".equals(kind)) return "application/octet-stream";
        if ("范围媒体候选".equals(kind)) return "application/octet-stream";
        if ("媒体分片".equals(kind)) return hasAny(url, ISO_SEGMENT_HINTS) ? "video/iso.segment" : "video/mp2t";
        if (hasToken(url, ".mp3")) return "audio/mpeg";
        if (hasToken(url, ".m4a") || hasToken(url, ".aac")) return "audio/mp4";
        if (hasToken(url, ".webm") || hasToken(url, ".weba")) return "video/webm";
        return "视频".equals(kind) ? "video/*" : "audio/*";
    }

    private static int priorityOf(String kind) {
        if ("HLS 流".equals(kind) || "DASH 流".equals(kind) || "Smooth 流".equals(kind) || "媒体清单".equals(kind)) return 3;
        if ("媒体分片".equals(kind) || "范围媒体候选".equals(kind)) return 1;
        return 2;
    }

    private static String searchable(String raw) {
        String source = value(raw);
        if (source.indexOf("&amp;") >= 0) source = source.replace("&amp;", "&");
        String lower = source.toLowerCase(Locale.US);
        if (lower.indexOf('%') < 0) return lower;
        for (int round = 0; round < 2 && lower.indexOf('%') >= 0; round++) {
            String decoded = lower.replace("%25", "%").replace("%2e", ".").replace("%2f", "/")
                    .replace("%3a", ":").replace("%3d", "=").replace("%26", "&").replace("%2b", "+");
            if (decoded.equals(lower)) break;
            lower = decoded;
        }
        return lower;
    }

    private static String cleanMime(String raw) {
        String mime = value(raw).trim().toLowerCase(Locale.US);
        int comma = mime.indexOf(',');
        if (comma >= 0) mime = mime.substring(0, comma);
        int semicolon = mime.indexOf(';');
        if (semicolon >= 0) mime = mime.substring(0, semicolon);
        return mime.trim();
    }

    private static boolean hasAny(String value, String... tokens) {
        for (String token : tokens) if (hasToken(value, token)) return true;
        return false;
    }

    private static boolean containsAny(String value, String... tokens) {
        for (String token : tokens) if (value.contains(token)) return true;
        return false;
    }

    private static boolean hasPotentialMediaMarker(String value) {
        return containsAnyIgnoreCase(value, HLS_HINTS) || containsAnyIgnoreCase(value, HLS_PATHS) ||
                containsAnyIgnoreCase(value, DASH_HINTS) || containsAnyIgnoreCase(value, DASH_PATHS) ||
                containsAnyIgnoreCase(value, SMOOTH_HINTS) || containsAnyIgnoreCase(value, SMOOTH_PATHS) ||
                containsAnyIgnoreCase(value, VIDEO_HINTS) || containsAnyIgnoreCase(value, VIDEO_PATHS) ||
                containsAnyIgnoreCase(value, AUDIO_HINTS) || containsAnyIgnoreCase(value, AUDIO_PATHS) ||
                containsAnyIgnoreCase(value, SEGMENT_HINTS) || containsAnyIgnoreCase(value, SEGMENT_PATHS) ||
                containsIgnoreCase(value, "manifest") || containsIgnoreCase(value, "playlist");
    }

    private static boolean containsAnyIgnoreCase(String value, String... tokens) {
        for (String token : tokens) if (containsIgnoreCase(value, token)) return true;
        return false;
    }

    private static boolean containsIgnoreCase(String value, String token) {
        if (value == null || token == null || token.length() == 0 || token.length() > value.length()) return false;
        for (int i = 0, end = value.length() - token.length(); i <= end; i++)
            if (value.regionMatches(true, i, token, 0, token.length())) return true;
        return false;
    }

    private static boolean endsWithAnyIgnoreCase(String value, String... suffixes) {
        for (String suffix : suffixes) {
            int at = value.length() - suffix.length();
            if (at >= 0 && value.regionMatches(true, at, suffix, 0, suffix.length())) return true;
        }
        return false;
    }

    private static boolean isMediaMime(String mime) {
        return mime.startsWith("video/") || mime.startsWith("audio/") || mime.contains("mpegurl") ||
                mime.contains("dash+xml") || mime.contains("vnd.ms-sstr") || mime.contains("x-median-range");
    }

    private static boolean hasPathMarker(String value, String marker) {
        String token = "/" + marker;
        int from = 0;
        while (true) {
            int at = value.indexOf(token, from);
            if (at < 0) return false;
            int end = at + token.length();
            if (end == value.length() || "?#/&=".indexOf(value.charAt(end)) >= 0) return true;
            from = at + 1;
        }
    }

    private static boolean hasToken(String value, String token) {
        int from = 0;
        while (true) {
            int at = value.indexOf(token, from);
            if (at < 0) return false;
            int end = at + token.length();
            if (!token.startsWith(".") || end == value.length() || "?#&/=;%".indexOf(value.charAt(end)) >= 0) return true;
            from = at + 1;
        }
    }

    private static boolean isHttpUrl(String url) {
        return url != null && url.length() <= 8192 && (url.startsWith("https://") || url.startsWith("http://"));
    }

    private static String normalizeKey(String url) {
        String key = stripHtmlAmp(url);
        int fragment = key.indexOf('#');
        return fragment < 0 ? key : key.substring(0, fragment);
    }

    private static String stripHtmlAmp(String value) { return value(value).replace("&amp;", "&"); }

    private static Resource copy(Resource source) {
        return source.copy();
    }

    private static String value(String value) { return value == null ? "" : value; }
}
