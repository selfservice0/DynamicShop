package org.minecraftsmp.dynamicshop.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.http.staticfiles.Location;
import io.javalin.json.JavalinJackson;
import org.bukkit.plugin.java.JavaPlugin;
import org.minecraftsmp.dynamicshop.DynamicShop;
import org.minecraftsmp.dynamicshop.transactions.Transaction;

import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class WebServer {

    private final DynamicShop plugin;
    private Javalin app;

    public WebServer(DynamicShop plugin) {
        this.plugin = plugin;
    }

    public void start() {
        if (!plugin.getConfig().getBoolean("webserver.enabled", false)) return;

        try {
            int port = plugin.getConfig().getInt("webserver.port", 7713);
            String host = plugin.getConfig().getString("webserver.bind", "0.0.0.0");

            app = Javalin.create(config -> {
                File webDir = new File(plugin.getDataFolder(), "web");
                webDir.mkdirs();

                // Extract web files from JAR if they don't exist
                extractWebFiles(webDir);

                config.staticFiles.add(sf -> {
                    sf.directory = webDir.getAbsolutePath();
                    sf.location = Location.EXTERNAL;
                });

                config.jsonMapper(new JavalinJackson(createFixedMapper()));

                if (plugin.getConfig().getBoolean("webserver.cors.enabled", true)) {
                    config.plugins.enableCors(cors -> cors.add(rule -> rule.anyHost()));
                }
            }).start(host, port);

            app.get("/", ctx -> ctx.redirect("/index.html"));
            app.get("/api/recent",       this::handleRecent);
            app.get("/api/player/{name}",    this::handlePlayer);
            app.get("/api/item/{item}",  this::handleItem);
            app.get("/api/date/{date}",  this::handleDate);
            app.get("/api/stats",        this::handleStats);

            plugin.getLogger().info("Web dashboard → http://" + host + ":" + port);
        } catch (Exception e) {
            plugin.getLogger().severe("═══════════════════════════════════════════════════════");
            plugin.getLogger().severe("FAILED TO START WEB SERVER! Port locked?");
            plugin.getLogger().severe("The plugin will continue without the web dashboard.");
            plugin.getLogger().severe("");
            plugin.getLogger().severe("To disable this error, set in config.yml:");
            plugin.getLogger().severe("  webserver:");
            plugin.getLogger().severe("    enabled: false");
            plugin.getLogger().severe("");
            plugin.getLogger().severe("Error: " + e.getMessage());
            plugin.getLogger().severe("═══════════════════════════════════════════════════════");
            app = null; // Make sure it's null so stop() doesn't crash
        }
    }

    public void stop() {
        if (app != null) app.stop();
    }

    /**
     * Extract web dashboard files from JAR to the web directory.
     * Only extracts if files don't exist or if force-update is enabled.
     */
    private void extractWebFiles(File webDir) {
        String[] webFiles = {"index.html", "style.css", "dashboard.js"};
        boolean forceUpdate = plugin.getConfig().getBoolean("webserver.force-update-files", false);

        for (String fileName : webFiles) {
            File targetFile = new File(webDir, fileName);

            // Skip if file exists and not forcing update
            if (targetFile.exists() && !forceUpdate) {
                continue;
            }

            try (var input = plugin.getResource("web/" + fileName)) {
                if (input == null) {
                    plugin.getLogger().warning("Could not find web/" + fileName + " in JAR");
                    continue;
                }

                // Copy file from JAR to disk
                java.nio.file.Files.copy(input, targetFile.toPath(),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);

                plugin.getLogger().info("Extracted web/" + fileName);
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to extract web/" + fileName + ": " + e.getMessage());
            }
        }
    }

    private ObjectMapper createFixedMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
        mapper.configOverride(DynamicShop.class).setIsIgnoredType(true);
        mapper.configOverride(JavaPlugin.class).setIsIgnoredType(true);
        return mapper;
    }

    // All handlers use safe list
    private void handleRecent(Context ctx)   { send(ctx, 100, null); }
    private void handlePlayer(Context ctx) { send(ctx, 200, t -> t.getPlayerName().equalsIgnoreCase(ctx.pathParam("name"))); }
    private void handleItem(Context ctx)     { send(ctx, 200, t -> t.getItem().equalsIgnoreCase(ctx.pathParam("item"))); }
    private void handleDate(Context ctx)     { send(ctx, 500, t -> t.getDate().equals(ctx.pathParam("date"))); }

    private void send(Context ctx, int defLimit, Predicate<Transaction> filter) {
        int limit = parseLimit(ctx.queryParam("limit"), defLimit);
        List<TransactionDTO> safeList = plugin.getTransactionLogger().getRecentTransactions().stream()
                .filter(filter == null ? t -> true : filter)
                .sorted((a, b) -> b.getTimestampRaw().compareTo(a.getTimestampRaw()))
                .limit(limit)
                .map(t -> new TransactionDTO(t))  // THIS LINE NOW COMPILES
                .collect(Collectors.toList());
        ctx.json(safeList);
    }

    private void handleStats(Context ctx) {
        var txs = plugin.getTransactionLogger().getRecentTransactions();
        long buys = txs.stream().filter(t -> t.getType() == Transaction.TransactionType.BUY).count();
        long sells = txs.stream().filter(t -> t.getType() == Transaction.TransactionType.SELL).count();
        double money = txs.stream().mapToDouble(Transaction::getPrice).sum();
        Map<String, Object> stats = new HashMap<>();
        stats.put("total", txs.size());
        stats.put("buys", buys);
        stats.put("sells", sells);
        stats.put("totalMoney", money);

        ctx.json(stats);
    }

    /**
     * GET /api/analytics/price-history/{item}?hours=24
     * Returns price history for a specific item
     */
    private void handlePriceHistory(Context ctx) {
        String item = ctx.pathParam("item");
        int hours = parseLimit(ctx.queryParam("hours"), 24);

        LocalDateTime cutoff = LocalDateTime.now().minusHours(hours);
        var txs = plugin.getTransactionLogger().getRecentTransactions().stream()
                .filter(t -> t.getItem().equalsIgnoreCase(item))
                .filter(t -> t.getTimestampRaw().isAfter(cutoff))
                .sorted(Comparator.comparing(Transaction::getTimestampRaw))
                .collect(Collectors.toList());

        // Group by hour
        Map<String, List<Transaction>> grouped = txs.stream()
                .collect(Collectors.groupingBy(t ->
                        t.getTimestampRaw().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:00"))
                ));

        List<PricePoint> history = grouped.entrySet().stream()
                .map(e -> {
                    var hourTxs = e.getValue();
                    double avgBuy = hourTxs.stream()
                            .filter(t -> t.getType() == Transaction.TransactionType.BUY)
                            .mapToDouble(t -> t.getPrice() / t.getAmount())
                            .average().orElse(0);

                    double avgSell = hourTxs.stream()
                            .filter(t -> t.getType() == Transaction.TransactionType.SELL)
                            .mapToDouble(t -> t.getPrice() / t.getAmount())
                            .average().orElse(0);

                    int volume = hourTxs.stream().mapToInt(Transaction::getAmount).sum();

                    return new PricePoint(e.getKey(), avgBuy, avgSell, volume);
                })
                .sorted(Comparator.comparing(PricePoint::timestamp))
                .collect(Collectors.toList());

        ctx.json(history);
    }

    /**
     * GET /api/analytics/leaderboard?type=earners&limit=10
     * Returns player leaderboards
     * Types: earners, spenders, traders, volume
     */
    private void handleLeaderboard(Context ctx) {
        String type = ctx.queryParam("type") != null ? ctx.queryParam("type") : "earners";
        int limit = parseLimit(ctx.queryParam("limit"), 10);

        var txs = plugin.getTransactionLogger().getRecentTransactions();

        // Group by player
        Map<String, List<Transaction>> byPlayer = txs.stream()
                .collect(Collectors.groupingBy(Transaction::getPlayerName));

        List<LeaderboardEntry> entries = byPlayer.entrySet().stream()
                .map(e -> {
                    String player = e.getKey();
                    var playerTxs = e.getValue();

                    double spent = playerTxs.stream()
                            .filter(t -> t.getType() == Transaction.TransactionType.BUY)
                            .mapToDouble(Transaction::getPrice).sum();

                    double earned = playerTxs.stream()
                            .filter(t -> t.getType() == Transaction.TransactionType.SELL)
                            .mapToDouble(Transaction::getPrice).sum();

                    long trades = playerTxs.size();
                    double volume = spent + earned;
                    long uniqueItems = playerTxs.stream().map(Transaction::getItem).distinct().count();

                    return new LeaderboardEntry(player, spent, earned, earned - spent, trades, volume, uniqueItems);
                })
                .sorted((a, b) -> switch (type) {
                    case "spenders" -> Double.compare(b.spent, a.spent);
                    case "traders" -> Long.compare(b.trades, a.trades);
                    case "volume" -> Double.compare(b.volume, a.volume);
                    default -> Double.compare(b.netProfit, a.netProfit); // earners
                })
                .limit(limit)
                .collect(Collectors.toList());

        ctx.json(entries);
    }

    /**
     * GET /api/analytics/trends?limit=10
     * Returns trending items (hot, rising, falling)
     */
    private void handleTrends(Context ctx) {
        int limit = parseLimit(ctx.queryParam("limit"), 10);
        var txs = plugin.getTransactionLogger().getRecentTransactions();

        LocalDateTime oneDayAgo = LocalDateTime.now().minusDays(1);
        LocalDateTime oneHourAgo = LocalDateTime.now().minusHours(1);

        // Get recent vs older transactions
        var recentTxs = txs.stream()
                .filter(t -> t.getTimestampRaw().isAfter(oneHourAgo))
                .collect(Collectors.toList());

        var olderTxs = txs.stream()
                .filter(t -> t.getTimestampRaw().isBefore(oneHourAgo) && t.getTimestampRaw().isAfter(oneDayAgo))
                .collect(Collectors.toList());

        // Count transactions per item
        Map<String, Long> recentCounts = recentTxs.stream()
                .collect(Collectors.groupingBy(Transaction::getItem, Collectors.counting()));

        Map<String, Long> olderCounts = olderTxs.stream()
                .collect(Collectors.groupingBy(Transaction::getItem, Collectors.counting()));

        // Calculate trends
        List<TrendItem> trends = recentCounts.entrySet().stream()
                .map(e -> {
                    String item = e.getKey();
                    long recentCount = e.getValue();
                    long olderCount = olderCounts.getOrDefault(item, 0L);

                    double changePercent = olderCount == 0 ? 100 :
                            ((double) (recentCount - olderCount) / olderCount) * 100;

                    double avgPrice = recentTxs.stream()
                            .filter(t -> t.getItem().equals(item))
                            .mapToDouble(t -> t.getPrice() / t.getAmount())
                            .average().orElse(0);

                    return new TrendItem(item, recentCount, changePercent, avgPrice);
                })
                .sorted(Comparator.comparingDouble(TrendItem::changePercent).reversed())
                .limit(limit)
                .collect(Collectors.toList());

        Map<String, Object> result = new HashMap<>();
        result.put("hot", trends.stream().filter(t -> t.recentCount > 5).limit(5).collect(Collectors.toList()));
        result.put("rising", trends.stream().filter(t -> t.changePercent > 20).limit(5).collect(Collectors.toList()));
        result.put("falling", trends.stream()
                .sorted(Comparator.comparingDouble(TrendItem::changePercent))
                .limit(5).collect(Collectors.toList()));

        ctx.json(result);
    }

    /**
     * GET /api/analytics/time-distribution?hours=24
     * Returns transaction distribution over time (for activity heatmap)
     */
    private void handleTimeDistribution(Context ctx) {
        int hours = parseLimit(ctx.queryParam("hours"), 24);
        LocalDateTime cutoff = LocalDateTime.now().minusHours(hours);

        var txs = plugin.getTransactionLogger().getRecentTransactions().stream()
                .filter(t -> t.getTimestampRaw().isAfter(cutoff))
                .collect(Collectors.toList());

        // Group by hour
        Map<Integer, Long> hourlyDist = txs.stream()
                .collect(Collectors.groupingBy(
                        t -> t.getTimestampRaw().getHour(),
                        Collectors.counting()
                ));

        // Fill in missing hours with 0
        List<TimeSlot> distribution = new ArrayList<>();
        for (int h = 0; h < 24; h++) {
            distribution.add(new TimeSlot(h, hourlyDist.getOrDefault(h, 0L)));
        }

        ctx.json(distribution);
    }

    /**
     * GET /api/analytics/items?query=stone
     * Returns searchable list of items with metadata
     */
    private void handleItemList(Context ctx) {
        String query = ctx.queryParam("query").toLowerCase();
        var txs = plugin.getTransactionLogger().getRecentTransactions();

        Map<String, List<Transaction>> byItem = txs.stream()
                .collect(Collectors.groupingBy(Transaction::getItem));

        List<ItemMetadata> items = byItem.entrySet().stream()
                .filter(e -> query.isEmpty() || e.getKey().toLowerCase().contains(query))
                .map(e -> {
                    String item = e.getKey();
                    var itemTxs = e.getValue();

                    long trades = itemTxs.size();
                    double avgPrice = itemTxs.stream()
                            .mapToDouble(t -> t.getPrice() / t.getAmount())
                            .average().orElse(0);

                    int totalVolume = itemTxs.stream().mapToInt(Transaction::getAmount).sum();

                    String category = itemTxs.get(0).getCategory();

                    return new ItemMetadata(item, trades, avgPrice, totalVolume, category);
                })
                .sorted(Comparator.comparingLong(ItemMetadata::trades).reversed())
                .limit(50)
                .collect(Collectors.toList());

        ctx.json(items);
    }

    //Utility
    private int parseLimit(String s, int def) {
        try { return s == null ? def : Math.min(Integer.parseInt(s), 1000); }
        catch (Exception e) { return def; }
    }

    // SAFE DTO
    private record TransactionDTO(
            String timestamp,
            String playerName,
            String type,
            String item,
            int amount,
            double price,
            String category
    ) {
        TransactionDTO(Transaction t) {
            this(
                    t.getTimestamp(),
                    t.getPlayerName(),
                    t.getType().name(),
                    t.getItem(),
                    t.getAmount(),
                    t.getPrice(),
                    t.getCategory() != null ? t.getCategory() : ""
            );
        }
    }

    private record PricePoint(
            String timestamp,
            double avgBuyPrice,
            double avgSellPrice,
            int volume
    ) {}

    private record LeaderboardEntry(
            String player,
            double spent,
            double earned,
            double netProfit,
            long trades,
            double volume,
            long uniqueItems
    ) {}

    private record TrendItem(
            String item,
            long recentCount,
            double changePercent,
            double avgPrice
    ) {}

    private record TimeSlot(
            int hour,
            long count
    ) {}

    private record ItemMetadata(
            String item,
            long trades,
            double avgPrice,
            int totalVolume,
            String category
    ) {}
}