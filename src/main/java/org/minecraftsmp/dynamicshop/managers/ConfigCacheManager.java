package org.minecraftsmp.dynamicshop.managers;

import org.minecraftsmp.dynamicshop.DynamicShop;

/**
 * Centralized configuration cache for frequently-accessed settings.
 * Prevents repeated file reads during transactions and price calculations.
 */
public class ConfigCacheManager {

    private static DynamicShop plugin;

    // ECONOMY SETTINGS
    public static String economySystem = "vault";
    public static String defaultCurrency = "coins";
    public static double sellTaxPercent = 0.30;
    public static long transactionCooldownMs = 0L;

    // DYNAMIC PRICING SETTINGS
    public static boolean dynamicPricingEnabled = true;
    public static boolean useStockCurve = true;
    public static double curveStrength = 0.7;
    public static double maxStock = 500.0;
    public static double minPriceMultiplier = 0.5;
    public static double maxPriceMultiplier = 2.0;
    public static double negativeStockPercentPerItem = 5.0;
    public static boolean useTimeInflation = true;
    public static double hourlyIncreasePercent = 2.0;
    public static double shortageDecayPercentPerHour = 2.0;
    public static boolean highInflationCorrectionEnabled = true;
    public static double highInflationCorrectionThresholdPercent = 100.0;
    public static double highInflationCorrectionReductionPercent = 50.0;
    public static boolean restrictBuyingAtZeroStock = false;
    public static boolean logDynamicPricing = false;

    // GUI SETTINGS
    public static int shopMenuSize = 54;
    public static String fillerMaterialStr = "AIR";
    public static boolean useDialogGui = false;

    // WEB SERVER SETTINGS
    public static boolean webServerEnabled = true;
    public static int webServerPort = 7713;
    public static String webServerBind = "0.0.0.0";
    public static boolean webServerForceUpdate = false;
    public static boolean webServerCorsEnabled = true;

    // LOGGING SETTINGS
    public static int maxRecentTransactions = 5000;

    // PLAYER SHOP SETTINGS
    public static boolean playerShopsEnabled = true;
    public static int maxListingsPerPlayer = 27;

    // Cross Server SETTINGS
    public static boolean crossServerEnabled = false;
    public static int crossServerSaveInterval = 600;

    public static void init(DynamicShop pluginInstance) {
        plugin = pluginInstance;
        loadAll();
    }

    public static void reload() {
        if (plugin != null) {
            loadAll();
            plugin.getLogger().info("[ConfigCache] Reloaded cached settings");
        }
    }

    private static void loadAll() {
        loadEconomySettings();
        loadDynamicPricingSettings();
        loadGuiSettings();
        loadWebServerSettings();
        loadLoggingSettings();
        loadPlayerShopSettings();
        loadCrossServerSettings();
    }

    private static void loadEconomySettings() {
        economySystem = plugin.getConfig().getString("economy.system", "vault");
        defaultCurrency = plugin.getConfig().getString("economy.default_currency", "coins");
        sellTaxPercent = plugin.getConfig().getDouble("economy.sell_tax_percent", 0.30) / 100.0;
        transactionCooldownMs = plugin.getConfig().getLong("economy.transaction_cooldown_ms", 0L);
    }

    private static void loadDynamicPricingSettings() {
        dynamicPricingEnabled = plugin.getConfig().getBoolean("dynamic-pricing.enabled", true);
        useStockCurve = plugin.getConfig().getBoolean("dynamic-pricing.use-stock-curve", true);
        curveStrength = plugin.getConfig().getDouble("dynamic-pricing.curve-strength", 0.7);
        maxStock = plugin.getConfig().getDouble("dynamic-pricing.max-stock", 500.0);
        minPriceMultiplier = plugin.getConfig().getDouble("dynamic-pricing.min-price-multiplier", 0.5);
        maxPriceMultiplier = plugin.getConfig().getDouble("dynamic-pricing.max-price-multiplier", 2.0);
        negativeStockPercentPerItem = plugin.getConfig().getDouble("dynamic-pricing.negative-stock-percent-per-item",
                5.0);
        useTimeInflation = plugin.getConfig().getBoolean("dynamic-pricing.use-time-inflation", true);
        hourlyIncreasePercent = plugin.getConfig().getDouble("dynamic-pricing.hourly-increase-percent", 2.0);
        shortageDecayPercentPerHour = plugin.getConfig().getDouble("dynamic-pricing.shortage-decay-percent-per-hour", 2.0);
        highInflationCorrectionEnabled = plugin.getConfig().getBoolean("dynamic-pricing.high-inflation-correction-enabled", true);
        highInflationCorrectionThresholdPercent = plugin.getConfig().getDouble("dynamic-pricing.high-inflation-correction-threshold-percent", 100.0);
        highInflationCorrectionReductionPercent = plugin.getConfig().getDouble("dynamic-pricing.high-inflation-correction-reduction-percent", 50.0);

        restrictBuyingAtZeroStock = plugin.getConfig().getBoolean("dynamic-pricing.restrict-buying-at-zero-stock",
                false);
        logDynamicPricing = plugin.getConfig().getBoolean("dynamic-pricing.log-dynamic-pricing", false);
    }

    private static void loadGuiSettings() {
        shopMenuSize = plugin.getConfig().getInt("gui.shop_menu_size", 54);
        fillerMaterialStr = plugin.getConfig().getString("gui.filler_material", "AIR");
        useDialogGui = plugin.getConfig().getBoolean("gui.use_dialog_gui", false);
    }

    /**
     * Get the filler item stack based on config.
     */
    public static org.bukkit.inventory.ItemStack getFillerItem() {
        if (fillerMaterialStr != null && fillerMaterialStr.toLowerCase().startsWith("nexo:")) {
            String nexoId = fillerMaterialStr.substring(5);
            if (plugin.getServer().getPluginManager().getPlugin("Nexo") != null) {
                org.bukkit.inventory.ItemStack nexoItem = NexoWrapper.getItem(nexoId);
                if (nexoItem != null) {
                    org.bukkit.inventory.meta.ItemMeta meta = nexoItem.getItemMeta();
                    if (meta != null) {
                        org.minecraftsmp.dynamicshop.util.PaperCompat.setDisplayName(meta, MessageManager.parseComponent(" "));
                        nexoItem.setItemMeta(meta);
                    }
                    return nexoItem.clone();
                }
            }
        }
        try {
            org.bukkit.Material mat = org.bukkit.Material.valueOf(fillerMaterialStr.toUpperCase());
            if (mat == org.bukkit.Material.AIR) return new org.bukkit.inventory.ItemStack(org.bukkit.Material.AIR);
            org.bukkit.inventory.ItemStack filler = new org.bukkit.inventory.ItemStack(mat);
            org.bukkit.inventory.meta.ItemMeta meta = filler.getItemMeta();
            if (meta != null) {
                org.minecraftsmp.dynamicshop.util.PaperCompat.setDisplayName(meta, MessageManager.parseComponent(" "));
                filler.setItemMeta(meta);
            }
            return filler;
        } catch (Exception ignored) {
            return new org.bukkit.inventory.ItemStack(org.bukkit.Material.AIR);
        }
    }

    private static void loadWebServerSettings() {
        webServerEnabled = plugin.getConfig().getBoolean("webserver.enabled", true);
        webServerPort = plugin.getConfig().getInt("webserver.port", 7713);
        webServerBind = plugin.getConfig().getString("webserver.bind", "127.0.0.1");
        webServerForceUpdate = plugin.getConfig().getBoolean("webserver.force-update-files", false);
        webServerCorsEnabled = plugin.getConfig().getBoolean("webserver.cors.enabled", false);
    }

    private static void loadLoggingSettings() {
        maxRecentTransactions = plugin.getConfig().getInt("logging.max_recent_transactions", 5000);
    }

    private static void loadPlayerShopSettings() {
        playerShopsEnabled = plugin.getConfig().getBoolean("player-shops.enabled", true);
        maxListingsPerPlayer = plugin.getConfig().getInt("player-shops.max-listings-per-player", 27);
    }

    private static void loadCrossServerSettings() {
        crossServerEnabled = plugin.getConfig().getBoolean("cross-server.enabled", false);
        crossServerSaveInterval = plugin.getConfig().getInt("cross-server.save-interval-seconds", 600);
    }
}
