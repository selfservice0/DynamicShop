package io.th0rgal.oraxen;

/** Test-only registry matching Oraxen's actual missing-glyph fallback behavior. */
public final class OraxenPlugin {
    private static final OraxenPlugin INSTANCE = new OraxenPlugin();
    public static final java.util.Set<String> MISSING = new java.util.HashSet<>();
    public static final java.util.Set<String> BROKEN_TEXTURES = new java.util.HashSet<>();
    private final FontManager fonts = new FontManager();
    public static OraxenPlugin get() { return INSTANCE; }
    public FontManager getFontManager() { return fonts; }

    public static final class FontManager {
        public Glyph getGlyphFromName(String name) {
            if (!MISSING.contains(name) && (name.equals("dynamicshop_gui_menu")
                    || name.matches("dynamicshop_(?:moonstone|halloween|christmas|newyear|valentines|easter|stpatricks|summer)_(?:dialog|admin_54|admin_45|admin_36|sell_54|list_54|action_27|shop_54|shop_45|shop_36|shop_27|categories_54|categories_wide_54)"))) {
                return new Glyph(name);
            }
            return new Glyph("required");
        }

        public Glyph getAnimatedGlyphFromID(String name) {
            return "animated_title".equals(name) ? new Glyph(name) : null;
        }

        public Glyph getReferenceGlyphFromID(String name) {
            return "reference_title".equals(name) ? new Glyph(name) : null;
        }
    }

    public record Glyph(String name) {
        public String getName() { return name; }
        public String getTexture() {
            return BROKEN_TEXTURES.contains(name) ? "required/exit_icon.png" : "dynamicshop:" + name + ".png";
        }
    }
}
