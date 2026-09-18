package org.minecraftsmp.dynamicshop.managers;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.minecraftsmp.dynamicshop.DynamicShop;
import org.minecraftsmp.dynamicshop.category.SpecialShopItem;
import org.minecraftsmp.dynamicshop.support.PluginTestFixture;
import static org.junit.Assert.*;

public class SpecialShopPurchaseSecurityTest {
    @Rule public TemporaryFolder directory = new TemporaryFolder();

    @Test
    public void replacedRemovedAndRestrictedEntriesCannotBePurchasedFromOldMenus() throws Exception {
        DynamicShop plugin = PluginTestFixture.plugin(directory.getRoot());
        List<Double> charges = new ArrayList<>();
        MultiCurrencyEconomyManager economy = new MultiCurrencyEconomyManager(plugin) {
            @Override public boolean charge(Player player, double amount) {
                charges.add(amount);
                return false; // Stop before executing any command or granting any reward.
            }
        };
        PluginTestFixture.set(plugin, DynamicShop.class, "economyManager", economy);
        Player player = PluginTestFixture.proxy(Player.class, (object, method, args) -> {
            if (method.getName().equals("hasPermission")) return false;
            if (method.getName().equals("sendMessage")) return null;
            throw new AssertionError("Unexpected player operation: " + method.getName());
        });
        SpecialShopManager manager = new SpecialShopManager(plugin);
        manager.addCommandItem("audit", 1, "say test-only", Material.PAPER, null);
        SpecialShopItem old = manager.getSpecialItem("cmd_audit");
        manager.updateItemPrice(old.getId(), 100);
        manager.purchase(player, old);
        assertTrue("Stale menus must not charge either the old or new price", charges.isEmpty());

        SpecialShopItem current = manager.getSpecialItem(old.getId());
        manager.purchase(player, current);
        assertEquals(List.of(100.0), charges);
        charges.clear();

        manager.updateItemRequiredPermission(old.getId(), "audit.vip");
        manager.purchase(player, current);
        manager.purchase(player, manager.getSpecialItem(old.getId()));
        assertTrue("Both stale and currently restricted entries must be denied", charges.isEmpty());
        SpecialShopItem removed = manager.getSpecialItem(old.getId());
        manager.removeSpecialItem(old.getId());
        manager.purchase(player, removed);
        assertTrue("Removed commands must not reach the economy or command dispatcher", charges.isEmpty());
    }
}
