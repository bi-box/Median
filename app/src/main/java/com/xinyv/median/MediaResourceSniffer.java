// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Median Browser contributors

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
    // Header reads cross the WebView provider boundary. Keep the probe limited to endpoint
    // shapes that commonly carry opaque media, while the strict marker gate remains unchanged.
    private static final String[] MEDIA_ENDPOINT_HINTS = {
            "/play", "/stream", "/media", "/video", "/audio", "/manifest", "/playlist",
            "/segment", "/chunk", "/download"
    };
    private static final String[] MEDIA_QUERY_HINTS = {
            "mime=", "mime_type=", "content_type=", "format=", "media=", "stream=",
            "video=", "audio=", "playback=", "playback_url=", "video_url=", "audio_url="
    };
    private static final String[] LOAD_RESOURCE_HINTS = collectLoadResourceHints();
    private static final String[][] LOAD_RESOURCE_HINTS_BY_FIRST = indexHints(LOAD_RESOURCE_HINTS);
    private static final String[] REQUEST_HINTS = requestHints();
    private static final String[][] REQUEST_HINTS_BY_FIRST = indexHints(REQUEST_HINTS);
    private static final String[] STATIC_SUFFIXES = { ".png", ".jpg", ".jpeg", ".gif", ".webp", ".svg", ".css", ".js", ".mjs", ".json", ".woff", ".woff2", ".ttf", ".otf", ".ico", ".avif", ".pdf", ".zip", ".rar", ".7z", ".apk", ".doc", ".docx", ".xls", ".xlsx" };
    private final LinkedHashMap<String, Resource> items = new LinkedHashMap<String, Resource>();
    private String pageUrl = "";
    private int opaqueMediaCount;

    synchronized void beginPage(String url) {
        String next = normalizePageKey(url);
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
        if (!isHttpUrl(url)) return false;
        return containsIndexedHint(url, LOAD_RESOURCE_HINTS_BY_FIRST, LOAD_RESOURCE_HINTS);
    }

    private static boolean containsIndexedHint(String value, String[][] byFirst, String[] all) {
        for (int at = 0; at < value.length(); at++) {
            char first = value.charAt(at);
            if (first >= 'A' && first <= 'Z') first = (char) (first + ('a' - 'A'));
            // Non-ASCII characters can match ASCII under Java's Unicode ignore-case rules.
            String[] candidates = first < byFirst.length ? byFirst[first] : all;
            if (candidates == null) continue;
            for (String token : candidates)
                if (value.regionMatches(true, at, token, 0, token.length())) return true;
        }
        return false;
    }

    private static String[] collectLoadResourceHints() {
        String[][] groups = { HLS_HINTS, HLS_PATHS, DASH_HINTS, DASH_PATHS, SMOOTH_HINTS, SMOOTH_PATHS,
                VIDEO_HINTS, VIDEO_PATHS, AUDIO_HINTS, AUDIO_PATHS, SEGMENT_HINTS, SEGMENT_PATHS };
        int count = 0;
        for (String[] group : groups) count += group.length;
        String[] tokens = new String[count];
        int at = 0;
        for (String[] group : groups) {
            System.arraycopy(group, 0, tokens, at, group.length);
            at += group.length;
        }
        return tokens;
    }

    private static String[] requestHints() {
        String[] result = new String[LOAD_RESOURCE_HINTS.length + 2];
        System.arraycopy(LOAD_RESOURCE_HINTS, 0, result, 0, LOAD_RESOURCE_HINTS.length);
        result[result.length - 2] = "manifest";
        result[result.length - 1] = "playlist";
        return result;
    }

    private static String[][] indexHints(String[] hints) {
        String[][] buckets = new String[128][];
        int[] counts = new int[buckets.length];
        for (String token : hints) {
            char first = Character.toLowerCase(Character.toUpperCase(token.charAt(0)));
            if (first < counts.length) counts[first]++;
        }
        for (int first = 0; first < counts.length; first++) {
            if (counts[first] > 0) buckets[first] = new String[counts[first]];
            counts[first] = 0;
        }
        for (String token : hints) {
            char first = Character.toLowerCase(Character.toUpperCase(token.charAt(0)));
            if (first < counts.length) buckets[first][counts[first]++] = token;
        }
        return buckets;
    }

    /** Fast shared filter used by legacy callers and regression tests. */
    static boolean shouldInspectRequest(String encodedPath, String encodedQuery, String declaredMime) {
        String rawMime = value(declaredMime);
        if (rawMime.length() > 0 && isMediaMime(cleanMime(rawMime))) return true;
        String path = value(encodedPath);
        String query = value(encodedQuery);
        if (hasPotentialMediaMarker(path) || hasPotentialMediaMarker(query)) return true;
        if (path.indexOf('%') >= 0 || query.indexOf('%') >= 0 || path.indexOf("&amp;") >= 0 ||
                query.indexOf("&amp;") >= 0) return true;
        return !endsWithAnyIgnoreCase(path, STATIC_SUFFIXES);
    }

    /** Strict gate for the WebView interception hot path; opaque media is recovered after load. */
    static boolean isLikelyMediaRequest(String encodedPath, String encodedQuery, String declaredMime) {
        if (isMediaMimeHint(declaredMime)) return true;
        return hasPotentialMediaMarker(value(encodedPath)) || hasPotentialMediaMarker(value(encodedQuery));
    }

    /** MIME-only continuation after the caller has already classified this URL's markers. */
    static boolean isMediaMimeHint(String declaredMime) {
        String rawMime = value(declaredMime);
        return rawMime.length() > 0 && isMediaMime(cleanMime(rawMime));
    }

    /**
     * Returns true for extensionless endpoints worth one request-header probe. This is
     * intentionally narrower than shouldInspectRequest(), which must stay broad for URL-only
     * discovery and would cause an IPC read for most JSON/XHR requests.
     */
    static boolean shouldProbeMediaHeaders(String encodedPath, String encodedQuery) {
        return shouldProbeMediaHeaders(encodedPath, encodedQuery,
                isLikelyMediaRequest(encodedPath, encodedQuery, ""));
    }

    /** Reuses the exact URL-marker result when interception already computed it. */
    static boolean shouldProbeMediaHeaders(String encodedPath, String encodedQuery, boolean likelyMedia) {
        if (likelyMedia) return true;
        String path = value(encodedPath);
        String query = value(encodedQuery);
        return containsAnyIgnoreCase(path, MEDIA_ENDPOINT_HINTS) ||
                containsAnyIgnoreCase(query, MEDIA_QUERY_HINTS);
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
        return containsIndexedHint(value, REQUEST_HINTS_BY_FIRST, REQUEST_HINTS);
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
        return url != null && url.length() <= 8192 &&
                (url.regionMatches(true, 0, "https://", 0, 8) || url.regionMatches(true, 0, "http://", 0, 7));
    }

    private static String normalizePageKey(String url) {
        String key = value(url);
        int fragment = key.indexOf('#');
        return fragment < 0 ? key : key.substring(0, fragment);
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
