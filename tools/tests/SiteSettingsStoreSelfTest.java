package com.xinyv.median;

public final class SiteSettingsStoreSelfTest {
    public static void main(String[] args) {
        SiteSettingsStore.SiteSettings value = new SiteSettingsStore.SiteSettings();
        if (!value.isDefault()) throw new AssertionError("new settings must be default");
        int[] keys = { SiteSettingsStore.JAVASCRIPT, SiteSettingsStore.IMAGES,
                SiteSettingsStore.THIRD_PARTY_COOKIES, SiteSettingsStore.DESKTOP,
                SiteSettingsStore.DARK, SiteSettingsStore.POPUPS, SiteSettingsStore.AUTOPLAY,
                SiteSettingsStore.LOCATION, SiteSettingsStore.CAMERA, SiteSettingsStore.MICROPHONE,
                SiteSettingsStore.TRACKING_PROTECTION };
        for (int i = 0; i < keys.length; i++) value.set(keys[i], i % 3);
        for (int i = 0; i < keys.length; i++)
            if (value.get(keys[i]) != i % 3) throw new AssertionError("packed state collision");
        value.compatibilityMode(true);
        value.textZoom(175);
        if (!value.compatibilityMode() || value.textZoom() != 175)
            throw new AssertionError("packed compatibility or text zoom mismatch");
        SiteSettingsStore.SiteSettings copy = value.copy();
        if (!value.sameAs(copy) || value.packedStates() != copy.packedStates())
            throw new AssertionError("packed copy mismatch");
        copy.set(SiteSettingsStore.CAMERA, SiteSettingsStore.BLOCK);
        if (copy.get(SiteSettingsStore.CAMERA) != SiteSettingsStore.BLOCK)
            throw new AssertionError("packed update failed");
        copy.compatibilityMode(false);
        copy.textZoom(100);
        if (copy.compatibilityMode() || copy.textZoom() != 100)
            throw new AssertionError("packed defaults failed");
        SiteSettingsStore.SiteSettings defaults = new SiteSettingsStore.SiteSettings();
        defaults.compatibilityMode(false);
        defaults.textZoom(100);
        if (!defaults.isDefault()) throw new AssertionError("encoded defaults must remain empty");
        System.out.println("SiteSettingsStoreSelfTest passed");
    }
}
