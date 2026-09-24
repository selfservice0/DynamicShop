# Release notes

## Unreleased

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
