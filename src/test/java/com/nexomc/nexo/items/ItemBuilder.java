package com.nexomc.nexo.items;

import org.bukkit.inventory.ItemStack;

/** Test-only API stand-in with a cached stack to exercise defensive cloning. */
public final class ItemBuilder {
    private final ItemStack template;
    public ItemBuilder(ItemStack template) { this.template = template; }
    public ItemStack build() { return template; }
}
