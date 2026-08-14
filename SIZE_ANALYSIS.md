# APK size analysis

> 1.7.2–2.1.3 数值来自 2026-07-16 的正式签名构建；2.1.4–2.1.5 来自 2026-07-17。

## Measured packages

| Version | APK size | DEX raw size | DEX stored size | DEX method |
|---|---:|---:|---:|---|
| 1.0.0 | 156,204 B | 321,548 B | 141,991 B | Deflate |
| 1.3.2 | 440,963 B | 434,728 B | 434,728 B | Stored / uncompressed |
| 1.3.3 | 184,936 B | 411,696 B | 178,716 B | Deflate |
| 1.4.0 KB RC1 | 184,958 B | 350,400 B | 170,831 B | Deflate + R8 |
| 1.4.0 500 KB RC1 | 184,958 B | 352,776 B | approximately 170 KB | Deflate + R8 |
| 1.7.2 classic | 180,862 B | 324,344 B | 163,496 B | Deflate + R8 |
| 1.7.3 stable | 180,862 B | 326,360 B | 165,035 B | Deflate + R8 |
| 2.0.0 major | 184,958 B | 330,548 B | 167,001 B | Deflate + R8 |
| 2.0.1 omnibox fix | 184,958 B | 331,788 B | 167,709 B | Deflate + R8 |
| 2.0.2 bounded menu ripple | 184,958 B | 331,788 B | 167,711 B | Deflate + R8 |
| 2.1.0 personalized home | 193,150 B | 349,332 B | 177,171 B | Deflate + R8 |
| 2.1.1 safe text logos | 197,246 B | 354,980 B | 179,853 B | Deflate + R8 |
| 2.1.2 logo typography | 197,246 B | 356,852 B | 180,946 B | Deflate + R8 |
| 2.1.3 real download progress | 197,246 B | 359,224 B | 182,058 B | Deflate + R8 |
| 2.1.4 homepage strategies | 201,342 B | 368,284 B | 185,693 B | Deflate + R8 |
| 2.1.5 homepage customizer | 209,534 B | 379,552 B | 191,180 B | Deflate + R8 |

No README, source archive, native library, advertising SDK, analytics SDK, or
full AndroidX/Kotlin runtime is included in the APK.

## 500 KB compatibility strategy

The browser keeps the AndroidX WebKit public call shape used by Median:

- `androidx.webkit.WebViewFeature`
- `androidx.webkit.WebViewCompat`
- `androidx.webkit.ScriptHandler`

It vendors only the focused source needed for `DOCUMENT_START_SCRIPT:1` and the
Chromium support-library boundary protocol. Unrelated WebKit APIs, AndroidX
Core, Lifecycle, VersionedParcelable, Kotlin runtime, notification resources,
and compatibility widgets are not packaged.

The final build retains release-mode R8 optimization, minification, a single
compressed DEX, compact resources, sparse resource encoding, no native
libraries, and APK Signature Scheme v2/v3. `tools/build_500kb_apk.sh` rejects a
signed APK larger than 512,000 bytes.

The 2.1.5 APK uses the stable `com.xinyv.median.compat` update package and release
certificate. Google Play AAB artifacts still require separate Play Console validation.
