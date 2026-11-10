package primevisuals;

import net.minecraft.item.Item;
import net.minecraft.item.Items;

import java.util.ArrayList;
import java.util.List;

public class Categories {
    public static final List<Category> ALL = new ArrayList<>();

    private static void add(String id, String name, Item icon, boolean extra, String... paths) {
        ALL.add(new Category(id, name, icon, extra, paths));
    }

    private static String[] numbered(String prefix, int n) {
        String[] r = new String[n];
        for (int i = 0; i < n; i++) r[i] = prefix + i;
        return r;
    }

    private static String[] named(String prefix, String suffix, String... names) {
        String[] r = new String[names.length];
        for (int i = 0; i < names.length; i++) r[i] = prefix + names[i] + suffix;
        return r;
    }

    private static String[] join(String[]... parts) {
        List<String> l = new ArrayList<>();
        for (String[] p : parts) for (String x : p) l.add(x);
        return l.toArray(new String[0]);
    }

    private static String[] dripstone() {
        List<String> l = new ArrayList<>();
        l.add("block/dripstone_block");
        l.add("item/pointed_dripstone");
        for (String dir : new String[]{"up", "down"}) {
            for (String part : new String[]{"tip", "tip_merge", "frustum", "middle", "base"}) {
                l.add("block/pointed_dripstone_" + dir + "_" + part);
            }
        }
        return l.toArray(new String[0]);
    }

    private static String[] tools(String type) {
        String[] mats = {"netherite", "diamond", "iron", "golden", "stone", "wooden"};
        String[] r = new String[mats.length];
        for (int i = 0; i < mats.length; i++) r[i] = "item/" + mats[i] + "_" + type;
        return r;
    }

    private static String[] armor(String[] itemMats, String[] equipMats) {
        String[] pieces = {"helmet", "chestplate", "leggings", "boots"};
        List<String> l = new ArrayList<>();
        for (String m : itemMats) for (String p : pieces) l.add("item/" + m + "_" + p);
        for (String e : equipMats) {
            l.add("entity/equipment/humanoid/" + e);
            l.add("entity/equipment/humanoid_leggings/" + e);
        }
        return l.toArray(new String[0]);
    }

    static {
        add("crystal", "Crystals", Items.END_CRYSTAL, false,
                "item/end_crystal", "entity/end_crystal/end_crystal", "entity/end_crystal/end_crystal_beam");
        add("anchor", "Anchor", Items.RESPAWN_ANCHOR, false,
                "block/respawn_anchor_top", "block/respawn_anchor_top_off", "block/respawn_anchor_bottom",
                "block/respawn_anchor_side0", "block/respawn_anchor_side1", "block/respawn_anchor_side2",
                "block/respawn_anchor_side3", "block/respawn_anchor_side4");
        add("obsidian", "Obsidian", Items.OBSIDIAN, false, "block/obsidian");
        add("totem", "Totem", Items.TOTEM_OF_UNDYING, false, "item/totem_of_undying");
        add("pearl", "Pearl", Items.ENDER_PEARL, false, "item/ender_pearl");
        add("gapple", "Gapple", Items.GOLDEN_APPLE, false, "item/golden_apple", "item/enchanted_golden_apple");
        add("sword", "Sword", Items.NETHERITE_SWORD, false, tools("sword"));
        add("axe", "Axe", Items.NETHERITE_AXE, false, tools("axe"));
        add("mace", "Mace", Items.MACE, false, "item/mace");
        add("armor", "Armor", Items.IRON_CHESTPLATE, false,
                armor(new String[]{"iron", "diamond", "golden", "chainmail", "leather"},
                        new String[]{"iron", "diamond", "gold", "chainmail"}));
        add("shield", "Shield", Items.SHIELD, false,
                "entity/shield_base", "entity/shield_base_nopattern", "entity/shield/*");
        add("bow", "Bow", Items.BOW, false,
                "item/bow", "item/bow_pulling_0", "item/bow_pulling_1", "item/bow_pulling_2");
        add("crossbow", "Crossbow", Items.CROSSBOW, false,
                "item/crossbow_standby", "item/crossbow_pulling_0", "item/crossbow_pulling_1",
                "item/crossbow_pulling_2", "item/crossbow_arrow", "item/crossbow_firework");
        add("netherite", "Netherite Armor", Items.NETHERITE_CHESTPLATE, false,
                armor(new String[]{"netherite"}, new String[]{"netherite"}));
        add("pickaxe", "Pickaxe", Items.NETHERITE_PICKAXE, false, tools("pickaxe"));
        add("xp", "XP Bottle", Items.EXPERIENCE_BOTTLE, false, "item/experience_bottle");
        add("elytra", "Elytra", Items.ELYTRA, false,
                "entity/equipment/wings/elytra", "entity/elytra", "item/elytra", "item/broken_elytra");
        add("shears", "Shears", Items.SHEARS, false, "item/shears");
        add("fireworks", "Fireworks", Items.FIREWORK_ROCKET, false,
                join(new String[]{"item/firework_rocket", "item/firework_star", "item/firework_star_overlay", "particle/flash"},
                        numbered("particle/spark_", 8)));

        add("hitsound", "Hit Sound", Items.IRON_SWORD, true);
        add("killsound", "Kill Sound", Items.NOTE_BLOCK, true);
        add("cobweb", "Cobweb", Items.COBWEB, true, "block/cobweb");
        add("glowstone", "Glowstone", Items.GLOWSTONE, true, "block/glowstone");
        add("explosion", "Explosions", Items.TNT, true, numbered("particle/explosion_", 16));
        add("glint", "Glint", Items.ENCHANTED_BOOK, true, "misc/enchanted_glint_item", "misc/enchanted_glint_entity");
        add("potions", "Potions", Items.POTION, true,
                "item/potion", "item/splash_potion", "item/lingering_potion", "item/potion_overlay");
        add("windcharge", "Wind Charge", Items.WIND_CHARGE, true, "item/wind_charge", "entity/projectiles/wind_charge");
        add("hearts", "Hearts", Items.POPPY, true, "gui/sprites/hud/heart/*");
        add("crosshair", "Crosshair", Items.COMPASS, true, "gui/sprites/hud/crosshair");
        add("hotbar", "Hotbar", Items.OAK_PLANKS, true,
                "gui/sprites/hud/hotbar", "gui/sprites/hud/hotbar_selection",
                "gui/sprites/hud/hotbar_offhand_left", "gui/sprites/hud/hotbar_offhand_right");
        add("fire", "Fire", Items.FLINT_AND_STEEL, true,
                "block/fire_0", "block/fire_1", "block/soul_fire_0", "block/soul_fire_1");
        add("inventory", "Inventory", Items.CRAFTING_TABLE, true, "gui/container/inventory");
        add("enderchest", "Ender Chest", Items.ENDER_CHEST, true, "entity/chest/ender");
        add("wool", "Wool", Items.WHITE_WOOL, true,
                named("block/", "_wool", "white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray",
                        "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black"));
        add("dirt", "Dirt", Items.DIRT, true,
                "block/dirt", "block/coarse_dirt", "block/rooted_dirt", "block/podzol_top", "block/podzol_side",
                "block/dirt_path_top", "block/dirt_path_side");
        add("cobblestone", "Cobblestone", Items.COBBLESTONE, true,
                "block/cobblestone", "block/mossy_cobblestone", "block/cobbled_deepslate");
        add("planks", "Planks", Items.OAK_PLANKS, true,
                named("block/", "_planks", "oak", "spruce", "birch", "jungle", "acacia", "dark_oak", "mangrove",
                        "cherry", "bamboo", "crimson", "warped", "pale_oak"));
        add("dripstone", "Dripstone", Items.POINTED_DRIPSTONE, true, dripstone());
    }
}
