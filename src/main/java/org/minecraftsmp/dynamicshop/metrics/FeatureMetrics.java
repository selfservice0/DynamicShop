package org.minecraftsmp.dynamicshop.metrics;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;
import org.bstats.bukkit.Metrics;
import org.bstats.charts.AdvancedPie;
import org.bstats.charts.SimplePie;
import org.bstats.charts.SingleLineChart;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.minecraftsmp.dynamicshop.DynamicShop;
import org.minecraftsmp.dynamicshop.category.ItemCategory;
import org.minecraftsmp.dynamicshop.managers.CategoryConfigManager;
import org.minecraftsmp.dynamicshop.managers.ConfigCacheManager;
import org.minecraftsmp.dynamicshop.managers.CustomItemSupport;
import org.minecraftsmp.dynamicshop.managers.ShopDataManager;
import org.minecraftsmp.dynamicshop.transactions.Transaction;

/** bStats adoption and activity charts. Callbacks run on bStats' Bukkit main-thread task. */
public final class FeatureMetrics {
    private final DynamicShop plugin;
    private final boolean bstatsEnabled;
    private final TransactionMetrics transactions;
    private volatile boolean enabled;

    public FeatureMetrics(DynamicShop plugin) {
        this.plugin = plugin;
        File config = new File(plugin.getDataFolder().getParentFile(), "bStats/config.yml");
        bstatsEnabled = YamlConfiguration.loadConfiguration(config).getBoolean("enabled", true);
        transactions = new TransactionMetrics(this::isEnabled);
        reload();
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void reload() {
        enabled = bstatsEnabled && plugin.getConfig().getBoolean("metrics.feature-usage", true);
        if (!enabled) transactions.clear();
    }

    public void register(Metrics metrics) {
        metrics.addCustomChart(new AdvancedPie("custom_item_adders", () -> customItemAdders(CustomItemSupport::enabled)));
        pie(metrics, "players_per_server", () -> {
            int players = Bukkit.getOnlinePlayers().size();
            if (players == 0) return "0";
            if (players < 10) return "1-9";
            if (players < 25) return "10-24";
            if (players < 50) return "25-49";
            if (players < 100) return "50-99";
            return "100+";
        });
        pie(metrics, "gui_provider", CustomItemSupport::guiProvider);
        pie(metrics, "custom_gui_backgrounds", () -> plugin.getMessageManager().hasCustomGuiTitles() ? "Configured" : "None");
        pie(metrics, "custom_gui_icons", () -> hasCustomGuiIcons() ? "Configured" : "None");
        flag(metrics, "dynamic_pricing_enabled", () -> ConfigCacheManager.dynamicPricingEnabled);
        flag(metrics, "time_inflation_enabled", FeatureMetrics::timeInflationEnabled);
        pie(metrics, "transactions_per_server_last_30m", () -> transactions.snapshot().volumeBand());
        metrics.addCustomChart(new SingleLineChart("transactions_last_30m", () -> isEnabled() ? transactions.snapshot().total() : 0));
        metrics.addCustomChart(new SingleLineChart("server_buys_last_30m", () -> isEnabled() ? transactions.snapshot().buys() : 0));
        metrics.addCustomChart(new SingleLineChart("server_sells_last_30m", () -> isEnabled() ? transactions.snapshot().sells() : 0));
        metrics.addCustomChart(new SingleLineChart("player_shop_purchases_last_30m", () -> isEnabled() ? transactions.snapshot().playerShops() : 0));
        metrics.addCustomChart(new SingleLineChart("dynamic_pricing_trades_last_30m", () -> isEnabled() ? transactions.snapshot().dynamic() : 0));
        metrics.addCustomChart(new SingleLineChart("time_inflation_enabled_trades_last_30m", () -> isEnabled() ? transactions.snapshot().inflationEnabled() : 0));
    }

    private void pie(Metrics metrics, String id, Supplier<String> value) {
        metrics.addCustomChart(new SimplePie(id, () -> isEnabled() ? value.get() : null));
    }

    // Called only when bStats collects a report, never on the transaction path.
    Map<String, Integer> customItemAdders(Predicate<String> pluginEnabled) {
        if (!isEnabled()) return null;
        Map<String, Integer> values = new LinkedHashMap<>();
        for (String name : new String[] {"Oraxen", "Nexo", "ItemsAdder"}) {
            if (pluginEnabled.test(name)) values.put(name, 1);
        }
        if (values.isEmpty()) values.put("No custom adder", 1);
        return values;
    }

    private void flag(Metrics metrics, String id, BooleanSupplier value) {
        pie(metrics, id, () -> value.getAsBoolean() ? "Enabled" : "Disabled");
    }

    static boolean timeInflationEnabled() {
        return ConfigCacheManager.dynamicPricingEnabled && ConfigCacheManager.useTimeInflation
                && Double.isFinite(ConfigCacheManager.hourlyIncreasePercent) && ConfigCacheManager.hourlyIncreasePercent > 0;
    }

    private boolean hasCustomGuiIcons() {
        if (CustomItemSupport.isCustomItem(ConfigCacheManager.fillerMaterialStr)) return true;
        for (ItemCategory category : ItemCategory.values()) {
            if (CategoryConfigManager.getSlot(category) >= 0 && CustomItemSupport.isCustomItem(CategoryConfigManager.getIconName(category))) return true;
        }
        for (String id : new String[] {"shop_back_button", "shop_next_button", "shop_categories_button",
                "shop_search_button", "shop_page_button", "shop_filter_button"}) {
            if (CustomItemSupport.hasMenuItem(id)) return true;
        }
        return false;
    }

    public void record(Transaction tx) {
        if (!isEnabled()) return;
        boolean dynamic = false;
        if (ConfigCacheManager.dynamicPricingEnabled && tx.getItem() != null) {
            if (tx.getItem().startsWith("VARIANT:")) dynamic = true;
            else {
                Material material = Material.getMaterial(tx.getItem());
                dynamic = material != null && ShopDataManager.itemConfigs.containsKey(material);
            }
        }
        transactions.record(tx.getType() == Transaction.TransactionType.BUY
                ? TransactionMetrics.Kind.SERVER_BUY : TransactionMetrics.Kind.SERVER_SELL,
                dynamic, dynamic && timeInflationEnabled());
    }

    public void recordPlayerShopPurchase() {
        if (!isEnabled()) return;
        transactions.record(TransactionMetrics.Kind.PLAYER_SHOP, false, false);
    }

    public TransactionMetrics.Snapshot transactionSnapshot() { return transactions.snapshot(); }
}
