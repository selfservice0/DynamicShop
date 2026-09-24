# Oraxen support

Oraxen is optional, alongside the existing Nexo integration. Install it before
starting DynamicShop; both are declared as soft dependencies. The Oraxen API is
loaded through its plugin classloader and is not bundled into DynamicShop.

Merge into your existing `gui` section in `plugins/DynamicShop/config.yml`:

```yaml
gui:
  custom_item_provider: oraxen
  shop_menu_size: 54
  filler_material: AIR
  use_dialog_gui: false
```

`custom_item_provider` accepts `auto`, `nexo`, `oraxen`, or `none`. The default
`auto` selects Nexo first, then Oraxen. Select `oraxen` explicitly when both are
installed and this pack should supply the menu glyphs and navigation items.
Explicit item identifiers always use their named provider.

## GUI backgrounds and buttons

Install the DynamicShop glyph, item and texture files in Oraxen. Merge the title
entries from the generated `messages_oraxen_example.yml` into the active
`messages.yml`; editing the example alone does not change existing menus.
New installations select a message example based on the configured provider.
Existing messages are preserved, including titles using raw Unicode characters.

Example title inside `messages`:

```yaml
gui-category-title: "<white><shift:-48><glyph:dynamicshop_gui_menu><shift:-170><black><bold>Categories"
```

The selected plugin's native MiniMessage resolvers render glyphs and spacing.
GUI titles are server-authored and do not enforce chat glyph permissions.
The Oraxen example uses the `dynamicshop_*` glyph IDs in the converted pack.
Its buttons use `shop_back_button`, `shop_next_button`, `shop_categories_button`,
`shop_search_button`, `shop_page_button`, and `shop_filter_button`.
Missing custom items use the existing vanilla icon fallback.

## Category icons, fillers, and purchases

- Use `oraxen:shop_food` as a category icon in `categories.yml`, the in-game
  category editor, or the website's category editor.
- The in-game category editor also recognizes an Oraxen item held in your hand.
- `gui.filler_material` accepts `oraxen:item_id` as well as Nexo IDs and materials.
- Holding an Oraxen item and using `/shopadmin add item <price>` stores its
  Oraxen ID and sets its delivery method automatically.
- The website's server-item editor accepts `oraxen` as the delivery method.
- Manual server-item configurations use `delivery_method: oraxen` and
  `nbt: item_id`. Here `nbt` is the existing custom-item identifier field.
- Admin/player shop previews use the registered Oraxen item. Purchases obtain
  a fresh stack from Oraxen; unavailable items follow the existing failed
  delivery/refund path.

After replacing the DynamicShop JAR, restart the server. For later configuration
changes, run `/oraxen reload all`, accept the updated pack, then
`/shopadmin reload` and reopen `/shop`.

## Validation

Tests exercise provider selection, current/legacy Oraxen item API signatures,
independent item stacks, Oraxen-only item/category lookup, native glyph/shift
resolver routing, legacy title compatibility, missing-provider fallbacks, and
custom-item delivery. The native resolver routing tests use test-only API
stand-ins. Actual client rendering still requires an in-game resource-pack test.
