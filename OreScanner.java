package com.example.orelock;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.WorldChunk;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

public final class OreScanner {
    private OreScanner() {}

    public static List<BlockPos> scan(ClientWorld w, BlockPos center, int radius, Set<Block> blocks, int limit) {
        List<BlockPos> out = new ArrayList<>();
        if (blocks.isEmpty()) return out;
        Predicate<BlockState> pred = s -> blocks.contains(s.getBlock());
        int minCx = (center.getX() - radius) >> 4, maxCx = (center.getX() + radius) >> 4;
        int minCz = (center.getZ() - radius) >> 4, maxCz = (center.getZ() + radius) >> 4;
        int minY = Math.max(w.getBottomY(), center.getY() - radius);
        int maxY = Math.min(w.getTopY() - 1, center.getY() + radius);
        long r2 = (long) radius * radius;

        for (int cx = minCx; cx <= maxCx; cx++) {
            for (int cz = minCz; cz <= maxCz; cz++) {
                WorldChunk chunk = w.getChunkManager().getWorldChunk(cx, cz);
                if (chunk == null) continue;
                ChunkSection[] sections = chunk.getSectionArray();
                for (int i = 0; i < sections.length; i++) {
                    ChunkSection sec = sections[i];
                    int baseY = (chunk.getBottomSectionCoord() + i) << 4;
                    if (baseY + 15 < minY || baseY > maxY) continue;
                    if (sec.isEmpty() || !sec.hasAny(pred)) continue;
                    for (int ly = 0; ly < 16; ly++) {
                        int y = baseY + ly;
                        if (y < minY || y > maxY) continue;
                        for (int lz = 0; lz < 16; lz++) {
                            for (int lx = 0; lx < 16; lx++) {
                                if (!pred.test(sec.getBlockState(lx, ly, lz))) continue;
                                int x = (cx << 4) + lx, z = (cz << 4) + lz;
                                long dx = x - center.getX(), dz = z - center.getZ();
                                if (dx * dx + dz * dz > r2) continue;
                                out.add(new BlockPos(x, y, z));
                                if (out.size() >= limit) return out;
                            }
                        }
                    }
                }
            }
        }
        return out;
    }
}
