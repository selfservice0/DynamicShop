package com.nexomc.nexo.api;

import com.nexomc.nexo.items.ItemBuilder;
import java.util.Set;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.minecraftsmp.dynamicshop.support.PlainTestStack;

/** Test-only optional API; no Nexo implementation is bundled in the plugin. */
public final class NexoItems {
    private static final Set<String> CATEGORIES = Set.of("all", "blocks", "redstone", "tools", "armor", "food",
            "farming", "wood", "permissions", "server", "players");
    private static final Set<String> CONTROLS = Set.of("back", "next", "search", "filter", "page", "categories",
            "confirm", "cancel", "total", "settings", "save");
    private static final ItemStack MOONSTONE = new PlainTestStack(Material.PRISMARINE_SHARD, 1);
    private static final ItemStack HALLOWEEN = new PlainTestStack(Material.CARVED_PUMPKIN, 1);
    private static final ItemStack CUSTOM = new PlainTestStack(Material.WHEAT, 1);

    public static ItemBuilder itemFromId(String id) {
        if ("shop_food".equals(id)) return new ItemBuilder(CUSTOM);
        for (String theme : new String[] {"moonstone", "halloween", "christmas", "newyear", "valentines", "easter", "stpatricks", "summer"}) {
            if (id.startsWith(theme + "_category_") && CATEGORIES.contains(id.substring((theme + "_category_").length()))
                    || id.startsWith(theme + "_") && id.endsWith("_button")
                    && CONTROLS.contains(id.substring(theme.length() + 1, id.length() - "_button".length()))) {
                ItemStack item = switch (theme) {
                    case "halloween" -> HALLOWEEN;
                    case "christmas" -> new PlainTestStack(Material.SPRUCE_SAPLING, 1);
                    case "newyear" -> new PlainTestStack(Material.FIREWORK_ROCKET, 1);
                    case "valentines" -> new PlainTestStack(Material.ROSE_BUSH, 1);
                    case "easter" -> new PlainTestStack(Material.RABBIT_FOOT, 1);
                    case "stpatricks" -> new PlainTestStack(Material.EMERALD, 1);
                    case "summer" -> new PlainTestStack(Material.SUNFLOWER, 1);
                    default -> MOONSTONE;
                };
                return new ItemBuilder(item);
            }
        }
        if (id.startsWith("shop_") && id.endsWith("_button")
                && CONTROLS.contains(id.substring(5, id.length() - "_button".length()))) return new ItemBuilder(MOONSTONE);
        return null;
    }

    public static String idFromItem(ItemStack item) {
        return item != null && item.getType() == Material.WHEAT ? "shop_food" : null;
    }
}
