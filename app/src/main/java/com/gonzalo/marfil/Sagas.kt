package com.gonzalo.marfil

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * Una saga: libros juntos bajo un nombre, en su orden. No mueve nada de su sitio (cada libro sigue en su estante); solo los junta en las listas.
 * Los libros se apuntan por su clave (la misma en todos tus dispositivos), así que la saga viaja con tu cuenta.
 */
data class Saga(val id: String, val nombre: String, val claves: List<String>, val mod: Long = 0L) {
    fun aJson(): JSONObject = JSONObject().put("id", id).put("n", nombre).put("mod", mod).put("k", JSONArray().also { a -> claves.forEach { a.put(it) } })
    companion object {
        fun deJson(o: JSONObject): Saga? {
            val id = o.optString("id").ifBlank { return null }
            val k = o.optJSONArray("k") ?: JSONArray()
            return Saga(id, o.optString("n"), (0 until k.length()).map { k.optString(it) }.filter { it.isNotBlank() }.distinct(), o.optLong("mod"))
        }
    }
}

/** Una fila de una lista de libros: un libro suelto o una saga con sus libros (los que entran en esa lista, en el orden de la saga). */
sealed class FilaLista {
    class Suelto(val libro: Libro, val saga: Saga? = null, val numero: Int = 0) : FilaLista()
    class DeSaga(val saga: Saga, val miembros: List<Libro>) : FilaLista()
}

object Sagas {
    val lista = mutableStateListOf<Saga>()
    /** Las que borraste (su id y cuándo): así los demás dispositivos también las quitan. */
    val borradas = HashMap<String, Long>()
    var cargada by mutableStateOf(false)
    private var archivo: File? = null
    private var appCtx: Context? = null

    fun cargar(c: Context) {
        if (cargada) return
        appCtx = c.applicationContext
        val f = File(c.filesDir, "sagas.json").also { archivo = it }
        if (f.exists()) runCatching {
            val o = JSONObject(f.readText())
            o.optJSONArray("sagas")?.let { a -> (0 until a.length()).mapNotNull { a.optJSONObject(it)?.let(Saga::deJson) }.forEach { lista.add(it) } }
            o.optJSONObject("borradas")?.let { b -> b.keys().forEach { k -> borradas[k] = b.optLong(k) } }
        }
        cargada = true
    }

    private fun guardar() {
        val f = archivo ?: return
        runCatching {
            val o = JSONObject().put("sagas", JSONArray().also { a -> lista.forEach { a.put(it.aJson()) } }).put("borradas", JSONObject().also { b -> borradas.forEach { (k, v) -> b.put(k, v) } })
            val t = File(f.path + ".tmp"); t.writeText(o.toString()); t.renameTo(f)
        }
        appCtx?.let { c -> Widgets.pedir(c); SincroP.cambio(c) }
    }

    fun por(id: String): Saga? = lista.firstOrNull { it.id == id }

    /** La saga a la que pertenece un libro (por su clave), si tiene. */
    fun de(clave: String): Saga? = lista.firstOrNull { clave in it.claves }

    /** Guarda una saga (con la hora de ahora). Un libro solo está en una: se quita de las demás. */
    fun poner(s: Saga) {
        val ahora = System.currentTimeMillis()
        val nueva = s.copy(claves = s.claves.distinct(), mod = ahora)
        // Los libros de esta saga salen de las otras
        lista.toList().forEach { o -> if (o.id != nueva.id && o.claves.any { it in nueva.claves }) {
            val i = lista.indexOfFirst { it.id == o.id }
            val resto = o.claves.filter { it !in nueva.claves }
            if (resto.isEmpty()) { lista.removeAt(i); borradas[o.id] = ahora } else lista[i] = o.copy(claves = resto, mod = ahora)
        } }
        val i = lista.indexOfFirst { it.id == nueva.id }
        if (i >= 0) lista[i] = nueva else { lista.add(nueva); borradas.remove(nueva.id) }
        guardar()
    }

    fun crear(nombre: String, claves: List<String> = emptyList()): Saga = Saga(UUID.randomUUID().toString().replace("-", "").take(10), nombre.trim(), claves).also { poner(it) }

    fun quitar(id: String) { if (lista.removeAll { it.id == id }) { borradas[id] = System.currentTimeMillis(); guardar() } }

    /** Pone un libro en una saga (al final) o, con [saga] null, lo saca de la que estuviera. */
    fun asignar(clave: String, saga: Saga?) {
        val actual = de(clave)
        if (actual?.id == saga?.id) return
        if (actual != null) { val r = actual.claves - clave; if (r.isEmpty()) quitar(actual.id) else poner(actual.copy(claves = r)) }
        if (saga != null) por(saga.id)?.let { poner(it.copy(claves = it.claves + clave)) }
    }

    /** Pone en la biblioteca lo que ha salido de mezclar con tus otros dispositivos (sin cambiar la hora de nadie). */
    fun aplicarMezcla(nuevas: List<Saga>, borr: Map<String, Long>) {
        lista.clear(); lista.addAll(nuevas); borradas.clear(); borradas.putAll(borr)
        val f = archivo ?: return
        runCatching {
            val o = JSONObject().put("sagas", JSONArray().also { a -> lista.forEach { a.put(it.aJson()) } }).put("borradas", JSONObject().also { b -> borradas.forEach { (k, v) -> b.put(k, v) } })
            File(f.path + ".tmp").also { it.writeText(o.toString()) }.renameTo(f)
        }
        appCtx?.let { Widgets.pedir(it) }
    }

    /** Los libros de una saga, en su orden (los que ya no están en la biblioteca se saltan). */
    fun libros(s: Saga, todos: List<Libro> = Biblioteca.libros.toList()): List<Libro> {
        val porClave = todos.associateBy { it.clave }
        return s.claves.mapNotNull { porClave[it] }
    }

    /**
     * Agrupa una lista de libros (ya ordenada) por sagas: cada saga sale una vez, donde está su primer libro de la lista, con los de la lista en el
     * orden de la saga. Una saga con un solo libro en la lista sale como libro suelto (con su saga y número, para no perder el dato).
     */
    fun agrupar(libros: List<Libro>, sagas: List<Saga> = lista.toList()): List<FilaLista> {
        val deClave = HashMap<String, Saga>().also { m -> sagas.forEach { s -> s.claves.forEach { m[it] = s } } }
        val enLista = libros.map { it.clave }.toSet()
        val vistas = HashSet<String>()
        val out = ArrayList<FilaLista>()
        for (l in libros) {
            val s = deClave[l.clave]
            if (s == null) { out += FilaLista.Suelto(l); continue }
            if (!vistas.add(s.id)) continue
            val miembros = s.claves.filter { it in enLista }.mapNotNull { k -> libros.firstOrNull { it.clave == k } }
            if (miembros.size >= 2) out += FilaLista.DeSaga(s, miembros)
            else out += FilaLista.Suelto(miembros.first(), s, s.claves.indexOf(miembros.first().clave) + 1)
        }
        return out
    }
}
