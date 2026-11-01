package com.xinyv.median;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parses the Netscape bookmark format exported by Chrome, Firefox and Chromium forks. */
final class BookmarkHtmlParser {
    static final int MAX_BOOKMARKS = 1500;
    private static final Pattern TOKEN = Pattern.compile(
            "(?is)<dt\\s*>\\s*<a\\b([^>]*)>(.*?)</a\\s*>|" +
                    "<dt\\s*>\\s*<h3\\b[^>]*>(.*?)</h3\\s*>|</dl\\s*>");
    private static final Pattern HREF = Pattern.compile("(?is)\\bhref\\s*=\\s*(?:\\\"([^\\\"]*)\\\"|'([^']*)'|([^\\s>]+))");
    private static final Pattern TAG = Pattern.compile("(?is)<[^>]+>");

    static final class Item {
        final String title;
        final String url;
        final List<String> folders;

        Item(String title, String url, List<String> folders) {
            this.title = title;
            this.url = url;
            this.folders = folders;
        }
    }

    private BookmarkHtmlParser() {}

    static List<Item> parse(String raw) {
        if (raw == null || raw.length() == 0 || raw.length() > 4 * 1024 * 1024) return Collections.emptyList();
        ArrayList<Item> result = new ArrayList<Item>();
        ArrayList<String> folders = new ArrayList<String>();
        Matcher tokens = TOKEN.matcher(raw);
        while (tokens.find() && result.size() < MAX_BOOKMARKS) {
            String attributes = tokens.group(1);
            if (attributes != null) {
                Matcher href = HREF.matcher(attributes);
                if (!href.find()) continue;
                String url = clean(href.group(1) != null ? href.group(1) :
                        (href.group(2) != null ? href.group(2) : href.group(3)));
                if (!isWebUrl(url)) continue;
                String title = clean(TAG.matcher(tokens.group(2) == null ? "" : tokens.group(2)).replaceAll(" "));
                if (title.length() == 0) title = url;
                result.add(new Item(limit(title, 200), url,
                        new ArrayList<String>(folders)));
            } else if (tokens.group(3) != null) {
                String name = clean(TAG.matcher(tokens.group(3)).replaceAll(" "));
                if (name.length() > 0 && folders.size() < 32) folders.add(limit(name, 80));
            } else if (!folders.isEmpty()) {
                folders.remove(folders.size() - 1);
            }
        }
        return result;
    }

    private static boolean isWebUrl(String value) {
        String lower = value.toLowerCase(Locale.US);
        return (lower.startsWith("https://") || lower.startsWith("http://")) &&
                value.length() <= 8192 && value.indexOf('\u0000') < 0;
    }

    private static String clean(String value) {
        if (value == null) return "";
        String text = value.replace("\u0000", "").trim();
        text = text.replace("&amp;", "&").replace("&quot;", "\"")
                .replace("&#39;", "'").replace("&apos;", "'")
                .replace("&lt;", "<").replace("&gt;", ">");
        Matcher numeric = Pattern.compile("&#(x[0-9a-fA-F]+|[0-9]+);").matcher(text);
        StringBuffer out = new StringBuffer();
        while (numeric.find()) {
            try {
                int code = numeric.group(1).toLowerCase(Locale.US).startsWith("x") ?
                        Integer.parseInt(numeric.group(1).substring(1), 16) :
                        Integer.parseInt(numeric.group(1));
                numeric.appendReplacement(out, Matcher.quoteReplacement(new String(Character.toChars(code))));
            } catch (RuntimeException ignored) { numeric.appendReplacement(out, ""); }
        }
        numeric.appendTail(out);
        return out.toString().replaceAll("\\s+", " ").trim();
    }

    private static String limit(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }
}
