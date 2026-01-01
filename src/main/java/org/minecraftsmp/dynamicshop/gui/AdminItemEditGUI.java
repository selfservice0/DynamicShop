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
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import java.util.ArrayList;
import java.util.List;

/**
 * Admin GUI for editing individual shop items.
 * Allows adjusting stock, base price, category, and enabled/disabled status.
 * 
 * Uses InputManager for text input (Paper Dialog API or chat fallback).
 */
public class AdminItemEditGUI {

    private final DynamicShop plugin;
    private final Player player;
    private final Material material;
    private final AdminShopBrowseGUI parentGUI;
    private final Inventory inventory;

    private static final int SIZE = 45; // 5 rows

    // Slot positions
    private static final int ITEM_DISPLAY_SLOT = 13; // Center of row 2

    private static final int STOCK_MINUS_64 = 10;
    private static final int STOCK_MINUS_10 = 11;
    private static final int STOCK_MINUS_1 = 12;
    private static final int STOCK_PLUS_1 = 14;
    private static final int STOCK_PLUS_10 = 15;
    private static final int STOCK_PLUS_64 = 16;

    private static final int PRICE_EDIT_SLOT = 20; // Row 3
    private static final int PRICE_INCREASE_SLOT = 21; // Row 3 - price increase %
    private static final int CATEGORY_EDIT_SLOT = 22; // Row 3
    private static final int TOGGLE_ENABLED_SLOT = 24; // Row 3

    private static final int BACK_BUTTON_SLOT = 40; // Bottom center

    public AdminItemEditGUI(DynamicShop plugin, Player player, Material material, AdminShopBrowseGUI parentGUI) {
        this.plugin = plugin;
        this.player = player;
        this.material = material;
        this.parentGUI = parentGUI;

        String itemName = material.name().replace("_", " ");
        this.inventory = Bukkit.createInventory(null, SIZE,
                LegacyComponentSerializer.legacySection().deserialize("§4§lEdit: " + itemName));
    }

    public void open() {
        render();
        player.openInventory(inventory);
    }

    public void render() {
        inventory.clear();

        // Fill with glass panes
        ItemStack filler = createFiller();
        for (int i = 0; i < SIZE; i++) {
            inventory.setItem(i, filler);
        }

        // Item display in center
        inventory.setItem(ITEM_DISPLAY_SLOT, createItemDisplay());

        // Stock adjustment buttons
        inventory.setItem(STOCK_MINUS_64, createStockButton(-64));
        inventory.setItem(STOCK_MINUS_10, createStockButton(-10));
        inventory.setItem(STOCK_MINUS_1, createStockButton(-1));
        inventory.setItem(STOCK_PLUS_1, createStockButton(1));
        inventory.setItem(STOCK_PLUS_10, createStockButton(10));
        inventory.setItem(STOCK_PLUS_64, createStockButton(64));

        // Action buttons
        inventory.setItem(PRICE_EDIT_SLOT, createPriceButton());
        inventory.setItem(PRICE_INCREASE_SLOT, createPriceIncreaseButton());
        inventory.setItem(CATEGORY_EDIT_SLOT, createCategoryButton());
        inventory.setItem(TOGGLE_ENABLED_SLOT, createToggleButton());

        // Back button
        inventory.setItem(BACK_BUTTON_SLOT, createBackButton());
    }

    private ItemStack createItemDisplay() {
        double basePrice = ShopDataManager.getBasePrice(material);
        double stock = ShopDataManager.getStock(material);
        boolean disabled = ShopDataManager.isItemDisabled(material);
        ItemCategory category = ShopDataManager.detectCategory(material);

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(
                    LegacyComponentSerializer.legacySection().deserialize("§e§l" + material.name().replace("_", " ")));

            List<String> lore = new ArrayList<>();
            lore.add("§7═══════════════════════");
            lore.add("§7Base Price: §e$" + String.format("%.2f", basePrice));
            lore.add("§7Current Stock: §f" + String.format("%.0f", stock));
            lore.add("§7Category: §b" + category.getDisplayName());
            lore.add("§7Status: " + (disabled ? "§c✗ DISABLED" : "§a✓ ENABLED"));
            lore.add("§7═══════════════════════");

            meta.lore(lore.stream().map(s -> LegacyComponentSerializer.legacySection().deserialize(s)).toList());
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createStockButton(int delta) {
        Material mat = delta > 0 ? Material.LIME_WOOL : Material.RED_WOOL;
        String prefix = delta > 0 ? "§a+" : "§c";

        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(LegacyComponentSerializer.legacySection().deserialize(prefix + delta + " Stock"));

            List<String> lore = new ArrayList<>();
            double currentStock = ShopDataManager.getStock(material);
            lore.add("§7Current: §f" + String.format("%.0f", currentStock));
            lore.add("§7After: §f" + String.format("%.0f", currentStock + delta));
            lore.add("");
            lore.add("§eClick to adjust");

            meta.lore(lore.stream().map(s -> LegacyComponentSerializer.legacySection().deserialize(s)).toList());
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createPriceButton() {
        ItemStack item = new ItemStack(Material.GOLD_INGOT);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(LegacyComponentSerializer.legacySection().deserialize("§e§lChange Base Price"));

            List<String> lore = new ArrayList<>();
            double currentPrice = ShopDataManager.getBasePrice(material);
            lore.add("§7Current: §e$" + String.format("%.2f", currentPrice));
            lore.add("");
            lore.add("§eClick to change");

            meta.lore(lore.stream().map(s -> LegacyComponentSerializer.legacySection().deserialize(s)).toList());
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createPriceIncreaseButton() {
        long lastUpdate = ShopDataManager.getLastUpdate(material);
        long hours = (System.currentTimeMillis() - lastUpdate) / (3600 * 1000);
        long priceIncrease = hours * 2; // 2% per hour

        ItemStack item = new ItemStack(Material.CLOCK);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(LegacyComponentSerializer.legacySection().deserialize("§6§lPrice Increase %"));

            List<String> lore = new ArrayList<>();
            lore.add("§7Hours since update: §f" + hours);
            lore.add("§7Current increase: §c+" + priceIncrease + "%");
            lore.add("");
            lore.add("§7This increases buy price when");
            lore.add("§7stock is at 0 or negative.");
            lore.add("");
            lore.add("§eClick to set % (resets timer)");

            meta.lore(lore.stream().map(s -> LegacyComponentSerializer.legacySection().deserialize(s)).toList());
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createCategoryButton() {
        ItemCategory current = ShopDataManager.detectCategory(material);

        ItemStack item = new ItemStack(org.minecraftsmp.dynamicshop.managers.CategoryConfigManager.getIcon(current));
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(LegacyComponentSerializer.legacySection().deserialize("§b§lChange Category"));

            List<String> lore = new ArrayList<>();
            lore.add("§7Current: §b"
                    + org.minecraftsmp.dynamicshop.managers.CategoryConfigManager.getDisplayName(current));
            lore.add("");
            lore.add("§eClick to cycle");

            meta.lore(lore.stream().map(s -> LegacyComponentSerializer.legacySection().deserialize(s)).toList());
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createToggleButton() {
        boolean disabled = ShopDataManager.isItemDisabled(material);

        Material mat = disabled ? Material.RED_CONCRETE : Material.LIME_CONCRETE;
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(
                    LegacyComponentSerializer.legacySection().deserialize(disabled ? "§c§lDISABLED" : "§a§lENABLED"));

            List<String> lore = new ArrayList<>();
            if (disabled) {
                lore.add("§7This item is §chidden§7 from the shop");
                lore.add("");
                lore.add("§aClick to ENABLE");
            } else {
                lore.add("§7This item is §avisible§7 in the shop");
                lore.add("");
                lore.add("§cClick to DISABLE");
            }

            meta.lore(lore.stream().map(s -> LegacyComponentSerializer.legacySection().deserialize(s)).toList());
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createBackButton() {
        ItemStack item = new ItemStack(Material.ARROW);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(LegacyComponentSerializer.legacySection().deserialize("§c§l◀ Back"));
            List<String> lore = new ArrayList<>();
            lore.add("§7Return to item browser");
            meta.lore(lore.stream().map(s -> LegacyComponentSerializer.legacySection().deserialize(s)).toList());
            item.setItemMeta(meta);
        }
        return item;
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

    /**
     * Handle click events in this GUI
     */
    public void handleClick(int slot) {
        switch (slot) {
            // Stock adjustments
            case STOCK_MINUS_64 -> adjustStock(-64);
            case STOCK_MINUS_10 -> adjustStock(-10);
            case STOCK_MINUS_1 -> adjustStock(-1);
            case STOCK_PLUS_1 -> adjustStock(1);
            case STOCK_PLUS_10 -> adjustStock(10);
            case STOCK_PLUS_64 -> adjustStock(64);

            // Actions
            case PRICE_EDIT_SLOT -> openPriceEditor();
            case PRICE_INCREASE_SLOT -> openPriceIncreaseEditor();
            case CATEGORY_EDIT_SLOT -> cycleCategory();
            case TOGGLE_ENABLED_SLOT -> toggleEnabled();
            case BACK_BUTTON_SLOT -> goBack();
        }
    }

    private void adjustStock(int delta) {
        ShopDataManager.updateStock(material, delta);
        player.sendMessage("§a✓ §7Stock adjusted by §e" + (delta > 0 ? "+" : "") + delta);
        render();
    }

    private void openPriceEditor() {
        double currentPrice = ShopDataManager.getBasePrice(material);

        plugin.getInputManager().requestNumber(player,
                "Enter new base price for " + material.name(),
                currentPrice,
                newPrice -> {
                    if (newPrice != null) {
                        if (newPrice < 0) {
                            player.sendMessage("§c✗ §7Price cannot be negative!");
                        } else {
                            ShopDataManager.setBasePrice(material, newPrice);
                            player.sendMessage("§a✓ §7Base price set to §e$" + String.format("%.2f", newPrice));
                        }
                    }
                    // Reopen this GUI with a NEW instance to avoid stale onClose handler
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        AdminItemEditGUI newGUI = new AdminItemEditGUI(plugin, player, material, parentGUI);
                        plugin.getShopListener().registerAdminEdit(player, newGUI);
                        newGUI.open();
                    });
                });
    }

    private void openPriceIncreaseEditor() {
        long hours = (System.currentTimeMillis() - ShopDataManager.getLastUpdate(material)) / (3600 * 1000);
        int currentPercent = (int) (hours * 2);

        plugin.getInputManager().requestInt(player,
                "Enter price increase % for " + material.name(),
                currentPercent,
                percent -> {
                    if (percent != null) {
                        if (percent < 0) {
                            player.sendMessage("§c✗ §7Percentage cannot be negative!");
                        } else {
                            // Calculate the timestamp that would result in this percentage
                            long hoursNeeded = percent / 2;
                            long newLastUpdate = System.currentTimeMillis() - (hoursNeeded * 3600 * 1000);
                            ShopDataManager.setLastUpdate(material, newLastUpdate);
                            player.sendMessage("§a✓ §7Price increase set to §c+" + percent + "%");
                        }
                    }
                    // Reopen this GUI with a NEW instance to avoid stale onClose handler
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        AdminItemEditGUI newGUI = new AdminItemEditGUI(plugin, player, material, parentGUI);
                        plugin.getShopListener().registerAdminEdit(player, newGUI);
                        newGUI.open();
                    });
                });
    }

    private void cycleCategory() {
        ItemCategory current = ShopDataManager.detectCategory(material);
        ItemCategory[] categories = ItemCategory.values();
        int currentIndex = current.ordinal();

        // Find next valid category (skip special categories, but include visible custom
        // categories)
        do {
            currentIndex = (currentIndex + 1) % categories.length;
        } while (categories[currentIndex] == ItemCategory.PERMISSIONS ||
                categories[currentIndex] == ItemCategory.SERVER_SHOP ||
                categories[currentIndex] == ItemCategory.PLAYER_SHOPS ||
                org.minecraftsmp.dynamicshop.managers.CategoryConfigManager.getSlot(categories[currentIndex]) < 0);

        ItemCategory newCategory = categories[currentIndex];
        ShopDataManager.setCategoryOverride(material, newCategory);
        player.sendMessage("§a✓ §7Category changed to §b"
                + org.minecraftsmp.dynamicshop.managers.CategoryConfigManager.getDisplayName(newCategory));
        render();
    }

    private void toggleEnabled() {
        boolean currentlyDisabled = ShopDataManager.isItemDisabled(material);
        ShopDataManager.setItemDisabled(material, !currentlyDisabled);

        if (currentlyDisabled) {
            player.sendMessage("§a✓ §7Item §aenabled§7 in the shop");
        } else {
            player.sendMessage("§a✓ §7Item §cdisabled§7 from the shop");
        }
        render();
    }

    private void goBack() {
        // Return to parent browse GUI
        plugin.getShopListener().unregisterAdminEdit(player);
        plugin.getShopListener().registerAdminBrowse(player, parentGUI);
        parentGUI.render();
        player.openInventory(parentGUI.getInventory());
    }

    public Inventory getInventory() {
        return inventory;
    }

    public Material getMaterial() {
        return material;
    }
}
