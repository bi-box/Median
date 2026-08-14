package com.xinyv.median;

public final class MediaManifestParserSelfTest {
    public static void main(String[] args) {
        String master = "#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=2500000,RESOLUTION=1920x1080,CODECS=\"avc1.640028,mp4a.40.2\"\n1080/index.m3u8\n" +
                "#EXT-X-MEDIA:TYPE=AUDIO,NAME=\"中文\",URI=\"audio/zh.m3u8\"\n#EXT-X-KEY:METHOD=AES-128,URI=\"key\"\n";
        MediaManifestParser.Playlist parsed = MediaManifestParser.parseHls("https://media.example/master.m3u8", master);
        require(parsed.variants.size() == 2, "master variants");
        require(parsed.variants.get(0).url.equals("https://media.example/1080/index.m3u8"), "relative URL");
        require(parsed.variants.get(0).label.contains("1920x1080") && parsed.variants.get(0).label.contains("2500 kbps"), "quality label");
        require(parsed.encrypted && parsed.live, "encryption/live flags");

        MediaManifestParser.Playlist vod = MediaManifestParser.parseHls("https://media.example/vod.m3u8",
                "#EXTM3U\n#EXTINF:5,\na.ts\n#EXTINF:5,\nb.ts\n#EXT-X-ENDLIST\n");
        require(vod.variants.isEmpty() && vod.segments == 2 && !vod.live, "media playlist summary");

        String mpd = "<?xml version=\"1.0\"?><MPD type=\"dynamic\"><ContentProtection/>" +
                "<BaseURL>dash/</BaseURL><Period><AdaptationSet><Representation id=\"1080\" width=\"1920\" height=\"1080\" bandwidth=\"4000000\" codecs=\"avc1.640028\">" +
                "<BaseURL>video/main.mp4</BaseURL><SegmentList><SegmentURL media=\"s1.m4s\"/></SegmentList></Representation></AdaptationSet></Period></MPD>";
        MediaManifestParser.Playlist dash = MediaManifestParser.parse("https://media.example/live/master.mpd", "application/octet-stream", mpd);
        require("DASH".equals(dash.format) && dash.live && dash.encrypted && dash.segments == 1, "DASH summary");
        require(dash.variants.size() == 1 && dash.variants.get(0).url.equals("https://media.example/live/video/main.mp4"), "DASH BaseURL");
        require(dash.variants.get(0).label.contains("1920×1080") && dash.variants.get(0).label.contains("4000 kbps"), "DASH track label");

        String smoothXml = "<SmoothStreamingMedia IsLive=\"TRUE\"><Protection><ProtectionHeader>x</ProtectionHeader></Protection><StreamIndex><c/><c/></StreamIndex></SmoothStreamingMedia>";
        MediaManifestParser.Playlist smooth = MediaManifestParser.parse("https://media.example/live.ism/Manifest", "", smoothXml);
        require("Smooth".equals(smooth.format) && smooth.live && smooth.encrypted && smooth.segments == 2, "Smooth summary");

        MediaManifestParser.Playlist bom = MediaManifestParser.parse("https://media.example/master.m3u8", "", "\ufeff" + master);
        require("HLS".equals(bom.format) && bom.variants.size() == 2, "BOM HLS detection");
        System.out.println("MediaManifestParserSelfTest passed");
    }

    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
