package com.xinyv.median;

import java.util.List;

public final class MediaResourceSnifferSelfTest {
    public static void main(String[] args) {
        expect(MediaResourceSniffer.isCandidate("https://cdn.example/master.m3u8?token=x", ""), "HLS extension");
        expect(MediaResourceSniffer.isCandidate("https://cdn.example/api?url=https%3A%2F%2Fx%2Fmaster.m3u8", ""), "encoded HLS URL");
        expect(MediaResourceSniffer.isCandidate("https://cdn.example/api?url=https%253A%252F%252Fx%252Fmaster%252Em3u8", ""), "double-encoded HLS URL");
        expect(MediaResourceSniffer.isCandidate("https://cdn.example/opaque?id=7", "video/mp4; q=1"), "extensionless MIME");
        expect(MediaResourceSniffer.isCandidate("https://cdn.example/play?id=7&mime_type=video%2Fmp4", ""), "extensionless playback endpoint");
        expect(MediaResourceSniffer.isCandidate("https://cdn.example/manifest?token=x", ""), "extensionless manifest");
        expect(MediaResourceSniffer.isCandidate("https://cdn.example/channel.ism/Manifest", "application/vnd.ms-sstr+xml"), "Smooth Streaming manifest");
        expect(MediaResourceSniffer.isCandidate("https://cdn.example/movie.rmvb", ""), "extended video format");
        expect(MediaResourceSniffer.isCandidate("https://cdn.example/lossless.ape", ""), "extended audio format");
        expect(MediaResourceSniffer.isCandidate("https://cdn.example/chunk.m4s?part=2", ""), "fMP4 segment");
        reject(MediaResourceSniffer.isCandidate("https://cdn.example/manifestation.js", "application/javascript"), "ordinary JavaScript");
        reject(MediaResourceSniffer.isCandidate("https://cdn.example/poster.jpg", "image/jpeg"), "image");
        reject(MediaResourceSniffer.shouldInspectRequest("/assets/app.js", "v=1", "application/javascript"), "static request fast rejection");
        expect(MediaResourceSniffer.shouldInspectRequest("/assets/app.js", "source=movie.mp4", ""), "media nested in static query");
        expect(MediaResourceSniffer.shouldInspectRequest("/video/app.js", "v=1", ""), "media marker in static path");
        expect(MediaResourceSniffer.shouldInspectRequest("/assets/app.js", "source=movie%252Emp4", ""), "encoded static query remains inspectable");
        expect(MediaResourceSniffer.shouldInspectRequest("/api/resource", "id=7", ""), "extensionless request remains inspectable");
        expect(MediaResourceSniffer.shouldInspectRequest("/assets/app.js", "", "video/mp4"), "media MIME bypasses static rejection");
        expect(MediaResourceSniffer.isObviousLoadResource("https://cdn.example/MOVIE.MP4?sig=1"), "cheap obvious resource gate");
        reject(MediaResourceSniffer.isObviousLoadResource("https://cdn.example/assets/application.js"), "cheap ordinary resource gate");

        MediaResourceSniffer sniffer = new MediaResourceSniffer();
        sniffer.beginPage("https://page.example/a");
        sniffer.observe("https://cdn.example/movie.mp4?sig=1#first", "", "page.example");
        sniffer.observe("https://cdn.example/movie.mp4?sig=1#second", "video/mp4", "page.example");
        expect(sniffer.size() == 1, "fragment deduplication");
        for (int i = 0; i < 40; i++)
            sniffer.observe("https://cdn.example/chunk.m4s?part=" + i, "", "page.example");
        expect(sniffer.size() == 25, "bounded segment set");
        sniffer.observe("https://cdn.example/master.m3u8", "", "page.example");
        List<MediaResourceSniffer.Resource> all = sniffer.getAll();
        expect("HLS 流".equals(all.get(0).kind), "manifest priority");
        all.get(0).kind = "changed";
        expect("HLS 流".equals(sniffer.getAll().get(0).kind), "resource snapshot isolation");
        sniffer.beginPage("https://page.example/b");
        expect(sniffer.size() == 0, "navigation reset");
        System.out.println("MediaResourceSnifferSelfTest passed");
    }

    private static void expect(boolean value, String label) {
        if (!value) throw new AssertionError("Expected: " + label);
    }

    private static void reject(boolean value, String label) {
        if (value) throw new AssertionError("Unexpected: " + label);
    }
}
