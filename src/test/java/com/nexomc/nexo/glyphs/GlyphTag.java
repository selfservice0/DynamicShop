package com.nexomc.nexo.glyphs;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.Tag;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

/** Matches the Nexo singleton API so tests exercise NexoWrapper's actual resolver path. */
public final class GlyphTag {
    public static final GlyphTag INSTANCE = new GlyphTag();
    private static final java.util.Set<String> LAYOUTS = java.util.Set.of("admin_54", "admin_45", "admin_36",
            "sell_54", "list_54", "action_27", "shop_54", "shop_45", "shop_36", "shop_27",
            "categories_54", "categories_wide_54", "dialog");

    public TagResolver getRESOLVER() {
        return TagResolver.resolver("glyph", (args, context) -> {
            String id = args.popOr("glyph id required").value();
            for (String theme : new String[] {"moonstone", "halloween", "christmas", "newyear", "valentines", "easter", "stpatricks", "summer"}) {
                String prefix = "dynamicshop_" + theme + "_";
                if (id.startsWith(prefix) && LAYOUTS.contains(id.substring(prefix.length()))) {
                    String glyph = switch (theme) {
                        case "halloween" -> "\ue601";
                        case "christmas" -> "\ue602";
                        case "newyear" -> "\ue603";
                        case "valentines" -> "\ue604";
                        case "easter" -> "\ue605";
                        case "stpatricks" -> "\ue606";
                        case "summer" -> "\ue607";
                        default -> "\ue600";
                    };
                    return Tag.selfClosingInserting(Component.text(glyph)
                            .font(Key.key("dynamicshop:themes")));
                }
            }
            throw new IllegalArgumentException(id);
        });
    }
}
