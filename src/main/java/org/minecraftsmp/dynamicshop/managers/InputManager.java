package org.minecraftsmp.dynamicshop.managers;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.minecraftsmp.dynamicshop.DynamicShop;

import java.util.Collections;
import java.util.function.Consumer;

/**
 * Unified input system with 3-layer fallback:
 * 1. Paper Dialog API (1.21.7+)
 * 2. AnvilGUI (if available)
 * 3. Chat input
 */
public class InputManager {

    private final DynamicShop plugin;
    private final boolean dialogsAvailable;
    private final boolean anvilGuiAvailable;

    public InputManager(DynamicShop plugin) {
        this.plugin = plugin;
        this.dialogsAvailable = checkDialogSupport();
        this.anvilGuiAvailable = checkAnvilGuiSupport();

        if (dialogsAvailable) {
            plugin.getLogger().info("Paper Dialog API available - using modern dialogs for input");
        } else if (anvilGuiAvailable) {
            plugin.getLogger().info("AnvilGUI available - using anvil for input");
        } else {
            plugin.getLogger().info("Using chat input fallback");
        }
    }

    /**
     * Check if Paper Dialog API is available (Paper 1.21.7+)
     */
    private boolean checkDialogSupport() {
        try {
            Class.forName("io.papermc.paper.dialog.Dialog");
            Class.forName("io.papermc.paper.registry.data.dialog.DialogBase");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    /**
     * Check if AnvilGUI is available and compatible
     */
    private boolean checkAnvilGuiSupport() {
        try {
            // Just check if the class file exists, don't initialize it
            Class.forName("net.wesjd.anvilgui.AnvilGUI", false, getClass().getClassLoader());
            return true;
        } catch (Throwable e) {
            // Catch ALL errors including static initialization failures
            return false;
        }
    }

    /**
     * Request text input from a player
     * Config option "input-method" can force: "dialog", "anvil", or "chat"
     */
    public void requestText(Player player, String title, String currentValue, Consumer<String> callback) {
        String forceMethod = plugin.getConfig().getString("input-method", "auto").toLowerCase();

        if (forceMethod.equals("chat")) {
            requestTextViaChat(player, title, currentValue, callback);
        } else if (forceMethod.equals("anvil")) {
            if (anvilGuiAvailable) {
                requestTextViaAnvilGui(player, title, currentValue, callback, false); // No fallback when forced
            } else {
                plugin.getLogger().warning("AnvilGUI forced but not available, falling back to chat");
                requestTextViaChat(player, title, currentValue, callback);
            }
        } else if (forceMethod.equals("dialog")) {
            if (dialogsAvailable) {
                requestTextViaDialog(player, title, currentValue, callback, false); // No fallback when forced
            } else {
                plugin.getLogger().warning("Dialog forced but not available, falling back to chat");
                requestTextViaChat(player, title, currentValue, callback);
            }
        } else {
            // Auto mode - use best available with fallback
            if (dialogsAvailable) {
                requestTextViaDialog(player, title, currentValue, callback, true);
            } else if (anvilGuiAvailable) {
                requestTextViaAnvilGui(player, title, currentValue, callback, true);
            } else {
                requestTextViaChat(player, title, currentValue, callback);
            }
        }
    }

    /**
     * Request number input from a player
     */
    public void requestNumber(Player player, String title, double currentValue, Consumer<Double> callback) {
        requestText(player, title, String.valueOf(currentValue), input -> {
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
     * Request integer input from a player
     */
    public void requestInt(Player player, String title, int currentValue, Consumer<Integer> callback) {
        requestText(player, title, String.valueOf(currentValue), input -> {
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
    // DIALOG IMPLEMENTATION (Paper 1.21.7+)
    // =========================================================================

    private void requestTextViaDialog(Player player, String title, String currentValue, Consumer<String> callback,
            boolean allowFallback) {
        try {
            DialogHelper.showInputDialog(plugin, player, title, currentValue, callback);
        } catch (LinkageError | Exception e) {
            plugin.getLogger().warning("Dialog failed: " + e.getMessage());
            if (allowFallback) {
                plugin.getLogger().info("Falling back to next available input method");
                if (anvilGuiAvailable) {
                    requestTextViaAnvilGui(player, title, currentValue, callback, true);
                } else {
                    requestTextViaChat(player, title, currentValue, callback);
                }
            } else {
                player.sendMessage(
                        "§c[DynamicShop] Dialog input failed. Try setting input-method to 'chat' in config.");
                callback.accept(null);
            }
        }
    }

    // =========================================================================
    // ANVILGUI IMPLEMENTATION
    // =========================================================================

    private void requestTextViaAnvilGui(Player player, String title, String currentValue, Consumer<String> callback,
            boolean allowFallback) {
        try {
            new net.wesjd.anvilgui.AnvilGUI.Builder()
                    .plugin(plugin)
                    .title(title)
                    .text(currentValue != null && !currentValue.isEmpty() ? currentValue : " ")
                    .itemLeft(new ItemStack(Material.PAPER))
                    .onClick((slot, stateSnapshot) -> {
                        if (slot == net.wesjd.anvilgui.AnvilGUI.Slot.OUTPUT) {
                            String text = stateSnapshot.getText();
                            callback.accept(text);
                            return Collections.singletonList(net.wesjd.anvilgui.AnvilGUI.ResponseAction.close());
                        }
                        return Collections.emptyList();
                    })
                    .onClose(stateSnapshot -> {
                        // Callback with null if closed without submitting
                    })
                    .open(player);
        } catch (Throwable e) {
            plugin.getLogger().warning("AnvilGUI failed: " + e.getMessage());
            if (allowFallback) {
                plugin.getLogger().info("Falling back to chat input");
                requestTextViaChat(player, title, currentValue, callback);
            } else {
                player.sendMessage(
                        "§c[DynamicShop] AnvilGUI input failed. Try setting input-method to 'chat' in config.");
                callback.accept(null);
            }
        }
    }

    // =========================================================================
    // CHAT FALLBACK IMPLEMENTATION
    // =========================================================================

    private void requestTextViaChat(Player player, String title, String currentValue, Consumer<String> callback) {
        plugin.getChatInputListener().requestInput(player, title,
                "Current: " + currentValue + " | Type 'cancel' to cancel",
                callback);
    }

    public boolean isDialogsAvailable() {
        return dialogsAvailable;
    }

    public boolean isAnvilGuiAvailable() {
        return anvilGuiAvailable;
    }
}
