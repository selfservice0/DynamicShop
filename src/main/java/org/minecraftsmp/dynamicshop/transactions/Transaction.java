package org.minecraftsmp.dynamicshop.transactions;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Represents a single shop transaction.
 */
public class Transaction {

    public enum TransactionType {
        BUY,
        SELL
    }

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter TS_FMT   = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final LocalDateTime timestamp;
    private final String playerName;
    private final TransactionType type;
    private final String item;
    private final int amount;
    private final double price;     // total price for the transaction
    private final String category;
    private final String metadata;  // optional misc info (tax, notes, etc.)

    public Transaction(LocalDateTime timestamp,
                       String playerName,
                       TransactionType type,
                       String item,
                       int amount,
                       double price,
                       String category,
                       String metadata) {

        this.timestamp = timestamp;
        this.playerName = playerName;
        this.type = type;
        this.item = item;
        this.amount = amount;
        this.price = price;
        this.category = category;
        this.metadata = metadata;
    }

    // Convenience factory for "now"
    public static Transaction now(String playerName,
                                  TransactionType type,
                                  String item,
                                  int amount,
                                  double price,
                                  String category,
                                  String metadata) {
        return new Transaction(LocalDateTime.now(), playerName, type, item, amount, price, category, metadata);
    }

    // ---------------------------------------------------------------------
    // Accessors used by logger + web
    // ---------------------------------------------------------------------

    public LocalDateTime getTimestampRaw() {
        return timestamp;
    }

    public String getTimestamp() {
        return TS_FMT.format(timestamp);
    }

    public String getDate() {
        return DATE_FMT.format(timestamp);
    }

    public String getPlayerName() {
        return playerName;
    }

    public TransactionType getType() {
        return type;
    }

    /**
     * Text action for older code that expected a String ("BUY"/"SELL").
     */
    public String getAction() {
        return type.name();
    }

    public String getItem() {
        return item;
    }

    public int getAmount() {
        return amount;
    }

    public double getPrice() {
        return price;
    }

    public String getCategory() {
        return category;
    }

    public String getMetadata() {
        return metadata;
    }
}
