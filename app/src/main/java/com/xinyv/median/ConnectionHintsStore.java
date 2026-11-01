package com.xinyv.median;

import java.net.InetAddress;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Bounded origin-only history of completed critical resources; never fetches or resolves hosts. */
final class ConnectionHintsStore {
    static final int MAX_SITES = 24;
    static final int MAX_HINTS = 4;
    static final int MAX_BYTES = 16 * 1024;
    static final long TTL_MS = 7L * 24L * 60L * 60L * 1000L;
    private static final String HEADER = "median-connections-2\n";
    private static final String LEGACY_HEADER = "median-connections-1\n";
    private final LinkedHashMap<String, Entry> entries = new LinkedHashMap<String, Entry>(32, 0.75f, true);

    /** successful is supplied by the DOM/resource observer; a completed HTTP error is not success. */
    static final class ResourceHint {
        final String url;
        final String initiatorType;
        final double responseEndMillis;
        final double durationMillis;
        final String contentType;
        final boolean successful;

        ResourceHint(String url, String initiatorType, double responseEndMillis, double durationMillis) {
            this(url, initiatorType, responseEndMillis, durationMillis, "", true);
        }

        ResourceHint(String url, String initiatorType, double responseEndMillis, double durationMillis,
                     String contentType, boolean successful) {
            this.url = url;
            this.initiatorType = initiatorType;
            this.responseEndMillis = responseEndMillis;
            this.durationMillis = durationMillis;
            this.contentType = contentType;
            this.successful = successful;
        }
    }

    private static final class Hint {
        final String origin;
        final long observedAt;
        final double duration;
        final boolean image;
        Hint(String origin, long observedAt, double duration, boolean image) {
            this.origin = origin;
            this.observedAt = observedAt;
            this.duration = duration;
            this.image = image;
        }
    }

    private static final class Entry {
        final List<Hint> hints;
        Entry(List<Hint> hints) { this.hints = new ArrayList<Hint>(hints); }
    }

    private static final Comparator<Hint> SLOWEST_FIRST = new Comparator<Hint>() {
        @Override public int compare(Hint left, Hint right) {
            return Double.compare(right.duration, left.duration);
        }
    };

    /** The caller observes only the committed document and filters the result again before warming. */
    synchronized boolean observe(String pageUrl, List<ResourceHint> resources, long nowMillis) {
        String page = origin(pageUrl);
        if (page.length() == 0 || nowMillis < 0L) return false;
        prune(nowMillis);
        LinkedHashMap<String, Hint> candidates = new LinkedHashMap<String, Hint>();
        Entry previous = entries.get(page);
        if (previous != null) for (Hint hint : previous.hints) candidates.put(hint.origin, hint);
        if (resources != null) {
            // The observer is bounded too; this cap prevents untrusted documents from making
            // one publication monopolize the UI thread even before origins are deduplicated.
            int count = Math.min(resources.size(), 256);
            for (int i = 0; i < count; i++) {
                ResourceHint resource = resources.get(i);
                if (!critical(resource)) continue;
                String dependency = origin(resource.url);
                if (!allowedDependency(page, dependency)) continue;
                Hint before = candidates.get(dependency);
                boolean image = "image".equalsIgnoreCase(resource.initiatorType);
                candidates.put(dependency, new Hint(dependency, nowMillis,
                        before == null ? resource.durationMillis : Math.max(before.duration, resource.durationMillis),
                        image && (before == null || before.image)));
            }
        }
        ArrayList<Hint> ranked = new ArrayList<Hint>(candidates.values());
        Collections.sort(ranked, SLOWEST_FIRST);
        ArrayList<Hint> hints = new ArrayList<Hint>(MAX_HINTS);
        // Reserve two slots per resource class before filling unused slots. Otherwise a
        // rotating set of slow images can evict the script needed to discover those images.
        for (int kind = 0; kind < 2; kind++) {
            int count = 0;
            for (Hint hint : ranked) if (hint.image == (kind == 1) && count < MAX_HINTS / 2) {
                hints.add(hint);
                count++;
            }
        }
        for (Hint hint : ranked) {
            if (hints.size() == MAX_HINTS) break;
            if (!hints.contains(hint)) hints.add(hint);
        }
        Collections.sort(hints, SLOWEST_FIRST);
        if (hints.isEmpty()) return false;
        // An empty/incomplete observation cannot prove that an old dependency is obsolete.
        // Each old origin keeps its own timestamp; revisiting the page never renews it.
        entries.put(page, new Entry(hints));
        enforceBounds();
        return true;
    }

    synchronized List<String> hintsFor(String navigationUrl, long nowMillis) {
        prune(nowMillis);
        Entry found = entries.get(origin(navigationUrl));
        if (found == null) return Collections.<String>emptyList();
        ArrayList<String> origins = new ArrayList<String>(found.hints.size());
        for (Hint hint : found.hints) origins.add(hint.origin);
        return Collections.unmodifiableList(origins);
    }

    synchronized void clear() { entries.clear(); }

    /** ASCII-only versioned lines keep preferences bounded without saving URL paths or queries. */
    synchronized String snapshot(long nowMillis) {
        prune(nowMillis);
        enforceBounds();
        StringBuilder output = new StringBuilder(HEADER);
        for (Map.Entry<String, Entry> item : entries.entrySet()) {
            output.append(item.getKey());
            for (Hint hint : item.getValue().hints) output.append('\t').append(hint.observedAt)
                    .append('\t').append(hint.image ? 'i' : 'c').append('\t')
                    .append(hint.duration).append('\t').append(hint.origin);
            output.append('\n');
        }
        return output.toString();
    }

    /** Invalid, oversized, expired, or non-origin records are discarded without DNS or I/O. */
    synchronized void restore(String stored, long nowMillis) {
        entries.clear();
        if (stored == null || stored.length() > MAX_BYTES || nowMillis < 0L)
            return;
        boolean legacy = stored.startsWith(LEGACY_HEADER);
        if (!legacy && !stored.startsWith(HEADER)) return;
        for (int i = 0; i < stored.length(); i++) if (stored.charAt(i) > 127) return;
        String[] lines = stored.substring(legacy ? LEGACY_HEADER.length() : HEADER.length()).split("\n", -1);
        if (lines.length > MAX_SITES + 1) return;
        for (String line : lines) {
            if (line.length() == 0) continue;
            String[] fields = line.split("\t", -1);
            if (legacy ? (fields.length < 3 || fields.length > MAX_HINTS + 2) :
                    (fields.length < 5 || fields.length > MAX_HINTS * 4 + 1 || (fields.length - 1) % 4 != 0)) continue;
            String rawPage = fields[legacy ? 1 : 0];
            String page = origin(rawPage);
            if (!page.equals(rawPage) || page.length() == 0) continue;
            ArrayList<Hint> hints = new ArrayList<Hint>(MAX_HINTS);
            ArrayList<String> seen = new ArrayList<String>(MAX_HINTS);
            boolean valid = true;
            for (int i = legacy ? 2 : 1; i < fields.length; i += legacy ? 1 : 4) {
                long observed;
                double duration;
                String kind = legacy ? "c" : fields[i + 1];
                try {
                    observed = Long.parseLong(fields[legacy ? 0 : i]);
                    duration = legacy ? 0 : Double.parseDouble(fields[i + 2]);
                } catch (NumberFormatException ignored) { valid = false; break; }
                String rawDependency = fields[legacy ? i : i + 3];
                String dependency = origin(rawDependency);
                if (!dependency.equals(rawDependency) || !allowedDependency(page, dependency) ||
                        seen.contains(dependency) || !finite(duration) || duration < 0 ||
                        !("i".equals(kind) || "c".equals(kind))) {
                    valid = false;
                    break;
                }
                seen.add(dependency);
                if (fresh(observed, nowMillis)) hints.add(new Hint(dependency, observed, duration, "i".equals(kind)));
            }
            if (valid && !hints.isEmpty()) entries.put(page, new Entry(hints));
        }
        enforceBounds();
    }

    static String origin(String raw) {
        if (raw == null || raw.length() == 0 || raw.length() > 8192) return "";
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c <= 32 || c == 127 || c == '\\') return "";
        }
        try {
            URI uri = new URI(raw);
            String scheme = uri.getScheme();
            if (scheme == null) return "";
            scheme = scheme.toLowerCase(Locale.US);
            if (!"http".equals(scheme) && !"https".equals(scheme)) return "";
            String authority = uri.getRawAuthority();
            String host = uri.getHost();
            if (uri.isOpaque() || authority == null || host == null || uri.getRawUserInfo() != null ||
                    authority.indexOf('%') >= 0 || authority.endsWith(":")) return "";
            host = host.toLowerCase(Locale.US);
            boolean ipv6 = host.startsWith("[") && host.endsWith("]");
            if (ipv6) host = host.substring(1, host.length() - 1);
            if (host.endsWith(".")) host = host.substring(0, host.length() - 1);
            if (!publicHost(host, ipv6)) return "";
            int port = uri.getPort();
            if (port == 0 || port > 65535 || port < -1) return "";
            boolean defaultPort = port < 0 || ("https".equals(scheme) ? port == 443 : port == 80);
            return scheme + "://" + (ipv6 ? "[" + host + "]" : host) + (defaultPort ? "" : ":" + port);
        } catch (Exception ignored) { return ""; }
    }

    private static boolean critical(ResourceHint resource) {
        if (resource == null || !resource.successful || !finite(resource.responseEndMillis) ||
                resource.responseEndMillis <= 0 || !finite(resource.durationMillis) || resource.durationMillis < 0)
            return false;
        String type = resource.initiatorType == null ? "" : resource.initiatorType.toLowerCase(Locale.US);
        if ("script".equals(type) || "stylesheet".equals(type) || "font".equals(type)) return true;
        // Only the controlled DOM observer emits "image", after proving that this exact
        // URL decoded as a non-lazy, load-blocking image. Raw Resource Timing "img"
        // entries or image MIME types alone do not provide that evidence.
        if ("image".equals(type)) return true;
        String path;
        try { path = new URI(resource.url).getPath(); }
        catch (Exception ignored) { return false; }
        path = path == null ? "" : path.toLowerCase(Locale.US);
        if ("link".equals(type)) return path.endsWith(".css") ||
                (resource.contentType != null && resource.contentType.toLowerCase(Locale.US).split(";", 2)[0].trim().equals("text/css"));
        return "css".equals(type) && (path.endsWith(".woff") || path.endsWith(".woff2") ||
                path.endsWith(".ttf") || path.endsWith(".otf"));
    }

    private static boolean finite(double value) { return !Double.isNaN(value) && !Double.isInfinite(value); }

    private static boolean allowedDependency(String page, String dependency) {
        return dependency.length() > 0 && !page.equals(dependency) &&
                !(page.startsWith("https://") && dependency.startsWith("http://"));
    }

    private static boolean publicHost(String host, boolean ipv6) {
        if (host.length() == 0 || host.length() > 253) return false;
        if (ipv6) {
            // A colon-containing numeric literal cannot trigger a DNS lookup here.
            if (!host.matches("[0-9a-f:.]+")) return false;
            try { return publicAddress(InetAddress.getByName(host).getAddress()); }
            catch (Exception ignored) { return false; }
        }
        if (host.matches("[0-9.]+")) {
            String[] fields = host.split("\\.", -1);
            if (fields.length != 4) return false;
            byte[] bytes = new byte[4];
            for (int i = 0; i < 4; i++) {
                if (fields[i].length() == 0 || fields[i].length() > 3 ||
                        (fields[i].length() > 1 && fields[i].charAt(0) == '0')) return false;
                int value = Integer.parseInt(fields[i]);
                if (value > 255) return false;
                bytes[i] = (byte) value;
            }
            return publicAddress(bytes);
        }
        if (host.equals("localhost") || host.endsWith(".localhost") || host.endsWith(".local") ||
                host.endsWith(".internal") || host.equals("median.invalid") || host.endsWith(".median.invalid")) return false;
        String[] labels = host.split("\\.", -1);
        if (labels.length < 2) return false;
        // Reject alternate numeric IP spellings accepted by browser URL parsers.
        String last = labels[labels.length - 1];
        if (last.matches("[0-9]+") || last.matches("0x[0-9a-f]+")) return false;
        for (String label : labels) {
            if (label.length() == 0 || label.length() > 63 || label.charAt(0) == '-' ||
                    label.charAt(label.length() - 1) == '-' || !label.matches("[a-z0-9-]+")) return false;
        }
        return true;
    }

    private static boolean publicAddress(byte[] bytes) {
        if (bytes.length == 4) {
            int a = bytes[0] & 255, b = bytes[1] & 255;
            return !(a == 0 || a == 10 || a == 127 || a >= 224 || (a == 100 && b >= 64 && b <= 127) ||
                    (a == 169 && b == 254) || (a == 172 && b >= 16 && b <= 31) || (a == 192 && b == 168) ||
                    (a == 198 && (b == 18 || b == 19)));
        }
        if (bytes.length != 16) return false;
        // Warm only globally routed IPv6 unicast; this also excludes mapped/private IPv4,
        // loopback, ULA, link-local, multicast, and NAT64/private translation literals.
        return ((bytes[0] & 255) & 0xe0) == 0x20;
    }

    private static boolean fresh(long observed, long now) {
        return observed >= 0L && now >= observed && now - observed <= TTL_MS;
    }

    private void prune(long nowMillis) {
        Iterator<Entry> iterator = entries.values().iterator();
        while (iterator.hasNext()) {
            Entry entry = iterator.next();
            Iterator<Hint> hints = entry.hints.iterator();
            while (hints.hasNext()) if (!fresh(hints.next().observedAt, nowMillis)) hints.remove();
            if (entry.hints.isEmpty()) iterator.remove();
        }
    }

    private void enforceBounds() {
        while (entries.size() > MAX_SITES || serializedSize() > MAX_BYTES) {
            Iterator<String> iterator = entries.keySet().iterator();
            if (!iterator.hasNext()) break;
            iterator.next();
            iterator.remove();
        }
    }

    private int serializedSize() {
        int size = HEADER.length();
        for (Map.Entry<String, Entry> item : entries.entrySet()) {
            size += item.getKey().length() + 1;
            for (Hint hint : item.getValue().hints) size += Long.toString(hint.observedAt).length() +
                    Double.toString(hint.duration).length() + hint.origin.length() + 5;
        }
        return size;
    }
}
