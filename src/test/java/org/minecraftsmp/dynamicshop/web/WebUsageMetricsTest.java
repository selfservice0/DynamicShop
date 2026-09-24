package org.minecraftsmp.dynamicshop.web;

import org.junit.Test;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.Assert.*;

public class WebUsageMetricsTest {
    @Test
    public void countsOnlyAllowedActionsAndDrainsOnce() {
        var metrics = new WebUsageMetrics(() -> true);
        assertTrue(metrics.record("page_view"));
        assertTrue(metrics.record("page_view"));
        assertFalse(metrics.record("search:secret text"));
        assertEquals(2, metrics.drain("page_view"));
        assertEquals(0, metrics.drain("page_view"));
    }

    @Test
    public void disablingDiscardsPendingCountsAndStopsCollection() {
        var on = new AtomicBoolean(true);
        var metrics = new WebUsageMetrics(on::get);
        metrics.record("search");
        on.set(false);
        assertFalse(metrics.record("page_view"));
        on.set(true);
        assertEquals(0, metrics.drain("search"));
    }

    @Test
    public void publicEventRateIsBoundedWithoutVisitorIdentifiers() {
        var clock = new AtomicLong(60_000);
        var metrics = new WebUsageMetrics(() -> true, clock::get);
        for (int i = 0; i < 1000; i++) assertTrue(metrics.record("item_open"));
        assertFalse(metrics.record("item_open"));
        clock.addAndGet(60_000);
        assertTrue(metrics.record("item_open"));
        assertEquals(1001, metrics.drain("item_open"));
    }
}
