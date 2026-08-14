package com.xinyv.median;

public final class CredentialAutofillSelfTest {
    public static void main(String[] args) {
        String detect = CredentialAutofill.detectScript();
        String fill = CredentialAutofill.fillScript("a\"b@example.com", "line1\nline2");
        String capture = CredentialAutofill.captureScript("0123456789abcdef0123456789abcdef");
        for (String marker : new String[] { "new-password", "one-time-code", "shadowRoot", "IFRAME", "usernameOnly" })
            require(detect.contains(marker), "detection marker: " + marker);
        for (String marker : new String[] { "Object.getOwnPropertyDescriptor", "InputEvent", "dispatchEvent", "String(e.value||'').length" })
            require(fill.contains(marker), "fill marker: " + marker);
        for (String marker : new String[] { "isTrusted", "sessionStorage", "__MEDIAN_CREDENTIAL__", "__MEDIAN_AUTOFILL__", "typeof X==='object'" })
            require(capture.contains(marker), "capture marker: " + marker);
        require(!capture.contains(".submit("), "credential code must never submit a form");
        if (args.length == 0) {
            System.out.println("CredentialAutofillSelfTest passed");
        } else if ("detect".equals(args[0])) {
            System.out.print(detect);
        } else if ("fill".equals(args[0])) {
            System.out.print(fill);
        } else if ("capture".equals(args[0])) {
            System.out.print(capture);
        } else {
            throw new IllegalArgumentException("unknown script");
        }
    }

    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
