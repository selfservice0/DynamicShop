package org.minecraftsmp.dynamicshop.gui;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.minecraftsmp.dynamicshop.DynamicShop;
import org.minecraftsmp.dynamicshop.managers.ShopDataManager;
import org.minecraftsmp.dynamicshop.managers.MessageManager;

import java.util.ArrayList;
import java.util.List;

/**
 * A small confirmation GUI for Bedrock players who cannot right-click or shift-click.
 * Shows explicit Buy ×1, Buy ×64, Sell ×1, Sell ×64 buttons.
 */
public class ItemActionGUI {

    private final DynamicShop plugin;
    private final Player player;
    private final Material targetItem;
    private final ShopGUI parentShop;
    private final ItemStack deliveryOverride;
    private final double variantBasePrice;
    private final String variantId;
    private final Inventory inventory;

    // Slot layout (27-slot / 3 rows):
    // Row 0: [filler] [Buy×1] [filler] [filler] [ITEM] [filler] [filler] [Sell×1] [filler]
    // Row 1: [filler] [Buy×64][filler] [filler] [info ] [filler] [filler] [Sell×64][filler]
    // Row 2: [filler] [filler][filler] [filler] [BACK ] [filler] [filler] [filler] [filler]
    private static final int SLOT_BUY_1 = 1;
    private static final int SLOT_BUY_64 = 10;
    private static final int SLOT_ITEM_DISPLAY = 4;
    private static final int SLOT_INFO = 13;
    private static final int SLOT_SELL_1 = 7;
    private static final int SLOT_SELL_64 = 16;
    private static final int SLOT_BACK = 22;
    private static final int SIZE = 27;

    public ItemActionGUI(DynamicShop plugin, Player player, Material targetItem, ShopGUI parentShop) {
        this(plugin, player, targetItem, parentShop, null, -1, null);
    }

    public ItemActionGUI(DynamicShop plugin, Player player, Material targetItem, ShopGUI parentShop,
                         ItemStack deliveryOverride, double variantBasePrice, String variantId) {
        this.plugin = plugin;
        this.player = player;
        this.targetItem = targetItem;
        this.parentShop = parentShop;
        this.deliveryOverride = deliveryOverride != null ? deliveryOverride.clone() : null;
        this.variantBasePrice = variantBasePrice;
        this.variantId = variantId;

        String title = plugin.getMessageManager().getMessage("item-action-title");
        if (title == null) title = "§8Buy / Sell";
        this.inventory = org.minecraftsmp.dynamicshop.util.PaperCompat.createInventory(null, SIZE,
                MessageManager.parseComponent(title));
    }

    public void open() {
        render();
        player.openInventory(inventory);
    }

    private void render() {
        // Fill with glass panes
        ItemStack filler = createFiller();
        for (int i = 0; i < SIZE; i++) {
            inventory.setItem(i, filler);
        }

        // Display item in center with price/stock info
        inventory.setItem(SLOT_ITEM_DISPLAY, buildDisplayItem());

        // Price info
        inventory.setItem(SLOT_INFO, buildInfoItem());

        // Buy buttons
        inventory.setItem(SLOT_BUY_1, buildActionButton(
                "item-action-buy-1", "§a§lBuy ×1", Material.LIME_CONCRETE,
                plugin.getEconomyManager().format(getBuyCost(1))));
        inventory.setItem(SLOT_BUY_64, buildActionButton(
                "item-action-buy-64", "§a§lBuy ×64", Material.LIME_CONCRETE,
                plugin.getEconomyManager().format(getBuyCost(64))));

        // Sell buttons
        inventory.setItem(SLOT_SELL_1, buildActionButton(
                "item-action-sell-1", "§c§lSell ×1", Material.RED_CONCRETE,
                plugin.getEconomyManager().format(getSellValue(1))));
        inventory.setItem(SLOT_SELL_64, buildActionButton(
                "item-action-sell-64", "§c§lSell ×64", Material.RED_CONCRETE,
                plugin.getEconomyManager().format(getSellValue(64))));

        // Back button
        String backName = plugin.getMessageManager().getMessage("item-action-back");
        if (backName == null) backName = "§7§l← Back";
        ItemStack back = new ItemStack(Material.ARROW);
        ItemMeta backMeta = back.getItemMeta();
        if (backMeta != null) {
            org.minecraftsmp.dynamicshop.util.PaperCompat.setDisplayName(backMeta, MessageManager.parseComponent(backName));
            back.setItemMeta(backMeta);
        }
        inventory.setItem(SLOT_BACK, back);
    }

    private ItemStack buildDisplayItem() {
        ItemStack item = deliveryOverride != null ? deliveryOverride.clone() : ShopDataManager.getTemplate(targetItem);
        if (item == null) {
            item = new ItemStack(targetItem, 1);
        } else {
            item.setAmount(1);
        }
        ItemMeta meta = item.getItemMeta();
        if (meta != null && !meta.hasDisplayName()) {
            org.minecraftsmp.dynamicshop.util.PaperCompat.setDisplayName(meta, MessageManager.parseComponent("§e§l" + targetItem.name().replace("_", " ")));
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack buildInfoItem() {
        double buyPrice = getBuyCost(1);
        double sellPrice = getSellValue(1);
        double stock = getStock();

        ItemStack info = new ItemStack(Material.PAPER);
        ItemMeta meta = info.getItemMeta();
        if (meta != null) {
            org.minecraftsmp.dynamicshop.util.PaperCompat.setDisplayName(meta, MessageManager.parseComponent("§e§lItem Info"));

            List<String> lore = new ArrayList<>();
            lore.add("§7Buy: §a" + plugin.getEconomyManager().format(buyPrice));
            lore.add("§7Sell: §c" + plugin.getEconomyManager().format(sellPrice));
            if (stock <= 0) {
                lore.add("§7Stock: §c" + String.format("%.0f", stock));
                
                // Show price increase if out of stock
                double hours = variantId != null
                        ? ShopDataManager.getVariantShortageHours(variantId)
                        : ShopDataManager.getHoursInShortage(targetItem);
                double percentIncrease = ShopDataManager.getInflationIncreasePercent(hours);
                double maxPercent = (org.minecraftsmp.dynamicshop.managers.ConfigCacheManager.maxPriceMultiplier - 1.0) * 100.0;
                boolean capped = percentIncrease >= maxPercent;
                if (capped) percentIncrease = maxPercent;

                java.util.Map<String, String> percentPlaceholders = new java.util.HashMap<>();
                percentPlaceholders.put("percent", String.format("%,.0f", percentIncrease) + (capped ? " (MAX)" : ""));
                percentPlaceholders.put("hourly_rate", String.format("%.1f", org.minecraftsmp.dynamicshop.managers.ConfigCacheManager.hourlyIncreasePercent));
                MessageManager.addLoreIfNotEmpty(lore,
                        plugin.getMessageManager().getMessage("shop-lore-price-increase", percentPlaceholders));
                MessageManager.addLoreIfNotEmpty(lore,
                        plugin.getMessageManager().getMessage("shop-lore-price-increase-note", percentPlaceholders));

            } else {
                lore.add("§7Stock: §a" + String.format("%.0f", stock));
            }

            org.minecraftsmp.dynamicshop.util.PaperCompat.setLore(meta, lore.stream()
                    .map(s -> MessageManager.parseComponent(s))
                    .toList());
            info.setItemMeta(meta);
        }
        return info;
    }

    private ItemStack buildActionButton(String messageKey, String fallback, Material material, String price) {
        String name = plugin.getMessageManager().getMessage(messageKey);
        if (name == null) name = fallback;

        ItemStack button = new ItemStack(material);
        ItemMeta meta = button.getItemMeta();
        if (meta != null) {
            org.minecraftsmp.dynamicshop.util.PaperCompat.setDisplayName(meta, MessageManager.parseComponent(name));

            List<String> lore = new ArrayList<>();
            lore.add("§7Price: §e" + price);
            org.minecraftsmp.dynamicshop.util.PaperCompat.setLore(meta, lore.stream()
                    .map(s -> MessageManager.parseComponent(s))
                    .toList());

            button.setItemMeta(meta);
        }
        return button;
    }

    private double getBuyCost(int amount) {
        if (variantId != null && variantBasePrice > 0) {
            return ShopDataManager.getTotalVariantBuyCost(variantId, targetItem, variantBasePrice, amount);
        }
        return ShopDataManager.getTotalBuyCost(targetItem, amount);
    }

    private double getSellValue(int amount) {
        if (variantId != null && variantBasePrice > 0) {
            return ShopDataManager.getTotalVariantSellValue(variantId, targetItem, variantBasePrice, amount);
        }
        return ShopDataManager.getTotalSellValue(targetItem, amount);
    }

    private double getStock() {
        return variantId != null
                ? ShopDataManager.getVariantStock(variantId)
                : ShopDataManager.getStock(targetItem);
    }

    private ItemStack createFiller() {
        return org.minecraftsmp.dynamicshop.managers.ConfigCacheManager.getFillerItem();
    }

    /**
     * Handle a click in this GUI. Returns true if the click was handled.
     */
    public void handleClick(Player p, int slot) {
        switch (slot) {
            case SLOT_BUY_1 -> {
                plugin.getShopListener().buyItem(p, targetItem, 1, parentShop,
                        deliveryOverride, variantBasePrice, variantId);
                // Re-render to update prices after transaction
                render();
            }
            case SLOT_BUY_64 -> {
                plugin.getShopListener().buyItem(p, targetItem, 64, parentShop,
                        deliveryOverride, variantBasePrice, variantId);
                render();
            }
            case SLOT_SELL_1 -> {
                plugin.getShopListener().sellItem(p, targetItem, 1, parentShop,
                        deliveryOverride, variantBasePrice, variantId);
                render();
            }
            case SLOT_SELL_64 -> {
                plugin.getShopListener().sellItem(p, targetItem, 64, parentShop,
                        deliveryOverride, variantBasePrice, variantId);
                render();
            }
            case SLOT_BACK -> {
                // Return to parent shop
                p.closeInventory();
                parentShop.open();
            }
        }
    }

    public Inventory getInventory() {
        return inventory;
    }

    public ShopGUI getParentShop() {
        return parentShop;
    }
}
