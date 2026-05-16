package org.minecraftsmp.dynamicshop.gui;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.minecraftsmp.dynamicshop.DynamicShop;
import org.minecraftsmp.dynamicshop.managers.ShopDataManager;

import java.util.List;

/**
 * Manages the Paper Dialog API integration for buy/sell item interactions.
 * Shows a dialog with the item preview, a quantity slider, and Buy/Sell/Return buttons.
 */
public class ShopDialogManager {

    private final DynamicShop plugin;

    public ShopDialogManager(DynamicShop plugin) {
        this.plugin = plugin;
    }

    /**
     * Opens a buy/sell dialog for the specified material.
     *
     * @param player The player to show the dialog to
     * @param mat    The material being transacted
     * @param gui    The ShopGUI to return to (can be ShopGUI or SearchResultsGUI)
     */
    public void openDialog(Player player, Material mat, Object gui) {
        openDialog(player, mat, gui, null, -1, null);
    }

    public void openDialog(Player player, Material mat, Object gui, ItemStack deliveryOverride, double variantBasePrice) {
        openDialog(player, mat, gui, deliveryOverride, variantBasePrice, null);
    }

    public void openDialog(Player player, Material mat, Object gui, ItemStack deliveryOverride, double variantBasePrice, String variantId) {
        // Use delivery override or template if available so the dialog shows the item with components
        ItemStack displayItem;
        if (deliveryOverride != null) {
            displayItem = deliveryOverride.clone();
            displayItem.setAmount(1);
        } else {
            org.bukkit.inventory.ItemStack template = ShopDataManager.getTemplate(mat);
            displayItem = template != null ? template.clone() : new ItemStack(mat);
            if (template != null) displayItem.setAmount(1);
        }
        String itemName = formatMaterialName(mat);

        double buyPrice1 = variantId != null && variantBasePrice > 0
                ? ShopDataManager.getTotalVariantBuyCost(variantId, mat, variantBasePrice, 1)
                : ShopDataManager.getTotalBuyCost(mat, 1);
        double sellPrice1 = variantId != null && variantBasePrice > 0
                ? ShopDataManager.getTotalVariantSellValue(variantId, mat, variantBasePrice, 1)
                : ShopDataManager.getTotalSellValue(mat, 1);

        java.util.Map<String, String> placeholders = new java.util.HashMap<>();
        placeholders.put("item", itemName);
        placeholders.put("price", plugin.getEconomyManager().format(buyPrice1));
        
        java.util.Map<String, String> sellPlaceholders = new java.util.HashMap<>();
        sellPlaceholders.put("price", plugin.getEconomyManager().format(sellPrice1));

        Component titleComp = org.minecraftsmp.dynamicshop.managers.MessageManager.parseComponent(
                plugin.getMessageManager().getMessage("dialog-title", placeholders), player);
                
        Component buyPriceComp = org.minecraftsmp.dynamicshop.managers.MessageManager.parseComponent(
                plugin.getMessageManager().getMessage("dialog-buy-price", placeholders), player);
                
        Component sellPriceComp = org.minecraftsmp.dynamicshop.managers.MessageManager.parseComponent(
                plugin.getMessageManager().getMessage("dialog-sell-price", sellPlaceholders), player);

        Component priceWarningComp = org.minecraftsmp.dynamicshop.managers.MessageManager.parseComponent(
                plugin.getMessageManager().getMessage("dialog-price-warning"), player);
                
        Component qtyLabel = org.minecraftsmp.dynamicshop.managers.MessageManager.parseComponent(
                plugin.getMessageManager().getMessage("dialog-quantity"), player);
                
        Component buyBtn = org.minecraftsmp.dynamicshop.managers.MessageManager.parseComponent(
                plugin.getMessageManager().getMessage("dialog-buy-button"), player);
        Component buyDesc = org.minecraftsmp.dynamicshop.managers.MessageManager.parseComponent(
                plugin.getMessageManager().getMessage("dialog-buy-desc"), player);
                
        Component sellBtn = org.minecraftsmp.dynamicshop.managers.MessageManager.parseComponent(
                plugin.getMessageManager().getMessage("dialog-sell-button"), player);
        Component sellDesc = org.minecraftsmp.dynamicshop.managers.MessageManager.parseComponent(
                plugin.getMessageManager().getMessage("dialog-sell-desc"), player);
                
        Component sellAllLabel = org.minecraftsmp.dynamicshop.managers.MessageManager.parseComponent(
                plugin.getMessageManager().getMessage("dialog-sell-all-label"), player);
                
        Component returnBtn = org.minecraftsmp.dynamicshop.managers.MessageManager.parseComponent(
                plugin.getMessageManager().getMessage("dialog-return-button"), player);
        Component returnDesc = org.minecraftsmp.dynamicshop.managers.MessageManager.parseComponent(
                plugin.getMessageManager().getMessage("dialog-return-desc"), player);

        // Combine buy and sell price on adjacent lines for tighter spacing
        Component pricesComp = buyPriceComp
                .append(Component.text("\n"))
                .append(sellPriceComp);

        // Build the dialog dynamically
        Dialog dialog = Dialog.create(builder -> builder.empty()
                .base(DialogBase.builder(titleComp)
                        .canCloseWithEscape(true)
                        .body(List.of(
                                DialogBody.plainMessage(Component.text("\n"), 10), // Small padding
                                DialogBody.item(displayItem)
                                        .showDecorations(true)
                                        .showTooltip(true)
                                        .build(),
                                DialogBody.plainMessage(pricesComp, 300),
                                DialogBody.plainMessage(priceWarningComp, 300)
                        ))
                        .inputs(List.of(
                                DialogInput.numberRange("quantity", qtyLabel, 0f, 128f)
                                        .step(1f)
                                        .initial(0f)
                                        .width(300)
                                        .labelFormat("%s: %s")
                                        .build(),
                                DialogInput.bool("sell_all", sellAllLabel).build()
                        ))
                        .build()
                )
                .type(DialogType.multiAction(List.of(
                        // BUY button
                        ActionButton.create(
                                buyBtn,
                                buyDesc,
                                50,
                                DialogAction.customClick(
                                        (view, audience) -> {
                                            if (audience instanceof Player p) {
                                                Float qtyFloat = view.getFloat("quantity");
                                                int qty = qtyFloat != null ? qtyFloat.intValue() : 0;
                                                
                                                if (qty <= 0) {
                                                    p.sendMessage(org.minecraftsmp.dynamicshop.managers.MessageManager.parseComponent(
                                                            plugin.getMessageManager().getMessage("dialog-error-no-qty-buy"), p));
                                                    return;
                                                }
                                                
                                                plugin.getShopListener().buyItem(p, mat, qty, gui, deliveryOverride, variantBasePrice, variantId);
                                            }
                                        },
                                        ClickCallback.Options.builder().uses(ClickCallback.UNLIMITED_USES).lifetime(java.time.Duration.ofMinutes(5)).build()
                                )
                        ),
                        // SELL button
                        ActionButton.create(
                                sellBtn,
                                sellDesc,
                                50,
                                DialogAction.customClick(
                                        (view, audience) -> {
                                            if (audience instanceof Player p) {
                                                Boolean sellAll = view.getBoolean("sell_all");
                                                if (sellAll != null && sellAll) {
                                                    // Sell all logic
                                                    int totalItems = 0;
                                                    for (ItemStack invItem : p.getInventory().getContents()) {
                                                        if (isSellMatch(invItem, mat, deliveryOverride)) {
                                                            totalItems += invItem.getAmount();
                                                        }
                                                    }
                                                    if (totalItems > 0) {
                                                        plugin.getShopListener().sellItem(p, mat, totalItems, gui, deliveryOverride, variantBasePrice, variantId);
                                                    } else {
                                                        p.sendMessage(org.minecraftsmp.dynamicshop.managers.MessageManager.parseComponent(
                                                                plugin.getMessageManager().getMessage("dialog-error-no-items-sell"), p));
                                                    }
                                                    return;
                                                }
                                                
                                                Float qtyFloat = view.getFloat("quantity");
                                                int qty = qtyFloat != null ? qtyFloat.intValue() : 0;
                                                
                                                if (qty <= 0) {
                                                    p.sendMessage(org.minecraftsmp.dynamicshop.managers.MessageManager.parseComponent(
                                                            plugin.getMessageManager().getMessage("dialog-error-no-qty-sell"), p));
                                                    return;
                                                }
                                                
                                                plugin.getShopListener().sellItem(p, mat, qty, gui, deliveryOverride, variantBasePrice, variantId);
                                            }
                                        },
                                        ClickCallback.Options.builder().uses(ClickCallback.UNLIMITED_USES).lifetime(java.time.Duration.ofMinutes(5)).build()
                                )
                        ),
                        // RETURN button
                        ActionButton.create(
                                returnBtn,
                                returnDesc,
                                50,
                                DialogAction.customClick(
                                        (view, audience) -> {
                                            if (audience instanceof Player p) {
                                                p.closeInventory();
                                                // Re-open the shop GUI
                                                if (gui instanceof org.minecraftsmp.dynamicshop.gui.ShopGUI shopGUI) {
                                                    shopGUI.open();
                                                } else if (gui instanceof org.minecraftsmp.dynamicshop.gui.SearchResultsGUI searchGUI) {
                                                    searchGUI.open();
                                                }
                                            }
                                        },
                                        ClickCallback.Options.builder().uses(ClickCallback.UNLIMITED_USES).lifetime(java.time.Duration.ofMinutes(5)).build()
                                )
                        )
                ), null, 3)));
        player.showDialog(dialog);
    }

    private String formatMaterialName(Material mat) {
        String name = mat.name().replace("_", " ").toLowerCase();
        StringBuilder sb = new StringBuilder();
        for (String word : name.split(" ")) {
            if (!word.isEmpty()) {
                sb.append(Character.toUpperCase(word.charAt(0)))
                        .append(word.substring(1)).append(" ");
            }
        }
        return sb.toString().trim();
    }

    private boolean isSellMatch(ItemStack item, Material mat, ItemStack variantTemplate) {
        if (item == null || item.getType() != mat) {
            return false;
        }
        if (variantTemplate == null) {
            return true;
        }

        ItemStack oneItem = item.clone();
        oneItem.setAmount(1);
        ItemStack oneTemplate = variantTemplate.clone();
        oneTemplate.setAmount(1);
        return oneItem.isSimilar(oneTemplate);
    }
}
