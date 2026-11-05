# Contrato de datos de Marfil (lo que otras apps pueden leer)

Todo vive en tu cuenta de Firebase bajo `/users/<uid>/marfil/`. Marfil escribe; Azabache (y quien venga) **lee**. Nada de esto cambia Atrio.
Estado: **propuesta de Marfil**; Azabache aún no lo lee. Los nombres están pensados para no tener que cambiarse al añadirlo.

## Identidad de un libro

`clave` = `"libro:" + norm(título) + ":" + norm(autor)` (norm: minúsculas, sin tildes, y todo lo que no sea letra o número —de cualquier alfabeto— pasa a un espacio). Se fija al crear el libro y no cambia aunque se edite el título.
En la cuenta un libro se llama `kid` = los 20 primeros hex del SHA-1 de la clave (la clave tiene puntos y barras, que Firebase no admite).
Las valoraciones de Azabache deben enlazarse por esta misma clave.

## Nodos

| Nodo | Contenido | Quién escribe |
|---|---|---|
| `dev/<dispositivo>/data` | La biblioteca de ese dispositivo (texto JSON `v:1`): por libro `k` (clave), `t` título, `a` autor, `ar` archivo, `f` favorito, `e` estado (`PENDIENTE`/`LEYENDO`/`LEIDO`), `an` añadido, `ul` última lectura, `fr` fracción (0–1), `mod`, `msp` ms leídos en ese dispositivo, `pg`/`po` páginas y su origen, `tm` fechas (ms) en que se terminó, `pu` dirección de la portada si se eligió de internet (vacío si es de la galería: esa va por kDrive); `borrados` (clave → hora). | cada Marfil, solo el suyo |
| `stamps/<dispositivo>` | Hora de la última subida de ese dispositivo (servidor). | cada Marfil |
| `lec/<kid>/<dispositivo>` | `{t, u, s}`: `s` es el estado de lectura entero (posición, subrayados, notas, marcadores, ajustes, tiempo). **Pesado**: no leerlo para listados. | cada Marfil |
| `lstamps/<kid>/<dispositivo>` | Hora de la última subida de ese libro en ese dispositivo. | cada Marfil |
| `tiempo/<kid>/<escritor>` | `{t, a, dev}`: título, autor y el tiempo leído: `dev[<dispositivo>] = {total (ms), days: {"AAAA-MM-DD": ms}, best}`. **Ligero**: es lo que debe leer un Diario. | cada Marfil |
| `subrayados/<kid>/<escritor>` | `{t, a, n}`: título, autor y `n` = lista de `{x: texto, o: nota, c: creado (ms), l: etiqueta, k: sitio}`; **ligero** y sin el estado del lector. Cada escritor trae los subrayados de todos los dispositivos: une por `k`+`c`. Sin subrayados, el nodo no existe. | cada Marfil |
| `objetivos` | `{dia, ano, anio, mod}`: tus objetivos de lectura, los minutos al día (`dia`, 0 = sin objetivo) y los libros del año (`ano`, 0 = sin reto; `anio`, el año en que se fijó), y cuándo se cambiaron (`mod`, ms). **Los fija Marfil**: Azabache los enseña y no los cambia. Manda el `mod` más alto. Al cambiarlos, se sella `stamps/<dispositivo>`. | cada Marfil |
| `nube/<kid>` | `{f, n, s, u}`: id del archivo en kDrive, nombre, tamaño, hora. | cualquier Marfil |

## Cómo leer el tiempo para un Diario

Para cada libro, lee `tiempo/<kid>/*`. Cada escritor trae su vista de **todos** los dispositivos; une por dispositivo y día quedándote con el **máximo** (no sumes entre escritores: se contaría varias veces) y después suma los dispositivos. Con eso, los minutos de cada día y por libro salen sin duplicar. Los dispositivos de Atrio importados aparecen como un dispositivo más (con su propio id).

## Lo que aún no está (se añadirá cuando Azabache lo pida)

Nada pendiente de Marfil. Las fechas de terminado van en `dev/<dispositivo>/data` (`tm`).

## Abrir Marfil desde fuera

Con el intent de lanzamiento del paquete `com.gonzalo.marfil`: `go=escritos` abre la sección Escritos (con `obra=<id de Escribir>`, abre esa obra en el lector); `clave=<clave del libro>` abre la ficha
de ese libro (la clave es la `k` de la biblioteca); `ficha=<id>` y `leer=<id>` (por id de este dispositivo) los usan los widgets.
- **Verse entre hermanas**: desde Android 11 una app no ve a otra (`getLaunchIntentForPackage` da null) si no la declara en `<queries>` de su
  manifiesto. Cada hermana declara a las demás (`com.gonzalo.hiedra`, `com.gonzalo.marfil`, `com.gonzalo.cobalto`, `com.gonzalo.gamerate`);
  un test (`ManifiestoHermanasTest`) lo vigila. Sin esto, «Jugar en Hiedra», «Leer en Marfil» y «Escribir en Cobalto» no salen nunca en un móvil.

## Días seguidos (la racha)

La misma regla en Hiedra, Marfil, Cobalto y Azabache (`Racha.kt` en cada una, con las mismas pruebas, `RachaTest`):

- Un día **cuenta** si ese día jugaste o leíste **al menos un minuto**, o escribiste **al menos una palabra**. Cada app mira lo suyo
  (Hiedra lo jugado, Marfil lo leído, Cobalto lo escrito); Azabache, las tres a la vez (basta con una).
- La racha son los días seguidos que cuentan **hasta hoy**; si hoy todavía no cuenta, **hasta ayer** (no se rompe a primera hora).
- Los días son los del calendario del móvil (`"AAAA-MM-DD"` en los nodos de tiempo y de palabras).
