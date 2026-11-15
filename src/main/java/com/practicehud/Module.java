package com.practicehud;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;

/** A toggleable HUD feature. */
public final class Module {
    @FunctionalInterface
    public interface Renderer {
        void render(DrawContext ctx, MinecraftClient mc, ClientPlayerEntity player);
    }

    public final String id;
    public final String name;
    public final String category;
    public final String description;
    private final Renderer renderer;

    public Module(String id, String name, String category, String description, Renderer renderer) {
        this.id = id;
        this.name = name;
        this.category = category;
        this.description = description;
        this.renderer = renderer;
    }

    public boolean isEnabled() {
        return Config.get().modules.getOrDefault(id, false);
    }

    public void toggle() {
        Config.get().modules.put(id, !isEnabled());
        Config.save();
    }

    public void render(DrawContext ctx, MinecraftClient mc, ClientPlayerEntity player) {
        if (renderer == null) return;
        try {
            renderer.render(ctx, mc, player);
        } catch (Exception e) {
            // A buggy module must never crash the game; switch it off and log.
            PracticeHudClient.LOGGER.error("Module {} failed and was disabled", id, e);
            Config.get().modules.put(id, false);
        }
    }
}
