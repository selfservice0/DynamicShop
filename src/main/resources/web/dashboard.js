// Global state
let allTransactions = [];
let filteredTransactions = [];
let currentPage = 1;
const itemsPerPage = 50;
let sortColumn = 'timestamp';
let sortDirection = 'desc';
let timeRange = 'all'; // all, 1h, 24h, 7d, 30d
let priceChart = null;
let activityChart = null;

// Initialize on page load
document.addEventListener('DOMContentLoaded', () => {
    loadData();
    setInterval(loadData, 30000); // Auto-refresh every 30 seconds
    initCharts();
});

// Load all data
async function loadData() {
    try {
        await Promise.all([
            loadTransactions(),
            loadStats(),
            loadEconomyHealth(),
            loadLeaderboards(),
            loadTrends()
        ]);
        updateLastUpdateTime();
    } catch (error) {
        console.error('Error loading data:', error);
    }
}

// Load transactions
async function loadTransactions() {
    try {
        const response = await fetch('/api/recent?limit=1000');
        allTransactions = await response.json();
        applyTimeFilter();
        renderTransactions();
        updateInsights();
    } catch (error) {
        console.error('Error loading transactions:', error);
        document.getElementById('transactionsBody').innerHTML =
            '<tr><td colspan="7" class="loading">Error loading transactions</td></tr>';
    }
}

// Load stats
async function loadStats() {
    try {
        const response = await fetch('/api/stats');
        const stats = await response.json();

        document.getElementById('totalTransactions').textContent = stats.total.toLocaleString();
        document.getElementById('totalMoney').textContent = '$' + stats.totalMoney.toLocaleString('en-US', {
            minimumFractionDigits: 2,
            maximumFractionDigits: 2
        });
        document.getElementById('totalBuys').textContent = stats.buys.toLocaleString();
        document.getElementById('totalSells').textContent = stats.sells.toLocaleString();
    } catch (error) {
        console.error('Error loading stats:', error);
    }
}

// Load economy health metrics
async function loadEconomyHealth() {
    try {
        const response = await fetch('/api/analytics/economy');
        const health = await response.json();

        // Update health metrics
        document.getElementById('buyRatio').textContent =
            (health.buyRatio * 100).toFixed(1) + '%';

        document.getElementById('velocity').textContent =
            health.velocity + ' txs/hour';

        const netFlow = health.netFlow;
        const flowElement = document.getElementById('netFlow');
        flowElement.textContent = '$' + Math.abs(netFlow).toLocaleString('en-US', {
            minimumFractionDigits: 2,
            maximumFractionDigits: 2
        });
        flowElement.className = netFlow > 0 ? 'positive' : 'negative';

        document.getElementById('avgTransaction').textContent =
            '$' + health.avgTransaction.toLocaleString('en-US', {
                minimumFractionDigits: 2,
                maximumFractionDigits: 2
            });

        document.getElementById('uniqueItems').textContent = health.uniqueItems;
        document.getElementById('uniquePlayers').textContent = health.uniquePlayers;

    } catch (error) {
        console.error('Error loading economy health:', error);
    }
}

// Load leaderboards
async function loadLeaderboards() {
    try {
        const [earners, spenders, traders] = await Promise.all([
            fetch('/api/analytics/leaderboard?type=earners&limit=5').then(r => r.json()),
            fetch('/api/analytics/leaderboard?type=spenders&limit=5').then(r => r.json()),
            fetch('/api/analytics/leaderboard?type=traders&limit=5').then(r => r.json())
        ]);

        renderLeaderboard('topEarners', earners, 'netProfit');
        renderLeaderboard('topSpenders', spenders, 'spent');
        renderLeaderboard('topTraders', traders, 'trades');

    } catch (error) {
        console.error('Error loading leaderboards:', error);
    }
}

// Render leaderboard
function renderLeaderboard(elementId, data, valueKey) {
    const element = document.getElementById(elementId);
    if (!element) return;

    const html = data.map((entry, index) => {
        let value;
        if (valueKey === 'netProfit' || valueKey === 'spent') {
            value = '$' + Math.abs(entry[valueKey]).toLocaleString('en-US', {
                maximumFractionDigits: 0
            });
        } else {
            value = entry[valueKey].toLocaleString();
        }

        return `
            <div class="leaderboard-item">
                <span class="rank">#${index + 1}</span>
                <span class="player-name">${escapeHtml(entry.player)}</span>
                <span class="player-value">${value}</span>
            </div>
        `;
    }).join('');

    element.innerHTML = html || '<div class="loading">No data</div>';
}

// Load trends
async function loadTrends() {
    try {
        const response = await fetch('/api/analytics/trends?limit=10');
        const trends = await response.json();

        renderTrendList('hotItems', trends.hot, 'hot');
        renderTrendList('risingItems', trends.rising, 'rising');
        renderTrendList('fallingItems', trends.falling, 'falling');

    } catch (error) {
        console.error('Error loading trends:', error);
    }
}

// Render trend list
function renderTrendList(elementId, items, type) {
    const element = document.getElementById(elementId);
    if (!element) return;

    const html = items.map(item => {
        const changeIcon = item.changePercent > 0 ? '📈' : '📉';
        const changeClass = item.changePercent > 0 ? 'positive' : 'negative';

        return `
            <div class="trend-item" onclick="showItemDetails('${escapeHtml(item.item)}')">
                <div class="trend-header">
                    <span class="trend-name">${prettifyItem(item.item)}</span>
                    <span class="trend-change ${changeClass}">
                        ${changeIcon} ${Math.abs(item.changePercent).toFixed(0)}%
                    </span>
                </div>
                <div class="trend-details">
                    <span>${item.recentCount} recent txs</span>
                    <span>$${item.avgPrice.toFixed(2)}/unit</span>
                </div>
            </div>
        `;
    }).join('');

    element.innerHTML = html || '<div class="loading">No data</div>';
}

// Show item details modal
async function showItemDetails(item) {
    const modal = document.getElementById('itemModal');
    const modalTitle = document.getElementById('modalItemName');
    const modalBody = document.getElementById('modalBody');

    modalTitle.textContent = prettifyItem(item);
    modalBody.innerHTML = '<div class="loading">Loading price history...</div>';
    modal.style.display = 'flex';

    try {
        const response = await fetch(`/api/analytics/price-history/${encodeURIComponent(item)}?hours=168`);
        const history = await response.json();

        renderPriceChart(history);

        // Show recent transactions for this item
        const itemTxs = filteredTransactions
            .filter(tx => tx.item === item)
            .slice(0, 20);

        const txHtml = `
            <div class="modal-section">
                <h3>Recent Transactions</h3>
                <div class="modal-transactions">
                    ${itemTxs.map(tx => `
                        <div class="modal-tx">
                            <span>${formatTime(tx.timestamp)}</span>
                            <span class="type-badge ${tx.type.toLowerCase()}">${tx.type}</span>
                            <span>${tx.amount}x @ $${tx.price.toFixed(2)}</span>
                            <span>${escapeHtml(tx.playerName)}</span>
                        </div>
                    `).join('')}
                </div>
            </div>
        `;

        modalBody.innerHTML = `
            <div class="modal-section">
                <canvas id="priceChart" width="600" height="300"></canvas>
            </div>
            ${txHtml}
        `;

        // Render chart after DOM update
        setTimeout(() => renderPriceChart(history), 100);

    } catch (error) {
        modalBody.innerHTML = '<div class="error">Failed to load item details</div>';
        console.error('Error loading item details:', error);
    }
}

// Close modal
function closeModal() {
    document.getElementById('itemModal').style.display = 'none';
    if (priceChart) {
        priceChart.destroy();
        priceChart = null;
    }
}

// Render price chart
function renderPriceChart(history) {
    const canvas = document.getElementById('priceChart');
    if (!canvas) return;

    const ctx = canvas.getContext('2d');

    if (priceChart) {
        priceChart.destroy();
    }

    // Use Chart.js if available
    if (typeof Chart !== 'undefined') {
        priceChart = new Chart(ctx, {
            type: 'line',
            data: {
                labels: history.map(h => new Date(h.timestamp).toLocaleDateString() + ' ' +
                    new Date(h.timestamp).getHours() + ':00'),
                datasets: [
                    {
                        label: 'Buy Price',
                        data: history.map(h => h.avgBuyPrice),
                        borderColor: '#10b981',
                        backgroundColor: 'rgba(16, 185, 129, 0.1)',
                        tension: 0.4
                    },
                    {
                        label: 'Sell Price',
                        data: history.map(h => h.avgSellPrice),
                        borderColor: '#ef4444',
                        backgroundColor: 'rgba(239, 68, 68, 0.1)',
                        tension: 0.4
                    }
                ]
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                plugins: {
                    legend: {
                        labels: { color: '#f1f5f9' }
                    }
                },
                scales: {
                    x: {
                        ticks: { color: '#cbd5e1' },
                        grid: { color: '#334155' }
                    },
                    y: {
                        ticks: { color: '#cbd5e1' },
                        grid: { color: '#334155' }
                    }
                }
            }
        });
    }
}

// Initialize charts
function initCharts() {
    // Load Chart.js from CDN if not already loaded
    if (typeof Chart === 'undefined') {
        const script = document.createElement('script');
        script.src = 'https://cdn.jsdelivr.net/npm/chart.js@4.4.0/dist/chart.umd.min.js';
        document.head.appendChild(script);
    }
}

// Apply time filter
function applyTimeFilter() {
    const now = new Date();
    let cutoff;

    switch(timeRange) {
        case '1h':
            cutoff = new Date(now - 60 * 60 * 1000);
            break;
        case '24h':
            cutoff = new Date(now - 24 * 60 * 60 * 1000);
            break;
        case '7d':
            cutoff = new Date(now - 7 * 24 * 60 * 60 * 1000);
            break;
        case '30d':
            cutoff = new Date(now - 30 * 24 * 60 * 60 * 1000);
            break;
        default:
            filteredTransactions = [...allTransactions];
            return;
    }

    filteredTransactions = allTransactions.filter(tx =>
        new Date(tx.timestamp) > cutoff
    );
}

// Set time range
function setTimeRange(range) {
    timeRange = range;

    // Update button states
    document.querySelectorAll('.time-btn').forEach(btn => {
        btn.classList.remove('active');
    });
    event.target.classList.add('active');

    applyTimeFilter();
    renderTransactions();
    updateInsights();
}

// Render transactions table
function renderTransactions() {
    const tbody = document.getElementById('transactionsBody');

    if (filteredTransactions.length === 0) {
        tbody.innerHTML = '<tr><td colspan="7" class="loading">No transactions found</td></tr>';
        return;
    }

    // Sort
    sortTransactions();

    // Paginate
    const totalPages = Math.ceil(filteredTransactions.length / itemsPerPage);
    const startIndex = (currentPage - 1) * itemsPerPage;
    const endIndex = startIndex + itemsPerPage;
    const pageTransactions = filteredTransactions.slice(startIndex, endIndex);

    // Render
    tbody.innerHTML = pageTransactions.map(tx => {
        const priceClass = tx.price > 1000 ? 'high-value' : '';
        return `
            <tr onclick="showItemDetails('${escapeHtml(tx.item)}')">
                <td>${formatTime(tx.timestamp)}</td>
                <td>${escapeHtml(tx.playerName)}</td>
                <td><span class="type-badge ${tx.type.toLowerCase()}">${tx.type}</span></td>
                <td class="item-cell">${prettifyItem(tx.item)}</td>
                <td>${tx.amount.toLocaleString()}</td>
                <td class="${priceClass}">$${tx.price.toLocaleString('en-US', {minimumFractionDigits: 2, maximumFractionDigits: 2})}</td>
                <td>${escapeHtml(tx.category || 'N/A')}</td>
            </tr>
        `;
    }).join('');

    // Update pagination
    document.getElementById('currentPage').textContent = currentPage;
    document.getElementById('totalPages').textContent = totalPages;
    document.getElementById('prevBtn').disabled = currentPage === 1;
    document.getElementById('nextBtn').disabled = currentPage === totalPages || totalPages === 0;

    // Update result count
    document.getElementById('resultCount').textContent =
        `Showing ${startIndex + 1}-${Math.min(endIndex, filteredTransactions.length)} of ${filteredTransactions.length}`;
}

// Sort transactions
function sortTransactions() {
    filteredTransactions.sort((a, b) => {
        let aVal = a[sortColumn];
        let bVal = b[sortColumn];

        if (sortColumn === 'timestamp') {
            aVal = new Date(aVal).getTime();
            bVal = new Date(bVal).getTime();
        } else if (typeof aVal === 'string') {
            aVal = aVal.toLowerCase();
            bVal = bVal.toLowerCase();
        }

        if (sortDirection === 'asc') {
            return aVal > bVal ? 1 : -1;
        } else {
            return aVal < bVal ? 1 : -1;
        }
    });
}

// Sort table
function sortTable(column) {
    if (sortColumn === column) {
        sortDirection = sortDirection === 'asc' ? 'desc' : 'asc';
    } else {
        sortColumn = column;
        sortDirection = 'desc';
    }
    renderTransactions();
}

// Apply filters
function applyFilters() {
    const playerFilter = document.getElementById('playerFilter').value.toLowerCase();
    const itemFilter = document.getElementById('itemFilter').value.toLowerCase();
    const typeFilter = document.getElementById('typeFilter').value;

    filteredTransactions = allTransactions.filter(tx => {
        const matchesPlayer = !playerFilter || tx.playerName.toLowerCase().includes(playerFilter);
        const matchesItem = !itemFilter || tx.item.toLowerCase().includes(itemFilter);
        const matchesType = !typeFilter || tx.type === typeFilter;

        return matchesPlayer && matchesItem && matchesType;
    });

    applyTimeFilter();
    currentPage = 1;
    renderTransactions();
}

// Clear filters
function clearFilters() {
    document.getElementById('playerFilter').value = '';
    document.getElementById('itemFilter').value = '';
    document.getElementById('typeFilter').value = '';
    filteredTransactions = [...allTransactions];
    currentPage = 1;
    renderTransactions();
}

// Pagination
function nextPage() {
    const totalPages = Math.ceil(filteredTransactions.length / itemsPerPage);
    if (currentPage < totalPages) {
        currentPage++;
        renderTransactions();
        window.scrollTo({ top: 0, behavior: 'smooth' });
    }
}

function previousPage() {
    if (currentPage > 1) {
        currentPage--;
        renderTransactions();
        window.scrollTo({ top: 0, behavior: 'smooth' });
    }
}

// Update insights
function updateInsights() {
    updateTopItems();
    updateTopPlayers();
    updateCategoryStats();
}

// Top items
function updateTopItems() {
    const itemCounts = {};

    filteredTransactions.forEach(tx => {
        const item = tx.item;
        if (!itemCounts[item]) {
            itemCounts[item] = { count: 0, volume: 0 };
        }
        itemCounts[item].count += tx.amount;
        itemCounts[item].volume += tx.price;
    });

    const sorted = Object.entries(itemCounts)
        .sort((a, b) => b[1].volume - a[1].volume)
        .slice(0, 10);

    const html = sorted.map(([item, data]) => `
        <div class="insight-item" onclick="showItemDetails('${escapeHtml(item)}')">
            <span class="insight-name">${prettifyItem(item)}</span>
            <span class="insight-value">$${data.volume.toLocaleString('en-US', {maximumFractionDigits: 0})}</span>
        </div>
    `).join('');

    document.getElementById('topItems').innerHTML = html || '<div class="loading">No data</div>';
}

// Top players
function updateTopPlayers() {
    const playerStats = {};

    filteredTransactions.forEach(tx => {
        const player = tx.playerName;
        if (!playerStats[player]) {
            playerStats[player] = { count: 0, volume: 0 };
        }
        playerStats[player].count++;
        playerStats[player].volume += tx.price;
    });

    const sorted = Object.entries(playerStats)
        .sort((a, b) => b[1].count - a[1].count)
        .slice(0, 10);

    const html = sorted.map(([player, data]) => `
        <div class="insight-item">
            <span class="insight-name">${escapeHtml(player)}</span>
            <span class="insight-value">${data.count} txs</span>
        </div>
    `).join('');

    document.getElementById('topPlayers').innerHTML = html || '<div class="loading">No data</div>';
}

// Category stats
function updateCategoryStats() {
    const categoryStats = {};

    filteredTransactions.forEach(tx => {
        const category = tx.category || 'Unknown';
        if (!categoryStats[category]) {
            categoryStats[category] = 0;
        }
        categoryStats[category]++;
    });

    const sorted = Object.entries(categoryStats)
        .sort((a, b) => b[1] - a[1])
        .slice(0, 10);

    const total = filteredTransactions.length;
    const html = sorted.map(([category, count]) => {
        const percent = ((count / total) * 100).toFixed(1);
        return `
            <div class="insight-item">
                <span class="insight-name">${escapeHtml(category)}</span>
                <span class="insight-value">${percent}%</span>
            </div>
        `;
    }).join('');

    document.getElementById('categoryStats').innerHTML = html || '<div class="loading">No data</div>';
}

// Refresh data
async function refreshData() {
    const btn = document.querySelector('.refresh-btn');
    btn.disabled = true;
    btn.style.opacity = '0.7';

    await loadData();

    btn.disabled = false;
    btn.style.opacity = '1';
}

// Update last update time
function updateLastUpdateTime() {
    const now = new Date();
    const timeStr = now.toLocaleTimeString();
    document.getElementById('lastUpdate').textContent = timeStr;
}

// Utility functions
function formatTime(timestamp) {
    const date = new Date(timestamp);
    const now = new Date();
    const diffMs = now - date;
    const diffMins = Math.floor(diffMs / 60000);

    if (diffMins < 1) return 'Just now';
    if (diffMins < 60) return `${diffMins}m ago`;
    if (diffMins < 1440) return `${Math.floor(diffMins / 60)}h ago`;

    return date.toLocaleDateString() + ' ' + date.toLocaleTimeString([], {
        hour: '2-digit',
        minute: '2-digit'
    });
}

function prettifyItem(item) {
    return item.split('_')
        .map(word => word.charAt(0).toUpperCase() + word.slice(1).toLowerCase())
        .join(' ');
}

function escapeHtml(text) {
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}

// Add Enter key support for filters
document.addEventListener('DOMContentLoaded', () => {
    ['playerFilter', 'itemFilter', 'typeFilter'].forEach(id => {
        const element = document.getElementById(id);
        if (element) {
            element.addEventListener('keypress', (e) => {
                if (e.key === 'Enter') {
                    applyFilters();
                }
            });
        }
    });

    // Close modal on outside click
    window.onclick = function(event) {
        const modal = document.getElementById('itemModal');
        if (event.target === modal) {
            closeModal();
        }
    };
});