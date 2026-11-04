package dev.tokyo.metalliciris.config;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * User settings: a master on/off switch, which pack is active, which installed packs are
 * disabled, and per-pack option overrides. Pack names are folder names or {@code .zip} file
 * names inside the {@code shaderpacks} folder.
 */
public final class ShaderSettings {
    private boolean enabled;
    private boolean testEffect;
    private String activePack;
    private final Set<String> disabledPacks = new TreeSet<>();
    private final Map<String, Map<String, String>> options = new TreeMap<>();

    /** Master switch. When off, no pack is loaded regardless of the active pack. */
    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /** Experimental: run the built-in grayscale post effect while shaders are active. Off by default. */
    public boolean isTestEffect() {
        return testEffect;
    }

    public void setTestEffect(boolean testEffect) {
        this.testEffect = testEffect;
    }

    /** The selected pack, or {@code null} for none. */
    public String getActivePack() {
        return activePack;
    }

    public void setActivePack(String activePack) {
        this.activePack = activePack;
    }

    public boolean isPackEnabled(String pack) {
        return !disabledPacks.contains(pack);
    }

    /** Enables or disables one pack. Disabling the active pack also deselects it. */
    public void setPackEnabled(String pack, boolean packEnabled) {
        if (packEnabled) {
            disabledPacks.remove(pack);
        } else {
            disabledPacks.add(pack);
            if (pack.equals(activePack)) {
                activePack = null;
            }
        }
    }

    public Set<String> disabledPacks() {
        return Collections.unmodifiableSet(disabledPacks);
    }

    /** The user's option overrides for a pack (empty when it uses all defaults). */
    public Map<String, String> optionOverrides(String pack) {
        Map<String, String> m = options.get(pack);
        return m == null ? Map.of() : Collections.unmodifiableMap(m);
    }

    public void setOption(String pack, String option, String value) {
        options.computeIfAbsent(pack, p -> new TreeMap<>()).put(option, value);
    }

    public void resetOption(String pack, String option) {
        Map<String, String> m = options.get(pack);
        if (m != null) {
            m.remove(option);
            if (m.isEmpty()) {
                options.remove(pack);
            }
        }
    }

    public void resetPackOptions(String pack) {
        options.remove(pack);
    }

    /** Names of packs that have at least one saved override. */
    public Set<String> packsWithOverrides() {
        return Collections.unmodifiableSet(options.keySet());
    }
}
