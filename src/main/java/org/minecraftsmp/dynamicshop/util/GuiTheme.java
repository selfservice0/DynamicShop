package org.minecraftsmp.dynamicshop.util;

import java.util.Map;
import java.util.List;
import java.util.Locale;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.minecraftsmp.dynamicshop.DynamicShop;
import org.minecraftsmp.dynamicshop.category.ItemCategory;
import org.minecraftsmp.dynamicshop.managers.CustomItemSupport;
import org.minecraftsmp.dynamicshop.managers.CategoryConfigManager;

/** Optional GUI artwork; existing message titles remain the default. */
public final class GuiTheme {
    private static final List<String> THEMES = List.of("default", "moonstone", "halloween", "christmas", "newyear",
            "valentines", "easter", "stpatricks", "summer");

    public static List<String> names() { return THEMES; }

    public static String normalize(String value) {
        if (value == null) return "default";
        String name = value.trim().toLowerCase(Locale.ROOT).replace("-", "").replace("_", "").replace(" ", "")
                .replace("'", "").replace(".", "");
        return switch (name) {
            case "newyears" -> "newyear";
            case "moonlit" -> "moonstone";
            case "valentine", "valentinesday" -> "valentines";
            case "stpatrick", "stpatricksday", "saintpatricksday" -> "stpatricks";
            case "summerfestival", "midsummer" -> "summer";
            default -> name;
        };
    }

    public static boolean builtIn(String name) {
        String normalized = normalize(name);
        return !"default".equals(normalized) && THEMES.contains(normalized);
    }

    public static String selected(DynamicShop plugin) {
        return normalize(plugin.getConfig().getString("gui.theme", "default"));
    }
    private static final Map<ItemCategory, String> CATEGORY_ICONS = Map.ofEntries(
            Map.entry(ItemCategory.MISC, "moonstone_category_all"),
            Map.entry(ItemCategory.BLOCKS, "moonstone_category_blocks"),
            Map.entry(ItemCategory.REDSTONE, "moonstone_category_redstone"),
            Map.entry(ItemCategory.TOOLS, "moonstone_category_tools"),
            Map.entry(ItemCategory.ARMOR, "moonstone_category_armor"),
            Map.entry(ItemCategory.FOOD, "moonstone_category_food"),
            Map.entry(ItemCategory.FARMING, "moonstone_category_farming"),
            Map.entry(ItemCategory.WOOD, "moonstone_category_wood"),
            Map.entry(ItemCategory.PERMISSIONS, "moonstone_category_permissions"),
            Map.entry(ItemCategory.SERVER_SHOP, "moonstone_category_server"),
            Map.entry(ItemCategory.PLAYER_SHOPS, "moonstone_category_players"));
    private record Layout(String glyph, int slots) {}
    private static final Map<String, Layout> MOONSTONE = Map.ofEntries(
            Map.entry("gui-category-title", new Layout("dynamicshop_moonstone_categories_54", 54)),
            Map.entry("shop-gui-title", new Layout("dynamicshop_moonstone_shop_54", 54)),
            Map.entry("sell-gui-title", new Layout("dynamicshop_moonstone_sell_54", 54)),
            Map.entry("search-gui-title", new Layout("dynamicshop_moonstone_list_54", 54)),
            Map.entry("player-shop-browser-title", new Layout("dynamicshop_moonstone_list_54", 54)),
            Map.entry("player-shop-view-title", new Layout("dynamicshop_moonstone_shop_54", 54)),
            Map.entry("item-action-title", new Layout("dynamicshop_moonstone_action_27", 27)),
            Map.entry("admin-shop-gui-title", new Layout("dynamicshop_moonstone_admin_54", 54)),
            Map.entry("admin-category-gui-title", new Layout("dynamicshop_moonstone_admin_54", 54)),
            Map.entry("admin-category-edit-title", new Layout("dynamicshop_moonstone_admin_36", 36)),
            Map.entry("admin-config-gui-title", new Layout("dynamicshop_moonstone_admin_54", 54)),
            Map.entry("admin-item-edit-title", new Layout("dynamicshop_moonstone_admin_45", 45)),
            Map.entry("admin-special-edit-title", new Layout("dynamicshop_moonstone_admin_54", 54)));

    private GuiTheme() {}

    public static boolean enabled(DynamicShop plugin, Player viewer) {
        if (plugin == null || BedrockUtil.isBedrock(viewer)) return false;
        if (!builtIn(selected(plugin))) return false;
        String provider = CustomItemSupport.guiProvider();
        return "nexo".equals(provider) || "oraxen".equals(provider);
    }

    /** The themed category chooser and item browser share one six-row storefront. */
    public static int shopMenuSize(DynamicShop plugin, Player viewer) {
        return enabled(plugin, viewer) ? 54 : plugin.getConfig().getInt("gui.shop_menu_size", 54);
    }

    public static String title(DynamicShop plugin, Player viewer, String key, String fallback, int slots,
                               Map<String, String> placeholders) {
        String configured = plugin.getMessageManager().getMessage(key, placeholders, fallback);
        if (!enabled(plugin, viewer)) return configured;
        Layout layout = MOONSTONE.get(key);
        if ("shop-gui-title".equals(key)) {
            layout = switch (slots) {
                case 27 -> new Layout("dynamicshop_moonstone_shop_27", 27);
                case 36 -> new Layout("dynamicshop_moonstone_shop_36", 36);
                case 45 -> new Layout("dynamicshop_moonstone_shop_45", 45);
                default -> layout;
            };
        }
        if ("gui-category-title".equals(key) && slots == 54) layout = categoryLayout();
        String label = plainLabel(configured);
        // Unsupported sizes keep a readable title instead of a misaligned inventory glyph.
        if (layout == null || layout.slots != slots) return "§f§l" + label;
        String glyph = layout.glyph.replace("moonstone", selected(plugin));
        return themedTitle(configured, glyph);
    }

    /** Paper slider dialogs have their own backdrop, without an inventory grid. */
    public static String dialogTitle(DynamicShop plugin, Player viewer, Map<String, String> placeholders) {
        String configured = plugin.getMessageManager().getMessage("dialog-title", placeholders, "&6{item}");
        if (!enabled(plugin, viewer)) return configured;
        String glyph = "dynamicshop_" + selected(plugin) + "_dialog";
        return themedTitle(configured, glyph);
    }

    private static String themedTitle(String configured, String glyph) {
        return "<white><shift:-48><glyph:" + glyph + "><shift:-170><white><bold>"
                + MiniMessage.miniMessage().escapeTags(plainLabel(configured));
    }

    private static Layout categoryLayout() {
        boolean central = true;
        boolean wide = false;
        for (ItemCategory category : ItemCategory.values()) {
            int slot = CategoryConfigManager.getSlot(category);
            if (slot < 0 || slot >= 54) continue;
            int row = slot / 9;
            int column = slot % 9;
            if (row < 1 || row > 4 || column < 3 || column > 5) central = false;
            if (row < 1 || row > 4 || column < 1 || column > 7) wide = true;
        }
        // Fit the showcase to existing category positions instead of moving configured icons.
        String glyph = central ? "dynamicshop_moonstone_categories_54"
                : wide ? "dynamicshop_moonstone_categories_wide_54" : "dynamicshop_moonstone_shop_54";
        return new Layout(glyph, 54);
    }

    static String plainLabel(String title) {
        if (title == null) return "";
        String text = title.replaceAll("(?i)</?(?:glyph|g|shift|s|font)(?::[^>]*)?>", "");
        text = MiniMessage.miniMessage().stripTags(text);
        text = ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&', text));
        StringBuilder label = new StringBuilder();
        text.codePoints().filter(code -> Character.getType(code) != Character.PRIVATE_USE
                && (code < 0xA413 || code > 0xA418)).forEach(label::appendCodePoint);
        return label.toString();
    }

    public static ItemStack button(DynamicShop plugin, Player viewer, String name, String id,
                                   Material fallback, String... lore) {
        return enabled(plugin, viewer) ? ShopItemBuilder.navItemNexo(viewer, name, id, fallback, lore)
                : ShopItemBuilder.navItem(name, fallback, lore);
    }

    /** Theme defaults only; callers keep an explicit category icon override in preference. */
    public static ItemStack categoryIcon(DynamicShop plugin, Player viewer, ItemCategory category) {
        String id = CATEGORY_ICONS.get(category);
        return id != null && enabled(plugin, viewer) ? CustomItemSupport.getMenuItem(itemId(plugin, viewer, id), viewer) : null;
    }

    /** Theme only built-in controls; explicitly qualified custom item IDs remain authoritative. */
    public static String itemId(DynamicShop plugin, Player viewer, String id) {
        if (id == null || !enabled(plugin, viewer)) return id;
        String theme = selected(plugin);
        if (id.startsWith("moonstone_")) return theme + "_" + id.substring("moonstone_".length());
        return switch (id) {
            case "shop_back_button", "shop_next_button", "shop_search_button", "shop_filter_button",
                 "shop_page_button", "shop_categories_button" -> theme + "_" + id.substring("shop_".length());
            default -> id;
        };
    }

    /** Decorate an existing control without changing its name, lore, or click slot. */
    public static ItemStack control(DynamicShop plugin, Player viewer, ItemStack original, String id) {
        if (!enabled(plugin, viewer)) return original;
        ItemStack themed = CustomItemSupport.getMenuItem(itemId(plugin, viewer, id), viewer);
        if (themed == null) return original;
        themed = themed.clone();
        var source = original.getItemMeta();
        var target = themed.getItemMeta();
        if (source == null || target == null) return original;
        PaperCompat.setDisplayName(target, PaperCompat.getDisplayName(source));
        PaperCompat.setLore(target, PaperCompat.getLore(source));
        themed.setItemMeta(target);
        return themed;
    }
}
