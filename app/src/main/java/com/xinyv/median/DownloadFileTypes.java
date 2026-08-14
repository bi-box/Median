package com.xinyv.median;

import android.content.ContentResolver;
import android.net.Uri;
import android.webkit.MimeTypeMap;
import android.webkit.URLUtil;

import java.io.File;
import java.util.Locale;
import java.util.zip.ZipFile;

/** Small, shared filename/MIME resolver for WebView, HTTP and completed downloads. */
final class DownloadFileTypes {
    static final String APK_MIME = "application/vnd.android.package-archive";
    private static final String BINARY_MIME = "application/octet-stream";

    private DownloadFileTypes() {}

    static String resolveName(String url, String contentDisposition, String requestedMime,
                              String serverMime, String preferredName) {
        String preferred = sanitize(preferredName);
        String responseMime = bestMime("", requestedMime, serverMime);
        String guessed = sanitize(URLUtil.guessFileName(value(url), value(contentDisposition), responseMime));
        String name = preferred;
        if (name.length() == 0 || (isGenericName(name) && !isGenericName(guessed)) ||
                (value(contentDisposition).length() > 0 && hasSpecificExtension(guessed))) name = guessed;
        if (name.length() == 0) name = "download";
        String mime = bestMime(name, requestedMime, serverMime);
        String extension = extensionForMime(mime);
        if (extension.length() > 0 && (extensionOf(name).length() == 0 ||
                (isGenericName(name) && !BINARY_MIME.equals(mime)))) name = replaceExtension(name, extension);
        return sanitize(name.length() == 0 ? "download.bin" : name);
    }

    static String resolveMime(String filename, String requestedMime, String serverMime) {
        return bestMime(filename, requestedMime, serverMime);
    }

    static String mimeForOpen(ContentResolver resolver, Uri uri, String filename, String storedMime) {
        if (isApk(filename, storedMime)) return APK_MIME;
        String provider = "";
        try { provider = resolver == null || uri == null ? "" : value(resolver.getType(uri)); }
        catch (RuntimeException ignored) {}
        return bestMime(filename, storedMime, provider);
    }

    static boolean correctCompletedApk(File file, Metadata metadata) {
        if (!isApkArchive(file)) return false;
        metadata.mime = APK_MIME;
        metadata.filename = forceExtension(sanitize(metadata.filename), "apk");
        return true;
    }

    static boolean isApk(String filename, String mime) {
        return APK_MIME.equals(cleanMime(mime)) || "apk".equals(extensionOf(filename));
    }

    /** Generic WebView metadata needs one HTTP probe before choosing the final name and type. */
    static boolean needsResponseMetadata(String filename, String mime) {
        String extension = extensionOf(filename);
        String normalizedMime = cleanMime(mime);
        boolean genericExtension = extension.length() == 0 || "bin".equals(extension) ||
                "dat".equals(extension) || "download".equals(extension) || "tmp".equals(extension);
        return genericExtension && !isSpecificMime(normalizedMime);
    }

    private static boolean isApkArchive(File file) {
        if (file == null || !file.isFile() || file.length() < 64L) return false;
        ZipFile zip = null;
        try {
            zip = new ZipFile(file);
            return zip.getEntry("AndroidManifest.xml") != null &&
                    (zip.getEntry("classes.dex") != null || zip.getEntry("resources.arsc") != null);
        } catch (Exception ignored) { return false;
        } finally { NetworkSecurity.closeQuietly(zip); }
    }

    private static String bestMime(String filename, String requestedMime, String serverMime) {
        if ("apk".equals(extensionOf(filename))) return APK_MIME;
        String server = cleanMime(serverMime);
        if (isSpecificMime(server)) return server;
        String requested = cleanMime(requestedMime);
        if (isSpecificMime(requested)) return requested;
        String extension = extensionOf(filename);
        String mapped = extension.length() == 0 ? null : MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
        return mapped == null || mapped.length() == 0 ? BINARY_MIME : mapped;
    }

    private static boolean isSpecificMime(String mime) {
        return mime.length() > 0 && !BINARY_MIME.equals(mime) && !"binary/octet-stream".equals(mime) &&
                !mime.endsWith("/*") && !"application/download".equals(mime) &&
                !"application/x-download".equals(mime);
    }

    private static String extensionForMime(String mime) {
        if (APK_MIME.equals(mime)) return "apk";
        String extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime);
        return extension == null ? "" : extension.toLowerCase(Locale.US);
    }

    private static boolean hasSpecificExtension(String filename) {
        String extension = extensionOf(filename);
        return extension.length() > 0 && !"bin".equals(extension) && !"dat".equals(extension) &&
                !"download".equals(extension) && !"tmp".equals(extension);
    }

    private static boolean isGenericName(String filename) {
        String lower = value(filename).toLowerCase(Locale.US);
        String extension = extensionOf(lower);
        return lower.length() == 0 || "download".equals(lower) || "file".equals(lower) ||
                "bin".equals(extension) || "dat".equals(extension) || "download".equals(extension) ||
                "tmp".equals(extension);
    }

    private static String extensionOf(String filename) {
        String name = value(filename);
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        int dot = name.lastIndexOf('.');
        if (dot <= slash || dot + 1 >= name.length() || name.length() - dot > 12) return "";
        return name.substring(dot + 1).toLowerCase(Locale.US);
    }

    private static String replaceExtension(String filename, String extension) {
        String name = sanitize(filename);
        if (name.length() == 0) name = "download";
        int dot = name.lastIndexOf('.');
        if (dot > 0 && isGenericName(name)) name = name.substring(0, dot);
        else if (dot > 0 && extensionOf(name).equals(extension)) return name;
        return name + "." + extension;
    }

    private static String forceExtension(String filename, String extension) {
        String name = sanitize(filename);
        if (name.length() == 0) name = "download";
        int dot = name.lastIndexOf('.');
        if (dot > 0) name = name.substring(0, dot);
        return name + "." + extension;
    }

    static String sanitize(String raw) {
        String value = value(raw).trim();
        StringBuilder out = new StringBuilder(Math.min(value.length(), 180));
        for (int i = 0; i < value.length() && out.length() < 180; i++) {
            char c = value.charAt(i);
            boolean unsafeControl = c < 0x20 || (c >= 0x7f && c <= 0x9f);
            boolean bidiControl = (c >= '\u202a' && c <= '\u202e') || (c >= '\u2066' && c <= '\u2069');
            if (unsafeControl || bidiControl || "\\/:*?\"<>|".indexOf(c) >= 0) out.append('_'); else out.append(c);
        }
        while (out.length() > 0 && (out.charAt(0) == '.' || Character.isWhitespace(out.charAt(0)))) out.deleteCharAt(0);
        return out.toString().trim();
    }

    private static String cleanMime(String mime) {
        String value = value(mime).trim().toLowerCase(Locale.US);
        int separator = value.indexOf(';');
        return separator < 0 ? value : value.substring(0, separator).trim();
    }

    private static String value(String value) { return value == null ? "" : value; }

    static final class Metadata {
        String filename;
        String mime;
        Metadata(String filename, String mime) {
            this.filename = value(filename);
            this.mime = value(mime);
        }
    }
}
