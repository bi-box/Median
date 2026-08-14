package com.xinyv.median;

import org.junit.Test;

import java.net.URL;

import static org.junit.Assert.*;

public class NetworkSecurityTest {
    @Test public void redirectRejectsHttpsDowngrade() throws Exception {
        URL start = new URL("https://example.com/a");
        try {
            NetworkSecurity.resolveRedirect(start, "http://example.com/b", false);
            fail("downgrade must be rejected");
        } catch (Exception expected) {
            assertTrue(expected.getMessage().toLowerCase().contains("downgrade"));
        }
    }

    @Test public void originIncludesSchemeAndPort() throws Exception {
        assertTrue(NetworkSecurity.sameOrigin(new URL("https://example.com/a"), new URL("https://example.com:443/b")));
        assertFalse(NetworkSecurity.sameOrigin(new URL("https://example.com"), new URL("http://example.com")));
        assertFalse(NetworkSecurity.sameOrigin(new URL("https://example.com"), new URL("https://example.com:8443")));
    }

    @Test public void obviousLocalCheckDoesNotNeedDnsForHostnames() {
        assertFalse(NetworkSecurity.isObviouslyLocalHost("example.invalid"));
        assertTrue(NetworkSecurity.isObviouslyLocalHost("localhost"));
        assertTrue(NetworkSecurity.isObviouslyLocalHost("127.0.0.1"));
    }

    @Test public void credentialsAndHeaderInjectionAreRejected() throws Exception {
        try {
            NetworkSecurity.parseHttpUrl("https://user:pass@example.com/");
            fail("userinfo must be rejected");
        } catch (Exception expected) {
            assertNotNull(expected);
        }
        assertFalse(NetworkSecurity.validHeader("X-Test", "ok\r\nInjected: 1"));
        assertTrue(NetworkSecurity.isForbiddenRequestHeader("Sec-Fetch-Site"));
    }
}
