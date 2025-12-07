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
        inventory = pm.createVirtualInventory(
                player,
                54,
                "Â§8Search Results: Â§f" + results.size()
        );

        render();
        player.openInventory(inventory);

        // AFTER OPEN: update hover-lore
        plugin.getShopListener().updatePlayerInventoryLore(player, 2L);
    }

    // --------------------------------------------------------
    //  RENDER GUI
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
                "Â§cÂ§lBack to Categories",
                Material.BARRIER,
                "Â§7Return to main menu"
        );
        pm.sendSlot(inventory, 49, back);
    }

    private ItemStack buildResultItem(Material mat) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        meta.setDisplayName("Â§e" + mat.name().toLowerCase().replace("_", " "));

        double buy = ShopDataManager.getTotalBuyCost(mat, 1);
        double sell = ShopDataManager.getTotalSellValue(mat, 1);
        double stock = ShopDataManager.getStock(mat);

        List<String> lore = new ArrayList<>();

        // BUY
        lore.add("Â§aBuy: Â§f" + plugin.getEconomyManager().format(buy));

        // SELL
        lore.add("Â§cSell: Â§f" + plugin.getEconomyManager().format(sell));
        lore.add("");

        // STOCK
        if (stock < 0) {
            lore.add("Â§cStock: " + (int) stock + " (negative)");
        } else if (stock == 0) {
            lore.add("Â§eOut of stock");
        } else {
            lore.add("Â§7Stock: Â§f" + (int) stock);
        }

        lore.add("");
        lore.add("Â§eLeft-Click: Â§7Buy 1");
        lore.add("Â§eShift+Left: Â§7Buy 64");
        lore.add("Â§cRight-Click: Â§7Sell 1");
        lore.add("Â§cShift+Right: Â§7Sell 64");

        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    // --------------------------------------------------------
    //  CLICK HANDLING (forwarded from ShopListener)
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

        if (slot < 0 || slot >= 45) return;
        if (slot >= results.size()) return;

        Material mat = results.get(slot);
        int amount = shiftClick ? 64 : 1;

        if (rightClick) {
            // SELL
            if (shiftClick) {
                int has = 0;
                for (ItemStack it : player.getInventory().getContents()) {
                    if (it != null && it.getType() == mat) has += it.getAmount();
                }
                amount = Math.min(has, 64);
            }

            if (amount <= 0) {
                player.sendMessage("Â§cYou have no Â§f" + mat.name() + "Â§c to sell!");
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