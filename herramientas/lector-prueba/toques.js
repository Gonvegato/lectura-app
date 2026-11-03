const { chromium } = require('playwright'); const http=require('http'), fs=require('fs'), path=require('path')
const root=process.env.ROOT||'/home/user/lectura-app/app/src/main/assets', port=process.env.PORT||8767
const srv=http.createServer((q,r)=>{ let p=decodeURIComponent(q.url.split('?')[0]); let f = p.startsWith('/libro/') ? '/tmp/lt/t.epub' : path.join(root,p);
  fs.readFile(f,(e,d)=>{ if(e){r.writeHead(404);r.end();return} const t=f.endsWith('.js')||f.endsWith('.mjs')?'text/javascript':f.endsWith('.html')||f.endsWith('.epub')&&0?'text/html':f.endsWith('.css')?'text/css':f.endsWith('.json')?'application/json':'application/octet-stream'; r.writeHead(200,{'Content-Type':t});r.end(d)})}).listen(port)
;(async()=>{
 const b=await chromium.launch(); const ctx=await b.newContext({viewport:{width:400,height:760},hasTouch:true,isMobile:true}); const pg=await ctx.newPage()
 const flow=process.env.FLOW||'scrolled'
 await pg.addInitScript((flow)=>{
   const st={title:'Libro de prueba',file:'t.epub',location:null,fraction:0,goto:null,settings:{flow, theme:'light'},notes:[],marks:[],stats:null,fondo:null,accent:null,name:'t.epub'}
   const base={state:()=>JSON.stringify(st),brilloActual:()=>0.5,notes:()=>'[]'}
   window.Atrio=new Proxy(base,{get:(t,k)=>k in t?t[k]:(()=>undefined)})
 },flow)
 const errs=[]; pg.on('pageerror',e=>errs.push(String(e).slice(0,200))); pg.on('console',m=>{ if(m.type()==='error') errs.push(m.text().slice(0,160)) })
 await pg.goto('http://localhost:'+port+'/lector/index.html'); await pg.waitForFunction(()=>window.__view&&window.__docs&&window.__docs.length,null,{timeout:25000}).catch(()=>{})
 await pg.waitForTimeout(1500)
 const cdp=await ctx.newCDPSession(pg)
 const T=(type,x,y)=>cdp.send('Input.dispatchTouchEvent',{type,touchPoints:type==='touchEnd'?[]:[{x,y}]})
 const tap=async(x,y)=>{ await T('touchStart',x,y); await pg.waitForTimeout(40); await T('touchEnd') }
 const estado=()=>pg.evaluate(()=>({desplaza:document.body.classList.contains('desplaza'),estadoDisplay:getComputedStyle(document.querySelector('#estado')).display,palabra:!document.querySelector('#palabra').classList.contains('oculta'),menu:document.body.classList.contains('menu')}))
 console.log('modo',flow,'errores de página:',JSON.stringify(errs.slice(0,3)))
 console.log('inicio',JSON.stringify(await estado()))
 // mantener pulsado 700 ms sin mover
 await T('touchStart',180,300); await pg.waitForTimeout(700); await T('touchEnd'); await pg.waitForTimeout(500)
 console.log('tras mantener pulsado',JSON.stringify(await estado()))
 // dos toques seguidos
 await tap(180,300); await pg.waitForTimeout(120); await tap(180,300); await pg.waitForTimeout(700)
 console.log('tras dos toques',JSON.stringify(await estado()))
 await pg.screenshot({path:'/tmp/lt/lector_'+flow+'.png'})
 await b.close(); srv.close()
})().catch(e=>{console.log('ERR',e.message); process.exit(1)})
