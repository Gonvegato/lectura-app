// Prueba: el EPUB que Marfil hace con los Escritos de Cobalto (EscritosTest lo deja en app/build/outputs/escritos/escrito.epub)
// se abre con el lector de verdad: índice con portada, partes y capítulos; el texto con sus cursivas y negritas; se pasa de capítulo.
// Uso: gradle :app:testDebugUnitTest --tests '*EscritosTest*' && node escrito.js
const { chromium } = require('playwright'); const http = require('http'), fs = require('fs'), path = require('path')
const root = process.env.ROOT || '/home/user/lectura-app/app/src/main/assets', port = process.env.PORT || 8772
const epub = process.env.EPUB || '/home/user/lectura-app/app/build/outputs/escritos/escrito.epub'
const srv = http.createServer((q, r) => { const p = decodeURIComponent(q.url.split('?')[0]); const f = p.startsWith('/libro/') ? epub : path.join(root, p)
  fs.readFile(f, (e, d) => { if (e) { r.writeHead(404); r.end(); return } const t = f.endsWith('.js') || f.endsWith('.mjs') ? 'text/javascript' : f.endsWith('.html') ? 'text/html' : f.endsWith('.css') ? 'text/css' : f.endsWith('.json') ? 'application/json' : 'application/octet-stream'; r.writeHead(200, { 'Content-Type': t }); r.end(d) }) }).listen(port)
;(async () => {
  const b = await chromium.launch(); const ctx = await b.newContext({ viewport: { width: 400, height: 760 }, hasTouch: true, isMobile: true }); const pg = await ctx.newPage()
  await pg.addInitScript(() => {
    const st = { title: 'La casa', file: 'escrito.epub', location: null, fraction: 0, goto: null, settings: { flow: 'scrolled', theme: 'light' }, notes: [], marks: [], stats: null, fondo: null, accent: null, name: 'escrito.epub' }
    window.Atrio = new Proxy({ state: () => JSON.stringify(st), brilloActual: () => 0.5 }, { get: (t, k) => k in t ? t[k] : (() => undefined) })
  })
  const errs = []; pg.on('pageerror', e => errs.push(String(e).slice(0, 200)))
  await pg.goto('http://localhost:' + port + '/lector/index.html'); await pg.waitForFunction(() => window.__view && window.__docs && window.__docs.length, null, { timeout: 25000 }).catch(() => {})
  await pg.waitForTimeout(1500)
  const toc = await pg.evaluate(() => (window.__view.book.toc || []).map(t => t.label))
  const secciones = await pg.evaluate(() => window.__view.book.sections.length)
  const meta = await pg.evaluate(() => ({ title: window.__view.book.metadata?.title, author: window.__view.book.metadata?.author }))
  console.log('errores de página:', JSON.stringify(errs.slice(0, 3)))
  console.log('metadatos:', JSON.stringify(meta))
  console.log('índice:', JSON.stringify(toc), '| secciones:', secciones)
  const texto = i => pg.evaluate(i => { const d = (window.__docs || []).find(x => x.index === i)?.doc; return d ? { h: [...d.querySelectorAll('h1,h2,h3')].map(e => e.textContent.trim()), strong: [...d.querySelectorAll('strong')].map(e => e.textContent), em: [...d.querySelectorAll('em')].map(e => e.textContent), p: [...d.querySelectorAll('p')].map(e => e.textContent.trim()).slice(0, 6) } : null }, i)
  console.log('portada:', JSON.stringify(await texto(0)))
  await pg.screenshot({ path: '/tmp/lt/escrito_portada.png' })
  await pg.evaluate(() => window.__view.goTo(4)); await pg.waitForTimeout(1500)
  const cap2 = await texto(4)
  console.log('capítulo «El regreso»:', JSON.stringify(cap2))
  await pg.screenshot({ path: '/tmp/lt/escrito_capitulo.png' })
  const ok = errs.length === 0 && toc.length === 5 && secciones === 5 && meta.title === 'La casa' && cap2 && cap2.strong.includes('tarde') && cap2.em.includes('nadie') && cap2.h.includes('El regreso')
  console.log(ok ? 'BIEN: el lector abre el escrito con su índice, sus partes y su texto.' : 'MAL: revisa lo de arriba.')
  await b.close(); srv.close(); process.exit(ok ? 0 : 1)
})().catch(e => { console.log('ERR', e.message); process.exit(1) })
