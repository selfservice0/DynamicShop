package org.minecraftsmp.dynamicshop.metrics;

import java.nio.file.Files;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.minecraftsmp.dynamicshop.DynamicShop;
import org.minecraftsmp.dynamicshop.managers.ConfigCacheManager;
import org.minecraftsmp.dynamicshop.managers.MessageManager;
import org.minecraftsmp.dynamicshop.managers.ShopDataManager;
import org.minecraftsmp.dynamicshop.support.PluginTestFixture;
import org.minecraftsmp.dynamicshop.transactions.Transaction;
import org.minecraftsmp.dynamicshop.transactions.TransactionLogger;
import static org.junit.Assert.*;

public class FeatureMetricsTest {
    @Rule public TemporaryFolder directory = new TemporaryFolder();

    private DynamicShop plugin() throws Exception {
        var path = directory.newFolder().toPath().resolve("plugins/DynamicShop");
        Files.createDirectories(path);
        return PluginTestFixture.plugin(path.toFile());
    }

    @Test public void combinedAdderChartCountsEveryEnabledProviderAndOnlyReportsNoneWhenAbsent() throws Exception {
        DynamicShop plugin = plugin();
        FeatureMetrics metrics = new FeatureMetrics(plugin);
        assertEquals(java.util.Map.of("No custom adder", 1), metrics.customItemAdders(name -> false));
        for (String provider : new String[] {"Oraxen", "Nexo", "ItemsAdder"}) {
            assertEquals(java.util.Map.of(provider, 1), metrics.customItemAdders(provider::equals));
        }
        assertEquals(java.util.Map.of("Oraxen", 1, "Nexo", 1),
                metrics.customItemAdders(name -> !name.equals("ItemsAdder")));
        assertEquals(java.util.Map.of("Oraxen", 1, "Nexo", 1, "ItemsAdder", 1), metrics.customItemAdders(name -> true));
        plugin.getConfig().set("metrics.feature-usage", false);
        metrics.reload();
        assertNull(metrics.customItemAdders(name -> { throw new AssertionError("Disabled metrics queried plugins"); }));
    }

    @Test public void loggerCountsNewRecordsWithoutSendingTheirPrivateFields() throws Exception {
        DynamicShop plugin = plugin();
        FeatureMetrics metrics = new FeatureMetrics(plugin);
        PluginTestFixture.set(plugin, DynamicShop.class, "featureMetrics", metrics);
        TransactionLogger logger = new TransactionLogger(plugin);
        boolean previousDynamic = ConfigCacheManager.dynamicPricingEnabled;
        boolean previousInflation = ConfigCacheManager.useTimeInflation;
        double previousRate = ConfigCacheManager.hourlyIncreasePercent;
        var previousItem = ShopDataManager.itemConfigs.put(Material.DIAMOND,
                new ShopDataManager.ShopItemConfig(100, null, null, null, null, false, false, null, null));
        try {
            ConfigCacheManager.dynamicPricingEnabled = true;
            ConfigCacheManager.useTimeInflation = true;
            ConfigCacheManager.hourlyIncreasePercent = 2;
            logger.log(Transaction.now("private-player", Transaction.TransactionType.BUY, "DIAMOND", 64, 400,
                    "private-category", "private-note"));
            logger.log(Transaction.now("private-player", Transaction.TransactionType.BUY, "SERVER_SHOP:custom_item", 1, 100,
                    "private-category", "private-note"));
            metrics.recordPlayerShopPurchase();
            assertEquals(new TransactionMetrics.Snapshot(3, 2, 0, 1, 1, 1), metrics.transactionSnapshot());
            assertFalse(metrics.transactionSnapshot().toString().contains("private"));
            logger.getRecentTransactions();
            assertEquals(3, metrics.transactionSnapshot().total());
        } finally {
            ConfigCacheManager.dynamicPricingEnabled = previousDynamic;
            ConfigCacheManager.useTimeInflation = previousInflation;
            ConfigCacheManager.hourlyIncreasePercent = previousRate;
            if (previousItem == null) ShopDataManager.itemConfigs.remove(Material.DIAMOND);
            else ShopDataManager.itemConfigs.put(Material.DIAMOND, previousItem);
        }
    }

    @Test public void inflationRequiresTheMasterToggleAndAPositiveRate() {
        boolean dynamic = ConfigCacheManager.dynamicPricingEnabled, inflation = ConfigCacheManager.useTimeInflation;
        double rate = ConfigCacheManager.hourlyIncreasePercent;
        try {
            ConfigCacheManager.useTimeInflation = true;
            ConfigCacheManager.hourlyIncreasePercent = 2;
            ConfigCacheManager.dynamicPricingEnabled = false;
            assertFalse(FeatureMetrics.timeInflationEnabled());
            ConfigCacheManager.dynamicPricingEnabled = true;
            assertTrue(FeatureMetrics.timeInflationEnabled());
            ConfigCacheManager.hourlyIncreasePercent = 0;
            assertFalse(FeatureMetrics.timeInflationEnabled());
        } finally {
            ConfigCacheManager.dynamicPricingEnabled = dynamic;
            ConfigCacheManager.useTimeInflation = inflation;
            ConfigCacheManager.hourlyIncreasePercent = rate;
        }
    }

    @Test public void globalBstatsOptOutAndFeatureOptOutAreHonored() throws Exception {
        DynamicShop plugin = plugin();
        FeatureMetrics metrics = new FeatureMetrics(plugin);
        metrics.recordPlayerShopPurchase();
        plugin.getConfig().set("metrics.feature-usage", false);
        metrics.reload();
        plugin.getConfig().set("metrics.feature-usage", true);
        assertEquals(0, metrics.transactionSnapshot().total());
        metrics.reload();
        assertTrue(metrics.isEnabled());
        metrics.recordPlayerShopPurchase();
        assertEquals(1, metrics.transactionSnapshot().total());
        var config = plugin.getDataFolder().toPath().getParent().resolve("bStats/config.yml");
        Files.createDirectories(config.getParent());
        Files.writeString(config, "enabled: false\n");
        FeatureMetrics disabled = new FeatureMetrics(plugin);
        disabled.recordPlayerShopPurchase();
        assertFalse(disabled.isEnabled());
        assertEquals(0, disabled.transactionSnapshot().total());
    }

    @Test public void backgroundDetectionHandlesNativeAndLegacyTitlesWithoutReportingTheirText() throws Exception {
        DynamicShop plugin = plugin();
        MessageManager messages = plugin.getMessageManager();
        YamlConfiguration config = new YamlConfiguration();
        PluginTestFixture.set(messages, MessageManager.class, "messagesConfig", config);
        config.set("messages.gui-category-title", "<white>Categories");
        assertFalse(messages.hasCustomGuiTitles());
        config.set("messages.gui-category-title", "<glyph:private_id>Categories");
        assertTrue(messages.hasCustomGuiTitles());
        config.set("messages.gui-category-title", "\ua413Categories");
        assertTrue(messages.hasCustomGuiTitles());
    }
}
