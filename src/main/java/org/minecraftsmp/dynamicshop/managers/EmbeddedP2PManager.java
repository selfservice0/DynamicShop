package org.minecraftsmp.dynamicshop.managers;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.Material;
import org.bukkit.scheduler.BukkitRunnable;
import org.minecraftsmp.dynamicshop.DynamicShop;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class EmbeddedP2PManager {

    private final DynamicShop plugin;
    private ZContext context;
    private ZMQ.Socket publisher;
    private ZMQ.Socket subscriber;
    private Thread listenerThread;
    private final AtomicBoolean running = new AtomicBoolean(false);

    private int port;
    private static final String TOPIC_STOCK = "STOCK";
    private static final String TOPIC_SYNC_REQUEST = "SYNC_REQUEST";
    private static final String TOPIC_SYNC_RESPONSE = "SYNC_RESPONSE";

    private static long lastKnownTransactionTime = 0L;

    public EmbeddedP2PManager(DynamicShop plugin) {
        this.plugin = plugin;
    }

    // --------------------------------------------------------------------
    // INIT
    // --------------------------------------------------------------------
    public void init() {
        if (!plugin.getConfig().getBoolean("cross-server.enabled", false)) return;

        this.port = plugin.getConfig().getInt("cross-server.port", 5556);
        if (port < 1024 || port > 65535) port = 5556;

        context = new ZContext();
        publisher = context.createSocket(SocketType.PUB);
        subscriber = context.createSocket(SocketType.SUB);

        if (!publisher.bind("tcp://*:" + port)) {
            plugin.getLogger().severe("[P2P] Failed to bind port " + port);
            shutdown();
            return;
        }

        List<String> peers = plugin.getConfig().getStringList("cross-server.peers");
        for (String peer : peers) {
            String addr = parsePeerAddress(peer.trim().replaceAll("[\"']", ""));
            if (addr != null) subscriber.connect(addr);
        }

        subscriber.subscribe(TOPIC_STOCK.getBytes());
        subscriber.subscribe(TOPIC_SYNC_REQUEST.getBytes());
        subscriber.subscribe(TOPIC_SYNC_RESPONSE.getBytes());

        lastKnownTransactionTime = plugin.getTransactionLogger().getMostRecentTransactionTime();
        running.set(true);
        startListener();

        // Ask who has newer data (after flushing our queue)
        new BukkitRunnable() {
            @Override public void run() {
                // Flush queue and save before requesting sync
                // This ensures we send our true current state
                ShopDataManager.flushQueue();
                ShopDataManager.saveDynamicData();

                long myTx = plugin.getTransactionLogger().getMostRecentTransactionTime();
                publisher.send(TOPIC_SYNC_REQUEST + " " + myTx);

                plugin.getLogger().info("[P2P] Requesting sync with transaction time: " + myTx);
            }
        }.runTaskLater(plugin, 60L);
    }

    private String parsePeerAddress(String input) {
        if (input == null || input.isBlank()) return null;

        String host = input;
        int targetPort = port;

        if (input.contains(":")) {
            int colon = input.lastIndexOf(':');
            host = input.substring(0, colon);
            try { targetPort = Integer.parseInt(input.substring(colon + 1)); }
            catch (Exception ignored) { return null; }
        }

        if (host.startsWith("[") && host.endsWith("]"))
            host = host.substring(1, host.length() - 1);

        return "tcp://" + host + ":" + targetPort;
    }

    // --------------------------------------------------------------------
    // LISTENER
    // --------------------------------------------------------------------
    private void startListener() {
        listenerThread = new Thread(() -> {
            while (running.get()) {
                try {
                    String msg = subscriber.recvStr(0);
                    if (msg == null) continue;

                    if (msg.startsWith(TOPIC_SYNC_REQUEST + " ")) {
                        handleSyncRequest(msg);
                    } else if (msg.startsWith(TOPIC_SYNC_RESPONSE + " ")) {
                        handleSyncResponse(msg);
                    } else if (msg.startsWith(TOPIC_STOCK + " ")) {
                        String json = msg.substring(TOPIC_STOCK.length() + 1);
                        new BukkitRunnable() {
                            @Override public void run() { handleStock(json); }
                        }.runTask(plugin);
                    }

                } catch (Exception ignored) {}
            }
        }, "DynamicShop-P2P");
        listenerThread.setDaemon(true);
        listenerThread.start();
    }

    // --------------------------------------------------------------------
    // REQUEST HANDLER
    // --------------------------------------------------------------------
    private void handleSyncRequest(String msg) {
        long requesterTx;
        try {
            requesterTx = Long.parseLong(msg.substring(TOPIC_SYNC_REQUEST.length() + 1).trim());
        } catch (Exception ignored) { return; }

        // CRITICAL: Flush queue and save data before checking/responding
        // This ensures we send the most up-to-date data as source of truth
        new BukkitRunnable() {
            @Override
            public void run() {
                // Force process all pending updates immediately
                ShopDataManager.flushQueue();

                // Force immediate save to disk (synchronous)
                ShopDataManager.saveDynamicData();

                // Now check if we have newer data
                long myTx = plugin.getTransactionLogger().getMostRecentTransactionTime();
                if (myTx > requesterTx) {
                    publisher.send(TOPIC_SYNC_RESPONSE + " " + myTx);
                    sendFullSync(myTx);
                }
            }
        }.runTask(plugin);
    }

    // --------------------------------------------------------------------
    // RESPONSE HANDLER
    // --------------------------------------------------------------------
    private void handleSyncResponse(String msg) {
        long tx;
        try {
            tx = Long.parseLong(msg.substring(TOPIC_SYNC_RESPONSE.length() + 1).trim());
        } catch (Exception ignored) { return; }

        if (tx > lastKnownTransactionTime) {
            lastKnownTransactionTime = tx;
        }
    }

    // --------------------------------------------------------------------
    // SEND FULL SYNC
    // --------------------------------------------------------------------
    private void sendFullSync(long tx) {
        for (Material mat : ShopDataManager.stockMap.keySet()) {
            double stock = ShopDataManager.getStock(mat);
            double purchases = ShopDataManager.getPurchases(mat);
            publishStock(mat, stock, purchases, tx);
        }
    }

    private void publishStock(Material mat, double stock, double purchases, long tx) {
        JsonObject json = new JsonObject();
        json.addProperty("material", mat.name());
        json.addProperty("stock", stock);
        json.addProperty("purchases", purchases);
        json.addProperty("lastTx", tx);

        publisher.send(TOPIC_STOCK + " " + json);
    }

    public void publishStockUpdate(Material mat, double stock, double purchases) {
        if (!running.get()) return;

        long tx = plugin.getTransactionLogger().getMostRecentTransactionTime();

        JsonObject json = new JsonObject();
        json.addProperty("material", mat.name());
        json.addProperty("stock", stock);
        json.addProperty("purchases", purchases);
        json.addProperty("lastTx", tx);

        publisher.send(TOPIC_STOCK + " " + json);
    }

    // --------------------------------------------------------------------
    // RECEIVE STOCK UPDATE (queues for processing)
    // --------------------------------------------------------------------
    private void handleStock(String jsonStr) {
        try {
            JsonObject obj = JsonParser.parseString(jsonStr).getAsJsonObject();
            long remoteTx = obj.get("lastTx").getAsLong();

            if (remoteTx < lastKnownTransactionTime) return;
            lastKnownTransactionTime = remoteTx;

            Material mat = Material.valueOf(obj.get("material").getAsString());
            double stock = obj.get("stock").getAsDouble();
            double purchases = obj.get("purchases").getAsDouble();

            // Use new queue-based method for receiving remote updates
            ShopDataManager.receiveRemoteStockUpdate(mat, stock, purchases);

        } catch (Exception ignored) {}
    }

    // --------------------------------------------------------------------
    // SHUTDOWN
    // --------------------------------------------------------------------
    public void shutdown() {
        running.set(false);
        if (listenerThread != null) listenerThread.interrupt();
        if (context != null) context.destroy();
    }

    public boolean isRunning() { return running.get(); }
}