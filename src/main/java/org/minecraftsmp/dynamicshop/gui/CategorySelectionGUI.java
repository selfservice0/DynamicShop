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
import org.minecraftsmp.dynamicshop.managers.ShopDataManager;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
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

    private static final int SIZE = 36;

    public CategorySelectionGUI(DynamicShop plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
        this.inv = Bukkit.createInventory(null, SIZE,
                LegacyComponentSerializer.legacySection().deserialize("§8§lShop Categories"));
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
        Material icon = CategoryConfigManager.getIcon(category);
        String displayName = CategoryConfigManager.getDisplayName(category);

        ItemStack item = new ItemStack(icon);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            // Apply colors if present
            String formattedName = displayName.contains("&")
                    ? displayName
                    : "§e§l" + displayName;
            meta.displayName(LegacyComponentSerializer.legacyAmpersand().deserialize(formattedName));

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

            meta.lore(lore.stream().map(s -> LegacyComponentSerializer.legacySection().deserialize(s)).toList());
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
            meta.displayName(LegacyComponentSerializer.legacySection().deserialize(" "));
            filler.setItemMeta(meta);
        }
        return filler;
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
