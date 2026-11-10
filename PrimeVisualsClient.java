package primevisuals;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.OtherClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import org.lwjgl.glfw.GLFW;

public class PrimeVisualsClient implements ClientModInitializer {
    public static KeyBinding OPEN;

    @Override
    public void onInitializeClient() {
        OPEN = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.primevisuals.open", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_SHIFT, "category.primevisuals"));
        Bolts.register();
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (OPEN.wasPressed()) {
                if (client.currentScreen == null) {
                    client.setScreen(new TexScreen());
                }
            }
            Bots.tick(client);
            Fx.tick(client);
        });
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (world.isClient()) {
                Fx.onAttack(player, entity);
                if (Bots.isBot(entity)) {
                    // bots are client-only: apply the damage ourselves and do not send an attack packet
                    Bots.hit((OtherClientPlayerEntity) entity, player);
                    player.swingHand(hand);
                    player.resetLastAttackedTicks();
                    return ActionResult.SUCCESS;
                }
            }
            return ActionResult.PASS;
        });
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                dispatcher.register(ClientCommandManager.literal("bot")
                        .then(ClientCommandManager.literal("spawn").executes(ctx -> {
                            boolean ok = Bots.spawn(MinecraftClient.getInstance());
                            ctx.getSource().sendFeedback(Text.literal(ok
                                    ? "Bot spawned where you are looking. Hit it! (only you can see it)"
                                    : "Join a world first."));
                            return ok ? 1 : 0;
                        }))
                        .then(ClientCommandManager.literal("clear").executes(ctx -> {
                            int n = Bots.clear();
                            ctx.getSource().sendFeedback(Text.literal("Removed " + n + " bot(s)."));
                            return 1;
                        }))));
    }
}
