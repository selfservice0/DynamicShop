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
import org.minecraftsmp.dynamicshop.managers.ItemsAdderWrapper;
import org.minecraftsmp.dynamicshop.managers.ShopDataManager;
import org.minecraftsmp.dynamicshop.managers.ProtocolShopManager;
import org.minecraftsmp.dynamicshop.managers.MessageManager;
import org.minecraftsmp.dynamicshop.managers.ConfigCacheManager;
import org.minecraftsmp.dynamicshop.util.ShopItemBuilder;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

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
    private final boolean commandOpened;

    public ShopGUI(DynamicShop plugin, Player player, ItemCategory category) {
        this(plugin, player, category, false);
    }

    public ShopGUI(DynamicShop plugin, Player player, ItemCategory category, boolean commandOpened) {
        this.plugin = plugin;
        this.player = player;
        this.commandOpened = commandOpened;
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

        inventory = pm.createVirtualInventory(player, size,
                org.minecraftsmp.dynamicshop.managers.CategoryConfigManager.getDisplayName(category));
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
        ItemStack item = null;
        if ("itemsadder".equalsIgnoreCase(specialItem.getDeliveryMethod()) && specialItem.getNbt() != null) {
            item = ItemsAdderWrapper.getItem(specialItem.getNbt());
        }

        if (item == null) {
            item = new ItemStack(specialItem.getDisplayMaterial(), 1);
        } else {
            item = item.clone();
            item.setAmount(1);
        }
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(
                    LegacyComponentSerializer.legacySection().deserialize("§e§l" + specialItem.getDisplayName()));

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
                if (plugin.getPermissionsManager().hasPermission(player, specialItem.getPermission(), specialItem.getPermissionWorld())) {
                    lore.add("§7");
                    lore.add("§a✔ You already own this!");
                }
            } else if (specialItem.isServerShopItem()) {
                lore.add("§7Type: §bServer Item");
                lore.add("§7ID: §f" + specialItem.getItemIdentifier());
            }

            lore.add("§7───────────────────");
            lore.add("§eLeft-click to BUY");

            meta.lore(lore.stream().map(s -> LegacyComponentSerializer.legacySection().deserialize(s)).toList());
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
                meta.displayName(LegacyComponentSerializer.legacySection().deserialize("§c§lINVALID ITEM"));
                List<String> lore = new ArrayList<>();
                lore.add("§7Material: " + mat);
                lore.add("§cThis item is invalid");
                lore.add("§cin this version.");
                meta.lore(lore.stream().map(s -> LegacyComponentSerializer.legacySection().deserialize(s)).toList());
                item.setItemMeta(meta);
            }
            return item;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(net.kyori.adventure.text.Component.translatable(mat.translationKey()).color(net.kyori.adventure.text.format.NamedTextColor.YELLOW).decorate(net.kyori.adventure.text.format.TextDecoration.BOLD));

            List<String> lore = new ArrayList<>();

            boolean buyDisabled = ShopDataManager.isBuyDisabled(mat);
            boolean sellDisabled = ShopDataManager.isSellDisabled(mat);

            // Buy price (hide if buy disabled)
            if (!buyDisabled) {
                java.util.Map<String, String> buyPlaceholders = new java.util.HashMap<>();
                buyPlaceholders.put("price", plugin.getEconomyManager().format(price));
                MessageManager.addLoreIfNotEmpty(lore,
                        plugin.getMessageManager().getMessage("shop-lore-buy-price", buyPlaceholders));
            }

            // Sell price (hide if sell disabled)
            if (!sellDisabled) {
                java.util.Map<String, String> sellPlaceholders = new java.util.HashMap<>();
                sellPlaceholders.put("price", plugin.getEconomyManager().format(sellPrice));
                MessageManager.addLoreIfNotEmpty(lore,
                        plugin.getMessageManager().getMessage("shop-lore-sell-price", sellPlaceholders));
            }

            // Stock info (hide if buy disabled)
            if (!buyDisabled) {
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
                    double maxPercent = (ConfigCacheManager.maxPriceMultiplier - 1.0) * 100.0;
                    boolean capped = percentIncrease >= maxPercent;
                    if (capped) percentIncrease = maxPercent;

                    java.util.Map<String, String> percentPlaceholders = new java.util.HashMap<>();
                    percentPlaceholders.put("percent", String.format("%,.0f", percentIncrease) + (capped ? " (MAX)" : ""));
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
                    double maxPercent = (ConfigCacheManager.maxPriceMultiplier - 1.0) * 100.0;
                    boolean capped = percentIncrease >= maxPercent;
                    if (capped) percentIncrease = maxPercent;

                    java.util.Map<String, String> percentPlaceholders = new java.util.HashMap<>();
                    percentPlaceholders.put("percent", String.format("%,.0f", percentIncrease) + (capped ? " (MAX)" : ""));
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
            }

            lore.add("");
            if (!buyDisabled) {
                MessageManager.addLoreIfNotEmpty(lore, plugin.getMessageManager().getMessage("shop-lore-left-click-buy"));
                MessageManager.addLoreIfNotEmpty(lore,
                        plugin.getMessageManager().getMessage("shop-lore-shift-left-click-buy"));
            }
            if (!sellDisabled) {
                MessageManager.addLoreIfNotEmpty(lore, plugin.getMessageManager().getMessage("shop-lore-right-click-sell"));
                MessageManager.addLoreIfNotEmpty(lore,
                        plugin.getMessageManager().getMessage("shop-lore-shift-right-click-sell"));
            }

            meta.lore(lore.stream().map(s -> LegacyComponentSerializer.legacySection().deserialize(s)).toList());
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

        // Helper to get strings safely
        String prevName = plugin.getMessageManager().getMessage("gui-nav-previous");
        if (prevName == null) prevName = "§ePrevious Page";
        String prevLore = page > 0 ? plugin.getMessageManager().getMessage("gui-nav-previous-lore") : plugin.getMessageManager().getMessage("gui-nav-previous-none");
        if (prevLore == null) prevLore = page > 0 ? "§7Click to go back" : "§cNo previous page";
        
        // Left Arrow - Previous Page
        ItemStack prevPage = ShopItemBuilder.navItem(prevName, Material.ARROW, prevLore);
        pm.sendSlot(inventory, navRow + 0, prevPage);

        String nextName = plugin.getMessageManager().getMessage("gui-nav-next");
        if (nextName == null) nextName = "§eNext Page";
        String nextLore = page < maxPage ? plugin.getMessageManager().getMessage("gui-nav-next-lore") : plugin.getMessageManager().getMessage("gui-nav-next-none");
        if (nextLore == null) nextLore = page < maxPage ? "§7Click to go forward" : "§cNo next page";
        
        // Right Arrow - Next Page
        ItemStack nextPage = ShopItemBuilder.navItem(nextName, Material.ARROW, nextLore);
        pm.sendSlot(inventory, navRow + 8, nextPage);

        // X - Back to Categories (Red X) — hidden when opened via command
        if (commandOpened) {
            ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
            ItemMeta fillerMeta = filler.getItemMeta();
            if (fillerMeta != null) {
                fillerMeta.displayName(LegacyComponentSerializer.legacySection().deserialize(" "));
                filler.setItemMeta(fillerMeta);
            }
            pm.sendSlot(inventory, navRow + 4, filler);
        } else {
            String backName = plugin.getMessageManager().getMessage("gui-nav-back");
            if (backName == null) backName = "§c§lBack to Categories";
            String backLore = plugin.getMessageManager().getMessage("gui-nav-back-lore");
            if (backLore == null) backLore = "§7Return to category selection";
            
            ItemStack backToCategories = ShopItemBuilder.navItem(backName, Material.BARRIER, backLore);
            pm.sendSlot(inventory, navRow + 4, backToCategories);
        }

        // Compass - Search (Anvil GUI) — hidden when opened via command
        if (!commandOpened) {
            String searchName = plugin.getMessageManager().getMessage("gui-nav-search");
            if (searchName == null) searchName = "§b§lSearch Items";
            String searchLore = plugin.getMessageManager().getMessage("gui-nav-search-lore");
            if (searchLore == null) searchLore = "§7Open search menu";
            
            ItemStack search = ShopItemBuilder.navItem(searchName, Material.COMPASS, searchLore);
            pm.sendSlot(inventory, navRow + 3, search);
        }

        // Page Info
        int totalItems = (category == ItemCategory.PERMISSIONS || category == ItemCategory.SERVER_SHOP)
                ? specialItems.size()
                : displayItems.size();
                
        java.util.Map<String, String> pagePlaceholders = new java.util.HashMap<>();
        pagePlaceholders.put("page", String.valueOf(page + 1));
        pagePlaceholders.put("max", String.valueOf(maxPage + 1));
        pagePlaceholders.put("total", String.valueOf(totalItems));
        
        String pageName = plugin.getMessageManager().getMessage("gui-nav-page", pagePlaceholders);
        if (pageName == null) pageName = "§ePage §f" + (page + 1) + " §7/ §f" + (maxPage + 1);
        
        String pageLoreStr = plugin.getMessageManager().getMessage("gui-nav-page-lore", pagePlaceholders);
        if (pageLoreStr == null) pageLoreStr = "§7Total items: §e" + totalItems;
        
        ItemStack pageInfo = ShopItemBuilder.navItem(pageName, Material.PAPER, pageLoreStr);
        pm.sendSlot(inventory, navRow + 5, pageInfo);

        // Filter Toggle (Hopper)
        if (category != ItemCategory.PERMISSIONS && category != ItemCategory.SERVER_SHOP) {
            String filterName = plugin.getMessageManager().getMessage("gui-nav-filter");
            if (filterName == null) filterName = "§6Filter Options";
            
            String filterState = hideOutOfStock 
                    ? plugin.getMessageManager().getMessage("gui-nav-filter-hidden") 
                    : plugin.getMessageManager().getMessage("gui-nav-filter-shown");
            if (filterState == null) filterState = hideOutOfStock ? "§aCurrently: §fHiding Out of Stock" : "§cCurrently: §fShowing All";
            
            String filterLoreStr = plugin.getMessageManager().getMessage("gui-nav-filter-lore");
            if (filterLoreStr == null) filterLoreStr = "§7Click to toggle";
            
            ItemStack filterItem = ShopItemBuilder.navItem(filterName, Material.HOPPER, filterState, filterLoreStr);
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

    public boolean isCommandOpened() {
        return commandOpened;
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