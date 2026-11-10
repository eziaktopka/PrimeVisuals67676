package primevisuals;

import net.minecraft.client.MinecraftClient;
import net.minecraft.resource.InputSupplier;
import net.minecraft.resource.ResourcePack;
import net.minecraft.resource.ResourceType;
import net.minecraft.util.Identifier;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class PackInfo {
    static final String ROOT = "assets/minecraft/textures/";

    public final String name;
    public final Path path;
    public final boolean zip;
    public final long modified;
    /** -1 = real pack from the resourcepacks folder, otherwise index into Recolor.NAMES (built-in variant). */
    public final int variant;
    public final Set<String> textures = new HashSet<>();
    private final Map<String, byte[]> cache = new ConcurrentHashMap<>();

    public PackInfo(String name, Path path, boolean zip) {
        this.name = name;
        this.path = path;
        this.zip = zip;
        this.variant = -1;
        long m = 0;
        try {
            m = Files.getLastModifiedTime(path).toMillis();
        } catch (IOException ignored) {
        }
        this.modified = m;
    }

    /** Built-in colour variant generated from vanilla textures. */
    public PackInfo(String name, int variant) {
        this.name = name;
        this.path = null;
        this.zip = false;
        this.modified = 0;
        this.variant = variant;
    }

    public boolean isBuiltIn() {
        return variant >= 0;
    }

    public void load() throws IOException {
        if (isBuiltIn()) {
            for (Category c : Categories.ALL) {
                for (String p : c.paths()) {
                    if (p.endsWith("*")) continue;
                    if (vanilla(p + ".png") != null) textures.add(p + ".png");
                }
            }
        } else if (zip) {
            try (ZipFile z = new ZipFile(path.toFile())) {
                Enumeration<? extends ZipEntry> en = z.entries();
                while (en.hasMoreElements()) {
                    ZipEntry e = en.nextElement();
                    String n = e.getName().replace('\\', '/');
                    if (!e.isDirectory() && n.startsWith(ROOT) && n.endsWith(".png")) {
                        textures.add(n.substring(ROOT.length()));
                    }
                }
            }
        } else {
            Path root = path.resolve("assets/minecraft/textures");
            if (Files.isDirectory(root)) {
                try (Stream<Path> s = Files.walk(root)) {
                    s.filter(Files::isRegularFile).forEach(f -> {
                        String n = root.relativize(f).toString().replace('\\', '/');
                        if (n.endsWith(".png")) textures.add(n);
                    });
                }
            }
        }
    }

    /** Raw bytes of a vanilla texture (path relative to assets/minecraft/textures/). */
    static byte[] vanilla(String rel) {
        try {
            ResourcePack rp = MinecraftClient.getInstance().getDefaultResourcePack();
            InputSupplier<InputStream> s = rp.open(ResourceType.CLIENT_RESOURCES, Identifier.of("minecraft", "textures/" + rel));
            if (s == null) return null;
            try (InputStream in = s.get()) {
                return in.readAllBytes();
            }
        } catch (Exception e) {
            return null;
        }
    }

    /** rel is relative to assets/minecraft/textures/, e.g. "item/totem_of_undying.png" */
    public byte[] read(String rel) {
        if (isBuiltIn()) {
            byte[] hit = cache.get(rel);
            if (hit != null) return hit;
            byte[] raw = vanilla(rel);
            if (raw == null) return null;
            byte[] out = rel.endsWith(".png") ? Recolor.apply(raw, variant) : raw;
            if (out != null) cache.put(rel, out);
            return out;
        }
        try {
            if (zip) {
                try (ZipFile z = new ZipFile(path.toFile())) {
                    ZipEntry e = z.getEntry(ROOT + rel);
                    if (e == null) return null;
                    try (InputStream in = z.getInputStream(e)) {
                        return in.readAllBytes();
                    }
                }
            } else {
                Path f = path.resolve(ROOT).resolve(rel);
                return Files.isRegularFile(f) ? Files.readAllBytes(f) : null;
            }
        } catch (IOException e) {
            return null;
        }
    }
}
