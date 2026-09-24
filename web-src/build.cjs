const fs=require('node:fs'),path=require('node:path'),crypto=require('node:crypto');
const src=__dirname,out=path.join(src,'../src/main/resources/web');
const read=name=>fs.readFileSync(path.join(src,name),'utf8').replace(/^\uFEFF/,'');
let admin=read('admin.js').replace('/* APPEARANCE_UI */',()=>read('appearance.js')).replace('/* LIVE_ACTIONS */',()=>read('admin-actions.js')).replace('/* LIVE_AUTH */',()=>read('admin-auth.js'));
const script=['// DS-WEB-VERSION: @project.version@',read('catalog.js'),read('storefront.js'),read('activity.js'),read('nova.js'),read('usage.js'),read('dsx.js'),admin].join('\n');
const css=read('style.css')+'\n'+read('dsx.css'),digest=text=>crypto.createHash('sha256').update(text).digest('hex').slice(0,12);
const html=read('index.html').replace(/app\.js\?v=[^"']+/, 'app.js?v='+digest(script)).replace(/style\.css\?v=[^"']+/, 'style.css?v='+digest(css));
fs.mkdirSync(out,{recursive:true});fs.writeFileSync(path.join(out,'app.js'),script);fs.writeFileSync(path.join(out,'index.html'),html);fs.writeFileSync(path.join(out,'style.css'),css);
// Old bookmarks and operator registration links continue to reach the same application.
for(const [file,route] of [['admin.html','settings'],['items.html','catalog']])fs.writeFileSync(path.join(out,file),'<!-- DS-WEB-VERSION: @project.version@ -->\n<!doctype html><html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><meta name="referrer" content="no-referrer"><title>DynamicShop</title></head><body><p>Opening DynamicShop… <a href="index.html#'+route+'">Continue</a></p><script>location.replace("index.html"+location.search+"#'+route+'");</script></body></html>');
for(const file of ['dashboard.js','items.js'])fs.writeFileSync(path.join(out,file),'// DS-WEB-VERSION: @project.version@\n// Compatibility entry: the 3.0 website uses app.js.\n');
fs.writeFileSync(path.join(out,'web-update.html'),read('web-update.html'));
console.log('Built production website (six designs, real API data).');
