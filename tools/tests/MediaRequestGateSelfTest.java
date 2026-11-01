package com.xinyv.median;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

/** Independent original marker oracle for the request-path shared first-character index. */
public final class MediaRequestGateSelfTest {
    private static final List<String> MARKERS = markers();
    private static int checks;

    public static void main(String[] args) {
        for (String marker : MARKERS) {
            check("/prefix" + marker + "suffix.js", "v=1", "");
            check("/ordinary.js", "x=" + marker.toUpperCase(java.util.Locale.US), "");
            for (int i = 0; i < marker.length(); i++) {
                char c = marker.charAt(i);
                String variants = c == 'i' ? "\u0130\u0131" : c == 's' ? "\u017f" : c == 'k' ? "\u212a" : "";
                for (int j = 0; j < variants.length(); j++)
                    check("/ordinary.js", "x=" + marker.substring(0, i) + variants.charAt(j) + marker.substring(i + 1), "");
            }
        }
        String alphabet = "azAZ09._/%=&?\u0130\u0131\u017f\u212a\u4e2d\ud83d\ude00";
        String[] mimes = { null, "", "*/*", " video/mp4; codecs=avc1 ", "audio/*", "application/x-median-range",
                "application/vnd.apple.mpegurl", "application/json", "text/html,video/mp4", "VIDEO/MP4" };
        Random random = new Random(0x4d65646961L);
        for (int n = 0; n < 4000; n++) {
            StringBuilder value = new StringBuilder();
            for (int i = random.nextInt(400); i > 0; i--) value.append(alphabet.charAt(random.nextInt(alphabet.length())));
            if ((n & 1) == 0) value.insert(random.nextInt(value.length() + 1), MARKERS.get(random.nextInt(MARKERS.size())));
            for (String mime : mimes) check(n % 3 == 0 ? null : "/assets/app.js", value.toString(), mime);
        }
        check(null, null, null);
        check("/ordinary.js", "signature=" + "x".repeat(8192) + "&source=movie.mp4", "");
        check("/ordinary.js", "signature=" + "x".repeat(8192) + "&source=movie%252Emp4", "");
        System.out.println("MediaRequestGateSelfTest passed (" + checks + " marker/MIME/probe comparisons)");
    }

    private static void check(String path, String query, String mime) {
        boolean marker = oldMarker(path) || oldMarker(query);
        String clean = mime == null ? "" : mime.trim().toLowerCase(java.util.Locale.US);
        int at = clean.indexOf(';'); if (at >= 0) clean = clean.substring(0, at).trim();
        boolean mediaMime = clean.startsWith("video/") || clean.startsWith("audio/") || clean.contains("mpegurl") ||
                clean.contains("dash+xml") || clean.contains("vnd.ms-sstr") || clean.contains("x-median-range");
        boolean expected = marker || mediaMime;
        expect(MediaResourceSniffer.isLikelyMediaRequest(path, query, mime) == expected, "URL/MIME classification");
        expect(MediaResourceSniffer.isMediaMimeHint(mime) == mediaMime, "MIME continuation");
        boolean probe = marker || contains(path, new String[]{"/play","/stream","/media","/video","/audio","/manifest","/playlist","/segment","/chunk","/download"}) ||
                contains(query, new String[]{"mime=","mime_type=","content_type=","format=","media=","stream=","video=","audio=","playback=","playback_url=","video_url=","audio_url="});
        expect(MediaResourceSniffer.shouldProbeMediaHeaders(path, query) == probe, "standalone header gate");
        expect(MediaResourceSniffer.shouldProbeMediaHeaders(path, query, marker) == probe, "reused header gate");
    }

    private static boolean oldMarker(String value) { return contains(value, MARKERS.toArray(new String[0])); }
    private static boolean contains(String value, String[] tokens) {
        if (value == null) return false;
        for (String token : tokens) for (int i = 0; i <= value.length() - token.length(); i++)
            if (value.regionMatches(true, i, token, 0, token.length())) return true;
        return false;
    }
    private static List<String> markers() {
        ArrayList<String> result = new ArrayList<>();
        try {
            for (String name : Arrays.asList("HLS_HINTS","HLS_PATHS","DASH_HINTS","DASH_PATHS","SMOOTH_HINTS","SMOOTH_PATHS",
                    "VIDEO_HINTS","VIDEO_PATHS","AUDIO_HINTS","AUDIO_PATHS","SEGMENT_HINTS","SEGMENT_PATHS")) {
                Field f = MediaResourceSniffer.class.getDeclaredField(name); f.setAccessible(true);
                result.addAll(Arrays.asList((String[]) f.get(null)));
            }
        } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
        result.add("manifest"); result.add("playlist"); return result;
    }
    private static void expect(boolean value, String message) { checks++; if (!value) throw new AssertionError(message); }
}
