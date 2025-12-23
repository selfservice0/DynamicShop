package org.minecraftsmp.dynamicshop.gui;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.minecraftsmp.dynamicshop.DynamicShop;
import org.minecraftsmp.dynamicshop.category.ItemCategory;
import org.minecraftsmp.dynamicshop.category.SpecialShopItem;
import org.minecraftsmp.dynamicshop.managers.ShopDataManager;
import org.minecraftsmp.dynamicshop.managers.ProtocolShopManager;
import org.minecraftsmp.dynamicshop.managers.MessageManager;
import org.minecraftsmp.dynamicshop.managers.ConfigCacheManager;
import org.minecraftsmp.dynamicshop.util.ShopItemBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * A pure client-side virtual GUI rendered by ProtocolShopManager.
 *
 * NOTES:
 * - Does NOT mutate the real inventory.
 * - All slots are virtual-only and sent via ProtocolLib.
 * - Fetching items/slots relies on ShopDataManager.
 */
public class ShopGUI {

    private final DynamicShop plugin;
    private final Player player;
    private final ItemCategory category;
    private final int size;
    private final ProtocolShopManager pm;
    private final int itemsPerPage;
    private Inventory inventory; // Store the inventory reference

    private List<Material> allItems; // MASTER list of items in this category
    private List<Material> displayItems; // FILTERED list for rendering
    private List<SpecialShopItem> specialItems; // Special items (for PERMISSIONS/SERVER_SHOP)
    private int page = 0;
    private int maxPage = 0;
    private boolean hideOutOfStock = false;

    public ShopGUI(DynamicShop plugin, Player player, ItemCategory category) {
        this.plugin = plugin;
        this.player = player;
        this.category = category;

        this.size = plugin.getConfig().getInt("gui.shop_menu_size", 54);
        this.pm = plugin.getProtocolShopManager();
        this.itemsPerPage = size - 9; // Reserve bottom row for navigation

        // Load items based on category type
        if (category == ItemCategory.PERMISSIONS || category == ItemCategory.SERVER_SHOP) {
            // Load special items from SpecialShopManager
            this.specialItems = plugin.getSpecialShopManager().getAllSpecialItems().values().stream()
                    .filter(item -> item.getCategory() == category)
                    .collect(Collectors.toList());
            this.allItems = List.of(); // Empty regular items
            this.displayItems = List.of();

            // Calculate pagination based on special items
            this.maxPage = specialItems.isEmpty() ? 0 : (specialItems.size() - 1) / itemsPerPage;
        } else {
            // Load regular items from ShopDataManager
            this.allItems = ShopDataManager.getItemsInCategory(category);
            if (allItems == null)
                allItems = List.of();
            this.specialItems = List.of(); // Empty special items

            updateDisplayItems(); // Populate displayItems based on initial state
        }
    }

    /**
     * Open the GUI for the player.
     */
    public void open() {
        // Remove any stale tracking first
        plugin.getShopListener().unregisterCategory(player);
        plugin.getShopListener().unregisterShop(player); // just in case

        inventory = pm.createVirtualInventory(player, size, category.getDisplayName());
        render();
        player.openInventory(inventory);

        plugin.getShopListener().registerShop(player, this);
        plugin.getShopListener().updatePlayerInventoryLore(player, 2L);

        // Update player inventory lore AFTER the GUI is open
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            plugin.getShopListener().updatePlayerInventoryLore(player);
        }, 3L);
    }

    /**
     * Renders the entire GUI page — all items + navigation bar.
     */
    public void render() {
        // Clear upper slots
        for (int i = 0; i < itemsPerPage; i++) {
            pm.sendSlot(inventory, i, null);
        }

        // Render based on category type
        if (category == ItemCategory.PERMISSIONS || category == ItemCategory.SERVER_SHOP) {
            // Render special items
            int start = page * itemsPerPage;
            int end = Math.min(start + itemsPerPage, specialItems.size());

            for (int i = start; i < end; i++) {
                SpecialShopItem specialItem = specialItems.get(i);
                int slot = i - start;

                ItemStack displayItem = buildSpecialShopItem(specialItem);
                pm.sendSlot(inventory, slot, displayItem);
            }
        } else {
            // Render regular items from displayItems (filtered)
            int start = page * itemsPerPage;
            int end = Math.min(start + itemsPerPage, displayItems.size());

            for (int i = start; i < end; i++) {
                Material mat = displayItems.get(i);
                int slot = i - start;

                ItemStack displayItem = buildShopItem(mat);
                pm.sendSlot(inventory, slot, displayItem);
            }
        }

        // Render navigation
        renderNavigation();
    }

    private void updateDisplayItems() {
        if (allItems == null) {
            displayItems = List.of();
            maxPage = 0;
            return;
        }

        if (hideOutOfStock) {
            displayItems = allItems.stream()
                    .filter(mat -> ShopDataManager.getStock(mat) > 0)
                    .collect(Collectors.toList());
        } else {
            displayItems = new ArrayList<>(allItems);
        }

        // Recalculate max page
        this.maxPage = displayItems.isEmpty() ? 0 : (displayItems.size() - 1) / itemsPerPage;

        // Safety check: if current page exceeds maxPage (due to filtering), reset to 0
        if (page > maxPage) {
            page = 0;
        }
    }

    public void toggleHideOutOfStock() {
        this.hideOutOfStock = !this.hideOutOfStock;
        updateDisplayItems();
        // Clear slots first to prevent ghost items if list shrank
        for (int i = 0; i < itemsPerPage; i++) {
            pm.sendSlot(inventory, i, null);
        }
        render();
    }

    /**
     * Build display item for special shop items (permissions/server-shop)
     */
    private ItemStack buildSpecialShopItem(SpecialShopItem specialItem) {
        // Use the saved display material from the item
        Material icon = specialItem.getDisplayMaterial();

        ItemStack item = new ItemStack(icon, 1);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§e§l" + specialItem.getDisplayName());

            List<String> lore = new ArrayList<>();
            lore.add("§7───────────────────");

            // Price
            String priceFormatted = plugin.getEconomyManager().format(specialItem.getPrice());
            lore.add("§eBUY: §a" + priceFormatted);

            lore.add("§7");

            // Type-specific info
            if (specialItem.isPermissionItem()) {
                lore.add("§7Type: §dPermission");
                lore.add("§7Node: §f" + specialItem.getPermission());

                // Check if player already owns it
                if (plugin.getPermissionsManager().hasPermission(player, specialItem.getPermission())) {
                    lore.add("§7");
                    lore.add("§a✔ You already own this!");
                }
            } else if (specialItem.isServerShopItem()) {
                lore.add("§7Type: §bServer Item");
                lore.add("§7ID: §f" + specialItem.getItemIdentifier());
            }

            lore.add("§7───────────────────");
            lore.add("§eLeft-click to BUY");

            meta.setLore(lore);
            item.setItemMeta(meta);
        }

        return item;
    }

    // build the item with lore
    private ItemStack buildShopItem(Material mat) {
        double price = ShopDataManager.getTotalBuyCost(mat, 1);
        double sellPrice = ShopDataManager.getTotalSellValue(mat, 1);
        double stock = ShopDataManager.getStock(mat);

        ItemStack item;
        try {
            item = new ItemStack(mat, 1);
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Invalid shop item material skipped: " + mat);
            // Return a placeholder item so the GUI doesn't break entirely
            item = new ItemStack(Material.BARRIER);
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.setDisplayName("§c§lINVALID ITEM");
                List<String> lore = new ArrayList<>();
                lore.add("§7Material: " + mat);
                lore.add("§cThis item is invalid");
                lore.add("§cin this version.");
                meta.setLore(lore);
                item.setItemMeta(meta);
            }
            return item;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§e§l" + mat.name().replace("_", " "));

            List<String> lore = new ArrayList<>();

            // Buy price
            java.util.Map<String, String> buyPlaceholders = new java.util.HashMap<>();
            buyPlaceholders.put("price", plugin.getEconomyManager().format(price));
            MessageManager.addLoreIfNotEmpty(lore,
                    plugin.getMessageManager().getMessage("shop-lore-buy-price", buyPlaceholders));

            // Sell price
            java.util.Map<String, String> sellPlaceholders = new java.util.HashMap<>();
            sellPlaceholders.put("price", plugin.getEconomyManager().format(sellPrice));
            MessageManager.addLoreIfNotEmpty(lore,
                    plugin.getMessageManager().getMessage("shop-lore-sell-price", sellPlaceholders));

            // Stock info
            if (stock < 0) {
                java.util.Map<String, String> stockPlaceholders = new java.util.HashMap<>();
                stockPlaceholders.put("stock", String.format("%.0f", stock));
                MessageManager.addLoreIfNotEmpty(lore,
                        plugin.getMessageManager().getMessage("lore-stock-negative", stockPlaceholders));

                // Show price increase for negative stock too
                double hours = ShopDataManager.getHoursInShortage(mat);
                double hourlyRate = ConfigCacheManager.hourlyIncreasePercent / 100.0;
                double multiplier = Math.pow(1.0 + hourlyRate, hours);
                double percentIncrease = (multiplier - 1.0) * 100.0;

                java.util.Map<String, String> percentPlaceholders = new java.util.HashMap<>();
                percentPlaceholders.put("percent", String.format("%,.0f", percentIncrease));
                MessageManager.addLoreIfNotEmpty(lore,
                        plugin.getMessageManager().getMessage("shop-lore-price-increase", percentPlaceholders));
                MessageManager.addLoreIfNotEmpty(lore,
                        plugin.getMessageManager().getMessage("shop-lore-price-increase-note"));
            } else if (stock == 0) {
                MessageManager.addLoreIfNotEmpty(lore, plugin.getMessageManager().getMessage("lore-out-of-stock"));

                // Show price increase for zero stock
                double hours = ShopDataManager.getHoursInShortage(mat);
                double hourlyRate = ConfigCacheManager.hourlyIncreasePercent / 100.0;
                double multiplier = Math.pow(1.0 + hourlyRate, hours);
                double percentIncrease = (multiplier - 1.0) * 100.0;

                java.util.Map<String, String> percentPlaceholders = new java.util.HashMap<>();
                percentPlaceholders.put("percent", String.format("%,.0f", percentIncrease));
                MessageManager.addLoreIfNotEmpty(lore,
                        plugin.getMessageManager().getMessage("shop-lore-price-increase", percentPlaceholders));
                MessageManager.addLoreIfNotEmpty(lore,
                        plugin.getMessageManager().getMessage("shop-lore-price-increase-note"));
            } else {
                java.util.Map<String, String> stockPlaceholders = new java.util.HashMap<>();
                stockPlaceholders.put("stock", String.format("%.0f", stock));
                MessageManager.addLoreIfNotEmpty(lore,
                        plugin.getMessageManager().getMessage("lore-stock", stockPlaceholders));
                if (stock < 10) {
                    MessageManager.addLoreIfNotEmpty(lore,
                            plugin.getMessageManager().getMessage("shop-lore-low-stock"));
                }
            }

            lore.add("");
            MessageManager.addLoreIfNotEmpty(lore, plugin.getMessageManager().getMessage("shop-lore-left-click-buy"));
            MessageManager.addLoreIfNotEmpty(lore,
                    plugin.getMessageManager().getMessage("shop-lore-shift-left-click-buy"));
            MessageManager.addLoreIfNotEmpty(lore, plugin.getMessageManager().getMessage("shop-lore-right-click-sell"));
            MessageManager.addLoreIfNotEmpty(lore,
                    plugin.getMessageManager().getMessage("shop-lore-shift-right-click-sell"));

            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    /**
     * Clears all visual items.
     */
    public void clear() {
        if (inventory != null) {
            inventory.clear();
        }
    }

    /**
     * Renders navigation area (bottom row).
     */
    private void renderNavigation() {
        int navRow = size - 9; // Bottom row starts here

        // Left Arrow - Previous Page
        ItemStack prevPage = ShopItemBuilder.navItem(
                "§ePrevious Page",
                Material.ARROW,
                page > 0 ? "§7Click to go back" : "§cNo previous page");
        pm.sendSlot(inventory, navRow + 0, prevPage);

        // Right Arrow - Next Page
        ItemStack nextPage = ShopItemBuilder.navItem(
                "§eNext Page",
                Material.ARROW,
                page < maxPage ? "§7Click to go forward" : "§cNo next page");
        pm.sendSlot(inventory, navRow + 8, nextPage);

        // X - Back to Categories (Red X)
        ItemStack backToCategories = ShopItemBuilder.navItem(
                "§c§lBack to Categories",
                Material.BARRIER,
                "§7Return to category selection");
        pm.sendSlot(inventory, navRow + 4, backToCategories);

        // Compass - Search (Anvil GUI)
        ItemStack search = ShopItemBuilder.navItem(
                "§b§lSearch Items",
                Material.COMPASS,
                "§7Open search menu");
        pm.sendSlot(inventory, navRow + 3, search);

        // Page Info
        int totalItems = (category == ItemCategory.PERMISSIONS || category == ItemCategory.SERVER_SHOP)
                ? specialItems.size()
                : displayItems.size();
        ItemStack pageInfo = ShopItemBuilder.navItem(
                "§ePage §f" + (page + 1) + " §7/ §f" + (maxPage + 1),
                Material.PAPER,
                "§7Total items: §e" + totalItems);
        pm.sendSlot(inventory, navRow + 5, pageInfo);

        // Filter Toggle (Hopper)
        if (category != ItemCategory.PERMISSIONS && category != ItemCategory.SERVER_SHOP) {
            ItemStack filterItem = ShopItemBuilder.navItem(
                    "§6Filter Options",
                    Material.HOPPER,
                    hideOutOfStock ? "§aCurrently: §fHiding Out of Stock" : "§cCurrently: §fShowing All",
                    "§7Click to toggle");
            pm.sendSlot(inventory, navRow + 2, filterItem);
        }
    }

    /**
     * Moves a page forward.
     */
    public void nextPage() {
        if (page < maxPage) {
            page++;
            render();
        }
    }

    /**
     * Moves a page backwards.
     */
    public void prevPage() {
        if (page > 0) {
            page--;
            render();
        }
    }

    public ItemCategory getCategory() {
        return category;
    }

    public int getSize() {
        return size;
    }

    public int getPage() {
        return page;
    }

    public boolean isNavigationSlot(int rawSlot) {
        return rawSlot >= (size - 9);
    }

    /**
     * Returns the Material that corresponds to the clicked slot (for regular
     * items).
     * IMPORTANT: Because this is a virtual GUI, we compute it instead of reading
     * inventory.
     */
    public Material getItemFromSlot(int clickedSlot) {
        if (isNavigationSlot(clickedSlot))
            return null;
        if (category == ItemCategory.PERMISSIONS || category == ItemCategory.SERVER_SHOP)
            return null;

        int index = (page * itemsPerPage) + clickedSlot;

        if (index < 0 || index >= displayItems.size())
            return null;
        return displayItems.get(index);
    }

    /**
     * Returns the SpecialShopItem that corresponds to the clicked slot (for special
     * categories).
     */
    public SpecialShopItem getSpecialItemFromSlot(int clickedSlot) {
        if (isNavigationSlot(clickedSlot))
            return null;
        if (category != ItemCategory.PERMISSIONS && category != ItemCategory.SERVER_SHOP)
            return null;

        int index = (page * itemsPerPage) + clickedSlot;

        if (index < 0 || index >= specialItems.size())
            return null;
        return specialItems.get(index);
    }

    public Inventory getInventory() {
        return inventory;
    }
}