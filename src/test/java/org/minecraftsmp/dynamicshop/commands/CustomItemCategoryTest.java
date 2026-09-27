package org.minecraftsmp.dynamicshop.commands;

import static org.junit.Assert.*;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.minecraftsmp.dynamicshop.DynamicShop;
import org.minecraftsmp.dynamicshop.category.ItemCategory;
import org.minecraftsmp.dynamicshop.managers.MessageManager;
import org.minecraftsmp.dynamicshop.managers.ShopDataManager;
import org.minecraftsmp.dynamicshop.managers.SpecialShopManager;
import org.minecraftsmp.dynamicshop.support.PlainTestStack;
import org.minecraftsmp.dynamicshop.support.PluginTestFixture;

import java.io.File;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

public class CustomItemCategoryTest {
    @Rule public TemporaryFolder directory = new TemporaryFolder();
    private DynamicShop plugin;
    private SpecialShopManager manager;
    private Player player;
    private Field serverField;
    private Object previousServer;
    private final List<String> messages = new ArrayList<>();

    @Before
    public void setUp() throws Exception {
        plugin = PluginTestFixture.plugin(directory.getRoot());
        manager = new SpecialShopManager(plugin);
        PluginTestFixture.set(plugin, DynamicShop.class, "specialShopManager", manager);
        YamlConfiguration messageConfig = new YamlConfiguration();
        messageConfig.set("messages.admin-server-shop-added", "{identifier}: {category}");
        PluginTestFixture.set(
                plugin.getMessageManager(), MessageManager.class, "messagesConfig", messageConfig);
        Plugin oraxen = PluginTestFixture.proxy(Plugin.class, (object, method, args) -> true);
        PluginManager plugins =
                PluginTestFixture.proxy(
                        PluginManager.class,
                        (object, method, args) -> {
                            if (method.getName().equals("getPlugin")) {
                                return "Oraxen".equals(args[0]) ? oraxen : null;
                            }
                            throw new AssertionError(method.getName());
                        });
        serverField = Bukkit.class.getDeclaredField("server");
        serverField.setAccessible(true);
        previousServer = serverField.get(null);
        serverField.set(
                null,
                PluginTestFixture.proxy(
                        Server.class,
                        (object, method, args) -> {
                            if (method.getName().equals("getPluginManager")) return plugins;
                            throw new AssertionError(method.getName());
                        }));
        PlayerInventory inventory =
                PluginTestFixture.proxy(
                        PlayerInventory.class,
                        (object, method, args) -> {
                            if (method.getName().equals("getItemInMainHand")) {
                                return new PlainTestStack(Material.BRICK, 1);
                            }
                            throw new AssertionError(method.getName());
                        });
        player =
                PluginTestFixture.proxy(
                        Player.class,
                        (object, method, args) ->
                                switch (method.getName()) {
                                    case "hasPermission" -> true;
                                    case "getInventory" -> inventory;
                                    case "sendMessage" -> {
                                        messages.add((String) args[0]);
                                        yield null;
                                    }
                                    default -> throw new AssertionError(method.getName());
                                });
    }

    @After
    public void tearDown() throws Exception {
        serverField.set(null, previousServer);
    }

    @Test
    public void customItemUsesRequestedCategoryAndKeepsItAfterReload() throws Exception {
        add("food");
        assertCategory(ItemCategory.FOOD);
        reloadSavedConfig();
        assertCategory(ItemCategory.FOOD);
    }

    @Test
    public void customPlaceholderCategoryIsSupported() throws Exception {
        add("CUSTOM_1");
        reloadSavedConfig();
        assertCategory(ItemCategory.CUSTOM_1);
    }

    @Test
    public void omittedCategoryUsesNormalMaterialClassification() {
        add();
        assertCategory(ShopDataManager.detectCategory(Material.BRICK));
    }

    @Test
    public void serverShopCanStillBeChosenExplicitly() throws Exception {
        add("SERVER_SHOP");
        reloadSavedConfig();
        assertCategory(ItemCategory.SERVER_SHOP);
    }

    @Test
    public void addingExistingCustomItemAgainUpdatesItsCategory() throws Exception {
        add("SERVER_SHOP");
        add("FARMING");
        reloadSavedConfig();
        assertCategory(ItemCategory.FARMING);
        assertEquals(1, manager.getAllSpecialItems().size());
    }

    @Test
    public void confirmationShowsTheActualCategoryAndIdentifier() {
        add("FOOD");
        assertEquals(List.of("shop_food: Food"), messages);
    }

    @Test
    public void oldEntriesWithoutCategoryKeepTheirServerShopDefault() throws Exception {
        add("FOOD");
        plugin.getConfig().set("special_items.shop_food.category", null);
        plugin.saveConfig();
        reloadSavedConfig();
        assertEquals(ItemCategory.SERVER_SHOP, manager.getSpecialItem("shop_food").getCategory());
    }

    private void add(String... category) {
        List<String> args = new ArrayList<>(List.of("add", "item", "25"));
        args.addAll(List.of(category));
        assertTrue(
                new ShopAdminCommand(plugin)
                        .onCommand(player, null, "shopadmin", args.toArray(String[]::new)));
    }

    private void assertCategory(ItemCategory expected) {
        var entry = manager.getSpecialItem("shop_food");
        assertNotNull(entry);
        assertEquals(expected, entry.getCategory());
        assertEquals(
                expected.name(), plugin.getConfig().getString("special_items.shop_food.category"));
        assertEquals("oraxen", entry.getDeliveryMethod());
        assertEquals("shop_food", entry.getNbt());
        assertEquals(25, entry.getPrice(), 0);
        assertTrue(entry.isServerShopItem());
    }

    private void reloadSavedConfig() throws Exception {
        YamlConfiguration saved = new YamlConfiguration();
        saved.load(new File(directory.getRoot(), "config.yml"));
        PluginTestFixture.set(plugin, JavaPlugin.class, "newConfig", saved);
        manager.reload();
    }
}
