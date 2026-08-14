# Validation — Median Browser 2.1.11

Validation date: 2026-08-10

## Verified

- `applicationId` remains `com.xinyv.median.compat`.
- `versionCode` is 75 and `versionName` is `2.1.11`.
- Java syntax sanity passed for all 53 Java files.
- Android XML parsing passed.
- The focused Java self-tests, generated home-page JavaScript syntax check, R8 release shrink, APK ZIP integrity, zip alignment, and APK signature verification passed.
- The generated userscript JavaScript passed Node syntax validation after the script store moved to coalesced background persistence.
- Async WebView V1/V2 boundary methods and their reflection member names survived R8 release shrinking.
- Normal and private-process WebView startup both use the async boundary with an idempotent three-second fallback; private navigation waits for WebView, filter rules, and profile clearing.
- Download/offline indexes are warmed on the idle startup executor, and page-analysis callbacks are guarded by WebView identity plus navigation sequence.
- Settings use a 9-entry task-oriented root; website settings use 6 grouped entries; predictive and traditional Back share the same layered exit path.
- Navigation, script/network, password-vault, and filter-subscription executors are bounded and cannot execute rejected work on the UI caller.
- Normal browsing no longer holds a Wi-Fi performance lock; the removed permission is absent from the packaged manifest.
- Launcher artwork is pure Android vector code, contains no letter or terminal mark, and includes Android 13 monochrome resources.

## APK/signing status

A signed 2.1.11 APK and the matching stable signing backup are included with this delivery. Android requires an update to use both the same package name and the same signing identity.

The APK certificate SHA-256 is:

`80daf48c091d6174981c2a176360b42ac32463d23ddc9e5f3c95c0951e5e3da9`

The recovered PKCS12 private key matches this fingerprint exactly. The previous 2.1.10 APK and the new 2.1.11 APK were independently verified with the same certificate, while the versionCode increases from 74 to 75. An installed build on that signing line can therefore update in place. The verified 2.1.11 APK SHA-256 is `20cd93bca6bfd492c4cf452401215710ba8f225bf455c5a28ed1407d1b767da2` and its exact size is 226,374 bytes.

No emulator or physical Android device was attached to this workspace. UI instrumentation, OEM WebView callback behavior, startup P50/P95, PSS, frame timing, battery, ANR and Via A/B measurements remain device-matrix release gates rather than claimed results.
