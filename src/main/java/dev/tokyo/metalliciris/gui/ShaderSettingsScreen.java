package dev.tokyo.metalliciris.gui;

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
    private static final int ROWS_PER_PAGE = 5;

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
        int cx = this.width / 2;
        int y = 20;

        addRenderableWidget(new StringWidget(cx - 150, y, 300, 12, this.title, Minecraft.getInstance().font));
        y += 22;

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
            MetallicIrisMetal.saveSettings();
            refresh();
        }).bounds(cx - 150, y, 300, 20).build());
        y += 28;

        List<String> packs = manager.installedPacks();
        if (packs.isEmpty()) {
            addRenderableWidget(new StringWidget(cx - 150, y, 300, 12,
                    Component.literal("No shaderpacks found in the shaderpacks folder."),
                    Minecraft.getInstance().font));
        }

        int pageCount = Math.max(1, (packs.size() + ROWS_PER_PAGE - 1) / ROWS_PER_PAGE);
        int current = Math.min(page, pageCount - 1);
        int from = current * ROWS_PER_PAGE;
        int to = Math.min(packs.size(), from + ROWS_PER_PAGE);

        for (int i = from; i < to; i++) {
            String pack = packs.get(i);
            boolean packEnabled = settings.isPackEnabled(pack);
            boolean active = pack.equals(settings.getActivePack());

            Button select = Button.builder(
                    Component.literal((active ? "> " : "") + shorten(pack, 22)),
                    b -> {
                        manager.activate(pack);
                        MetallicIrisMetal.saveSettings();
                        refresh();
                    }).bounds(cx - 150, y, 150, 20).build();
            select.active = packEnabled;
            addRenderableWidget(select);

            addRenderableWidget(Button.builder(Component.literal(packEnabled ? "On" : "Off"), b -> {
                settings.setPackEnabled(pack, !packEnabled);
                MetallicIrisMetal.saveSettings();
                refresh();
            }).bounds(cx + 4, y, 50, 20).build());

            Button options = Button.builder(Component.literal("Options"),
                    b -> Minecraft.getInstance().setScreenAndShow(new PackOptionsScreen(this, pack)))
                    .bounds(cx + 58, y, 92, 20).build();
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

    static Component label(String name, boolean on) {
        return Component.literal(name + ": " + (on ? "ON" : "OFF"));
    }

    static String shorten(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }
}
