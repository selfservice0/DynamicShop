package org.minecraftsmp.dynamicshop.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.http.staticfiles.Location;
import io.javalin.json.JavalinJackson;
import org.bukkit.Material;
import org.bukkit.plugin.java.JavaPlugin;
import org.minecraftsmp.dynamicshop.DynamicShop;
import org.minecraftsmp.dynamicshop.category.ItemCategory;
import org.minecraftsmp.dynamicshop.managers.ShopDataManager;
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
        if (!plugin.getConfig().getBoolean("webserver.enabled", false))
            return;

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

            // Basic endpoints
            app.get("/", ctx -> ctx.redirect("/index.html"));
            app.get("/api/recent", this::handleRecent);
            app.get("/api/player/{name}", this::handlePlayer);
            app.get("/api/item/{item}", this::handleItem);
            app.get("/api/date/{date}", this::handleDate);
            app.get("/api/stats", this::handleStats);

            // NEW ANALYTICS ENDPOINTS
            app.get("/api/analytics/economy", this::handleEconomyHealth);
            app.get("/api/analytics/price-history/{item}", this::handlePriceHistory);
            app.get("/api/analytics/leaderboard", this::handleLeaderboard);
            app.get("/api/analytics/trends", this::handleTrends);
            app.get("/api/analytics/time-distribution", this::handleTimeDistribution);
            app.get("/api/analytics/items", this::handleItemList);

            // SHOP CATALOG ENDPOINTS
            app.get("/api/shop/items", this::handleShopItems);
            app.get("/api/shop/item/{item}", this::handleShopItemDetail);
            app.get("/api/shop/categories", this::handleShopCategories);

            plugin.getLogger().info("Web dashboard → http://" + host + ":" + port);
        } catch (Exception e) {
            plugin.getLogger().severe("╔═══════════════════════════════════════════════════╗");
            plugin.getLogger().severe("FAILED TO START WEB SERVER! Port locked?");
            plugin.getLogger().severe("The plugin will continue without the web dashboard.");
            plugin.getLogger().severe("");
            plugin.getLogger().severe("To disable this error, set in config.yml:");
            plugin.getLogger().severe("  webserver:");
            plugin.getLogger().severe("    enabled: false");
            plugin.getLogger().severe("");
            plugin.getLogger().severe("Error: " + e.getMessage());
            plugin.getLogger().severe("╚═══════════════════════════════════════════════════╝");
            app = null;
        }
    }

    public void stop() {
        if (app != null)
            app.stop();
    }

    private void extractWebFiles(File webDir) {
        String[] webFiles = { "index.html", "style.css", "dashboard.js", "items.html", "items.js" };
        boolean forceUpdate = plugin.getConfig().getBoolean("webserver.force-update-files", false);

        for (String fileName : webFiles) {
            File targetFile = new File(webDir, fileName);

            if (targetFile.exists() && !forceUpdate) {
                continue;
            }

            try (var input = plugin.getResource("web/" + fileName)) {
                if (input == null) {
                    plugin.getLogger().warning("Could not find web/" + fileName + " in JAR");
                    continue;
                }

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

    // ═══════════════════════════════════════════════════════════════
    // BASIC ENDPOINTS
    // ═══════════════════════════════════════════════════════════════

    private void handleRecent(Context ctx) {
        send(ctx, 100, null);
    }

    private void handlePlayer(Context ctx) {
        send(ctx, 200, t -> t.getPlayerName().equalsIgnoreCase(ctx.pathParam("name")));
    }

    private void handleItem(Context ctx) {
        send(ctx, 200, t -> t.getItem().equalsIgnoreCase(ctx.pathParam("item")));
    }

    private void handleDate(Context ctx) {
        send(ctx, 500, t -> t.getDate().equals(ctx.pathParam("date")));
    }

    private void send(Context ctx, int defLimit, Predicate<Transaction> filter) {
        int limit = parseLimit(ctx.queryParam("limit"), defLimit);
        List<TransactionDTO> safeList = plugin.getTransactionLogger().getRecentTransactions().stream()
                .filter(filter == null ? t -> true : filter)
                .sorted((a, b) -> b.getTimestampRaw().compareTo(a.getTimestampRaw()))
                .limit(limit)
                .map(TransactionDTO::new)
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

    // ═══════════════════════════════════════════════════════════════
    // NEW ANALYTICS ENDPOINTS
    // ═══════════════════════════════════════════════════════════════

    /**
     * GET /api/analytics/economy
     * Returns overall economy health metrics
     */
    private void handleEconomyHealth(Context ctx) {
        var txs = plugin.getTransactionLogger().getRecentTransactions();

        long buys = txs.stream().filter(t -> t.getType() == Transaction.TransactionType.BUY).count();
        long sells = txs.stream().filter(t -> t.getType() == Transaction.TransactionType.SELL).count();

        double totalBuyValue = txs.stream()
                .filter(t -> t.getType() == Transaction.TransactionType.BUY)
                .mapToDouble(Transaction::getPrice).sum();

        double totalSellValue = txs.stream()
                .filter(t -> t.getType() == Transaction.TransactionType.SELL)
                .mapToDouble(Transaction::getPrice).sum();

        double avgTransaction = txs.isEmpty() ? 0 : txs.stream().mapToDouble(Transaction::getPrice).average().orElse(0);

        // Calculate velocity (txs per hour)
        LocalDateTime oneHourAgo = LocalDateTime.now().minusHours(1);
        long recentTxs = txs.stream()
                .filter(t -> t.getTimestampRaw().isAfter(oneHourAgo))
                .count();

        // Get unique items and players
        long uniqueItems = txs.stream().map(Transaction::getItem).distinct().count();
        long uniquePlayers = txs.stream().map(Transaction::getPlayerName).distinct().count();

        Map<String, Object> health = new HashMap<>();
        health.put("totalTransactions", txs.size());
        health.put("buyCount", buys);
        health.put("sellCount", sells);
        health.put("buyRatio", txs.isEmpty() ? 0 : (double) buys / txs.size());
        health.put("totalBuyValue", totalBuyValue);
        health.put("totalSellValue", totalSellValue);
        health.put("netFlow", totalSellValue - totalBuyValue);
        health.put("avgTransaction", avgTransaction);
        health.put("velocity", recentTxs);
        health.put("uniqueItems", uniqueItems);
        health.put("uniquePlayers", uniquePlayers);

        ctx.json(health);
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
                .collect(Collectors
                        .groupingBy(t -> t.getTimestampRaw().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:00"))));

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

                    double changePercent = olderCount == 0 ? 100
                            : ((double) (recentCount - olderCount) / olderCount) * 100;

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
                        Collectors.counting()));

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

    // ═══════════════════════════════════════════════════════════════
    // SHOP CATALOG ENDPOINTS
    // ═══════════════════════════════════════════════════════════════

    /**
     * GET /api/shop/items
     * Returns all shop items with current prices, stock, and category
     */
    private void handleShopItems(Context ctx) {
        String query = ctx.queryParam("query");
        String categoryFilter = ctx.queryParam("category");

        List<ShopItemDTO> items = new ArrayList<>();

        for (Material mat : ShopDataManager.getAllTrackedMaterials()) {
            double basePrice = ShopDataManager.getBasePrice(mat);
            if (basePrice < 0)
                continue; // Skip disabled items

            // Apply search filter
            if (query != null && !query.isEmpty()) {
                if (!mat.name().toLowerCase().contains(query.toLowerCase())) {
                    continue;
                }
            }

            ItemCategory category = ShopDataManager.detectCategory(mat);

            // Apply category filter
            if (categoryFilter != null && !categoryFilter.isEmpty()) {
                try {
                    ItemCategory filterCat = ItemCategory.valueOf(categoryFilter.toUpperCase());
                    if (category != filterCat)
                        continue;
                } catch (IllegalArgumentException ignored) {
                }
            }

            double buyPrice = ShopDataManager.getPrice(mat);
            double sellPrice = ShopDataManager.getSellPrice(mat);
            double stock = ShopDataManager.getStock(mat);

            String imageUrl = "https://mc.nerothe.com/img/1.21/minecraft_" + mat.name().toLowerCase() + ".png";

            items.add(new ShopItemDTO(
                    mat.name(),
                    prettifyItemName(mat.name()),
                    category.name(),
                    buyPrice,
                    sellPrice,
                    stock,
                    basePrice,
                    imageUrl));
        }

        // Sort by display name
        items.sort(Comparator.comparing(ShopItemDTO::displayName));

        ctx.json(items);
    }

    /**
     * GET /api/shop/item/{item}
     * Returns detailed data for a specific item
     */
    private void handleShopItemDetail(Context ctx) {
        String itemName = ctx.pathParam("item");
        Material mat = Material.matchMaterial(itemName);

        if (mat == null) {
            ctx.status(404).json(Map.of("error", "Item not found"));
            return;
        }

        double basePrice = ShopDataManager.getBasePrice(mat);
        if (basePrice < 0) {
            ctx.status(404).json(Map.of("error", "Item not tradeable"));
            return;
        }

        ItemCategory category = ShopDataManager.detectCategory(mat);
        double buyPrice = ShopDataManager.getPrice(mat);
        double sellPrice = ShopDataManager.getSellPrice(mat);
        double stock = ShopDataManager.getStock(mat);
        String imageUrl = "https://mc.nerothe.com/img/1.21/minecraft_" + mat.name().toLowerCase() + ".png";

        // Get recent transactions for this item
        var txs = plugin.getTransactionLogger().getRecentTransactions();
        var itemTxs = txs.stream()
                .filter(t -> t.getItem().equalsIgnoreCase(mat.name()))
                .sorted((a, b) -> b.getTimestampRaw().compareTo(a.getTimestampRaw()))
                .limit(50)
                .map(TransactionDTO::new)
                .collect(Collectors.toList());

        // Get recent buyers and sellers
        var recentBuyers = txs.stream()
                .filter(t -> t.getItem().equalsIgnoreCase(mat.name()))
                .filter(t -> t.getType() == Transaction.TransactionType.BUY)
                .sorted((a, b) -> b.getTimestampRaw().compareTo(a.getTimestampRaw()))
                .limit(10)
                .map(t -> new RecentTrader(t.getPlayerName(), t.getTimestamp(), t.getAmount(), t.getPrice()))
                .collect(Collectors.toList());

        var recentSellers = txs.stream()
                .filter(t -> t.getItem().equalsIgnoreCase(mat.name()))
                .filter(t -> t.getType() == Transaction.TransactionType.SELL)
                .sorted((a, b) -> b.getTimestampRaw().compareTo(a.getTimestampRaw()))
                .limit(10)
                .map(t -> new RecentTrader(t.getPlayerName(), t.getTimestamp(), t.getAmount(), t.getPrice()))
                .collect(Collectors.toList());

        // Calculate stats
        long totalBuys = txs.stream()
                .filter(t -> t.getItem().equalsIgnoreCase(mat.name()))
                .filter(t -> t.getType() == Transaction.TransactionType.BUY)
                .count();

        long totalSells = txs.stream()
                .filter(t -> t.getItem().equalsIgnoreCase(mat.name()))
                .filter(t -> t.getType() == Transaction.TransactionType.SELL)
                .count();

        double totalVolume = txs.stream()
                .filter(t -> t.getItem().equalsIgnoreCase(mat.name()))
                .mapToDouble(Transaction::getPrice)
                .sum();

        Map<String, Object> result = new HashMap<>();
        result.put("item", mat.name());
        result.put("displayName", prettifyItemName(mat.name()));
        result.put("category", category.name());
        result.put("buyPrice", buyPrice);
        result.put("sellPrice", sellPrice);
        result.put("stock", stock);
        result.put("basePrice", basePrice);
        result.put("imageUrl", imageUrl);
        result.put("recentTransactions", itemTxs);
        result.put("recentBuyers", recentBuyers);
        result.put("recentSellers", recentSellers);
        result.put("totalBuys", totalBuys);
        result.put("totalSells", totalSells);
        result.put("totalVolume", totalVolume);

        ctx.json(result);
    }

    /**
     * GET /api/shop/categories
     * Returns list of categories with item counts
     */
    private void handleShopCategories(Context ctx) {
        List<CategoryDTO> categories = new ArrayList<>();

        for (ItemCategory cat : ItemCategory.values()) {
            if (cat == ItemCategory.PERMISSIONS || cat == ItemCategory.SERVER_SHOP) {
                continue;
            }

            long count = ShopDataManager.getAllTrackedMaterials().stream()
                    .filter(mat -> ShopDataManager.getBasePrice(mat) >= 0)
                    .filter(mat -> ShopDataManager.detectCategory(mat) == cat)
                    .count();

            if (count > 0) {
                categories.add(new CategoryDTO(cat.name(), prettifyItemName(cat.name()), count));
            }
        }

        ctx.json(categories);
    }

    private String prettifyItemName(String name) {
        String[] parts = name.split("_");
        StringBuilder result = new StringBuilder();
        for (String part : parts) {
            if (!part.isEmpty()) {
                result.append(Character.toUpperCase(part.charAt(0)))
                        .append(part.substring(1).toLowerCase())
                        .append(" ");
            }
        }
        return result.toString().trim();
    }

    // ═══════════════════════════════════════════════════════════════
    // UTILITY METHODS
    // ═══════════════════════════════════════════════════════════════

    private int parseLimit(String s, int def) {
        try {
            return s == null ? def : Math.min(Integer.parseInt(s), 1000);
        } catch (Exception e) {
            return def;
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // DTOs (Data Transfer Objects)
    // ═══════════════════════════════════════════════════════════════

    private record TransactionDTO(
            String timestamp,
            String playerName,
            String type,
            String item,
            int amount,
            double price,
            String category) {
        TransactionDTO(Transaction t) {
            this(
                    t.getTimestamp(),
                    t.getPlayerName(),
                    t.getType().name(),
                    t.getItem(),
                    t.getAmount(),
                    t.getPrice(),
                    t.getCategory() != null ? t.getCategory() : "");
        }
    }

    private record PricePoint(
            String timestamp,
            double avgBuyPrice,
            double avgSellPrice,
            int volume) {
    }

    private record LeaderboardEntry(
            String player,
            double spent,
            double earned,
            double netProfit,
            long trades,
            double volume,
            long uniqueItems) {
    }

    private record TrendItem(
            String item,
            long recentCount,
            double changePercent,
            double avgPrice) {
    }

    private record TimeSlot(
            int hour,
            long count) {
    }

    private record ItemMetadata(
            String item,
            long trades,
            double avgPrice,
            int totalVolume,
            String category) {
    }

    private record ShopItemDTO(
            String item,
            String displayName,
            String category,
            double buyPrice,
            double sellPrice,
            double stock,
            double basePrice,
            String imageUrl) {
    }

    private record RecentTrader(
            String playerName,
            String timestamp,
            int amount,
            double price) {
    }

    private record CategoryDTO(
            String id,
            String displayName,
            long itemCount) {
    }
}