package com.xinyv.median;

public final class CredentialAutofillSelfTest {
    public static void main(String[] args) {
        String detect = CredentialAutofill.detectScript();
        String fill = CredentialAutofill.fillScript("a\"b@example.com", "line1\nline2");
        String overwrite = CredentialAutofill.fillScript("chosen@example.com", "chosen-secret", true);
        String capture = CredentialAutofill.captureScript("0123456789abcdef0123456789abcdef");
        String fields = CredentialAutofill.captureFieldsScript();
        for (String marker : new String[] { "new-password", "one-time-code", "shadowRoot", "IFRAME", "usernameOnly" })
            require(detect.contains(marker), "detection marker: " + marker);
        for (String marker : new String[] { "Object.getOwnPropertyDescriptor", "InputEvent", "dispatchEvent", "String(e.value||'').length" })
            require(fill.contains(marker), "fill marker: " + marker);
        require(!capture.contains("prompt"), "page credential flow must not use window.prompt");
        require(!capture.contains("__MEDIAN_CREDENTIAL__"), "page credential capture marker must be removed");
        require(!capture.contains("__MEDIAN_AUTOFILL__"), "page autofill marker must be removed");
        require(!capture.contains("0123456789abcdef0123456789abcdef"), "credential token must not enter page script");
        for (String marker : new String[] { "badHint", "one-time-code", "loginContext", "JSON.stringify([u&&u.value" })
            require(fields.contains(marker), "field probe marker: " + marker);
        if (args.length == 0) {
            System.out.println("CredentialAutofillSelfTest passed");
        } else if ("detect".equals(args[0])) {
            System.out.print(detect);
        } else if ("fill".equals(args[0])) {
            System.out.print(fill);
        } else if ("fill-overwrite".equals(args[0])) {
            System.out.print(overwrite);
        } else if ("capture".equals(args[0])) {
            System.out.print(capture);
        } else if ("fields".equals(args[0])) {
            System.out.print(fields);
        } else {
            throw new IllegalArgumentException("unknown script");
        }
    }

    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
