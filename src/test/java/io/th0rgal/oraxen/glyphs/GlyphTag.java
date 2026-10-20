package io.th0rgal.oraxen.glyphs;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.Tag;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

/** Test-only native resolver so title tests exercise the optional classloader bridge. */
public final class GlyphTag {
    public static final TagResolver RESOLVER = TagResolver.resolver("glyph", (args, context) -> {
        String id = args.popOr("glyph id required").value();
        String registered = io.th0rgal.oraxen.OraxenPlugin.get().getFontManager().getGlyphFromName(id).getName();
        if (io.th0rgal.oraxen.OraxenPlugin.BROKEN_TEXTURES.contains(id)) registered = "required";
        String glyph = registered.startsWith("dynamicshop_valentines_") ? "\ue504"
                : registered.startsWith("dynamicshop_easter_") ? "\ue505"
                : registered.startsWith("dynamicshop_stpatricks_") ? "\ue506"
                : registered.startsWith("dynamicshop_summer_") ? "\ue507"
                : registered.startsWith("dynamicshop_christmas_") ? "\ue502"
                : registered.startsWith("dynamicshop_newyear_") ? "\ue503" : switch (registered) {
            case "dynamicshop_gui_menu" -> "\ua413";
            case "dynamicshop_moonstone_dialog", "dynamicshop_moonstone_admin_54", "dynamicshop_moonstone_admin_45",
                    "dynamicshop_moonstone_admin_36", "dynamicshop_moonstone_sell_54",
                    "dynamicshop_moonstone_list_54", "dynamicshop_moonstone_action_27",
                    "dynamicshop_moonstone_shop_54", "dynamicshop_moonstone_shop_45",
                    "dynamicshop_moonstone_shop_36", "dynamicshop_moonstone_shop_27",
                    "dynamicshop_moonstone_categories_54", "dynamicshop_moonstone_categories_wide_54" -> "\ue500";
            case "dynamicshop_halloween_dialog", "dynamicshop_halloween_admin_54", "dynamicshop_halloween_admin_45",
                    "dynamicshop_halloween_admin_36", "dynamicshop_halloween_sell_54",
                    "dynamicshop_halloween_list_54", "dynamicshop_halloween_action_27",
                    "dynamicshop_halloween_shop_54", "dynamicshop_halloween_shop_45",
                    "dynamicshop_halloween_shop_36", "dynamicshop_halloween_shop_27",
                    "dynamicshop_halloween_categories_54", "dynamicshop_halloween_categories_wide_54" -> "\ue501";
            default -> "\ue5ff"; // Native Oraxen substitutes the "required" icon for an unknown ID.
        };
        return Tag.selfClosingInserting(Component.text(glyph).font(Key.key("minecraft:default")));
    });
}
