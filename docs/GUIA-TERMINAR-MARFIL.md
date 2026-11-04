# Guía: terminar Marfil (de la 0.8.1 a la 0.9.x)

Escrita para que la ejecute otro modelo, paso a paso. **Sigue el orden.** Cada tarea dice qué archivos tocar, qué hacer
exactamente, cómo probarlo y cuándo está terminada. Si algo de aquí no cuadra con el código que tienes delante, **para y
pregunta al usuario**: no improvises un diseño distinto.

---

## 0. Antes de empezar (obligatorio)

### 0.1 Dónde está todo
- Repositorio: `Gonvegato/lectura-app`, clonado en `/home/user/lectura-app`, rama **`desarrollo`** (nunca `main`).
- Código: `app/src/main/java/com/gonzalo/marfil/`. Tests: `app/src/test/java/com/gonzalo/marfil/`.
- Lector (web): `app/src/main/assets/lector/` (`lector.js`, `lector.css`, `foliate/…`).
- Contrato de datos con las otras apps: `docs/CONTRATO-DATOS.md` (**actualízalo** cada vez que añadas un nodo o un campo que viaje).
- Paquete `com.gonzalo.marfil`. Firma: `app/marfil.keystore` (ya configurada en `app/build.gradle.kts`).

### 0.2 Cómo se compila y se prueba
```bash
cd /home/user/lectura-app
gradle :app:compileDebugKotlin --offline                     # compilar
gradle testDebugUnitTest --offline                           # tests (deben pasar TODOS)
gradle testDebugUnitTest --tests '*CapturasTest*' --tests '*WidgetsTest*' -Proborazzi.test.record=true --offline   # capturas
gradle testDebugUnitTest assembleRelease --offline           # tests + APK firmado
```
- Si `--offline` falla por dependencias que faltan (contenedor nuevo), quítalo una vez.
- Si Gradle responde «429 Too Many Requests», espera 20–60 s y repite.
- Las capturas salen en `app/build/outputs/roborazzi/*.png`. **Míralas** (con la herramienta de leer imágenes) antes de dar
  nada por bueno. Las de `CapturasTest` solo se generan con `-Proborazzi.test.record=true`.
- El lector web se prueba con Chromium táctil: `herramientas/lector-prueba/` (ver su README). Úsalo si tocas `lector.js`
  o `foliate/`.

### 0.3 Reglas del usuario (no negociables)
- Responde en español, directo y honesto. Si algo no se ha podido probar, dilo.
- Comprueba en internet cualquier dato externo (APIs, formatos) antes de usarlo; no inventes.
- Cada versión: subir a la rama `desarrollo`, **mandar el APK** con `SendUserFile` y **subirlo a kDrive «versiones»**:
  `python3 herramientas/subir-version.py /ruta/Marfil-X.Y.Z.apk` (el nombre del archivo debe ser `Marfil-X.Y.Z.apk`;
  la autenticación la pone el entorno, no pidas tokens).
- Commits con las líneas de atribución que te indique el sistema. No crees PR si no lo pide.
- Nada de borrar datos del usuario.
- La estética es la de Atrio (revista): usa las piezas que ya hay (`Revista.kt`, `UiBase.kt`: `Antetitulo`, `EnlaceRevista`,
  `FiltrosRevista`, `MagLink`, `MagFact`, `FitTitle`, `Filete`, `AccionGrande`, `CampoRevista`, `Fila`, `Interruptor`,
  `MagChoice`). No metas componentes de Material con su aspecto por defecto.

### 0.4 Cómo subir la versión
En `app/build.gradle.kts` sube `versionCode` (+1) y `versionName`; en `PantEstilo.kt` la constante `VERSION` igual que
`versionName`. Añade una sección al final de `README.md` con lo nuevo.

### 0.5 Versiones previstas
- **0.8.2**: tareas 1–4 (orden, atajo, tamaño del APK, icono si hay PNG).
- **0.9.0**: tareas 5–9 (terminado y reto, ajustes del lector, subrayados ligeros, nota de Azabache, fotos de Atrio).
- Al final de cada una: tests, capturas revisadas, APK, kDrive, commit, push, mensaje al usuario.

---

## 1. Ordenar las secciones

**Qué quiere el usuario:** en Leyendo, Pendientes, Leídos y Abandonados poder ordenar.

**Archivo:** `Secciones.kt`.

1. Añade un enum:
   ```kotlin
   enum class OrdenLibros(val nombre: String) { RECIENTES("Recientes"), ANADIDOS("Añadidos"), TITULO("Título"), AUTOR("Autor"), PAGINAS("Páginas") }
   ```
2. Sustituye la función privada `ordenar(estado, l)` por `ordenar(o: OrdenLibros, l: List<Libro>)`:
   - `RECIENTES`: `ultima` desc, luego `anadido` desc.
   - `ANADIDOS`: `anadido` desc.
   - `TITULO`: `norm(titulo)` asc.
   - `AUTOR`: `norm(autor)` asc (los vacíos al final), luego `norm(titulo)`.
   - `PAGINAS`: `paginas` desc; los de `paginas == 0` al final.
3. Orden por defecto de cada estante: Leyendo → `RECIENTES`; Pendientes → `ANADIDOS`; Leídos → `RECIENTES` (en la tarea 5
   pasará a ser «Terminado»); Abandonados → `RECIENTES`.
4. Guarda la elección por estante en `SharedPreferences("marfil_orden")`, clave `estado.name`, valor `OrdenLibros.name`.
5. En `PaginaEstante`, justo debajo del `FitTitle` del título y antes del `Filete`, pon
   `FiltrosRevista(OrdenLibros.entries.map { it.nombre }, orden.ordinal) { … }` (guarda y recompone).
6. Las sagas siguen saliendo juntas: `Sagas.agrupar` ya respeta el orden de la lista que recibe; no lo cambies.

**Pruebas:** nuevo test unitario (archivo `SeccionesTest.kt` ya existe; añade ahí) que compruebe los cinco órdenes con
libros de ejemplo, incluido «páginas 0 al final» y «autor vacío al final». Captura: `seccionesYSagas` debe seguir verde y
el filtro debe verse debajo del título.

**Terminado cuando:** cada estante recuerda su orden al cerrar y abrir la app.

---

## 2. Atajo «Seguir leyendo» en el icono de la app

**Modelo:** es una copia de `AtajoLectura` de Atrio (`Atrio-Suite-2.0/movil/.../Lector.kt`, líneas ~997–1020).

1. Copia el icono `Atrio-Suite-2.0/movil/app/src/main/res/drawable/sc_book.xml` a `app/src/main/res/drawable/sc_book.xml`.
2. Nuevo archivo `Atajo.kt` con `object AtajoLectura { fun pon(ctx: Context) }`:
   - El libro: el de `Biblioteca.libros` con estado `LEYENDO` y `ultima` mayor, **que tenga archivo** (`!enOtroDispositivo`).
   - Si no hay: `sm.removeDynamicShortcuts(listOf("seguir_leyendo"))` y salir.
   - Intent: `Intent(ctx, MainActivity::class.java).setAction(Intent.ACTION_VIEW).putExtra("leer", libro.id).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)`.
   - `ShortcutInfo.Builder(ctx, "seguir_leyendo")`: etiqueta corta (título recortado a 22 letras con «…»), larga
     `Seguir leyendo «título» · 37 %`, icono `R.drawable.sc_book`, rango 0. `sm.dynamicShortcuts = listOf(sc)`.
   - Todo dentro de `runCatching` (algunos launchers no tienen atajos).
3. Llamadas a `AtajoLectura.pon(applicationContext)`: en `MainActivity.onStart()` y en `LectorActivity.onStop()`.
4. En `MainActivity`, en `onCreate` y `onNewIntent`: si el intent trae `"leer"` (String), quítalo del intent
   (`removeExtra`) y abre `Lector.abrir(this, libro)` con `Biblioteca.por(id)` (si existe).

**Pruebas:** test Robolectric: con un libro leyendo, `AtajoLectura.pon` deja un atajo con ese id en
`ShortcutManager.dynamicShortcuts`; sin libros, ninguno.

---

## 3. Tamaño del APK (78 MB)

**Dato medido:** casi todo es `libtranslate_jni.so` de ML Kit (el traductor del diccionario), repetido para cuatro
arquitecturas: x86_64 17,4 MB, x86 17,3 MB, arm64-v8a 16,4 MB, armeabi-v7a 11,6 MB.

1. **Pregunta antes al usuario** (con `AskUserQuestion`): «¿Usas Marfil en algún dispositivo de 32 bits o en un
   ordenador con Waydroid? (Atrio se usaba en Waydroid.)». Opciones: «Solo móvil y tablet modernos» /
   «También Waydroid en el PC» / «No lo sé».
2. Según la respuesta, en `app/build.gradle.kts`, dentro de `defaultConfig`:
   - «Solo móvil y tablet»: `ndk { abiFilters += listOf("arm64-v8a") }`.
   - «También Waydroid» o «No lo sé»: `ndk { abiFilters += listOf("arm64-v8a", "x86_64") }`.
3. Compila el release y apunta el tamaño antes y después en el mensaje al usuario.

**No hagas** nada más para adelgazar (ni quitar letras ni pdf.js): todo eso son funciones.

---

## 4. Icono de Marfil (solo si ya está el PNG)

El usuario mandó los iconos en el chat, pero **no como archivo**. Si en el repo existe `docs/iconos/marfil.png`, hazlo;
si no, sáltatelo y recuérdaselo al final.

La imagen es una tarjeta crema con esquinas redondas sobre un fondo oscuro. Android pone sus propias esquinas, así que hay
que quedarse con el interior de la tarjeta:

1. Con Python (PIL), detecta el rectángulo de la tarjeta (los píxeles claros: luminancia > 180) y recórtalo metiéndolo
   un 9 % por cada lado (para que no se vean sus esquinas redondas). Escala a 432×432.
2. Guárdalo como `app/src/main/res/drawable-nodpi/ic_launcher_marfil.png`.
3. `res/mipmap-anydpi-v26/ic_launcher.xml`: `<background android:drawable="@drawable/ic_launcher_marfil"/>` y
   `<foreground android:drawable="@android:color/transparent"/>`; deja el `<monochrome>` que hay.
4. Comprueba que el dibujo central queda dentro del 66 % del centro (zona segura). Si no, reduce el recorte al 5 %.
5. Captura de control: dibuja el PNG con una máscara circular y otra cuadrada redondeada y míralas.

---

## 5. Fecha de terminado y reto anual

### 5.1 Modelo
- En `Libro` (`Modelo.kt`) añade `val terminados: List<Long> = emptyList()`: las fechas (ms) en que lo terminaste,
  ordenadas. Propiedad derivada: `val terminado: Long get() = terminados.lastOrNull() ?: 0L`.
- JSON (`Biblioteca.aJson/aLibro`): clave `"tm"` como array de números.
- **Regla única, en `Biblioteca.poner(l)`:** si el libro ya existía (`libros[i]`), su estado anterior **no** era `LEIDO`,
  el nuevo **sí**, y `l.terminado < ahora - 12 h` → añade `ahora` a `terminados`. No pongas esta regla en ningún otro
  sitio: así vale para la ficha, para el lector (que lo marca leído al 98,5 %) y para todo lo demás. `aplicarMezcla` no
  pasa por `poner`, y así debe seguir.
- Importar de Atrio (`ImportarAtrio.candidatos`): si el libro sale de `books` y tiene `end > 0`, `terminados = listOf(end)`.
  En `importar`, para libros que ya existían sin `terminados`, rellénalos con ese `end`.

### 5.2 Sincronización
- `LibroSync` (`Mezcla.kt`): campo `val terminados: List<Long> = emptyList()`, JSON `"tm"`.
- En `unir` y `crear`: la unión de todas las fechas, ordenada, y quitando las que estén a menos de 12 h de otra (se queda
  la primera).
- Test en `MezclaTest`: dos dispositivos con fechas distintas → se juntan; fechas casi iguales → una sola.

### 5.3 Dónde se ve
- Ficha (`PantFicha.kt`): en la línea en cursiva de debajo del título, si `terminado > 0`:
  `leído el 3 nov 2026` (con `fecha()`), y si hay más de una, `· 2.ª vez`.
  En «Datos», `MagFact("Terminado", fecha(...))` y, si hay varias, `MagFact("Lecturas", "${n}")`.
- Leídos: añade a `OrdenLibros` (tarea 1) el valor `TERMINADO("Terminado")` (por `terminado` desc) y hazlo el orden por
  defecto de Leídos. En las otras secciones no lo enseñes.

### 5.4 Reto anual
- `object Ajustes` (`EstadoLectura.kt`): `fun metaAnual(c): Int` y `fun ponMetaAnual(c, n)` en las mismas prefs
  `marfil_ajustes`, clave `meta_anual`.
- Ajustes → sección «Lectura»: debajo del objetivo diario, `MagChoice` «Libros al año» con
  `[0, 6, 12, 20, 24, 30, 40, 52]` (0 = «Sin reto»).
- Cuenta: libros con alguna fecha de `terminados` en el año actual (una por libro y año).
- Portada (`PantInicio.kt`): si `metaAnual > 0`, justo después de `BarrasLectura(libros)` y antes del menú, una línea con
  el mismo estilo que la cabecera de las barras: cuadradito de acento + `RETO 2026 · 7 DE 12 LIBROS` y debajo una barra
  fina (`Barra(f)`). Si ya está cumplido: `RETO 2026 CUMPLIDO · 13 LIBROS` en acento.
- Widget «Lectura de la semana» (`Widgets.kt`, función `semana`): en el bloque izquierdo, tras las líneas que ya hay, una
  más `Reto: 7/12` si `metaAnual > 0` y cabe.

### 5.5 Contrato
En `docs/CONTRATO-DATOS.md`, en la fila de `dev/<dispositivo>/data`, añade el campo `tm` (fechas de terminado).

---

## 6. Los ajustes del lector, iguales en todos tus dispositivos

Hoy los ajustes por defecto del lector (letra, tamaño, tema, márgenes…) se guardan solo en el dispositivo:
`SharedPreferences("lector")`, clave `"ajustes"` (ver `LectorActivity.kt`, `saveSettings`, línea ~440).

1. En `saveSettings`, guarda también `"ajustesAt"` = `System.currentTimeMillis()` en esas mismas prefs, y avisa a la
   sincronización: `SincroP.cambio(applicationContext)`.
2. `Blob` (`Mezcla.kt`): campo `val lector: String = ""` (texto JSON `{"at": ms, "s": {…}}`), JSON clave `"la"`.
3. `SincroP.subir`: pon en el blob los ajustes del lector **sin** `brightness` ni `pdfTexto` ni `pdfForzar`
   (son de cada dispositivo).
4. `SincroP.bajar`: de los blobs nuevos, el `la` con `at` mayor; si es mayor que el `ajustesAt` local, escríbelo en
   `prefs("lector")["ajustes"]` (conservando el `brightness` local) y su `ajustesAt`.
5. Los ajustes de cada libro ya viajan (estado de lectura, `settingsAt`); no los toques.

**Prueba:** test de ida y vuelta del blob con `la`; test de «gana el más reciente».

---

## 7. Subrayados en un nodo ligero (para la cita del Diario de Azabache)

1. En `SincroP.subirLectura`, junto al `PUT` de `tiempo`, escribe
   `${Cuenta.raiz}/subrayados/<kid>/<dispositivo>` con
   `{"t": título, "a": autor, "n": [{"x": texto, "o": nota, "c": creado, "l": etiqueta}]}`.
   Sácalos con `notasDe(st)` (`Notas.kt`). Si no hay ninguno, escribe `null` (borra el nodo).
2. Documenta el nodo en `docs/CONTRATO-DATOS.md` (ligero; los borrados ya no salen).

---

## 8. La nota de Azabache en la ficha (solo lectura)

Mientras Azabache no exista, lee la de Atrio (mismo formato que usa `ImportarAtrio`).

1. Nuevo archivo `NotaAzabache.kt`:
   - `data class Nota(val atrioId: Long, val general: Int, val resena: String)`.
   - `fun leer(): Map<String, Nota>`: reutiliza la lectura de blobs de `ImportarAtrio.leer()` (sácala a una función
     `ImportarAtrio.blobs(): List<JSONObject>` y úsala en los dos sitios). De la lista `books`, por cada uno:
     clave `claveDe(name, creator)` → `Nota(id, general, review)`. `general` va de 0 a 100.
   - Caché en `SharedPreferences("marfil_azabache")`: el mapa en JSON y la hora. Se refresca en segundo plano al abrir
     una ficha si la caché tiene más de 15 minutos, y a mano.
2. Ficha: entre la pareja de casillas Tiempo/Páginas y «Subrayados y notas», una casilla a todo lo ancho
   `CabezaCasilla("En Azabache")`:
   - Con nota: el número grande (`FitTitle`, 48 sp) + `/100` pequeño, y la reseña en cursiva (3 líneas como mucho).
   - Sin nota: «Sin valorar todavía» en cursiva.
   - Enlace «Abrir en Azabache →» (o «Valorar en Azabache →»): solo si Atrio está instalado
     (`packageManager.getLaunchIntentForPackage("com.gonzalo.gamerate") != null`). El intent: ese, con
     `putExtra("go", "item:1:<atrioId>")` y `FLAG_ACTIVITY_NEW_TASK` (Atrio ya entiende `go=item:<categoría>:<id>`;
     los libros son la categoría 1). Sin `atrioId`, abre Atrio sin más.
   - Sin cuenta vinculada: no enseñes la casilla.
3. **No escribas nada** en Atrio. Solo se lee.

**Prueba:** test de `leer` con un blob de ejemplo (como en `ImportarAtrioTest`).

---

## 9. Las portadas que en Atrio eran fotos tuyas

En Atrio, una portada que es foto tuya se guarda como `cover = "file:///…/foto_1700000000000.jpg"` y, si está en kDrive,
en la lista `nube` hay una entrada `{"k": "foto:foto_1700000000000.jpg", "f": <id de kDrive>, …}`.

1. `ImportarAtrio.candidatos`: además de las entradas `libro:` de `nube`, junta las `foto:`. Si el `cover` empieza por
   `file:`, su nombre es `Uri.parse(cover).lastPathSegment`; busca `foto:<nombre>` y guárdalo en el `Candidato` como
   `val foto: NubeLibro? = null` (`f`, `n`, `s`).
2. `ImportarAtrio.importar`: si el libro no tiene portada y el candidato trae `foto`, **no la bajes ahí**: regístrala en el
   índice de portadas de Marfil con el id de kDrive de Atrio:
   `Cuenta.db("PUT", "${Cuenta.raiz}/portadas/<kid>", {"f": foto.f, "u": ahora})`. La sincronización de portadas
   (`NubeLibros.sincronizarPortadas`) ya la baja en todos tus dispositivos cuando hay kDrive.
3. Después de importar, llama a `NubeLibros.programar(ctx)`.

**Prueba:** test de `candidatos` con un `cover` `file:` y su entrada `foto:`.

---

## 10. Cierre de cada versión

1. `gradle testDebugUnitTest --offline`: **0 fallos**.
2. Capturas con `-Proborazzi.test.record=true` y **revísalas una a una** (portada, secciones, ficha arriba y abajo, ajustes,
   widgets). Si tocaste la ficha o la portada, añade la captura que lo enseñe.
3. Versión (0.4), `README.md`, `docs/CONTRATO-DATOS.md`.
4. `gradle testDebugUnitTest assembleRelease --offline`; copia el APK a `Marfil-X.Y.Z.apk`.
5. `python3 herramientas/subir-version.py …/Marfil-X.Y.Z.apk`.
6. Commit y `git push -u origin desarrollo`.
7. `SendUserFile` con el APK y un mensaje corto: qué hay nuevo, qué está probado y cómo, qué **no** se ha podido probar
   (todo lo que dependa de su cuenta, su kDrive o su móvil).

## 11. Lo que el usuario tiene que probar (pídeselo al final)

- Dos toques para subrayar leyendo deslizando; pasar de capítulo deslizando.
- Atajo «Seguir leyendo» manteniendo pulsado el icono.
- Reto anual y fecha de terminado al acabar un libro.
- Que los ajustes del lector cambiados en un dispositivo lleguen al otro.
- La nota de Azabache en la ficha y «Abrir en Azabache».
- Que las portadas que eran fotos suyas en Atrio aparezcan tras importar (con kDrive conectado).
