// Prueba: leyendo deslizando, al final de cada capítulo hay un botón al siguiente (y al principio, al anterior); nada pasa solo.
// Uso: python3 crear_epub.py && node botones.js   (FLOW=paginated para ver que en páginas no hay botones)
const { chromium } = require('playwright'); const http = require('http'), fs = require('fs'), path = require('path')
const root = process.env.ROOT || '/home/user/lectura-app/app/src/main/assets', port = process.env.PORT || 8771
const srv = http.createServer((q, r) => { const p = decodeURIComponent(q.url.split('?')[0]); const f = p.startsWith('/libro/') ? '/tmp/lt/t.epub' : path.join(root, p)
  fs.readFile(f, (e, d) => { if (e) { r.writeHead(404); r.end(); return } const t = f.endsWith('.js') || f.endsWith('.mjs') ? 'text/javascript' : f.endsWith('.html') ? 'text/html' : f.endsWith('.css') ? 'text/css' : f.endsWith('.json') ? 'application/json' : 'application/octet-stream'; r.writeHead(200, { 'Content-Type': t }); r.end(d) }) }).listen(port)
;(async () => {
  const flow = process.env.FLOW || 'scrolled'
  const b = await chromium.launch(); const ctx = await b.newContext({ viewport: { width: 400, height: 760 }, hasTouch: true, isMobile: true }); const pg = await ctx.newPage()
  await pg.addInitScript(flow => {
    const st = { title: 'Libro de prueba', file: 't.epub', location: null, fraction: 0, goto: null, settings: { flow, theme: 'light' }, notes: [], marks: [], stats: null, fondo: null, accent: null, name: 't.epub' }
    window.Atrio = new Proxy({ state: () => JSON.stringify(st), brilloActual: () => 0.5 }, { get: (t, k) => k in t ? t[k] : (() => undefined) })
  }, flow)
  const errs = []; pg.on('pageerror', e => errs.push(String(e).slice(0, 200)))
  await pg.goto('http://localhost:' + port + '/lector/index.html'); await pg.waitForFunction(() => window.__view && window.__docs && window.__docs.length, null, { timeout: 25000 }).catch(() => {})
  await pg.waitForTimeout(1500)
  const cdp = await ctx.newCDPSession(pg)
  const T = (type, x, y) => cdp.send('Input.dispatchTouchEvent', { type, touchPoints: type === 'touchEnd' ? [] : [{ x, y }] })
  const swipe = async (y0, y1) => { await T('touchStart', 200, y0); for (let k = 1; k <= 12; k++) { await T('touchMove', 200, y0 + (y1 - y0) * k / 12); await pg.waitForTimeout(16) } await T('touchEnd') }
  const sec = () => pg.evaluate(() => { const l = window.__view.lastLocation; return l ? (l.section?.current ?? l.index) : null })
  const botones = () => pg.evaluate(() => (window.__docs || []).map(({ doc, index }) => {
    const f = n => { const e = doc.defaultView && doc.querySelector('.atrio-cap.' + n + ' button'); if (!e) return null; const r = e.getBoundingClientRect(), fr = doc.defaultView?.frameElement?.getBoundingClientRect() || { left: 0, top: 0 }
      return { visible: getComputedStyle(e.parentElement).display !== 'none', texto: e.innerText.replace(/\s+/g, ' ').trim(), x: Math.round(r.left + fr.left + r.width / 2), y: Math.round(r.top + fr.top + r.height / 2) } }
    return { index, sig: f('sig'), ant: f('ant') } }))
  console.log('modo', flow, '| errores de página:', JSON.stringify(errs.slice(0, 3)))
  console.log('inicio: capítulo', await sec())
  // Hasta el final del capítulo 1 y 12 deslizamientos más: NO debe pasar solo
  for (let n = 0; n < 12; n++) { await swipe(600, 100); await pg.waitForTimeout(250) }
  for (let n = 0; n < 8; n++) { await swipe(600, 100); await pg.waitForTimeout(250) }
  console.log('tras seguir deslizando al final: capítulo', await sec(), '(0 = se queda donde está)')
  const bs = (await botones()).find(x => x.index === 0)
  console.log('botón del capítulo 1:', JSON.stringify(bs?.sig))
  await pg.screenshot({ path: '/tmp/lt/boton_' + flow + '.png' })
  if (bs?.sig?.visible) {
    await T('touchStart', bs.sig.x, bs.sig.y); await pg.waitForTimeout(40); await T('touchEnd'); await pg.waitForTimeout(1500)
    console.log('tras tocar el botón: capítulo', await sec())
    const b2 = (await botones()).find(x => x.index === 1)
    console.log('capítulo 2: anterior =', JSON.stringify(b2?.ant), '| siguiente =', JSON.stringify(b2?.sig))
    await pg.screenshot({ path: '/tmp/lt/capitulo2_' + flow + '.png' })
    if (b2?.ant?.visible) { await T('touchStart', b2.ant.x, b2.ant.y); await pg.waitForTimeout(40); await T('touchEnd'); await pg.waitForTimeout(1500); console.log('tras tocar «anterior»: capítulo', await sec()) }
  }
  await b.close(); srv.close()
})().catch(e => { console.log('ERR', e.message); process.exit(1) })
