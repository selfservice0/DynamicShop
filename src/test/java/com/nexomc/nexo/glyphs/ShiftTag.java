package com.nexomc.nexo.glyphs;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.Tag;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

/** Test-only singleton matching the Nexo API, preserving its shift font. */
public final class ShiftTag {
    public static final ShiftTag INSTANCE = new ShiftTag();
    public TagResolver getRESOLVER() {
        return TagResolver.resolver("shift", (args, context) -> {
            int value = Integer.parseInt(args.popOr("shift required").value());
            if (value != -48 && value != -170) throw new IllegalArgumentException("Unexpected shift " + value);
            return Tag.selfClosingInserting(Component.text(value == -48 ? "\uf810" : "\uf811")
                    .font(Key.key("nexo:shift")));
        });
    }
}
