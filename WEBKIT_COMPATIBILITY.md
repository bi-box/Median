# WebKit compatibility design

Median's 500 KB build keeps the AndroidX WebKit public call shape used by the
browser (`WebViewFeature`, `WebViewCompat`, and `ScriptHandler`) and follows the
same Android System WebView support-library boundary protocol for
`DOCUMENT_START_SCRIPT:1`.

Only this focused API slice is vendored. This avoids packaging AndroidX Core,
Lifecycle, VersionedParcelable, Kotlin, notification resources, and unrelated
WebKit APIs. The bridge re-evaluates the WebView package identity after provider
updates and fails closed when the feature is unavailable or incompatible.

The cross-ClassLoader boundary interface names are protected from R8 renaming.
The implementation must be tested against the current stable WebView and major
OEM WebView providers before production rollout.
