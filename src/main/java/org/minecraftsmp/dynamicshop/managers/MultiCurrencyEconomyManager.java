package org.minecraftsmp.dynamicshop.managers;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.minecraftsmp.dynamicshop.DynamicShop;
import org.minecraftsmp.dynamicshop.category.ItemCategory;
import su.nightexpress.coinsengine.api.CoinsEngineAPI;
import su.nightexpress.coinsengine.api.currency.Currency;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Multi-currency economy manager supporting both Vault and CoinEngine.
 *
 * Features:
 * - Vault integration (single currency)
 * - CoinEngine integration (multiple currencies)
 * - Per-item currency configuration
 * - Per-category currency configuration
 * - Cached currency lookups for performance
 * - Automatic fallback to Vault if CoinEngine unavailable
 */
public class MultiCurrencyEconomyManager {

    private final DynamicShop plugin;
    private Economy vaultEconomy;
    private boolean useCoinEngine = false;
    private String defaultCurrency;

    // CACHED CURRENCY MAPPINGS (cleared on reload)
    private final Map<Material, String> itemCurrencyCache = new ConcurrentHashMap<>();
    private final Map<ItemCategory, String> categoryCurrencyCache = new ConcurrentHashMap<>();
    private final Map<String, Currency> coinEngineCurrencyCache = new ConcurrentHashMap<>();

    public MultiCurrencyEconomyManager(DynamicShop plugin) {
        this.plugin = plugin;
    }

    // ---------------------------------------------------------------
    // INITIALIZATION
    // ---------------------------------------------------------------

    public void init() {
        String system = plugin.getConfig().getString("economy.system", "vault");

        if (system.equalsIgnoreCase("coinengine")) {
            if (!setupCoinEngine()) {
                plugin.getLogger().warning("[MultiCurrency] CoinEngine not found, falling back to Vault");
                system = "vault";
            } else {
                plugin.getLogger().info("[MultiCurrency] Using CoinEngine (multi-currency mode)");
            }
        }

        if (system.equalsIgnoreCase("vault")) {
            if (!setupVault()) {
                plugin.getLogger().severe("[MultiCurrency] Vault economy not found! Disabling plugin.");
                plugin.getServer().getPluginManager().disablePlugin(plugin);
            } else {
                plugin.getLogger().info("[MultiCurrency] Using Vault (single currency mode)");
            }
        }

        // Load currency caches
        loadCurrencyCaches();
    }

    private boolean setupVault() {
        if (plugin.getServer().getPluginManager().getPlugin("Vault") == null) {
            return false;
        }
        var rsp = plugin.getServer().getServicesManager()
                .getRegistration(Economy.class);
        if (rsp == null) return false;
        vaultEconomy = rsp.getProvider();
        useCoinEngine = false;
        return vaultEconomy != null;
    }

    private boolean setupCoinEngine() {
        if (plugin.getServer().getPluginManager().getPlugin("CoinsEngine") == null) {
            return false;
        }
        defaultCurrency = plugin.getConfig().getString("economy.default_currency", "coins");
        useCoinEngine = true;

        // Verify default currency exists
        Currency defaultCurr = CoinsEngineAPI.getCurrency(defaultCurrency);
        if (defaultCurr == null) {
            plugin.getLogger().warning("[MultiCurrency] Default currency '" + defaultCurrency +
                    "' not found in CoinsEngine! Check your config.");
            return false;
        }

        plugin.getLogger().info("[MultiCurrency] Default currency: " + defaultCurrency);
        return true;
    }

    // ---------------------------------------------------------------
    // CACHE LOADING (called on init and reload)
    // ---------------------------------------------------------------

    private void loadCurrencyCaches() {
        itemCurrencyCache.clear();
        categoryCurrencyCache.clear();
        coinEngineCurrencyCache.clear();

        if (!useCoinEngine) return;

        // Cache item currencies
        if (plugin.getConfig().isConfigurationSection("items")) {
            for (String key : plugin.getConfig().getConfigurationSection("items").getKeys(false)) {
                String currencyStr = plugin.getConfig().getString("items." + key + ".currency");
                if (currencyStr != null && !currencyStr.isEmpty()) {
                    try {
                        Material mat = Material.valueOf(key.toUpperCase());
                        itemCurrencyCache.put(mat, currencyStr);
                        plugin.getLogger().info("[MultiCurrency] Item " + key + " uses currency: " + currencyStr);
                    } catch (IllegalArgumentException e) {
                        plugin.getLogger().warning("[MultiCurrency] Invalid material: " + key);
                    }
                }
            }
        }

        // Cache category currencies
        if (plugin.getConfig().isConfigurationSection("categories")) {
            for (String key : plugin.getConfig().getConfigurationSection("categories").getKeys(false)) {
                String currencyStr = plugin.getConfig().getString("categories." + key + ".currency");
                if (currencyStr != null && !currencyStr.isEmpty()) {
                    try {
                        ItemCategory category = ItemCategory.valueOf(key.toUpperCase());
                        categoryCurrencyCache.put(category, currencyStr);
                        plugin.getLogger().info("[MultiCurrency] Category " + key + " uses currency: " + currencyStr);
                    } catch (IllegalArgumentException e) {
                        plugin.getLogger().warning("[MultiCurrency] Invalid category: " + key);
                    }
                }
            }
        }

        plugin.getLogger().info("[MultiCurrency] Loaded " + itemCurrencyCache.size() + " item currencies, " +
                categoryCurrencyCache.size() + " category currencies");
    }

    // ---------------------------------------------------------------
    // RELOAD SUPPORT
    // ---------------------------------------------------------------

    public void reload() {
        loadCurrencyCaches();
    }

    // ---------------------------------------------------------------
    // CURRENCY LOOKUP (CACHED)
    // ---------------------------------------------------------------

    /**
     * Get currency for a material.
     * Priority: Item-specific > Category > Default
     */
    public String getCurrency(Material mat) {
        if (!useCoinEngine) return null;

        // Check item-specific currency (cached)
        String currency = itemCurrencyCache.get(mat);
        if (currency != null) return currency;

        // Check category currency (cached)
        ItemCategory category = ShopDataManager.detectCategory(mat);
        currency = categoryCurrencyCache.get(category);
        if (currency != null) return currency;

        // Fall back to default
        return defaultCurrency;
    }

    /**
     * Get currency for a category
     */
    public String getCurrency(ItemCategory category) {
        if (!useCoinEngine) return null;

        // Check cached category currency
        String currency = categoryCurrencyCache.get(category);
        if (currency != null) return currency;

        // Fall back to default
        return defaultCurrency;
    }

    /**
     * Get CoinEngine Currency object (cached)
     */
    private Currency getCoinEngineCurrency(String currencyId) {
        if (currencyId == null) return null;

        // Check cache first
        Currency cached = coinEngineCurrencyCache.get(currencyId);
        if (cached != null) return cached;

        // Fetch from API and cache
        Currency currency = CoinsEngineAPI.getCurrency(currencyId);
        if (currency != null) {
            coinEngineCurrencyCache.put(currencyId, currency);
        }
        return currency;
    }

    // ---------------------------------------------------------------
    // ECONOMY OPERATIONS (MULTI-CURRENCY)
    // ---------------------------------------------------------------

    /**
     * Charge player with automatic currency detection based on material
     */
    public boolean charge(Player p, Material mat, double amount) {
        String currency = getCurrency(mat);
        return charge(p, amount, currency);
    }

    /**
     * Charge player with automatic currency detection based on category
     */
    public boolean charge(Player p, ItemCategory category, double amount) {
        String currency = getCurrency(category);
        return charge(p, amount, currency);
    }

    /**
     * Charge player with explicit currency
     */
    public boolean charge(Player p, double amount, String currency) {
        if (amount < 0) return false;

        if (useCoinEngine) {
            Currency curr = getCoinEngineCurrency(currency);
            if (curr == null) {
                plugin.getLogger().warning("[MultiCurrency] Currency not found: " + currency);
                return false;
            }
            if (!hasEnough(p, amount, currency)) return false;
            CoinsEngineAPI.removeBalance(p, curr, amount);
            return true;
        } else {
            // Vault (ignore currency parameter)
            if (!hasEnough(p, amount, null)) return false;
            vaultEconomy.withdrawPlayer(p, amount);
            return true;
        }
    }

    /**
     * Charge player with default currency (backward compatible)
     */
    public boolean charge(Player p, double amount) {
        if (useCoinEngine) {
            return charge(p, amount, defaultCurrency);
        } else {
            if (amount < 0) return false;
            if (!hasEnough(p, amount)) return false;
            vaultEconomy.withdrawPlayer(p, amount);
            return true;
        }
    }

    /**
     * Deposit to player with explicit currency
     */
    public void deposit(Player p, double amount, String currency) {
        if (amount < 0) return;

        if (useCoinEngine) {
            Currency curr = getCoinEngineCurrency(currency);
            if (curr == null) {
                plugin.getLogger().warning("[MultiCurrency] Currency not found: " + currency);
                return;
            }
            CoinsEngineAPI.addBalance(p, curr, amount);
        } else {
            vaultEconomy.depositPlayer(p, amount);
        }
    }

    public void depositOffline(OfflinePlayer offline, UUID uuid, double amount, String currency) {
        if (amount < 0) return;

        if (useCoinEngine) {
            Currency curr = getCoinEngineCurrency(currency);
            if (curr == null) {
                plugin.getLogger().warning("[MultiCurrency] Currency not found: " + currency);
                return;
            }
            CoinsEngineAPI.addBalance(uuid, curr, amount);
        } else {
            vaultEconomy.depositPlayer(offline, amount);
        }
    }

    /**
     * Deposit to player with default currency (backward compatible)
     */
    public void deposit(Player p, double amount) {
        if (useCoinEngine) {
            deposit(p, amount, defaultCurrency);
        } else {
            if (amount < 0) return;
            vaultEconomy.depositPlayer(p, amount);
        }
    }
    // ------------------------------------------------------------------
    // OFFLINE DEPOSIT SUPPORT
    // ------------------------------------------------------------------
    public void depositOffline(OfflinePlayer offline, double amount) {


        if (useCoinEngine) {
            UUID uuid = offline.getUniqueId();
            depositOffline(offline, uuid, amount, defaultCurrency);

        } else if (vaultEconomy != null) {
            // Vault supports OfflinePlayer directly
            vaultEconomy.depositPlayer(offline, amount);
        }
    }


    /**
     * Check if player has enough money (explicit currency)
     */
    public boolean hasEnough(Player p, double amount, String currency) {
        if (amount <= 0) return true;

        if (useCoinEngine) {
            Currency curr = getCoinEngineCurrency(currency);
            if (curr == null) return false;
            double balance = CoinsEngineAPI.getBalance(p, curr);
            return balance >= amount;
        } else {
            if (vaultEconomy == null) return false;
            return vaultEconomy.getBalance(p) >= amount;
        }
    }

    /**
     * Check if player has enough money with default currency (backward compatible)
     */
    public boolean hasEnough(Player p, double amount) {
        if (useCoinEngine) {
            return hasEnough(p, amount, defaultCurrency);
        } else {
            if (amount <= 0) return true;
            if (vaultEconomy == null) return false;
            return vaultEconomy.getBalance(p) >= amount;
        }
    }

    /**
     * Get player balance (explicit currency)
     */
    public double getBalance(Player p, String currency) {
        if (useCoinEngine) {
            Currency curr = getCoinEngineCurrency(currency);
            if (curr == null) return 0.0;
            return CoinsEngineAPI.getBalance(p, curr);
        } else {
            if (vaultEconomy == null) return 0.0;
            return vaultEconomy.getBalance(p);
        }
    }

    // ---------------------------------------------------------------
    // FORMATTING (MULTI-CURRENCY)
    // ---------------------------------------------------------------

    /**
     * Format money with automatic currency detection (category)
     */
    public String format(ItemCategory category, double value) {
        String currency = getCurrency(category);
        return format(value, currency);
    }

    /**
     * Format money with explicit currency
     */
    public String format(double value, String currency) {
        if (useCoinEngine) {
            Currency curr = getCoinEngineCurrency(currency);
            if (curr == null) {
                return String.format("%.2f %s", value, currency != null ? currency : "");
            }
            return curr.format(value);
        } else {
            if (vaultEconomy != null) {
                return vaultEconomy.format(value);
            }
            return String.format("$%.2f", value);
        }
    }

    /**
     * Format money with default currency (backward compatible)
     * Used when currency context is not available
     */
    public String format(double value) {
        if (useCoinEngine) {
            return format(value, defaultCurrency);
        } else {
            if (vaultEconomy != null) {
                return vaultEconomy.format(value);
            }
            return String.format("$%.2f", value);
        }
    }

}