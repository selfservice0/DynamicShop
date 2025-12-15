package org.minecraftsmp.dynamicshop.gui;

import net.wesjd.anvilgui.AnvilGUI;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.minecraftsmp.dynamicshop.DynamicShop;
import org.minecraftsmp.dynamicshop.managers.ConfigCacheManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Admin GUI for editing plugin configuration options.
 * Changes are saved to config.yml and take effect immediately.
 */
public class AdminConfigGUI {

    private final DynamicShop plugin;
    private final Player player;
    private final AdminShopBrowseGUI parentGUI;
    private final Inventory inventory;

    private static final int SIZE = 54; // 6 rows

    // Slot positions - organized by category
    // Row 1: Dynamic Pricing
    private static final int DYNAMIC_PRICING_ENABLED = 10;
    private static final int USE_STOCK_CURVE = 11;
    private static final int CURVE_STRENGTH = 12;
    private static final int MAX_STOCK = 13;
    private static final int RESTRICT_BUYING = 14;
    private static final int USE_TIME_INFLATION = 15;
    private static final int HOURLY_INCREASE = 16;

    // Row 2: More pricing options
    private static final int MIN_PRICE_MULT = 19;
    private static final int MAX_PRICE_MULT = 20;
    private static final int SELL_TAX = 21;
    private static final int USE_DEMAND = 22;
    private static final int DEMAND_FACTOR = 23;
    private static final int MAX_DEMAND = 24;

    // Row 3: Player Shops
    private static final int PLAYER_SHOPS_ENABLED = 28;
    private static final int MAX_LISTINGS = 29;
    private static final int CROSS_SERVER_ENABLED = 31;
    private static final int CROSS_SERVER_INTERVAL = 32;

    // Row 5: Back button
    private static final int BACK_BUTTON = 49;

    public AdminConfigGUI(DynamicShop plugin, Player player, AdminShopBrowseGUI parentGUI) {
        this.plugin = plugin;
        this.player = player;
        this.parentGUI = parentGUI;
        this.inventory = Bukkit.createInventory(null, SIZE, "§4§lConfig Editor");
    }

    public void open() {
        render();
        player.openInventory(inventory);
    }

    public void render() {
        inventory.clear();

        // Fill with glass
        ItemStack filler = createFiller();
        for (int i = 0; i < SIZE; i++) {
            inventory.setItem(i, filler);
        }

        // Dynamic Pricing section
        inventory.setItem(DYNAMIC_PRICING_ENABLED, createToggleItem("Dynamic Pricing",
                ConfigCacheManager.dynamicPricingEnabled, "dynamic-pricing.enabled"));
        inventory.setItem(USE_STOCK_CURVE, createToggleItem("Use Stock Curve",
                ConfigCacheManager.useStockCurve, "dynamic-pricing.use-stock-curve"));
        inventory.setItem(CURVE_STRENGTH, createNumberItem("Curve Strength",
                ConfigCacheManager.curveStrength, "dynamic-pricing.curve-strength", Material.BLAZE_ROD));
        inventory.setItem(MAX_STOCK, createNumberItem("Max Stock",
                ConfigCacheManager.maxStock, "dynamic-pricing.max-stock", Material.CHEST));
        inventory.setItem(RESTRICT_BUYING, createToggleItem("Restrict Buy at 0 Stock",
                ConfigCacheManager.restrictBuyingAtZeroStock, "dynamic-pricing.restrict-buying-at-zero-stock"));
        inventory.setItem(USE_TIME_INFLATION, createToggleItem("Use Time Inflation",
                ConfigCacheManager.useTimeInflation, "dynamic-pricing.use-time-inflation"));
        inventory.setItem(HOURLY_INCREASE, createNumberItem("Hourly Increase %",
                ConfigCacheManager.hourlyIncreasePercent, "dynamic-pricing.hourly-increase-percent", Material.CLOCK));

        // More pricing options
        inventory.setItem(MIN_PRICE_MULT, createNumberItem("Min Price Multiplier",
                ConfigCacheManager.minPriceMultiplier, "dynamic-pricing.min-price-multiplier", Material.IRON_NUGGET));
        inventory.setItem(MAX_PRICE_MULT, createNumberItem("Max Price Multiplier",
                ConfigCacheManager.maxPriceMultiplier, "dynamic-pricing.max-price-multiplier", Material.GOLD_NUGGET));
        inventory.setItem(SELL_TAX, createNumberItem("Sell Tax %",
                ConfigCacheManager.sellTaxPercent * 100, "economy.sell_tax_percent", Material.PAPER));
        inventory.setItem(USE_DEMAND, createToggleItem("Use Demand",
                ConfigCacheManager.useDemand, "dynamic-pricing.use-demand"));
        inventory.setItem(DEMAND_FACTOR, createNumberItem("Demand Factor",
                ConfigCacheManager.demandFactor, "dynamic-pricing.demand-factor", Material.COMPASS));
        inventory.setItem(MAX_DEMAND, createNumberItem("Max Demand",
                ConfigCacheManager.maxDemand, "dynamic-pricing.max-demand", Material.EXPERIENCE_BOTTLE));

        // Player Shops
        inventory.setItem(PLAYER_SHOPS_ENABLED, createToggleItem("Player Shops Enabled",
                ConfigCacheManager.playerShopsEnabled, "player-shops.enabled"));
        inventory.setItem(MAX_LISTINGS, createIntItem("Max Listings/Player",
                ConfigCacheManager.maxListingsPerPlayer, "player-shops.max-listings-per-player", Material.CHEST));
        inventory.setItem(CROSS_SERVER_ENABLED, createToggleItem("Cross-Server Sync",
                ConfigCacheManager.crossServerEnabled, "cross-server.enabled"));
        inventory.setItem(CROSS_SERVER_INTERVAL, createIntItem("Sync Interval (sec)",
                ConfigCacheManager.crossServerSaveInterval, "cross-server.save-interval-seconds",
                Material.ENDER_PEARL));

        // Back button
        inventory.setItem(BACK_BUTTON, createBackButton());
    }

    private ItemStack createToggleItem(String name, boolean currentValue, String configPath) {
        Material mat = currentValue ? Material.LIME_DYE : Material.GRAY_DYE;
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName((currentValue ? "§a" : "§c") + "§l" + name);
            List<String> lore = new ArrayList<>();
            lore.add("§7Status: " + (currentValue ? "§aENABLED" : "§cDISABLED"));
            lore.add("§7Config: §f" + configPath);
            lore.add("");
            lore.add("§eClick to toggle");
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createNumberItem(String name, double currentValue, String configPath, Material icon) {
        ItemStack item = new ItemStack(icon);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§e§l" + name);
            List<String> lore = new ArrayList<>();
            lore.add("§7Value: §f" + String.format("%.2f", currentValue));
            lore.add("§7Config: §f" + configPath);
            lore.add("");
            lore.add("§eClick to change");
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createIntItem(String name, int currentValue, String configPath, Material icon) {
        ItemStack item = new ItemStack(icon);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§e§l" + name);
            List<String> lore = new ArrayList<>();
            lore.add("§7Value: §f" + currentValue);
            lore.add("§7Config: §f" + configPath);
            lore.add("");
            lore.add("§eClick to change");
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createBackButton() {
        ItemStack item = new ItemStack(Material.ARROW);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§c§l◀ Back");
            List<String> lore = new ArrayList<>();
            lore.add("§7Return to admin shop");
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createFiller() {
        ItemStack filler = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta meta = filler.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(" ");
            filler.setItemMeta(meta);
        }
        return filler;
    }

    public void handleClick(int slot) {
        switch (slot) {
            // Toggle options
            case DYNAMIC_PRICING_ENABLED -> toggleBoolean("dynamic-pricing.enabled");
            case USE_STOCK_CURVE -> toggleBoolean("dynamic-pricing.use-stock-curve");
            case RESTRICT_BUYING -> toggleBoolean("dynamic-pricing.restrict-buying-at-zero-stock");
            case USE_TIME_INFLATION -> toggleBoolean("dynamic-pricing.use-time-inflation");
            case USE_DEMAND -> toggleBoolean("dynamic-pricing.use-demand");
            case PLAYER_SHOPS_ENABLED -> toggleBoolean("player-shops.enabled");
            case CROSS_SERVER_ENABLED -> toggleBoolean("cross-server.enabled");

            // Number options
            case CURVE_STRENGTH -> editDouble("Curve Strength", "dynamic-pricing.curve-strength");
            case MAX_STOCK -> editDouble("Max Stock", "dynamic-pricing.max-stock");
            case HOURLY_INCREASE -> editDouble("Hourly Increase %", "dynamic-pricing.hourly-increase-percent");
            case MIN_PRICE_MULT -> editDouble("Min Price Multiplier", "dynamic-pricing.min-price-multiplier");
            case MAX_PRICE_MULT -> editDouble("Max Price Multiplier", "dynamic-pricing.max-price-multiplier");
            case SELL_TAX -> editDouble("Sell Tax %", "economy.sell_tax_percent");
            case DEMAND_FACTOR -> editDouble("Demand Factor", "dynamic-pricing.demand-factor");
            case MAX_DEMAND -> editDouble("Max Demand", "dynamic-pricing.max-demand");

            // Integer options
            case MAX_LISTINGS -> editInt("Max Listings", "player-shops.max-listings-per-player");
            case CROSS_SERVER_INTERVAL -> editInt("Sync Interval", "cross-server.save-interval-seconds");

            // Back button
            case BACK_BUTTON -> goBack();
        }
    }

    private void toggleBoolean(String configPath) {
        boolean current = plugin.getConfig().getBoolean(configPath, false);
        boolean newValue = !current;

        plugin.getConfig().set(configPath, newValue);
        plugin.saveConfig();
        ConfigCacheManager.reload();

        player.sendMessage("§a✓ §7" + configPath + " set to §e" + newValue);
        render();
    }

    private void editDouble(String name, String configPath) {
        player.closeInventory();
        double current = plugin.getConfig().getDouble(configPath, 0);

        new AnvilGUI.Builder()
                .title("§8Edit " + name)
                .text(String.format("%.2f", current))
                .itemLeft(new ItemStack(Material.PAPER))
                .onClick((clickSlot, state) -> {
                    try {
                        double value = Double.parseDouble(state.getText().trim());
                        plugin.getConfig().set(configPath, value);
                        plugin.saveConfig();
                        ConfigCacheManager.reload();
                        player.sendMessage("§a✓ §7" + configPath + " set to §e" + value);

                        // Reopen config GUI
                        Bukkit.getScheduler().runTaskLater(plugin, () -> {
                            plugin.getShopListener().registerAdminConfig(player, this);
                            open();
                        }, 2L);
                    } catch (NumberFormatException e) {
                        player.sendMessage("§c✗ §7Invalid number!");
                    }
                    return Arrays.asList(AnvilGUI.ResponseAction.close());
                })
                .plugin(plugin)
                .open(player);
    }

    private void editInt(String name, String configPath) {
        player.closeInventory();
        int current = plugin.getConfig().getInt(configPath, 0);

        new AnvilGUI.Builder()
                .title("§8Edit " + name)
                .text(String.valueOf(current))
                .itemLeft(new ItemStack(Material.PAPER))
                .onClick((clickSlot, state) -> {
                    try {
                        int value = Integer.parseInt(state.getText().trim());
                        plugin.getConfig().set(configPath, value);
                        plugin.saveConfig();
                        ConfigCacheManager.reload();
                        player.sendMessage("§a✓ §7" + configPath + " set to §e" + value);

                        // Reopen config GUI
                        Bukkit.getScheduler().runTaskLater(plugin, () -> {
                            plugin.getShopListener().registerAdminConfig(player, this);
                            open();
                        }, 2L);
                    } catch (NumberFormatException e) {
                        player.sendMessage("§c✗ §7Invalid number!");
                    }
                    return Arrays.asList(AnvilGUI.ResponseAction.close());
                })
                .plugin(plugin)
                .open(player);
    }

    private void goBack() {
        plugin.getShopListener().unregisterAdminConfig(player);
        plugin.getShopListener().registerAdminBrowse(player, parentGUI);
        parentGUI.render();
        player.openInventory(parentGUI.getInventory());
    }

    public Inventory getInventory() {
        return inventory;
    }
}
