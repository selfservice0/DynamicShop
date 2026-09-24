package org.minecraftsmp.dynamicshop.managers;

import java.lang.reflect.Field;
import java.util.logging.Logger;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.minecraftsmp.dynamicshop.DynamicShop;
import org.minecraftsmp.dynamicshop.support.PlainTestStack;
import org.minecraftsmp.dynamicshop.support.PluginTestFixture;
import static org.junit.Assert.*;

public class OraxenSupportTest {
    @Rule public TemporaryFolder directory = new TemporaryFolder();

    @Test public void providerSelectionPreservesNexoAndHonorsExplicitChoice() {
        assertEquals("nexo", CustomItemSupport.selectProvider("auto", true, true));
        assertEquals("oraxen", CustomItemSupport.selectProvider("auto", false, true));
        assertEquals("none", CustomItemSupport.selectProvider("auto", false, false));
        assertEquals("oraxen", CustomItemSupport.selectProvider("ORAXEN", true, true));
        assertEquals("none", CustomItemSupport.selectProvider("oraxen", true, false));
        assertEquals("none", CustomItemSupport.selectProvider("none", true, true));
    }

    @Test public void customIdsPreserveProviderBoundaries() {
        assertTrue(CustomItemSupport.isCustomItem("oraxen:shop_food"));
        assertTrue(CustomItemSupport.isCustomItem("ORAXEN:MixedCaseId"));
        assertTrue(CustomItemSupport.isCustomItem("nexo:shop_food"));
        for (String invalid : new String[] {"oraxen:", "nexo: ", "minecraft:stone", "STONE", "unknown:item"}) {
            assertFalse(invalid, CustomItemSupport.isCustomItem(invalid));
        }
        assertFalse(CustomItemSupport.isCustomItem(null));
    }

    @Test public void currentAndLegacyApisReturnIndependentStacksAndIdentifyItems() throws Exception {
        for (Class<?> api : new Class<?>[] {io.th0rgal.oraxen.api.OraxenItems.class, LegacyItems.class}) {
            OraxenWrapper.ItemApi bridge = new OraxenWrapper.ItemApi(api);
            ItemStack first = bridge.getItem("shop_food");
            assertNotNull(first);
            first.setAmount(8);
            assertEquals(1, bridge.getItem("shop_food").getAmount());
            assertEquals("shop_food", bridge.getId(first));
            assertNull(bridge.getItem("missing"));
            assertNull(bridge.getId(new PlainTestStack(Material.STONE, 1)));
        }
    }

    @Test public void metricsRegistryCheckNeverBuildsItems() throws Exception {
        OraxenWrapper.ItemApi bridge = new OraxenWrapper.ItemApi(LookupOnlyItems.class);
        assertTrue(bridge.isValid("existing"));
        assertFalse(bridge.isValid("missing"));
        withServer(true, () -> {
            assertTrue(CustomItemSupport.hasMenuItem("shop_food"));
            assertTrue(CustomItemSupport.hasMenuItem("oraxen:shop_food"));
            assertFalse(CustomItemSupport.hasMenuItem("missing"));
            assertFalse(CustomItemSupport.hasMenuItem("nexo:shop_food"));
        });
        withServer(false, () -> assertFalse(CustomItemSupport.hasMenuItem("shop_food")));
    }

    public static final class LookupOnlyItems {
        private static final LookupOnlyBuilder BUILDER = new LookupOnlyBuilder();
        public static LookupOnlyBuilder getItemById(String id) {
            return "existing".equals(id) ? BUILDER : null;
        }
        public static String getIdByItem(ItemStack stack) { return null; }
    }

    public static final class LookupOnlyBuilder {
        public ItemStack build() { throw new AssertionError("Metrics must not build items"); }
    }

    @Test public void oraxenOnlyServerResolvesItemsAndNativeTitleTags() throws Exception {
        withServer(true, () -> {
            assertNotNull(CustomItemSupport.getItem("oraxen:shop_food"));
            assertNotNull(CustomItemSupport.getMenuItem("shop_food"));
            assertNull(CustomItemSupport.getItem("nexo:shop_food"));
            assertEquals("shop_food", OraxenWrapper.getCustomItemId(new PlainTestStack(Material.BRICK, 1)));
            assertEquals("oraxen:shop_food", CustomItemSupport.getItemIdentifier(new PlainTestStack(Material.BRICK, 1)));
            Component title = MessageManager.parseComponent("&f<shift:-48><glyph:dynamicshop_gui_menu>&0&lCategories");
            assertEquals("\uf810\uf80f\ua413Categories", plain(title));
            assertTrue("Native shift font must survive", hasFont(title, Key.key("oraxen:shift")));
            assertEquals("Categories", plain(MessageManager.parseComponent("&0&lCategories")));
            assertEquals("\ua413Categories", plain(MessageManager.parseComponent("&f\ua413&0Categories")));
        });
    }

    @Test public void absentProviderFallsBackWithoutLoadingOrDeliveringCustomItems() throws Exception {
        withServer(false, () -> {
            assertNull(OraxenWrapper.getItem("shop_food"));
            assertNull(OraxenWrapper.getCustomItemId(new PlainTestStack(Material.BRICK, 1)));
            assertNull(CustomItemSupport.getMenuItem("shop_food"));
            assertEquals("Categories", plain(MessageManager.parseComponent(
                    "<white><shift:-48><glyph:dynamicshop_gui_menu><black>Categories")));
        });
    }

    @Test public void categoryIconsResolveOraxenIdsWithoutNexo() throws Exception {
        withServer(true, () -> {
            var category = org.minecraftsmp.dynamicshop.category.ItemCategory.FOOD;
            String previous = CategoryConfigManager.getIconName(category);
            Field fileField = CategoryConfigManager.class.getDeclaredField("configFile");
            fileField.setAccessible(true);
            Object previousFile = fileField.get(null);
            fileField.set(null, new java.io.File(directory.getRoot(), "categories.yml"));
            try {
                CategoryConfigManager.setIcon(category, "oraxen:shop_food");
                assertEquals("oraxen:shop_food", CategoryConfigManager.getIconName(category));
                assertEquals(Material.BRICK, CategoryConfigManager.getIconItem(category).getType());
            } finally {
                CategoryConfigManager.setIcon(category, previous);
                fileField.set(null, previousFile);
            }
        });
    }

    @Test public void deliveryUsesTheRegisteredOraxenItemAndRejectsMissingIds() throws Exception {
        withServer(true, () -> {
            DynamicShop shop = DynamicShop.getInstance();
            SpecialShopManager manager = new SpecialShopManager(shop);
            manager.addServerShopItem("oraxen_test", "Test", 10, "shop_food", Material.BRICK, null, true);
            var entry = manager.getSpecialItem("oraxen_test");
            entry.setDeliveryMethod("oraxen");
            entry.setNbt("shop_food");
            entry.setAmount(3);
            java.util.List<ItemStack> delivered = new java.util.ArrayList<>();
            var inventory = PluginTestFixture.proxy(org.bukkit.inventory.PlayerInventory.class,
                    (object, method, args) -> switch (method.getName()) {
                        case "getStorageContents" -> new ItemStack[36];
                        case "addItem" -> {
                            delivered.addAll(java.util.Arrays.asList((ItemStack[]) args[0]));
                            yield new java.util.HashMap<Integer, ItemStack>();
                        }
                        default -> throw new AssertionError(method.getName());
                    });
            var player = PluginTestFixture.proxy(org.bukkit.entity.Player.class,
                    (object, method, args) -> switch (method.getName()) {
                        case "getInventory" -> inventory;
                        case "sendMessage" -> null;
                        default -> throw new AssertionError(method.getName());
                    });
            var give = SpecialShopManager.class.getDeclaredMethod("giveServerShopItem",
                    org.bukkit.entity.Player.class, org.minecraftsmp.dynamicshop.category.SpecialShopItem.class);
            give.setAccessible(true);
            assertEquals(true, give.invoke(manager, player, entry));
            assertEquals(1, delivered.size());
            assertEquals(Material.BRICK, delivered.get(0).getType());
            assertEquals(3, delivered.get(0).getAmount());
            entry.setNbt("missing");
            assertEquals(false, give.invoke(manager, player, entry));
            assertEquals(1, delivered.size());
        });
    }

    private void withServer(boolean oraxenEnabled, CheckedAction action) throws Exception {
        Field serverField = Bukkit.class.getDeclaredField("server");
        serverField.setAccessible(true);
        Object oldServer = serverField.get(null);
        DynamicShop oldPlugin = DynamicShop.getInstance();
        DynamicShop shop = PluginTestFixture.plugin(directory.getRoot());
        shop.getConfig().set("gui.custom_item_provider", "oraxen");
        Plugin oraxen = PluginTestFixture.proxy(Plugin.class, (object, method, args) -> switch (method.getName()) {
            case "isEnabled" -> oraxenEnabled;
            case "getName" -> "Oraxen";
            default -> throw new AssertionError(method.getName());
        });
        PluginManager manager = PluginTestFixture.proxy(PluginManager.class, (object, method, args) -> switch (method.getName()) {
            case "getPlugin" -> "Oraxen".equals(args[0]) && oraxenEnabled ? oraxen : null;
            case "isPluginEnabled" -> "Oraxen".equals(args[0]) && oraxenEnabled;
            default -> throw new AssertionError(method.getName());
        });
        Server server = PluginTestFixture.proxy(Server.class, (object, method, args) -> switch (method.getName()) {
            case "getPluginManager" -> manager;
            case "getLogger" -> Logger.getAnonymousLogger();
            default -> throw new AssertionError(method.getName());
        });
        try {
            serverField.set(null, server);
            PluginTestFixture.set(null, DynamicShop.class, "instance", shop);
            action.run();
        } finally {
            PluginTestFixture.set(null, DynamicShop.class, "instance", oldPlugin);
            serverField.set(null, oldServer);
        }
    }

    @Test public void bedrockTitlesRemovePackMarkupWhileJavaKeepsNativeGlyphs() throws Exception {
        withServer(true, () -> {
            var bedrock = viewer();
            var java = viewer();
            org.minecraftsmp.dynamicshop.util.BedrockUtil.setForceBedrock(bedrock, true);
            try {
                String title = "<white><shift:-48><glyph:dynamicshop_gui_menu><shift:-170><black><bold>Categories";
                Component clean = MessageManager.parseComponent(title, bedrock);
                assertEquals("Categories", plain(clean));
                assertFalse(hasFont(clean, Key.key("oraxen:shift")));
                assertTrue(plain(MessageManager.parseComponent(title, java)).contains("\ua413"));
                for (String label : new String[] {"All", "Buy / Sell", "Player Shops", "Alex's Shop", "Shop Admin"}) {
                    assertEquals(label, plain(MessageManager.parseComponent(title.replace("Categories", label), bedrock)));
                }
            } finally {
                org.minecraftsmp.dynamicshop.util.BedrockUtil.setForceBedrock(bedrock, false);
            }
        });
    }

    @Test public void bedrockNavigationSkipsCustomItemsWithoutChangingDeliveryOrJavaMenus() throws Exception {
        withServer(true, () -> {
            var bedrock = viewer();
            var java = viewer();
            org.minecraftsmp.dynamicshop.util.BedrockUtil.setForceBedrock(bedrock, true);
            try {
                assertNotNull(CustomItemSupport.getMenuItem("shop_food", java));
                assertNull(CustomItemSupport.getMenuItem("shop_food", bedrock));
                assertNull(CustomItemSupport.getMenuItem("oraxen:shop_food", bedrock));
                assertNotNull(CustomItemSupport.getItem("oraxen:shop_food"));
                assertNotNull(CustomItemSupport.getMenuItem("shop_food", java));
            } finally {
                org.minecraftsmp.dynamicshop.util.BedrockUtil.setForceBedrock(bedrock, false);
            }
        });
    }

    @Test public void bedrockLegacyGlyphsAreRemovedWithoutStrippingReadableUnicodeOrColors() throws Exception {
        var bedrock = viewer();
        org.minecraftsmp.dynamicshop.util.BedrockUtil.setForceBedrock(bedrock, true);
        try {
            assertEquals("Categories 日本語", plain(MessageManager.parseComponent("&f\ua413\uf810\udb80\udc00&0Categories 日本語", bedrock)));
            assertEquals("All", plain(MessageManager.parseComponent("<g:menu><s:-10><font:custom:gui>All</font>", bedrock)));
            assertEquals(net.kyori.adventure.text.Component.text("Buy", net.kyori.adventure.text.format.NamedTextColor.GREEN),
                    MessageManager.parseComponent("<glyph:menu>&aBuy", bedrock));
        } finally {
            org.minecraftsmp.dynamicshop.util.BedrockUtil.setForceBedrock(bedrock, false);
        }
    }

    private static org.bukkit.entity.Player viewer() {
        var id = java.util.UUID.randomUUID();
        return PluginTestFixture.proxy(org.bukkit.entity.Player.class, (object, method, args) -> switch (method.getName()) {
            case "getUniqueId" -> id;
            default -> throw new AssertionError(method.getName());
        });
    }

    private static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    private static boolean hasFont(Component component, Key key) {
        return key.equals(component.font()) || component.children().stream().anyMatch(child -> hasFont(child, key));
    }

    @FunctionalInterface private interface CheckedAction { void run() throws Exception; }

    public static final class LegacyItems {
        public static io.th0rgal.oraxen.api.OraxenItems.Builder itemFromId(String id) {
            return io.th0rgal.oraxen.api.OraxenItems.getItemById(id);
        }
        public static String idFromItem(ItemStack stack) {
            return io.th0rgal.oraxen.api.OraxenItems.getIdByItem(stack);
        }
    }
}
