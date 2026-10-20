DynamicShop — Dynamic Global Trade Market
=========================================

A fully dynamic, supply-and-demand driven economy system for Minecraft servers.

Current version: **4.0.1** · [Downloads](https://github.com/selfservice0/DynamicShop/releases) · [Wiki](https://github.com/selfservice0/DynamicShop/wiki) · [Interactive website demo](https://selfservice0.github.io/DynamicShop/) · [DSX Exchange preview](https://selfservice0.github.io/DynamicShop/?design=dsx#catalog)

Version **4.0.1** includes corrected category textures for seven themes. The 4.0 release adds eight optional shop themes, animated category icons and matching controls, with automatic installation for the selected **Nexo or Oraxen** provider. It also improves sell confirmation, custom-item editing/deletion, restocking and reward ownership checks. Existing configurations and custom category overrides are preserved.

Select a theme with `/shopadmin theme halloween` (or `moonstone`, `christmas`, `newyear`, `valentines`, `easter`, `stpatricks`, `summer`). `default` retains your existing appearance. Named Java themes use matching 54-slot browsing; Bedrock retains its vanilla fallback. Custom theme files are preserved by normal installation; `/shopadmin theme install force` backs them up before replacement. Reload the selected provider and send its rebuilt pack after installing/updating resources. See the [theme guide](THEMES.md) and [current wiki](https://github.com/selfservice0/DynamicShop/wiki/GUI-Themes).

The sell menu stays open after confirming so players can sell another batch. Buying/selling through a quantity dialog returns to the refreshed shop/search page. Physical custom items share the standard admin controls, including permanent Shift+click deletion, and category restocks update their separate stock without changing purchase totals.

Version 3.0.7 adds a deposit-and-confirm sell menu, fixes permission/group upgrade prices and ownership checks, shows saved enchanted-book enchantments in game and on the webshop, and makes `dynamicshop.use.sellall` default to operators. Purchased books retain their original metadata. Install the updated JAR and restart; no config reset, re-adding of saved books, or website-file update is needed.

Use `/sell` or `/shop sell` to open the sell menu. Place or shift-click items into its 45 deposit slots, hover over each stack in either inventory to see its per-item price and stack value, and check the **Total worth** button before confirming. Prices include sell tax and stock-storage limits. Cancel or close the menu to get unsold items back; if your inventory is full, they drop beside you with your UUID set as their owner. Existing item lore and enchantments are preserved. The menu uses `dynamicshop.use` and `dynamicshop.use.sellgui` (default: `true`); disable it with `gui.sell_menu_enabled: false`. If another plugin owns `/sell`, use `/dynamicshop:sell` or `/shop sell`. `/shop sell <price>` still creates a player-shop listing.

Version 3.0.6 fixes PlaceholderAPI resolution and adds `%dynamicshop_stock_<MATERIAL>%` for live shop stock. Material and player names containing underscores, and item/player statistic names containing underscores, are parsed intact. Install the JAR and restart with PlaceholderAPI installed; no additional expansion download or config changes are required. Check stock with `/papi parse me %dynamicshop_stock_GOLD_INGOT%`.

The category-selection menu icons hide vanilla attribute tooltips, such as attack damage and attack speed. Category names, item counts, and browse instructions are retained. Install the new JAR and restart; this fix needs no config edits or website update.

Missing config defaults are inserted in their sections without reserializing existing settings, comments, quotes, or line endings. A symbol alone in `economy.currency_format` is also accepted (for example `"🪙"` becomes a coin prefix in displays, without changing the saved value). Startup and `/shopadmin reload` log the provider, its formatted sample, the configured override, and the shop's effective format to help diagnose currency display issues.

Version 3.0.6 uses the economy provider's currency formatting throughout in-game price displays and inherits its symbol placement on the website. This includes LiteEco's configured coin symbol through Vault. Optional currency display settings are available under **Administration → Configuration → Economy**, or in `config.yml`:

```yaml
economy:
  currency_format: "🪙 <money>"
  currency_singular_name: "coin"
  currency_plural_name: "coins"
```

Use `"<money> 🪙"` for a suffix or `"<money> <currency>"` for singular/plural names. Leave `currency_format` empty to use Vault/CoinsEngine formatting in game; the website inherits the provider's prefix/suffix and uses grouped numbers with two decimal places. These settings affect display only. After editing the file, run `/shopadmin reload`. Existing messages with `${price}` are handled without adding an extra dollar sign. Install the new JAR, restart, and run `/shopadmin webupdate` to install the updated website files. Currency changes saved through the website apply immediately.

Version 3.0.5 fixes in-game searching for registered custom items. Search finds their visible names, keeps them in the selected category, and supports buying and selling directly from the results. Large searches now have previous and next page buttons.

Version 3.0.4 enables buying and selling physical custom items with independent stock and adds them to the website category editor, including trading controls and permanent removal. See [CHANGELOG.md](CHANGELOG.md) for details.

Use `/shop sellall hand` to sell all sellable stacks matching your held item throughout your inventory. It requires `dynamicshop.use` and `dynamicshop.use.sellall`, respects shop stock limits, and preserves custom-item template checks. `dynamicshop.use.sellall` defaults to operators; other players can be granted it through their permissions plugin. `/shop sellhand` sells only the held stack; `/shop sellall` sells all sellable item types.

Version 3.0.2 includes six website designs, eight coordinated color palettes, twelve accent presets, and aggregate bStats charts for integrations, website/GUI usage, online player ranges, dynamic pricing, inflation, and transaction volume. Oraxen support and the Floodgate-based Bedrock GUI fallback are included.

After replacing the plugin JAR and restarting, run `/shopadmin webupdate` to back up and update existing website files. Select **DSX Exchange** under **Administration → Appearance** and save. Website settings and shop data are retained.

See [release notes](CHANGELOG.md), [bStats chart setup](BSTATS.md), [Oraxen integration](ORAXEN.md), and [website development and upgrades](web-src/README.md).

------------------------------------------------------------
Project Information
------------------------------------------------------------

Java Version: 21+
License: All Rights Reserved
Repository: https://github.com/selfservice0/DynamicShop

------------------------------------------------------------
Overview
------------------------------------------------------------

DynamicShop replaces static shop plugins with a global, fully dynamic economic marketplace.

Prices automatically adjust based on:
- Server-wide supply levels
- Player trading patterns
- Negative-stock inflation multipliers
- Hourly shortage inflation
- Curve-strength based stock soft limits
- Sell tax
- Optional player-run shops
- Automatic smart category detection
- Item searching with a virtual GUI
- Continuous (calculus-based) price calculation

This system creates a scalable, MMO-style economy that reacts to player behavior in real time.

------------------------------------------------------------
Major Features
------------------------------------------------------------

Global Dynamic Market
---------------------
- Prices increase as stock drops.
- Prices decrease as stock rises.
- Smooth price curves using adjustable curve strength.
- Strong price inflation for items with zero or negative stock.
- Configurable stock caps and multiplier limits.
- Continuous price calculation avoids looping for bulk buys and sells.

Player Shops (Optional)
-----------------------
- Players can list custom items for sale.
- Buyers automatically pay sellers, even if sellers are offline.
- Safe transaction pipeline prevents item duplication or mispayments.

Search GUI
----------
- Built-in fuzzy search for item names.
- Clean 6x9 inventory layout.
- Supports buying and selling directly from search results.

Smart Categorization
--------------------
- Automatically detects item categories.
- Used for organizing items inside shop pages.

Economy System Integration
--------------------------
- Supports Vault economy providers.
- Supports custom economy managers.
- Configurable number formatting system.

Demand Tracking
---------------
- Tracks purchases to slowly influence price weighting.

Highly Configurable
-------------------
DynamicShop allows control over:
- maxStock
- curveStrength
- negativeStockPercentPerItem
- hourlyIncreasePercent
- sellTaxPercent
- category GUI layout
- stock restrictions
- dynamic pricing enable/disable
- many other internal tuning variables

------------------------------------------------------------
Installation
------------------------------------------------------------

1. Install Java 21 or newer.
2. Drop the plugin JAR into the /plugins folder.
3. Start the server to generate configuration files.
4. Adjust config.yml for desired economic behavior.
5. Restart server.

------------------------------------------------------------
Developer Notes
------------------------------------------------------------

DynamicShop uses a calculus-based continuous pricing engine with the following model:

P(s) =
  BasePrice * (1 - k(s / L))                        if 0 <= s <= L  
  BasePrice * (1 - k)                              if s > L  
  BasePrice * (q^(-s)) * t                         if s < 0  

Where:
- s = stock
- L = maxStock
- k = curveStrength
- q = 1 + negativeStockPercentPerItem
- t = (1 + hourlyIncreasePercent)^(hoursInShortage)

This system ensures smooth price behavior and eliminates the need to loop through items one at a time.

------------------------------------------------------------
All Rights Reserved License
------------------------------------------------------------

© 2025 selfservice0

All Rights Reserved.

This software and all associated assets (including but not limited to: source code, compiled binaries, images, configuration files, and documentation) are the exclusive property of the author.

You MAY:
- Download and run this plugin on your own Minecraft server.
- View the source code for personal reference.

You may NOT:
- Copy, modify (other than  pull requests on this repo), distribute, or re-upload any part of this software.
- Create or distribute derivative works.
- Fork this repository on GitHub or any other platform.
- Use portions of this project in your own software.
- Sell or redistribute the plugin in any form.

Commercial licensing requires explicit written permission from the author.

------------------------------------------------------------
Contributions
------------------------------------------------------------

DynamicShop does accept pull requests due to the, but users must agree to All Rights Reserved license.  
Bug reports may be submitted through GitHub Issues.

------------------------------------------------------------
Support
------------------------------------------------------------

If you find this project useful, consider starring the repository to support development.
