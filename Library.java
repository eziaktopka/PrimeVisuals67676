package primevisuals;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

public class Library {
    public static final String OUT = "PrimeVisuals_Output";
    public static volatile Library current;
    public static volatile boolean scanning;

    public final List<PackInfo> packs = new ArrayList<>();
    private final Map<String, PackInfo> byName = new HashMap<>();
    // category id -> pack name -> matched texture paths
    private final Map<String, Map<String, List<String>>> index = new HashMap<>();

    public static Path packDir() {
        return FabricLoader.getInstance().getGameDir().resolve("resourcepacks");
    }

    public static void scanAsync() {
        if (scanning) return;
        scanning = true;
        Thread t = new Thread(() -> {
            try {
                Library l = new Library();
                l.scan();
                current = l;
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                scanning = false;
            }
        }, "PrimeVisuals-Scan");
        t.setDaemon(true);
        t.start();
    }

    private void scan() throws IOException {
        Path dir = packDir();
        if (Files.isDirectory(dir)) {
            List<Path> files;
            try (Stream<Path> s = Files.list(dir)) {
                files = s.toList();
            }
            for (Path p : files) {
                String fn = p.getFileName().toString();
                if (fn.equals(OUT)) continue;
                try {
                    PackInfo info = null;
                    if (Files.isDirectory(p) && Files.isDirectory(p.resolve("assets"))) {
                        info = new PackInfo(fn, p, false);
                    } else if (fn.toLowerCase(Locale.ROOT).endsWith(".zip")) {
                        info = new PackInfo(fn.substring(0, fn.length() - 4), p, true);
                    }
                    if (info != null) {
                        info.load();
                        if (!info.textures.isEmpty()) {
                            packs.add(info);
                            byName.put(info.name, info);
                        }
                    }
                } catch (Exception ignored) {
                }
            }
        }
        for (int i = 0; i < Recolor.NAMES.length; i++) {
            try {
                PackInfo v = new PackInfo("Prime - " + Recolor.NAMES[i], i);
                v.load();
                if (!v.textures.isEmpty()) {
                    packs.add(v);
                    byName.put(v.name, v);
                }
            } catch (Exception ignored) {
            }
        }
        for (Category c : Categories.ALL) {
            Map<String, List<String>> m = new HashMap<>();
            for (PackInfo p : packs) {
                List<String> found = c.match(p.textures);
                if (!found.isEmpty()) m.put(p.name, found);
            }
            index.put(c.id(), m);
        }
    }

    public PackInfo pack(String name) {
        return byName.get(name);
    }

    public int count(Category c) {
        Map<String, List<String>> m = index.get(c.id());
        return m == null ? 0 : m.size();
    }

    public List<PackInfo> packsFor(Category c) {
        List<PackInfo> r = new ArrayList<>();
        Map<String, List<String>> m = index.get(c.id());
        if (m != null) {
            for (String n : m.keySet()) {
                PackInfo p = byName.get(n);
                if (p != null) r.add(p);
            }
        }
        return r;
    }

    public List<String> matches(Category c, PackInfo p) {
        Map<String, List<String>> m = index.get(c.id());
        if (m == null) return List.of();
        return m.getOrDefault(p.name, List.of());
    }
}
