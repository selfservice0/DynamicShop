// DS-WEB-VERSION: @project.version@
function escapeText(value){return String(value??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]))}
const iconPaths={grid:'M3 3h7v7H3z M14 3h7v7h-7z M3 14h7v7H3z M14 14h7v7h-7z',activity:'M3 12h4l3-8 4 16 3-8h4',heart:'M20.8 4.6a5.5 5.5 0 0 0-7.8 0L12 5.7l-1.1-1.1a5.5 5.5 0 0 0-7.8 7.8L12 21l8.8-8.6a5.5 5.5 0 0 0 0-7.8Z',settings:'M9 3h6l1 3 3 1 2 5-2 5-3 1-1 3H9l-1-3-3-1-2-5 2-5 3-1Z M15 12a3 3 0 1 1-6 0 3 3 0 0 1 6 0',refresh:'M20 7v5h-5 M4 17v-5h5 M6 6a8 8 0 0 1 13 2 M18 18A8 8 0 0 1 5 16',search:'M17 10a7 7 0 1 1-14 0 7 7 0 0 1 14 0 M15 15l6 6',cube:'m12 2 9 5v10l-9 5-9-5V7Z M3 7l9 5 9-5 M12 12v10 M7 4l9 5',users:'M15 7a3 3 0 1 1-6 0 3 3 0 0 1 6 0 M5 21v-3a7 7 0 0 1 14 0v3 M19 5a3 3 0 0 1 0 6 M22 20v-3a5 5 0 0 0-3-4',shield:'m12 3 8 3v6c0 5-8 9-8 9s-8-4-8-9V6Z m-4 9 3 3 5-6',info:'M22 12a10 10 0 1 1-20 0 10 10 0 0 1 20 0 M12 11v6 M12 7v1'};
const icon=name=>`<svg class="icon" viewBox="0 0 24 24" aria-hidden="true"><path d="${iconPaths[name]||iconPaths.cube}"/></svg>`;
document.querySelectorAll('[data-icon]').forEach(el=>el.innerHTML=icon(el.dataset.icon));
const items=[]; let transactions=[],serverTime=Date.now(),catalogReady=false; let visibleLimit=48; const watchlistStorageKey="dynamicshop.watchlist.v1";
function readWatchlist(){
  try{
    const saved=JSON.parse(localStorage.getItem(watchlistStorageKey)||'[]');

    return new Set(Array.isArray(saved)?saved.filter(id=>typeof id==='string'):[]);
  }catch{return new Set()}
}
function saveWatchlist(){
  try{localStorage.setItem(watchlistStorageKey,JSON.stringify([...watched]));return true}
  catch{return false}
}
let category='All',page='catalog',selected=null,watched=readWatchlist(),toastTimer;
const $=id=>document.getElementById(id),money=n=>n == null ? 'Unavailable' : '$'+n.toLocaleString('en-US',{minimumFractionDigits:2,maximumFractionDigits:2});

function notify(text){$('toast').textContent=text;$('toast').classList.add('visible');clearTimeout(toastTimer);toastTimer=setTimeout(()=>$('toast').classList.remove('visible'),2700)}
function stockText(item){if(item.kind==='special')return 'Always available';if(item.kind==='playershop')return item.stock+' items in listing';return item.disabled?'Trading disabled':item.stock==null?'Stock unavailable':item.stock<=0?'Out of stock':item.stock.toLocaleString()+' in stock'}
function render(){
  const search=$('search').value.toLowerCase().replaceAll('_',' ').trim();
  let visible=items.filter(i=>(category==='All'||i.categoryId===category)&&i.name.toLowerCase().includes(search)&&(page!=='favorites'||watched.has(i.id))&&(!$('hideOutOfStock').checked||i.kind==='special'||(i.stock!=null&&i.stock>0)));
  const sort=$('sort').value;
  if(sort==='low'||sort==='high') visible.sort((a,b)=>(a.buy==null)!==(b.buy==null)?(a.buy==null?1:-1):sort==='low'?a.buy-b.buy:b.buy-a.buy);
  if(sort==='name') visible.sort((a,b)=>a.name.localeCompare(b.name));
  $('resultCount').textContent=visible.length.toLocaleString()+' '+(visible.length===1?'item':'items');
  $('watchCount').textContent=watched.size;
  $('empty').hidden=visible.length>0;
  $('emptyText').textContent=page==='favorites'&&!watched.size?'Tap the heart on an item to keep it close.':'Try another name or category.';
  const shown=visible.slice(0,visibleLimit);
  $('itemGrid').innerHTML=shown.map(i=>`<article class="item-card ${i.disabled?'unavailable':''}"><button class="watch-button ${watched.has(i.id)?'active':''}" data-watch="${escapeText(i.id)}" aria-label="${watched.has(i.id)?'Remove':'Add'} ${escapeText(i.name)} ${watched.has(i.id)?'from':'to'} watchlist" aria-pressed="${watched.has(i.id)}">${icon('heart')}</button><button class="card-open" data-open="${escapeText(i.id)}" aria-label="View ${escapeText(i.name)}"><div class="card-art" style="--tint:#263c34">${i.disabled?'<span class="tag disabled-tag">UNAVAILABLE</span>':i.tag?`<span class="tag ${i.category==='Custom'?'custom':''}">${i.tag}</span>`:''}${art(i.id)}</div><div class="card-info"><span class="card-category">${escapeText(i.category)}</span><h3>${escapeText(i.name)}</h3><div class="card-prices"><div><span class="price-label">BUY</span><strong>${money(i.buy)}</strong></div><div><span class="price-label">SELL</span><strong>${money(i.sell)}</strong></div></div></div><div class="card-bottom"><span>${i.stock>0&&!i.disabled?'<i class="green-dot"></i>':''}${stockText(i)}</span><span>View item ↗</span></div></button></article>`).join('');
  $('loadMore').hidden=shown.length>=visible.length;
  $('catalogProgress').textContent=visible.length?'Showing '+shown.length+' of '+visible.length.toLocaleString()+' items':'';
  document.querySelectorAll('[data-open]').forEach(b=>b.onclick=()=>openItem(b.dataset.open));
  document.querySelectorAll('[data-watch]').forEach(b=>b.onclick=()=>toggleWatch(b.dataset.watch));
}
function toggleWatch(id){window.dispatchEvent(new Event("usage-watchlist-change"));watched.has(id)?watched.delete(id):watched.add(id);const saved=saveWatchlist();render();if(selected)updateWatchButton();notify((watched.has(id)?'Added to your watchlist':'Removed from your watchlist')+(saved?'':' · browser storage unavailable; change lasts for this visit'))}
function updateWatchButton(){$('watchItem').textContent=watched.has(selected.id)?'Remove from watchlist':'Add to watchlist'}

// Real server data only. No sample catalog, prices, or transactions are bundled here.
let website=null,adminSession='',registrationToken=new URLSearchParams(location.search).get('token')||'';
try{adminSession=localStorage.getItem('ds_admin_session')||''}catch{}
if(registrationToken){const clean=new URL(location.href);clean.searchParams.delete('token');history.replaceState(null,'',clean.pathname+clean.search+clean.hash)}
async function api(path,{method='GET',body,auth=false,signal}={}){
  const headers={'Accept':'application/json'};
  if(body!==undefined)headers['Content-Type']='application/json';
  if(auth&&adminSession)headers['X-Session-Token']=adminSession;
  const response=await fetch(path,{method,headers,body:body===undefined?undefined:JSON.stringify(body),cache:'no-store',signal});
  const result=await response.json().catch(()=>({error:'The server returned an invalid response.'}));
  if(!response.ok){const error=new Error(result.error||result.message||'Request failed ('+response.status+')');error.status=response.status;if(auth&&response.status===401)window.dispatchEvent(new Event('admin-expired'));throw error}
  return result;
}
function art(id){const item=items.find(i=>i.id===id);const raw=item?.imageUrl||(/^\w+$/.test(id)?'https://mc.nerothe.com/img/1.21/minecraft_'+id.toLowerCase()+'.png':'');let url='';try{const parsed=new URL(raw,location.href);if(['http:','https:'].includes(parsed.protocol))url=parsed.href}catch{}return url?`<img class="pixel-item" src="${escapeText(url)}" alt="" aria-hidden="true" loading="lazy" onerror="this.hidden=true">`:'<span class="missing-art">'+icon('cube')+'</span>'}
function mapItem(raw){return {...raw,id:raw.item,name:raw.displayName||raw.item,category:raw.categoryDisplayName||raw.category,categoryId:raw.category,historyId:raw.historyId||raw.item,kind:raw.kind||'material',buy:raw.buyDisabled?null:raw.buyPrice,sell:raw.sellDisabled?null:raw.sellPrice,desc:raw.kind==='playershop'?'Player listing by '+raw.seller+'. Price is for the full listing.':'Current prices from your server. Complete purchases and sales in game.',disabled:!!raw.disabled};}
function refreshCategories(){
 const categories=[...new Map(items.map(i=>[i.categoryId,i.category])).entries()];
 if(category!=='All'&&!categories.some(([id])=>id===category))category='All';
 $('categories').innerHTML='<button data-category="All">All items <span>'+items.length+'</span></button>'+categories.map(([id,label])=>`<button data-category="${escapeText(id)}">${escapeText(label==='All'?'Miscellaneous':label)} <span>${items.filter(i=>i.categoryId===id).length}</span></button>`).join('');
 document.querySelectorAll('[data-category]').forEach(b=>{b.classList.toggle('active',b.dataset.category===category);b.onclick=()=>{category=b.dataset.category;visibleLimit=48;refreshCategories();render()}});
 $('navItemCount').textContent=items.length;$('heroItemCount').textContent=items.length;$('heroCategoryCount').textContent='across '+categories.length+' categories';
}
function age(timestamp){const mins=Math.max(0,Math.floor((serverTime-timestamp)/60000));return mins<1?'Just now':mins<60?mins+' min ago':mins<1440?Math.floor(mins/60)+' hours ago':Math.floor(mins/1440)+' days ago'}
async function reloadMarket(){
 const [catalog,activity]=await Promise.all([api('/api/market/catalog'),api('/api/market/activity')]);
 items.splice(0,items.length,...catalog.map(mapItem));transactions=activity.transactions;serverTime=activity.serverTime;catalogReady=true;
 refreshCategories();$('heroGem').innerHTML=art('EMERALD');render();renderTrades();window.dispatchEvent(new Event('catalog-updated'));
 $('todayTrades').textContent=transactions.filter(t=>t.timestamp>=serverTime-86400000).length.toLocaleString();$('todayTraders').textContent=new Set(transactions.map(t=>t.playerName)).size.toLocaleString();
 $('connectionStatus').textContent='Updated '+new Date().toLocaleTimeString([],{hour:'2-digit',minute:'2-digit'});
}
let refreshPending=false;
async function refreshMarket(){if(refreshPending)return;refreshPending=true;try{await reloadMarket()}catch(error){$('connectionStatus').textContent=catalogReady?'Connection lost · showing last update':'Could not connect';notify(error.message);if(!catalogReady){$('empty').hidden=false;$('emptyText').textContent='Could not load the server catalog. Use Refresh to try again.'}}finally{refreshPending=false}}
// Appearance must not wait for the potentially much larger catalog/history responses.
const websiteReady=(async()=>{
 const controller=new AbortController(),timeout=setTimeout(()=>controller.abort(),10000);
 try{website=await api('/api/website',{signal:controller.signal})}
 catch(error){notify(controller.signal.aborted?'Website settings timed out. Using the default appearance.':error.message)}
 finally{clearTimeout(timeout)}
 return website;
})();
const ready=Promise.all([websiteReady,refreshMarket()]).then(()=>website);
let activityRender=()=>{};
function renderTrades(){activityRender()}
function openItem(id){
 window.dispatchEvent(new Event("usage-item-open"));
 selected=items.find(i=>i.id===id);if(!selected)return;
 $('detailArt').innerHTML=art(selected.id);$('detailCategory').textContent=selected.category;$('detailName').textContent=selected.name;$('detailDescription').textContent=selected.desc;
 $('detailBuy').textContent=money(selected.buy);$('detailSell').textContent=money(selected.sell);$('detailStock').textContent=selected.kind==='special'?'Always available':selected.stock==null?'Unavailable':selected.stock.toLocaleString()+' items';
 $('quantity').value=1;const disabled=selected.buy==null||selected.kind==='playershop';for(const id of ['quantity','increase','decrease'])$(id).disabled=disabled;
 updateTotal();updateWatchButton();renderPriceHistory('7d');renderItemTransactions();$('itemDialog').showModal();document.body.style.overflow='hidden';
}
function itemHistory(){return selected.kind==='playershop'?[]:transactions.filter(t=>t.item.toLowerCase()===selected.historyId.toLowerCase())}
function renderItemTransactions(type='All',limit=5){
 const all=itemHistory(),filtered=all.filter(t=>type==='All'||t.type===type);$('itemTradeCount').textContent=all.length+' recorded trades';
 document.querySelectorAll('[data-item-trade-type]').forEach(b=>{const active=b.dataset.itemTradeType===type;b.classList.toggle('active',active);b.setAttribute('aria-pressed',String(active));b.onclick=()=>renderItemTransactions(b.dataset.itemTradeType)});
 $('itemTransactionList').innerHTML=filtered.slice(0,limit).map(t=>`<div class="item-trade-row"><span class="trade-avatar">${escapeText(t.playerName.slice(0,1))}</span><div class="trade-person"><strong>${escapeText(t.playerName)}</strong><time title="${escapeText(new Date(t.timestamp).toLocaleString())}">${age(t.timestamp)}</time></div><span class="tx-type ${t.type==='SELL'?'sell':''}">${t.type==='BUY'?'Buy':'Sell'}</span><div class="trade-amount"><strong>${money(t.price)}</strong><span>${t.amount} × ${money(t.amount?t.price/t.amount:0)}</span></div></div>`).join('')||'<div class="item-trades-empty">No recorded transactions in this view.</div>';
 $('moreItemTransactions').hidden=filtered.length<=limit;$('moreItemTransactions').onclick=()=>renderItemTransactions(type,limit+5);
}
function renderPriceHistory(range='7d'){
 document.querySelectorAll('[data-history-range]').forEach(b=>{b.classList.toggle('active',b.dataset.historyRange===range);b.setAttribute('aria-pressed',String(b.dataset.historyRange===range));b.onclick=()=>renderPriceHistory(b.dataset.historyRange)});
 const hours={'24h':24,'7d':168,'30d':720}[range],groups=new Map();
 for(const t of itemHistory().filter(t=>t.timestamp>=serverTime-hours*3600000&&t.amount>0&&Number.isFinite(t.price)&&t.price>=0)){
  const key=Math.floor(t.timestamp/3600000)*3600000;if(!groups.has(key))groups.set(key,{date:key,BUY:[],SELL:[]});groups.get(key)[t.type]?.push(t.price/t.amount);
 }
 const points=[...groups.values()].sort((a,b)=>a.date-b.date).map(p=>({...p,buy:p.BUY.length?p.BUY.reduce((a,b)=>a+b,0)/p.BUY.length:null,sell:p.SELL.length?p.SELL.reduce((a,b)=>a+b,0)/p.SELL.length:null}));
 if(!points.length){$('priceHistoryChart').innerHTML='<div class="history-empty">No recorded trades for this period.</div>';$('historyReadout').textContent='Price history appears after items are traded. Only retained transaction history is available.';return}
 const w=420,h=202,left=62,right=12,top=15,bottom=27,values=points.flatMap(p=>[p.buy,p.sell]).filter(v=>v!==null),min=Math.max(0,Math.min(...values)*.9),max=Math.max(...values)*1.1||1;
 const x=i=>left+(points.length===1?.5:(points[i].date-points[0].date)/(points.at(-1).date-points[0].date))*(w-left-right),y=v=>top+(max-v)/(max-min||1)*(h-top-bottom);
 const path=key=>{let started=false;return points.map((p,i)=>{if(p[key]===null)return '';const v=(started?'L':'M')+x(i)+','+y(p[key]);started=true;return v}).join(' ')};
 const grid=Array.from({length:4},(_,i)=>{const v=min+(max-min)*i/3;return `<line class="history-gridline" x1="${left}" x2="${w-right}" y1="${y(v)}" y2="${y(v)}"/><text x="${left-8}" y="${y(v)+3}" text-anchor="end">${v>=1000?'$'+(v/1000).toFixed(1)+'k':money(v)}</text>`}).join('');
 $('priceHistoryChart').innerHTML=`<svg class="history-svg" viewBox="0 0 ${w} ${h}" role="img" aria-label="Recorded buy and sell prices for ${escapeText(selected.name)}">${grid}<path d="${path('buy')}" class="history-buy-line"/><path d="${path('sell')}" class="history-sell-line"/>${points.map((p,i)=>['buy','sell'].map(k=>p[k]===null?'':`<circle cx="${x(i)}" cy="${y(p[k])}" r="2.5" class="history-${k}-dot"/>`).join('')).join('')}<line id="historyCursor" y1="${top}" y2="${h-bottom}" class="history-cursor"/><text x="${left}" y="${h-5}">${new Date(points[0].date).toLocaleDateString()}</text><text x="${w-right}" y="${h-5}" text-anchor="end">${new Date(points.at(-1).date).toLocaleDateString()}</text></svg><input id="historyPoint" class="history-slider" type="range" min="0" max="${points.length-1}" value="${points.length-1}" aria-label="Explore historical prices">`;
 const price=v=>v===null?'No trades':money(v);
 function show(i){const p=points[i],label=new Date(p.date).toLocaleString();$('historyCursor').setAttribute('x1',x(i));$('historyCursor').setAttribute('x2',x(i));$('historyReadout').innerHTML=`<span>${escapeText(label)}</span><strong class="history-buy-value">Buy ${price(p.buy)}</strong><strong class="history-sell-value">Sell ${price(p.sell)}</strong>`;$('historyPoint').value=i;$('historyPoint').setAttribute('aria-valuetext',label+', buy '+price(p.buy)+', sell '+price(p.sell))}
 $('historyPoint').oninput=e=>show(Number(e.target.value));const svg=$('priceHistoryChart').querySelector('svg');svg.onpointermove=e=>{const r=svg.getBoundingClientRect(),px=(e.clientX-r.left)/r.width*w;show(points.reduce((best,p,i)=>Math.abs(x(i)-px)<Math.abs(x(best)-px)?i:best,0))};show(points.length-1);
}
function updateTotal(){const n=Math.max(1,Math.min(2304,Math.floor(+$('quantity').value||1)));$('quantity').value=n;$('quantitySummary').textContent=selected.kind==='playershop'?'Price for this listing':'Estimate for '+n+' items';$('tradeTotal').textContent=money(selected.buy==null?null:selected.kind==='playershop'?selected.buy:n*selected.buy);$('quantitySummary').title='Estimate using the current single-item quote. In-game dynamic pricing may change the final total.'}
$('closeDetails').onclick=()=>$('itemDialog').close();$('itemDialog').addEventListener('close',()=>document.body.style.overflow='');
$('increase').onclick=()=>{$('quantity').value=+$('quantity').value+1;updateTotal()};$('decrease').onclick=()=>{$('quantity').value=+$('quantity').value-1;updateTotal()};$('quantity').onchange=updateTotal;$('watchItem').onclick=()=>toggleWatch(selected.id);
$('hideOutOfStock').onchange=()=>{visibleLimit=48;render()};$('search').oninput=()=>{visibleLimit=48;render()};$('sort').onchange=()=>{visibleLimit=48;render()};$('loadMore').onclick=()=>{visibleLimit+=48;render()};$('refresh').onclick=refreshMarket;
$('clearFilters').onclick=()=>{$('hideOutOfStock').checked=false;$('search').value='';category='All';refreshCategories();render()};
function navigate(){const hash=location.hash.slice(1),browsing=hash==='browse';page=['catalog','favorites','activity','settings'].includes(hash)?hash:'catalog';visibleLimit=48;
 $('catalogView').hidden=!['catalog','favorites'].includes(page);$('activityView').hidden=page!=='activity';$('settingsView').hidden=page!=='settings';
 document.querySelectorAll('[data-nav]').forEach(a=>a.classList.toggle('active',a.dataset.nav===page));$('pageCrumb').textContent={catalog:'Item catalog',favorites:'Watchlist',activity:'Market activity',settings:'Administration'}[page];
 $('catalogTitle').firstChild.textContent=page==='favorites'?'Your watchlist':'Browse the market';document.querySelector('.hero').hidden=page==='favorites';document.querySelector('.market-strip').hidden=page==='favorites';
 if(page==='favorites'){category='All';$('search').value='';refreshCategories()}render();if(browsing)$('browse').scrollIntoView({behavior:'smooth'});else window.scrollTo(0,0);
}
window.addEventListener('hashchange',navigate);navigate();
document.addEventListener('keydown',e=>{if(e.key==='/'&&!['INPUT','TEXTAREA','SELECT'].includes(document.activeElement.tagName)&&!document.querySelector('dialog[open]')){e.preventDefault();if(page!=='catalog'&&page!=='favorites')location.hash='catalog';$('search').focus()}});
setInterval(()=>{if(!document.hidden&&!document.querySelector('dialog[open]')&&page!=='settings')refreshMarket()},30000);

// Activity analytics use recorded transaction totals, including items removed from the catalog.
(() => {
  const root=$('activityView');let hours=24,activityType='All';
  const ageMinutes=label=>label==='Just now'?0:Number(label.match(/\d+/)?.[0]||0)*(label.includes('week')?10080:label.includes('day')?1440:label.includes('hour')?60:1);
  root.innerHTML=`<div class="page-title activity-title"><div><div class="eyebrow">THE MARKET, IN MOTION</div><h1>A good day to trade.</h1><p>See what’s moving, who’s trading, and where the money goes.</p></div><div class="activity-period" role="group" aria-label="Activity time range"><button type="button" data-period="24" aria-pressed="true">Last day</button><button type="button" data-period="168" aria-pressed="false">Last week</button><button type="button" data-period="all" aria-pressed="false">Forever</button></div></div><p class="activity-sample-note" id="activityScope"></p><div class="market-metrics" id="marketMetrics"></div><section class="economy-health" aria-labelledby="economyHealthTitle"><div class="economy-heading"><div><span class="economy-symbol">${icon('activity')}</span><h2 id="economyHealthTitle">Economy health</h2></div><span>For the selected time range</span></div><div class="economy-grid" id="economyMetrics"></div><p class="economy-flow-note">Net flow = payments to players for sales − money spent by players on purchases.</p></section><section class="activity-chart activity-volume-chart" aria-labelledby="activityChartTitle"><div class="activity-chart-heading"><div><h3 id="activityChartTitle">Trading activity</h3><span id="activityChartCaption"></span></div><div class="activity-chart-legend"><span><i></i>Purchases</span><span><i></i>Sales</span></div></div><div id="activityBars" class="activity-bar-grid"></div><div class="activity-chart-axis" id="activityAxis"></div><p id="activityBarReadout" class="activity-bar-readout" role="status"></p></section><div class="section-heading activity-transactions-heading"><div><h2>Recent transactions</h2><p id="activityTableCount"></p></div><div class="category-tabs activity-filters" role="group" aria-label="Transaction type"><button type="button" class="active" data-type="All" aria-pressed="true">All activity</button><button type="button" data-type="BUY" aria-pressed="false">Purchases</button><button type="button" data-type="SELL" aria-pressed="false">Sales</button></div></div><div class="table-wrap"><table><thead><tr><th>ITEM</th><th>PLAYER</th><th>TYPE</th><th>QUANTITY</th><th>TOTAL</th><th>TIME</th></tr></thead><tbody id="transactions"></tbody></table></div>`;
  let shownLimit=100;
  const more=document.createElement('button');more.className='secondary';more.textContent='Load more transactions';root.append(more);more.onclick=()=>{shownLimit+=100;renderTrades()};
  function records(){return transactions.map(t=>{const item=items.find(i=>i.historyId?.toLowerCase()===t.item.toLowerCase());return {id:item?.id||t.item,item:item||{name:t.item},player:t.playerName,type:t.type,quantity:t.amount,time:age(t.timestamp),minutes:Math.max(0,(serverTime-t.timestamp)/60000),cents:Math.round(t.price*100),available:!!item}}).filter(r=>(hours===null||r.minutes<=hours*60)&&['BUY','SELL'].includes(r.type))}
  const cash=cents=>money(cents/100);
  const windowHours=rows=>hours??Math.max(1,Math.ceil(Math.max(0,...rows.map(r=>r.minutes))/60));
  function updateMetrics(rows){
    const buys=rows.filter(r=>r.type==='BUY'),sells=rows.filter(r=>r.type==='SELL');
    const bought=buys.reduce((n,r)=>n+r.cents,0),sold=sells.reduce((n,r)=>n+r.cents,0),total=bought+sold;
    const net=sold-bought,ratio=rows.length?buys.length/rows.length*100:0;
    const cards=[['Total transactions',rows.length.toLocaleString(),'Purchases and sales','activity'],['Total volume',cash(total),'Combined value of all trades','cube'],['Purchases',buys.length.toLocaleString(),cash(bought)+' spent by players','grid'],['Sales',sells.length.toLocaleString(),cash(sold)+' paid to players','users']];
    $('marketMetrics').innerHTML=cards.map(([label,value,detail,symbol],index)=>`<article class="metric-card metric-${index}"><span class="metric-icon">${icon(symbol)}</span><div><h3>${label}</h3><strong>${value}</strong><p>${detail}</p></div></article>`).join('');
    const health=[['Buy ratio',ratio.toFixed(1)+'%','Share of trades that are purchases',`<div class="buy-ratio-track" role="meter" aria-label="Buy ratio" aria-valuemin="0" aria-valuemax="100" aria-valuenow="${ratio.toFixed(1)}"><span style="width:${ratio}%"></span></div>`],['Activity',(rows.length/windowHours(rows)).toFixed(1)+' /hr',hours===null?'Average since the earliest recorded trade (minimum 1 hour)':'Transactions per hour',''],['Net flow',(net>0?'+':net<0?'−':'')+cash(Math.abs(net)),net>0?'Money added to player balances':net<0?'Money removed from player balances':'No net change',''],['Average trade',cash(rows.length?Math.round(total/rows.length):0),'Average transaction value',''],['Unique items',new Set(rows.map(r=>r.id)).size.toLocaleString(),'Different items traded',''],['Active traders',new Set(rows.map(r=>r.player.toLowerCase())).size.toLocaleString(),'Players with a transaction','']];
    $('economyMetrics').innerHTML=health.map(([label,value,hint,extra],i)=>`<div class="economy-stat" title="${hint}"><span>${label}</span><strong ${i===2?'class="flow-value"':''}>${value}</strong>${extra||'<small>'+hint+'</small>'}</div>`).join('');
    $('activityScope').textContent=rows.length+' recorded transactions '+(hours===null?'across all recorded history':hours===24?'in the last day':'in the last week')+' · Forever shows all history retained by the server';
    updateChart(rows);
  }
  function updateChart(rows){
    const span=windowHours(rows),count=12,interval=span*60/count,buckets=Array.from({length:count},()=>({BUY:0,SELL:0}));
    rows.forEach(r=>{const index=Math.max(0,count-1-Math.floor(r.minutes/interval));buckets[index][r.type]++});
    const max=Math.max(1,...buckets.map(b=>b.BUY+b.SELL));
    const duration=min=>{min=Math.round(min);return [Math.floor(min/1440)?Math.floor(min/1440)+'d':'',Math.floor(min%1440/60)?Math.floor(min%1440/60)+'h':'',min%60?min%60+'m':''].filter(Boolean).join(' ')||'0m'};
    const ago=min=>min===0?'now':duration(min)+' ago';
    $('activityBars').innerHTML=buckets.map((b,i)=>{const label=ago((count-i)*interval)+' – '+ago((count-i-1)*interval)+': '+b.BUY+' purchases, '+b.SELL+' sales';return `<button type="button" class="activity-bar" data-bucket="${i}" aria-label="${label}" title="${label}"><span class="activity-stack" style="height:${(b.BUY+b.SELL)/max*100}%"><i class="activity-buy" style="flex:${b.BUY}"></i><i class="activity-sell" style="flex:${b.SELL}"></i></span><span class="activity-bar-baseline"></span></button>`}).join('');
    $('activityChartCaption').textContent='Recorded transaction count · '+duration(interval)+' intervals';
    $('activityAxis').innerHTML='<span>'+ago(span*60)+'</span><span>'+ago(span*30)+'</span><span>Now</span>';
    $('activityBarReadout').textContent='Hover, tap, or focus a bar to inspect purchases and sales.';
    root.querySelectorAll('[data-bucket]').forEach(b=>{const inspect=()=>{$('activityBarReadout').textContent=b.getAttribute('aria-label')};b.onmouseenter=inspect;b.onfocus=inspect;b.onclick=inspect});
  }
  function renderTrades(type=activityType){
    activityType=['All','BUY','SELL'].includes(type)?type:'All';const rows=records(),shown=rows.filter(r=>activityType==='All'||r.type===activityType);
    root.querySelectorAll('[data-type]').forEach(b=>{const selected=b.dataset.type===activityType;b.classList.toggle('active',selected);b.setAttribute('aria-pressed',String(selected))});
    $('transactions').innerHTML=shown.slice(0,shownLimit).map(r=>`<tr><td><button type="button" class="activity-item-link" data-activity-item="${escapeText(r.id)}" ${r.available?'':'disabled'}>${art(r.id)}<span>${escapeText(r.item.name)}</span></button></td><td>${escapeText(r.player)}</td><td><span class="tx-type ${r.type==='SELL'?'sell':''}">${r.type==='BUY'?'Purchase':'Sale'}</span></td><td>${r.quantity.toLocaleString()}</td><td>${cash(r.cents)}</td><td>${escapeText(r.time)}</td></tr>`).join('')||'<tr><td colspan="6">No transactions in this view.</td></tr>';
    $('activityTableCount').textContent='Showing '+Math.min(shownLimit,shown.length)+' of '+rows.length+' recorded transactions. Type filters apply to this table.';
    more.hidden=shown.length<=shownLimit;
    updateMetrics(rows);
  };
  root.querySelectorAll('[data-period]').forEach(b=>b.onclick=()=>{shownLimit=100;hours=b.dataset.period==='all'?null:Number(b.dataset.period);root.querySelectorAll('[data-period]').forEach(button=>button.setAttribute('aria-pressed',String(button===b)));renderTrades()});
  root.querySelectorAll('[data-type]').forEach(b=>b.onclick=()=>renderTrades(b.dataset.type));
  $('transactions').onclick=e=>{const button=e.target.closest('[data-activity-item]');if(button)openItem(button.dataset.activityItem)};
  activityRender=()=>renderTrades(activityType);
  renderTrades();
})();

// Nova's interactions share the catalog, drawer, and watchlist used by every design.
(() => {
  const active=()=>document.documentElement.dataset.design==='nova';
  const motion=()=>active()&&document.documentElement.dataset.themeMotion!=='false'&&!matchMedia('(prefers-reduced-motion: reduce)').matches;
  const command=document.createElement('button');command.type='button';command.className='nova-command nova-only';command.innerHTML=icon('search')+'<span>Quick find</span><kbd>Ctrl K</kbd>';document.querySelector('.topbar-right').prepend(command);
  const spotlight=document.createElement('section');spotlight.className='nova-spotlight nova-only';spotlight.setAttribute('aria-label','Featured discoveries');
  spotlight.innerHTML='<div class="nova-spotlight-label"><span>THE DISCOVERY DECK</span><span id="novaFeaturedIndex"></span></div><button type="button" id="novaFeaturedOpen" class="nova-featured-open"><div id="novaFeaturedArt"></div><div class="nova-featured-info"><span id="novaFeaturedCategory"></span><h2 id="novaFeaturedName"></h2><div><span>BUY PRICE<strong id="novaFeaturedPrice"></strong></span><span class="nova-featured-arrow">↗</span></div></div></button><div class="nova-featured-controls"><span>Pick your next upgrade</span><button type="button" id="novaPrevious" aria-label="Previous featured item">←</button><button type="button" id="novaNext" aria-label="Next featured item">→</button></div>';
  document.querySelector('.hero').append(spotlight);
  let featured=[];let featuredIndex=0;
  function updateFeatured(){featured=items.filter(i=>i.buy!==null).slice(0,5).map(i=>i.id);spotlight.hidden=!featured.length;featuredIndex=featured.length?featuredIndex%featured.length:0;const item=items.find(i=>i.id===featured[featuredIndex]);if(!item)return;$('novaFeaturedArt').innerHTML=art(item.id);$('novaFeaturedCategory').textContent=item.category;$('novaFeaturedName').textContent=item.name;$('novaFeaturedPrice').textContent=money(item.buy);$('novaFeaturedIndex').textContent=String(featuredIndex+1).padStart(2,'0')+' / '+featured.length;$('novaFeaturedOpen').setAttribute('aria-label','Explore '+item.name)}
  $('novaFeaturedOpen').onclick=()=>{if(featured.length)openItem(featured[featuredIndex])};
  window.addEventListener('catalog-updated',updateFeatured);
  $('novaPrevious').onclick=()=>{if(!featured.length)return;featuredIndex=(featuredIndex+featured.length-1)%featured.length;updateFeatured()};$('novaNext').onclick=()=>{if(!featured.length)return;featuredIndex=(featuredIndex+1)%featured.length;updateFeatured()};updateFeatured();
  const viewbar=document.createElement('div');viewbar.className='nova-viewbar nova-only';
  viewbar.innerHTML='<div class="nova-view-options" role="group" aria-label="Catalog layout"><button type="button" data-nova-view="grid" aria-pressed="true">'+icon('grid')+' Grid</button><button type="button" data-nova-view="list" aria-pressed="false">'+icon('activity')+' List</button></div><button type="button" id="novaDiscover">Discover an item <span>↗</span></button>';
  $('itemGrid').before(viewbar);
  document.querySelectorAll('[data-nova-view]').forEach(b=>b.onclick=()=>{document.documentElement.dataset.novaView=b.dataset.novaView;document.querySelectorAll('[data-nova-view]').forEach(button=>button.setAttribute('aria-pressed',String(button===b)))});
  $('novaDiscover').onclick=()=>{const visible=Array.from($('itemGrid').querySelectorAll('[data-open]')).map(b=>items.find(i=>i.id===b.dataset.open)).filter(i=>i&&!i.disabled);if(!visible.length){notify('No available items in this view');return}openItem(visible[Math.floor(Math.random()*visible.length)].id)};
  const finder=document.createElement('dialog');finder.id='novaFinder';finder.setAttribute('aria-labelledby','novaFinderTitle');
  finder.innerHTML='<div class="nova-finder-heading"><div><span>LESS SCROLLING. MORE DISCOVERY.</span><h2 id="novaFinderTitle">Find your next item</h2></div><button type="button" class="icon-button" id="novaCloseFinder" aria-label="Close quick find">✕</button></div><label class="nova-finder-input">'+icon('search')+'<input id="novaFindInput" type="search" placeholder="Try diamond, tools, or watering can…" aria-label="Quick find items" autocomplete="off"></label><p id="novaFindCount" role="status"></p><div id="novaFindResults"></div><div class="nova-finder-hint"><span>↑ ↓ to move · Enter to explore</span><span>Esc to close</span></div>';
  document.body.append(finder);
  function findItems(){const query=$('novaFindInput').value.trim().toLowerCase();const matches=items.filter(i=>(i.name+' '+i.category).toLowerCase().includes(query));const shown=matches.slice(0,8);$('novaFindCount').textContent=query?matches.length+' matching items'+(matches.length>8?' · showing the first 8':''):'Start typing to search all '+items.length+' items';$('novaFindResults').innerHTML=shown.map(i=>'<button class="nova-find-result" type="button" data-find-item="'+escapeText(i.id)+'">'+art(i.id)+'<span><strong>'+escapeText(i.name)+'</strong><small>'+escapeText(i.category)+'</small></span><span>'+ (i.disabled?'Unavailable':money(i.buy))+'</span><span>↗</span></button>').join('')||'<div class="nova-find-empty">No matches. Try a different item name or category.</div>'}
  function showFinder(){if(!active()||document.querySelector('dialog[open]'))return;finder.showModal();document.body.style.overflow='hidden';$('novaFindInput').value='';findItems();$('novaFindInput').focus()}
  command.onclick=showFinder;$('novaCloseFinder').onclick=()=>finder.close();finder.addEventListener('close',()=>{if(!document.querySelector('dialog[open]'))document.body.style.overflow=''});$('novaFindInput').oninput=findItems;
  $('novaFindResults').onclick=e=>{const button=e.target.closest('[data-find-item]');if(!button)return;finder.close();openItem(button.dataset.findItem)};
  finder.addEventListener('keydown',e=>{const buttons=Array.from(finder.querySelectorAll('[data-find-item]'));if(!buttons.length)return;if(e.key==='ArrowDown'||e.key==='ArrowUp'){e.preventDefault();const index=buttons.indexOf(document.activeElement);const next=index<0?(e.key==='ArrowDown'?0:buttons.length-1):(index+(e.key==='ArrowDown'?1:-1)+buttons.length)%buttons.length;buttons[next].focus()}else if(e.key==='Enter'&&e.target===$('novaFindInput')){e.preventDefault();buttons[0].click()}});
  document.addEventListener('keydown',e=>{if(active()&&(e.ctrlKey||e.metaKey)&&e.key.toLowerCase()==='k'){e.preventDefault();showFinder()}});
  // Pointer lighting is decorative; keyboard and touch controls work without it.
  let frame=0,pending=null;
  $('itemGrid').addEventListener('pointermove',e=>{if(!motion()||e.pointerType!=='mouse')return;const card=e.target.closest('.item-card');if(!card)return;pending={card,x:e.clientX,y:e.clientY};if(frame)return;frame=requestAnimationFrame(()=>{frame=0;const p=pending;if(!p?.card.isConnected)return;const r=p.card.getBoundingClientRect();p.card.style.setProperty('--pointer-x',((p.x-r.left)/r.width*100)+'%');p.card.style.setProperty('--pointer-y',((p.y-r.top)/r.height*100)+'%')})});
  new MutationObserver(()=>{if(active())updateFeatured();else if(finder.open)finder.close()}).observe(document.documentElement,{attributes:true,attributeFilter:['data-design']});
})();

// Count only allowlisted actions. Never send text, URLs, item IDs, or visitor identifiers.
(async()=>{
  await ready;
  const key='dynamicshop.usage.optout.v1';
  let optedOut=false,enabled=website?.usageMetricsEnabled===true,searchTimer;
  const pending=new Set();
  const browserOptOut=()=>navigator.doNotTrack==='1'||navigator.globalPrivacyControl===true;
  function readPreference(){try{optedOut=localStorage.getItem(key)==='true'}catch{optedOut=true}}
  readPreference();
  function stop(){clearTimeout(searchTimer);for(const controller of pending)controller.abort();pending.clear()}
  function track(event){
    if(!enabled||optedOut||browserOptOut()||document.hidden)return;
    const controller=new AbortController();pending.add(controller);
    fetch('/api/usage/event',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({event}),credentials:'omit',referrerPolicy:'no-referrer',signal:controller.signal}).catch(()=>{}).finally(()=>pending.delete(controller));
  }
  const button=document.createElement('button');button.type='button';button.className='usage-privacy-button';button.textContent='Usage privacy';
  const note=document.createElement('span');note.className='usage-privacy-note';
  document.querySelector('footer').append(note,button);
  const dialog=document.createElement('dialog');dialog.className='usage-privacy-dialog';dialog.setAttribute('aria-labelledby','usagePrivacyTitle');
  dialog.innerHTML='<form method="dialog"><h2 id="usagePrivacyTitle">Website usage privacy</h2><p>When enabled, this site counts page views, item-detail opens, searches, and watchlist changes. Only action counts are shared with DynamicShop through bStats. The server also reports its selected website design.</p><p>No search text, item names, player names, admin form contents, visitor IDs, or browsing URLs are included. Events go to this Minecraft server; visitors do not contact bStats directly.</p><label><input id="usageAllowed" type="checkbox"> Share my usage counts from this browser</label><p id="usagePrivacyStatus" role="status"></p><p>Your choice is saved in this browser for this website. Opting out stops future events; counts already aggregated cannot be attributed to you or removed individually. Browser Do Not Track and Global Privacy Control signals are respected.</p><button type="submit" class="secondary">Close</button></form>';
  document.body.append(dialog);
  function render(){
    const blocked=browserOptOut(),field=document.getElementById('usageAllowed');field.checked=!optedOut&&!blocked;field.disabled=blocked;
    note.textContent=!enabled?'Usage counts disabled by server':optedOut||blocked?'Usage counts off':'Aggregate usage counts enabled';
    document.getElementById('usagePrivacyStatus').textContent=blocked?'Your browser privacy signal disables collection.':!enabled?'Collection is disabled by the server. Your browser preference is saved for future visits.':optedOut?'Usage collection is off for this browser.':'Only aggregate action counts are collected.';
  }
  button.onclick=()=>{render();dialog.showModal()};
  document.getElementById('usageAllowed').onchange=e=>{
    optedOut=!e.target.checked;if(optedOut)stop();let saved=true;
    try{localStorage.setItem(key,String(optedOut))}catch{saved=false}
    render();if(!saved)document.getElementById('usagePrivacyStatus').textContent='Browser storage is unavailable. This choice lasts for this page only.';
  };
  window.addEventListener('storage',e=>{if(e.key===key||e.key===null){readPreference();if(optedOut)stop();render()}});
  window.addEventListener('hashchange',()=>track('page_view'));
  window.addEventListener('usage-item-open',()=>track('item_open'));
  window.addEventListener('usage-watchlist-change',()=>track('watchlist_change'));
  for(const field of [document.getElementById('search'),document.getElementById('novaFindInput')].filter(Boolean))field.addEventListener('input',e=>{clearTimeout(searchTimer);if(e.target.value.trim())searchTimer=setTimeout(()=>track('search'),1000)});
  render();track('page_view');
  setInterval(async()=>{try{const status=await api('/api/usage/status');enabled=status.enabled===true;if(!enabled)stop();render()}catch{enabled=false;stop();render()}},60000);
})();

// DSX Exchange adapts the supplied terminal design to the shared catalog and authenticated admin.
// No extra polling: quotes refresh with the catalog and respect its filters, limits and watchlist.
(() => {
  const active=()=>document.documentElement.dataset.design==='dsx';
  const board=document.createElement('section');board.className='dsx-board dsx-only';board.setAttribute('aria-label','Quote board');
  board.innerHTML='<div class="dsx-board-heading"><h2>Quote board</h2><span>Ask = buy · Bid = sell · trade in game with /shop</span></div><div class="dsx-table-scroll"><table><thead><tr><th><span class="sr-only">Watchlist</span></th><th>Instrument</th><th>Sector</th><th class="dsx-number">Ask</th><th class="dsx-number">Bid</th><th class="dsx-number">Spread</th><th class="dsx-number">Stock</th><th class="dsx-number">Trades 24h</th><th class="dsx-number">Volume 24h</th></tr></thead><tbody></tbody></table></div><p class="dsx-history-note">Activity totals cover recorded history available to this website.</p>';
  $('itemGrid').after(board);
  const tape=document.createElement('div');tape.className='dsx-tape dsx-only';tape.setAttribute('aria-label','Recent trades ticker');
  document.querySelector('.topbar').after(tape);
  const records=()=>/* DSX_TRADES */transactions.map(t=>({id:t.item,type:t.type,amount:t.amount,total:t.price,time:Number(t.timestamp)}));
  let activity=new Map(),recent=[];
  function collect(){
    activity=new Map();const now=Date.now();
    recent=records().filter(t=>Number.isFinite(t.time)&&Number.isFinite(t.total)&&t.amount>0).sort((a,b)=>b.time-a.time);
    for(const t of recent){if(t.time<now-86400000)continue;const stats=activity.get(t.id)||{count:0,volume:0};stats.count++;stats.volume+=t.total;activity.set(t.id,stats)}
  }
  const number=value=>value==null?'—':Number(value).toLocaleString();
  const price=value=>value==null?'—':money(value);
  function renderBoard(){
    if(!active())return;
    const byId=new Map(items.map(item=>[item.id,item]));
    const shown=Array.from($('itemGrid').querySelectorAll('[data-open]'),b=>byId.get(b.dataset.open)).filter(Boolean);
    board.hidden=shown.length===0;
    board.querySelector('tbody').innerHTML=shown.map(i=>{
      const stats=activity.get(i.historyId||i.id)||{count:0,volume:0};
      const spread=i.buy>0&&i.sell!=null?((i.buy-i.sell)/i.buy*100).toFixed(1)+'%':'—';
      return `<tr><td><button class="dsx-watch" data-dsx-watch="${escapeText(i.id)}" aria-label="${watched.has(i.id)?'Remove':'Add'} ${escapeText(i.name)} ${watched.has(i.id)?'from':'to'} watchlist" aria-pressed="${watched.has(i.id)}">${watched.has(i.id)?'★':'☆'}</button></td><th scope="row"><button class="dsx-instrument" data-dsx-open="${escapeText(i.id)}">${art(i.id)}<span>${escapeText(i.name)}</span></button></th><td>${escapeText(i.category)}</td><td class="dsx-number dsx-ask">${price(i.disabled?null:i.buy)}</td><td class="dsx-number dsx-bid">${price(i.disabled?null:i.sell)}</td><td class="dsx-number">${i.disabled?'—':spread}</td><td class="dsx-number">${i.kind==='special'?'Always':number(i.stock)}</td><td class="dsx-number">${number(stats.count)}</td><td class="dsx-number">${money(stats.volume)}</td></tr>`;
    }).join('');
  }
  function renderTape(){
    if(!active())return;
    const byId=new Map(items.flatMap(i=>[[i.id,i],[i.historyId||i.id,i]]));
    const rows=recent.slice(0,16);
    tape.replaceChildren();
    if(!rows.length){tape.textContent='No recorded trades yet';return}
    const track=document.createElement('div');track.className='dsx-tape-track';
    for(const t of rows){
      const item=byId.get(t.id),el=document.createElement(item?'button':'span');
      el.className='dsx-tape-item '+(t.type==='SELL'?'dsx-bid':'dsx-ask');
      el.textContent=(item?.name||t.id)+' · '+t.type+' '+money(t.total/t.amount)+' ×'+t.amount;
      if(item){el.type='button';el.onclick=()=>openItem(item.id)}
      track.append(el);
    }
    tape.append(track);
  }
  board.addEventListener('click',event=>{
    const open=event.target.closest('[data-dsx-open]'),watch=event.target.closest('[data-dsx-watch]');
    if(open)openItem(open.dataset.dsxOpen);
    if(watch){toggleWatch(watch.dataset.dsxWatch);renderBoard()}
  });
  new MutationObserver(renderBoard).observe($('itemGrid'),{childList:true});
  window.addEventListener('catalog-updated',()=>{collect();renderBoard();renderTape()});
  window.addEventListener('appearance-updated',()=>{collect();renderBoard();renderTape()});
  collect();renderBoard();renderTape();
})();

(async () => {
  await websiteReady;
  'use strict';
  iconPaths.edit='m15 3 6 6-12 12H3v-6Z M13 5l6 6 M3 15l6 6';
  iconPaths.home='m3 10 9-7 9 7 M5 9v12h5v-7h4v7h5V9';
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

  const esc=value=>String(value??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
  const cats=[],items=[],defaults=[];
  const state={tab:'shop',cat:null,search:'',limit:40,checked:new Set(),audit:[],specials:[],serverItems:[],listings:[],config:{}};
  let authenticated=false,username='',authBusy=false;
  const appearanceDefaults={title:'DynamicShop',subtitle:'YOUR SERVER MARKETPLACE',accent:'#b4f2ca',mode:'dark',background:'#101415',panel:'#1b2220',sidebar:'#131819',font:'modern',radius:10,density:'comfortable',motion:true,hero:true,homeEnabled:false,homeLabel:'Home',homeUrl:'',homeNewTab:false,design:'classic',layoutStyles:{}};
  const layoutStyleKeys=['accent','mode','background','panel','sidebar','font','radius','density','motion','hero'];
  const layoutDefaults={dsx:{...appearanceDefaults,accent:'#f0b429',mode:'custom',background:'#0a0d11',panel:'#0f1419',sidebar:'#0f1419',radius:0,font:'mono',density:'compact',hero:false,motion:true},chest:{...appearanceDefaults,accent:'#e3b21c',mode:'custom',background:'#1d1e22',panel:'#c6c6c6',sidebar:'#16171b',radius:0,font:'legible',density:'compact',hero:false,motion:false},classic:appearanceDefaults,market:{...appearanceDefaults,accent:'#c25b36',mode:'custom',background:'#f4f0e7',panel:'#fffdf7',sidebar:'#f4f0e7',radius:0,font:'modern'},inventory:{...appearanceDefaults,accent:'#efcc72',mode:'custom',background:'#141b28',panel:'#222d40',sidebar:'#1b2434',radius:0,font:'mono',density:'compact',motion:false},nova:{...appearanceDefaults,accent:'#b5a4ff',mode:'custom',background:'#0c0f19',panel:'#171c2d',sidebar:'#121725',radius:20,font:'rounded',motion:true}};
  const layoutStyle=value=>Object.fromEntries(layoutStyleKeys.map(k=>[k,value[k]]));
  const themeSurfaces={dark:{background:'#101415',panel:'#1b2220',sidebar:'#131819'},light:{background:'#f3f5f7',panel:'#ffffff',sidebar:'#e8edf1'}};
  const themeFonts={legible:"'Atkinson Hyperlegible','Segoe UI',sans-serif",modern:"'DM Sans','Segoe UI',sans-serif",rounded:"'Manrope','Segoe UI',sans-serif",system:"'Segoe UI',Arial,sans-serif",mono:"'Cascadia Code',Consolas,monospace",clean:"'Inter','Segoe UI',sans-serif",geometric:"'Outfit','Segoe UI',sans-serif",soft:"'Nunito','Segoe UI',sans-serif",condensed:"'Roboto Condensed','Arial Narrow',sans-serif",tech:"'Space Grotesk','Segoe UI',sans-serif",serif:"Georgia,'Times New Roman',serif",editorial:"'Playfair Display',Georgia,serif",pixel:"'VT323',Consolas,monospace"};
  const themeFontOptions=[['legible','Hyperlegible','Atkinson Hyperlegible'],['modern','Modern','DM Sans'],['rounded','Rounded','Manrope'],['system','System','Segoe UI / Arial'],['mono','Monospace','Cascadia / Consolas'],['clean','Clean','Inter'],['geometric','Geometric','Outfit'],['soft','Soft','Nunito'],['condensed','Condensed','Roboto Condensed'],['tech','Tech','Space Grotesk'],['serif','Classic serif','Georgia'],['editorial','Editorial','Playfair Display'],['pixel','Pixel','VT323']];
  const themeFontSources={legible:'Atkinson+Hyperlegible:wght@400;700',chestHeading:'Pixelify+Sans:wght@500;700',clean:'Inter:wght@400;500;600;700;800',geometric:'Outfit:wght@400;500;600;700;800',soft:'Nunito:wght@400;500;600;700;800',condensed:'Roboto+Condensed:wght@400;500;600;700',tech:'Space+Grotesk:wght@400;500;600;700',editorial:'Playfair+Display:wght@400;500;600;700;800',pixel:'VT323'};
  function ensureThemeFont(key){if(!themeFontSources[key]||document.getElementById('theme-font-'+key))return;const link=document.createElement('link');link.id='theme-font-'+key;link.rel='stylesheet';link.href='https://fonts.googleapis.com/css2?family='+themeFontSources[key]+'&display=swap';document.head.append(link)}
  const themePresets=[['Emerald','#b4f2ca'],['Ocean','#85c8ff'],['Amethyst','#c7a6ff'],['Ember','#ffbb85'],['Rose','#f5a6c9'],['Gold','#efcc72'],['Ruby','#f87171'],['Cyan','#67e8f9'],['Lime','#bef264'],['Indigo','#a5b4fc'],['Copper','#d99a6c'],['Silver','#cbd5e1']];
  const paletteKeys=['accent','background','panel','sidebar'];
  const themePalettes=[
    {id:'forest',name:'Forest',tone:'Dark',accent:'#b4f2ca',background:'#101b16',panel:'#1b2c23',sidebar:'#0c1510'},
    {id:'deep-ocean',name:'Deep Ocean',tone:'Dark',accent:'#85c8ff',background:'#0d1826',panel:'#192c40',sidebar:'#09121d'},
    {id:'end-city',name:'End City',tone:'Dark',accent:'#c7a6ff',background:'#191326',panel:'#2a213d',sidebar:'#120e1c'},
    {id:'nether',name:'Nether',tone:'Dark',accent:'#ffbb85',background:'#211311',panel:'#35211d',sidebar:'#190e0c'},
    {id:'midnight',name:'Midnight',tone:'Dark',accent:'#cbd5e1',background:'#101216',panel:'#20242c',sidebar:'#090b0f'},
    {id:'cherry',name:'Cherry Blossom',tone:'Light',accent:'#b83d70',background:'#fbf1f5',panel:'#fffafd',sidebar:'#f2e1e9'},
    {id:'sandstone',name:'Sandstone',tone:'Light',accent:'#a86128',background:'#f5efdf',panel:'#fffcf3',sidebar:'#ebe1cc'},
    {id:'glacier',name:'Glacier',tone:'Light',accent:'#176c96',background:'#edf5fa',panel:'#ffffff',sidebar:'#dcebf4'}
  ];
  function validHomeDestination(value){
    if(typeof value!=='string'||!value.trim()||value.length>2048||/[\s\\\u0000-\u001f\u007f]/.test(value))return false;
    if(!/^https?:\/\/[^/?#]/i.test(value))return false;
    try{const url=new URL(value);return ['https:','http:'].includes(url.protocol)&&!!url.hostname}catch{return false}
  }
  function validHomeSettings(value){return typeof value.homeEnabled==='boolean'&&typeof value.homeNewTab==='boolean'&&typeof value.homeLabel==='string'&&value.homeLabel.length<=32&&typeof value.homeUrl==='string'&&value.homeUrl.length<=2048&&(!value.homeEnabled||(value.homeLabel.trim().length>0&&validHomeDestination(value.homeUrl)))}
  function validAppearance(value){return value&&['classic','market','inventory','nova','chest','dsx'].includes(value.design)&&typeof value.title==='string'&&value.title.trim().length>0&&value.title.length<=40&&typeof value.subtitle==='string'&&value.subtitle.length<=60&&['accent','background','panel','sidebar'].every(k=>/^#[0-9a-f]{6}$/i.test(value[k]))&&['dark','light','custom'].includes(value.mode)&&Object.hasOwn(themeFonts,value.font)&&[0,10,20].includes(value.radius)&&['comfortable','compact'].includes(value.density)&&typeof value.motion==='boolean'&&typeof value.hero==='boolean'&&validHomeSettings(value)}
  let appearance={...appearanceDefaults,...website?.appearance};
  if(!validAppearance(appearance))appearance={...layoutDefaults.chest,design:'chest'};
  function luminance(hex){const c=hex.slice(1).match(/../g).map(v=>parseInt(v,16)/255).map(v=>v<=.04045?v/12.92:((v+.055)/1.055)**2.4);return c[0]*.2126+c[1]*.7152+c[2]*.0722}
  function readableAccent(accent,surface){
    const channels=accent.slice(1).match(/../g).map(c=>parseInt(c,16));const bg=luminance(surface),target=bg>.179?0:255;
    for(let step=0;step<=20;step++){const color='#'+channels.map(v=>Math.round(v+(target-v)*step/20).toString(16).padStart(2,'0')).join('');const light=luminance(color);if((Math.max(light,bg)+.05)/(Math.min(light,bg)+.05)>=4.5)return color}
    return target?'#ffffff':'#101415';
  }
  function setTheme(element,value){
    ensureThemeFont(value.font);
    const style=element.style,light=luminance(value.panel)>.179,sideLight=luminance(value.sidebar)>.179,bgLight=luminance(value.background)>.179;
    const vars={'--accent':value.accent,'--green':readableAccent(value.accent,value.panel),'--accent-ink':accentColors(value.accent).ink,'--bg':value.background,'--panel':value.panel,'--sidebar-bg':value.sidebar,'--text':light?'#182127':'#e8edeb','--muted':light?'#52616b':'#a4b2ad','--page-text':bgLight?'#182127':'#e8edeb','--page-muted':bgLight?'#52616b':'#a4b2ad','--sidebar-text':sideLight?'#182127':'#e8edeb','--sidebar-muted':sideLight?'#52616b':'#a4b2ad','--sidebar-accent':readableAccent(value.accent,value.sidebar),'--theme-font':themeFonts[value.font],'--theme-radius':value.radius+'px','--control-radius':Math.min(value.radius,10)+'px','--card-padding':value.density==='compact'?'11px':'15px','--art-height':value.density==='compact'?'105px':'141px','--grid-gap':value.density==='compact'?'10px':'15px','--border':light?'#c4cdd1':'#36413d'};
    for(const [key,val] of Object.entries(vars))style.setProperty(key,val);
    // Define derived colors on each preview as well as the document root.
    style.setProperty('--theme-soft','color-mix(in srgb,var(--accent) 10%,var(--panel))');style.setProperty('--theme-strong','color-mix(in srgb,var(--accent) 22%,var(--panel))');style.setProperty('--theme-border','color-mix(in srgb,var(--accent) 22%,var(--border))');
    style.colorScheme=light?'light':'dark';element.dataset.themeTone=light?'light':'dark';
  }
  function accentColors(hex){
    const channels=hex.slice(1).match(/../g).map(c=>parseInt(c,16));
    const linear=channels.map(c=>{const n=c/255;return n<=.04045?n/12.92:((n+.055)/1.055)**2.4});
    const luminance=linear[0]*.2126+linear[1]*.7152+linear[2]*.0722;
    // Keep accent text readable on dark surfaces, even for a very dark custom color.
    const text=luminance<.3?'#'+channels.map(c=>Math.round(c*.45+255*.55).toString(16).padStart(2,'0')).join(''):hex;
    return {text,ink:luminance>.179?'#101415':'#ffffff'};
  }
  function applyAppearance(value){
    setTheme(document.documentElement,value);
    if(value.design==='chest')ensureThemeFont('chestHeading');
    document.documentElement.dataset.design=value.design;
    const heading=document.querySelector('.hero h1');
    document.querySelector('.hero-copy>p').innerHTML=value.design==='nova'?'From your first diamond to your next upgrade. Find what you need. Make your inventory count.':'From your first diamond to your next upgrade.<br>Find what you need. Make your inventory count.';
    heading.innerHTML=value.design==='nova'?'Find your next<br><span>main character item.</span>':value.design==='market'?'A little trading.<br><span>A world of possibilities.</span>':value.design==='inventory'?'Your next upgrade.<br><span>One slot away.</span>':'Good finds.<br><span>Better trades.</span>';
    document.querySelector('.hero .eyebrow').innerHTML='<span></span> '+(value.design==='nova'?'YOUR WORLD. YOUR NEXT DISCOVERY.':value.design==='market'?'THE MARKET HALL · OPEN TO EVERY ADVENTURER':value.design==='inventory'?'SERVER INVENTORY / READY TO BROWSE':'THE SERVER MARKETPLACE');
    document.documentElement.dataset.themeMotion=String(value.motion);document.documentElement.dataset.themeHero=String(value.hero);
    const brand=document.querySelector('.brand');brand.replaceChildren();
    const wrap=document.createElement('span'),title=document.createElement('span'),subtitle=document.createElement('small');
    title.textContent=value.title;subtitle.textContent=value.subtitle;subtitle.hidden=!value.subtitle;wrap.append(title,subtitle);brand.append(wrap);
    document.querySelector('.footer-brand').textContent=value.title;
    document.title=value.title+' · Marketplace';
    let home=$('marketHomeLink');
    if(!home){home=document.createElement('a');home.id='marketHomeLink';home.className='market-home-link';home.innerHTML=icon('home')+'<span></span>';document.querySelector('.topbar-right').prepend(home)}
    const homeReady=value.homeEnabled&&validHomeDestination(value.homeUrl);
    home.hidden=!homeReady;if(homeReady)home.href=value.homeUrl;else home.removeAttribute('href');
    home.querySelector('span').textContent=value.homeLabel||'Home';
    home.target=value.homeNewTab?'_blank':'_self';home.rel='noopener noreferrer';
    home.title=(value.homeLabel||'Home')+(value.homeNewTab?' (opens in a new tab)':'');
    window.dispatchEvent(new Event('appearance-updated'));
  }
  applyAppearance(appearance);
  // Reveal only after both the layout attributes and its colors/branding are applied.
  document.documentElement.removeAttribute('data-appearance-pending');
  await ready;
  const root=$('settingsView');
  root.innerHTML=`<div class="admin-heading"><div><div class="eyebrow">YOUR SERVER, YOUR SHOP</div><h1>Shop administration</h1><p>The same controls you know. A little more room to work.</p></div><span class="admin-access">${icon('shield')} Server administrator</span></div><div class="admin-preview-bar"><span class="preview-dot"></span>Local preview · edits update this demo for this visit. No server is connected.</div><div class="admin-nav" role="group" aria-label="Admin sections">${[['shop','Shop & categories'],['config','Configuration'],['permissions','Permissions'],['server','Server items'],['players','Player shops'],['actions','Actions'],['audit','Change log']].map(([id,label])=>`<button data-admin-tab="${id}">${label}</button>`).join('')}</div><div id="adminContent"></div>`;
  const appearanceTab=document.createElement('button');appearanceTab.dataset.adminTab='appearance';appearanceTab.textContent='Appearance';
  root.querySelector('.admin-preview-bar').hidden=true;
  root.querySelector('.admin-nav').insertBefore(appearanceTab,root.querySelector('[data-admin-tab="permissions"]'));
  const navigation=document.querySelector('[data-nav="settings"]');
  navigation.innerHTML=icon('settings')+'<span>Administration</span>';
  navigation.setAttribute('aria-label','Administration');
  const dialog=document.createElement('dialog');dialog.id='adminEditor';dialog.className='admin-editor';dialog.setAttribute('aria-labelledby','adminEditorTitle');document.body.append(dialog);
  const currentContent=()=>$('adminContent');
  const catById=id=>cats.find(c=>c.id===id)||{name:id||'Miscellaneous'};
  const catItems=id=>id==='MISC'?items:items.filter(i=>i.adminCategory===id);
  const thumb=material=>/^[a-z0-9_]+$/i.test(material)?`<img class="pixel-item" src="https://mc.nerothe.com/img/1.21/minecraft_${material.toLowerCase()}.png" alt="" loading="lazy" onerror="this.hidden=true">`:`<span class="admin-custom-icon">${icon('cube')}</span>`;
  const input=(id,label,value='',extra='',hint='')=>`<label class="admin-field"><span>${label}</span><input id="${id}" value="${esc(value)}" ${extra}>${hint?`<small>${hint}</small>`:''}</label>`;
  const toggle=(id,label,value,hint='')=>`<label class="admin-toggle"><span><strong>${label}</strong>${hint?`<small>${hint}</small>`:''}</span><input id="${id}" type="checkbox" role="switch" ${value?'checked':''}></label>`;
  const categoryOptions=(id)=>cats.filter(c=>!['PERMISSIONS','SERVER_SHOP','PLAYER_SHOPS'].includes(c.id)).map(c=>`<option value="${esc(c.id)}" ${id===c.id?'selected':''}>${esc(c.name)}${c.slot<0?' (hidden)':''}</option>`).join('');
  function log(){} // Mutations are audited by the server, never synthesized in the browser.
  function closeEditor(){dialog.close();}
  dialog.addEventListener('close',()=>document.body.style.overflow='');
  function editor(title,subtitle,body,save,label='Save changes'){
    dialog.innerHTML=`<form id="adminEditorForm"><div class="admin-editor-head"><div><div class="eyebrow">SHOP EDITOR</div><h2 id="adminEditorTitle">${esc(title)}</h2><p>${esc(subtitle)}</p></div><button type="button" class="icon-button" id="closeAdminEditor" aria-label="Close editor">✕</button></div><div class="admin-editor-body">${body}<p id="adminFormError" class="admin-form-error" role="alert"></p></div><div class="admin-editor-footer"><span>Changes are saved to your Minecraft server</span><div><button class="secondary" type="button" id="cancelAdminEditor">Cancel</button><button class="primary" type="submit">${label}</button></div></div></form>`;
    $('closeAdminEditor').onclick=closeEditor;$('cancelAdminEditor').onclick=closeEditor;
    $('adminEditorForm').onsubmit=async event=>{event.preventDefault();const form=event.currentTarget;const submit=form.querySelector('[type=submit]');submit.disabled=true;try{await save()}catch(error){if($('adminFormError'))$('adminFormError').textContent=error.message}finally{submit.disabled=false}};
    if(!dialog.open)dialog.showModal();document.body.style.overflow='hidden';
  }
  function confirmAction(title,description,action){editor(title,description,'<div class="admin-confirm-note">'+esc(description)+'</div>',async()=>{const result=await action();closeEditor();await refresh();notify(typeof result==='string'?result:'Changes saved')},'Confirm change')}
  function numeric(id,{optional=false,min=-Infinity,max=Infinity,integer=false}={}){const field=$(id);if(optional&&field.value.trim()==='')return null;const n=Number(field.value);if(!field.value.trim()||!Number.isFinite(n)||n<min||n>max||(integer&&!Number.isInteger(n)))throw new Error('Check '+field.closest('label')?.querySelector('span')?.textContent+'.');return n}
  async function loadAdmin(){
    const [rawItems,rawCats,config,extra,audit]=await Promise.all([api('/api/admin/items',{auth:true}),api('/api/admin/categories',{auth:true}),api('/api/admin/config',{auth:true}),api('/api/admin/entries',{auth:true}),api('/api/admin/audit',{auth:true})]);
    items.splice(0,items.length,...rawItems.filter(i=>!i.item.includes(':')).map(i=>({...mapItem(i),material:i.item.toLowerCase(),adminCategory:i.category,basePrice:Math.max(0,i.basePrice),customName:i.customName||'',stockRate:i.stockRate,shortageHours:i.shortageHours,maxStock:i.maxStock,maxStockStorage:i.maxStockStorage,buyDisabled:i.buyDisabled,sellDisabled:i.sellDisabled,disabled:i.disabled})));
    cats.splice(0,cats.length,...rawCats.map(c=>({id:c.id,name:c.displayName,browse:c.displayName,material:c.icon.toLowerCase(),slot:c.slot,custom:c.isCustom,restock:c.restockTarget!==null,target:c.restockTarget??200,interval:c.restockInterval??30})));
    const slots=[20,21,22,23,24,29,30,31,32,33,40];
    defaults.splice(0,defaults.length,...rawCats.map((c,i)=>({id:c.id,name:c.defaultName,material:c.defaultIcon.toLowerCase(),slot:slots[i]??-1,restock:false,target:200,interval:30})));
    state.config=config;state.specials=extra.entries.filter(e=>e.type!=='server');state.serverItems=extra.entries.filter(e=>e.type==='server');state.listings=extra.listings;state.audit=audit;
    if(state.cat&&!catById(state.cat))state.cat=null;
    $('adminMaterials').innerHTML=items.map(i=>`<option value="${esc(i.material.toUpperCase())}"></option>`).join('');
  }
  async function refresh(){await loadAdmin();renderAdmin();await refreshMarket();}
  function switchTab(tab){state.tab=tab;state.search='';state.checked.clear();state.limit=40;renderAdmin()}
  document.querySelectorAll('[data-admin-tab]').forEach(b=>b.onclick=()=>switchTab(b.dataset.adminTab));
  function renderAdmin(){
    if(!authenticated){showLogin();return;}
    document.querySelectorAll('[data-admin-tab]').forEach(b=>{b.classList.toggle('active',b.dataset.adminTab===state.tab);b.setAttribute('aria-pressed',String(b.dataset.adminTab===state.tab))});
    ({shop:renderShop,config:renderConfig,appearance:renderAppearance,permissions:()=>renderSpecials(false),server:()=>renderSpecials(true),players:renderPlayers,actions:renderActions,audit:renderAudit}[state.tab])();
  }
  function renderShop(){
    currentContent().innerHTML=`<div class="admin-toolbar"><div class="admin-path"><button id="adminBack">Categories</button>${state.cat?'<span>/</span><strong>'+esc(catById(state.cat).name)+'</strong>':''}</div><div class="admin-toolbar-actions"><label class="search">${icon('search')}<input id="adminSearch" aria-label="Search admin items or categories" placeholder="Search items or categories…" value="${esc(state.search)}"></label><button class="secondary" id="newAdminCategory">+ Category</button><button class="primary" id="newAdminItem">+ Item</button></div></div><div id="adminShopResults"></div>`;
    $('adminBack').onclick=()=>{state.cat=null;state.search='';state.checked.clear();state.limit=40;renderShop()};
    $('newAdminCategory').onclick=()=>{const free=cats.find(c=>c.custom&&c.slot<0);if(free)editCategory(free,true);else notify('All 10 custom category slots are in use')};
    $('newAdminItem').onclick=()=>editItem(null);
    $('adminSearch').oninput=e=>{state.search=e.target.value;state.limit=40;state.checked.clear();renderShopResults()};renderShopResults();
  }
  function renderShopResults(){
    const query=state.search.trim().toLowerCase().replaceAll('_',' ');
    const target=$('adminShopResults');
    if(!state.cat&&!query){
      target.innerHTML=`<div class="admin-section-intro"><div><h2>Your shop, at a glance</h2><p>Open a category to manage its items. Use the pencil to change its name, icon, or restock rules.</p></div><span>${items.length} items · ${cats.filter(c=>c.slot>=0).length} categories</span></div><div class="admin-category-grid">${cats.filter(c=>c.slot>=0).sort((a,b)=>a.slot-b.slot).map(c=>{
        const list=c.id==='PERMISSIONS'?state.specials:c.id==='SERVER_SHOP'?state.serverItems:c.id==='PLAYER_SHOPS'?state.listings:catItems(c.id);
        return `<article class="admin-category-card"><button class="admin-category-edit" data-edit-cat="${esc(c.id)}" aria-label="Edit ${esc(c.name)} category">${icon('edit')}</button><button class="admin-category-open" data-open-cat="${esc(c.id)}">${thumb(c.material)}<h3>${esc(c.name)}</h3><span>${esc(c.id)}</span><div class="admin-category-stats"><strong>${list.length} items</strong>${list.some(i=>i.disabled)?'<span>'+list.filter(i=>i.disabled).length+' disabled</span>':''}${c.restock?'<span class="positive">↻ '+c.interval+'m</span>':''}</div></button><div class="admin-category-foot"><span>Slot ${c.slot}</span><span>Manage items ↗</span></div></article>`
      }).join('')}<button class="admin-add-category" id="addCategoryCard"><span>+</span><strong>Create a category</strong><small>${cats.filter(c=>c.custom&&c.slot<0).length} custom slots available</small></button></div><details class="admin-hidden-categories"><summary>Hidden categories (${cats.filter(c=>c.slot<0).length})</summary><div>${cats.filter(c=>c.slot<0).map(c=>`<button class="secondary" data-edit-cat="${esc(c.id)}">${esc(c.name)} · hidden</button>`).join('')}</div></details>`;
      document.querySelectorAll('[data-open-cat]').forEach(b=>b.onclick=()=>{const id=b.dataset.openCat;if(id==='PERMISSIONS')switchTab('permissions');else if(id==='SERVER_SHOP')switchTab('server');else if(id==='PLAYER_SHOPS')switchTab('players');else{state.cat=id;state.limit=40;state.checked.clear();renderShop()}});
      document.querySelectorAll('[data-edit-cat]').forEach(b=>b.onclick=()=>editCategory(catById(b.dataset.editCat)));
      $('addCategoryCard').onclick=$('newAdminCategory').onclick;return;
    }
    const list=(state.cat?catItems(state.cat):items).filter(i=>!query||(i.name+' '+catById(i.adminCategory).name).toLowerCase().includes(query));
    const shown=list.slice(0,state.limit);
    target.innerHTML=`<div class="admin-list-heading"><h2>${query?'Search results':esc(catById(state.cat).name)} <span>${list.length} items</span></h2>${state.cat?'<button class="secondary" id="editCurrentCategory">Edit category</button>':''}</div><div class="admin-bulk"><span id="adminSelectionCount">${state.checked.size} selected</span><select id="adminBulkOperation" aria-label="Bulk item action"><option value="enable">Enable items</option><option value="disable">Disable items</option><option value="buyOn">Enable buying</option><option value="buyOff">Disable buying</option><option value="sellOn">Enable selling</option><option value="sellOff">Disable selling</option></select><button id="applyAdminBulk" class="secondary">Apply to selected</button><span>Changes apply to selected items only</span></div><div class="table-wrap admin-items-table"><table><thead><tr><th><input id="selectAdminPage" type="checkbox" aria-label="Select visible items"></th><th>ITEM</th><th>BASE PRICE</th><th>STOCK</th><th>BUY / SELL</th><th>STATUS</th><th></th></tr></thead><tbody>${shown.map(i=>`<tr><td><input data-select-item="${esc(i.id)}" type="checkbox" aria-label="Select ${esc(i.name)}" ${state.checked.has(i.id)?'checked':''}></td><td><button class="admin-item-name" data-edit-item="${esc(i.id)}">${art(i.id)}<span>${esc(i.name)}<small>${esc(catById(i.adminCategory).name)}</small></span></button></td><td>${money(i.basePrice)}</td><td>${i.stock==null?'<span class="muted-stock">Unavailable</span>':i.stock.toLocaleString()}</td><td><span class="admin-dot ${i.buyDisabled?'off':''}"></span> / <span class="admin-dot ${i.sellDisabled?'off':''}"></span></td><td><span class="admin-status ${i.disabled?'off':''}">${i.disabled?'Disabled':'Enabled'}</span></td><td><button class="admin-row-edit" data-edit-item="${esc(i.id)}" aria-label="Edit ${esc(i.name)}">Edit ↗</button></td></tr>`).join('')||'<tr><td colspan="7">No items match this search.</td></tr>'}</tbody></table></div><div class="admin-list-footer"><span>Showing ${shown.length} of ${list.length} items</span>${shown.length<list.length?'<button class="secondary" id="moreAdminItems">Load more items</button>':''}</div>`;
    if($('editCurrentCategory'))$('editCurrentCategory').onclick=()=>editCategory(catById(state.cat));
    document.querySelectorAll('[data-edit-item]').forEach(b=>b.onclick=()=>editItem(items.find(i=>i.id===b.dataset.editItem)));
    document.querySelectorAll('[data-select-item]').forEach(b=>b.onchange=()=>{b.checked?state.checked.add(b.dataset.selectItem):state.checked.delete(b.dataset.selectItem);$('adminSelectionCount').textContent=state.checked.size+' selected'});
    $('selectAdminPage').onchange=e=>{shown.forEach(i=>e.target.checked?state.checked.add(i.id):state.checked.delete(i.id));renderShopResults()};
    if($('moreAdminItems'))$('moreAdminItems').onclick=()=>{state.limit+=40;renderShopResults()};
    $('applyAdminBulk').onclick=()=>{if(!state.checked.size){notify('Select at least one item first');return}const op=$('adminBulkOperation').value;confirmAction('Update '+state.checked.size+' items?',$('adminBulkOperation').selectedOptions[0].text+' for the selected items.',async()=>{const updates=op==='enable'?{disabled:false}:op==='disable'?{disabled:true}:op==='buyOn'?{buyDisabled:false}:op==='buyOff'?{buyDisabled:true}:op==='sellOn'?{sellDisabled:false}:{sellDisabled:true};await api('/api/admin/items/bulk',{auth:true,method:'POST',body:{items:[...state.checked],updates}});log('Bulk update',state.checked.size+' items',op);state.checked.clear()})};
  }
  function editItem(item){
    const isNew=!item;
    const draft=item?{...item}:{name:'',basePrice:1,stock:0,stockRate:5,maxStock:null,maxStockStorage:null,shortageHours:0,adminCategory:state.cat||'MISC',disabled:false,buyDisabled:false,sellDisabled:false};
    editor(isNew?'Add shop item':draft.name,isNew?'Choose a material and set its starting values.':'Adjust stock, price, category, and availability.',`${!isNew?'<div class="admin-edit-item-hero">'+art(item.id)+'<span>'+esc((item.material||item.id).toUpperCase())+'</span></div>':input('aeMaterial','Minecraft material','','required pattern="[A-Za-z0-9_]+" placeholder="DIAMOND" list="adminMaterials"')}${input('aeName','Display name',draft.customName||'','maxlength="100" placeholder="Default item name"')}<label class="admin-field"><span>Category</span><select id="aeCategory">${categoryOptions(draft.adminCategory)}</select></label><div class="admin-form-grid">${input('aePrice','Base price',draft.basePrice,'type="number" min="0" step="any" required')}${input('aeStock','Current stock',draft.stock??'','type="number" step="1" placeholder="Unavailable"','Leave blank to keep unknown stock.')}</div><div class="admin-stock-steps"><span>Quick stock adjustment</span><div>${[-64,-10,-1,1,10,64].map(n=>`<button type="button" data-stock-step="${n}">${n>0?'+':''}${n}</button>`).join('')}</div></div><h3 class="admin-form-heading">Pricing & storage</h3><div class="admin-form-grid">${input('aeRate','Shortage rate (% per negative unit)',draft.stockRate,'type="number" min="0" step="any" required')}${input('aeCurve','Pricing curve max stock',draft.maxStock??'','type="number" min="0.01" step="any" placeholder="Global default"')}${input('aeStorage','Storage hard limit',draft.maxStockStorage??'','type="number" min="0" step="1" placeholder="Unlimited"')}${input('aeShortage','Shortage hours',draft.shortageHours,'type="number" min="0" step="any" required')}</div><button type="button" class="admin-text-button" id="resetItemShortagePreview">Reset shortage hours to zero</button><h3 class="admin-form-heading">Availability</h3>${toggle('aeDisabled','Item disabled',draft.disabled,'Hide the item from the in-game shop.')}${toggle('aeBuyDisabled','Buying disabled',draft.buyDisabled,'Players cannot buy this item.')}${toggle('aeSellDisabled','Selling disabled',draft.sellDisabled,'Players cannot sell this item.')}${!isNew?'<button type="button" class="admin-danger" id="removeAdminItem">Remove from shop</button>':''}`,async()=>{
      const material=isNew?$('aeMaterial').value.trim().toLowerCase():item.material||item.id;
      if(isNew&&items.some(i=>(i.material||i.id)===material))throw new Error('That material already exists. Edit its existing entry.');
      const price=numeric('aePrice',{min:0}),stock=numeric('aeStock',{optional:true,integer:true}),limit=numeric('aeStorage',{optional:true,min:0,integer:true});
      if(limit!=null&&stock!=null&&stock>limit)throw new Error('Current stock exceeds the storage hard limit.');
      const next={...draft,id:item?.id||material,material,name:$('aeName').value.trim()||material.split('_').map(w=>w[0].toUpperCase()+w.slice(1)).join(' '),basePrice:price,stock,stockRate:numeric('aeRate',{min:0}),maxStock:numeric('aeCurve',{optional:true,min:.0001}),maxStockStorage:limit,shortageHours:numeric('aeShortage',{min:0}),adminCategory:$('aeCategory').value,disabled:$('aeDisabled').checked,buyDisabled:$('aeBuyDisabled').checked,sellDisabled:$('aeSellDisabled').checked};
      const body={basePrice:next.basePrice,stockRate:next.stockRate,maxStock:next.maxStock,maxStockStorage:next.maxStockStorage,shortageHours:next.shortageHours,category:next.adminCategory,disabled:next.disabled,buyDisabled:next.buyDisabled,sellDisabled:next.sellDisabled,displayName:$('aeName').value.trim()};
      if(next.stock!==null)body.stock=next.stock;
      if(isNew)await api('/api/admin/items/create',{auth:true,method:'POST',body:{material:material.toUpperCase(),basePrice:price,category:next.adminCategory}});
      await api('/api/admin/item/'+encodeURIComponent(material.toUpperCase()),{auth:true,method:'POST',body});
      closeEditor();await refresh();notify('Saved '+next.name);

    });
    document.querySelectorAll('[data-stock-step]').forEach(b=>b.onclick=()=>{$('aeStock').value=(Number($('aeStock').value)||0)+Number(b.dataset.stockStep)});
    $('resetItemShortagePreview').onclick=()=>{$('aeShortage').value=0};
    if(!isNew)$('removeAdminItem').onclick=()=>confirmAction('Remove '+item.name+'?','This disables the shop entry. You can enable it again from the admin item list.',async()=>{await api('/api/admin/item/'+encodeURIComponent(item.id),{auth:true,method:'DELETE'})});
  }
  function editCategory(cat,creating=false){
    const draft={...cat};if(creating){draft.slot=Array.from({length:54},(_,i)=>i).find(n=>!cats.some(c=>c.slot===n))??-1;draft.name='New category'}
    editor(creating?'Create category':'Edit '+cat.name,cat.id+' · Name, icon, inventory position, and restocking.',`<div class="admin-category-preview" id="categoryIconPreview">${thumb(draft.material)}<span>${esc(draft.name)}</span></div>${input('acName','Display name',draft.name,'required maxlength="60"')}${input('acIcon','Icon material or custom ID',draft.material.toUpperCase(),'required pattern="[A-Za-z0-9_:/.-]+" list="adminMaterials"','For example DIAMOND, GRASS_BLOCK, or a supported custom item ID.')}<div class="admin-form-grid">${input('acSlot','Inventory slot',draft.slot,'type="number" min="-1" max="53" step="1" required','Slots 0–53. Use -1 to hide the category.')}<button type="button" id="hideAdminCategory" class="secondary">Hide category</button></div><div class="admin-slot-picker" aria-label="Category inventory position">${Array.from({length:54},(_,i)=>{const occupied=cats.find(c=>c.slot===i&&c.id!==cat.id);return `<button type="button" data-cat-slot="${i}" class="${i===draft.slot?'selected':''}" ${occupied?'disabled':''} aria-label="Slot ${i}${occupied?', occupied by '+esc(occupied.name):''}">${occupied?thumb(occupied.material):i}</button>`}).join('')}</div><h3 class="admin-form-heading">Automatic restocking</h3>${toggle('acRestock','Restock this category',draft.restock,'Uses the global automatic restock setting.')}<div class="admin-form-grid">${input('acTarget','Target stock',draft.target,'type="number" min="0" step="1" required')}${input('acInterval','Interval (minutes)',draft.interval,'type="number" min="1" step="1" required')}</div><div class="admin-editor-extra"><button class="secondary" id="resetAdminCategory" type="button">Reset to defaults</button>${cat.custom&&!creating?'<button type="button" class="admin-danger" id="deleteAdminCategory">Delete category</button>':''}</div>`,async()=>{
      const slot=numeric('acSlot',{min:-1,max:53,integer:true});if(slot>=0&&cats.some(c=>c.id!==cat.id&&c.slot===slot))throw new Error('That inventory slot is already used. Choose an empty slot.');
      const name=$('acName').value.trim();if(!name)throw new Error('Enter a category name.');
      await api('/api/admin/category/'+encodeURIComponent(cat.id),{auth:true,method:'POST',body:{displayName:name,icon:$('acIcon').value.trim(),slot,restockTarget:$('acRestock').checked?numeric('acTarget',{min:1,integer:true}):null,restockInterval:$('acRestock').checked?numeric('acInterval',{min:1,integer:true}):null}});
      closeEditor();await refresh();notify('Saved category');

    });
    const updateSlot=()=>document.querySelectorAll('[data-cat-slot]').forEach(b=>b.classList.toggle('selected',Number(b.dataset.catSlot)===Number($('acSlot').value)));
    document.querySelectorAll('[data-cat-slot]').forEach(b=>b.onclick=()=>{$('acSlot').value=b.dataset.catSlot;updateSlot()});$('acSlot').oninput=updateSlot;
    $('hideAdminCategory').onclick=()=>{$('acSlot').value=-1;updateSlot()};
    $('acIcon').oninput=()=>{const value=$('acIcon').value.trim().toLowerCase();$('categoryIconPreview').innerHTML=thumb(value)+'<span>'+esc($('acName').value)+'</span>'};
    $('acName').oninput=()=>{$('categoryIconPreview').lastElementChild.textContent=$('acName').value};
    $('resetAdminCategory').onclick=()=>{const original=defaults.find(c=>c.id===cat.id);$('acName').value=original.name;$('acIcon').value=original.material;$('acSlot').value=original.slot;$('acRestock').checked=original.restock;$('acTarget').value=original.target;$('acInterval').value=original.interval;updateSlot();$('acIcon').oninput()};
    if($('deleteAdminCategory'))$('deleteAdminCategory').onclick=()=>confirmAction('Delete '+cat.name+'?','Its items will move to Miscellaneous and this custom category slot will become available again.',async()=>{await api('/api/admin/category/'+encodeURIComponent(cat.id),{auth:true,method:'DELETE'});state.cat=null});
  }
    function renderAppearance(){
    themeFontOptions.forEach(([key])=>ensureThemeFont(key));
    const draft={...appearance,layoutStyles:{...(appearance.layoutStyles||{})}};
    const choices=(key,label,options)=>`<fieldset class="appearance-choices"><legend>${label}</legend><div>${options.map(([value,name])=>`<button type="button" data-choice="${key}" data-value="${value}" aria-pressed="${String(draft[key])===String(value)}">${name}</button>`).join('')}</div></fieldset>`;
    const colorField=(key,label)=>`<div class="appearance-color-field"><label for="theme_${key}">${label}</label><div><input type="color" data-color-picker="${key}" value="${draft[key]}" aria-label="${label} picker"><input id="theme_${key}" data-color-text="${key}" value="${draft[key]}" aria-label="${label} hex" pattern="#[0-9a-fA-F]{6}" maxlength="7" spellcheck="false" required></div></div>`;
    currentContent().innerHTML=`<div class="admin-section-intro"><div><h2>Make this market yours</h2><p>Set the colors, typography, and layout players see throughout your shop.</p></div></div>
    <form id="appearanceForm"><div class="appearance-layout"><div class="appearance-controls">
      <section class="admin-config-card"><h3>Website design</h3><p>Six different layouts. The same shop, watchlist, and admin tools.</p><div class="design-picker" role="group" aria-label="Website design"><button type="button" data-design="classic" aria-pressed="${draft.design==='classic'}"><span class="design-thumbnail design-classic"><i></i><b></b><em></em><em></em><em></em></span><strong>Classic</strong><small>Sidebar navigation, a colorful welcome panel, and item cards.</small></button><button type="button" data-design="market" aria-pressed="${draft.design==='market'}"><span class="design-thumbnail design-market"><i></i><b></b><em></em><em></em><em></em></span><strong>Market Hall</strong><small>A wide storefront, top navigation, and a compact trading list.</small></button><button type="button" data-design="inventory" aria-pressed="${draft.design==='inventory'}"><span class="design-thumbnail design-inventory"><i></i><b></b><em></em><em></em><em></em></span><strong>Inventory</strong><small>A game-inspired shop with item slots, gold highlights, and a compact toolbar.</small></button><button type="button" data-design="nova" aria-pressed="${draft.design==='nova'}"><span class="design-thumbnail design-nova"><i></i><b></b><em></em><em></em><em></em></span><strong>Nova</strong><small>A modern interactive storefront with quick search, featured discoveries, and fluid cards.</small></button><button type="button" data-design="chest" aria-pressed="${draft.design==='chest'}"><span class="design-thumbnail design-chest"><i></i><b></b><em></em><em></em><em></em></span><strong>Chest GUI</strong><small>The imported Minecraft storefront: stone panels, beveled slots, pixel headings, and a category rail.</small></button><button type="button" data-design="dsx" aria-pressed="${draft.design==='dsx'}"><span class="design-thumbnail design-dsx"><i></i><b></b><em></em><em></em><em></em></span><strong>DSX Exchange</strong><small>A trading terminal with a quote board, amber and cyan prices, and a market ticker.</small></button></div><p class="appearance-hint">Each design remembers its own colors and styling. These preferences are shared by everyone on this server website.</p></section>
      <section class="admin-config-card"><h3>Branding</h3>${input('appearanceTitle','Shop title',draft.title,'required maxlength="40" autocomplete="off"')}${input('appearanceSubtitle','Sidebar subtitle',draft.subtitle,'maxlength="60" autocomplete="off"','Optional. Leave empty to show only the title.')}</section>
      <section class="admin-config-card"><h3>Home button</h3><p>Redirect players to your server website when they click Home.</p>${toggle('appearanceHomeEnabled','Show Home button',draft.homeEnabled,'Display it in the top bar on every page.')}<div class="appearance-home-fields">${input('appearanceHomeLabel','Button label',draft.homeLabel,'maxlength="32" autocomplete="off"')}${input('appearanceHomeUrl','Website URL',draft.homeUrl,'type="url" required maxlength="2048" inputmode="url" spellcheck="false" placeholder="https://yourserver.com"')}<p class="appearance-hint">Enter the full website address, starting with https:// or http://. Home redirects there in the current tab unless you enable a new tab below.</p>${toggle('appearanceHomeNewTab','Open in a new tab',draft.homeNewTab)}</div><div class="appearance-home-preview" id="appearanceHomePreview">${icon('home')}<span id="appearanceHomePreviewLabel"></span><small id="appearanceHomePreviewUrl"></small></div></section>
      <section class="admin-config-card"><h3>Colors & surfaces</h3>${choices('mode','Base theme',[['dark','Dark'],['light','Light'],['custom','Custom']])}<p class="appearance-hint">Dark and Light reset the three surface colors. Custom lets you choose them separately.</p><h4 class="appearance-color-heading">Color palettes</h4><p class="appearance-hint">Apply four coordinated colors to this design, then fine-tune them below.</p><div class="theme-palettes" role="group" aria-label="Color palettes">${themePalettes.map(p=>`<button type="button" data-theme-palette="${p.id}" aria-pressed="false" aria-label="${p.name} palette"><span class="palette-swatches" aria-hidden="true">${paletteKeys.map(key=>`<i style="background:${p[key]}"></i>`).join('')}</span><strong>${p.name}</strong><small>${p.tone}</small></button>`).join('')}</div><h4 class="appearance-color-heading">Accent presets</h4><div class="theme-presets" role="group" aria-label="Accent presets">${themePresets.map(([name,color])=>`<button type="button" data-theme-color="${color}" aria-pressed="${draft.accent===color}" style="--swatch:${color}"><span></span>${name}</button>`).join('')}</div><div class="appearance-colors">${colorField('accent','Accent color')}${colorField('background','Page background')}${colorField('panel','Cards & panels')}${colorField('sidebar','Sidebar background')}</div><p class="appearance-hint">Text colors adjust automatically to keep your chosen surfaces readable.</p></section>
      <section class="admin-config-card"><h3>Typography & layout</h3><fieldset class="appearance-choices appearance-font-choices"><legend>Font style</legend><div>${themeFontOptions.map(([key,name,family])=>`<button type="button" data-choice="font" data-value="${key}" aria-label="${name} font" aria-pressed="${draft.font===key}"><span class="font-option-label">${name}</span><span class="font-option-sample" style="font-family:${esc(themeFonts[key])}">Diamond · $100</span><small>${family}</small></button>`).join('')}</div><p class="appearance-hint">Choose a lettering style, then save to apply it. Web fonts use a system fallback if unavailable.</p></fieldset>${choices('radius','Corner style',[[0,'Square'],[10,'Soft'],[20,'Rounded']])}${choices('density','Catalog spacing',[['comfortable','Comfortable'],['compact','Compact']])}${toggle('themeHero','Show welcome banner',draft.hero,'Display the large welcome banner above the catalog.')}${toggle('themeMotion','Animated effects',draft.motion,'Floating artwork and hover movement. Reduced-motion browser preferences are always respected.')}</section>
    </div><section class="appearance-preview-wrap"><div class="eyebrow">LIVE PREVIEW</div><div class="appearance-sample" id="appearanceSample"><div class="appearance-sample-brand"><strong id="appearanceSampleTitle"></strong><small id="appearanceSampleSubtitle"></small></div><div class="appearance-sample-nav">${icon('grid')} Item catalog <span>${items.length}</span></div><div class="appearance-mini-hero" id="appearanceSampleHero">Good finds. <strong>Better trades.</strong></div><div class="appearance-sample-item">${thumb('diamond')}<div><small>YOUR MARKETPLACE</small><h3>Diamond</h3><p>Available to trade</p></div></div><div class="appearance-sample-prices"><span>Buy price<strong>$100.00</strong></span><span>Sell price<strong>$70.00</strong></span></div><div class="appearance-sample-button">Explore the catalog <span>↗</span></div></div><p>Save to apply your theme for everyone visiting your server website. Only administrators can change these settings.</p><div class="appearance-preview-actions"><button class="primary" type="submit">Save appearance</button><button class="secondary" type="button" id="discardAppearance">Discard unsaved changes</button><button class="admin-text-button" type="button" id="resetAppearance">Reset to defaults</button></div><p id="appearanceStatus" role="status">Saved appearance applies to every visitor and survives server restarts.</p><p class="admin-form-error" id="appearanceError" role="alert"></p></section></div></form>`;
    function updateSample(){
      $('appearanceSampleTitle').textContent=draft.title||appearanceDefaults.title;
      $('appearanceSampleSubtitle').textContent=draft.subtitle;
      $('appearanceSampleHero').hidden=!draft.hero;
      $('appearanceHomePreview').hidden=!draft.homeEnabled;$('appearanceHomePreviewLabel').textContent=draft.homeLabel||'Home';$('appearanceHomePreviewUrl').textContent=(draft.homeUrl||'Choose a destination')+(draft.homeNewTab?' ↗':'');
      for(const id of ['appearanceHomeLabel','appearanceHomeUrl','appearanceHomeNewTab'])$(id).disabled=!draft.homeEnabled;
      setTheme($('appearanceSample'),draft);
      $('appearanceSample').dataset.design=draft.design;
      document.querySelectorAll('button[data-design]').forEach(b=>b.setAttribute('aria-pressed',String(b.dataset.design===draft.design)));
      $('appearanceSample').dataset.motion=String(draft.motion);
      document.querySelectorAll('[data-choice]').forEach(b=>b.setAttribute('aria-pressed',String(b.dataset.value===String(draft[b.dataset.choice]))));
      document.querySelectorAll('[data-theme-color]').forEach(b=>b.setAttribute('aria-pressed',String(b.dataset.themeColor===draft.accent.toLowerCase())));
      document.querySelectorAll('[data-theme-palette]').forEach(b=>{const palette=themePalettes.find(p=>p.id===b.dataset.themePalette);b.setAttribute('aria-pressed',String(paletteKeys.every(key=>draft[key].toLowerCase()===palette[key])))});
    }
    function syncFields(){
      $('appearanceTitle').value=draft.title;$('appearanceSubtitle').value=draft.subtitle;
      $('themeHero').checked=draft.hero;$('themeMotion').checked=draft.motion;
      $('appearanceHomeEnabled').checked=draft.homeEnabled;$('appearanceHomeLabel').value=draft.homeLabel;$('appearanceHomeUrl').value=draft.homeUrl;$('appearanceHomeNewTab').checked=draft.homeNewTab;
      document.querySelectorAll('[data-color-text]').forEach(f=>f.value=draft[f.dataset.colorText]);
      document.querySelectorAll('[data-color-picker]').forEach(f=>f.value=draft[f.dataset.colorPicker]);updateSample();
    }
    $('appearanceTitle').oninput=e=>{draft.title=e.target.value;updateSample()};$('appearanceSubtitle').oninput=e=>{draft.subtitle=e.target.value;updateSample()};
    document.querySelectorAll('button[data-design]').forEach(b=>b.onclick=()=>{if(b.dataset.design===draft.design)return;draft.layoutStyles[draft.design]=layoutStyle(draft);draft.design=b.dataset.design;const saved=draft.layoutStyles[draft.design];const candidate={...appearanceDefaults,...saved};Object.assign(draft,layoutStyle(saved&&validAppearance(candidate)?candidate:layoutDefaults[draft.design]));syncFields();$('appearanceStatus').textContent='Design selected. Save appearance to switch the website.'});
    $('appearanceHomeEnabled').onchange=e=>{draft.homeEnabled=e.target.checked;updateSample()};$('appearanceHomeLabel').oninput=e=>{draft.homeLabel=e.target.value;updateSample()};$('appearanceHomeUrl').oninput=e=>{draft.homeUrl=e.target.value;updateSample()};$('appearanceHomeNewTab').onchange=e=>{draft.homeNewTab=e.target.checked;updateSample()};
    document.querySelectorAll('[data-choice]').forEach(b=>b.onclick=()=>{const key=b.dataset.choice;draft[key]=key==='radius'?Number(b.dataset.value):b.dataset.value;if(key==='mode'&&themeSurfaces[draft.mode])Object.assign(draft,themeSurfaces[draft.mode]);syncFields();$('appearanceStatus').textContent='Unsaved changes · save to apply.'});
    function changeColor(key,value){if(!/^#[0-9a-f]{6}$/i.test(value))return;draft[key]=value.toLowerCase();if(key!=='accent')draft.mode='custom';document.querySelector(`[data-color-picker="${key}"]`).value=draft[key];document.querySelector(`[data-color-text="${key}"]`).value=draft[key];updateSample();$('appearanceStatus').textContent='Unsaved changes · save to apply.'}
    document.querySelectorAll('[data-color-text]').forEach(f=>f.oninput=()=>changeColor(f.dataset.colorText,f.value));
    document.querySelectorAll('[data-color-picker]').forEach(f=>f.oninput=()=>changeColor(f.dataset.colorPicker,f.value));
    document.querySelectorAll('[data-theme-color]').forEach(b=>b.onclick=()=>changeColor('accent',b.dataset.themeColor));
    document.querySelectorAll('[data-theme-palette]').forEach(b=>b.onclick=()=>{const palette=themePalettes.find(p=>p.id===b.dataset.themePalette);for(const key of paletteKeys)draft[key]=palette[key];draft.mode='custom';syncFields();$('appearanceStatus').textContent=palette.name+' palette selected · save to apply.'});
    $('themeHero').onchange=e=>{draft.hero=e.target.checked;updateSample()};$('themeMotion').onchange=e=>{draft.motion=e.target.checked;updateSample()};
    $('discardAppearance').onclick=renderAppearance;
    $('resetAppearance').onclick=()=>{Object.assign(draft,appearanceDefaults);syncFields();$('appearanceStatus').textContent='Defaults selected. Save appearance to apply them.'};
    $('appearanceForm').oninput=()=>{$('appearanceStatus').textContent='Unsaved changes · save to apply.';$('appearanceError').textContent=''};
    $('appearanceForm').onsubmit=async event=>{
      event.preventDefault();const next={...draft,title:draft.title.trim(),subtitle:draft.subtitle.trim(),homeLabel:draft.homeLabel.trim(),homeUrl:draft.homeUrl.trim()};
      if(!validHomeSettings(next)){$('appearanceError').textContent='Enter a Home button label and a full website URL starting with https:// or http://.';return}
      if(!validAppearance(next)){$('appearanceError').textContent='Check the shop title and theme values. Colors must use six-digit hex codes.';return}
      next.layoutStyles={};for(const id of ['classic','market','inventory','nova','chest','dsx']){const saved=draft.layoutStyles[id];if(saved&&validAppearance({...appearanceDefaults,...saved}))next.layoutStyles[id]=layoutStyle(saved)}next.layoutStyles[next.design]=layoutStyle(next);
      const submit=$('appearanceForm').querySelector('[type=submit]');submit.disabled=true;
      try{appearance=await api('/api/admin/appearance',{auth:true,method:'POST',body:next});applyAppearance(appearance);$('appearanceError').textContent='';$('appearanceStatus').textContent='Appearance saved for all visitors.';notify('Website appearance saved')}
      catch(error){$('appearanceError').textContent=error.message}
      finally{submit.disabled=false}

    };
    updateSample();
  }

  function renderConfig(){
    const sections=[['Dynamic pricing',cfgSchema],['Economy',cfgSchema.economy],['Player shops',cfgSchema.shop],['Web server',cfgSchema.web],['Restocking, sync & display',cfgSchema.misc]];
    currentContent().innerHTML=`<form id="adminConfigForm"><div class="admin-section-intro"><div><h2>Configuration</h2><p>All existing web settings, plus the in-game input controls and console transaction logging.</p></div><button class="primary">Save configuration</button></div><div class="admin-config-grid">${sections.map(([name,group])=>`<section class="admin-config-card"><h3>${name}</h3>${group.toggles.map(f=>toggle('cfg_'+f.key,f.label,state.config[f.key],esc(f.desc))).join('')}<div class="admin-form-grid">${group.numbers.map(f=>input('cfg_'+f.key,f.label,state.config[f.key],f.isText?'type="text"':`type="number" step="${Number(f.step)>=1?1:"any"}" min="${['maxStock','maxListingsPerPlayer'].includes(f.key)?1:0}" ${f.key.toLowerCase().includes('port')?'max="65535" min="1"':''} required`,esc(f.desc))).join('')}</div></section>`).join('')}<section class="admin-config-card"><h3>Logging & in-game menus</h3>${toggle('cfg_websiteUsageMetrics','Share website usage counts',state.config.websiteUsageMetrics,'Send aggregate page views, feature counts, and the selected design to DynamicShop via bStats. Visitors can also opt out. No search text or player names.')}${toggle('cfg_consoleTransactions','Console transaction logs',state.config.consoleTransactions,'Print completed transactions to the console.')}${toggle('cfg_useDialogGui','Use dialog menus',state.config.useDialogGui,'Available on compatible Paper servers.')}<label class="admin-field"><span>Admin input method</span><select id="cfg_inputMethod">${['auto','dialog','anvil','chat'].map(v=>`<option ${state.config.inputMethod===v?'selected':''}>${v}</option>`).join('')}</select><small>Same input method selector as the in-game admin GUI.</small></label></section></div><div class="admin-config-footer"><span id="adminConfigStatus">Web server and cross-server connection changes require a server restart when deployed.</span><button type="button" class="secondary" id="revertAdminConfig">Discard unsaved changes</button><button class="primary">Save configuration</button></div><p id="adminConfigError" class="admin-form-error" role="alert"></p></form>`;
    $('revertAdminConfig').onclick=renderConfig;
    $('adminConfigForm').onsubmit=async e=>{e.preventDefault();const next={...state.config};try{for(const input of $('adminConfigForm').querySelectorAll('[id^="cfg_"]')){const key=input.id.slice(4);next[key]=input.type==='checkbox'?input.checked:input.type==='number'?Number(input.value):input.value;if(input.type==='number'&&!Number.isFinite(next[key]))throw new Error('Enter valid numeric settings.')}if(next.sellTaxPercent>100||next.curveStrength>1||next.highInflationCorrectionReductionPercent>100)throw new Error('Sell tax and correction reduction must be 0–100%; curve strength must be 0–1.');if(next.maxPriceMultiplier<next.minPriceMultiplier)throw new Error('Maximum price multiplier must be at least the minimum.');if(![27,36,45,54].includes(next.shopMenuSize))throw new Error('Shop menu size must be 27, 36, 45, or 54.');if(next.maxRecentTransactions<1||next.webserverPort<1||next.crossServerPort<1)throw new Error('Transaction history capacity and ports must be greater than zero.');const changes=Object.keys(next).filter(k=>next[k]!==state.config[k]);const submit=$('adminConfigForm').querySelectorAll('button');submit.forEach(b=>b.disabled=true);try{await api('/api/admin/config',{auth:true,method:'POST',body:Object.fromEntries(changes.map(k=>[k,next[k]]))});state.config=next;await refreshMarket();$('adminConfigError').textContent='';$('adminConfigStatus').textContent='Configuration saved. Connection and history-capacity changes require a restart.';notify('Configuration saved')}finally{submit.forEach(b=>b.disabled=false)}}catch(error){$('adminConfigError').textContent=error.message}};
  }
  function renderSpecials(server){
    const list=server?state.serverItems:state.specials;
    currentContent().innerHTML=`<div class="admin-section-intro"><div><h2>${server?'Server items':'Permissions, groups & commands'}</h2><p>${server?'Edit special deliveries, their prices, icons, and access requirements.':'Manage the upgrades players can buy through your shop.'}</p></div><button class="primary" id="newSpecialAdmin">+ ${server?'Server item':'Permission / group'}</button></div><div class="table-wrap"><table><thead><tr><th>NAME</th><th>TYPE</th><th>VALUE</th><th>PRICE</th><th>REQUIRED PERMISSION</th><th></th></tr></thead><tbody>${list.map(i=>`<tr><td>${thumb(i.material)}${esc(i.name)}</td><td>${esc(i.type)}</td><td>${esc(i.value)}</td><td>${money(i.price)}</td><td>${esc(i.gate||'None')}</td><td><button class="admin-row-edit" data-special="${esc(i.id)}">Edit ↗</button></td></tr>`).join('')||'<tr><td colspan="6">No entries yet. Add your first item above.</td></tr>'}</tbody></table></div>`;
    $('newSpecialAdmin').onclick=()=>editSpecial(null,server);document.querySelectorAll('[data-special]').forEach(b=>b.onclick=()=>editSpecial(list.find(i=>i.id===b.dataset.special),server));
  }
  function editSpecial(item,server){
    const data=item||{type:server?'server':'perm',name:'',value:'',price:0,material:'enchanted_book',world:'',gate:'',delivery:'item',command:''};
    editor(item?'Edit '+item.name:'Add '+(server?'server item':'permission / group'),'Configure what this purchase gives the player.',`${!server?'<label class="admin-field"><span>Purchase type</span><select id="asType">'+[['perm','Permission node'],['group','Permission group'],['command','Console command']].map(([v,t])=>`<option value="${v}" ${v===data.type?'selected':''}>${t}</option>`).join('')+'</select></label>':''}${input('asName','Display name',data.name,'required maxlength="100"')}${input('asValue',server?'Item identifier':'Permission, group, or command',data.value,'required',server?'The ID used by the selected delivery method.':'For commands, use {player} and omit the leading slash.')}${!server?input('asWorld','World (optional)',data.world):''}<div class="admin-form-grid">${input('asPrice','Price',data.price,'type="number" min="0" step="any" required')}${input('asMaterial','Display material',data.material,'required pattern="[A-Za-z0-9_]+" list="adminMaterials"')}</div>${input('asGate','Required permission (optional)',data.gate)}${server?'<label class="admin-field"><span>Delivery method</span><select id="asDelivery">'+['item','command','itemsadder','nexo','oraxen','nbt','component','valhallammo','stored_item'].map(v=>`<option ${v===data.delivery?'selected':''}>${v}</option>`).join('')+'</select></label>'+input('asCommand','Delivery command, data, or custom item ID',data.command||'','maxlength="65536"')+input('asDeliveryMaterial','Delivered Minecraft material',data.deliveryMaterial||data.material,'required pattern="[A-Za-z0-9_]+"')+input('asAmount','Delivered amount',data.amount||1,'type="number" min="1" max="2304" step="1" required'):''}${item?'<button type="button" class="admin-danger" id="deleteSpecialAdmin">Delete entry</button>':''}`,async()=>{
      const next={...data,id:item?.id||'web_'+Date.now(),name:$('asName').value.trim(),type:server?'server':$('asType').value,value:$('asValue').value.trim(),price:numeric('asPrice',{min:0}),material:$('asMaterial').value.toLowerCase(),gate:$('asGate').value.trim(),world:server?'':$('asWorld').value.trim(),delivery:server?$('asDelivery').value:null,command:server?$('asCommand').value.trim():null};
      if(server&&next.delivery==='command'&&!next.command)throw new Error('Enter the delivery command.');
      if(server){next.deliveryMaterial=$('asDeliveryMaterial').value;next.amount=numeric('asAmount',{min:1,max:2304,integer:true})}
      await api('/api/admin/entry/'+encodeURIComponent(next.id),{auth:true,method:'POST',body:{...next,create:!item}});closeEditor();await refresh();notify('Special item saved');

    });
    if(item)$('deleteSpecialAdmin').onclick=()=>confirmAction('Delete '+item.name+'?','This removes the entry from the server shop.',async()=>{await api('/api/admin/special-items/'+encodeURIComponent(item.id),{auth:true,method:'DELETE'})});
  }
  function renderPlayers(){currentContent().innerHTML=`<div class="admin-section-intro"><div><h2>Player shop listings</h2><p>Review and remove listings, just like the existing admin panel. These are live server listings.</p></div><span>${state.listings.length} listings</span></div><div class="table-wrap"><table><thead><tr><th>ITEM</th><th>SELLER</th><th>QUANTITY</th><th>PRICE</th><th></th></tr></thead><tbody>${state.listings.map(i=>`<tr><td>${thumb(i.material)}${esc(i.name)}</td><td>${esc(i.seller)}</td><td>${i.quantity}</td><td>${money(i.price)}</td><td><button class="admin-danger" data-remove-listing="${esc(i.id)}">Remove listing</button></td></tr>`).join('')||'<tr><td colspan="5">No player listings.</td></tr>'}</tbody></table></div>`;document.querySelectorAll('[data-remove-listing]').forEach(b=>b.onclick=()=>{const i=state.listings.find(i=>i.id===b.dataset.removeListing);confirmAction('Remove '+i.seller+'’s listing?','Remove '+i.quantity+' × '+i.name+' from the server shop.',async()=>{await api('/api/admin/playershop/'+encodeURIComponent(i.id),{auth:true,method:'DELETE'})})})}
    function renderActions(){
    currentContent().innerHTML=`<div class="admin-section-intro"><div><h2>Shop actions</h2><p>Maintenance controls for your server.</p></div></div><div class="admin-action-grid"><section class="admin-config-card"><h3>Website files</h3><p id="websiteFileStatus" role="status">Checking installed website files…</p><p>Install the website bundled with this plugin. Existing files are backed up in <code>web-backups/</code>. Shop data and appearance settings are kept.</p><button class="primary" id="updateWebsiteFiles">Update website files</button><button class="secondary" id="checkWebsiteFiles">Check again</button></section><section class="admin-config-card"><h3>Reset shortage data</h3><p>Clear accumulated shortage hours for all items.</p><button class="admin-danger" id="resetAllAdminShortage">Reset all shortage data</button></section><section class="admin-config-card"><h3>Set stock in bulk</h3><p>Set the same stock level for all standard shop items.</p>${input('adminAllStock','Stock amount',0,'type="number" step="1"')}<button class="secondary" id="setAllAdminStock">Set all stock</button></section><section class="admin-config-card"><h3>Reload plugin</h3><p>Reload configuration files, like <code>/shopadmin reload</code>.</p><button class="secondary" id="reloadAdminPlugin">Reload plugin</button></section></div>`;
    async function check(){try{const status=await api('/api/admin/website-files',{auth:true});if($('websiteFileStatus'))$('websiteFileStatus').textContent=status.needsUpdate?'Update available: '+status.outdatedFiles.join(', '):'Website files are up to date with plugin '+status.version+'.'}catch(error){if($('websiteFileStatus'))$('websiteFileStatus').textContent=error.message}}
    $('checkWebsiteFiles').onclick=check;check();
    $('updateWebsiteFiles').onclick=()=>confirmAction('Update website files?','Back up the current web files, then replace them with the files bundled with this plugin. Custom HTML, CSS, and JavaScript edits will be replaced; the backup keeps a copy.',async()=>{const result=await api('/api/admin/website-files/update',{auth:true,method:'POST',body:{}});setTimeout(()=>location.reload(),3500);return 'Website updated. Backup: '+result.backup});
    $('resetAllAdminShortage').onclick=()=>confirmAction('Reset all shortage data?','Clear every item shortage counter on the server.',()=>api('/api/admin/resetshortage',{auth:true,method:'POST',body:{}}));
    $('setAllAdminStock').onclick=()=>{const value=Number($('adminAllStock').value);if(!$('adminAllStock').value.trim()||!Number.isSafeInteger(value)){notify('Enter a whole stock amount');return}const invalid=items.find(i=>i.maxStockStorage!=null&&value>i.maxStockStorage);if(invalid){notify('Exceeds the storage limit for '+invalid.name);return}confirmAction('Set all stock to '+value+'?',items.length+' standard shop items will be updated.',()=>api('/api/admin/items/bulk',{auth:true,method:'POST',body:{items:items.map(i=>i.id),updates:{stock:value}}}))};
    $('reloadAdminPlugin').onclick=()=>confirmAction('Reload plugin?','Reload the server configuration from disk.',()=>api('/api/admin/reload',{auth:true,method:'POST',body:{}}));
  }
  function renderAudit(){currentContent().innerHTML=`<div class="admin-section-intro"><div><h2>Admin change log</h2><p>Changes recorded by your Minecraft server.</p></div><button class="secondary" id="refreshAdminAudit">Refresh</button></div><div class="admin-audit-list">${state.audit.map(entry=>`<article><span class="admin-audit-symbol">${icon('edit')}</span><div><strong>${esc(entry.action)} <span>· ${esc(entry.target)}</span></strong><p>${esc(entry.details)}</p><small>${esc(entry.user)} · ${esc(new Date(entry.timestamp).toLocaleString())}</small></div></article>`).join('')||'<div class="admin-empty">No changes recorded.</div>'}</div>`;$('refreshAdminAudit').onclick=async()=>{try{state.audit=await api('/api/admin/audit',{auth:true});renderAudit()}catch(error){notify(error.message)}}}

  const materialList=document.createElement('datalist');materialList.id='adminMaterials';materialList.innerHTML=items.map(i=>`<option value="${i.material.toUpperCase()}"></option>`).join('');document.body.append(materialList);
  function fixBreadcrumb(){if(location.hash==='#settings')$('pageCrumb').textContent='Administration'}window.addEventListener('hashchange',fixBreadcrumb);
    function setAccess(on){
    authenticated=on;navigation.hidden=!on;root.querySelector('.admin-nav').hidden=!on;root.querySelector('.admin-access').hidden=!on;document.querySelector('.manage-label').hidden=!on;
    document.querySelector('.profile>div').innerHTML=on?esc(username)+'<small>Server administrator</small>':'Visitor<small>Public marketplace</small>';
    $('adminLoginLink').textContent=on?'Administration':'Admin sign in';
  }
  function showLogin(message=''){
    setAccess(false);
    if(website?.adminEnabled===false){currentContent().innerHTML='<div class="admin-empty">Web administration is disabled in the server configuration.</div>';return}
    const registering=!!registrationToken;
    currentContent().innerHTML=`<section class="admin-config-card" style="max-width:480px;margin:30px auto"><h2>${registering?'Create your admin account':'Admin sign in'}</h2><p>${registering?'Your in-game registration link authorizes this account. Choose a password.':'Use your DynamicShop web admin account. To register, an operator must run /shopadmin webadmin in game.'}</p><form id="liveLoginForm">${registering?'':input('loginUsername','Username','','required name="username" autocomplete="username"')}${input('loginPassword','Password','','type="password" required name="password" minlength="'+(registering?8:1)+'" autocomplete="'+(registering?'new-password':'current-password')+'"')}<button class="primary" type="submit">${registering?'Create account':'Sign in'}</button><p class="admin-form-error" id="loginError" role="alert">${esc(message)}</p></form></section>`;
    $('liveLoginForm').onsubmit=async event=>{event.preventDefault();if(authBusy)return;authBusy=true;const form=event.currentTarget,fields=new FormData(form),button=form.querySelector('button');const password=String(fields.get('password')||''),loginName=String(fields.get('username')||'').trim();button.disabled=true;try{
      if(!password||(!registering&&!loginName))throw new Error('Enter your username and password.');
      const result=await api('/api/auth/'+(registering?'register':'login'),{method:'POST',body:registering?{token:registrationToken,password}:{username:loginName,password}});
      adminSession=result.session;registrationToken='';try{localStorage.setItem('ds_admin_session',adminSession)}catch{}
      await signIn();
    }catch(error){if($('loginError'))$('loginError').textContent=error.message;else showLogin(error.message)}finally{authBusy=false;button.disabled=false}};
  }
  async function signIn(){
    const result=await api('/api/auth/verify',{auth:true});username=result.username||'Administrator';
    await loadAdmin();setAccess(true);renderAdmin();
  }
  const logout=document.createElement('button');logout.className='secondary';logout.textContent='Sign out';logout.hidden=true;root.querySelector('.admin-heading').append(logout);
  logout.onclick=async()=>{try{await api('/api/auth/logout',{auth:true,method:'POST',body:{}})}catch(error){notify(error.message);return}adminSession='';try{localStorage.removeItem('ds_admin_session')}catch{}logout.hidden=true;showLogin();notify('Signed out')};
  const originalSetAccess=setAccess;setAccess=on=>{originalSetAccess(on);logout.hidden=!on};
  window.addEventListener('admin-expired',()=>{adminSession='';try{localStorage.removeItem('ds_admin_session')}catch{}if(dialog.open)dialog.close();showLogin('Your session expired. Please sign in again.')});
  $('adminLoginLink').href='#settings';
  setAccess(false);
  if(website?.adminEnabled!==false&&adminSession){try{await signIn()}catch(error){showLogin(error.message)}}
  else if(registrationToken){
    try{const response=await fetch('/api/auth/verify',{headers:{'X-Admin-Token':registrationToken},cache:'no-store'});const data=await response.json();if(!response.ok||!data.valid){registrationToken='';showLogin('This registration link has expired. Run /shopadmin webadmin for a new link.')}else if(data.alreadyRegistered){registrationToken='';showLogin('Your admin account already exists. Sign in below.')}else showLogin()}
    catch(error){showLogin('Could not verify your registration link.')}
  }else showLogin();
  // Refresh shared appearance for visitors, but never replace an administrator's unsaved form.
  setInterval(async()=>{if(document.hidden||page==='settings')return;try{const next=await api('/api/website');if(validAppearance(next.appearance)){appearance=next.appearance;applyAppearance(appearance)}}catch{}},60000);

  fixBreadcrumb();
})();
