# Nexo Integration

Nexo is an optional soft dependency. DynamicShop 4.0.1 supports native Nexo GUI themes, glyphs, controls, category icons, fillers and tradeable custom items. Install Nexo separately before starting DynamicShop.

## Built-in themes

Use the existing `gui:` section:

```yaml
gui:
  custom_item_provider: nexo
  theme: halloween
  auto_install_nexo_theme: true
```

`auto` also chooses Nexo when it is enabled, including when Oraxen is enabled too. Run `/shopadmin theme halloween` to save/select that theme. The JAR installs its native files into Nexo's actual data folder:

```text
items/dynamicshop_halloween_theme.yml
glyphs/dynamicshop_halloween.yml
pack/external_packs/dynamicshop_halloween/assets/dynamicshop_halloween/textures/
```

This applies to all eight [GUI Themes](THEMES.md). Automatic installation runs on startup, DynamicShop reload and theme selection. Existing custom files are preserved; unchanged managed files can update. Use `/shopadmin theme install` manually, or `/shopadmin theme install force` to back up and replace the selected theme's bundled files. Manual installation works with automatic installation disabled.

After installation run `/nexo reload`, wait for pack generation/upload, then `/nexo pack <player>` if necessary. Accept the new pack and reopen menus. A separate ZIP or messages-file replacement is not required for built-in themes. Nexo reload/send is not performed automatically by DynamicShop.

## Existing custom GUI packs

`gui.theme: default` retains configured message titles and legacy navigation IDs. A new `messages.yml` is seeded from the selected provider's example; an existing file is preserved. `messages_nexo_example.yml` is a reference, not the active messages file.

Legacy examples use glyphs such as `shop_gui_menu` and `shop_item_display`, and navigation items `shop_back_button`, `shop_next_button`, `shop_categories_button`, `shop_search_button`, `shop_page_button` and `shop_filter_button`. Supply these in your own pack or change active message titles. Named built-in themes use their own installed IDs instead.

## Custom items and category icons

Hold a registered Nexo item and run `/shopadmin add item <price> [category] [display name...]`. DynamicShop detects its Nexo ID and stores `nexo` delivery. Physical Nexo entries support buying, selling, independent stock/pricing, category search, restocking and the standard admin item editor. They do not share stock with the base vanilla material or another custom ID.

Use `nexo:watering_can` (with your item's actual ID) as a category icon or `gui.filler_material`. To set an icon by holding the item, open `/shopadmin`, Shift+right-click the category and left-click **Change Category Icon**. See [Custom Items and Templates](https://github.com/selfservice0/DynamicShop/wiki/Custom-Items-and-Templates).

## Troubleshooting

- Check that Nexo is enabled and is the selected GUI provider.
- For a named theme, check the installed `dynamicshop_<theme>` glyph and texture files, then reload Nexo and send the rebuilt pack.
- For legacy/custom titles, verify the IDs in active `messages.yml`; editing only the example does not change menus.
- Check provider startup/resource-pack errors and whether the client accepted the new pack.
- Custom files reported as preserved require a manual merge or an intentional force installation to replace them.
- Floodgate-detected Bedrock players receive vanilla menu decoration. Actual custom items still require appropriate Bedrock resources/mappings; see [Bedrock Support](https://github.com/selfservice0/DynamicShop/wiki/Bedrock-Support).
