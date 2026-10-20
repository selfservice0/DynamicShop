Bundled provider themes

Each provider directory (oraxen/ and nexo/) contains eight theme ZIP files:
moonstone, halloween, christmas, newyear, valentines, easter, stpatricks, summer.
They contain native glyphs, item definitions, textures and animation metadata.
These resources must remain binary/unfiltered in Maven builds.

Admin commands (dynamicshop.admin; also available from console):
  /shopadmin theme install        Install the selected theme, preserving custom files.
  /shopadmin theme install force  Back up and replace its bundled provider files.

Installation targets gui.custom_item_provider: nexo or oraxen. Auto selects
Nexo first when both plugins are enabled. No provider is installed when none
is selected or an explicit provider is unavailable. Automatic installation runs
on startup, /shopadmin reload and theme selection, controlled independently by
gui.auto_install_nexo_theme and gui.auto_install_oraxen_theme (both default true).
Explicit installation ignores that provider's automatic-install setting. It
does not change the active theme or provider/config settings. A bundled theme
and an enabled provider must be selected. Other themes and unrelated files are
untouched. Force backups are saved under DynamicShop/theme-installation/backups/;
the command reports the folder. All required backups finish before any theme
files are written. Existing glyph characters are retained when YAML is readable;
malformed old glyph files are backed up and replaced with valid bundled YAML.
Nexo files use items/, glyphs/ and pack/external_packs/dynamicshop_<theme>/.
After installation reload the selected provider and wait for generation/upload:
  Nexo:   /nexo reload, then /nexo pack <player>
  Oraxen: /oraxen reload all, then /oraxen pack send
Accept the updated resource pack and reopen menus. Reloads are not automatic.

Regenerate from the workspace's checked artwork before building the plugin:
  .build-tools/isolate-moonstone-textures.ps1
  .build-tools/build-dialog-themes.ps1
  .build-tools/build-seasonal-themes.ps1
  .build-tools/build-sword-category-icons.ps1
  .build-tools/restore-category-trim.ps1
  .build-tools/unify-shop-outlines.ps1
  .build-tools/bundle-oraxen-themes.ps1
  .build-tools/convert-nexo-themes.ps1
  .build-tools/bundle-nexo-themes.ps1 (also runs at the end of conversion)

Workspace owner edits live in theme-texture-edits/. The original-file-hashes
manifest distinguishes edited files from unchanged 4.0.0 baselines. Conversion
and bundling run apply-theme-texture-edits.ps1 after generation hooks, copying
only changed files to the provider output with exact bytes. Never regenerate
or overwrite the edit directory. Refresh previews after applying these edits.
Showcase validation retains strict clickable-cell/inventory checks; an exact
owner-edited texture may customize decorative outer-cabinet border pixels.

The installer excludes the Moonstone pack's optional original-name texture
replacements. Separate navigation textures and the native dialog background
let it coexist with existing original/custom artwork.

The six seasonal Tools & Weapons icons use the sword-only sources saved as
art-source/category-tools-sword.png in their workspace theme folders. Their
export overrides the older category-atlas cell without changing item IDs,
icon dimensions or animation metadata. Conversion and bundling also apply
this export so a rebuild cannot restore the crossed-pickaxe artwork.
Moonstone and Halloween already use sword-only category icons.

The themed category chooser and item browser use one shared exterior per
theme and common visible frame bounds across all eight themes. Native item
slots, player inventory and footer controls remain registered independently
of the outer roof/margin artwork. Built-in Java themes use 54-slot browsing
to match the chooser; default menus and Bedrock retain gui.shop_menu_size.
outline-validation.json checks every exterior pixel and visible frame bounds
for the six exported browsing layouts, including compact fallback assets.

All eight central category choosers restore their original decorative trim
above the player inventory instead of an unused solid navigation rectangle.
restore-category-trim.ps1 changes only that interior region, preserving the
shared exterior, cabinet border and player slots. Conversion and bundling
apply the same repair; showcase validation checks its detail and opacity.

OraxenThemeInstallerTest and NexoThemeInstallerTest check actual bundled resources, installation,
updates, assigned glyph characters, custom-file preservation, selected
provider/theme/opt-out behavior, and path restrictions.
