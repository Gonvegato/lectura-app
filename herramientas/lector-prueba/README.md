# Pruebas del lector en un navegador (Chromium con pantalla táctil)

Abren el lector de `app/src/main/assets/lector` (con un `window.Atrio` de mentira) y un EPUB de tres capítulos, y simulan dedos de verdad:

- `capitulos.js`: en «Desplazamiento continuo», seguir deslizando al final de un capítulo pasa al siguiente (y al principio, al anterior).
- `toques.js`: deslizando, mantener pulsado NO selecciona palabra y dos toques seguidos SÍ; la línea de abajo (% y lo que queda) no se ve. En «Páginas» todo sigue como antes (`FLOW=paginated`).

Uso: `python3 -c "…"` genera `/tmp/lt/t.epub` (ver el script de abajo), copia `t.html` a `/tmp/lt/` y `node capitulos.js` / `node toques.js` (hace falta `playwright`).
