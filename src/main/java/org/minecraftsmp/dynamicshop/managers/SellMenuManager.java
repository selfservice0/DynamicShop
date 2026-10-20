package org.minecraftsmp.dynamicshop.managers;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryCreativeEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import org.minecraftsmp.dynamicshop.DynamicShop;
import org.minecraftsmp.dynamicshop.util.PaperCompat;
import org.minecraftsmp.dynamicshop.util.ShopItemBuilder;

/** A real deposit inventory, with price lore added only to outgoing display packets. */
public final class SellMenuManager implements Listener {
    static final int ACCEPT = 47, TOTAL = 49, CANCEL = 51;
    private final DynamicShop plugin;
    private final SellBasketService sales;
    private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();
    private final SellMenuPackets.Sender packets;
    private BukkitTask refreshTask;
    private boolean shuttingDown;

    public SellMenuManager(DynamicShop plugin) {
        this(plugin, SellMenuPackets::send);
    }

    SellMenuManager(DynamicShop plugin, SellMenuPackets.Sender packets) {
        this.plugin = plugin;
        this.packets = packets;
        sales = new SellBasketService(plugin);
    }

    public void start() {
        refreshTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Session session : List.copyOf(sessions.values())) {
                if (!allowed(session.owner)) {
                    session.owner.closeInventory();
                    close(session);
                } else if (!session.processing) refresh(session);
            }
        }, 20L, 20L);
    }

    private boolean allowed(Player player) {
        return plugin.getConfig().getBoolean("gui.sell_menu_enabled", true)
                && player.hasPermission("dynamicshop.use") && player.hasPermission("dynamicshop.use.sellgui");
    }

    public void open(Player player) {
        if (!allowed(player)) {
            player.sendMessage(text("sell-gui-unavailable", "&cThe sell menu is disabled or you do not have permission to use it.", Map.of()));
            return;
        }
        player.closeInventory();
        Session previous = sessions.get(player.getUniqueId());
        if (previous != null) close(previous);
        Session session = new Session(player);
        sessions.put(player.getUniqueId(), session);
        refresh(session);
        player.openInventory(session.inventory);
        sendDisplaysLater(session);
    }

    private String text(String key, String fallback, Map<String, String> values) {
        String result = plugin.getMessageManager().getMessage(key, values, fallback);
        return result == null ? "" : result;
    }

    private void refresh(Session session) {
        if (session.closed) return;
        session.quote = sales.quote(session.owner, session.inventory);
        Map<Integer, Display> displays = new java.util.HashMap<>();
        addDisplays(displays, session.quote, false);
        // Bottom-inventory prices are previews; only the top deposit slots form the sale.
        addDisplays(displays, sales.quote(session.owner, session.owner.getInventory(), 36), true);
        session.displays = Map.copyOf(displays);
        for (int slot = SellBasketService.INPUT_SLOTS; slot < 54; slot++)
            session.inventory.setItem(slot, org.minecraftsmp.dynamicshop.util.GuiTheme.enabled(plugin, session.owner)
                    ? new ItemStack(Material.AIR) : ShopItemBuilder.navItem(" ", Material.GRAY_STAINED_GLASS_PANE));
        session.inventory.setItem(ACCEPT, org.minecraftsmp.dynamicshop.util.GuiTheme.button(plugin, session.owner,
                text("sell-gui-accept", "&a&lConfirm sale", Map.of()), "moonstone_confirm_button", Material.LIME_CONCRETE,
                text("sell-gui-accept-lore", "&7Sell the items placed above.", Map.of())));
        session.inventory.setItem(CANCEL, org.minecraftsmp.dynamicshop.util.GuiTheme.button(plugin, session.owner,
                text("sell-gui-cancel", "&c&lCancel", Map.of()), "moonstone_cancel_button", Material.RED_CONCRETE,
                text("sell-gui-cancel-lore", "&7Return your items and close.", Map.of())));
        List<String> totals = new ArrayList<>();
        totals.add(text("sell-gui-total-count", "&7Items to sell: &f{count}", Map.of("count", String.valueOf(session.quote.accepted()))));
        for (var total : session.quote.totals().entrySet()) totals.add("§a" + plugin.getEconomyManager().format(total.getValue(), total.getKey()));
        if (session.quote.totals().isEmpty()) totals.add(text("sell-gui-empty", "&7Place items in the empty slots above.", Map.of()));
        totals.add(text("sell-gui-tax-note", "&7Prices include sell tax.", Map.of()));
        totals.add(text("sell-gui-return-note", "&7Unsold items will be returned.", Map.of()));
        session.inventory.setItem(TOTAL, org.minecraftsmp.dynamicshop.util.GuiTheme.button(plugin, session.owner,
                text("sell-gui-total", "&e&lTotal worth", Map.of()), "moonstone_total_button", Material.PAPER, totals.toArray(String[]::new)));
        if (session.owner.getOpenInventory().getTopInventory() == session.inventory) {
            session.owner.updateInventory();
            sendDisplaysLater(session);
        }
    }

    private void addDisplays(Map<Integer, Display> displays, SellBasketService.Quote quote, boolean preview) {
        for (var line : quote.lines()) {
            ItemStack shown = line.original().clone();
            var meta = shown.getItemMeta();
            if (meta != null) {
                var lore = PaperCompat.getLore(meta);
                List<net.kyori.adventure.text.Component> updated = new ArrayList<>(lore == null ? List.of() : lore);
                updated.add(MessageManager.parseComponent("§7────────────────────"));
                double perItem = line.accepted() > 0 ? line.value() / line.accepted() : 0;
                updated.add(MessageManager.parseComponent(text("sell-gui-item-value", "&7Per item: &f{price}",
                        Map.of("price", plugin.getEconomyManager().format(perItem, line.currency())))));
                updated.add(MessageManager.parseComponent(text("sell-gui-stack-value", "&aStack value: &f{price}",
                        Map.of("price", plugin.getEconomyManager().format(line.value(), line.currency())))));
                updated.add(MessageManager.parseComponent(text("sell-gui-stack-count", "&7Sellable: &f{count}/{amount}",
                        Map.of("count", String.valueOf(line.accepted()), "amount", String.valueOf(line.original().getAmount())))));
                if (!line.reason().isEmpty()) updated.add(MessageManager.parseComponent("§c" + line.reason()));
                if (preview) updated.add(MessageManager.parseComponent(text("sell-gui-preview", "&7Place this item above to sell it.", Map.of())));
                PaperCompat.setLore(meta, updated);
                shown.setItemMeta(meta);
            }
            int slot = preview ? line.slot() < 9 ? 81 + line.slot() : 54 + line.slot() - 9 : line.slot();
            displays.put(slot, new Display(line.original(), shown));
        }
    }

    private void sendDisplaysLater(Session session) {
        if (session.displayPending) return;
        session.displayPending = true;
        Bukkit.getScheduler().runTask(plugin, () -> {
            session.displayPending = false;
            sendDisplays(session);
        });
    }

    private void sendDisplays(Session session) {
        if (session.closed || session.owner.getOpenInventory().getTopInventory() != session.inventory) return;
        try {
            Map<Integer, ItemStack> shownItems = new java.util.HashMap<>();
            for (var entry : session.displays.entrySet()) {
                int slot = entry.getKey();
                ItemStack raw = slot < 54 ? session.inventory.getItem(slot)
                        : session.owner.getInventory().getItem(slot < 81 ? slot - 54 + 9 : slot - 81);
                ItemStack shown = session.display(slot, raw);
                if (shown == raw) continue;
                shownItems.put(slot, shown);
            }
            if (!shownItems.isEmpty()) packets.send(session.owner, shownItems);
        } catch (ReflectiveOperationException | RuntimeException ex) {
            if (!session.displayWarning) {
                session.displayWarning = true;
                plugin.getLogger().warning("Could not send sell-menu price previews: " + ex.getMessage());
            }
        }
    }

    private Session session(Inventory inventory) {
        if (!(inventory.getHolder() instanceof Session candidate)) return null;
        return sessions.get(candidate.owner.getUniqueId()) == candidate ? candidate : null;
    }

    @EventHandler(ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        Session session = session(event.getView().getTopInventory());
        if (session == null) return;
        if (!event.getWhoClicked().getUniqueId().equals(session.owner.getUniqueId()) || session.processing
                || event instanceof InventoryCreativeEvent || event.getAction() == InventoryAction.COLLECT_TO_CURSOR
                || event.getAction() == InventoryAction.CLONE_STACK) {
            event.setCancelled(true);
            return;
        }
        int slot = event.getRawSlot();
        if (slot >= SellBasketService.INPUT_SLOTS && slot < 54) {
            event.setCancelled(true);
            if (slot != ACCEPT && slot != CANCEL) return;
            session.processing = true;
            var approved = session.quote;
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (session.closed) return;
                if (slot == CANCEL) {
                    session.owner.closeInventory();
                    close(session);
                    return;
                }
                var result = sales.sell(session.owner, session.inventory, approved);
                session.processing = false;
                if (result.status() == SellBasketService.Status.SOLD) {
                    if (result.paidItems() > 0) {
                        String amounts = result.paidTotals().entrySet().stream()
                                .map(total -> plugin.getEconomyManager().format(total.getValue(), total.getKey()))
                                .collect(java.util.stream.Collectors.joining(", "));
                        session.owner.sendMessage(text("sell-gui-sold", "&aSold &f{count} &aitems for &f{price}&a.",
                                Map.of("count", String.valueOf(result.paidItems()), "price", amounts)));
                    }
                    refresh(session);
                } else if (result.status() == SellBasketService.Status.DENIED) {
                    session.owner.closeInventory();
                    close(session);
                } else {
                    if (result.status() == SellBasketService.Status.CHANGED)
                        session.owner.sendMessage(text("sell-gui-changed", "&ePrices or items changed. Review the updated total and confirm again.", Map.of()));
                    if (result.status() == SellBasketService.Status.EMPTY)
                        session.owner.sendMessage(text("sell-gui-nothing", "&cThere are no sellable items in this menu.", Map.of()));
                    refresh(session);
                }
            });
        } else if (slot >= 54 && event.isShiftClick()) {
            event.setCancelled(true);
            transferIntoMenu(session, event.getSlot());
            refreshLater(session);
        } else refreshLater(session);
    }

    private void transferIntoMenu(Session session, int playerSlot) {
        if (playerSlot < 0 || playerSlot >= 36) return;
        ItemStack source = session.owner.getInventory().getItem(playerSlot);
        if (source == null || source.getType() == Material.AIR) return;
        int remaining = source.getAmount();
        for (int pass = 0; pass < 2; pass++) for (int slot = 0; slot < SellBasketService.INPUT_SLOTS && remaining > 0; slot++) {
            ItemStack existing = session.inventory.getItem(slot);
            boolean empty = existing == null || existing.getType() == Material.AIR;
            if ((pass == 0 && empty) || (pass == 1 && !empty) || (!empty && !existing.isSimilar(source))) continue;
            int current = empty ? 0 : existing.getAmount();
            int moved = Math.min(remaining, Math.max(0, source.getMaxStackSize() - current));
            if (moved == 0) continue;
            ItemStack placed = source.clone();
            placed.setAmount(current + moved);
            session.inventory.setItem(slot, placed);
            remaining -= moved;
        }
        ItemStack leftover = source.clone();
        leftover.setAmount(remaining);
        session.owner.getInventory().setItem(playerSlot, remaining == 0 ? null : leftover);
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        Session session = session(event.getView().getTopInventory());
        if (session == null) return;
        if (session.processing || !event.getWhoClicked().getUniqueId().equals(session.owner.getUniqueId())
                || event.getRawSlots().stream().anyMatch(slot -> slot >= SellBasketService.INPUT_SLOTS && slot < 54)) event.setCancelled(true);
        else refreshLater(session);
    }

    private void refreshLater(Session session) {
        if (session.refreshPending) return;
        session.refreshPending = true;
        Bukkit.getScheduler().runTask(plugin, () -> {
            session.refreshPending = false;
            if (!session.closed && !session.processing) refresh(session);
        });
    }

    @EventHandler public void onClose(InventoryCloseEvent event) {
        Session session = session(event.getInventory());
        if (session != null && event.getPlayer().getUniqueId().equals(session.owner.getUniqueId())) close(session);
    }
    @EventHandler public void onQuit(PlayerQuitEvent event) {
        Session session = sessions.get(event.getPlayer().getUniqueId());
        if (session != null) close(session);
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true) public void onDeath(PlayerDeathEvent event) {
        // Paper deaths can be cancelled; the surviving player still owns the deposited items.
        if (event instanceof Cancellable && ((Cancellable) event).isCancelled()) return;
        Session session = sessions.get(event.getEntity().getUniqueId());
        if (session == null) return;
        if (event.getKeepInventory()) close(session);
        else event.getDrops().addAll(drain(session));
    }

    private List<ItemStack> drain(Session session) {
        if (session.closed) return List.of();
        session.closed = true;
        sessions.remove(session.owner.getUniqueId(), session);
        session.displays = Map.of();
        List<ItemStack> items = new ArrayList<>();
        for (int slot = 0; slot < SellBasketService.INPUT_SLOTS; slot++) {
            ItemStack item = session.inventory.getItem(slot);
            if (item != null && item.getType() != Material.AIR) items.add(item.clone());
            session.inventory.setItem(slot, null);
        }
        return items;
    }

    private void close(Session session) {
        for (ItemStack item : drain(session)) {
            var leftovers = session.owner.getInventory().addItem(item);
            for (ItemStack leftover : leftovers.values()) {
                var drop = session.owner.getWorld().dropItemNaturally(session.owner.getLocation(), leftover);
                drop.setOwner(session.owner.getUniqueId());
            }
        }
        if (!shuttingDown) Bukkit.getScheduler().runTask(plugin, session.owner::updateInventory);
    }

    public void shutdown() {
        shuttingDown = true;
        if (refreshTask != null) refreshTask.cancel();
        for (Session session : List.copyOf(sessions.values())) {
            close(session);
            if (session.owner.getOpenInventory().getTopInventory() == session.inventory) session.owner.closeInventory();
        }
    }

    private record Display(ItemStack original, ItemStack shown) {}
    private final class Session implements InventoryHolder {
        final Player owner;
        final Inventory inventory;
        SellBasketService.Quote quote;
        volatile Map<Integer, Display> displays = Map.of();
        volatile boolean closed;
        boolean processing;
        boolean refreshPending;
        boolean displayPending;
        boolean displayWarning;
        Session(Player player) {
            owner = player;
            inventory = PaperCompat.createInventory(this, 54,
                    MessageManager.parseComponent(org.minecraftsmp.dynamicshop.util.GuiTheme.title(plugin, player,
                            "sell-gui-title", "&8Sell Items", 54, Map.of()), player));
        }
        @Override public Inventory getInventory() { return inventory; }
        ItemStack display(int slot, ItemStack raw) {
            Display cached = displays.get(slot);
            if (raw == null || cached == null || raw.getAmount() != cached.original.getAmount()
                    || !raw.isSimilar(cached.original) || !cached.original.isSimilar(raw)) return raw;
            return cached.shown.clone();
        }
    }
}
