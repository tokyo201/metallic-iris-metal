package dev.tokyo.metalliciris;

/** Counters the render hook updates, shown on the settings screen so the hook can be checked on-device. */
public final class HookStats {
    private static volatile long hudFrames;
    private static volatile long tintedFrames;

    private static volatile String effectState = "not started";

    private HookStats() {}

    /** What the post-effect controller last did, shown on the settings screen. */
    public static void setEffectState(String state) {
        effectState = state;
    }

    public static String effectState() {
        return effectState;
    }

    public static void frameSeen() {
        hudFrames++;
    }

    public static void frameTinted() {
        tintedFrames++;
    }

    public static long hudFrames() {
        return hudFrames;
    }

    public static long tintedFrames() {
        return tintedFrames;
    }

    /** One line saying what the hook has done and, if it is not tinting, why not. */
    public static String describe(ShaderManager manager) {
        if (hudFrames == 0) {
            return "Hook: NOT RUNNING yet (open this from inside a world, or Fabric API is missing)";
        }
        String why;
        if (manager == null) {
            why = "manager not ready";
        } else if (!manager.settings().isEnabled()) {
            why = "Shaders is OFF";
        } else if (manager.settings().getActivePack() == null) {
            why = "no pack selected - tap a pack name (" + manager.installedPacks().size()
                    + " found, " + manager.settings().disabledPacks().size() + " disabled)";
        } else if (!manager.settings().isPackEnabled(manager.settings().getActivePack())) {
            why = "selected pack is disabled - press Enable";
        } else {
            why = "pack selected";
        }
        return "Hook: running, " + hudFrames + " frames, " + tintedFrames + " tinted - " + why;
    }
}
