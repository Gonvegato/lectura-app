const { chromium } = require('playwright'); const http=require('http'), fs=require('fs'), path=require('path')
const root='/home/user/lectura-app/app/src/main/assets'
const srv=http.createServer((q,r)=>{ let p=decodeURIComponent(q.url.split('?')[0]); let f = p==='/t.epub'||p==='/t.html' ? '/tmp/lt'+p : path.join(root,p);
  fs.readFile(f,(e,d)=>{ if(e){r.writeHead(404);r.end();return} const t=f.endsWith('.js')||f.endsWith('.mjs')?'text/javascript':f.endsWith('.html')?'text/html':f.endsWith('.css')?'text/css':'application/octet-stream'; r.writeHead(200,{'Content-Type':t});r.end(d)})}).listen(8765)
;(async()=>{
 const b=await chromium.launch(); const ctx=await b.newContext({viewport:{width:400,height:700},hasTouch:true,isMobile:true}); const pg=await ctx.newPage()
 pg.on('console',m=>{ if(m.type()==='error') console.log('console.error',m.text().slice(0,200)) }); pg.on('pageerror',e=>console.log('pageerror',String(e).slice(0,300)))
 await pg.goto('http://localhost:8765/t.html'); await pg.waitForFunction(()=>window.ready,null,{timeout:20000})
 const cdp=await ctx.newCDPSession(pg)
 const T=(type,y)=>cdp.send('Input.dispatchTouchEvent',{type,touchPoints:type==='touchEnd'?[]:[{x:200,y}]})
 const pos=()=>pg.evaluate(()=>{const r=window.view.renderer;const l=window.log[window.log.length-1]||{};return {sec:l.i,start:Math.round(r.start),end:Math.round(r.end),vs:Math.round(r.viewSize)}})
 const swipe=async(y0,y1,steps=12,pausa=16)=>{ await T('touchStart',y0); for(let k=1;k<=steps;k++){ await T('touchMove',y0+(y1-y0)*k/steps); await pg.waitForTimeout(pausa)} await T('touchEnd') }
 console.log('inicio',JSON.stringify(await pos()))
 // avanzar con deslizamientos hasta el final del capítulo 1 (sección 0)
 let n=0
 for(;n<40;n++){ const p0=await pos(); await swipe(600,100); await pg.waitForTimeout(500); const p=await pos(); if(n%5==0||p.sec!==0) console.log(n,JSON.stringify(p)); if(p.sec!==0) break }
 console.log('sección tras seguir deslizando al final:',JSON.stringify(await pos()))
 // y volver atrás deslizando hacia abajo hasta el principio del capítulo 2 y un poco más
 for(let k=0;k<30;k++){ await swipe(100,600); await pg.waitForTimeout(500); const p=await pos(); if(p.sec===0) { console.log('vuelve al capítulo 1 en',k,JSON.stringify(p)); break } }
 console.log('log', JSON.stringify(await pg.evaluate(()=>window.log.filter(x=>x.r!=='scroll').slice(-6))))
 await b.close(); srv.close()
})().catch(e=>{console.log('ERR',e.message); process.exit(1)})
