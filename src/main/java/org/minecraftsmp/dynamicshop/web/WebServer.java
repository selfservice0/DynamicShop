package org.minecraftsmp.dynamicshop.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.http.staticfiles.Location;
import io.javalin.json.JavalinJackson;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.plugin.java.JavaPlugin;
import org.minecraftsmp.dynamicshop.DynamicShop;
import org.minecraftsmp.dynamicshop.category.ItemCategory;
import org.minecraftsmp.dynamicshop.managers.ConfigCacheManager;
import org.minecraftsmp.dynamicshop.managers.CategoryConfigManager;
import org.minecraftsmp.dynamicshop.managers.ShopDataManager;
import org.minecraftsmp.dynamicshop.transactions.Transaction;
import org.minecraftsmp.dynamicshop.models.PlayerShopListing;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class WebServer {

    private final DynamicShop plugin;
    private Javalin app;
    private final WebAdminTokenManager tokenManager = new WebAdminTokenManager();
    private WebAdminUserManager userManager;
    private WebAdminAuditLog auditLog;

    // Cache for /api/shop/items endpoint (60 second TTL)
    private static final long CACHE_TTL_MS = 60_000; // 60 seconds
    private List<ShopItemDTO> cachedShopItems = null;
    private long cacheTimestamp = 0;

    public WebServer(DynamicShop plugin) {
        this.plugin = plugin;
        this.userManager = new WebAdminUserManager(plugin);
        this.auditLog = new WebAdminAuditLog(plugin);
    }

    public void start() {
        if (!plugin.getConfig().getBoolean("webserver.enabled", false))
            return;

        try {
            int port = plugin.getConfig().getInt("webserver.port", 7713);
            String host = plugin.getConfig().getString("webserver.bind", "127.0.0.1");

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

                if (plugin.getConfig().getBoolean("webserver.cors.enabled", false)) {
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
            // AUTH + ADMIN ENDPOINTS (only if admin panel is enabled)
            if (plugin.getConfig().getBoolean("webserver.admin-enabled", true)) {
                // AUTH ENDPOINTS (no auth required)
                app.post("/api/auth/register", this::handleRegister);
                app.post("/api/auth/login", this::handleLogin);
                app.get("/api/auth/verify", this::handleVerify);

                // ADMIN API ENDPOINTS (token or session auth required)
                app.before("/api/admin/*", ctx -> {
                    // Check one-time token first
                    String token = ctx.queryParam("token");
                    if (token == null) token = ctx.header("X-Admin-Token");
                    if (tokenManager.isValid(token)) return; // one-time token valid

                    // Check session token
                    String session = ctx.queryParam("session");
                    if (session == null) session = ctx.header("X-Session-Token");
                    if (userManager.isValidSession(session)) return; // session valid

                    ctx.status(401).json(Map.of("error", "Unauthorized — invalid or expired token"));
                });
                app.get("/api/admin/items", this::handleAdminItems);
                app.get("/api/admin/item/{item}", this::handleAdminItemDetail);
                app.post("/api/admin/item/{item}", this::handleAdminItemUpdate);
                app.post("/api/admin/items/bulk", this::handleAdminItemsBulkUpdate);
                app.get("/api/admin/config", this::handleAdminConfigGet);
                app.post("/api/admin/config", this::handleAdminConfigUpdate);
                app.post("/api/admin/resetshortage", this::handleAdminResetShortage);
                app.post("/api/admin/resetshortage/{item}", this::handleAdminResetShortageItem);
                app.get("/api/admin/categories", this::handleAdminCategories);
                app.post("/api/admin/category/{category}", this::handleAdminCategoryUpdate);
                app.get("/api/admin/audit", this::handleAdminAudit);
                app.post("/api/admin/items/create", this::handleAdminItemCreate);
                app.delete("/api/admin/item/{item}", this::handleAdminItemRemove);
                app.get("/api/admin/special-items", this::handleAdminSpecialItems);
                app.get("/api/admin/special-items/{id}", this::handleAdminSpecialItemGet);
                app.post("/api/admin/special-items", this::handleAdminSpecialItemCreate);
                app.post("/api/admin/special-items/{id}", this::handleAdminSpecialItemUpdate);
                app.delete("/api/admin/special-items/{id}", this::handleAdminSpecialItemDelete);
                app.delete("/api/admin/playershop/{id}", this::handleAdminPlayerShopDelete);
                app.post("/api/admin/reload", this::handleAdminReload);
                plugin.getLogger().info("Web admin panel enabled.");
            } else {
                // Admin disabled — serve a simple message if someone hits admin.html
                app.get("/api/auth/verify", ctx -> ctx.status(403).json(Map.of("valid", false, "disabled", true)));
                plugin.getLogger().info("Web admin panel DISABLED via config.");
            }

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
    private String getAdminUsername(io.javalin.http.Context ctx) {
        String token = ctx.queryParam("token");
        if (token == null) token = ctx.header("X-Admin-Token");
        if (tokenManager.isValid(token)) {
            String name = tokenManager.getPlayerName(token);
            return name != null ? name : "unknown";
        }

        String session = ctx.queryParam("session");
        if (session == null) session = ctx.header("X-Session-Token");
        if (userManager.isValidSession(session)) {
            String name = userManager.getUsername(session);
            return name != null ? name : "unknown";
        }

        return "unknown";
    }

    public void stop() {
        if (app != null)
            app.stop();
    }

    private boolean runSyncAdminTask(Context ctx, Runnable task) {
        Boolean result = callSyncAdminTask(ctx, () -> {
            task.run();
            return true;
        });
        return Boolean.TRUE.equals(result);
    }

    private <T> T callSyncAdminTask(Context ctx, Callable<T> task) {
        try {
            if (Bukkit.isPrimaryThread()) {
                return task.call();
            }
            return Bukkit.getScheduler().callSyncMethod(plugin, task).get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            plugin.getLogger().warning("[WebAdmin] Failed to apply admin request: " + e.getMessage());
            ctx.status(500).json(Map.of("error", "Failed to apply admin request"));
            return null;
        }
    }

    private void extractWebFiles(File webDir) {
        String[] webFiles = { "index.html", "style.css", "dashboard.js", "items.html", "items.js", "admin.html" };
        boolean forceUpdate = plugin.getConfig().getBoolean("webserver.force-update-files", false);

        for (String fileName : webFiles) {
            File targetFile = new File(webDir, fileName);

            if (!forceUpdate && targetFile.exists()) {
                // Version-aware check: read marker from JAR and disk
                String jarVersion  = readWebFileVersion(plugin.getResource("web/" + fileName));
                String diskVersion = readDiskFileVersion(targetFile);

                if (jarVersion != null && jarVersion.equals(diskVersion)) {
                    continue; // Up to date — skip
                }

                if (jarVersion != null) {
                    plugin.getLogger().info("Updating web/" + fileName
                            + " (disk: " + diskVersion + " \u2192 jar: " + jarVersion + ")");
                }
                // If JAR has no marker, fall through and overwrite anyway
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

    /** Read the DS-WEB-VERSION marker from a JAR resource stream. */
    private String readWebFileVersion(java.io.InputStream in) {
        if (in == null) return null;
        try (var reader = new java.io.BufferedReader(new java.io.InputStreamReader(in))) {
            return parseVersionMarker(reader.readLine());
        } catch (Exception e) { return null; }
    }

    /** Read the DS-WEB-VERSION marker from an on-disk file. */
    private String readDiskFileVersion(File file) {
        try (var reader = new java.io.BufferedReader(new java.io.FileReader(file))) {
            return parseVersionMarker(reader.readLine());
        } catch (Exception e) { return null; }
    }

    /** Extracts version from markers in three comment styles:
     *  HTML: {@code <!-- DS-WEB-VERSION: 2.5.3 -->}
     *  CSS:  {@code /* DS-WEB-VERSION: 2.5.3 * /}
     *  JS:   {@code // DS-WEB-VERSION: 2.5.3}
     */
    private String parseVersionMarker(String line) {
        if (line == null) return null;
        String t = line.trim();
        final String KEY = "DS-WEB-VERSION:";
        if (!t.contains(KEY)) return null;
        int colon = t.indexOf(KEY) + KEY.length();
        // Strip trailing comment closers
        String raw = t.substring(colon);
        raw = raw.replace("-->", "").replace("*/", "").trim();
        return raw.isEmpty() ? null : raw;
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
                    default -> Double.compare(b.earned, a.earned); // earners
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
     * Uses 60-second cache to reduce server load
     */
    private void handleShopItems(Context ctx) {
        String query = ctx.queryParam("query");
        String categoryFilter = ctx.queryParam("category");

        // Check if cache needs refresh
        long now = System.currentTimeMillis();
        if (cachedShopItems == null || (now - cacheTimestamp) > CACHE_TTL_MS) {
            refreshShopItemsCache();
        }

        // Apply filters to cached data
        List<ShopItemDTO> items = cachedShopItems;

        // Apply search filter if present
        if (query != null && !query.isEmpty()) {
            String lowerQuery = query.toLowerCase();
            items = items.stream()
                    .filter(item -> item.displayName().toLowerCase().contains(lowerQuery)
                            || item.item().toLowerCase().contains(lowerQuery))
                    .collect(Collectors.toList());
        }

        // Apply category filter if present
        if (categoryFilter != null && !categoryFilter.isEmpty()) {
            String upperCategory = categoryFilter.toUpperCase();
            items = items.stream()
                    .filter(item -> item.category().equals(upperCategory))
                    .collect(Collectors.toList());
        }

        ctx.json(items);
    }

    public synchronized void invalidateShopItemsCache() {
        this.cacheTimestamp = 0;
    }

    /**
     * Refreshes the shop items cache
     */
    private synchronized void refreshShopItemsCache() {
        List<ShopItemDTO> items = new ArrayList<>();

        for (Material mat : ShopDataManager.getAllTrackedMaterials()) {
            double basePrice = ShopDataManager.getBasePrice(mat);
            if (basePrice < 0)
                continue; // Skip disabled items

            ItemCategory category = ShopDataManager.detectCategory(mat);
            double buyPrice = ShopDataManager.getPrice(mat);
            double sellPrice = ShopDataManager.getSellPrice(mat);
            double stock = ShopDataManager.getStock(mat);

            String imageUrl = "https://mc.nerothe.com/img/1.21/minecraft_" + mat.name().toLowerCase() + ".png";

            items.add(new ShopItemDTO(
                    mat.name(),
                    ShopDataManager.getCustomName(mat) != null ? ShopDataManager.getCustomName(mat) : prettifyItemName(mat.name()),
                    category.name(),
                    buyPrice,
                    sellPrice,
                    stock,
                    basePrice,
                    imageUrl));
        }

        // Add Special Items
        for (org.minecraftsmp.dynamicshop.category.SpecialShopItem specialItem : plugin.getSpecialShopManager().getAllSpecialItems().values()) {
            String imageUrl = "https://mc.nerothe.com/img/1.21/minecraft_" + 
                (specialItem.getDisplayMaterial() != null ? specialItem.getDisplayMaterial().name().toLowerCase() : "enchanted_book") + ".png";
            
            items.add(new ShopItemDTO(
                    "special:" + specialItem.getId(),
                    specialItem.getName() != null ? specialItem.getName() : specialItem.getId(),
                    specialItem.getCategory().name(),
                    specialItem.getPrice(),
                    0.0,
                    0,
                    specialItem.getPrice(),
                    imageUrl
            ));
        }

        // Add Player Shop Items
        for (PlayerShopListing ps : plugin.getPlayerShopManager().getAllListings()) {
            String psName = ps.getItem().hasItemMeta() && ps.getItem().getItemMeta().hasDisplayName() ? 
                PlainTextComponentSerializer.plainText().serialize(ps.getItem().getItemMeta().displayName()) : 
                prettifyItemName(ps.getItem().getType().name());
            
            String imageUrl = "https://mc.nerothe.com/img/1.21/minecraft_" + ps.getItem().getType().name().toLowerCase() + ".png";

            items.add(new ShopItemDTO(
                    "playershop:" + ps.getListingId(),
                    psName,
                    "PLAYER_SHOPS",
                    ps.getPrice(),
                    0.0,
                    ps.getItem().getAmount(),
                    ps.getPrice(),
                    imageUrl
            ));
        }

        // Sort by display name
        items.sort(Comparator.comparing(ShopItemDTO::displayName));

        // Update cache
        cachedShopItems = items;
        cacheTimestamp = System.currentTimeMillis();

        plugin.getLogger().info("Shop items cache refreshed (" + items.size() + " items)");
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
        String customName = ShopDataManager.getCustomName(mat);
        result.put("displayName", customName != null ? customName : prettifyItemName(mat.name()));
        result.put("customName", customName);
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
    // AUTH ENDPOINTS
    // ═══════════════════════════════════════════════════════════════

    /**
     * POST /api/auth/register
     * Register a new admin user. Requires a valid one-time token.
     * Body: { "token": "...", "username": "...", "password": "..." }
     */
    private void handleRegister(Context ctx) {
        Map<String, Object> body;
        try {
            body = ctx.bodyAsClass(Map.class);
        } catch (Exception e) {
            ctx.status(400).json(Map.of("error", "Invalid JSON"));
            return;
        }

        String token = (String) body.get("token");
        String password = (String) body.get("password");

        if (token == null || password == null) {
            ctx.status(400).json(Map.of("error", "Missing required fields: token, password"));
            return;
        }

        if (!tokenManager.isValid(token)) {
            ctx.status(401).json(Map.of("error", "Invalid or expired registration token"));
            return;
        }

        // Username comes from the token (player's in-game name)
        String username = tokenManager.getPlayerName(token);
        if (username == null) {
            ctx.status(400).json(Map.of("error", "Token has no associated player name"));
            return;
        }

        if (password.length() < 8) {
            ctx.status(400).json(Map.of("error", "Password must be at least 8 characters"));
            return;
        }

        if (!userManager.register(username, password)) {
            ctx.status(409).json(Map.of("error", "Account already exists for " + username));
            return;
        }

        // Consume the one-time token after successful registration
        tokenManager.revoke(token);

        // Auto-login after registration
        String session = userManager.login(username, password);

        auditLog.log(username, "account_created", username, "Admin account registered via web");

        ctx.json(Map.of("success", true, "session", session, "username", username,
                "message", "Account created for " + username + "!"));
    }

    /**
     * POST /api/auth/login
     * Login with username/password.
     * Body: { "username": "...", "password": "..." }
     */
    private void handleLogin(Context ctx) {
        Map<String, Object> body;
        try {
            body = ctx.bodyAsClass(Map.class);
        } catch (Exception e) {
            ctx.status(400).json(Map.of("error", "Invalid JSON"));
            return;
        }

        String username = (String) body.get("username");
        String password = (String) body.get("password");

        if (username == null || password == null) {
            ctx.status(400).json(Map.of("error", "Missing username or password"));
            return;
        }

        String session = userManager.login(username, password);
        if (session == null) {
            ctx.status(401).json(Map.of("error", "Invalid username or password"));
            return;
        }

        ctx.json(Map.of("success", true, "session", session));
    }

    /**
     * GET /api/auth/verify?token=...&session=...
     * Verify that a token or session is valid. Returns auth type.
     */
    private void handleVerify(Context ctx) {
        String token = ctx.queryParam("token");
        String session = ctx.queryParam("session");

        if (token != null && tokenManager.isValid(token)) {
            String playerName = tokenManager.getPlayerName(token);
            boolean alreadyRegistered = playerName != null && userManager.userExists(playerName);
            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("valid", true);
            resp.put("type", "token");
            resp.put("playerName", playerName);
            resp.put("alreadyRegistered", alreadyRegistered);
            ctx.json(resp);
            return;
        }

        if (session != null && userManager.isValidSession(session)) {
            ctx.json(Map.of("valid", true, "type", "session"));
            return;
        }

        ctx.status(401).json(Map.of("valid", false));
    }

    // ═══════════════════════════════════════════════════════════════
    // ADMIN API ENDPOINTS (token-protected)
    // ═══════════════════════════════════════════════════════════════

    public WebAdminTokenManager getTokenManager() {
        return tokenManager;
    }

    public WebAdminUserManager getUserManager() {
        return userManager;
    }

    /**
     * GET /api/admin/items
     * Returns all items with full admin data (stock, base price, shortage, rates, etc.)
     */
    private void handleAdminItems(Context ctx) {
        if (ctx.statusCode() == 401) return;

        List<Map<String, Object>> items = new ArrayList<>();
        for (Material mat : ShopDataManager.getAllTrackedMaterials()) {
            double basePrice = ShopDataManager.getBasePrice(mat);
            if (basePrice < 0) continue;

            Map<String, Object> item = buildAdminItemMap(mat);
            items.add(item);
        }
        
        // Add Special Items
        for (org.minecraftsmp.dynamicshop.category.SpecialShopItem specialItem : plugin.getSpecialShopManager().getAllSpecialItems().values()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("item", "special:" + specialItem.getId());
            item.put("displayName", specialItem.getName() != null ? specialItem.getName() : specialItem.getId());
            item.put("category", specialItem.getCategory().name());
            item.put("basePrice", specialItem.getPrice());
            item.put("buyPrice", specialItem.getPrice());
            item.put("sellPrice", 0.0);
            item.put("stock", 0.0);
            item.put("stockRate", 0.0);
            item.put("maxStock", null);
            item.put("maxStockStorage", null);
            item.put("shortageHours", 0.0);
            item.put("priceIncreasePercent", 0.0);
            item.put("buyDisabled", false);
            item.put("sellDisabled", false);
            item.put("disabled", false);
            String imageUrl = "https://mc.nerothe.com/img/1.21/minecraft_" + 
                (specialItem.getDisplayMaterial() != null ? specialItem.getDisplayMaterial().name().toLowerCase() : "enchanted_book") + ".png";
            item.put("imageUrl", imageUrl);
            items.add(item);
        }

        // Add Player Shop Items
        for (PlayerShopListing ps : plugin.getPlayerShopManager().getAllListings()) {
            Map<String, Object> item = new LinkedHashMap<>();
            String psName = ps.getItem().hasItemMeta() && ps.getItem().getItemMeta().hasDisplayName() ? 
                PlainTextComponentSerializer.plainText().serialize(ps.getItem().getItemMeta().displayName()) : 
                prettifyItemName(ps.getItem().getType().name());

            item.put("item", "playershop:" + ps.getListingId());
            item.put("displayName", psName);
            item.put("category", "PLAYER_SHOPS");
            item.put("basePrice", ps.getPrice());
            item.put("buyPrice", ps.getPrice());
            item.put("sellPrice", 0.0);
            item.put("stock", (double) ps.getItem().getAmount());
            item.put("stockRate", 0.0);
            item.put("maxStock", null);
            item.put("maxStockStorage", null);
            item.put("shortageHours", 0.0);
            item.put("priceIncreasePercent", 0.0);
            item.put("buyDisabled", false);
            item.put("sellDisabled", false);
            item.put("disabled", false);
            String imageUrl = "https://mc.nerothe.com/img/1.21/minecraft_" + ps.getItem().getType().name().toLowerCase() + ".png";
            item.put("imageUrl", imageUrl);
            items.add(item);
        }

        items.sort(Comparator.comparing(m -> (String) m.get("displayName")));
        ctx.json(items);
    }

    /**
     * GET /api/admin/item/{item}
     * Returns detailed admin data for a single item
     */
    private void handleAdminItemDetail(Context ctx) {
        if (ctx.statusCode() == 401) return;

        Material mat = Material.matchMaterial(ctx.pathParam("item"));
        if (mat == null || ShopDataManager.getBasePrice(mat) < 0) {
            ctx.status(404).json(Map.of("error", "Item not found"));
            return;
        }
        ctx.json(buildAdminItemMap(mat));
    }

    /**
     * POST /api/admin/item/{item}
     * Update item properties. Accepts JSON body with optional fields:
     *   basePrice, stock, stockRate, shortageHours, buyDisabled, sellDisabled, disabled, category
     */
    private void handleAdminItemUpdate(Context ctx) {
        if (ctx.statusCode() == 401) return;

        Material mat = Material.matchMaterial(ctx.pathParam("item"));
        if (mat == null || ShopDataManager.getBasePrice(mat) < 0) {
            ctx.status(404).json(Map.of("error", "Item not found"));
            return;
        }

        // Parse JSON body
        Map<String, Object> body;
        try {
            body = ctx.bodyAsClass(Map.class);
        } catch (Exception e) {
            ctx.status(400).json(Map.of("error", "Invalid JSON body"));
            return;
        }

        // Apply changes on main thread for thread safety
        if (!runSyncAdminTask(ctx, () -> {
            boolean isDisabled = false;
            if (body.containsKey("disabled")) {
                isDisabled = (Boolean) body.get("disabled");
                ShopDataManager.setItemDisabled(mat, isDisabled);
            }

            if (!isDisabled && body.containsKey("basePrice")) {
                double price = ((Number) body.get("basePrice")).doubleValue();
                ShopDataManager.setBasePrice(mat, price);
            }
            if (body.containsKey("stock")) {
                double stock = ((Number) body.get("stock")).doubleValue();
                ShopDataManager.setStockDirect(mat, stock);
            }
            if (body.containsKey("stockRate")) {
                double rate = ((Number) body.get("stockRate")).doubleValue();
                ShopDataManager.setStockRate(mat, rate);
            }
            if (body.containsKey("shortageHours")) {
                double hours = ((Number) body.get("shortageHours")).doubleValue();
                ShopDataManager.setHoursInShortage(mat, hours);
                ShopDataManager.setLastUpdate(mat, System.currentTimeMillis());
            }
            if (body.containsKey("buyDisabled")) {
                ShopDataManager.setBuyDisabled(mat, (Boolean) body.get("buyDisabled"));
            }
            if (body.containsKey("sellDisabled")) {
                ShopDataManager.setSellDisabled(mat, (Boolean) body.get("sellDisabled"));
            }
            if (body.containsKey("maxStock")) {
                Object maxStockObj = body.get("maxStock");
                Double maxStock = maxStockObj == null ? null : ((Number) maxStockObj).doubleValue();
                ShopDataManager.setMaxStock(mat, maxStock);
            }
            if (body.containsKey("maxStockStorage")) {
                Object maxStorageObj = body.get("maxStockStorage");
                Integer maxStorage = maxStorageObj == null ? null : ((Number) maxStorageObj).intValue();
                ShopDataManager.setMaxStockStorage(mat, maxStorage);
            }
            if (body.containsKey("category")) {
                try {
                    ItemCategory cat = ItemCategory.valueOf(((String) body.get("category")).toUpperCase());
                    ShopDataManager.setCategoryOverride(mat, cat);
                } catch (Exception ignored) {}
            }
            if (body.containsKey("displayName")) {
                String name = (String) body.get("displayName");
                if (name == null || name.isEmpty() || name.equals(prettifyItemName(mat.name()))) {
                    ShopDataManager.removeCustomName(mat);
                } else {
                    ShopDataManager.setCustomName(mat, name);
                }
            }

            ShopDataManager.saveDynamicData();
            invalidateShopItemsCache();
        })) return;

        // Audit log
        StringBuilder changes = new StringBuilder();
        body.forEach((k, v) -> changes.append(k).append("=").append(v).append(", "));
        auditLog.log(getAdminUsername(ctx), "item_update", mat.name(), changes.toString());

        ctx.json(Map.of("success", true, "item", mat.name()));
    }

    /**
     * POST /api/admin/items/bulk
     * Body: { items: ["MAT1", "MAT2"], updates: { buyDisabled: true, ... } }
     */
    private void handleAdminItemsBulkUpdate(Context ctx) {
        if (ctx.statusCode() == 401) return;

        Map<String, Object> body;
        try {
            body = ctx.bodyAsClass(Map.class);
        } catch (Exception e) {
            ctx.status(400).json(Map.of("error", "Invalid JSON body"));
            return;
        }

        List<String> items = (List<String>) body.get("items");
        Map<String, Object> updates = (Map<String, Object>) body.get("updates");
        if (items == null || updates == null || items.isEmpty()) {
            ctx.status(400).json(Map.of("error", "Items and updates are required"));
            return;
        }

        List<Material> materialsToUpdate = new ArrayList<>();
        for (String itemName : items) {
             Material mat = Material.matchMaterial(itemName);
             if (mat != null && ShopDataManager.getBasePrice(mat) >= 0) {
                 materialsToUpdate.add(mat);
             }
        }

        if (!runSyncAdminTask(ctx, () -> {
            for (Material mat : materialsToUpdate) {
                if (updates.containsKey("disabled")) {
                    ShopDataManager.setItemDisabled(mat, (Boolean) updates.get("disabled"));
                }
                if (updates.containsKey("buyDisabled")) {
                    ShopDataManager.setBuyDisabled(mat, (Boolean) updates.get("buyDisabled"));
                }
                if (updates.containsKey("sellDisabled")) {
                    ShopDataManager.setSellDisabled(mat, (Boolean) updates.get("sellDisabled"));
                }
            }
            ShopDataManager.saveDynamicData();
            invalidateShopItemsCache();
        })) return;

        auditLog.log(getAdminUsername(ctx), "bulk_item_update", items.size() + " items", updates.toString());
        ctx.json(Map.of("success", true, "updated", materialsToUpdate.size()));
    }

    /**
     * GET /api/admin/config
     * Returns all dynamic-pricing config values
     */
    private void handleAdminConfigGet(Context ctx) {
        if (ctx.statusCode() == 401) return;

        Map<String, Object> config = new LinkedHashMap<>();
        config.put("dynamicPricingEnabled", ConfigCacheManager.dynamicPricingEnabled);
        config.put("useStockCurve", ConfigCacheManager.useStockCurve);
        config.put("curveStrength", ConfigCacheManager.curveStrength);
        config.put("maxStock", ConfigCacheManager.maxStock);
        config.put("minPriceMultiplier", ConfigCacheManager.minPriceMultiplier);
        config.put("maxPriceMultiplier", ConfigCacheManager.maxPriceMultiplier);
        config.put("negativeStockPercentPerItem", ConfigCacheManager.negativeStockPercentPerItem);
        config.put("useTimeInflation", ConfigCacheManager.useTimeInflation);
        config.put("hourlyIncreasePercent", ConfigCacheManager.hourlyIncreasePercent);
        config.put("shortageDecayPercentPerHour", ConfigCacheManager.shortageDecayPercentPerHour);
        config.put("restrictBuyingAtZeroStock", ConfigCacheManager.restrictBuyingAtZeroStock);
        config.put("logDynamicPricing", plugin.getConfig().getBoolean("dynamic-pricing.log-dynamic-pricing", false));

        // Economy
        config.put("sellTaxPercent", plugin.getConfig().getDouble("economy.sell_tax_percent", 30));
        config.put("transactionCooldownMs", plugin.getConfig().getInt("economy.transaction_cooldown_ms", 0));

        // GUI
        config.put("shopMenuSize", plugin.getConfig().getInt("gui.shop_menu_size", 54));

        // Logging
        config.put("maxRecentTransactions", plugin.getConfig().getInt("logging.max_recent_transactions", 10000));

        // Player Shops
        config.put("playerShopsEnabled", plugin.getConfig().getBoolean("player-shops.enabled", true));
        config.put("maxListingsPerPlayer", plugin.getConfig().getInt("player-shops.max-listings-per-player", 27));

        // Webserver
        config.put("webserverEnabled", plugin.getConfig().getBoolean("webserver.enabled", false));
        config.put("webserverPort", plugin.getConfig().getInt("webserver.port", 7713));
        config.put("webserverBind", plugin.getConfig().getString("webserver.bind", "127.0.0.1"));
        config.put("webserverCorsEnabled", plugin.getConfig().getBoolean("webserver.cors.enabled", false));
        config.put("webserverForceUpdate", plugin.getConfig().getBoolean("webserver.force-update-files", false));
        config.put("webserverAdminEnabled", plugin.getConfig().getBoolean("webserver.admin-enabled", true));
        config.put("webserverHostname", plugin.getConfig().getString("webserver.hostname", ""));

        // Restock
        config.put("restockEnabled", plugin.getConfig().getBoolean("restock.enabled", false));

        // Cross-server
        config.put("crossServerEnabled", plugin.getConfig().getBoolean("cross-server.enabled", false));
        config.put("crossServerPort", plugin.getConfig().getInt("cross-server.port", 5556));
        config.put("crossServerSaveInterval", plugin.getConfig().getInt("cross-server.save-interval-seconds", 600));

        ctx.json(config);
    }

    /**
     * POST /api/admin/config
     * Update config values. Accepts JSON body with any config fields.
     */
    private void handleAdminConfigUpdate(Context ctx) {
        if (ctx.statusCode() == 401) return;

        Map<String, Object> body;
        try {
            body = ctx.bodyAsClass(Map.class);
        } catch (Exception e) {
            ctx.status(400).json(Map.of("error", "Invalid JSON body"));
            return;
        }

        if (!runSyncAdminTask(ctx, () -> {
            if (body.containsKey("dynamicPricingEnabled")) {
                boolean val = (Boolean) body.get("dynamicPricingEnabled");
                plugin.getConfig().set("dynamic-pricing.enabled", val);
                ConfigCacheManager.dynamicPricingEnabled = val;
            }
            if (body.containsKey("useStockCurve")) {
                boolean val = (Boolean) body.get("useStockCurve");
                plugin.getConfig().set("dynamic-pricing.use-stock-curve", val);
                ConfigCacheManager.useStockCurve = val;
            }
            if (body.containsKey("curveStrength")) {
                double val = ((Number) body.get("curveStrength")).doubleValue();
                plugin.getConfig().set("dynamic-pricing.curve-strength", val);
                ConfigCacheManager.curveStrength = val;
            }
            if (body.containsKey("maxStock")) {
                double val = ((Number) body.get("maxStock")).doubleValue();
                plugin.getConfig().set("dynamic-pricing.max-stock", val);
                ConfigCacheManager.maxStock = val;
            }
            if (body.containsKey("minPriceMultiplier")) {
                double val = ((Number) body.get("minPriceMultiplier")).doubleValue();
                plugin.getConfig().set("dynamic-pricing.min-price-multiplier", val);
                ConfigCacheManager.minPriceMultiplier = val;
            }
            if (body.containsKey("maxPriceMultiplier")) {
                double val = ((Number) body.get("maxPriceMultiplier")).doubleValue();
                plugin.getConfig().set("dynamic-pricing.max-price-multiplier", val);
                ConfigCacheManager.maxPriceMultiplier = val;
            }
            if (body.containsKey("negativeStockPercentPerItem")) {
                double val = ((Number) body.get("negativeStockPercentPerItem")).doubleValue();
                plugin.getConfig().set("dynamic-pricing.negative-stock-percent-per-item", val);
                ConfigCacheManager.negativeStockPercentPerItem = val;
            }
            if (body.containsKey("useTimeInflation")) {
                boolean val = (Boolean) body.get("useTimeInflation");
                plugin.getConfig().set("dynamic-pricing.use-time-inflation", val);
                ConfigCacheManager.useTimeInflation = val;
            }
            if (body.containsKey("hourlyIncreasePercent")) {
                double val = ((Number) body.get("hourlyIncreasePercent")).doubleValue();
                plugin.getConfig().set("dynamic-pricing.hourly-increase-percent", val);
                ConfigCacheManager.hourlyIncreasePercent = val;
            }
            if (body.containsKey("shortageDecayPercentPerHour")) {
                double val = ((Number) body.get("shortageDecayPercentPerHour")).doubleValue();
                plugin.getConfig().set("dynamic-pricing.shortage-decay-percent-per-hour", val);
                ConfigCacheManager.shortageDecayPercentPerHour = val;
            }
            if (body.containsKey("restrictBuyingAtZeroStock")) {
                boolean val = (Boolean) body.get("restrictBuyingAtZeroStock");
                plugin.getConfig().set("dynamic-pricing.restrict-buying-at-zero-stock", val);
                ConfigCacheManager.restrictBuyingAtZeroStock = val;
            }
            if (body.containsKey("sellTaxPercent")) {
                double val = ((Number) body.get("sellTaxPercent")).doubleValue();
                plugin.getConfig().set("economy.sell_tax_percent", val);
                ConfigCacheManager.sellTaxPercent = val / 100.0;
            }
            if (body.containsKey("logDynamicPricing")) {
                boolean val = (Boolean) body.get("logDynamicPricing");
                plugin.getConfig().set("dynamic-pricing.log-dynamic-pricing", val);
            }

            // Economy
            if (body.containsKey("transactionCooldownMs")) {
                int val = ((Number) body.get("transactionCooldownMs")).intValue();
                plugin.getConfig().set("economy.transaction_cooldown_ms", val);
            }

            // GUI
            if (body.containsKey("shopMenuSize")) {
                int val = ((Number) body.get("shopMenuSize")).intValue();
                plugin.getConfig().set("gui.shop_menu_size", val);
            }

            // Logging
            if (body.containsKey("maxRecentTransactions")) {
                int val = ((Number) body.get("maxRecentTransactions")).intValue();
                plugin.getConfig().set("logging.max_recent_transactions", val);
            }

            // Player Shops
            if (body.containsKey("playerShopsEnabled")) {
                boolean val = (Boolean) body.get("playerShopsEnabled");
                plugin.getConfig().set("player-shops.enabled", val);
            }
            if (body.containsKey("maxListingsPerPlayer")) {
                int val = ((Number) body.get("maxListingsPerPlayer")).intValue();
                plugin.getConfig().set("player-shops.max-listings-per-player", val);
            }

            // Webserver
            if (body.containsKey("webserverPort")) {
                int val = ((Number) body.get("webserverPort")).intValue();
                plugin.getConfig().set("webserver.port", val);
            }
            if (body.containsKey("webserverBind")) {
                String val = (String) body.get("webserverBind");
                plugin.getConfig().set("webserver.bind", val);
            }
            if (body.containsKey("webserverCorsEnabled")) {
                boolean val = (Boolean) body.get("webserverCorsEnabled");
                plugin.getConfig().set("webserver.cors.enabled", val);
            }
            if (body.containsKey("webserverForceUpdate")) {
                boolean val = (Boolean) body.get("webserverForceUpdate");
                plugin.getConfig().set("webserver.force-update-files", val);
            }
            if (body.containsKey("webserverAdminEnabled")) {
                boolean val = (Boolean) body.get("webserverAdminEnabled");
                plugin.getConfig().set("webserver.admin-enabled", val);
            }
            if (body.containsKey("webserverHostname")) {
                String val = (String) body.get("webserverHostname");
                plugin.getConfig().set("webserver.hostname", val);
            }

            // Restock
            if (body.containsKey("restockEnabled")) {
                boolean val = (Boolean) body.get("restockEnabled");
                plugin.getConfig().set("restock.enabled", val);
            }

            // Cross-server
            if (body.containsKey("crossServerEnabled")) {
                plugin.getConfig().set("cross-server.enabled", (Boolean) body.get("crossServerEnabled"));
            }
            if (body.containsKey("crossServerPort")) {
                plugin.getConfig().set("cross-server.port", ((Number) body.get("crossServerPort")).intValue());
            }
            if (body.containsKey("crossServerSaveInterval")) {
                plugin.getConfig().set("cross-server.save-interval-seconds", ((Number) body.get("crossServerSaveInterval")).intValue());
            }
            if (body.containsKey("webserverEnabled")) {
                plugin.getConfig().set("webserver.enabled", (Boolean) body.get("webserverEnabled"));
            }
            plugin.saveConfig();
            invalidateShopItemsCache();
        })) return;

        // Audit log
        StringBuilder changes = new StringBuilder();
        body.forEach((k, v) -> changes.append(k).append("=").append(v).append(", "));
        auditLog.log(getAdminUsername(ctx), "config_update", "global", changes.toString());

        ctx.json(Map.of("success", true));
    }

    /**
     * POST /api/admin/resetshortage
     * Reset shortage data for all items
     */
    private void handleAdminResetShortage(Context ctx) {
        if (ctx.statusCode() == 401) return;

        if (!runSyncAdminTask(ctx, () -> {
            ShopDataManager.resetAllShortageData();
        })) return;
        auditLog.log(getAdminUsername(ctx), "shortage_reset", "ALL", "All shortage data reset");
        ctx.json(Map.of("success", true, "message", "All shortage data reset"));
    }

    /**
     * POST /api/admin/resetshortage/{item}
     * Reset shortage data for a specific item
     */
    private void handleAdminResetShortageItem(Context ctx) {
        if (ctx.statusCode() == 401) return;

        Material mat = Material.matchMaterial(ctx.pathParam("item"));
        if (mat == null) {
            ctx.status(404).json(Map.of("error", "Item not found"));
            return;
        }

        if (!runSyncAdminTask(ctx, () -> {
            ShopDataManager.setHoursInShortage(mat, 0.0);
            ShopDataManager.setLastUpdate(mat, System.currentTimeMillis());
            ShopDataManager.saveDynamicData();
        })) return;

        auditLog.log(getAdminUsername(ctx), "shortage_reset", mat.name(), "Shortage hours reset to 0");
        ctx.json(Map.of("success", true, "item", mat.name()));
    }

    /**
     * POST /api/admin/reload
     * Reload all plugin configuration (config.yml, messages.yml, items.yml)
     */
    private void handleAdminReload(Context ctx) {
        if (ctx.statusCode() == 401) return;

        if (!runSyncAdminTask(ctx, () -> {
            plugin.reload();
        })) return;

        auditLog.log(getAdminUsername(ctx), "plugin_reload", "global", "Plugin configuration reloaded via web admin");
        invalidateShopItemsCache();
        ctx.json(Map.of("success", true, "message", "Plugin reloaded"));
    }

    /**
     * GET /api/admin/audit
     * Returns the admin audit log (newest first, max 200 entries)
     */
    private void handleAdminAudit(Context ctx) {
        if (ctx.statusCode() == 401) return;
        String limitStr = ctx.queryParam("limit");
        int limit = 200;
        if (limitStr != null) {
            try { limit = Math.min(Integer.parseInt(limitStr), 500); } catch (NumberFormatException ignored) {}
        }
        ctx.json(auditLog.getEntriesAsJson(limit));
    }

    /**
     * GET /api/admin/categories
     * Returns all categories with full metadata (icon, slot, hidden, item count, etc.)
     */
    private void handleAdminCategories(Context ctx) {
        if (ctx.statusCode() == 401) return;

        List<Map<String, Object>> categories = new ArrayList<>();

        for (ItemCategory cat : ItemCategory.values()) {
            int slot = CategoryConfigManager.getSlot(cat);
            Material icon = CategoryConfigManager.getIcon(cat);
            String displayName = CategoryConfigManager.getDisplayName(cat);
            boolean hidden = (slot == -1);

            // Count items in this category
            long itemCount = ShopDataManager.getAllTrackedMaterials().stream()
                    .filter(mat -> ShopDataManager.getBasePrice(mat) >= 0)
                    .filter(mat -> ShopDataManager.detectCategory(mat) == cat)
                    .count();

            if (cat == ItemCategory.PERMISSIONS || cat == ItemCategory.SERVER_SHOP) {
                itemCount += plugin.getSpecialShopManager().getAllSpecialItems().values().stream()
                        .filter(i -> i.getCategory() == cat).count();
            } else if (cat == ItemCategory.PLAYER_SHOPS) {
                itemCount += plugin.getPlayerShopManager().getAllListings().size();
            }

            // Count items with shortage
            long shortageCount = ShopDataManager.getAllTrackedMaterials().stream()
                    .filter(mat -> ShopDataManager.getBasePrice(mat) >= 0)
                    .filter(mat -> ShopDataManager.detectCategory(mat) == cat)
                    .filter(mat -> ShopDataManager.getHoursInShortage(mat) > 0)
                    .count();

            // Count items out of stock
            long outOfStockCount = ShopDataManager.getAllTrackedMaterials().stream()
                    .filter(mat -> ShopDataManager.getBasePrice(mat) >= 0)
                    .filter(mat -> ShopDataManager.detectCategory(mat) == cat)
                    .filter(mat -> ShopDataManager.getStock(mat) <= 0)
                    .count();

            String iconUrl = "https://mc.nerothe.com/img/1.21/minecraft_" + icon.name().toLowerCase() + ".png";

            Map<String, Object> catMap = new LinkedHashMap<>();
            catMap.put("id", cat.name());
            catMap.put("displayName", displayName);
            catMap.put("icon", icon.name());
            catMap.put("iconUrl", iconUrl);
            catMap.put("slot", slot);
            catMap.put("hidden", hidden);
            catMap.put("isCustom", cat.isCustomCategory());
            catMap.put("itemCount", itemCount);
            catMap.put("shortageCount", shortageCount);
            catMap.put("outOfStockCount", outOfStockCount);

            int[] restockRule = plugin.getRestockManager().getRuleForCategory(cat);
            if (restockRule != null) {
                catMap.put("restockTarget", restockRule[0]);
                catMap.put("restockInterval", restockRule[1]);
            } else {
                catMap.put("restockTarget", null);
                catMap.put("restockInterval", null);
            }

            categories.add(catMap);
        }

        ctx.json(categories);
    }

    /**
     * POST /api/admin/category/{category}
     */
    private void handleAdminCategoryUpdate(Context ctx) {
        if (ctx.statusCode() == 401) return;
        String idName = ctx.pathParam("category");
        ItemCategory cat;
        try {
            cat = ItemCategory.valueOf(idName.toUpperCase());
        } catch (IllegalArgumentException e) {
            ctx.status(404).json(Map.of("error", "Category not found"));
            return;
        }

        Map<String, Object> body;
        try {
            body = ctx.bodyAsClass(Map.class);
        } catch (Exception e) {
            ctx.status(400).json(Map.of("error", "Invalid JSON body"));
            return;
        }

        if (!runSyncAdminTask(ctx, () -> {
            boolean categoryChanged = false;
            
            if (body.containsKey("slot")) {
                CategoryConfigManager.setSlot(cat, ((Number) body.get("slot")).intValue());
                categoryChanged = true;
            }
            if (body.containsKey("icon")) {
                String iconName = (String) body.get("icon");
                if (iconName == null || iconName.isEmpty() || iconName.equalsIgnoreCase("DEFAULT")) {
                    CategoryConfigManager.removeIcon(cat);
                } else {
                    try {
                        if (iconName.startsWith("nexo:")) {
                            CategoryConfigManager.setIcon(cat, iconName);
                        } else {
                            CategoryConfigManager.setIcon(cat, Material.valueOf(iconName.toUpperCase()).name());
                        }
                    } catch (Exception ignored) {}
                }
                categoryChanged = true;
            }
            if (body.containsKey("displayName")) {
                String displayName = (String) body.get("displayName");
                if (displayName == null || displayName.isEmpty() || displayName.equals(cat.getDisplayName())) {
                    CategoryConfigManager.removeDisplayName(cat);
                } else {
                    CategoryConfigManager.setDisplayName(cat, displayName);
                }
                categoryChanged = true;
            }

            if (categoryChanged) {
                CategoryConfigManager.save();
            }

            if (body.containsKey("restockTarget") && body.containsKey("restockInterval")) {
                Object tgtObj = body.get("restockTarget");
                Object intObj = body.get("restockInterval");
                if (tgtObj == null || intObj == null) {
                    plugin.getRestockManager().removeRuleForCategory(cat);
                } else {
                    int target = ((Number) tgtObj).intValue();
                    int interval = ((Number) intObj).intValue();
                    if (target > 0 && interval > 0) {
                        plugin.getRestockManager().setRuleForCategory(cat, target, interval);
                    } else {
                        plugin.getRestockManager().removeRuleForCategory(cat);
                    }
                }
            }
            
            auditLog.log(getAdminUsername(ctx), "category_update", cat.name(), "Updated category layout/restock rules");
        })) return;

        ctx.json(Map.of("success", true));
    }

    // ═══════════════════════════════════════════════════════════════
    // SHOP ITEM CREATE / REMOVE ENDPOINTS
    // ═══════════════════════════════════════════════════════════════

    /**
     * POST /api/admin/items/create
     * Add a new material to the shop.
     * Body: { material, basePrice, category? }
     */
    private void handleAdminItemCreate(Context ctx) {
        if (ctx.statusCode() == 401) return;
        Map<String, Object> body;
        try { body = ctx.bodyAsClass(Map.class); }
        catch (Exception e) { ctx.status(400).json(Map.of("error", "Invalid JSON")); return; }

        String matName = (String) body.get("material");
        if (matName == null || matName.isBlank()) {
            ctx.status(400).json(Map.of("error", "material is required"));
            return;
        }
        Material mat = Material.matchMaterial(matName.trim());
        if (mat == null || !mat.isItem()) {
            ctx.status(400).json(Map.of("error", "Unknown or non-item material: " + matName));
            return;
        }
        if (ShopDataManager.getBasePrice(mat) >= 0) {
            ctx.status(409).json(Map.of("error", "Item already exists in the shop: " + mat.name()));
            return;
        }

        double basePrice = body.containsKey("basePrice") ? ((Number) body.get("basePrice")).doubleValue() : 1.0;
        if (basePrice <= 0) {
            ctx.status(400).json(Map.of("error", "basePrice must be > 0"));
            return;
        }

        String catStr = (String) body.get("category");
        ItemCategory catOverride = null;
        if (catStr != null && !catStr.isBlank()) {
            try { catOverride = ItemCategory.valueOf(catStr.toUpperCase()); } catch (Exception ignored) {}
        }
        final ItemCategory finalCat = catOverride;

        if (!runSyncAdminTask(ctx, () -> {
            ShopDataManager.setBasePrice(mat, basePrice);
            ShopDataManager.setStockDirect(mat, 0);
            if (finalCat != null) ShopDataManager.setCategoryOverride(mat, finalCat);
            ShopDataManager.saveDynamicData();
            invalidateShopItemsCache();
        })) return;

        auditLog.log(getAdminUsername(ctx), "item_create", mat.name(),
                "basePrice=" + basePrice + (catOverride != null ? ", category=" + catOverride : ""));
        ctx.json(Map.of("success", true, "item", mat.name()));
    }

    /**
     * DELETE /api/admin/item/{item}
     * Fully removes an item from the shop (sets base = -1 then removes config key).
     */
    private void handleAdminItemRemove(Context ctx) {
        if (ctx.statusCode() == 401) return;
        Material mat = Material.matchMaterial(ctx.pathParam("item"));
        if (mat == null || ShopDataManager.getBasePrice(mat) < 0) {
            ctx.status(404).json(Map.of("error", "Item not in shop"));
            return;
        }
        if (!runSyncAdminTask(ctx, () -> {
            ShopDataManager.setItemDisabled(mat, true);
            ShopDataManager.saveDynamicData();
            invalidateShopItemsCache();
        })) return;
        auditLog.log(getAdminUsername(ctx), "item_remove", mat.name(), "Disabled/removed from shop");
        ctx.json(Map.of("success", true));
    }

    // ═══════════════════════════════════════════════════════════════

    private Map<String, Object> buildSpecialItemMap(org.minecraftsmp.dynamicshop.category.SpecialShopItem item) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", item.getId());
        m.put("name", item.getName());
        m.put("price", item.getPrice());
        m.put("displayMaterial", item.getDisplayMaterial() != null ? item.getDisplayMaterial().name() : "ENCHANTED_BOOK");
        m.put("requiredPermission", item.getRequiredPermission());
        if (item.isCommandItem()) {
            m.put("type", "command");
            m.put("command", item.getCommandOnPurchase());
        } else if (item.isGroupItem()) {
            m.put("type", "group");
            m.put("group", item.getGroupName());
            if (item.getGroupWorld() != null) {
                m.put("groupWorld", item.getGroupWorld());
            }
        } else {
            m.put("type", "perm");
            m.put("permission", item.getPermission());
            if (item.getPermissionWorld() != null) {
                m.put("permissionWorld", item.getPermissionWorld());
            }
        }
        return m;
    }

    /**
     * GET /api/admin/special-items
     * Returns all perm + group shop items.
     */
    private void handleAdminSpecialItems(Context ctx) {
        if (ctx.statusCode() == 401) return;
        List<Map<String, Object>> result = plugin.getSpecialShopManager().getAllSpecialItems().values().stream()
                .filter(i -> i.isPermissionItem() || i.isGroupItem() || i.isCommandItem())
                .map(this::buildSpecialItemMap)
                .collect(Collectors.toList());
        ctx.json(result);
    }

    /**
     * GET /api/admin/special-items/{id}
     */
    private void handleAdminSpecialItemGet(Context ctx) {
        if (ctx.statusCode() == 401) return;
        String id = ctx.pathParam("id");
        var item = plugin.getSpecialShopManager().getSpecialItem(id);
        if (item == null || (!item.isPermissionItem() && !item.isGroupItem() && !item.isCommandItem())) {
            ctx.status(404).json(Map.of("error", "Special item not found: " + id));
            return;
        }
        ctx.json(buildSpecialItemMap(item));
    }

    /**
     * POST /api/admin/special-items
     * Create a new perm or group item.
     * Body: { type, price, permission OR group, displayMaterial?, requiredPermission? }
     */
    private void handleAdminSpecialItemCreate(Context ctx) {
        if (ctx.statusCode() == 401) return;
        Map<String, Object> body;
        try { body = ctx.bodyAsClass(Map.class); }
        catch (Exception e) { ctx.status(400).json(Map.of("error", "Invalid JSON")); return; }

        String type = (String) body.getOrDefault("type", "perm");
        double price = body.containsKey("price") ? ((Number) body.get("price")).doubleValue() : 0;
        String requiredPerm = (String) body.get("requiredPermission");
        String matName = (String) body.getOrDefault("displayMaterial", "");
        Material displayMat;
        try {
            displayMat = (matName != null && !matName.isEmpty()) ? Material.valueOf(matName.toUpperCase()) :
                    ("group".equalsIgnoreCase(type) ? Material.NETHER_STAR : Material.ENCHANTED_BOOK);
        } catch (Exception e) {
            displayMat = "group".equalsIgnoreCase(type) ? Material.NETHER_STAR : Material.ENCHANTED_BOOK;
        }

        String group = (String) body.get("group");
        String groupWorld = (String) body.get("groupWorld");
        String displayName = (String) body.getOrDefault("name", "Command");
        String command = (String) body.get("command");
        String perm = (String) body.get("permission");
        String permWorld = (String) body.get("permissionWorld");

        if ("group".equalsIgnoreCase(type) && (group == null || group.isBlank())) {
            ctx.status(400).json(Map.of("error", "group is required"));
            return;
        }
        if ("command".equalsIgnoreCase(type) && (command == null || command.isBlank())) {
            ctx.status(400).json(Map.of("error", "command is required"));
            return;
        }
        if (!"group".equalsIgnoreCase(type) && !"command".equalsIgnoreCase(type)
                && (perm == null || perm.isBlank())) {
            ctx.status(400).json(Map.of("error", "permission is required"));
            return;
        }

        final Material finalMat = displayMat;
        if (!runSyncAdminTask(ctx, () -> {
            if ("group".equalsIgnoreCase(type)) {
                plugin.getSpecialShopManager().addGroupItem(group, groupWorld, price, finalMat,
                        (requiredPerm != null && !requiredPerm.isBlank()) ? requiredPerm : null);
            } else if ("command".equalsIgnoreCase(type)) {
                plugin.getSpecialShopManager().addCommandItem(displayName, price, command, finalMat,
                        (requiredPerm != null && !requiredPerm.isBlank()) ? requiredPerm : null);
            } else {
                plugin.getSpecialShopManager().addPermissionItem(perm, permWorld, price, finalMat,
                        (requiredPerm != null && !requiredPerm.isBlank()) ? requiredPerm : null);
            }
        })) return;

        auditLog.log(getAdminUsername(ctx), "special_item_create", type,
                "type=" + type + ", price=" + price);
        ctx.json(Map.of("success", true));
    }

    /**
     * POST /api/admin/special-items/{id}
     * Update price, displayMaterial, or requiredPermission of a special item.
     * Body: { price?, displayMaterial?, requiredPermission? }
     */
    private void handleAdminSpecialItemUpdate(Context ctx) {
        if (ctx.statusCode() == 401) return;
        String id = ctx.pathParam("id");
        var item = plugin.getSpecialShopManager().getSpecialItem(id);
        if (item == null || (!item.isPermissionItem() && !item.isGroupItem() && !item.isCommandItem())) {
            ctx.status(404).json(Map.of("error", "Special item not found: " + id));
            return;
        }
        Map<String, Object> body;
        try { body = ctx.bodyAsClass(Map.class); }
        catch (Exception e) { ctx.status(400).json(Map.of("error", "Invalid JSON")); return; }

        if (!runSyncAdminTask(ctx, () -> {
            if (body.containsKey("price")) {
                double price = ((Number) body.get("price")).doubleValue();
                plugin.getSpecialShopManager().updateItemPrice(id, price);
            }
            if (body.containsKey("displayMaterial")) {
                try {
                    Material mat = Material.valueOf(((String) body.get("displayMaterial")).toUpperCase());
                    plugin.getSpecialShopManager().updateItemDisplayMaterial(id, mat);
                } catch (Exception ignored) {}
            }
            if (body.containsKey("requiredPermission")) {
                String rp = (String) body.get("requiredPermission");
                plugin.getSpecialShopManager().updateItemRequiredPermission(id,
                        (rp != null && !rp.isBlank()) ? rp : null);
            }
        })) return;

        auditLog.log(getAdminUsername(ctx), "special_item_update", id, body.toString());
        ctx.json(Map.of("success", true));
    }

    /**
     * DELETE /api/admin/special-items/{id}
     */
    private void handleAdminSpecialItemDelete(Context ctx) {
        if (ctx.statusCode() == 401) return;
        String id = ctx.pathParam("id");
        Boolean removed = callSyncAdminTask(ctx, () -> plugin.getSpecialShopManager().removeSpecialItem(id));
        if (removed == null) return;
        if (!removed) {
            ctx.status(404).json(Map.of("error", "Special item not found: " + id));
            return;
        }
        auditLog.log(getAdminUsername(ctx), "special_item_delete", id, "Deleted");
        ctx.json(Map.of("success", true));
    }

    /**
     * DELETE /api/admin/playershop/{id}
     */
    private void handleAdminPlayerShopDelete(Context ctx) {
        if (ctx.statusCode() == 401) return;
        String id = ctx.pathParam("id");
        Boolean removed = callSyncAdminTask(ctx, () -> plugin.getPlayerShopManager().removeListing(id));
        if (removed == null) return;
        if (!removed) {
            ctx.status(404).json(Map.of("error", "Listing not found: " + id));
            return;
        }
        invalidateShopItemsCache();
        auditLog.log(getAdminUsername(ctx), "playershop_delete", id, "Admin deleted player shop listing: " + id);
        ctx.json(Map.of("success", true));
    }

    /**
     * Build the admin data map for a single material.
     */
    private Map<String, Object> buildAdminItemMap(Material mat) {
        double basePrice = ShopDataManager.getBasePrice(mat);
        double stock = ShopDataManager.getStock(mat);
        double buyPrice = ShopDataManager.getPrice(mat);
        double sellPrice = ShopDataManager.getSellPrice(mat);
        ItemCategory category = ShopDataManager.detectCategory(mat);
        double shortageHours = ShopDataManager.getHoursInShortage(mat);
        double stockRate = ShopDataManager.getStockRate(mat);

        // Calculate price increase %
        double hourlyRate = ConfigCacheManager.hourlyIncreasePercent / 100.0;
        double multiplier = Math.pow(1.0 + hourlyRate, shortageHours);
        double percentIncrease = (multiplier - 1.0) * 100.0;
        double maxPercent = (ConfigCacheManager.maxPriceMultiplier - 1.0) * 100.0;
        if (percentIncrease > maxPercent) percentIncrease = maxPercent;

        String imageUrl = "https://mc.nerothe.com/img/1.21/minecraft_" + mat.name().toLowerCase() + ".png";

        // Fetch max limits directly from record if available, else retrieve from global defaults
        Double maxStockConfig = null;
        Integer maxStockStorageConfig = null;
        try {
            var itemConfig = ShopDataManager.itemConfigs.get(mat);
            if (itemConfig != null) {
                maxStockConfig = itemConfig.maxStock();
                maxStockStorageConfig = itemConfig.maxStockStorage();
            }
        } catch (Exception ignored) {}

        Map<String, Object> item = new LinkedHashMap<>();
        item.put("item", mat.name());
        String adminCustomName = ShopDataManager.getCustomName(mat);
        item.put("displayName", adminCustomName != null ? adminCustomName : prettifyItemName(mat.name()));
        item.put("customName", adminCustomName);
        item.put("category", category.name());
        item.put("basePrice", basePrice);
        item.put("buyPrice", buyPrice);
        item.put("sellPrice", sellPrice);
        item.put("stock", stock);
        item.put("stockRate", stockRate);
        item.put("maxStock", maxStockConfig);
        item.put("maxStockStorage", maxStockStorageConfig);
        item.put("shortageHours", shortageHours);
        item.put("priceIncreasePercent", percentIncrease);
        item.put("buyDisabled", ShopDataManager.isBuyDisabled(mat));
        item.put("sellDisabled", ShopDataManager.isSellDisabled(mat));
        item.put("disabled", ShopDataManager.isItemDisabled(mat));
        item.put("imageUrl", imageUrl);
        return item;
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
