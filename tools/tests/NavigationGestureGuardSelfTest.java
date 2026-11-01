package com.xinyv.median;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

/** Pure navigation-gesture boundaries; no WebView or DOM is needed. */
public final class NavigationGestureGuardSelfTest {
    private static final String PAGE = "https://www.xmfyy.com/watch?id=1#top";
    private static final String CROSS_SITE = "https://101nali029.t1hufyr.top/";
    private static final String RELAY = "https://www.xmfyy.com/relay";
    private static final String SECOND_RELAY = "https://cdn.xmfyy.com/relay-two";

    public static void main(String[] args) {
        tapDoesNotBlockCrossSiteNavigation();
        dragBlocksCrossSiteNavigation();
        sameSiteBoundariesRemainAllowed();
        verifiedDragMetadataAndRedirectBoundaries();
        staleOrCancelledGesturesRemainAllowed();
        documentAndTimeBoundariesRemainIndependent();
        ipAndSecondLevelSiteBoundaries();
        inProgressAndThresholdBoundaries();
        activeHoldAndCompletedTapBoundaries();
        cancellationPreservesOnlyVerifiedDrag();
        sameOriginHistoryMigration();
        rejectedHistoryMigrationClearsTouchButKeepsChain();
        acceptedNavigationChainProtectsDelayedHops();
        activeHoldStartsNavigationChain();
        chainCallbackAndExpiryBoundaries();
        acceptedCrossSiteHopDoesNotGrantLaterHops();
        chainResetAndMultiPointerBoundaries();
        concurrentCallbacksAndReadersRemainConsistent();
        startFirstVerifiedDragPreservesNavigationChain();
        startFirstTapAndUnverifiedHoldDoNotCreateChain();
        existingAcceptedHoldChainSurvivesPageStartFallback();
        staleRequestTimestampCannotRetireNewerChain();
        staleAcceptedCallbackCannotReplaceNewerChain();
        blockedNavigationProtectsDelayedGetAndPost();
        blockedTargetCommitCannotClearProvenance();
        blockedRetriesAndAcceptedChainsKeepOriginalDeadline();
        blockedChainEndsOnNewTouchOrNativeReset();
        staleBlockedCallbackCannotReplaceNewerChain();
        System.out.println("NavigationGestureGuardSelfTest passed");
    }

    private static void tapDoesNotBlockCrossSiteNavigation() {
        NavigationGestureGuard guard = new NavigationGestureGuard(8f);
        guard.onDown(PAGE, 1_000L, 20f, 20f, 1);
        guard.onUp(PAGE, 1_080L, 1);
        expectFalse(guard.hasRecentDrag(PAGE, 1_080L), "tap must not become a recent drag");
        expectFalse(guard.shouldBlockNavigation(PAGE, CROSS_SITE, true, false, 1_080L),
                "ordinary cross-site tap must remain navigable");
    }

    private static void dragBlocksCrossSiteNavigation() {
        NavigationGestureGuard guard = dragged(PAGE, 2_000L);
        expectTrue(guard.hasRecentDrag(PAGE, 2_080L), "horizontal drag is remembered briefly");
        expectTrue(guard.shouldBlockNavigation(PAGE, CROSS_SITE, true, false, 2_080L),
                "cross-site navigation after a recent drag is blocked");
        // Fragments identify the same document and must not defeat the guard.
        expectTrue(guard.shouldBlockNavigation(PAGE.replace("#top", "#episode-2"), CROSS_SITE + "#ad",
                        true, false, 2_080L), "document fragments do not change gesture identity");
    }

    private static void sameSiteBoundariesRemainAllowed() {
        NavigationGestureGuard guard = dragged(PAGE, 3_000L);
        expectFalse(guard.shouldBlockNavigation(PAGE, "https://xmfyy.com/next", true, false, 3_080L),
                "same registrable site must remain navigable");
        expectFalse(guard.shouldBlockNavigation(PAGE, "https://cdn.xmfyy.com/asset", true, false, 3_080L),
                "same-site subdomain must remain navigable");
        expectTrue(guard.shouldBlockNavigation(PAGE, "https://xmfyy.com.evil.example/", true, false, 3_080L),
                "a look-alike suffix must remain cross-site");
    }

    private static void verifiedDragMetadataAndRedirectBoundaries() {
        NavigationGestureGuard noDrag = new NavigationGestureGuard(8f);
        expectFalse(noDrag.shouldBlockNavigation(PAGE, CROSS_SITE, false, false, 4_080L),
                "cross-site navigation without a verified drag must remain navigable");
        expectFalse(noDrag.shouldBlockNavigation(PAGE, "https://login.example/sign-in", false, false, 4_080L),
                "ordinary login navigation without a drag must remain navigable");
        expectFalse(noDrag.shouldBlockNavigation(PAGE, "https://meta.example/next", false, false, 4_080L),
                "ordinary meta navigation without a drag must remain navigable");
        expectFalse(noDrag.shouldBlockNavigation(PAGE, CROSS_SITE, false, true, 4_080L),
                "ordinary server redirect without suspicious touch remains navigable");

        NavigationGestureGuard guard = dragged(PAGE, 4_000L);
        // WebView may omit gesture metadata on touchend. The bounded tradeoff is to
        // block a cross-site navigation during a verified drag's 700 ms window.
        expectTrue(guard.shouldBlockNavigation(PAGE, CROSS_SITE, false, false, 4_080L),
                "a recent verified drag still blocks when WebView omits gesture metadata");
        expectTrue(guard.shouldBlockNavigation(PAGE, CROSS_SITE, true, true, 4_080L),
                "server redirect during a verified drag must remain protected");
        expectTrue(guard.shouldBlockNavigation(PAGE, "https://login.example/sign-in", true, true, 4_080L),
                "cross-site redirect during a verified drag is suspicious regardless of path");
        expectTrue(guard.shouldBlockNavigation(PAGE, CROSS_SITE, false, true, 4_080L),
                "server redirect without gesture metadata cannot bypass verified drag protection");
        expectFalse(guard.shouldBlockNavigation(PAGE, "mailto:next@example.com", true, false, 4_080L),
                "a non-HTTP(S) destination must not be treated as an ad navigation");
    }

    private static void staleOrCancelledGesturesRemainAllowed() {
        NavigationGestureGuard nextDown = dragged(PAGE, 5_000L);
        nextDown.onDown(PAGE, 5_090L, 100f, 100f, 1);
        expectFalse(nextDown.hasRecentDrag(PAGE, 5_090L), "a new down cancels the prior drag");
        expectFalse(nextDown.shouldBlockNavigation(PAGE, CROSS_SITE, true, false, 5_090L),
                "a new tap cannot inherit an earlier drag");

        NavigationGestureGuard multiTouch = new NavigationGestureGuard(8f);
        multiTouch.onDown(PAGE, 5_200L, 10f, 10f, 1);
        multiTouch.onMove(PAGE, 5_230L, 40f, 10f, 2);
        multiTouch.onUp(PAGE, 5_240L, 2);
        expectFalse(multiTouch.hasRecentDrag(PAGE, 5_240L), "multi-touch cancels navigation arming");
        expectFalse(multiTouch.shouldBlockNavigation(PAGE, CROSS_SITE, true, false, 5_240L),
                "multi-touch must not create an ad-navigation block");

        NavigationGestureGuard cancelled = dragged(PAGE, 5_400L);
        cancelled.reset();
        expectFalse(cancelled.hasRecentDrag(PAGE, 5_480L), "reset acts as touch cancellation");
        expectFalse(cancelled.shouldBlockNavigation(PAGE, CROSS_SITE, true, false, 5_480L),
                "cancelled drag must remain navigable");
    }

    private static void documentAndTimeBoundariesRemainIndependent() {
        NavigationGestureGuard otherDocument = dragged(PAGE, 6_000L);
        expectFalse(otherDocument.hasRecentDrag("https://www.xmfyy.com/other", 6_080L),
                "a different document has no inherited drag");
        expectFalse(otherDocument.shouldBlockNavigation("https://www.xmfyy.com/other", CROSS_SITE,
                true, false, 6_080L), "a different document cannot consume the drag");
        String otherQuery = PAGE.replace("id=1", "id=2");
        expectFalse(otherDocument.hasRecentDrag(otherQuery, 6_080L),
                "a changed query has no inherited drag");
        expectFalse(otherDocument.shouldBlockNavigation(otherQuery, CROSS_SITE,
                true, false, 6_080L), "a changed query cannot consume the drag");

        NavigationGestureGuard negative = new NavigationGestureGuard(8f);
        negative.onDown(PAGE, -1L, 0f, 0f, 1);
        negative.onMove(PAGE, -1L, 40f, 0f, 1);
        negative.onUp(PAGE, -1L, 1);
        expectFalse(negative.hasRecentDrag(PAGE, -1L), "negative time cannot arm a drag");
        expectFalse(negative.shouldBlockNavigation(PAGE, CROSS_SITE, true, false, -1L),
                "negative time cannot block navigation");

        NavigationGestureGuard expiry = dragged(PAGE, 7_000L);
        expectTrue(expiry.hasRecentDrag(PAGE, 7_779L), "drag remains valid before 700 ms");
        expectFalse(expiry.hasRecentDrag(PAGE, 7_781L), "drag expires after the 700 ms window");
        expectFalse(expiry.shouldBlockNavigation(PAGE, CROSS_SITE, true, false, 7_781L),
                "expired drag cannot block navigation");
    }

    private static void ipAndSecondLevelSiteBoundaries() {
        NavigationGestureGuard coUk = dragged("https://www.example.co.uk/page", 8_000L);
        expectFalse(coUk.shouldBlockNavigation("https://www.example.co.uk/page", "https://cdn.example.co.uk/next",
                true, false, 8_080L), "co.uk registrable site is shared by subdomains");

        NavigationGestureGuard comCn = dragged("https://a.example.com.cn/page", 8_200L);
        expectFalse(comCn.shouldBlockNavigation("https://a.example.com.cn/page", "https://b.example.com.cn/next",
                true, false, 8_280L), "com.cn registrable site is shared by subdomains");

        NavigationGestureGuard sameIp = dragged("http://192.0.2.10/page", 8_400L);
        expectFalse(sameIp.shouldBlockNavigation("http://192.0.2.10/page", "http://192.0.2.10:8080/next",
                true, false, 8_480L), "same IP with another port is same-site");
        expectTrue(sameIp.shouldBlockNavigation("http://192.0.2.10/page", "http://192.0.2.11/next",
                true, false, 8_480L), "different IP is cross-site");

        NavigationGestureGuard comAu = dragged("https://www.example.com.au/page", 8_500L);
        expectFalse(comAu.shouldBlockNavigation("https://www.example.com.au/page", "https://cdn.example.com.au/next",
                true, false, 8_580L), "com.au subdomains share the same registrable site");
        expectTrue(comAu.shouldBlockNavigation("https://www.example.com.au/page", "https://other.com.au/next",
                true, false, 8_580L), "different com.au registrants are cross-site");

        NavigationGestureGuard coJp = dragged("https://www.example.co.jp/page", 8_600L);
        expectFalse(coJp.shouldBlockNavigation("https://www.example.co.jp/page", "https://cdn.example.co.jp/next",
                true, false, 8_680L), "co.jp subdomains share the same registrable site");
        expectTrue(coJp.shouldBlockNavigation("https://www.example.co.jp/page", "https://other.co.jp/next",
                true, false, 8_680L), "different co.jp registrants are cross-site");

        NavigationGestureGuard githubTenant = dragged("https://www.alpha.github.io/page", 8_700L);
        expectFalse(githubTenant.shouldBlockNavigation("https://www.alpha.github.io/page", "https://cdn.alpha.github.io/next",
                true, false, 8_780L), "github.io subdomains within one tenant are same-site");
        expectTrue(githubTenant.shouldBlockNavigation("https://www.alpha.github.io/page", "https://beta.github.io/next",
                true, false, 8_780L), "different github.io tenants are cross-site");

        NavigationGestureGuard pagesTenant = dragged("https://www.alpha.pages.dev/page", 8_800L);
        expectFalse(pagesTenant.shouldBlockNavigation("https://www.alpha.pages.dev/page", "https://preview.alpha.pages.dev/next",
                true, false, 8_880L), "pages.dev subdomains within one tenant are same-site");
        expectTrue(pagesTenant.shouldBlockNavigation("https://www.alpha.pages.dev/page", "https://beta.pages.dev/next",
                true, false, 8_880L), "different pages.dev tenants are cross-site");

        NavigationGestureGuard ipv6 = dragged("https://[2001:db8::10]/page", 8_900L);
        expectFalse(ipv6.shouldBlockNavigation("https://[2001:db8::10]/page", "https://[2001:db8::10]:8443/next",
                true, false, 8_980L), "same IPv6 with another port is same-site");
        expectTrue(ipv6.shouldBlockNavigation("https://[2001:db8::10]/page", "https://[2001:db8::11]/next",
                true, false, 8_980L), "different IPv6 is cross-site");
    }

    private static void inProgressAndThresholdBoundaries() {
        NavigationGestureGuard vertical = new NavigationGestureGuard(8f);
        vertical.onDown(PAGE, 9_000L, 30f, 30f, 1);
        vertical.onMove(PAGE, 9_020L, 30f, 50f, 1);
        expectTrue(vertical.hasRecentDrag(PAGE, 9_020L), "vertical drag is active before pointer up");
        expectTrue(vertical.shouldBlockNavigation(PAGE, CROSS_SITE, true, false, 9_020L),
                "active vertical drag blocks a cross-site navigation");
        expectTrue(vertical.hasRecentDrag(PAGE, 10_020L),
                "active drag remains verified when the finger pauses longer than 700 ms");
        expectTrue(vertical.shouldBlockNavigation(PAGE, CROSS_SITE, true, true, 10_020L),
                "paused active drag cannot become an ordinary tap or redirect grant");

        NavigationGestureGuard exactSlop = new NavigationGestureGuard(8f);
        exactSlop.onDown(PAGE, 9_100L, 0f, 0f, 1);
        exactSlop.onMove(PAGE, 9_120L, 8f, 0f, 1);
        exactSlop.onUp(PAGE, 9_130L, 1);
        expectFalse(exactSlop.hasRecentDrag(PAGE, 9_130L), "movement exactly at slop is not a drag");

        NavigationGestureGuard diagonal = new NavigationGestureGuard(8f);
        diagonal.onDown(PAGE, 9_200L, 0f, 0f, 1);
        diagonal.onMove(PAGE, 9_220L, 6f, 6f, 1);
        expectTrue(diagonal.hasRecentDrag(PAGE, 9_220L), "diagonal movement beyond slop is a drag");

        NavigationGestureGuard foreground = new NavigationGestureGuard(8f);
        foreground.onDown(PAGE, 9_300L, 0f, 0f, 1);
        foreground.onMove(PAGE, 9_320L, 40f, 0f, 1);
        NavigationGestureGuard background = new NavigationGestureGuard(8f);
        expectFalse(background.hasRecentDrag(PAGE, 9_320L), "another view has no inherited active drag");
        expectFalse(background.shouldBlockNavigation(PAGE, CROSS_SITE, true, false, 9_320L),
                "another view cannot consume the foreground drag");

        foreground.onCancel();
        expectFalse(foreground.hasRecentDrag(PAGE, 9_340L), "onCancel clears an in-progress drag");
        expectFalse(foreground.shouldBlockNavigation(PAGE, CROSS_SITE, true, false, 9_340L),
                "cancelled active drag cannot block navigation");

        NavigationGestureGuard multiDown = new NavigationGestureGuard(8f);
        multiDown.onDown(PAGE, 9_400L, 0f, 0f, 2);
        multiDown.onMove(PAGE, 9_420L, 40f, 0f, 1);
        multiDown.onUp(PAGE, 9_430L, 1);
        expectFalse(multiDown.hasRecentDrag(PAGE, 9_430L), "multi-touch down never arms a drag");
        expectFalse(multiDown.shouldBlockNavigation(PAGE, CROSS_SITE, true, false, 9_430L),
                "multi-touch down cannot block navigation");

        NavigationGestureGuard multiMove = dragged(PAGE, 9_500L);
        multiMove.onDown(PAGE, 9_590L, 0f, 0f, 1);
        multiMove.onMove(PAGE, 9_610L, 40f, 0f, 1);
        multiMove.onMove(PAGE, 9_620L, 80f, 0f, 2);
        expectFalse(multiMove.hasRecentDrag(PAGE, 9_620L), "second pointer cancels an active drag");
        expectFalse(multiMove.shouldBlockNavigation(PAGE, CROSS_SITE, true, false, 9_620L),
                "second pointer prevents navigation blocking");

        NavigationGestureGuard timeReversal = new NavigationGestureGuard(8f);
        timeReversal.onDown(PAGE, 9_700L, 0f, 0f, 1);
        timeReversal.onMove(PAGE, 9_720L, 40f, 0f, 1);
        expectFalse(timeReversal.hasRecentDrag(PAGE, 9_719L), "a query before the last move cannot use the drag");
        expectFalse(timeReversal.shouldBlockNavigation(PAGE, CROSS_SITE, true, false, 9_719L),
                "a query before the last move cannot block navigation");
        timeReversal.onMove(PAGE, 9_710L, 80f, 0f, 1);
        expectFalse(timeReversal.hasRecentDrag(PAGE, 9_720L), "time reversal cancels the active drag");
        expectFalse(timeReversal.shouldBlockNavigation(PAGE, CROSS_SITE, true, false, 9_720L),
                "time reversal prevents navigation blocking");
    }

    private static void activeHoldAndCompletedTapBoundaries() {
        NavigationGestureGuard held = new NavigationGestureGuard(8f);
        held.onDown(PAGE, 10_000L, 20f, 20f, 1);
        expectFalse(held.hasRecentDrag(PAGE, 10_001L), "DOWN alone does not become a verified drag");
        expectTrue(held.shouldBlockNavigation(PAGE, CROSS_SITE, false, false, 10_001L),
                "script navigation while the finger is held is blocked before MOVE");
        expectTrue(held.shouldBlockNavigation(PAGE, CROSS_SITE, false, true, 10_001L),
                "redirect metadata does not bypass a suspicious active hold");
        expectFalse(held.shouldBlockNavigation(PAGE, CROSS_SITE, true, false, 10_001L),
                "an active hold with genuine gesture metadata remains navigable before drag");
        expectFalse(held.shouldBlockNavigation(PAGE, RELAY, false, false, 10_001L),
                "a same-site script navigation during a hold remains navigable");
        expectFalse(held.shouldBlockNavigation(RELAY, CROSS_SITE, false, false, 10_001L),
                "active hold protection is bound to its document");
        held.onMove(PAGE, 10_020L, 24f, 20f, 1);
        expectTrue(held.shouldBlockNavigation(PAGE, CROSS_SITE, false, false, 10_020L),
                "movement below slop retains active hold protection");
        held.onUp(PAGE, 10_080L, 1);
        expectFalse(held.shouldBlockNavigation(PAGE, CROSS_SITE, false, false, 10_081L),
                "a completed ordinary tap does not block script navigation");
        expectFalse(held.shouldBlockNavigation(PAGE, CROSS_SITE, true, true, 10_081L),
                "a completed ordinary tap does not block a normal redirect");
    }

    private static void cancellationPreservesOnlyVerifiedDrag() {
        NavigationGestureGuard completed = dragged(PAGE, 11_000L);
        completed.onCancel(PAGE, 11_100L);
        expectTrue(completed.hasRecentDrag(PAGE, 11_779L),
                "cancel after UP preserves the completed drag window");
        expectFalse(completed.hasRecentDrag(PAGE, 11_781L),
                "cancel after UP does not renew the completed drag window");
        completed.onCancel(PAGE, 11_900L);
        expectFalse(completed.hasRecentDrag(PAGE, 11_900L),
                "later cancellation cannot revive an expired drag");

        NavigationGestureGuard active = new NavigationGestureGuard(8f);
        active.onDown(PAGE, 12_000L, 0f, 0f, 1);
        active.onMove(PAGE, 12_020L, 40f, 0f, 1);
        active.onCancel(PAGE, 12_040L);
        expectTrue(active.hasRecentDrag(PAGE, 12_739L),
                "cancelled verified drag retains a short protection window");
        expectTrue(active.shouldBlockNavigation(PAGE, CROSS_SITE, false, true, 12_739L),
                "cancelled verified drag still protects a cross-site redirect");
        expectFalse(active.hasRecentDrag(PAGE, 12_741L),
                "cancelled verified drag expires without another touch");

        NavigationGestureGuard tap = new NavigationGestureGuard(8f);
        tap.onDown(PAGE, 12_800L, 0f, 0f, 1);
        tap.onCancel(PAGE, 12_820L);
        expectFalse(tap.hasRecentDrag(PAGE, 12_820L), "cancelling a hold cannot invent a drag");
        expectFalse(tap.shouldBlockNavigation(PAGE, CROSS_SITE, false, false, 12_820L),
                "cancelled non-drag hold has no remaining touch protection");

        NavigationGestureGuard compatibility = dragged(PAGE, 13_000L);
        compatibility.onCancel();
        expectFalse(compatibility.hasRecentDrag(PAGE, 13_100L),
                "legacy no-argument cancellation retains full-reset semantics");
    }

    private static void sameOriginHistoryMigration() {
        String rewritten = "https://www.xmfyy.com/player?id=2#episode";
        NavigationGestureGuard active = new NavigationGestureGuard(8f);
        active.onDown(PAGE, 14_000L, 0f, 0f, 1);
        active.onMove(PAGE, 14_020L, 40f, 0f, 1);
        expectTrue(active.migrateDocument(PAGE, rewritten),
                "same-origin path and query rewrite is permitted");
        expectTrue(active.hasRecentDrag(rewritten, 14_030L),
                "active drag follows a same-origin history rewrite");
        expectFalse(active.hasRecentDrag(PAGE, 14_030L),
                "old history URL no longer owns the active drag");
        active.onUp(rewritten, 14_040L, 1);
        expectTrue(active.hasRecentDrag(rewritten, 14_040L),
                "UP after history rewrite completes the same active drag");

        NavigationGestureGuard recent = dragged(PAGE, 14_200L);
        expectTrue(recent.migrateDocument(PAGE, rewritten), "completed drag follows same-origin history");
        expectTrue(recent.hasRecentDrag(rewritten, 14_979L),
                "history rewrite preserves original completed-drag expiry");
        expectFalse(recent.hasRecentDrag(rewritten, 14_981L),
                "history rewrite does not extend original completed-drag expiry");

        NavigationGestureGuard fragment = dragged(PAGE, 15_000L);
        String fragmentOnly = PAGE.replace("#top", "#episode-3");
        expectTrue(fragment.migrateDocument(PAGE, fragmentOnly), "fragment-only history rewrite succeeds");
        expectTrue(fragment.hasRecentDrag(fragmentOnly, 15_090L),
                "fragment-only rewrite retains document binding");
        String explicitPort = "https://www.xmfyy.com:443/next";
        expectTrue(fragment.migrateDocument(fragmentOnly, explicitPort),
                "explicit default HTTPS port has the same origin");
        expectTrue(fragment.hasRecentDrag(explicitPort, 15_090L),
                "default-port rewrite migrates the matching binding");

        NavigationGestureGuard unbound = new NavigationGestureGuard(8f);
        expectTrue(unbound.migrateDocument(PAGE, rewritten),
                "same-origin migration succeeds even with no current touch binding");
        expectFalse(unbound.hasRecentDrag(rewritten, 15_090L),
                "history migration does not create touch state");

        NavigationGestureGuard unmatched = dragged(PAGE, 15_200L);
        expectTrue(unmatched.migrateDocument(RELAY, rewritten),
                "same-origin rewrite can succeed even when its old URL has no binding");
        expectTrue(unmatched.hasRecentDrag(PAGE, 15_290L),
                "history migration affects only a binding matching the old document");
        expectFalse(unmatched.hasRecentDrag(rewritten, 15_290L),
                "history rewrite cannot steal a different document's touch binding");
    }

    private static void rejectedHistoryMigrationClearsTouchButKeepsChain() {
        String[] rejected = {
                "http://www.xmfyy.com/next", "https://www.xmfyy.com:8443/next",
                "https://cdn.xmfyy.com/next", CROSS_SITE, "about:blank", "https://"
        };
        for (String target : rejected) {
            NavigationGestureGuard recent = dragged(PAGE, 16_000L);
            expectFalse(recent.migrateDocument(PAGE, target),
                    "history cannot migrate touch across origin: " + target);
            expectFalse(recent.hasRecentDrag(PAGE, 16_090L),
                    "rejected history migration clears the old completed drag: " + target);
            expectFalse(recent.hasRecentDrag(target, 16_090L),
                    "rejected history migration cannot give the new origin a drag: " + target);

            NavigationGestureGuard active = new NavigationGestureGuard(8f);
            active.onDown(PAGE, 16_000L, 0f, 0f, 1);
            active.onMove(PAGE, 16_020L, 40f, 0f, 1);
            expectFalse(active.migrateDocument(PAGE, target), "active cross-origin migration is rejected");
            expectFalse(active.shouldBlockNavigation(PAGE, CROSS_SITE, false, false, 16_030L),
                    "rejected migration retires active touch protection");
        }

        NavigationGestureGuard chain = dragged(PAGE, 16_200L);
        chain.onNavigationAccepted(PAGE, CROSS_SITE, false, false, 16_290L);
        chain.onPageStarted(CROSS_SITE);
        expectFalse(chain.migrateDocument(PAGE, CROSS_SITE), "cross-site history migration remains rejected");
        expectDocument(PAGE, chain.navigationSourceDocument(CROSS_SITE, 17_000L),
                "rejected migration preserves an already accepted chain's original source");
        expectTrue(chain.shouldBlockNavigation(CROSS_SITE, "https://third.example/next", false, true, 17_000L),
                "history callback cannot erase accepted chain protection");
    }

    private static void acceptedNavigationChainProtectsDelayedHops() {
        NavigationGestureGuard chain = acceptedChain(18_000L);
        expectFalse(chain.hasRecentDrag(PAGE, 18_100L), "page start clears native touch state");
        expectDocument(PAGE, chain.navigationSourceDocument(RELAY, 19_000L),
                "accepted same-site chain retains the initiating document after page start");
        expectTrue(chain.shouldBlockNavigation(RELAY, CROSS_SITE, false, true, 19_000L),
                "delayed cross-site server redirect is blocked after the 700 ms touch window");
        expectTrue(chain.shouldBlockNavigation(RELAY, CROSS_SITE, false, false, 19_000L),
                "delayed cross-site script hop is blocked after the 700 ms touch window");
        expectFalse(chain.shouldBlockNavigation(RELAY, SECOND_RELAY, false, true, 19_000L),
                "same-site redirect chain remains navigable");
        expectFalse(chain.shouldBlockNavigation(RELAY, "mailto:test@example.com", false, false, 19_000L),
                "a chain does not turn non-HTTP destinations into ad navigations");

        String rewrittenRelay = "https://www.xmfyy.com/relay?state=2";
        expectTrue(chain.migrateDocument(RELAY, rewrittenRelay), "same-origin chain history rewrite succeeds");
        expectDocument(PAGE, chain.navigationSourceDocument(rewrittenRelay, 19_010L),
                "same-origin history preserves original chain provenance");

        NavigationGestureGuard ordinary = new NavigationGestureGuard(8f);
        ordinary.onNavigationAccepted(PAGE, RELAY, false, false, 19_000L);
        ordinary.onPageStarted(RELAY);
        expectDocument(RELAY, ordinary.navigationSourceDocument(RELAY, 19_001L),
                "ordinary accepted navigation cannot create a touch-derived chain");
        expectFalse(ordinary.shouldBlockNavigation(RELAY, CROSS_SITE, false, true, 19_001L),
                "ordinary redirect navigation remains navigable");

        NavigationGestureGuard expired = dragged(PAGE, 19_100L);
        expired.onNavigationAccepted(PAGE, RELAY, false, false, 19_881L);
        expired.onPageStarted(RELAY);
        expectDocument(RELAY, expired.navigationSourceDocument(RELAY, 19_882L),
                "an expired drag cannot arm a new accepted navigation chain");
    }

    private static void activeHoldStartsNavigationChain() {
        NavigationGestureGuard held = new NavigationGestureGuard(8f);
        held.onDown(PAGE, 20_000L, 0f, 0f, 1);
        held.onNavigationAccepted(PAGE, RELAY, false, false, 20_001L);
        held.onPageStarted(RELAY);
        expectDocument(PAGE, held.navigationSourceDocument(RELAY, 20_900L),
                "same-site script navigation before MOVE retains its initiating hold");
        expectTrue(held.shouldBlockNavigation(RELAY, CROSS_SITE, false, true, 20_900L),
                "a DOWN-only first hop cannot bypass delayed redirect protection");

        NavigationGestureGuard genuine = new NavigationGestureGuard(8f);
        genuine.onDown(PAGE, 21_000L, 0f, 0f, 1);
        genuine.onNavigationAccepted(PAGE, RELAY, true, false, 21_001L);
        genuine.onPageStarted(RELAY);
        expectDocument(RELAY, genuine.navigationSourceDocument(RELAY, 21_900L),
                "a non-drag hold with real gesture metadata does not arm a chain");

        NavigationGestureGuard tap = new NavigationGestureGuard(8f);
        tap.onDown(PAGE, 21_200L, 0f, 0f, 1);
        tap.onUp(PAGE, 21_280L, 1);
        tap.onNavigationAccepted(PAGE, RELAY, false, false, 21_281L);
        tap.onPageStarted(RELAY);
        expectDocument(RELAY, tap.navigationSourceDocument(RELAY, 21_900L),
                "a completed tap cannot arm a chain even when WebView omits gesture metadata");
    }

    private static void chainCallbackAndExpiryBoundaries() {
        NavigationGestureGuard chain = acceptedChain(22_000L);
        chain.onNavigationAccepted(RELAY, SECOND_RELAY, false, true, 31_000L);
        chain.onPageStarted(SECOND_RELAY);
        chain.onNavigationCommitted(RELAY);
        expectDocument(PAGE, chain.navigationSourceDocument(SECOND_RELAY, 31_001L),
                "stale commit for the previous hop cannot clear the latest accepted target");
        expectTrue(chain.shouldBlockNavigation(SECOND_RELAY, CROSS_SITE, false, true, 32_089L),
                "chain remains valid just before its original 10 second deadline");
        expectTrue(chain.shouldBlockNavigation(SECOND_RELAY, CROSS_SITE, false, true, 32_090L),
                "chain remains valid at its inclusive original 10 second boundary");
        expectFalse(chain.shouldBlockNavigation(SECOND_RELAY, CROSS_SITE, false, true, 32_091L),
                "later accepted hops do not renew the original 10 second deadline");
        expectDocument(SECOND_RELAY, chain.navigationSourceDocument(SECOND_RELAY, 32_091L),
                "expired chain restores the current fallback source");

        NavigationGestureGuard committed = acceptedChain(33_000L);
        committed.onNavigationAccepted(RELAY, SECOND_RELAY, false, true, 33_100L);
        committed.onPageStarted(SECOND_RELAY);
        committed.onNavigationCommitted(SECOND_RELAY + "#loaded");
        expectDocument(SECOND_RELAY, committed.navigationSourceDocument(SECOND_RELAY, 33_101L),
                "matching target commit clears its chain despite fragment changes");
        expectFalse(committed.shouldBlockNavigation(SECOND_RELAY, CROSS_SITE, false, true, 33_101L),
                "committed navigation has no remaining touch-derived chain");

        NavigationGestureGuard mismatch = acceptedChain(34_000L);
        mismatch.onPageStarted("https://www.xmfyy.com/unrelated");
        expectDocument(PAGE, mismatch.navigationSourceDocument(RELAY, 34_100L),
                "page start for an unaccepted target preserves initiating chain provenance");
        expectTrue(mismatch.shouldBlockNavigation(RELAY, CROSS_SITE, false, true, 35_000L),
                "unaccepted page start cannot bypass later cross-site redirect protection");
        mismatch.onNavigationCommitted("https://www.xmfyy.com/unrelated");
        expectDocument(PAGE, mismatch.navigationSourceDocument(RELAY, 35_001L),
                "unaccepted target commit cannot retire the accepted current target's chain");
    }

    private static void acceptedCrossSiteHopDoesNotGrantLaterHops() {
        NavigationGestureGuard chain = dragged(PAGE, 35_000L);
        // The caller may accept this first hop through an explicit rule. The guard does
        // not know that rule and must keep evaluating each later hop against the source.
        chain.onNavigationAccepted(PAGE, CROSS_SITE, false, false, 35_090L);
        chain.onPageStarted(CROSS_SITE);
        expectDocument(PAGE, chain.navigationSourceDocument(CROSS_SITE, 36_000L),
                "an accepted cross-site first hop preserves original source provenance");
        expectTrue(chain.shouldBlockNavigation(CROSS_SITE, "https://another.example/next", false, true, 36_000L),
                "an accepted first hop cannot grant an unrelated cross-site redirect");
        expectFalse(chain.shouldBlockNavigation(CROSS_SITE, RELAY, false, true, 36_000L),
                "a return to the original site remains allowed within the chain");
        chain.onNavigationAccepted(CROSS_SITE, "https://another.example/next", false, true, 36_010L);
        chain.onPageStarted("https://another.example/next");
        expectDocument(PAGE, chain.navigationSourceDocument("https://another.example/next", 36_900L),
                "later accepted cross-site hop cannot replace the original chain source");
        expectTrue(chain.shouldBlockNavigation("https://another.example/next", CROSS_SITE, false, true, 36_900L),
                "each accepted hop remains subject to the initiating site's protection");
    }

    private static void chainResetAndMultiPointerBoundaries() {
        NavigationGestureGuard down = acceptedChain(37_000L);
        down.onDown(RELAY, 37_200L, 0f, 0f, 1);
        expectDocument(RELAY, down.navigationSourceDocument(RELAY, 37_201L),
                "new DOWN retires the previous chain provenance");
        expectFalse(down.shouldBlockNavigation(RELAY, CROSS_SITE, true, false, 37_201L),
                "new genuine tap cannot inherit earlier chain protection");

        NavigationGestureGuard reset = acceptedChain(37_300L);
        reset.reset();
        expectFalse(reset.shouldBlockNavigation(RELAY, CROSS_SITE, false, true, 37_400L),
                "full reset retires accepted chain protection");
        NavigationGestureGuard timestampCancel = acceptedChain(37_400L);
        timestampCancel.onCancel(RELAY, 37_500L);
        expectDocument(PAGE, timestampCancel.navigationSourceDocument(RELAY, 38_200L),
                "timestamped cancellation cannot erase a chain accepted before page start");
        expectTrue(timestampCancel.shouldBlockNavigation(RELAY, CROSS_SITE, false, true, 38_200L),
                "timestamped cancellation preserves delayed redirect protection");
        NavigationGestureGuard legacyCancel = acceptedChain(37_500L);
        legacyCancel.onCancel();
        expectDocument(RELAY, legacyCancel.navigationSourceDocument(RELAY, 37_600L),
                "legacy cancellation clears accepted chain provenance");

        NavigationGestureGuard multiDown = acceptedChain(37_700L);
        multiDown.onDown(RELAY, 37_900L, 0f, 0f, 2);
        expectFalse(multiDown.shouldBlockNavigation(RELAY, CROSS_SITE, false, true, 37_901L),
                "multiple-pointer DOWN resets an old navigation chain");

        NavigationGestureGuard multiMove = new NavigationGestureGuard(8f);
        multiMove.onDown(PAGE, 38_000L, 0f, 0f, 1);
        multiMove.onMove(PAGE, 38_020L, 40f, 0f, 1);
        multiMove.onNavigationAccepted(PAGE, RELAY, false, false, 38_030L);
        multiMove.onMove(PAGE, 38_040L, 50f, 0f, 2);
        expectDocument(RELAY, multiMove.navigationSourceDocument(RELAY, 38_041L),
                "multiple-pointer MOVE retires a chain accepted during the active drag");
        expectFalse(multiMove.shouldBlockNavigation(PAGE, CROSS_SITE, false, true, 38_041L),
                "multiple-pointer MOVE resets both touch and navigation-chain protection");

        NavigationGestureGuard multiUp = new NavigationGestureGuard(8f);
        multiUp.onDown(PAGE, 38_200L, 0f, 0f, 1);
        multiUp.onMove(PAGE, 38_220L, 40f, 0f, 1);
        multiUp.onNavigationAccepted(PAGE, RELAY, false, false, 38_230L);
        multiUp.onUp(PAGE, 38_240L, 2);
        expectDocument(RELAY, multiUp.navigationSourceDocument(RELAY, 38_241L),
                "multiple-pointer UP retires an active accepted chain");
    }

    private static void concurrentCallbacksAndReadersRemainConsistent() {
        final NavigationGestureGuard stable = acceptedChain(40_000L);
        stable.onNavigationAccepted(RELAY, SECOND_RELAY, false, true, 40_100L);
        stable.onPageStarted(SECOND_RELAY);
        runConcurrently(
                () -> {
                    for (int i = 0; i < 2_000; i++) {
                        stable.onNavigationCommitted(RELAY);
                        stable.onPageStarted(SECOND_RELAY);
                    }
                },
                () -> {
                    for (int i = 0; i < 2_000; i++) {
                        expectDocument(PAGE, stable.navigationSourceDocument(SECOND_RELAY, 41_000L),
                                "concurrent stale callbacks retain the current chain source");
                        expectTrue(stable.shouldBlockNavigation(SECOND_RELAY, CROSS_SITE, false, true, 41_000L),
                                "concurrent stale callbacks cannot expose a cross-site redirect");
                    }
                });

        final NavigationGestureGuard changing = new NavigationGestureGuard(8f);
        runConcurrently(
                () -> {
                    for (int i = 0; i < 1_000; i++) {
                        changing.reset();
                        changing.onDown(PAGE, 50_000L, 0f, 0f, 1);
                        changing.onMove(PAGE, 50_020L, 40f, 0f, 1);
                        changing.onNavigationAccepted(PAGE, RELAY, false, false, 50_030L);
                        changing.onPageStarted(RELAY);
                        changing.onNavigationAccepted(RELAY, SECOND_RELAY, false, true, 50_040L);
                        changing.onPageStarted(SECOND_RELAY);
                    }
                },
                () -> {
                    for (int i = 0; i < 3_000; i++) {
                        String source = changing.navigationSourceDocument(SECOND_RELAY, 51_000L);
                        expectTrue(sameDocument(PAGE, source) || sameDocument(SECOND_RELAY, source),
                                "reader observes either original chain provenance or its fallback during resets");
                        changing.shouldBlockNavigation(SECOND_RELAY, CROSS_SITE, false, true, 51_000L);
                        changing.hasRecentDrag(PAGE, 51_000L);
                    }
                },
                () -> {
                    for (int i = 0; i < 1_000; i++) {
                        changing.onNavigationCommitted(RELAY);
                        changing.reset();
                    }
                });
        changing.reset();
        expectDocument(SECOND_RELAY, changing.navigationSourceDocument(SECOND_RELAY, 51_001L),
                "final reset after concurrent callbacks removes chain state");
        expectFalse(changing.shouldBlockNavigation(SECOND_RELAY, CROSS_SITE, false, true, 51_001L),
                "final reset after concurrent callbacks restores ordinary navigation");
    }

    private static void startFirstVerifiedDragPreservesNavigationChain() {
        String laterCrossSite = "https://third.example/delayed";
        for (String initialTarget : new String[] { RELAY, CROSS_SITE }) {
            for (boolean active : new boolean[] { true, false }) {
                NavigationGestureGuard guard;
                if (active) {
                    guard = new NavigationGestureGuard(8f);
                    guard.onDown(PAGE, 60_000L, 0f, 0f, 1);
                    guard.onMove(PAGE, 60_020L, 40f, 0f, 1);
                } else {
                    guard = dragged(PAGE, 60_000L);
                }
                expectTrue(guard.hasRecentDrag(PAGE, 60_090L),
                        "start-first fallback sees a verified active or completed drag");
                simulatePageStartedFallback(guard, PAGE, initialTarget, true, 60_090L);
                expectFalse(guard.hasRecentDrag(PAGE, 60_091L),
                        "page start clears touch after preserving its verified provenance");
                expectDocument(PAGE, guard.navigationSourceDocument(initialTarget, 61_000L),
                        "start-first fallback keeps the original source after delayed interception");
                expectTrue(guard.shouldBlockNavigation(initialTarget, laterCrossSite, false, true, 61_000L),
                        "start-first fallback protects a delayed cross-site redirect beyond 700 ms");
                expectTrue(guard.shouldBlockNavigation(initialTarget, laterCrossSite, false, false, 61_000L),
                        "start-first fallback protects a delayed cross-site script hop beyond 700 ms");
                guard.onNavigationCommitted(initialTarget);
                expectDocument(initialTarget, guard.navigationSourceDocument(initialTarget, 61_010L),
                        "matching commit clears a fallback-created chain");
                expectFalse(guard.shouldBlockNavigation(initialTarget, laterCrossSite, false, true, 61_010L),
                        "committed fallback navigation restores ordinary redirect handling");
            }
        }
    }

    private static void startFirstTapAndUnverifiedHoldDoNotCreateChain() {
        for (String initialTarget : new String[] { RELAY, CROSS_SITE }) {
            NavigationGestureGuard held = new NavigationGestureGuard(8f);
            held.onDown(PAGE, 62_000L, 0f, 0f, 1);
            expectFalse(held.hasRecentDrag(PAGE, 62_001L), "DOWN-only fallback is not a verified drag");
            simulatePageStartedFallback(held, PAGE, initialTarget, true, 62_001L);
            expectDocument(initialTarget, held.navigationSourceDocument(initialTarget, 63_000L),
                    "page-start fallback does not infer a chain from an unverified hold");
            expectFalse(held.shouldBlockNavigation(initialTarget, "https://third.example/delayed",
                            false, true, 63_000L),
                    "unverified hold fallback cannot create later redirect protection");

            NavigationGestureGuard tap = new NavigationGestureGuard(8f);
            tap.onDown(PAGE, 63_200L, 0f, 0f, 1);
            tap.onUp(PAGE, 63_280L, 1);
            simulatePageStartedFallback(tap, PAGE, initialTarget, true, 63_281L);
            expectDocument(initialTarget, tap.navigationSourceDocument(initialTarget, 64_000L),
                    "page-start fallback cannot turn a completed tap into a drag chain");

            NavigationGestureGuard disabled = dragged(PAGE, 64_200L);
            simulatePageStartedFallback(disabled, PAGE, initialTarget, false, 64_290L);
            expectDocument(initialTarget, disabled.navigationSourceDocument(initialTarget, 65_000L),
                    "disabled source protection prevents page-start fallback from arming a chain");
        }
    }

    private static void existingAcceptedHoldChainSurvivesPageStartFallback() {
        for (String initialTarget : new String[] { RELAY, CROSS_SITE }) {
            NavigationGestureGuard held = new NavigationGestureGuard(8f);
            held.onDown(PAGE, 66_000L, 0f, 0f, 1);
            held.onNavigationAccepted(PAGE, initialTarget, false, false, 66_001L);
            expectFalse(held.hasRecentDrag(PAGE, 66_500L),
                    "a previously accepted hold chain still has no verified drag");
            simulatePageStartedFallback(held, PAGE, initialTarget, true, 66_500L);
            expectDocument(PAGE, held.navigationSourceDocument(initialTarget, 67_000L),
                    "matching page-start fallback preserves an already accepted hold chain");
            expectTrue(held.shouldBlockNavigation(initialTarget, "https://third.example/delayed",
                            false, true, 76_000L),
                    "existing hold chain remains protected until its original deadline");
            expectFalse(held.shouldBlockNavigation(initialTarget, "https://third.example/delayed",
                            false, true, 76_002L),
                    "page-start fallback does not renew an existing hold chain's lifetime");
        }
    }

    private static void staleRequestTimestampCannotRetireNewerChain() {
        NavigationGestureGuard guard = acceptedChain(70_000L);
        // A request thread sampled this time immediately before the UI accepted the
        // chain at 70_090. The process clock itself has not moved backwards.
        long sampledRequestAt = 70_089L;
        expectDocument(RELAY, guard.navigationSourceDocument(RELAY, sampledRequestAt),
                "a request sampled before chain creation cannot use the future chain");
        expectFalse(guard.shouldBlockNavigation(RELAY, CROSS_SITE, false, true, sampledRequestAt),
                "a request sampled before chain creation is not attributed to the future chain");
        expectDocument(PAGE, guard.navigationSourceDocument(RELAY, 70_091L),
                "stale source and block queries cannot delete a newer UI chain");
        expectTrue(guard.shouldBlockNavigation(RELAY, CROSS_SITE, false, true, 70_091L),
                "newer redirect protection survives stale request-thread queries");
    }

    private static void staleAcceptedCallbackCannotReplaceNewerChain() {
        NavigationGestureGuard guard = dragged(PAGE, 71_000L);
        guard.onNavigationAccepted(PAGE, RELAY, false, false, 71_090L);
        // The old request time is after the completed drag's UP at 71_080, so
        // ignoring the existing chain would otherwise re-arm it with an old target.
        guard.onNavigationAccepted(PAGE, SECOND_RELAY, false, false, 71_085L);
        guard.onPageStarted(RELAY);
        guard.onNavigationCommitted(SECOND_RELAY);
        expectDocument(PAGE, guard.navigationSourceDocument(RELAY, 72_000L),
                "stale accepted callback cannot replace the newer chain target or source");
        expectTrue(guard.shouldBlockNavigation(RELAY, CROSS_SITE, false, true, 81_089L),
                "stale accepted callback cannot shorten the newer chain's original lifetime");
        expectFalse(guard.shouldBlockNavigation(RELAY, CROSS_SITE, false, true, 81_091L),
                "stale accepted callback cannot renew the newer chain's original lifetime");

        NavigationGestureGuard afterStart = acceptedChain(82_000L);
        afterStart.onNavigationAccepted(PAGE, SECOND_RELAY, false, false, 82_089L);
        afterStart.onNavigationCommitted(SECOND_RELAY);
        expectDocument(PAGE, afterStart.navigationSourceDocument(RELAY, 82_100L),
                "old accepted callback after page start cannot erase a newer accepted chain");
        expectTrue(afterStart.shouldBlockNavigation(RELAY, CROSS_SITE, false, true, 82_100L),
                "delayed redirect remains protected after a stale accepted callback");
    }

    private static void blockedNavigationProtectsDelayedGetAndPost() {
        String delayedTarget = "https://third.example/delayed-post";
        NavigationGestureGuard recent = dragged(PAGE, 84_000L);
        expectTrue(recent.shouldBlockNavigation(PAGE, CROSS_SITE, false, false, 84_090L),
                "first cross-site navigation is blocked by the verified recent drag");
        recent.onNavigationBlocked(PAGE, CROSS_SITE, 84_090L);
        expectFalse(recent.hasRecentDrag(PAGE, 85_590L),
                "the real 1500 ms delayed retry is outside the 700 ms drag window");
        expectTrue(recent.shouldBlockNavigation(PAGE, delayedTarget, false, false, 85_590L),
                "delayed GET decision retains protection after a blocked first hop");
        // POST can omit override and start its page before the request-thread decision.
        recent.onPageStarted(delayedTarget);
        recent.migrateDocument(PAGE, delayedTarget);
        expectDocument(PAGE, recent.navigationSourceDocument(delayedTarget, 85_591L),
                "start-first delayed POST retains the blocked first hop's original source");
        expectTrue(recent.shouldBlockNavigation(delayedTarget, delayedTarget, false, false, 85_591L),
                "delayed POST interception cannot treat its destination as a new unprotected source");

        NavigationGestureGuard held = new NavigationGestureGuard(8f);
        held.onDown(PAGE, 86_000L, 0f, 0f, 1);
        expectTrue(held.shouldBlockNavigation(PAGE, CROSS_SITE, false, false, 86_001L),
                "a held-finger script first hop is blocked before MOVE");
        held.onNavigationBlocked(PAGE, CROSS_SITE, 86_001L);
        held.onUp(PAGE, 86_080L, 1);
        expectFalse(held.hasRecentDrag(PAGE, 87_501L), "blocked hold need not become a verified drag");
        expectTrue(held.shouldBlockNavigation(PAGE, delayedTarget, false, false, 87_501L),
                "delayed GET after blocked hold remains protected after UP");
        held.onPageStarted(delayedTarget);
        held.migrateDocument(PAGE, delayedTarget);
        expectTrue(held.shouldBlockNavigation(delayedTarget, delayedTarget, false, true, 87_502L),
                "delayed POST or redirect after blocked hold remains protected across page start");
        expectDocument(PAGE, held.navigationSourceDocument(delayedTarget, 87_502L),
                "blocked hold chain retains its original source across destination replacement");
    }

    private static void blockedTargetCommitCannotClearProvenance() {
        NavigationGestureGuard blocked = dragged(PAGE, 88_000L);
        blocked.onNavigationBlocked(PAGE, CROSS_SITE, 88_090L);
        blocked.onPageStarted(CROSS_SITE);
        String[] unacceptedCommits = { CROSS_SITE, PAGE, RELAY, null, "", "about:blank", "https://" };
        for (String url : unacceptedCommits) {
            blocked.onNavigationCommitted(url);
            expectDocument(PAGE, blocked.navigationSourceDocument(CROSS_SITE, 89_000L),
                    "blocked chain has no accepted commit target, including empty identities: " + url);
        }
        expectTrue(blocked.shouldBlockNavigation(CROSS_SITE, "https://third.example/delayed",
                        false, false, 89_000L),
                "rejected target commit cannot clear blocked-source protection");
        blocked.onNavigationAccepted(PAGE, RELAY, false, false, 89_100L);
        blocked.onPageStarted(RELAY);
        blocked.onNavigationCommitted(CROSS_SITE);
        expectDocument(PAGE, blocked.navigationSourceDocument(RELAY, 89_101L),
                "later accepted same-site target is independent of the earlier rejected target");
        blocked.onNavigationCommitted(RELAY);
        expectDocument(RELAY, blocked.navigationSourceDocument(RELAY, 89_102L),
                "matching accepted same-site commit clears a previously blocked chain");
        expectFalse(blocked.shouldBlockNavigation(RELAY, CROSS_SITE, false, false, 89_102L),
                "accepted same-site completion restores ordinary navigation");
    }

    private static void blockedRetriesAndAcceptedChainsKeepOriginalDeadline() {
        NavigationGestureGuard blocked = dragged(PAGE, 90_000L);
        blocked.onNavigationBlocked(PAGE, CROSS_SITE, 90_090L);
        blocked.onNavigationBlocked(PAGE, "https://third.example/retry", 99_090L);
        expectTrue(blocked.shouldBlockNavigation(PAGE, CROSS_SITE, false, false, 100_090L),
                "blocked retries preserve the first inclusive 10 second deadline");
        expectFalse(blocked.shouldBlockNavigation(PAGE, CROSS_SITE, false, false, 100_091L),
                "blocked retries cannot extend the first 10 second deadline");

        NavigationGestureGuard accepted = acceptedChain(101_000L);
        accepted.onNavigationBlocked(RELAY, CROSS_SITE, 110_000L);
        accepted.onNavigationCommitted(CROSS_SITE);
        expectDocument(PAGE, accepted.navigationSourceDocument(RELAY, 111_089L),
                "blocking a later hop does not replace an accepted chain's source or target");
        expectTrue(accepted.shouldBlockNavigation(RELAY, CROSS_SITE, false, true, 111_090L),
                "blocking a later hop retains the existing chain's original deadline");
        expectFalse(accepted.shouldBlockNavigation(RELAY, CROSS_SITE, false, true, 111_091L),
                "blocking a later hop cannot renew an existing accepted chain");

        NavigationGestureGuard acceptedCommit = acceptedChain(112_000L);
        acceptedCommit.onNavigationBlocked(RELAY, CROSS_SITE, 113_000L);
        acceptedCommit.onNavigationCommitted(RELAY);
        expectDocument(RELAY, acceptedCommit.navigationSourceDocument(RELAY, 113_001L),
                "blocked later hop leaves the existing accepted target eligible to commit");
    }

    private static void blockedChainEndsOnNewTouchOrNativeReset() {
        NavigationGestureGuard nextTap = dragged(PAGE, 114_000L);
        nextTap.onNavigationBlocked(PAGE, CROSS_SITE, 114_090L);
        nextTap.onDown(PAGE, 115_000L, 0f, 0f, 1);
        expectFalse(nextTap.shouldBlockNavigation(PAGE, CROSS_SITE, true, false, 115_001L),
                "new genuine tap cannot inherit the blocked prior gesture's chain");
        nextTap.onUp(PAGE, 115_080L, 1);
        expectFalse(nextTap.shouldBlockNavigation(PAGE, CROSS_SITE, false, false, 115_081L),
                "completed new tap releases blocked-chain protection");

        NavigationGestureGuard nativeReset = dragged(PAGE, 116_000L);
        nativeReset.onNavigationBlocked(PAGE, CROSS_SITE, 116_090L);
        nativeReset.reset();
        expectDocument(RELAY, nativeReset.navigationSourceDocument(RELAY, 117_000L),
                "explicit native navigation reset clears blocked-source provenance");
        expectFalse(nativeReset.shouldBlockNavigation(RELAY, CROSS_SITE, false, true, 117_000L),
                "explicit native reset restores ordinary redirect behavior");
    }

    private static void staleBlockedCallbackCannotReplaceNewerChain() {
        NavigationGestureGuard accepted = dragged(PAGE, 118_000L);
        accepted.onNavigationAccepted(PAGE, RELAY, false, false, 118_090L);
        accepted.onNavigationBlocked(PAGE, CROSS_SITE, 118_085L);
        accepted.onPageStarted(RELAY);
        expectDocument(PAGE, accepted.navigationSourceDocument(RELAY, 119_000L),
                "old blocked timestamp cannot erase or replace a newer accepted source");
        accepted.onNavigationCommitted(RELAY);
        expectDocument(RELAY, accepted.navigationSourceDocument(RELAY, 119_001L),
                "old blocked timestamp cannot replace a newer accepted target with an empty target");

        NavigationGestureGuard blocked = dragged(PAGE, 120_000L);
        blocked.onNavigationBlocked(PAGE, CROSS_SITE, 120_090L);
        blocked.onNavigationBlocked(PAGE, "https://third.example/old", 120_085L);
        expectTrue(blocked.shouldBlockNavigation(PAGE, CROSS_SITE, false, false, 130_089L),
                "old blocked timestamp cannot shorten a newer blocked chain's deadline");
        expectFalse(blocked.shouldBlockNavigation(PAGE, CROSS_SITE, false, false, 130_091L),
                "old blocked timestamp cannot renew a newer blocked chain's deadline");
    }

    /** Mirrors UI page-start ordering when WebView omitted an override callback. */
    private static void simulatePageStartedFallback(NavigationGestureGuard guard, String previousDocument,
                                                   String target, boolean sourceProtectionEnabled, long now) {
        String initiatingDocument = guard.navigationSourceDocument(previousDocument, now);
        if (sourceProtectionEnabled && guard.hasRecentDrag(initiatingDocument, now))
            guard.onNavigationAccepted(initiatingDocument, target, true, false, now);
        guard.onPageStarted(target);
        guard.migrateDocument(previousDocument, target);
    }

    private static void runConcurrently(Runnable... tasks) {
        CountDownLatch start = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread[] threads = new Thread[tasks.length];
        for (int i = 0; i < tasks.length; i++) {
            final Runnable task = tasks[i];
            threads[i] = new Thread(() -> {
                try {
                    start.await();
                    task.run();
                } catch (Throwable throwable) {
                    failure.compareAndSet(null, throwable);
                }
            }, "navigation-guard-test-" + i);
            threads[i].setDaemon(true);
            threads[i].start();
        }
        start.countDown();
        try {
            for (Thread thread : threads) {
                thread.join(5_000L);
                expectFalse(thread.isAlive(), "concurrent guard callbacks must complete without deadlock");
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrupted while testing concurrent guard callbacks", interrupted);
        }
        if (failure.get() != null) throw new AssertionError("Concurrent guard callback failed", failure.get());
    }

    private static NavigationGestureGuard acceptedChain(long at) {
        NavigationGestureGuard guard = dragged(PAGE, at);
        guard.onNavigationAccepted(PAGE, RELAY, false, false, at + 90L);
        guard.onPageStarted(RELAY);
        return guard;
    }

    private static void expectDocument(String expected, String actual, String label) {
        expectTrue(sameDocument(expected, actual), label + ": " + actual);
    }

    private static boolean sameDocument(String first, String second) {
        if (first == null || second == null) return first == second;
        int firstFragment = first.indexOf('#');
        int secondFragment = second.indexOf('#');
        return (firstFragment < 0 ? first : first.substring(0, firstFragment)).equals(
                secondFragment < 0 ? second : second.substring(0, secondFragment));
    }

    private static NavigationGestureGuard dragged(String documentUrl, long at) {
        NavigationGestureGuard guard = new NavigationGestureGuard(8f);
        guard.onDown(documentUrl, at, 20f, 20f, 1);
        guard.onMove(documentUrl, at + 30L, 80f, 20f, 1);
        guard.onUp(documentUrl, at + 80L, 1);
        return guard;
    }

    private static void expectTrue(boolean value, String label) {
        if (!value) throw new AssertionError("Expected: " + label);
    }

    private static void expectFalse(boolean value, String label) {
        if (value) throw new AssertionError("Unexpected: " + label);
    }
}
