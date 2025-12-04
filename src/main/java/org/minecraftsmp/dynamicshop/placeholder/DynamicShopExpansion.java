package org.minecraftsmp.dynamicshop.placeholder;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.minecraftsmp.dynamicshop.DynamicShop;
import org.minecraftsmp.dynamicshop.managers.ShopDataManager;
import org.minecraftsmp.dynamicshop.transactions.Transaction;

import java.util.Map;

public class DynamicShopExpansion extends PlaceholderExpansion {

    private final DynamicShop plugin;

    public DynamicShopExpansion(DynamicShop plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() { return "dynamicshop"; }

    @Override
    public @NotNull String getAuthor() { return "selfservice0"; }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() { return true; }

    public @Nullable String onPlaceholderRequest(OfflinePlayer p, @NotNull String id) {

        id = id.toLowerCase();

        // ------------------------------
        // Global Stats
        // ------------------------------
        switch (id) {
            case "total_transactions":
                return String.valueOf(plugin.getTransactionLogger().getTotalTransactions());
            case "items_bought":
                return String.valueOf(plugin.getTransactionLogger().getTotalItemsBought());
            case "items_sold":
                return String.valueOf(plugin.getTransactionLogger().getTotalItemsSold());
            case "total_money_exchanged":
                return String.format("%.2f", plugin.getTransactionLogger().getTotalMoneyExchanged());
            case "recent_transaction":
                Transaction last = plugin.getTransactionLogger().getMostRecentTransaction();
                return last == null ?
                        "None" :
                        String.format(
                                "%s | %s %s %dx %s | $%.2f",
                                last.getTimestamp(),
                                last.getPlayerName(),
                                last.getType().name(),
                                last.getAmount(),
                                last.getItem(),
                                last.getPrice()
                        );

            case "most_traded_item":
                return plugin.getTransactionLogger().getMostTradedItem();
        }

        // ------------------------------
        // Player Stats
        // dynamicshop_player_<name>_<stat>
        // ------------------------------
        if (id.startsWith("player_")) {
            // player_swiftxx_purchases
            String[] parts = id.split("_");
            if (parts.length < 3) return "invalid_format";

            String player = parts[1];
            String stat = parts[2];

            Map<String, Object> stats = plugin.getTransactionLogger().getPlayerStats(player);
            if (stats == null) return "0";

            return String.valueOf(stats.getOrDefault(stat, "0"));
        }

        // ------------------------------
        // Item Stats
        // dynamicshop_item_<stat>_<identifier>
        // ------------------------------
        if (id.startsWith("item_")) {
            String[] parts = id.split("_");
            if (parts.length < 3) return "invalid_format";

            String stat = parts[1];
            String identifier = parts[2].toUpperCase();

            ShopDataManager.ItemStats s = ShopDataManager.getStats(identifier);
            if (s == null) return "0";

            switch (stat) {
                case "displayname": return s.displayName;
                case "timesbought":
                case "times_bought": return String.valueOf(s.timesBought);
                case "timessold":
                case "times_sold": return String.valueOf(s.timesSold);
                case "times_bought_quantity": return String.valueOf(s.quantityBought);
                case "times_sold_quantity": return String.valueOf(s.quantitySold);
                case "net":
                case "net_flow": return String.valueOf(s.getNetFlow());
            }
        }

        return null;
    }
}