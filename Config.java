package primevisuals;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public class Config {
    public Map<String, String> selected = new HashMap<>();
    public Set<String> favorites = new HashSet<>();
    public Map<String, Map<String, String>> presets = new LinkedHashMap<>();
    public int theme = 0;
    public int accent = 0;
    public int opacity = 95;
    public int grid = 1;
    public boolean bg = false;
    public String aura = "off";
    public String hit = "off";
    public String kill = "off";
    public boolean auraFirstPerson = false;
    public int density = 1;
    public String ring = "blue";
    public boolean ringSelf = false;
    public boolean ringTarget = true;
    public String ringStyle = "both";
    public String killSoundId = "coin";
    public int killVolume = 100;
    public String hitSoundId = "tiktok";
    public int hitVolume = 100;
    public String bolt = "white";
    public boolean boltSound = false;
    public int cfgVersion = 0;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    public static final Config INSTANCE = load();

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("primevisuals.json");
    }

    private static Config load() {
        try {
            Path f = file();
            if (Files.exists(f)) {
                Config c = GSON.fromJson(Files.readString(f), Config.class);
                if (c != null) {
                    if (c.selected == null) c.selected = new HashMap<>();
                    if (c.favorites == null) c.favorites = new HashSet<>();
                    if (c.presets == null) c.presets = new LinkedHashMap<>();
                    if (c.cfgVersion < 6) {
                        // new defaults for rings and lightning (old saved settings had them switched off)
                        c.ringTarget = true;
                        c.ringStyle = "both";
                        c.boltSound = false; // lightning is silent by default
                        c.cfgVersion = 6;
                    }
                    return c;
                }
            }
        } catch (Exception ignored) {
        }
        return new Config();
    }

    public void save() {
        try {
            Files.writeString(file(), GSON.toJson(this));
        } catch (IOException ignored) {
        }
    }
}
