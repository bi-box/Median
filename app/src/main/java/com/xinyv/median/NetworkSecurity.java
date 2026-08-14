package com.xinyv.median;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.IDN;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;

/** Shared URL, redirect and request-header policy for native HTTP clients. */
final class NetworkSecurity {
    static final int MAX_REDIRECTS = 8;

    private NetworkSecurity() {}

    static byte[] readBounded(InputStream input, int maxBytes, String sizeError) throws Exception {
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream(Math.min(32768, maxBytes));
            byte[] buffer = new byte[16384];
            int total = 0;
            int read;
            while ((read = input.read(buffer)) != -1) {
                total += read;
                if (total > maxBytes) throw new IllegalArgumentException(sizeError);
                output.write(buffer, 0, read);
            }
            return output.toByteArray();
        } finally { input.close(); }
    }

    static void closeQuietly(Closeable closeable) {
        if (closeable != null) try { closeable.close(); } catch (Exception ignored) {}
    }

    static URL parseHttpUrl(String value) throws MalformedURLException {
        URL url = new URL(value == null ? "" : value.trim());
        String scheme = url.getProtocol().toLowerCase(Locale.US);
        if (!"http".equals(scheme) && !"https".equals(scheme)) {
            throw new MalformedURLException("Only HTTP(S) URLs are allowed");
        }
        if (url.getUserInfo() != null && url.getUserInfo().length() > 0) {
            throw new MalformedURLException("Credentials in URLs are not allowed");
        }
        if (normalizedHost(url).length() == 0) throw new MalformedURLException("URL host is missing");
        return url;
    }

    static URL parseHttpsUrl(String value) throws MalformedURLException {
        URL url = parseHttpUrl(value);
        if (!"https".equalsIgnoreCase(url.getProtocol())) throw new MalformedURLException("HTTPS is required");
        return url;
    }

    static URL resolveRedirect(URL current, String location, boolean httpsOnly) throws MalformedURLException {
        if (location == null || location.trim().length() == 0) throw new MalformedURLException("Redirect location is missing");
        URL next = parseHttpUrl(new URL(current, location.trim()).toString());
        if (httpsOnly && !"https".equalsIgnoreCase(next.getProtocol())) {
            throw new MalformedURLException("HTTPS redirect downgrade rejected");
        }
        if ("https".equalsIgnoreCase(current.getProtocol()) && "http".equalsIgnoreCase(next.getProtocol())) {
            throw new MalformedURLException("HTTPS redirect downgrade rejected");
        }
        return next;
    }

    static boolean isRedirect(int status) {
        return status == 301 || status == 302 || status == 303 || status == 307 || status == 308;
    }

    /** Opens a bounded GET request and follows redirects manually so every hop is revalidated. */
    static HttpURLConnection openGetFollowingRedirects(URL initial, boolean httpsOnly, int connectTimeout,
                                                       int readTimeout, Map<String, String> headers) throws Exception {
        return openGetFollowingRedirects(initial, httpsOnly, false, connectTimeout, readTimeout, headers);
    }

    /** HTTPS fetch for subscriptions and executable script assets; rejects local/private targets at every hop. */
    static HttpURLConnection openPublicHttpsGetFollowingRedirects(URL initial, int connectTimeout,
                                                                  int readTimeout, Map<String, String> headers) throws Exception {
        return openGetFollowingRedirects(initial, true, true, connectTimeout, readTimeout, headers);
    }

    /** Public HTTP(S) media fetch; credential headers are retained only on the original origin. */
    static HttpURLConnection openPublicGetFollowingRedirects(URL initial, int connectTimeout,
                                                             int readTimeout, Map<String, String> headers) throws Exception {
        return openGetFollowingRedirects(initial, false, true, connectTimeout, readTimeout, headers);
    }

    private static HttpURLConnection openGetFollowingRedirects(URL initial, boolean httpsOnly, boolean publicOnly,
                                                               int connectTimeout, int readTimeout,
                                                               Map<String, String> headers) throws Exception {
        URL current = httpsOnly ? parseHttpsUrl(initial.toString()) : parseHttpUrl(initial.toString());
        URL first = current;
        Map<String, String> safeHeaders = headers == null ? Collections.<String, String>emptyMap() : headers;
        for (int redirects = 0; redirects <= MAX_REDIRECTS; redirects++) {
            if (publicOnly) requirePublicHost(normalizedHost(current));
            HttpURLConnection connection = (HttpURLConnection) current.openConnection();
            connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(connectTimeout);
            connection.setReadTimeout(readTimeout);
            connection.setUseCaches(false);
            connection.setRequestMethod("GET");
            for (Map.Entry<String, String> header : safeHeaders.entrySet()) {
                boolean trustedCookie = publicOnly && "cookie".equalsIgnoreCase(header.getKey());
                if (!validHeader(header.getKey(), header.getValue()) ||
                        (isForbiddenRequestHeader(header.getKey()) && !trustedCookie)) continue;
                if (!sameOrigin(first, current) && isCredentialHeader(header.getKey())) continue;
                connection.setRequestProperty(header.getKey(), header.getValue());
            }
            int status = connection.getResponseCode();
            if (!isRedirect(status)) return connection;
            if (redirects == MAX_REDIRECTS) {
                connection.disconnect();
                throw new MalformedURLException("Too many redirects");
            }
            String location = connection.getHeaderField("Location");
            URL next = resolveRedirect(current, location, httpsOnly);
            connection.disconnect();
            current = next;
        }
        throw new MalformedURLException("Too many redirects");
    }

    static boolean sameOrigin(URL left, URL right) {
        if (left == null || right == null) return false;
        return left.getProtocol().equalsIgnoreCase(right.getProtocol()) &&
                effectivePort(left) == effectivePort(right) &&
                normalizedHost(left).equals(normalizedHost(right));
    }

    static boolean sameHost(URL left, URL right) {
        return left != null && right != null && normalizedHost(left).equals(normalizedHost(right));
    }

    static int effectivePort(URL url) {
        if (url.getPort() >= 0) return url.getPort();
        return "https".equalsIgnoreCase(url.getProtocol()) ? 443 : 80;
    }

    static String normalizedHost(URL url) {
        String host = url == null ? "" : url.getHost();
        if (host == null) return "";
        host = host.trim().toLowerCase(Locale.US);
        while (host.endsWith(".")) host = host.substring(0, host.length() - 1);
        try { return IDN.toASCII(host, IDN.USE_STD3_ASCII_RULES).toLowerCase(Locale.US); }
        catch (IllegalArgumentException ignored) { return ""; }
    }

    static boolean isForbiddenRequestHeader(String name) {
        String lower = name == null ? "" : name.trim().toLowerCase(Locale.US);
        return lower.length() == 0 || lower.equals("host") || lower.equals("content-length") ||
                lower.equals("connection") || lower.equals("cookie") || lower.equals("cookie2") ||
                lower.equals("range") || lower.equals("accept-encoding") || lower.equals("transfer-encoding") ||
                lower.equals("proxy-authorization") || lower.equals("proxy-connection") || lower.equals("upgrade") ||
                lower.startsWith("sec-");
    }

    static boolean isCredentialHeader(String name) {
        String lower = name == null ? "" : name.trim().toLowerCase(Locale.US);
        return lower.equals("authorization") || lower.equals("proxy-authorization") ||
                lower.equals("cookie") || lower.equals("cookie2");
    }

    static boolean validHeader(String name, String value) {
        if (name == null || value == null || name.length() == 0 || name.length() > 128 || value.length() > 4096) return false;
        if (name.indexOf('\r') >= 0 || name.indexOf('\n') >= 0 || value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0) return false;
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (c <= 32 || c >= 127 || "()<>@,;:\\\"/[]?={}".indexOf(c) >= 0) return false;
        }
        return true;
    }

    static boolean isObviouslyLocalHost(String host) {
        if (host == null) return false;
        String normalized = host.trim().toLowerCase(Locale.US);
        if (normalized.equals("localhost") || normalized.endsWith(".localhost") || normalized.endsWith(".local")) return true;
        boolean looksLiteral = normalized.indexOf(':') >= 0 || normalized.matches("[0-9.]+");
        if (!looksLiteral) return false; // Never perform DNS on the UI thread for ordinary hostnames.
        try {
            return isLocalOrPrivateAddress(InetAddress.getByName(normalized));
        } catch (Exception ignored) { return true; }
    }

    static boolean isLocalOrPrivateHost(String host) {
        if (host == null) return false;
        String normalized = host.trim().toLowerCase(Locale.US);
        if (isObviouslyLocalHost(normalized)) return true;
        try {
            InetAddress[] addresses = InetAddress.getAllByName(normalized);
            if (addresses.length == 0) return true;
            for (InetAddress address : addresses) if (isLocalOrPrivateAddress(address)) return true;
        } catch (Exception ignored) {
            return true; // Fail closed for privileged native requests when DNS cannot be validated.
        }
        return false;
    }

    /** Validates native executable downloads without misreporting a transient DNS failure as a private host. */
    private static void requirePublicHost(String host) throws Exception {
        String normalized = host == null ? "" : host.trim().toLowerCase(Locale.US);
        if (normalized.length() == 0) throw new MalformedURLException("URL host is missing");
        if (isObviouslyLocalHost(normalized))
            throw new MalformedURLException("Local/private network target rejected");
        InetAddress[] addresses = InetAddress.getAllByName(normalized);
        if (addresses.length == 0) throw new java.net.UnknownHostException(normalized);
        for (InetAddress address : addresses) if (isLocalOrPrivateAddress(address))
            throw new MalformedURLException("Local/private network target rejected");
    }

    static boolean isLocalOrPrivateAddress(InetAddress address) {
        if (address == null) return true;
        if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress() ||
                address.isSiteLocalAddress() || address.isMulticastAddress()) return true;
        byte[] bytes = address.getAddress();
        if (address instanceof Inet4Address && bytes.length == 4) {
            int a = bytes[0] & 0xff;
            int b = bytes[1] & 0xff;
            return a == 0 || a == 10 || a == 127 || (a == 169 && b == 254) ||
                    (a == 172 && b >= 16 && b <= 31) || (a == 192 && b == 168) ||
                    (a == 100 && b >= 64 && b <= 127) || a >= 224;
        }
        if (address instanceof Inet6Address && bytes.length == 16) {
            int first = bytes[0] & 0xff;
            int second = bytes[1] & 0xff;
            return (first & 0xfe) == 0xfc || (first == 0xfe && (second & 0xc0) == 0x80);
        }
        return false;
    }
}
