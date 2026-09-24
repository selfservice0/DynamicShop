package org.minecraftsmp.dynamicshop.managers;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.logging.*;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import org.minecraftsmp.dynamicshop.DynamicShop;
import org.minecraftsmp.dynamicshop.commands.ShopCommand;
import org.minecraftsmp.dynamicshop.listeners.ShopListener;
import org.minecraftsmp.dynamicshop.support.PlainTestStack;
import org.minecraftsmp.dynamicshop.support.PluginTestFixture;
import org.minecraftsmp.dynamicshop.transactions.TransactionLogger;
import static org.junit.Assert.*;

public class SalePaymentFailureTest {
    @Rule public TemporaryFolder directory = new TemporaryFolder();
    private final Map<Field, Object> settings = new HashMap<>();
    private final Map<Map<Object, Object>, Map<Object, Object>> originalMaps =
            new IdentityHashMap<>();
    private final Map<Set<Object>, Set<Object>> originalSets = new IdentityHashMap<>();
    private final ItemStack[] contents = new ItemStack[41];
    private final List<String> messages = new ArrayList<>();
    private final List<LogRecord> logs = new ArrayList<>();
    private final UUID playerId = UUID.randomUUID();
    private DynamicShop plugin;
    private ShopListener listener;
    private Player player;
    private MultiCurrencyEconomyManager economy;
    private Object previousServer;
    private int depositCalls;
    private double credited;
    private String mode = "FAILURE";

    @Before
    @SuppressWarnings("unchecked")
    public void setUp() throws Exception {
        for (Field field : ConfigCacheManager.class.getFields()) {
            if (Modifier.isStatic(field.getModifiers())
                    && !Modifier.isFinal(field.getModifiers())) {
                settings.put(field, field.get(null));
            }
        }
        for (Field field : ShopDataManager.class.getDeclaredFields()) {
            if (!Modifier.isStatic(field.getModifiers())) continue;
            field.setAccessible(true);
            Object value = field.get(null);
            if (value instanceof Map<?, ?> map) {
                originalMaps.put(
                        (Map<Object, Object>) map, new HashMap<>((Map<Object, Object>) map));
            } else if (value instanceof Set<?> set) {
                originalSets.put((Set<Object>) set, new HashSet<>((Set<Object>) set));
            }
        }
        Field serverField = Bukkit.class.getDeclaredField("server");
        serverField.setAccessible(true);
        previousServer = serverField.get(null);
        BukkitScheduler scheduler =
                PluginTestFixture.proxy(
                        BukkitScheduler.class,
                        (object, method, args) -> {
                            if (method.getName().equals("runTaskLater")) return null;
                            throw new AssertionError(method.getName());
                        });
        serverField.set(
                null,
                PluginTestFixture.proxy(
                        Server.class,
                        (object, method, args) -> {
                            if (method.getName().equals("getScheduler")) return scheduler;
                            throw new AssertionError(method.getName());
                        }));
        plugin = PluginTestFixture.plugin(directory.getRoot());
        plugin.getLogger().setUseParentHandlers(false);
        plugin.getLogger().setLevel(Level.ALL);
        plugin.getLogger()
                .addHandler(
                        new Handler() {
                            @Override
                            public void publish(LogRecord record) {
                                logs.add(record);
                            }

                            @Override
                            public void flush() {}

                            @Override
                            public void close() {}
                        });
        YamlConfiguration messageConfig = new YamlConfiguration();
        messageConfig.set("messages.sold-item-success", "Sold {amount}x {item} for {price}");
        PluginTestFixture.set(
                plugin.getMessageManager(), MessageManager.class, "messagesConfig", messageConfig);
        listener = new ShopListener(plugin);
        PluginTestFixture.set(plugin, DynamicShop.class, "shopListener", listener);
        PluginTestFixture.set(
                plugin, DynamicShop.class, "transactionLogger", new TransactionLogger(plugin));
        Economy provider =
                PluginTestFixture.proxy(
                        Economy.class,
                        (object, method, args) ->
                                switch (method.getName()) {
                                    case "getName" -> "TestEconomy";
                                    case "format" -> Double.toString((double) args[0]);
                                    case "depositPlayer" -> {
                                        depositCalls++;
                                        double amount = (double) args[1];
                                        if (mode.equals("THROW_AFTER_CREDIT")) {
                                            credited += amount;
                                            throw new IllegalStateException("response lost");
                                        }
                                        if (mode.equals("NULL")) yield null;
                                        if (mode.equals("SUCCESS")
                                                || (mode.equals("MIXED") && amount == 400)) {
                                            credited += amount;
                                            yield new EconomyResponse(
                                                    amount,
                                                    credited,
                                                    EconomyResponse.ResponseType.SUCCESS,
                                                    "");
                                        }
                                        yield new EconomyResponse(
                                                0,
                                                credited,
                                                EconomyResponse.ResponseType.FAILURE,
                                                "Balance limit\nreached");
                                    }
                                    default -> throw new AssertionError(method.getName());
                                });
        economy =
                new MultiCurrencyEconomyManager(plugin) {
                    @Override
                    public String getCurrency(Material material) {
                        return material.name();
                    }
                };
        PluginTestFixture.set(economy, MultiCurrencyEconomyManager.class, "vaultEconomy", provider);
        PluginTestFixture.set(plugin, DynamicShop.class, "economyManager", economy);
        PlayerInventory inventory =
                PluginTestFixture.proxy(
                        PlayerInventory.class,
                        (object, method, args) ->
                                switch (method.getName()) {
                                    case "getContents" -> contents.clone();
                                    case "getSize" -> contents.length;
                                    case "getItem" -> contents[(int) args[0]];
                                    case "setItem" -> {
                                        contents[(int) args[0]] = (ItemStack) args[1];
                                        yield null;
                                    }
                                    case "getItemInMainHand" -> contents[0];
                                    case "setItemInMainHand" -> {
                                        contents[0] = (ItemStack) args[0];
                                        yield null;
                                    }
                                    default -> throw new AssertionError(method.getName());
                                });
        player =
                PluginTestFixture.proxy(
                        Player.class,
                        (object, method, args) ->
                                switch (method.getName()) {
                                    case "getInventory" -> inventory;
                                    case "hasPermission" -> true;
                                    case "getUniqueId" -> playerId;
                                    case "getName" -> "Seller";
                                    case "sendMessage" -> {
                                        messages.add(String.valueOf(args[0]));
                                        yield null;
                                    }
                                    default -> throw new AssertionError(method.getName());
                                });
        ConfigCacheManager.dynamicPricingEnabled = false;
        ConfigCacheManager.useTimeInflation = false;
        ConfigCacheManager.highInflationCorrectionEnabled = false;
        ConfigCacheManager.sellTaxPercent = 0.8;
        ConfigCacheManager.transactionCooldownMs = 0;
        configureItem(Material.GOLDEN_HORSE_ARMOR, 100_000, "Iridium Watering Can");
        configureItem(Material.DIAMOND, 2_000, "Diamond");
    }

    @After
    public void tearDown() throws Exception {
        for (Map.Entry<Field, Object> setting : settings.entrySet())
            setting.getKey().set(null, setting.getValue());
        originalMaps.forEach(
                (map, original) -> {
                    map.clear();
                    map.putAll(original);
                });
        originalSets.forEach(
                (set, original) -> {
                    set.clear();
                    set.addAll(original);
                });
        Field serverField = Bukkit.class.getDeclaredField("server");
        serverField.setAccessible(true);
        serverField.set(null, previousServer);
    }

    @SuppressWarnings("unchecked")
    private void configureItem(Material material, double price, String name) throws Exception {
        ShopDataManager.itemConfigs.put(
                material,
                new ShopDataManager.ShopItemConfig(
                        price, null, null, null, null, false, false, null, null));
        Field templates = ShopDataManager.class.getDeclaredField("itemTemplates");
        templates.setAccessible(true);
        ((Map<Material, ItemStack>) templates.get(null))
                .put(material, new PlainTestStack(material, 1));
        Field names = ShopDataManager.class.getDeclaredField("customNames");
        names.setAccessible(true);
        ((Map<Material, String>) names.get(null)).put(material, name);
    }

    private void resetSale() throws Exception {
        PluginTestFixture.set(
                plugin,
                DynamicShop.class,
                "featureMetrics",
                new org.minecraftsmp.dynamicshop.metrics.FeatureMetrics(plugin));
        Arrays.fill(contents, null);
        contents[0] = new PlainTestStack(Material.GOLDEN_HORSE_ARMOR, 1);
        ShopDataManager.stockMap.put(Material.GOLDEN_HORSE_ARMOR, 0.0);
        messages.clear();
        logs.clear();
        depositCalls = 0;
        credited = 0;
        PluginTestFixture.set(
                plugin, DynamicShop.class, "transactionLogger", new TransactionLogger(plugin));
    }

    private void sell(String path) {
        if (path.equals("GUI")) listener.sellItem(player, Material.GOLDEN_HORSE_ARMOR, 1, null);
        else new ShopCommand(plugin).onCommand(player, null, "shop", new String[] {path});
    }

    @Test
    public void rejectedOrUncertainSalesLogDetailsWithoutRestoringRetryingOrReportingSuccess()
            throws Exception {
        for (String result : List.of("FAILURE", "NULL", "THROW_AFTER_CREDIT")) {
            mode = result;
            for (String path : List.of("GUI", "sellhand", "sellall")) {
                resetSale();
                sell(path);
                assertNull(path, contents[0]);
                assertEquals(1, depositCalls);
                assertEquals(1.0, ShopDataManager.getStock(Material.GOLDEN_HORSE_ARMOR), 0);
                assertEquals(result.equals("THROW_AFTER_CREDIT") ? 20_000 : 0, credited, 0);
                assertTrue(plugin.getTransactionLogger().getRecentTransactions().isEmpty());
                assertEquals(0, plugin.getFeatureMetrics().transactionSnapshot().total());
                assertEquals(1, messages.size());
                assertTrue(messages.get(0).contains("could not be confirmed"));
                assertEquals(1, logs.size());
                LogRecord log = logs.get(0);
                assertEquals(Level.SEVERE, log.getLevel());
                for (String detail :
                        List.of(
                                "player=Seller",
                                playerId.toString(),
                                "1x Iridium Watering Can",
                                "[GOLDEN_HORSE_ARMOR]",
                                "amount=20000.0",
                                "currency=GOLDEN_HORSE_ARMOR",
                                "provider=Vault/TestEconomy",
                                "items and stock were not restored",
                                "no payment retry")) {
                    assertTrue(detail, log.getMessage().contains(detail));
                }
                assertTrue(
                        log.getMessage()
                                .contains(
                                        "outcome="
                                                + (result.equals("FAILURE")
                                                        ? "FAILED"
                                                        : "UNKNOWN")));
                assertFalse(log.getMessage().contains("\n"));
                assertTrue(
                        log.getMessage()
                                .contains(
                                        result.equals("FAILURE")
                                                ? "Balance limit reached"
                                                : result.equals("NULL")
                                                        ? "Provider returned no response"
                                                        : "response lost"));
                sell(path);
                assertEquals("Empty inventory must not initiate another deposit", 1, depositCalls);
            }
        }
    }

    @Test
    public void successfulSalesStillPayAndRecordExactlyOnce() throws Exception {
        mode = "SUCCESS";
        for (String path : List.of("GUI", "sellhand", "sellall")) {
            resetSale();
            sell(path);
            sell(path);
            assertEquals(1, depositCalls);
            assertEquals(20_000, credited, 0);
            assertNull(contents[0]);
            assertEquals(1, plugin.getTransactionLogger().getRecentTransactions().size());
            assertEquals(1, plugin.getFeatureMetrics().transactionSnapshot().sells());
            assertTrue(logs.isEmpty());
            assertEquals(1, messages.stream().filter(message -> message.contains("Sold")).count());
        }
    }

    @Test
    public void sellAllReportsOnlySuccessfullyPaidCurrencyGroups() throws Exception {
        mode = "MIXED";
        resetSale();
        contents[1] = new PlainTestStack(Material.DIAMOND, 1);
        sell("sellall");
        assertEquals(2, depositCalls);
        assertEquals(400, credited, 0);
        assertNull(contents[0]);
        assertNull(contents[1]);
        assertEquals(1, logs.size());
        assertEquals(1, plugin.getTransactionLogger().getRecentTransactions().size());
        assertEquals(
                "DIAMOND", plugin.getTransactionLogger().getRecentTransactions().get(0).getItem());
        assertEquals(1, plugin.getFeatureMetrics().transactionSnapshot().total());
        String success =
                messages.stream()
                        .filter(message -> message.contains("Sold"))
                        .findFirst()
                        .orElseThrow();
        assertTrue(success.contains("1 items"));
        assertTrue(success.contains("400.0"));
        assertFalse(success.contains("20000"));
    }

    public static class CoinsApi {
        static int calls;

        public static Object getCurrency(String id) {
            return new Object();
        }

        public static boolean accept(Player player, Object currency, double amount) {
            calls++;
            return true;
        }

        public static boolean reject(Player player, Object currency, double amount) {
            calls++;
            return false;
        }

        public static void acceptVoid(Player player, Object currency, double amount) {
            calls++;
        }

        public static void throwAfterCall(Player player, Object currency, double amount) {
            calls++;
            throw new IllegalStateException("CoinsEngine response lost");
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    public void coinsEngineResultsAndInvocationErrorsAreHandledWithoutRetry() throws Exception {
        PluginTestFixture.set(economy, MultiCurrencyEconomyManager.class, "useCoinEngine", true);
        PluginTestFixture.set(
                economy,
                MultiCurrencyEconomyManager.class,
                "coinEngineGetCurrencyMethod",
                CoinsApi.class.getMethod("getCurrency", String.class));
        Field cacheField =
                MultiCurrencyEconomyManager.class.getDeclaredField("coinEngineCurrencyCache");
        cacheField.setAccessible(true);
        ((Map<String, Object>) cacheField.get(economy)).put("coins", new Object());
        for (String method : List.of("accept", "reject", "acceptVoid", "throwAfterCall")) {
            CoinsApi.calls = 0;
            logs.clear();
            messages.clear();
            PluginTestFixture.set(
                    economy,
                    MultiCurrencyEconomyManager.class,
                    "coinEngineAddPlayerBalanceMethod",
                    CoinsApi.class.getMethod(method, Player.class, Object.class, double.class));
            boolean paid = economy.depositSale(player, 20_000, "coins", "1x Iridium Watering Can");
            assertEquals(method.startsWith("accept"), paid);
            assertEquals(1, CoinsApi.calls);
            if (paid) {
                assertTrue(logs.isEmpty());
                assertTrue(messages.isEmpty());
            } else {
                assertEquals(1, logs.size());
                assertEquals(1, messages.size());
                assertTrue(
                        logs.get(0)
                                .getMessage()
                                .contains(
                                        method.equals("reject")
                                                ? "outcome=FAILED"
                                                : "outcome=UNKNOWN"));
                assertTrue(
                        logs.get(0)
                                .getMessage()
                                .contains(
                                        method.equals("reject")
                                                ? "CoinsEngine returned false"
                                                : "CoinsEngine response lost"));
            }
        }
    }
}
