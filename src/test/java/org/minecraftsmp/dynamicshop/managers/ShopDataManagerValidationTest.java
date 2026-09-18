package org.minecraftsmp.dynamicshop.managers;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;
import org.bukkit.Material;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.minecraftsmp.dynamicshop.DynamicShop;
import org.minecraftsmp.dynamicshop.commands.ShopCommand;
import org.minecraftsmp.dynamicshop.listeners.ShopListener;
import org.minecraftsmp.dynamicshop.support.PlainTestStack;
import org.minecraftsmp.dynamicshop.support.PluginTestFixture;
import static org.junit.Assert.*;

public class ShopDataManagerValidationTest {
    @Rule public TemporaryFolder directory = new TemporaryFolder();
    private final Map<Field, Object> settings = new HashMap<>();
    private final Map<Material, ShopDataManager.ShopItemConfig> items = new HashMap<>();
    private final Map<Material, Double> stocks = new HashMap<>();

    @Before
    public void setUp() throws Exception {
        for (Field field : ConfigCacheManager.class.getFields()) {
            if (Modifier.isStatic(field.getModifiers()) && !Modifier.isFinal(field.getModifiers())) {
                settings.put(field, field.get(null));
            }
        }
        items.putAll(ShopDataManager.itemConfigs);
        stocks.putAll(ShopDataManager.stockMap);
        ConfigCacheManager.dynamicPricingEnabled = true;
        ConfigCacheManager.useTimeInflation = false;
        ConfigCacheManager.useStockCurve = true;
        ConfigCacheManager.curveStrength = 0.9;
        ConfigCacheManager.maxStock = 500;
        ConfigCacheManager.minPriceMultiplier = 0.01;
        ConfigCacheManager.maxPriceMultiplier = 20;
        ConfigCacheManager.negativeStockPercentPerItem = 5;
        ConfigCacheManager.sellTaxPercent = 0.3;
        base(100);
        ShopDataManager.stockMap.put(Material.DIAMOND, 100.0);
    }

    @After
    public void tearDown() throws Exception {
        for (Map.Entry<Field, Object> entry : settings.entrySet()) entry.getKey().set(null, entry.getValue());
        ShopDataManager.itemConfigs.clear();
        ShopDataManager.itemConfigs.putAll(items);
        ShopDataManager.stockMap.clear();
        ShopDataManager.stockMap.putAll(stocks);
    }

    private void base(double value) {
        ShopDataManager.itemConfigs.put(Material.DIAMOND,
                new ShopDataManager.ShopItemConfig(value, null, null, null, null, false, false, null, null));
    }

    @Test
    public void eightyPercentTaxOnOneHundredThousandIsExactlyTwentyThousand() {
        base(100_000);
        ConfigCacheManager.sellTaxPercent = 0.8;
        ConfigCacheManager.useStockCurve = false;
        ConfigCacheManager.useTimeInflation = false;
        for (boolean dynamic : new boolean[] {false, true}) {
            ConfigCacheManager.dynamicPricingEnabled = dynamic;
            double regular = ShopDataManager.getTotalSellValue(Material.DIAMOND, 1);
            double variant = ShopDataManager.getTotalVariantSellValue("tax_precision_test", Material.DIAMOND, 100_000, 1);
            assertEquals("Tax must not leave a sub-cent binary remainder", 20_000.0, regular, 0.0);
            assertEquals(20_000.0, variant, 0.0);
            assertEquals("A provider truncating cents must still see $20,000", 20_000.0, Math.floor(regular * 100) / 100, 0.0);
        }
    }

    @Test
    public void taxCalculationPreservesSubCentPricesAndBoundaryRates() {
        ConfigCacheManager.dynamicPricingEnabled = false;
        base(0.003);
        ConfigCacheManager.sellTaxPercent = 0.8;
        assertEquals(0.0006, ShopDataManager.getTotalSellValue(Material.DIAMOND, 1), 0.0);
        assertEquals(0.0384, ShopDataManager.getTotalSellValue(Material.DIAMOND, 64), 0.0);
        ConfigCacheManager.sellTaxPercent = 0;
        assertEquals(0.003, ShopDataManager.getTotalSellValue(Material.DIAMOND, 1), 0.0);
        ConfigCacheManager.sellTaxPercent = 1;
        assertEquals(0.0, ShopDataManager.getTotalSellValue(Material.DIAMOND, 1), 0.0);
    }

    @Test
    public void genuineStockCurveDifferenceIsNotRoundedAwayAsTaxNoise() {
        base(100_000);
        ConfigCacheManager.dynamicPricingEnabled = true;
        ConfigCacheManager.useStockCurve = true;
        ConfigCacheManager.useTimeInflation = false;
        ConfigCacheManager.sellTaxPercent = 0.8;
        ConfigCacheManager.maxStock = 500_000;
        ConfigCacheManager.curveStrength = 0.9;
        ConfigCacheManager.negativeStockPercentPerItem = 0;
        ShopDataManager.stockMap.put(Material.DIAMOND, 0.0);
        assertEquals(100_000.0, ShopDataManager.getTotalBuyCost(Material.DIAMOND, 1), 0.0);
        double sell = ShopDataManager.getTotalSellValue(Material.DIAMOND, 1);
        assertEquals(19_999.991, sell, 1e-9);
        assertEquals(19_999.99, Math.floor(sell * 100) / 100, 0.0);
    }

    private void assertDisabledQuotes() {
        assertEquals(-1, ShopDataManager.getTotalBuyCost(Material.DIAMOND, 1), 0);
        assertEquals(-1, ShopDataManager.getTotalSellValue(Material.DIAMOND, 1), 0);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void rejectedSellQuotesPreserveInventoryAndStockAcrossCommandsAndGui() throws Exception {
        DynamicShop plugin = PluginTestFixture.plugin(directory.getRoot());
        ShopListener listener = new ShopListener(plugin);
        PluginTestFixture.set(plugin, DynamicShop.class, "shopListener", listener);
        MultiCurrencyEconomyManager economy = new MultiCurrencyEconomyManager(plugin) {
            @Override public String getCurrency(Material material) { return "default"; }
            @Override public void deposit(Player player, double amount, String currency) {
                fail("Rejected sale must never pay out");
            }
        };
        PluginTestFixture.set(plugin, DynamicShop.class, "economyManager", economy);
        PlainTestStack held = new PlainTestStack(Material.DIAMOND, 10);
        PlayerInventory inventory = PluginTestFixture.proxy(PlayerInventory.class, (object, method, args) -> switch (method.getName()) {
            case "getContents", "getStorageContents" -> new ItemStack[] {held};
            case "getItemInMainHand", "getItem" -> held;
            case "getSize" -> 1;
            default -> throw new AssertionError("Rejected quote must not modify inventory: " + method.getName());
        });
        Player player = PluginTestFixture.proxy(Player.class, (object, method, args) -> switch (method.getName()) {
            case "hasPermission" -> true;
            case "getInventory" -> inventory;
            case "sendMessage" -> null;
            default -> throw new AssertionError(method.getName());
        });
        Field templateField = ShopDataManager.class.getDeclaredField("itemTemplates");
        templateField.setAccessible(true);
        Map<Material, ItemStack> templates = (Map<Material, ItemStack>) templateField.get(null);
        ItemStack oldTemplate = templates.put(Material.DIAMOND, held);
        try {
            ConfigCacheManager.sellTaxPercent = Double.NaN;
            new ShopCommand(plugin).onCommand(player, null, "shop", new String[] {"sellhand"});
            new ShopCommand(plugin).onCommand(player, null, "shop", new String[] {"sellall"});
            listener.sellItem(player, Material.DIAMOND, 10, null);
            assertEquals(10, held.getAmount());
            assertEquals(100, ShopDataManager.getStock(Material.DIAMOND), 0);
        } finally {
            if (oldTemplate == null) templates.remove(Material.DIAMOND);
            else templates.put(Material.DIAMOND, oldTemplate);
        }
    }

    @Test
    public void invalidQuantitiesCannotProduceFreePurchasesOrPassStockChecks() {
        for (double amount : new double[] {0, -1, Double.NaN, Double.POSITIVE_INFINITY, Double.MAX_VALUE}) {
            assertEquals(-1, ShopDataManager.getTotalBuyCost(Material.DIAMOND, amount), 0);
            assertEquals(-1, ShopDataManager.getTotalVariantBuyCost("test", Material.DIAMOND, 100, amount), 0);
        }
        for (int amount : new int[] {0, -1, Integer.MIN_VALUE}) {
            assertFalse(ShopDataManager.canBuy(Material.DIAMOND, amount));
            assertFalse(ShopDataManager.canSell(Material.DIAMOND, amount));
            assertFalse(ShopDataManager.canBuyVariant("test", Material.DIAMOND, amount));
            assertFalse(ShopDataManager.canSellVariant("test", Material.DIAMOND, amount));
            assertEquals(-1, ShopDataManager.getTotalSellValue(Material.DIAMOND, amount), 0);
            assertEquals(-1, ShopDataManager.getTotalVariantSellValue("test", Material.DIAMOND, 100, amount), 0);
        }
    }

    @Test
    public void nonFiniteBasePricesAndStocksFailClosed() {
        for (double invalid : new double[] {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
            base(invalid);
            assertTrue(ShopDataManager.isItemDisabled(Material.DIAMOND));
            assertDisabledQuotes();
            base(100);
            ShopDataManager.stockMap.put(Material.DIAMOND, invalid);
            assertDisabledQuotes();
            ShopDataManager.stockMap.put(Material.DIAMOND, 100.0);
        }
    }

    @Test
    public void invalidTaxCannotPayMoreThanTheUntaxedValue() {
        for (boolean dynamic : new boolean[] {true, false}) {
            ConfigCacheManager.dynamicPricingEnabled = dynamic;
            for (double tax : new double[] {-0.01, 1.01, Double.NaN, Double.POSITIVE_INFINITY}) {
                ConfigCacheManager.sellTaxPercent = tax;
                assertEquals(-1, ShopDataManager.getTotalSellValue(Material.DIAMOND, 1), 0);
                assertEquals(-1, ShopDataManager.getTotalVariantSellValue("test", Material.DIAMOND, 100, 1), 0);
            }
        }
    }

    @Test
    public void overflowingQuotesAreRejected() {
        ConfigCacheManager.dynamicPricingEnabled = false;
        base(Double.MAX_VALUE);
        assertEquals(-1, ShopDataManager.getTotalBuyCost(Material.DIAMOND, 2), 0);
        assertEquals(-1, ShopDataManager.getTotalSellValue(Material.DIAMOND, 2), 0);
    }

    @Test
    public void unsupportedCurveParametersFailClosed() {
        for (double curve : new double[] {-0.1, 1.1, Double.NaN, Double.POSITIVE_INFINITY}) {
            ConfigCacheManager.curveStrength = curve;
            assertDisabledQuotes();
        }
        ConfigCacheManager.curveStrength = 0.9;
        ConfigCacheManager.maxStock = 0;
        assertDisabledQuotes();
    }

    @Test
    public void validFreeItemsRemainFree() {
        base(0);
        assertEquals(0, ShopDataManager.getTotalBuyCost(Material.DIAMOND, 1), 0);
        assertEquals(0, ShopDataManager.getTotalSellValue(Material.DIAMOND, 1), 0);
    }
}
