# Marfil

El launcher de juegos del móvil. Hermano de [Atrio](https://github.com/gvegagato-prog/Atrio-Suite-2.0): mismo sistema de temas
(las mismas paletas, letras y «estilos»), otro paquete (`com.gonzalo.marfil`), así que **conviven en el mismo móvil** y se ven
como dos programas de la misma suite. Atrio es la puerta de la casa; Marfil es la galería de arcos.

Marfil **no es un emulador**: abre los que ya tienes instalados, los juegos de Android y los juegos de navegador, todo desde
una sola biblioteca.

## Capturas

La interfaz es la «Revista» de Atrio. Generadas en el ordenador con datos de ejemplo (`CapturasTest`): [portada](docs/capturas/2_portada.png) ·
[portada en oscuro](docs/capturas/7_portada_oscuro.png) · [juegos](docs/capturas/3_juegos.png) · [ficha](docs/capturas/12_ficha_casillas.png) ·
[ficha editando casillas](docs/capturas/13_ficha_editando.png) · [sistemas](docs/capturas/4_sistemas.png) · [ajustes](docs/capturas/5_ajustes.png) ·
[añadir](docs/capturas/9_anadir.png)

## La pantalla de inicio es tu lienzo

Como la portada de Atrio, con **las mismas medidas** (márgenes de 28 dp, cabecera de 92 sp o 132 sp en tableta, filetes, línea del número y la fecha en 12 sp, y el menú de secciones en versalitas de 12 sp entre dos filetes con 18 dp de aire arriba): cabecera de revista, el **menú de secciones** (Recientes, Favoritos, ROMs, Android, Web, Todo, Sistemas) y, debajo, **tu lienzo**: una cuadrícula de 4 columnas de casillas invisibles donde colocas lo que quieras con el tamaño que quieras.
- **Editar**: arrastra un elemento para moverlo (se encaja en las casillas; si pisa a otros, los empuja hacia abajo, como en la pantalla de inicio del móvil), arrastra el **círculo** de su esquina para hacerlo grande o pequeño, toca para elegir su **color de fondo** (solo el de ese elemento): cualquier color (matiz, intensidad y brillo, o el código #RRGGBB); los que uses quedan guardados y a mano para otras fichas («T» = el del tema), o **encuadrar** una imagen (arrastrar y zoom, con la forma exacta de su hueco; la foto se guarda y se dibuja ENTERA, y el hueco solo enseña la parte que queda dentro, así que se puede llevar al hueco cualquier parte de la foto) o quitarlo. «+ Añadir»: un juego, una imagen de la galería o una **imagen de internet** (la misma búsqueda de las portadas: Google Imágenes, Pinterest, ArtStation, Newgrounds).
- **Los juegos y los widgets de dentro son los widgets de fuera**: se dibujan con el mismo código (papel, tinta, letras del tema, portadas, reborde fino y esquinas). Un juego es el widget «Sigue jugando» fijo en él (según su tamaño: portada a sangre, ancho o alto). «+ Añadir → Widgets» pone también Sigue jugando, Recientes, En portada, ¿Y ahora qué?, LECTURA y LECTURA en papel; tocarlos hace lo mismo que fuera. El contador «1/4 ›» de los widgets se quitó (tocar la esquina de arriba sigue pasando al siguiente).
- Dentro de la app los widgets llevan **más margen** (el reborde fino más metido), la categoría arriba, un **botón «Jugar →»** y algo más de información (veces jugadas, terminado…) para que no queden vacíos; los de fuera no cambian.
- **Acento**: el del tema por defecto; Marfil no inventa ninguno por juego. Si quieres, eliges uno por elemento («Color de acento»: cuadradito, botón y horas, con un lavado suave de ese color).
- Cada juego, widget e imagen lleva por fuera una **línea de contorno del color del texto** (sin sombra), siguiendo su silueta.
- **Color**: cada juego o widget puede tener su propio color de fondo (cualquiera, con los usados a mano): ese pasa a ser su papel, y la tinta, el gris y las líneas se sacan de él para que se lea. Sin color propio usan los del tema.
- **Sincronizado entre tus móviles** (por elemento, gana el cambio más reciente; lo quitado se quita en todos). Los juegos se enlazan por su clave, las imágenes viajan por kDrive. La cuadrícula tiene 4 columnas en todos los móviles para que la disposición sea la misma.
- La primera vez (sin cuenta vinculada) se rellena con tus últimos juegos. Capturas: [lienzo](docs/capturas/20_lienzo.png) · [editando](docs/capturas/21_lienzo_editando.png).

## (Antes) La pantalla de inicio era un launcher de baldosas

Cabecera de revista compacta y, debajo, **baldosas** con la misma disposición que los widgets (portada a la izquierda, nombre a la derecha, el papel de la revista y
su reborde fino de tinta). **Un toque juega; mantener pulsado abre la ficha.** Pestañas: Recientes, Favoritos, ROMs (con filtro por sistema), Android, Web y Todo;
«Buscar» filtra en el sitio entre todos tus juegos, y la última baldosa («Añadir») es el atajo para meter más. «Lista» abre la lista de siempre, con orden y filtros;
«Sistemas» y el engranaje, sus pantallas.

## 0.10.0: sección Sagas

- Nueva pestaña **Sagas** al final de las secciones (Leyendo · Pendientes · Leídos · Abandonados · Autores · Sagas), también en el
  menú de la portada. Lista todas tus sagas por nombre, con su abanico de portadas y su resumen; cada una se despliega para ver sus
  libros en orden y abrir su página.
- Desde ahí se crea una saga nueva («+ Nueva saga»): se abre su página para ir metiendo libros con «Editar».

## 0.9.2: portadas solo las tuyas

- Marfil ya no saca portadas de los archivos (ni de los EPUB ni la primera página de un PDF). Si no has puesto tú una portada,
  el libro se queda con la «tela» generada (color según el título, con la letra o el título en crema).
- Solo se sincronizan por kDrive las portadas que pones tú; «Quitar portada» también se propaga a tus otros dispositivos.
- Al actualizar, las portadas automáticas que había se limpian una sola vez; las que pusiste tú se conservan.

## Qué hace

- **ROMs con emuladores externos** (GBA, DS, SNES, N64, PSP, PS1/PS2, Dreamcast, 3DS… hasta 170 sistemas). Eliges la ROM o una
  carpeta entera; Marfil reconoce el sistema por la extensión o por el nombre de la carpeta, y la abre con el emulador que
  elijas por sistema o por juego (melonDS, DraStic, mGBA/RetroArch, Pizza Boy, My Boy!, SkyEmu…). Sin elegir nada, usa uno
  propio instalado y, si no, RetroArch.
- **Juegos de Android**: elige de las apps instaladas (marca primero las que Android declara como juegos).
- **Juegos de navegador** (PokéRogue y similares): pantalla completa, sin barra de direcciones, con cookies y datos guardados
  entre sesiones (ahí viven las partidas), pantalla siempre encendida, orientación fija si quieres, y confirmación antes de
  salir. Deslizar hacia abajo no recarga ni rebota (overscroll desactivado). Funciona con mando Bluetooth (la API Gamepad de Chromium).
- **Accesos directos** en el escritorio del móvil para cualquier juego.
- **Pantalla de inicio**: opcionalmente puedes elegir Marfil como launcher del móvil (Estilo → Pantalla de inicio).
- **Portadas desde internet** (como en Atrio): en la ficha, Portada → «Buscar en internet» abre Google Imágenes, Pinterest, ArtStation o Newgrounds dentro de Marfil, con su casilla de búsqueda; tocas una imagen (o la mantienes pulsada) y se usa de portada. También puedes elegir una de la galería.
- **Ficha con casillas editables** (el sistema de maquetas de Atrio, con menos casillas): filas de una o dos casillas separadas por una línea
  que arrastras (⇔) y con alto que arrastras (⇕); subir, bajar, dividir, unir, quitar y añadir filas. Casillas: horas jugadas (editables),
  veces y última vez, estado (terminado), valoración (solo cuando está terminado; 0–10 en pasos de 0,5), apuntes, cómo se abre, texto libre e
  imagen tuya (galería o búsqueda en internet). Cada juego guarda su diseño y puedes usar el de uno para todos los de su tipo.
- Tiempo de juego aproximado, favoritos, búsqueda, orden, portadas propias, portadas automáticas (libretro), copia de seguridad.

## Diseño: la misma interfaz que Atrio

Marfil usa la interfaz «Revista» de Atrio, con sus mismas piezas (copiadas de `Revista.kt`, `RevistaUI.kt`, `Ediciones.kt` y `Maqueta.kt`):
papel crema (o oscuro), Fraunces para los titulares, filetes gruesos y finos, antetítulos con cuadradito de acento, pestañas subrayadas,
enlaces con flecha, cifras enormes y puntos guía. No hay dock: se navega desde el menú de la portada y con «← Marfil».
Ajustes → **Apariencia** es la de Atrio: Tema (claro, oscuro, como el móvil), **Fondo** de color, Tamaño del texto, **Acento**, y «A tu manera» (color del fondo, del texto y del acento a mano, y la **letra** de los títulos y del texto). En «A tu manera», cada color (fondo, texto y acento) tiene su enlace **«Copiar de Atrio»**, que copia solo ese color (el que Atrio tiene a mano o, si no, el que se ve en Atrio según su tema, fondo y acento). El código de un color acepta #RRGGBB, RRGGBB, #RGB y #AARRGGBB, en mayúsculas o minúsculas, escrito o pegado, y se aplica en cuanto es válido. Además, «Colores de Atrio, en directo» (sigue los de la app instalada, con opción de invertirlos) y **«Copiar de Atrio»**: Atrio sube su apariencia a tu cuenta; Marfil la lee (tema, fondo, acento, colores a mano, letras, tamaño) y copia solo lo que marques, sin tocar nada en Atrio (hace falta la cuenta vinculada, no Atrio instalado en este móvil). La apariencia de Marfil (tema, fondo, acento, colores a mano, letras, tamaño) también **viaja con tu cuenta** entre tus Marfiles (gana el cambio más reciente) y repinta los widgets de fuera. Probado contra el Firebase de mentira de Atrio, no con tu cuenta real.
**Colores de Atrio** (Ajustes → Apariencia → Colores de): Marfil lee los colores que tienes puestos en Atrio (paleta, fondo de color, colores a mano, acento, claro u oscuro) y los usa; con **Invertir** los pone del revés: el fondo con el color del texto y el texto con el del fondo, y el acento se mantiene.
Para eso Atrio 1.0.7 (o más nueva) ofrece sus colores en `content://com.gonzalo.gamerate.tema/colores` (solo colores, ningún dato tuyo). Sin esa versión, o sin Atrio, Marfil usa su propia paleta y te lo dice en Ajustes.

## Cuenta de Atrio y sincronización entre tus Marfiles

Ajustes → **Cuenta de Atrio**: entras con el mismo usuario y contraseña que en Atrio (Firebase Authentication por REST; la contraseña no se guarda, solo el pase de acceso, que se renueva solo).
Marfil no crea cuentas. Lo que se sincroniza, **solo entre tus Marfiles** (no con Atrio), igual que Atrio entre sus dispositivos (cada dispositivo escribe en su hueco bajo `/users/<tu uid>/marfil/`, con sellos de hora y escucha en directo):
juegos (por clave: sistema + nombre de archivo, paquete o web), favorito, terminado, nota, apuntes, nombre y diseño de la ficha. Gana el cambio más reciente; lo que quitas viaja como «borrado»;
las **horas y las veces se suman** (cada dispositivo sube las suyas). Una ROM que viene de otro móvil aparece «en otro dispositivo» hasta que la añades aquí (se une sola por sistema y nombre de archivo).
Tu pareja de Atrio puede leer esa parte de la cuenta (las reglas de Atrio dejan leer a la pareja todo `/users/<uid>`).
Las ROMs, los guardados y las portadas van por kDrive (siguiente sección). Probado en el ordenador contra el Firebase de mentira de Atrio (`SincroTest`) y las reglas de mezcla (`MezclaTest`); no con tu cuenta real.

## Actualizaciones automáticas (kDrive, como Atrio)

Marfil se actualiza sola con el mismo sistema que Atrio: cada versión se sube a **«Mi carpeta/versiones»** de tu kDrive como `Marfil-<versión>.apk` (junto a las `Atrio-…apk`; cada app solo mira las suyas).
Se mira al abrir, cada minuto mientras se ve y, con la app cerrada, cada 4 horas (WorkManager). Si hay una más nueva se baja, se comprueba que es Marfil y que lleva la misma firma, y se instala
**sin preguntar** cuando no la usas (Android 12+, con el permiso de instalar apps); si Android no lo deja, te pregunta, y al terminar se vuelve a abrir (con «Mostrar sobre otras apps») o te avisa.
Ajustes → **Actualizaciones**: interruptor, token y número de tu kDrive (el token de Atrio es solo de Atrio y no se comparte: hay que pegarlo aquí una vez; se guarda solo en el móvil), «Buscar ahora» y el estado de la última comprobación.
La primera vez que se instala una versión con esto hay que hacerlo a mano; a partir de ahí, sola. Probado contra el kDrive de mentira de Atrio (`ActualizadorTest`), no contra tu kDrive ni instalando de verdad en un móvil.

## Guardados, ROMs y portadas en tu kDrive

Con la cuenta de Atrio vinculada y el kDrive conectado (Ajustes), en la ficha de una ROM aparece **En la nube**:
- **Guardados**: eliges la carpeta donde el emulador deja los `.srm`/`.sav`/`.dsv`/… de ese sistema (la misma para todos sus juegos; se busca por el nombre del archivo de la ROM, también en subcarpetas).
  **Antes de jugar**, Marfil mira si en la nube hay un guardado distinto del tuyo y que no sea el que ya habías sincronizado; si lo hay, lo baja (comprobando que llega entero) y lo pone en su sitio, dejando antes una copia de lo que sustituye (`copias_guardados`, las 5 últimas).
  **Al volver a Marfil**, si el guardado de este móvil ha cambiado, lo sube. Si los dos móviles cambiaron el mismo guardado sin sincronizar entre medias, gana el de la nube y la copia te protege.
  No hay vigilancia mientras juegas (Android no avisa de cuándo un emulador escribe un archivo): se mira al lanzar y al volver.
- **ROMs**: se **suben solas** a tu kDrive (a trozos, como en Atrio) las que aún no están, solo con wifi (WorkManager; si se corta, reintenta la ROM entera). Interruptor en Ajustes → Actualizaciones. En el otro móvil, «Descargar la ROM» a la carpeta que elijas (hace falta elegir carpeta); se une sola al juego. Solo a tu kDrive.
- **Portadas** que elegiste: se suben solas y las bajan tus otros móviles si no tienen portada (si ya tienen otra, no se pisa).
- El índice (hash, id de kDrive y nombre de archivo, nunca rutas de tu móvil) viaja en tu cuenta: `/users/<uid>/marfil/nube/…`; los archivos, en «Mi carpeta/Marfil/…».

**Límite importante**: Android no deja a una app leer las carpetas privadas de otra (`Android/data/<emulador>/…`; el permiso «todos los archivos» tampoco las incluye, según la FAQ oficial de Android 11). Muchos emuladores guardan ahí por defecto (según guías de terceros, Pizza Boy y My Boy!).
Marfil solo puede sincronizar guardados que estén en una carpeta que Android te deje elegir: configura el emulador para guardar en una carpeta normal del almacenamiento (RetroArch permite cambiar la carpeta de «Savefile» en Ajustes → Directorio). Probado con carpetas normales y los servidores de mentira de Atrio (`NubePTest`); **no con SAF real ni con ningún emulador en un móvil**.

## Widgets

Seis widgets, copiados de los de Atrio (mismo lienzo: papel, titulares en Fraunces, portadas recortadas, esquinas redondas y el reborde fino de tinta)
pero con tus juegos: **Sigue jugando**, **Recientes** (la estantería; tocar un juego lo lanza), **En portada**, **¿Y ahora qué?** (una propuesta distinta cada día) la **marca** «LECTURA.» sobre tu fondo y **LECTURA en papel** (con su papel, el número del día y la fecha). Usan los colores del tema que tengas puesto (los de Marfil o los de Atrio, invertidos o no) y se repintan cuando cambias el tema, los
colores de Atrio o la biblioteca. Con los colores de Atrio no dependen de claro/oscuro; con los de Marfil, «como el sistema» lleva los dos dibujos y se ve el que toca.
Las letras también son las del tema (Ajustes → Estilo): las que vienen con la app, Vitrina o las de Google Fonts (se piden a los servicios de Google, igual que en la app; si no llegan, se usa la letra de la app).
Vistas previas en `docs/widgets/`.

## Icono

El marco de Atrio (la puerta) en versión baja y cuadrada, con la luz roja por dentro en vez de arena, sobre el mismo azul noche con su difuminado interior.

## Cómo funciona el lanzamiento (y qué no está probado)

Los comandos para abrir cada emulador **no están escritos a mano**: salen de los archivos de ES-DE para Android
(`es_systems.xml` y `es_find_rules.xml`, licencia MIT; ver `NOTICE`) y `Comando.kt` los interpreta (acción, datos, extras,
banderas, permisos de lectura al documento). Eso se prueba en el ordenador con el catálogo real (`ComandoTest`).

**Lo que NO se ha podido probar**: abrir de verdad cada emulador en un móvil. Esta versión se escribió y se verificó sin
dispositivo. Si un emulador no abre una ROM, el primer sospechoso es el permiso de acceso al archivo: algunos emuladores
necesitan que les des acceso a la carpeta dentro de sus propios ajustes (ES-DE documenta lo mismo), y RetroArch necesita su
núcleo descargado. Marfil te lo dice en la ficha del juego («Se abre con…»).

## Compilar

```
gradle :app:assembleDebug        # o ./gradlew
gradle :app:testDebugUnitTest --tests '*ComandoTest*'
gradle :app:testDebugUnitTest --tests '*CapturasTest*' -Proborazzi.test.record=true   # capturas en app/build/outputs/roborazzi
```

Necesita JDK 17+ y el SDK de Android (`local.properties` con `sdk.dir`). El APK de release se firma con `app/marfil.keystore`
(misma costumbre que Atrio: clave fija para que cada versión se instale encima de la anterior).

## Privacidad

- Portadas automáticas: pide `thumbnails.libretro.com/<sistema>/Named_Boxarts/<nombre del archivo>.png`, es decir, **el nombre
  de tus archivos** llega a ese servidor. Se apaga en Estilo → «Portadas automáticas».
- Los juegos web guardan cookies (también de terceros: muchos inician sesión en otro dominio) dentro del WebView de Marfil.
- Si vinculas tu cuenta de Atrio, tu biblioteca (títulos, notas, horas, favoritos…) se sube a tu base de datos de Firebase, legible por ti y por tu pareja de Atrio. Sin vincular, nada más sale del móvil.

## Lo leído entre dispositivos

Además de la biblioteca, cada libro sincroniza su estado de lectura por la cuenta: `/users/<uid>/marfil/lec/<libro>/<dispositivo>` (el estado entero) y `/lstamps/<libro>/<dispositivo>` (su hora, que es lo que se escucha en directo). Se une así (ver `MezclaLectura`): posición, la más reciente; «lo más lejos que has llegado», el mayor; ajustes del libro, los últimos; subrayados, notas y marcadores se juntan (los quitados viajan como borrados con hora); el tiempo leído se suma por dispositivo. Al abrir un libro se baja antes lo último de los demás (hasta 2,5 s de espera) y al salir se sube.

## Libros por kDrive e importar de Atrio

- Cada libro que añades se sube solo (con Wi-Fi) a `Mi carpeta/Marfil/Libros/<título>/`; el índice (`/users/<uid>/marfil/nube/<libro>`: id de kDrive, nombre, tamaño) viaja por tu cuenta. En otro dispositivo el libro sale «sin archivo» y se baja al abrirlo (hace falta conectar el kDrive en Ajustes → Actualizaciones).
- Ajustes → Libros → «Traer mis libros de Atrio»: lee (sin cambiar nada en Atrio) lo que Atrio subió a tu cuenta y crea cada libro con su ficha, lo leído (posición, subrayados, notas, marcadores, tiempo) y, si Atrio lo subió a kDrive, el enlace a su archivo (no se duplica). Los «abandonados» de Atrio entran como pendientes. Se puede repetir sin duplicar.

## Portadas, ficha y widgets (0.7)

- **Ficha** con la estética de la de Atrio: titular, línea en cursiva, acciones (Leer / Bajar y leer / Elegir archivo, Portada, Editar, Archivo), estante y tiempo, subrayados y notas, datos. Se puede cambiar el título y el autor, elegir o cambiar el archivo, quitar el archivo de este dispositivo y quitar el libro.
- **Portada**: de la galería, de internet (iTunes Libros y Open Library, sin clave), la del propio libro, o ninguna (entonces, una tapa de tela con el título; en pequeño, la inicial). La que eliges se sube a `Marfil/Portadas/` de tu kDrive (`/marfil/portadas/<libro>` en la cuenta) y sale en tus otros dispositivos.
- **Tus libros en todos tus dispositivos**: un trabajo en segundo plano (WorkManager, con Wi-Fi) sube libros y portadas y, si lo dejas activo (Ajustes → Libros), baja los que faltan.
- **Widgets** (los mismos de Atrio y Carmesí, con libros): Sigue leyendo, En la mesilla, En portada, ¿Y ahora qué?, Lectura de la semana (con tu objetivo), Una cita (de tus subrayados; un toque abre el libro en esa frase) y la cabecera (sola o en papel). Vista previa en `docs/widgets/hoja.png`.

## Marfil 0.8

- **Secciones como páginas**: Leyendo, Pendientes, Leídos, Abandonados y Autores se recorren con el dedo (o con las pestañas); Buscar mira en todos los estantes.
- **Sagas** (las de Atrio, de vuelta): en las listas los libros de una saga salen juntos (un toque los abre; «Ver la saga →» va a su página, con lo que toca leer ahora, ordenar, añadir y quitar libros). Se crean desde la ficha («Saga →»), viajan con tu cuenta y se traen de Atrio al importar.
- **Inicio**: «Ahora» (lo último que abriste), «En la mesilla» (lo que lees a medias, menos «Ahora») y «En la estantería» (lo pendiente). A la derecha, en acento, las páginas del libro (`320 págs`; `118/320` si lo llevas empezado).
- **Páginas de cada libro**, de la fuente más fiable: lo que escribes tú → Atrio / el PDF → Open Library → calculadas por el texto del EPUB. Ver `Paginas`.
- **Ficha** con la casilla de lectura de Atrio (cuadrados anchos: «Bajar de tu kDrive», «Elegir archivo», «Seguir leyendo»).
- **Portada**: además de la galería, iTunes y Open Library, el buscador de imágenes de Atrio con **Pinterest** en cuadrados (`Internet.kt`).
- **Abandonados** como estado propio (y un abandonado que vuelves a leer pasa a Leyendo).
- **Widgets**: las etiquetas llevan siempre la negrita de la app, como en Atrio.

## Marfil 0.8.1: leer deslizando

- Deslizando (PDF o cómic «hacia abajo», o «Desplazamiento continuo»): en vez de mantener pulsado, **dos toques seguidos** seleccionan la palabra (subrayar, nota, diccionario); un toque suelto espera una fracción de segundo por si es el primero de dos.
- La línea de abajo (lo que queda y el %) no se enseña en ese modo: se solapaba con el texto.
- Al llegar al final de un capítulo se puede **seguir deslizando**: arrastrar o el impulso del dedo pasa al siguiente capítulo (y al principio, al anterior). Probado con un navegador con pantalla táctil: `herramientas/lector-prueba`.

## Marfil 0.8.2

- **Ordenar** cada estante (Recientes, Añadidos, Título, Autor, Páginas); se recuerda por estante.
- **Atajo «Seguir leyendo»** al mantener pulsado el icono de la app: abre el libro que estás leyendo en tu página.
- **APK más pequeño**: solo arm64 y x86_64 (el traductor del diccionario se copiaba para cuatro procesadores).

## Marfil 0.9.0

- **Fecha de terminado** (y «2.ª vez» si lo relees): se apunta sola cuando un libro pasa a Leído, viaja entre dispositivos, sale en la ficha y es el orden «Terminado» de Leídos. De Atrio se trae la fecha en que lo terminaste.
- **Reto anual** (Ajustes → Lectura → «Libros al año»): en la portada («RETO 2026 · 7 DE 12 LIBROS») y en el widget de la semana.
- **Ajustes del lector iguales en todos tus dispositivos** (letra, tamaño, tema, márgenes…); el brillo y lo propio de los PDF son de cada dispositivo.
- **Subrayados en un nodo ligero** de tu cuenta (`/marfil/subrayados`) para la cita del Diario de Azabache.
- **«En Azabache»** en la ficha: la valoración (solo se ve) y «Abrir en Azabache →».
- **Portadas que en Atrio eran fotos tuyas**: al importar se enlazan con su archivo de tu kDrive y se bajan a todos tus dispositivos.
- El botón de un libro ya leído dice «Volver a leer».

## Marfil 0.9.1: pasar de capítulo con un botón

- Leyendo deslizando («Desplazamiento continuo», EPUB y PDF adaptado), **ya no se pasa de capítulo solo** (el salto parpadeaba y no se sabía dónde se llegaba). Al final de cada capítulo hay un botón **«Siguiente capítulo → nombre del capítulo»** y al principio **«← Capítulo anterior»** (que lleva al final del anterior). Al pasar, un aviso dice el capítulo al que se llega.
- En el modo de páginas los botones no se ven. En el último capítulo no hay botón de siguiente.
- Probado con un navegador táctil: `herramientas/lector-prueba/botones.js`.
- **Icono nuevo**: la flor de líneas sobre el degradado crema (fondo, primer plano y monocromo para los iconos con tema). Se genera con `herramientas/icono-marfil.py` a partir de `docs/iconos/marfil-original.jpg`.
