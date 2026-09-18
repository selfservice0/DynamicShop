package org.minecraftsmp.dynamicshop.managers;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.minecraftsmp.dynamicshop.DynamicShop;
import org.minecraftsmp.dynamicshop.support.PluginTestFixture;
import static org.junit.Assert.*;

public class EconomyAmountValidationTest {
    @Rule public TemporaryFolder directory = new TemporaryFolder();

    @Test
    public void invalidAmountsNeverReachTheEconomyProvider() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        DynamicShop plugin = PluginTestFixture.plugin(directory.getRoot());
        MultiCurrencyEconomyManager manager = new MultiCurrencyEconomyManager(plugin);
        Economy provider = PluginTestFixture.proxy(Economy.class, (object, method, args) -> {
            calls.incrementAndGet();
            if (method.getName().equals("getBalance")) return 100.0;
            return new EconomyResponse(0, 100, EconomyResponse.ResponseType.SUCCESS, "");
        });
        PluginTestFixture.set(manager, MultiCurrencyEconomyManager.class, "vaultEconomy", provider);
        for (double amount : new double[] {-1, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
            assertFalse(manager.charge(null, amount));
            assertFalse(manager.charge(null, amount, "coins"));
            assertFalse(manager.hasEnough(null, amount));
            assertFalse(manager.hasEnough(null, amount, "coins"));
            manager.deposit(null, amount);
            manager.deposit(null, amount, "coins");
            manager.depositOffline(null, amount);
            manager.depositOffline(null, UUID.randomUUID(), amount, "coins");
        }
        assertEquals("Invalid values must not read or alter balances", 0, calls.get());
        assertTrue(manager.charge(null, 0));
        assertTrue(manager.hasEnough(null, 0));
        assertEquals(0, calls.get());
        manager.deposit(null, 10);
        assertEquals("Valid deposits must still reach the provider", 1, calls.get());
    }
}
