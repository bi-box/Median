package com.xinyv.median;

/** Safe mini-markup for multi-color and gradient text logos. No raw HTML is accepted. */
final class LogoMarkup {
    static final int MAX_CODE_LENGTH = 1024;
    static final int MAX_VISIBLE_CODE_POINTS = 48;
    static final String GOOGLE_CODE =
            "[color=#4285F4]G[/color][color=#EA4335]o[/color][color=#FBBC05]o[/color]" +
            "[color=#4285F4]g[/color][color=#34A853]l[/color][color=#EA4335]e[/color]";
    static final String GRADIENT_EXAMPLE =
            "[gradient=#8B5CF6,#6366F1,#22D3EE]Median[/gradient]";
    static final String LEGACY_GRADIENT_EXAMPLE =
            "[gradient=#8B5CF6,#6366F1,#22D3EE]我的主页[/gradient]";

    static final class Result {
        final String html;
        final String plainText;
        final String error;

        Result(String html, String plainText, String error) {
            this.html = html;
            this.plainText = plainText;
            this.error = error;
        }

        boolean valid() { return error == null; }
    }

    private LogoMarkup() {}

    static String gradientExample(String title) {
        String safe = HomePageConfig.cleanTitle(title).replace('[', '(').replace(']', ')');
        return "[gradient=#8B5CF6,#6366F1,#22D3EE]" + safe + "[/gradient]";
    }

    static Result parse(String raw) {
        return parse(raw, 90);
    }

    static Result parse(String raw, int gradientAngle) {
        if (raw != null && raw.length() > MAX_CODE_LENGTH) return error("Logo 代码不能超过 1024 个字符");
        String code = clean(raw);
        if (code.length() == 0) return error("Logo 代码不能为空");
        StringBuilder html = new StringBuilder(code.length() + 96);
        StringBuilder plain = new StringBuilder(code.length());
        int cursor = 0;
        while (cursor < code.length()) {
            int open = code.indexOf('[', cursor);
            if (open < 0) {
                appendText(code.substring(cursor), html, plain);
                break;
            }
            if (open > cursor) appendText(code.substring(cursor, open), html, plain);
            int headerEnd = code.indexOf(']', open + 1);
            if (headerEnd < 0) return error("标记缺少 ]");
            String header = code.substring(open + 1, headerEnd);
            if (header.startsWith("space=")) {
                int width;
                try { width = Integer.parseInt(header.substring(6)); }
                catch (NumberFormatException invalid) { return error("间隔必须是 0–24 的整数"); }
                if (width < 0 || width > 24) return error("间隔必须是 0–24 的整数");
                html.append("<span class='logo-space' style='width:").append(width).append("px'></span>");
                if (width > 0) plain.append(' ');
                cursor = headerEnd + 1;
            } else if (header.startsWith("color=")) {
                String color = header.substring(6).toUpperCase(java.util.Locale.US);
                if (!validColor(color)) return error("颜色必须写成 #RRGGBB");
                int close = code.indexOf("[/color]", headerEnd + 1);
                if (close < 0) return error("颜色标记缺少 [/color]");
                String text = code.substring(headerEnd + 1, close);
                if (text.indexOf('[') >= 0) return error("颜色标记不能嵌套");
                html.append("<span style='color:").append(color).append("'>").append(escape(text)).append("</span>");
                plain.append(text);
                cursor = close + 8;
            } else if (header.startsWith("gradient=")) {
                String colors = gradientColors(header.substring(9));
                if (colors == null) return error("渐变需要 2–4 个 #RRGGBB 颜色，用英文逗号分隔");
                int close = code.indexOf("[/gradient]", headerEnd + 1);
                if (close < 0) return error("渐变标记缺少 [/gradient]");
                String text = code.substring(headerEnd + 1, close);
                if (text.indexOf('[') >= 0) return error("渐变标记不能嵌套");
                html.append("<span class='logo-gradient' style='--logo-gradient:linear-gradient(")
                        .append(safeAngle(gradientAngle)).append("deg,")
                        .append(colors).append(")'>").append(escape(text)).append("</span>");
                plain.append(text);
                cursor = close + 11;
            } else {
                return error("只支持 [color]、[gradient] 和 [space] 标记");
            }
            if (plain.codePointCount(0, plain.length()) > MAX_VISIBLE_CODE_POINTS)
                return error("Logo 文字不能超过 48 个字符");
        }
        if (plain.codePointCount(0, plain.length()) > MAX_VISIBLE_CODE_POINTS)
            return error("Logo 文字不能超过 48 个字符");
        String visible = plain.toString().trim();
        if (visible.length() == 0) return error("Logo 必须包含可见文字");
        return new Result(html.toString(), visible, null);
    }

    static String renderPreset(String style, String title, String customCode, int gradientAngle) {
        String safeTitle = HomePageConfig.cleanTitle(title);
        if ("google".equals(style)) return parse(GOOGLE_CODE).html;
        if ("aurora".equals(style)) return gradient(safeTitle, "#8B5CF6,#6366F1,#22D3EE", gradientAngle);
        if ("sunset".equals(style)) return gradient(safeTitle, "#F43F5E,#FB7185,#F59E0B", gradientAngle);
        if ("ocean".equals(style)) return gradient(safeTitle, "#06B6D4,#3B82F6,#4F46E5", gradientAngle);
        if ("rose_gold".equals(style)) return gradient(safeTitle, "#B76E79,#DFA6A0,#F4D4C4", gradientAngle);
        if ("custom".equals(style)) {
            Result custom = parse(customCode, gradientAngle);
            if (custom.valid()) return custom.html;
        }
        if (HomePageConfig.DEFAULT_TITLE.equals(safeTitle))
            return "med<span style='color:#D6A23A'>i</span>an";
        return escape(safeTitle);
    }

    static String renderPreset(String style, String title, String customCode) {
        return renderPreset(style, title, customCode, 90);
    }

    static String clean(String value) {
        return value == null ? "" : value.trim().replace('\n', ' ').replace('\r', ' ');
    }

    private static String gradient(String text, String colors, int angle) {
        return "<span class='logo-gradient' style='--logo-gradient:linear-gradient(" + safeAngle(angle) + "deg," + colors + ")'>" +
                escape(text) + "</span>";
    }

    private static int safeAngle(int value) { return Math.max(0, Math.min(360, value)); }

    private static String gradientColors(String raw) {
        String[] parts = raw.split(",", -1);
        if (parts.length < 2 || parts.length > 4) return null;
        StringBuilder result = new StringBuilder();
        for (String part : parts) {
            String color = part.trim().toUpperCase(java.util.Locale.US);
            if (!validColor(color)) return null;
            if (result.length() > 0) result.append(',');
            result.append(color);
        }
        return result.toString();
    }

    private static boolean validColor(String value) {
        if (value == null || value.length() != 7 || value.charAt(0) != '#') return false;
        for (int i = 1; i < value.length(); i++) {
            char c = value.charAt(i);
            if (!((c >= '0' && c <= '9') || (c >= 'A' && c <= 'F'))) return false;
        }
        return true;
    }

    private static void appendText(String text, StringBuilder html, StringBuilder plain) {
        html.append(escape(text));
        plain.append(text);
    }

    private static Result error(String message) { return new Result("", "", message); }

    private static String escape(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }
}
