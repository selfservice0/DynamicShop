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
