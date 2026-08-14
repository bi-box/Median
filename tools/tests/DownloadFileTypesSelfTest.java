package com.xinyv.median;

import java.io.File;

public final class DownloadFileTypesSelfTest {
    public static void main(String[] args) {
        if (args.length != 1) throw new AssertionError("expected APK path");
        DownloadFileTypes.Metadata metadata =
                new DownloadFileTypes.Metadata("MedianBrowser.bin", "application/octet-stream");
        if (!DownloadFileTypes.correctCompletedApk(new File(args[0]), metadata))
            throw new AssertionError("APK archive was not detected");
        if (!"MedianBrowser.apk".equals(metadata.filename))
            throw new AssertionError("bad APK filename: " + metadata.filename);
        if (!DownloadFileTypes.APK_MIME.equals(metadata.mime))
            throw new AssertionError("bad APK MIME: " + metadata.mime);
        if (!DownloadFileTypes.needsResponseMetadata("content.bin", "application/octet-stream"))
            throw new AssertionError("generic binary metadata must be probed");
        if (DownloadFileTypes.needsResponseMetadata("content.apk", DownloadFileTypes.APK_MIME))
            throw new AssertionError("known APK metadata must not be probed");
        System.out.println("DownloadFileTypesSelfTest passed");
    }
}
