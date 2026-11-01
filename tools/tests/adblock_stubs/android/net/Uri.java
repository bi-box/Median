package android.net;

import java.net.URI;

/** Minimal Android URI surface for running the production request matcher on the JVM. */
public final class Uri {
    private final String raw;
    private final URI parsed;

    private Uri(String raw) {
        this.raw = raw;
        this.parsed = URI.create(raw);
    }

    public static Uri parse(String raw) { return new Uri(raw); }
    public String getScheme() { return parsed.getScheme(); }
    public String getHost() { return parsed.getHost(); }
    public String getEncodedPath() { return parsed.getRawPath(); }
    public String getEncodedQuery() { return parsed.getRawQuery(); }
    public String getPath() { return parsed.getPath(); }
    @Override public String toString() { return raw; }
}
