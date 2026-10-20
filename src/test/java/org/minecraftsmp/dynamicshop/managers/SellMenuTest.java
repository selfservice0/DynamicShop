package org.minecraftsmp.dynamicshop.managers;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import net.kyori.adventure.text.Component;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.damage.DamageSource;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryCreativeEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.enchantments.Enchantment;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.minecraftsmp.dynamicshop.DynamicShop;
import org.minecraftsmp.dynamicshop.commands.SellCommand;
import org.minecraftsmp.dynamicshop.commands.ShopCommand;
import org.minecraftsmp.dynamicshop.listeners.ShopListener;
import org.minecraftsmp.dynamicshop.support.BookTestStack;
import org.minecraftsmp.dynamicshop.support.PluginTestFixture;
import org.minecraftsmp.dynamicshop.transactions.TransactionLogger;
import static org.junit.Assert.*;

public class SellMenuTest {
    @Rule public TemporaryFolder directory = new TemporaryFolder();
    private final Map<Field, Object> settings = new HashMap<>();
    private final Map<Map<Object, Object>, Map<Object, Object>> maps = new IdentityHashMap<>();
    private final Map<Set<Object>, Set<Object>> sets = new IdentityHashMap<>();
    private final Set<String> permissions = new HashSet<>(Set.of("dynamicshop.use", "dynamicshop.use.sellgui"));
    private final ItemStack[] playerItems = new ItemStack[41];
    private final Map<Inventory, ItemStack[]> inventoryItems = new IdentityHashMap<>();
    private final List<ItemStack> dropped = new ArrayList<>();
    private final List<UUID> dropOwners = new ArrayList<>();
    private final List<String> messages = new ArrayList<>();
    private final List<Double> deposits = new ArrayList<>();
    private final ArrayDeque<Runnable> tasks = new ArrayDeque<>();
    private final AtomicReference<Inventory> top = new AtomicReference<>();
    private DynamicShop plugin;
    private SellBasketService service;
    private SellMenuManager menu;
    private Player player;
    private InventoryView view;
    private PlayerInventory playerInventory;
    private Inventory basket;
    private Field serverField;
    private Object previousServer;
    private Object previousDataPlugin;
    private String outcome = "SUCCESS";
    private boolean multiCurrency;
    private double credited;
    private final ServerHandle serverHandle = new ServerHandle();

    @Before @SuppressWarnings("unchecked") public void setUp() throws Exception {
        for (Field field : ConfigCacheManager.class.getFields()) if (Modifier.isStatic(field.getModifiers()) && !Modifier.isFinal(field.getModifiers())) settings.put(field, field.get(null));
        for (Field field : ShopDataManager.class.getDeclaredFields()) {
            if (!Modifier.isStatic(field.getModifiers())) continue;
            field.setAccessible(true);
            Object value = field.get(null);
            if (value instanceof Map<?, ?> map) maps.put((Map<Object, Object>) map, new HashMap<>((Map<Object, Object>) map));
            if (value instanceof Set<?> set) sets.put((Set<Object>) set, new HashSet<>((Set<Object>) set));
        }
        plugin = PluginTestFixture.plugin(directory.getRoot());
        Field dataPlugin = ShopDataManager.class.getDeclaredField("plugin");
        dataPlugin.setAccessible(true);
        previousDataPlugin = dataPlugin.get(null);
        dataPlugin.set(null, null);
        PluginTestFixture.set(plugin, DynamicShop.class, "shopListener", new ShopListener(plugin));
        PluginTestFixture.set(plugin, DynamicShop.class, "specialShopManager", new SpecialShopManager(plugin));
        PluginTestFixture.set(plugin, DynamicShop.class, "transactionLogger", new TransactionLogger(plugin));
        var economy = new MultiCurrencyEconomyManager(plugin) {
            @Override public String getCurrency(Material material) { return multiCurrency && material == Material.DIAMOND ? "gems" : "coins"; }
        };
        Economy provider = PluginTestFixture.proxy(Economy.class, (object, method, args) -> switch (method.getName()) {
            case "getName" -> "MenuEconomy";
            case "currencyNameSingular" -> "coin";
            case "currencyNamePlural" -> "coins";
            case "format" -> String.format(java.util.Locale.US, "%.2f coins", (double) args[0]);
            case "depositPlayer" -> {
                double value = (double) args[1];
                deposits.add(value);
                if (outcome.equals("THROW")) {
                    credited += value;
                    throw new IllegalStateException("reply lost");
                }
                boolean success = outcome.equals("SUCCESS") || (outcome.equals("MIXED") && value == 16);
                if (success) credited += value;
                yield new EconomyResponse(value, credited, success ? EconomyResponse.ResponseType.SUCCESS : EconomyResponse.ResponseType.FAILURE, "balance limit");
            }
            default -> throw new AssertionError(method.getName());
        });
        PluginTestFixture.set(economy, MultiCurrencyEconomyManager.class, "vaultEconomy", provider);
        PluginTestFixture.set(plugin, DynamicShop.class, "economyManager", economy);
        service = new SellBasketService(plugin);
        menu = new SellMenuManager(plugin, (owner, items) -> {});
        PluginTestFixture.set(plugin, DynamicShop.class, "sellMenuManager", menu);
        playerInventory = PluginTestFixture.proxy(PlayerInventory.class, (object, method, args) -> switch (method.getName()) {
            case "getItem" -> playerItems[(int) args[0]];
            case "setItem" -> {
                playerItems[(int) args[0]] = (ItemStack) args[1];
                yield null;
            }
            case "getContents", "getStorageContents" -> playerItems.clone();
            case "getSize" -> playerItems.length;
            case "addItem" -> addToPlayer((ItemStack[]) args[0]);
            default -> throw new AssertionError(method.getName());
        });
        World world = PluginTestFixture.proxy(World.class, (object, method, args) -> {
            if (method.getName().equals("dropItemNaturally")) {
                dropped.add(((ItemStack) args[1]).clone());
                return PluginTestFixture.proxy(Item.class, (drop, operation, values) -> {
                    if (operation.getName().equals("setOwner")) {
                        dropOwners.add((UUID) values[0]);
                        return null;
                    }
                    throw new AssertionError(operation.getName());
                });
            }
            throw new AssertionError(method.getName());
        });
        player = PluginTestFixture.proxy(WindowPlayer.class, (object, method, args) -> switch (method.getName()) {
            case "getHandle" -> serverHandle;
            case "getUniqueId" -> UUID.fromString("00000000-0000-0000-0000-000000000003");
            case "getName" -> "BasketSeller";
            case "hasPermission" -> permissions.contains(args[0]);
            case "getInventory" -> playerInventory;
            case "getOpenInventory" -> view;
            case "getWorld" -> world;
            case "getLocation" -> new Location(world, 0, 64, 0);
            case "sendMessage" -> {
                messages.add((String) args[0]);
                yield null;
            }
            case "updateInventory" -> null;
            case "closeInventory" -> {
                if (top.get() != null) menu.onClose(new InventoryCloseEvent(view));
                top.set(basket);
                yield null;
            }
            case "openInventory" -> {
                top.set((Inventory) args[0]);
                yield view;
            }
            default -> throw new AssertionError(method.getName());
        });
        view = PluginTestFixture.proxy(InventoryView.class, (object, method, args) -> switch (method.getName()) {
            case "getTopInventory" -> top.get();
            case "getBottomInventory" -> playerInventory;
            case "getPlayer" -> player;
            case "getInventory" -> (int) args[0] < 0 ? null : (int) args[0] < 54 ? top.get() : playerInventory;
            case "convertSlot" -> (int) args[0] < 54 ? (int) args[0] : (int) args[0] < 81 ? (int) args[0] - 54 + 9 : (int) args[0] - 81;
            case "getType" -> InventoryType.CHEST;
            case "getTitle", "getOriginalTitle" -> "Sell Items";
            case "countSlots" -> 90;
            case "getCursor" -> null;
            default -> throw new AssertionError(method.getName());
        });
        BukkitScheduler scheduler = PluginTestFixture.proxy(BukkitScheduler.class, (object, method, args) -> {
            if (method.getName().equals("runTask") || method.getName().equals("runTaskLater")) {
                tasks.add((Runnable) args[1]);
                return null;
            }
            throw new AssertionError(method.getName());
        });
        serverField = Bukkit.class.getDeclaredField("server");
        serverField.setAccessible(true);
        previousServer = serverField.get(null);
        serverField.set(null, PluginTestFixture.proxy(Server.class, (object, method, args) -> switch (method.getName()) {
            case "createInventory" -> inventory((InventoryHolder) args[0]);
            case "getScheduler" -> scheduler;
            case "getLogger" -> plugin.getLogger();
            case "isPrimaryThread" -> true;
            default -> throw new AssertionError(method.getName());
        }));
        basket = inventory(null);
        top.set(basket);
        ConfigCacheManager.dynamicPricingEnabled = false;
        ConfigCacheManager.sellTaxPercent = 0.2;
        ConfigCacheManager.transactionCooldownMs = 0;
        configure(Material.STONE, 2, null);
        configure(Material.DIAMOND, 10, null);
        stock(Material.STONE, 0);
        stock(Material.DIAMOND, 0);
    }

    @After public void restore() throws Exception {
        settings.forEach((field, value) -> {
            try { field.set(null, value); }
            catch (IllegalAccessException ex) { throw new AssertionError(ex); }
        });
        maps.forEach((map, original) -> {
            map.clear();
            map.putAll(original);
        });
        sets.forEach((set, original) -> {
            set.clear();
            set.addAll(original);
        });
        serverField.set(null, previousServer);
        PluginTestFixture.set(null, ShopDataManager.class, "plugin", previousDataPlugin);
    }

    private Inventory inventory(InventoryHolder holder) {
        ItemStack[] items = new ItemStack[54];
        Inventory inventory = PluginTestFixture.proxy(Inventory.class, (object, method, args) -> switch (method.getName()) {
            case "getHolder" -> holder;
            case "getSize" -> 54;
            case "getItem" -> items[(int) args[0]];
            case "setItem" -> {
                items[(int) args[0]] = (ItemStack) args[1];
                yield null;
            }
            case "getContents", "getStorageContents" -> items.clone();
            default -> throw new AssertionError(method.getName());
        });
        inventoryItems.put(inventory, items);
        return inventory;
    }

    private Map<Integer, ItemStack> addToPlayer(ItemStack[] items) {
        Map<Integer, ItemStack> overflow = new HashMap<>();
        for (int index = 0; index < items.length; index++) {
            ItemStack source = items[index];
            int remaining = source.getAmount();
            for (int slot = 0; slot < 36 && remaining > 0; slot++) {
                if (playerItems[slot] != null) continue;
                ItemStack placed = source.clone();
                placed.setAmount(Math.min(remaining, source.getMaxStackSize()));
                playerItems[slot] = placed;
                remaining -= placed.getAmount();
            }
            if (remaining > 0) {
                ItemStack rest = source.clone();
                rest.setAmount(remaining);
                overflow.put(index, rest);
            }
        }
        return overflow;
    }

    @SuppressWarnings("unchecked") private void configure(Material material, double price, Integer capacity) throws Exception {
        ShopDataManager.itemConfigs.put(material, new ShopDataManager.ShopItemConfig(price, null, null, capacity, null, false, false, null, null));
        var field = ShopDataManager.class.getDeclaredField("itemTemplates");
        field.setAccessible(true);
        ((Map<Material, ItemStack>) field.get(null)).put(material, stack(material, 1));
    }
    @SuppressWarnings("unchecked") private void stock(Material material, double value) throws Exception {
        var field = ShopDataManager.class.getDeclaredField("stockMap");
        field.setAccessible(true);
        ((Map<Material, Double>) field.get(null)).put(material, value);
    }
    private BookTestStack stack(Material material, int amount) {
        var item = new BookTestStack(material, Map.of());
        item.setAmount(amount);
        return item;
    }
    private InventoryClickEvent click(int slot, ClickType type, InventoryAction action) {
        var event = new InventoryClickEvent(view, InventoryType.SlotType.CONTAINER, slot, type, action);
        menu.onClick(event);
        return event;
    }
    private void tick() { while (!tasks.isEmpty()) tasks.removeFirst().run(); }
    private void refresh() {
        click(0, ClickType.LEFT, InventoryAction.NOTHING);
        tick();
    }

    @Test public void quotesCombineMatchingStacksAndHonorCapacityWithoutChangingItems() throws Exception {
        configure(Material.STONE, 2, 10);
        basket.setItem(0, stack(Material.STONE, 8));
        basket.setItem(1, stack(Material.STONE, 8));
        var quote = service.quote(player, basket);
        assertEquals(10, quote.accepted());
        assertEquals(16.0, quote.totals().get("coins"), 0.000001);
        assertEquals(8, quote.lines().get(0).accepted());
        assertEquals(2, quote.lines().get(1).accepted());
        assertEquals(8, basket.getItem(1).getAmount());
        assertEquals(0.0, ShopDataManager.getStock(Material.STONE), 0.0);
        assertTrue(deposits.isEmpty());
    }

    @Test public void confirmationSellsOnlyDepositedItemsAndPaysTheBulkQuoteOnce() {
        basket.setItem(0, stack(Material.STONE, 3));
        basket.setItem(1, stack(Material.STONE, 4));
        playerItems[0] = stack(Material.STONE, 10);
        var result = service.sell(player, basket, service.quote(player, basket));
        assertEquals(SellBasketService.Status.SOLD, result.status());
        assertEquals(7, result.paidItems());
        assertEquals(11.2, credited, 0.000001);
        assertEquals(1, deposits.size());
        assertEquals(1, plugin.getTransactionLogger().getTotalTransactions());
        assertEquals(7.0, ShopDataManager.getStock(Material.STONE), 0.0);
        assertEquals(10, playerItems[0].getAmount());
        service.sell(player, basket, service.quote(player, basket));
        assertEquals(1, deposits.size());
    }

    @Test public void changedPriceOrContentsRequiresANewConfirmation() throws Exception {
        basket.setItem(0, stack(Material.STONE, 3));
        var quote = service.quote(player, basket);
        configure(Material.STONE, 4, null);
        assertEquals(SellBasketService.Status.CHANGED, service.sell(player, basket, quote).status());
        assertEquals(3, basket.getItem(0).getAmount());
        assertTrue(deposits.isEmpty());
        quote = service.quote(player, basket);
        basket.getItem(0).setAmount(4);
        assertEquals(SellBasketService.Status.CHANGED, service.sell(player, basket, quote).status());
        assertTrue(deposits.isEmpty());
    }

    @Test public void untradeableAndDisabledItemsAreNeverConsumed() throws Exception {
        configure(Material.STONE, 2, null);
        ShopDataManager.itemConfigs.put(Material.STONE, new ShopDataManager.ShopItemConfig(2, null, null, null, null, false, true, null, null));
        basket.setItem(0, stack(Material.STONE, 3));
        basket.setItem(1, stack(Material.BARRIER, 1));
        assertEquals(0, service.quote(player, basket).accepted());
        assertEquals(SellBasketService.Status.EMPTY, service.sell(player, basket, service.quote(player, basket)).status());
        assertNotNull(basket.getItem(0));
        assertNotNull(basket.getItem(1));
    }

    @Test public void permissionRevocationAndDisableSwitchPreventPayment() {
        basket.setItem(0, stack(Material.STONE, 3));
        var quote = service.quote(player, basket);
        permissions.remove("dynamicshop.use.sellgui");
        assertEquals(SellBasketService.Status.DENIED, service.sell(player, basket, quote).status());
        permissions.add("dynamicshop.use.sellgui");
        plugin.getConfig().set("gui.sell_menu_enabled", false);
        assertEquals(SellBasketService.Status.DENIED, service.sell(player, basket, quote).status());
        assertEquals(3, basket.getItem(0).getAmount());
        assertTrue(deposits.isEmpty());
    }

    @Test public void failedOrUncertainPaymentConsumesOnlyOnceAndDoesNotRecordSuccess() {
        for (String mode : List.of("FAILURE", "THROW")) {
            outcome = mode;
            basket.setItem(0, stack(Material.STONE, 3));
            var result = service.sell(player, basket, service.quote(player, basket));
            assertEquals(0, result.paidItems());
            assertTrue(result.paidTotals().isEmpty());
            assertNull(basket.getItem(0));
            int calls = deposits.size();
            service.sell(player, basket, service.quote(player, basket));
            assertEquals(calls, deposits.size());
        }
        assertEquals(0, plugin.getTransactionLogger().getTotalTransactions());
    }

    @Test public void mixedCurrenciesReportOnlyConfirmedPayments() {
        multiCurrency = true;
        outcome = "MIXED";
        basket.setItem(0, stack(Material.STONE, 3));
        basket.setItem(1, stack(Material.DIAMOND, 2));
        var result = service.sell(player, basket, service.quote(player, basket));
        assertEquals(2, result.paidItems());
        assertEquals(Map.of("gems", 16.0), result.paidTotals());
        assertEquals(2, deposits.size());
        assertEquals(1, plugin.getTransactionLogger().getTotalTransactions());
    }

    @Test public void priceLoreIsOnlyOnDisplayCopiesAndCancelReturnsOriginalMetadata() throws Exception {
        menu.open(player);
        var original = stack(Material.STONE, 3);
        var originalMeta = original.getItemMeta();
        originalMeta.lore(List.of(Component.text("Original item lore")));
        original.setItemMeta(originalMeta);
        top.get().setItem(0, original);
        refresh();
        var holder = top.get().getHolder();
        var displayMethod = holder.getClass().getDeclaredMethod("display", int.class, ItemStack.class);
        displayMethod.setAccessible(true);
        ItemStack displayed = (ItemStack) displayMethod.invoke(holder, 0, original);
        assertEquals(Component.text("Original item lore"), displayed.getItemMeta().lore().getFirst());
        assertTrue(displayed.getItemMeta().getLore().stream().anyMatch(line -> line.contains("Stack value:")));
        assertEquals(originalMeta.lore(), original.getItemMeta().lore());
        assertTrue(click(51, ClickType.LEFT, InventoryAction.PICKUP_ALL).isCancelled());
        tick();
        assertEquals(3, playerItems[0].getAmount());
        assertEquals(originalMeta.lore(), playerItems[0].getItemMeta().lore());
        assertTrue(original.isSimilar(playerItems[0]));
        assertTrue(deposits.isEmpty());
    }

    @Test public void displayedPriceCopiesCannotMutateRealItemsOrLaterDisplays() throws Exception {
        menu.open(player);
        var original = stack(Material.STONE, 3);
        top.get().setItem(0, original);
        refresh();
        var displayMethod = top.get().getHolder().getClass().getDeclaredMethod("display", int.class, ItemStack.class);
        displayMethod.setAccessible(true);
        ItemStack shown = (ItemStack) displayMethod.invoke(top.get().getHolder(), 0, original);
        assertTrue(shown.getItemMeta().getLore().stream().anyMatch(line -> line.contains("Per item:") && line.contains("1.60 coins")));
        assertTrue(shown.getItemMeta().getLore().stream().anyMatch(line -> line.contains("4.80 coins")));
        shown.setAmount(64);
        var meta = shown.getItemMeta();
        meta.lore(List.of(Component.text("Changed display")));
        shown.setItemMeta(meta);
        ItemStack next = (ItemStack) displayMethod.invoke(top.get().getHolder(), 0, original);
        assertEquals(3, next.getAmount());
        assertTrue(next.getItemMeta().getLore().stream().anyMatch(line -> line.contains("4.80 coins")));
        assertFalse(original.getItemMeta().hasLore());
        assertSame(original, top.get().getItem(0));

        // Bukkit moves the authoritative server stack, never the outgoing packet copy.
        assertFalse(click(0, ClickType.SHIFT_LEFT, InventoryAction.MOVE_TO_OTHER_INVENTORY).isCancelled());
        playerInventory.addItem(top.get().getItem(0));
        top.get().setItem(0, null);
        tick();
        assertEquals(3, playerItems[0].getAmount());
        assertFalse(playerItems[0].getItemMeta().hasLore());
        player.closeInventory();
        assertNull(playerItems[1]);
    }

    @Test public void stalePriceCopiesAreNotAppliedToChangedOrPlayerInventorySlots() throws Exception {
        menu.open(player);
        var original = stack(Material.STONE, 3);
        top.get().setItem(0, original);
        refresh();
        var holder = top.get().getHolder();
        var displayMethod = holder.getClass().getDeclaredMethod("display", int.class, ItemStack.class);
        displayMethod.setAccessible(true);
        var changedAmount = stack(Material.STONE, 2);
        var replacement = stack(Material.DIAMOND, 3);
        var changedLore = original.clone();
        var meta = changedLore.getItemMeta();
        meta.lore(List.of(Component.text("Different original lore")));
        changedLore.setItemMeta(meta);
        assertSame(changedAmount, displayMethod.invoke(holder, 0, changedAmount));
        assertSame(replacement, displayMethod.invoke(holder, 0, replacement));
        assertSame(changedLore, displayMethod.invoke(holder, 0, changedLore));
        assertSame(original, displayMethod.invoke(holder, 54, original));
        assertNull(displayMethod.invoke(holder, 0, null));
    }

    @Test public void hoverUnitPriceUsesSellableCountWhenOnlyPartOfAStackFits() throws Exception {
        configure(Material.STONE, 2, 2);
        menu.open(player);
        var original = stack(Material.STONE, 5);
        top.get().setItem(0, original);
        refresh();
        var holder = top.get().getHolder();
        var displayMethod = holder.getClass().getDeclaredMethod("display", int.class, ItemStack.class);
        displayMethod.setAccessible(true);
        ItemStack shown = (ItemStack) displayMethod.invoke(holder, 0, original);
        var lore = shown.getItemMeta().getLore();
        assertTrue(lore.stream().anyMatch(line -> line.contains("Per item:") && line.contains("1.60 coins")));
        assertTrue(lore.stream().anyMatch(line -> line.contains("Stack value:") && line.contains("3.20 coins")));
        assertTrue(lore.stream().anyMatch(line -> line.contains("Sellable:") && line.contains("2/5")));
        assertFalse(original.getItemMeta().hasLore());
    }

    @Test public void refreshSendsRealWindowPricePacketsForDepositsMainInventoryAndHotbar() throws Exception {
        var bridge = new SellMenuPackets.Bridge(player.getClass(), NativeItems.class, NativeSlotPacket.class, NativePacket.class);
        menu = new SellMenuManager(plugin, bridge);
        PluginTestFixture.set(plugin, DynamicShop.class, "sellMenuManager", menu);
        playerItems[9] = stack(Material.STONE, 3);
        playerItems[0] = stack(Material.DIAMOND, 2);
        menu.open(player);
        top.get().setItem(0, stack(Material.STONE, 1));
        refresh();
        Map<Integer, NativeSlotPacket> bySlot = new HashMap<>();
        for (NativeSlotPacket packet : serverHandle.connection.sent) {
            assertEquals(7, packet.containerId());
            assertEquals(29, packet.stateId());
            bySlot.put(packet.slot(), packet);
        }
        assertEquals(Set.of(0, 54, 81), bySlot.keySet());
        assertTrue(bySlot.get(54).item().stack().getItemMeta().getLore().stream()
                .anyMatch(line -> line.contains("Stack value:") && line.contains("4.80 coins")));
        assertTrue(bySlot.get(81).item().stack().getItemMeta().getLore().stream()
                .anyMatch(line -> line.contains("Stack value:") && line.contains("16.00 coins")));
        assertFalse(playerItems[0].getItemMeta().hasLore());
        assertFalse(playerItems[9].getItemMeta().hasLore());
        assertFalse(top.get().getItem(0).getItemMeta().hasLore());
        assertEquals(1, service.quote(player, top.get()).accepted());
        assertTrue(deposits.isEmpty());
        serverHandle.connection.sent.clear();
        player.closeInventory();
        tick();
        assertTrue(serverHandle.connection.sent.isEmpty());
    }

    @Test public void packetSenderReadsCurrentWindowAndStateOnEveryRefresh() throws Exception {
        var bridge = new SellMenuPackets.Bridge(player.getClass(), NativeItems.class, NativeSlotPacket.class, NativePacket.class);
        menu = new SellMenuManager(plugin, bridge);
        menu.open(player);
        top.get().setItem(0, stack(Material.STONE, 2));
        serverHandle.containerMenu.containerId = 23;
        serverHandle.containerMenu.stateId = 105;
        refresh();
        var sent = serverHandle.connection.sent;
        assertFalse(sent.isEmpty());
        assertTrue(sent.stream().allMatch(packet -> packet.containerId() == 23 && packet.stateId() == 105));
        sent.clear();
        serverHandle.containerMenu.stateId = 106;
        refresh();
        assertTrue(sent.stream().allMatch(packet -> packet.stateId() == 106));
        sent.clear();
        bridge.send(player, Map.of(0, stack(Material.STONE, 1)));
        assertEquals(1, sent.size());
        sent.clear();
        serverHandle.containerMenu.containerId = 0;
        bridge.send(player, Map.of(0, stack(Material.STONE, 1)));
        assertTrue(sent.isEmpty());
    }

    @Test public void queuedPricePacketsSkipItemsChangedOrRemovedBeforeSending() throws Exception {
        List<Map<Integer, ItemStack>> sent = new ArrayList<>();
        menu = new SellMenuManager(plugin, (owner, items) -> sent.add(items));
        playerItems[9] = stack(Material.STONE, 3);
        menu.open(player);
        playerItems[9] = stack(Material.DIAMOND, 1);
        tick();
        assertTrue(sent.isEmpty());
        refresh();
        assertEquals(1, sent.size());
        assertEquals(Material.DIAMOND, sent.getFirst().get(54).getType());
        assertFalse(playerItems[9].getItemMeta().hasLore());
        sent.clear();
        click(54, ClickType.LEFT, InventoryAction.NOTHING);
        tasks.removeFirst().run();
        playerItems[9] = null;
        tick();
        assertTrue(sent.isEmpty());
    }

    public interface WindowPlayer extends Player { ServerHandle getHandle(); }
    public static final class ServerHandle {
        public final ServerContainer containerMenu = new ServerContainer();
        public final NativeConnection connection = new NativeConnection();
    }
    public static final class ServerContainer {
        public int containerId = 7;
        public int stateId = 29;
        public int getStateId() { return stateId; }
    }
    public interface NativePacket {}
    public record NativeItem(ItemStack stack) {}
    public record NativeSlotPacket(int containerId, int stateId, int slot, NativeItem item) implements NativePacket {}
    public static final class NativeItems {
        public static NativeItem asNMSCopy(ItemStack stack) { return new NativeItem(stack.clone()); }
    }
    public static final class NativeConnection {
        final List<NativeSlotPacket> sent = new ArrayList<>();
        public void send(NativePacket packet) { sent.add((NativeSlotPacket) packet); }
    }
    @Test public void closeQuitAndShutdownReturnDepositsExactlyOnce() {
        menu.open(player);
        top.get().setItem(0, stack(Material.STONE, 3));
        Inventory old = top.get();
        menu.onQuit(new PlayerQuitEvent(player, ""));
        menu.onClose(new InventoryCloseEvent(view));
        menu.shutdown();
        assertNull(old.getItem(0));
        assertEquals(3, playerItems[0].getAmount());
        assertNull(playerItems[1]);
        assertTrue(deposits.isEmpty());
    }

    @Test public void fullInventoriesDropReturnedItemsWithTheOwnerSet() {
        for (int slot = 0; slot < 36; slot++) playerItems[slot] = stack(Material.DIAMOND, 64);
        menu.open(player);
        top.get().setItem(0, stack(Material.STONE, 3));
        player.closeInventory();
        assertEquals(1, dropped.size());
        assertEquals(3, dropped.getFirst().getAmount());
        assertEquals(List.of(player.getUniqueId()), dropOwners);
    }

    @Test public void buttonsDragsAndDoubleClicksCannotRemoveControls() {
        menu.open(player);
        assertTrue(click(49, ClickType.NUMBER_KEY, InventoryAction.HOTBAR_SWAP).isCancelled());
        assertTrue(click(60, ClickType.DOUBLE_CLICK, InventoryAction.COLLECT_TO_CURSOR).isCancelled());
        var blocked = new InventoryDragEvent(view, stack(Material.STONE, 1), stack(Material.STONE, 2), false, Map.of(45, stack(Material.STONE, 1)));
        menu.onDrag(blocked);
        assertTrue(blocked.isCancelled());
        var allowed = new InventoryDragEvent(view, null, stack(Material.STONE, 1), false, Map.of(0, stack(Material.STONE, 1)));
        menu.onDrag(allowed);
        assertFalse(allowed.isCancelled());
        assertNotNull(top.get().getItem(49));
    }

    @Test public void shiftClickFillsOnlyDepositSlotsAndLeavesOverflowWithPlayer() {
        menu.open(player);
        for (int slot = 0; slot < 45; slot++) top.get().setItem(slot, stack(Material.STONE, 64));
        top.get().getItem(0).setAmount(63);
        playerItems[9] = stack(Material.STONE, 5);
        assertTrue(click(54, ClickType.SHIFT_LEFT, InventoryAction.MOVE_TO_OTHER_INVENTORY).isCancelled());
        assertEquals(64, top.get().getItem(0).getAmount());
        assertEquals(4, playerItems[9].getAmount());
        assertEquals(Material.LIME_CONCRETE, top.get().getItem(47).getType());
    }

    @Test public void rapidConfirmClicksPayOnceAndKeepRemaindersUntilClose() throws Exception {
        configure(Material.STONE, 2, 2);
        menu.open(player);
        Inventory opened = top.get();
        top.get().setItem(0, stack(Material.STONE, 5));
        refresh();
        click(47, ClickType.LEFT, InventoryAction.PICKUP_ALL);
        assertTrue(click(47, ClickType.LEFT, InventoryAction.PICKUP_ALL).isCancelled());
        tick();
        assertEquals(1, deposits.size());
        assertEquals(3.2, credited, 0.000001);
        assertSame(opened, top.get());
        assertEquals(3, top.get().getItem(0).getAmount());
        assertNull(playerItems[0]);
        click(47, ClickType.LEFT, InventoryAction.PICKUP_ALL);
        tick();
        assertEquals(1, deposits.size());
        assertSame(opened, top.get());
        player.closeInventory();
        tick();
        assertEquals(3, playerItems[0].getAmount());
        assertFalse(playerItems[0].getItemMeta().hasLore());
        menu.shutdown();
        assertNull(playerItems[1]);
    }

    @Test public void sellCommandsOpenTheMenuAndInvalidArgumentsDoNot() {
        new SellCommand(plugin).onCommand(player, null, "sell", new String[0]);
        assertNotNull(top.get().getHolder());
        player.closeInventory();
        new ShopCommand(plugin).onCommand(player, null, "shop", new String[] {"sell"});
        assertNotNull(top.get().getHolder());
        player.closeInventory();
        new SellCommand(plugin).onCommand(player, null, "sell", new String[] {"all"});
        assertNull(top.get().getHolder());
        permissions.remove("dynamicshop.use.sellgui");
        new SellCommand(plugin).onCommand(player, null, "sell", new String[0]);
        assertNull(top.get().getHolder());
    }

    @Test public void dynamicPricesUseTheSameBulkIntegralAsNormalShopSales() throws Exception {
        ConfigCacheManager.dynamicPricingEnabled = true;
        ConfigCacheManager.useTimeInflation = false;
        stock(Material.STONE, 100);
        basket.setItem(0, stack(Material.STONE, 3));
        basket.setItem(1, stack(Material.STONE, 4));
        double expected = ShopDataManager.getTotalSellValue(Material.STONE, 7);
        var quote = service.quote(player, basket);
        assertEquals(expected, quote.totals().get("coins"), 0.000001);
        assertEquals(expected, quote.lines().stream().mapToDouble(SellBasketService.Line::value).sum(), 0.000001);
        service.sell(player, basket, quote);
        assertEquals(expected, credited, 0.000001);
    }

    private long enableTimedPricing() {
        ConfigCacheManager.dynamicPricingEnabled = true;
        ConfigCacheManager.useTimeInflation = true;
        ConfigCacheManager.useStockCurve = false;
        ConfigCacheManager.negativeStockPercentPerItem = 0;
        ConfigCacheManager.hourlyIncreasePercent = 10;
        ConfigCacheManager.maxPriceMultiplier = 10;
        ConfigCacheManager.shortageDecayPercentPerHour = 2;
        ShopDataManager.setHoursInShortage(Material.STONE, 0);
        ShopDataManager.setHoursInShortage(Material.DIAMOND, 0);
        return System.currentTimeMillis() - 1_000;
    }

    @Test public void multipleStacksWithLiveInflationSellAtTheDisplayedTotal() throws Exception {
        long pricedAt = enableTimedPricing();
        stock(Material.STONE, -100);
        stock(Material.DIAMOND, -100);
        ShopDataManager.setLastUpdate(Material.STONE, pricedAt - 3_600_000);
        ShopDataManager.setLastUpdate(Material.DIAMOND, pricedAt - 3_600_000);
        basket.setItem(0, stack(Material.STONE, 3));
        basket.setItem(1, stack(Material.STONE, 4));
        basket.setItem(2, stack(Material.DIAMOND, 2));
        var approved = service.quote(player, basket, 45, pricedAt);
        assertEquals(9, approved.accepted());
        assertEquals(29.92, approved.totals().get("coins"), 0.000001);
        assertFalse(approved.matches(service.quote(player, basket)));
        var result = service.sell(player, basket, approved);
        assertEquals(SellBasketService.Status.SOLD, result.status());
        assertEquals(9, result.paidItems());
        assertEquals(approved.totals().get("coins"), credited, 0.0);
        assertEquals(1, deposits.size());
        assertEquals(-93.0, ShopDataManager.getStock(Material.STONE), 0.0);
        assertEquals(-98.0, ShopDataManager.getStock(Material.DIAMOND), 0.0);
        assertNull(basket.getItem(0));
        assertNull(basket.getItem(1));
        assertNull(basket.getItem(2));
    }

    @Test public void liveShortageDecayDoesNotInvalidateConfirmation() throws Exception {
        long pricedAt = enableTimedPricing();
        stock(Material.STONE, 100);
        ShopDataManager.setHoursInShortage(Material.STONE, 2);
        ShopDataManager.setLastUpdate(Material.STONE, pricedAt);
        basket.setItem(0, stack(Material.STONE, 3));
        basket.setItem(1, stack(Material.STONE, 4));
        var approved = service.quote(player, basket, 45, pricedAt);
        assertFalse(approved.matches(service.quote(player, basket)));
        assertEquals(SellBasketService.Status.SOLD, service.sell(player, basket, approved).status());
        assertEquals(approved.totals().get("coins"), credited, 0.0);
    }

    @Test public void customItemsWithLiveInflationOrDecaySellAtTheDisplayedTotal() throws Exception {
        long pricedAt = enableTimedPricing();
        configure(Material.ENCHANTED_BOOK, 10, null);
        var specials = plugin.getSpecialShopManager();
        specials.addServerShopItem("mending", "Mending", 25, "mending", Material.ENCHANTED_BOOK, null, false);
        specials.getSpecialItem("mending").setDeliveryMethod("stored_item");
        var book = new BookTestStack(Map.of(Enchantment.MENDING, 1));
        plugin.getConfig().set("special_items.mending.stored_item", book);
        for (double currentStock : new double[] {-100, 100}) {
            ShopDataManager.variantStockMap.put("mending", currentStock);
            ShopDataManager.setVariantShortageHours("mending", 1);
            ShopDataManager.setVariantLastUpdate("mending", pricedAt);
            basket.setItem(0, book.clone());
            basket.setItem(1, book.clone());
            var approved = service.quote(player, basket, 45, pricedAt);
            assertEquals(2, approved.accepted());
            assertFalse(approved.matches(service.quote(player, basket)));
            var result = service.sell(player, basket, approved);
            assertEquals(SellBasketService.Status.SOLD, result.status());
            assertEquals(approved.totals(), result.paidTotals());
            assertEquals(currentStock + 2, ShopDataManager.getVariantStock("mending"), 0.0);
        }
        assertEquals(2, deposits.size());
    }

    @Test public void timedQuotesStillRejectStockAndPricingSettingChanges() throws Exception {
        long pricedAt = enableTimedPricing();
        ConfigCacheManager.negativeStockPercentPerItem = 0.1;
        stock(Material.STONE, -100);
        ShopDataManager.setLastUpdate(Material.STONE, pricedAt - 3_600_000);
        basket.setItem(0, stack(Material.STONE, 3));
        var approved = service.quote(player, basket, 45, pricedAt);
        stock(Material.STONE, -90);
        assertEquals(SellBasketService.Status.CHANGED, service.sell(player, basket, approved).status());
        approved = service.quote(player, basket, 45, pricedAt);
        ConfigCacheManager.sellTaxPercent = 0.3;
        assertEquals(SellBasketService.Status.CHANGED, service.sell(player, basket, approved).status());
        approved = service.quote(player, basket, 45, pricedAt);
        configure(Material.STONE, 3, null);
        assertEquals(SellBasketService.Status.CHANGED, service.sell(player, basket, approved).status());
        assertEquals(3, basket.getItem(0).getAmount());
        assertTrue(deposits.isEmpty());
    }

    @Test public void customBooksUseTheirOwnStockPriceAndCurrentPermissions() throws Exception {
        configure(Material.ENCHANTED_BOOK, 10, null);
        var specials = plugin.getSpecialShopManager();
        specials.addServerShopItem("mending", "Mending", 25, "mending", Material.ENCHANTED_BOOK, "vip", false);
        specials.getSpecialItem("mending").setDeliveryMethod("stored_item");
        var book = new BookTestStack(Map.of(Enchantment.MENDING, 1));
        plugin.getConfig().set("special_items.mending.stored_item", book);
        basket.setItem(0, book.clone());
        assertEquals(0, service.quote(player, basket).accepted());
        permissions.add("vip");
        var approved = service.quote(player, basket);
        assertEquals(1, approved.accepted());
        assertEquals(20.0, approved.totals().get("coins"), 0.0);
        plugin.getConfig().set("special_items.mending.disable-sell", true);
        assertEquals(SellBasketService.Status.CHANGED, service.sell(player, basket, approved).status());
        assertTrue(deposits.isEmpty());
        plugin.getConfig().set("special_items.mending.disable-sell", false);
        var result = service.sell(player, basket, service.quote(player, basket));
        assertEquals(1, result.paidItems());
        assertEquals(1.0, ShopDataManager.getVariantStock("mending"), 0.0);
        assertEquals(0.0, ShopDataManager.getStock(Material.ENCHANTED_BOOK), 0.0);
        assertEquals("VARIANT:mending", plugin.getTransactionLogger().getMostRecentTransaction().getItem());
        assertFalse(book.getItemMeta().hasLore());
    }

    @Test public void closingWhileAConfirmationIsQueuedReturnsItemsWithoutPayment() {
        menu.open(player);
        top.get().setItem(0, stack(Material.STONE, 3));
        refresh();
        click(47, ClickType.LEFT, InventoryAction.PICKUP_ALL);
        player.closeInventory();
        tick();
        assertEquals(3, playerItems[0].getAmount());
        assertTrue(deposits.isEmpty());
    }

    @Test public void immediatelyConfirmingNewDepositsRequiresReviewingTheirTotalFirst() {
        menu.open(player);
        Inventory opened = top.get();
        click(0, ClickType.LEFT, InventoryAction.PLACE_ALL);
        top.get().setItem(0, stack(Material.STONE, 3));
        click(47, ClickType.LEFT, InventoryAction.PICKUP_ALL);
        tick();
        assertTrue(deposits.isEmpty());
        assertEquals(3, top.get().getItem(0).getAmount());
        assertTrue(messages.stream().anyMatch(message -> message.contains("confirm again")));
        click(47, ClickType.LEFT, InventoryAction.PICKUP_ALL);
        tick();
        assertEquals(1, deposits.size());
        assertSame(opened, top.get());
        assertNull(top.get().getItem(0));
        assertEquals(0, service.quote(player, top.get()).accepted());
        playerItems[9] = stack(Material.DIAMOND, 2);
        click(54, ClickType.SHIFT_LEFT, InventoryAction.MOVE_TO_OTHER_INVENTORY);
        tick();
        click(47, ClickType.LEFT, InventoryAction.PICKUP_ALL);
        tick();
        assertSame(opened, top.get());
        assertNull(top.get().getItem(0));
        assertNull(playerItems[9]);
        assertEquals(2, deposits.size());
        assertEquals(20.8, credited, 0.000001);
    }

    @Test public void saleCooldownIsSharedWithTheExistingShopCommands() {
        ConfigCacheManager.transactionCooldownMs = 10_000;
        basket.setItem(0, stack(Material.STONE, 1));
        service.sell(player, basket, service.quote(player, basket));
        basket.setItem(0, stack(Material.STONE, 1));
        assertEquals(SellBasketService.Status.COOLDOWN, service.sell(player, basket, service.quote(player, basket)).status());
        assertNotNull(basket.getItem(0));
        assertEquals(1, deposits.size());
    }

    @Test public void deathDropsOrRetainsDepositsWithoutDuplicatingThem() {
        menu.open(player);
        top.get().setItem(0, stack(Material.STONE, 3));
        List<ItemStack> drops = new ArrayList<>();
        var source = PluginTestFixture.proxy(DamageSource.class, (object, method, args) -> { throw new AssertionError(method.getName()); });
        var death = new PlayerDeathEvent(player, source, drops, 0, "");
        death.setKeepInventory(false);
        menu.onDeath(death);
        menu.onClose(new InventoryCloseEvent(view));
        assertEquals(1, drops.size());
        assertEquals(3, drops.getFirst().getAmount());
        assertNull(playerItems[0]);
        menu.open(player);
        top.get().setItem(0, stack(Material.STONE, 4));
        death = new PlayerDeathEvent(player, source, new ArrayList<>(), 0, "");
        death.setKeepInventory(true);
        menu.onDeath(death);
        assertEquals(4, playerItems[0].getAmount());
        assertTrue(death.getDrops().isEmpty());
    }

    @Test public void cancelledDeathLeavesDepositsAvailableAndReturnsThemOnceOnClose() {
        menu.open(player);
        var original = stack(Material.STONE, 3);
        top.get().setItem(0, original);
        var source = PluginTestFixture.proxy(DamageSource.class, (object, method, args) -> { throw new AssertionError(method.getName()); });
        var death = new PlayerDeathEvent(player, source, new ArrayList<>(), 0, "");
        death.setKeepInventory(false);
        death.setCancelled(true);
        menu.onDeath(death);
        assertSame(original, top.get().getItem(0));
        assertTrue(death.getDrops().isEmpty());
        assertNull(playerItems[0]);
        player.closeInventory();
        menu.onQuit(new PlayerQuitEvent(player, ""));
        menu.shutdown();
        assertEquals(3, playerItems[0].getAmount());
        assertNull(playerItems[1]);
        assertTrue(deposits.isEmpty());
    }

    @Test public void queuedConfirmationBlocksPickupShiftHotbarOffhandDropAndDrag() {
        menu.open(player);
        top.get().setItem(0, stack(Material.STONE, 3));
        refresh();
        click(47, ClickType.LEFT, InventoryAction.PICKUP_ALL);
        assertTrue(click(0, ClickType.LEFT, InventoryAction.PICKUP_ALL).isCancelled());
        assertTrue(click(0, ClickType.SHIFT_LEFT, InventoryAction.MOVE_TO_OTHER_INVENTORY).isCancelled());
        var hotbar = new InventoryClickEvent(view, InventoryType.SlotType.CONTAINER, 0, ClickType.NUMBER_KEY, InventoryAction.HOTBAR_SWAP, 0);
        menu.onClick(hotbar);
        assertTrue(hotbar.isCancelled());
        assertTrue(click(0, ClickType.SWAP_OFFHAND, InventoryAction.HOTBAR_SWAP).isCancelled());
        assertTrue(click(0, ClickType.CONTROL_DROP, InventoryAction.DROP_ALL_SLOT).isCancelled());
        assertTrue(click(-999, ClickType.LEFT, InventoryAction.DROP_ALL_CURSOR).isCancelled());
        assertTrue(click(54, ClickType.SHIFT_LEFT, InventoryAction.MOVE_TO_OTHER_INVENTORY).isCancelled());
        var drag = new InventoryDragEvent(view, null, stack(Material.STONE, 1), false, Map.of(0, stack(Material.STONE, 4)));
        menu.onDrag(drag);
        assertTrue(drag.isCancelled());
        tick();
        assertEquals(1, deposits.size());
        assertEquals(4.8, credited, 0.000001);
        assertNull(playerItems[0]);
    }

    @Test public void footerControlsAndCreativeCopiesCannotBeTaken() {
        menu.open(player);
        for (int slot = 45; slot < 54; slot++) {
            assertTrue(click(slot, ClickType.SHIFT_LEFT, InventoryAction.MOVE_TO_OTHER_INVENTORY).isCancelled());
            assertTrue(click(slot, ClickType.SWAP_OFFHAND, InventoryAction.HOTBAR_SWAP).isCancelled());
            assertTrue(click(slot, ClickType.CONTROL_DROP, InventoryAction.DROP_ALL_SLOT).isCancelled());
        }
        var creative = new InventoryCreativeEvent(view, InventoryType.SlotType.CONTAINER, 0, stack(Material.DIAMOND, 64));
        menu.onClick(creative);
        assertTrue(creative.isCancelled());
        tick();
        assertTrue(deposits.isEmpty());
        assertNull(playerItems[0]);
    }

    @Test public void anotherViewerCannotMoveItemsOrConfirmAnOwnersSale() throws Exception {
        menu.open(player);
        top.get().setItem(0, stack(Material.STONE, 3));
        refresh();
        Player stranger = PluginTestFixture.proxy(Player.class, (object, method, args) -> {
            if (method.getName().equals("getUniqueId")) return UUID.fromString("00000000-0000-0000-0000-000000000004");
            throw new AssertionError(method.getName());
        });
        InventoryView foreignView = PluginTestFixture.proxy(InventoryView.class, (object, method, args) -> {
            if (method.getName().equals("getPlayer")) return stranger;
            return method.invoke(view, args);
        });
        for (int slot : new int[] {0, 47, 51, 54}) {
            var event = new InventoryClickEvent(foreignView, InventoryType.SlotType.CONTAINER, slot, ClickType.LEFT, InventoryAction.PICKUP_ALL);
            menu.onClick(event);
            assertTrue(event.isCancelled());
        }
        var drag = new InventoryDragEvent(foreignView, null, stack(Material.STONE, 1), false, Map.of(0, stack(Material.STONE, 4)));
        menu.onDrag(drag);
        assertTrue(drag.isCancelled());
        tick();
        assertTrue(deposits.isEmpty());
        assertEquals(3, top.get().getItem(0).getAmount());
    }

    @Test public void metadataChangesCannotReuseAPlainItemSaleQuote() {
        basket.setItem(0, stack(Material.STONE, 3));
        var approved = service.quote(player, basket);
        var altered = basket.getItem(0).clone();
        var meta = altered.getItemMeta();
        meta.displayName(Component.text("Expensive item"));
        meta.lore(List.of(Component.text("Stack value: 99999999 coins")));
        altered.setItemMeta(meta);
        basket.setItem(0, altered);
        assertEquals(SellBasketService.Status.CHANGED, service.sell(player, basket, approved).status());
        assertEquals(SellBasketService.Status.EMPTY, service.sell(player, basket, service.quote(player, basket)).status());
        assertEquals(3, basket.getItem(0).getAmount());
        assertTrue(deposits.isEmpty());
    }

    @Test public void negativeOrNonFinitePricesAndTaxCannotProduceAPayment() throws Exception {
        basket.setItem(0, stack(Material.STONE, 3));
        for (double price : new double[] {-1, Double.NaN, Double.POSITIVE_INFINITY, Double.MAX_VALUE}) {
            configure(Material.STONE, price, null);
            assertEquals(SellBasketService.Status.EMPTY, service.sell(player, basket, service.quote(player, basket)).status());
        }
        configure(Material.STONE, 2, null);
        for (double tax : new double[] {-1, 2, Double.NaN, Double.POSITIVE_INFINITY}) {
            ConfigCacheManager.sellTaxPercent = tax;
            assertEquals(SellBasketService.Status.EMPTY, service.sell(player, basket, service.quote(player, basket)).status());
        }
        assertEquals(3, basket.getItem(0).getAmount());
        assertTrue(deposits.isEmpty());
    }

    @Test public void twoApprovedBasketsCannotBothUseTheLastStockCapacity() throws Exception {
        configure(Material.STONE, 2, 2);
        basket.setItem(0, stack(Material.STONE, 2));
        Inventory second = inventory(null);
        second.setItem(0, stack(Material.STONE, 2));
        var firstQuote = service.quote(player, basket);
        var secondQuote = service.quote(player, second);
        assertEquals(SellBasketService.Status.SOLD, service.sell(player, basket, firstQuote).status());
        assertEquals(SellBasketService.Status.CHANGED, service.sell(player, second, secondQuote).status());
        assertEquals(SellBasketService.Status.EMPTY, service.sell(player, second, service.quote(player, second)).status());
        assertEquals(2, second.getItem(0).getAmount());
        assertEquals(2.0, ShopDataManager.getStock(Material.STONE), 0.0);
        assertEquals(1, deposits.size());
        assertEquals(3.2, credited, 0.000001);
    }

    @Test public void reopeningBeforeQueuedConfirmationCannotPayForReturnedItems() {
        menu.open(player);
        top.get().setItem(0, stack(Material.STONE, 3));
        refresh();
        click(47, ClickType.LEFT, InventoryAction.PICKUP_ALL);
        menu.open(player);
        top.get().setItem(0, stack(Material.DIAMOND, 1));
        tick();
        assertTrue(deposits.isEmpty());
        assertEquals(3, playerItems[0].getAmount());
        refresh();
        click(47, ClickType.LEFT, InventoryAction.PICKUP_ALL);
        tick();
        assertEquals(1, deposits.size());
        assertEquals(8.0, credited, 0.000001);
        assertEquals(3, playerItems[0].getAmount());
        assertNull(playerItems[1]);
    }

    @Test public void revokingBasePermissionAfterConfirmReturnsItemsWithoutPayment() {
        menu.open(player);
        top.get().setItem(0, stack(Material.STONE, 3));
        refresh();
        click(47, ClickType.LEFT, InventoryAction.PICKUP_ALL);
        permissions.remove("dynamicshop.use");
        tick();
        assertTrue(deposits.isEmpty());
        assertEquals(3, playerItems[0].getAmount());
        assertNull(playerItems[1]);
    }
}
