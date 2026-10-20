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
        withServer(oraxenEnabled, false, action);
    }

    private void withServer(boolean oraxenEnabled, boolean nexoEnabled, CheckedAction action) throws Exception {
        Field serverField = Bukkit.class.getDeclaredField("server");
        serverField.setAccessible(true);
        Object oldServer = serverField.get(null);
        DynamicShop oldPlugin = DynamicShop.getInstance();
        DynamicShop shop = PluginTestFixture.plugin(directory.getRoot());
        shop.getConfig().set("gui.custom_item_provider", nexoEnabled ? "auto" : "oraxen");
        Plugin oraxen = PluginTestFixture.proxy(Plugin.class, (object, method, args) -> switch (method.getName()) {
            case "isEnabled" -> oraxenEnabled;
            case "getName" -> "Oraxen";
            default -> throw new AssertionError(method.getName());
        });
        Plugin nexo = PluginTestFixture.proxy(Plugin.class, (object, method, args) -> switch (method.getName()) {
            case "isEnabled" -> nexoEnabled;
            case "getName" -> "Nexo";
            default -> throw new AssertionError(method.getName());
        });
        PluginManager manager = PluginTestFixture.proxy(PluginManager.class, (object, method, args) -> switch (method.getName()) {
            case "getPlugin" -> "Oraxen".equals(args[0]) && oraxenEnabled ? oraxen
                    : "Nexo".equals(args[0]) && nexoEnabled ? nexo : null;
            case "isPluginEnabled" -> "Oraxen".equals(args[0]) && oraxenEnabled || "Nexo".equals(args[0]) && nexoEnabled;
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
            NexoWrapper.invalidateCache();
            action.run();
        } finally {
            NexoWrapper.invalidateCache();
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

    @Test public void moonstoneActivationPreservesLabelsAndReplacesOldGlyphMarkup() throws Exception {
        withServer(true, () -> {
            var shop = DynamicShop.getInstance();
            var player = viewer();
            String legacy = "<white><shift:-48><glyph:old_menu><shift:-170><black><bold>Categories 日本語";
            assertEquals(legacy, org.minecraftsmp.dynamicshop.util.GuiTheme.title(shop, player,
                    "gui-category-title", legacy, 54, java.util.Map.of()));
            shop.getConfig().set("gui.theme", "moonstone");
            String title = org.minecraftsmp.dynamicshop.util.GuiTheme.title(shop, player,
                    "gui-category-title", legacy, 54, java.util.Map.of());
            assertTrue(title.contains("<glyph:dynamicshop_moonstone_"));
            assertFalse(title.contains("old_menu"));
            assertTrue(title.endsWith("Categories 日本語"));
            assertTrue(shop.getMessageManager().hasCustomGuiTitles());
        });
    }

    @Test public void moonstoneCoversAdminSellSearchAndDifferentInventoryHeights() throws Exception {
        withServer(true, () -> {
            var shop = DynamicShop.getInstance();
            shop.getConfig().set("gui.theme", "moonstone");
            var player = viewer();
            String[][] layouts = {
                {"admin-category-gui-title", "54", "dynamicshop_moonstone_admin_54"},
                {"admin-shop-gui-title", "54", "dynamicshop_moonstone_admin_54"},
                {"admin-item-edit-title", "45", "dynamicshop_moonstone_admin_45"},
                {"admin-special-edit-title", "54", "dynamicshop_moonstone_admin_54"},
                {"admin-config-gui-title", "54", "dynamicshop_moonstone_admin_54"},
                {"admin-category-edit-title", "36", "dynamicshop_moonstone_admin_36"},
                {"sell-gui-title", "54", "dynamicshop_moonstone_sell_54"},
                {"search-gui-title", "54", "dynamicshop_moonstone_list_54"},
                {"player-shop-browser-title", "54", "dynamicshop_moonstone_list_54"},
                {"item-action-title", "27", "dynamicshop_moonstone_action_27"},
                {"shop-gui-title", "27", "dynamicshop_moonstone_shop_27"},
                {"shop-gui-title", "36", "dynamicshop_moonstone_shop_36"},
                {"shop-gui-title", "45", "dynamicshop_moonstone_shop_45"}
            };
            for (String[] layout : layouts) {
                String title = org.minecraftsmp.dynamicshop.util.GuiTheme.title(shop, player, layout[0],
                        "&4Edit {item}", Integer.parseInt(layout[1]), java.util.Map.of("item", "Mending 日本語"));
                assertTrue(layout[0], title.contains("<glyph:" + layout[2] + ">"));
                assertTrue(title.endsWith("Edit Mending 日本語"));
                Component rendered = MessageManager.parseComponent(title, player);
                assertTrue(plain(rendered).contains("\ue500"));
                assertTrue(hasFont(rendered, Key.key("oraxen:shift")));
            }
            String unsupported = org.minecraftsmp.dynamicshop.util.GuiTheme.title(shop, player,
                    "shop-gui-title", "<glyph:old>Shop", 18, java.util.Map.of());
            assertEquals("§f§lShop", unsupported);
        });
    }

    @Test public void moonstoneKeepsBedrockAndUnavailableProviderTitlesReadable() throws Exception {
        withServer(true, () -> {
            var shop = DynamicShop.getInstance();
            shop.getConfig().set("gui.theme", "moonstone");
            var player = viewer();
            org.minecraftsmp.dynamicshop.util.BedrockUtil.setForceBedrock(player, true);
            try {
                assertFalse(org.minecraftsmp.dynamicshop.util.GuiTheme.enabled(shop, player));
                String title = org.minecraftsmp.dynamicshop.util.GuiTheme.title(shop, player,
                        "sell-gui-title", "&8Sell Items", 54, java.util.Map.of());
                assertEquals("Sell Items", plain(MessageManager.parseComponent(title, player)));
            } finally {
                org.minecraftsmp.dynamicshop.util.BedrockUtil.setForceBedrock(player, false);
            }
            shop.getConfig().set("gui.custom_item_provider", "none");
            assertFalse(org.minecraftsmp.dynamicshop.util.GuiTheme.enabled(shop, player));
        });
        withServer(false, () -> {
            var shop = DynamicShop.getInstance();
            shop.getConfig().set("gui.theme", "moonstone");
            assertEquals("§8Sell Items", org.minecraftsmp.dynamicshop.util.GuiTheme.title(shop, viewer(),
                    "sell-gui-title", "&8Sell Items", 54, java.util.Map.of()));
        });
    }

    @Test public void moonstoneSuppliesIndependentAnimatedIconsForEveryDefaultCategory() throws Exception {
        withServer(true, () -> {
            var shop = DynamicShop.getInstance();
            shop.getConfig().set("gui.theme", "moonstone");
            var player = viewer();
            int defaults = 0;
            for (var category : org.minecraftsmp.dynamicshop.category.ItemCategory.values()) {
                var icon = org.minecraftsmp.dynamicshop.util.GuiTheme.categoryIcon(shop, player, category);
                if (category.isCustomCategory()) {
                    assertNull(icon);
                } else {
                    defaults++;
                    assertNotNull(category.name(), icon);
                    icon.setAmount(37);
                    assertEquals(1, org.minecraftsmp.dynamicshop.util.GuiTheme.categoryIcon(shop, player, category).getAmount());
                }
            }
            assertEquals(11, defaults);
            org.minecraftsmp.dynamicshop.util.BedrockUtil.setForceBedrock(player, true);
            try {
                assertNull(org.minecraftsmp.dynamicshop.util.GuiTheme.categoryIcon(shop, player,
                        org.minecraftsmp.dynamicshop.category.ItemCategory.BLOCKS));
            } finally {
                org.minecraftsmp.dynamicshop.util.BedrockUtil.setForceBedrock(player, false);
            }
        });
    }

    @Test @SuppressWarnings("unchecked") public void themedCategoryDefaultsDoNotRewriteCustomIconsNamesOrPositions() throws Exception {
        withServer(true, () -> {
            var shop = DynamicShop.getInstance();
            shop.getConfig().set("gui.theme", "moonstone");
            var category = org.minecraftsmp.dynamicshop.category.ItemCategory.WOOD;
            var iconsField = CategoryConfigManager.class.getDeclaredField("categoryIcons");
            iconsField.setAccessible(true);
            var icons = (java.util.Map<org.minecraftsmp.dynamicshop.category.ItemCategory, String>) iconsField.get(null);
            var previous = new java.util.HashMap<>(icons);
            int slot = CategoryConfigManager.getSlot(category);
            String name = CategoryConfigManager.getDisplayName(category);
            try {
                icons.remove(category);
                var generated = CategoryConfigManager.getIconItem(category, viewer());
                assertEquals(Material.AMETHYST_SHARD, generated.getType());
                assertFalse(icons.containsKey(category));
                icons.put(category, "oraxen:shop_food");
                assertEquals(Material.BRICK, CategoryConfigManager.getIconItem(category, viewer()).getType());
                assertEquals("oraxen:shop_food", CategoryConfigManager.getIconName(category));
                assertEquals(slot, CategoryConfigManager.getSlot(category));
                assertEquals(name, CategoryConfigManager.getDisplayName(category));
            } finally {
                icons.clear();
                icons.putAll(previous);
            }
        });
    }

    @Test public void halloweenSwitchesLayoutsNavigationAndEveryDefaultCategory() throws Exception {
        withServer(true, () -> {
            var shop = DynamicShop.getInstance();
            shop.getConfig().set("gui.theme", "HALLOWEEN");
            var player = viewer();
            for (int slots : new int[] {27, 36, 45, 54}) {
                String title = org.minecraftsmp.dynamicshop.util.GuiTheme.title(shop, player,
                        "shop-gui-title", "&8Market 日本語", slots, java.util.Map.of());
                assertTrue(title.contains("dynamicshop_halloween_"));
                assertTrue(title.endsWith("Market 日本語"));
                assertTrue(plain(MessageManager.parseComponent(title, player)).contains("\ue501"));
            }
            assertTrue(org.minecraftsmp.dynamicshop.util.GuiTheme.title(shop, player,
                    "sell-gui-title", "Sell", 54, java.util.Map.of()).contains("dynamicshop_halloween_sell_54"));
            assertTrue(org.minecraftsmp.dynamicshop.util.GuiTheme.title(shop, player,
                    "admin-item-edit-title", "Edit", 45, java.util.Map.of()).contains("dynamicshop_halloween_admin_45"));
            for (var category : org.minecraftsmp.dynamicshop.category.ItemCategory.values()) {
                var icon = org.minecraftsmp.dynamicshop.util.GuiTheme.categoryIcon(shop, player, category);
                if (category.isCustomCategory()) {
                    assertNull(icon);
                } else {
                    assertNotNull(icon);
                    assertEquals(Material.JACK_O_LANTERN, icon.getType());
                    icon.setAmount(12);
                    assertEquals(1, org.minecraftsmp.dynamicshop.util.GuiTheme.categoryIcon(shop, player, category).getAmount());
                }
            }
            for (String name : new String[] {"back", "next", "search", "filter", "page", "categories"}) {
                assertEquals("halloween_" + name + "_button", org.minecraftsmp.dynamicshop.util.GuiTheme.itemId(
                        shop, player, "shop_" + name + "_button"));
            }
            assertEquals("halloween_confirm_button", org.minecraftsmp.dynamicshop.util.GuiTheme.itemId(
                    shop, player, "moonstone_confirm_button"));
            assertEquals("oraxen:shop_back_button", org.minecraftsmp.dynamicshop.util.GuiTheme.itemId(
                    shop, player, "oraxen:shop_back_button"));
            assertEquals("shop_food", org.minecraftsmp.dynamicshop.util.GuiTheme.itemId(shop, player, "shop_food"));
            shop.getConfig().set("gui.theme", "moonstone");
            assertEquals("moonstone_back_button", org.minecraftsmp.dynamicshop.util.GuiTheme.itemId(shop, player, "shop_back_button"));
        });
    }

    @Test public void halloweenPreservesCustomCategoryOverridesAndProviderFallbacks() throws Exception {
        withServer(true, () -> {
            var shop = DynamicShop.getInstance();
            shop.getConfig().set("gui.theme", "halloween");
            var player = viewer();
            var category = org.minecraftsmp.dynamicshop.category.ItemCategory.WOOD;
            var field = CategoryConfigManager.class.getDeclaredField("categoryIcons");
            field.setAccessible(true);
            @SuppressWarnings("unchecked")
            var icons = (java.util.Map<org.minecraftsmp.dynamicshop.category.ItemCategory, String>) field.get(null);
            var previous = new java.util.HashMap<>(icons);
            try {
                icons.put(category, "oraxen:shop_food");
                assertEquals(Material.BRICK, CategoryConfigManager.getIconItem(category, player).getType());
                icons.put(category, "OAK_LOG");
                assertEquals(Material.JACK_O_LANTERN, CategoryConfigManager.getIconItem(category, player).getType());
                assertEquals("OAK_LOG", icons.get(category));
                org.minecraftsmp.dynamicshop.util.BedrockUtil.setForceBedrock(player, true);
                assertFalse(org.minecraftsmp.dynamicshop.util.GuiTheme.enabled(shop, player));
                assertNull(org.minecraftsmp.dynamicshop.util.GuiTheme.categoryIcon(shop, player, category));
                assertEquals(Material.OAK_LOG, CategoryConfigManager.getIconItem(category, player).getType());
            } finally {
                icons.clear();
                icons.putAll(previous);
                org.minecraftsmp.dynamicshop.util.BedrockUtil.setForceBedrock(player, false);
            }
            for (String provider : new String[] {"none", "nexo"}) {
                shop.getConfig().set("gui.custom_item_provider", provider);
                assertFalse(org.minecraftsmp.dynamicshop.util.GuiTheme.enabled(shop, player));
                assertEquals("shop_back_button", org.minecraftsmp.dynamicshop.util.GuiTheme.itemId(shop, player, "shop_back_button"));
            }
        });
        withServer(false, () -> {
            var shop = DynamicShop.getInstance();
            shop.getConfig().set("gui.theme", "halloween");
            assertFalse(org.minecraftsmp.dynamicshop.util.GuiTheme.enabled(shop, viewer()));
        });
    }

    @Test public void nexoOnlyAutomaticallyRendersBothThemesWithNativeFontsAndIndependentIcons() throws Exception {
        withServer(false, true, () -> {
            var shop = DynamicShop.getInstance();
            var player = viewer();
            String[][] layouts = {
                {"gui-category-title", "54", "categories_54"}, {"shop-gui-title", "54", "shop_54"},
                {"sell-gui-title", "54", "sell_54"}, {"search-gui-title", "54", "list_54"},
                {"player-shop-browser-title", "54", "list_54"}, {"player-shop-view-title", "54", "shop_54"},
                {"item-action-title", "27", "action_27"}, {"admin-shop-gui-title", "54", "admin_54"},
                {"admin-category-gui-title", "54", "admin_54"}, {"admin-category-edit-title", "36", "admin_36"},
                {"admin-config-gui-title", "54", "admin_54"}, {"admin-item-edit-title", "45", "admin_45"},
                {"admin-special-edit-title", "54", "admin_54"}, {"shop-gui-title", "27", "shop_27"},
                {"shop-gui-title", "36", "shop_36"}, {"shop-gui-title", "45", "shop_45"}
            };
            for (String provider : new String[] {"auto", "nexo"}) {
                shop.getConfig().set("gui.custom_item_provider", provider);
                assertEquals("nexo", CustomItemSupport.guiProvider());
                for (String theme : new String[] {"moonstone", "halloween"}) {
                    shop.getConfig().set("gui.theme", theme);
                    assertTrue(org.minecraftsmp.dynamicshop.util.GuiTheme.enabled(shop, player));
                    assertTrue(shop.getMessageManager().hasCustomGuiTitles());
                    for (String[] layout : layouts) {
                        String title = org.minecraftsmp.dynamicshop.util.GuiTheme.title(shop, player, layout[0],
                                "<glyph:old><shift:-8><black><bold>Market {category}", Integer.parseInt(layout[1]),
                                java.util.Map.of("category", "日本語"));
                        assertTrue(title.contains("<glyph:dynamicshop_" + theme + "_"));
                        if (!layout[0].equals("gui-category-title")) {
                            assertTrue(title.contains("<glyph:dynamicshop_" + theme + "_" + layout[2] + ">"));
                        }
                        assertTrue(title.endsWith("Market 日本語"));
                        var component = MessageManager.parseComponent(title, player);
                        assertTrue(plain(component).contains(theme.equals("halloween") ? "\ue601" : "\ue600"));
                        assertTrue(hasFont(component, Key.key("dynamicshop:themes")));
                        assertTrue(hasFont(component, Key.key("nexo:shift")));
                        assertFalse(plain(component).contains("<glyph:"));
                    }
                    int defaults = 0;
                    for (var category : org.minecraftsmp.dynamicshop.category.ItemCategory.values()) {
                        var icon = org.minecraftsmp.dynamicshop.util.GuiTheme.categoryIcon(shop, player, category);
                        if (category.isCustomCategory()) {
                            assertNull(icon);
                        } else {
                            defaults++;
                            assertNotNull(icon);
                            assertEquals(theme.equals("halloween") ? Material.CARVED_PUMPKIN : Material.PRISMARINE_SHARD, icon.getType());
                            icon.setAmount(23);
                            assertEquals(1, org.minecraftsmp.dynamicshop.util.GuiTheme.categoryIcon(shop, player, category).getAmount());
                        }
                    }
                    assertEquals(11, defaults);
                    for (String name : new String[] {"back", "next", "search", "filter", "page", "categories"}) {
                        String id = org.minecraftsmp.dynamicshop.util.GuiTheme.itemId(shop, player, "shop_" + name + "_button");
                        var icon = CustomItemSupport.getMenuItem(id, player);
                        assertNotNull(icon);
                        assertEquals(theme.equals("halloween") ? Material.CARVED_PUMPKIN : Material.PRISMARINE_SHARD, icon.getType());
                    }
                    for (String name : new String[] {"confirm", "cancel", "total", "settings", "save"}) {
                        String id = org.minecraftsmp.dynamicshop.util.GuiTheme.itemId(shop, player, "moonstone_" + name + "_button");
                        assertNotNull(CustomItemSupport.getMenuItem(id, player));
                    }
                }
            }
        });
    }

    @Test public void themesUseTheSameSelectedProviderForIconsAndGlyphsWhenBothAreInstalled() throws Exception {
        withServer(true, true, () -> {
            var shop = DynamicShop.getInstance();
            var player = viewer();
            for (String theme : new String[] {"moonstone", "halloween"}) {
                shop.getConfig().set("gui.theme", theme);
                for (String provider : new String[] {"auto", "nexo", "oraxen"}) {
                    shop.getConfig().set("gui.custom_item_provider", provider);
                    boolean nexo = !provider.equals("oraxen");
                    assertEquals(nexo ? "nexo" : "oraxen", CustomItemSupport.guiProvider());
                    var icon = org.minecraftsmp.dynamicshop.util.GuiTheme.categoryIcon(shop, player,
                            org.minecraftsmp.dynamicshop.category.ItemCategory.BLOCKS);
                    assertEquals(nexo ? theme.equals("halloween") ? Material.CARVED_PUMPKIN : Material.PRISMARINE_SHARD
                            : theme.equals("halloween") ? Material.JACK_O_LANTERN : Material.AMETHYST_SHARD, icon.getType());
                    String title = org.minecraftsmp.dynamicshop.util.GuiTheme.title(shop, player,
                            "sell-gui-title", "&8Sell", 54, java.util.Map.of());
                    assertTrue(hasFont(MessageManager.parseComponent(title, player), Key.key(nexo ? "nexo:shift" : "oraxen:shift")));
                }
            }
        });
    }

    @Test public void nexoThemeKeepsCustomOverridesAndBedrockAndDisabledProviderFallbacks() throws Exception {
        withServer(false, true, () -> {
            var shop = DynamicShop.getInstance();
            var player = viewer();
            shop.getConfig().set("gui.theme", "halloween");
            var category = org.minecraftsmp.dynamicshop.category.ItemCategory.WOOD;
            var field = CategoryConfigManager.class.getDeclaredField("categoryIcons");
            field.setAccessible(true);
            @SuppressWarnings("unchecked")
            var icons = (java.util.Map<org.minecraftsmp.dynamicshop.category.ItemCategory, String>) field.get(null);
            var previous = new java.util.HashMap<>(icons);
            try {
                icons.put(category, "nexo:shop_food");
                assertEquals(Material.WHEAT, CategoryConfigManager.getIconItem(category, player).getType());
                assertEquals("nexo:shop_food", icons.get(category));
                assertEquals("nexo:shop_back_button", org.minecraftsmp.dynamicshop.util.GuiTheme.itemId(shop, player, "nexo:shop_back_button"));
                icons.put(category, "OAK_LOG");
                assertEquals(Material.CARVED_PUMPKIN, CategoryConfigManager.getIconItem(category, player).getType());
                org.minecraftsmp.dynamicshop.util.BedrockUtil.setForceBedrock(player, true);
                assertFalse(org.minecraftsmp.dynamicshop.util.GuiTheme.enabled(shop, player));
                assertNull(org.minecraftsmp.dynamicshop.util.GuiTheme.categoryIcon(shop, player, category));
                assertEquals(Material.OAK_LOG, CategoryConfigManager.getIconItem(category, player).getType());
                String title = org.minecraftsmp.dynamicshop.util.GuiTheme.title(shop, player,
                        "sell-gui-title", "<glyph:old><shift:-48>&8Sell", 54, java.util.Map.of());
                assertEquals("Sell", plain(MessageManager.parseComponent(title, player)));
            } finally {
                icons.clear();
                icons.putAll(previous);
                org.minecraftsmp.dynamicshop.util.BedrockUtil.setForceBedrock(player, false);
            }
            for (String provider : new String[] {"none", "oraxen"}) {
                shop.getConfig().set("gui.custom_item_provider", provider);
                assertFalse(org.minecraftsmp.dynamicshop.util.GuiTheme.enabled(shop, player));
                assertNull(org.minecraftsmp.dynamicshop.util.GuiTheme.categoryIcon(shop, player, category));
            }
            shop.getConfig().set("gui.custom_item_provider", "auto");
            for (String theme : new String[] {"default", "unknown"}) {
                shop.getConfig().set("gui.theme", theme);
                assertFalse(org.minecraftsmp.dynamicshop.util.GuiTheme.enabled(shop, player));
            }
        });
    }

    @Test public void missingOraxenThemeGlyphFallsBackWithoutRequiredIconOrNegativeSpacing() throws Exception {
        withServer(true, () -> {
            var shop = DynamicShop.getInstance();
            var player = viewer();
            shop.getConfig().set("gui.theme", "halloween");
            String id = "dynamicshop_halloween_sell_54";
            String title = org.minecraftsmp.dynamicshop.util.GuiTheme.title(shop, player,
                    "sell-gui-title", "&8Categories", 54, java.util.Map.of());
            io.th0rgal.oraxen.OraxenPlugin.MISSING.add(id);
            try {
                var nativeParser = net.kyori.adventure.text.minimessage.MiniMessage.builder()
                        .tags(net.kyori.adventure.text.minimessage.tag.resolver.TagResolver.resolver(
                                net.kyori.adventure.text.minimessage.tag.resolver.TagResolver.standard(),
                                io.th0rgal.oraxen.glyphs.GlyphTag.RESOLVER, io.th0rgal.oraxen.glyphs.ShiftTag.RESOLVER)).build();
                assertTrue(plain(nativeParser.deserialize(title)).contains("\ue5ff"));
                assertNull(OraxenWrapper.parseMiniMessage(title));
                Component fallback = MessageManager.parseComponent(title, player);
                assertEquals("Categories", plain(fallback));
                assertFalse(hasFont(fallback, Key.key("oraxen:shift")));
            } finally {
                io.th0rgal.oraxen.OraxenPlugin.MISSING.clear();
            }
            // A subsequent Oraxen reload can restore the same ID without a DynamicShop restart.
            assertTrue(plain(MessageManager.parseComponent(title, player)).contains("\ue501"));
        });
    }

    @Test public void oraxenGlyphRegistryRejectsRequiredFallbackAndAcceptsTheRequestedName() throws Exception {
        var registry = new OraxenWrapper.GlyphApi(io.th0rgal.oraxen.OraxenPlugin.get().getFontManager());
        assertTrue(registry.isValid("dynamicshop_moonstone_shop_54"));
        assertTrue(registry.isValid("dynamicshop_halloween_list_54"));
        assertTrue(registry.isValid("animated_title"));
        assertTrue(registry.isValid("reference_title"));
        assertFalse(registry.isValid("shop_gui_menu"));
        assertFalse(registry.isValid("missing_gui"));
        // Older font-manager APIs have no animated/reference lookup methods.
        var legacy = new OraxenWrapper.GlyphApi(new LegacyFontManager());
        assertTrue(legacy.isValid("legacy_title"));
        assertFalse(legacy.isValid("missing_gui"));
    }

    @Test public void registeredOraxenGuiWithMissingTextureFallsBackUntilPackReloadRestoresIt() throws Exception {
        withServer(true, () -> {
            var shop = DynamicShop.getInstance();
            shop.getConfig().set("gui.theme", "halloween");
            var player = viewer();
            String id = "dynamicshop_halloween_sell_54";
            String title = org.minecraftsmp.dynamicshop.util.GuiTheme.title(shop, player,
                    "sell-gui-title", "&8Categories", 54, java.util.Map.of());
            io.th0rgal.oraxen.OraxenPlugin.BROKEN_TEXTURES.add(id);
            try {
                var registry = new OraxenWrapper.GlyphApi(io.th0rgal.oraxen.OraxenPlugin.get().getFontManager());
                assertFalse(registry.isValid(id));
                var fallback = MessageManager.parseComponent(title, player);
                assertEquals("Categories", plain(fallback));
                assertFalse(hasFont(fallback, Key.key("oraxen:shift")));
            } finally {
                io.th0rgal.oraxen.OraxenPlugin.BROKEN_TEXTURES.clear();
            }
            assertTrue(plain(MessageManager.parseComponent(title, player)).contains("\ue501"));
        });
    }

    @Test public void additionalHolidayThemesCoverMenusDialogsControlsAndCategoryIcons() throws Exception {
        withServer(true, true, () -> {
            var shop = DynamicShop.getInstance();
            var player = viewer();
            String[] themes = {"valentines", "easter", "stpatricks", "summer"};
            Material[] materials = {Material.ROSE_BUSH, Material.RABBIT_FOOT, Material.EMERALD, Material.SUNFLOWER};
            for (String provider : new String[] {"oraxen", "nexo"}) {
                shop.getConfig().set("gui.custom_item_provider", provider);
                for (int index = 0; index < themes.length; index++) {
                    String theme = themes[index];
                    shop.getConfig().set("gui.theme", theme);
                    String glyph = Character.toString((provider.equals("oraxen") ? 0xe504 : 0xe604) + index);
                    for (int slots : new int[] {27, 36, 45, 54}) {
                        String title = org.minecraftsmp.dynamicshop.util.GuiTheme.title(shop, player,
                                "shop-gui-title", "&8Shop", slots, java.util.Map.of());
                        assertTrue(title.contains("dynamicshop_" + theme + "_shop_" + slots));
                        assertTrue(plain(MessageManager.parseComponent(title, player)).contains(glyph));
                    }
                    for (String key : new String[] {"sell-gui-title", "search-gui-title", "admin-shop-gui-title", "item-action-title"}) {
                        int slots = key.equals("item-action-title") ? 27 : 54;
                        String title = org.minecraftsmp.dynamicshop.util.GuiTheme.title(shop, player, key, "&8Menu", slots, java.util.Map.of());
                        assertTrue(title.contains("dynamicshop_" + theme + "_"));
                        assertTrue(plain(MessageManager.parseComponent(title, player)).contains(glyph));
                    }
                    String dialog = org.minecraftsmp.dynamicshop.util.GuiTheme.dialogTitle(shop, player, java.util.Map.of("item", "Diamond"));
                    assertTrue(dialog.contains("dynamicshop_" + theme + "_dialog"));
                    assertTrue(plain(MessageManager.parseComponent(dialog, player)).endsWith("Diamond"));
                    for (var category : org.minecraftsmp.dynamicshop.category.ItemCategory.values()) {
                        if (!category.name().startsWith("CUSTOM_")) {
                            assertEquals(materials[index], org.minecraftsmp.dynamicshop.util.GuiTheme.categoryIcon(shop, player, category).getType());
                        }
                    }
                    for (String control : new String[] {"back", "next", "search", "filter", "page", "categories", "confirm", "cancel", "total", "settings", "save"}) {
                        String id = org.minecraftsmp.dynamicshop.util.GuiTheme.itemId(shop, player, "moonstone_" + control + "_button");
                        assertEquals(theme + "_" + control + "_button", id);
                        assertEquals(materials[index], CustomItemSupport.getMenuItem(id, player).getType());
                    }
                    org.minecraftsmp.dynamicshop.util.BedrockUtil.setForceBedrock(player, true);
                    try { assertFalse(org.minecraftsmp.dynamicshop.util.GuiTheme.enabled(shop, player)); }
                    finally { org.minecraftsmp.dynamicshop.util.BedrockUtil.setForceBedrock(player, false); }
                }
            }
        });
    }

    @Test public void seasonalThemesUseCorrectProviderGlyphsAndAllBuiltInIcons() throws Exception {
        withServer(true, true, () -> {
            var shop = DynamicShop.getInstance();
            var player = viewer();
            for (String provider : new String[] {"oraxen", "nexo"}) {
                shop.getConfig().set("gui.custom_item_provider", provider);
                for (String theme : new String[] {"christmas", "newyear", "newyears"}) {
                    shop.getConfig().set("gui.theme", theme);
                    String selected = org.minecraftsmp.dynamicshop.util.GuiTheme.normalize(theme);
                    Material expected = selected.equals("christmas") ? Material.SPRUCE_SAPLING : Material.FIREWORK_ROCKET;
                    assertTrue(org.minecraftsmp.dynamicshop.util.GuiTheme.enabled(shop, player));
                    for (int slots : new int[] {27, 36, 45, 54}) {
                        String title = org.minecraftsmp.dynamicshop.util.GuiTheme.title(shop, player,
                                "shop-gui-title", "&8Seasonal shop", slots, java.util.Map.of());
                        assertTrue(title.contains("dynamicshop_" + selected + "_shop_" + slots));
                        String glyph = selected.equals("christmas") ? provider.equals("nexo") ? "\ue602" : "\ue502"
                                : provider.equals("nexo") ? "\ue603" : "\ue503";
                        assertTrue(plain(MessageManager.parseComponent(title, player)).contains(glyph));
                    }
                    String dialog = org.minecraftsmp.dynamicshop.util.GuiTheme.dialogTitle(shop, player, java.util.Map.of("item", "Diamond"));
                    assertTrue(dialog.contains("dynamicshop_" + selected + "_dialog"));
                    assertTrue(plain(MessageManager.parseComponent(dialog, player)).endsWith("Diamond"));
                    for (var category : org.minecraftsmp.dynamicshop.category.ItemCategory.values()) {
                        var icon = org.minecraftsmp.dynamicshop.util.GuiTheme.categoryIcon(shop, player, category);
                        if (!category.name().startsWith("CUSTOM_")) assertEquals(expected, icon.getType());
                    }
                    for (String control : new String[] {"back", "next", "search", "filter", "page", "categories", "confirm", "cancel", "total", "settings", "save"}) {
                        String id = org.minecraftsmp.dynamicshop.util.GuiTheme.itemId(shop, player, "moonstone_" + control + "_button");
                        assertEquals(selected + "_" + control + "_button", id);
                        assertEquals(expected, CustomItemSupport.getMenuItem(id, player).getType());
                    }
                    assertEquals("oraxen:my_custom_icon", org.minecraftsmp.dynamicshop.util.GuiTheme.itemId(shop, player, "oraxen:my_custom_icon"));
                    org.minecraftsmp.dynamicshop.util.BedrockUtil.setForceBedrock(player, true);
                    try { assertFalse(org.minecraftsmp.dynamicshop.util.GuiTheme.enabled(shop, player)); }
                    finally { org.minecraftsmp.dynamicshop.util.BedrockUtil.setForceBedrock(player, false); }
                }
            }
        });
    }

    @Test public void nativeQuantityDialogSwitchesThemeDespiteLegacyMessageGlyphs() throws Exception {
        withServer(true, true, () -> {
            var shop = DynamicShop.getInstance();
            var player = viewer();
            var messages = new org.bukkit.configuration.file.YamlConfiguration();
            messages.set("messages.dialog-title", "<white><shift:-48><glyph:dynamicshop_gui_dialog_2>"
                    + "<shift:-170><black><bold>Purchase {item}");
            PluginTestFixture.set(shop.getMessageManager(), MessageManager.class, "messagesConfig", messages);
            for (String provider : new String[] {"oraxen", "nexo", "auto"}) {
                shop.getConfig().set("gui.custom_item_provider", provider);
                // Repeat Halloween after switching back to ensure no stale glyph is cached.
                for (String theme : new String[] {"halloween", "moonstone", "halloween"}) {
                    shop.getConfig().set("gui.theme", theme);
                    String title = org.minecraftsmp.dynamicshop.util.GuiTheme.dialogTitle(shop, player,
                            java.util.Map.of("item", "Diamond"));
                    assertTrue(title.contains("<glyph:dynamicshop_" + theme + "_dialog>"));
                    assertFalse(title.contains("dynamicshop_gui_dialog_2"));
                    assertEquals(1, title.split("<glyph:", -1).length - 1);
                    Component parsed = MessageManager.parseComponent(title, player);
                    assertTrue(plain(parsed).endsWith("Purchase Diamond"));
                    boolean nexo = !provider.equals("oraxen");
                    assertTrue(plain(parsed).contains(theme.equals("halloween")
                            ? nexo ? "\ue601" : "\ue501" : nexo ? "\ue600" : "\ue500"));
                    assertTrue(hasFont(parsed, Key.key(nexo ? "nexo:shift" : "oraxen:shift")));
                }
            }
        });
    }

    @Test public void nativeQuantityDialogKeepsConfiguredTitleWhenThemeIsInactive() throws Exception {
        withServer(true, () -> {
            var shop = DynamicShop.getInstance();
            var player = viewer();
            var messages = new org.bukkit.configuration.file.YamlConfiguration();
            messages.set("messages.dialog-title", "<gold>Trade {item}");
            PluginTestFixture.set(shop.getMessageManager(), MessageManager.class, "messagesConfig", messages);
            for (String theme : new String[] {"default", "unknown"}) {
                shop.getConfig().set("gui.theme", theme);
                assertEquals("<gold>Trade Emerald", org.minecraftsmp.dynamicshop.util.GuiTheme.dialogTitle(
                        shop, player, java.util.Map.of("item", "Emerald")));
            }
            shop.getConfig().set("gui.theme", "halloween");
            shop.getConfig().set("gui.custom_item_provider", "none");
            assertEquals("<gold>Trade Emerald", org.minecraftsmp.dynamicshop.util.GuiTheme.dialogTitle(
                    shop, player, java.util.Map.of("item", "Emerald")));
        });
    }

    @Test public void missingNativeDialogGlyphKeepsReadableItemNameUntilReload() throws Exception {
        withServer(true, () -> {
            var shop = DynamicShop.getInstance();
            var player = viewer();
            shop.getConfig().set("gui.theme", "halloween");
            String title = org.minecraftsmp.dynamicshop.util.GuiTheme.dialogTitle(shop, player,
                    java.util.Map.of("item", "Diamond"));
            io.th0rgal.oraxen.OraxenPlugin.MISSING.add("dynamicshop_halloween_dialog");
            try {
                Component parsed = MessageManager.parseComponent(title, player);
                assertEquals("Diamond", plain(parsed));
                assertFalse(hasFont(parsed, Key.key("oraxen:shift")));
            } finally {
                io.th0rgal.oraxen.OraxenPlugin.MISSING.clear();
            }
            assertTrue(plain(MessageManager.parseComponent(title, player)).contains("\ue501"));
        });
    }

    @Test public void categoryShowcaseFitsConfiguredPositionsWithoutMovingOrHidingCategories() throws Exception {
        withServer(true, true, () -> {
            var shop = DynamicShop.getInstance();
            var player = viewer();
            var field = CategoryConfigManager.class.getDeclaredField("categorySlots");
            field.setAccessible(true);
            @SuppressWarnings("unchecked")
            var slots = (java.util.Map<org.minecraftsmp.dynamicshop.category.ItemCategory, Integer>) field.get(null);
            var previous = new java.util.HashMap<>(slots);
            try {
                for (String provider : new String[] {"oraxen", "nexo"}) {
                    shop.getConfig().set("gui.custom_item_provider", provider);
                    for (String theme : new String[] {"moonstone", "halloween"}) {
                        shop.getConfig().set("gui.theme", theme);
                        for (var category : org.minecraftsmp.dynamicshop.category.ItemCategory.values()) slots.put(category, -1);
                        var misc = org.minecraftsmp.dynamicshop.category.ItemCategory.MISC;
                        slots.put(misc, 12);
                        slots.put(org.minecraftsmp.dynamicshop.category.ItemCategory.BLOCKS, 41);
                        for (String layout : new String[] {"categories_54", "shop_54", "categories_wide_54"}) {
                            if (layout.equals("shop_54")) slots.put(misc, 20);
                            if (layout.equals("categories_wide_54")) slots.put(org.minecraftsmp.dynamicshop.category.ItemCategory.CUSTOM_1, 0);
                            var before = new java.util.HashMap<>(slots);
                            String title = org.minecraftsmp.dynamicshop.util.GuiTheme.title(shop, player,
                                    "gui-category-title", "&8Categories", 54, java.util.Map.of());
                            assertTrue(title.contains("<glyph:dynamicshop_" + theme + "_" + layout + ">"));
                            assertEquals(before, slots);
                            assertTrue(plain(MessageManager.parseComponent(title, player)).contains(theme.equals("halloween")
                                    ? provider.equals("nexo") ? "\ue601" : "\ue501"
                                    : provider.equals("nexo") ? "\ue600" : "\ue500"));
                        }
                    }
                }
            } finally {
                slots.clear();
                slots.putAll(previous);
            }
        });
    }

    @Test public void themedItemBrowsingMatchesTheCategoryChooserHeightWithoutChangingConfiguredFallbacks() throws Exception {
        withServer(true, true, () -> {
            var shop = DynamicShop.getInstance();
            var player = viewer();
            PluginTestFixture.set(shop, DynamicShop.class, "specialShopManager", new SpecialShopManager(shop));
            for (String provider : new String[] {"oraxen", "nexo"}) {
                shop.getConfig().set("gui.custom_item_provider", provider);
                for (String theme : org.minecraftsmp.dynamicshop.util.GuiTheme.names()) {
                    if (theme.equals("default")) continue;
                    shop.getConfig().set("gui.theme", theme);
                    for (int configured : new int[] {27, 36, 45, 54}) {
                        shop.getConfig().set("gui.shop_menu_size", configured);
                        var gui = new org.minecraftsmp.dynamicshop.gui.ShopGUI(shop, player,
                                org.minecraftsmp.dynamicshop.category.ItemCategory.PERMISSIONS);
                        assertEquals(theme + "/" + provider, 54, gui.getSize());
                        assertEquals(configured, shop.getConfig().getInt("gui.shop_menu_size"));
                    }
                }
            }
            shop.getConfig().set("gui.shop_menu_size", 36);
            shop.getConfig().set("gui.theme", "default");
            assertEquals(36, new org.minecraftsmp.dynamicshop.gui.ShopGUI(shop, player,
                    org.minecraftsmp.dynamicshop.category.ItemCategory.PERMISSIONS).getSize());
            shop.getConfig().set("gui.theme", "halloween");
            org.minecraftsmp.dynamicshop.util.BedrockUtil.setForceBedrock(player, true);
            try {
                assertEquals(36, new org.minecraftsmp.dynamicshop.gui.ShopGUI(shop, player,
                        org.minecraftsmp.dynamicshop.category.ItemCategory.PERMISSIONS).getSize());
            } finally {
                org.minecraftsmp.dynamicshop.util.BedrockUtil.setForceBedrock(player, false);
            }
            shop.getConfig().set("gui.custom_item_provider", "none");
            assertEquals(36, new org.minecraftsmp.dynamicshop.gui.ShopGUI(shop, player,
                    org.minecraftsmp.dynamicshop.category.ItemCategory.PERMISSIONS).getSize());
        });
    }

    public static final class LegacyFontManager {
        public io.th0rgal.oraxen.OraxenPlugin.Glyph getGlyphFromName(String name) {
            return new io.th0rgal.oraxen.OraxenPlugin.Glyph("legacy_title".equals(name) ? name : "required");
        }
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
