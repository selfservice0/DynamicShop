package org.minecraftsmp.dynamicshop.gui;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.function.Consumer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import org.minecraftsmp.dynamicshop.DynamicShop;
import org.minecraftsmp.dynamicshop.category.*;
import org.minecraftsmp.dynamicshop.listeners.ShopListener;
import org.minecraftsmp.dynamicshop.managers.*;
import org.minecraftsmp.dynamicshop.support.*;
import org.minecraftsmp.dynamicshop.util.PaperCompat;
import static org.junit.Assert.*;

public class AdminCustomItemEditTest {
    @Rule public TemporaryFolder directory = new TemporaryFolder();
    private final Map<Field, Object> savedFields = new HashMap<>();
    private final Map<Field, Map<Object, Object>> savedMaps = new HashMap<>();
    private final Map<Field, Set<Object>> savedSets = new HashMap<>();
    private final Map<String, ItemStack> templates = new HashMap<>();
    private Field serverField;
    private Object previousServer;
    private DynamicShop plugin;
    private SpecialShopManager specials;
    private Player player;
    private AdminItemEditGUI latestEditor;
    private AdminSpecialItemEditGUI latestRewardEditor;
    private RecordingInput input;

    private static class RecordingInput extends InputManager {
        Consumer<Double> number;
        Consumer<Integer> integer;
        Consumer<String> text;
        RecordingInput(DynamicShop plugin) { super(plugin); }
        @Override public void requestNumber(Player p, String title, double current, Consumer<Double> callback) { number = callback; }
        @Override public void requestInt(Player p, String title, int current, Consumer<Integer> callback) { integer = callback; }
        @Override public void requestText(Player p, String title, String current, Consumer<String> callback) { text = callback; }
    }

    @SuppressWarnings("unchecked")
    @Before public void setUp() throws Exception {
        for (Field field : ShopDataManager.class.getDeclaredFields()) {
            if (!Modifier.isStatic(field.getModifiers())) continue;
            field.setAccessible(true);
            Object value = field.get(null);
            if (value instanceof Map<?, ?> map) {
                savedMaps.put(field, new HashMap<>((Map<Object, Object>) map));
                map.clear();
            } else if (value instanceof Set<?> set) {
                savedSets.put(field, new HashSet<>((Set<Object>) set));
                set.clear();
            } else if (!Modifier.isFinal(field.getModifiers())) savedFields.put(field, value);
        }
        for (Field field : ConfigCacheManager.class.getFields()) {
            if (Modifier.isStatic(field.getModifiers()) && !Modifier.isFinal(field.getModifiers())) {
                savedFields.put(field, field.get(null));
            }
        }
        plugin = PluginTestFixture.plugin(directory.getRoot());
        PluginTestFixture.set(null, ShopDataManager.class, "plugin", plugin);
        ConfigCacheManager.fillerMaterialStr = "GRAY_STAINED_GLASS_PANE";
        ConfigCacheManager.useTimeInflation = true;
        ConfigCacheManager.hourlyIncreasePercent = 2;
        ConfigCacheManager.maxPriceMultiplier = 20;
        ShopDataManager.itemConfigs.put(Material.IRON_AXE,
                new ShopDataManager.ShopItemConfig(80, 600.0, null, 500, null, false, false, null, null));
        ShopDataManager.setStockDirect(Material.IRON_AXE, 100);
        specials = new SpecialShopManager(plugin) {
            @Override public ItemStack getTradeTemplate(SpecialShopItem entry) {
                return entry == null ? null : templates.get(entry.getId());
            }
        };
        PluginTestFixture.set(plugin, DynamicShop.class, "specialShopManager", specials);
        ShopListener listener = new ShopListener(plugin) {
            @Override public void registerAdminEdit(Player p, AdminItemEditGUI gui) {
                super.registerAdminEdit(p, gui);
                latestEditor = gui;
            }
            @Override public void registerAdminSpecialEdit(Player p, AdminSpecialItemEditGUI gui) {
                super.registerAdminSpecialEdit(p, gui);
                latestRewardEditor = gui;
            }
        };
        PluginTestFixture.set(plugin, DynamicShop.class, "shopListener", listener);
        PlayerInventory held = PluginTestFixture.proxy(PlayerInventory.class, (object, method, args) -> {
            if (method.getName().equals("getItemInMainHand")) return new ItemStack(Material.DIAMOND);
            throw new AssertionError(method.getName());
        });
        UUID playerId = UUID.randomUUID();
        player = PluginTestFixture.proxy(Player.class, (object, method, args) -> switch (method.getName()) {
            case "getUniqueId" -> playerId;
            case "getInventory" -> held;
            case "sendMessage", "openInventory" -> null;
            default -> throw new AssertionError(method.getName());
        });
        ItemFactory factory = PluginTestFixture.proxy(ItemFactory.class, (object, method, args) -> switch (method.getName()) {
            case "getItemMeta" -> new BookTestStack(Map.of()).getItemMeta();
            case "asMetaFor" -> ((ItemMeta) args[0]).clone();
            case "isApplicable" -> true;
            case "equals" -> args[0] == args[1];
            default -> throw new AssertionError(method.getName());
        });
        BukkitScheduler scheduler = PluginTestFixture.proxy(BukkitScheduler.class, (object, method, args) -> {
            if (!method.getName().equals("runTask")) throw new AssertionError(method.getName());
            ((Runnable) args[1]).run();
            return null;
        });
        serverField = Bukkit.class.getDeclaredField("server");
        serverField.setAccessible(true);
        previousServer = serverField.get(null);
        serverField.set(null, PluginTestFixture.proxy(Server.class, (object, method, args) -> switch (method.getName()) {
            case "getItemFactory" -> factory;
            case "getScheduler" -> scheduler;
            case "getLogger" -> plugin.getLogger();
            case "createInventory" -> inventory((int) args[1]);
            default -> throw new AssertionError(method.getName());
        }));
        input = new org.objenesis.ObjenesisStd().newInstance(RecordingInput.class);
        PluginTestFixture.set(plugin, DynamicShop.class, "inputManager", input);
    }

    private Inventory inventory(int size) {
        ItemStack[] slots = new ItemStack[size];
        return PluginTestFixture.proxy(Inventory.class, (object, method, args) -> switch (method.getName()) {
            case "getSize" -> size;
            case "clear" -> {
                Arrays.fill(slots, null);
                yield null;
            }
            case "setItem" -> {
                slots[(int) args[0]] = (ItemStack) args[1];
                yield null;
            }
            case "getItem" -> slots[(int) args[0]];
            default -> throw new AssertionError(method.getName());
        });
    }

    @SuppressWarnings("unchecked")
    @After public void restore() throws Exception {
        for (var entry : savedMaps.entrySet()) {
            var map = (Map<Object, Object>) entry.getKey().get(null);
            map.clear();
            map.putAll(entry.getValue());
        }
        for (var entry : savedSets.entrySet()) {
            var set = (Set<Object>) entry.getKey().get(null);
            set.clear();
            set.addAll(entry.getValue());
        }
        for (var entry : savedFields.entrySet()) entry.getKey().set(null, entry.getValue());
        if (serverField != null) serverField.set(null, previousServer);
    }

    private void custom(String id, String delivery) {
        String path = "special_items." + id + ".";
        plugin.getConfig().set(path + "type", "server-shop");
        plugin.getConfig().set(path + "identifier", id);
        plugin.getConfig().set(path + "price", 31);
        plugin.getConfig().set(path + "display_material", "IRON_AXE");
        plugin.getConfig().set(path + "delivery_method", delivery);
        plugin.getConfig().set(path + "nbt", "provider:custom_axe");
        plugin.getConfig().set(path + "amount", 3);
        plugin.getConfig().set(path + "category", "SERVER_SHOP");
        templates.put(id, new BookTestStack(Material.IRON_AXE, Map.of()));
        specials.reload();
        ShopDataManager.setVariantStockDirect(id, 10);
        ShopDataManager.setVariantPurchasesDirect(id, 7);
    }

    private AdminItemEditGUI edit(String id) {
        return new AdminItemEditGUI(plugin, player, specials.getSpecialItem(id),
                new AdminShopBrowseGUI(plugin, player, ItemCategory.SERVER_SHOP));
    }

    @Test public void everyPhysicalProviderOpensTheSameControlsAsNormalItems() {
        var normal = new AdminItemEditGUI(plugin, player, Material.IRON_AXE, null);
        normal.render();
        for (String delivery : List.of("stored_item", "itemsadder", "nexo", "oraxen", "valhallammo")) {
            custom(delivery, delivery);
            var browser = new AdminShopBrowseGUI(plugin, player, ItemCategory.SERVER_SHOP);
            int slot = 0;
            while (!browser.getSpecialItemFromSlot(slot).getId().equals(delivery)) slot++;
            browser.handleClick(slot, true);
            assertEquals(delivery, latestEditor.getVariantId());
            assertEquals(45, latestEditor.getInventory().getSize());
            for (int control : new int[] {10, 11, 12, 14, 15, 16, 19, 20, 21, 23, 24, 25, 30, 32, 40}) {
                assertEquals(control + ":" + delivery,
                        PaperCompat.getPlainDisplayName(normal.getInventory().getItem(control).getItemMeta()),
                        PaperCompat.getPlainDisplayName(latestEditor.getInventory().getItem(control).getItemMeta()));
            }
            assertEquals(Material.TNT, latestEditor.getInventory().getItem(34).getType());
            assertNull(templates.get(delivery).getItemMeta().displayName());
        }
    }

    @Test public void stockAdjustmentsUseCustomLimitsAndDoNotCountAsPurchases() {
        custom("axe", "stored_item");
        custom("other", "stored_item");
        plugin.getConfig().set("special_items.axe.max-stock-storage", 20);
        plugin.getConfig().set("special_items.axe.min-stock-storage", 2);
        var gui = edit("axe");
        gui.handleClick(16, false); // +64, capped at 20
        assertEquals(20, ShopDataManager.getVariantStock("axe"), 0);
        gui.handleClick(10, false); // -64, floored at 2
        assertEquals(2, ShopDataManager.getVariantStock("axe"), 0);
        assertEquals(7, ShopDataManager.getVariantPurchases("axe"), 0);
        assertEquals(10, ShopDataManager.getVariantStock("other"), 0);
        assertEquals(100, ShopDataManager.getStock(Material.IRON_AXE), 0);
    }

    @Test public void customSettingsPersistAndInputReopensACustomEditor() {
        custom("axe", "oraxen");
        custom("other", "stored_item");
        var gui = edit("axe");
        gui.handleClick(19, false);
        input.number.accept(42.0);
        assertEquals("axe", latestEditor.getVariantId());
        assertEquals(42, specials.getSpecialItem("axe").getPrice(), 0);
        gui.handleClick(30, false);
        input.number.accept(150.0);
        gui.handleClick(32, false);
        input.integer.accept(90);
        gui.handleClick(23, false);
        gui.handleClick(25, false);
        gui.handleClick(24, false);
        gui.handleClick(21, false); // SERVER_SHOP wraps to All/MISC
        gui.handleClick(28, false); // change decorative icon, preserve delivery
        gui.handleClick(31, false);
        input.text.accept("shop.vip");
        var saved = YamlConfiguration.loadConfiguration(new java.io.File(directory.getRoot(), "config.yml"));
        assertEquals(150, saved.getDouble("special_items.axe.max-stock"), 0);
        assertEquals(90, saved.getInt("special_items.axe.max-stock-storage"));
        for (String flag : List.of("disable-buy", "disable-sell", "disabled")) assertTrue(saved.getBoolean("special_items.axe." + flag));
        assertEquals("MISC", saved.getString("special_items.axe.category"));
        assertEquals("DIAMOND", saved.getString("special_items.axe.display_material"));
        assertEquals("shop.vip", specials.getSpecialItem("axe").getRequiredPermission());
        assertEquals("provider:custom_axe", saved.getString("special_items.axe.nbt"));
        assertEquals("oraxen", saved.getString("special_items.axe.delivery_method"));
        assertEquals(3, saved.getInt("special_items.axe.amount"));
        assertEquals(31, specials.getSpecialItem("other").getPrice(), 0);
        assertEquals(80, ShopDataManager.getBasePrice(Material.IRON_AXE), 0);
        assertEquals(600, ShopDataManager.itemConfigs.get(Material.IRON_AXE).maxStock(), 0);
        gui.handleClick(30, true);
        gui.handleClick(32, true);
        assertFalse(plugin.getConfig().isSet("special_items.axe.max-stock"));
        assertFalse(plugin.getConfig().isSet("special_items.axe.max-stock-storage"));
        gui.handleClick(31, false);
        input.text.accept("none");
        assertNull(specials.getSpecialItem("axe").getRequiredPermission());
    }

    @Test public void shortageEditsAndInvalidNumbersDoNotTouchTheSharedMaterial() {
        custom("axe", "nexo");
        var gui = edit("axe");
        gui.handleClick(20, false);
        input.integer.accept(50);
        assertEquals(Math.log(1.5) / Math.log(1.02), ShopDataManager.getVariantShortageHours("axe"), 0.001);
        assertEquals(0, ShopDataManager.getHoursInShortage(Material.IRON_AXE), 0.001);
        gui.handleClick(19, false);
        input.number.accept(Double.NaN);
        assertEquals(31, specials.getSpecialItem("axe").getPrice(), 0);
        gui.handleClick(30, false);
        input.number.accept(Double.POSITIVE_INFINITY);
        assertFalse(plugin.getConfig().isSet("special_items.axe.max-stock"));
    }

    @Test public void deletedEntriesCannotBeResurrectedByPendingInputAndDeleteNeedsShift() {
        custom("axe", "stored_item");
        var gui = edit("axe");
        gui.handleClick(34, false, false);
        assertNotNull(specials.getSpecialItem("axe"));
        gui.handleClick(19, false);
        Consumer<Double> pending = input.number;
        gui.handleClick(34, false, true);
        assertNull(specials.getSpecialItem("axe"));
        pending.accept(123.0);
        gui.handleClick(16, false);
        assertFalse(plugin.getConfig().isSet("special_items.axe"));
        assertEquals(10, ShopDataManager.getVariantStock("axe"), 0);
    }

    @Test public void permissionRewardsKeepTheirRewardEditor() {
        specials.addPermissionItem("upgrade.node", null, 10, Material.PAPER, null);
        new AdminShopBrowseGUI(plugin, player, ItemCategory.PERMISSIONS).handleClick(0, true);
        assertNotNull(latestRewardEditor);
        assertNull(latestEditor);
    }

    @Test public void normalItemDeletionRemovesTheCatalogEntryAndSurvivesReload() throws Exception {
        custom("custom_axe", "stored_item");
        plugin.getConfig().set("items.IRON_AXE.base", 80);
        PluginTestFixture.set(null, ShopDataManager.class, "shopDataConfig", new YamlConfiguration());
        var dataFile = new java.io.File(directory.getRoot(), "shopdata.yml");
        PluginTestFixture.set(null, ShopDataManager.class, "shopDataFile", dataFile);
        ShopDataManager.saveDynamicData();
        var parent = new AdminShopBrowseGUI(plugin, player, ItemCategory.MISC);
        var gui = new AdminItemEditGUI(plugin, player, Material.IRON_AXE, parent);
        gui.render();
        assertEquals(Material.TNT, gui.getInventory().getItem(34).getType());
        gui.handleClick(34, false, false);
        assertTrue(ShopDataManager.itemConfigs.containsKey(Material.IRON_AXE));
        gui.handleClick(19, false);
        Consumer<Double> pending = input.number;
        gui.handleClick(34, false, true);
        pending.accept(123.0);
        gui.handleClick(16, false);
        assertFalse(ShopDataManager.itemConfigs.containsKey(Material.IRON_AXE));
        assertNull(parent.getItemFromSlot(0));
        assertFalse(plugin.getConfig().isSet("items.IRON_AXE"));
        assertEquals(0, ShopDataManager.getStock(Material.IRON_AXE), 0);
        assertFalse(YamlConfiguration.loadConfiguration(dataFile).isSet("items.IRON_AXE"));
        assertNotNull(specials.getSpecialItem("custom_axe"));
        assertEquals(10, ShopDataManager.getVariantStock("custom_axe"), 0);
        PluginTestFixture.set(plugin, org.bukkit.plugin.java.JavaPlugin.class, "classLoader", getClass().getClassLoader());
        plugin.reloadConfig();
        var load = ShopDataManager.class.getDeclaredMethod("loadConfigItems");
        load.setAccessible(true);
        load.invoke(null);
        assertFalse("Bundled defaults must not restore deleted entries", ShopDataManager.itemConfigs.containsKey(Material.IRON_AXE));
        assertFalse(ShopDataManager.getItemsInCategoryIncludeDisabled(ItemCategory.MISC).contains(Material.IRON_AXE));
    }
}
