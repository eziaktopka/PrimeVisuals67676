package primevisuals;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.OtherClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Training dummies. A bot only exists on YOUR client: nobody else sees it and hitting it sends nothing to the server.
 * It stands still, has 20 HP, and is removed a moment after it dies.
 */
public class Bots {
    private static final List<OtherClientPlayerEntity> BOTS = new ArrayList<>();
    private static final Map<OtherClientPlayerEntity, Integer> DEAD = new HashMap<>();
    private static int counter;

    public static boolean isBot(Entity e) {
        return e instanceof OtherClientPlayerEntity b && BOTS.contains(b);
    }

    /** Spawns a bot where you are looking (on the block you aim at, or 4 blocks in front of you). */
    public static boolean spawn(MinecraftClient mc) {
        ClientWorld w = mc.world;
        ClientPlayerEntity p = mc.player;
        if (w == null || p == null) return false;
        double x;
        double y;
        double z;
        HitResult hr = p.raycast(30.0, 1.0f, false);
        if (hr.getType() == HitResult.Type.BLOCK && hr instanceof BlockHitResult bhr) {
            BlockPos bp = bhr.getBlockPos().offset(bhr.getSide());
            x = bp.getX() + 0.5;
            y = bp.getY();
            z = bp.getZ() + 0.5;
        } else {
            Vec3d v = p.getRotationVec(1.0f);
            x = p.getX() + v.x * 4;
            y = p.getY();
            z = p.getZ() + v.z * 4;
        }
        counter++;
        UUID id = UUID.nameUUIDFromBytes(("primevisuals-bot-" + counter + "-" + System.nanoTime()).getBytes(StandardCharsets.UTF_8));
        OtherClientPlayerEntity bot = new OtherClientPlayerEntity(w, new GameProfile(id, "Bot"));
        bot.setId(1_000_000 + counter);
        bot.setPosition(x, y, z);
        float yaw = (float) (MathHelper.atan2(p.getZ() - z, p.getX() - x) * 57.29578) - 90.0f;
        bot.setYaw(yaw);
        bot.setHeadYaw(yaw);
        bot.setBodyYaw(yaw);
        bot.setPitch(0.0f);
        bot.setHealth(20.0f);
        w.addEntity(bot);
        BOTS.add(bot);
        return true;
    }

    /** Called when you hit a bot (the real attack is cancelled, so we apply the damage ourselves). */
    public static void hit(OtherClientPlayerEntity bot, PlayerEntity player) {
        if (bot.getHealth() <= 0.0f) return;
        float cd = player.getAttackCooldownProgress(0.5f);
        float dmg = 2.0f + 6.0f * cd * cd;
        bot.setHealth(Math.max(0.0f, bot.getHealth() - dmg));
        bot.hurtTime = 10;
        bot.maxHurtTime = 10;
        if (bot.getHealth() <= 0.0f) DEAD.put(bot, 0);
    }

    public static void tick(MinecraftClient mc) {
        if (mc.world == null) {
            BOTS.clear();
            DEAD.clear();
            return;
        }
        for (Iterator<Map.Entry<OtherClientPlayerEntity, Integer>> it = DEAD.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<OtherClientPlayerEntity, Integer> e = it.next();
            int t = e.getValue() + 1;
            if (t > 30) {
                e.getKey().discard();
                BOTS.remove(e.getKey());
                it.remove();
            } else {
                e.setValue(t);
            }
        }
        BOTS.removeIf(b -> b.isRemoved() || b.getWorld() != mc.world);
    }

    public static int clear() {
        int n = BOTS.size();
        for (OtherClientPlayerEntity b : BOTS) b.discard();
        BOTS.clear();
        DEAD.clear();
        return n;
    }
}
