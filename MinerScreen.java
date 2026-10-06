package com.example.orelock;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

/** Menu unique : tout se regle ici, a la souris. Ne met PAS le jeu en pause. */
public class MinerScreen extends Screen {
    private static final int PW = 440, COL = 216;

    private record Preset(String label, List<String> ids) {}

    private static final List<Preset> PRESETS = List.of(
            new Preset("Diam.", List.of("minecraft:diamond_ore", "minecraft:deepslate_diamond_ore")),
            new Preset("Émer.", List.of("minecraft:emerald_ore", "minecraft:deepslate_emerald_ore")),
            new Preset("Or", List.of("minecraft:gold_ore", "minecraft:deepslate_gold_ore", "minecraft:nether_gold_ore")),
            new Preset("Fer", List.of("minecraft:iron_ore", "minecraft:deepslate_iron_ore")),
            new Preset("Char.", List.of("minecraft:coal_ore", "minecraft:deepslate_coal_ore")),
            new Preset("Cuiv.", List.of("minecraft:copper_ore", "minecraft:deepslate_copper_ore")),
            new Preset("Lapis", List.of("minecraft:lapis_ore", "minecraft:deepslate_lapis_ore")),
            new Preset("Red.", List.of("minecraft:redstone_ore", "minecraft:deepslate_redstone_ore")),
            new Preset("Débris", List.of("minecraft:ancient_debris")),
            new Preset("Quartz", List.of("minecraft:nether_quartz_ore"))
    );

    private static String query = "";
    private static int scroll = 0;

    private int left, top, rows, rx, infoY, statusY;
    private final List<ButtonWidget> rowButtons = new ArrayList<>();
    private final List<Runnable> refreshers = new ArrayList<>();
    private List<Targets.Entry> results = new ArrayList<>();
    private TextFieldWidget search;

    public MinerScreen() {
        super(Text.literal("OreLock"));
    }

    @Override
    protected void init() {
        rowButtons.clear();
        refreshers.clear();
        rows = Math.max(4, Math.min(8, (height - 170) / 20));
        left = width / 2 - PW / 2;
        top = Math.max(6, height / 2 - (rows * 20 + 150) / 2);
        rx = left + PW - COL;
        buildLeft();
        buildRight();
        results = Targets.search(query);
        clampScroll();
        refreshRows();
    }

    // ---------------------------------------------------------------- colonne gauche : reglages

    private void buildLeft() {
        int y = top;
        ButtonWidget master = ButtonWidget.builder(masterLabel(), b -> {
            Miner.I.setEnabled(!Miner.I.enabled);
            b.setMessage(masterLabel());
        }).dimensions(left, y, COL, 24).build();
        addDrawableChild(master);
        refreshers.add(() -> master.setMessage(masterLabel()));
        y += 28;

        addDrawableChild(new ValueSlider(left, y, COL, 20, "Vitesse de minage x", 1, 100, 1, Cfg.mineMult, v -> Cfg.mineMult = v));
        y += 22;
        addDrawableChild(new ValueSlider(left, y, COL, 20, "Vitesse de déplacement x", 1, 50, 1, Cfg.moveMult, v -> Cfg.moveMult = v));
        y += 22;
        addDrawableChild(new ValueSlider(left, y, COL, 20, "Portée de minage ", 2.0, 4.5, 0.5, Cfg.reach, v -> Cfg.reach = v));
        y += 22;
        addDrawableChild(new ValueSlider(left, y, COL, 20, "Rayon de recherche ", 16, 1024, 16, Cfg.radius, v -> Cfg.radius = (int) Math.round(v)));
        y += 26;

        int hw = (COL - 4) / 2;
        addToggle(left, y, hw, "Sans délai", () -> Cfg.noDelay, () -> Cfg.noDelay = !Cfg.noDelay);
        addToggle(left + hw + 4, y, hw, "Ramasser drops", () -> Cfg.collect, () -> Cfg.collect = !Cfg.collect);
        y += 22;
        addToggle(left, y, hw, "Traits colorés", () -> Cfg.tracers, () -> Cfg.tracers = !Cfg.tracers);
        addToggle(left + hw + 4, y, hw, "Creuser tunnels", () -> Cfg.tunnel, () -> Cfg.tunnel = !Cfg.tunnel);
        y += 22;
        addToggle(left, y, hw, "Sécurité lave/eau", () -> Cfg.safeLiquids, () -> Cfg.safeLiquids = !Cfg.safeLiquids);
        addToggle(left + hw + 4, y, hw, "Sprint", () -> Cfg.sprint, () -> Cfg.sprint = !Cfg.sprint);
        y += 22;
        addToggle(left, y, hw, "Stop inv. plein", () -> Cfg.stopFull, () -> Cfg.stopFull = !Cfg.stopFull);
        addToggle(left + hw + 4, y, hw, "Stop vie basse", () -> Cfg.stopLowHp, () -> Cfg.stopLowHp = !Cfg.stopLowHp);
        y += 22;
        addToggle(left, y, COL, "Affichage HUD", () -> Cfg.overlay, () -> Cfg.overlay = !Cfg.overlay);
        y += 26;
        statusY = y;
    }

    private Text masterLabel() {
        return Miner.I.enabled
                ? Text.literal("AUTO-MINAGE : ON").formatted(Formatting.GREEN, Formatting.BOLD)
                : Text.literal("AUTO-MINAGE : OFF").formatted(Formatting.RED, Formatting.BOLD);
    }

    // ---------------------------------------------------------------- colonne droite : choix des blocs

    private void buildRight() {
        int y = top;
        search = new TextFieldWidget(textRenderer, rx, y, COL, 20, Text.literal("Recherche"));
        search.setMaxLength(40);
        search.setText(query);
        search.setPlaceholder(Text.literal("Nom du bloc (ex: aube)").formatted(Formatting.DARK_GRAY));
        search.setChangedListener(s -> {
            query = s;
            scroll = 0;
            results = Targets.search(query);
            refreshRows();
        });
        addDrawableChild(search);
        y += 22;

        String[] quick = {"Aube", "Éclat", "Nuit", "Glace"};
        int qw = (COL - 6) / 4;
        for (int i = 0; i < quick.length; i++) {
            String term = quick[i];
            addDrawableChild(ButtonWidget.builder(Text.literal(term), b -> search.setText(term))
                    .dimensions(rx + i * (qw + 2), y, qw, 20).build());
        }
        y += 22;

        for (int i = 0; i < rows; i++) {
            final int row = i;
            ButtonWidget b = ButtonWidget.builder(Text.empty(), btn -> {
                int idx = scroll + row;
                if (idx < results.size()) {
                    Cfg.toggleBlock(results.get(idx).id());
                    changed();
                }
            }).dimensions(rx, y + i * 20, COL, 20).build();
            rowButtons.add(b);
            addDrawableChild(b);
        }
        y += rows * 20 + 2;

        addDrawableChild(ButtonWidget.builder(Text.literal("^"), b -> { scroll -= rows; clampScroll(); refreshRows(); })
                .dimensions(rx, y, 24, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("v"), b -> { scroll += rows; clampScroll(); refreshRows(); })
                .dimensions(rx + 26, y, 24, 20).build());
        int bw = (COL - 52 - 4) / 2;
        addDrawableChild(ButtonWidget.builder(Text.literal("Tout ajouter"), b -> {
            for (Targets.Entry e : results) Cfg.blocks.add(e.id());
            changed();
        }).dimensions(rx + 52, y, bw, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Tout retirer"), b -> {
            for (Targets.Entry e : results) Cfg.blocks.remove(e.id());
            changed();
        }).dimensions(rx + 52 + bw + 4, y, bw, 20).build());
        y += 22;
        infoY = y;
        y += 12;

        int pw = (COL - 8) / 5;
        for (int i = 0; i < PRESETS.size(); i++) {
            Preset p = PRESETS.get(i);
            int x = rx + (i % 5) * (pw + 2);
            int yy = y + (i / 5) * 22;
            ButtonWidget b = ButtonWidget.builder(presetLabel(p), btn -> {
                if (Cfg.blocks.containsAll(p.ids())) p.ids().forEach(Cfg.blocks::remove);
                else Cfg.blocks.addAll(p.ids());
                changed();
            }).dimensions(x, yy, pw, 20).build();
            addDrawableChild(b);
            refreshers.add(() -> b.setMessage(presetLabel(p)));
        }
    }

    private Text presetLabel(Preset p) {
        boolean on = Cfg.blocks.containsAll(p.ids());
        return Text.literal(p.label()).formatted(on ? Formatting.GREEN : Formatting.WHITE);
    }

    private void changed() {
        Targets.invalidate();
        Cfg.save();
        results = Targets.search(query);
        clampScroll();
        refreshRows();
        for (Runnable r : refreshers) r.run();
    }

    private void clampScroll() {
        int max = Math.max(0, results.size() - rows);
        scroll = Math.max(0, Math.min(scroll, max));
    }

    private void refreshRows() {
        for (int i = 0; i < rowButtons.size(); i++) {
            ButtonWidget b = rowButtons.get(i);
            int idx = scroll + i;
            if (idx < results.size()) {
                Targets.Entry e = results.get(idx);
                boolean on = Cfg.blocks.contains(e.id());
                String s = textRenderer.trimToWidth((on ? "[x] " : "[ ] ") + e.name() + "  " + e.id(), COL - 12);
                b.visible = true;
                b.setMessage(Text.literal(s).formatted(on ? Formatting.GREEN : Formatting.WHITE));
            } else {
                b.visible = false;
            }
        }
    }

    private void addToggle(int x, int y, int w, String name, BooleanSupplier state, Runnable toggle) {
        ButtonWidget b = ButtonWidget.builder(label(name, state.getAsBoolean()), btn -> {
            toggle.run();
            Cfg.save();
            btn.setMessage(label(name, state.getAsBoolean()));
        }).dimensions(x, y, w, 20).build();
        addDrawableChild(b);
    }

    private static Text label(String name, boolean on) {
        return Text.literal(name + " : " + (on ? "ON" : "OFF")).formatted(on ? Formatting.GREEN : Formatting.RED);
    }

    // ---------------------------------------------------------------- affichage

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, 0x88000000);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        String info = query.isBlank()
                ? Cfg.blocks.size() + " bloc(s) sélectionné(s)"
                : results.size() + " résultat(s)  |  " + Cfg.blocks.size() + " sélectionné(s)";
        context.drawTextWithShadow(textRenderer, info, rx, infoY, 0xFFAAAAAA);
        context.drawTextWithShadow(textRenderer, "Statut : " + Miner.I.status, left, statusY, 0xFFFFFF55);
        context.drawTextWithShadow(textRenderer, "Minés : " + Miner.I.mined + "   Cibles : " + Miner.I.nearest.size() + "+", left, statusY + 11, 0xFFFFFFFF);
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (mouseX >= rx && verticalAmount != 0) {
            scroll -= (int) Math.signum(verticalAmount) * 2;
            clampScroll();
            refreshRows();
            return true;
        }
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (OreLockClient.menuKey.matchesKey(keyCode, scanCode) && !(getFocused() instanceof TextFieldWidget)) {
            close();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void removed() {
        Cfg.save();
    }
}
