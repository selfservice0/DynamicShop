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
    public static long transactionCooldownMs = 500L;

    // DYNAMIC PRICING SETTINGS
    public static boolean dynamicPricingEnabled = true;
    public static boolean useStockCurve = true;
    public static double curveStrength = 0.7;
    public static double maxStock = 500.0;
    public static double minPriceMultiplier = 0.5;
    public static double maxPriceMultiplier = 20.0;
    public static double negativeStockPercentPerItem = 5.0;
    public static boolean useTimeInflation = true;
    public static double hourlyIncreasePercent = 2.0;
    public static boolean restrictBuyingAtZeroStock = false;
    public static boolean logDynamicPricing = false;

    // GUI SETTINGS
    public static int shopMenuSize = 54;

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
        transactionCooldownMs = plugin.getConfig().getLong("economy.transaction_cooldown_ms", 500L);
    }

    private static void loadDynamicPricingSettings() {
        dynamicPricingEnabled = plugin.getConfig().getBoolean("dynamic-pricing.enabled", true);
        useStockCurve = plugin.getConfig().getBoolean("dynamic-pricing.use-stock-curve", true);
        curveStrength = plugin.getConfig().getDouble("dynamic-pricing.curve-strength", 0.7);
        maxStock = plugin.getConfig().getDouble("dynamic-pricing.max-stock", 500.0);
        minPriceMultiplier = plugin.getConfig().getDouble("dynamic-pricing.min-price-multiplier", 0.5);
        maxPriceMultiplier = plugin.getConfig().getDouble("dynamic-pricing.max-price-multiplier", 20.0);
        negativeStockPercentPerItem = plugin.getConfig().getDouble("dynamic-pricing.negative-stock-percent-per-item",
                5.0);
        useTimeInflation = plugin.getConfig().getBoolean("dynamic-pricing.use-time-inflation", true);
        hourlyIncreasePercent = plugin.getConfig().getDouble("dynamic-pricing.hourly-increase-percent", 2.0);

        restrictBuyingAtZeroStock = plugin.getConfig().getBoolean("dynamic-pricing.restrict-buying-at-zero-stock",
                false);
        logDynamicPricing = plugin.getConfig().getBoolean("dynamic-pricing.log-dynamic-pricing", false);
    }

    private static void loadGuiSettings() {
        shopMenuSize = plugin.getConfig().getInt("gui.shop_menu_size", 54);
    }

    private static void loadWebServerSettings() {
        webServerEnabled = plugin.getConfig().getBoolean("webserver.enabled", true);
        webServerPort = plugin.getConfig().getInt("webserver.port", 7713);
        webServerBind = plugin.getConfig().getString("webserver.bind", "0.0.0.0");
        webServerForceUpdate = plugin.getConfig().getBoolean("webserver.force-update-files", false);
        webServerCorsEnabled = plugin.getConfig().getBoolean("webserver.cors.enabled", true);
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
