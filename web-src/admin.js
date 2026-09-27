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
    applyHeroCopy(value.design);
    document.documentElement.dataset.themeMotion=String(value.motion);document.documentElement.dataset.themeHero=String(value.hero);
    applyBranding(value);
    applyHomeLink(value);
    window.dispatchEvent(new Event('appearance-updated'));
  }
  function applyHeroCopy(design){
    const value={design},heading=document.querySelector('.hero h1');
    document.querySelector('.hero-copy>p').innerHTML=value.design==='nova'?'From your first diamond to your next upgrade. Find what you need. Make your inventory count.':'From your first diamond to your next upgrade.<br>Find what you need. Make your inventory count.';
    heading.innerHTML=value.design==='nova'?'Find your next<br><span>main character item.</span>':value.design==='market'?'A little trading.<br><span>A world of possibilities.</span>':value.design==='inventory'?'Your next upgrade.<br><span>One slot away.</span>':'Good finds.<br><span>Better trades.</span>';
    document.querySelector('.hero .eyebrow').innerHTML='<span></span> '+(value.design==='nova'?'YOUR WORLD. YOUR NEXT DISCOVERY.':value.design==='market'?'THE MARKET HALL · OPEN TO EVERY ADVENTURER':value.design==='inventory'?'SERVER INVENTORY / READY TO BROWSE':'THE SERVER MARKETPLACE');
  }
  function applyBranding(value){
    const brand=document.querySelector('.brand');brand.replaceChildren();
    const wrap=document.createElement('span'),title=document.createElement('span'),subtitle=document.createElement('small');
    title.textContent=value.title;subtitle.textContent=value.subtitle;subtitle.hidden=!value.subtitle;wrap.append(title,subtitle);brand.append(wrap);
    document.querySelector('.footer-brand').textContent=value.title;
    document.title=value.title+' · Marketplace';
  }
  function applyHomeLink(value){
    let home=$('marketHomeLink');
    if(!home){home=document.createElement('a');home.id='marketHomeLink';home.className='market-home-link';home.innerHTML=icon('home')+'<span></span>';document.querySelector('.topbar-right').prepend(home)}
    const homeReady=value.homeEnabled&&validHomeDestination(value.homeUrl);
    home.hidden=!homeReady;if(homeReady)home.href=value.homeUrl;else home.removeAttribute('href');
    home.querySelector('span').textContent=value.homeLabel||'Home';
    home.target=value.homeNewTab?'_blank':'_self';home.rel='noopener noreferrer';
    home.title=(value.homeLabel||'Home')+(value.homeNewTab?' (opens in a new tab)':'');
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
  /* APPEARANCE_UI */
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
  /* LIVE_ACTIONS */
  const materialList=document.createElement('datalist');materialList.id='adminMaterials';materialList.innerHTML=items.map(i=>`<option value="${i.material.toUpperCase()}"></option>`).join('');document.body.append(materialList);
  function fixBreadcrumb(){if(location.hash==='#settings')$('pageCrumb').textContent='Administration'}window.addEventListener('hashchange',fixBreadcrumb);
  /* LIVE_AUTH */
  fixBreadcrumb();
})();
