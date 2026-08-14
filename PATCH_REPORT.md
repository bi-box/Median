# Median Browser 1.4.0 RC — Hardening report

Date: 2026-07-13

This report describes the changes made from the supplied `1.3.5-omni-preview` source. The result is a release candidate, not a claim that production stability or performance has already been proven.

## Release engineering

- Replaced the ad-hoc APK-only path with a Gradle Android application project.
- Set `minSdk 26`, `compileSdk 36`, `targetSdk 36`, `versionCode 39`, and `versionName 1.4.0`.
- Added debug APK, release APK, Android App Bundle, R8/resource shrinking, strict Lint, JUnit, and Android instrumentation configuration.
- Release signing is read only from `MEDIAN_KEYSTORE`, `MEDIAN_STOREPASS`, `MEDIAN_KEY_ALIAS`, and `MEDIAN_KEYPASS`; missing signing material fails the release build.
- Removed the previous weak development-certificate generation path.
- Added GitHub Actions for static checks, Lint, unit tests, APK/AAB builds, signed tag releases, checksums, and scheduled emulator tests.
- Added Dependabot, release-tag/version verification, adaptive launcher icons, backup exclusions, and publication documentation.

## Browser and WebView security

- Kept SSL failures fail-closed, mixed content blocked, Safe Browsing enabled, file access disabled, and normal content access disabled.
- External launch intents accept only validated HTTP(S) input at the exported activity boundary; `file:`, `content:`, `data:`, `javascript:` and malformed credential-bearing URLs are rejected.
- External application intents clear explicit component/package/selector, URI grants, and ClipData before launch.
- Camera, microphone, and geolocation grants are now tied to the exact current HTTPS origin, including scheme and effective port. Navigation while a system permission dialog is open invalidates the request.
- Offline MHTML temporarily enables only the required read-only app provider, disables JavaScript, and blocks network loads.
- Browser-data import now rejects malformed or credential-bearing HTTP(S) URLs.

## User-script security

- A script with no `@grant` receives no native capability. `@grant none` remains unprivileged.
- Grant matching is exact rather than substring based.
- High-privilege scripts require AndroidX WebKit document-start support. Unsupported WebView versions fail closed instead of falling back to a page-replaceable bridge.
- The native prompt capability and random token are captured before page JavaScript, kept in a closure, and checked against the WebView, current URL, enabled script, URL match rules, and declared API grant on every call.
- Native-capability scripts are restricted to the top frame.
- Internal pages and all non-HTTP(S) pages are excluded from user-script execution.
- Script menu callbacks no longer use a conventional page-visible global; the dispatcher is non-enumerable, immutable, randomly named, and token-gated.
- `GM_xmlhttpRequest` follows redirects manually, rechecks `@connect` at every hop, rejects HTTPS downgrade, blocks remote-page access to local/private targets, strips credentials cross-origin, limits body size, and supports abort/timeout/progress callbacks.
- `GM_download` must satisfy both the download grant and `@connect`; remote-page downloads are public-network-only and cannot silently fall back to the unrestricted system downloader.
- Authorization, Cookie, proxy credentials, and other unsafe headers are not persisted in download preferences.
- Generated user-script JavaScript is compiled from the actual Java source and syntax-checked with Node in the static test suite.

## Download integrity and privacy

- Redirects are handled manually and capped; HTTPS-to-HTTP downgrade is rejected.
- Cookie and authorization material is sent only to the original origin and is removed after a cross-origin redirect.
- Public-only script downloads reject local, loopback, link-local, private, multicast, and unresolved targets on every hop.
- Range probes, segmented responses, and resumed responses validate `Content-Range` start/end/total values before writing.
- Segment completion and final byte count are checked so incomplete files fail instead of being published.
- Temporary-task naming handles `Long.MIN_VALUE` safely.
- Download filenames remove path separators, C0/C1 controls, and Unicode bidirectional override/isolate controls.
- Android 15+ foreground-service timeout stores progress and pauses the job instead of allowing an abnormal system termination.

## Private browsing

- Private browsing requires an isolated WebView data-directory suffix and closes rather than pretending to be private if isolation cannot be established.
- It runs in a separate process/task, stores no normal history/session, blocks downloads, and does not run persistent user scripts.
- Startup waits for cookie removal before loading the private home page.
- Shutdown clears cookies, WebStorage, geolocation grants, HTTP authentication, form data, cache, and history.
- Private address input uses the same strict HTTP(S) parser and rejects URL credentials.

## Automated checks included

`./tools/static_checks.sh` currently verifies:

- Java lexical/string/bracket integrity across 32 Java files.
- Android XML parsing.
- absence of selected dangerous WebView/redirect patterns.
- release version, API level, manifest, signing, provider, private-task, credential-header, exact-origin, internal-page, and range-validation invariants.
- JDK 17 compilation and execution of `NetworkSecuritySelfTest`.
- generated user-script JavaScript syntax with Node.
- shell syntax and whitespace errors.

The repository also contains:

- JUnit tests for origin, URL credentials, redirects, headers, and local-address classification.
- Android instrumentation tests for hardened WebView defaults and rejection of non-HTTP(S) explicit intents.
- CI build, release, checksum, and emulator workflows.

## Not proven in this environment

This environment did not contain an Android SDK, Gradle installation, emulator, or reachable dependency repositories. Therefore it did not produce or install an APK/AAB and did not run Android Lint, Gradle JUnit tasks, R8, instrumentation, Play pre-launch testing, or real-device performance tests.

The first mandatory gate is a clean GitHub Actions run. The second is the device/site matrix in `RELEASE_CHECKLIST.md`. Production rollout must remain blocked until both pass.

## Residual risks and engineering debt

- `MainActivity` remains a very large controller. It should be split into navigation, tabs, permissions, user scripts, downloads, backup, and UI modules before rapid feature expansion.
- User scripts execute in the page JavaScript world because synchronous legacy GM APIs are supported. The document-start closure protects the native capability, but hostile pages can still interfere with ordinary script objects and DOM behavior. An isolated-world redesign would be stronger but requires an asynchronous compatibility layer and broad script regression testing.
- Native HTTP local-address checks resolve the hostname before opening `HttpURLConnection`; a hostile DNS service could theoretically change answers between validation and connection. Eliminating that DNS-rebinding window requires a custom connection stack with verified address pinning and correct TLS SNI/certificate handling.
- Privacy cleanup depends partly on Android System WebView behavior; service-worker/cache edge cases require device testing across WebView versions.
- Renderer compatibility, media codecs, memory use, startup latency, battery use, background survival, and OEM behavior are unmeasured here.
- No independent penetration test, external code audit, Play pre-launch report, or production telemetry evidence exists yet.
- Store screenshots, 512×512 listing icon, 1024×500 feature graphic, public privacy-policy URL, and final licensing decision remain publication tasks.

## Competitive claim

The source now has a credible release pipeline and substantially stronger security defaults, but code changes alone cannot prove that it equals or exceeds Via. Use `COMPETITIVE_READINESS.md` as the acceptance contract. Do not publish an “exceeds Via” claim until the same-device tests show equal-or-better stability, compatibility, memory, startup, power, and download integrity with no security regression.
