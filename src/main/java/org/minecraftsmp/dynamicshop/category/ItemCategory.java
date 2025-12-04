package org.minecraftsmp.dynamicshop.category;

import org.bukkit.Material;

/**
 * Represents a shop category.
 *
 * The icons here appear in:
 *  - CategorySelectionGUI
 *  - SpecialShopItem
 *  - ShopGUI titles
 *
 * Category detection is based on material name patterns in ShopDataManager.
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
    PLAYER_SHOPS("Player Shops", Material.PLAYER_HEAD);

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
}