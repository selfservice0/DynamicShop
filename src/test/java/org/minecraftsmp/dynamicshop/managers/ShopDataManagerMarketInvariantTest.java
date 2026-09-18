package org.minecraftsmp.dynamicshop.managers;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** Instantaneous trade invariants; time-based and crafting routes are audited separately. */
public class ShopDataManagerMarketInvariantTest {
    private final Map<Field, Object> originalSettings = new HashMap<>();
    private Method integral;

    @Before
    public void setUp() throws Exception {
        for (Field field : ConfigCacheManager.class.getFields()) {
            if (Modifier.isStatic(field.getModifiers()) && !Modifier.isFinal(field.getModifiers())) {
                originalSettings.put(field, field.get(null));
            }
        }
        ConfigCacheManager.useTimeInflation = true;
        ConfigCacheManager.hourlyIncreasePercent = 2;
        ConfigCacheManager.minPriceMultiplier = 0.01;
        ConfigCacheManager.maxPriceMultiplier = 20;
        ConfigCacheManager.highInflationCorrectionEnabled = true;
        ConfigCacheManager.highInflationCorrectionThresholdPercent = 100;
        ConfigCacheManager.highInflationCorrectionReductionPercent = 50;
        integral = ShopDataManager.class.getDeclaredMethod("computeClampedIntegral",
                double.class, double.class, double.class, double.class,
                double.class, double.class, double.class, double.class);
        integral.setAccessible(true);
    }

    @After
    public void tearDown() throws Exception {
        for (Map.Entry<Field, Object> entry : originalSettings.entrySet()) {
            entry.getKey().set(null, entry.getValue());
        }
    }

    private double gross(double start, double end, double max, double curve, double hours) throws Exception {
        return (double) integral.invoke(null, 1.0, start, end, max, 0.0, curve, 1.05,
                ShopDataManager.getInflationMultiplier(hours));
    }

    @Test
    public void buyingThenResellingCannotCreateMoneyAcrossStockAndInflationBoundaries() throws Exception {
        Random random = new Random(60219);
        for (int i = 0; i < 10000; i++) {
            double stockAfterBuy = random.nextInt(2001);
            int quantity = 1 + random.nextInt(2304);
            double max = new double[] {1, 10, 500, 10000}[random.nextInt(4)];
            double curve = new double[] {0, 0.5, 0.9, 1}[random.nextInt(4)];
            double hours = new double[] {0, 20, 35, 100, 200}[random.nextInt(5)];
            double cost = gross(stockAfterBuy, stockAfterBuy + quantity, max, curve, hours);
            double payout = 0.7 * ShopDataManager.computeSellIntegral(
                    1, stockAfterBuy, quantity, max, 0, curve, 1.05, hours);
            assertTrue("Quote must be finite and nonnegative", Double.isFinite(cost) && Double.isFinite(payout)
                    && cost >= 0 && payout >= 0);
            assertTrue("Immediate resale must not refund more than the purchase cost", payout <= cost + 1e-8);
        }
    }

    @Test
    public void splittingOrdersDoesNotImproveQuotesWhenInflationDoesNotChange() throws Exception {
        Random random = new Random(92813);
        for (int i = 0; i < 10000; i++) {
            double start = random.nextInt(12001) - 6000;
            int quantity = 2 + random.nextInt(2303);
            int first = 1 + random.nextInt(quantity - 1);
            double max = new double[] {1, 10, 500, 10000}[random.nextInt(4)];
            double curve = new double[] {0, 0.5, 0.9, 1}[random.nextInt(4)];
            double whole = gross(start, start + quantity, max, curve, 0);
            double split = gross(start, start + first, max, curve, 0)
                    + gross(start + first, start + quantity, max, curve, 0);
            assertEquals("Split and bulk orders must have equal gross cost", whole, split,
                    Math.max(1e-8, Math.abs(whole) * 1e-10));
        }
    }

    @Test
    public void clampedCurveAgreesWithIndependentNumericalIntegration() throws Exception {
        // Sample within each continuous region; avoid numerical integration over the price jump at 500.
        double[][] ranges = {{-100, -1}, {1, 499}, {501, 900}};
        for (double hours : new double[] {0, 20, 100, 200}) {
            double timeMultiplier = ShopDataManager.getInflationMultiplier(hours);
            for (double[] range : ranges) {
                int steps = 20000;
                double width = (range[1] - range[0]) / steps;
                double numerical = 0;
                for (int i = 0; i < steps; i++) {
                    double stock = range[0] + (i + 0.5) * width;
                    double rawPrice = stock < 0 ? Math.pow(1.05, -stock)
                            : stock < 500 ? 1 - 0.5 * 0.9 * stock / 500 : 0.1;
                    numerical += Math.max(0.01, Math.min(rawPrice * timeMultiplier, 20)) * width;
                }
                assertEquals("Analytical price integral must match numerical quadrature",
                        numerical, gross(range[0], range[1], 500, 0.9, hours),
                        Math.max(1e-5, numerical * 1e-7));
            }
        }
    }
}
