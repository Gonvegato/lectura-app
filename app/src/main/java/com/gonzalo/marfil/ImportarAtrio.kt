package com.gonzalo.marfil

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

/**
 * Traer tus libros de Atrio. Atrio sube sus datos a tu cuenta (/users/<uid>/dev/<dispositivo>/data); Marfil los LEE (nada se cambia en Atrio)
 * y crea de cada libro: su ficha (título, autor, estante, favorito, portada de internet), lo leído (posición, subrayados, notas, marcadores y
 * tiempo, tal como el lector los guarda) y, si Atrio lo subió a tu kDrive, dónde está su archivo, para bajarlo al abrirlo. Se puede repetir:
 * lo que ya está no se duplica; lo leído se mezcla.
 */
object ImportarAtrio {
    /** Lo que hay en Atrio: los libros y las sagas (con los libros de cada una, por su clave, en su orden). */
    class Hallazgo(val candidatos: List<Candidato>, val sagas: List<Saga>)

    class Candidato(val atrioId: Long, val libro: Libro, val estado: JSONObject?, val nube: NubeLibro?, val abandonado: Boolean)

    private fun cambio(o: JSONObject) = maxOf(o.optLong("updated", 0L), o.optLong("sv", 0L))

    /** Los candidatos a partir de lo que subieron los dispositivos de Atrio (los datos de cada uno). Sin red: se puede probar. */
    fun candidatos(blobs: List<JSONObject>): List<Candidato> {
        // Lo borrado en cualquiera: «lista:id» → cuándo
        val tombs = HashMap<String, Long>()
        blobs.forEach { b -> b.optJSONObject("tombs")?.let { t -> for (k in t.keys()) tombs[k] = maxOf(tombs[k] ?: 0L, t.optLong(k)) } }
        fun lista(nombre: String): Map<Long, JSONObject> {
            val out = HashMap<Long, JSONObject>()
            for (b in blobs) b.optJSONArray(nombre)?.let { a ->
                for (i in 0 until a.length()) {
                    val o = a.optJSONObject(i) ?: continue
                    val id = o.optLong("id"); if (id == 0L) continue
                    val t = tombs["$nombre:$id"]
                    if (t != null && t >= cambio(o)) continue
                    val cur = out[id]
                    if (cur == null || cambio(o) > cambio(cur)) out[id] = o
                }
            }
            return out
        }
        val valorados = lista("books"); val enCurso = lista("bplays"); val lecturas = lista("lectura")
        val nubes = HashMap<String, JSONObject>()
        for (b in blobs) b.optJSONArray("nube")?.let { a ->
            for (i in 0 until a.length()) {
                val o = a.optJSONObject(i) ?: continue
                val k = o.optString("k"); if (!k.startsWith("libro:")) continue
                val cur = nubes[k]; if (cur == null || o.optLong("updated") > cur.optLong("updated")) nubes[k] = o
            }
        }
        val ahora = System.currentTimeMillis()
        val ids = (valorados.keys + enCurso.keys)
        return ids.mapNotNull { id ->
            val g = valorados[id]; val p = enCurso[id]
            val titulo = (g?.optString("name") ?: p?.optString("name")).orEmpty().trim()
            if (titulo.isBlank()) return@mapNotNull null
            val autor = (g?.optString("creator").orEmpty().ifBlank { p?.optString("creator").orEmpty() }).trim()
            val st = lecturas[id]?.optJSONObject("s")
            val pst = p?.optInt("status", 0) ?: -1
            val estado = when { pst == 1 -> Estado.LEYENDO; g != null -> Estado.LEIDO; pst == 2 -> Estado.ABANDONADO; else -> Estado.PENDIENTE }
            val inicio = maxOf(g?.optLong("start") ?: 0L, p?.optLong("start") ?: 0L)
            val fin = g?.optLong("end") ?: 0L
            val fr = (st?.let { maxOf(it.optDouble("fraction", 0.0), it.optDouble("bestFrac", 0.0)) } ?: 0.0).toFloat().coerceIn(0f, 1f)
            val portada = (g?.optString("cover").orEmpty().ifBlank { p?.optString("cover").orEmpty() }).takeIf { it.startsWith("http") }.orEmpty()
            val nube = nubes["libro:$id"]?.let { NubeLibro(it.optLong("f"), it.optString("n"), it.optLong("s")) }?.takeIf { it.f > 0 }
            val pags = maxOf(g?.optInt("pages") ?: 0, p?.optInt("pages") ?: 0)
            val libro = Libro(
                id = "atrio$id", paginas = pags, paginasOrigen = if (pags > 0) "atrio" else "", titulo = titulo, autor = autor, portada = portada, archivo = nube?.n.orEmpty(),
                favorito = g?.optBoolean("fav") ?: false, estado = estado,
                anadido = if (inicio > 0) inicio else maxOf(g?.optLong("updated") ?: 0L, p?.optLong("updated") ?: 0L).takeIf { it > 0 } ?: ahora,
                ultima = maxOf(fin, st?.optLong("read") ?: 0L), fraccion = if (estado == Estado.LEIDO) 1f else fr
            )
            Candidato(id, libro, st, nube, abandonado = pst == 2)
        }.sortedBy { it.libro.titulo.lowercase() }
    }

    /** Las sagas de libros de Atrio, con sus libros en orden (por clave). Las que quedan sin ningún libro de los hallados, no salen. */
    fun sagasDe(blobs: List<JSONObject>, cands: List<Candidato>): List<Saga> {
        val tombs = HashMap<String, Long>()
        blobs.forEach { b -> b.optJSONObject("tombs")?.let { t -> for (k in t.keys()) tombs[k] = maxOf(tombs[k] ?: 0L, t.optLong(k)) } }
        val porId = HashMap<Long, JSONObject>()
        for (b in blobs) b.optJSONArray("sagas")?.let { a ->
            for (i in 0 until a.length()) {
                val o = a.optJSONObject(i) ?: continue
                val id = o.optLong("id"); if (id == 0L || o.optInt("kind", 1) != 1) continue
                val t = tombs["sagas:$id"]
                if (t != null && t >= cambio(o)) continue
                val cur = porId[id]; if (cur == null || cambio(o) > cambio(cur)) porId[id] = o
            }
        }
        val clave = cands.associate { it.atrioId to it.libro.clave }
        return porId.values.mapNotNull { o ->
            val items = o.optJSONArray("items") ?: return@mapNotNull null
            val ks = (0 until items.length()).mapNotNull { clave[items.optLong(it)] }.distinct()
            val nombre = o.optString("name").trim()
            if (ks.isEmpty() || nombre.isBlank()) null else Saga("atrio${o.optLong("id")}", nombre, ks)
        }.sortedBy { norm(it.nombre) }
    }

    /** Lo último que Atrio subió a tu cuenta (de sus dispositivos más recientes). Bloqueante (red). */
    fun leer(): Hallazgo? {
        if (!Cuenta.vinculada) return null
        val st = Cuenta.db("GET", "/users/${Cuenta.uid}/stamps", null)?.let { JSONObject(it) } ?: return null
        val devs = st.keys().asSequence().map { it to st.optLong(it) }.sortedByDescending { it.second }.take(4).toList()
        val blobs = ArrayList<JSONObject>()
        for ((dev, _) in devs) {
            val raw = Cuenta.db("GET", "/users/${Cuenta.uid}/dev/$dev/data", null) ?: continue
            val datos = (JSONTokener(raw).nextValue() as? String) ?: continue
            runCatching { JSONObject(datos) }.getOrNull()?.let { blobs += it }
        }
        return if (blobs.isEmpty()) null else candidatos(blobs).let { Hallazgo(it, sagasDe(blobs, it)) }
    }

    class Resultado(val nuevos: Int, val yaEstaban: Int, val conArchivo: Int, val sagas: Int = 0)

    /** Pone los candidatos en Marfil. Bloqueante (red para el índice de kDrive). Se puede repetir sin duplicar. */
    fun importar(ctx: Context, cands: List<Candidato>, avance: (Int, Int) -> Unit = { _, _ -> }, sagas: List<Saga> = emptyList()): Resultado {
        var nuevos = 0; var ya = 0; var conArchivo = 0
        cands.forEachIndexed { i, c ->
            avance(i, cands.size)
            val previo = Biblioteca.existente(c.libro.titulo, c.libro.autor)
            val libro = previo ?: c.libro.copy(id = Biblioteca.nuevoId()).also { Biblioteca.poner(it) }
            if (previo == null) nuevos++ else {
                ya++
                // Lo que antes no se traía bien: los abandonados (entraban como pendientes) y las páginas
                var n = previo
                if (c.libro.estado == Estado.ABANDONADO && previo.estado == Estado.PENDIENTE) n = n.copy(estado = Estado.ABANDONADO)
                if (previo.paginas == 0 && c.libro.paginas > 0) n = n.copy(paginas = c.libro.paginas, paginasOrigen = "atrio")
                if (n != previo) Biblioteca.poner(n)
            }
            c.estado?.let { atrio ->
                val local = EstadoLectura.estado(ctx, libro.id)
                val unido = MezclaLectura.mezclar(local, JSONObject(atrio.toString()).apply { remove("uri"); remove("name") }, Cuenta.dispositivo)
                if (!MezclaLectura.igual(unido, local)) EstadoLectura.poner(ctx, libro.id, unido)
            }
            if (c.nube != null && NubeLibros.de(ctx, libro) == null) { runCatching { NubeLibros.registrar(ctx, libro, c.nube) }; conArchivo++ }
        }
        // Las sagas: las que no estén, se crean; las que ya están (de otra importación), solo reciben lo que aún no esté en ninguna saga
        var nSagas = 0
        for (s in sagas) {
            val aqui = Sagas.por(s.id)
            if (aqui == null) { Sagas.poner(s); nSagas++ }
            else {
                val nuevas = s.claves.filter { Sagas.de(it) == null }
                if (nuevas.isNotEmpty()) Sagas.poner(aqui.copy(claves = aqui.claves + nuevas))
            }
        }
        avance(cands.size, cands.size)
        return Resultado(nuevos, ya, conArchivo, nSagas)
    }
}
