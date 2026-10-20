package org.minecraftsmp.dynamicshop.managers;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.minecraftsmp.dynamicshop.DynamicShop;
import org.minecraftsmp.dynamicshop.category.ItemCategory;
import org.minecraftsmp.dynamicshop.support.PluginTestFixture;
import static org.junit.Assert.*;

public class RestockManagerTest {
    @Rule public TemporaryFolder directory = new TemporaryFolder();
    private final Map<Field, Map<Object, Object>> originalMaps = new HashMap<>();
    private final Map<Field, Set<Object>> originalSets = new HashMap<>();
    private final Map<Field, Object> originalFields = new HashMap<>();
    private final List<ScheduledTask> scheduled = new ArrayList<>();
    private Field serverField;
    private Object previousServer;
    private DynamicShop plugin;
    private SpecialShopManager specials;
    private RestockManager restock;
    private File stockFile;

    private static class ScheduledTask {
        final Runnable run;
        final long delay;
        final long period;
        boolean cancelled;
        ScheduledTask(Runnable run, long delay, long period) {
            this.run = run;
            this.delay = delay;
            this.period = period;
        }
        BukkitTask task() {
            return PluginTestFixture.proxy(BukkitTask.class, (object, method, args) -> switch (method.getName()) {
                case "isCancelled" -> cancelled;
                case "cancel" -> {
                    cancelled = true;
                    yield null;
                }
                default -> throw new AssertionError(method.getName());
            });
        }
    }

    @SuppressWarnings("unchecked")
    @Before public void setUp() throws Exception {
        for (Field field : ShopDataManager.class.getDeclaredFields()) {
            if (!Modifier.isStatic(field.getModifiers())) continue;
            field.setAccessible(true);
            Object value = field.get(null);
            if (value instanceof Map<?, ?> map) {
                originalMaps.put(field, new HashMap<>((Map<Object, Object>) map));
                map.clear();
            } else if (value instanceof Set<?> set) {
                originalSets.put(field, new HashSet<>((Set<Object>) set));
                set.clear();
            } else if (!Modifier.isFinal(field.getModifiers())) {
                originalFields.put(field, value);
            }
        }
        plugin = PluginTestFixture.plugin(directory.getRoot());
        specials = new SpecialShopManager(plugin);
        PluginTestFixture.set(plugin, DynamicShop.class, "specialShopManager", specials);
        restock = new RestockManager(plugin);
        setDataField("plugin", plugin);
        stockFile = new File(directory.getRoot(), "shopdata.yml");
        setDataField("shopDataFile", stockFile);
        setDataField("shopDataConfig", new YamlConfiguration());
        ShopDataManager.itemConfigs.put(Material.PAPER,
                new ShopDataManager.ShopItemConfig(12, null, null, null, null, false, false, null, null));
        ShopDataManager.stockMap.put(Material.PAPER, 7.0);
        Field categories = ShopDataManager.class.getDeclaredField("categoryItems");
        categories.setAccessible(true);
        ((Map<ItemCategory, List<Material>>) categories.get(null))
                .put(ItemCategory.CUSTOM_1, List.of(Material.PAPER));

        BukkitScheduler scheduler = PluginTestFixture.proxy(BukkitScheduler.class, (object, method, args) -> {
            if (!method.getName().equals("runTaskTimer")) throw new AssertionError(method.getName());
            ScheduledTask task = new ScheduledTask((Runnable) args[1], (long) args[2], (long) args[3]);
            scheduled.add(task);
            return task.task();
        });
        Server server = PluginTestFixture.proxy(Server.class, (object, method, args) -> switch (method.getName()) {
            case "getScheduler" -> scheduler;
            case "getLogger" -> plugin.getLogger();
            default -> throw new AssertionError(method.getName());
        });
        PluginTestFixture.set(plugin, JavaPlugin.class, "server", server);
        serverField = Bukkit.class.getDeclaredField("server");
        serverField.setAccessible(true);
        previousServer = serverField.get(null);
        serverField.set(null, server);
    }

    private void setDataField(String name, Object value) throws Exception {
        Field field = ShopDataManager.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(null, value);
    }

    @SuppressWarnings("unchecked")
    @After public void restore() throws Exception {
        if (restock != null) restock.shutdown();
        for (var entry : originalMaps.entrySet()) {
            var map = (Map<Object, Object>) entry.getKey().get(null);
            map.clear();
            map.putAll(entry.getValue());
        }
        for (var entry : originalSets.entrySet()) {
            var set = (Set<Object>) entry.getKey().get(null);
            set.clear();
            set.addAll(entry.getValue());
        }
        for (var entry : originalFields.entrySet()) entry.getKey().set(null, entry.getValue());
        if (serverField != null) serverField.set(null, previousServer);
    }

    private void custom(String id, String delivery, ItemCategory category) {
        String path = "special_items." + id + ".";
        plugin.getConfig().set(path + "type", "server-shop");
        plugin.getConfig().set(path + "identifier", id);
        plugin.getConfig().set(path + "price", 12);
        plugin.getConfig().set(path + "display_material", "PAPER");
        plugin.getConfig().set(path + "delivery_method", delivery);
        plugin.getConfig().set(path + "category", category.name());
    }

    private void startRule(ItemCategory category, double target) {
        specials.reload();
        plugin.getConfig().set("restock.enabled", true);
        plugin.getConfig().set("restock.rules", List.of(Map.of(
                "category", category.name(), "stock", target, "interval-minutes", 5)));
        restock.init();
    }

    @Test public void categoryTimerRestocksEveryPhysicalDeliveryByItsOwnId() {
        List<String> deliveries = List.of("itemsadder", "nexo", "oraxen", "valhallammo", "stored_item");
        for (String id : deliveries) {
            custom(id, id, ItemCategory.CUSTOM_1);
            ShopDataManager.setVariantStockDirect(id, id.equals("stored_item") ? 500 : -5);
            ShopDataManager.setVariantPurchasesDirect(id, 12);
            ShopDataManager.setVariantShortageHours(id, 24);
        }
        custom("other", "stored_item", ItemCategory.CUSTOM_2);
        ShopDataManager.setVariantStockDirect("other", 3);
        startRule(ItemCategory.CUSTOM_1, 200);
        assertEquals(1, scheduled.size());
        assertEquals(6000, scheduled.get(0).delay);
        assertEquals(6000, scheduled.get(0).period);
        scheduled.get(0).run.run();
        for (String id : deliveries) {
            assertEquals(id, 200, ShopDataManager.getVariantStock(id), 0);
            assertEquals(id, 12, ShopDataManager.getVariantPurchases(id), 0);
            assertEquals(id, 0, ShopDataManager.getVariantShortageHours(id), 0);
        }
        assertEquals(3, ShopDataManager.getVariantStock("other"), 0);
        assertEquals(200, ShopDataManager.getStock(Material.PAPER), 0);
    }

    @Test public void restockRespectsCustomLimitsInsteadOfTheSharedMaterialLimits() throws Exception {
        custom("capped", "oraxen", ItemCategory.CUSTOM_1);
        custom("floor", "nexo", ItemCategory.CUSTOM_1);
        custom("uncapped", "stored_item", ItemCategory.CUSTOM_1);
        plugin.getConfig().set("special_items.capped.max-stock-storage", 40);
        plugin.getConfig().set("special_items.floor.min-stock-storage", 250);
        ShopDataManager.itemConfigs.put(Material.PAPER,
                new ShopDataManager.ShopItemConfig(12, null, null, 2, null, false, false, null, null));
        startRule(ItemCategory.CUSTOM_1, 200);
        scheduled.get(0).run.run();
        assertEquals(40, ShopDataManager.getVariantStock("capped"), 0);
        assertEquals(250, ShopDataManager.getVariantStock("floor"), 0);
        assertEquals(200, ShopDataManager.getVariantStock("uncapped"), 0);
        assertEquals(2, ShopDataManager.getStock(Material.PAPER), 0);
        ShopDataManager.saveQueuedItems();
        var saved = YamlConfiguration.loadConfiguration(stockFile);
        assertEquals(40, saved.getDouble("variants.capped.stock"), 0);
        assertEquals(0, saved.getDouble("variants.capped.shortage_hours"), 0);
        assertEquals(200, saved.getDouble("variants.uncapped.stock"), 0);
    }

    @Test public void allCategoryIncludesCustomsButSkipsDisabledEntriesAndNonStockRewards() {
        custom("one", "stored_item", ItemCategory.CUSTOM_1);
        custom("two", "oraxen", ItemCategory.SERVER_SHOP);
        custom("disabled", "nexo", ItemCategory.CUSTOM_1);
        plugin.getConfig().set("special_items.disabled.disabled", true);
        custom("negative", "nexo", ItemCategory.CUSTOM_1);
        plugin.getConfig().set("special_items.negative.price", -1);
        custom("command", "command", ItemCategory.SERVER_SHOP);
        custom("plain", "item", ItemCategory.SERVER_SHOP);
        specials.addPermissionItem("test.upgrade", null, 10, Material.PAPER, null);
        specials.addGroupItem("upgrade", null, 10, Material.PAPER, null);
        specials.addCommandItem("grant", 10, "say hello", Material.PAPER, null);
        startRule(ItemCategory.MISC, 75);
        scheduled.get(0).run.run();
        assertEquals(Set.of("one", "two"), ShopDataManager.getAllVariantIds());
        assertEquals(75, ShopDataManager.getVariantStock("one"), 0);
        assertEquals(75, ShopDataManager.getVariantStock("two"), 0);
        assertEquals(75, ShopDataManager.getStock(Material.PAPER), 0);
    }

    @Test public void currentCategoryAndDeliveryAreUsedWhenTheTimerFires() {
        custom("moved", "stored_item", ItemCategory.CUSTOM_1);
        custom("converted", "oraxen", ItemCategory.CUSTOM_1);
        startRule(ItemCategory.CUSTOM_1, 100);
        plugin.getConfig().set("special_items.moved.category", "CUSTOM_2");
        plugin.getConfig().set("special_items.converted.delivery_method", "command");
        custom("new", "nexo", ItemCategory.CUSTOM_1);
        specials.reload();
        scheduled.get(0).run.run();
        assertEquals(Set.of("new"), ShopDataManager.getAllVariantIds());
        assertEquals(100, ShopDataManager.getVariantStock("new"), 0);
    }

    @Test public void disablingRestockAndReloadingCancelOldTimers() {
        custom("new", "oraxen", ItemCategory.CUSTOM_1);
        startRule(ItemCategory.CUSTOM_1, 100);
        ScheduledTask old = scheduled.get(0);
        restock.reload();
        assertTrue(old.cancelled);
        assertEquals(2, scheduled.size());
        restock.setRestockEnabled(false);
        assertTrue(scheduled.get(1).cancelled);
        assertEquals(2, scheduled.size());
        assertFalse(ShopDataManager.hasVariantData("new"));
    }
}
