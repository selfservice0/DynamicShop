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
import org.minecraftsmp.dynamicshop.managers.PlayerShopManager;
import org.minecraftsmp.dynamicshop.listeners.PlayerShopListener;

public class DynamicShop extends JavaPlugin {

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

    private static DynamicShop instance;

    @Override
    public void onEnable() {
        instance = this;

        saveDefaultConfig();

        getLogger().info("§aDynamicShop is enabling...");

        // --------------------------------------------------------------------
        // CONFIG CACHE
        // --------------------------------------------------------------------
        ConfigCacheManager.init(this);

        // --------------------------------------------------------------------
        // MANAGER INIT
        // --------------------------------------------------------------------
        messageManager = new MessageManager(this);
        messageManager.init();

        protocolShopManager = new ProtocolShopManager(this);

        economyManager = new MultiCurrencyEconomyManager(this);
        economyManager.init();

        permissionsManager = new PermissionsManager(this);
        permissionsManager.init();

        specialShopManager = new SpecialShopManager(this);
        specialShopManager.init();

        transactionLogger = new TransactionLogger(this);
        transactionLogger.init();

        this.shopListener = new ShopListener(this);
        getServer().getPluginManager().registerEvents(shopListener, this);

        // Data manager (loads items & categories)
        this.p2pCrossServerManager = new EmbeddedP2PManager(this);
        this.p2pCrossServerManager.init();
        ShopDataManager.init(this);

        // Initialize player shops
        this.playerShopManager = new PlayerShopManager(this);
        getLogger().info("Player shops enabled!");

        setupPlaceholderAPI();

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

        // Flush queue before shutdown to save all pending updates
        getLogger().info("§e[ShopData] Flushing queue before shutdown...");
        try {
            ShopDataManager.flushQueue();
        } catch (Exception e) {
            getLogger().severe("[DynamicShop] Failed to flush shop queue on disable: " + e.getMessage());
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
        ShopDataManager.saveDynamicData(); // final save
        getLogger().info("§cDynamicShop disabled.");
    }

    // ------------------------------------------------------------------------
    // RELOAD
    // ------------------------------------------------------------------------
    public void reload() {
        ShopDataManager.flushQueue();
        reloadConfig();
        ConfigCacheManager.reload();
        economyManager.reload();
        specialShopManager.reload();
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

    public static DynamicShop getInstance() {
        return instance;
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