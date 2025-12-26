// ═══════════════════════════════════════════════════════════════════════
// DYNAMICSHOP DASHBOARD - JavaScript
// ═══════════════════════════════════════════════════════════════════════

// Global State
let allTransactions = [];
let filteredTransactions = [];
let currentPage = 1;
const itemsPerPage = 25;
let sortColumn = 'timestamp';
let sortDirection = 'desc';
let timeRange = 'all';
let currentLeaderboard = 'earners';
let currentTrends = 'hot';
let activityChart = null;
let categoryChart = null;

// ═══ INITIALIZATION ═══
document.addEventListener('DOMContentLoaded', () => {
    loadAllData();
    setupEventListeners();
    setInterval(loadAllData, 30000); // Auto-refresh every 30 seconds
});

// ═══ EVENT LISTENERS ═══
function setupEventListeners() {
    // Search input
    const txSearch = document.getElementById('txSearch');
    if (txSearch) {
        txSearch.addEventListener('input', debounce(applyFilters, 300));
    }

    // Type filter
    const typeFilter = document.getElementById('typeFilter');
    if (typeFilter) {
        typeFilter.addEventListener('change', applyFilters);
    }

    // Modal close on outside click
    document.getElementById('itemModal')?.addEventListener('click', (e) => {
        if (e.target.id === 'itemModal') closeModal();
    });

    // Escape key closes modal
    document.addEventListener('keydown', (e) => {
        if (e.key === 'Escape') closeModal();
    });
}

// ═══ DATA LOADING ═══
async function loadAllData() {
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

async function loadTransactions() {
    try {
        const response = await fetch('/api/recent?limit=500');
        allTransactions = await response.json();
        applyTimeFilter();
        renderTransactions();
        updateInsights();
        renderActivityChart();
    } catch (error) {
        console.error('Error loading transactions:', error);
        showError('transactionsBody', 'Error loading transactions');
    }
}

async function loadStats() {
    try {
        const response = await fetch('/api/stats');
        const stats = await response.json();

        animateNumber('totalTransactions', stats.total);
        animateNumber('totalMoney', stats.totalMoney, true);
        animateNumber('totalBuys', stats.buys);
        animateNumber('totalSells', stats.sells);
    } catch (error) {
        console.error('Error loading stats:', error);
    }
}

async function loadEconomyHealth() {
    try {
        const response = await fetch('/api/analytics/economy');
        const health = await response.json();

        // Update health metrics
        const buyRatioEl = document.getElementById('buyRatio');
        if (buyRatioEl) {
            buyRatioEl.textContent = (health.buyRatio * 100).toFixed(1) + '%';
        }

        const buyRatioBar = document.getElementById('buyRatioBar');
        if (buyRatioBar) {
            buyRatioBar.style.width = (health.buyRatio * 100) + '%';
        }

        setText('velocity', health.velocity + ' txs/hr');

        const netFlow = health.netFlow;
        const flowElement = document.getElementById('netFlow');
        if (flowElement) {
            flowElement.textContent = formatCurrency(Math.abs(netFlow));
            flowElement.className = 'health-value ' + (netFlow >= 0 ? 'positive' : 'negative');
        }

        setText('avgTransaction', formatCurrency(health.avgTransaction));
        setText('uniqueItems', health.uniqueItems);
        setText('uniquePlayers', health.uniquePlayers);
    } catch (error) {
        console.error('Error loading economy health:', error);
    }
}

async function loadLeaderboards() {
    try {
        const response = await fetch(`/api/analytics/leaderboard?type=${currentLeaderboard}&limit=5`);
        const data = await response.json();
        renderLeaderboard(data);
    } catch (error) {
        console.error('Error loading leaderboards:', error);
        showError('leaderboardContent', 'Error loading leaderboard');
    }
}

async function loadTrends() {
    try {
        const response = await fetch('/api/analytics/trends?limit=10');
        const data = await response.json();
        renderTrends(data[currentTrends] || []);
    } catch (error) {
        console.error('Error loading trends:', error);
        showError('trendsContent', 'Error loading trends');
    }
}

// ═══ RENDERING ═══
function renderLeaderboard(data) {
    const container = document.getElementById('leaderboardContent');
    if (!container || !data.length) {
        if (container) container.innerHTML = '<div class="loading">No data available</div>';
        return;
    }

    const valueKey = currentLeaderboard === 'traders' ? 'trades' :
        currentLeaderboard === 'spenders' ? 'spent' : 'netProfit';

    container.innerHTML = data.map((entry, i) => {
        const rankClass = i === 0 ? 'gold' : i === 1 ? 'silver' : i === 2 ? 'bronze' : '';
        const value = valueKey === 'trades' ?
            entry.trades.toLocaleString() + ' trades' :
            formatCurrency(Math.abs(entry[valueKey]));

        return `
            <div class="leaderboard-item">
                <div class="rank ${rankClass}">${i + 1}</div>
                <img class="player-avatar" 
                     src="https://mc-heads.net/avatar/${entry.player}/32" 
                     alt="${entry.player}"
                     onerror="this.src='data:image/svg+xml,<svg xmlns=%22http://www.w3.org/2000/svg%22 width=%2232%22 height=%2232%22><rect fill=%22%23334155%22 width=%2232%22 height=%2232%22/></svg>'">
                <div class="player-info">
                    <div class="player-name">${escapeHtml(entry.player)}</div>
                    <div class="player-stat">${entry.trades} trades</div>
                </div>
                <div class="player-value">${value}</div>
            </div>
        `;
    }).join('');
}

function renderTrends(items) {
    const container = document.getElementById('trendsContent');
    if (!container || !items.length) {
        if (container) container.innerHTML = '<div class="loading">No trend data</div>';
        return;
    }

    container.innerHTML = items.map(item => {
        const isPositive = item.changePercent >= 0;
        return `
            <div class="trend-item" onclick="showItemDetails('${escapeHtml(item.item)}')">
                <span class="trend-icon">${isPositive ? '📈' : '📉'}</span>
                <div class="trend-info">
                    <div class="trend-name">${prettifyItem(item.item)}</div>
                    <div class="trend-stats">${item.recentCount} recent • ${formatCurrency(item.avgPrice)}/unit</div>
                </div>
                <span class="trend-change ${isPositive ? 'positive' : 'negative'}">
                    ${isPositive ? '+' : ''}${item.changePercent.toFixed(0)}%
                </span>
            </div>
        `;
    }).join('');
}

function renderTransactions() {
    const tbody = document.getElementById('transactionsBody');
    if (!tbody) return;

    if (!filteredTransactions.length) {
        tbody.innerHTML = '<tr><td colspan="6" class="loading">No transactions found</td></tr>';
        return;
    }

    sortTransactions();

    const totalPages = Math.ceil(filteredTransactions.length / itemsPerPage);
    const start = (currentPage - 1) * itemsPerPage;
    const pageData = filteredTransactions.slice(start, start + itemsPerPage);

    tbody.innerHTML = pageData.map(tx => `
        <tr onclick="showItemDetails('${escapeHtml(tx.item)}')">
            <td>${formatTime(tx.timestamp)}</td>
            <td>${escapeHtml(tx.playerName)}</td>
            <td><span class="type-badge ${tx.type.toLowerCase()}">${tx.type}</span></td>
            <td>${prettifyItem(tx.item)}</td>
            <td>${tx.amount.toLocaleString()}</td>
            <td>${formatCurrency(tx.price)}</td>
        </tr>
    `).join('');

    // Update pagination
    setText('currentPage', currentPage);
    setText('totalPages', totalPages || 1);
    document.getElementById('prevBtn').disabled = currentPage === 1;
    document.getElementById('nextBtn').disabled = currentPage >= totalPages;
}

function renderActivityChart() {
    const canvas = document.getElementById('activityChart');
    if (!canvas) return;

    // Group transactions by hour
    const hourlyData = {};
    const now = new Date();
    for (let i = 23; i >= 0; i--) {
        const hour = new Date(now - i * 60 * 60 * 1000);
        const key = hour.toLocaleDateString() + ' ' + hour.getHours() + ':00';
        hourlyData[key] = { buys: 0, sells: 0 };
    }

    filteredTransactions.forEach(tx => {
        const date = new Date(tx.timestamp);
        const key = date.toLocaleDateString() + ' ' + date.getHours() + ':00';
        if (hourlyData[key]) {
            if (tx.type === 'BUY') hourlyData[key].buys++;
            else hourlyData[key].sells++;
        }
    });

    const labels = Object.keys(hourlyData).map(k => k.split(' ')[1]);
    const buyData = Object.values(hourlyData).map(v => v.buys);
    const sellData = Object.values(hourlyData).map(v => v.sells);

    if (activityChart) activityChart.destroy();

    activityChart = new Chart(canvas, {
        type: 'bar',
        data: {
            labels,
            datasets: [
                {
                    label: 'Purchases',
                    data: buyData,
                    backgroundColor: 'rgba(16, 185, 129, 0.7)',
                    borderRadius: 4
                },
                {
                    label: 'Sales',
                    data: sellData,
                    backgroundColor: 'rgba(239, 68, 68, 0.7)',
                    borderRadius: 4
                }
            ]
        },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            plugins: {
                legend: {
                    labels: { color: '#CBD5E1' }
                }
            },
            scales: {
                x: {
                    stacked: true,
                    ticks: { color: '#94A3B8' },
                    grid: { color: 'rgba(51, 65, 85, 0.5)' }
                },
                y: {
                    stacked: true,
                    ticks: { color: '#94A3B8' },
                    grid: { color: 'rgba(51, 65, 85, 0.5)' }
                }
            }
        }
    });
}

function updateInsights() {
    updateTopItems();
    updateTopPlayers();
    renderCategoryChart();
}

function updateTopItems() {
    const container = document.getElementById('topItems');
    if (!container) return;

    const itemCounts = {};
    filteredTransactions.forEach(tx => {
        if (!itemCounts[tx.item]) itemCounts[tx.item] = { count: 0, volume: 0 };
        itemCounts[tx.item].count += tx.amount;
        itemCounts[tx.item].volume += tx.price;
    });

    const sorted = Object.entries(itemCounts)
        .sort((a, b) => b[1].volume - a[1].volume)
        .slice(0, 8);

    container.innerHTML = sorted.map(([item, data]) => `
        <div class="insight-item" onclick="showItemDetails('${escapeHtml(item)}')">
            <span class="insight-name">${prettifyItem(item)}</span>
            <span class="insight-value">${formatCurrency(data.volume)}</span>
        </div>
    `).join('') || '<div class="loading">No data</div>';
}

function updateTopPlayers() {
    const container = document.getElementById('topPlayers');
    if (!container) return;

    const playerStats = {};
    filteredTransactions.forEach(tx => {
        if (!playerStats[tx.playerName]) playerStats[tx.playerName] = 0;
        playerStats[tx.playerName]++;
    });

    const sorted = Object.entries(playerStats)
        .sort((a, b) => b[1] - a[1])
        .slice(0, 8);

    container.innerHTML = sorted.map(([player, count]) => `
        <div class="insight-item">
            <span class="insight-name">${escapeHtml(player)}</span>
            <span class="insight-value">${count} txs</span>
        </div>
    `).join('') || '<div class="loading">No data</div>';
}

function renderCategoryChart() {
    const canvas = document.getElementById('categoryChart');
    if (!canvas) return;

    const categories = {};
    filteredTransactions.forEach(tx => {
        const cat = tx.category || 'Unknown';
        categories[cat] = (categories[cat] || 0) + 1;
    });

    const labels = Object.keys(categories);
    const data = Object.values(categories);
    const colors = [
        '#8B5CF6', '#06B6D4', '#10B981', '#F59E0B',
        '#EF4444', '#EC4899', '#6366F1', '#14B8A6'
    ];

    if (categoryChart) categoryChart.destroy();

    categoryChart = new Chart(canvas, {
        type: 'doughnut',
        data: {
            labels,
            datasets: [{
                data,
                backgroundColor: colors.slice(0, labels.length),
                borderWidth: 0
            }]
        },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            plugins: {
                legend: {
                    position: 'right',
                    labels: {
                        color: '#CBD5E1',
                        boxWidth: 12,
                        padding: 8
                    }
                }
            }
        }
    });
}

// ═══ MODAL ═══
async function showItemDetails(item) {
    const modal = document.getElementById('itemModal');
    const modalTitle = document.getElementById('modalItemName');
    const modalBody = document.getElementById('modalBody');

    if (!modal || !modalTitle || !modalBody) return;

    modalTitle.textContent = prettifyItem(item);
    modalBody.innerHTML = '<div class="loading-spinner"></div>';
    modal.classList.add('active');

    try {
        const [itemData, priceHistory] = await Promise.all([
            fetch(`/api/shop/item/${encodeURIComponent(item)}`).then(r => r.json()),
            fetch(`/api/analytics/price-history/${encodeURIComponent(item)}?hours=168`).then(r => r.json())
        ]);

        const recentTxs = itemData.recentTransactions || [];

        modalBody.innerHTML = `
            <div class="modal-item-info">
                <img src="${itemData.imageUrl}" alt="${itemData.displayName}" 
                     style="width: 64px; height: 64px; margin-right: 1rem;"
                     onerror="this.style.display='none'">
                <div>
                    <div style="font-size: 1.25rem; font-weight: 600;">${itemData.displayName}</div>
                    <div style="color: var(--text-muted); margin-bottom: 0.5rem;">${itemData.category}</div>
                    <div style="display: flex; gap: 1rem;">
                        <span style="color: var(--success);">Buy: ${formatCurrency(itemData.buyPrice)}</span>
                        <span style="color: var(--danger);">Sell: ${formatCurrency(itemData.sellPrice)}</span>
                    </div>
                </div>
            </div>
            <div style="display: grid; grid-template-columns: repeat(3, 1fr); gap: 1rem; margin: 1.5rem 0;">
                <div style="background: var(--bg-tertiary); padding: 1rem; border-radius: 8px; text-align: center;">
                    <div style="color: var(--text-muted); font-size: 0.8rem;">Stock</div>
                    <div style="font-size: 1.25rem; font-weight: 600;">${itemData.stock?.toFixed(0) || 0}</div>
                </div>
                <div style="background: var(--bg-tertiary); padding: 1rem; border-radius: 8px; text-align: center;">
                    <div style="color: var(--text-muted); font-size: 0.8rem;">Total Buys</div>
                    <div style="font-size: 1.25rem; font-weight: 600;">${itemData.totalBuys || 0}</div>
                </div>
                <div style="background: var(--bg-tertiary); padding: 1rem; border-radius: 8px; text-align: center;">
                    <div style="color: var(--text-muted); font-size: 0.8rem;">Total Sells</div>
                    <div style="font-size: 1.25rem; font-weight: 600;">${itemData.totalSells || 0}</div>
                </div>
            </div>
            <h3 style="margin-bottom: 1rem;">Price History (7 Days)</h3>
            <div style="height: 200px; margin-bottom: 1.5rem;">
                <canvas id="modalPriceChart"></canvas>
            </div>
            <h3 style="margin-bottom: 1rem;">Recent Transactions</h3>
            <div style="max-height: 200px; overflow-y: auto;">
                ${recentTxs.slice(0, 10).map(tx => `
                    <div style="display: flex; justify-content: space-between; padding: 0.75rem; background: var(--bg-tertiary); border-radius: 6px; margin-bottom: 0.5rem;">
                        <span>${formatTime(tx.timestamp)}</span>
                        <span class="type-badge ${tx.type.toLowerCase()}">${tx.type}</span>
                        <span>${tx.amount}x @ ${formatCurrency(tx.price)}</span>
                        <span>${escapeHtml(tx.playerName)}</span>
                    </div>
                `).join('') || '<div class="loading">No recent transactions</div>'}
            </div>
        `;

        // Render price chart
        setTimeout(() => {
            const canvas = document.getElementById('modalPriceChart');
            if (canvas && priceHistory.length) {
                new Chart(canvas, {
                    type: 'line',
                    data: {
                        labels: priceHistory.map(h => h.timestamp.split(' ')[1] || h.timestamp),
                        datasets: [
                            {
                                label: 'Buy Price',
                                data: priceHistory.map(h => h.avgBuyPrice),
                                borderColor: '#10B981',
                                backgroundColor: 'rgba(16, 185, 129, 0.1)',
                                tension: 0.4,
                                fill: true
                            },
                            {
                                label: 'Sell Price',
                                data: priceHistory.map(h => h.avgSellPrice),
                                borderColor: '#EF4444',
                                backgroundColor: 'rgba(239, 68, 68, 0.1)',
                                tension: 0.4,
                                fill: true
                            }
                        ]
                    },
                    options: {
                        responsive: true,
                        maintainAspectRatio: false,
                        plugins: { legend: { labels: { color: '#CBD5E1' } } },
                        scales: {
                            x: { ticks: { color: '#94A3B8' }, grid: { color: 'rgba(51, 65, 85, 0.5)' } },
                            y: { ticks: { color: '#94A3B8' }, grid: { color: 'rgba(51, 65, 85, 0.5)' } }
                        }
                    }
                });
            }
        }, 100);
    } catch (error) {
        console.error('Error loading item details:', error);
        modalBody.innerHTML = '<div class="loading">Error loading item details</div>';
    }
}

function closeModal() {
    const modal = document.getElementById('itemModal');
    if (modal) modal.classList.remove('active');
}

// ═══ FILTERS & SORTING ═══
function applyTimeFilter() {
    const now = new Date();
    let cutoff;

    switch (timeRange) {
        case '1h': cutoff = new Date(now - 60 * 60 * 1000); break;
        case '24h': cutoff = new Date(now - 24 * 60 * 60 * 1000); break;
        case '7d': cutoff = new Date(now - 7 * 24 * 60 * 60 * 1000); break;
        case '30d': cutoff = new Date(now - 30 * 24 * 60 * 60 * 1000); break;
        default: filteredTransactions = [...allTransactions]; return;
    }

    filteredTransactions = allTransactions.filter(tx => new Date(tx.timestamp) > cutoff);
}

function applyFilters() {
    const search = document.getElementById('txSearch')?.value.toLowerCase() || '';
    const typeFilter = document.getElementById('typeFilter')?.value || '';

    // First apply time filter to get the base set
    applyTimeFilter();

    // Then filter by search and type on top of the time-filtered results
    if (search || typeFilter) {
        filteredTransactions = filteredTransactions.filter(tx => {
            const matchesSearch = !search ||
                tx.playerName.toLowerCase().includes(search) ||
                tx.item.toLowerCase().includes(search);
            const matchesType = !typeFilter || tx.type === typeFilter;
            return matchesSearch && matchesType;
        });
    }

    currentPage = 1;
    renderTransactions();
}

function sortTransactions() {
    filteredTransactions.sort((a, b) => {
        let aVal = a[sortColumn];
        let bVal = b[sortColumn];

        if (sortColumn === 'timestamp') {
            aVal = new Date(aVal).getTime();
            bVal = new Date(bVal).getTime();
        }

        return sortDirection === 'desc' ? bVal - aVal : aVal - bVal;
    });
}

function setTimeRange(range) {
    timeRange = range;
    document.querySelectorAll('.time-btn').forEach(btn => btn.classList.remove('active'));
    event.target.classList.add('active');
    applyTimeFilter();
    renderTransactions();
    updateInsights();
    renderActivityChart();
}

// ═══ TAB SWITCHING ═══
function switchLeaderboard(type) {
    currentLeaderboard = type;
    document.querySelectorAll('.leaderboard-tabs .tab-btn').forEach(btn => btn.classList.remove('active'));
    event.target.classList.add('active');
    loadLeaderboards();
}

function switchTrends(type) {
    currentTrends = type;
    document.querySelectorAll('.trends-tabs .tab-btn').forEach(btn => btn.classList.remove('active'));
    event.target.classList.add('active');
    loadTrends();
}

// ═══ PAGINATION ═══
function nextPage() {
    const totalPages = Math.ceil(filteredTransactions.length / itemsPerPage);
    if (currentPage < totalPages) {
        currentPage++;
        renderTransactions();
    }
}

function previousPage() {
    if (currentPage > 1) {
        currentPage--;
        renderTransactions();
    }
}

// ═══ UTILITIES ═══
function refreshData() {
    const btn = document.querySelector('.refresh-btn');
    if (btn) {
        btn.style.transform = 'rotate(360deg)';
        setTimeout(() => btn.style.transform = '', 500);
    }
    loadAllData();
}

function updateLastUpdateTime() {
    const el = document.getElementById('lastUpdate');
    if (el) el.textContent = new Date().toLocaleTimeString();
}

function formatTime(timestamp) {
    const date = new Date(timestamp);
    const diff = Date.now() - date.getTime();
    const mins = Math.floor(diff / 60000);

    if (mins < 1) return 'Just now';
    if (mins < 60) return `${mins}m ago`;
    if (mins < 1440) return `${Math.floor(mins / 60)}h ago`;
    return date.toLocaleDateString();
}

function formatCurrency(amount) {
    return '$' + (amount || 0).toLocaleString('en-US', {
        minimumFractionDigits: 2,
        maximumFractionDigits: 2
    });
}

function prettifyItem(item) {
    return item.split('_')
        .map(w => w.charAt(0).toUpperCase() + w.slice(1).toLowerCase())
        .join(' ');
}

function escapeHtml(text) {
    const div = document.createElement('div');
    div.textContent = text || '';
    return div.innerHTML;
}

function setText(id, value) {
    const el = document.getElementById(id);
    if (el) el.textContent = value;
}

function showError(id, message) {
    const el = document.getElementById(id);
    if (el) el.innerHTML = `<div class="loading">${message}</div>`;
}

function animateNumber(id, value, isCurrency = false) {
    const el = document.getElementById(id);
    if (!el) return;

    const target = typeof value === 'number' ? value : 0;
    const duration = 500;
    const start = performance.now();
    const startValue = 0;

    function update(now) {
        const elapsed = now - start;
        const progress = Math.min(elapsed / duration, 1);
        const current = startValue + (target - startValue) * easeOutQuart(progress);

        el.textContent = isCurrency ? formatCurrency(current) : Math.round(current).toLocaleString();

        if (progress < 1) requestAnimationFrame(update);
    }

    requestAnimationFrame(update);
}

function easeOutQuart(x) {
    return 1 - Math.pow(1 - x, 4);
}

function debounce(fn, delay) {
    let timeout;
    return (...args) => {
        clearTimeout(timeout);
        timeout = setTimeout(() => fn(...args), delay);
    };
}