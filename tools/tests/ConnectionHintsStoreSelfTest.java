package com.xinyv.median;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class ConnectionHintsStoreSelfTest {
    private static int assertions;
    private static final long NOW = 1_800_000_000_000L;

    public static void main(String[] args) {
        criticalOriginsAndPriority();
        observedImages();
        stableLearning();
        selectionAndExpiryBoundaries();
        unsafeInput();
        originCanonicalization();
        persistenceAndExpiry();
        boundedHistory();
        System.out.println("ConnectionHintsStoreSelfTest passed: " + assertions + " assertions");
    }

    private static ConnectionHintsStore.ResourceHint resource(String url, String type, double duration) {
        return new ConnectionHintsStore.ResourceHint(url, type, duration + 1, duration);
    }

    private static void criticalOriginsAndPriority() {
        ConnectionHintsStore store = new ConnectionHintsStore();
        List<ConnectionHintsStore.ResourceHint> resources = Arrays.asList(
                resource("https://page.example/main.js", "script", 500),
                resource("https://one.example/a.js?secret=1", "script", 1),
                resource("https://one.example/slower.js", "script", 12),
                resource("https://two.example/app.css?version=2", "link", 9),
                resource("https://three.example/web.woff2", "css", 18),
                resource("https://four.example/dynamic", "stylesheet", 30),
                resource("https://five.example/dynamic", "font", 15),
                resource("https://six.example/preload", "link", 300),
                resource("https://image.example/a.png", "img", 500),
                resource("https://api.example/data", "fetch", 500));
        expect(store.observe("https://page.example/path?private=query", resources, NOW), "valid observation stored");
        expect(store.hintsFor("https://page.example/another-path", NOW).equals(Arrays.asList(
                "https://four.example", "https://three.example", "https://five.example", "https://one.example")),
                "slowest four distinct critical dependency origins, skipping same origin");
        expect(store.hintsFor("http://page.example/", NOW).isEmpty(), "source origins do not cross schemes");
        expect(store.hintsFor("https://page.example:8443/", NOW).isEmpty(), "source origins do not cross ports");
        String saved = store.snapshot(NOW);
        expect(!saved.contains("secret") && !saved.contains("private") && !saved.contains("a.js") && !saved.contains("path"),
                "only origins are persisted");
        ConnectionHintsStore css = new ConnectionHintsStore();
        css.observe("https://page.example/", Arrays.asList(new ConnectionHintsStore.ResourceHint(
                "https://css.example/dynamic", "link", 2, 1, "text/css; charset=utf-8", true)), NOW);
        expect(css.hintsFor("https://page.example/", NOW).equals(Arrays.asList("https://css.example")),
                "CSS content type identifies extensionless stylesheet links");
        css.observe("https://page.example/", Collections.<ConnectionHintsStore.ResourceHint>emptyList(), NOW);
        expect(css.hintsFor("https://page.example/", NOW).equals(Arrays.asList("https://css.example")),
                "empty resource timing observation retains fresh dependencies");
    }

    private static void stableLearning() {
        ConnectionHintsStore store = new ConnectionHintsStore();
        String page = "https://page.example/";
        store.observe(page, Arrays.asList(resource("https://script.example/app.js", "script", 5000),
                resource("https://image.example/photo", "image", 4000)), NOW);
        store.observe(page, Arrays.asList(resource("https://script.example/app.js", "script", 1),
                resource("https://other.example/photo", "image", 2)), NOW + 100);
        expect(store.hintsFor(page, NOW + 100).equals(Arrays.asList("https://script.example",
                "https://image.example", "https://other.example")), "fast follow-up does not forget a slow script or an unseen image");
        String saved = store.snapshot(NOW + 100);
        ConnectionHintsStore restored = new ConnectionHintsStore();
        restored.restore(saved, NOW + 100);
        expect(restored.snapshot(NOW + 100).equals(saved), "per-origin metadata round trips");
        restored.observe(page, Collections.<ConnectionHintsStore.ResourceHint>emptyList(), NOW + 200);
        expect(!restored.hintsFor(page, NOW + ConnectionHintsStore.TTL_MS + 1).contains("https://image.example"),
                "page observations never renew unseen origin TTL");
        expect(restored.hintsFor(page, NOW + ConnectionHintsStore.TTL_MS + 1).equals(Arrays.asList(
                "https://script.example", "https://other.example")), "only observed dependencies renew independently");
        expect(restored.hintsFor(page, NOW + ConnectionHintsStore.TTL_MS + 101).isEmpty(), "all dependencies eventually expire");
        store.clear();
        store.observe(page, Arrays.asList(resource("https://s1.example/app", "script", 2),
                resource("https://s2.example/app", "stylesheet", 1),
                resource("https://i1.example/photo", "image", 5000),
                resource("https://i2.example/photo", "image", 4000),
                resource("https://i3.example/photo", "image", 3000),
                resource("https://i4.example/photo", "image", 2000)), NOW);
        expect(store.hintsFor(page, NOW).equals(Arrays.asList("https://i1.example",
                "https://i2.example", "https://s1.example", "https://s2.example")), "rotating images cannot evict both critical script slots");
        store.observe(page, Arrays.asList(resource("https://s1.example/photo", "image", 9000)), NOW + 1);
        expect(store.hintsFor(page, NOW + 1).contains("https://s2.example"), "mixed-use script origin keeps critical classification");
        String header = "median-connections-2\n";
        String[] malformed = {
                header + "https://page.example\t" + NOW + "\ti\tNaN\thttps://cdn.example\n",
                header + "https://page.example\t" + NOW + "\tx\t1.0\thttps://cdn.example\n",
                header + "https://page.example\t" + NOW + "\ti\t-1\thttps://cdn.example\n",
                header + "https://page.example\t" + NOW + "\ti\t1\thttps://cdn.example/path\n"
        };
        for (String raw : malformed) {
            restored.restore(raw, NOW);
            expect(restored.hintsFor(page, NOW).isEmpty(), "invalid v2 metadata rejected");
        }
        restored.restore("median-connections-1\n" + NOW + "\thttps://page.example\thttps://cdn.example\n", NOW);
        expect(restored.hintsFor(page, NOW).equals(Arrays.asList("https://cdn.example")) &&
                restored.snapshot(NOW).startsWith(header), "legacy origins migrate without losing expiry");
    }

    private static void selectionAndExpiryBoundaries() {
        String page = "https://page.example/";
        ConnectionHintsStore store = new ConnectionHintsStore();
        store.observe(page, Arrays.asList(
                resource("https://i1.example/a", "image", 90),
                resource("https://i2.example/a", "image", 80),
                resource("https://i3.example/a", "image", 70),
                resource("https://i4.example/a", "image", 60),
                resource("https://i5.example/a", "image", 50)), NOW);
        expect(store.hintsFor(page, NOW).equals(Arrays.asList(
                "https://i1.example", "https://i2.example", "https://i3.example", "https://i4.example")),
                "one resource class may fill all unused slots");
        store.clear();
        store.observe(page, Arrays.asList(
                resource("https://s1.example/a", "script", 1),
                resource("https://i1.example/a", "image", 90),
                resource("https://i2.example/a", "image", 80),
                resource("https://i3.example/a", "image", 70),
                resource("https://i4.example/a", "image", 60)), NOW);
        expect(store.hintsFor(page, NOW).equals(Arrays.asList(
                "https://i1.example", "https://i2.example", "https://i3.example", "https://s1.example")),
                "one critical origin leaves three image slots");
        store.clear();
        store.observe(page, Arrays.asList(
                resource("https://mixed.example/a", "image", 100),
                resource("https://mixed.example/b", "script", 1),
                resource("https://s2.example/a", "font", 2),
                resource("https://s3.example/a", "stylesheet", 3),
                resource("https://i1.example/a", "image", 90),
                resource("https://i2.example/a", "image", 80),
                resource("https://i3.example/a", "image", 70)), NOW);
        expect(store.hintsFor(page, NOW).equals(Arrays.asList(
                "https://mixed.example", "https://i1.example", "https://i2.example", "https://s3.example")),
                "mixed-use origin occupies one critical slot and preserves two image slots");
        String saved = store.snapshot(NOW);
        ConnectionHintsStore restored = new ConnectionHintsStore();
        restored.restore(saved, NOW);
        expect(restored.hintsFor(page, NOW).equals(store.hintsFor(page, NOW)),
                "category selection and order survive persistence");
        store.clear();
        store.observe(page, Arrays.asList(resource("https://old.example/a", "image", 5000)), NOW);
        store.observe(page, Arrays.asList(resource("https://new.example/a", "script", 1)),
                NOW + ConnectionHintsStore.TTL_MS);
        expect(store.hintsFor(page, NOW + ConnectionHintsStore.TTL_MS).size() == 2,
                "exact boundary old origin remains beside newly observed origin");
        expect(store.hintsFor(page, NOW + ConnectionHintsStore.TTL_MS + 1).equals(
                Arrays.asList("https://new.example")), "expired unseen origin is pruned independently");
        expect(store.observe(page, null, NOW + 2 * ConnectionHintsStore.TTL_MS + 1) == false &&
                store.hintsFor(page, NOW + 2 * ConnectionHintsStore.TTL_MS + 1).isEmpty(),
                "empty observation still prunes expired history without creating an entry");
        restored.restore("median-connections-2\nhttps://page.example\t" + (NOW - ConnectionHintsStore.TTL_MS - 1) +
                "\ti\t99\thttps://old.example\t" + NOW + "\tc\t1\thttps://new.example\n", NOW);
        expect(restored.hintsFor(page, NOW).equals(Arrays.asList("https://new.example")),
                "one expired record does not invalidate another fresh record on restore");
        restored.restore("median-connections-1\n" + (NOW - ConnectionHintsStore.TTL_MS) +
                "\thttps://page.example\thttps://legacy.example\n", NOW);
        expect(restored.hintsFor(page, NOW).equals(Arrays.asList("https://legacy.example")) &&
                restored.hintsFor(page, NOW + 1).isEmpty(), "v1 migration never renews a boundary-age origin");
    }

    private static void unsafeInput() {
        String[] invalid = {
                "file:///etc/passwd", "javascript:alert(1)", "data:text/html,hello", "//cdn.example/asset.js",
                "https://user@cdn.example/a.js", "https://user:password@cdn.example/a.js", "https://@cdn.example/a.js",
                "https://localhost/a.js", "https://foo.localhost/a.js", "https://foo.local/a.js",
                "https://median.invalid/a.js", "https://x.median.invalid/a.js", "https://printer/a.js",
                "https://127.0.0.1/a.js", "https://127.1/a.js", "https://2130706433/a.js", "https://0x7f000001/a.js",
                "https://0177.0.0.1/a.js", "https://10.1.2.3/a.js", "https://172.31.1.2/a.js", "https://192.168.1.1/a.js",
                "https://100.100.1.1/a.js", "https://169.254.1.1/a.js", "https://0.0.0.0/a.js", "https://224.1.1.1/a.js",
                "https://[::1]/a.js", "https://[::ffff:127.0.0.1]/a.js", "https://[fc00::1]/a.js", "https://[fe80::1]/a.js",
                "https://[ff02::1]/a.js", "https://[fe80::1%25wlan0]/a.js", "https://cdn.example:0/a.js",
                "https://cdn.example:65536/a.js", "https://cdn.example:/a.js", "https://cdn.example\\@localhost/a.js",
                " https://cdn.example/a.js", "https://cdn.example/a\n.js", "https://cdn%2eexample/a.js"
        };
        for (String value : invalid) expect(ConnectionHintsStore.origin(value).isEmpty(), "reject unsafe origin: " + value);
        ConnectionHintsStore store = new ConnectionHintsStore();
        ArrayList<ConnectionHintsStore.ResourceHint> resources = new ArrayList<ConnectionHintsStore.ResourceHint>();
        for (String value : invalid) resources.add(resource(value, "script", 100));
        resources.add(resource("http://insecure.example/a.js", "script", 100));
        resources.add(new ConnectionHintsStore.ResourceHint("https://failed.example/a.js", "script", 1, 1, "", false));
        resources.add(new ConnectionHintsStore.ResourceHint("https://pending.example/a.js", "script", 0, 1));
        resources.add(new ConnectionHintsStore.ResourceHint("https://nan.example/a.js", "script", Double.NaN, 1));
        resources.add(new ConnectionHintsStore.ResourceHint("https://inf.example/a.js", "script", 1, Double.POSITIVE_INFINITY));
        resources.add(resource("https://preload.example/a.js", "link", 100));
        resources.add(resource("https://style-image.example/a.png", "css", 100));
        store.observe("https://page.example/", resources, NOW);
        expect(store.hintsFor("https://page.example/", NOW).isEmpty(), "reject failed, pending, noncritical, private, and downgraded dependencies");
        store.observe("http://page.example/", Arrays.asList(resource("https://secure.example/a.js", "script", 5)), NOW);
        expect(store.hintsFor("http://page.example/", NOW).equals(Arrays.asList("https://secure.example")), "HTTP page may warm HTTPS dependency");
    }

    private static void observedImages() {
        ConnectionHintsStore store = new ConnectionHintsStore();
        store.observe("https://page.example/private?session=secret", Arrays.asList(
                resource("https://images.example/opaque?signature=private", "image", 90),
                resource("https://images.example/another?signature=secret", "image", 5),
                resource("https://scripts.example/app.js", "script", 30),
                resource("https://raw-image.example/file.png", "img", 1000),
                new ConnectionHintsStore.ResourceHint("https://mime-image.example/opaque", "fetch", 1001, 1000, "image/png", true),
                new ConnectionHintsStore.ResourceHint("https://failed-image.example/opaque", "image", 101, 100, "", false),
                new ConnectionHintsStore.ResourceHint("https://pending-image.example/opaque", "image", 0, 100),
                resource("http://downgrade-image.example/opaque", "image", 100),
                resource("https://page.example/other-image", "image", 100),
                resource("https://user:secret@credential-image.example/opaque", "image", 100),
                resource("https://192.168.1.1/opaque", "image", 100)), NOW);
        expect(store.hintsFor("https://page.example/", NOW).equals(Arrays.asList(
                "https://images.example", "https://scripts.example")),
                "only observer-proven images join critical origins, ranked by maximum duration");
        String snapshot = store.snapshot(NOW);
        expect(!snapshot.contains("opaque") && !snapshot.contains("signature") &&
                !snapshot.contains("private") && !snapshot.contains("secret"),
                "image paths, credentials, and query values are not persisted");
        ConnectionHintsStore restored = new ConnectionHintsStore();
        restored.restore(snapshot, NOW + 1);
        expect(restored.hintsFor("https://page.example/", NOW + 1).equals(store.hintsFor("https://page.example/", NOW)),
                "image origin hints use the existing persistence format");
        restored.hintsFor("https://page.example/", NOW + ConnectionHintsStore.TTL_MS);
        expect(restored.hintsFor("https://page.example/", NOW + ConnectionHintsStore.TTL_MS + 1).isEmpty(),
                "image learning does not prolong TTL when read");
    }

    private static void originCanonicalization() {
        expect(ConnectionHintsStore.origin("HTTPS://CDN.Example.:443/a?q=x#fragment").equals("https://cdn.example"),
                "canonical lowercase origin removes default port and trailing DNS dot");
        expect(ConnectionHintsStore.origin("http://cdn.example:80/a").equals("http://cdn.example"), "HTTP default port removed");
        expect(ConnectionHintsStore.origin("https://cdn.example:8443/a").equals("https://cdn.example:8443"), "nondefault port retained");
        expect(ConnectionHintsStore.origin("https://8.8.8.8/a").equals("https://8.8.8.8"), "public IPv4 literal accepted");
        expect(ConnectionHintsStore.origin("https://[2606:4700:4700::1111]/a").equals("https://[2606:4700:4700::1111]"), "public IPv6 literal accepted without DNS");
    }

    private static void persistenceAndExpiry() {
        ConnectionHintsStore store = new ConnectionHintsStore();
        store.observe("https://page.example/", Arrays.asList(resource("https://cdn.example/a.js", "script", 10)), NOW);
        String encoded = store.snapshot(NOW);
        ConnectionHintsStore restored = new ConnectionHintsStore();
        restored.restore(encoded, NOW + 10);
        expect(restored.hintsFor("https://page.example/", NOW + 10).equals(Arrays.asList("https://cdn.example")), "persistence round trip");
        expect(!restored.hintsFor("https://page.example/", NOW + ConnectionHintsStore.TTL_MS).isEmpty(), "exact TTL boundary retained");
        expect(restored.hintsFor("https://page.example/", NOW + ConnectionHintsStore.TTL_MS + 1).isEmpty(), "read does not prolong observed TTL");
        restored.restore(encoded, NOW - 1);
        expect(restored.hintsFor("https://page.example/", NOW).isEmpty(), "future observation discarded after wall clock rollback");
        String header = "median-connections-1\n";
        String[] bad = {
                "unknown-version\n", header + NOW + "\thttps://page.example/private\thttps://cdn.example\n",
                header + NOW + "\thttps://page.example\thttps://localhost\n",
                header + NOW + "\thttps://page.example\thttp://cdn.example\n",
                header + NOW + "\thttps://page.example\thttps://cdn.example/secret?q=1\n",
                header + NOW + "\thttps://page.example\thttps://cdn.example\thttps://cdn.example\n",
                header + NOW + "\thttps://page.example\thttps://café.example\n",
                header + "-1\thttps://page.example\thttps://cdn.example\n",
                String.join("", Collections.nCopies(ConnectionHintsStore.MAX_BYTES + 1, "x"))
        };
        for (String value : bad) {
            restored.restore(value, NOW);
            expect(restored.hintsFor("https://page.example/", NOW).isEmpty(), "malformed persistence discarded");
        }
        restored.restore(encoded, NOW);
        restored.clear();
        expect(restored.hintsFor("https://page.example/", NOW).isEmpty() && restored.snapshot(NOW).equals("median-connections-2\n"), "clear removes persisted history");
    }

    private static void boundedHistory() {
        ConnectionHintsStore store = new ConnectionHintsStore();
        for (int i = 0; i < 24; i++) store.observe("https://p" + i + ".example/",
                Arrays.asList(resource("https://cdn.example/a.js", "script", 1)), NOW);
        store.hintsFor("https://p0.example/", NOW);
        store.observe("https://p24.example/", Arrays.asList(resource("https://cdn.example/a.js", "script", 1)), NOW);
        expect(!store.hintsFor("https://p0.example/", NOW).isEmpty(), "recently used source retained");
        expect(store.hintsFor("https://p1.example/", NOW).isEmpty(), "least recently used source evicted at 24 entries");
        expect(store.snapshot(NOW).split("\n").length == 25, "header plus 24 source mappings only");
        String longLabel = String.join("", Collections.nCopies(60, "a"));
        for (int i = 0; i < 24; i++) {
            ArrayList<ConnectionHintsStore.ResourceHint> resources = new ArrayList<ConnectionHintsStore.ResourceHint>();
            for (int j = 0; j < 4; j++) resources.add(resource("https://d" + i + "-" + j + "." + longLabel + "." + longLabel + "." + longLabel + ".example/a.js", "script", j + 1));
            store.observe("https://p" + i + "." + longLabel + ".example/", resources, NOW);
        }
        String encoded = store.snapshot(NOW);
        expect(encoded.getBytes(StandardCharsets.UTF_8).length <= ConnectionHintsStore.MAX_BYTES, "long origins remain below 16 KiB persistence limit");
        ConnectionHintsStore restored = new ConnectionHintsStore();
        restored.restore(encoded, NOW);
        expect(restored.snapshot(NOW).equals(encoded), "byte-bounded snapshot restores without loss");
        ArrayList<ConnectionHintsStore.ResourceHint> many = new ArrayList<ConnectionHintsStore.ResourceHint>();
        for (int i = 0; i < 300; i++) many.add(resource("https://d" + i + ".example/a.js", "script", i + 1));
        store.observe("https://many.example/", many, NOW);
        expect(store.hintsFor("https://many.example/", NOW).equals(Arrays.asList("https://d255.example", "https://d254.example", "https://d253.example", "https://d252.example")), "untrusted observation work is capped at 256 entries");
    }

    private static void expect(boolean value, String message) {
        assertions++;
        if (!value) throw new AssertionError(message);
    }
}
