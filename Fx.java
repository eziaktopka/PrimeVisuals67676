package primevisuals;

import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

import java.util.concurrent.ThreadLocalRandom;

/** Client-side cosmetics: auras around you, effects when you hit and when you kill. Particles only, nothing is sent to the server. */
public class Fx {
    public static final String[][] AURAS = {
            {"off", "Off"}, {"christmas", "Christmas"}, {"halloween", "Halloween"}, {"colorful", "Colorful"},
            {"fire", "Fire"}, {"ice", "Ice"}, {"galaxy", "Galaxy"}, {"love", "Love"}, {"sakura", "Sakura"}
    };
    public static final String[][] HITS = {
            {"off", "Off"}, {"crit", "Crit"}, {"blood", "Blood"}, {"hearts", "Hearts"}, {"sparks", "Sparks"},
            {"fire", "Fire"}, {"magic", "Magic"}, {"notes", "Notes"}, {"snow", "Snow"}, {"ring", "Ring"}
    };
    public static final String[][] KILLS = {
            {"off", "Off"}, {"totem", "Totem"}, {"explosion", "Explosion"}, {"soul", "Souls"},
            {"lightning", "Lightning"}, {"fireworks", "Fireworks"}, {"hearts", "Hearts"},
            {"sakura", "Sakura"}, {"halloween", "Halloween"}, {"rainbow", "Rainbow"}, {"shockwave", "Shockwave"}
    };
    public static final String[][] BOLTS = {
            {"off", "Off"}, {"white", "White"}, {"blue", "Blue"}, {"purple", "Purple"}, {"gold", "Gold"}, {"red", "Red"}
    };
    public static final String[][] RINGS = {
            {"blue", "Blue"}, {"white", "White"}, {"purple", "Purple"}, {"green", "Green"},
            {"orange", "Orange"}, {"ice", "Ice"}, {"pink", "Pink"}, {"rainbow", "Rainbow"}
    };
    public static final String[][] RING_STYLES = {
            {"ground", "Ground circle"}, {"orbit", "Orbiting"}, {"both", "Both"}
    };

    public static final String[][] SOUNDS = {
            {"off", "Off"}, {"tiktok", "TikTok"}, {"coin", "Coin"}, {"oneup", "1-Up"}, {"stomp", "Stomp"}
    };
    public static final String[][] HIT_SOUNDS = {
            {"off", "Off"}, {"tiktok", "TikTok"}, {"blip", "Blip"}, {"bump", "Bump"}
    };
    private static final SoundEvent SND_HIT_TIKTOK = SoundEvent.of(Identifier.of("primevisuals", "hit_tiktok"));
    private static final SoundEvent SND_HIT_BLIP = SoundEvent.of(Identifier.of("primevisuals", "hit_blip"));
    private static final SoundEvent SND_HIT_BUMP = SoundEvent.of(Identifier.of("primevisuals", "hit_bump"));
    private static LivingEntity pendingHit;
    private static int pendingTick;
    private static int pendingHurt;
    private static final SoundEvent SND_BOLT = SoundEvent.of(Identifier.of("primevisuals", "bolt"));
    private static final SoundEvent SND_TIKTOK = SoundEvent.of(Identifier.of("primevisuals", "kill_tiktok"));
    private static final SoundEvent SND_COIN = SoundEvent.of(Identifier.of("primevisuals", "kill_coin"));
    private static final SoundEvent SND_ONEUP = SoundEvent.of(Identifier.of("primevisuals", "kill_1up"));
    private static final SoundEvent SND_STOMP = SoundEvent.of(Identifier.of("primevisuals", "kill_stomp"));

    private static int ticks;
    private static Entity tracked;
    private static int trackedTick;
    private static final java.util.List<Wave> WAVES = new java.util.ArrayList<>();

    private static class Wave {
        final double x, y, z, r0, speed;
        final String color;
        final int max;
        int age;

        Wave(double x, double y, double z, String color, double r0, double speed, int max) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.color = color;
            this.r0 = r0;
            this.speed = speed;
            this.max = max;
        }
    }

    // ---------------------------------------------------------------- events

    public static void tick(MinecraftClient mc) {
        ClientWorld w = mc.world;
        ClientPlayerEntity p = mc.player;
        if (w == null || p == null) {
            tracked = null;
            WAVES.clear();
            return;
        }
        ticks++;
        Bolts.tick(w);
        Config cfg = Config.INSTANCE;
        if (!"off".equals(cfg.aura) && (cfg.auraFirstPerson || !mc.options.getPerspective().isFirstPerson())) {
            int n = Math.max(1, Math.min(3, cfg.density));
            for (int d = 0; d < n; d++) aura(w, p, cfg.aura, d);
        }
        if (pendingHit != null) {
            if (pendingHit.hurtTime > 0 && pendingHit.hurtTime > pendingHurt) {
                playHit(cfg.hitSoundId);
                pendingHit = null;
            } else if (ticks - pendingTick > 10) {
                pendingHit = null;
            }
        }
        boolean groundStyle = !"orbit".equals(cfg.ringStyle);
        boolean orbitStyle = !"ground".equals(cfg.ringStyle);
        if (cfg.ringSelf) {
            if (groundStyle && ticks % 2 == 0) {
                groundRing(w, cfg.ring, p.getX(), p.getY() + 0.05, p.getZ(), 0.9, 20, ticks * 0.05);
                groundRing(w, cfg.ring, p.getX(), p.getY() + 0.05, p.getZ(), 1.25, 28, -ticks * 0.04);
            }
            if (orbitStyle && (cfg.auraFirstPerson || !mc.options.getPerspective().isFirstPerson())) {
                orbit(w, cfg.ring, p);
            }
        }
        if (tracked != null) {
            if (ticks - trackedTick > 160) {
                tracked = null;
            } else if (tracked instanceof LivingEntity le && le.getHealth() <= 0f) {
                kill(w, le.getX(), le.getY(), le.getZ(), cfg.kill);
                tracked = null;
            } else if (cfg.ringTarget) {
                if (groundStyle && ticks % 2 == 0) {
                    groundRing(w, cfg.ring, tracked.getX(), tracked.getY() + 0.05, tracked.getZ(), 0.9, 20, ticks * 0.05);
                    groundRing(w, cfg.ring, tracked.getX(), tracked.getY() + 0.05, tracked.getZ(), 1.25, 28, -ticks * 0.04);
                }
                if (orbitStyle) orbit(w, cfg.ring, tracked);
            }
        }
        for (java.util.Iterator<Wave> it = WAVES.iterator(); it.hasNext(); ) {
            Wave v = it.next();
            double r = v.r0 + v.age * v.speed;
            groundRing(w, v.color, v.x, v.y, v.z, r, Math.max(16, (int) (r * 12)), v.age * 0.1);
            if (++v.age > v.max) it.remove();
        }
    }

    /** The entity that should currently get rings (the one you are fighting), or null. */
    public static Entity ringTarget() {
        if (!Config.INSTANCE.ringTarget || tracked == null) return null;
        if (tracked instanceof LivingEntity le && le.getHealth() <= 0f) return null;
        return tracked;
    }

    public static void onAttack(PlayerEntity player, Entity target) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != player || mc.world == null) return;
        tracked = target;
        trackedTick = ticks;
        if (target instanceof LivingEntity le) {
            pendingHit = le;
            pendingTick = ticks;
            pendingHurt = le.hurtTime;
        }
        hit(mc.world, target.getX(), target.getBodyY(0.6), target.getZ(), Config.INSTANCE.hit);
    }

    public static void testHit() {
        MinecraftClient mc = MinecraftClient.getInstance();
        Vec3d f = front(mc);
        if (f != null && mc.world != null) hit(mc.world, f.x, mc.player.getY() + 1.1, f.z, Config.INSTANCE.hit);
    }

    public static void testKill() {
        MinecraftClient mc = MinecraftClient.getInstance();
        Vec3d f = front(mc);
        if (f != null && mc.world != null) kill(mc.world, f.x, mc.player.getY(), f.z, Config.INSTANCE.kill);
    }

    private static Vec3d front(MinecraftClient mc) {
        ClientPlayerEntity p = mc.player;
        if (p == null) return null;
        Vec3d v = p.getRotationVec(1.0f);
        return new Vec3d(p.getX() + v.x * 2.5, p.getEyeY() - 0.4 + v.y * 2.5, p.getZ() + v.z * 2.5);
    }

    // ---------------------------------------------------------------- helpers

    /** Two glowing orbs with a short comet tail circling the body on two crossing, tilted orbits (like the rings in the video). */
    private static void orbit(ClientWorld w, String color, Entity e) {
        boolean rainbow = "rainbow".equals(color);
        ParticleEffect fx = ringFx(color);
        double cx = e.getX();
        double cy = e.getY() + e.getHeight() * 0.5;
        double cz = e.getZ();
        double radius = 0.85;
        for (int o = 0; o < 2; o++) {
            double tilt = o == 0 ? 0.5 : -0.5;
            for (int k = 0; k < 3; k++) {
                double a = ticks * 0.28 * (o == 0 ? 1 : -1) + o * Math.PI - k * 0.14;
                double x = Math.cos(a) * radius;
                double z = Math.sin(a) * radius;
                double yy = z * Math.sin(tilt);
                double zz = z * Math.cos(tilt);
                if (rainbow) {
                    w.addParticle(ParticleTypes.NOTE, cx + x, cy + yy, cz + zz, (ticks * 0.02 + o * 0.5 + k * 0.03) % 1.0, 0, 0);
                } else {
                    w.addParticle(fx, cx + x, cy + yy, cz + zz, 0, 0, 0);
                }
            }
        }
    }

    private static ParticleEffect ringFx(String color) {
        return switch (color) {
            case "white" -> ParticleTypes.END_ROD;
            case "purple" -> ParticleTypes.WITCH;
            case "green" -> ParticleTypes.HAPPY_VILLAGER;
            case "orange" -> ParticleTypes.FLAME;
            case "ice" -> ParticleTypes.SNOWFLAKE;
            case "pink" -> ParticleTypes.CHERRY_LEAVES;
            default -> ParticleTypes.SOUL_FIRE_FLAME;
        };
    }

    private static void groundRing(ClientWorld w, String color, double x, double y, double z, double r, int points, double rot) {
        boolean rainbow = "rainbow".equals(color);
        ParticleEffect fx = ringFx(color);
        for (int i = 0; i < points; i++) {
            double a = rot + i * (Math.PI * 2 / points);
            double px = x + Math.cos(a) * r;
            double pz = z + Math.sin(a) * r;
            if (rainbow) w.addParticle(ParticleTypes.NOTE, px, y, pz, (i / (double) points + ticks * 0.01) % 1.0, 0, 0);
            else w.addParticle(fx, px, y, pz, 0, 0, 0);
        }
    }

    private static void ring(ClientWorld w, Entity p, ParticleEffect fx, int count, double radius, double height,
                             double speed, double vy, int phase) {
        for (int i = 0; i < count; i++) {
            double a = ticks * speed + phase * 0.7 + i * (Math.PI * 2 / count);
            double y = p.getY() + ((ticks * 0.03 + phase * 0.31 + i / (double) count) % 1.0) * height;
            w.addParticle(fx, p.getX() + Math.cos(a) * radius, y, p.getZ() + Math.sin(a) * radius, 0, vy, 0);
        }
    }

    private static void cloud(ClientWorld w, Entity p, ParticleEffect fx, int count, double vy, double height) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (int i = 0; i < count; i++) {
            w.addParticle(fx,
                    p.getX() + (r.nextDouble() - 0.5) * 1.6,
                    p.getY() + r.nextDouble() * height,
                    p.getZ() + (r.nextDouble() - 0.5) * 1.6,
                    0, vy, 0);
        }
    }

    private static void burst(ClientWorld w, ParticleEffect fx, double x, double y, double z, int n, double speed, double up) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (int i = 0; i < n; i++) {
            w.addParticle(fx, x, y, z,
                    (r.nextDouble() - 0.5) * 2 * speed,
                    r.nextDouble() * speed + up,
                    (r.nextDouble() - 0.5) * 2 * speed);
        }
    }

    private static void column(ClientWorld w, ParticleEffect fx, double x, double y, double z, int n, double height, double vy) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (int i = 0; i < n; i++) {
            w.addParticle(fx, x + (r.nextDouble() - 0.5) * 0.8, y + r.nextDouble() * height,
                    z + (r.nextDouble() - 0.5) * 0.8, 0, vy, 0);
        }
    }

    // ---------------------------------------------------------------- aura

    private static void aura(ClientWorld w, Entity p, String id, int d) {
        switch (id) {
            case "christmas" -> {
                cloud(w, p, ParticleTypes.SNOWFLAKE, 2, -0.03, 2.4);
                ring(w, p, ParticleTypes.END_ROD, 2, 0.75, 2.0, 0.15, 0.0, d);
                ring(w, p, ParticleTypes.HAPPY_VILLAGER, 1, 0.8, 2.0, -0.12, 0.0, d);
                ring(w, p, ParticleTypes.CRIMSON_SPORE, 1, 0.8, 2.0, 0.1, 0.0, d + 1);
            }
            case "halloween" -> {
                ring(w, p, ParticleTypes.FLAME, 2, 0.7, 2.0, 0.14, 0.0, d);
                ring(w, p, ParticleTypes.WITCH, 1, 0.8, 2.0, -0.1, 0.0, d);
                ring(w, p, ParticleTypes.SOUL, 1, 0.6, 2.0, 0.08, 0.02, d);
                cloud(w, p, ParticleTypes.SMALL_FLAME, 1, 0.0, 2.0);
            }
            case "colorful" -> {
                for (int i = 0; i < 3; i++) {
                    double hue = (ticks * 0.02 + i / 3.0 + d * 0.13) % 1.0;
                    double a = ticks * 0.18 + i * 2.0944 + d;
                    double y = p.getY() + ((ticks * 0.03 + i / 3.0 + d * 0.3) % 1.0) * 2.0;
                    w.addParticle(ParticleTypes.NOTE, p.getX() + Math.cos(a) * 0.8, y, p.getZ() + Math.sin(a) * 0.8, hue, 0, 0);
                }
                ring(w, p, ParticleTypes.END_ROD, 1, 0.6, 2.0, -0.2, 0.0, d);
            }
            case "fire" -> {
                ring(w, p, ParticleTypes.FLAME, 3, 0.7, 2.0, 0.16, 0.0, d);
                cloud(w, p, ParticleTypes.SMALL_FLAME, 1, 0.01, 2.0);
                if (ticks % 10 == 0) cloud(w, p, ParticleTypes.LAVA, 1, 0.0, 1.5);
            }
            case "ice" -> {
                cloud(w, p, ParticleTypes.SNOWFLAKE, 2, -0.01, 2.2);
                ring(w, p, ParticleTypes.END_ROD, 2, 0.75, 2.0, 0.12, 0.0, d);
                ring(w, p, ParticleTypes.ITEM_SNOWBALL, 1, 0.8, 2.0, -0.1, 0.0, d);
            }
            case "galaxy" -> {
                ring(w, p, ParticleTypes.PORTAL, 3, 0.8, 2.0, 0.13, 0.0, d);
                ring(w, p, ParticleTypes.END_ROD, 1, 0.7, 2.0, -0.15, 0.0, d);
                cloud(w, p, ParticleTypes.ENCHANT, 1, 0.0, 2.0);
            }
            case "love" -> {
                if (ticks % 3 == 0) cloud(w, p, ParticleTypes.HEART, 1, 0.04, 1.6);
                ring(w, p, ParticleTypes.HAPPY_VILLAGER, 1, 0.8, 2.0, 0.1, 0.0, d);
            }
            case "sakura" -> {
                cloud(w, p, ParticleTypes.CHERRY_LEAVES, 2, -0.02, 2.4);
                ring(w, p, ParticleTypes.CHERRY_LEAVES, 1, 0.8, 2.0, 0.1, 0.0, d);
            }
            default -> {
            }
        }
    }

    // ---------------------------------------------------------------- hit

    private static void hit(ClientWorld w, double x, double y, double z, String id) {
        switch (id) {
            case "crit" -> burst(w, ParticleTypes.CRIT, x, y, z, 18, 0.35, 0.1);
            case "blood" -> burst(w, new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.REDSTONE_BLOCK.getDefaultState()),
                    x, y, z, 24, 0.25, 0.12);
            case "hearts" -> burst(w, ParticleTypes.HEART, x, y, z, 6, 0.1, 0.08);
            case "sparks" -> burst(w, ParticleTypes.ELECTRIC_SPARK, x, y, z, 25, 0.35, 0.0);
            case "fire" -> burst(w, ParticleTypes.FLAME, x, y, z, 14, 0.12, 0.08);
            case "magic" -> burst(w, ParticleTypes.ENCHANTED_HIT, x, y, z, 24, 0.4, 0.1);
            case "notes" -> {
                for (int i = 0; i < 12; i++) {
                    double a = i * (Math.PI * 2 / 12);
                    w.addParticle(ParticleTypes.NOTE, x + Math.cos(a) * 0.4, y, z + Math.sin(a) * 0.4, i / 12.0, 0, 0);
                }
            }
            case "snow" -> burst(w, ParticleTypes.SNOWFLAKE, x, y, z, 20, 0.2, 0.1);
            case "ring" -> WAVES.add(new Wave(x, y - 1.0, z, Config.INSTANCE.ring, 0.3, 0.12, 6));
            default -> {
            }
        }
    }

    // ---------------------------------------------------------------- kill

    private static void playKillSound() {
        play(Config.INSTANCE.killSoundId);
    }

    /** Plays a kill sound (used by the Kill Sound category to preview it). */
    public static void previewSound(String id) {
        play(id);
    }

    private static void play(String idIn) {
        String id = idIn == null ? "coin" : idIn;
        if ("off".equals(id)) return;
        SoundEvent snd = switch (id) {
            case "tiktok" -> SND_TIKTOK;
            case "oneup" -> SND_ONEUP;
            case "stomp" -> SND_STOMP;
            default -> SND_COIN;
        };
        float vol = Math.max(0f, Math.min(1f, Config.INSTANCE.killVolume / 100f));
        MinecraftClient.getInstance().getSoundManager().play(PositionedSoundInstance.master(snd, 1.0f, vol));
    }

    /** Plays a hit sound (used by the Hit Sound category to preview it). */
    public static void previewHitSound(String id) {
        playHit(id);
    }

    private static void playHit(String idIn) {
        String id = idIn == null ? "tiktok" : idIn;
        if ("off".equals(id)) return;
        SoundEvent snd = switch (id) {
            case "blip" -> SND_HIT_BLIP;
            case "bump" -> SND_HIT_BUMP;
            default -> SND_HIT_TIKTOK;
        };
        // the original varies the pitch a little on every hit (about +-4%)
        float pitch = "tiktok".equals(id) ? 0.96f + (float) (ThreadLocalRandom.current().nextDouble() * 0.08) : 1.0f;
        float vol = Math.max(0f, Math.min(1f, Config.INSTANCE.hitVolume / 100f));
        MinecraftClient.getInstance().getSoundManager().play(PositionedSoundInstance.master(snd, pitch, vol));
    }

    private static void kill(ClientWorld w, double x, double y, double z, String id) {
        playKillSound();
        double cy = y + 1.0;
        if (!"off".equals(Config.INSTANCE.bolt)) {
            Bolts.spawn(w, x, y, z, Config.INSTANCE.bolt);
            String wc = switch (Config.INSTANCE.bolt) {
                case "blue" -> "blue";
                case "purple" -> "purple";
                case "gold", "red" -> "orange";
                default -> "white";
            };
            WAVES.add(new Wave(x, y + 0.05, z, wc, 0.4, 0.28, 10));
            w.addParticle(ParticleTypes.FLASH, x, y + 1.0, z, 0, 0, 0);
            burst(w, ParticleTypes.ELECTRIC_SPARK, x, y + 0.2, z, 30, 0.35, 0.05);
            burst(w, ParticleTypes.END_ROD, x, cy, z, 12, 0.12, 0.03);
            if (Config.INSTANCE.boltSound) {
                float v = Math.max(0f, Math.min(1f, Config.INSTANCE.killVolume / 100f)) * 0.8f;
                MinecraftClient.getInstance().getSoundManager().play(PositionedSoundInstance.master(SND_BOLT, 1.0f, v));
            }
        }
        switch (id) {
            case "totem" -> burst(w, ParticleTypes.TOTEM_OF_UNDYING, x, cy, z, 45, 0.5, 0.3);
            case "explosion" -> {
                w.addParticle(ParticleTypes.EXPLOSION_EMITTER, x, cy, z, 0, 0, 0);
                burst(w, ParticleTypes.FIREWORK, x, cy, z, 30, 0.3, 0.1);
            }
            case "soul" -> {
                column(w, ParticleTypes.SOUL_FIRE_FLAME, x, y, z, 30, 2.5, 0.08);
                column(w, ParticleTypes.SOUL, x, y, z, 15, 2.0, 0.06);
            }
            case "lightning" -> {
                column(w, ParticleTypes.ELECTRIC_SPARK, x, y, z, 50, 4.0, 0.0);
                w.addParticle(ParticleTypes.FLASH, x, cy, z, 0, 0, 0);
                burst(w, ParticleTypes.CRIT, x, cy, z, 20, 0.4, 0.1);
            }
            case "fireworks" -> {
                burst(w, ParticleTypes.FIREWORK, x, cy, z, 60, 0.5, 0.1);
                w.addParticle(ParticleTypes.FLASH, x, cy, z, 0, 0, 0);
            }
            case "hearts" -> {
                burst(w, ParticleTypes.HEART, x, cy, z, 18, 0.2, 0.15);
                burst(w, ParticleTypes.HAPPY_VILLAGER, x, cy, z, 12, 0.3, 0.1);
            }
            case "sakura" -> burst(w, ParticleTypes.CHERRY_LEAVES, x, cy, z, 50, 0.3, 0.15);
            case "shockwave" -> {
                String col = Config.INSTANCE.ring;
                WAVES.add(new Wave(x, y + 0.05, z, col, 0.5, 0.22, 14));
                WAVES.add(new Wave(x, y + 0.05, z, col, 0.2, 0.16, 12));
                column(w, ParticleTypes.ELECTRIC_SPARK, x, y, z, 40, 5.0, 0.0);
                burst(w, ParticleTypes.END_ROD, x, y + 2.2, z, 25, 0.15, 0.02);
                burst(w, ParticleTypes.SNOWFLAKE, x, y + 2.2, z, 15, 0.1, 0.02);
            }
            case "halloween" -> {
                burst(w, ParticleTypes.WITCH, x, cy, z, 35, 0.3, 0.1);
                burst(w, ParticleTypes.FLAME, x, cy, z, 20, 0.2, 0.1);
                burst(w, ParticleTypes.SOUL, x, cy, z, 15, 0.1, 0.08);
            }
            case "rainbow" -> {
                for (int i = 0; i < 48; i++) {
                    double a = i * 0.5;
                    double r = 0.3 + i * 0.012;
                    w.addParticle(ParticleTypes.NOTE, x + Math.cos(a) * r, y + i * 0.05, z + Math.sin(a) * r, i / 48.0, 0, 0);
                }
            }
            default -> {
            }
        }
    }
}
