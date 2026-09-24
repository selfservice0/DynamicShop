package io.th0rgal.oraxen.glyphs;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.Tag;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

/** Test-only native resolver so title tests exercise the optional classloader bridge. */
public final class GlyphTag {
    public static final TagResolver RESOLVER = TagResolver.resolver("glyph", (args, context) -> {
        String id = args.popOr("glyph id required").value();
        if (!id.equals("dynamicshop_gui_menu")) throw new IllegalArgumentException(id);
        return Tag.selfClosingInserting(Component.text("\ua413").font(Key.key("minecraft:default")));
    });
}
