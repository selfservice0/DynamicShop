package io.th0rgal.oraxen.glyphs;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.Tag;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

/** Test-only resolver; tests assert that the provider's component font survives parsing. */
public final class ShiftTag {
    public static final TagResolver RESOLVER = TagResolver.resolver("shift", (args, context) -> {
        int value = Integer.parseInt(args.popOr("shift required").value());
        if (value != -48) throw new IllegalArgumentException("Unexpected shift " + value);
        return Tag.selfClosingInserting(Component.text("\uf810\uf80f").font(Key.key("oraxen:shift")));
    });
}
