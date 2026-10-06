package com.example.orelock;

import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

/**
 * Casse un bloc. Marche avec ou sans menu ouvert : on appelle directement le gestionnaire de minage du jeu
 * (et on maintient le clic gauche quand aucun menu n'est ouvert).
 */
public final class Breaker {
    private static BlockPos cur;
    private static int ticks;

    private Breaker() {}

    public static int ticks() { return ticks; }

    public static void release(MinecraftClient mc) {
        cur = null;
        ticks = 0;
        mc.options.attackKey.setPressed(false);
    }

    /** @return false si le bloc ne peut pas etre vise (cache ou trop loin). */
    public static boolean mine(MinecraftClient mc, BlockPos pos, double range) {
        ClientPlayerEntity p = mc.player;
        ClientWorld w = mc.world;
        BlockState s = w.getBlockState(pos);
        if (s.isAir()) { release(mc); return true; }
        BlockHitResult hit = aim(w, p, pos, range);
        if (hit == null) return false;

        if (!pos.equals(cur)) { cur = pos; ticks = 0; }
        ticks++;

        ToolSelector.equip(p, s);
        face(p, hit.getPos());
        if (mc.currentScreen == null) mc.options.attackKey.setPressed(true);
        if (mc.interactionManager != null) {
            mc.interactionManager.updateBlockBreakingProgress(pos, hit.getSide());
            p.swingHand(Hand.MAIN_HAND);
        }
        return true;
    }

    /** Face visible du bloc, a portee. null si cache ou trop loin. */
    public static BlockHitResult aim(ClientWorld w, ClientPlayerEntity p, BlockPos pos, double range) {
        Vec3d eye = p.getEyePos();
        Vec3d center = Vec3d.ofCenter(pos);
        BlockHitResult best = rayTo(w, p, eye, center, pos);
        if (best == null) {
            for (Direction d : Direction.values()) {
                Vec3d fp = center.add(d.getOffsetX() * 0.45, d.getOffsetY() * 0.45, d.getOffsetZ() * 0.45);
                best = rayTo(w, p, eye, fp, pos);
                if (best != null) break;
            }
        }
        if (best == null || eye.distanceTo(best.getPos()) > range) return null;
        return best;
    }

    /** Premier bloc qui gene la vue vers la cible, a portee. null s'il n'y en a pas. */
    public static BlockPos blocker(ClientWorld w, ClientPlayerEntity p, BlockPos target, double range) {
        Vec3d eye = p.getEyePos();
        BlockHitResult r = w.raycast(new RaycastContext(eye, Vec3d.ofCenter(target),
                RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, p));
        if (r.getType() == HitResult.Type.BLOCK && !r.getBlockPos().equals(target)
                && eye.distanceTo(r.getPos()) <= range) {
            return r.getBlockPos();
        }
        return null;
    }

    private static BlockHitResult rayTo(ClientWorld w, ClientPlayerEntity p, Vec3d from, Vec3d to, BlockPos expected) {
        BlockHitResult r = w.raycast(new RaycastContext(from, to, RaycastContext.ShapeType.OUTLINE,
                RaycastContext.FluidHandling.NONE, p));
        if (r.getType() == HitResult.Type.BLOCK && r.getBlockPos().equals(expected)) return r;
        return null;
    }

    private static void face(ClientPlayerEntity p, Vec3d aim) {
        Vec3d e = p.getEyePos();
        double dx = aim.x - e.x, dy = aim.y - e.y, dz = aim.z - e.z;
        double h = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) (Math.atan2(dz, dx) * 180.0 / Math.PI) - 90.0f;
        float pitch = (float) -(Math.atan2(dy, h) * 180.0 / Math.PI);
        p.setYaw(yaw);
        p.setPitch(pitch);
        p.prevYaw = yaw;
        p.prevPitch = pitch;
    }
}
