package com.example.orelock;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class Targets {
    public record Entry(String id, String name, String norm, Block block) {}

    private static List<Entry> index;
    private static Set<Block> cache = new HashSet<>();
    private static boolean dirty = true;

    private Targets() {}

    public static void invalidate() { dirty = true; }

    public static String norm(String s) {
        return Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}+", "").toLowerCase(Locale.ROOT);
    }

    /** Index de tous les blocs du jeu (vanilla + mods), construit a la premiere utilisation. */
    public static List<Entry> index() {
        if (index == null) {
            List<Entry> l = new ArrayList<>();
            for (Identifier id : Registries.BLOCK.getIds()) {
                Block b = Registries.BLOCK.get(id);
                BlockState d = b.getDefaultState();
                if (d.isAir() || !d.getFluidState().isEmpty()) continue;
                String name = b.getName().getString();
                l.add(new Entry(id.toString(), name, norm(name + " " + id), b));
            }
            l.sort(Comparator.comparing(e -> norm(e.name())));
            index = l;
        }
        return index;
    }

    /** Recherche par nom affiche ou identifiant. Recherche vide = blocs deja selectionnes. */
    public static List<Entry> search(String query) {
        String q = norm(query).trim();
        List<Entry> out = new ArrayList<>();
        if (q.isEmpty()) {
            for (Entry e : index()) if (Cfg.blocks.contains(e.id())) out.add(e);
            return out;
        }
        String[] toks = q.split("\\s+");
        for (Entry e : index()) {
            boolean ok = true;
            for (String t : toks) {
                if (!e.norm().contains(t)) { ok = false; break; }
            }
            if (ok) out.add(e);
        }
        return out;
    }

    public static Set<Block> blocks() {
        if (dirty) {
            Set<Block> set = new HashSet<>();
            for (String id : Cfg.blocks) {
                Identifier ident = Identifier.tryParse(id);
                if (ident != null && Registries.BLOCK.containsId(ident)) set.add(Registries.BLOCK.get(ident));
            }
            cache = set;
            dirty = false;
        }
        return cache;
    }

    public static boolean matches(BlockState s) { return blocks().contains(s.getBlock()); }
}
