package com.example.orelock;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.KeyBinding;

public final class Input {
    private Input() {}

    public static void stopMoving(MinecraftClient mc) {
        GameOptions o = mc.options;
        o.forwardKey.setPressed(false);
        o.jumpKey.setPressed(false);
        o.sprintKey.setPressed(false);
    }

    public static void release(MinecraftClient mc) {
        stopMoving(mc);
        mc.options.attackKey.setPressed(false);
        KeyBinding.updatePressedStates();
    }
}
