# Marfil

La app de leer del móvil. Hermana de [Azabache](https://github.com/Gonvegato/azabache) (la revista: valorar), [Hiedra](https://github.com/Gonvegato/hiedra)
(juegos) y [Cobalto](https://github.com/Gonvegato/cobalto) (escribir): misma interfaz «Revista» de Atrio, mismos temas, otro paquete
(`com.gonzalo.marfil`), así que conviven en el mismo móvil. Aquí se **añaden y se leen** los libros (EPUB, PDF y cómics); Azabache los
refleja para valorarlos, y los escritos de Cobalto se leen aquí.

## Capturas

Generadas en el ordenador con libros de ejemplo (`CapturasTest`, solo con `-Proborazzi.test.record=true`): [portada](docs/capturas/1_portada.png) ·
[en oscuro](docs/capturas/2_portada_oscuro.png) · [Leyendo](docs/capturas/3_lista.png) · [Pendientes](docs/capturas/3b_pendientes.png) ·
[saga abierta](docs/capturas/3c_saga_abierta.png) · [página de una saga](docs/capturas/3d_pagina_saga.png) · [Sagas](docs/capturas/3h_sagas.png) ·
[ficha](docs/capturas/4_ficha.png) · [ficha sin archivo](docs/capturas/4c_ficha_sin_archivo.png) · [ficha de un leído](docs/capturas/4d_ficha_leido.png) ·
[tapas](docs/capturas/6_tapas.png) · [elegir portada](docs/capturas/7_selector_portada.png) · [Escritos](docs/capturas/9_escritos.png) ·
[Ajustes](docs/capturas/8_ajustes.png) · [Lectura](docs/capturas/8b_ajustes_lectura.png) · [Cuenta](docs/capturas/8c_ajustes_cuenta.png) ·
[Avanzado](docs/capturas/8d_ajustes_avanzado.png). Los widgets, en [`docs/widgets/hoja.png`](docs/widgets/hoja.png).

## Qué hace

- **Portada** de revista: cabecera, número y fecha, las barras de los días que cumples tu objetivo, «Ahora» (lo último que abriste),
  «En la mesilla» (lo que lees a medias) y «En la estantería» (lo pendiente), el reto del año, y el menú de secciones.
- **Secciones** como páginas que se recorren con el dedo: Leyendo · Pendientes · Leídos · Abandonados · Autores · Sagas · Escritos. Cada
  estante se ordena (Recientes, Añadidos, Título, Autor, Páginas, Terminado) y se recuerda; «Buscar» mira en todos.
- **Ficha** con la estética de Atrio: estado arriba (Pendiente · Leyendo · Leído · Abandonado), saga y favorito, la casilla de lectura
  («Seguir leyendo», «Bajar de tu kDrive», «Elegir archivo»), portada, tiempo y páginas, subrayados y notas, fecha de terminado (y «2.ª vez»),
  y «En Azabache» (la valoración, solo se ve, y «Abrir en Azabache →»).
- **Lector** (foliate-js para EPUB y cómics, pdf.js para PDF): páginas o desplazamiento continuo, letra, tamaño, tema y márgenes (iguales en
  todos tus dispositivos), subrayados, notas y marcadores, diccionario (Wikcionario), botones de capítulo siguiente y anterior al deslizar.
- **Portadas**: de la galería, de internet (iTunes Libros, Open Library y el buscador de imágenes de Atrio) o ninguna (una tapa de tela con el
  título). Marfil no saca portadas de los archivos. Las que eliges viajan: las de internet por su dirección, las tuyas por kDrive.
- **Sagas**: en las listas los libros de una saga salen juntos; su página dice qué toca leer ahora, y se ordenan, añaden y quitan libros.
- **Escritos**: las obras de Cobalto con capítulos terminados, convertidas en EPUB, para leerlas con el lector (no salen en los estantes ni viajan).
- **Páginas** de cada libro, de la fuente más fiable: lo que escribes tú → Atrio / el PDF → Open Library → calculadas por el texto del EPUB.
- **Widgets** (los de Atrio, con libros): Sigue leyendo, En la mesilla, En portada, ¿Y ahora qué?, Lectura de la semana, Una cita y la cabecera.
  Eligen libro al ponerlos («el que toque» o uno fijo) y se repintan con el tema.
- Objetivo diario de minutos y reto anual de libros; atajo «Seguir leyendo» al mantener pulsado el icono; copia de la biblioteca en un archivo.

## Ajustes

En pestañas, como Atrio: **Apariencia** (tema claro/oscuro/como el móvil, fondo de color, tamaño del texto, acento, «A tu manera» con los colores y
las letras a mano, cada color con «Copiar de Atrio», y los colores de Atrio en directo, invertidos o no) · **Lectura** (objetivo diario y libros al
año; el lector se ajusta desde dentro, con «Letra») · **Cuenta** (la cuenta de Atrio y los libros en la nube: tener todos aquí, traer los de Atrio,
subir a kDrive ahora) · **Avanzado** (actualizarse sola, el token y el número de kDrive, y las copias).

## Cuenta de Atrio y sincronización entre tus Marfiles

Entras con el mismo usuario y contraseña que en Atrio (Firebase Authentication por REST; se guarda el pase de acceso, no la contraseña). Marfil no
crea cuentas. Cada dispositivo escribe en su hueco bajo `/users/<uid>/marfil/` con sellos de hora y escucha en directo: libros (por clave:
título + autor), estado, favorito, saga, portada, páginas, fechas de terminado y los ajustes del lector. Gana el cambio más reciente; lo que
quitas viaja como «borrado»; el **tiempo leído se suma** por dispositivo. Un libro que viene de otro móvil aparece «sin archivo» hasta que se baja
de kDrive o eliges su archivo. Tu pareja de Atrio puede leer esa parte de la cuenta (las reglas de Atrio dejan leer a la pareja todo `/users/<uid>`).
Lo que lee Azabache está en `docs/CONTRATO-DATOS.md`. Probado en el ordenador contra el Firebase de mentira (`MezclaTest`, `MezclaLecturaTest`,
`ImportarAtrioTest`); no con tu cuenta real.

## Actualizaciones automáticas (kDrive, como Atrio)

Cada versión se sube a **«Mi carpeta/versiones»** de tu kDrive como `Marfil-<versión>.apk` (`python3 herramientas/subir-version.py`). Marfil mira
al abrir, cada minuto mientras se ve y, cerrada, cada 4 horas (WorkManager); si hay una más nueva se baja, se comprueba que es Marfil con la misma
firma y se instala sin preguntar cuando no la usas (Android 12+, con el permiso de instalar apps); si Android no lo deja, te pregunta. El token
de kDrive se pega una vez en Ajustes → Avanzado y se guarda solo en este móvil (no viaja por la cuenta: la pareja la lee).

## Compilar

```
gradle :app:assembleDebug
gradle :app:testDebugUnitTest                                                   # 0 fallos; ContratoTest deja en app/build/outputs/contrato/marfil.json lo que sube Marfil, para la prueba ContratoHermanasTest de Azabache; NotaAzabacheContratoTest lee herramientas/casos/azabache.json (lo que sube Azabache)
gradle :app:testDebugUnitTest --tests '*CapturasTest*' -Proborazzi.test.record=true   # capturas en app/build/outputs/roborazzi
gradle :app:assembleRelease                                                     # firmado con app/marfil.keystore
```

Necesita JDK 17+ y el SDK de Android (`local.properties` con `sdk.dir`). Al sacar versión: subir `versionCode`/`versionName`
(`app/build.gradle.kts`) y `VERSION` (`PantEstilo.kt`), apuntar los cambios aquí y subir el APK a kDrive.

## Privacidad

- Las portadas de internet se buscan por título y autor en iTunes Libros y Open Library; el diccionario pregunta a Wikcionario.
- Con la cuenta vinculada, la biblioteca (títulos, estados, tiempo, subrayados) se sube a tu Firebase, legible por ti y por tu pareja de Atrio.
  Los archivos de los libros y las portadas tuyas van a tu kDrive. Sin vincular, nada sale del móvil.

## Versiones

## 0.12.20

- **Fechas y cifras como en las otras tres** (`Formato.kt`, con las mismas pruebas en las cuatro): «16 oct 2026» en fichas y textos, «16/10/2026» en las tablas de datos y siempre con punto de millar («4.010»). Los meses van escritos en la app (Android, según la versión, les ponía punto: «oct.»).
- **Escritos: «N capítulos nuevos»** en el acento cuando Cobalto ha publicado capítulos desde la última vez que abriste la obra, y debajo, por dónde vas («vas por el 34 % · Capítulo 5»).
- «En Azabache» sin nota: una línea, «Sin valorar aún · Valorar en Azabache →».
- Al elegir el libro de un widget, los terminados dicen «TERMINADO».
- Los contadores de las pestañas van en tinta (más oscura la de la pestaña elegida), no en rojo: el rojo queda para lo que se toca.
- En las listas, la línea de debajo del título un punto más grande y más legible.
- **La cabecera de la portada se pliega al bajar**: arriba solo se quedan el nombre y «Buscar · + Añadir»; el número, la fecha, las barras y el menú se van con la página.
- Debajo de «Marfil · hecho en casa», en gris, la versión instalada.

## 0.12.19

- **El lector, más fluido.** Mientras leías, cada poco (cada 0,4 % avanzado) Marfil reescribía la biblioteca entera y, un segundo
  después, repintaba todos los widgets (decodificando portadas) justo mientras pasabas página o abrías el menú. Ahora, con el libro
  abierto, los widgets no se repintan (se hace al cerrarlo) y la ficha se pone al día cada 2 % o cada 20 s, y siempre al cerrar. Las
  barras del menú van en su propia capa: aparecer ya no obliga a repintar la página que hay debajo.
- **Cambiar qué libro sigue un widget sin quitarlo**: déjalo pulsado en el escritorio y sale «Reconfigurar» (Android 12 o más, en los
  lanzadores que lo ofrecen, como el de Pixel): abre la misma lista que al ponerlo («Sigue leyendo» y «En portada»: el que toque o uno fijo). El menú del dedo pulsado es del escritorio,
  no de la app: lo único que una app puede hacer es decir que su widget se puede reconfigurar, y eso es lo que se ha hecho.
- **Un libro terminado dice «Terminado»**, en el acento, en vez de su porcentaje: en su ficha (en grande, con la barra llena), en las
  listas (a la derecha, donde van las páginas) y en los widgets.

## 0.12.18

- **Arreglo de las portadas que se perdían.** Al abrir Marfil se mezcla tu biblioteca con la de tus otros dispositivos y, a la vez, se bajan
  las portadas. La mezcla se hacía con una copia de la biblioteca de unos segundos antes y, al aplicarla, quitaba la portada que acababa
  de llegar (y el archivo de un libro recién bajado). Ahora la portada y el archivo se toman de como están en ese momento.
- Las portadas que ya estaban bajadas en el móvil pero el libro había dejado de apuntar a ellas **se vuelven a poner solas** al abrir
  Marfil (sin bajar nada). Las que no estén en el móvil se bajan de tu kDrive como siempre.
- **«¿Vas por aquí?» tras un salto largo** en el lector (un cuarto del libro o más de golpe: del 98 % al 2 %, o al revés, con la barra o el
  índice): sale un aviso con «Voy por aquí». Si lo pulsas, esa es tu posición de verdad aunque sea menos de lo que llegaste: el libro
  abre ahí, el porcentaje de la ficha baja (y vuelve a crecer al leer) y lo mismo en tus otros dispositivos y en Azabache. Marcar el
  principio deja el libro en Pendientes; marcar antes del final uno que salía Leído lo pasa a Leyendo.
- Mientras no lo confirmes, un salto largo **ya no cuenta como leído**: mirar el final no sube el porcentaje ni se queda como «lo más lejos».

## 0.12.17

- Los «días seguidos» leyendo siguen la misma regla en Hiedra, Marfil, Cobalto y Azabache (`Racha.kt`, con las mismas pruebas en las cuatro, y en el contrato): cuenta un día con al menos un minuto leído; si hoy aún no, desde ayer. Para ti no cambia nada (era la misma).

## 0.12.16

- **Un solo objetivo de lectura**: los minutos al día y los libros del año los fija Marfil (Ajustes → Lectura) y viajan con tu cuenta
  (`marfil/objetivos`) a tus otros Marfil y a Azabache, que los enseña sin poder cambiarlos. Antes cada Marfil tenía el suyo y Azabache
  otro, y podían no coincidir. La primera vez, si Marfil no tenía ninguno, se traen los que tenías puestos en Azabache.

## 0.12.15

- En las filas de datos (etiqueta · puntos · cifra), si no cabe todo en una línea, la etiqueta va entera arriba y la cifra debajo, en vez
  de cortar la etiqueta con puntos suspensivos (como en Azabache 1.2.8).

## 0.12.14

- El widget «Sigue leyendo» alto y estrecho ya no corta su rótulo («SIGUE LE…»): ahí dice «Leyendo».

## 0.12.13

- La fila de orden y filtros (Recientes · Nombre · …) lleva el mismo degradado en los bordes que las pestañas.
- En los bordes de la fila de pestañas, un degradado cuando hay más pestañas por ese lado (para que no parezcan cortadas).

## 0.12.12

- Las pestañas (secciones, Ajustes) se desplazan solas solo cuando la elegida no se ve entera, y entonces la centran: antes se
  pegaba al borde izquierdo y la de al lado salía cortada.

## 0.12.11

- Lo leído llega a tu cuenta aunque no haya red al cerrar el libro o Android congele Marfil nada más salir: queda un trabajo de fondo
  (SubidaWorker) que sube lo pendiente en cuanto hay red, aunque la app siga congelada. Si Marfil ya lo subió, no hace nada.

## 0.12.10

- Textos al día: lo que seguía diciendo «Atrio» para hablar del móvil (seguir sus colores en directo, copiar su apariencia de tu cuenta,
  «los mismos que en Atrio») dice ahora Azabache, que es quien está en el móvil. La cuenta sigue siendo la cuenta de Atrio.

## 0.12.9

- **Arreglo**: en Android 13 o más, los avisos del actualizador («Marfil tiene una versión nueva», «Marfil se ha actualizado, toca para abrirla»)
  no se veían nunca: faltaba el permiso de avisos. Ahora se declara y se concede desde Ajustes → Avanzado («Avisos de actualización»).
- **Arreglo**: en Android 11 o más, «Escribir en Cobalto» no salía nunca (Marfil no declaraba que puede ver a Cobalto; los tests no lo
  detectan porque Robolectric no filtra paquetes). Ahora el manifiesto declara a Hiedra y Cobalto, y un test lo vigila.
- Un subrayado o una nota hechos en otro dispositivo repintan los widgets («La cita») en cuanto llegan, no solo cuando cambia la biblioteca.

## 0.12.8

- Mientras lees, lo leído (posición, tiempo, subrayados) sube a tu cuenta como mucho una vez por minuto, no cada pocos segundos; al salir del
  lector sube al momento, como antes. Azabache y tus otros Marfiles lo escuchan, así que con eso basta y se gasta mucho menos.

## 0.12.7

- Lo que Azabache sube (sus fichas, para «En Azabache» y para traer libros) no se vuelve a bajar si sus sellos no han cambiado.

## 0.12.6: la ficha de un escrito

- La ficha de una obra de Cobalto (Escritos) no ofrece portada, editar, borrar, estante, saga, favorito ni «En Azabache» (nada de eso es
  suyo): arriba lleva «Escribir en Cobalto». Y quitarla de la biblioteca no deja rastro en lo que viaja a tus otros dispositivos.

## 0.12.5: la nota de Azabache, al momento

- Marfil escucha el sello de Azabache: en cuanto valoras un libro allí, «En Azabache» en su ficha cambia sin salir ni esperar los 15 minutos.
- Las fechas de la ficha salen siempre en español.

## 0.12.4

- En la portada, «+ Añadir» en el acento, como en las listas y como en Hiedra y Cobalto; el pie de Ajustes dice de quién es hermana.

## 0.12.3: Azabache abre el libro exacto, y la obra exacta de Cobalto

- Marfil entiende el extra `clave` (la clave del libro, la misma en todos tus Marfiles): «Leer» en Azabache abre Marfil en la ficha de ese libro.
  Los widgets siguen usando `ficha` (por id) y `leer` (directo al lector).
- En Escritos, «Escribir en Cobalto» abre Cobalto **en esa obra** (no en su portada). Y al revés: Azabache y Cobalto pueden pedir
  «lee esta obra» (`go=escritos` + `obra`), y Marfil la prepara y la abre en el lector.

## 0.12.1: los Escritos llegan solos

- Marfil escucha el sello de Cobalto: en cuanto das un capítulo por terminado allí (y Cobalto sincroniza), aquí se traen los Escritos y se
  rehace su EPUB, sin entrar en la pantalla (Cobalto 0.2.1 o más nueva).
- Al subir lo leído de un libro, los nodos ligeros que lee Azabache (`tiempo`, `subrayados`) van antes del sello: el Diario de Azabache ya no
  puede leerlos viejos.

## 0.12.0: los Escritos de Cobalto, portadas para Azabache y ajustes en pestañas

- **Escritos** (en el menú): las obras de Cobalto con capítulos terminados se leen aquí, con el lector de Marfil. Cada obra se convierte
  en un EPUB (portada, partes y capítulos, con las cursivas, negritas, citas y saltos de escena de Cobalto) que se rehace solo cuando
  Cobalto cambia algo. Son libros «de casa»: no salen en las listas, ni en los estantes, ni viajan a otros dispositivos ni a la nube.
  «Escribir en Cobalto» abre Cobalto si está en el móvil; Azabache abre esta pantalla desde su «Escritos».
- **Portadas**: la dirección de la portada que elegiste de internet viaja con el libro (`pu`), así Azabache (y tus otros móviles)
  enseñan la misma portada que tienes aquí. Las que subiste de tu galería siguen yendo por kDrive.
- **Ajustes** en pestañas, como Atrio: Apariencia · Lectura (objetivos y el lector) · Cuenta (Atrio y los libros en la nube) ·
  Avanzado (actualizaciones y copias). Lo mismo, pero sin tenerlo todo a la vista.

## 0.11.1

- **Azabache**: una ficha que Azabache refleja de Marfil y que aún no has valorado (todas las notas a 0, sin reseña) ya no sale en «En
  Azabache» como un 0: sigue «sin valorar».

## 0.11.0: el estado arriba y los widgets eligen libro

- En la ficha, las pestañas de estado (Pendiente · Leyendo · Leído · Abandonado) van arriba, pegadas a la barra; debajo quedan la saga y el favorito.
- **Los widgets eligen libro**: al poner «Sigue leyendo» o «En portada» en el escritorio, Android pregunta qué libro abre: «el que toque» (lo
  que estés leyendo, como hasta ahora) o uno fijo.

## 0.10.1: los filtros de orden en todas las secciones

- Las secciones ya no precomponen las páginas vecinas: en las pruebas del ordenador, con ellas precompuestas, los filtros de orden («Título»,
  «Autor»…) de las páginas que no eran la primera no recibían el toque. Si en el móvil te pasaba, esto lo arregla; si no, no cambia nada visible.

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

## Lo leído entre dispositivos

Además de la biblioteca, cada libro sincroniza su estado de lectura por la cuenta: `/users/<uid>/marfil/lec/<libro>/<dispositivo>` (el estado entero) y `/lstamps/<libro>/<dispositivo>` (su hora, que es lo que se escucha en directo). Se une así (ver `MezclaLectura`): posición, la más reciente; «lo más lejos que has llegado», el mayor; ajustes del libro, los últimos; subrayados, notas y marcadores se juntan (los quitados viajan como borrados con hora); el tiempo leído se suma por dispositivo. Al abrir un libro se baja antes lo último de los demás (hasta 2,5 s de espera) y al salir se sube.

## Libros por kDrive e importar de Atrio

- Cada libro que añades se sube solo (con Wi-Fi) a `Mi carpeta/Marfil/Libros/<título>/`; el índice (`/users/<uid>/marfil/nube/<libro>`: id de kDrive, nombre, tamaño) viaja por tu cuenta. En otro dispositivo el libro sale «sin archivo» y se baja al abrirlo (hace falta conectar el kDrive en Ajustes → Avanzado).
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
