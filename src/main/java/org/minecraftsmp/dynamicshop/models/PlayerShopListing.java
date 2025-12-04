package org.minecraftsmp.dynamicshop.models;

import org.bukkit.inventory.ItemStack;
import java.util.UUID;

public class PlayerShopListing {
    private final UUID sellerId;
    private final String sellerName;
    private final ItemStack item;
    private final double price;
    private final long listedTime;
    private final String listingId;

    public PlayerShopListing(UUID sellerId, String sellerName, ItemStack item, double price) {
        this.sellerId = sellerId;
        this.sellerName = sellerName;
        this.item = item.clone();
        this.price = price;
        this.listedTime = System.currentTimeMillis();
        this.listingId = UUID.randomUUID().toString();
    }

    // Reconstruct from saved data
    public PlayerShopListing(UUID sellerId, String sellerName, ItemStack item, double price,
                             long listedTime, String listingId) {
        this.sellerId = sellerId;
        this.sellerName = sellerName;
        this.item = item.clone();
        this.price = price;
        this.listedTime = listedTime;
        this.listingId = listingId;
    }

    public UUID getSellerId() {
        return sellerId;
    }

    public String getSellerName() {
        return sellerName;
    }

    public ItemStack getItem() {
        return item.clone();
    }

    public double getPrice() {
        return price;
    }

    public long getListedTime() {
        return listedTime;
    }

    public String getListingId() {
        return listingId;
    }
}