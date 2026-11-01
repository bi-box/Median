package com.xinyv.median;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Differential guards for the raw-URL UI gate, independent of the full media classifier. */
public final class MediaGateSelfTest {
    private static final String[] TOKEN_FIELDS = {
            "HLS_HINTS", "HLS_PATHS", "DASH_HINTS", "DASH_PATHS", "SMOOTH_HINTS", "SMOOTH_PATHS",
            "VIDEO_HINTS", "VIDEO_PATHS", "AUDIO_HINTS", "AUDIO_PATHS", "SEGMENT_HINTS", "SEGMENT_PATHS"
    };
    private static final String ASCII_NOISE =
            "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789._-/?:;=%&#+";
    private static final String UNICODE_NOISE = ASCII_NOISE + "\u0130\u0131\u017f\u212a\u00df\u00c5\u00e9\u03a3\u03c3\u03c2\u4e2d\u0307\ud83d\ude00";
    private static final List<String> TOKENS = tokens();
    private static int comparisons;

    public static void main(String[] args) {
        expect(TOKENS.size() == 85, "the oracle includes all 85 original UI markers");
        checkEveryToken();
        checkUnicodeFolding();
        checkRawUrlSemantics();
        checkSchemeAndLengthLimits();
        checkLongQueries();
        checkRandomUrls(2000, false, 0x4d656469616eL);
        checkRandomUrls(2000, true, 0x4d6564696170L);
        System.out.println("MediaGateSelfTest passed: " + TOKENS.size() + " markers, 4000 randomized URLs, " +
                comparisons + " differential comparisons");
    }

    private static void checkEveryToken() {
        for (String token : TOKENS) {
            String mixed = mixedCase(token);
            checkExpected("https://cdn.example/prefix" + token + "suffix.js", true,
                    "raw marker needs no token boundary: " + token);
            checkExpected("HTTPS://cdn.example/assets/app.js?source=" + mixed, true,
                    "mixed-case marker in a static query: " + token);
            checkExpected("http://cdn.example/assets/poster.JPG#" + mixed, true,
                    "mixed-case marker in a static fragment: " + token);
            checkExpected("https://" + mixed + ".example/assets/app.css", true,
                    "gate scans every raw URL component: " + token);
            checkExpected("https://cdn.example/" + mixed + "/assets/app.js?version=1", true,
                    "mixed-case marker before a static path suffix: " + token);
        }
    }

    private static void checkUnicodeFolding() {
        boolean testedFirstCharacter = false;
        for (String token : TOKENS) {
            for (int offset = 0; offset < token.length(); offset++) {
                char letter = Character.toLowerCase(token.charAt(offset));
                String replacements = letter == 'i' ? "\u0130\u0131" :
                        letter == 's' ? "\u017f" : letter == 'k' ? "\u212a" : "";
                for (int variant = 0; variant < replacements.length(); variant++) {
                    String folded = token.substring(0, offset) + replacements.charAt(variant) +
                            token.substring(offset + 1);
                    expect(folded.regionMatches(true, 0, token, 0, token.length()),
                            "Java regionMatches folds the chosen Unicode variant");
                    checkExpected("https://cdn.example/app.js?" + folded, true,
                            "Unicode marker folding at position " + offset + ": " + token);
                    if (offset == 0) testedFirstCharacter = true;
                }
            }
        }
        expect(testedFirstCharacter, "Unicode variants exercise first-character bucket dispatch");
        checkExpected("https://cdn.example/\u017fmoothstreaming", true,
                "long s folds into a marker's first character");
        checkExpected("https://cdn.example/app.js?\u017fegmentnumber=4", true,
                "long s folds into query-marker dispatch");
        checkExpected("https://cdn.example/app.js?v\u0130deo_url=x", true,
                "dotted capital I preserves Java's case-insensitive matching");
        checkExpected("https://cdn.example/app.js?v\u0131deo_url=x", true,
                "dotless I preserves Java's case-insensitive matching");
        checkExpected("https://cdn.example/app.js?playbac\u212a_url=x", true,
                "Kelvin sign folds into the interior of a marker");
        checkExpected("http\u017f://cdn.example/movie.mp4", true,
                "the original case-insensitive scheme check also folds long s");
    }

    private static void checkRawUrlSemantics() {
        for (String url : new String[] {
                "https://cdn.example/assets/app.js?video_url=opaque",
                "https://cdn.example/VIDEO/app.js?v=1",
                "https://cdn.example/assets/poster.jpg#movie.mp4",
                "https://movie.mp4.example/assets/app.css",
                "https://cdn.example/movie.mp4fake.js",
                "https://cdn.example/assets/app.js?source=movie.mp4junk",
                "https://cdn.example/assets/app.js?type=VIDEOmore",
                "https://cdn.example/assets/app.js?x=1&amp;FORMAT=MP4",
                "https://cdn.example/assets/app.js?source=movie.mp4%25",
                "https://cdn.example/assets/app.js?source=movie.mp4%2Fanything"
        }) checkExpected(url, true, "raw path, query, fragment, host and unbounded marker semantics");

        for (String url : new String[] {
                "https://cdn.example/movie%2Emp4",
                "https://cdn.example/movie%252Emp4",
                "https://cdn.example/%2Fvideo%2Fassets/app.js",
                "https://cdn.example/assets/app.js?format%3Dmp4",
                "https://cdn.example/assets/app.js?v%69deo_url=x",
                "https://cdn.example/assets/app.js?url=movie%2Emp4&amp;x=1",
                "https://cdn.example/manifest",
                "https://cdn.example/manifest?token=x",
                "https://cdn.example/playlist",
                "https://cdn.example/playlist?token=x",
                "https://cdn.example/manifestation.js",
                "https://cdn.example/assets/app.js?unknown=%",
                "https://cdn.example/assets/app.js#ordinary"
        }) checkExpected(url, false, "gate neither decodes percent escapes nor adds bare manifest markers");
    }

    private static void checkSchemeAndLengthLimits() {
        for (String url : new String[] { null, "", "ftp://cdn.example/movie.mp4", "file:///movie.mp4",
                "blob:https://cdn.example/movie.mp4", "mediasource:movie.mp4", "//cdn.example/movie.mp4",
                " https://cdn.example/movie.mp4", "http:/cdn.example/movie.mp4", "httpx://cdn.example/movie.mp4" })
            checkExpected(url, false, "original HTTP(S) scheme requirement");
        checkExpected("HtTp://cdn.example/movie.mp4", true, "mixed-case HTTP is accepted");
        checkExpected("hTtPs://cdn.example/movie.mp4", true, "mixed-case HTTPS is accepted");
        checkExpected("http://.mp4", true, "gate preserves its raw scheme check without adding host validation");
        checkExpected("https://?source=movie.mp4", true, "gate preserves raw malformed-authority acceptance");
        checkExpected(sizedUrl(8192, ".mp4"), true, "media marker at the 8192-character limit");
        checkExpected(sizedUrl(8193, ".mp4"), false, "8193-character URL is rejected before marker scanning");
        checkExpected(sizedUrl(8192, "ordinary"), false, "static query at the exact length limit");
        checkExpected(sizedUrl(8193, "ordinary"), false, "oversized static query");
        checkExpected("https://cdn.example/movie.mp4?" + repeat('x', 8192), false,
                "an early media marker cannot bypass the URL length limit");
    }

    private static void checkLongQueries() {
        String noise = repeat('x', 6000);
        for (String suffix : new String[] { ".jpg", ".js", ".css", ".woff2", ".json" }) {
            String base = "https://cdn.example/assets/resource" + suffix + "?signature=" + noise;
            checkExpected(base, false, "long static query contains no marker");
            checkExpected(base + "&source=movie.mp4", true, "late raw media marker survives a long static query");
            checkExpected(base + "#TYPE=HLS", true, "late fragment marker survives a long static query");
            checkExpected(base + "&source=movie%252Emp4", false, "late encoded marker remains unrecognized");
        }
    }

    private static void checkRandomUrls(int count, boolean unicode, long seed) {
        Random random = new Random(seed);
        String alphabet = unicode ? UNICODE_NOISE : ASCII_NOISE;
        for (int i = 0; i < count; i++) {
            String prefix = (i % 19) == 0 ? "ftp://cdn.example/" :
                    (i % 23) == 0 ? "HTTP\u017f://cdn.example/" : "https://cdn.example/";
            int length = 20 + random.nextInt(380);
            StringBuilder raw = new StringBuilder(prefix);
            for (int j = 0; j < length; j++) raw.append(alphabet.charAt(random.nextInt(alphabet.length())));
            if ((i & 1) == 0) {
                String token = TOKENS.get(random.nextInt(TOKENS.size()));
                token = (i % 4) == 0 ? mixedCase(token) : token;
                if (unicode) token = unicodeVariant(token, random);
                raw.insert(prefix.length() + random.nextInt(length + 1), token);
            } else if (i % 7 == 0) {
                raw.append(i % 3 == 0 ? "movie%252Emp4" : "%2Fvideo%2F");
            }
            check(raw.toString(), (unicode ? "Unicode" : "ASCII") + " randomized URL " + i);
        }
    }

    private static String unicodeVariant(String token, Random random) {
        StringBuilder result = new StringBuilder(token);
        for (int i = 0; i < result.length(); i++) {
            char letter = Character.toLowerCase(result.charAt(i));
            if (!random.nextBoolean()) continue;
            if (letter == 'i') result.setCharAt(i, random.nextBoolean() ? '\u0130' : '\u0131');
            else if (letter == 's') result.setCharAt(i, '\u017f');
            else if (letter == 'k') result.setCharAt(i, '\u212a');
        }
        return result.toString();
    }

    private static String mixedCase(String token) {
        StringBuilder result = new StringBuilder(token);
        for (int i = 0; i < result.length(); i++)
            if ((i & 1) == 0) result.setCharAt(i, Character.toUpperCase(result.charAt(i)));
        return result.toString();
    }

    private static String sizedUrl(int size, String suffix) {
        String prefix = "https://cdn.example/assets/app.js?signature=";
        String url = prefix + repeat('x', size - prefix.length() - suffix.length()) + suffix;
        expect(url.length() == size, "sized URL fixture has the requested length");
        return url;
    }

    private static String repeat(char value, int count) {
        StringBuilder result = new StringBuilder(count);
        for (int i = 0; i < count; i++) result.append(value);
        return result.toString();
    }

    private static List<String> tokens() {
        try {
            ArrayList<String> result = new ArrayList<String>();
            for (String name : TOKEN_FIELDS) {
                Field field = MediaResourceSniffer.class.getDeclaredField(name);
                field.setAccessible(true);
                for (String token : (String[]) field.get(null)) result.add(token);
            }
            return result;
        } catch (ReflectiveOperationException error) {
            throw new AssertionError("Cannot load the original UI marker arrays", error);
        }
    }

    /** The original gate: whole raw URL, original marker order and regionMatches case folding. */
    private static boolean oldGate(String url) {
        if (url == null || url.length() > 8192 ||
                !(url.regionMatches(true, 0, "https://", 0, 8) || url.regionMatches(true, 0, "http://", 0, 7)))
            return false;
        for (String token : TOKENS) {
            for (int i = 0, end = url.length() - token.length(); i <= end; i++)
                if (url.regionMatches(true, i, token, 0, token.length())) return true;
        }
        return false;
    }

    private static void checkExpected(String url, boolean expected, String label) {
        expect(oldGate(url) == expected, label + " (original gate fixture)");
        check(url, label);
    }

    private static void check(String url, String label) {
        boolean expected = oldGate(url);
        boolean actual = MediaResourceSniffer.isObviousLoadResource(url);
        comparisons++;
        if (actual != expected)
            throw new AssertionError(label + ": expected " + expected + ", got " + actual + ", URL=" + url);
    }

    private static void expect(boolean value, String label) {
        if (!value) throw new AssertionError("Expected: " + label);
    }
}
