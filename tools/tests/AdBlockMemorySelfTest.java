package com.xinyv.median;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class AdBlockMemorySelfTest {
    public static void main(String[] args) throws Exception {
        AdBlockEngine engine = new AdBlockEngine();
        int count = engine.updateRules(
                "*plain-ad-script*$script\n" +
                "*scoped-ad-script*$script,domain=site.example|~safe.example\n" +
                "||tracker.example^\n" +
                "example.org##.sponsor", Collections.<String>emptyList());
        expect(count == 4, "all synthetic rules compiled");

        Set<Object> rules = networkRules(engine);
        Object plain = rule(rules, "*plain-ad-script*");
        Object scoped = rule(rules, "*scoped-ad-script*");
        expect(field(plain, "includeDomains") == null, "unconstrained include set stays null");
        expect(field(plain, "excludeDomains") == null, "unconstrained exclude set stays null");
        expect(((Set<?>) field(scoped, "includeDomains")).contains("site.example"), "include domain retained");
        expect(((Set<?>) field(scoped, "excludeDomains")).contains("safe.example"), "exclude domain retained");
        Object packedDomains = field(field(engine, "rules"), "blockedDomains");
        expect(((Integer) invoke(packedDomains, "size")).intValue() == 1,
                "pure domains compacted into packed table");

        engine.cosmeticCssForHost("example.org");
        engine.trimMemory();
        Object ruleSet = field(engine, "rules");
        expect(((java.util.Map<?, ?>) field(ruleSet, "cosmeticCssCache")).isEmpty(), "recomputable CSS cache trimmed");
        System.out.println("AdBlockMemorySelfTest passed");
    }

    private static Set<Object> networkRules(AdBlockEngine engine) throws Exception {
        Object ruleSet = field(engine, "rules");
        Object index = field(ruleSet, "blockedIndex");
        Object[] values = (Object[]) field(index, "candidates");
        Set<Object> result = new HashSet<Object>();
        Collections.addAll(result, values);
        result.addAll((List<?>) field(ruleSet, "genericBlocked"));
        return result;
    }

    private static Object rule(Set<Object> rules, String pattern) throws Exception {
        for (Object rule : rules) if (pattern.equals(field(rule, "pattern"))) return rule;
        throw new AssertionError("Missing rule: " + pattern);
    }

    private static Object field(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static Object invoke(Object target, String name) throws Exception {
        java.lang.reflect.Method method = target.getClass().getDeclaredMethod(name);
        method.setAccessible(true);
        return method.invoke(target);
    }

    private static void expect(boolean value, String label) {
        if (!value) throw new AssertionError("Expected: " + label);
    }
}
