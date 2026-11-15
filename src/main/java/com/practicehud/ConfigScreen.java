package com.practicehud;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

/** Settings screen: one tab per category, a toggle per module, and a Settings tab for tunables. */
public class ConfigScreen extends Screen {
    private static final String SETTINGS_TAB = "Settings";
    private static String currentTab = Modules.CATEGORIES.get(0);

    private static final String[] SHAPES = {"Cross", "Dot", "Circle", "Cross + Dot"};

    public ConfigScreen() {
        super(Text.literal("Practice HUD"));
    }

    @Override
    protected void init() {
        List<String> tabs = new ArrayList<>(Modules.CATEGORIES);
        tabs.add(SETTINGS_TAB);

        int tabW = 96;
        int totalW = tabs.size() * (tabW + 2);
        int tx = (width - totalW) / 2;
        for (String tab : tabs) {
            final String t = tab;
            ButtonWidget b = ButtonWidget.builder(Text.literal((t.equals(currentTab) ? "> " : "") + t), btn -> {
                currentTab = t;
                clearAndInit();
            }).dimensions(tx, 28, tabW, 20).build();
            addDrawableChild(b);
            tx += tabW + 2;
        }

        if (currentTab.equals(SETTINGS_TAB)) {
            initSettings();
        } else {
            int y = 60;
            int w = 220;
            int x = width / 2 - w / 2;
            for (Module m : Modules.ALL) {
                if (!m.category.equals(currentTab)) continue;
                final Module mod = m;
                ButtonWidget b = ButtonWidget.builder(label(mod), btn -> {
                    mod.toggle();
                    btn.setMessage(label(mod));
                }).dimensions(x, y, w, 20).tooltip(Tooltip.of(Text.literal(mod.description))).build();
                addDrawableChild(b);
                y += 24;
            }
        }

        addDrawableChild(ButtonWidget.builder(Text.literal("Done"), btn -> close())
                .dimensions(width / 2 - 50, height - 28, 100, 20).build());
    }

    private static Text label(Module m) {
        return Text.literal(m.name + ": " + (m.isEnabled() ? "ON" : "OFF"));
    }

    private void initSettings() {
        Config c = Config.get();
        int w = 220;
        int x = width / 2 - w / 2;
        int y = 60;

        addDrawableChild(ButtonWidget.builder(Text.literal("Crosshair shape: " + SHAPES[c.crosshairShape]), btn -> {
            c.crosshairShape = (c.crosshairShape + 1) % SHAPES.length;
            btn.setMessage(Text.literal("Crosshair shape: " + SHAPES[c.crosshairShape]));
            Config.save();
        }).dimensions(x, y, w, 20).build());
        y += 24;

        addStepper(x, y, w, "Crosshair size", () -> c.crosshairSize, v -> c.crosshairSize = v, 1, 24);
        y += 24;
        addStepper(x, y, w, "Crosshair gap", () -> c.crosshairGap, v -> c.crosshairGap = v, 0, 12);
        y += 24;
        addStepper(x, y, w, "Crosshair thickness", () -> c.crosshairThickness, v -> c.crosshairThickness = v, 1, 5);
        y += 24;

        addDrawableChild(ButtonWidget.builder(Text.literal("Dynamic crosshair: " + (c.crosshairDynamic ? "ON" : "OFF")), btn -> {
            c.crosshairDynamic = !c.crosshairDynamic;
            btn.setMessage(Text.literal("Dynamic crosshair: " + (c.crosshairDynamic ? "ON" : "OFF")));
            Config.save();
        }).dimensions(x, y, w, 20).build());
        y += 24;

        addStepper(x, y, w, "Tracking ring radius", () -> c.ringRadius, v -> c.ringRadius = v, 10, 120);
        y += 30;

        for (int i = 0; i < 4; i++) {
            final int idx = i;
            addDrawableChild(ButtonWidget.builder(slotText(idx), btn -> {
                c.quickSlots[idx] = (c.quickSlots[idx] + 1) % 9;
                btn.setMessage(slotText(idx));
                Config.save();
            }).dimensions(x, y, w, 20).tooltip(Tooltip.of(Text.literal("Bind the key itself in Options > Controls > Practice HUD"))).build());
            y += 24;
        }
    }

    private Text slotText(int idx) {
        return Text.literal("Quick Slot " + (char) ('A' + idx) + " selects hotbar slot " + (Config.get().quickSlots[idx] + 1));
    }

    private interface IntGetter {
        int get();
    }

    private interface IntSetter {
        void set(int v);
    }

    /** A button that steps a value up on left-click and wraps back to min after max. */
    private void addStepper(int x, int y, int w, String name, IntGetter get, IntSetter set, int min, int max) {
        addDrawableChild(ButtonWidget.builder(Text.literal(name + ": " + get.get()), btn -> {
            int v = get.get() + 1;
            if (v > max) v = min;
            set.set(v);
            btn.setMessage(Text.literal(name + ": " + v));
            Config.save();
        }).dimensions(x, y, w, 20).tooltip(Tooltip.of(Text.literal("Click to increase (wraps at " + max + ")"))).build());
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        ctx.drawCenteredTextWithShadow(textRenderer, title, width / 2, 10, 0xFFFFFFFF);
    }

    @Override
    public void close() {
        Config.save();
        super.close();
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
