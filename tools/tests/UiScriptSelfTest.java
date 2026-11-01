package com.xinyv.median;

/** Emits generated in-page tools so the shell verifier can run a real JavaScript parser. */
public final class UiScriptSelfTest {
    public static void main(String[] args) {
        String token = "0123456789abcdef0123456789abcdef";
        if (args.length > 0 && "ad-hide-picked".equals(args[0])) {
            String script = AdPickerScript.hide("#ad-slot > .banner:nth-of-type(2)", true);
            if (!script.contains("__medianAdSelection") || !script.contains("picked.nodes[i]"))
                throw new AssertionError("picked ad hide must verify original node identity");
            System.out.print(script);
            return;
        }
        if (args.length > 0 && "ad-validate-picked".equals(args[0])) {
            String script = AdPickerScript.validate("#ad-slot > .banner:nth-of-type(2)", true);
            if (!script.contains("__medianAdSelection") || !script.contains("picked.nodes[i]"))
                throw new AssertionError("picked ad validation must verify original node identity");
            System.out.print(script);
            return;
        }
        if (args.length > 0 && "ad-hide".equals(args[0])) {
            String script = AdPickerScript.hide("#ad-slot > .banner:nth-of-type(2)");
            if (!script.contains("__median_manual_blocks") || !script.contains("display:none"))
                throw new AssertionError("persistent ad hide payload incomplete");
            System.out.print(script);
            return;
        }
        if (args.length > 0 && "ad-validate".equals(args[0])) {
            String script = AdPickerScript.validate("#ad-slot > .banner:nth-of-type(2)");
            if (!script.contains("querySelectorAll") || !script.contains("return -1"))
                throw new AssertionError("ad selector validation payload incomplete");
            System.out.print(script);
            return;
        }
        if (args.length == 0 || "ad".equals(args[0])) {
            String script = AdPickerScript.build(token);
            if (!script.contains(token) || !script.contains("__medianAdPicker") ||
                    !script.contains("selector") || !script.contains("隐藏此元素") ||
                    !script.contains("手动输入")) throw new AssertionError("ad picker payload incomplete");
            System.out.print(script);
            return;
        }
        String html = QrScannerPage.html(token, false);
        if (!html.contains(token) || !html.contains("BarcodeDetector") ||
                !html.contains("getUserMedia")) throw new AssertionError("QR scanner payload incomplete");
        int start = html.indexOf("<script>");
        int end = html.lastIndexOf("</script>");
        if (start < 0 || end <= start) throw new AssertionError("QR scanner script missing");
        System.out.print(html.substring(start + 8, end));
    }
}
