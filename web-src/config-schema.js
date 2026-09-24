const cfgSchema={
            toggles: [
                { key: 'dynamicPricingEnabled', label: 'Dynamic Pricing', desc: 'Master toggle for all price adjustments' },
                { key: 'useStockCurve', label: 'Stock Curve', desc: 'Prices drop as stock increases' },
                { key: 'useTimeInflation', label: 'Time Inflation', desc: 'Prices rise over time at zero stock' },
                { key: 'highInflationCorrectionEnabled', label: 'High Inflation Correction', desc: 'Cut time inflation when stock recovers from shortage' },
                { key: 'restrictBuyingAtZeroStock', label: 'Restrict Zero-Stock Buying', desc: 'Block purchases at 0 stock' },
                { key: 'logDynamicPricing', label: 'Debug Logging', desc: 'Log pricing calculations to console' }
            ],
            numbers: [
                { key: 'curveStrength', label: 'Curve Strength', step: 0.01, desc: '0-1, how aggressively prices drop' },
                { key: 'maxStock', label: 'Max Stock', step: 1, desc: 'Normalized stock ceiling for curve' },
                { key: 'minPriceMultiplier', label: 'Min Price Multiplier', step: 0.01, desc: 'Floor (e.g. 0.01 = 1%)' },
                { key: 'maxPriceMultiplier', label: 'Max Price Multiplier', step: 0.1, desc: 'Ceiling (e.g. 2 = 2x)' },
                { key: 'negativeStockPercentPerItem', label: 'Neg Stock %/Item', step: 0.1, desc: 'Price increase per negative stock unit' },
                { key: 'hourlyIncreasePercent', label: 'Hourly Increase %', step: 0.1, desc: 'Compound inflation at zero stock' },
                { key: 'shortageDecayPercentPerHour', label: 'Shortage Decay/Hr', step: 0.1, desc: '0 = disabled' },
                { key: 'highInflationCorrectionThresholdPercent', label: 'Correction Threshold %', step: 1, desc: 'Only correct above this time-inflation %' },
                { key: 'highInflationCorrectionReductionPercent', label: 'Correction Reduction %', step: 1, desc: 'Percent of time-inflation markup removed on recovery' }
            ],
            economy: {
                toggles: [],
                numbers: [
                    { key: 'sellTaxPercent', label: 'Sell Tax %', step: 1, desc: 'Tax on player sells (30 = 70% payout)' },
                    { key: 'transactionCooldownMs', label: 'Transaction Cooldown (ms)', step: 100, desc: '0 = disabled' }
                ]
            },
            shop: {
                toggles: [
                    { key: 'playerShopsEnabled', label: 'Player Shops', desc: 'Enable/disable player shop system' }
                ],
                numbers: [
                    { key: 'maxListingsPerPlayer', label: 'Max Listings Per Player', step: 1, desc: 'Maximum items per shop' }
                ]
            },
            web: {
                toggles: [
                    { key: 'webserverEnabled', label: 'Web Server Enabled', desc: 'Enable/disable the web server entirely (requires restart)' },
                    { key: 'webserverAdminEnabled', label: 'Admin Panel Enabled', desc: 'Enable/disable the web admin panel (requires restart)' },
                    { key: 'webserverCorsEnabled', label: 'CORS Enabled', desc: 'Allow cross-origin API requests' },
                    { key: 'webserverSslEnabled', label: 'SSL Enabled', desc: 'Serve the dashboard over HTTPS (requires restart)' },
                    { key: 'webserverForceUpdate', label: 'Force Update Files', desc: 'Back up and replace bundled web files on every startup' }
                ],
                numbers: [
                    { key: 'webserverPort', label: 'Port', step: 1, desc: 'Requires restart' },
                    { key: 'webserverBind', label: 'Bind Address', step: 0, desc: '0.0.0.0 = all interfaces', isText: true },
                    { key: 'webserverHostname', label: 'Hostname', step: 0, desc: 'Public IP/domain for admin links (empty = auto-detect)', isText: true },
                    { key: 'webserverSslKeystorePath', label: 'SSL Keystore Path', step: 0, desc: 'Relative paths resolve inside plugins/DynamicShop/', isText: true }
                ]
            },
            misc: {
                toggles: [
                    { key: 'restockEnabled', label: 'Auto Restock', desc: 'Enable automatic restocking system' },
                    { key: 'crossServerEnabled', label: 'Cross-Server Sync', desc: 'Enable P2P synchronisation between servers (requires restart)' }
                ],
                numbers: [
                    { key: 'shopMenuSize', label: 'Shop Menu Size', step: 9, desc: 'Must be multiple of 9 (27-54)' },
                    { key: 'maxRecentTransactions', label: 'Max Recent Transactions', step: 1000, desc: 'Web dashboard memory limit' },
                    { key: 'crossServerPort', label: 'Cross-Server Port', step: 1, desc: 'Port for JeroMQ sync mesh' },
                    { key: 'crossServerSaveInterval', label: 'Cross-Server Save Interval', step: 1, desc: 'In seconds. Frequency of backup to shopdata.yml' }
                ]
            }
        };
