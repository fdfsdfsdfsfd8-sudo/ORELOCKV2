package com.example.orelock;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.ItemEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.hit.BlockHitResult;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Cerveau de l'auto-minage. Regle d'or : UNE SEULE cible verrouillee a la fois.
 * La cible ne change jamais tant qu'elle n'est pas minee ou abandonnee (timeout), meme si une autre devient plus proche.
 */
public final class Miner {
    public static final Miner I = new Miner();

    private enum Mode { SEEK, COLLECT }

    public static final int MAX_LINES = 24;
    private static final Set<Block> AVOID = Set.of(Blocks.FIRE, Blocks.SOUL_FIRE, Blocks.COBWEB,
            Blocks.SWEET_BERRY_BUSH, Blocks.POWDER_SNOW, Blocks.CACTUS, Blocks.MAGMA_BLOCK);

    public boolean enabled;
    public String status = "Inactif";
    public int mined;
    public BlockPos locked;                              // LA cible verrouillee
    public List<BlockPos> nearest = new ArrayList<>();   // cibles affichees (traits colores)

    private List<BlockPos> candidates = new ArrayList<>();
    private final Map<Long, Integer> banned = new HashMap<>();
    private int tick;
    private int lastScan = -1000;
    private Mode mode = Mode.SEEK;
    private Direction dir;
    private int lockedTicks, noProgress, lockLimit, nextRetry;
    private double bestDist;
    private String reason = "";
    private BlockPos collectOrigin;
    private int collectTicks, collectWait, dropId = -1;

    // ------------------------------------------------------------ controle

    public void setEnabled(boolean on) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (on) {
            if (Targets.blocks().isEmpty()) { status = "Choisis des blocs d'abord"; enabled = false; return; }
            mined = 0;
            banned.clear();
            locked = null;
            mode = Mode.SEEK;
            lastScan = -1000;
            nextRetry = 0;
            enabled = true;
            status = "Recherche...";
        } else {
            enabled = false;
            fullStop(mc);
            status = "Inactif";
        }
    }

    public void reset() {
        enabled = false;
        locked = null;
        mode = Mode.SEEK;
        candidates = new ArrayList<>();
        nearest = new ArrayList<>();
        status = "Inactif";
    }

    private void fullStop(MinecraftClient mc) {
        locked = null;
        dir = null;
        mode = Mode.SEEK;
        dropId = -1;
        Breaker.release(mc);
        Input.release(mc);
    }

    private void disable(String why) {
        enabled = false;
        fullStop(MinecraftClient.getInstance());
        status = why;
    }

    // ------------------------------------------------------------ boucle principale

    public void tick(MinecraftClient mc) {
        ClientPlayerEntity p = mc.player;
        ClientWorld w = mc.world;
        if (p == null || w == null) { if (enabled) reset(); return; }
        tick++;
        if (!enabled && !Cfg.tracers) return;

        if (Targets.blocks().isEmpty()) {
            candidates = new ArrayList<>();
            nearest = new ArrayList<>();
            if (enabled) disable("Aucun bloc sélectionné");
            return;
        }
        int interval = Cfg.radius > 128 ? 40 : 20;
        if (tick - lastScan >= interval || (candidates.isEmpty() && tick - lastScan >= 10)) scan(p, w);
        else if (tick % 10 == 0) refresh(p, w);
        if (!enabled) return;

        boolean creative = p.getAbilities().creativeMode;
        if (Cfg.stopLowHp && !creative && p.getHealth() <= 6f) { disable("Vie basse : arrêt"); return; }
        if (Cfg.stopFull && !creative && p.getInventory().getEmptySlot() == -1) { disable("Inventaire plein : arrêt"); return; }

        if (mode == Mode.COLLECT) { collect(mc, p, w); return; }

        if (locked != null && !Targets.matches(w.getBlockState(locked))) {   // minee
            mined++;
            BlockPos origin = locked;
            locked = null;
            dir = null;
            Breaker.release(mc);
            if (Cfg.collect) {
                mode = Mode.COLLECT;
                collectOrigin = origin;
                collectTicks = 0;
                collectWait = 0;
                dropId = -1;
            }
            return;
        }
        if (locked == null) {
            pick(p, w);
            if (locked == null) {
                if (!banned.isEmpty() && tick >= nextRetry) {   // cibles ecartees : on les retente au lieu de rester bloque
                    banned.clear();
                    nextRetry = tick + 100;
                    lastScan = -1000;
                    status = "Réessaie les cibles écartées";
                } else {
                    status = "Aucun minerai trouvé";
                }
                Input.stopMoving(mc);
                Breaker.release(mc);
                return;
            }
        }
        act(mc, p, w);
    }

    // ------------------------------------------------------------ scan et choix de cible

    private void scan(ClientPlayerEntity p, ClientWorld w) {
        lastScan = tick;
        candidates = OreScanner.scan(w, p.getBlockPos(), Cfg.radius, Targets.blocks(), 8000);
        candidates.removeIf(this::isBanned);
        sortAndShow(p);
    }

    private void refresh(ClientPlayerEntity p, ClientWorld w) {
        candidates.removeIf(b -> !Targets.matches(w.getBlockState(b)) || isBanned(b));
        sortAndShow(p);
    }

    private void sortAndShow(ClientPlayerEntity p) {
        final double px = p.getX(), py = p.getEyeY(), pz = p.getZ();
        candidates.sort(Comparator.comparingDouble(b -> cost(b, px, py, pz)));
        nearest = new ArrayList<>(candidates.subList(0, Math.min(MAX_LINES, candidates.size())));
    }

    /** Plus petit = meilleur. Le vertical compte double : on prefere les minerais au meme niveau. */
    private static double cost(BlockPos b, double px, double py, double pz) {
        double dx = b.getX() + 0.5 - px, dy = b.getY() + 0.5 - py, dz = b.getZ() + 0.5 - pz;
        return dx * dx + dz * dz + 4.0 * dy * dy;
    }

    private void pick(ClientPlayerEntity p, ClientWorld w) {
        sortAndShow(p);
        for (BlockPos b : candidates) {
            if (isBanned(b)) continue;
            if (!Targets.matches(w.getBlockState(b))) continue;
            if (Cfg.safeLiquids && liquidAdjacent(w, b)) { ban(b, 6000); continue; }
            locked = b;
            double ldx = b.getX() + 0.5 - p.getX(), ldy = b.getY() + 0.5 - p.getEyeY(), ldz = b.getZ() + 0.5 - p.getZ();
            lockLimit = 600 + (int) (Math.sqrt(ldx * ldx + ldy * ldy + ldz * ldz) * 25);
            dir = null;
            lockedTicks = 0;
            noProgress = 0;
            bestDist = Double.MAX_VALUE;
            return;
        }
    }

    private boolean isBanned(BlockPos b) {
        Integer until = banned.get(b.asLong());
        return until != null && until > tick;
    }

    private void ban(BlockPos b, int ticks) { banned.put(b.asLong(), tick + ticks); }

    private void giveUp(MinecraftClient mc, String why) {
        status = why + " → suivant";
        if (locked != null) ban(locked, 600);
        locked = null;
        dir = null;
        Breaker.release(mc);
    }

    // ------------------------------------------------------------ action sur la cible verrouillee

    private double range(ClientPlayerEntity p) {
        return Math.min(Cfg.reach, p.getBlockInteractionRange()) - 0.15;
    }

    private void act(MinecraftClient mc, ClientPlayerEntity p, ClientWorld w) {
        double range = range(p);
        Vec3d eye = p.getEyePos();

        lockedTicks++;
        double d = eye.distanceTo(Vec3d.ofCenter(locked));
        if (d < bestDist - 0.25) { bestDist = d; noProgress = 0; } else noProgress++;
        if (noProgress > 50 || lockedTicks > lockLimit) { giveUp(mc, "Cible inaccessible"); return; }

        // 1) cible visible et a portee : on la mine, sans bouger
        BlockHitResult direct = Breaker.aim(w, p, locked, range);
        if (direct != null) {
            Input.stopMoving(mc);
            Breaker.mine(mc, locked, range);
            noProgress = 0;
            status = "Mine la cible (" + Math.round(d) + " m)";
            if (Breaker.ticks() > 400) giveUp(mc, "Minage trop long");
            return;
        }

        // 2) quelque chose gene la vue : on casse ce bloc (tunnel droit vers la cible)
        BlockPos blk = Breaker.blocker(w, p, locked, range);
        if (blk != null) {
            if (!canBreak(w, blk)) { giveUp(mc, "Bloc infranchissable"); return; }
            Input.stopMoving(mc);
            Breaker.mine(mc, blk, range);
            noProgress = 0;
            status = "Dégage le chemin";
            if (Breaker.ticks() > 400) giveUp(mc, "Minage trop long");
            return;
        }

        // 3) trop loin : on s'approche
        Breaker.release(mc);
        if (!approach(mc, p, w, locked, false)) { giveUp(mc, reason); return; }
        status = "Va vers la cible (" + Math.round(d) + " m)";
    }

    // ------------------------------------------------------------ deplacement simple : une case a la fois, en creusant

    /** Avance d'une case vers le but en creusant si besoin. @return false si impossible. */
    private boolean approach(MinecraftClient mc, ClientPlayerEntity p, ClientWorld w, BlockPos goal, boolean collecting) {
        double range = range(p);
        BlockPos feet = p.getBlockPos();
        int dx = goal.getX() - feet.getX();
        int dz = goal.getZ() - feet.getZ();
        int dy = goal.getY() - feet.getY();

        if (dx == 0 && dz == 0) {                       // meme colonne
            Input.stopMoving(mc);
            if (collecting) return Math.abs(dy) < 2;
            if (dy < 0) {
                BlockPos below = feet.down();
                if (passable(w, below)) return true;    // on attend de tomber
                if (!canBreak(w, below) || !safeFloor(w, below)) { reason = "Sol dangereux"; return false; }
                if (!Breaker.mine(mc, below, range)) { reason = "Sol introuvable"; return false; }
                noProgress = 0;
                return true;
            }
            reason = "Cible au-dessus";
            return false;
        }

        Direction want = Math.abs(dx) >= Math.abs(dz) ? (dx > 0 ? Direction.EAST : Direction.WEST)
                                                      : (dz > 0 ? Direction.SOUTH : Direction.NORTH);
        if (dir == null) {
            dir = want;
        } else {                                        // on garde la direction : pas de zigzag
            boolean x = dir.getAxis() == Direction.Axis.X;
            int main = x ? dx : dz;
            int other = x ? dz : dx;
            int sign = (dir == Direction.EAST || dir == Direction.SOUTH) ? 1 : -1;
            if (main * sign <= 0 || Math.abs(other) > Math.abs(main) + 2) dir = want;
        }

        BlockPos ahead = feet.offset(dir);
        boolean stepUp = dy >= 2 && !passable(w, ahead);
        boolean down = dy <= -2;
        List<BlockPos> need;
        BlockPos stand;
        if (stepUp) {
            need = List.of(feet.up(2), ahead.up(), ahead.up(2));
            stand = ahead.up();
        } else if (down) {
            need = List.of(ahead, ahead.up(), ahead.down());
            stand = ahead.down();
        } else {
            need = List.of(ahead, ahead.up());
            stand = ahead;
        }

        for (BlockPos c : need) {
            if (!w.getChunkManager().isChunkLoaded(c.getX() >> 4, c.getZ() >> 4)) { reason = "Chunk non chargé"; return false; }
            if (passable(w, c)) continue;
            if (collecting) { reason = "Drop inaccessible"; return false; }   // on ne creuse pas pour un drop
            BlockState s = w.getBlockState(c);
            if (!s.getFluidState().isEmpty()) { reason = "Liquide devant"; return false; }
            if (!canBreak(w, c)) { reason = "Bloc infranchissable"; return false; }
            Input.stopMoving(mc);
            if (!Breaker.mine(mc, c, range)) { reason = "Bloc introuvable"; return false; }
            if (Breaker.ticks() > 400) { reason = "Minage trop long"; return false; }
            noProgress = 0;                                  // on creuse : ce n'est pas "bloque"
            return true;
        }
        Breaker.release(mc);
        if (!safeFloor(w, stand)) { reason = "Vide ou lave devant"; return false; }

        double tx = ahead.getX() + 0.5 - p.getX();
        double tz = ahead.getZ() + 0.5 - p.getZ();
        p.setYaw((float) (Math.atan2(tz, tx) * 180.0 / Math.PI) - 90.0f);
        p.setPitch(0f);

        GameOptions o = mc.options;
        o.forwardKey.setPressed(true);
        o.sprintKey.setPressed(Cfg.sprint && !stepUp && !down);
        o.jumpKey.setPressed(stepUp && p.isOnGround());     // on saute UNIQUEMENT pour monter une marche creusee

        if (Cfg.moveMult > 1.001 && p.isOnGround() && !stepUp && !down) {
            double base = Cfg.sprint ? 0.2806 : 0.2158;
            double speed = base * Cfg.moveMult;
            // jamais plus de la moitie de la distance restante par tick : pas de depassement de la cible
            int remaining = dir.getAxis() == Direction.Axis.X ? Math.abs(dx) : Math.abs(dz);
            speed = Math.min(speed, Math.max(base, remaining * 0.5));
            // on regarde devant : tant que les cases suivantes ne sont pas libres et avec un sol, on freine
            int n = (int) Math.ceil(speed) + 1;
            int clear = 0;
            BlockPos look = feet;
            for (int k = 1; k <= n; k++) {
                look = look.offset(dir);
                if (!standable(w, look)) break;
                clear++;
            }
            if (clear < n) speed = Math.min(speed, Math.max(base, clear * 0.5));
            double yaw = Math.toRadians(p.getYaw());
            Vec3d v = p.getVelocity();
            p.setVelocity(-Math.sin(yaw) * speed, v.y, Math.cos(yaw) * speed);
        }
        return true;
    }

    // ------------------------------------------------------------ ramassage des drops

    private void collect(MinecraftClient mc, ClientPlayerEntity p, ClientWorld w) {
        collectTicks++;
        status = "Ramasse les drops";
        if (collectTicks > 60) { endCollect(mc); return; }

        ItemEntity target = null;
        if (dropId != -1 && w.getEntityById(dropId) instanceof ItemEntity e && !e.isRemoved()) target = e;
        if (target == null) {
            dropId = -1;
            List<ItemEntity> items = w.getEntitiesByClass(ItemEntity.class, new Box(collectOrigin).expand(6.0), e -> !e.isRemoved());
            double best = Double.MAX_VALUE;
            for (ItemEntity e : items) {
                double dd = e.squaredDistanceTo(p);
                if (dd < best) { best = dd; target = e; }
            }
            if (target != null) dropId = target.getId();
        }
        if (target == null) {                           // rien (encore) : on attend un peu que les drops apparaissent
            Input.stopMoving(mc);
            if (++collectWait > 6) endCollect(mc);
            return;
        }

        Vec3d ip = target.getPos();
        double hd = Math.sqrt((ip.x - p.getX()) * (ip.x - p.getX()) + (ip.z - p.getZ()) * (ip.z - p.getZ()));
        if (hd < 0.8 && Math.abs(ip.y - p.getY()) < 2.0) {
            Input.stopMoving(mc);
            if (++collectWait > 15) endCollect(mc);
            return;
        }
        if (!approach(mc, p, w, BlockPos.ofFloored(ip), true)) endCollect(mc);
    }

    private void endCollect(MinecraftClient mc) {
        mode = Mode.SEEK;
        dropId = -1;
        dir = null;
        Input.stopMoving(mc);
    }

    // ------------------------------------------------------------ outils de securite

    private static boolean passable(ClientWorld w, BlockPos pos) {
        BlockState s = w.getBlockState(pos);
        if (!s.getFluidState().isEmpty()) return false;
        if (AVOID.contains(s.getBlock())) return false;
        return s.getCollisionShape(w, pos).isEmpty();
    }

    /** Case libre (pieds + tete) avec un vrai sol dessous : on peut y courir sans risque. */
    private static boolean standable(ClientWorld w, BlockPos cell) {
        if (!passable(w, cell) || !passable(w, cell.up())) return false;
        BlockPos b = cell.down();
        BlockState s = w.getBlockState(b);
        return s.getFluidState().isEmpty() && !AVOID.contains(s.getBlock()) && !s.getCollisionShape(w, b).isEmpty();
    }

    private static boolean liquidAdjacent(ClientWorld w, BlockPos pos) {
        BlockPos.Mutable m = new BlockPos.Mutable();
        for (Direction d : Direction.values()) {
            m.set(pos, d);
            if (!w.getBlockState(m).getFluidState().isEmpty()) return true;
        }
        return false;
    }

    /** On peut casser ce bloc sans danger (ni liquide autour, ni bedrock, ni coffre...). */
    private static boolean canBreak(ClientWorld w, BlockPos pos) {
        BlockState s = w.getBlockState(pos);
        if (s.isAir()) return true;
        if (!s.getFluidState().isEmpty()) return false;
        if (s.getHardness(w, pos) < 0) return false;
        boolean isTarget = Targets.matches(s);
        if (!Cfg.tunnel && !isTarget) return false;
        if (s.hasBlockEntity() && !isTarget) return false;
        if (Cfg.safeLiquids && liquidAdjacent(w, pos)) return false;
        return true;
    }

    /** Sol sur sous cette case : au plus 3 blocs de chute, jamais de liquide. */
    private static boolean safeFloor(ClientWorld w, BlockPos cell) {
        for (int k = 1; k <= 4; k++) {
            BlockPos b = cell.down(k);
            BlockState s = w.getBlockState(b);
            if (!s.getFluidState().isEmpty()) return false;
            if (!s.getCollisionShape(w, b).isEmpty()) return !AVOID.contains(s.getBlock());
        }
        return false;
    }
}
