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
| `dev/<dispositivo>/data` | La biblioteca de ese dispositivo (texto JSON `v:1`): por libro `k` (clave), `t` título, `a` autor, `ar` archivo, `f` favorito, `e` estado (`PENDIENTE`/`LEYENDO`/`LEIDO`), `an` añadido, `ul` última lectura, `fr` fracción (0–1), `mod`, `msp` ms leídos en ese dispositivo; `borrados` (clave → hora). | cada Marfil, solo el suyo |
| `stamps/<dispositivo>` | Hora de la última subida de ese dispositivo (servidor). | cada Marfil |
| `lec/<kid>/<dispositivo>` | `{t, u, s}`: `s` es el estado de lectura entero (posición, subrayados, notas, marcadores, ajustes, tiempo). **Pesado**: no leerlo para listados. | cada Marfil |
| `lstamps/<kid>/<dispositivo>` | Hora de la última subida de ese libro en ese dispositivo. | cada Marfil |
| `tiempo/<kid>/<escritor>` | `{t, a, dev}`: título, autor y el tiempo leído: `dev[<dispositivo>] = {total (ms), days: {"AAAA-MM-DD": ms}, best}`. **Ligero**: es lo que debe leer un Diario. | cada Marfil |
| `nube/<kid>` | `{f, n, s, u}`: id del archivo en kDrive, nombre, tamaño, hora. | cualquier Marfil |

## Cómo leer el tiempo para un Diario

Para cada libro, lee `tiempo/<kid>/*`. Cada escritor trae su vista de **todos** los dispositivos; une por dispositivo y día quedándote con el **máximo** (no sumes entre escritores: se contaría varias veces) y después suma los dispositivos. Con eso, los minutos de cada día y por libro salen sin duplicar. Los dispositivos de Atrio importados aparecen como un dispositivo más (con su propio id).

## Lo que aún no está (se añadirá cuando Azabache lo pida)

Fecha exacta en que se terminó un libro; subrayados en un nodo ligero (hoy solo van dentro de `lec`).
