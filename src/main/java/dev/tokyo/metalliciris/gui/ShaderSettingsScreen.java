package dev.tokyo.metalliciris.gui;

import dev.tokyo.metalliciris.HookStats;
import dev.tokyo.metalliciris.MetallicIrisMetal;
import dev.tokyo.metalliciris.ShaderManager;
import dev.tokyo.metalliciris.config.ShaderSettings;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Main shader settings: the master on/off switch and, for each installed pack, buttons to
 * select it, enable or disable it, and open its options. Large pack lists are paged.
 */
public final class ShaderSettingsScreen extends Screen {
    private final Screen parent;
    private final int page;

    public ShaderSettingsScreen(Screen parent) {
        this(parent, 0);
    }

    private ShaderSettingsScreen(Screen parent, int page) {
        super(Component.literal("Metallic Iris Metal - Shaders"));
        this.parent = parent;
        this.page = page;
    }

    @Override
    protected void init() {
        ShaderManager manager = MetallicIrisMetal.manager();
        if (manager != null && manager.autoSelectIfNeeded()) {
            MetallicIrisMetal.saveSettings();
            MetallicIrisMetal.say("auto-selected pack " + manager.settings().getActivePack());
        }
        MetallicIrisMetal.say("settings screen opened: " + HookStats.describe(manager));
        if (manager != null) {
            MetallicIrisMetal.say(manager.describeFolder());
        }
        int cx = this.width / 2;
        int y = 10;

        addRenderableWidget(new StringWidget(cx - 150, y, 300, 12, this.title, Minecraft.getInstance().font));
        y += 16;

        if (manager == null) {
            addRenderableWidget(new StringWidget(cx - 150, y, 300, 12,
                    Component.literal("Shader manager is not ready yet."), Minecraft.getInstance().font));
            addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose())
                    .bounds(cx - 100, this.height - 28, 200, 20).build());
            return;
        }
        ShaderSettings settings = manager.settings();

        addRenderableWidget(Button.builder(label("Shaders", settings.isEnabled()), b -> {
            settings.setEnabled(!settings.isEnabled());
            if (settings.isEnabled() && settings.getActivePack() == null) {
                List<String> usable = manager.enabledPacks();
                if (!usable.isEmpty()) {
                    manager.activate(usable.get(0));
                }
            }
            MetallicIrisMetal.saveSettings();
            refresh();
        }).bounds(cx - 150, y, 300, 20).build());
        y += 24;

        addRenderableWidget(Button.builder(label("Test effect (grayscale)", settings.isTestEffect()), b -> {
            settings.setTestEffect(!settings.isTestEffect());
            MetallicIrisMetal.saveSettings();
            refresh();
        }).bounds(cx - 150, y, 300, 20).build());
        y += 24;

        addRenderableWidget(new StringWidget(cx - 150, y, 300, 10,
                Component.literal(shorten(HookStats.describe(manager), 70)), Minecraft.getInstance().font));
        y += 11;
        addRenderableWidget(new StringWidget(cx - 150, y, 300, 10,
                Component.literal(shorten("Effect: " + HookStats.effectState(), 70)), Minecraft.getInstance().font));
        y += 14;

        List<String> packs = manager.installedPacks();
        if (packs.isEmpty()) {
            addRenderableWidget(new StringWidget(cx - 150, y, 300, 12,
                    Component.literal("No packs in " + tail(manager.packsDir().toString(), 44)),
                    Minecraft.getInstance().font));
        }

        int rows = Math.max(1, (this.height - y - 56) / 24);
        int pageCount = Math.max(1, (packs.size() + rows - 1) / rows);
        int current = Math.min(page, pageCount - 1);
        int from = current * rows;
        int to = Math.min(packs.size(), from + rows);

        for (int i = from; i < to; i++) {
            String pack = packs.get(i);
            boolean packEnabled = settings.isPackEnabled(pack);
            boolean active = pack.equals(settings.getActivePack());

            Button select = Button.builder(
                    Component.literal((active ? "> " : packEnabled ? "" : "(off) ") + shorten(pack, 20)),
                    b -> {
                        // Tapping the name always selects the pack, re-enabling it first if needed.
                        if (!settings.isPackEnabled(pack)) {
                            settings.setPackEnabled(pack, true);
                        }
                        manager.activate(pack);
                        MetallicIrisMetal.saveSettings();
                        refresh();
                    }).bounds(cx - 150, y, 150, 20).build();
            addRenderableWidget(select);

            addRenderableWidget(Button.builder(Component.literal(packEnabled ? "Disable" : "Enable"), b -> {
                settings.setPackEnabled(pack, !packEnabled);
                MetallicIrisMetal.saveSettings();
                refresh();
            }).bounds(cx + 4, y, 60, 20).build());

            Button options = Button.builder(Component.literal("Options"),
                    b -> Minecraft.getInstance().setScreenAndShow(new PackOptionsScreen(this, pack)))
                    .bounds(cx + 68, y, 82, 20).build();
            options.active = packEnabled;
            addRenderableWidget(options);
            y += 24;
        }

        if (pageCount > 1) {
            int navY = this.height - 52;
            Button prev = Button.builder(Component.literal("<"), b -> goTo(current - 1))
                    .bounds(cx - 150, navY, 40, 20).build();
            prev.active = current > 0;
            addRenderableWidget(prev);
            addRenderableWidget(new StringWidget(cx - 100, navY + 5, 200, 12,
                    Component.literal("Page " + (current + 1) + " / " + pageCount),
                    Minecraft.getInstance().font));
            Button next = Button.builder(Component.literal(">"), b -> goTo(current + 1))
                    .bounds(cx + 110, navY, 40, 20).build();
            next.active = current < pageCount - 1;
            addRenderableWidget(next);
        }

        addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose())
                .bounds(cx - 100, this.height - 28, 200, 20).build());
    }

    private void refresh() {
        Minecraft.getInstance().setScreenAndShow(new ShaderSettingsScreen(parent, page));
    }

    private void goTo(int newPage) {
        Minecraft.getInstance().setScreenAndShow(new ShaderSettingsScreen(parent, Math.max(0, newPage)));
    }

    @Override
    public void onClose() {
        if (MetallicIrisMetal.manager() != null) {
            MetallicIrisMetal.saveSettings();
        }
        Minecraft.getInstance().setScreenAndShow(parent);
    }

    static String tail(String s, int max) {
        return s.length() <= max ? s : "…" + s.substring(s.length() - (max - 1));
    }

    static Component label(String name, boolean on) {
        return Component.literal(name + ": " + (on ? "ON" : "OFF"));
    }

    static String shorten(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }
}
