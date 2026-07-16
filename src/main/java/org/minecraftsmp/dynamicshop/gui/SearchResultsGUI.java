package org.minecraftsmp.dynamicshop.gui;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.minecraftsmp.dynamicshop.DynamicShop;
import org.minecraftsmp.dynamicshop.category.ItemCategory;
import org.minecraftsmp.dynamicshop.managers.ProtocolShopManager;
import org.minecraftsmp.dynamicshop.managers.ShopDataManager;
import org.minecraftsmp.dynamicshop.managers.ConfigCacheManager;
import org.minecraftsmp.dynamicshop.util.ShopItemBuilder;
import org.minecraftsmp.dynamicshop.managers.MessageManager;
import java.util.ArrayList;
import java.util.List;

public class SearchResultsGUI {

    private final DynamicShop plugin;
    private final Player player;
    private final ProtocolShopManager pm;
    private final List<Material> results;
    // private final ItemCategory category; // Removed unused field
    private Inventory inventory;

    /**
     * Search all categories
     */
    public SearchResultsGUI(DynamicShop plugin, Player player, String query) {
        this(plugin, player, query, null);
    }

    /**
     * Search within a specific category (or all if category is null)
     */
    public SearchResultsGUI(DynamicShop plugin, Player player, String query, ItemCategory category) {
        this.plugin = plugin;
        this.player = player;
        this.pm = plugin.getProtocolShopManager();

        String lower = query.toLowerCase();
        this.results = new ArrayList<>();

        // Get items to search through
        List<Material> searchPool;
        if (category != null) {
            // Search only in this category
            searchPool = ShopDataManager.getItemsInCategory(category);
        } else {
            // Search all items
            searchPool = new ArrayList<>();
            for (Material mat : Material.values()) {
                if (mat.isItem() && ShopDataManager.getPrice(mat) >= 0) {
                    searchPool.add(mat);
                }
            }
        }

        // Filter by query (match material name or custom display name)
        for (Material mat : searchPool) {
            String customName = ShopDataManager.getCustomName(mat);
            if (mat.name().toLowerCase().contains(lower)
                    || (customName != null && customName.toLowerCase().contains(lower))) {
                results.add(mat);
            }
        }

        // REGISTER BEFORE OPENING (critical)
        plugin.getShopListener().registerSearch(player, this);
        open();
    }

    public void open() {
        // VALID TITLE SO SHOPLISTENER DETECTS IT
        java.util.Map<String, String> placeholders = new java.util.HashMap<>();
        placeholders.put("count", String.valueOf(results.size()));

        inventory = pm.createVirtualInventory(
                player,
                54,
                plugin.getMessageManager().getMessage("search-gui-title", placeholders));

        render();
        player.openInventory(inventory);

        // AFTER OPEN: update hover-lore
        plugin.getShopListener().updatePlayerInventoryLore(player, 2L);
    }

    // --------------------------------------------------------
    // RENDER GUI
    // --------------------------------------------------------
    public void render() {
        // Clear GUI
        for (int i = 0; i < 54; i++) {
            pm.sendSlot(inventory, i, null);
        }

        // Items
        int limit = Math.min(45, results.size());
        for (int i = 0; i < limit; i++) {
            pm.sendSlot(inventory, i, buildResultItem(results.get(i)));
        }

        // NAVIGATION BACK BUTTON
        ItemStack back = ShopItemBuilder.navItem(
                plugin.getMessageManager().getMessage("search-gui-back-name"),
                Material.BARRIER,
                plugin.getMessageManager().getMessage("search-gui-back-lore"));
        pm.sendSlot(inventory, 49, back);
    }

    private ItemStack buildResultItem(Material mat) {
        // Use template if available (preserves enchantments, custom name, lore, etc.)
        ItemStack template = ShopDataManager.getTemplate(mat);
        ItemStack item = template != null ? template.clone() : new ItemStack(mat);
        if (template != null) item.setAmount(1);

        ItemMeta meta = item.getItemMeta();
        if (meta == null)
            return item;

        // Display name priority: custom_name > template's name > prettified material name
        String customName = ShopDataManager.getCustomName(mat);
        if (customName != null) {
            net.kyori.adventure.text.Component nameComponent = MessageManager.parseComponent("§e§l" + customName);
            org.minecraftsmp.dynamicshop.util.PaperCompat.setDisplayName(meta, nameComponent);
            org.minecraftsmp.dynamicshop.util.PaperCompat.setItemName(meta, nameComponent);
        } else if (!meta.hasDisplayName()) {
            org.minecraftsmp.dynamicshop.util.PaperCompat.setDisplayName(meta, MessageManager.parseComponent("§e§l" + mat.name().replace("_", " ")));
        }

        double buy = ShopDataManager.getTotalBuyCost(mat, 1);
        double sell = ShopDataManager.getTotalSellValue(mat, 1);
        double stock = ShopDataManager.getStock(mat);

        List<String> lore = new ArrayList<>();

        // BUY
        java.util.Map<String, String> buyPlaceholders = new java.util.HashMap<>();
        buyPlaceholders.put("price", plugin.getEconomyManager().format(buy));
        lore.add(plugin.getMessageManager().getMessage("search-lore-buy", buyPlaceholders));

        // SELL
        java.util.Map<String, String> sellPlaceholders = new java.util.HashMap<>();
        sellPlaceholders.put("price", plugin.getEconomyManager().format(sell));
        lore.add(plugin.getMessageManager().getMessage("search-lore-sell", sellPlaceholders));
        lore.add("");

        // STOCK
        if (stock < 0) {
            java.util.Map<String, String> stockPlaceholders = new java.util.HashMap<>();
            stockPlaceholders.put("stock", String.valueOf((int) stock));
            lore.add(plugin.getMessageManager().getMessage("search-lore-stock-negative", stockPlaceholders));
        } else if (stock == 0) {
            lore.add(plugin.getMessageManager().getMessage("search-lore-out-of-stock"));
        } else {
            java.util.Map<String, String> stockPlaceholders = new java.util.HashMap<>();
            stockPlaceholders.put("stock", String.valueOf((int) stock));
            lore.add(plugin.getMessageManager().getMessage("search-lore-stock", stockPlaceholders));
        }

        lore.add("");
        if (ConfigCacheManager.useDialogGui) {
            lore.add(plugin.getMessageManager().getMessage("dialog-lore-click-to-open"));
        } else {
            lore.add(plugin.getMessageManager().getMessage("search-lore-buy-1"));
            lore.add(plugin.getMessageManager().getMessage("search-lore-buy-64"));
            lore.add(plugin.getMessageManager().getMessage("search-lore-sell-1"));
            lore.add(plugin.getMessageManager().getMessage("search-lore-sell-64"));
        }

        org.minecraftsmp.dynamicshop.util.PaperCompat.setLore(meta, lore.stream().map(s -> MessageManager.parseComponent(s)).toList());
        item.setItemMeta(meta);
        return item;
    }

    // --------------------------------------------------------
    // CLICK HANDLING (forwarded from ShopListener)
    // --------------------------------------------------------
    public void handleClick(int slot, boolean rightClick, boolean shiftClick) {

        // BACK button
        if (slot == 49) {
            plugin.getShopListener().unregisterSearch(player);

            CategorySelectionGUI catGUI = new CategorySelectionGUI(plugin, player);
            catGUI.open();
            plugin.getShopListener().registerCategory(player, catGUI);
            return;
        }

        if (slot < 0 || slot >= 45)
            return;
        if (slot >= results.size())
            return;

        Material mat = results.get(slot);

        // Dialog-based buy/sell (configurable)
        if (ConfigCacheManager.useDialogGui && !org.minecraftsmp.dynamicshop.util.BedrockUtil.isBedrock(player)) {
            player.closeInventory();
            plugin.getShopDialogManager().openDialog(player, mat, this);
            return;
        }

        int amount = shiftClick ? 64 : 1;

        if (rightClick) {
            // SELL
            if (shiftClick) {
                int has = plugin.getShopListener().countSellableItems(player, mat, null);
                amount = Math.min(has, 64);
            }

            if (amount <= 0) {
                java.util.Map<String, String> sellPlaceholders = new java.util.HashMap<>();
                sellPlaceholders.put("item", mat.name());
                player.sendMessage(
                        plugin.getMessageManager().getMessage("search-message-no-item-sell", sellPlaceholders));
                return;
            }

            plugin.getShopListener().sellItem(player, mat, amount, this);
        } else {
            // BUY
            plugin.getShopListener().buyItem(player, mat, amount, this);
        }
    }

    public Inventory getInventory() {
        return inventory;
    }
}
