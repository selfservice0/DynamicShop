package org.minecraftsmp.dynamicshop.gui;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.minecraftsmp.dynamicshop.DynamicShop;
import org.minecraftsmp.dynamicshop.category.ItemCategory;
import org.minecraftsmp.dynamicshop.managers.CategoryConfigManager;
import org.minecraftsmp.dynamicshop.managers.MessageManager;
import org.minecraftsmp.dynamicshop.managers.ShopDataManager;
import org.minecraftsmp.dynamicshop.managers.MessageManager;
import java.util.ArrayList;
import java.util.List;

/**
 * GUI for selecting a category to browse in the shop.
 * 
 * Now uses CategoryConfigManager for dynamic slot positions, icons, and names.
 */
public class CategorySelectionGUI {

    private final DynamicShop plugin;
    private final Player player;
    private final Inventory inv;

    private static final int SIZE = 54;

    public CategorySelectionGUI(DynamicShop plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
        
        String title = plugin.getMessageManager().getMessage("gui-category-title");
        if (title == null) title = "§8§lShop Categories";
        
        this.inv = org.minecraftsmp.dynamicshop.util.PaperCompat.createInventory(null, SIZE,
                MessageManager.parseComponent(title, player));
    }

    public void open() {
        setupItems();
        player.openInventory(inv);
    }

    private void setupItems() {
        // Clear inventory
        inv.clear();

        // Fill all slots with filler first
        ItemStack filler = createFiller();
        for (int i = 0; i < SIZE; i++) {
            inv.setItem(i, filler);
        }

        // Place categories at their configured slots
        for (ItemCategory category : ItemCategory.values()) {
            int slot = CategoryConfigManager.getSlot(category);
            if (slot >= 0 && slot < SIZE) {
                inv.setItem(slot, createCategoryItem(category));
            }
        }
    }

    private ItemStack createCategoryItem(ItemCategory category) {
        // Use configured icon and name from CategoryConfigManager
        ItemStack item = CategoryConfigManager.getIconItem(category);
        String displayName = CategoryConfigManager.getDisplayName(category);

        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            // Apply colors if present
            String formattedName = displayName.contains("&")
                    ? displayName
                    : "§e§l" + displayName;
            org.minecraftsmp.dynamicshop.util.PaperCompat.setDisplayName(meta, MessageManager.parseComponent(formattedName));

            List<String> lore = new ArrayList<>();
            lore.add("§7───────────────────");

            // Get item count for this category
            int itemCount = getItemCount(category);

            if (itemCount > 0) {
                MessageManager.addLoreIfNotEmpty(lore, plugin.getMessageManager().categoryLoreItems(itemCount));
                lore.add("§7");
                MessageManager.addLoreIfNotEmpty(lore, plugin.getMessageManager().categoryLoreClickToBrowse());
            } else {
                MessageManager.addLoreIfNotEmpty(lore, plugin.getMessageManager().categoryLoreNoItems());
            }

            lore.add("§7───────────────────");

            org.minecraftsmp.dynamicshop.util.PaperCompat.setLore(meta, lore.stream().map(s -> MessageManager.parseComponent(s)).toList());
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
            int normalItems = ShopDataManager.getItemsInCategory(category).size();
            long specialItems = plugin.getSpecialShopManager().getAllSpecialItems().values().stream()
                    .filter(item -> item.getCategory() == category)
                    .count();
            return normalItems + (int) specialItems;
        }
    }

    private ItemStack createFiller() {
        return org.minecraftsmp.dynamicshop.managers.ConfigCacheManager.getFillerItem();
    }

    public void handleClick(Player p, int slot) {
        // Find category at this slot using CategoryConfigManager
        ItemCategory category = CategoryConfigManager.getCategoryAtSlot(slot);

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

    public Inventory getInventory() {
        return inv;
    }
}
