# GUI Themes

DynamicShop 4.0.1 bundles eight optional themes for **both Nexo and Oraxen**. Install the provider separately. Theme files are included in the DynamicShop JAR; no separate theme ZIP is required for automatic installation.

## Available themes

| Theme | Command name | Common aliases |
|---|---|---|
| Original appearance / existing message titles | `default` | |
| Moonstone | `moonstone` | `moonlit` |
| Halloween | `halloween` | |
| Christmas | `christmas` | |
| New Year | `newyear` | `newyears`, `new-year` |
| Valentine's Day | `valentines` | `valentine`, `valentinesday` |
| Easter | `easter` | |
| St. Patrick's Day | `stpatricks` | `stpatrick`, `stpatricksday` |
| Summer Festival | `summer` | `summerfestival`, `midsummer` |

## Select and install

Merge settings into the existing `gui:` section of `plugins/DynamicShop/config.yml`:

```yaml
gui:
  custom_item_provider: auto
  theme: default
  auto_install_oraxen_theme: true
  auto_install_nexo_theme: true
```

`auto` prefers enabled Nexo, then enabled Oraxen. Choose `nexo` or `oraxen` explicitly when both are installed and you want a specific provider. `none`, an unavailable explicit provider or `theme: default` installs no theme files.

Admin commands require `dynamicshop.admin` and also work from console:

```text
/shopadmin theme list
/shopadmin theme halloween
/shopadmin theme default
/shopadmin theme install
/shopadmin theme install force
```

Selecting a named theme saves only `gui.theme`, preserving other config values and comments. Automatic installation runs on startup, `/shopadmin reload` and theme selection. Only the selected theme and provider are targeted. Disable that provider's automatic-install flag if you prefer manual file management; explicit `install` and `install force` still work.

`default` restores the existing message-based appearance and does not delete installed packs. Already loaded themes can be switched by command and reopening the menus. Newly installed/updated resources require the provider reload and pack steps below. Themes do not switch automatically on holiday dates.

## Reload and send the pack

**Nexo:** run `/nexo reload`, wait for generation/upload, then `/nexo pack <player>` if the rebuilt pack is not sent automatically.

**Oraxen:** run `/oraxen reload all`, wait for generation/upload, then `/oraxen pack send`.

Accept the updated pack and reopen the menus. DynamicShop installs files but does not automatically reload either provider or send packs. Built-in named themes select their matching background glyphs without replacing `messages.yml`.

## Updates and custom files

Normal installation adds missing files, updates unchanged files previously installed by DynamicShop, and preserves custom/unrecognized files. Readable assigned glyph characters are retained. The console reports preserved files.

To replace older/custom copies of the selected theme, use `/shopadmin theme install force`. It backs up every replaced file under `plugins/DynamicShop/theme-installation/backups/` before writing any theme files and reports the backup directory. It does not alter other themes, provider settings, category overrides, prices or stock. Reload and send the provider pack afterward.

4.0.1 contains corrected category textures for Moonstone, Christmas, New Year, Valentine's Day, Easter, St. Patrick's Day and Summer. A modified server copy is intentionally preserved until you choose a force installation.

## Layout and coverage

All themes cover shop showcases, category selection, admin editors, `/sell`, search, player-shop menus, inventory buy/sell menus and the supported Paper quantity-dialog backdrop. Each has 11 animated default-category icons and 11 matching controls.

Themed Java item browsing uses 54 slots to match the chooser. Each theme's chooser and item pages share the same exterior; all eight themes share common visible frame dimensions. Standard menus and Bedrock retain `gui.shop_menu_size`. Existing category positions, names and custom icon overrides are preserved; the showcase adapts to central, wider or custom edge layouts.

## Editing texture files

For Halloween, edit the server's PNG files at:

```text
Nexo:   plugins/Nexo/pack/external_packs/dynamicshop_halloween/assets/dynamicshop_halloween/textures/
Oraxen: plugins/Oraxen/pack/assets/dynamicshop/textures/
```

Use the provider's actual data folder if it differs. Replace `halloween` with the theme's command name:

| File | Menu |
|---|---|
| `dynamicshop_halloween_categories_54.png` | Central category chooser |
| `dynamicshop_halloween_categories_wide_54.png` | Custom full-width category chooser |
| `dynamicshop_halloween_shop_54.png` | Inside a category |
| `dynamicshop_halloween_sell_54.png` | Sell menu |
| `dynamicshop_halloween_dialog.png` | Native quantity dialog |

Keep the original dimensions, transparency and slot positions. Animated category icons are vertical frame strips with `.png.mcmeta` timing files. Save PNG, reload the provider and send its rebuilt pack. Normal installation preserves your edits; force installation replaces them after backup.

## Custom category icons

Hold the installed custom item, open `/shopadmin`, Shift+right-click the category, then left-click **Change Category Icon**. DynamicShop detects Nexo/Oraxen IDs automatically. Right-click the icon control to type `nexo:watering_can` or `oraxen:watering_can`, using the item's actual registered ID.

A saved icon override takes priority over a themed default. To restore the theme icon without changing the category name, remove that category's `icon:` setting from `plugins/DynamicShop/categories.yml`, run `/shopadmin reload` and reopen the menu.

## Troubleshooting

- Check `gui.theme` and `gui.custom_item_provider` before installing files.
- If Nexo and Oraxen are both enabled, `auto` selects Nexo; installing Oraxen files alone will not supply the Nexo pack.
- Missing/invalid Oraxen glyphs fall back to a readable title and report the glyph ID. Install the matching files and fix provider startup/pack-generation errors, then reload and send the pack.
- An old appearance after a successful installation usually needs the rebuilt resource pack accepted by the client and the inventory reopened.
- For Floodgate-detected Bedrock players, use the [Bedrock fallback](https://github.com/selfservice0/DynamicShop/wiki/Bedrock-Support); Java GUI fonts do not supply Bedrock textures.
