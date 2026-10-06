package com.example.orelock;

import com.example.orelock.mixin.ClientPlayerInteractionManagerAccessor;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

public class OreLockClient implements ClientModInitializer {
    /** Par defaut ":" d'un clavier AZERTY (touche physique "." d'un QWERTY). Modifiable dans Options > Controles. */
    public static KeyBinding menuKey;

    @Override
    public void onInitializeClient() {
        Cfg.load();
        menuKey = KeyBindingHelper.registerKeyBinding(
                new KeyBinding("key.orelock.menu", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_PERIOD, "category.orelock"));
        ClientTickEvents.END_CLIENT_TICK.register(OreLockClient::onEndTick);
        HudRenderCallback.EVENT.register(Hud::render);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> Miner.I.reset());
    }

    private static void onEndTick(MinecraftClient mc) {
        while (menuKey.wasPressed()) {
            if (mc.currentScreen == null && mc.player != null) mc.setScreen(new MinerScreen());
        }
        if (mc.player == null || mc.world == null) return;
        if (Cfg.noDelay && mc.interactionManager != null) {
            ((ClientPlayerInteractionManagerAccessor) mc.interactionManager).orelock$setBlockBreakingCooldown(0);
        }
        Miner.I.tick(mc);
    }
}
