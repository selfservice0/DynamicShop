package org.minecraftsmp.dynamicshop.web;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;

/** Bounded aggregate counters only: no visitor IDs, addresses, URLs, or event log. */
public final class WebUsageMetrics {
    public static final Set<String> EVENTS =
            Set.of("page_view", "item_open", "search", "watchlist_change");
    private final BooleanSupplier enabled;
    private final LongSupplier clock;
    private final Map<String, Integer> counts = new HashMap<>();
    private long window;
    private int accepted;

    public WebUsageMetrics(BooleanSupplier enabled) {
        this(enabled, System::currentTimeMillis);
    }

    WebUsageMetrics(BooleanSupplier enabled, LongSupplier clock) {
        this.enabled = enabled;
        this.clock = clock;
    }

    public synchronized boolean isEnabled() {
        boolean on = enabled.getAsBoolean();
        if (!on) counts.clear();
        return on;
    }

    public synchronized boolean record(String event) {
        if (!isEnabled() || !EVENTS.contains(event)) return false;
        long minute = clock.getAsLong() / 60_000;
        if (minute != window) {
            window = minute;
            accepted = 0;
        }
        // Bound untrusted public events without identifying or retaining visitors.
        if (accepted >= 1000) return false;
        accepted++;
        counts.merge(event, 1, Integer::sum);
        return true;
    }

    public synchronized int drain(String event) {
        if (!isEnabled()) return 0;
        Integer value = counts.remove(event);
        return value == null ? 0 : value;
    }

    public synchronized void clear() {
        counts.clear();
    }
}
