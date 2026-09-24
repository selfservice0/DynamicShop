package org.minecraftsmp.dynamicshop.metrics;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import org.bukkit.Material;
import org.minecraftsmp.dynamicshop.managers.ConfigCacheManager;
import org.minecraftsmp.dynamicshop.managers.ShopDataManager;
import org.minecraftsmp.dynamicshop.support.PluginTestFixture;
import org.minecraftsmp.dynamicshop.transactions.Transaction;

/** Optional standalone timing probe; no hardware-dependent timing assertions in the test suite.
 * Run with the Maven test classpath and an empty scratch directory argument.
 * Measures tracking only, not economy, transaction logging, network submission, or a live server tick.
 */
public final class MetricsPerformanceCheck {
    public static void main(String[] args) throws Exception {
        Path folder = Path.of(args[0]).resolve("plugins/DynamicShop");
        Files.createDirectories(folder);
        var plugin = PluginTestFixture.plugin(folder.toFile());
        FeatureMetrics metrics = new FeatureMetrics(plugin);
        ShopDataManager.itemConfigs.put(Material.DIAMOND,
                new ShopDataManager.ShopItemConfig(100, null, null, null, null, false, false, null, null));
        ConfigCacheManager.dynamicPricingEnabled = true;
        ConfigCacheManager.useTimeInflation = true;
        ConfigCacheManager.hourlyIncreasePercent = 2;
        Transaction tx = Transaction.now("benchmark", Transaction.TransactionType.BUY,
                "DIAMOND", 64, 100, "test", "test");
        int count = 1_000_000;
        for (int i = 0; i < count; i++) metrics.record(tx);
        long[] elapsed = new long[5];
        for (int round = 0; round < elapsed.length; round++) {
            long start = System.nanoTime();
            for (int i = 0; i < count; i++) metrics.record(tx);
            elapsed[round] = System.nanoTime() - start;
        }
        if (metrics.transactionSnapshot().total() != count * 6)
            throw new AssertionError("Unexpected count (or benchmark crossed a 30-minute boundary)");
        Arrays.sort(elapsed);
        System.out.printf(java.util.Locale.ROOT,
                "Tracking: %,d calls/round, 5 rounds; median %.2f ms, %.3f microseconds/call; range %.2f-%.2f ms%n",
                count, elapsed[2] / 1e6, elapsed[2] / (count * 1000.0), elapsed[0] / 1e6, elapsed[4] / 1e6);
        plugin.getConfig().set("metrics.feature-usage", false);
        metrics.reload();
        for (int i = 0; i < count; i++) metrics.record(tx);
        if (metrics.transactionSnapshot().total() != 0) throw new AssertionError("Opt-out collected data");
        System.out.println("Opt-out: 1,000,000 calls retained zero counts.");
    }
}
