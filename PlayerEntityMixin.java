package com.example.orelock.mixin;

import com.example.orelock.Cfg;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerEntity.class)
public abstract class PlayerEntityMixin {
    @Inject(method = "getBlockBreakingSpeed", at = @At("RETURN"), cancellable = true)
    private void orelock$boost(BlockState block, CallbackInfoReturnable<Float> cir) {
        if (Cfg.mineMult <= 1.0) return;
        PlayerEntity self = (PlayerEntity) (Object) this;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null && self.getUuid().equals(mc.player.getUuid())) {
            cir.setReturnValue(cir.getReturnValueF() * (float) Cfg.mineMult);
        }
    }
}
