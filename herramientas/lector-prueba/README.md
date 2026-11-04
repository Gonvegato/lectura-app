# Pruebas del lector en un navegador (Chromium con pantalla táctil)

Abren el lector de `app/src/main/assets/lector` (con un `window.Atrio` de mentira) y un EPUB de tres capítulos, y simulan dedos de verdad:

- `botones.js`: en «Desplazamiento continuo», al final de cada capítulo hay un botón «Siguiente capítulo → <nombre>» (y al principio «← Capítulo anterior»); seguir deslizando NO pasa solo de capítulo; en «Páginas» los botones no se ven (`FLOW=paginated`).
- `toques.js`: deslizando, mantener pulsado NO selecciona palabra y dos toques seguidos SÍ; la línea de abajo (% y lo que queda) no se ve. En «Páginas» todo sigue como antes (`FLOW=paginated`).

Uso: `python3 crear_epub.py` genera `/tmp/lt/t.epub` (tres capítulos con índice), copia `t.html` a `/tmp/lt/` y `node botones.js` / `node toques.js` (hace falta `playwright`).
