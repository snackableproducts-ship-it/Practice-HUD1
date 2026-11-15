package com.practicehud;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PracticeHudClient implements ClientModInitializer {
    public static final String MOD_ID = "practicehud";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static KeyBinding openGui;
    private static final KeyBinding[] quickKeys = new KeyBinding[4];

    @Override
    public void onInitializeClient() {
        Config.load();

        KeyBinding.Category category = KeyBinding.Category.create(Identifier.of(MOD_ID, "main"));
        openGui = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.practicehud.open_gui", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_SHIFT, category));
        String[] names = {"slot_a", "slot_b", "slot_c", "slot_d"};
        for (int i = 0; i < quickKeys.length; i++) {
            quickKeys[i] = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                    "key.practicehud." + names[i], InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, category));
        }

        // Panels, rings and readouts are drawn after the subtitle overlay.
        HudElementRegistry.attachElementAfter(VanillaHudElements.SUBTITLES,
                Identifier.of(MOD_ID, "panels"),
                (ctx, tickCounter) -> Modules.renderAll(ctx, MinecraftClient.getInstance()));

        // Replace the vanilla crosshair only while the Custom Crosshair module is on.
        HudElementRegistry.replaceElement(VanillaHudElements.CROSSHAIR, original -> (ctx, tickCounter) -> {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (Modules.enabled("reticle")) {
                if (mc.options.getPerspective().isFirstPerson() && !mc.options.hudHidden) {
                    Modules.drawCrosshair(ctx, mc);
                }
            } else {
                original.render(ctx, tickCounter);
            }
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            Modules.tick(client);
            while (openGui.wasPressed()) {
                client.setScreen(new ConfigScreen());
            }
            if (client.player == null) return;
            for (int i = 0; i < quickKeys.length; i++) {
                while (quickKeys[i].wasPressed()) {
                    client.player.getInventory().setSelectedSlot(Config.get().quickSlots[i]);
                }
            }
        });
    }
}
