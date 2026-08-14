package com.xinyv.median;

import android.net.Uri;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
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

        Stats(int networkRules, int cosmeticRules, int sourceLines, long inspectedRequests,
              int blockedRequests, int allowedRequests) {
            this.networkRules = networkRules;
            this.cosmeticRules = cosmeticRules;
            this.sourceLines = sourceLines;
            this.inspectedRequests = inspectedRequests;
            this.blockedRequests = blockedRequests;
            this.allowedRequests = allowedRequests;
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
    private static final int MAX_LINES = 450000;
    private static final int MAX_NETWORK_RULES = 260000;
    private static final int MAX_COSMETIC_RULES = 60000;
    private static final int MAX_GENERIC_RULES = 4096;
    private static final int MAX_SELECTORS_PER_PAGE = 900;
    private static final int MAX_REQUEST_URL = 16384;
    private static final int MAX_COSMETIC_CACHE_ENTRIES = 24;
    private static final int MAX_COSMETIC_CACHE_VALUE = 96 * 1024;
    private static final int INDEX_KEY_LENGTH = 6;
    private static final int INDEX_ROLLING_FACTOR = 28629151; // 31^(INDEX_KEY_LENGTH-1)
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
    private static final ThreadLocal<HashSet<NetworkRule>> RULE_MATCH_SCRATCH = new ThreadLocal<HashSet<NetworkRule>>();

    private static final RuleSet EMPTY_RULES = RuleSet.empty();

    private final AtomicInteger blockedCount = new AtomicInteger();
    private final AtomicInteger allowedCount = new AtomicInteger();
    private final AtomicLong inspectedCount = new AtomicLong();
    private volatile RuleSet rules = EMPTY_RULES;

    private static final Set<String> BUILTIN_BLOCKED_HOSTS = Collections.unmodifiableSet(new HashSet<String>(Arrays.asList(
            "doubleclick.net", "googlesyndication.com", "googleadservices.com", "adservice.google.com",
            "2mdn.net", "adnxs.com", "adsrvr.org", "advertising.com", "adform.net", "taboola.com",
            "taboolasyndication.com", "outbrain.com", "outbrainimg.com", "criteo.com", "criteo.net",
            "scorecardresearch.com", "quantserve.com", "hotjar.com", "mouseflow.com", "fullstory.com",
            "mixpanel.com", "segment.io", "amplitude.com", "branch.io", "appsflyer.com", "adjust.com",
            "unityads.unity3d.com", "ads-twitter.com", "amazon-adsystem.com", "rubiconproject.com",
            "pubmatic.com", "openx.net", "smartadserver.com", "moatads.com", "zedo.com", "yieldmo.com",
            "teads.tv", "mathtag.com", "chartbeat.net", "nr-data.net", "demdex.net", "omtrdc.net",
            "everesttech.net", "bluekai.com", "krxd.net", "rlcdn.com", "casalemedia.com", "lijit.com",
            "bidswitch.net", "contextweb.com", "serving-sys.com", "flashtalking.com", "innovid.com",
            "spotxchange.com", "springserve.com", "33across.com", "sharethrough.com", "revcontent.com",
            "mgid.com", "adroll.com", "popads.net", "popcash.net", "propellerads.com", "exoclick.com",
            "trafficjunky.com", "adcolony.com", "vungle.com", "inmobi.com", "applovin.com", "ironsrc.com",
            "kochava.com", "singular.net", "pushwoosh.com", "leanplum.com", "optimizely.com", "crazyegg.com",
            "heapanalytics.com", "clarity.ms", "logrocket.com", "yandexadexchange.net", "adkernel.com",
            "bidr.io", "districtm.io", "yieldlab.net", "adition.com", "plista.com", "tradedoubler.com",
            "cpro.baidu.com", "pos.baidu.com", "eclick.baidu.com", "tanx.com", "alimama.com", "gdt.qq.com",
            "e.qq.com", "ad.toutiao.com", "mobads.baidu.com", "miaozhen.com", "ipinyou.com", "allyes.com",
            "adview.cn", "domob.cn", "youmi.net"
    )));

    private static final class NetworkRule {
        String pattern;
        Pattern regex;
        boolean allow;
        boolean thirdParty;
        boolean firstParty;
        boolean unsupportedAction;
        int includedTypes;
        int excludedTypes;
        final HashSet<String> includeDomains = new HashSet<String>();
        final HashSet<String> excludeDomains = new HashSet<String>();
    }

    /** Compact primitive-key table: request matching performs no substring or boxed-key allocation. */
    private static final class IntRuleIndex {
        private final int[] keys;
        private final Object[] values;
        private final int mask;

        IntRuleIndex(Map<Integer, List<NetworkRule>> source) {
            int required = Math.max(2, source.size() + source.size() / 2);
            int capacity = 2;
            while (capacity < required) capacity <<= 1;
            keys = new int[capacity];
            values = new Object[capacity];
            mask = capacity - 1;
            for (Map.Entry<Integer, List<NetworkRule>> entry : source.entrySet()) {
                int key = entry.getKey().intValue();
                int slot = slot(key);
                while (values[slot] != null) slot = (slot + 1) & mask;
                keys[slot] = key;
                values[slot] = entry.getValue();
            }
        }

        @SuppressWarnings("unchecked")
        List<NetworkRule> get(int key) {
            int slot = slot(key);
            while (values[slot] != null) {
                if (keys[slot] == key) return (List<NetworkRule>) values[slot];
                slot = (slot + 1) & mask;
            }
            return null;
        }

        private int slot(int key) {
            int mixed = key ^ (key >>> 16);
            mixed *= 0x7feb352d;
            mixed ^= mixed >>> 15;
            return mixed & mask;
        }
    }

    private static final class RuleSet {
        final Set<String> blockedDomains;
        final Set<String> allowedDomains;
        final IntRuleIndex blockedIndex;
        final IntRuleIndex allowedIndex;
        final List<NetworkRule> genericBlocked;
        final List<NetworkRule> genericAllowed;
        final List<String> globalCosmetic;
        final Set<String> globalCosmeticExceptions;
        final Map<String, List<String>> domainCosmetic;
        final Map<String, Set<String>> domainCosmeticExceptions;
        final ConcurrentHashMap<String, String> cosmeticCssCache = new ConcurrentHashMap<String, String>();
        final int networkCount;
        final int cosmeticCount;
        final int sourceLines;

        RuleSet(Set<String> blockedDomains, Set<String> allowedDomains,
                Map<Integer, List<NetworkRule>> blockedIndex, Map<Integer, List<NetworkRule>> allowedIndex,
                List<NetworkRule> genericBlocked, List<NetworkRule> genericAllowed,
                List<String> globalCosmetic, Set<String> globalCosmeticExceptions,
                Map<String, List<String>> domainCosmetic, Map<String, Set<String>> domainCosmeticExceptions,
                int networkCount, int cosmeticCount, int sourceLines) {
            this.blockedDomains = Collections.unmodifiableSet(blockedDomains);
            this.allowedDomains = Collections.unmodifiableSet(allowedDomains);
            this.blockedIndex = new IntRuleIndex(blockedIndex);
            this.allowedIndex = new IntRuleIndex(allowedIndex);
            this.genericBlocked = Collections.unmodifiableList(genericBlocked);
            this.genericAllowed = Collections.unmodifiableList(genericAllowed);
            this.globalCosmetic = Collections.unmodifiableList(globalCosmetic);
            this.globalCosmeticExceptions = Collections.unmodifiableSet(globalCosmeticExceptions);
            this.domainCosmetic = Collections.unmodifiableMap(domainCosmetic);
            this.domainCosmeticExceptions = Collections.unmodifiableMap(domainCosmeticExceptions);
            this.networkCount = networkCount;
            this.cosmeticCount = cosmeticCount;
            this.sourceLines = sourceLines;
        }

        static RuleSet empty() {
            return new Builder().finish();
        }
    }

    private static final class Builder {
        final HashSet<String> blockedDomains = new HashSet<String>();
        final HashSet<String> allowedDomains = new HashSet<String>();
        final HashMap<Integer, List<NetworkRule>> blockedIndex = new HashMap<Integer, List<NetworkRule>>();
        final HashMap<Integer, List<NetworkRule>> allowedIndex = new HashMap<Integer, List<NetworkRule>>();
        final ArrayList<NetworkRule> genericBlocked = new ArrayList<NetworkRule>();
        final ArrayList<NetworkRule> genericAllowed = new ArrayList<NetworkRule>();
        final ArrayList<String> globalCosmetic = new ArrayList<String>();
        final HashSet<String> globalCosmeticExceptions = new HashSet<String>();
        final HashMap<String, List<String>> domainCosmetic = new HashMap<String, List<String>>();
        final HashMap<String, Set<String>> domainCosmeticExceptions = new HashMap<String, Set<String>>();
        int networkCount;
        int cosmeticCount;
        int sourceLines;

        RuleSet finish() {
            return new RuleSet(blockedDomains, allowedDomains, blockedIndex, allowedIndex,
                    genericBlocked, genericAllowed, globalCosmetic, globalCosmeticExceptions,
                    domainCosmetic, domainCosmeticExceptions, networkCount, cosmeticCount, sourceLines);
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

    boolean shouldBlock(Uri request, String pageHost, String acceptHeader, boolean mainFrame) {
        if (request == null) return false;
        try {
            String scheme = request.getScheme();
            if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) return false;
            String host = normalize(request.getHost());
            if (host.length() == 0) return false;
            String page = normalize(pageHost);
            inspectedCount.incrementAndGet();
            RuleSet snapshot = rules;
            if (matchesAnyDomain(host, snapshot.allowedDomains)) {
                allowedCount.incrementAndGet();
                return false;
            }
            String rawUrl = request.toString();
            if (rawUrl.length() > MAX_REQUEST_URL) {
                if (matchesAnyDomain(host, BUILTIN_BLOCKED_HOSTS) || matchesAnyDomain(host, snapshot.blockedDomains)) {
                    blockedCount.incrementAndGet();
                    return true;
                }
                return false;
            }
            int type = inferType(request, acceptHeader, mainFrame);
            String full = rawUrl.toLowerCase(Locale.US);
            if (matchesIndexed(snapshot.allowedIndex, snapshot.genericAllowed, full, host, page, type)) {
                allowedCount.incrementAndGet();
                return false;
            }
            if (matchesAnyDomain(host, BUILTIN_BLOCKED_HOSTS) || matchesAnyDomain(host, snapshot.blockedDomains) ||
                    matchesIndexed(snapshot.blockedIndex, snapshot.genericBlocked, full, host, page, type)) {
                blockedCount.incrementAndGet();
                return true;
            }
        } catch (RuntimeException ignored) {
            return false;
        }
        return false;
    }

    int getBlockedCount() { return blockedCount.get(); }

    Stats getStats() {
        RuleSet snapshot = rules;
        return new Stats(snapshot.networkCount + BUILTIN_BLOCKED_HOSTS.size(), snapshot.cosmeticCount,
                snapshot.sourceLines, inspectedCount.get(), blockedCount.get(), allowedCount.get());
    }

    int updateCustomRules(String raw) {
        return updateRules(raw, Collections.<String>emptyList());
    }

    int updateRules(String custom, List<String> subscriptionSources) {
        Builder builder = new Builder();
        parseSource(custom, builder);
        if (subscriptionSources != null) {
            for (String source : subscriptionSources) {
                if (builder.sourceLines >= MAX_LINES || builder.networkCount >= MAX_NETWORK_RULES) break;
                parseSource(source, builder);
            }
        }
        rules = builder.finish();
        return builder.networkCount + builder.cosmeticCount;
    }

    boolean requiresEarlyCosmetic(String host) {
        RuleSet snapshot = rules;
        return snapshot.globalCosmetic.size() > 0 || hasDomainEntry(normalize(host), snapshot.domainCosmetic);
    }

    String cosmeticCssForHost(String host) {
        String normalized = normalize(host);
        if (normalized.length() == 0) return "";
        if (isDomainOrSubdomain(normalized, "youtube.com") || isDomainOrSubdomain(normalized, "youtu.be") ||
                isDomainOrSubdomain(normalized, "youtube-nocookie.com")) return "";
        RuleSet snapshot = rules;
        String remembered = snapshot.cosmeticCssCache.get(normalized);
        if (remembered != null) return remembered;
        HashSet<String> exceptions = new HashSet<String>(snapshot.globalCosmeticExceptions);
        collectDomainSets(normalized, snapshot.domainCosmeticExceptions, exceptions);
        ArrayList<String> selectors = new ArrayList<String>();
        HashSet<String> seen = new HashSet<String>();
        addSelectors(selectors, seen, BUILTIN_SELECTORS, exceptions, MAX_SELECTORS_PER_PAGE);
        addSelectors(selectors, seen, snapshot.globalCosmetic, exceptions, MAX_SELECTORS_PER_PAGE);
        collectDomainSelectors(normalized, snapshot.domainCosmetic, exceptions, selectors, seen, MAX_SELECTORS_PER_PAGE);
        StringBuilder css = new StringBuilder(Math.min(65536, selectors.size() * 45));
        // Keep selectors isolated: one unsupported selector must not invalidate a whole CSS group.
        for (String selector : selectors) css.append(selector).append("{display:none!important;}\n");
        String result = css.toString();
        if (result.length() > MAX_COSMETIC_CACHE_VALUE) return result;
        if (snapshot.cosmeticCssCache.size() >= MAX_COSMETIC_CACHE_ENTRIES) snapshot.cosmeticCssCache.clear();
        String existing = snapshot.cosmeticCssCache.putIfAbsent(normalized, result);
        return existing == null ? result : existing;
    }

    private static void parseSource(String raw, Builder builder) {
        if (raw == null || raw.length() == 0) return;
        int start = 0;
        while (start <= raw.length() && builder.sourceLines < MAX_LINES) {
            int end = raw.indexOf('\n', start);
            if (end < 0) end = raw.length();
            String line = raw.substring(start, end).trim();
            builder.sourceLines++;
            if (line.length() >= 3 && line.length() <= 4096 && !line.startsWith("!") && !line.startsWith("[")) {
                if (!parseCosmetic(line, builder) && builder.networkCount < MAX_NETWORK_RULES) parseNetwork(line, builder);
            }
            if (end == raw.length()) break;
            start = end + 1;
        }
    }

    private static boolean parseCosmetic(String line, Builder builder) {
        if (line.contains("#$#") || line.contains("#%#")) return true;
        int marker = line.indexOf("#@#");
        boolean exception = marker >= 0;
        int markerLength = exception ? 3 : 2;
        if (marker < 0) marker = line.indexOf("#?#");
        if (marker >= 0 && !exception) markerLength = 3;
        if (marker < 0) marker = line.indexOf("##");
        if (marker < 0) return false;
        if (builder.cosmeticCount >= MAX_COSMETIC_RULES) return true;
        String domains = line.substring(0, marker).trim();
        String selector = line.substring(marker + markerLength).trim();
        if (!safeSelector(selector)) return true;
        if (domains.length() == 0) {
            if (exception) builder.globalCosmeticExceptions.add(selector); else builder.globalCosmetic.add(selector);
            builder.cosmeticCount++;
            return true;
        }
        String[] values = domains.split(",");
        boolean hasPositiveDomain = false;
        for (String value : values) {
            boolean excludedDomain = value.trim().startsWith("~");
            String domain = normalize(excludedDomain ? value.trim().substring(1) : value);
            if (!validDomain(domain)) continue;
            if (excludedDomain || exception) addToSetMap(builder.domainCosmeticExceptions, domain, selector);
            else { addToListMap(builder.domainCosmetic, domain, selector); hasPositiveDomain = true; }
        }
        if (!exception && !hasPositiveDomain) builder.globalCosmetic.add(selector);
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
            for (String host : hosts) {
                host = normalize(host);
                if (validDomain(host) && !"localhost".equals(host)) builder.blockedDomains.add(host);
            }
            builder.networkCount++;
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
                rule.excludedTypes != 0 || !rule.includeDomains.isEmpty() || !rule.excludeDomains.isEmpty();
        if (pureDomain.length() > 0 && !hasConstraints) {
            (allow ? builder.allowedDomains : builder.blockedDomains).add(pureDomain);
            builder.networkCount++;
            return;
        }

        String keyword = rule.regex == null ? bestKeyword(line) : "";
        if (keyword.length() > 0) {
            addToListMap(allow ? builder.allowedIndex : builder.blockedIndex, Integer.valueOf(keywordHash(keyword, 0)), rule);
        } else {
            List<NetworkRule> generic = allow ? builder.genericAllowed : builder.genericBlocked;
            if (generic.size() >= MAX_GENERIC_RULES) return;
            generic.add(rule);
        }
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
        String[] domains = raw.split("\\|");
        for (String value : domains) {
            boolean excluded = value.startsWith("~");
            String domain = normalize(excluded ? value.substring(1) : value);
            if (!validDomain(domain)) continue;
            (excluded ? rule.excludeDomains : rule.includeDomains).add(domain);
        }
    }

    private static boolean matchesIndexed(IntRuleIndex index, List<NetworkRule> generic,
                                          String url, String requestHost, String pageHost, int type) {
        HashSet<NetworkRule> checked = null;
        int start = 0;
        for (int i = 0; i <= url.length(); i++) {
            char c = i < url.length() ? url.charAt(i) : '/';
            if (isKeywordChar(c)) continue;
            if (i - start >= INDEX_KEY_LENGTH) {
                int hash = keywordHash(url, start);
                for (int offset = start; offset <= i - INDEX_KEY_LENGTH; offset++) {
                    if (offset > start) hash = (hash - url.charAt(offset - 1) * INDEX_ROLLING_FACTOR) * 31 +
                            url.charAt(offset + INDEX_KEY_LENGTH - 1);
                    List<NetworkRule> candidates = index.get(hash);
                    if (candidates != null) for (NetworkRule rule : candidates) {
                        if (checked == null) {
                            checked = RULE_MATCH_SCRATCH.get();
                            if (checked == null) {
                                checked = new HashSet<NetworkRule>();
                                RULE_MATCH_SCRATCH.set(checked);
                            } else checked.clear();
                        }
                        if (checked.add(rule) && matchesRule(rule, url, requestHost, pageHost, type)) {
                            checked.clear();
                            return true;
                        }
                    }
                }
            }
            start = i + 1;
        }
        for (NetworkRule rule : generic) if (matchesRule(rule, url, requestHost, pageHost, type)) {
            if (checked != null) checked.clear();
            return true;
        }
        if (checked != null) checked.clear();
        return false;
    }

    private static boolean matchesRule(NetworkRule rule, String url, String requestHost, String pageHost, int type) {
        if (rule.includedTypes != 0 && (rule.includedTypes & type) == 0) return false;
        if ((rule.excludedTypes & type) != 0) return false;
        if (!rule.includeDomains.isEmpty() && !matchesAnyDomain(pageHost, rule.includeDomains)) return false;
        if (!rule.excludeDomains.isEmpty() && matchesAnyDomain(pageHost, rule.excludeDomains)) return false;
        // Most rules have no party constraint. Avoid public-suffix work and temporary
        // strings on the overwhelmingly common path.
        if (rule.thirdParty || rule.firstParty) {
            boolean thirdParty = isThirdParty(requestHost, pageHost);
            if (rule.thirdParty && !thirdParty) return false;
            if (rule.firstParty && thirdParty) return false;
        }
        return matchesPattern(rule, url, requestHost);
    }

    private static boolean matchesPattern(NetworkRule rule, String url, String requestHost) {
        if (rule.regex != null) return rule.regex.matcher(url).find();
        String pattern = rule.pattern;
        boolean domainAnchor = pattern.startsWith("||");
        boolean startAnchor = !domainAnchor && pattern.startsWith("|");
        boolean endAnchor = pattern.endsWith("|") && pattern.length() > 1;
        if (domainAnchor) pattern = pattern.substring(2);
        else if (startAnchor) pattern = pattern.substring(1);
        if (endAnchor) pattern = pattern.substring(0, pattern.length() - 1);
        if (domainAnchor) {
            String domain = leadingDomain(pattern);
            if (domain.length() > 0 && !isDomainOrSubdomain(requestHost, domain)) return false;
        }

        String target = domainAnchor ? stripScheme(url) : url;
        int position = 0;
        int segmentStart = 0;
        boolean firstSegment = true;
        boolean mustSeparate = false;
        for (int i = 0; i <= pattern.length(); i++) {
            char marker = i < pattern.length() ? pattern.charAt(i) : '\0';
            if (marker != '*' && marker != '^' && marker != '\0') continue;
            String segment = pattern.substring(segmentStart, i);
            if (segment.length() > 0) {
                int found = target.indexOf(segment, position);
                if (found < 0) return false;
                if (firstSegment && startAnchor && found != 0) return false;
                if (mustSeparate && found > 0 && !isSeparator(target.charAt(found - 1))) return false;
                position = found + segment.length();
                firstSegment = false;
            }
            mustSeparate = marker == '^';
            if (mustSeparate && i == pattern.length() - 1) {
                if (position < target.length() && !isSeparator(target.charAt(position))) return false;
            }
            segmentStart = i + 1;
        }
        if (endAnchor && position != target.length()) return false;
        return true;
    }

    private static int inferType(Uri uri, String accept, boolean mainFrame) {
        if (mainFrame) return TYPE_DOCUMENT;
        String path = value(uri.getPath()).toLowerCase(Locale.US);
        String header = value(accept).toLowerCase(Locale.US);
        if (header.contains("javascript") || path.endsWith(".js") || path.endsWith(".mjs")) return TYPE_SCRIPT;
        if (header.contains("image/") || path.endsWith(".png") || path.endsWith(".jpg") || path.endsWith(".jpeg") ||
                path.endsWith(".gif") || path.endsWith(".webp") || path.endsWith(".svg") || path.endsWith(".avif")) return TYPE_IMAGE;
        if (header.contains("text/css") || path.endsWith(".css")) return TYPE_STYLE;
        if (header.contains("font/") || path.endsWith(".woff") || path.endsWith(".woff2") ||
                path.endsWith(".ttf") || path.endsWith(".otf")) return TYPE_FONT;
        if (header.contains("video/") || header.contains("audio/") || path.endsWith(".mp4") || path.endsWith(".webm") ||
                path.endsWith(".m3u8") || path.endsWith(".mpd") || path.endsWith(".mp3") ||
                path.endsWith(".m4a") || path.endsWith(".ogg")) return TYPE_MEDIA;
        if (header.contains("application/json") || header.contains("text/event-stream")) return TYPE_XHR;
        if (header.contains("text/html")) return TYPE_FRAME;
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
        if (!pattern.startsWith("||")) return "";
        String value = pattern.substring(2);
        if (value.endsWith("^")) value = value.substring(0, value.length() - 1);
        if (!validDomain(value)) return "";
        return normalize(value);
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
        return (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_' || c == '%';
    }

    private static int keywordHash(String value, int offset) {
        int result = 0;
        for (int i = 0; i < INDEX_KEY_LENGTH; i++) result = 31 * result + value.charAt(offset + i);
        return result;
    }

    private static boolean isSeparator(char c) {
        return !Character.isLetterOrDigit(c) && c != '_' && c != '-' && c != '.' && c != '%';
    }

    private static boolean isThirdParty(String requestHost, String pageHost) {
        if (pageHost == null || pageHost.length() == 0) return true;
        return !siteKey(requestHost).equals(siteKey(pageHost));
    }

    private static String siteKey(String host) {
        String value = normalize(host);
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
        String candidate = normalize(host);
        while (candidate.length() > 0) {
            if (domains.contains(candidate)) return true;
            int dot = candidate.indexOf('.');
            if (dot < 0) break;
            candidate = candidate.substring(dot + 1);
        }
        return false;
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

    private static boolean isDomainOrSubdomain(String host, String domain) {
        if (host.equals(domain)) return true;
        int offset = host.length() - domain.length();
        return offset > 0 && host.charAt(offset - 1) == '.' && host.regionMatches(offset, domain, 0, domain.length());
    }

    private static String stripScheme(String url) {
        int marker = url.indexOf("://");
        return marker < 0 ? url : url.substring(marker + 3);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.US);
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
