package primevisuals;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class TexScreen extends Screen {
    private static final int GREEN = 0xFF3FD06A;
    private static final int GREEN_DARK = 0xFF1C5C34;

    // panel, bar, cell, cellHover, button, text, dim
    private static final int[][] THEMES = {
            {0xFF101018, 0xFF181826, 0xFF20202E, 0xFF30304A, 0xFF1E1E2C, 0xFFE8E8F0, 0xFF8A8AA3}, // Prime
            {0xFF3A3A3A, 0xFF4A4A4A, 0xFF5A5A5A, 0xFF6E6E6E, 0xFF474747, 0xFFFFFFFF, 0xFFBBBBBB}, // Minecraft
            {0xFFC6C6C6, 0xFFB0B0B0, 0xFFD8D8D8, 0xFFE8E8E8, 0xFFBDBDBD, 0xFF202020, 0xFF555555}, // Classic
            {0xFF0B0F14, 0xFF121820, 0xFF17202B, 0xFF22303F, 0xFF141C26, 0xFFEAF2FA, 0xFF7E92A6}, // Modern
            {0xFF0B1020, 0x28FFFFFF, 0x24FFFFFF, 0x3CFFFFFF, 0x1EFFFFFF, 0xFFFFFFFF, 0xFFB0B8C8}, // Glass
            {0xFF1A1A1A, 0xFF1A1A1A, 0xFF262626, 0xFF333333, 0xFF262626, 0xFFF2F2F2, 0xFF999999}  // Flat
    };
    private static final String[] THEME_NAMES = {"Prime", "Minecraft", "Classic", "Modern", "Glass", "Flat"};
    private static final int[] ACCENTS = {0xFF8A5CFF, 0xFF3B82F6, 0xFF14B8A6, 0xFF3FD06A, 0xFFEF4444, 0xFFF59E0B, 0xFFEC4899, 0xFFD0D0D8};

    private int accent, accentDark, textC, dimC, cellC, cellHov, panelC, barC, btnC;
    private int view = 0; // 0 main, 1 settings, 2 look
    private boolean bgTried;
    private Identifier bgId;
    private int bgW, bgH;


    private record Hit(int x, int y, int w, int h, Runnable r) {}
    private record Thumb(Identifier id, int w, int h) {}

    private final Config cfg = Config.INSTANCE;
    private final List<Hit> hits = new ArrayList<>();
    private final Map<String, Thumb> thumbs = new HashMap<>();
    private final Set<String> failed = new HashSet<>();
    private int thumbCounter;
    private int loadBudget;
    private boolean dirty;

    private int leftTab = 0;
    private int rightTab = 0;
    private int listRow = 0;
    private int gridRow = 0;
    private int sort = 1;   // 0 A-Z, 1 newest, 2 oldest
    private int filter = 0; // 0 all, 1 favorites
    private Category cat = Categories.ALL.get(0);
    private String query = "";
    private TextFieldWidget search;

    private int px, py, pw, ph;
    private final int leftW = 150;
    private final int rightW = 150;

    private PackInfo hoverPack;
    private boolean hoverSet;
    private String hoverSound;

    public TexScreen() {
        super(Text.literal("Prime Visuals"));
        Library.scanAsync();
    }

    @Override
    protected void init() {
        applyTheme();
        pw = Math.min(width - 20, 920);
        ph = Math.min(height - 20, 540);
        px = (width - pw) / 2;
        py = (height - ph) / 2;
        int cx = px + leftW;
        int cw = pw - leftW - rightW;
        search = new TextFieldWidget(textRenderer, cx + cw - 136, py + 32, 128, 14, Text.literal("Search"));
        search.setPlaceholder(Text.literal("Search styles..."));
        search.setText(query);
        search.setChangedListener(s -> {
            query = s.toLowerCase(Locale.ROOT);
            gridRow = 0;
        });
        addDrawableChild(search);
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        // no vanilla blur / dirt background
    }

    private void text(DrawContext c, String s, int x, int y, int col) {
        c.drawText(textRenderer, s, x, y, col, false);
    }

    private void btn(DrawContext c, int x, int y, int w, int h, String label, int bg, int mx, int my, Runnable r) {
        boolean hov = mx >= x && mx < x + w && my >= y && my < y + h;
        c.fill(x, y, x + w, y + h, bg);
        if (hov) c.drawBorder(x, y, w, h, accent);
        text(c, label, x + (w - textRenderer.getWidth(label)) / 2, y + (h - 8) / 2, textC);
        hits.add(new Hit(x, y, w, h, r));
    }

    private List<Category> tabCats() {
        List<Category> l = new ArrayList<>();
        for (Category k : Categories.ALL) {
            if (k.extra() == (leftTab == 1)) l.add(k);
        }
        return l;
    }

    private String selectedName(Category k) {
        return cfg.selected.getOrDefault(k.id(), "");
    }

    private void select(Category k, PackInfo p) {
        String name = p == null ? "" : p.name;
        if (name.equals(selectedName(k))) name = "";
        if (name.isEmpty()) cfg.selected.remove(k.id());
        else cfg.selected.put(k.id(), name);
        cfg.save();
        dirty = true;
    }

    private boolean isFav(Category k, PackInfo p) {
        return cfg.favorites.contains(k.id() + "|" + p.name);
    }

    private void toggleFav(Category k, PackInfo p) {
        String key = k.id() + "|" + p.name;
        if (!cfg.favorites.remove(key)) cfg.favorites.add(key);
        cfg.save();
    }

    @Override
    public void render(DrawContext c, int mx, int my, float delta) {
        hits.clear();
        loadBudget = 4;
        hoverSet = false;
        hoverPack = null;
        hoverSound = null;
        Library lib = Library.current;

        c.fill(0, 0, width, height, 0x88000000);
        drawWindow(c);
        c.drawBorder(px, py, pw, ph, 0xFF3A2D66);
        c.fill(px + 1, py + 1, px + pw - 1, py + 28, barC);
        text(c, "PRIME VISUALS", px + 10, py + 6, accent);
        text(c, "TEXTURES MANAGER", px + 10, py + 16, dimC);
        btn(c, px + pw - 20, py + 7, 14, 14, "x", btnC, mx, my, this::close);
        btn(c, px + pw - 58, py + 7, 34, 14, "LOOK", view == 2 ? accentDark : btnC, mx, my, () -> view = view == 2 ? 0 : 2);
        btn(c, px + pw - 118, py + 7, 56, 14, "SETTINGS", view == 1 ? accentDark : btnC, mx, my, () -> view = view == 1 ? 0 : 1);
        btn(c, px + pw - 176, py + 7, 54, 14, "EFFECTS", view == 3 ? accentDark : btnC, mx, my, () -> view = view == 3 ? 0 : 3);
        if (Library.scanning || lib == null) {
            text(c, "Scanning resource packs...", px + 170, py + 11, dimC);
        }

        if (view != 0) {
            search.visible = false;
            renderView(c, mx, my);
            bottomBar(c, mx, my);
            super.render(c, mx, my, delta);
            return;
        }
        search.visible = true;

        // ---------- left column ----------
        int tw = leftW / 2;
        btn(c, px + 2, py + 32, tw - 2, 14, "COLLECTIONS", leftTab == 0 ? accentDark : btnC, mx, my, () -> setLeftTab(0));
        btn(c, px + 2 + tw, py + 32, tw - 3, 14, "EXTRAS", leftTab == 1 ? accentDark : btnC, mx, my, () -> setLeftTab(1));

        List<Category> cats = tabCats();
        int rowH = 22;
        int listTop = py + 50;
        int listBottom = py + ph - 22;
        int vis = Math.max(1, (listBottom - listTop) / rowH);
        listRow = Math.max(0, Math.min(listRow, Math.max(0, cats.size() - vis)));
        for (int i = 0; i < vis && listRow + i < cats.size(); i++) {
            Category k = cats.get(listRow + i);
            int y = listTop + i * rowH;
            int x = px + 2;
            int w = leftW - 4;
            boolean sel = k == cat;
            boolean hov = mx >= x && mx < x + w && my >= y && my < y + rowH - 2;
            c.fill(x, y, x + w, y + rowH - 2, sel ? accentDark : hov ? cellHov : cellC);
            if (sel) c.drawBorder(x, y, w, rowH - 2, accent);
            c.drawItem(new ItemStack(k.icon()), x + 3, y + 2);
            text(c, k.name(), x + 24, y + 3, textC);
            boolean snd = isSound(k);
            String s = snd ? soundLabel(k, curSound(k)) : selectedName(k);
            text(c, s.isEmpty() ? "Vanilla" : textRenderer.trimToWidth(s, w - 50), x + 24, y + 12, s.isEmpty() ? dimC : GREEN);
            if (snd || lib != null) {
                String n = String.valueOf(snd ? soundOpts(k).length - 1 : lib.count(k));
                text(c, n, x + w - 4 - textRenderer.getWidth(n), y + 3, dimC);
            }
            hits.add(new Hit(x, y, w, rowH - 2, () -> {
                cat = k;
                gridRow = 0;
            }));
        }

        // ---------- center ----------
        int cx = px + leftW;
        int cw = pw - leftW - rightW;
        text(c, cat.name(), cx + 8, py + 32, textC);
        String now = selectedName(cat);
        if (isSound(cat)) {
            renderSoundGrid(c, mx, my, cx, cw);
        } else if (lib == null) {
            text(c, "Please wait...", cx + 8, py + 46, dimC);
        } else {
            List<PackInfo> list = new ArrayList<>(lib.packsFor(cat));
            text(c, list.size() + " packs - now: " + (now.isEmpty() ? "Vanilla" : now), cx + 8, py + 46, dimC);

            btn(c, cx + 8, py + 60, 30, 14, "All", filter == 0 ? accentDark : btnC, mx, my, () -> { filter = 0; gridRow = 0; });
            btn(c, cx + 40, py + 60, 58, 14, "Favorites", filter == 1 ? accentDark : btnC, mx, my, () -> { filter = 1; gridRow = 0; });
            int ex = cx + cw - 8;
            btn(c, ex - 42, py + 60, 42, 14, "Oldest", sort == 2 ? accentDark : btnC, mx, my, () -> sort = 2);
            btn(c, ex - 42 - 46, py + 60, 44, 14, "Newest", sort == 1 ? accentDark : btnC, mx, my, () -> sort = 1);
            btn(c, ex - 42 - 46 - 32, py + 60, 30, 14, "A-Z", sort == 0 ? accentDark : btnC, mx, my, () -> sort = 0);

            if (filter == 1) list.removeIf(p -> !isFav(cat, p));
            if (!query.isEmpty()) list.removeIf(p -> !p.name.toLowerCase(Locale.ROOT).contains(query));
            if (sort == 0) list.sort(Comparator.comparing(p -> p.name.toLowerCase(Locale.ROOT)));
            else if (sort == 1) list.sort(Comparator.comparingLong((PackInfo p) -> p.modified).reversed());
            else list.sort(Comparator.comparingLong((PackInfo p) -> p.modified));

            List<PackInfo> cells = new ArrayList<>();
            if (query.isEmpty() && filter == 0) cells.add(null); // vanilla
            cells.addAll(list);

            int cellW = cfg.grid == 0 ? 44 : cfg.grid == 2 ? 70 : 54;
            int cellH = cfg.grid == 0 ? 48 : cfg.grid == 2 ? 74 : 56;
            int ts = cfg.grid == 0 ? 24 : cfg.grid == 2 ? 48 : 32;
            int gx = cx + 8;
            int gy = py + 80;
            int gridBottom = py + ph - 22;
            int cols = Math.max(1, (cw - 16) / cellW);
            int rowsVis = Math.max(1, (gridBottom - gy) / cellH);
            int totalRows = (cells.size() + cols - 1) / cols;
            gridRow = Math.max(0, Math.min(gridRow, Math.max(0, totalRows - rowsVis)));
            int start = gridRow * cols;
            int end = Math.min(cells.size(), (gridRow + rowsVis) * cols);
            for (int i = start; i < end; i++) {
                PackInfo p = cells.get(i);
                int col = i % cols;
                int row = i / cols - gridRow;
                int x = gx + col * cellW;
                int y = gy + row * cellH;
                int w = cellW - 2;
                int h = cellH - 2;
                boolean hov = mx >= x && mx < x + w && my >= y && my < y + h;
                boolean isSel = p == null ? now.isEmpty() : p.name.equals(now);
                c.fill(x, y, x + w, y + h, hov ? cellHov : cellC);
                if (isSel) c.drawBorder(x, y, w, h, GREEN);
                else if (hov) c.drawBorder(x, y, w, h, accent);
                if (hov) {
                    hoverSet = true;
                    hoverPack = p;
                }
                drawPreview(c, lib, p, x + (w - ts) / 2, y + 3, ts);
                String label = p == null ? "Vanilla" : p.name;
                text(c, textRenderer.trimToWidth(label, w - 4), x + 3, y + h - 11, textC);
                if (p != null && isFav(cat, p)) text(c, "*", x + w - 8, y + 2, 0xFFFFD84A);
                final PackInfo fp = p;
                hits.add(new Hit(x, y, w, h, () -> select(cat, fp)));
            }
            if (cells.isEmpty()) text(c, "No packs with this texture in resourcepacks/", cx + 8, gy + 4, dimC);
        }

        // ---------- right column ----------
        int rx = px + pw - rightW;
        int rtw = rightW / 2;
        btn(c, rx + 2, py + 32, rtw - 3, 14, "INSPECTOR", rightTab == 0 ? accentDark : btnC, mx, my, () -> rightTab = 0);
        btn(c, rx + 2 + rtw, py + 32, rtw - 4, 14, "PRESETS", rightTab == 1 ? accentDark : btnC, mx, my, () -> rightTab = 1);

        if (rightTab == 0 && isSound(cat)) {
            renderSoundInspector(c, mx, my, rx);
        } else if (rightTab == 0) {
            PackInfo cur = lib == null ? null : lib.pack(now);
            PackInfo prev = hoverSet ? hoverPack : cur;
            int box = 96;
            int bx = rx + (rightW - box) / 2;
            c.fill(bx - 2, py + 52, bx + box + 2, py + 52 + box + 4, cellC);
            drawPreview(c, lib, prev, bx, py + 54, box);
            String pn = prev == null ? "Vanilla" : prev.name;
            text(c, textRenderer.trimToWidth(pn, rightW - 12), rx + 6, py + 160, textC);
            text(c, prev == null ? "Minecraft's own textures" : (lib == null ? "" : lib.matches(cat, prev).size() + " textures"),
                    rx + 6, py + 172, dimC);
            boolean active = prev == null ? now.isEmpty() : prev.name.equals(now);
            final PackInfo fprev = prev;
            btn(c, rx + 6, py + ph - 62, rightW - 12, 16, active ? "ACTIVE" : "SELECT", active ? GREEN_DARK : btnC, mx, my,
                    () -> select(cat, fprev));
            btn(c, rx + 6, py + ph - 42, rightW - 12, 16, "Reset category", btnC, mx, my, () -> {
                cfg.selected.remove(cat.id());
                cfg.save();
                dirty = true;
            });
        } else {
            btn(c, rx + 6, py + 54, rightW - 12, 16, "Save current", GREEN_DARK, mx, my, () -> {
                cfg.presets.put("Preset " + (cfg.presets.size() + 1), new HashMap<>(cfg.selected));
                cfg.save();
            });
            int y = py + 76;
            for (String name : new ArrayList<>(cfg.presets.keySet())) {
                if (y > py + ph - 40) break;
                btn(c, rx + 6, y, rightW - 32, 16, name, btnC, mx, my, () -> {
                    Map<String, String> m = cfg.presets.get(name);
                    if (m != null) {
                        cfg.selected = new HashMap<>(m);
                        cfg.save();
                        dirty = true;
                    }
                });
                btn(c, rx + rightW - 24, y, 18, 16, "x", btnC, mx, my, () -> {
                    cfg.presets.remove(name);
                    cfg.save();
                });
                y += 19;
            }
        }

        bottomBar(c, mx, my);

        super.render(c, mx, my, delta); // search box
    }

    private static int darken(int col) {
        int r = ((col >> 16) & 255) * 45 / 100;
        int g = ((col >> 8) & 255) * 45 / 100;
        int b = (col & 255) * 45 / 100;
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private void applyTheme() {
        int[] t = THEMES[Math.max(0, Math.min(cfg.theme, THEMES.length - 1))];
        barC = t[1];
        cellC = t[2];
        cellHov = t[3];
        btnC = t[4];
        textC = t[5];
        dimC = t[6];
        accent = ACCENTS[Math.max(0, Math.min(cfg.accent, ACCENTS.length - 1))];
        accentDark = darken(accent);
        int alpha = Math.round(Math.max(30, Math.min(100, cfg.opacity)) * 255 / 100f);
        panelC = (alpha << 24) | (t[0] & 0xFFFFFF);
    }

    private void loadBg() {
        if (bgTried) return;
        bgTried = true;
        Path f = FabricLoader.getInstance().getConfigDir().resolve("primevisuals_bg.png");
        if (!Files.isRegularFile(f)) return;
        try (InputStream in = Files.newInputStream(f)) {
            NativeImage img = NativeImage.read(in);
            NativeImageBackedTexture tex = new NativeImageBackedTexture(img);
            Identifier id = Identifier.of("primevisuals", "background");
            client.getTextureManager().registerTexture(id, tex);
            bgId = id;
            bgW = img.getWidth();
            bgH = img.getHeight();
        } catch (Exception ignored) {
        }
    }

    private void drawWindow(DrawContext c) {
        if (cfg.bg) loadBg();
        if (cfg.bg && bgId != null) {
            float scale = Math.max((float) pw / bgW, (float) ph / bgH);
            int sw = Math.min(bgW, Math.round(pw / scale));
            int sh = Math.min(bgH, Math.round(ph / scale));
            float u = (bgW - sw) / 2f;
            float v = (bgH - sh) / 2f;
            c.drawTexture(RenderLayer::getGuiTextured, bgId, px, py, u, v, pw, ph, sw, sh, bgW, bgH);
            int oa = Math.round(Math.max(30, Math.min(100, cfg.opacity)) * 2.04f);
            c.fill(px, py, px + pw, py + ph, (oa << 24) | (panelC & 0xFFFFFF));
        } else {
            c.fill(px, py, px + pw, py + ph, panelC);
        }
    }

    private void bottomBar(DrawContext c, int mx, int my) {
        c.fill(px + 1, py + ph - 20, px + pw - 1, py + ph - 1, barC);
        text(c, view == 0
                        ? "click: select - click again: vanilla - F: favorite - changes apply when you close"
                        : "Look and settings are saved automatically",
                px + 8, py + ph - 14, dimC);
        btn(c, px + pw - 80, py + ph - 18, 74, 14, "RESET ALL", btnC, mx, my, () -> {
            cfg.selected.clear();
            cfg.save();
            dirty = true;
        });
    }

    @Override
    public boolean shouldPause() {
        // keep the game running on the EFFECTS screen and on the Kill Sound category so sounds/effects can be previewed
        return !(view == 3 || (view == 0 && isSound(cat)));
    }

    private int choiceRow(DrawContext c, int mx, int my, int x, int y, String[][] opts, String selected,
                          java.util.function.Consumer<String> set) {
        int bw = 78;
        int gap = 4;
        int perRow = Math.max(1, (pw - 32) / (bw + gap));
        for (int i = 0; i < opts.length; i++) {
            int col = i % perRow;
            int row = i / perRow;
            final String id = opts[i][0];
            btn(c, x + col * (bw + gap), y + row * 20, bw, 16, opts[i][1], id.equals(selected) ? accentDark : btnC, mx, my, () -> {
                set.accept(id);
                cfg.save();
            });
        }
        return y + ((opts.length + perRow - 1) / perRow) * 20;
    }

    private void toggleBtn(DrawContext c, int mx, int my, int x, int y, boolean on, Runnable r) {
        btn(c, x, y, 46, 14, on ? "ON" : "OFF", on ? GREEN_DARK : btnC, mx, my, () -> {
            r.run();
            cfg.save();
        });
    }

    private void renderEffects(DrawContext c, int mx, int my) {
        int x = px + 16;
        int y = py + 54;
        text(c, "EFFECTS", px + 64, py + 37, accent);
        text(c, "AURA", x, y, dimC);
        y = choiceRow(c, mx, my, x, y + 11, Fx.AURAS, cfg.aura, v -> cfg.aura = v) + 4;
        text(c, "HIT EFFECT", x, y, dimC);
        y = choiceRow(c, mx, my, x, y + 11, Fx.HITS, cfg.hit, v -> cfg.hit = v) + 4;
        text(c, "KILL EFFECT", x, y, dimC);
        y = choiceRow(c, mx, my, x, y + 11, Fx.KILLS, cfg.kill, v -> cfg.kill = v) + 4;
        text(c, "KILL SOUND", x, y, dimC);
        y = choiceRow(c, mx, my, x, y + 11, Fx.SOUNDS, cfg.killSoundId, v -> cfg.killSoundId = v) + 4;
        text(c, "KILL LIGHTNING BOLT", x, y, dimC);
        y = choiceRow(c, mx, my, x, y + 11, Fx.BOLTS, cfg.bolt, v -> cfg.bolt = v) + 4;
        text(c, "RING COLOUR (ground circle)", x, y, dimC);
        y = choiceRow(c, mx, my, x, y + 11, Fx.RINGS, cfg.ring, v -> cfg.ring = v) + 4;
        text(c, "RING STYLE", x, y, dimC);
        y = choiceRow(c, mx, my, x, y + 11, Fx.RING_STYLES, cfg.ringStyle, v -> cfg.ringStyle = v) + 6;

        text(c, "Ring around me", x, y + 3, textC);
        toggleBtn(c, mx, my, x + 110, y, cfg.ringSelf, () -> cfg.ringSelf = !cfg.ringSelf);
        text(c, "Ring around target", x + 190, y + 3, textC);
        toggleBtn(c, mx, my, x + 310, y, cfg.ringTarget, () -> cfg.ringTarget = !cfg.ringTarget);
        y += 20;

        text(c, "Aura density", x, y + 3, textC);
        btn(c, x + 110, y, 18, 14, "-", btnC, mx, my, () -> {
            cfg.density = Math.max(1, cfg.density - 1);
            cfg.save();
        });
        text(c, String.valueOf(cfg.density), x + 134, y + 3, textC);
        btn(c, x + 148, y, 18, 14, "+", btnC, mx, my, () -> {
            cfg.density = Math.min(3, cfg.density + 1);
            cfg.save();
        });
        text(c, "Aura in first person", x + 190, y + 3, textC);
        toggleBtn(c, mx, my, x + 310, y, cfg.auraFirstPerson, () -> cfg.auraFirstPerson = !cfg.auraFirstPerson);
        y += 24;

        text(c, "Kill sound volume", x, y + 3, textC);
        btn(c, x + 120, y, 18, 14, "-", btnC, mx, my, () -> {
            cfg.killVolume = Math.max(10, cfg.killVolume - 10);
            cfg.save();
        });
        text(c, cfg.killVolume + "%", x + 144, y + 3, textC);
        btn(c, x + 182, y, 18, 14, "+", btnC, mx, my, () -> {
            cfg.killVolume = Math.min(100, cfg.killVolume + 10);
            cfg.save();
        });
        text(c, "Bolt thunder", x + 250, y + 3, textC);
        toggleBtn(c, mx, my, x + 330, y, cfg.boltSound, () -> cfg.boltSound = !cfg.boltSound);
        y += 24;

        btn(c, x, y, 110, 16, "Test hit", btnC, mx, my, Fx::testHit);
        btn(c, x + 116, y, 110, 16, "Test kill", btnC, mx, my, Fx::testKill);
        btn(c, x + 232, y, 110, 16, "Spawn bot", btnC, mx, my, () -> Bots.spawn(client));
        text(c, "Aura: third person (F5). Ring: always. Bot: /bot spawn, /bot clear. Only you see all this.", x, y + 22, dimC);
    }

    private void renderView(DrawContext c, int mx, int my) {
        btn(c, px + 10, py + 34, 46, 14, "< Back", btnC, mx, my, () -> view = 0);
        if (view == 3) {
            renderEffects(c, mx, my);
            return;
        }
        int x = px + 16;
        int y = py + 58;
        if (view == 1) {
            text(c, "SETTINGS", px + 64, py + 37, accent);
            text(c, "TEXTURE BROWSER", x, y, dimC);
            y += 14;
            text(c, "Grid size", x, y + 3, textC);
            String[] gs = {"Small", "Medium", "Large"};
            for (int i = 0; i < 3; i++) {
                final int fi = i;
                btn(c, x + 140 + i * 58, y, 54, 14, gs[i], cfg.grid == i ? accentDark : btnC, mx, my, () -> {
                    cfg.grid = fi;
                    cfg.save();
                    gridRow = 0;
                });
            }
            y += 28;
            text(c, "LIBRARY", x, y, dimC);
            y += 14;
            text(c, "Resource pack folder", x, y, textC);
            y += 11;
            text(c, textRenderer.trimToWidth(Library.packDir().toString(), pw - 40), x, y, dimC);
            y += 16;
            text(c, "Rescan packs", x, y + 3, textC);
            btn(c, x + 140, y, 80, 14, "Rescan now", btnC, mx, my, Library::scanAsync);
            y += 28;
            text(c, "Output pack: resourcepacks/" + Library.OUT, x, y, dimC);
            y += 11;
            text(c, "Your picks are written there when you close this window.", x, y, dimC);
        } else {
            text(c, "CUSTOMIZE THE LOOK", px + 64, py + 37, accent);
            text(c, "STYLE", x, y, dimC);
            y += 12;
            for (int i = 0; i < THEME_NAMES.length; i++) {
                final int fi = i;
                btn(c, x + i * 84, y, 80, 22, THEME_NAMES[i], cfg.theme == i ? accentDark : btnC, mx, my, () -> {
                    cfg.theme = fi;
                    cfg.save();
                    applyTheme();
                });
            }
            y += 36;
            text(c, "ACCENT COLOUR", x, y, dimC);
            y += 12;
            for (int i = 0; i < ACCENTS.length; i++) {
                final int fi = i;
                int sx = x + i * 26;
                c.fill(sx, y, sx + 22, y + 16, ACCENTS[i]);
                if (cfg.accent == i) c.drawBorder(sx - 1, y - 1, 24, 18, 0xFFFFFFFF);
                hits.add(new Hit(sx, y, 22, 16, () -> {
                    cfg.accent = fi;
                    cfg.save();
                    applyTheme();
                }));
            }
            y += 30;
            text(c, "WINDOW OPACITY", x, y, dimC);
            y += 12;
            btn(c, x, y, 18, 14, "-", btnC, mx, my, () -> {
                cfg.opacity = Math.max(30, cfg.opacity - 5);
                cfg.save();
                applyTheme();
            });
            text(c, cfg.opacity + "%", x + 26, y + 3, textC);
            btn(c, x + 62, y, 18, 14, "+", btnC, mx, my, () -> {
                cfg.opacity = Math.min(100, cfg.opacity + 5);
                cfg.save();
                applyTheme();
            });
            y += 28;
            text(c, "BACKGROUND IMAGE", x, y, dimC);
            y += 12;
            btn(c, x, y, 50, 14, cfg.bg ? "ON" : "OFF", cfg.bg ? GREEN_DARK : btnC, mx, my, () -> {
                cfg.bg = !cfg.bg;
                cfg.save();
                bgTried = false;
            });
            text(c, "Put a PNG at config/primevisuals_bg.png", x + 58, y + 3, dimC);
            if (cfg.bg && bgTried && bgId == null) {
                text(c, "No image found there yet.", x, y + 18, 0xFFEF4444);
            }
        }
    }

    private boolean isHit(Category k) {
        return k.id().equals("hitsound");
    }

    private boolean isSound(Category k) {
        return k.id().equals("killsound") || isHit(k);
    }

    private String[][] soundOpts(Category k) {
        return isHit(k) ? Fx.HIT_SOUNDS : Fx.SOUNDS;
    }

    private String curSound(Category k) {
        return isHit(k) ? cfg.hitSoundId : cfg.killSoundId;
    }

    private void setSound(Category k, String id) {
        if (isHit(k)) cfg.hitSoundId = id;
        else cfg.killSoundId = id;
        cfg.save();
    }

    private void previewSound(Category k, String id) {
        if (isHit(k)) Fx.previewHitSound(id);
        else Fx.previewSound(id);
    }

    private int soundVol(Category k) {
        return isHit(k) ? cfg.hitVolume : cfg.killVolume;
    }

    private void addSoundVol(Category k, int d) {
        int v = Math.max(10, Math.min(100, soundVol(k) + d));
        if (isHit(k)) cfg.hitVolume = v;
        else cfg.killVolume = v;
        cfg.save();
    }

    private String soundLabel(Category k, String id) {
        for (String[] o : soundOpts(k)) {
            if (o[0].equals(id)) return o[1];
        }
        return soundOpts(k)[1][1];
    }

    private String soundInfo(Category k, String id) {
        if (id.equals("off")) return "No sound";
        if (isHit(k)) {
            return switch (id) {
                case "blip" -> "Mario-style blip";
                case "bump" -> "Mario-style bump";
                default -> "Short 4.2 kHz tick from the video";
            };
        }
        return switch (id) {
            case "tiktok" -> "Chiptune A5 + E6 from the video";
            case "oneup" -> "Mario-style 1-Up jingle";
            case "stomp" -> "Mario-style stomp";
            default -> "Mario-style coin";
        };
    }

    private void renderSoundGrid(DrawContext c, int mx, int my, int cx, int cw) {
        String[][] opts = soundOpts(cat);
        text(c, (opts.length - 1) + " sounds - now: " + soundLabel(cat, curSound(cat)), cx + 8, py + 46, dimC);
        int cellW = 70;
        int cellH = 60;
        int gx = cx + 8;
        int gy = py + 66;
        int cols = Math.max(1, (cw - 16) / cellW);
        final Category k = cat;
        for (int i = 0; i < opts.length; i++) {
            final String id = opts[i][0];
            int x = gx + (i % cols) * cellW;
            int y = gy + (i / cols) * cellH;
            int w = cellW - 4;
            int h = cellH - 4;
            boolean hov = mx >= x && mx < x + w && my >= y && my < y + h;
            boolean sel = id.equals(curSound(k));
            c.fill(x, y, x + w, y + h, hov ? cellHov : cellC);
            if (sel) c.drawBorder(x, y, w, h, GREEN);
            else if (hov) c.drawBorder(x, y, w, h, accent);
            if (hov) hoverSound = id;
            c.drawItem(new ItemStack(id.equals("off") ? Items.BARRIER : Items.NOTE_BLOCK), x + (w - 16) / 2, y + 8);
            String label = opts[i][1];
            text(c, label, x + (w - textRenderer.getWidth(label)) / 2, y + h - 14, textC);
            hits.add(new Hit(x, y, w, h, () -> {
                setSound(k, id);
                previewSound(k, id);
            }));
        }
        int rows = (opts.length + cols - 1) / cols;
        text(c, isHit(cat) ? "Click = select + play. Plays when you hit someone." : "Click = select + play. Plays when you kill someone.",
                cx + 8, gy + rows * cellH + 4, dimC);
    }

    private void renderSoundInspector(DrawContext c, int mx, int my, int rx) {
        final Category k = cat;
        String id = hoverSound != null ? hoverSound : curSound(k);
        int box = 96;
        int bx = rx + (rightW - box) / 2;
        c.fill(bx - 2, py + 52, bx + box + 2, py + 52 + box + 4, cellC);
        c.getMatrices().push();
        c.getMatrices().translate(bx + (box - 64) / 2f, py + 54 + (box - 64) / 2f, 0);
        c.getMatrices().scale(4f, 4f, 1f);
        c.drawItem(new ItemStack(id.equals("off") ? Items.BARRIER : Items.NOTE_BLOCK), 0, 0);
        c.getMatrices().pop();
        text(c, soundLabel(k, id), rx + 6, py + 160, textC);
        text(c, textRenderer.trimToWidth(soundInfo(k, id), rightW - 12), rx + 6, py + 172, dimC);
        final String fid = id;
        btn(c, rx + 6, py + ph - 100, rightW - 12, 16, "PLAY", btnC, mx, my, () -> previewSound(k, fid));
        boolean active = id.equals(curSound(k));
        btn(c, rx + 6, py + ph - 80, rightW - 12, 16, active ? "ACTIVE" : "SELECT", active ? GREEN_DARK : btnC, mx, my,
                () -> setSound(k, fid));
        text(c, "Volume", rx + 6, py + ph - 56, dimC);
        btn(c, rx + 6, py + ph - 44, 18, 14, "-", btnC, mx, my, () -> addSoundVol(k, -10));
        text(c, soundVol(k) + "%", rx + 32, py + ph - 41, textC);
        btn(c, rx + 70, py + ph - 44, 18, 14, "+", btnC, mx, my, () -> addSoundVol(k, 10));
    }

    private void setLeftTab(int t) {
        leftTab = t;
        listRow = 0;
        gridRow = 0;
        List<Category> l = tabCats();
        if (!l.isEmpty()) cat = l.get(0);
    }

    private void drawPreview(DrawContext c, Library lib, PackInfo p, int x, int y, int size) {
        if (p == null) {
            int s = Math.max(1, size / 16);
            if (s == 1) {
                c.drawItem(new ItemStack(cat.icon()), x + (size - 16) / 2, y + (size - 16) / 2);
            } else {
                c.getMatrices().push();
                c.getMatrices().translate(x + (size - 16 * s) / 2f, y + (size - 16 * s) / 2f, 0);
                c.getMatrices().scale(s, s, 1f);
                c.drawItem(new ItemStack(cat.icon()), 0, 0);
                c.getMatrices().pop();
            }
            return;
        }
        if (lib == null) return;
        List<String> m = lib.matches(cat, p);
        if (m.isEmpty()) return;
        Thumb t = thumb(p, m.get(0));
        if (t == null) return;
        int frameH = (t.h() > t.w() && t.h() % t.w() == 0) ? t.w() : t.h();
        float scale = Math.min((float) size / t.w(), (float) size / frameH);
        int dw = Math.max(1, Math.round(t.w() * scale));
        int dh = Math.max(1, Math.round(frameH * scale));
        int dx = x + (size - dw) / 2;
        int dy = y + (size - dh) / 2;
        c.drawTexture(RenderLayer::getGuiTextured, t.id(), dx, dy, 0f, 0f, dw, dh, t.w(), frameH, t.w(), t.h());
    }

    private Thumb thumb(PackInfo p, String rel) {
        String key = p.name + "|" + rel;
        Thumb t = thumbs.get(key);
        if (t != null) return t;
        if (failed.contains(key) || loadBudget <= 0) return null;
        loadBudget--;
        byte[] b = p.read(rel);
        if (b == null) {
            failed.add(key);
            return null;
        }
        try {
            NativeImage img = NativeImage.read(new ByteArrayInputStream(b));
            Identifier id = Identifier.of("primevisuals", "thumb/" + (thumbCounter++));
            NativeImageBackedTexture tex = new NativeImageBackedTexture(img);
            tex.setFilter(false, false);
            client.getTextureManager().registerTexture(id, tex);
            t = new Thumb(id, img.getWidth(), img.getHeight());
            thumbs.put(key, t);
            return t;
        } catch (Exception e) {
            failed.add(key);
            return null;
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) return true;
        setFocused(null);
        if (button == 0) {
            for (int i = hits.size() - 1; i >= 0; i--) {
                Hit h = hits.get(i);
                if (mx >= h.x() && mx < h.x() + h.w() && my >= h.y() && my < h.y() + h.h()) {
                    h.r().run();
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double horizontal, double vertical) {
        int d = vertical > 0 ? -1 : 1;
        if (mx < px + leftW) listRow += d;
        else if (mx < px + pw - rightW) gridRow += d;
        else return false;
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!search.isFocused()) {
            if (keyCode == GLFW.GLFW_KEY_RIGHT_SHIFT) {
                close();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_F && hoverSet && hoverPack != null) {
                toggleFav(cat, hoverPack);
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void removed() {
        for (Thumb t : thumbs.values()) {
            client.getTextureManager().destroyTexture(t.id());
        }
        thumbs.clear();
        if (bgId != null) client.getTextureManager().destroyTexture(bgId);
        if (dirty) {
            dirty = false;
            Applier.apply(cfg);
        }
    }
}
