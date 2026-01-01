package org.minecraftsmp.dynamicshop.listeners;

import org.bukkit.Bukkit;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.event.player.PlayerQuitEvent;
import org.minecraftsmp.dynamicshop.DynamicShop;
import org.minecraftsmp.dynamicshop.category.ItemCategory;
import org.minecraftsmp.dynamicshop.gui.AdminCategoryEditGUI;
import org.minecraftsmp.dynamicshop.gui.AdminCategoryGUI;
import org.minecraftsmp.dynamicshop.managers.CategoryConfigManager;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

public class ChatInputListener implements Listener {

    private final DynamicShop plugin;

    // Generic pending inputs with callbacks
    private final Map<UUID, PendingGenericInput> pendingGenericInputs = new HashMap<>();

    // Category-specific inputs (legacy support)
    private final Map<UUID, PendingCategoryInput> pendingCategoryInputs = new HashMap<>();

    public ChatInputListener(DynamicShop plugin) {
        this.plugin = plugin;
    }

    // =========================================================================
    // GENERIC INPUT - for any purpose with callback
    // =========================================================================

    /**
     * Request generic text input from a player with a callback
     * 
     * @param player   The player to request input from
     * @param prompt   The prompt message to show
     * @param hint     Optional hint message (can be null)
     * @param callback Called with the input text (null if cancelled)
     */
    public void requestInput(Player player, String prompt, String hint, Consumer<String> callback) {
        pendingGenericInputs.put(player.getUniqueId(), new PendingGenericInput(callback));

        player.closeInventory();
        player.sendMessage("");
        player.sendMessage("§e§l━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        player.sendMessage("§b§l" + prompt);
        if (hint != null && !hint.isEmpty()) {
            player.sendMessage("§7" + hint);
        }
        player.sendMessage("§7Type §ccancel§7 to cancel");
        player.sendMessage("§e§l━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        player.sendMessage("");
    }

    /**
     * Request a number input from a player
     */
    public void requestNumberInput(Player player, String prompt, double currentValue, Consumer<Double> callback) {
        requestInput(player, prompt, "Current: " + currentValue + " | Enter a number", input -> {
            if (input == null) {
                callback.accept(null);
                return;
            }
            try {
                double value = Double.parseDouble(input);
                callback.accept(value);
            } catch (NumberFormatException e) {
                player.sendMessage("§c[DynamicShop] §fInvalid number: " + input);
                callback.accept(null);
            }
        });
    }

    /**
     * Request an integer input from a player
     */
    public void requestIntInput(Player player, String prompt, int currentValue, Consumer<Integer> callback) {
        requestInput(player, prompt, "Current: " + currentValue + " | Enter a whole number", input -> {
            if (input == null) {
                callback.accept(null);
                return;
            }
            try {
                int value = Integer.parseInt(input);
                callback.accept(value);
            } catch (NumberFormatException e) {
                player.sendMessage("§c[DynamicShop] §fInvalid number: " + input);
                callback.accept(null);
            }
        });
    }

    // =========================================================================
    // CATEGORY-SPECIFIC INPUT (for AdminCategoryEditGUI)
    // =========================================================================

    public void requestInput(Player player, InputType type, ItemCategory category, AdminCategoryGUI parentGUI) {
        pendingCategoryInputs.put(player.getUniqueId(), new PendingCategoryInput(type, category, parentGUI));

        player.closeInventory();
        player.sendMessage("");
        player.sendMessage("§e§l━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

        if (type == InputType.NAME) {
            player.sendMessage("§b§lEnter new category name:");
            player.sendMessage("§7Use §f&§7 for colors (e.g. §f&a§7 = §agreen§7)");
        } else if (type == InputType.ICON) {
            player.sendMessage("§b§lEnter material name for icon:");
            player.sendMessage("§7Example: §fDIAMOND§7, §fGRASS_BLOCK§7, §fEMERALD");
        }

        player.sendMessage("§7Type §ccancel§7 to cancel");
        player.sendMessage("§e§l━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        player.sendMessage("");
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        String input = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();

        // Check generic inputs first
        PendingGenericInput genericPending = pendingGenericInputs.remove(player.getUniqueId());
        if (genericPending != null) {
            event.setCancelled(true);

            if (input.equalsIgnoreCase("cancel")) {
                player.sendMessage("§c[DynamicShop] §fCancelled.");
                Bukkit.getScheduler().runTask(plugin, () -> genericPending.callback.accept(null));
                return;
            }

            Bukkit.getScheduler().runTask(plugin, () -> genericPending.callback.accept(input));
            return;
        }

        // Check category inputs
        PendingCategoryInput categoryPending = pendingCategoryInputs.remove(player.getUniqueId());
        if (categoryPending != null) {
            event.setCancelled(true);

            if (input.equalsIgnoreCase("cancel")) {
                player.sendMessage("§c[DynamicShop] §fCancelled.");
                reopenCategoryGUI(player, categoryPending);
                return;
            }

            processCategoryInput(player, input, categoryPending);
            reopenCategoryGUI(player, categoryPending);
        }
    }

    private void processCategoryInput(Player player, String input, PendingCategoryInput pending) {
        if (pending.type == InputType.NAME) {
            String coloredName = input.replace('&', '§');
            CategoryConfigManager.setDisplayName(pending.category, coloredName);
            player.sendMessage("§a[DynamicShop] §fName changed to: " + coloredName);
        } else if (pending.type == InputType.ICON) {
            String materialName = input.toUpperCase().replace(" ", "_");
            try {
                Material mat = Material.valueOf(materialName);
                CategoryConfigManager.setIcon(pending.category, mat);
                player.sendMessage("§a[DynamicShop] §fIcon changed to §e" + mat.name());
            } catch (IllegalArgumentException e) {
                player.sendMessage("§c[DynamicShop] §fInvalid material: " + input);
            }
        }
    }

    private void reopenCategoryGUI(Player player, PendingCategoryInput pending) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            AdminCategoryEditGUI editGUI = new AdminCategoryEditGUI(plugin, player, pending.category,
                    pending.parentGUI);
            plugin.getShopListener().registerAdminCategoryEdit(player, editGUI);
            editGUI.open();
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        pendingGenericInputs.remove(event.getPlayer().getUniqueId());
        pendingCategoryInputs.remove(event.getPlayer().getUniqueId());
    }

    public boolean hasPendingInput(Player player) {
        return pendingGenericInputs.containsKey(player.getUniqueId())
                || pendingCategoryInputs.containsKey(player.getUniqueId());
    }

    public void cancelPendingInput(Player player) {
        pendingGenericInputs.remove(player.getUniqueId());
        pendingCategoryInputs.remove(player.getUniqueId());
    }

    public enum InputType {
        NAME,
        ICON
    }

    private record PendingGenericInput(Consumer<String> callback) {
    }

    private record PendingCategoryInput(InputType type, ItemCategory category, AdminCategoryGUI parentGUI) {
    }
}
