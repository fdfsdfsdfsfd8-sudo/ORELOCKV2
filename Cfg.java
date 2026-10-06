package com.example.orelock;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

public final class Cfg {
    /** Identifiants des blocs a miner (ex: minecraft:diamond_ore, modid:pierre_aube). */
    public static final Set<String> blocks = new LinkedHashSet<>();

    public static double mineMult = 10.0;   // 1 - 100
    public static double moveMult = 1.0;    // 1 - 50
    public static double reach = 4.0;       // 2 - 4.5
    public static int radius = 480;         // 16 - 1024

    public static boolean noDelay = true;
    public static boolean collect = true;
    public static boolean tracers = true;
    public static boolean tunnel = true;
    public static boolean safeLiquids = true;
    public static boolean stopFull = true;
    public static boolean stopLowHp = true;
    public static boolean sprint = true;
    public static boolean overlay = true;

    private Cfg() {}

    private static Path file() { return FabricLoader.getInstance().getConfigDir().resolve("orelock.json"); }

    public static void toggleBlock(String id) {
        if (!blocks.remove(id)) blocks.add(id);
    }

    public static void load() {
        try {
            Path f = file();
            if (!Files.exists(f)) return;
            JsonObject o = JsonParser.parseString(Files.readString(f)).getAsJsonObject();
            mineMult = clamp(num(o, "mineMult", mineMult), 1, 100);
            moveMult = clamp(num(o, "moveMult", moveMult), 1, 50);
            reach = clamp(num(o, "reach", reach), 2, 4.5);
            radius = (int) clamp(num(o, "radius", radius), 16, 1024);
            if (!o.has("v2")) radius = 480;   // ancienne config : ancien rayon trop petit
            noDelay = bool(o, "noDelay", noDelay);
            collect = bool(o, "collect", collect);
            tracers = bool(o, "tracers", tracers);
            tunnel = bool(o, "tunnel", tunnel);
            safeLiquids = bool(o, "safeLiquids", safeLiquids);
            stopFull = bool(o, "stopFull", stopFull);
            stopLowHp = bool(o, "stopLowHp", stopLowHp);
            sprint = bool(o, "sprint", sprint);
            overlay = bool(o, "overlay", overlay);
            if (o.has("blocks")) {
                blocks.clear();
                for (JsonElement e : o.getAsJsonArray("blocks")) blocks.add(e.getAsString());
            }
        } catch (Exception e) {
            System.err.println("[OreLock] Config illisible : " + e);
        }
        Targets.invalidate();
    }

    public static void save() {
        try {
            JsonObject o = new JsonObject();
            o.addProperty("mineMult", mineMult);
            o.addProperty("moveMult", moveMult);
            o.addProperty("reach", reach);
            o.addProperty("radius", radius);
            o.addProperty("v2", true);
            o.addProperty("noDelay", noDelay);
            o.addProperty("collect", collect);
            o.addProperty("tracers", tracers);
            o.addProperty("tunnel", tunnel);
            o.addProperty("safeLiquids", safeLiquids);
            o.addProperty("stopFull", stopFull);
            o.addProperty("stopLowHp", stopLowHp);
            o.addProperty("sprint", sprint);
            o.addProperty("overlay", overlay);
            JsonArray a = new JsonArray();
            for (String s : blocks) a.add(s);
            o.add("blocks", a);
            Files.writeString(file(), o.toString());
        } catch (Exception e) {
            System.err.println("[OreLock] Sauvegarde impossible : " + e);
        }
    }

    public static String fmt(double v) {
        if (v == Math.floor(v)) return Integer.toString((int) v);
        return String.format(Locale.ROOT, "%.1f", v);
    }

    private static boolean bool(JsonObject o, String k, boolean d) { return o.has(k) ? o.get(k).getAsBoolean() : d; }
    private static double num(JsonObject o, String k, double d) { return o.has(k) ? o.get(k).getAsDouble() : d; }
    private static double clamp(double v, double a, double b) { return Math.max(a, Math.min(b, v)); }
}
