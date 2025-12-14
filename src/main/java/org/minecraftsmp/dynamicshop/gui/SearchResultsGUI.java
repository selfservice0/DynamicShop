package org.minecraftsmp.dynamicshop.gui;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.minecraftsmp.dynamicshop.DynamicShop;
import org.minecraftsmp.dynamicshop.managers.ProtocolShopManager;
import org.minecraftsmp.dynamicshop.managers.ShopDataManager;
import org.minecraftsmp.dynamicshop.util.ShopItemBuilder;

import java.util.ArrayList;
import java.util.List;

public class SearchResultsGUI {

    private final DynamicShop plugin;
    private final Player player;
    private final ProtocolShopManager pm;
    private final List<Material> results;
    private Inventory inventory;

    public SearchResultsGUI(DynamicShop plugin, Player player, String query) {
        this.plugin = plugin;
        this.player = player;
        this.pm = plugin.getProtocolShopManager();

        String lower = query.toLowerCase();
        this.results = new ArrayList<>();

        for (Material mat : Material.values()) {
            if (mat.isItem() && mat.name().toLowerCase().contains(lower)) {
                if (ShopDataManager.getPrice(mat) >= 0) {
                    results.add(mat);
                }
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
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta == null)
            return item;

        meta.setDisplayName("§e" + mat.name().toLowerCase().replace("_", " "));

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
        lore.add(plugin.getMessageManager().getMessage("search-lore-buy-1"));
        lore.add(plugin.getMessageManager().getMessage("search-lore-buy-64"));
        lore.add(plugin.getMessageManager().getMessage("search-lore-sell-1"));
        lore.add(plugin.getMessageManager().getMessage("search-lore-sell-64"));

        meta.setLore(lore);
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
        int amount = shiftClick ? 64 : 1;

        if (rightClick) {
            // SELL
            if (shiftClick) {
                int has = 0;
                for (ItemStack it : player.getInventory().getContents()) {
                    if (it != null && it.getType() == mat)
                        has += it.getAmount();
                }
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