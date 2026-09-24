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
