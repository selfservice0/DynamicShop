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
