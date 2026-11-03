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
            val estado = when { pst == 1 -> Estado.LEYENDO; g != null -> Estado.LEIDO; else -> Estado.PENDIENTE }
            val inicio = maxOf(g?.optLong("start") ?: 0L, p?.optLong("start") ?: 0L)
            val fin = g?.optLong("end") ?: 0L
            val fr = (st?.let { maxOf(it.optDouble("fraction", 0.0), it.optDouble("bestFrac", 0.0)) } ?: 0.0).toFloat().coerceIn(0f, 1f)
            val portada = (g?.optString("cover").orEmpty().ifBlank { p?.optString("cover").orEmpty() }).takeIf { it.startsWith("http") }.orEmpty()
            val nube = nubes["libro:$id"]?.let { NubeLibro(it.optLong("f"), it.optString("n"), it.optLong("s")) }?.takeIf { it.f > 0 }
            val libro = Libro(
                id = "atrio$id", titulo = titulo, autor = autor, portada = portada, archivo = nube?.n.orEmpty(),
                favorito = g?.optBoolean("fav") ?: false, estado = estado,
                anadido = if (inicio > 0) inicio else maxOf(g?.optLong("updated") ?: 0L, p?.optLong("updated") ?: 0L).takeIf { it > 0 } ?: ahora,
                ultima = maxOf(fin, st?.optLong("read") ?: 0L), fraccion = if (estado == Estado.LEIDO) 1f else fr
            )
            Candidato(id, libro, st, nube, abandonado = pst == 2)
        }.sortedBy { it.libro.titulo.lowercase() }
    }

    /** Lo último que Atrio subió a tu cuenta (de sus dispositivos más recientes). Bloqueante (red). */
    fun leer(): List<Candidato>? {
        if (!Cuenta.vinculada) return null
        val st = Cuenta.db("GET", "/users/${Cuenta.uid}/stamps", null)?.let { JSONObject(it) } ?: return null
        val devs = st.keys().asSequence().map { it to st.optLong(it) }.sortedByDescending { it.second }.take(4).toList()
        val blobs = ArrayList<JSONObject>()
        for ((dev, _) in devs) {
            val raw = Cuenta.db("GET", "/users/${Cuenta.uid}/dev/$dev/data", null) ?: continue
            val datos = (JSONTokener(raw).nextValue() as? String) ?: continue
            runCatching { JSONObject(datos) }.getOrNull()?.let { blobs += it }
        }
        return if (blobs.isEmpty()) null else candidatos(blobs)
    }

    class Resultado(val nuevos: Int, val yaEstaban: Int, val conArchivo: Int)

    /** Pone los candidatos en Marfil. Bloqueante (red para el índice de kDrive). Se puede repetir sin duplicar. */
    fun importar(ctx: Context, cands: List<Candidato>, avance: (Int, Int) -> Unit = { _, _ -> }): Resultado {
        var nuevos = 0; var ya = 0; var conArchivo = 0
        cands.forEachIndexed { i, c ->
            avance(i, cands.size)
            val previo = Biblioteca.existente(c.libro.titulo, c.libro.autor)
            val libro = previo ?: c.libro.copy(id = Biblioteca.nuevoId()).also { Biblioteca.poner(it) }
            if (previo == null) nuevos++ else ya++
            c.estado?.let { atrio ->
                val local = EstadoLectura.estado(ctx, libro.id)
                val unido = MezclaLectura.mezclar(local, JSONObject(atrio.toString()).apply { remove("uri"); remove("name") }, Cuenta.dispositivo)
                if (!MezclaLectura.igual(unido, local)) EstadoLectura.poner(ctx, libro.id, unido)
            }
            if (c.nube != null && NubeLibros.de(ctx, libro) == null) { runCatching { NubeLibros.registrar(ctx, libro, c.nube) }; conArchivo++ }
        }
        avance(cands.size, cands.size)
        return Resultado(nuevos, ya, conArchivo)
    }
}
