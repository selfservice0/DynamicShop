package org.minecraftsmp.dynamicshop.managers;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ShopDataManagerPricingTest {

    private boolean originalUseTimeInflation;
    private double originalHourlyIncreasePercent;
    private double originalMinPriceMultiplier;
    private double originalMaxPriceMultiplier;
    private boolean originalCorrectionEnabled;
    private double originalCorrectionThreshold;
    private double originalCorrectionReduction;

    @Before
    public void setUp() {
        originalUseTimeInflation = ConfigCacheManager.useTimeInflation;
        originalHourlyIncreasePercent = ConfigCacheManager.hourlyIncreasePercent;
        originalMinPriceMultiplier = ConfigCacheManager.minPriceMultiplier;
        originalMaxPriceMultiplier = ConfigCacheManager.maxPriceMultiplier;
        originalCorrectionEnabled = ConfigCacheManager.highInflationCorrectionEnabled;
        originalCorrectionThreshold = ConfigCacheManager.highInflationCorrectionThresholdPercent;
        originalCorrectionReduction = ConfigCacheManager.highInflationCorrectionReductionPercent;

        ConfigCacheManager.useTimeInflation = true;
        ConfigCacheManager.hourlyIncreasePercent = 2.0;
        ConfigCacheManager.minPriceMultiplier = 0.01;
        ConfigCacheManager.maxPriceMultiplier = 20.0;
        ConfigCacheManager.highInflationCorrectionEnabled = true;
        ConfigCacheManager.highInflationCorrectionThresholdPercent = 100.0;
        ConfigCacheManager.highInflationCorrectionReductionPercent = 50.0;
    }

    @After
    public void tearDown() {
        ConfigCacheManager.useTimeInflation = originalUseTimeInflation;
        ConfigCacheManager.hourlyIncreasePercent = originalHourlyIncreasePercent;
        ConfigCacheManager.minPriceMultiplier = originalMinPriceMultiplier;
        ConfigCacheManager.maxPriceMultiplier = originalMaxPriceMultiplier;
        ConfigCacheManager.highInflationCorrectionEnabled = originalCorrectionEnabled;
        ConfigCacheManager.highInflationCorrectionThresholdPercent = originalCorrectionThreshold;
        ConfigCacheManager.highInflationCorrectionReductionPercent = originalCorrectionReduction;
    }

    @Test
    public void suppliedSugarCaneConfigurationKeepsBulkAndStackPricingConsistent() {
        double stack = ShopDataManager.computeSellIntegral(
                1.0, 500.0, 64, 500.0, 0.0, 0.9, 1.05, 0.0) * 0.70;
        double inventory = ShopDataManager.computeSellIntegral(
                1.0, 500.0, 2304, 500.0, 0.0, 0.9, 1.05, 0.0) * 0.70;

        assertEquals(4.48, stack, 0.0000001);
        assertEquals(161.28, inventory, 0.0000001);
        assertEquals(stack * 36.0, inventory, 0.0000001);
    }

    @Test
    public void bulkSellAppliesInflationCorrectionAtRecoveryBoundary() {
        double shortageHoursAtCap = Math.log(20.0) / Math.log(1.02);

        double correctedBulk = ShopDataManager.computeSellIntegral(
                1.0, -100.0, 2304, 500.0, 0.0, 0.9, 1.05, shortageHoursAtCap);

        double beforeRecovery = ShopDataManager.computeSellIntegral(
                1.0, -100.0, 100, 500.0, 0.0, 0.9, 1.05, shortageHoursAtCap);
        double afterRecovery = ShopDataManager.computeSellIntegral(
                1.0, 0.0, 2204, 500.0, 0.0, 0.9, 1.05, shortageHoursAtCap);

        assertEquals(beforeRecovery + afterRecovery, correctedBulk, 0.0000001);

        ConfigCacheManager.highInflationCorrectionEnabled = false;
        double uncorrectedBulk = ShopDataManager.computeSellIntegral(
                1.0, -100.0, 2304, 500.0, 0.0, 0.9, 1.05, shortageHoursAtCap);

        assertTrue("Recovery correction must lower the post-recovery payout",
                correctedBulk < uncorrectedBulk);
    }

    @Test
    public void correctionDoesNotChangeSalesThatStartAboveZero() {
        double shortageHoursAtCap = Math.log(20.0) / Math.log(1.02);
        double withCorrection = ShopDataManager.computeSellIntegral(
                1.0, 500.0, 2304, 500.0, 0.0, 0.9, 1.05, shortageHoursAtCap);

        ConfigCacheManager.highInflationCorrectionEnabled = false;
        double withoutCorrection = ShopDataManager.computeSellIntegral(
                1.0, 500.0, 2304, 500.0, 0.0, 0.9, 1.05, shortageHoursAtCap);

        assertEquals(withoutCorrection, withCorrection, 0.0000001);
    }
}
