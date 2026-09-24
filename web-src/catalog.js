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
