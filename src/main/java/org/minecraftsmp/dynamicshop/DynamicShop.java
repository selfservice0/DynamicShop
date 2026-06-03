package org.minecraftsmp.dynamicshop;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import org.minecraftsmp.dynamicshop.commands.ShopAdminCommand;
import org.minecraftsmp.dynamicshop.commands.ShopCommand;

import org.minecraftsmp.dynamicshop.gui.SearchResultsGUI;
import org.minecraftsmp.dynamicshop.managers.*;
import org.minecraftsmp.dynamicshop.listeners.ShopListener;
import org.minecraftsmp.dynamicshop.placeholder.DynamicShopExpansion;
import org.minecraftsmp.dynamicshop.transactions.TransactionLogger;
import org.minecraftsmp.dynamicshop.web.WebServer;
import org.minecraftsmp.dynamicshop.listeners.PlayerShopListener;
import org.bstats.bukkit.Metrics;

public class DynamicShop extends JavaPlugin {

    // bStats plugin ID - get yours from https://bstats.org
    private static final int BSTATS_PLUGIN_ID = 28506;

    // Managers
    private MultiCurrencyEconomyManager economyManager;
    private ProtocolShopManager protocolShopManager;
    private ShopListener shopListener;
    private SpecialShopManager specialShopManager;
    private TransactionLogger transactionLogger;
    private PermissionsManager permissionsManager;
    private WebServer webServer;
    private MessageManager messageManager;
    private PlayerShopManager playerShopManager;
    private SearchResultsGUI searchResultsGUI;
    private PlayerShopListener playerShopListener;
    private EmbeddedP2PManager p2pCrossServerManager;
    private org.minecraftsmp.dynamicshop.listeners.ChatInputListener chatInputListener;
    private org.minecraftsmp.dynamicshop.managers.InputManager inputManager;
    private RestockManager restockManager;
    private org.minecraftsmp.dynamicshop.gui.ShopDialogManager shopDialogManager;

    private static DynamicShop instance;

    @Override
    public void onEnable() {
        instance = this;

        saveDefaultConfig();

        getLogger().info("§aDynamicShop is enabling...");

        // --------------------------------------------------------------------
        // BSTATS METRICS
        // --------------------------------------------------------------------
        new Metrics(this, BSTATS_PLUGIN_ID);

        // --------------------------------------------------------------------
        // CONFIG CACHE
        // --------------------------------------------------------------------
        ConfigCacheManager.init(this);
        CategoryConfigManager.init(this);

        // --------------------------------------------------------------------
        // MANAGER INIT
        // --------------------------------------------------------------------
        messageManager = new MessageManager(this);
        messageManager.init();

        protocolShopManager = new ProtocolShopManager(this);

        economyManager = new MultiCurrencyEconomyManager(this);
        if (!economyManager.init()) {
            return;
        }

        permissionsManager = new PermissionsManager(this);
        permissionsManager.init();

        specialShopManager = new SpecialShopManager(this);
        specialShopManager.init();

        transactionLogger = new TransactionLogger(this);
        transactionLogger.init();

        // Data manager (loads items & categories)
        this.p2pCrossServerManager = new EmbeddedP2PManager(this);
        this.p2pCrossServerManager.init();
        ShopDataManager.init(this);

        // Initialize restock scheduler
        restockManager = new RestockManager(this);
        restockManager.init();

        // Auto-populate restock config section for existing servers
        if (!getConfig().isSet("restock")) {
            getConfig().set("restock.enabled", false);
            getConfig().set("restock.rules", new java.util.ArrayList<>());
            saveConfig();
            getLogger().info("[Restock] Added default restock config section (disabled).");
        }

        // Auto-populate shortage decay setting for existing servers
        if (!getConfig().isSet("dynamic-pricing.shortage-decay-percent-per-hour")) {
            getConfig().set("dynamic-pricing.shortage-decay-percent-per-hour", 2.0);
            saveConfig();
            getLogger().info("[DynamicPricing] Added default shortage-decay-percent-per-hour (2.0) to config.");
        }

        // ──────────────────────────────────────────────────────────────
        // AUTO-POPULATE MISSING CONFIG KEYS for existing servers
        // This ensures all config options exist even on upgrades.
        // ──────────────────────────────────────────────────────────────
        boolean configChanged = false;

        // Dynamic Pricing
        if (!getConfig().isSet("dynamic-pricing.log-dynamic-pricing")) {
            getConfig().set("dynamic-pricing.log-dynamic-pricing", false);
            configChanged = true;
        }
        if (!getConfig().isSet("dynamic-pricing.restrict-buying-at-zero-stock")) {
            getConfig().set("dynamic-pricing.restrict-buying-at-zero-stock", true);
            configChanged = true;
        }
        if (!getConfig().isSet("dynamic-pricing.negative-stock-percent-per-item")) {
            getConfig().set("dynamic-pricing.negative-stock-percent-per-item", 5.0);
            configChanged = true;
        }

        // Economy
        if (!getConfig().isSet("economy.sell_tax_percent")) {
            getConfig().set("economy.sell_tax_percent", 30);
            configChanged = true;
        }
        if (!getConfig().isSet("economy.transaction_cooldown_ms")) {
            getConfig().set("economy.transaction_cooldown_ms", 0);
            configChanged = true;
        }

        // GUI
        if (!getConfig().isSet("gui.shop_menu_size")) {
            getConfig().set("gui.shop_menu_size", 54);
            configChanged = true;
        }
        if (!getConfig().isSet("gui.use_dialog_gui")) {
            getConfig().set("gui.use_dialog_gui", false);
            configChanged = true;
        }

        // Logging
        if (!getConfig().isSet("logging.max_recent_transactions")) {
            getConfig().set("logging.max_recent_transactions", 10000);
            configChanged = true;
        }

        // Player Shops
        if (!getConfig().isSet("player-shops.enabled")) {
            getConfig().set("player-shops.enabled", true);
            configChanged = true;
        }
        if (!getConfig().isSet("player-shops.max-listings-per-player")) {
            getConfig().set("player-shops.max-listings-per-player", 27);
            configChanged = true;
        }

        // Webserver
        if (!getConfig().isSet("webserver.enabled")) {
            getConfig().set("webserver.enabled", true);
            configChanged = true;
        }
        if (!getConfig().isSet("webserver.port")) {
            getConfig().set("webserver.port", 7713);
            configChanged = true;
        }
        if (!getConfig().isSet("webserver.bind")) {
            getConfig().set("webserver.bind", "127.0.0.1");
            configChanged = true;
        }
        if (!getConfig().isSet("webserver.cors.enabled")) {
            getConfig().set("webserver.cors.enabled", false);
            configChanged = true;
        }
        if (!getConfig().isSet("webserver.force-update-files")) {
            getConfig().set("webserver.force-update-files", false);
            configChanged = true;
        }
        // Web Admin Panel toggle — allows disabling admin panel while keeping the public dashboard
        if (!getConfig().isSet("webserver.admin-enabled")) {
            getConfig().set("webserver.admin-enabled", true);
            configChanged = true;
        }
        // Hostname for generated admin links (empty = auto-detect)
        if (!getConfig().isSet("webserver.hostname")) {
            getConfig().set("webserver.hostname", "");
            configChanged = true;
        }

        // Cross-server
        if (!getConfig().isSet("cross-server.enabled")) {
            getConfig().set("cross-server.enabled", false);
            configChanged = true;
        }

        if (configChanged) {
            saveConfig();
            getLogger().info("[Config] Auto-populated missing config defaults.");
        }
        // Initialize player shops
        this.playerShopManager = new PlayerShopManager(this);
        getLogger().info("Player shops enabled!");

        setupPlaceholderAPI();

        // Hook into ValhallaMMO if present
        if (Bukkit.getPluginManager().getPlugin("ValhallaMMO") != null) {
            getLogger().info("§aValhallaMMO found — custom item support enabled!");
        }

        // Warm up Nexo resolver AFTER all plugins have finished enabling.
        // Nexo's GlyphTag/ShiftTag singletons may not be ready during onEnable.
        if (Bukkit.getPluginManager().getPlugin("Nexo") != null) {
            getServer().getGlobalRegionScheduler().runDelayed(this, task -> {
                NexoWrapper.invalidateCache();  // Force re-init
                getLogger().info("§aNexo glyph resolver cache warmed up.");
            }, 1L);  // 1 tick after all plugins are done
        }

        // --------------------------------------------------------------------
        // WEB SERVER
        // --------------------------------------------------------------------
        webServer = new WebServer(this);
        webServer.start();

        // --------------------------------------------------------------------
        // LISTENERS
        // --------------------------------------------------------------------
        shopListener = new ShopListener(this);
        Bukkit.getPluginManager().registerEvents(shopListener, this);

        // Register player shop listener
        this.playerShopListener = new PlayerShopListener(this);
        getServer().getPluginManager().registerEvents(playerShopListener, this);

        // Register chat input listener for category editing
        this.chatInputListener = new org.minecraftsmp.dynamicshop.listeners.ChatInputListener(this);
        getServer().getPluginManager().registerEvents(chatInputListener, this);

        // Initialize input manager (auto-detects Dialog API availability)
        this.inputManager = new org.minecraftsmp.dynamicshop.managers.InputManager(this);

        // Initialize dialog-based shop GUI (optional, config-driven)
        this.shopDialogManager = new org.minecraftsmp.dynamicshop.gui.ShopDialogManager(this);

        // Update checker — async GitHub release check + OP join notifications
        UpdateChecker updateChecker = new UpdateChecker(this);
        getServer().getPluginManager().registerEvents(updateChecker, this);
        updateChecker.check();

        // --------------------------------------------------------------------
        // COMMANDS
        // --------------------------------------------------------------------
        getCommand("shop").setExecutor(new ShopCommand(this));
        getCommand("shop").setTabCompleter(new ShopCommand(this));

        ShopAdminCommand adminCmd = new ShopAdminCommand(this);
        getCommand("shopadmin").setExecutor(adminCmd);
        getCommand("shopadmin").setTabCompleter(adminCmd);

        getLogger().info("§aDynamicShop enabled successfully.");
    }

    @Override
    public void onDisable() {

        // Cancel timer to prevent memory leak
        if (ShopDataManager.saveTimer != null) {
            ShopDataManager.saveTimer.cancel();
        }

        // Cancel restock timers
        if (restockManager != null) {
            restockManager.shutdown();
        }

        // Flush queue before shutdown to save all pending updates
        // Only attempt if ShopDataManager was initialized
        if (ShopDataManager.isInitialized()) {
            getLogger().info("§e[ShopData] Flushing queue before shutdown...");
            try {
                ShopDataManager.flushQueue();
            } catch (Exception e) {
                getLogger().severe("[DynamicShop] Failed to flush shop queue on disable: " + e.getMessage());
            }
        }

        // Shutdown P2P cross-server (saves YAML)
        if (p2pCrossServerManager != null) {
            p2pCrossServerManager.shutdown();
        }

        if (transactionLogger != null) {
            transactionLogger.shutdown();
        }
        if (webServer != null) {
            webServer.stop();
        }
        // Final save - only if initialized
        if (ShopDataManager.isInitialized()) {
            ShopDataManager.saveDynamicData();
        }

        // Save category config
        CategoryConfigManager.save();

        getLogger().info("§cDynamicShop disabled.");
    }

    // ------------------------------------------------------------------------
    // RELOAD
    // ------------------------------------------------------------------------
    public void reload() {
        getLogger().info("[DEBUG] DynamicShop.reload() method called!");
        ShopDataManager.flushQueue();
        reloadConfig();
        ConfigCacheManager.reload();
        ShopDataManager.reload();
        messageManager.reload();
        economyManager.reload();
        specialShopManager.reload();
        CategoryConfigManager.load();
        if (restockManager != null) {
            restockManager.reload();
        }
    }

    // ------------------------------------------------------------------------
    // GETTERS
    // ------------------------------------------------------------------------
    public MultiCurrencyEconomyManager getEconomyManager() {
        return economyManager;
    }

    public ProtocolShopManager getProtocolShopManager() {
        return protocolShopManager;
    }

    public ShopListener getShopListener() {
        return shopListener;
    }

    public SpecialShopManager getSpecialShopManager() {
        return specialShopManager;
    }

    public PermissionsManager getPermissionsManager() {
        return permissionsManager;
    }

    public TransactionLogger getTransactionLogger() {
        return transactionLogger;
    }

    public WebServer getWebServer() {
        return webServer;
    }

    public MessageManager getMessageManager() {
        return messageManager;
    }

    public PlayerShopManager getPlayerShopManager() {
        return playerShopManager;
    }

    public PlayerShopListener getPlayerShopListener() {
        return playerShopListener;
    }

    public SearchResultsGUI getSearchResultsGUI() {
        return searchResultsGUI;
    }

    public EmbeddedP2PManager getP2PCrossServerManager() {
        return p2pCrossServerManager;
    }

    public RestockManager getRestockManager() {
        return restockManager;
    }

    public static DynamicShop getInstance() {
        return instance;
    }

    public org.minecraftsmp.dynamicshop.listeners.ChatInputListener getChatInputListener() {
        return chatInputListener;
    }

    public org.minecraftsmp.dynamicshop.managers.InputManager getInputManager() {
        return inputManager;
    }

    public org.minecraftsmp.dynamicshop.gui.ShopDialogManager getShopDialogManager() {
        return shopDialogManager;
    }

    private void setupPlaceholderAPI() {
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new DynamicShopExpansion(this).register();
            getLogger().info("§aPlaceholderAPI found and hooked successfully!");
        } else {
            getLogger().info("§ePlaceholderAPI not found – placeholders will be disabled.");
        }
    }
}
