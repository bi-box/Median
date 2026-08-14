package com.xinyv.median;

import java.io.ByteArrayInputStream;
import java.net.InetAddress;
import java.net.URL;

public final class NetworkSecuritySelfTest {
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    public static void main(String[] args) throws Exception {
        check(NetworkSecurity.sameOrigin(new URL("https://example.com/a"), new URL("https://example.com:443/b")), "default HTTPS port");
        check(!NetworkSecurity.sameOrigin(new URL("https://example.com"), new URL("http://example.com")), "scheme is part of origin");
        check(!NetworkSecurity.sameOrigin(new URL("https://example.com"), new URL("https://example.com:8443")), "port is part of origin");
        check(NetworkSecurity.resolveRedirect(new URL("https://example.com/a"), "/b", true).toString().equals("https://example.com/b"), "relative redirect");
        try {
            NetworkSecurity.resolveRedirect(new URL("https://example.com/a"), "http://example.com/b", false);
            throw new AssertionError("HTTPS downgrade accepted");
        } catch (Exception expected) { }
        try {
            NetworkSecurity.parseHttpUrl("https://user:pass@example.com/");
            throw new AssertionError("URL userinfo accepted");
        } catch (Exception expected) { }
        check(NetworkSecurity.validHeader("X-Median-Test", "ok"), "valid header rejected");
        check(!NetworkSecurity.validHeader("X-Test", "ok\r\nInjected: 1"), "CRLF header injection accepted");
        check(NetworkSecurity.isForbiddenRequestHeader("Sec-Fetch-Site"), "Sec-* header accepted");
        check(!NetworkSecurity.isObviouslyLocalHost("example.invalid"), "ordinary hostname marked obviously local");
        check(NetworkSecurity.isObviouslyLocalHost("localhost"), "localhost not recognized");
        try {
            NetworkSecurity.openPublicHttpsGetFollowingRedirects(new URL("https://localhost/"), 100, 100, null);
            throw new AssertionError("public-only fetch accepted localhost");
        } catch (Exception expected) { }
        check(NetworkSecurity.isLocalOrPrivateAddress(InetAddress.getByName("127.0.0.1")), "loopback not private");
        check(NetworkSecurity.isLocalOrPrivateAddress(InetAddress.getByName("10.0.0.1")), "RFC1918 not private");
        check(NetworkSecurity.isLocalOrPrivateAddress(InetAddress.getByName("169.254.1.1")), "link-local not private");
        check(!NetworkSecurity.isLocalOrPrivateAddress(InetAddress.getByName("8.8.8.8")), "public address marked private");
        check(NetworkSecurity.readBounded(new ByteArrayInputStream(new byte[] { 1, 2, 3 }), 3, "large").length == 3,
                "bounded reader truncated valid input");
        try {
            NetworkSecurity.readBounded(new ByteArrayInputStream(new byte[] { 1, 2, 3, 4 }), 3, "large");
            throw new AssertionError("bounded reader accepted oversized input");
        } catch (IllegalArgumentException expected) { }
        System.out.println("NetworkSecuritySelfTest passed");
    }
}
