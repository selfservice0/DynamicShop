package org.minecraftsmp.dynamicshop.managers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.StringJoiner;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.minecraftsmp.dynamicshop.DynamicShop;
import org.minecraftsmp.dynamicshop.transactions.Transaction;
import org.minecraftsmp.dynamicshop.util.ShopItemNames;

/** Quotes and sells only items placed in the deposit area, leaving their metadata untouched. */
public final class SellBasketService {
    public static final int INPUT_SLOTS = 45;
    private final DynamicShop plugin;

    public SellBasketService(DynamicShop plugin) { this.plugin = plugin; }

    public record Target(Material material, String variantId, String name, double basePrice, String category) {
        public String key() { return variantId == null ? material.name() : "VARIANT:" + variantId; }
    }
    public record Line(int slot, ItemStack original, Target target, int accepted, double value, String currency, String reason) {}
    public record Group(Target target, int amount, double value, String currency) {}
    public record Quote(List<Line> lines, List<Group> groups, Map<String, Double> totals, int accepted, long pricedAt) {
        public boolean matches(Quote other) {
            if (other == null || lines.size() != other.lines.size()) return false;
            for (int i = 0; i < lines.size(); i++) {
                Line a = lines.get(i), b = other.lines.get(i);
                if (a.slot != b.slot || a.accepted != b.accepted || a.original.getAmount() != b.original.getAmount()
                        || !a.original.isSimilar(b.original) || !b.original.isSimilar(a.original)
                        || Double.compare(a.value, b.value) != 0 || !Objects.equals(a.currency, b.currency)
                        || !Objects.equals(a.reason, b.reason)
                        || !Objects.equals(a.target == null ? null : a.target.key(), b.target == null ? null : b.target.key())) return false;
            }
            return true;
        }
    }
    public enum Status { SOLD, CHANGED, EMPTY, DENIED, COOLDOWN }
    public record Result(Status status, int paidItems, Map<String, Double> paidTotals) {}

    public Quote quote(Player player, Inventory inventory) {
        return quote(player, inventory, INPUT_SLOTS);
    }

    Quote quote(Player player, Inventory inventory, int slots) {
        return quote(player, inventory, slots, System.currentTimeMillis());
    }

    Quote quote(Player player, Inventory inventory, int slots, long pricedAt) {
        Map<String, List<Line>> candidates = new LinkedHashMap<>();
        List<Line> lines = new ArrayList<>();
        for (int slot = 0; slot < slots; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack == null || stack.getType() == Material.AIR || stack.getAmount() <= 0) continue;
            Target target = target(player, stack);
            Line line = new Line(slot, stack.clone(), target, 0, 0, null, "This item cannot be sold");
            if (target == null) lines.add(line);
            else candidates.computeIfAbsent(target.key(), key -> new ArrayList<>()).add(line);
        }
        List<Group> groups = new ArrayList<>();
        Map<String, Double> totals = new LinkedHashMap<>();
        int accepted = 0;
        for (List<Line> stacks : candidates.values()) {
            Target target = stacks.getFirst().target;
            long requested = stacks.stream().mapToLong(line -> line.original.getAmount()).sum();
            int limit = target.variantId == null ? ShopDataManager.getSellLimit(target.material)
                    : ShopDataManager.getVariantSellLimit(target.variantId, target.material);
            int amount = (int) Math.min(Math.min(requested, Integer.MAX_VALUE), Math.max(0, limit));
            String currency = plugin.getEconomyManager().getCurrency(target.material);
            double value = amount == 0 ? 0 : value(target, amount, pricedAt);
            boolean valid = Double.isFinite(value) && value >= 0
                    && Double.isFinite(totals.getOrDefault(currency, 0.0) + value);
            int remaining = valid ? amount : 0;
            int cumulative = 0;
            double previous = 0;
            List<Line> allocations = new ArrayList<>();
            for (Line line : stacks) {
                int take = Math.min(remaining, line.original.getAmount());
                cumulative += take;
                double next = cumulative == 0 ? 0 : value(target, cumulative, pricedAt);
                double stackValue = next - previous;
                if (!Double.isFinite(stackValue) || stackValue < 0) valid = false;
                allocations.add(new Line(line.slot, line.original, target, take, stackValue, currency,
                        take < line.original.getAmount() ? "Shop storage limit; leftovers will be returned" : ""));
                previous = next;
                remaining -= take;
            }
            if (!valid) {
                for (Line line : stacks) lines.add(new Line(line.slot, line.original, target, 0, 0, currency, "Sell price unavailable"));
            } else {
                lines.addAll(allocations);
                if (amount > 0) {
                    groups.add(new Group(target, amount, value, currency));
                    totals.merge(currency, value, Double::sum);
                    accepted += amount;
                }
            }
        }
        lines.sort(java.util.Comparator.comparingInt(Line::slot));
        return new Quote(List.copyOf(lines), List.copyOf(groups), Collections.unmodifiableMap(totals), accepted, pricedAt);
    }

    private Target target(Player player, ItemStack stack) {
        if (stack.hasItemMeta() && stack.getItemMeta() instanceof Damageable damage && damage.hasDamage()) return null;
        var specials = plugin.getSpecialShopManager();
        var custom = specials == null ? null : specials.findTradeItem(stack);
        if (custom != null) {
            if (!specials.canTrade(player, custom.getId(), false)) return null;
            return new Target(stack.getType(), custom.getId(), specials.getDisplayName(custom), custom.getPrice(), custom.getCategory().name());
        }
        Material material = stack.getType();
        if (ShopDataManager.getBasePrice(material) < 0 || ShopDataManager.isItemDisabled(material)
                || ShopDataManager.isSellDisabled(material)) return null;
        ItemStack template = ShopDataManager.getTemplate(material);
        if (template == null) template = new ItemStack(material);
        ItemStack single = stack.clone();
        single.setAmount(1);
        template.setAmount(1);
        if (!single.isSimilar(template) || !template.isSimilar(single)) return null;
        return new Target(material, null, ShopItemNames.getDisplayName(material), ShopDataManager.getBasePrice(material), ShopDataManager.detectCategory(material).name());
    }

    private double value(Target target, int amount, long pricedAt) {
        return target.variantId == null ? ShopDataManager.getTotalSellValue(target.material, amount, pricedAt)
                : ShopDataManager.getTotalVariantSellValue(target.variantId, target.material, target.basePrice, amount, pricedAt);
    }

    public Result sell(Player player, Inventory inventory, Quote approved) {
        if (!plugin.getConfig().getBoolean("gui.sell_menu_enabled", true)
                || !player.hasPermission("dynamicshop.use") || !player.hasPermission("dynamicshop.use.sellgui"))
            return new Result(Status.DENIED, 0, Map.of());
        if (!plugin.getShopListener().checkTransactionCooldown(player)) return new Result(Status.COOLDOWN, 0, Map.of());
        if (approved == null) return new Result(Status.CHANGED, 0, Map.of());
        // Recheck live items, stock, permissions and settings at the displayed quote's time.
        // Inflation/decay advancing between display and click must not invalidate every sale.
        Quote fresh = quote(player, inventory, INPUT_SLOTS, approved.pricedAt);
        if (!fresh.matches(approved)) return new Result(Status.CHANGED, 0, Map.of());
        if (fresh.accepted == 0) return new Result(Status.EMPTY, 0, Map.of());

        // Finish consuming the batch before calling providers: uncertain payments must not be retried.
        for (Line line : fresh.lines) {
            if (line.accepted == 0) continue;
            ItemStack remaining = line.original.clone();
            remaining.setAmount(line.original.getAmount() - line.accepted);
            inventory.setItem(line.slot, remaining.getAmount() == 0 ? null : remaining);
        }
        for (Group group : fresh.groups) {
            if (group.target.variantId == null) ShopDataManager.updateStock(group.target.material, group.amount);
            else ShopDataManager.updateVariantStock(group.target.variantId, group.amount);
        }
        plugin.getShopListener().recordTransaction(player);
        Map<String, Double> paid = new LinkedHashMap<>();
        int paidItems = 0;
        for (var total : fresh.totals.entrySet()) {
            List<Group> currencyGroups = fresh.groups.stream().filter(group -> Objects.equals(group.currency, total.getKey())).toList();
            StringJoiner description = new StringJoiner(", ");
            for (Group group : currencyGroups) description.add(group.amount + "x " + group.target.name + " [" + group.target.key() + "]");
            if (!plugin.getEconomyManager().depositSale(player, total.getValue(), total.getKey(), description + " [sell menu]")) continue;
            paid.put(total.getKey(), total.getValue());
            for (Group group : currencyGroups) {
                paidItems += group.amount;
                plugin.getTransactionLogger().log(Transaction.now(player.getName(), Transaction.TransactionType.SELL,
                        group.target.key(), group.amount, group.value, group.target.category, "sell-menu"));
            }
        }
        return new Result(Status.SOLD, paidItems, Collections.unmodifiableMap(paid));
    }
}
