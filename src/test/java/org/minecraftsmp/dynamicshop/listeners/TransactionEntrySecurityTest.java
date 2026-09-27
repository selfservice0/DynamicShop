package org.minecraftsmp.dynamicshop.listeners;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.minecraftsmp.dynamicshop.DynamicShop;
import org.minecraftsmp.dynamicshop.commands.ShopCommand;
import org.minecraftsmp.dynamicshop.managers.ConfigCacheManager;
import org.minecraftsmp.dynamicshop.managers.MultiCurrencyEconomyManager;
import org.minecraftsmp.dynamicshop.managers.PlayerShopManager;
import org.minecraftsmp.dynamicshop.models.PlayerShopListing;
import org.minecraftsmp.dynamicshop.support.PlainTestStack;
import org.minecraftsmp.dynamicshop.support.PluginTestFixture;
import static org.junit.Assert.*;

public class TransactionEntrySecurityTest {
    @Rule public TemporaryFolder directory = new TemporaryFolder();

    @Test
    public void sellCommandsCannotBypassGuiCooldown() throws Exception {
        DynamicShop plugin = PluginTestFixture.plugin(directory.getRoot());
        ShopListener listener = new ShopListener(plugin);
        PluginTestFixture.set(plugin, DynamicShop.class, "shopListener", listener);
        UUID id = UUID.randomUUID();
        Player player =
                PluginTestFixture.proxy(
                        Player.class,
                        (object, method, args) ->
                                switch (method.getName()) {
                                    case "getUniqueId" -> id;
                                    case "hasPermission" ->
                                            !args[0].equals("dynamicshop.bypass.cooldown");
                                    case "sendMessage" -> null;
                                    default ->
                                            throw new AssertionError(
                                                    "Cooldown must reject before "
                                                            + method.getName());
                                });
        long oldCooldown = ConfigCacheManager.transactionCooldownMs;
        try {
            ConfigCacheManager.transactionCooldownMs = 60_000;
            listener.recordTransaction(player);
            ShopCommand command = new ShopCommand(plugin);
            assertTrue(command.onCommand(player, null, "shop", new String[] {"sellhand"}));
            assertTrue(command.onCommand(player, null, "shop", new String[] {"sellall"}));
            listener.buyItem(player, Material.DIAMOND, 1, null);
            listener.sellItem(player, Material.DIAMOND, 1, null);
        } finally {
            ConfigCacheManager.transactionCooldownMs = oldCooldown;
        }
    }

    @Test
    public void invalidAndDisabledListingsNeverReachInventoryOrEconomy() throws Exception {
        DynamicShop plugin = PluginTestFixture.plugin(directory.getRoot());
        PlayerShopManager manager = new PlayerShopManager(plugin);
        PluginTestFixture.set(plugin, DynamicShop.class, "playerShopManager", manager);
        Player player =
                PluginTestFixture.proxy(
                        Player.class,
                        (object, method, args) ->
                                switch (method.getName()) {
                                    case "hasPermission" -> true;
                                    case "sendMessage" -> null;
                                    default ->
                                            throw new AssertionError(
                                                    "Rejected listing must not call "
                                                            + method.getName());
                                });
        ShopCommand command = new ShopCommand(plugin);
        PlayerShopListener listener = new PlayerShopListener(plugin);
        PlainTestStack stack = new PlainTestStack(Material.DIAMOND, 1);
        for (double price : new double[] {Double.NaN, Double.POSITIVE_INFINITY, -1, 0}) {
            assertTrue(
                    command.onCommand(
                            player, null, "shop", new String[] {"sell", Double.toString(price)}));
            assertFalse(manager.addListing(player, stack, price));
            purchase(
                    listener,
                    player,
                    new PlayerShopListing(UUID.randomUUID(), "seller", stack, price));
        }
        plugin.getConfig().set("player-shops.enabled", false);
        assertTrue(command.onCommand(player, null, "shop", new String[] {"sell", "10"}));
        assertFalse(manager.addListing(player, stack, 10));
        purchase(listener, player, new PlayerShopListing(UUID.randomUUID(), "seller", stack, 10));
        assertEquals(0, manager.getActiveShopOwners().size());
    }

    @Test
    public void offlineSellerReceivesOnePaymentBeforeBuyerDelivery() throws Exception {
        DynamicShop plugin = PluginTestFixture.plugin(directory.getRoot());
        UUID sellerId = UUID.randomUUID();
        List<String> actions = new ArrayList<>();
        OfflinePlayer seller =
                PluginTestFixture.proxy(
                        OfflinePlayer.class,
                        (object, method, args) -> {
                            if (method.getName().equals("getUniqueId")) return sellerId;
                            throw new AssertionError(method.getName());
                        });
        Server server =
                PluginTestFixture.proxy(
                        Server.class,
                        (object, method, args) -> {
                            assertEquals(sellerId, args[0]);
                            return switch (method.getName()) {
                                case "getPlayer" -> null;
                                case "getOfflinePlayer" -> seller;
                                default -> throw new AssertionError(method.getName());
                            };
                        });
        MultiCurrencyEconomyManager economy =
                new MultiCurrencyEconomyManager(plugin) {
                    @Override
                    public boolean hasEnough(Player p, double amount) {
                        assertEquals(25, amount, 0);
                        return true;
                    }

                    @Override
                    public boolean charge(Player p, double amount) {
                        assertEquals(25, amount, 0);
                        actions.add("charge");
                        return true;
                    }

                    @Override
                    public void depositOffline(OfflinePlayer p, double amount) {
                        assertSame(seller, p);
                        assertEquals(25, amount, 0);
                        actions.add("pay offline seller");
                    }

                    @Override
                    public void deposit(Player p, double amount) {
                        fail("Offline seller used online payment");
                    }
                };
        PlayerShopListing listing =
                new PlayerShopListing(
                        sellerId, "seller", new PlainTestStack(Material.DIAMOND, 3), 25);
        PlayerShopManager manager =
                new PlayerShopManager(plugin) {
                    @Override
                    public synchronized boolean removeListing(String id) {
                        assertEquals(listing.getListingId(), id);
                        actions.add("remove listing");
                        return true;
                    }
                };
        PlayerInventory inventory =
                PluginTestFixture.proxy(
                        PlayerInventory.class,
                        (object, method, args) -> {
                            if (method.getName().equals("getStorageContents"))
                                return new ItemStack[36];
                            if (method.getName().equals("addItem")) {
                                ItemStack delivered = ((ItemStack[]) args[0])[0];
                                assertEquals(Material.DIAMOND, delivered.getType());
                                assertEquals(3, delivered.getAmount());
                                actions.add("deliver");
                                return new HashMap<Integer, ItemStack>();
                            }
                            throw new AssertionError(method.getName());
                        });
        Player buyer =
                PluginTestFixture.proxy(
                        Player.class,
                        (object, method, args) ->
                                switch (method.getName()) {
                                    case "getInventory" -> inventory;
                                    case "getName" -> "buyer";
                                    case "sendMessage" -> null;
                                    default -> throw new AssertionError(method.getName());
                                });
        PluginTestFixture.set(plugin, DynamicShop.class, "economyManager", economy);
        PluginTestFixture.set(plugin, DynamicShop.class, "playerShopManager", manager);
        Field serverField = Bukkit.class.getDeclaredField("server");
        serverField.setAccessible(true);
        Object previousServer = serverField.get(null);
        try {
            serverField.set(null, server);
            purchase(new PlayerShopListener(plugin), buyer, listing);
            assertEquals(
                    List.of("charge", "pay offline seller", "deliver", "remove listing"), actions);
        } finally {
            serverField.set(null, previousServer);
        }
    }

    private static void purchase(
            PlayerShopListener listener, Player player, PlayerShopListing listing)
            throws Exception {
        Method method =
                PlayerShopListener.class.getDeclaredMethod(
                        "purchaseItem", Player.class, PlayerShopListing.class);
        method.setAccessible(true);
        method.invoke(listener, player, listing);
    }
}
