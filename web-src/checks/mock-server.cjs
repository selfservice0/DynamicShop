// Local-only integration fixture; never included in plugin resources or the JAR.
const http=require('node:http'),fs=require('node:fs'),path=require('node:path'),vm=require('node:vm');
const root=path.resolve(process.argv[3]||path.join(__dirname,'../../target/classes/web'));
const schema=vm.runInNewContext(fs.readFileSync(path.join(__dirname,'../config-schema.js'),'utf8')+'\ncfgSchema');
const config={};for(const group of [schema,...Object.values(schema).filter(x=>x&&x.toggles)]){for(const f of group.toggles)config[f.key]=false;for(const f of group.numbers)config[f.key]=f.isText?'':1}
Object.assign(config,{sellTaxPercent:30,curveStrength:.9,maxStock:500,minPriceMultiplier:.01,maxPriceMultiplier:2,shopMenuSize:54,maxRecentTransactions:10000,webserverPort:7713,crossServerPort:7714,webserverSslEnabled:false,webserverSslKeystorePath:'keystore.jks',consoleTransactions:false,useDialogGui:false,inputMethod:'auto',websiteUsageMetrics:true});
let appearance={title:'DynamicShop',subtitle:'YOUR SERVER MARKETPLACE',design:'chest',accent:'#e3b21c',mode:'custom',background:'#1d1e22',panel:'#c6c6c6',sidebar:'#16171b',font:'legible',radius:0,density:'compact',motion:false,hero:false,homeEnabled:false,homeLabel:'Home',homeUrl:'',homeNewTab:false,layoutStyles:{}};
const materials=['DIAMOND','OAK_LOG','GOLDEN_HORSE_ARMOR'];let items=materials.map((id,i)=>({item:id,displayName:i===2?'Iridium Watering Can':id==='DIAMOND'?'Diamond':'Oak Log',customName:i===2?'Iridium Watering Can':'',kind:'material',category:i===1?'WOOD':'MISC',categoryDisplayName:i===1?'Wood':'Miscellaneous',basePrice:i===2?100000:100,buyPrice:i===2?100000:100,sellPrice:i===2?70000:70,stock:i===1?0:64,stockRate:5,maxStock:null,maxStockStorage:null,shortageHours:0,buyDisabled:false,sellDisabled:false,disabled:false,historyId:id,imageUrl:'https://mc.nerothe.com/img/1.21/minecraft_'+id.toLowerCase()+'.png'}));
const cats=[{id:'MISC',displayName:'All',defaultName:'All',icon:'CHEST',defaultIcon:'CHEST',slot:20,isCustom:false,restockTarget:null,restockInterval:null},{id:'WOOD',displayName:'Wood',defaultName:'Wood',icon:'OAK_LOG',defaultIcon:'OAK_LOG',slot:31,isCustom:false,restockTarget:null,restockInterval:null},{id:'CUSTOM_1',displayName:'Custom 1',defaultName:'Custom 1',icon:'CHEST',defaultIcon:'CHEST',slot:-1,isCustom:true,restockTarget:null,restockInterval:null}];
const extra={entries:[],listings:[]},audit=[];let loggedIn=false;
const transactions=[0,1,25,100,200].map((hours,i)=>({item:'DIAMOND',playerName:i===1?'Trader <test>':'Alex',type:i%2?'SELL':'BUY',amount:2,price:i%2?123:180,timestamp:Date.now()-hours*3600000}));
const journal=path.join(__dirname,'requests.jsonl');
http.createServer(async(req,res)=>{
 const url=new URL(req.url,'http://localhost'),route=url.pathname;res.setHeader('Cache-Control','no-store');
 if(route==='/api/website'||route.startsWith('/api/market/')){
  const delay=Number(process.env[route==='/api/website'?'WEBSITE_DELAY_MS':'MARKET_DELAY_MS'])||0;
  if(delay)await new Promise(resolve=>setTimeout(resolve,delay));
 }
 const send=(data,status=200)=>{res.writeHead(status,{'Content-Type':'application/json'});res.end(JSON.stringify(data))};
 let raw='';for await(const chunk of req)raw+=chunk;const body=raw?JSON.parse(raw):{};
 if(route.startsWith('/api/admin/')&&(!loggedIn||req.headers['x-session-token']!=='fixture-session'))return send({error:'Unauthorized'},401);
 if(req.method!=='GET')fs.appendFileSync(journal,JSON.stringify({route,method:req.method,body:route.startsWith('/api/auth')?'[auth omitted]':body})+'\n');
 if(route==='/api/website')return process.env.WEBSITE_FAIL==='1'?send({error:'Website settings unavailable'},503):send({appearance,version:'3.0.0',adminEnabled:true,usageMetricsEnabled:config.websiteUsageMetrics});
 if(route==='/api/usage/status')return send({enabled:config.websiteUsageMetrics});
 if(route==='/api/usage/event'){res.writeHead(204);return res.end()}
 if(route==='/api/market/catalog')return send(items.filter(i=>!i.disabled));
 if(route==='/api/market/activity')return send({transactions,serverTime:Date.now(),retainedOnly:true});
 if(route==='/api/auth/register'&&body.token==='fixture-token'){loggedIn=true;return send({session:'fixture-session',username:'Admin'})}
 if(route==='/api/auth/verify'&&req.headers['x-admin-token']==='fixture-token')return send({valid:true,type:'token',playerName:'Admin',alreadyRegistered:false});
 if(route==='/api/auth/login'){loggedIn=body.username==='Admin'&&body.password==='testing123';return loggedIn?send({session:'fixture-session'}):send({error:'Invalid credentials'},401)}
 if(route==='/api/auth/verify')return loggedIn&&req.headers['x-session-token']==='fixture-session'?send({valid:true,type:'session',username:'Admin'}):send({valid:false},401);
 if(route==='/api/auth/logout'){loggedIn=false;return send({success:true})}
 if(route==='/api/admin/items')return send(items);
 if(route==='/api/admin/categories')return send(cats);
 if(route==='/api/admin/config'){if(req.method==='POST')Object.assign(config,body);return send(config)}
 if(route==='/api/admin/entries')return send(extra);
 if(route==='/api/admin/audit')return send(audit);
 if(route==='/api/admin/appearance'){appearance=body;return send(appearance)}
 if(route==='/api/admin/website-files')return send({version:'3.0',needsUpdate:true,outdatedFiles:['index.html']});
 if(route==='/api/admin/website-files/update')return send({success:true,backup:'web-backups/fixture',version:'3.0'});
 if(route.startsWith('/api/admin/item/')){const item=items.find(i=>i.item===decodeURIComponent(route.split('/').at(-1)));if(!item)return send({error:'Not found'},404);if(req.method==='DELETE')item.disabled=true;else{Object.assign(item,body);item.customName=body.displayName;item.displayName=body.displayName||item.item;item.buyPrice=body.basePrice;item.sellPrice=body.basePrice*.7}return send({success:true})}
 if(route.startsWith('/api/admin/category/')){const cat=cats.find(c=>c.id===route.split('/').at(-1));if(req.method==='DELETE')cat.slot=-1;else Object.assign(cat,body);return send({success:true})}
 if(route.startsWith('/api/admin/entry/')){const id=route.split('/').at(-1);const old=extra.entries.find(i=>i.id===id);if(old)Object.assign(old,body);else extra.entries.push({...body,id});return send({success:true})}
 const files={'/':'index.html','/index.html':'index.html','/style.css':'style.css','/app.js':'app.js','/admin.html':'admin.html','/web-update.html':'web-update.html'};
 if(!files[route])return send({error:'Not found'},404);
 res.setHeader('Content-Type',route.endsWith('.js')?'text/javascript':route.endsWith('.css')?'text/css':'text/html');res.end(fs.readFileSync(path.join(root,files[route]),'utf8'));
}).listen(Number(process.argv[2])||8791,'127.0.0.1',()=>console.log('Production UI API fixture: http://127.0.0.1:8791/'));
