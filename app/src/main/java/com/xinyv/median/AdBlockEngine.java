package com.xinyv.median;

import android.net.Uri;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.BooleanSupplier;
import java.util.regex.Pattern;

/**
 * Bounded ABP/hosts engine optimized for WebView's request callback.
 *
 * It deliberately implements the safe, common network and cosmetic subset instead of
 * evaluating arbitrary scriptlets. Domain rules remain O(labels); general rules are
 * keyword-indexed so large subscriptions do not turn every request into a linear scan.
 */
final class AdBlockEngine {
    static final class Stats {
        final int networkRules;
        final int cosmeticRules;
        final int sourceLines;
        final long inspectedRequests;
        final int blockedRequests;
        final int allowedRequests;
        final boolean capacityLimited;

        Stats(int networkRules, int cosmeticRules, int sourceLines, long inspectedRequests,
              int blockedRequests, int allowedRequests, boolean capacityLimited) {
            this.networkRules = networkRules;
            this.cosmeticRules = cosmeticRules;
            this.sourceLines = sourceLines;
            this.inspectedRequests = inspectedRequests;
            this.blockedRequests = blockedRequests;
            this.allowedRequests = allowedRequests;
            this.capacityLimited = capacityLimited;
        }
    }

    private static final int TYPE_SCRIPT = 1;
    private static final int TYPE_IMAGE = 1 << 1;
    private static final int TYPE_STYLE = 1 << 2;
    private static final int TYPE_FONT = 1 << 3;
    private static final int TYPE_MEDIA = 1 << 4;
    private static final int TYPE_XHR = 1 << 5;
    private static final int TYPE_FRAME = 1 << 6;
    private static final int TYPE_DOCUMENT = 1 << 7;
    private static final int TYPE_OTHER = 1 << 8;
    private static final int TYPE_ALL = (1 << 9) - 1;
    private static final int MAX_LINES = 1100000;
    private static final int MAX_NETWORK_RULES = 260000;
    private static final int MAX_PACKED_DOMAIN_RULES = 1000000;
    private static final int MAX_COSMETIC_RULES = 60000;
    private static final int MAX_GENERIC_RULES = 4096;
    private static final int MAX_SELECTORS_PER_PAGE = 900;
    private static final int MAX_REQUEST_URL = 16384;
    private static final int MAX_COSMETIC_CACHE_ENTRIES = 24;
    private static final int MAX_COSMETIC_CACHE_VALUE = 96 * 1024;
    private static final int INDEX_KEY_LENGTH = 6;
    private static final int INDEX_ROLLING_FACTOR = 28629151; // 31^(INDEX_KEY_LENGTH-1)
    private static final int RULE_SCRATCH_LIMIT = 1 << 12;
    private static final String TWO_LEVEL_SUFFIXES =
            "|ac.uk|co.uk|gov.uk|org.uk|me.uk|com.cn|net.cn|org.cn|gov.cn|com.tw|net.tw|org.tw|" +
            "com.au|net.au|org.au|edu.au|co.jp|ne.jp|or.jp|co.kr|or.kr|co.nz|org.nz|co.in|firm.in|" +
            "com.br|com.mx|com.sg|com.hk|com.my|com.ph|com.tr|com.ar|com.pl|com.ua|co.za|co.il|" +
            "github.io|appspot.com|blogspot.com|wordpress.com|pages.dev|workers.dev|vercel.app|netlify.app|" +
            "herokuapp.com|firebaseapp.com|web.app|azurewebsites.net|cloudfront.net|notion.site|duckdns.org|";
    private static final List<String> BUILTIN_SELECTORS = Collections.unmodifiableList(Arrays.asList(
            "iframe[src*='doubleclick']", "iframe[src*='googlesyndication']", "[id^='google_ads_']",
            "[data-ad-slot]", ".adsbygoogle", ".ad-container", ".advertisement", ".sponsored-content",
            "[aria-label='Ads']", "[aria-label='广告']"));
    private static final String BUILTIN_CSS = buildCss(BUILTIN_SELECTORS);
    private static final ThreadLocal<RuleMatchScratch> RULE_MATCH_SCRATCH =
            new ThreadLocal<RuleMatchScratch>();

    private static final RuleSet EMPTY_RULES = RuleSet.empty();

    // Request callbacks can arrive on several renderer threads. LongAdder keeps the diagnostic
    // counters off the matcher lock/CAS hot spot; these counters never affect a block decision.
    private final LongAdder blockedCount = new LongAdder();
    private final LongAdder allowedCount = new LongAdder();
    private final LongAdder inspectedCount = new LongAdder();
    private volatile RuleSet rules = EMPTY_RULES;

    // Keep the built-in list as one compact constant. Splitting once at class load avoids a
    // HashSet object graph and dozens of independent DEX string entries; requests use only the
    // packed representation below.
    private static final String BUILTIN_BLOCKED_HOSTS =
            "doubleclick.net|googlesyndication.com|googleadservices.com|adservice.google.com|" +
            "2mdn.net|adnxs.com|adsrvr.org|advertising.com|adform.net|taboola.com|" +
            "taboolasyndication.com|outbrain.com|outbrainimg.com|criteo.com|criteo.net|" +
            "scorecardresearch.com|quantserve.com|hotjar.com|mouseflow.com|fullstory.com|" +
            "mixpanel.com|segment.io|amplitude.com|branch.io|appsflyer.com|adjust.com|" +
            "unityads.unity3d.com|ads-twitter.com|amazon-adsystem.com|rubiconproject.com|" +
            "pubmatic.com|openx.net|smartadserver.com|moatads.com|zedo.com|yieldmo.com|" +
            "teads.tv|mathtag.com|chartbeat.net|nr-data.net|demdex.net|omtrdc.net|" +
            "everesttech.net|bluekai.com|krxd.net|rlcdn.com|casalemedia.com|lijit.com|" +
            "bidswitch.net|contextweb.com|serving-sys.com|flashtalking.com|innovid.com|" +
            "spotxchange.com|springserve.com|33across.com|sharethrough.com|revcontent.com|" +
            "mgid.com|adroll.com|popads.net|popcash.net|propellerads.com|exoclick.com|" +
            "trafficjunky.com|adcolony.com|vungle.com|inmobi.com|applovin.com|ironsrc.com|" +
            "kochava.com|singular.net|pushwoosh.com|leanplum.com|optimizely.com|crazyegg.com|" +
            "heapanalytics.com|clarity.ms|logrocket.com|yandexadexchange.net|adkernel.com|" +
            "bidr.io|districtm.io|yieldlab.net|adition.com|plista.com|tradedoubler.com|" +
            "cpro.baidu.com|pos.baidu.com|eclick.baidu.com|tanx.com|alimama.com|gdt.qq.com|" +
            "e.qq.com|ad.toutiao.com|mobads.baidu.com|miaozhen.com|ipinyou.com|allyes.com|" +
            "adview.cn|domob.cn|youmi.net|3vvjqio.com|lq4y0kr.com";
    // The built-in list is hit for every request. Keep a packed copy so ordinary hosts do not
    // allocate one substring per DNS label while probing the source list.
    private static final PackedDomainSet BUILTIN_BLOCKED_PACKED = PackedDomainSet.from(
            Arrays.asList(BUILTIN_BLOCKED_HOSTS.split("\\|")));
    private static final int BUILTIN_ENDPOINT_RULE_COUNT = 5;
    // Two tiny direct-mapped tables replace the old bounded ConcurrentHashMap.  The latter had
    // to clear the whole map at the limit, so a page with many distinct hosts paid a burst of
    // allocations and re-scanned the packed million-rule table.  A host is stored only in the
    // table matching its decision; stale slots can therefore never change a result, even though
    // request callbacks run concurrently on renderer threads.
    // 1024 slots keep the hot host set of a modern page resident without allocating a map or
    // retaining request URLs. Two reference arrays cost only a few extra KiB per immutable rule
    // snapshot and avoid repeated million-rule suffix probes when CDN hosts collide.
    private static final int DOMAIN_DECISION_CACHE_LIMIT = 1024;
    private static final int DOMAIN_DECISION_CACHE_MASK = DOMAIN_DECISION_CACHE_LIMIT - 1;

    private static final class NetworkRule {
        int id = -1;
        String pattern;
        String matchPattern = "";
        String leadingDomain = "";
        Pattern regex;
        boolean allow;
        boolean thirdParty;
        boolean firstParty;
        boolean domainAnchor;
        boolean domainBoundary;
        boolean startAnchor;
        boolean endAnchor;
        boolean unsupportedAction;
        int includedTypes;
        int excludedTypes;
        // EasyList-scale inputs contain mostly unconstrained rules. Two eager empty HashSets per
        // rule waste tens of megabytes, so domain containers exist only when $domain is present.
        Set<String> includeDomains;
        Set<String> excludeDomains;
    }

    /** Request-local metadata: unrelated URL buckets do not need type or party inference. */
    private static final class RequestMatchContext {
        final Uri request;
        final String accept;
        final boolean mainFrame;
        final String requestHost;
        final String rawPageHost;
        String page;
        int type;
        boolean pageComputed;
        boolean typeComputed;
        boolean partyComputed;
        boolean thirdParty;

        RequestMatchContext(Uri request, String accept, boolean mainFrame, String requestHost,
                            String rawPageHost) {
            this.request = request;
            this.accept = accept;
            this.mainFrame = mainFrame;
            this.requestHost = requestHost;
            this.rawPageHost = rawPageHost;
        }

        RequestMatchContext(String requestHost, String pageHost, int type,
                            String requestSiteKey, String pageSiteKey) {
            this(null, "", false, requestHost, pageHost);
            // The pure matcher overloads already receive the caller's page/type context.
            this.page = pageHost;
            this.type = type;
            this.pageComputed = true;
            this.typeComputed = true;
            if (requestSiteKey != null && pageSiteKey != null) {
                this.partyComputed = true;
                this.thirdParty = pageHost == null || pageHost.length() == 0 ||
                        !requestSiteKey.equals(pageSiteKey);
            }
        }

        String page() {
            if (!pageComputed) {
                page = normalize(rawPageHost);
                pageComputed = true;
            }
            return page;
        }

        int type() {
            if (!typeComputed) {
                type = inferType(request, accept, mainFrame);
                typeComputed = true;
            }
            return type;
        }

        boolean thirdParty() {
            if (!partyComputed) {
                thirdParty = isThirdPartyNormalized(requestHost, page());
                partyComputed = true;
            }
            return thirdParty;
        }
    }

    /** Per-thread exact bucket suppression without allocating a boxed HashSet per request. */
    private static final class RuleMatchScratch {
        private int[] bucketKeys = new int[256];
        private int[] marks = new int[256];
        private int generation;
        private int bucketCount;

        void begin() {
            generation++;
            if (generation == 0) {
                Arrays.fill(marks, 0);
                generation = 1;
            }
            bucketCount = 0;
        }

        boolean alreadySeenBucket(int bucket) {
            if (bucket < 0) return false;
            int slot = bucketSlot(bucket, marks.length - 1);
            while (marks[slot] == generation) {
                if (bucketKeys[slot] == bucket) return true;
                slot = (slot + 1) & (marks.length - 1);
            }
            if (bucketCount >= marks.length / 2) {
                // Memory remains bounded per renderer thread. At capacity, an unremembered
                // bucket is matched again rather than skipped; saturation changes only cost.
                if (marks.length >= RULE_SCRATCH_LIMIT) return false;
                growBuckets();
                slot = bucketSlot(bucket, marks.length - 1);
                while (marks[slot] == generation) slot = (slot + 1) & (marks.length - 1);
            }
            marks[slot] = generation;
            bucketKeys[slot] = bucket;
            bucketCount++;
            return false;
        }

        private void growBuckets() {
            int[] previousKeys = bucketKeys;
            int[] previousMarks = marks;
            bucketKeys = new int[previousKeys.length << 1];
            marks = new int[previousMarks.length << 1];
            int mask = marks.length - 1;
            for (int i = 0; i < previousMarks.length; i++) {
                if (previousMarks[i] != generation) continue;
                int bucket = previousKeys[i];
                int slot = bucketSlot(bucket, mask);
                while (marks[slot] == generation) slot = (slot + 1) & mask;
                marks[slot] = generation;
                bucketKeys[slot] = bucket;
            }
        }

        private static int bucketSlot(int bucket, int mask) {
            int mixed = bucket ^ (bucket >>> 16);
            mixed *= 0x7feb352d;
            mixed ^= mixed >>> 15;
            return mixed & mask;
        }
    }

    /** Compact primitive-key table: request matching performs no substring or boxed-key allocation. */
    private static final class IntRuleIndex {
        private final int[] keys;
        private final int[] starts;
        private final int[] lengths;
        private final NetworkRule[] candidates;
        private final int mask;

        IntRuleIndex(Map<Integer, List<NetworkRule>> source) {
            int required = Math.max(2, source.size() + source.size() / 2);
            int capacity = 2;
            while (capacity < required) capacity <<= 1;
            keys = new int[capacity];
            starts = new int[capacity];
            lengths = new int[capacity];
            mask = capacity - 1;
            int candidateCount = 0;
            for (List<NetworkRule> bucket : source.values()) candidateCount += bucket.size();
            candidates = new NetworkRule[candidateCount];
            int cursor = 0;
            for (Map.Entry<Integer, List<NetworkRule>> entry : source.entrySet()) {
                int key = entry.getKey().intValue();
                int slot = slot(key);
                while (lengths[slot] != 0) slot = (slot + 1) & mask;
                keys[slot] = key;
                List<NetworkRule> bucket = entry.getValue();
                starts[slot] = cursor;
                lengths[slot] = bucket.size();
                for (NetworkRule rule : bucket) candidates[cursor++] = rule;
            }
        }

        int find(int key) {
            int slot = slot(key);
            while (lengths[slot] != 0) {
                if (keys[slot] == key) return slot;
                slot = (slot + 1) & mask;
            }
            return -1;
        }

        int startAt(int slot) { return starts[slot]; }
        int lengthAt(int slot) { return lengths[slot]; }
        NetworkRule candidateAt(int index) { return candidates[index]; }

        private int slot(int key) {
            int mixed = key ^ (key >>> 16);
            mixed *= 0x7feb352d;
            mixed ^= mixed >>> 15;
            return mixed & mask;
        }
    }

    /** Sorted, contiguous domain table: about 2 bytes per character plus 4 bytes per rule. */
    private static final class PackedDomainSet {
        private static final PackedDomainSet EMPTY = new PackedDomainSet(new char[0], new int[] { 0 },
                new long[0], -1);
        private final char[] characters;
        private final int[] offsets;
        private final long[] bloom;
        private final int bloomMask;
        private PackedDomainSet(char[] characters, int[] offsets, long[] bloom, int bloomMask) {
            this.characters = characters;
            this.offsets = offsets;
            this.bloom = bloom;
            this.bloomMask = bloomMask;
        }

        static PackedDomainSet from(List<String> source) {
            if (source == null || source.isEmpty()) return EMPTY;
            Collections.sort(source);
            int count = 0;
            int characterCount = 0;
            String previous = null;
            for (String domain : source) {
                if (domain == null || domain.equals(previous)) continue;
                count++;
                characterCount += domain.length();
                previous = domain;
            }
            if (count == 0) return EMPTY;
            int bloomBits = 64;
            long desiredBits = Math.max(64L, (long) count * 8L);
            while (bloomBits < desiredBits && bloomBits < (1 << 27)) bloomBits <<= 1;
            long[] bloom = new long[(bloomBits + 63) >>> 6];
            char[] characters = new char[characterCount];
            int[] offsets = new int[count + 1];
            int item = 0;
            int cursor = 0;
            previous = null;
            // Copy and hash in one pass. Building a million-domain filter must not walk the
            // packed character table a second time just to populate the Bloom prefilter.
            for (String domain : source) {
                if (domain == null || domain.equals(previous)) continue;
                offsets[item++] = cursor;
                int hash = 0;
                for (int i = 0; i < domain.length(); i++) {
                    char value = domain.charAt(i);
                    characters[cursor++] = value;
                    hash = 31 * hash + value;
                }
                setBloomBits(bloom, bloomBits - 1, hash);
                previous = domain;
            }
            offsets[item] = cursor;
            // Bloom filtering rejects almost every miss. A full binary search after a positive
            // probe is cheaper than retaining a 65,537-entry prefix table per snapshot.
            return new PackedDomainSet(characters, offsets, bloom, bloomBits - 1);
        }

        int size() { return offsets.length - 1; }

        boolean matches(String host) {
            if (host == null || host.length() == 0 || offsets.length <= 1) return false;
            // Build all suffix hashes in one reverse pass. The old implementation rehashed the
            // complete host once per label, turning a long hostname into avoidable O(n^2) work.
            int hash = 0;
            int power = 1;
            for (int i = host.length() - 1; i >= 0; i--) {
                hash = host.charAt(i) * power + hash;
                power *= 31;
                if (i == 0 || host.charAt(i - 1) == '.') {
                    if (mightContain(hash) && contains(host, i)) return true;
                }
            }
            return false;
        }

        private boolean mightContain(int hash) {
            if (bloomMask < 0) return false;
            return hasBloomBits(bloom, bloomMask, hash);
        }

        private static void setBloomBits(long[] bits, int mask, int hash) {
            int mixed = mix(hash);
            int step = (mixed >>> 16) | 1;
            int first = mixed & mask;
            int second = (mixed + step) & mask;
            int third = (mixed + step + step) & mask;
            bits[first >>> 6] |= 1L << (first & 63);
            bits[second >>> 6] |= 1L << (second & 63);
            bits[third >>> 6] |= 1L << (third & 63);
        }

        private static boolean hasBloomBits(long[] bits, int mask, int hash) {
            int mixed = mix(hash);
            int step = (mixed >>> 16) | 1;
            int first = mixed & mask;
            int second = (mixed + step) & mask;
            int third = (mixed + step + step) & mask;
            return (bits[first >>> 6] & (1L << (first & 63))) != 0L &&
                    (bits[second >>> 6] & (1L << (second & 63))) != 0L &&
                    (bits[third >>> 6] & (1L << (third & 63))) != 0L;
        }

        private static int mix(int value) {
            value ^= value >>> 16;
            value *= 0x7feb352d;
            value ^= value >>> 15;
            return value;
        }

        private boolean contains(String value, int start) {
            int low = 0;
            int high = offsets.length - 2;
            if (low > high) return false;
            while (low <= high) {
                int middle = (low + high) >>> 1;
                int comparison = compare(value, start, middle);
                if (comparison == 0) return true;
                if (comparison < 0) high = middle - 1;
                else low = middle + 1;
            }
            return false;
        }

        private int compare(String value, int valueStart, int item) {
            int domainStart = offsets[item];
            int domainLength = offsets[item + 1] - domainStart;
            int valueLength = value.length() - valueStart;
            int length = Math.min(domainLength, valueLength);
            for (int i = 0; i < length; i++) {
                char left = value.charAt(valueStart + i);
                char right = characters[domainStart + i];
                if (left != right) return left < right ? -1 : 1;
            }
            return valueLength == domainLength ? 0 : (valueLength < domainLength ? -1 : 1);
        }

    }

    private static final class RuleSet {
        final PackedDomainSet blockedDomains;
        final PackedDomainSet allowedDomains;
        final IntRuleIndex blockedIndex;
        final IntRuleIndex allowedIndex;
        final List<NetworkRule> genericBlocked;
        final List<NetworkRule> genericAllowed;
        final List<String> globalCosmetic;
        final List<String> customGlobalCosmetic;
        final Set<String> globalCosmeticExceptions;
        final Map<String, List<String>> domainCosmetic;
        final Map<String, List<String>> customDomainCosmetic;
        final Map<String, Set<String>> domainCosmeticExceptions;
        final ConcurrentHashMap<String, String> cosmeticCssCache = new ConcurrentHashMap<String, String>();
        final int networkCount;
        final int cosmeticCount;
        final int sourceLines;
        final boolean capacityLimited;
        final boolean hasPatternRules;
        final boolean hasAllowedPatternRules;
        final boolean hasTypeRules;
        final boolean hasPartyRules;
        final boolean hasPageDomainRules;
        // A page commonly requests the same CDN host dozens of times. These arrays are owned by
        // the immutable snapshot, so replacing rules automatically invalidates them. Plain
        // reference writes are sufficient: every value in a table represents the same decision,
        // and observing an older slot can only cause a harmless cache miss.
        final String[] blockedDomainCache = new String[DOMAIN_DECISION_CACHE_LIMIT];
        final String[] allowedDomainCache = new String[DOMAIN_DECISION_CACHE_LIMIT];

        RuleSet(List<String> blockedDomains, List<String> allowedDomains,
                Map<Integer, List<NetworkRule>> blockedIndex, Map<Integer, List<NetworkRule>> allowedIndex,
                List<NetworkRule> genericBlocked, List<NetworkRule> genericAllowed,
                List<String> globalCosmetic, List<String> customGlobalCosmetic, Set<String> globalCosmeticExceptions,
                Map<String, List<String>> domainCosmetic, Map<String, List<String>> customDomainCosmetic,
                Map<String, Set<String>> domainCosmeticExceptions,
                int networkCount, int cosmeticCount, int sourceLines, boolean capacityLimited) {
            boolean patternRules = !blockedIndex.isEmpty() || !allowedIndex.isEmpty() ||
                    !genericBlocked.isEmpty() || !genericAllowed.isEmpty();
            boolean allowedPatternRules = !allowedIndex.isEmpty() || !genericAllowed.isEmpty();
            this.blockedDomains = PackedDomainSet.from(blockedDomains);
            this.allowedDomains = PackedDomainSet.from(allowedDomains);
            blockedDomains.clear();
            allowedDomains.clear();
            this.blockedIndex = new IntRuleIndex(blockedIndex);
            this.allowedIndex = new IntRuleIndex(allowedIndex);
            blockedIndex.clear();
            allowedIndex.clear();
            this.genericBlocked = Collections.unmodifiableList(genericBlocked);
            this.genericAllowed = Collections.unmodifiableList(genericAllowed);
            this.globalCosmetic = Collections.unmodifiableList(globalCosmetic);
            this.customGlobalCosmetic = Collections.unmodifiableList(customGlobalCosmetic);
            this.globalCosmeticExceptions = Collections.unmodifiableSet(globalCosmeticExceptions);
            this.domainCosmetic = Collections.unmodifiableMap(domainCosmetic);
            this.customDomainCosmetic = Collections.unmodifiableMap(customDomainCosmetic);
            this.domainCosmeticExceptions = Collections.unmodifiableMap(domainCosmeticExceptions);
            this.networkCount = networkCount;
            this.cosmeticCount = cosmeticCount;
            this.sourceLines = sourceLines;
            this.capacityLimited = capacityLimited;
            this.hasPatternRules = patternRules;
            this.hasAllowedPatternRules = allowedPatternRules;
            boolean typeRules = false;
            boolean partyRules = false;
            boolean pageDomainRules = false;
            for (NetworkRule rule : this.blockedIndex.candidates) {
                if (rule.includedTypes != 0 || rule.excludedTypes != 0) typeRules = true;
                if (rule.thirdParty || rule.firstParty) partyRules = true;
                if (rule.includeDomains != null || rule.excludeDomains != null) pageDomainRules = true;
                if (typeRules && partyRules && pageDomainRules) break;
            }
            if (!typeRules || !partyRules || !pageDomainRules) {
                for (NetworkRule rule : this.allowedIndex.candidates) {
                    if (rule.includedTypes != 0 || rule.excludedTypes != 0) typeRules = true;
                    if (rule.thirdParty || rule.firstParty) partyRules = true;
                    if (rule.includeDomains != null || rule.excludeDomains != null) pageDomainRules = true;
                    if (typeRules && partyRules && pageDomainRules) break;
                }
            }
            if (!typeRules || !partyRules || !pageDomainRules) {
                for (NetworkRule rule : this.genericBlocked) {
                    if (rule.includedTypes != 0 || rule.excludedTypes != 0) typeRules = true;
                    if (rule.thirdParty || rule.firstParty) partyRules = true;
                    if (rule.includeDomains != null || rule.excludeDomains != null) pageDomainRules = true;
                    if (typeRules && partyRules && pageDomainRules) break;
                }
            }
            if (!typeRules || !partyRules || !pageDomainRules) {
                for (NetworkRule rule : this.genericAllowed) {
                    if (rule.includedTypes != 0 || rule.excludedTypes != 0) typeRules = true;
                    if (rule.thirdParty || rule.firstParty) partyRules = true;
                    if (rule.includeDomains != null || rule.excludeDomains != null) pageDomainRules = true;
                    if (typeRules && partyRules && pageDomainRules) break;
                }
            }
            this.hasTypeRules = typeRules;
            this.hasPartyRules = partyRules;
            this.hasPageDomainRules = pageDomainRules;
        }

        static RuleSet empty() {
            return new Builder().finish();
        }
    }

    private static final class Builder {
        final ArrayList<String> blockedDomains = new ArrayList<String>();
        final ArrayList<String> allowedDomains = new ArrayList<String>();
        final HashMap<Integer, List<NetworkRule>> blockedIndex = new HashMap<Integer, List<NetworkRule>>();
        final HashMap<Integer, List<NetworkRule>> allowedIndex = new HashMap<Integer, List<NetworkRule>>();
        final ArrayList<NetworkRule> genericBlocked = new ArrayList<NetworkRule>();
        final ArrayList<NetworkRule> genericAllowed = new ArrayList<NetworkRule>();
        final ArrayList<String> globalCosmetic = new ArrayList<String>();
        final ArrayList<String> customGlobalCosmetic = new ArrayList<String>();
        final HashSet<String> globalCosmeticExceptions = new HashSet<String>();
        final HashMap<String, List<String>> domainCosmetic = new HashMap<String, List<String>>();
        final HashMap<String, List<String>> customDomainCosmetic = new HashMap<String, List<String>>();
        final HashMap<String, Set<String>> domainCosmeticExceptions = new HashMap<String, Set<String>>();
        int networkCount;
        int indexedNetworkCount;
        int nextRuleId;
        int cosmeticCount;
        int sourceLines;
        boolean capacityLimited;

        boolean hasDomainCapacity() {
            boolean available = blockedDomains.size() + allowedDomains.size() < MAX_PACKED_DOMAIN_RULES;
            if (!available) capacityLimited = true;
            return available;
        }

        RuleSet finish() {
            return new RuleSet(blockedDomains, allowedDomains, blockedIndex, allowedIndex,
                    genericBlocked, genericAllowed, globalCosmetic, customGlobalCosmetic, globalCosmeticExceptions,
                    domainCosmetic, customDomainCosmetic, domainCosmeticExceptions, networkCount, cosmeticCount, sourceLines,
                    capacityLimited);
        }
    }

    boolean shouldBlock(String requestUrl, String pageHost) {
        if (requestUrl == null || requestUrl.length() == 0) return false;
        try { return shouldBlock(Uri.parse(requestUrl), pageHost, "", false); }
        catch (RuntimeException ignored) { return false; }
    }

    boolean shouldBlock(Uri request, String pageHost) {
        return shouldBlock(request, pageHost, "", false);
    }

    /** Checks an explicit exception without changing request counters or domain decisions. */
    boolean isExplicitlyAllowed(Uri request, String pageHost, String acceptHeader, boolean mainFrame) {
        if (request == null) return false;
        try {
            String scheme = request.getScheme();
            if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) return false;
            String host = normalize(request.getHost());
            if (host.length() == 0) return false;
            RuleSet snapshot = rules;
            if (snapshot.allowedDomains.matches(host)) return true;
            if (!snapshot.hasAllowedPatternRules) return false;
            String rawUrl = request.toString();
            if (rawUrl.length() > MAX_REQUEST_URL) return false;
            RequestMatchContext context = requestMatchContext(snapshot, request, acceptHeader,
                    mainFrame, host, pageHost);
            return matchesIndexed(snapshot.allowedIndex, snapshot.genericAllowed,
                    lowercaseIfNeeded(rawUrl), host, context);
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private static boolean matchesExplicitException(RuleSet snapshot, String rawUrl, String host,
                                                     String pageHost, int type) {
        if (snapshot.allowedDomains.matches(host)) return true;
        if (!snapshot.hasAllowedPatternRules || rawUrl.length() > MAX_REQUEST_URL) return false;
        RequestMatchContext context = new RequestMatchContext(null, "", false, host, pageHost);
        context.type = type;
        context.typeComputed = true;
        return matchesIndexed(snapshot.allowedIndex, snapshot.genericAllowed,
                lowercaseIfNeeded(rawUrl), host, context);
    }

    boolean shouldBlock(Uri request, String pageHost, String acceptHeader, boolean mainFrame) {
        if (request == null) return false;
        try {
            String scheme = request.getScheme();
            if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) return false;
            String host = normalize(request.getHost());
            if (host.length() == 0) return false;
            inspectedCount.increment();
            RuleSet snapshot = rules;
            if (snapshot.allowedDomains.matches(host)) {
                allowedCount.increment();
                return false;
            }
            // ID-mapping JSONP, empty exposure pixels and analytics tags can hold the document
            // load event open while their tracking hosts time out. Match only these endpoints,
            // never the host or navigation; explicit exceptions retain their usual priority.
            boolean builtinEndpoint = !mainFrame && builtinEndpointDecision(request, host);
            // A pure hosts/domain subscription is the common million-rule case. Avoid creating
            // the full URL, lower-casing it, and inferring a resource type when no pattern rule
            // can consume those values.
            if (!snapshot.hasPatternRules) {
                if (builtinEndpoint || domainDecision(snapshot, host)) {
                    blockedCount.increment();
                    return true;
                }
                return false;
            }
            // A blocked domain cannot be exempted by a pattern when the allow index is empty.
            // Resolve that common mixed-subscription case before materializing the full URL.
            if (!snapshot.hasAllowedPatternRules && (builtinEndpoint || domainDecision(snapshot, host))) {
                blockedCount.increment();
                return true;
            }
            String rawUrl = request.toString();
            if (rawUrl.length() > MAX_REQUEST_URL) {
                if (domainDecision(snapshot, host)) {
                    blockedCount.increment();
                    return true;
                }
                return false;
            }
            RequestMatchContext context = requestMatchContext(snapshot, request, acceptHeader,
                    mainFrame, host, pageHost);
            String full = lowercaseIfNeeded(rawUrl);
            if (matchesIndexed(snapshot.allowedIndex, snapshot.genericAllowed, full, host, context)) {
                allowedCount.increment();
                return false;
            }
            if (builtinEndpoint || domainDecision(snapshot, host) ||
                    matchesIndexed(snapshot.blockedIndex, snapshot.genericBlocked, full, host, context)) {
                blockedCount.increment();
                return true;
            }
        } catch (RuntimeException ignored) {
            return false;
        }
        return false;
    }

    private static RequestMatchContext requestMatchContext(RuleSet snapshot, Uri request,
                                                            String acceptHeader, boolean mainFrame,
                                                            String host, String pageHost) {
        // The immutable snapshot inspects every candidate when computing these flags. With no
        // constraints, matchesRule never reads context, so URL-only lists allocate none.
        if (!snapshot.hasTypeRules && !snapshot.hasPartyRules && !snapshot.hasPageDomainRules)
            return null;
        return new RequestMatchContext(request, acceptHeader, mainFrame, host, pageHost);
    }

    private static boolean builtinEndpointDecision(Uri request, String host) {
        if ("ext.baidu.com".equals(host)) {
            return "/rest/id-mapping/cuid".equals(request.getEncodedPath());
        }
        if ("www.googletagmanager.com".equals(host)) {
            return "/gtag/js".equals(request.getEncodedPath());
        }
        if ("m.baidu.com".equals(host)) {
            // /tc also handles real outbound navigation. Only its literal logging marker
            // identifies this empty image request; encoded or similar keys are left alone.
            return "/tc".equals(request.getEncodedPath()) &&
                    hasLiteralQueryKey(request.getEncodedQuery(), "tcreq4log");
        }
        if (!"sp1.baidu.com".equals(host)) return false;
        String path = request.getEncodedPath();
        return "/5b1ZeDe5KgQFm2e88IuM_a/mwb.gif".equals(path) ||
                "/5b1ZeDe5KgQFm2e88IuM_a/webb.gif".equals(path);
    }

    private static boolean hasLiteralQueryKey(String query, String key) {
        if (query == null) return false;
        int length = key.length();
        for (int at = query.indexOf(key); at >= 0; at = query.indexOf(key, at + length)) {
            int end = at + length;
            if ((at == 0 || query.charAt(at - 1) == '&') &&
                    (end == query.length() || query.charAt(end) == '=' || query.charAt(end) == '&'))
                return true;
        }
        return false;
    }

    private static boolean domainDecision(RuleSet snapshot, String host) {
        int hash = host.hashCode();
        int slot = (hash ^ (hash >>> 16)) & DOMAIN_DECISION_CACHE_MASK;
        String remembered = snapshot.blockedDomainCache[slot];
        if (host == remembered || (remembered != null && host.equals(remembered))) return true;
        remembered = snapshot.allowedDomainCache[slot];
        if (host == remembered || (remembered != null && host.equals(remembered))) return false;
        boolean blocked = BUILTIN_BLOCKED_PACKED.matches(host) || snapshot.blockedDomains.matches(host);
        if (blocked) snapshot.blockedDomainCache[slot] = host;
        else snapshot.allowedDomainCache[slot] = host;
        return blocked;
    }

    int getBlockedCount() { return blockedCount.intValue(); }

    /**
     * Request headers are only consumed when a compiled rule can use resource type, party, or
     * domain metadata. Callers can skip the provider IPC for plain URL/domain-only snapshots.
     */
    boolean requiresRequestHeaders() {
        return rules.hasTypeRules;
    }

    Stats getStats() {
        RuleSet snapshot = rules;
        return new Stats(snapshot.networkCount + BUILTIN_BLOCKED_PACKED.size() + BUILTIN_ENDPOINT_RULE_COUNT,
                snapshot.cosmeticCount,
                snapshot.sourceLines, inspectedCount.sum(), blockedCount.intValue(), allowedCount.intValue(),
                snapshot.capacityLimited);
    }

    int updateCustomRules(String raw) {
        return updateRules(raw, Collections.<String>emptyList());
    }

    int updateRules(String custom, List<String> subscriptionSources) {
        CompiledRules compiled = compileRules(custom, subscriptionSources);
        applyCompiledRules(compiled);
        return compiled.count;
    }

    /** An unpublished immutable rule snapshot, built without blocking request callbacks. */
    static final class CompiledRules {
        private final RuleSet snapshot;
        final int count;

        private CompiledRules(RuleSet snapshot, int count) {
            this.snapshot = snapshot;
            this.count = count;
        }
    }

    CompiledRules compileRules(String custom, List<String> subscriptionSources) {
        return compileRules(custom, subscriptionSources, null);
    }

    /** Returns no snapshot if newer edits or teardown make this compilation obsolete. */
    CompiledRules compileRules(String custom, List<String> subscriptionSources, BooleanSupplier cancelled) {
        Builder builder = new Builder();
        if (!parseSource(custom, builder, true, cancelled)) return null;
        if (subscriptionSources != null) {
            for (String source : subscriptionSources) {
                if (builder.sourceLines >= MAX_LINES) break;
                if (!parseSource(source, builder, false, cancelled)) return null;
            }
        }
        if (isCompilationCancelled(cancelled)) return null;
        return new CompiledRules(builder.finish(), builder.networkCount + builder.cosmeticCount);
    }

    private static boolean isCompilationCancelled(BooleanSupplier cancelled) {
        return cancelled != null && (Thread.currentThread().isInterrupted() || cancelled.getAsBoolean());
    }

    void applyCompiledRules(CompiledRules compiled) {
        if (compiled != null) rules = compiled.snapshot;
    }

    /** Drops data that is cheap to regenerate without discarding compiled filtering rules. */
    void trimMemory() {
        rules.cosmeticCssCache.clear();
        RULE_MATCH_SCRATCH.remove();
    }

    boolean requiresEarlyCosmetic(String host) {
        String normalized = normalize(host);
        if (!allowsCosmeticFiltering(normalized)) return false;
        RuleSet snapshot = rules;
        // Built-in selectors are available before subscriptions finish compiling and are part of
        // the first-document protection contract. Keep ordinary hosts on the document-start path
        // so common ad containers do not flash during the initial paint. YouTube remains excluded
        // by allowsCosmeticFiltering() because its player is sensitive to generic selectors.
        return !BUILTIN_SELECTORS.isEmpty() || snapshot.cosmeticCount > 0;
    }

    /**
     * Returns whether this host is eligible for CSS cosmetic filtering. Kept separate from
     * cosmeticCssForHost() so callers can avoid scheduling an empty renderer script for hosts
     * that are intentionally excluded (for example, YouTube's player pages).
     */
    boolean cosmeticFilteringAllowed(String host) {
        return allowsCosmeticFiltering(normalize(host));
    }

    String cosmeticCssForHost(String host) {
        String normalized = normalize(host);
        if (!allowsCosmeticFiltering(normalized)) return "";
        RuleSet snapshot = rules;
        String remembered = snapshot.cosmeticCssCache.get(normalized);
        if (remembered != null) return remembered;
        // The built-ins are enabled for ordinary hosts even when no subscription or custom
        // cosmetic rules exist. Reuse one immutable CSS string instead of allocating the
        // exception, selector, and builder objects on every first host lookup.
        if (snapshot.globalCosmeticExceptions.isEmpty() && snapshot.domainCosmeticExceptions.isEmpty() &&
                snapshot.customGlobalCosmetic.isEmpty() && snapshot.customDomainCosmetic.isEmpty() &&
                snapshot.globalCosmetic.isEmpty() && snapshot.domainCosmetic.isEmpty()) {
            return BUILTIN_CSS;
        }
        HashSet<String> exceptions = new HashSet<String>(snapshot.globalCosmeticExceptions);
        collectDomainSets(normalized, snapshot.domainCosmeticExceptions, exceptions);
        ArrayList<String> selectors = new ArrayList<String>();
        HashSet<String> seen = new HashSet<String>();
        // Explicit rules, including a parent-domain manual rule on a busy subdomain, must not
        // lose their slots to subscription rules. Site rules precede generic subscription CSS.
        collectDomainSelectors(normalized, snapshot.customDomainCosmetic, exceptions, selectors, seen, MAX_SELECTORS_PER_PAGE);
        addSelectors(selectors, seen, snapshot.customGlobalCosmetic, exceptions, MAX_SELECTORS_PER_PAGE);
        collectDomainSelectors(normalized, snapshot.domainCosmetic, exceptions, selectors, seen, MAX_SELECTORS_PER_PAGE);
        addSelectors(selectors, seen, BUILTIN_SELECTORS, exceptions, MAX_SELECTORS_PER_PAGE);
        addSelectors(selectors, seen, snapshot.globalCosmetic, exceptions, MAX_SELECTORS_PER_PAGE);
        StringBuilder css = new StringBuilder(Math.min(65536, selectors.size() * 45));
        // Keep selectors isolated: one unsupported selector must not invalidate a whole CSS group.
        for (String selector : selectors) css.append(selector).append("{display:none!important;}\n");
        String result = css.toString();
        if (result.length() > MAX_COSMETIC_CACHE_VALUE) return result;
        if (snapshot.cosmeticCssCache.size() >= MAX_COSMETIC_CACHE_ENTRIES) {
            // Evict one entry rather than clearing the complete cache. Concurrent renderer
            // callbacks may otherwise all miss and rebuild CSS after the same clear operation.
            Iterator<String> keys = snapshot.cosmeticCssCache.keySet().iterator();
            String evict = keys.hasNext() ? keys.next() : null;
            if (evict != null) snapshot.cosmeticCssCache.remove(evict);
        }
        String existing = snapshot.cosmeticCssCache.putIfAbsent(normalized, result);
        return existing == null ? result : existing;
    }

    private static String buildCss(List<String> selectors) {
        StringBuilder css = new StringBuilder(selectors.size() * 45);
        for (String selector : selectors) css.append(selector).append("{display:none!important;}\n");
        return css.toString();
    }

    private static boolean allowsCosmeticFiltering(String host) {
        return host.length() > 0 && !isDomainOrSubdomain(host, "youtube.com") &&
                !isDomainOrSubdomain(host, "youtu.be") && !isDomainOrSubdomain(host, "youtube-nocookie.com");
    }

    private static boolean parseSource(String raw, Builder builder, boolean custom, BooleanSupplier cancelled) {
        if (isCompilationCancelled(cancelled)) return false;
        if (raw == null || raw.length() == 0) return true;
        int start = 0;
        while (start <= raw.length() && builder.sourceLines < MAX_LINES) {
            // Do not finish a superseded million-line subscription before applying the next edit.
            // Sampling keeps normal parsing cheap; no partial rule set is ever published.
            if ((builder.sourceLines & 255) == 0 && isCompilationCancelled(cancelled)) return false;
            int end = raw.indexOf('\n', start);
            if (end < 0) end = raw.length();
            String line = raw.substring(start, end).trim();
            builder.sourceLines++;
            if (line.length() >= 3 && line.length() <= 4096 && !line.startsWith("!") && !line.startsWith("[")) {
                if (!parseCosmetic(line, builder, custom)) parseNetwork(line, builder);
            }
            if (end == raw.length()) return true;
            start = end + 1;
        }
        if (start <= raw.length()) builder.capacityLimited = true;
        return true;
    }

    private static boolean parseCosmetic(String line, Builder builder, boolean custom) {
        if (line.contains("#$#") || line.contains("#%#")) return true;
        int marker = line.indexOf("#@#");
        boolean exception = marker >= 0;
        int markerLength = exception ? 3 : 2;
        if (marker < 0) marker = line.indexOf("#?#");
        if (marker >= 0 && !exception) markerLength = 3;
        if (marker < 0) marker = line.indexOf("##");
        if (marker < 0) return false;
        if (builder.cosmeticCount >= MAX_COSMETIC_RULES) {
            builder.capacityLimited = true;
            return true;
        }
        String domains = line.substring(0, marker).trim();
        String selector = line.substring(marker + markerLength).trim();
        if (!safeSelector(selector)) return true;
        List<String> global = custom ? builder.customGlobalCosmetic : builder.globalCosmetic;
        Map<String, List<String>> domainRules = custom ? builder.customDomainCosmetic : builder.domainCosmetic;
        if (domains.length() == 0) {
            if (exception) builder.globalCosmeticExceptions.add(selector); else global.add(selector);
            builder.cosmeticCount++;
            return true;
        }
        String[] values = domains.split(",");
        boolean hasPositiveDomain = false;
        boolean declaredPositiveDomain = false;
        boolean addedDomain = false;
        for (String value : values) {
            boolean excludedDomain = value.trim().startsWith("~");
            if (!excludedDomain) declaredPositiveDomain = true;
            String domain = normalize(excludedDomain ? value.trim().substring(1) : value);
            if (!validCosmeticDomain(domain)) continue;
            if (excludedDomain || exception) addToSetMap(builder.domainCosmeticExceptions, domain, selector);
            else { addToListMap(domainRules, domain, selector); hasPositiveDomain = true; }
            addedDomain = true;
        }
        if (!exception && !hasPositiveDomain && !declaredPositiveDomain && addedDomain)
            global.add(selector);
        if (!addedDomain) return true;
        builder.cosmeticCount++;
        return true;
    }

    private static void parseNetwork(String original, Builder builder) {
        String line = original;
        if (line.startsWith("#")) return;
        boolean allow = line.startsWith("@@");
        if (allow) line = line.substring(2);

        // Hosts-file syntax.
        if (line.startsWith("0.0.0.0 ") || line.startsWith("127.0.0.1 ") || line.startsWith("::1 ")) {
            int space = line.indexOf(' ');
            String[] hosts = line.substring(space + 1).trim().split("\\s+");
            boolean added = false;
            for (String host : hosts) {
                host = normalize(host);
                if (builder.hasDomainCapacity() && validDomain(host) && !"localhost".equals(host)) {
                    builder.blockedDomains.add(host);
                    added = true;
                }
            }
            if (added) builder.networkCount++;
            return;
        }

        String options = "";
        int optionAt = line.lastIndexOf('$');
        if (line.startsWith("/")) {
            int closingSlash = line.lastIndexOf('/');
            optionAt = closingSlash > 0 && closingSlash + 1 < line.length() && line.charAt(closingSlash + 1) == '$'
                    ? closingSlash + 1 : -1;
        }
        if (optionAt > 0) {
            options = line.substring(optionAt + 1);
            line = line.substring(0, optionAt);
        }
        line = line.trim().toLowerCase(Locale.US);
        if (line.length() < 2) return;

        // Hosts subscriptions are overwhelmingly made of bare `||host^` entries. Handle that
        // shape before allocating a NetworkRule (and its option/anchor state); a million-line
        // list otherwise creates a million short-lived objects only to immediately discard them.
        if (optionAt < 0 && line.startsWith("||") && line.endsWith("^") &&
                validDomainRange(line, 2, line.length() - 1)) {
            if (builder.hasDomainCapacity()) {
                (allow ? builder.allowedDomains : builder.blockedDomains).add(
                        line.substring(2, line.length() - 1));
                builder.networkCount++;
            }
            return;
        }

        NetworkRule rule = new NetworkRule();
        rule.pattern = line;
        if (line.startsWith("/") && line.endsWith("/") && line.length() > 2) {
            rule.regex = safeRegex(line.substring(1, line.length() - 1));
            if (rule.regex == null) return;
        }
        rule.allow = allow;
        parseOptions(options, rule);
        if (rule.unsupportedAction) return;

        String pureDomain = rule.regex == null ? pureDomainRule(line) : "";
        boolean hasConstraints = rule.thirdParty || rule.firstParty || rule.includedTypes != 0 ||
                rule.excludedTypes != 0 || rule.includeDomains != null || rule.excludeDomains != null;
        if (pureDomain.length() > 0 && !hasConstraints) {
            if (builder.hasDomainCapacity()) {
                (allow ? builder.allowedDomains : builder.blockedDomains).add(pureDomain);
                builder.networkCount++;
            }
            return;
        }

        // Only general/regex rules need the normalized pattern slices. Pure domain rules above
        // are already represented by the packed domain table and avoid these per-rule strings.
        preparePattern(rule);

        if (builder.indexedNetworkCount >= MAX_NETWORK_RULES) {
            builder.capacityLimited = true;
            return;
        }

        String keyword = rule.regex == null ? bestKeyword(line) : "";
        if (keyword.length() > 0) {
            rule.id = builder.nextRuleId++;
            addToListMap(allow ? builder.allowedIndex : builder.blockedIndex, Integer.valueOf(keywordHash(keyword, 0)), rule);
        } else {
            List<NetworkRule> generic = allow ? builder.genericAllowed : builder.genericBlocked;
            if (generic.size() >= MAX_GENERIC_RULES) {
                builder.capacityLimited = true;
                return;
            }
            rule.id = builder.nextRuleId++;
            generic.add(rule);
        }
        builder.indexedNetworkCount++;
        builder.networkCount++;
    }

    private static void parseOptions(String raw, NetworkRule rule) {
        if (raw == null || raw.length() == 0) return;
        String[] options = raw.toLowerCase(Locale.US).split(",");
        for (String option : options) {
            option = option.trim();
            if ("third-party".equals(option) || "3p".equals(option)) rule.thirdParty = true;
            else if ("~third-party".equals(option) || "~3p".equals(option)) rule.firstParty = true;
            else if (option.startsWith("domain=")) parseDomains(option.substring(7), rule);
            else if (option.equals("badfilter") || option.equals("popup") || option.equals("popunder") ||
                    option.equals("match-case") || option.equals("generichide") || option.equals("genericblock") ||
                    option.equals("elemhide") || option.startsWith("redirect") || option.startsWith("removeparam") ||
                    option.startsWith("csp") || option.startsWith("permissions") || option.startsWith("header") ||
                    option.startsWith("replace") || option.startsWith("urltransform") || option.startsWith("method=") ||
                    option.startsWith("from=") || option.startsWith("to=")) rule.unsupportedAction = true;
            else if (option.equals("important")) { /* priority is irrelevant without redirect/scriptlet actions */ }
            else {
                boolean excluded = option.startsWith("~");
                String name = excluded ? option.substring(1) : option;
                int type = "all".equals(name) ? TYPE_ALL : typeOption(name);
                if (type != 0) {
                    if (excluded) rule.excludedTypes |= type; else rule.includedTypes |= type;
                } else rule.unsupportedAction = true; // never broaden a rule by silently dropping constraints
            }
        }
    }

    private static void parseDomains(String raw, NetworkRule rule) {
        if (raw == null || raw.length() == 0) {
            rule.unsupportedAction = true;
            return;
        }
        String[] domains = raw.split("\\|");
        boolean accepted = false;
        for (String value : domains) {
            boolean excluded = value.startsWith("~");
            String domain = normalize(excluded ? value.substring(1) : value);
            if (!validDomain(domain)) {
                // Dropping an invalid domain would silently broaden the network rule.
                rule.unsupportedAction = true;
                continue;
            }
            Set<String> target = excluded ? rule.excludeDomains : rule.includeDomains;
            if (target == null) {
                target = new HashSet<String>();
                if (excluded) rule.excludeDomains = target; else rule.includeDomains = target;
            }
            target.add(domain);
            accepted = true;
        }
        if (!accepted) rule.unsupportedAction = true;
    }

    private static boolean matchesIndexed(IntRuleIndex index, List<NetworkRule> generic,
                                          String url, String requestHost, String pageHost, int type) {
        return matchesIndexed(index, generic, url, requestHost, pageHost, type, null, null);
    }

    private static boolean matchesIndexed(IntRuleIndex index, List<NetworkRule> generic,
                                          String url, String requestHost, String pageHost, int type,
                                          String requestSiteKey, String pageSiteKey) {
        return matchesIndexed(index, generic, url, requestHost,
                new RequestMatchContext(requestHost, pageHost, type, requestSiteKey, pageSiteKey));
    }

    private static boolean matchesIndexed(IntRuleIndex index, List<NetworkRule> generic,
                                          String url, String requestHost, RequestMatchContext context) {
        RuleMatchScratch checked = null;
        if (index.candidates.length > 0) {
            int start = 0;
            for (int i = 0; i <= url.length(); i++) {
                char c = i < url.length() ? url.charAt(i) : '/';
                if (isKeywordChar(c)) continue;
                if (i - start >= INDEX_KEY_LENGTH) {
                    int hash = keywordHash(url, start);
                    for (int offset = start; offset <= i - INDEX_KEY_LENGTH; offset++) {
                        if (offset > start) hash = (hash - url.charAt(offset - 1) * INDEX_ROLLING_FACTOR) * 31 +
                                url.charAt(offset + INDEX_KEY_LENGTH - 1);
                        int slot = index.find(hash);
                        if (slot >= 0) {
                            if (checked == null) {
                                checked = RULE_MATCH_SCRATCH.get();
                                if (checked == null) {
                                    checked = new RuleMatchScratch();
                                    RULE_MATCH_SCRATCH.set(checked);
                                }
                                checked.begin();
                            }
                            // Each rule belongs to one bucket, and matching consumes the whole
                            // URL rather than this keyword offset. Repeated windows cannot make
                            // a previously checked bucket yield a different result.
                            if (checked.alreadySeenBucket(slot)) continue;
                            int candidateStart = index.startAt(slot);
                            int candidateEnd = candidateStart + index.lengthAt(slot);
                            for (int candidate = candidateStart; candidate < candidateEnd; candidate++) {
                                NetworkRule rule = index.candidateAt(candidate);
                                if (matchesRule(rule, url, requestHost, context)) {
                                    releaseRuleScratch(checked);
                                    return true;
                                }
                            }
                        }
                    }
                }
                start = i + 1;
            }
        }
        for (NetworkRule rule : generic) if (matchesRule(rule, url, requestHost, context)) {
            releaseRuleScratch(checked);
            return true;
        }
        releaseRuleScratch(checked);
        return false;
    }

    private static void releaseRuleScratch(RuleMatchScratch checked) {
        if (checked == null) return;
        if (checked.marks.length > (1 << 18)) RULE_MATCH_SCRATCH.remove();
    }

    private static boolean matchesRule(NetworkRule rule, String url, String requestHost, String pageHost, int type) {
        return matchesRule(rule, url, requestHost, pageHost, type, null, null);
    }

    private static boolean matchesRule(NetworkRule rule, String url, String requestHost, String pageHost, int type,
                                       String requestSiteKey, String pageSiteKey) {
        return matchesRule(rule, url, requestHost,
                new RequestMatchContext(requestHost, pageHost, type, requestSiteKey, pageSiteKey));
    }

    private static boolean matchesRule(NetworkRule rule, String url, String requestHost,
                                       RequestMatchContext context) {
        if (rule.includedTypes != 0 && (rule.includedTypes & context.type()) == 0) return false;
        if (rule.excludedTypes != 0 && (rule.excludedTypes & context.type()) != 0) return false;
        if (rule.includeDomains != null || rule.excludeDomains != null) {
            String pageHost = context.page();
            String normalizedPage = pageHost == null ? "" : pageHost;
            if (rule.includeDomains != null && !matchesAnyNormalizedDomain(normalizedPage, rule.includeDomains)) return false;
            if (rule.excludeDomains != null && matchesAnyNormalizedDomain(normalizedPage, rule.excludeDomains)) return false;
        }
        // Most rules have no party constraint. Avoid public-suffix work and temporary
        // strings on the overwhelmingly common path.
        if (rule.thirdParty || rule.firstParty) {
            boolean thirdParty = context.thirdParty();
            if (rule.thirdParty && !thirdParty) return false;
            if (rule.firstParty && thirdParty) return false;
        }
        return matchesPattern(rule, url, requestHost);
    }

    private static boolean matchesPattern(NetworkRule rule, String url, String requestHost) {
        if (rule.regex != null) return rule.regex.matcher(url).find();
        String pattern = rule.matchPattern;
        if (rule.domainAnchor) {
            if (rule.leadingDomain.length() > 0) {
                boolean hostMatches = rule.domainBoundary
                        ? isDomainOrSubdomain(requestHost, rule.leadingDomain)
                        : requestHost.startsWith(rule.leadingDomain);
                if (!hostMatches) return false;
            }
        }
        int targetStart = rule.domainAnchor ? schemeEnd(url) : 0;
        if (targetStart < 0) targetStart = 0;
        int position = 0;
        int segmentStart = 0;
        boolean firstSegment = true;
        boolean mustSeparate = false;
        boolean exactNextSegment = false;
        for (int i = 0; i <= pattern.length(); i++) {
            char marker = i < pattern.length() ? pattern.charAt(i) : '\0';
            if (marker != '*' && marker != '^' && marker != '\0') continue;
            int segmentLength = i - segmentStart;
            if (segmentLength > 0) {
                int found = segmentStart == 0 && i == pattern.length()
                        ? url.indexOf(pattern, targetStart + position)
                        : indexOfSegment(url, pattern, segmentStart, i, targetStart + position);
                if (found < 0) return false;
                if (firstSegment && rule.startAnchor && found != targetStart) return false;
                if (exactNextSegment && found != targetStart + position) return false;
                if (mustSeparate && found > targetStart && !isSeparator(url.charAt(found - 1))) return false;
                position = found - targetStart + segmentLength;
                firstSegment = false;
                mustSeparate = false;
                exactNextSegment = false;
            }
            if (marker == '^') {
                boolean followedByPattern = i < pattern.length() - 1;
                boolean wildcardBeforeSeparator = i > 0 && pattern.charAt(i - 1) == '*';
                if (followedByPattern && segmentLength == 0 && firstSegment) {
                    // A leading separator anchor can match the start of the URL or a real
                    // separator before the next literal; it is not tied to the URL's first byte.
                    mustSeparate = true;
                } else if (followedByPattern && !wildcardBeforeSeparator) {
                    int separatorAt = targetStart + position;
                    if (separatorAt >= url.length() || !isSeparator(url.charAt(separatorAt))) return false;
                    position++;
                    exactNextSegment = pattern.charAt(i + 1) != '*';
                } else if (followedByPattern) {
                    // A preceding wildcard may consume up to the separator immediately before
                    // the next literal segment (for example, `foo*^bar`).
                    mustSeparate = true;
                } else if (targetStart + position < url.length()) {
                    if (!isSeparator(url.charAt(targetStart + position))) return false;
                    position++;
                }
            }
            segmentStart = i + 1;
        }
        if (rule.endAnchor && targetStart + position != url.length()) return false;
        return true;
    }

    /** Finds a pattern slice without allocating a temporary String for every request match. */
    private static int indexOfSegment(String value, String pattern, int patternStart, int patternEnd,
                                      int fromIndex) {
        int length = patternEnd - patternStart;
        if (length <= 0) return Math.max(0, fromIndex);
        int start = Math.max(0, fromIndex);
        int limit = value.length() - length;
        char first = pattern.charAt(patternStart);
        for (int i = start; i <= limit; i++) {
            if (value.charAt(i) == first && value.regionMatches(i, pattern, patternStart, length)) return i;
        }
        return -1;
    }

    private static int inferType(Uri uri, String accept, boolean mainFrame) {
        if (mainFrame) return TYPE_DOCUMENT;
        // Uri.getPath() decodes and commonly allocates on every callback. Most WebView paths
        // are already unescaped, so classify the encoded path directly and only decode paths
        // that can actually contain an escaped suffix. This keeps normal page loads allocation
        // free while preserving the old behavior for URLs such as `/asset%2Ejs`.
        String encodedPath = uri == null ? "" : value(uri.getEncodedPath());
        String path = lowercaseIfNeeded(encodedPath);
        if (path.indexOf('%') >= 0 && uri != null) {
            String decodedPath = uri.getPath();
            if (decodedPath != null && decodedPath.length() > 0)
                path = lowercaseIfNeeded(decodedPath);
        }
        String header = lowercaseIfNeeded(value(accept));
        if (header.contains("javascript") || path.endsWith(".js") || path.endsWith(".mjs")) return TYPE_SCRIPT;
        if (header.contains("image/") || path.endsWith(".png") || path.endsWith(".jpg") || path.endsWith(".jpeg") ||
                path.endsWith(".gif") || path.endsWith(".webp") || path.endsWith(".svg") || path.endsWith(".avif")) return TYPE_IMAGE;
        if (header.contains("text/css") || path.endsWith(".css")) return TYPE_STYLE;
        if (header.contains("font/") || path.endsWith(".woff") || path.endsWith(".woff2") ||
                path.endsWith(".ttf") || path.endsWith(".otf")) return TYPE_FONT;
        if (header.contains("video/") || header.contains("audio/") || path.endsWith(".mp4") || path.endsWith(".webm") ||
                path.endsWith(".m3u8") || path.endsWith(".mpd") || path.endsWith(".mp3") ||
                path.endsWith(".m4a") || path.endsWith(".ogg") || path.endsWith(".opus") ||
                path.endsWith(".aac") || path.endsWith(".m4s") || path.endsWith(".ts")) return TYPE_MEDIA;
        if (header.contains("application/json") || header.contains("text/event-stream") ||
                path.endsWith(".json") || path.endsWith(".xml") || path.endsWith(".rss") ||
                path.endsWith(".atom")) return TYPE_XHR;
        if (header.contains("text/html") || path.endsWith(".html") || path.endsWith(".htm")) return TYPE_FRAME;
        return TYPE_OTHER;
    }

    private static int typeOption(String name) {
        if ("script".equals(name)) return TYPE_SCRIPT;
        if ("image".equals(name)) return TYPE_IMAGE;
        if ("stylesheet".equals(name) || "css".equals(name)) return TYPE_STYLE;
        if ("font".equals(name)) return TYPE_FONT;
        if ("media".equals(name)) return TYPE_MEDIA;
        if ("xmlhttprequest".equals(name) || "xhr".equals(name) || "ping".equals(name) || "websocket".equals(name)) return TYPE_XHR;
        if ("subdocument".equals(name) || "frame".equals(name)) return TYPE_FRAME;
        if ("document".equals(name)) return TYPE_DOCUMENT;
        if ("object".equals(name) || "object-subrequest".equals(name) || "other".equals(name)) return TYPE_OTHER;
        return 0;
    }

    private static String pureDomainRule(String pattern) {
        // Keep prefix rules without an explicit separator in the normal matcher. A compact host
        // entry is an exact boundary and must never turn `||example.com` into a narrower rule.
        if (!pattern.startsWith("||") || !pattern.endsWith("^")) return "";
        String value = pattern.substring(2, pattern.length() - 1);
        if (!validDomain(value)) return "";
        // parseNetwork lower-cases and trims the complete rule before this helper is called.
        return value;
    }

    private static String leadingDomain(String pattern) {
        int end = 0;
        while (end < pattern.length()) {
            char c = pattern.charAt(end);
            if (!(Character.isLetterOrDigit(c) || c == '.' || c == '-')) break;
            end++;
        }
        String value = pattern.substring(0, end);
        return validDomain(value) ? value : "";
    }

    /** Normalizes match anchors once while parsing instead of slicing every request callback. */
    private static void preparePattern(NetworkRule rule) {
        String pattern = rule.pattern == null ? "" : rule.pattern;
        rule.domainAnchor = pattern.startsWith("||");
        rule.startAnchor = !rule.domainAnchor && pattern.startsWith("|");
        rule.endAnchor = pattern.endsWith("|") && pattern.length() > 1;
        int start = rule.domainAnchor ? 2 : (rule.startAnchor ? 1 : 0);
        int end = rule.endAnchor && pattern.length() > start ? pattern.length() - 1 : pattern.length();
        if (end < start) end = start;
        rule.matchPattern = pattern.substring(start, end);
        rule.leadingDomain = rule.domainAnchor ? leadingDomain(rule.matchPattern) : "";
        if (rule.domainAnchor && rule.leadingDomain.length() > 0 &&
                rule.leadingDomain.length() < rule.matchPattern.length()) {
            char next = rule.matchPattern.charAt(rule.leadingDomain.length());
            rule.domainBoundary = next == '^' || next == '/' || next == '?' || next == '#' || next == ':';
        }
    }

    private static String bestKeyword(String pattern) {
        String bestSegment = "";
        int start = 0;
        for (int i = 0; i <= pattern.length(); i++) {
            char c = i < pattern.length() ? pattern.charAt(i) : '/';
            if (isKeywordChar(c)) continue;
            int length = i - start;
            if (length >= INDEX_KEY_LENGTH && length > bestSegment.length()) {
                String candidate = pattern.substring(start, i);
                if (!candidate.startsWith("https") && !candidate.startsWith("http")) bestSegment = candidate;
            }
            start = i + 1;
        }
        if (bestSegment.length() < INDEX_KEY_LENGTH) return "";
        // A fixed-width shard guarantees that a longer URL token still retrieves the rule.
        int offset = Math.max(0, (bestSegment.length() - INDEX_KEY_LENGTH) / 2);
        return bestSegment.substring(offset, offset + INDEX_KEY_LENGTH);
    }

    private static boolean isKeywordChar(char c) {
        // Dots and dashes are stable URL token characters. Including them keeps common
        // hostname/path rules in the primitive index instead of the bounded generic scan.
        return (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_' || c == '%' ||
                c == '.' || c == '-';
    }

    private static int keywordHash(String value, int offset) {
        int result = 0;
        for (int i = 0; i < INDEX_KEY_LENGTH; i++) result = 31 * result + value.charAt(offset + i);
        return result;
    }

    private static boolean isSeparator(char c) {
        return !Character.isLetterOrDigit(c) && c != '_' && c != '-' && c != '.' && c != '%';
    }

    /** Both hosts are normalized by the request hot path. */
    private static boolean isThirdPartyNormalized(String requestHost, String pageHost) {
        if (pageHost == null || pageHost.length() == 0) return true;
        return !siteKeyNormalized(requestHost).equals(siteKeyNormalized(pageHost));
    }

    private static String siteKeyNormalized(String host) {
        String value = host == null ? "" : host;
        int lastDot = value.lastIndexOf('.');
        if (lastDot <= 0 || lastDot == value.length() - 1) return value;
        int secondDot = value.lastIndexOf('.', lastDot - 1);
        if (secondDot < 0) return value;
        // Small public-suffix accommodation for the most common two-level suffixes.
        String lastTwo = value.substring(secondDot + 1);
        if (TWO_LEVEL_SUFFIXES.contains("|" + lastTwo + "|")) {
            int thirdDot = value.lastIndexOf('.', secondDot - 1);
            return value.substring(thirdDot + 1);
        }
        return lastTwo;
    }

    private static boolean matchesAnyDomain(String host, Set<String> domains) {
        return matchesAnyNormalizedDomain(normalize(host), domains);
    }

    /** Matches a host that has already been normalized by the request hot path. */
    private static boolean matchesAnyNormalizedDomain(String host, Set<String> domains) {
        if (host == null || host.length() == 0 || domains == null || domains.isEmpty()) return false;
        String candidate = host;
        while (candidate.length() > 0) {
            if (domains.contains(candidate)) return true;
            int dot = candidate.indexOf('.');
            if (dot < 0) break;
            candidate = candidate.substring(dot + 1);
        }
        return false;
    }

    /** Avoids allocating a second URL string when WebView already supplied lowercase ASCII. */
    private static String lowercaseIfNeeded(String value) {
        if (value == null || value.length() == 0) return value == null ? "" : value;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c >= 'A' && c <= 'Z') return value.toLowerCase(Locale.US);
        }
        return value;
    }

    private static boolean hasDomainEntry(String host, Map<String, ?> map) {
        String candidate = host;
        while (candidate.length() > 0) {
            if (map.containsKey(candidate)) return true;
            int dot = candidate.indexOf('.');
            if (dot < 0) break;
            candidate = candidate.substring(dot + 1);
        }
        return false;
    }

    private static void collectDomainSets(String host, Map<String, Set<String>> map, Set<String> target) {
        String candidate = host;
        while (candidate.length() > 0) {
            Set<String> values = map.get(candidate);
            if (values != null) target.addAll(values);
            int dot = candidate.indexOf('.');
            if (dot < 0) break;
            candidate = candidate.substring(dot + 1);
        }
    }

    private static void collectDomainSelectors(String host, Map<String, List<String>> map, Set<String> exceptions,
                                               List<String> target, Set<String> seen, int limit) {
        String candidate = host;
        while (candidate.length() > 0 && target.size() < limit) {
            List<String> values = map.get(candidate);
            if (values != null) addSelectors(target, seen, values, exceptions, limit);
            int dot = candidate.indexOf('.');
            if (dot < 0) break;
            candidate = candidate.substring(dot + 1);
        }
    }

    private static void addSelectors(List<String> target, Set<String> seen, List<String> source,
                                     Set<String> exceptions, int limit) {
        for (String selector : source) {
            if (target.size() >= limit) return;
            if (!exceptions.contains(selector) && seen.add(selector)) target.add(selector);
        }
    }

    private static Pattern safeRegex(String raw) {
        if (raw == null || raw.length() == 0 || raw.length() > 512) return null;
        String lower = raw.toLowerCase(Locale.US);
        if (lower.contains("(?=") || lower.contains("(?!") || lower.contains("(?<=") || lower.contains("(?<!") ||
                raw.matches(".*\\([^)]*[+*][^)]*\\)[+*].*")) return null;
        try { return Pattern.compile(raw, Pattern.CASE_INSENSITIVE); }
        catch (RuntimeException ignored) { return null; }
    }

    private static boolean safeSelector(String selector) {
        if (selector.length() < 1 || selector.length() > 512) return false;
        String lower = selector.toLowerCase(Locale.US);
        return selector.indexOf('{') < 0 && selector.indexOf('}') < 0 && !lower.startsWith("+js(") &&
                !lower.contains(":-abp-") && !lower.contains(":has-text(") && !lower.contains(":matches-path(");
    }

    private static boolean validDomain(String domain) {
        if (domain == null || domain.length() < 3 || domain.length() > 253 || domain.indexOf('.') <= 0) return false;
        for (int i = 0; i < domain.length(); i++) {
            char c = domain.charAt(i);
            if (!(Character.isLetterOrDigit(c) || c == '.' || c == '-' || c == '_')) return false;
        }
        return true;
    }

    private static boolean validDomainRange(String value, int start, int end) {
        int length = end - start;
        if (value == null || length < 3 || length > 253) return false;
        boolean dot = false;
        for (int i = start; i < end; i++) {
            char c = value.charAt(i);
            if (c == '.') dot = true;
            else if (!(Character.isLetterOrDigit(c) || c == '-' || c == '_')) return false;
        }
        return dot && value.charAt(start) != '.';
    }

    private static boolean validCosmeticDomain(String domain) {
        if (domain == null || domain.length() == 0 || domain.length() > 253) return false;
        for (int i = 0; i < domain.length(); i++) {
            char c = domain.charAt(i);
            if (!(Character.isLetterOrDigit(c) || c == '.' || c == '-' || c == '_' || c == ':')) return false;
        }
        return true;
    }

    private static boolean isDomainOrSubdomain(String host, String domain) {
        if (host.equals(domain)) return true;
        int offset = host.length() - domain.length();
        return offset > 0 && host.charAt(offset - 1) == '.' && host.regionMatches(offset, domain, 0, domain.length());
    }

    private static String stripScheme(String url) {
        int marker = url.indexOf("://");
        return marker < 0 ? url : url.substring(marker + 3);
    }

    private static int schemeEnd(String url) {
        int marker = url.indexOf("://");
        return marker < 0 ? 0 : marker + 3;
    }

    private static String normalize(String value) {
        if (value == null || value.length() == 0) return value == null ? "" : value;
        int start = 0;
        int end = value.length();
        while (start < end && value.charAt(start) <= ' ') start++;
        while (end > start && value.charAt(end - 1) <= ' ') end--;
        boolean lower = false;
        for (int i = start; i < end; i++) {
            char c = value.charAt(i);
            if ((c >= 'A' && c <= 'Z') || c > 127) {
                lower = true;
                break;
            }
        }
        if (start == 0 && end == value.length() && !lower) return value;
        String clean = start == 0 && end == value.length() ? value : value.substring(start, end);
        return lower ? clean.toLowerCase(Locale.US) : clean;
    }

    private static String value(String value) { return value == null ? "" : value; }

    private static <K, T> void addToListMap(Map<K, List<T>> map, K key, T value) {
        List<T> list = map.get(key);
        if (list == null) { list = new ArrayList<T>(); map.put(key, list); }
        list.add(value);
    }

    private static <T> void addToSetMap(Map<String, Set<T>> map, String key, T value) {
        Set<T> set = map.get(key);
        if (set == null) { set = new HashSet<T>(); map.put(key, set); }
        set.add(value);
    }

}
