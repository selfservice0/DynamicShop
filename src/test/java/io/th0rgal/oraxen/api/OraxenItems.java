package io.th0rgal.oraxen.api;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.minecraftsmp.dynamicshop.support.PlainTestStack;

/** Test-only stand-in for the optional Oraxen API; never included in the plugin JAR. */
public final class OraxenItems {
    private static final java.util.Set<String> CATEGORIES = java.util.Set.of(
            "moonstone_category_all", "moonstone_category_blocks", "moonstone_category_redstone",
            "moonstone_category_tools", "moonstone_category_armor", "moonstone_category_food",
            "moonstone_category_farming", "moonstone_category_wood", "moonstone_category_permissions",
            "moonstone_category_server", "moonstone_category_players");
    public static final ItemStack TEMPLATE = new PlainTestStack(Material.BRICK, 1);
    private static final ItemStack CATEGORY_TEMPLATE = new PlainTestStack(Material.AMETHYST_SHARD, 1);
    private static final ItemStack HALLOWEEN_TEMPLATE = new PlainTestStack(Material.JACK_O_LANTERN, 1);
    private static final java.util.Set<String> HALLOWEEN_BUTTONS = java.util.Set.of(
            "halloween_back_button", "halloween_next_button", "halloween_search_button", "halloween_filter_button",
            "halloween_page_button", "halloween_categories_button", "halloween_confirm_button", "halloween_cancel_button",
            "halloween_total_button", "halloween_settings_button", "halloween_save_button");
    public static Builder getItemById(String id) {
        for (String theme : new String[] {"christmas", "newyear", "valentines", "easter", "stpatricks", "summer"}) {
            if (id.startsWith(theme + "_") && (HALLOWEEN_BUTTONS.contains(id.replace(theme + "_", "halloween_"))
                    || CATEGORIES.contains(id.replace(theme + "_", "moonstone_")))) {
                Material material = switch (theme) {
                    case "christmas" -> Material.SPRUCE_SAPLING;
                    case "valentines" -> Material.ROSE_BUSH;
                    case "easter" -> Material.RABBIT_FOOT;
                    case "stpatricks" -> Material.EMERALD;
                    case "summer" -> Material.SUNFLOWER;
                    default -> Material.FIREWORK_ROCKET;
                };
                return new Builder(new PlainTestStack(material, 1));
            }
        }
        if (id.startsWith("moonstone_") && HALLOWEEN_BUTTONS.contains(id.replace("moonstone_", "halloween_"))) return new Builder(CATEGORY_TEMPLATE);
        if (HALLOWEEN_BUTTONS.contains(id) || id.startsWith("halloween_category_")
                && CATEGORIES.contains(id.replace("halloween_", "moonstone_"))) return new Builder(HALLOWEEN_TEMPLATE);
        if (CATEGORIES.contains(id)) return new Builder(CATEGORY_TEMPLATE);
        return id.equals("shop_food") ? new Builder() : null;
    }
    public static String getIdByItem(ItemStack stack) {
        return stack.getType() == Material.BRICK ? "shop_food" : null;
    }
    public static final class Builder {
        private final ItemStack template;
        public Builder() { this(TEMPLATE); }
        private Builder(ItemStack template) { this.template = template; }
        public ItemStack build() { return template; }
    }
}
