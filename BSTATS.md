# DynamicShop bStats charts

Project ID: **28506**. In the bStats plugin page, choose **Edit** and add the charts below using the exact IDs and chart types. Adding the code alone does not create dashboard charts. Reference: https://bstats.org/docs/custom-charts.

Install the updated JAR and fully restart the server to register the new chart suppliers. Data appears on bStats' normal reporting schedule, not immediately after an in-game transaction. Servers need this updated build and enabled metrics to contribute.

## Feature adoption

Create one **Advanced Pie** chart with ID `custom_item_adders` and title **Custom item adders**. Its four categories are **Oraxen**, **Nexo**, **ItemsAdder**, and **No custom adder**. Each server contributes 1 for every enabled supported plugin; it contributes 1 to No custom adder only if all three are absent or disabled. A server with multiple adders contributes to multiple categories, so percentages represent reported selections, not mutually exclusive percentages of servers. Detection runs only when bStats collects a report.

This replaces the three old charts `oraxen_enabled`, `nexo_enabled`, and `itemsadder_enabled`. Remove those charts from the dashboard if already created; this build no longer submits them. Older builds may still submit the old IDs until updated. Existing chart history is not migrated automatically.

The remaining feature charts use **Simple Pie**:

| Chart ID | Suggested title | Values / meaning |
|---|---|---|
| `players_per_server` | Online players per server | 0 / 1-9 / 10-24 / 25-49 / 50-99 / 100+; online player count at collection time |
| `gui_provider` | Selected GUI provider | nexo / oraxen / none; resolved provider, including automatic selection |
| `custom_gui_backgrounds` | Custom GUI backgrounds configured | Configured / None; glyph, spacing, custom font markup, or legacy glyph characters in configured GUI titles |
| `custom_gui_icons` | Custom GUI icons configured | Configured / None; custom filler/category IDs or a resolvable custom navigation item |
| `dynamic_pricing_enabled` | Dynamic pricing enabled | Enabled / Disabled; dynamic-pricing master toggle |
| `time_inflation_enabled` | Time inflation enabled | Enabled only when dynamic pricing and time inflation are enabled and the hourly rate is positive |
| `transactions_per_server_last_30m` | Transactions per server in the last 30 minutes | 0 / 1-9 / 10-99 / 100-999 / 1000+ |
| `website_design` | Website design | classic / market / inventory / nova / chest / dsx; already implemented, subject to the separate website metrics switch |

An enabled plugin does not prove its items are used. GUI charts indicate server-side configuration or available navigation items, not that each player has accepted or successfully rendered a resource pack. ItemsAdder is a custom-item integration; it is not a selectable GUI glyph provider in this build.

The built-in bStats player metric already reports total online players. `players_per_server` adds a distribution of server populations: each reporting server contributes to one range, including empty servers in `0`. It counts all online players on that server, not just shop users, and does not expose individual servers or player identities. It reads the online collection size only at report time, with no player iteration, join/quit tracking, or additional timer. This custom chart respects `metrics.feature-usage`.

## Transaction activity

These charts use **Single Line**. Each server submits the count in its current minute plus the preceding 29 minute buckets; bStats combines the contributing servers' values.

| Chart ID | Suggested title |
|---|---|
| `transactions_last_30m` | All completed trade records in the last 30 minutes |
| `server_buys_last_30m` | Server-shop buys in the last 30 minutes |
| `server_sells_last_30m` | Server-shop sells in the last 30 minutes |
| `player_shop_purchases_last_30m` | Player-shop purchases in the last 30 minutes |
| `dynamic_pricing_trades_last_30m` | Regular/variant trades with dynamic pricing enabled |
| `time_inflation_enabled_trades_last_30m` | Regular/variant trades with time inflation enabled |

Counts represent completed records, not items, clicks, price previews, or currency totals. Buying a stack counts once. `/shop sellall` can produce multiple records, one for each item sale recorded by the transaction logger. Special purchases count as server buys; they do not count as dynamic-pricing trades. A completed player-shop purchase counts once, separately from server-shop buys and sells; listing creation and reclamation are not trades.

The inflation activity chart records trades while the time-inflation feature is enabled. It does **not** assert that an inflation multiplier raised the final price: shortage hours, stock, and price clamps still affect the result. The pricing settings are captured when each new transaction is recorded, so changing settings does not relabel earlier records in the window.

Historical CSV/log entries loaded on startup are not counted again. The bounded counters exist only in memory and start empty after a restart or an opt-out. Newly started servers initially contribute a partial window. Samples may overlap: do not add points together as a lifetime transaction total. These are aggregate usage charts, not accounting records or a list of identifiable servers.

## Existing website activity charts

These remain **Single Line** charts: `website_page_view`, `website_item_open`, `website_search`, and `website_watchlist_change`. Unlike the new rolling transaction charts, these website counters drain on collection and represent events since the previous collection attempt. Their existing visitor privacy controls remain in place.

## Controls and data

### Performance

Transaction tracking updates in-memory integer counters only. It performs no disk writes, HTTP calls, item construction, player scans, or scheduled task creation per trade. The enabled setting is cached at startup and refreshed by `/shopadmin reload`; disabled tracking returns immediately.

Storage is bounded to 30 minute buckets regardless of transaction volume. Reports sum at most 30 buckets per chart. GUI adoption checks read configuration and item registries without building custom item stacks. There is no new repeating collection task: these charts use bStats' existing reporting cycle (normally every 30 minutes after its randomized startup submissions). bStats collects Bukkit chart values on the server thread, then compresses and sends the report on its own background executor.

Validation includes one million simulated trades over 1,000 minute buckets, checking both counts and the 30-bucket storage bound, plus a regression test that rejects item construction during registry checks. The optional `MetricsPerformanceCheck` standalone probe measures the full `FeatureMetrics.record` path with dynamic pricing and inflation enabled. One local Java 25 run measured a median **12.44 ms per million calls** over five warmed rounds (12.27–14.71 ms). This is a synthetic tracking-only measurement, not a live-server TPS guarantee; economy processing, existing transaction logging, and report submission are outside that timing.

```yaml
metrics:
  feature-usage: true
```

Set this to `false` and run `/shopadmin reload` to stop the new adoption and transaction charts and discard pending transaction counts. It does not change basic bStats metrics or website metrics.

- `plugins/bStats/config.yml` → `enabled: false`, followed by a server restart, disables bStats and prevents the new counters from collecting.
- `webserver.usage-metrics: false` controls the existing website events and `website_design` chart separately.
- The new custom charts send only fixed category labels and aggregate numbers. They do not send player names, UUIDs, server addresses, item IDs, prices, transaction metadata, custom titles, or custom URLs. bStats' normal platform reporting remains governed by its own settings.

The website design chart, integration charts, and transaction-volume bands describe how many reporting servers use each option. They do not expose which named server owner chose a design.
