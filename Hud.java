package com.example.orelock;

import net.minecraft.block.Block;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/** Traits colores vers les cibles + petit affichage d'etat. Dessin 2D : visible a travers les murs. */
public final class Hud {
    private static final int[] PALETTE = {0xFF00E5FF, 0xFFFF4DFF, 0xFF66FF66, 0xFFFF9933, 0xFF4D79FF, 0xFFFFFFFF};
    private static final int LOCK_COLOR = 0xFFFFFF00;

    private Hud() {}

    public static void render(DrawContext ctx, RenderTickCounter tickCounter) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null || mc.options.hudHidden) return;
        Miner m = Miner.I;
        if (Cfg.tracers) tracers(ctx, mc, m);
        if (Cfg.overlay) {
            TextRenderer tr = mc.textRenderer;
            int y = 4;
            ctx.drawTextWithShadow(tr, "OreLock : " + (m.enabled ? "ON" : "OFF") + "  |  minés : " + m.mined,
                    4, y, m.enabled ? 0xFF55FF55 : 0xFFAAAAAA);
            y += 10;
            ctx.drawTextWithShadow(tr, m.status, 4, y, 0xFFFFFF55);
            y += 10;
            if (m.locked != null && m.enabled) {
                ctx.drawTextWithShadow(tr, "Cible : " + m.locked.getX() + " " + m.locked.getY() + " " + m.locked.getZ(), 4, y, 0xFFFFFFFF);
            }
        }
    }

    private static void tracers(DrawContext ctx, MinecraftClient mc, Miner m) {
        Camera cam = mc.gameRenderer.getCamera();
        int w = mc.getWindow().getScaledWidth();
        int h = mc.getWindow().getScaledHeight();
        double fov = mc.options.getFov().getValue();

        for (BlockPos b : m.nearest) {
            if (b.equals(m.locked)) continue;
            Block bl = mc.world.getBlockState(b).getBlock();
            int color = PALETTE[Math.abs(Registries.BLOCK.getId(bl).hashCode()) % PALETTE.length];
            draw(ctx, mc, cam, b, w, h, fov, color, 1, false);
        }
        if (m.locked != null) draw(ctx, mc, cam, m.locked, w, h, fov, LOCK_COLOR, 3, true);
    }

    private static void draw(DrawContext ctx, MinecraftClient mc, Camera cam, BlockPos b, int w, int h,
                             double fov, int color, int th, boolean label) {
        Vec3d pos = Vec3d.ofCenter(b);
        Vec3d d = pos.subtract(cam.getPos());
        double yaw = Math.toRadians(cam.getYaw()), pitch = Math.toRadians(cam.getPitch());
        double cp = Math.cos(pitch), sp = Math.sin(pitch), cy = Math.cos(yaw), sy = Math.sin(yaw);
        double fx = -sy * cp, fy = -sp, fz = cy * cp;     // avant
        double rx = -cy, rz = -sy;                         // droite
        double ux = -rz * fy, uy = rz * fx - rx * fz, uz = rx * fy; // haut = droite x avant
        double zc = d.x * fx + d.y * fy + d.z * fz;
        double xc = d.x * rx + d.z * rz;
        double yc = d.x * ux + d.y * uy + d.z * uz;

        double cx = w / 2.0, cyy = h / 2.0;
        double tanV = Math.tan(Math.toRadians(fov) / 2.0);
        double aspect = w / (double) h;
        double sx, sy2;
        boolean front = zc > 0.05;
        if (front) {
            sx = cx + (xc / zc) / (tanV * aspect) * cx;
            sy2 = cyy - (yc / zc) / tanV * cyy;
        } else {
            double vx = xc, vy = -yc;
            double len = Math.sqrt(vx * vx + vy * vy);
            if (len < 1e-6) { vx = 0; vy = 1; len = 1; }
            sx = cx + vx / len * 10000.0;
            sy2 = cyy + vy / len * 10000.0;
        }

        boolean inside = front && sx >= 0 && sx < w && sy2 >= 0 && sy2 < h;
        double margin = 8;
        double vx = sx - cx, vy = sy2 - cyy;
        double kx = Math.abs(vx) < 1e-6 ? Double.MAX_VALUE : (cx - margin) / Math.abs(vx);
        double ky = Math.abs(vy) < 1e-6 ? Double.MAX_VALUE : (cyy - margin) / Math.abs(vy);
        double k = Math.min(1.0, Math.min(kx, ky));
        int ex = (int) Math.round(cx + vx * k);
        int ey = (int) Math.round(cyy + vy * k);

        line(ctx, (int) cx, (int) cyy, ex, ey, color, th);
        if (inside) {
            ctx.fill(ex - 3, ey - 3, ex + 4, ey + 4, color);
            if (label) {
                double dist = Math.sqrt(d.x * d.x + d.y * d.y + d.z * d.z);
                ctx.drawTextWithShadow(mc.textRenderer, Math.round(dist) + " m", ex + 7, ey - 4, color);
            }
        }
    }

    private static void line(DrawContext ctx, int x0, int y0, int x1, int y1, int color, int th) {
        int steps = Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0));
        if (steps == 0) { ctx.fill(x0, y0, x0 + th, y0 + th, color); return; }
        steps = Math.min(steps, 1500);
        int stride = Math.max(1, th);
        for (int i = 0; i <= steps; i += stride) {
            int x = x0 + (x1 - x0) * i / steps;
            int y = y0 + (y1 - y0) * i / steps;
            ctx.fill(x, y, x + th, y + th, color);
        }
    }
}
