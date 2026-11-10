package primevisuals;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Glowing graphics drawn straight into the world with the additive "lightning" render layer:
 * - the lightning bolt after a kill (fractal main bolt, branches, flicker, ground flash)
 * - the rings around you / the player you fight (ground circles and orbiting comets)
 */
public class Bolts {
    private static final int LIFE = 20;
    private static final float[] FLICKER = {1f, 1f, 0.45f, 1f, 0.5f, 0.95f, 0.7f, 0.9f, 0.6f, 0.85f, 0.7f};
    private static final List<Bolt> ACTIVE = new ArrayList<>();

    private record Branch(List<Vec3d> pts, float scale) {}

    private static class Bolt {
        double x, y, z;
        int rgb;
        int age;
        List<Vec3d> main;
        final List<Branch> branches = new ArrayList<>();
    }

    // ------------------------------------------------------------------ colours

    public static int colorOf(String id) {
        return switch (id) {
            case "blue" -> 0x4FA8FF;
            case "purple" -> 0xB066FF;
            case "gold" -> 0xFFC93A;
            case "red" -> 0xFF4545;
            default -> 0xFFFFFF;
        };
    }

    public static int ringRgb(String id, double time) {
        return switch (id) {
            case "white" -> 0xFFFFFF;
            case "purple" -> 0xB066FF;
            case "green" -> 0x50E070;
            case "orange" -> 0xFF9030;
            case "ice" -> 0xA0E8FF;
            case "pink" -> 0xFF70C0;
            case "rainbow" -> java.awt.Color.HSBtoRGB((float) ((time * 0.3) % 1.0), 0.85f, 1.0f) & 0xFFFFFF;
            default -> 0x3FA0FF;
        };
    }

    // ------------------------------------------------------------------ bolt generation

    private static List<Vec3d> jag(Vec3d a, Vec3d b, double amp, int iters, ThreadLocalRandom r) {
        List<Vec3d> pts = new ArrayList<>();
        pts.add(a);
        pts.add(b);
        for (int it = 0; it < iters; it++) {
            List<Vec3d> next = new ArrayList<>();
            for (int i = 0; i + 1 < pts.size(); i++) {
                Vec3d p = pts.get(i);
                Vec3d q = pts.get(i + 1);
                Vec3d m = p.add(q).multiply(0.5);
                m = m.add((r.nextDouble() - 0.5) * 2 * amp, (r.nextDouble() - 0.5) * amp * 0.3, (r.nextDouble() - 0.5) * 2 * amp);
                next.add(p);
                next.add(m);
            }
            next.add(pts.get(pts.size() - 1));
            pts = next;
            amp *= 0.55;
        }
        return pts;
    }

    public static void spawn(ClientWorld w, double x, double y, double z, String color) {
        if (color == null || "off".equals(color)) return;
        ThreadLocalRandom r = ThreadLocalRandom.current();
        Bolt b = new Bolt();
        b.x = x;
        b.y = y;
        b.z = z;
        b.rgb = colorOf(color);
        Vec3d start = new Vec3d(x + (r.nextDouble() - 0.5) * 10, y + 34 + r.nextDouble() * 6, z + (r.nextDouble() - 0.5) * 10);
        Vec3d end = new Vec3d(x, y + 0.05, z);
        b.main = jag(start, end, 5.0, 6, r);
        int size = b.main.size();
        for (int k = 0; k < 9; k++) {
            Vec3d p = b.main.get(8 + r.nextInt(size - 16));
            double dirX = (r.nextDouble() - 0.5) * 2;
            double dirZ = (r.nextDouble() - 0.5) * 2;
            double len = 3 + r.nextDouble() * 5;
            Vec3d q = new Vec3d(p.x + dirX * len, Math.max(y + 0.05, p.y - 6 - r.nextDouble() * 8), p.z + dirZ * len);
            List<Vec3d> br = jag(p, q, 2.0, 4, r);
            b.branches.add(new Branch(br, 0.5f));
            if (r.nextDouble() < 0.45) {
                Vec3d m = br.get(br.size() / 2);
                Vec3d q2 = new Vec3d(m.x + (r.nextDouble() - 0.5) * 5, Math.max(y + 0.05, m.y - 3 - r.nextDouble() * 4), m.z + (r.nextDouble() - 0.5) * 5);
                b.branches.add(new Branch(jag(m, q2, 1.0, 3, r), 0.3f));
            }
        }
        ACTIVE.add(b);
        sparks(w, b);
    }

    private static void sparks(ClientWorld w, Bolt b) {
        if (w == null) return;
        for (int i = 0; i < b.main.size(); i += 2) {
            Vec3d p = b.main.get(i);
            w.addParticle(ParticleTypes.ELECTRIC_SPARK, p.x, p.y, p.z, 0, 0, 0);
        }
    }

    public static void tick(ClientWorld w) {
        for (Iterator<Bolt> it = ACTIVE.iterator(); it.hasNext(); ) {
            Bolt b = it.next();
            b.age++;
            if (b.age == 4 || b.age == 8) sparks(w, b);
            if (b.age > LIFE) it.remove();
        }
    }

    public static void register() {
        WorldRenderEvents.AFTER_TRANSLUCENT.register(Bolts::render);
    }

    // ------------------------------------------------------------------ rendering

    private static void render(WorldRenderContext ctx) {
        MinecraftClient mc = MinecraftClient.getInstance();
        Config cfg = Config.INSTANCE;
        Entity target = Fx.ringTarget();
        boolean selfRing = cfg.ringSelf && mc.player != null;
        if (ACTIVE.isEmpty() && target == null && !selfRing) return;
        MatrixStack ms = ctx.matrixStack();
        VertexConsumerProvider vcp = ctx.consumers();
        if (ms == null || vcp == null) return;
        Vec3d cam = ctx.camera().getPos();
        ms.push();
        ms.translate(-cam.x, -cam.y, -cam.z);
        Matrix4f mat = ms.peek().getPositionMatrix();
        VertexConsumer vc = vcp.getBuffer(RenderLayer.getLightning());
        double time = System.nanoTime() / 1_000_000_000.0;

        for (Bolt b : ACTIVE) drawBolt(vc, mat, cam, b);

        boolean ground = !"orbit".equals(cfg.ringStyle);
        boolean orbit = !"ground".equals(cfg.ringStyle);
        if (selfRing) {
            boolean orbitSelf = orbit && (cfg.auraFirstPerson || !mc.options.getPerspective().isFirstPerson());
            drawRings(vc, mat, cam, mc.player, cfg.ring, ground, orbitSelf, time);
        }
        if (target != null && target != mc.player) {
            drawRings(vc, mat, cam, target, cfg.ring, ground, orbit, time);
        }
        ms.pop();
        if (vcp instanceof VertexConsumerProvider.Immediate imm) {
            imm.draw(RenderLayer.getLightning());
        }
    }

    private static void drawBolt(VertexConsumer vc, Matrix4f mat, Vec3d cam, Bolt b) {
        float f = b.age < FLICKER.length ? FLICKER[b.age] : 0.6f;
        if (b.age >= 13) f *= Math.max(0f, (LIFE - b.age) / 7.0f);
        if (f <= 0f) return;
        // ground flash
        if (b.age <= 3) {
            double rad = 3.4 - b.age * 0.6;
            Vec3d c = new Vec3d(b.x, b.y + 0.06, b.z);
            for (int i = 0; i < 18; i++) {
                double a1 = i * (Math.PI * 2 / 18);
                double a2 = (i + 1) * (Math.PI * 2 / 18);
                Vec3d e1 = new Vec3d(b.x + Math.cos(a1) * rad, b.y + 0.06, b.z + Math.sin(a1) * rad);
                Vec3d e2 = new Vec3d(b.x + Math.cos(a2) * rad, b.y + 0.06, b.z + Math.sin(a2) * rad);
                vert(vc, mat, c, b.rgb, 0.75f * f);
                vert(vc, mat, c, b.rgb, 0.75f * f);
                vert(vc, mat, e2, b.rgb, 0f);
                vert(vc, mat, e1, b.rgb, 0f);
            }
        }
        for (int i = 0; i + 1 < b.main.size(); i++) {
            Vec3d p = b.main.get(i);
            Vec3d q = b.main.get(i + 1);
            double wp = 0.03 + 0.0025 * p.distanceTo(cam);
            double wq = 0.03 + 0.0025 * q.distanceTo(cam);
            ribbon(vc, mat, cam, p, q, wp, wq, b.rgb, f, f, false);
        }
        for (Branch br : b.branches) {
            int n = br.pts().size();
            for (int i = 0; i + 1 < n; i++) {
                Vec3d p = br.pts().get(i);
                Vec3d q = br.pts().get(i + 1);
                float taper = 1f - (i / (float) n) * 0.6f;
                double wp = (0.03 + 0.0025 * p.distanceTo(cam)) * br.scale() * taper;
                double wq = (0.03 + 0.0025 * q.distanceTo(cam)) * br.scale() * taper;
                ribbon(vc, mat, cam, p, q, wp, wq, b.rgb, f * 0.7f, f * 0.55f, false);
            }
        }
    }

    private static void drawRings(VertexConsumer vc, Matrix4f mat, Vec3d cam, Entity e, String colorId,
                                  boolean ground, boolean orbit, double time) {
        int rgb = ringRgb(colorId, time);
        double cx = e.getX();
        double cy = e.getY();
        double cz = e.getZ();
        if (ground) {
            circle(vc, mat, cam, cx, cy + 0.04, cz, 0.95 + 0.05 * Math.sin(time * 3), 0.05, rgb, 0.95f, time * 0.5, 48);
            circle(vc, mat, cam, cx, cy + 0.04, cz, 1.3 + 0.04 * Math.sin(time * 2.3), 0.03, rgb, 0.6f, -time * 0.4, 56);
            double ph = (time * 0.9) % 1.0;
            circle(vc, mat, cam, cx, cy + 0.045, cz, 0.5 + ph * 1.3, 0.03, rgb, (float) ((1 - ph) * 0.8), 0, 48);
        }
        if (orbit) {
            double mid = cy + e.getHeight() * 0.45;
            double radius = 0.85;
            for (int o = 0; o < 2; o++) {
                double tilt = o == 0 ? 0.5 : -0.5;
                double dir = o == 0 ? 1 : -1;
                double head = time * 5.5 * dir + o * Math.PI;
                // faint full orbit
                int segs = 40;
                for (int i = 0; i < segs; i++) {
                    Vec3d p = orbitPoint(cx, mid, cz, radius, i * (Math.PI * 2 / segs), tilt);
                    Vec3d q = orbitPoint(cx, mid, cz, radius, (i + 1) * (Math.PI * 2 / segs), tilt);
                    ribbon(vc, mat, cam, p, q, 0.012, 0.012, rgb, 0.18f, 0.18f, false);
                }
                // bright comet behind the head
                int n = 26;
                for (int i = 0; i < n; i++) {
                    double a1 = head - dir * (i * 0.07);
                    double a2 = head - dir * ((i + 1) * 0.07);
                    float al1 = (float) Math.pow(1 - i / (double) n, 1.5);
                    float al2 = (float) Math.pow(1 - (i + 1) / (double) n, 1.5);
                    Vec3d p = orbitPoint(cx, mid, cz, radius, a1, tilt);
                    Vec3d q = orbitPoint(cx, mid, cz, radius, a2, tilt);
                    ribbon(vc, mat, cam, p, q, 0.02 + 0.06 * al1, 0.02 + 0.06 * al2, rgb, al1, al2, false);
                }
            }
        }
    }

    private static Vec3d orbitPoint(double cx, double cy, double cz, double radius, double a, double tilt) {
        double x = Math.cos(a) * radius;
        double z = Math.sin(a) * radius;
        return new Vec3d(cx + x, cy + z * Math.sin(tilt), cz + z * Math.cos(tilt));
    }

    private static void circle(VertexConsumer vc, Matrix4f mat, Vec3d cam, double cx, double cy, double cz,
                               double radius, double halfWidth, int rgb, float alpha, double rot, int segs) {
        for (int i = 0; i < segs; i++) {
            double a1 = rot + i * (Math.PI * 2 / segs);
            double a2 = rot + (i + 1) * (Math.PI * 2 / segs);
            Vec3d p = new Vec3d(cx + Math.cos(a1) * radius, cy, cz + Math.sin(a1) * radius);
            Vec3d q = new Vec3d(cx + Math.cos(a2) * radius, cy, cz + Math.sin(a2) * radius);
            ribbon(vc, mat, cam, p, q, halfWidth, halfWidth, rgb, alpha, alpha, true);
        }
    }

    /** A glowing beam between a and b: three additive layers (wide glow, soft middle, white-hot core). */
    private static void ribbon(VertexConsumer vc, Matrix4f mat, Vec3d cam, Vec3d a, Vec3d b,
                               double wa, double wb, int rgb, float aa, float ab, boolean flat) {
        Vec3d d = b.subtract(a);
        if (d.lengthSquared() < 1.0e-9) return;
        Vec3d dn = d.normalize();
        Vec3d side;
        if (flat) {
            side = new Vec3d(-dn.z, 0, dn.x);
        } else {
            Vec3d view = a.add(b).multiply(0.5).subtract(cam);
            side = dn.crossProduct(view);
        }
        double sl = side.length();
        if (sl < 1.0e-6) return;
        side = side.multiply(1.0 / sl);
        double[] ws = {5.0, 2.2, 1.0};
        float[] als = {0.10f, 0.28f, 1.0f};
        for (int k = 0; k < 3; k++) {
            double wA = wa * ws[k];
            double wB = wb * ws[k];
            int c = k == 2 ? mixWhite(rgb, 0.65f) : rgb;
            Vec3d sa = side.multiply(wA);
            Vec3d sb = side.multiply(wB);
            vert(vc, mat, a.subtract(sa), c, aa * als[k]);
            vert(vc, mat, a.add(sa), c, aa * als[k]);
            vert(vc, mat, b.add(sb), c, ab * als[k]);
            vert(vc, mat, b.subtract(sb), c, ab * als[k]);
        }
    }

    private static int mixWhite(int rgb, float t) {
        int r = (rgb >> 16) & 255;
        int g = (rgb >> 8) & 255;
        int b = rgb & 255;
        r += (int) ((255 - r) * t);
        g += (int) ((255 - g) * t);
        b += (int) ((255 - b) * t);
        return (r << 16) | (g << 8) | b;
    }

    private static void vert(VertexConsumer vc, Matrix4f mat, Vec3d p, int rgb, float alpha) {
        int a = (int) (255 * Math.max(0f, Math.min(1f, alpha)));
        vc.vertex(mat, (float) p.x, (float) p.y, (float) p.z).color((rgb >> 16) & 255, (rgb >> 8) & 255, rgb & 255, a);
    }
}
