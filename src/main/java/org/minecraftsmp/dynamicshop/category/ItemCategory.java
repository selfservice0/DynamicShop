package org.minecraftsmp.dynamicshop.category;

import org.bukkit.Material;

/**
 * Represents a shop category.
 *
 * The icons here appear in:
 * - CategorySelectionGUI
 * - SpecialShopItem
 * - ShopGUI titles
 *
 * Category detection is based on material name patterns in ShopDataManager.
 * 
 * CUSTOM_1 through CUSTOM_20 are placeholder categories that admins can
 * rename and customize via the category editor. By default they are hidden
 * (slot -1) until an admin assigns them a slot.
 */
public enum ItemCategory {

    MISC("All", Material.CHEST),
    BLOCKS("Blocks", Material.GRASS_BLOCK),
    REDSTONE("Redstone", Material.REDSTONE),
    TOOLS("Tools & Weapons", Material.DIAMOND_SWORD),
    ARMOR("Armor", Material.IRON_CHESTPLATE),
    FOOD("Food", Material.COOKED_BEEF),
    FARMING("Farming", Material.WHEAT),
    WOOD("Wood", Material.OAK_LOG),
    PERMISSIONS("Permissions", Material.ENCHANTED_BOOK),
    SERVER_SHOP("Server Shop", Material.EMERALD),
    PLAYER_SHOPS("Player Shops", Material.PLAYER_HEAD),

    // Placeholder custom categories - admins can rename these
    CUSTOM_1("Custom 1", Material.CHEST),
    CUSTOM_2("Custom 2", Material.CHEST),
    CUSTOM_3("Custom 3", Material.CHEST),
    CUSTOM_4("Custom 4", Material.CHEST),
    CUSTOM_5("Custom 5", Material.CHEST),
    CUSTOM_6("Custom 6", Material.CHEST),
    CUSTOM_7("Custom 7", Material.CHEST),
    CUSTOM_8("Custom 8", Material.CHEST),
    CUSTOM_9("Custom 9", Material.CHEST),
    CUSTOM_10("Custom 10", Material.CHEST);

    private final String displayName;
    private final Material icon;

    ItemCategory(String displayName, Material icon) {
        this.displayName = displayName;
        this.icon = icon;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Material getIcon() {
        return icon;
    }

    /**
     * Check if this is a custom/placeholder category
     */
    public boolean isCustomCategory() {
        return this.name().startsWith("CUSTOM_");
    }
}