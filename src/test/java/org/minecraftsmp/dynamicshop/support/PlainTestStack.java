package org.minecraftsmp.dynamicshop.support;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

/** Plain stacks without a running server's item-meta factory. */
public final class PlainTestStack extends ItemStack {
    private final Material material;
    private int amount;
    public PlainTestStack(Material material, int amount) { super(); this.material = material; this.amount = amount; }
    @Override public Material getType() { return material; }
    @Override public int getAmount() { return amount; }
    @Override public void setAmount(int amount) { this.amount = amount; }
    @Override public PlainTestStack clone() { return new PlainTestStack(getType(), getAmount()); }
    @Override public boolean hasItemMeta() { return false; }
    @Override public int getMaxStackSize() { return 64; }
    @Override public boolean isSimilar(ItemStack other) { return other != null && other.getType() == getType(); }
}
