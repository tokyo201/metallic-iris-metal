package dev.tokyo.metalliciris.gui;

import dev.tokyo.metalliciris.MetallicIrisMetal;
import dev.tokyo.metalliciris.ShaderManager;
import dev.tokyo.metalliciris.config.ShaderSettings;
import dev.tokyo.metalliciris.pack.ShaderOption;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Options for one pack. A toggle option flips on/off and a value option steps through the choices
 * the pack lists. A value option with no listed choices accepts any value, so it is shown but can
 * only be edited in the config file.
 */
public final class PackOptionsScreen extends Screen {
    private final Screen parent;
    private final String pack;
    private final int page;

    public PackOptionsScreen(Screen parent, String pack) {
        this(parent, pack, 0);
    }

    private PackOptionsScreen(Screen parent, String pack, int page) {
        super(Component.literal("Options: " + ShaderSettingsScreen.shorten(pack, 30)));
        this.parent = parent;
        this.pack = pack;
        this.page = page;
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int y = 10;
        addRenderableWidget(new StringWidget(cx - 150, y, 300, 12, this.title, Minecraft.getInstance().font));
        y += 18;

        ShaderManager manager = MetallicIrisMetal.manager();
        List<ShaderOption> options;
        try {
            options = manager == null ? List.of() : manager.options(pack);
        } catch (RuntimeException e) {
            addRenderableWidget(new StringWidget(cx - 150, y, 300, 12,
                    Component.literal("Could not read this pack: " + ShaderSettingsScreen.shorten(
                            String.valueOf(e.getMessage()), 40)), Minecraft.getInstance().font));
            addDone(cx);
            return;
        }

        if (options.isEmpty()) {
            addRenderableWidget(new StringWidget(cx - 150, y, 300, 12,
                    Component.literal("This pack has no adjustable options."), Minecraft.getInstance().font));
        }

        ShaderSettings settings = manager == null ? null : manager.settings();
        int rows = Math.max(1, (this.height - y - 80) / 24);
        int pageCount = Math.max(1, (options.size() + rows - 1) / rows);
        int current = Math.min(page, pageCount - 1);
        int from = current * rows;
        int to = Math.min(options.size(), from + rows);

        for (int i = from; i < to; i++) {
            ShaderOption option = options.get(i);
            String value = settings.optionOverrides(pack).getOrDefault(option.name(), option.defaultValue());
            boolean editable = option.type() == ShaderOption.Type.BOOLEAN || !option.allowedValues().isEmpty();

            Button button = Button.builder(
                    Component.literal(ShaderSettingsScreen.shorten(option.name(), 26) + ": " + value),
                    b -> {
                        String next = nextValue(option, value);
                        if (next.equals(option.defaultValue())) {
                            settings.resetOption(pack, option.name());
                        } else {
                            manager.setOption(pack, option.name(), next);
                        }
                        MetallicIrisMetal.saveSettings();
                        Minecraft.getInstance().setScreenAndShow(new PackOptionsScreen(parent, pack, current));
                    }).bounds(cx - 150, y, 300, 20).build();
            button.active = editable;
            addRenderableWidget(button);
            y += 24;
        }

        if (pageCount > 1) {
            int navY = this.height - 76;
            Button prev = Button.builder(Component.literal("<"), b ->
                    Minecraft.getInstance().setScreenAndShow(new PackOptionsScreen(parent, pack, current - 1)))
                    .bounds(cx - 150, navY, 40, 20).build();
            prev.active = current > 0;
            addRenderableWidget(prev);
            addRenderableWidget(new StringWidget(cx - 100, navY + 5, 200, 12,
                    Component.literal("Page " + (current + 1) + " / " + pageCount), Minecraft.getInstance().font));
            Button next = Button.builder(Component.literal(">"), b ->
                    Minecraft.getInstance().setScreenAndShow(new PackOptionsScreen(parent, pack, current + 1)))
                    .bounds(cx + 110, navY, 40, 20).build();
            next.active = current < pageCount - 1;
            addRenderableWidget(next);
        }

        addRenderableWidget(Button.builder(Component.literal("Reset all to defaults"), b -> {
            if (settings != null) {
                settings.resetPackOptions(pack);
                MetallicIrisMetal.saveSettings();
            }
            Minecraft.getInstance().setScreenAndShow(new PackOptionsScreen(parent, pack, 0));
        }).bounds(cx - 100, this.height - 52, 200, 20).build());
        addDone(cx);
    }

    private void addDone(int cx) {
        addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose())
                .bounds(cx - 100, this.height - 28, 200, 20).build());
    }

    /** The value after {@code current}, wrapping around the allowed values. */
    static String nextValue(ShaderOption option, String current) {
        List<String> choices = option.allowedValues();
        int i = choices.indexOf(current);
        return choices.get((i + 1) % choices.size());
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreenAndShow(parent);
    }
}
