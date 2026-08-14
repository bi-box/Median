package com.xinyv.median;

public final class MediaProbeScriptSelfTest {
    public static void main(String[] args) {
        String script = MediaProbeScript.build();
        String live = MediaProbeScript.install();
        for (String marker : new String[] { "currentSrc", "performance.getEntriesByType", "application/ld+json",
                "data-video-url", "data-stream-url", "twitter:player:stream", "srcset", "m3u8", "m4s", "playback", "mime_type", "vnd\\.ms-sstr", "shadowRoot", "__NEXT_DATA__" })
            if (!script.contains(marker)) throw new AssertionError("media probe missing: " + marker);
        for (String marker : new String[] { "PerformanceObserver", "loadedmetadata", "__medianMediaLog", "__medianMediaOpaque" })
            if (!live.contains(marker)) throw new AssertionError("live media collector missing: " + marker);
        if (script.length() > 7000) throw new AssertionError("media probe unexpectedly large");
        if (args.length == 0) System.out.println("MediaProbeScriptSelfTest passed");
        else if ("build".equals(args[0])) System.out.print(script);
        else if ("install".equals(args[0])) System.out.print(live);
        else throw new IllegalArgumentException("unknown script");
    }
}
