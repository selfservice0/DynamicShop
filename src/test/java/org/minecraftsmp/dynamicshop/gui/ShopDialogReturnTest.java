package org.minecraftsmp.dynamicshop.gui;

import static org.junit.Assert.*;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.minecraftsmp.dynamicshop.DynamicShop;
import org.minecraftsmp.dynamicshop.category.ItemCategory;
import org.minecraftsmp.dynamicshop.listeners.ShopListener;
import org.minecraftsmp.dynamicshop.managers.ProtocolShopManager;
import org.minecraftsmp.dynamicshop.support.PluginTestFixture;
import org.objenesis.ObjenesisStd;

public class ShopDialogReturnTest {
    @Rule public TemporaryFolder directory = new TemporaryFolder();
    private DynamicShop plugin;
    private ShopListener listener;
    private ProtocolShopManager inventories;
    private Player player;
    private boolean online = true;
    private final List<Runnable> nextTick = new ArrayList<>();
    private final AtomicReference<Inventory> visible = new AtomicReference<>();
    private Field serverField;
    private Object previousServer;

    @Before public void setUp() throws Exception {
        plugin = PluginTestFixture.plugin(directory.getRoot());
        listener = new ShopListener(plugin);
        PluginTestFixture.set(plugin, DynamicShop.class, "shopListener", listener);
        inventories = new ObjenesisStd().newInstance(ProtocolShopManager.class);
        PluginTestFixture.set(inventories, ProtocolShopManager.class, "shopInventories", new HashMap<>());
        PluginTestFixture.set(plugin, DynamicShop.class, "protocolShopManager", inventories);
        player = PluginTestFixture.proxy(Player.class, (object, method, args) -> switch (method.getName()) {
            case "isOnline" -> online;
            case "getUniqueId" -> new java.util.UUID(0, 1);
            case "openInventory" -> {
                visible.set((Inventory) args[0]);
                yield null;
            }
            case "updateInventory" -> null;
            default -> throw new AssertionError(method.getName());
        });
        BukkitScheduler scheduler = PluginTestFixture.proxy(BukkitScheduler.class, (object, method, args) -> switch (method.getName()) {
            case "runTask" -> {
                nextTick.add((Runnable) args[1]);
                yield null;
            }
            case "runTaskLater" -> null; // Inventory hover-lore tasks are outside this test.
            default -> throw new AssertionError(method.getName());
        });
        serverField = Bukkit.class.getDeclaredField("server");
        serverField.setAccessible(true);
        previousServer = serverField.get(null);
        serverField.set(null, PluginTestFixture.proxy(Server.class, (object, method, args) -> switch (method.getName()) {
            case "getScheduler" -> scheduler;
            case "createInventory" -> inventory((int) args[1]);
            default -> throw new AssertionError(method.getName());
        }));
    }

    @After public void restoreServer() throws Exception {
        serverField.set(null, previousServer);
    }

    @Test public void shopReturnsAfterDialogCloseWithFreshInventoryAndBothRegistrations() throws Exception {
        RefreshShop shop = shop();
        shop.open();
        Inventory oldInventory = visible.get();
        close(oldInventory);
        assertEquals(0, inventories.getActiveShopCount());

        new ShopDialogManager(plugin).returnToShop(player, shop);
        assertNull(visible.get()); // Must wait until the dialog callback has finished closing.
        assertEquals(1, nextTick.size());
        nextTick.removeFirst().run();

        assertNotSame(oldInventory, visible.get());
        assertEquals(2, shop.renders);
        assertEquals(2, shop.getPage());
        assertTrue(shop.isCommandOpened());
        assertTrue((boolean) field(shop, ShopGUI.class, "hideOutOfStock"));
        assertTrue(inventories.isShopInventory(visible.get()));
        assertSame(shop, ((java.util.Map<?, ?>) field(listener, ShopListener.class, "openShop")).get(player));
        assertClickProtected();

        // A late close notification from the previous inventory cannot clear the new mapping.
        listener.onClose(new InventoryCloseEvent(view(oldInventory)));
        assertTrue(inventories.isShopInventory(visible.get()));
        assertClickProtected();
    }

    @Test public void searchReturnsToSameResultsPageAndRestoresClickProtection() throws Exception {
        RefreshSearch search = new ObjenesisStd().newInstance(RefreshSearch.class);
        PluginTestFixture.set(search, SearchResultsGUI.class, "plugin", plugin);
        PluginTestFixture.set(search, SearchResultsGUI.class, "player", player);
        PluginTestFixture.set(search, SearchResultsGUI.class, "pm", inventories);
        PluginTestFixture.set(search, SearchResultsGUI.class, "results", List.of());
        PluginTestFixture.set(search, SearchResultsGUI.class, "specialResults", List.of());
        PluginTestFixture.set(search, SearchResultsGUI.class, "page", 2);
        search.open();
        Inventory oldInventory = visible.get();
        close(oldInventory);

        new ShopDialogManager(plugin).returnToShop(player, search);
        assertNull(visible.get());
        nextTick.removeFirst().run();

        assertNotSame(oldInventory, visible.get());
        assertEquals(2, search.renders);
        assertEquals(2, field(search, SearchResultsGUI.class, "page"));
        assertTrue(inventories.isShopInventory(visible.get()));
        assertSame(search, ((java.util.Map<?, ?>) field(listener, ShopListener.class, "openSearch")).get(player));
        assertClickProtected();
    }

    @Test public void disconnectedPlayerDoesNotReopenOrRegisterInventory() throws Exception {
        RefreshShop shop = shop();
        new ShopDialogManager(plugin).returnToShop(player, shop);
        online = false;
        nextTick.removeFirst().run();
        assertNull(visible.get());
        assertEquals(0, shop.renders);
        assertEquals(0, inventories.getActiveShopCount());
    }

    private RefreshShop shop() throws Exception {
        RefreshShop shop = new ObjenesisStd().newInstance(RefreshShop.class);
        PluginTestFixture.set(shop, ShopGUI.class, "plugin", plugin);
        PluginTestFixture.set(shop, ShopGUI.class, "player", player);
        PluginTestFixture.set(shop, ShopGUI.class, "pm", inventories);
        PluginTestFixture.set(shop, ShopGUI.class, "category", ItemCategory.BLOCKS);
        PluginTestFixture.set(shop, ShopGUI.class, "size", 54);
        PluginTestFixture.set(shop, ShopGUI.class, "page", 2);
        PluginTestFixture.set(shop, ShopGUI.class, "commandOpened", true);
        PluginTestFixture.set(shop, ShopGUI.class, "hideOutOfStock", true);
        return shop;
    }

    private Inventory inventory(int size) {
        return PluginTestFixture.proxy(Inventory.class, (object, method, args) -> switch (method.getName()) {
            case "getSize" -> size;
            default -> throw new AssertionError(method.getName());
        });
    }

    private InventoryView view(Inventory inventory) {
        return PluginTestFixture.proxy(InventoryView.class, (object, method, args) -> switch (method.getName()) {
            case "getPlayer" -> player;
            case "getTopInventory" -> inventory;
            case "getInventory" -> null; // Outside click.
            case "convertSlot" -> args[0];
            default -> throw new AssertionError(method.getName());
        });
    }

    private void close(Inventory inventory) {
        listener.onClose(new InventoryCloseEvent(view(inventory)));
        visible.set(null);
    }

    private void assertClickProtected() {
        InventoryClickEvent click = new InventoryClickEvent(view(visible.get()),
                org.bukkit.event.inventory.InventoryType.SlotType.OUTSIDE, -999,
                ClickType.LEFT, InventoryAction.NOTHING);
        listener.onClick(click);
        assertTrue(click.isCancelled());
    }

    private Object field(Object object, Class<?> owner, String name) throws Exception {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(object);
    }

    // Keep real open()/registration behavior; omit item rendering that needs a game server.
    private static class RefreshShop extends ShopGUI {
        int renders;
        private RefreshShop() { super(null, null, null); }
        @Override public void render() { renders++; }
    }

    private static class RefreshSearch extends SearchResultsGUI {
        int renders;
        private RefreshSearch() { super(null, null, ""); }
        @Override public void render() { renders++; }
    }
}
