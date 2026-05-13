DynamicShop — Dynamic Global Trade Market (Folia Supported! 🚀)
===========================================================

A fully dynamic, supply-and-demand driven economy system for Minecraft servers. Now updated with 100% thread-safety for Folia! (✿◠‿◠) ✨

------------------------------------------------------------
Project Information
------------------------------------------------------------

- **Java Version:** 21+
- **Platform:** Paper, Purpur, and **Folia** 1.20.1 - 1.21.x+
- **License:** All Rights Reserved
- **Repository:** https://github.com/selfservice0/DynamicShop

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
🌟 Folia Compatibility (New!)
------------------------------------------------------------

This version has been carefully migrated to the modern **Paper API** to ensure smooth and stable operation on multi-threaded server environments like **Folia**. 

Changes include:
- Migrated all `BukkitScheduler` tasks to `AsyncScheduler` and `GlobalRegionScheduler`.
- Used `EntityScheduler` for player-specific actions (GUIs and Messaging).
- Full thread-safety for high-performance servers!

------------------------------------------------------------
Major Features
------------------------------------------------------------

Global Dynamic Market
---------------------
- Prices increase as stock drops.
- Prices decrease as stock rises.
- Smooth price curves using adjustable curve strength.
- Strong price inflation for items with zero or negative stock.
- Configurable stock caps and multipliers.

Calculus-Based Pricing
----------------------
DynamicShop uses a continuous pricing model. Buying 64 items is mathematically identical to buying 1 item sixty-four times. This prevents price-sniping and ensures economic fairness.

Advanced Web Dashboard
----------------------
- Real-time transaction feed.
- Economic health analytics.
- Price history and trends for every item.
- Global leaderboards for earners and spenders.
- Fully-featured **Web Admin Panel** for remote management.

Player-Run Shops
----------------
- Players can list their own items for sale.
- Integrated with the main shop UI.
- Secure virtual GUI-based trading.

------------------------------------------------------------
Developer Notes
------------------------------------------------------------

DynamicShop uses a calculus-based continuous pricing engine with the following model:

**P(s) =**
- `BasePrice * (1 - k(s / L))` if `0 <= s <= L`
- `BasePrice * (1 - k)` if `s > L`
- `BasePrice * (q^(-s)) * t` if `s < 0`

**Where:**
- `s` = stock
- `L` = maxStock
- `k` = curveStrength
- `q` = 1 + negativeStockPercentPerItem
- `t` = (1 + hourlyIncreasePercent)^(hoursInShortage)

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
- Copy, modify (other than pull requests on this repo), distribute, or re-upload any part of this software.
- Create or distribute derivative works.
- Fork this repository on GitHub or any other platform.
- Use portions of this project in your own software.
- Sell or redistribute the plugin in any form.

Commercial licensing requires explicit written permission from the author.

------------------------------------------------------------
Contributions
------------------------------------------------------------

DynamicShop accepts pull requests for bug fixes and feature enhancements (like our new Folia support!). Contributors must agree to the All Rights Reserved license.  

Bug reports may be submitted through GitHub Issues.

------------------------------------------------------------
Support
------------------------------------------------------------

If you find this project useful, consider starring the repository to support development. Special thanks to the community for helping make this plugin better for everyone! (≧◡≦) ♡