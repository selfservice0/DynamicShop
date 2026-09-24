package org.minecraftsmp.dynamicshop.gui;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.minecraftsmp.dynamicshop.DynamicShop;
import org.minecraftsmp.dynamicshop.category.ItemCategory;
import org.minecraftsmp.dynamicshop.listeners.ShopListener;
import org.minecraftsmp.dynamicshop.managers.ShopDataManager;
import org.minecraftsmp.dynamicshop.support.PluginTestFixture;
import org.minecraftsmp.dynamicshop.util.ShopItemNames;
import static org.junit.Assert.*;

public class CustomItemSearchTest {
    @Rule public TemporaryFolder directory = new TemporaryFolder();
    private static final Material BASE = Material.GOLDEN_HORSE_ARMOR;
    private final Map<String, Map<?, ?>> originals = new HashMap<>();
    private DynamicShop plugin;

    @Before
    public void setUp() throws Exception {
        for (String field : List.of("itemConfigs", "itemTemplates", "customNames")) {
            Map<?, ?> map = map(field);
            originals.put(field, new HashMap<>(map));
            map.clear();
        }
        ShopDataManager.itemConfigs.put(
                BASE,
                new ShopDataManager.ShopItemConfig(
                        100, null, null, null, null, false, false, null, null));
        plugin = PluginTestFixture.plugin(directory.getRoot());
        PluginTestFixture.set(plugin, DynamicShop.class, "shopListener", new ShopListener(plugin));
    }

    @After
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void tearDown() throws Exception {
        for (var entry : originals.entrySet()) {
            Map map = map(entry.getKey());
            map.clear();
            map.putAll(entry.getValue());
        }
    }

    @Test
    public void itemNameTemplateMatchesVisibleNameButNeverBaseMaterial() throws Exception {
        template(null, Component.text("Iridium Watering Can", NamedTextColor.LIGHT_PURPLE), false);
        assertEquals("Iridium Watering Can", ShopItemNames.getDisplayName(BASE));
        for (String query :
                List.of(
                        "iridium",
                        "watering can",
                        "IRIDIUM_WATERING_CAN",
                        "  iridium   watering  can ")) {
            assertEquals(List.of(BASE), search(query));
        }
        for (String query :
                List.of("golden horse armor", "GOLDEN_HORSE_ARMOR", "horse", "armor", "copper")) {
            assertTrue(search(query).isEmpty());
        }
    }

    @Test
    public void legacyDisplayNameTakesPriorityOverItemName() throws Exception {
        template(
                Component.text("Copper Watering Can"),
                Component.text("Iridium Watering Can"),
                false);
        assertEquals(List.of(BASE), search("copper watering can"));
        assertTrue(search("iridium").isEmpty());
        assertTrue(search("golden horse armor").isEmpty());
    }

    @Test
    public void configuredNameTakesPriorityAndFormattingIsNotSearchable() throws Exception {
        template(null, Component.text("Iridium Watering Can"), false);
        map("customNames").put(BASE, "&6Garden &lSprinkler");
        assertEquals("Garden Sprinkler", ShopItemNames.getDisplayName(BASE));
        assertEquals(List.of(BASE), search("garden sprinkler"));
        assertTrue(search("iridium").isEmpty());
        assertTrue(search("golden_horse_armor").isEmpty());
        assertTrue(search("&6").isEmpty());
    }

    @Test
    public void unnamedVanillaItemsStillMatchMaterialNamesWithSpacesOrUnderscores()
            throws Exception {
        for (String query : List.of("golden horse armor", "golden_horse_armor", "horse")) {
            assertEquals(List.of(BASE), search(query));
        }
        assertTrue(search("iridium").isEmpty());
        template(null, null, false);
        assertEquals(List.of(BASE), search("golden horse armor"));
    }

    @Test
    public void spigotItemNameFallbackAlsoFindsTheCustomItem() throws Exception {
        template(null, Component.text("Iridium Watering Can"), true);
        assertEquals(List.of(BASE), search("iridium watering can"));
        assertTrue(search("golden horse armor").isEmpty());
    }

    @Test
    public void transactionNameUsesTheDeliveredVariantInsteadOfTheBaseTemplate() throws Exception {
        template(null, Component.text("Iridium Watering Can"), false);
        ItemStack variant = ShopDataManager.getTemplate(BASE);
        template(null, Component.text("Copper Watering Can"), false);
        map("customNames").put(BASE, "&6Base Shop Rename");
        assertEquals("Iridium Watering Can", ShopItemNames.getDisplayName(BASE, variant));
        assertEquals("Base Shop Rename", ShopItemNames.getDisplayName(BASE, null));
    }

    private void template(Component displayName, Component itemName, boolean legacyOnly)
            throws Exception {
        ItemMeta meta =
                PluginTestFixture.proxy(
                        ItemMeta.class,
                        (object, method, args) ->
                                switch (method.getName()) {
                                    case "hasDisplayName" -> displayName != null;
                                    case "hasItemName" -> itemName != null;
                                    case "displayName" -> displayName;
                                    case "itemName" -> {
                                        if (legacyOnly)
                                            throw new UnsupportedOperationException(
                                                    "Spigot fallback");
                                        yield itemName;
                                    }
                                    case "getItemName" -> "§dIridium Watering Can";
                                    default ->
                                            throw new AssertionError(
                                                    "Unexpected metadata access: "
                                                            + method.getName());
                                });
        map("itemTemplates").put(BASE, new NamedStack(meta));
    }

    @SuppressWarnings("unchecked")
    private List<Material> search(String query) throws Exception {
        SearchResultsGUI gui =
                new SearchResultsGUI(plugin, null, query, ItemCategory.MISC) {
                    @Override
                    public void
                            open() {} // Exercise real filtering without opening a server inventory.
                };
        Field field = SearchResultsGUI.class.getDeclaredField("results");
        field.setAccessible(true);
        return (List<Material>) field.get(gui);
    }

    @SuppressWarnings("unchecked")
    private static Map<Object, Object> map(String name) throws Exception {
        Field field = ShopDataManager.class.getDeclaredField(name);
        field.setAccessible(true);
        return (Map<Object, Object>) field.get(null);
    }

    private static final class NamedStack extends ItemStack {
        private final ItemMeta meta;

        NamedStack(ItemMeta meta) {
            super();
            this.meta = meta;
        }

        @Override
        public Material getType() {
            return BASE;
        }

        @Override
        public boolean hasItemMeta() {
            return true;
        }

        @Override
        public ItemMeta getItemMeta() {
            return meta;
        }

        @Override
        public NamedStack clone() {
            return new NamedStack(meta);
        }
    }
}
