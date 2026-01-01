package org.minecraftsmp.dynamicshop.gui;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.minecraftsmp.dynamicshop.DynamicShop;
import org.minecraftsmp.dynamicshop.managers.ConfigCacheManager;

import java.util.ArrayList;
import java.util.List;

/**
 * Admin GUI for editing plugin configuration options.
 * Changes are saved to config.yml and take effect immediately.
 * 
 * Uses InputManager for Paper 1.21+ compatibility.
 */
public class AdminConfigGUI {

    private final DynamicShop plugin;
    private final Player player;
    private final AdminShopBrowseGUI parentGUI;
    private final Inventory inventory;

    private static final int SIZE = 54; // 6 rows

    // Slot positions - organized by category
    // Row 1: Dynamic Pricing Core
    private static final int DYNAMIC_PRICING_ENABLED = 10;
    private static final int USE_STOCK_CURVE = 11;
    private static final int CURVE_STRENGTH = 12;
    private static final int MAX_STOCK = 13;
    private static final int RESTRICT_BUYING = 14;
    private static final int NEGATIVE_STOCK_PERCENT = 15;
    private static final int LOG_DYNAMIC_PRICING = 16;

    // Row 2: Time Inflation & Price Limits
    private static final int USE_TIME_INFLATION = 19;
    private static final int HOURLY_INCREASE = 20;
    private static final int MIN_PRICE_MULT = 21;
    private static final int MAX_PRICE_MULT = 22;
    private static final int SELL_TAX = 23;
    private static final int TRANSACTION_COOLDOWN = 24;

    // Row 3: Player Shops & Cross-Server
    private static final int PLAYER_SHOPS_ENABLED = 28;
    private static final int MAX_LISTINGS = 29;
    private static final int CROSS_SERVER_ENABLED = 31;
    private static final int CROSS_SERVER_INTERVAL = 32;

    // Row 4: Web Server & GUI
    private static final int WEBSERVER_ENABLED = 37;
    private static final int WEBSERVER_PORT = 38;
    private static final int GUI_MENU_SIZE = 40;
    private static final int INPUT_METHOD = 42;

    // Row 5: Back button
    private static final int BACK_BUTTON = 49;

    public AdminConfigGUI(DynamicShop plugin, Player player, AdminShopBrowseGUI parentGUI) {
        this.plugin = plugin;
        this.player = player;
        this.parentGUI = parentGUI;
        this.inventory = Bukkit.createInventory(null, SIZE,
                LegacyComponentSerializer.legacySection().deserialize("§4§lConfig Editor"));
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

        // === Row 1: Dynamic Pricing Core ===
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
        inventory.setItem(NEGATIVE_STOCK_PERCENT, createNumberItem("Negative Stock %/item",
                ConfigCacheManager.negativeStockPercentPerItem, "dynamic-pricing.negative-stock-percent-per-item",
                Material.BARRIER));
        inventory.setItem(LOG_DYNAMIC_PRICING, createToggleItem("Log Pricing Debug",
                ConfigCacheManager.logDynamicPricing, "dynamic-pricing.log-dynamic-pricing"));

        // === Row 2: Time Inflation & Price Limits ===
        inventory.setItem(USE_TIME_INFLATION, createToggleItem("Use Time Inflation",
                ConfigCacheManager.useTimeInflation, "dynamic-pricing.use-time-inflation"));
        inventory.setItem(HOURLY_INCREASE, createNumberItem("Hourly Increase %",
                ConfigCacheManager.hourlyIncreasePercent, "dynamic-pricing.hourly-increase-percent", Material.CLOCK));
        inventory.setItem(MIN_PRICE_MULT, createNumberItem("Min Price Multiplier",
                ConfigCacheManager.minPriceMultiplier, "dynamic-pricing.min-price-multiplier", Material.IRON_NUGGET));
        inventory.setItem(MAX_PRICE_MULT, createNumberItem("Max Price Multiplier",
                ConfigCacheManager.maxPriceMultiplier, "dynamic-pricing.max-price-multiplier", Material.GOLD_NUGGET));
        inventory.setItem(SELL_TAX, createNumberItem("Sell Tax %",
                ConfigCacheManager.sellTaxPercent * 100, "economy.sell_tax_percent", Material.PAPER));
        inventory.setItem(TRANSACTION_COOLDOWN, createIntItem("Transaction Cooldown (ms)",
                (int) ConfigCacheManager.transactionCooldownMs, "economy.transaction_cooldown_ms", Material.HOPPER));

        // === Row 3: Player Shops & Cross-Server ===
        inventory.setItem(PLAYER_SHOPS_ENABLED, createToggleItem("Player Shops Enabled",
                ConfigCacheManager.playerShopsEnabled, "player-shops.enabled"));
        inventory.setItem(MAX_LISTINGS, createIntItem("Max Listings/Player",
                ConfigCacheManager.maxListingsPerPlayer, "player-shops.max-listings-per-player", Material.CHEST));
        inventory.setItem(CROSS_SERVER_ENABLED, createToggleItem("Cross-Server Sync",
                ConfigCacheManager.crossServerEnabled, "cross-server.enabled"));
        inventory.setItem(CROSS_SERVER_INTERVAL, createIntItem("Sync Interval (sec)",
                ConfigCacheManager.crossServerSaveInterval, "cross-server.save-interval-seconds",
                Material.ENDER_PEARL));

        // === Row 4: Web Server & GUI ===
        inventory.setItem(WEBSERVER_ENABLED, createToggleItem("Web Server Enabled",
                ConfigCacheManager.webServerEnabled, "webserver.enabled"));
        inventory.setItem(WEBSERVER_PORT, createIntItem("Web Server Port",
                ConfigCacheManager.webServerPort, "webserver.port", Material.COMPASS));
        inventory.setItem(GUI_MENU_SIZE, createIntItem("Shop Menu Size",
                ConfigCacheManager.shopMenuSize, "gui.shop_menu_size", Material.CRAFTING_TABLE));
        inventory.setItem(INPUT_METHOD, createInputMethodItem());

        // Back button
        inventory.setItem(BACK_BUTTON, createBackButton());
    }

    private ItemStack createToggleItem(String name, boolean currentValue, String configPath) {
        Material mat = currentValue ? Material.LIME_DYE : Material.GRAY_DYE;
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(
                    LegacyComponentSerializer.legacySection().deserialize((currentValue ? "§a" : "§c") + "§l" + name));
            List<String> lore = new ArrayList<>();
            lore.add("§7Status: " + (currentValue ? "§aENABLED" : "§cDISABLED"));
            lore.add("§7Config: §f" + configPath);
            lore.add("");
            lore.add("§eClick to toggle");
            meta.lore(lore.stream().map(s -> LegacyComponentSerializer.legacySection().deserialize(s)).toList());
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createNumberItem(String name, double currentValue, String configPath, Material icon) {
        ItemStack item = new ItemStack(icon);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(LegacyComponentSerializer.legacySection().deserialize("§e§l" + name));
            List<String> lore = new ArrayList<>();
            lore.add("§7Value: §f" + String.format("%.2f", currentValue));
            lore.add("§7Config: §f" + configPath);
            lore.add("");
            lore.add("§eClick to change");
            meta.lore(lore.stream().map(s -> LegacyComponentSerializer.legacySection().deserialize(s)).toList());
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createIntItem(String name, int currentValue, String configPath, Material icon) {
        ItemStack item = new ItemStack(icon);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(LegacyComponentSerializer.legacySection().deserialize("§e§l" + name));
            List<String> lore = new ArrayList<>();
            lore.add("§7Value: §f" + currentValue);
            lore.add("§7Config: §f" + configPath);
            lore.add("");
            lore.add("§eClick to change");
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
            lore.add("§7Return to admin shop");
            meta.lore(lore.stream().map(s -> LegacyComponentSerializer.legacySection().deserialize(s)).toList());
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createFiller() {
        ItemStack filler = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta meta = filler.getItemMeta();
        if (meta != null) {
            meta.displayName(LegacyComponentSerializer.legacySection().deserialize(" "));
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
            case PLAYER_SHOPS_ENABLED -> toggleBoolean("player-shops.enabled");
            case CROSS_SERVER_ENABLED -> toggleBoolean("cross-server.enabled");
            case LOG_DYNAMIC_PRICING -> toggleBoolean("dynamic-pricing.log-dynamic-pricing");
            case WEBSERVER_ENABLED -> toggleBoolean("webserver.enabled");

            // Number options
            case CURVE_STRENGTH -> editDouble("Curve Strength", "dynamic-pricing.curve-strength");
            case MAX_STOCK -> editDouble("Max Stock", "dynamic-pricing.max-stock");
            case HOURLY_INCREASE -> editDouble("Hourly Increase %", "dynamic-pricing.hourly-increase-percent");
            case MIN_PRICE_MULT -> editDouble("Min Price Multiplier", "dynamic-pricing.min-price-multiplier");
            case MAX_PRICE_MULT -> editDouble("Max Price Multiplier", "dynamic-pricing.max-price-multiplier");
            case SELL_TAX -> editDouble("Sell Tax %", "economy.sell_tax_percent");
            case NEGATIVE_STOCK_PERCENT ->
                editDouble("Negative Stock %/item", "dynamic-pricing.negative-stock-percent-per-item");

            // Integer options
            case MAX_LISTINGS -> editInt("Max Listings", "player-shops.max-listings-per-player");
            case CROSS_SERVER_INTERVAL -> editInt("Sync Interval", "cross-server.save-interval-seconds");
            case TRANSACTION_COOLDOWN -> editInt("Transaction Cooldown (ms)", "economy.transaction_cooldown_ms");
            case WEBSERVER_PORT -> editInt("Web Server Port", "webserver.port");
            case GUI_MENU_SIZE -> editInt("Shop Menu Size", "gui.shop_menu_size");

            // Input method cycling
            case INPUT_METHOD -> cycleInputMethod();

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
        double current = plugin.getConfig().getDouble(configPath, 0);

        plugin.getInputManager().requestNumber(player,
                "Edit " + name,
                current,
                value -> {
                    if (value != null) {
                        plugin.getConfig().set(configPath, value);
                        plugin.saveConfig();
                        ConfigCacheManager.reload();
                        player.sendMessage("§a✓ §7" + configPath + " set to §e" + value);
                    }

                    // Reopen config GUI with a NEW instance to avoid stale onClose handler
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        AdminConfigGUI newGUI = new AdminConfigGUI(plugin, player, parentGUI);
                        plugin.getShopListener().registerAdminConfig(player, newGUI);
                        newGUI.open();
                    });
                });
    }

    private void editInt(String name, String configPath) {
        int current = plugin.getConfig().getInt(configPath, 0);

        plugin.getInputManager().requestInt(player,
                "Edit " + name,
                current,
                value -> {
                    if (value != null) {
                        plugin.getConfig().set(configPath, value);
                        plugin.saveConfig();
                        ConfigCacheManager.reload();
                        player.sendMessage("§a✓ §7" + configPath + " set to §e" + value);
                    }

                    // Reopen config GUI with a NEW instance to avoid stale onClose handler
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        AdminConfigGUI newGUI = new AdminConfigGUI(plugin, player, parentGUI);
                        plugin.getShopListener().registerAdminConfig(player, newGUI);
                        newGUI.open();
                    });
                });
    }

    private void goBack() {
        plugin.getShopListener().unregisterAdminConfig(player);
        plugin.getShopListener().registerAdminBrowse(player, parentGUI);
        parentGUI.render();
        player.openInventory(parentGUI.getInventory());
    }

    private ItemStack createInputMethodItem() {
        String current = plugin.getConfig().getString("input-method", "auto").toLowerCase();

        Material icon;
        String color;
        switch (current) {
            case "dialog" -> {
                icon = Material.PAPER;
                color = "§b";
            }
            case "anvil" -> {
                icon = Material.ANVIL;
                color = "§6";
            }
            case "chat" -> {
                icon = Material.WRITABLE_BOOK;
                color = "§a";
            }
            default -> {
                icon = Material.COMMAND_BLOCK;
                color = "§e";
                current = "auto";
            }
        }

        ItemStack item = new ItemStack(icon);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(LegacyComponentSerializer.legacySection()
                    .deserialize(color + "§lInput Method: " + current.toUpperCase()));
            List<String> lore = new ArrayList<>();
            lore.add("§7Current: " + color + current);
            lore.add("§7Config: §finput-method");
            lore.add("");
            lore.add("§7Options: auto, dialog, anvil, chat");
            lore.add("");
            lore.add("§eClick to cycle");
            meta.lore(lore.stream().map(s -> LegacyComponentSerializer.legacySection().deserialize(s)).toList());
            item.setItemMeta(meta);
        }
        return item;
    }

    private void cycleInputMethod() {
        String current = plugin.getConfig().getString("input-method", "auto").toLowerCase();

        String next = switch (current) {
            case "auto" -> "dialog";
            case "dialog" -> "anvil";
            case "anvil" -> "chat";
            default -> "auto";
        };

        plugin.getConfig().set("input-method", next);
        plugin.saveConfig();

        player.sendMessage("§a✓ §7Input method set to §e" + next);
        render();
    }

    public Inventory getInventory() {
        return inventory;
    }
}
