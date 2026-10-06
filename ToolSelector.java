package com.example.orelock;

import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;

/** Choisit le meilleur outil de la barre rapide pour un bloc. */
public final class ToolSelector {
    public record Pick(int slot, float speed, boolean harvest) {}

    private ToolSelector() {}

    public static Pick pick(PlayerEntity p, BlockState s) {
        PlayerInventory inv = p.getInventory();
        boolean needTool = s.isToolRequired();
        boolean creative = p.getAbilities().creativeMode;
        int bestSlot = inv.selectedSlot;
        float bestSpeed = 1f;
        boolean bestHarvest = !needTool;
        double bestScore = -1;

        for (int i = 0; i < 9; i++) {
            ItemStack st = inv.getStack(i);
            boolean harvest = !needTool || st.isSuitableFor(s);
            float sp = st.isEmpty() ? 1f : st.getMiningSpeedMultiplier(s);
            if (!creative && st.isDamageable()) {
                if (st.getMaxDamage() - st.getDamage() <= 2) continue; // ne pas casser l'outil
                if (sp <= 1.0f) continue;                              // objet abimable inutile ici (epee, hache sur pierre...)
            }
            double score = (harvest ? 1000.0 : 0.0) + sp;
            if (score > bestScore || (score == bestScore && i == inv.selectedSlot)) {
                bestScore = score;
                bestSlot = i;
                bestSpeed = sp;
                bestHarvest = harvest;
            }
        }
        return new Pick(bestSlot, Math.max(1f, bestSpeed), bestHarvest);
    }

    public static void equip(PlayerEntity p, BlockState s) {
        Pick pk = pick(p, s);
        if (pk.slot() != p.getInventory().selectedSlot) {
            p.getInventory().selectedSlot = pk.slot();
        }
    }
}
