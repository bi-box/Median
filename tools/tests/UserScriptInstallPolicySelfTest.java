package com.xinyv.median;

import java.net.SocketTimeoutException;

import javax.net.ssl.SSLHandshakeException;

public final class UserScriptInstallPolicySelfTest {
    public static void main(String[] args) {
        require(UserScriptInstallPolicy.isSourceText("// ==UserScript==\n// @name test\n// ==/UserScript=="),
                "pasted userscript source was not recognized");
        require(!UserScriptInstallPolicy.isSourceText("alert(1)"), "ordinary JavaScript accepted as userscript");
        require(UserScriptInstallPolicy.looksLikeInstallUrl("https://example.com/a.user.js?x=1"),
                ".user.js link was not recognized");
        require(UserScriptInstallPolicy.looksLikeInstallUrl("https://update.greasyfork.org/scripts/1/a.js"),
                "Greasy Fork install link was not recognized");
        require(!UserScriptInstallPolicy.looksLikeInstallUrl("http://example.com/a.user.js"),
                "insecure install URL accepted");
        require(UserScriptInstallPolicy.retryableHttpStatus(429), "429 must retry");
        require(UserScriptInstallPolicy.retryableHttpStatus(503), "503 must retry");
        require(!UserScriptInstallPolicy.retryableHttpStatus(404), "404 must fail fast");
        require(UserScriptInstallPolicy.retryableFailure(new SocketTimeoutException("slow")),
                "timeout must retry");
        require(!UserScriptInstallPolicy.retryableFailure(new SSLHandshakeException("bad certificate")),
                "TLS certificate failure must not retry");
        require(UserScriptInstallPolicy.readTimeoutMs(1) > UserScriptInstallPolicy.readTimeoutMs(0),
                "second attempt must allow a slower server more time");
        System.out.println("UserScriptInstallPolicySelfTest passed");
    }

    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
