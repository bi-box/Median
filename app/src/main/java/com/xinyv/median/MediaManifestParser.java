package com.xinyv.median;

import java.net.URL;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Small, dependency-free adaptive-media manifest parser used only on explicit inspection. */
final class MediaManifestParser {
    static final class Variant {
        final String url;
        final String label;
        Variant(String url, String label) { this.url = url; this.label = label; }
    }

    static final class Playlist {
        final ArrayList<Variant> variants = new ArrayList<Variant>();
        String format = "HLS";
        boolean encrypted;
        boolean live = true;
        int segments;
    }

    static Playlist parse(String baseUrl, String declaredMime, String text) {
        String source = text == null ? "" : text;
        if (source.length() > 0 && source.charAt(0) == '\ufeff') source = source.substring(1);
        String mime = declaredMime == null ? "" : declaredMime.toLowerCase(Locale.US);
        if (source.trim().startsWith("#EXTM3U") || mime.contains("mpegurl"))
            return parseHls(baseUrl, source);
        if (Pattern.compile("<\\s*MPD\\b", Pattern.CASE_INSENSITIVE).matcher(source).find() ||
                mime.contains("dash+xml")) return parseDash(baseUrl, source);
        if (Pattern.compile("<\\s*SmoothStreamingMedia\\b", Pattern.CASE_INSENSITIVE).matcher(source).find() ||
                mime.contains("vnd.ms-sstr")) return parseSmooth(baseUrl, source);
        throw new IllegalArgumentException("无法识别媒体清单格式");
    }

    static Playlist parseHls(String baseUrl, String text) {
        if (text != null && text.length() > 0 && text.charAt(0) == '\ufeff') text = text.substring(1);
        if (text == null || !text.trim().startsWith("#EXTM3U"))
            throw new IllegalArgumentException("不是有效的 HLS 清单");
        Playlist result = new Playlist();
        HashSet<String> seen = new HashSet<String>();
        String pendingLabel = null;
        String[] lines = text.replace("\r", "").split("\n");
        for (String raw : lines) {
            String line = raw.trim();
            String upper = line.toUpperCase(Locale.US);
            if (upper.startsWith("#EXT-X-KEY:") && !"NONE".equalsIgnoreCase(attribute(line, "METHOD")))
                result.encrypted = true;
            else if (upper.equals("#EXT-X-ENDLIST")) result.live = false;
            else if (upper.startsWith("#EXTINF:")) result.segments++;
            else if (upper.startsWith("#EXT-X-STREAM-INF:")) pendingLabel = variantLabel(line);
            else if (upper.startsWith("#EXT-X-I-FRAME-STREAM-INF:"))
                add(result.variants, seen, baseUrl, attribute(line, "URI"), "I-Frame · " + variantLabel(line));
            else if (upper.startsWith("#EXT-X-MEDIA:")) {
                String type = attribute(line, "TYPE");
                String name = attribute(line, "NAME");
                add(result.variants, seen, baseUrl, attribute(line, "URI"),
                        (name.length() == 0 ? type : name) + (type.length() == 0 ? "" : " · " + type));
            } else if (line.length() > 0 && line.charAt(0) != '#' && pendingLabel != null) {
                add(result.variants, seen, baseUrl, line, pendingLabel);
                pendingLabel = null;
            }
            if (result.variants.size() >= 48) break;
        }
        return result;
    }

    static Playlist parseDash(String baseUrl, String text) {
        if (text == null || !Pattern.compile("<\\s*MPD\\b", Pattern.CASE_INSENSITIVE).matcher(text).find())
            throw new IllegalArgumentException("不是有效的 DASH 清单");
        Playlist result = new Playlist();
        result.format = "DASH";
        String root = firstMatch(text, "<\\s*MPD\\b([^>]*)>");
        result.live = "dynamic".equalsIgnoreCase(xmlAttribute(root, "type"));
        result.encrypted = containsIgnoreCase(text, "ContentProtection") || containsIgnoreCase(text, "cenc:pssh");
        result.segments = countMatches(text, "<\\s*(?:SegmentURL|S)\\b");
        String globalBase = xmlText(firstMatch(text, "<\\s*BaseURL\\b[^>]*>(.*?)</\\s*BaseURL\\s*>")).trim();
        HashSet<String> seen = new HashSet<String>();
        Matcher representations = Pattern.compile("<\\s*Representation\\b([^>]*)>(.*?)</\\s*Representation\\s*>",
                Pattern.CASE_INSENSITIVE | Pattern.DOTALL).matcher(text);
        while (representations.find() && result.variants.size() < 48) {
            String attributes = representations.group(1);
            String body = representations.group(2);
            String value = xmlText(firstMatch(body, "<\\s*BaseURL\\b[^>]*>(.*?)</\\s*BaseURL\\s*>")).trim();
            if (value.length() == 0) value = globalBase;
            String resolution = xmlAttribute(attributes, "width");
            String height = xmlAttribute(attributes, "height");
            if (resolution.length() > 0 && height.length() > 0) resolution += "×" + height;
            String bandwidth = xmlAttribute(attributes, "bandwidth");
            String codecs = xmlAttribute(attributes, "codecs");
            String id = xmlAttribute(attributes, "id");
            StringBuilder label = new StringBuilder(resolution.length() > 0 ? resolution :
                    (id.length() > 0 ? "轨道 " + id : "DASH 轨道"));
            try {
                long bits = Long.parseLong(bandwidth);
                if (bits > 0) label.append(" · ").append(bits / 1000L).append(" kbps");
            } catch (NumberFormatException ignored) {}
            if (codecs.length() > 0) label.append(" · ").append(codecs);
            add(result.variants, seen, baseUrl, value, label.toString());
        }
        if (result.variants.size() == 0 && globalBase.length() > 0)
            add(result.variants, seen, baseUrl, globalBase, "DASH BaseURL");
        return result;
    }

    static Playlist parseSmooth(String baseUrl, String text) {
        if (text == null || !Pattern.compile("<\\s*SmoothStreamingMedia\\b", Pattern.CASE_INSENSITIVE).matcher(text).find())
            throw new IllegalArgumentException("不是有效的 Smooth Streaming 清单");
        Playlist result = new Playlist();
        result.format = "Smooth";
        String root = firstMatch(text, "<\\s*SmoothStreamingMedia\\b([^>]*)>");
        result.live = "true".equalsIgnoreCase(xmlAttribute(root, "IsLive"));
        result.encrypted = containsIgnoreCase(text, "ProtectionHeader");
        result.segments = countMatches(text, "<\\s*c\\b");
        return result;
    }

    private static void add(List<Variant> out, HashSet<String> seen, String base, String value, String label) {
        if (value == null || value.length() == 0) return;
        try {
            String resolved = NetworkSecurity.parseHttpUrl(new URL(new URL(base), value).toString()).toString();
            if (seen.add(resolved)) out.add(new Variant(resolved, label.length() == 0 ? "自适应码流" : label));
        } catch (Exception ignored) {}
    }

    private static String variantLabel(String line) {
        String resolution = attribute(line, "RESOLUTION");
        String bandwidth = attribute(line, "AVERAGE-BANDWIDTH");
        if (bandwidth.length() == 0) bandwidth = attribute(line, "BANDWIDTH");
        String codecs = attribute(line, "CODECS");
        StringBuilder label = new StringBuilder();
        if (resolution.length() > 0) label.append(resolution);
        try {
            long bits = Long.parseLong(bandwidth);
            if (bits > 0) label.append(label.length() == 0 ? "" : " · ").append(bits / 1000L).append(" kbps");
        } catch (NumberFormatException ignored) {}
        if (codecs.length() > 0) label.append(label.length() == 0 ? "" : " · ").append(codecs);
        return label.length() == 0 ? "自适应码流" : label.toString();
    }

    private static String attribute(String line, String key) {
        String upper = line.toUpperCase(Locale.US);
        int at = upper.indexOf(key + '=');
        if (at < 0) return "";
        at += key.length() + 1;
        if (at < line.length() && line.charAt(at) == '"') {
            int end = line.indexOf('"', at + 1);
            return end < 0 ? "" : line.substring(at + 1, end).trim();
        }
        int end = line.indexOf(',', at);
        return line.substring(at, end < 0 ? line.length() : end).trim();
    }

    private static String firstMatch(String text, String expression) {
        if (text == null) return "";
        Matcher matcher = Pattern.compile(expression, Pattern.CASE_INSENSITIVE | Pattern.DOTALL).matcher(text);
        return matcher.find() ? matcher.group(1) : "";
    }

    private static String xmlAttribute(String attributes, String key) {
        if (attributes == null) return "";
        Matcher matcher = Pattern.compile("(?:^|\\s)" + Pattern.quote(key) + "\\s*=\\s*(['\"])(.*?)\\1",
                Pattern.CASE_INSENSITIVE | Pattern.DOTALL).matcher(attributes);
        return matcher.find() ? xmlText(matcher.group(2)).trim() : "";
    }

    private static String xmlText(String value) {
        return value == null ? "" : value.replace("&amp;", "&").replace("&quot;", "\"")
                .replace("&apos;", "'").replace("&lt;", "<").replace("&gt;", ">");
    }

    private static int countMatches(String text, String expression) {
        Matcher matcher = Pattern.compile(expression, Pattern.CASE_INSENSITIVE).matcher(text == null ? "" : text);
        int count = 0;
        while (matcher.find() && count < 100000) count++;
        return count;
    }

    private static boolean containsIgnoreCase(String text, String token) {
        return text != null && token != null && text.toLowerCase(Locale.US).contains(token.toLowerCase(Locale.US));
    }

    private MediaManifestParser() {}
}
