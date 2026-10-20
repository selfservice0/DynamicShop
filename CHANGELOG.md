# Release notes

## 4.0.1

- Included the owner's corrected category-chooser textures for Moonstone, Christmas, New Year, Valentine's Day, Easter, St. Patrick's Day and Summer in both Oraxen and Nexo packs and their bundled automatic installers. Original image dimensions, identifiers and slot positions are retained.
- Packaging now reapplies edited texture files after generation steps so future builds preserve these corrections. Installation retains existing custom-file protection and the backup-first force-update command.

## 4.0.0

- Added eight optional GUI themes: Moonstone/Moonlit, Halloween, Christmas, New Year, Valentine's Day, Easter, St. Patrick's Day and Summer Festival. Both Nexo and Oraxen are supported across shop/category showcases, admin editors, `/sell`, search, player-shop menus and supported native quantity-dialog backgrounds. Each theme includes 11 animated default-category icons and 11 matching controls. `default` retains the existing appearance and installs nothing.
- Added `/shopadmin theme [list|name]` for admins and console, with tab completion and common holiday aliases. Selecting a theme changes only `gui.theme`, preserving other config values and comments. Existing custom category names, slots, visibility and icon overrides are retained. Reopen menus after switching.
- The selected theme's native files install from the JAR on startup, `/shopadmin reload` and theme selection. `gui.custom_item_provider: auto` prefers enabled Nexo, then Oraxen. Independent `gui.auto_install_nexo_theme` and `gui.auto_install_oraxen_theme` flags default to true. Nexo uses an isolated external pack; both providers preserve custom files and update unchanged managed files while retaining readable glyph characters.
- Added `/shopadmin theme install [force]` for the selected provider. Normal installation preserves custom files; force backs up every replaced file under `DynamicShop/theme-installation/backups/` before any theme writes. Explicit installation works with automatic installation disabled and leaves other themes and settings untouched. Commands report the provider's reload/send instructions.
- Themed Java browsing stays at 54 slots to match the category chooser. Choosers and item pages share their theme's exterior and common visible frame dimensions across all eight themes. Standard menus and Bedrock retain the configured size. Category showcases adapt to central, wider and custom edge layouts without moving category slots.
- Corrected uneven Moonstone button sizes and replaced its stretched side ornament with a compact crystal. Restored decorative category-footer trim across all themes and replaced crossed-pickaxe Tools & Weapons icons with sword-only art, retaining animation timing, identifiers and dimensions.
- Missing or invalid Oraxen glyphs now fall back to readable titles and log the affected ID. Placeholder spacing no longer pushes titles off-screen. Restored glyphs resolve after an Oraxen reload without restarting DynamicShop. Floodgate-detected Bedrock players retain readable titles and vanilla controls.
- Added `/sell` and `/shop sell`, with 45 deposit slots, confirmation/cancellation, per-item/stack price previews and totals for each currency. Confirming keeps the menu open and refreshes it for another batch. Closing or cancelling returns unsold items; only deposited items are sold.
- Sell previews include tax and stock limits, preserve original item lore/enchantments and avoid the newer Paper ProtocolLib tooltip error. A basket uses one pricing timestamp, fixing repeated confirmations caused by time inflation or shortage decay, while live items, permissions, settings and stock limits are rechecked. Changed quotes require another confirmation. Cancelled deaths retain deposited items.
- Quantity-dialog purchases and sales return to the refreshed originating shop or search page on the next tick, restoring inventory registration/click protection even after rejected transactions.
- Physical custom items share the normal admin controls for stock, price, shortage increase, category, buying/selling, visibility, pricing max stock and storage limits. Their provider metadata, display-material and required-permission controls are retained. Edits affect only the custom ID. Normal/custom editors include permanent Shift+click deletion; removed entries stay removed after reload and cannot be recreated by pending input.
- Category restocks include physical custom items and saved variants, using their own stock IDs and storage limits. Positive restocks clear shortage inflation without changing purchase totals.
- Saved enchanted books show enchantments and levels in game and on the webshop; purchased books retain those enchantments. Category icons hide extra vanilla tooltips while keeping names, counts and browse instructions.
- Permission/group upgrades use their configured prices and check ownership before charging, including after category changes. Command rewards detect supported permanent LuckPerms grants and support `owned_permission`, `owned_group`, optional `ownership_world` and intentional `allow_repeat_purchase`. Failed command dispatches are refunded.
- Special reward lore is configurable through `messages.yml`, including type, permission/group, price, stock, ownership and click instructions. Empty lines can hide individual messages; missing defaults preserve existing values. Shortage-increase lore is hidden when dynamic pricing or time inflation is disabled, including custom-item and Bedrock tooltips.
- The sell menu defaults to enabled, controlled by `gui.sell_menu_enabled` and `dynamicshop.use.sellgui`. `/shop sellall` and `/shop sellall hand` default to operators; other players need `dynamicshop.use.sellall`.

Replace the JAR and restart. Existing configs are preserved and missing settings are added without resetting them. Reload the selected GUI provider and rebuild/send its pack after installing updated theme files. Use `/dynamicshop:sell` if another plugin owns `/sell`; `/shop sell <price>` still creates a player-shop listing.

## 3.0.6

### PlaceholderAPI

DynamicShop's PlaceholderAPI expansion now overrides the correct `onRequest(OfflinePlayer, String)` callback. The previous method overloaded a different callback signature, allowing registration to succeed while requests fell through to PlaceholderAPI's default handler.

Added `%dynamicshop_stock_<MATERIAL>%`, including `%dynamicshop_stock_GOLD_INGOT%`, to return live shop stock. Whole quantities have no trailing decimal, fractional and negative stock are preserved, and missing or invalid materials return `0`. Material lookup is case-insensitive and independent of the server locale.

Item statistics preserve full material names and statistic names containing underscores, such as `%dynamicshop_item_times_bought_quantity_GOLD_INGOT%`. Player statistics preserve names such as `test_player` and the `total_spent`/`total_earned` suffixes. Existing compact item statistic aliases remain supported. Startup only reports a successful PlaceholderAPI hook when registration succeeds.

Install the JAR and restart with PlaceholderAPI installed. No extra expansion download, config edits, or website update are required. Verify with `/papi parse me %dynamicshop_stock_GOLD_INGOT%`.

Regression tests exercise PlaceholderAPI's real percent-placeholder parser with the exact reported placeholder, callback dispatch for existing global statistics, live stock changes, underscore parsing, Turkish locale handling, and the configured currency in recent transactions. A live check on the reporting server is still needed.

### Category menu

Category-selection icons no longer show vanilla attribute tooltips such as “When in Main Hand,” attack damage, attack speed, or armor stats below the category description. Names, item counts, and browse instructions are retained. The change is applied to menu icon copies, preserving the attributes of actual shop items and custom item templates. Icons without explicit attributes receive a hidden zero-value modifier for compatibility with Paper's vanilla-attribute hiding behavior.

Install the JAR and restart. No config edits or website update are required.

### Config preservation and currency overrides

Missing config defaults are inserted as new lines inside the relevant section. Upgrading preserves existing values, comments, quote styles, Unicode, and Windows line endings. Existing shop item entries are not repopulated. The combined YAML is validated before replacement; malformed files are left untouched.

A symbol-only currency format, such as `economy.currency_format: "🪙"`, now displays `🪙 75.51` in game and on the website. Previously, a value without `<money>` was silently ignored and could fall back to the economy's dollar format. The stored value is preserved. Empty formats still inherit Vault/CoinsEngine formatting, and explicit suffix templates such as `"<money> 🪙"` retain their placement.

Startup and reload logs include the economy provider, its formatted sample, the configured override, and the resulting shop format. This distinguishes a provider returning dollars from an unapplied override. Install the new JAR and restart; website files already containing currency-display support need no update for symbol-only overrides.

### Economy formatting

Currency symbols now follow the economy provider across in-game shops, player listings, admin price displays, messages, and the recent-transaction placeholder. LiteEco's configured coin symbol is inherited through Vault's formatter. Older installed messages that prefix `{price}` with a dollar sign are handled without editing the owner's message file.

The website inherits the provider's currency prefix/suffix instead of assuming dollars. Optional `economy.currency_format`, `currency_singular_name`, and `currency_plural_name` settings provide a shared display override, also editable in Administration → Configuration → Economy. Formats include `🪙 <money>`, `<money> 🪙`, and `<money> <currency>`. The override uses grouped numbers with two decimal places. Payment currencies and balances are unaffected. Website currency text is escaped when rendered into HTML.

Replace the JAR, restart the server, then run `/shopadmin webupdate`. File-based configuration changes use `/shopadmin reload`.

## 3.0.5

This update fixes custom items missing from the in-game search. The previous search only checked ordinary Minecraft material entries, so a newly registered watering can could appear in Gardening but disappear when you searched for it. Search now includes those separately registered custom entries.

- **Search by the name shown on the item.** Oraxen, Nexo, ItemsAdder, ValhallaMMO, and saved custom variants are included. Item names keep their normal priority over fallback names, and formatting codes do not affect matching. Capital letters, underscores, and extra spaces are handled consistently.
- **Category searches include their custom items.** Searching Gardening finds custom entries assigned to Gardening. The all-items search includes custom entries across categories. Disabled entries stay hidden.
- **Trade directly from a custom search result.** Results use the same custom item display, stock, pricing, and buying/selling controls as the category shop. Clicking keeps the custom item's ID and template, so sales update its stock and leave other items that share its Minecraft material alone. Dialog and Bedrock controls return to the same search.
- **More than 45 matches are reachable.** Previous and next buttons let players browse every page of results instead of silently dropping matches after the first page.
- **Current trading settings still apply.** If selling is disabled after a search opens, the old result cannot bypass that setting.

Replace the plugin JAR with 3.0.5 and restart the server. This fix does not require changes to custom item IDs, categories, or website files.

Validation: 114 Java tests pass. Regression tests cover all five physical delivery methods, visible names, category boundaries, disabled entries, pagination, custom sales, and dialog routing. These checks use isolated fixtures; the reporting server still needs a live check.

## 3.0.4

This update fixes the remaining problems with custom items. In 3.0.3, an item could be added to the right category but still behave like a purchase-only Server Shop entry. It could also be missing from that category in the web editor. Custom items can now be traded and managed in their assigned categories.

- **Players can sell custom items.** This covers physical items from Oraxen, Nexo, ItemsAdder, and ValhallaMMO, as well as saved custom item variants. Right-click selling in the shop and player inventory works, along with `/shop sellhand`, `/shop sellall`, and `/shop sellall hand`.
- **Each custom item has its own stock and settings.** Its price, stock, storage limits, and buying/selling switches are separate from the ordinary Minecraft item underneath it. Selling a custom watering can, for example, updates that can's stock rather than the stock of its base material.
- **Sales match the actual custom item.** Ordinary items and other custom variants that share the same base material are left alone. `/shop sellhand` sells only the stack in your hand; `/shop sellall hand` sells matching stacks throughout your inventory.
- **Custom items appear in the web category editor.** You can change their name, category, price, stock, shortage settings, and availability. The bulk buying and selling controls also work with custom entries.
- **Removing a custom entry now removes it from the shop configuration.** The web editor no longer just hides it. You can still hide an entry temporarily with the Item disabled switch.
- **Open menus use the current item settings when you trade.** Price changes, permissions, and disabled entries are checked again before the transaction. If a custom item provider is unavailable or an entry has been deleted, the trade is rejected instead of substituting an ordinary item. Permissions, commands, and other rewards remain purchase-only.
- **Website update checks follow website changes.** A plugin version change alone no longer asks you to replace unchanged web files. Updated bundles and missing files are detected, while manual updates continue to back up your existing files.

To update, replace the plugin JAR and restart the server. Run `/shopadmin webupdate`, then refresh your browser to load the new editor.

Existing custom categories and delivery IDs are kept. Older entries without a category stay in Server Shop; you can move them using the category field in the web editor. Review the starting stock and storage limits for existing custom entries, since their stock is now tracked independently.

Validation: 108 Java tests and 7 website tests pass, including custom-item sales, stock limits, mixed bulk edits, and removal. Browser checks cover category visibility, saving custom entries, and selling-switch persistence. Testing on the reporting server is still needed.

## 3.0.3

- Website update checks now use content revisions rather than the plugin version. Plugin-only updates and line-ending differences no longer trigger website updates; changed bundles are detected even within the same plugin version. Existing files, customizations, and backups retain the manual-update behavior.
- Added `/shop sellall hand` to sell all sellable stacks matching the held item across the inventory, using the existing `dynamicshop.use.sellall` permission. It preserves item-template checks, stock limits, pricing, cooldowns, and payment handling; plain `/shop sellall` still sells all sellable types.
- Fixed `/shopadmin add item <price> [category]` ignoring the category for Oraxen, Nexo, ItemsAdder, and ValhallaMMO items. These entries now use the requested category (or the normal material category when omitted), persist it across reloads, and retain their custom delivery method. Existing entries can be moved by setting `special_items.<id>.category` and running `/shopadmin reload`.
- Resolved Java formatting and compatibility findings and simplified command, transaction, GUI, pricing-data, and web-admin code without changing their intended behavior.
- Removed duplicate configuration and message keys while retaining their effective values, and consolidated repeated stock, inflation, and trading-instruction lore.
- Corrected website request options and admin access handling. Web bundles now use consistent line endings so Windows and Linux produce identical cache hashes.
- Added regression tests and CI checks for Java quality, valid bundled YAML, website requests, and generated web assets. Generated bundles are excluded only from duplicate-code analysis; the source remains checked.

## 3.0.2

- Added DSX Exchange as the sixth selectable website design, with a quote board, ask/bid prices, spread, stock, recent trade ticker, and recorded transaction activity. It shares the catalog filters, watchlist, item history, and administration controls.
- Added eight complete color palettes and expanded accent choices to twelve. Each design retains its own appearance settings.
- Added one combined bStats chart for Oraxen, Nexo, ItemsAdder, and No custom adder. Servers with multiple supported adders contribute to each enabled integration.
- Added bStats charts for online player ranges, custom GUI configuration, dynamic pricing, time-inflation adoption and activity, and completed transaction counts over a bounded 30-minute window. The inflation activity chart measures trades while inflation is enabled, not proof that it increased a price.
- Kept transaction metrics bounded in memory, cached their enabled setting, and removed item construction from GUI detection. Reports use bStats' existing collection schedule and background submission.
- Includes the Oraxen integration, Floodgate-based Bedrock GUI fallback, message formatting fixes, and saved-appearance loading fix from the preceding 3.0.1 work.

Install the new JAR and restart. Run `/shopadmin webupdate` to back up and replace bundled website files, then select DSX Exchange in Administration → Appearance if desired. Custom edits to bundled web files are backed up before replacement; server settings and shop data are retained.

The plugin owner must register the custom chart IDs and types described in [BSTATS.md](BSTATS.md). The code does not automatically create charts on the bStats dashboard.

Validation: 72 automated tests pass; browser checks cover DSX selection and persistence, quote filtering, empty results, watchlist actions, item history, and the shared palette editor. The [GitHub Pages demo](https://selfservice0.github.io/DynamicShop/?design=dsx#catalog) contains demonstration data.
