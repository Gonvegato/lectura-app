package com.gonzalo.marfil

import org.json.JSONArray
import org.json.JSONObject

/*
 * Mezclar la biblioteca de tus dispositivos. Cada dispositivo sube a tu cuenta SU biblioteca (un Blob) y baja la de los demás:
 *  - los libros se emparejan por su clave (título y autor);
 *  - lo que se edita de la ficha (título, autor, favorito, estante) lo decide el cambio más reciente;
 *  - el tiempo leído se suma: cada dispositivo sube el SUYO y el total es la suma de todos;
 *  - lo leído (la fracción) es lo más lejano de todos, y la última vez que se abrió, la más reciente;
 *  - lo que quitas viaja como «borrado» (con su hora) y se quita en los demás, salvo que se haya vuelto a cambiar después;
 *  - un libro de otro dispositivo se crea aquí sin archivo («en otro dispositivo») hasta que se baje de tu kDrive o lo añadas.
 */

/** Lo que un dispositivo sube de cada libro: lo compartido y SU tiempo leído. */
class LibroSync(
    val clave: String, val titulo: String, val autor: String, val archivo: String, val favorito: Boolean, val estado: Estado,
    val anadido: Long, val ultima: Long, val fraccion: Float, val mod: Long, val msp: Long, val paginas: Int = 0, val po: String = "", val terminados: List<Long> = emptyList(),
    val portadaUrl: String = "",
    /** Cuándo se marcó a mano la posición (0 = nunca): la fracción de la marca más reciente manda sobre «lo más lejos». */
    val ff: Long = 0L
)

/** La biblioteca de un dispositivo, tal como la sube a la cuenta. */
class Blob(val dev: String, val libros: List<LibroSync>, val borrados: Map<String, Long>, val ajustes: String = "", val sagas: List<Saga> = emptyList(), val sagasBorradas: Map<String, Long> = emptyMap(), val lector: String = "")

class ResMezcla(val libros: List<Libro>, val borrados: Map<String, Long>, val cambio: Boolean)

object Mezcla {
    /** Los borrados se olvidan a los 180 días (un dispositivo que lleve más sin conectar ya no los verá). */
    const val OLVIDO = 180L * 86_400_000L

    fun aBlob(dev: String, libros: List<Libro>, borrados: Map<String, Long>, ajustes: String = "", sagas: List<Saga> = emptyList(), sagasBorradas: Map<String, Long> = emptyMap(), lector: String = ""): Blob = Blob(dev,
        libros.filter { it.titulo.isNotBlank() && !it.esEscrito }.distinctBy { it.clave }.map { l ->
            LibroSync(l.clave, l.titulo, l.autor, l.archivo, l.favorito, l.estado, l.anadido, l.ultima, l.fraccion, l.mod, l.msPropios, l.paginas, l.paginasOrigen, l.terminados, l.portadaUrl, l.fraccionFijada)
        }, borrados, ajustes, sagas, sagasBorradas, lector)

    fun aJson(b: Blob): String = JSONObject().put("v", 1).put("dev", b.dev)
        .put("borrados", JSONObject().also { o -> b.borrados.forEach { (k, v) -> o.put(k, v) } })
        .put("libros", JSONArray().also { a ->
            b.libros.forEach { g ->
                a.put(JSONObject().put("k", g.clave).put("t", g.titulo).put("a", g.autor).put("ar", g.archivo).put("f", g.favorito).put("e", g.estado.name)
                    .put("an", g.anadido).put("ul", g.ultima).put("fr", g.fraccion.toDouble()).put("mod", g.mod).put("msp", g.msp).put("pg", g.paginas).put("po", g.po).put("tm", JSONArray().also { a -> g.terminados.forEach { a.put(it) } }).put("pu", g.portadaUrl).apply { if (g.ff > 0) put("ff", g.ff) })
            }
        })
        .apply {
            if (b.ajustes.isNotBlank()) put("aj", JSONObject(b.ajustes))
            if (b.sagas.isNotEmpty()) put("sg", JSONArray().also { a -> b.sagas.forEach { a.put(it.aJson()) } })
            if (b.lector.isNotBlank()) put("la", JSONObject(b.lector))
            if (b.sagasBorradas.isNotEmpty()) put("sgb", JSONObject().also { x -> b.sagasBorradas.forEach { (k, v) -> x.put(k, v) } })
        }.toString()

    fun deJson(s: String?): Blob? = try {
        if (s.isNullOrBlank()) null else {
            val o = JSONObject(s)
            val ja = o.optJSONArray("libros") ?: JSONArray()
            val bo = o.optJSONObject("borrados")
            Blob(o.optString("dev"), (0 until ja.length()).mapNotNull { i ->
                val g = ja.getJSONObject(i)
                LibroSync(g.getString("k"), g.optString("t"), g.optString("a"), g.optString("ar"), g.optBoolean("f"),
                    Estado.entries.firstOrNull { it.name == g.optString("e") } ?: Estado.PENDIENTE,
                    g.optLong("an"), g.optLong("ul"), g.optDouble("fr", 0.0).toFloat(), g.optLong("mod"), g.optLong("msp"), g.optInt("pg"), g.optString("po"), g.optJSONArray("tm")?.let { a -> (0 until a.length()).map { a.optLong(it) } } ?: emptyList(), g.optString("pu"), g.optLong("ff"))
            }, bo?.keys()?.asSequence()?.associateWith { bo.optLong(it) } ?: emptyMap(), o.optJSONObject("aj")?.toString().orEmpty(),
                o.optJSONArray("sg")?.let { a -> (0 until a.length()).mapNotNull { a.optJSONObject(it)?.let(Saga::deJson) } } ?: emptyList(),
                o.optJSONObject("sgb")?.let { x -> x.keys().asSequence().associateWith { x.optLong(it) } } ?: emptyMap(),
                o.optJSONObject("la")?.toString().orEmpty())
        }
    } catch (e: Exception) { null }

    /**
     * Mezcla [locales] con los [remotos] (los de los OTROS dispositivos que han cambiado). [yo] es este dispositivo (sus propios blobs se ignoran).
     * Devuelve la biblioteca nueva, los borrados y si ha cambiado algo.
     */
    fun mezclar(
        locales: List<Libro>, borrados: Map<String, Long>, remotos: List<Blob>, yo: String, ahora: Long = System.currentTimeMillis(),
        nuevoId: () -> String = { Biblioteca.nuevoId() }
    ): ResMezcla {
        val otros = remotos.filter { it.dev != yo }
        val bor = HashMap(borrados)
        otros.forEach { r -> r.borrados.forEach { (k, t) -> if (t > (bor[k] ?: 0L)) bor[k] = t } }
        bor.entries.removeAll { ahora - it.value > OLVIDO }

        // Lo de los demás, por clave (sin lo que se borró después de su último cambio)
        val remotoDe = LinkedHashMap<String, MutableList<Pair<String, LibroSync>>>()
        otros.forEach { r -> r.libros.forEach { g -> if ((bor[g.clave] ?: 0L) <= g.mod) remotoDe.getOrPut(g.clave) { ArrayList() }.add(r.dev to g) } }

        val vistas = HashSet<String>()
        val salida = ArrayList<Libro>()
        for (l in locales) {
            if (!vistas.add(l.clave)) continue                       // (dos iguales: se queda uno)
            if ((bor[l.clave] ?: 0L) > l.mod) continue               // quitado en otro dispositivo después de tu último cambio
            val rs = remotoDe[l.clave]
            salida += if (rs == null) l else unir(l, rs)
        }
        for ((k, rs) in remotoDe) {
            if (k in vistas) continue
            vistas += k
            salida += crear(rs, nuevoId())
        }
        // (los que ya estaban, en su sitio; los nuevos, al principio, los más recientes primero)
        val ids = locales.map { it.id }.toSet()
        val finales = salida.filter { it.id !in ids }.sortedByDescending { it.anadido } + salida.filter { it.id in ids }
        val cambio = finales != locales || bor != borrados
        return ResMezcla(finales, bor, cambio)
    }

    /** De varias cuentas de páginas (origen → número), la más fiable ([Paginas.rango]); a igual fiabilidad, la mayor. */
    private fun mejorPaginas(c: List<Pair<String, Int>>): Pair<String, Int>? = c.filter { it.second > 0 }.maxWithOrNull(compareBy({ Paginas.rango(it.first) }, { it.second }))

    class ResSagas(val sagas: List<Saga>, val borradas: Map<String, Long>, val cambio: Boolean)

    /** Mezcla las sagas de este dispositivo con las de los demás: de cada una gana la que se cambió más tarde; lo borrado se borra salvo que se haya vuelto a cambiar después. */
    fun mezclarSagas(locales: List<Saga>, borradas: Map<String, Long>, remotos: List<Blob>, yo: String, ahora: Long = System.currentTimeMillis()): ResSagas {
        val otros = remotos.filter { it.dev != yo }
        val bor = HashMap(borradas)
        otros.forEach { r -> r.sagasBorradas.forEach { (k, t) -> if (t > (bor[k] ?: 0L)) bor[k] = t } }
        bor.entries.removeAll { ahora - it.value > OLVIDO }
        val por = LinkedHashMap<String, Saga>()
        for (s in locales + otros.flatMap { it.sagas }) { val cur = por[s.id]; if (cur == null || s.mod > cur.mod) por[s.id] = s }
        val vivas = por.values.filter { (bor[it.id] ?: 0L) <= it.mod }
        // (en el orden en que estaban aquí; las nuevas, al final)
        val orden = locales.map { it.id }
        val finales = vivas.sortedBy { s -> orden.indexOf(s.id).let { if (it < 0) Int.MAX_VALUE else it } }
        return ResSagas(finales, bor, finales != locales || bor != borradas)
    }

    /**
     * Lo leído, de varios dispositivos ((marca a mano, fracción)): la de la marca a mano más reciente y, entre las de esa marca (o si
     * nadie marcó nada), la más lejana. Así un «voy por aquí» baja el porcentaje en todos tus dispositivos, y luego vuelve a crecer al leer.
     */
    fun fraccionDe(c: List<Pair<Long, Float>>): Pair<Long, Float> {
        val ff = c.maxOf { it.first }
        return ff to c.filter { it.first == ff }.maxOf { it.second }
    }

    private fun unir(l: Libro, rs: List<Pair<String, LibroSync>>): Libro {
        val g = rs.maxByOrNull { it.second.mod }!!.second
        val base = if (g.mod > l.mod) l.copy(titulo = g.titulo.ifBlank { l.titulo }, autor = g.autor, favorito = g.favorito, estado = g.estado, mod = g.mod, portadaUrl = g.portadaUrl.ifBlank { l.portadaUrl }) else l
        val ms = l.ajenosMs + rs.associate { it.first to it.second.msp }
        val anadidos = (listOf(l.anadido) + rs.map { it.second.anadido }).filter { it > 0 }
        val pg = mejorPaginas(listOf(l.paginasOrigen to l.paginas) + rs.map { it.second.po to it.second.paginas })
        return base.copy(paginas = pg?.second ?: l.paginas, paginasOrigen = pg?.first ?: l.paginasOrigen, terminados = juntarFechas(l.terminados, *rs.map { it.second.terminados }.toTypedArray()),
            ajenosMs = ms, ultima = maxOf(l.ultima, rs.maxOf { it.second.ultima }),
            fraccion = fraccionDe(listOf(l.fraccionFijada to l.fraccion) + rs.map { it.second.ff to it.second.fraccion }).second,
            fraccionFijada = fraccionDe(listOf(l.fraccionFijada to l.fraccion) + rs.map { it.second.ff to it.second.fraccion }).first,
            anadido = anadidos.minOrNull() ?: l.anadido
        )
    }

    private fun crear(rs: List<Pair<String, LibroSync>>, id: String): Libro {
        val g = rs.maxByOrNull { it.second.mod }!!.second
        return Libro(
            id = id, titulo = g.titulo, autor = g.autor, clave = g.clave, uri = "", archivo = g.archivo, favorito = g.favorito, estado = g.estado, portadaUrl = g.portadaUrl,
            anadido = rs.map { it.second.anadido }.filter { it > 0 }.minOrNull() ?: g.anadido, ultima = rs.maxOf { it.second.ultima },
            fraccion = fraccionDe(rs.map { it.second.ff to it.second.fraccion }).second, fraccionFijada = fraccionDe(rs.map { it.second.ff to it.second.fraccion }).first, mod = g.mod, msPropios = 0L, ajenosMs = rs.associate { it.first to it.second.msp },
            terminados = juntarFechas(*rs.map { it.second.terminados }.toTypedArray()), paginas = mejorPaginas(rs.map { it.second.po to it.second.paginas })?.second ?: 0, paginasOrigen = mejorPaginas(rs.map { it.second.po to it.second.paginas })?.first.orEmpty()
        )
    }
}
