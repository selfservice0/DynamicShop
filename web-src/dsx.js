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
