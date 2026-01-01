package org.minecraftsmp.dynamicshop.managers;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.entity.Player;
import org.minecraftsmp.dynamicshop.DynamicShop;

import java.lang.reflect.Method;
import java.util.List;
import java.util.function.Consumer;

/**
 * Helper class for Paper Dialog API (1.21.7+).
 * implemented using Reflection to ensure backward compatibility and compilation
 * without requiring the API at compile time.
 */
public class DialogHelper {

        /**
         * Show a text input dialog to the player
         */
        public static void showInputDialog(DynamicShop plugin, Player player, String title,
                        String currentValue, Consumer<String> callback) {

                try {
                        // 1. Prepare classes
                        Class<?> dialogClass = Class.forName("io.papermc.paper.dialog.Dialog");
                        Class<?> dialogBaseClass = Class.forName("io.papermc.paper.registry.data.dialog.DialogBase");
                        Class<?> dialogInputClass = Class
                                        .forName("io.papermc.paper.registry.data.dialog.input.DialogInput");
                        Class<?> dialogTypeClass = Class
                                        .forName("io.papermc.paper.registry.data.dialog.type.DialogType");
                        Class<?> actionButtonClass = Class
                                        .forName("io.papermc.paper.registry.data.dialog.ActionButton");
                        Class<?> dialogActionClass = Class
                                        .forName("io.papermc.paper.registry.data.dialog.action.DialogAction");

                        // 2. Prepare Title Component
                        Component titleComp = Component.text(title);

                        // 3. Create DialogBase
                        // DialogBase base = DialogBase.builder(titleComp)
                        // .inputs(...)
                        // .canCloseWithEscape(true)
                        // .build();
                        Method dialogBaseBuilderMethod = dialogBaseClass.getMethod("builder", Component.class);
                        Object baseBuilder = dialogBaseBuilderMethod.invoke(null, titleComp);

                        // Create Input
                        // DialogInput input = DialogInput.text("input", Component.text("Value",
                        // Green)).initial(currentValue).width(300).build()
                        Method textInputMethod = dialogInputClass.getMethod("text", String.class, Component.class);
                        Object inputBuilder = textInputMethod.invoke(null, "input",
                                        Component.text("Value", NamedTextColor.GREEN));

                        Method initialMethod = inputBuilder.getClass().getMethod("initial", String.class);
                        inputBuilder = initialMethod.invoke(inputBuilder, currentValue);

                        Method widthMethod = inputBuilder.getClass().getMethod("width", int.class);
                        inputBuilder = widthMethod.invoke(inputBuilder, 300);

                        Method inputBuildMethod = inputBuilder.getClass().getMethod("build");
                        Object inputObj = inputBuildMethod.invoke(inputBuilder); // DialogInputObject

                        // Add input to base builder
                        Method inputsMethod = baseBuilder.getClass().getMethod("inputs", List.class);
                        baseBuilder = inputsMethod.invoke(baseBuilder, List.of(inputObj));

                        Method canCloseMethod = baseBuilder.getClass().getMethod("canCloseWithEscape", boolean.class);
                        baseBuilder = canCloseMethod.invoke(baseBuilder, true);

                        Method baseBuildMethod = baseBuilder.getClass().getMethod("build");
                        Object baseObj = baseBuildMethod.invoke(baseBuilder);

                        // 4. Create Actions
                        // callback logic

                        // Actions need DialogActionCallback
                        // DialogAction.customClick(DialogActionCallback, ClickCallback.Options)
                        // DialogActionCallback.accept(DialogResponseView response, Audience audience)

                        Class<?> dialogViewClass = Class.forName("io.papermc.paper.dialog.DialogResponseView");
                        Class<?> dialogActionCallbackClass = Class
                                        .forName("io.papermc.paper.registry.data.dialog.action.DialogActionCallback");

                        java.util.function.BiConsumer<Object, Object> confirmLogic = (view, audience) -> {
                                try {
                                        Method getStringMethod = dialogViewClass.getMethod("getText", String.class);
                                        String value = (String) getStringMethod.invoke(view, "input");
                                        if (audience instanceof Player) {
                                                callback.accept(value);
                                        }
                                } catch (Exception e) {
                                        e.printStackTrace();
                                        callback.accept(null);
                                }
                        };

                        java.util.function.BiConsumer<Object, Object> cancelLogic = (view, audience) -> {
                                if (audience instanceof Player) {
                                        callback.accept(null);
                                }
                        };

                        // Custom Click Action creation using correct signature
                        Method customClickMethod = dialogActionClass.getMethod("customClick",
                                        dialogActionCallbackClass, ClickCallback.Options.class);

                        // Options
                        ClickCallback.Options options = ClickCallback.Options.builder()
                                        .uses(1)
                                        .lifetime(ClickCallback.DEFAULT_LIFETIME)
                                        .build();

                        // Create proxy instances for DialogActionCallback functional interface
                        Object confirmCallback = java.lang.reflect.Proxy.newProxyInstance(
                                        dialogActionCallbackClass.getClassLoader(),
                                        new Class<?>[] { dialogActionCallbackClass },
                                        (proxy, method, args) -> {
                                                if ("accept".equals(method.getName())) {
                                                        confirmLogic.accept(args[0], args[1]);
                                                }
                                                return null;
                                        });

                        Object cancelCallback = java.lang.reflect.Proxy.newProxyInstance(
                                        dialogActionCallbackClass.getClassLoader(),
                                        new Class<?>[] { dialogActionCallbackClass },
                                        (proxy, method, args) -> {
                                                if ("accept".equals(method.getName())) {
                                                        cancelLogic.accept(args[0], args[1]);
                                                }
                                                return null;
                                        });

                        Object confirmAction = customClickMethod.invoke(null, confirmCallback, options);
                        Object cancelAction = customClickMethod.invoke(null, cancelCallback, options);

                        // Action Buttons
                        // ActionButton.create(Component, Component, int priority, DialogAction)
                        Method createButtonMethod = actionButtonClass.getMethod("create", Component.class,
                                        Component.class, int.class, dialogActionClass);

                        Object confirmButton = createButtonMethod.invoke(null,
                                        Component.text("Confirm", TextColor.color(0xAEFFC1)),
                                        Component.text("Click to confirm your input."),
                                        100,
                                        confirmAction);

                        Object cancelButton = createButtonMethod.invoke(null,
                                        Component.text("Cancel", TextColor.color(0xFFA0B1)),
                                        Component.text("Click to cancel."),
                                        100,
                                        cancelAction);

                        // 5. Create Dialog Type
                        // DialogType.confirmation(confirmButton, cancelButton)
                        Method confirmationTypeMethod = dialogTypeClass.getMethod("confirmation", actionButtonClass,
                                        actionButtonClass);
                        Object typeObj = confirmationTypeMethod.invoke(null, confirmButton, cancelButton);

                        // 6. Final Dialog creation
                        // Dialog.create(builder -> builder.empty().base(base).type(type))

                        Method createDialogMethod = dialogClass.getMethod("create", Consumer.class);

                        Consumer<Object> dialogConfigurator = (Object builder) -> {
                                try {
                                        // builder.empty() -> returns EmptyBuilder
                                        Method emptyMethod = builder.getClass().getMethod("empty");
                                        Object emptyBuilder = emptyMethod.invoke(builder);

                                        // emptyBuilder.base(baseObj)
                                        Method baseMethod = emptyBuilder.getClass().getMethod("base", dialogBaseClass);
                                        Object withBase = baseMethod.invoke(emptyBuilder, baseObj);

                                        // emptyBuilder.type(typeObj)
                                        Method typeMethod = withBase.getClass().getMethod("type", dialogTypeClass);
                                        typeMethod.invoke(withBase, typeObj);

                                } catch (Exception e) {
                                        throw new RuntimeException("Failed to configure dialog", e);
                                }
                        };

                        Object dialogObj = createDialogMethod.invoke(null, dialogConfigurator);

                        // 7. Show Dialog
                        // Audience.showDialog(DialogLike) - Dialog implements DialogLike from Adventure
                        // API
                        Class<?> dialogLikeClass = Class.forName("net.kyori.adventure.dialog.DialogLike");
                        Class<?> audienceClass = Class.forName("net.kyori.adventure.audience.Audience");
                        Method showDialogMethod = audienceClass.getMethod("showDialog", dialogLikeClass);
                        showDialogMethod.invoke(player, dialogObj);

                } catch (Exception e) {
                        plugin.getLogger().warning("Failed to open Dialog API (Reflection error): " + e.getMessage());
                        e.printStackTrace();
                        // Fallback
                        callback.accept(null);
                }
        }
}
