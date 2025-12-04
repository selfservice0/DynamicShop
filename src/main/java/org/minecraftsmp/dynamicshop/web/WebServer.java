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
import java.util.List;
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

                // THIS IS THE ONLY VERSION THAT ACTUALLY WORKS
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
            app.get("/api/stats",        this::handleLegacyStats);

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

    // THIS METHOD IS THE FINAL FIX — DO NOT CHANGE IT
    private ObjectMapper createFixedMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
        // THIS LINE KILLS THE INFINITE RECURSION FOREVER
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

    private void handleLegacyStats(Context ctx) {
        var txs = plugin.getTransactionLogger().getRecentTransactions();
        long buys = txs.stream().filter(t -> t.getType() == Transaction.TransactionType.BUY).count();
        long sells = txs.stream().filter(t -> t.getType() == Transaction.TransactionType.SELL).count();
        double money = txs.stream().mapToDouble(Transaction::getPrice).sum();
        ctx.json("{\"total\":%d,\"buys\":%d,\"sells\":%d,\"totalMoney\":%.2f}"
                .formatted(txs.size(), buys, sells, money));
    }

    private int parseLimit(String s, int def) {
        try { return s == null ? def : Math.min(Integer.parseInt(s), 1000); }
        catch (Exception e) { return def; }
    }

    // SAFE DTO — THIS IS WHAT GETS SENT TO JSON
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
}