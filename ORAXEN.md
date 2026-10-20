# Oraxen Integration

DynamicShop 4.0.1 supports Oraxen as an optional integration for built-in GUI themes, glyph backgrounds, navigation, category icons, fillers and tradeable custom items. Install Oraxen separately before starting DynamicShop. Its API/plugin is not bundled into DynamicShop.

## Built-in themes

Merge settings into the existing `gui:` section:

```yaml
gui:
  custom_item_provider: oraxen
  theme: halloween
  auto_install_oraxen_theme: true
```

Select `oraxen` explicitly when Nexo is also enabled: `auto` prefers Nexo. Run `/shopadmin theme halloween` to save/select the theme. The JAR installs its files into Oraxen's actual data folder:

```text
glyphs/dynamicshop_halloween.yml
items/dynamicshop_halloween.yml
items/dynamicshop_halloween_categories.yml
pack/assets/dynamicshop/textures/
```

All eight [GUI Themes](THEMES.md) are included. Automatic installation runs on startup, `/shopadmin reload` and theme selection. It adds missing files, updates unchanged managed files, preserves custom files and retains readable assigned glyph characters. Theme-specific filenames and IDs keep other theme packs separate.

Run `/shopadmin theme install` to install manually, or `/shopadmin theme install force` to back up and replace the selected theme's bundled files. Both manual commands work with automatic installation disabled and target the selected GUI provider.

Then run `/oraxen reload all`, wait for resource-pack generation/upload, and `/oraxen pack send`. Accept the updated pack and reopen menus. No messages-file replacement or separate theme ZIP is required for named built-in themes. DynamicShop does not reload/send Oraxen automatically.

## Legacy and custom titles

`gui.theme: default` keeps existing configured message titles and legacy navigation IDs. Existing `messages.yml` is preserved; `messages_oraxen_example.yml` is a reference. To change a custom title, edit the existing `messages:` section in active `messages.yml`, then `/shopadmin reload`.

Legacy examples reference `dynamicshop_gui_menu`, `dynamicshop_item_display` and navigation items such as `shop_back_button`. Those IDs require your matching custom/legacy pack. Named themes select their installed `dynamicshop_<theme>_*` glyphs and themed controls automatically, including quantity-dialog backgrounds.

Allow Oraxen to allocate unused glyph characters. Avoid copying fixed character assignments from another pack. Glyph IDs describe backgrounds; item IDs describe buttons or custom items.

## Custom items and icons

Hold the registered item and run `/shopadmin add item <price> [category] [display name...]`. Oraxen's ID and delivery method are detected. Physical custom entries support buying, selling, independent stock and pricing, search, restocking and the normal admin item editor.

For manual special-item configuration, Oraxen entries use `delivery_method: oraxen` and `nbt: item_id`; the legacy field name `nbt` holds the provider item ID. The website's server-item editor supports Oraxen delivery too.

Use `oraxen:watering_can` (your actual item ID) for a category icon or custom filler. The category editor also detects an Oraxen item held in the main hand. See [Custom Items and Templates](https://github.com/selfservice0/DynamicShop/wiki/Custom-Items-and-Templates).

## Troubleshooting

- Replace a JAR with a full server restart; keep only one DynamicShop JAR installed.
- Verify `gui.theme`, the selected provider and that the client accepted the rebuilt pack.
- Missing/invalid Oraxen GUI glyphs log their exact ID and fall back to a readable title. Install the matching files, fix texture paths/pack errors and reload Oraxen. Restored glyphs can resolve after reload without restarting DynamicShop.
- If edited/old files are reported as preserved, merge manually or use the backup-first force command.
- Check conflicting fixed characters if two glyphs claim the same code.
- Floodgate-detected Bedrock players use vanilla decoration; Java resource packs do not supply Bedrock textures. See [Bedrock Support](https://github.com/selfservice0/DynamicShop/wiki/Bedrock-Support).
