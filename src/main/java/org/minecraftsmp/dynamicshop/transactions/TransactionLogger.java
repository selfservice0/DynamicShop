package org.minecraftsmp.dynamicshop.transactions;

import org.bukkit.scheduler.BukkitTask;
import org.minecraftsmp.dynamicshop.DynamicShop;

import java.io.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

public class TransactionLogger {

    private final DynamicShop plugin;
    private File logDir;
    private File csvFile;

    private final Deque<Transaction> recent = new ArrayDeque<>();
    private int maxRecent = 5000;

    // Queue for pending disk writes
    private final java.util.concurrent.ConcurrentLinkedQueue<Transaction> pendingWrites =
            new java.util.concurrent.ConcurrentLinkedQueue<>();

    // Batch update system - writes to disk periodically
    private BukkitTask periodicTask = null;

    public TransactionLogger(DynamicShop plugin) {
        this.plugin = plugin;
    }

    public void init() {
        logDir = new File(plugin.getDataFolder(), "logs");

        // Create transactions directory
        File transactionsDir = new File(plugin.getDataFolder(), "transactions");
        if (!transactionsDir.exists()) transactionsDir.mkdirs();

        // Put CSV in the transactions folder
        csvFile = new File(transactionsDir, "transactions.csv");

        if (!logDir.exists()) logDir.mkdirs();

        maxRecent = plugin.getConfig().getInt("logging.max_recent_transactions", 5000);

        // Load from transactions.csv on startup
        loadFromCSV();

        // Load from daily logs if CSV is empty or missing
        if (recent.isEmpty()) loadFromLogs();

        plugin.getLogger().info("Loaded " + recent.size() + " recent transactions");

        // Start batch write task - writes queued transactions every 5 seconds
        startBatchWriteTask();
    }

    /**
     * Start background task to flush transaction writes every 5 seconds
     */
    private void startBatchWriteTask() {
        if (periodicTask != null) {
            periodicTask.cancel();
        }

        periodicTask = plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            flushPendingWrites();
        }, 100L, 100L); // Every 5 seconds (100 ticks)
    }

    /**
     * Flush all pending transaction writes to disk
     */
    private void flushPendingWrites() {
        if (pendingWrites.isEmpty()) {
            return;
        }

        int flushed = 0;
        Transaction tx;

        while ((tx = pendingWrites.poll()) != null) {
            writeToCSV(tx);
            writeToDailyLog(tx);
            flushed++;
        }

        if (flushed > 0) {
            plugin.getLogger().fine("[TransactionLogger] Flushed " + flushed + " transactions to disk");
        }
    }

    /**
     * Stop any background tasks and flush pending writes (called on plugin disable)
     */
    public void shutdown() {
        if (periodicTask != null) {
            periodicTask.cancel();
            periodicTask = null;
        }

        // Flush any remaining pending writes
        plugin.getLogger().info("[TransactionLogger] Flushing pending writes on shutdown...");
        flushPendingWrites();
    }

    /**
     * Log a transaction to memory and queue for disk write
     */
    public void log(Transaction tx) {
        // Add to in-memory buffer (instant)
        recent.addLast(tx);

        // Keep it bounded
        while (recent.size() > maxRecent) {
            recent.removeFirst();
        }

        // Queue for disk write (batched every 5 seconds)
        pendingWrites.offer(tx);
    }

    /**
     * Get recent transactions for web dashboard
     */
    public synchronized List<Transaction> getRecentTransactions() {
        return new ArrayList<>(recent);
    }

    // ================================================================
    // CSV PERSISTENCE
    // ================================================================

    /**
     * Write a transaction to the CSV file
     */
    private void writeToCSV(Transaction tx) {
        String entry = String.join(",",
                UUID.randomUUID().toString(), // transactionID
                UUID.randomUUID().toString(), // playerID (random placeholder)
                safe(tx.getPlayerName()),
                tx.getType().name(),
                safe(tx.getItem()),
                safe(prettify(tx.getItem())), // displayName
                String.valueOf(tx.getAmount()),
                String.valueOf(tx.getPrice()),
                String.valueOf(tx.getPrice() / tx.getAmount()), // unitPrice
                tx.getTimestamp()
        );

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(csvFile, true))) {
            writer.write(entry);
            writer.newLine();
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to append to CSV: " + e.getMessage());
        }
    }

    /**
     * Write a transaction to the daily log file (backup)
     */
    private void writeToDailyLog(Transaction tx) {
        String fileName = tx.getDate() + ".log";
        File file = new File(logDir, fileName);
        String entry = String.join(",",
                tx.getTimestamp(),
                safe(tx.getPlayerName()),
                tx.getType().name(),
                safe(tx.getItem()),
                String.valueOf(tx.getAmount()),
                String.valueOf(tx.getPrice()),
                safe(tx.getCategory() != null ? tx.getCategory() : ""),
                safe(tx.getMetadata() != null ? tx.getMetadata() : "")
        );

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file, true))) {
            writer.write(entry);
            writer.newLine();
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to write daily log: " + e.getMessage());
        }
    }

    /**
     * Escape commas and quotes in CSV values
     */
    private String safe(String s) {
        if (s == null) return "";
        if (s.contains(",")) return "\"" + s.replace("\"", "\"\"") + "\"";
        return s;
    }

    // ================================================================
    // LOADING FROM DISK
    // ================================================================

    /**
     * Load recent transactions from CSV on startup
     */
    private void loadFromCSV() {
        if (!csvFile.exists()) {
            plugin.getLogger().info("No transactions.csv found - starting fresh");
            return;
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(csvFile))) {
            String line;
            int loaded = 0;

            // Read all lines into a temporary list
            List<Transaction> temp = new ArrayList<>();
            while ((line = reader.readLine()) != null) {
                Transaction tx = parseCSVLine(line);
                if (tx != null) {
                    temp.add(tx);
                }
            }

            // Keep only the most recent maxRecent transactions
            int start = Math.max(0, temp.size() - maxRecent);
            for (int i = start; i < temp.size(); i++) {
                recent.addLast(temp.get(i));
                loaded++;
            }

            plugin.getLogger().info("Loaded " + loaded + " transactions from CSV");
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to load transactions.csv: " + e.getMessage());
        }
    }

    /**
     * Parse a CSV line into a Transaction object
     */
    private Transaction parseCSVLine(String line) {
        String[] p = line.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)", -1);
        if (p.length < 10) return null;

        try {
            LocalDateTime ts = LocalDateTime.parse(p[9], DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            String player = p[2].replace("\"", "");
            Transaction.TransactionType type = Transaction.TransactionType.valueOf(p[3]);
            String item = p[4].replace("\"", "");
            int amount = Integer.parseInt(p[6]);
            double price = Double.parseDouble(p[7]);
            String category = ""; // Not stored in old CSV format
            String metadata = ""; // Not stored in old CSV format
            return new Transaction(ts, player, type, item, amount, price, category, metadata);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Load transactions from daily log files (fallback)
     */
    private void loadFromLogs() {
        File[] logFiles = logDir.listFiles((dir, name) -> name.matches("\\d{4}-\\d{2}-\\d{2}\\.log"));
        if (logFiles == null) return;

        // Sort by date, newest first
        Arrays.sort(logFiles, Comparator.comparing(File::getName).reversed());

        int loaded = 0;
        for (File file : logFiles) {
            if (loaded >= maxRecent) break;

            try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                String line;
                while ((line = reader.readLine()) != null && loaded < maxRecent) {
                    Transaction tx = parseLogLine(line);
                    if (tx != null) {
                        recent.addFirst(tx); // Add in reverse order
                        loaded++;
                    }
                }
            } catch (IOException e) {
                plugin.getLogger().warning("Failed to load log " + file.getName() + ": " + e.getMessage());
            }
        }

        plugin.getLogger().info("Loaded " + loaded + " transactions from daily logs");
    }

    /**
     * Parse a daily log line into a Transaction object
     */
    private Transaction parseLogLine(String line) {
        String[] p = line.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)", -1);
        if (p.length < 6) return null;

        try {
            LocalDateTime ts = LocalDateTime.parse(p[0], DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            String player = p[1].replace("\"", "");
            Transaction.TransactionType type = Transaction.TransactionType.valueOf(p[2]);
            String item = p[3].replace("\"", "");
            int amount = Integer.parseInt(p[4]);
            double price = Double.parseDouble(p[5]);
            String category = p.length > 6 ? p[6].replace("\"", "") : "";
            String metadata = p.length > 7 ? p[7].replace("\"", "") : "";
            return new Transaction(ts, player, type, item, amount, price, category, metadata);
        } catch (Exception e) {
            return null;
        }
    }

    // ================================================================
    // UTILITY METHODS
    // ================================================================

    /**
     * Prettify material names for display
     * STONE_BRICKS -> Stone Bricks
     */
    private String prettify(String name) {
        return Arrays.stream(name.toLowerCase().split("_"))
                .map(w -> w.isEmpty() ? "" : Character.toUpperCase(w.charAt(0)) + w.substring(1))
                .collect(Collectors.joining(" "));
    }

    // ================================================================
    // STATISTICS METHODS (for PlaceholderAPI)
    // ================================================================

    /**
     * Get total number of transactions
     */
    public int getTotalTransactions() {
        return recent.size();
    }

    /**
     * Get total items bought across all transactions
     */
    public int getTotalItemsBought() {
        return recent.stream()
                .filter(t -> t.getType() == Transaction.TransactionType.BUY)
                .mapToInt(Transaction::getAmount)
                .sum();
    }

    /**
     * Get total items sold across all transactions
     */
    public int getTotalItemsSold() {
        return recent.stream()
                .filter(t -> t.getType() == Transaction.TransactionType.SELL)
                .mapToInt(Transaction::getAmount)
                .sum();
    }

    /**
     * Get total money exchanged (buy + sell)
     */
    public double getTotalMoneyExchanged() {
        return recent.stream()
                .mapToDouble(Transaction::getPrice)
                .sum();
    }

    /**
     * Get the most recent transaction
     */
    public Transaction getMostRecentTransaction() {
        return recent.isEmpty() ? null : recent.getLast();
    }

    /**
     * Get the most traded item (by transaction count)
     */
    public String getMostTradedItem() {
        if (recent.isEmpty()) return "None";

        Map<String, Long> itemCounts = recent.stream()
                .collect(Collectors.groupingBy(Transaction::getItem, Collectors.counting()));

        return itemCounts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("None");
    }

    /**
     * Get player statistics
     * Returns map with keys: purchases, sales, total_spent, total_earned
     */
    public Map<String, Object> getPlayerStats(String playerName) {
        Map<String, Object> stats = new HashMap<>();

        List<Transaction> playerTxs = recent.stream()
                .filter(t -> t.getPlayerName().equalsIgnoreCase(playerName))
                .collect(Collectors.toList());

        if (playerTxs.isEmpty()) return null;

        int purchases = (int) playerTxs.stream()
                .filter(t -> t.getType() == Transaction.TransactionType.BUY)
                .count();

        int sales = (int) playerTxs.stream()
                .filter(t -> t.getType() == Transaction.TransactionType.SELL)
                .count();

        double totalSpent = playerTxs.stream()
                .filter(t -> t.getType() == Transaction.TransactionType.BUY)
                .mapToDouble(Transaction::getPrice)
                .sum();

        double totalEarned = playerTxs.stream()
                .filter(t -> t.getType() == Transaction.TransactionType.SELL)
                .mapToDouble(Transaction::getPrice)
                .sum();

        stats.put("purchases", purchases);
        stats.put("sales", sales);
        stats.put("total_spent", String.format("%.2f", totalSpent));
        stats.put("total_earned", String.format("%.2f", totalEarned));

        return stats;
    }

    // Add this method anywhere in TransactionLogger class
    public long getMostRecentTransactionTime() {
        if (recent.isEmpty()) {
            return 0L;
        }
        Transaction last = recent.getLast();
        return last != null ? last.getTimestampRaw().atZone(java.time.ZoneOffset.UTC).toInstant().toEpochMilli() : 0L;
    }
}