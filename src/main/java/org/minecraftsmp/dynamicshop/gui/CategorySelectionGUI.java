package org.minecraftsmp.dynamicshop.gui;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.minecraftsmp.dynamicshop.DynamicShop;
import org.minecraftsmp.dynamicshop.category.ItemCategory;
import org.minecraftsmp.dynamicshop.managers.ShopDataManager;

import java.util.ArrayList;
import java.util.List;

/**
 * GUI for selecting a category to browse in the shop.
 *
 * Layout (36 slots / 4 rows):
 * Row 1: Empty
 * Row 2: _, _, MISC, BLOCKS, REDSTONE, TOOLS, ARMOR, _, _
 * Row 3: _, _, FOOD, FARMING, WOOD, PERMISSIONS, SERVER_SHOP, _, _
 * Row 4: Empty
 */
public class CategorySelectionGUI {

    private final DynamicShop plugin;
    private final Player player;
    private final Inventory inv;

    // Slot positions
    private static final int MISC_SLOT = 11; // Row 2, position 3
    private static final int BLOCKS_SLOT = 12; // Row 2, position 4
    private static final int REDSTONE_SLOT = 13; // Row 2, position 5
    private static final int TOOLS_SLOT = 14; // Row 2, position 6
    private static final int ARMOR_SLOT = 15; // Row 2, position 7

    private static final int FOOD_SLOT = 20; // Row 3, position 3
    private static final int FARMING_SLOT = 21; // Row 3, position 4
    private static final int WOOD_SLOT = 22; // Row 3, position 5
    private static final int PERMISSIONS_SLOT = 23;// Row 3, position 6
    private static final int SERVER_SHOP_SLOT = 24;// Row 3, position 7

    // Row 4 (bottom navigation row) - slots 27-35
    private static final int PLAYER_SHOPS_SLOT = 31; // Row 4, center position

    public CategorySelectionGUI(DynamicShop plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
        this.inv = Bukkit.createInventory(null, 36, "§8§lShop Categories");
    }

    public void open() {
        setupItems();
        player.openInventory(inv);
    }

    private void setupItems() {
        // Clear inventory
        inv.clear();

        // Add filler panes for empty slots
        ItemStack filler = createFiller();

        // Fill top row (slots 0-8)
        for (int i = 0; i < 9; i++) {
            inv.setItem(i, filler);
        }

        // Fill bottom row (slots 27-35)
        for (int i = 27; i < 36; i++) {
            if (i != PLAYER_SHOPS_SLOT) { // Don't fill the player shops slot
                inv.setItem(i, filler);
            }
        }

        // Fill empty slots in row 2
        inv.setItem(9, filler); // Row 2, pos 1
        inv.setItem(10, filler); // Row 2, pos 2
        inv.setItem(16, filler); // Row 2, pos 8
        inv.setItem(17, filler); // Row 2, pos 9

        // Fill empty slots in row 3
        inv.setItem(18, filler); // Row 3, pos 1
        inv.setItem(19, filler); // Row 3, pos 2
        inv.setItem(25, filler); // Row 3, pos 8
        inv.setItem(26, filler); // Row 3, pos 9

        // Add category items
        inv.setItem(MISC_SLOT, createCategoryItem(ItemCategory.MISC));
        inv.setItem(BLOCKS_SLOT, createCategoryItem(ItemCategory.BLOCKS));
        inv.setItem(REDSTONE_SLOT, createCategoryItem(ItemCategory.REDSTONE));
        inv.setItem(TOOLS_SLOT, createCategoryItem(ItemCategory.TOOLS));
        inv.setItem(ARMOR_SLOT, createCategoryItem(ItemCategory.ARMOR));
        inv.setItem(FOOD_SLOT, createCategoryItem(ItemCategory.FOOD));
        inv.setItem(FARMING_SLOT, createCategoryItem(ItemCategory.FARMING));
        inv.setItem(WOOD_SLOT, createCategoryItem(ItemCategory.WOOD));
        inv.setItem(PERMISSIONS_SLOT, createCategoryItem(ItemCategory.PERMISSIONS));
        inv.setItem(SERVER_SHOP_SLOT, createCategoryItem(ItemCategory.SERVER_SHOP));
        inv.setItem(PLAYER_SHOPS_SLOT, createCategoryItem(ItemCategory.PLAYER_SHOPS));
    }

    private ItemStack createCategoryItem(ItemCategory category) {
        ItemStack item = new ItemStack(category.getIcon());
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            meta.setDisplayName("§e§l" + category.getDisplayName());

            List<String> lore = new ArrayList<>();
            lore.add("§7───────────────────");

            // Get item count for this category
            int itemCount = getItemCount(category);

            if (itemCount > 0) {
                lore.add("§7Items: §a" + itemCount);
                lore.add("§7");
                lore.add("§eClick to browse!");
            } else {
                lore.add("§cNo items available");
            }

            lore.add("§7───────────────────");

            meta.setLore(lore);
            item.setItemMeta(meta);
        }

        return item;
    }

    private int getItemCount(ItemCategory category) {
        if (category == ItemCategory.PERMISSIONS) {
            return plugin.getSpecialShopManager().getAllSpecialItems().values().stream()
                    .filter(item -> item.getCategory() == ItemCategory.PERMISSIONS)
                    .toArray().length;
        } else if (category == ItemCategory.SERVER_SHOP) {
            return plugin.getSpecialShopManager().getAllSpecialItems().values().stream()
                    .filter(item -> item.getCategory() == ItemCategory.SERVER_SHOP)
                    .toArray().length;
        } else if (category == ItemCategory.PLAYER_SHOPS) {
            // Return number of active player shops
            return plugin.getPlayerShopManager().getActiveShopOwners().size();
        } else {
            return ShopDataManager.getItemsInCategory(category).size();
        }
    }

    private ItemStack createFiller() {
        ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = filler.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(" ");
            filler.setItemMeta(meta);
        }
        return filler;
    }

    public void handleClick(Player p, int slot) {
        ItemCategory category = getCategoryFromSlot(slot);

        if (category == null) {
            return; // Clicked a filler or empty slot
        }

        // Check if category has items
        if (getItemCount(category) == 0) {
            p.sendMessage(plugin.getMessageManager().categoryEmpty());
            return;
        }

        // Close current inventory
        p.closeInventory();

        // Handle Player Shops category specially
        if (category == ItemCategory.PLAYER_SHOPS) {
            org.minecraftsmp.dynamicshop.gui.PlayerShopBrowserGUI browserGUI = new org.minecraftsmp.dynamicshop.gui.PlayerShopBrowserGUI(
                    plugin, p);
            plugin.getPlayerShopListener().registerBrowserGUI(p, browserGUI);
            browserGUI.open();
            return;
        }

        // Open the shop GUI for this category
        ShopGUI shopGUI = new ShopGUI(plugin, p, category);
        plugin.getShopListener().registerShop(p, shopGUI);
        shopGUI.open();
    }

    private ItemCategory getCategoryFromSlot(int slot) {
        return switch (slot) {
            case MISC_SLOT -> ItemCategory.MISC;
            case BLOCKS_SLOT -> ItemCategory.BLOCKS;
            case REDSTONE_SLOT -> ItemCategory.REDSTONE;
            case TOOLS_SLOT -> ItemCategory.TOOLS;
            case ARMOR_SLOT -> ItemCategory.ARMOR;
            case FOOD_SLOT -> ItemCategory.FOOD;
            case FARMING_SLOT -> ItemCategory.FARMING;
            case WOOD_SLOT -> ItemCategory.WOOD;
            case PERMISSIONS_SLOT -> ItemCategory.PERMISSIONS;
            case SERVER_SHOP_SLOT -> ItemCategory.SERVER_SHOP;
            case PLAYER_SHOPS_SLOT -> ItemCategory.PLAYER_SHOPS;
            default -> null;
        };
    }

    public Inventory getInventory() {
        return inv;
    }
}