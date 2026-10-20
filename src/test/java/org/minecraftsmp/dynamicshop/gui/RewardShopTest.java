package org.minecraftsmp.dynamicshop.gui;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFactory;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.minecraftsmp.dynamicshop.DynamicShop;
import org.minecraftsmp.dynamicshop.category.ItemCategory;
import org.minecraftsmp.dynamicshop.category.SpecialShopItem;
import org.minecraftsmp.dynamicshop.managers.MultiCurrencyEconomyManager;
import org.minecraftsmp.dynamicshop.managers.MessageManager;
import org.minecraftsmp.dynamicshop.managers.PermissionsManager;
import org.minecraftsmp.dynamicshop.managers.ShopDataManager;
import org.minecraftsmp.dynamicshop.managers.SpecialShopManager;
import org.minecraftsmp.dynamicshop.support.BookTestStack;
import org.minecraftsmp.dynamicshop.support.PluginTestFixture;
import org.minecraftsmp.dynamicshop.support.TestPermissionProvider;
import org.minecraftsmp.dynamicshop.transactions.TransactionLogger;
import static org.junit.Assert.*;

public class RewardShopTest {
    @Rule public TemporaryFolder directory = new TemporaryFolder();
    private DynamicShop plugin;
    private SpecialShopManager specials;
    private TestPermissionProvider provider;
    private Player player;
    private final List<Double> charges = new ArrayList<>();
    private final List<Double> refunds = new ArrayList<>();
    private final List<String> commands = new ArrayList<>();
    private boolean commandSucceeds = true;
    private boolean commandThrows;
    private boolean delayCommandGrant;
    private boolean playerCacheCurrent = true;
    private Map<Material, ShopDataManager.ShopItemConfig> savedItems;
    private Field serverField;
    private Object previousServer;

    @Before public void setUp() throws Exception {
        savedItems = new HashMap<>(ShopDataManager.itemConfigs);
        ShopDataManager.itemConfigs.put(Material.ENDER_CHEST,
                new ShopDataManager.ShopItemConfig(80, null, null, null, null, false, false, null, null));
        plugin = PluginTestFixture.plugin(directory.getRoot());
        PluginTestFixture.set(plugin.getMessageManager(), MessageManager.class, "messagesConfig", bundledMessages());
        plugin.getConfig().set("economy.currency_format", "🪙");
        provider = new TestPermissionProvider();
        var permissions = new PermissionsManager(plugin);
        PluginTestFixture.set(permissions, PermissionsManager.class, "vaultPerms", provider);
        PluginTestFixture.set(plugin, DynamicShop.class, "permissionsManager", permissions);
        PluginTestFixture.set(plugin, DynamicShop.class, "transactionLogger", new TransactionLogger(plugin));
        var economy = new MultiCurrencyEconomyManager(plugin) {
            @Override public boolean charge(Player buyer, double price) {
                charges.add(price);
                return true;
            }
            @Override public void deposit(Player buyer, double price) {
                refunds.add(price);
            }
        };
        PluginTestFixture.set(plugin, DynamicShop.class, "economyManager", economy);
        specials = new SpecialShopManager(plugin);
        PluginTestFixture.set(plugin, DynamicShop.class, "specialShopManager", specials);
        player = PluginTestFixture.proxy(Player.class, (object, method, args) -> switch (method.getName()) {
            case "getName" -> "UpgradeBuyer";
            case "getUniqueId" -> UUID.fromString("00000000-0000-0000-0000-000000000002");
            case "hasPermission" -> playerCacheCurrent && provider.permissions(null).contains((String) args[0]);
            case "sendMessage" -> null;
            default -> throw new AssertionError(method.getName());
        });
        ItemFactory factory = PluginTestFixture.proxy(ItemFactory.class, (object, method, args) -> switch (method.getName()) {
            case "getItemMeta" -> new BookTestStack(Map.of()).getItemMeta();
            case "asMetaFor" -> ((ItemMeta) args[0]).clone();
            case "isApplicable" -> true;
            case "equals" -> args[0] == args[1];
            default -> throw new AssertionError(method.getName());
        });
        serverField = Bukkit.class.getDeclaredField("server");
        serverField.setAccessible(true);
        previousServer = serverField.get(null);
        serverField.set(null, PluginTestFixture.proxy(Server.class, (object, method, args) -> {
            if (method.getName().equals("getItemFactory")) return factory;
            if (method.getName().equals("getLogger")) return plugin.getLogger();
            if (method.getName().equals("getConsoleSender")) return null;
            if (method.getName().equals("dispatchCommand")) {
                String command = (String) args[1];
                commands.add(command);
                if (commandThrows) throw new IllegalStateException("Command plugin failed");
                if (!commandSucceeds) return false;
                if (delayCommandGrant) return true;
                String[] parts = command.split(" ");
                if (parts.length >= 6 && parts[3].equals("permission")) {
                    provider.permissions(null).add(parts[5]);
                } else if (parts.length >= 6 && parts[3].equals("parent")) {
                    provider.groups(null).add(parts[5]);
                }
                return true;
            }
            throw new AssertionError(method.getName());
        }));
    }

    @After public void restore() throws Exception {
        ShopDataManager.itemConfigs.clear();
        ShopDataManager.itemConfigs.putAll(savedItems);
        serverField.set(null, previousServer);
    }

    @Test public void rewardIconsUseConfiguredPriceWithoutSellingOrMaterialInflation() {
        for (SpecialShopItem entry : entries()) {
            List<String> lore = new ShopGUI(plugin, player, ItemCategory.PERMISSIONS)
                    .buildSpecialShopItem(entry).getItemMeta().getLore();
            assertTrue(lore.toString(), lore.stream().anyMatch(line -> line.contains("31.00")));
            assertFalse(lore.toString(), lore.stream().anyMatch(line -> line.contains("SELL") || line.contains("80.00") || line.contains("Stock") || line.contains("increase")));
        }
    }

    @Test public void changingRewardPriceUpdatesTheMenuPrice() {
        var entry = entries().get(0);
        specials.updateItemPrice(entry.getId(), 42);
        var current = specials.getSpecialItem(entry.getId());
        List<String> lore = new ShopGUI(plugin, player, ItemCategory.PERMISSIONS)
                .buildSpecialShopItem(current).getItemMeta().getLore();
        assertTrue(lore.toString(), lore.stream().anyMatch(line -> line.contains("42.00")));
        assertFalse(lore.toString(), lore.stream().anyMatch(line -> line.contains("31.00") || line.contains("80.00")));
    }

    @Test public void alreadyOwnedPermissionsAndGroupsAreRejectedBeforeCharging() {
        provider.permissions(null).add("enderchest.upgrade1");
        provider.groups(null).add("ENDUPGRADE1");
        for (SpecialShopItem entry : entries()) {
            assertTrue(specials.ownsReward(player, entry));
            var icon = new ShopGUI(plugin, player, ItemCategory.PERMISSIONS).buildSpecialShopItem(entry);
            assertTrue(icon.getItemMeta().getLore().toString().contains("already own"));
            specials.purchase(player, entry);
        }
        assertTrue(charges.isEmpty());
        assertEquals(0, provider.grants);
    }

    @Test public void firstPurchaseSucceedsAndRepeatingItDoesNotChargeAgainInAnotherCategory() {
        for (SpecialShopItem entry : entries()) {
            entry.setCategory(ItemCategory.CUSTOM_1);
            specials.purchase(player, entry);
            assertTrue(specials.ownsReward(player, entry));
            specials.purchase(player, entry);
        }
        assertEquals(List.of(31.0, 31.0), charges);
        assertEquals(2, provider.grants);
        assertTrue(refunds.isEmpty());
        assertEquals(2, plugin.getTransactionLogger().getTotalTransactions());
    }

    @Test public void worldScopedOwnershipDoesNotUseAnotherWorldsPermissions() {
        var permissions = plugin.getPermissionsManager();
        provider.permissions(null).add("enderchest.upgrade1");
        provider.groups("nether").add("EndUpgrade1");
        assertFalse(permissions.hasPermission(player, "enderchest.upgrade1", "world"));
        assertTrue(permissions.isInGroup(player, "endupgrade1", "nether"));
        assertFalse(permissions.isInGroup(player, "endupgrade1", "world"));
    }

    @Test public void failedPermissionAndGroupGrantsRefundTheConfiguredPrice() {
        provider.allowGrants = false;
        for (SpecialShopItem entry : entries()) {
            specials.purchase(player, entry);
            assertFalse(specials.ownsReward(player, entry));
        }
        assertEquals(List.of(31.0, 31.0), charges);
        assertEquals(charges, refunds);
        assertEquals(0, plugin.getTransactionLogger().getTotalTransactions());
    }

    @Test public void luckPermsCommandsCannotChargeTwiceEvenWithStalePlayerPermissions() {
        playerCacheCurrent = false;
        for (String command : List.of("lp user {player} permission set group.endupgrade1 true",
                "/luckperms:lp user {uuid} parent add EndUpgrade1")) {
            specials.addCommandItem("upgrade", 31, command, Material.ENDER_CHEST, null);
            SpecialShopItem entry = specials.getSpecialItem("cmd_upgrade");
            entry.setCategory(ItemCategory.CUSTOM_1);
            specials.purchase(player, entry);
            assertTrue(specials.ownsReward(player, entry));
            specials.purchase(player, entry);
            specials.reload();
            specials.purchase(player, specials.getSpecialItem(entry.getId()));
        }
        assertEquals(List.of(31.0, 31.0), charges);
        assertEquals(2, commands.size());
        assertTrue(commands.get(1), commands.get(1).contains(player.getUniqueId().toString()));
        assertTrue(refunds.isEmpty());
        assertEquals(2, plugin.getTransactionLogger().getTotalTransactions());
    }

    @Test public void commandRewardIconsUseOwnPriceAndShowExistingOwnership() {
        specials.addCommandItem("upgrade", 31, "lp user {player} permission set group.endupgrade1 true",
                Material.ENDER_CHEST, null);
        var entry = specials.getSpecialItem("cmd_upgrade");
        specials.updateItemPrice(entry.getId(), 42);
        entry = specials.getSpecialItem(entry.getId());
        provider.permissions(null).add("group.endupgrade1");
        List<String> lore = new ShopGUI(plugin, player, ItemCategory.PERMISSIONS)
                .buildSpecialShopItem(entry).getItemMeta().getLore();
        assertTrue(lore.toString(), lore.toString().contains("42.00"));
        assertTrue(lore.toString(), lore.toString().contains("already own"));
        assertFalse(lore.toString(), lore.toString().contains("SELL") || lore.toString().contains("80.00"));
        specials.purchase(player, entry);
        assertTrue(charges.isEmpty());
        assertTrue(commands.isEmpty());
    }

    @Test public void serverShopCommandDeliveryAlsoChecksOwnership() {
        String path = "special_items.upgrade.";
        plugin.getConfig().set(path + "type", "server-shop");
        plugin.getConfig().set(path + "identifier", "upgrade");
        plugin.getConfig().set(path + "price", 31);
        plugin.getConfig().set(path + "delivery_method", "command");
        plugin.getConfig().set(path + "nbt", "lp user {uuid} permission set group.endupgrade1 true");
        specials.reload();
        var entry = specials.getSpecialItem("upgrade");
        specials.purchase(player, entry);
        specials.purchase(player, entry);
        assertEquals(List.of(31.0), charges);
        assertEquals(1, commands.size());
    }

    @Test public void customCommandsCanUseExplicitOwnershipAndAllowIntentionalRepeats() {
        specials.addCommandItem("upgrade", 31, "custom upgrade {player}", Material.ENDER_CHEST, null);
        var entry = specials.getSpecialItem("cmd_upgrade");
        String path = "special_items.cmd_upgrade.";
        plugin.getConfig().set(path + "owned_permission", "enderchest.upgrade1");
        plugin.getConfig().set(path + "ownership_world", "world");
        provider.permissions("world").add("enderchest.upgrade1");
        specials.purchase(player, entry);
        assertTrue(charges.isEmpty());
        plugin.getConfig().set(path + "allow_repeat_purchase", true);
        specials.purchase(player, entry);
        specials.purchase(player, entry);
        assertEquals(List.of(31.0, 31.0), charges);
        assertEquals(2, commands.size());
    }

    @Test public void unrelatedCommandsRemainRepeatableAndFailedCommandsAreRefunded() {
        specials.addCommandItem("reward", 31, "say hello {player}", Material.PAPER, null);
        var entry = specials.getSpecialItem("cmd_reward");
        specials.purchase(player, entry);
        specials.purchase(player, entry);
        assertEquals(2, commands.size());
        commandSucceeds = false;
        specials.purchase(player, entry);
        commandThrows = true;
        specials.purchase(player, entry);
        assertEquals(List.of(31.0, 31.0, 31.0, 31.0), charges);
        assertEquals(List.of(31.0, 31.0), refunds);
        assertEquals(2, plugin.getTransactionLogger().getTotalTransactions());
    }

    @Test public void worldScopedCommandsAndExplicitGroupsUseTheirOwnScope() {
        specials.addCommandItem("upgrade", 31,
                "lp user {player} permission set enderchest.upgrade1 true world=nether",
                Material.ENDER_CHEST, null);
        var entry = specials.getSpecialItem("cmd_upgrade");
        provider.permissions(null).add("enderchest.upgrade1");
        assertFalse(specials.ownsReward(player, entry));
        provider.permissions("nether").add("enderchest.upgrade1");
        specials.purchase(player, entry);
        assertTrue(charges.isEmpty());
        plugin.getConfig().set("special_items.cmd_upgrade.owned_group", "EndUpgrade1");
        plugin.getConfig().set("special_items.cmd_upgrade.ownership_world", "world");
        assertFalse(specials.ownsReward(player, entry));
        provider.groups("world").add("ENDUPGRADE1");
        specials.purchase(player, entry);
        assertTrue(charges.isEmpty());
        assertTrue(commands.isEmpty());
    }

    @Test public void rewardSellClicksNeverReachTheEconomy() {
        var rewards = new ArrayList<>(entries());
        specials.addCommandItem("upgrade", 31,
                "lp user {player} permission set group.endupgrade1 true", Material.ENDER_CHEST, null);
        rewards.add(specials.getSpecialItem("cmd_upgrade"));
        var listener = new org.minecraftsmp.dynamicshop.listeners.ShopListener(plugin);
        for (var entry : rewards) {
            for (var category : List.of(ItemCategory.PERMISSIONS, ItemCategory.CUSTOM_1)) {
                entry.setCategory(category);
                listener.handleItemClick(player, null, Material.ENDER_CHEST, entry, true, false);
                listener.handleItemClick(player, null, Material.ENDER_CHEST, entry, true, true);
            }
        }
        assertTrue(charges.isEmpty());
        assertTrue(refunds.isEmpty());
        assertTrue(commands.isEmpty());
        assertEquals(0, provider.grants);
        assertEquals(0, plugin.getTransactionLogger().getTotalTransactions());
    }

    @Test public void delayedCommandGrantsBlockRapidClicksAndFailedDispatchAllowsRetry() {
        specials.addCommandItem("upgrade", 31,
                "lp user {player} permission set group.endupgrade1 true", Material.ENDER_CHEST, null);
        var entry = specials.getSpecialItem("cmd_upgrade");
        commandSucceeds = false;
        specials.purchase(player, entry);
        assertFalse(specials.ownsReward(player, entry));
        assertEquals(List.of(31.0), refunds);
        commandSucceeds = true;
        delayCommandGrant = true;
        specials.purchase(player, entry);
        assertTrue(provider.permissions(null).isEmpty());
        specials.purchase(player, entry);
        specials.reload();
        specials.purchase(player, specials.getSpecialItem(entry.getId()));
        assertEquals(List.of(31.0, 31.0), charges);
        assertEquals(2, commands.size());
        specials.addCommandItem("duplicate", 31,
                "lp user {uuid} permission set group.endupgrade1 true", Material.ENDER_CHEST, null);
        specials.purchase(player, specials.getSpecialItem("cmd_duplicate"));
        assertEquals("Another entry granting the same pending permission must also be blocked", 2, commands.size());
        provider.permissions(null).add("group.endupgrade1");
        assertTrue(specials.ownsReward(player, entry));
        provider.permissions(null).clear();
        assertFalse("Confirmed ownership must clear the pending guard", specials.ownsReward(player, entry));
    }

    @SuppressWarnings("unchecked")
    @Test public void pendingGuardExpiresIfACommandNeverGrantsOwnership() throws Exception {
        specials.addCommandItem("upgrade", 31,
                "lp user {player} permission set group.endupgrade1 true", Material.ENDER_CHEST, null);
        var entry = specials.getSpecialItem("cmd_upgrade");
        delayCommandGrant = true;
        specials.purchase(player, entry);
        assertTrue(specials.ownsReward(player, entry));
        Field pending = SpecialShopManager.class.getDeclaredField("pendingCommandRewards");
        pending.setAccessible(true);
        var rewards = (Map<Object, Long>) pending.get(specials);
        rewards.replaceAll((key, started) -> System.nanoTime() - java.util.concurrent.TimeUnit.SECONDS.toNanos(31));
        assertFalse(specials.ownsReward(player, entry));
        assertTrue(rewards.isEmpty());
        specials.purchase(player, entry);
        assertEquals(List.of(31.0, 31.0), charges);
    }

    private List<SpecialShopItem> entries() {
        specials.addPermissionItem("enderchest.upgrade1", null, 31, Material.ENDER_CHEST, null);
        specials.addGroupItem("EndUpgrade1", null, 31, Material.ENDER_CHEST, null);
        return List.of(specials.getSpecialItem("enderchest_upgrade1"), specials.getSpecialItem("group_endupgrade1"));
    }

    @Test public void permissionAndGroupTooltipLabelsUseMessagesAndSubstituteTheirValues() throws Exception {
        var messages = bundledMessages();
        messages.set("messages.special-lore-type-permission", "&bClase: &dPermiso");
        messages.set("messages.special-lore-permission-node", "<gray>Nodo: <white>{permission}");
        messages.set("messages.special-lore-type-group", "&bClase: &dGrupo");
        messages.set("messages.special-lore-group-name", "&7Grupo: &f{group}");
        PluginTestFixture.set(plugin.getMessageManager(), MessageManager.class, "messagesConfig", messages);
        var rewards = entries();
        var gui = new ShopGUI(plugin, player, ItemCategory.PERMISSIONS);
        var permissionLore = gui.buildSpecialShopItem(rewards.get(0)).getItemMeta().getLore();
        assertTrue(permissionLore.toString(), permissionLore.contains("§bClase: §dPermiso"));
        assertTrue(permissionLore.toString(), permissionLore.stream().anyMatch(line -> line.contains("Nodo:") && line.contains("enderchest.upgrade1")));
        var groupLore = gui.buildSpecialShopItem(rewards.get(1)).getItemMeta().getLore();
        assertTrue(groupLore.toString(), groupLore.contains("§bClase: §dGrupo"));
        assertTrue(groupLore.toString(), groupLore.contains("§7Grupo: §fEndUpgrade1"));
        assertFalse(permissionLore.toString(), permissionLore.toString().contains("Type:") || permissionLore.toString().contains("Node:"));
    }

    @Test public void emptyTooltipMessagesHideOnlyTheirConfiguredLines() throws Exception {
        var messages = bundledMessages();
        for (String key : List.of("special-lore-type-permission", "special-lore-permission-node",
                "special-lore-type-group", "special-lore-group-name")) messages.set("messages." + key, "");
        PluginTestFixture.set(plugin.getMessageManager(), MessageManager.class, "messagesConfig", messages);
        for (var entry : entries()) {
            var lore = new ShopGUI(plugin, player, ItemCategory.PERMISSIONS)
                    .buildSpecialShopItem(entry).getItemMeta().getLore();
            assertTrue(lore.toString(), lore.toString().contains("31.00"));
            assertTrue(lore.toString(), lore.toString().contains("Left-click to BUY"));
            assertFalse(lore.toString(), lore.toString().contains("Type:") || lore.toString().contains("Node:")
                    || lore.toString().contains("Group:") || lore.toString().contains("null")
                    || lore.toString().contains("enderchest.upgrade1") || lore.toString().contains("EndUpgrade1"));
        }
    }

    private org.bukkit.configuration.file.YamlConfiguration bundledMessages() throws Exception {
        try (var stream = getClass().getResourceAsStream("/messages.yml")) {
            return org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(
                    new java.io.InputStreamReader(stream, java.nio.charset.StandardCharsets.UTF_8));
        }
    }
}
