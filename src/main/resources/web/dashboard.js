// Global state
let allTransactions = [];
let filteredTransactions = [];
let currentPage = 1;
const itemsPerPage = 50;
let sortColumn = 'timestamp';
let sortDirection = 'desc';

// Initialize on page load
document.addEventListener('DOMContentLoaded', () => {
    loadData();
    setInterval(loadData, 30000); // Auto-refresh every 30 seconds
});

// Load all data
async function loadData() {
    try {
        await Promise.all([
            loadTransactions(),
            loadStats()
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
        filteredTransactions = [...allTransactions];
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
        const data = await response.json();
        
        // Parse if it's a string
        const stats = typeof data === 'string' ? JSON.parse(data) : data;
        
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
    tbody.innerHTML = pageTransactions.map(tx => `
        <tr>
            <td>${formatTime(tx.timestamp)}</td>
            <td>${escapeHtml(tx.playerName)}</td>
            <td><span class="type-badge ${tx.type.toLowerCase()}">${tx.type}</span></td>
            <td>${prettifyItem(tx.item)}</td>
            <td>${tx.amount.toLocaleString()}</td>
            <td>$${tx.price.toLocaleString('en-US', {minimumFractionDigits: 2, maximumFractionDigits: 2})}</td>
            <td>${escapeHtml(tx.category || 'N/A')}</td>
        </tr>
    `).join('');

    // Update pagination
    document.getElementById('currentPage').textContent = currentPage;
    document.getElementById('totalPages').textContent = totalPages;
    document.getElementById('prevBtn').disabled = currentPage === 1;
    document.getElementById('nextBtn').disabled = currentPage === totalPages || totalPages === 0;
}

// Sort transactions
function sortTransactions() {
    filteredTransactions.sort((a, b) => {
        let aVal = a[sortColumn];
        let bVal = b[sortColumn];

        // Handle different types
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
    }
}

function previousPage() {
    if (currentPage > 1) {
        currentPage--;
        renderTransactions();
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
    
    allTransactions.forEach(tx => {
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
        <div class="insight-item">
            <span class="insight-name">${prettifyItem(item)}</span>
            <span class="insight-value">$${data.volume.toLocaleString('en-US', {maximumFractionDigits: 0})}</span>
        </div>
    `).join('');

    document.getElementById('topItems').innerHTML = html || '<div class="loading">No data</div>';
}

// Top players
function updateTopPlayers() {
    const playerStats = {};
    
    allTransactions.forEach(tx => {
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
    
    allTransactions.forEach(tx => {
        const category = tx.category || 'Unknown';
        if (!categoryStats[category]) {
            categoryStats[category] = 0;
        }
        categoryStats[category]++;
    });

    const sorted = Object.entries(categoryStats)
        .sort((a, b) => b[1] - a[1])
        .slice(0, 10);

    const total = allTransactions.length;
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
});
