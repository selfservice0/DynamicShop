DynamicShop — Dynamic Global Trade Market
=========================================

A fully dynamic, supply-and-demand driven economy system for Minecraft servers.

Current release: **3.0.2** · [Downloads](https://github.com/selfservice0/DynamicShop/releases) · [Wiki](https://github.com/selfservice0/DynamicShop/wiki) · [Interactive website demo](https://selfservice0.github.io/DynamicShop/) · [DSX Exchange preview](https://selfservice0.github.io/DynamicShop/?design=dsx#catalog)

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
