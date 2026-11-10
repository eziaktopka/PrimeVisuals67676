package primevisuals;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.resource.ResourcePackManager;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

/** Builds resourcepacks/PrimeVisuals_Output from the chosen textures and reloads resources. */
public class Applier {

    public static void apply(Config cfg) {
        Library lib = Library.current;
        Path out = FabricLoader.getInstance().getGameDir().resolve("resourcepacks").resolve(Library.OUT);
        try {
            deleteDir(out);
            Files.createDirectories(out);
            Files.writeString(out.resolve("pack.mcmeta"),
                    "{\"pack\":{\"pack_format\":46,\"description\":\"Prime Visuals output\"}}");
            if (lib != null) {
                Path texRoot = out.resolve("assets/minecraft/textures");
                for (Category c : Categories.ALL) {
                    String sel = cfg.selected.get(c.id());
                    if (sel == null || sel.isEmpty()) continue;
                    PackInfo p = lib.pack(sel);
                    if (p == null) continue;
                    for (String rel : lib.matches(c, p)) {
                        byte[] b = p.read(rel);
                        if (b == null) continue;
                        Path dst = texRoot.resolve(rel);
                        Files.createDirectories(dst.getParent());
                        Files.write(dst, b);
                        byte[] meta = p.read(rel + ".mcmeta");
                        if (meta != null) Files.write(Path.of(dst + ".mcmeta"), meta);
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }

        MinecraftClient mc = MinecraftClient.getInstance();
        ResourcePackManager m = mc.getResourcePackManager();
        m.scanPacks();
        String id = "file/" + Library.OUT;
        if (!m.getEnabledIds().contains(id)) {
            if (m.enable(id)) {
                mc.options.refreshResourcePacks(m);
            } else {
                mc.reloadResources();
            }
        } else {
            mc.reloadResources();
        }
    }

    private static void deleteDir(Path dir) throws IOException {
        if (!Files.exists(dir)) return;
        try (Stream<Path> s = Files.walk(dir)) {
            for (Path p : s.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(p);
            }
        }
    }
}
