package com.gonzalo.marfil

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.Normalizer
import java.util.UUID

/** En qué estante está un libro. */
enum class Estado(val nombre: String) { PENDIENTE("Pendiente"), LEYENDO("Leyendo"), LEIDO("Leído") }

/**
 * Un libro de tu biblioteca. El archivo (EPUB, PDF…) es de cada dispositivo ([uri] vacío = el libro viene de otro de tus dispositivos y aquí
 * aún no está el archivo); lo demás viaja con la cuenta: lo que editas gana por «el cambio más reciente», lo leído se suma por dispositivo
 * y el punto por el que vas es el más lejano (como en Atrio).
 */
data class Libro(
    val id: String,
    val titulo: String,
    val autor: String = "",
    /** Lo que identifica al libro en todos tus dispositivos: se fija al crearlo (título y autor de entonces) y NO cambia si luego editas el título o el autor. */
    val clave: String = claveDe(titulo, autor),
    /** Portada elegida o sacada del libro (archivo de la app); "" = sin portada. */
    val portada: String = "",
    /** Dirección del archivo en este dispositivo ("" = aún no está aquí). */
    val uri: String = "",
    /** Nombre del archivo (con su extensión). */
    val archivo: String = "",
    val favorito: Boolean = false,
    val estado: Estado = Estado.PENDIENTE,
    val anadido: Long = 0L,
    /** Última vez que se abrió (milisegundos). */
    val ultima: Long = 0L,
    /** Lo leído: de 0 a 1 (el punto más lejano de todos tus dispositivos). */
    val fraccion: Float = 0f,
    /** Última vez que se cambió la ficha (milisegundos): gana el más reciente. */
    val mod: Long = 0L,
    /** Tiempo leído (ms): el de ESTE dispositivo y el de los demás (por dispositivo); el total es la suma. */
    val msPropios: Long = 0L,
    val ajenosMs: Map<String, Long> = emptyMap(),
) {
    val msLeidos: Long get() = msPropios + ajenosMs.values.sum()
    /** El libro viene de otro dispositivo y aquí aún no tiene archivo. */
    val enOtroDispositivo: Boolean get() = uri.isBlank()
    val formato: String get() = archivo.substringAfterLast('.', "").lowercase()
}

/** Lo que identifica a un libro en todos tus dispositivos (el id de cada uno es distinto): título y autor, sin tildes ni mayúsculas. */
fun claveDe(titulo: String, autor: String) = "libro:" + norm(titulo) + ":" + norm(autor)

fun norm(s: String): String = Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()

/** Lo que se edita de un libro y viaja con la cuenta (gana el cambio más reciente). */
fun compartidos(l: Libro) = listOf(l.titulo, l.autor, l.favorito, l.estado)

/** El título de un libro a partir del nombre de su archivo: «El nombre de la rosa - Umberto Eco.epub» → («El nombre de la rosa», «Umberto Eco»). */
fun tituloYAutorDeArchivo(nombre: String): Pair<String, String> {
    val base = nombre.substringBeforeLast('.', nombre).replace('_', ' ').replace(Regex("\\s+"), " ").trim()
    val partes = base.split(" - ").map { it.trim() }.filter { it.isNotEmpty() }
    return if (partes.size >= 2) partes[0] to partes[1] else base to ""
}

object Biblioteca {
    val libros = mutableStateListOf<Libro>()
    var cargada by mutableStateOf(false)
    private var archivo: File? = null
    private var appCtx: Context? = null
    /** Los libros que quitaste (su clave y cuándo): así los demás dispositivos también los quitan al sincronizar. */
    val borrados = HashMap<String, Long>()

    fun cargar(c: Context) {
        if (cargada) return
        appCtx = c.applicationContext
        val f = File(c.filesDir, "biblioteca.json").also { archivo = it }
        if (f.exists()) runCatching { leer(f.readText(), reemplazar = true) }
        cargada = true
    }

    fun nuevoId() = UUID.randomUUID().toString().replace("-", "").take(12)

    fun leer(texto: String, reemplazar: Boolean) {
        val o = JSONObject(texto)
        val js = o.optJSONArray("libros") ?: JSONArray()
        val nuevos = (0 until js.length()).map { aLibro(js.getJSONObject(it)) }
        if (reemplazar) { libros.clear(); borrados.clear() }
        o.optJSONObject("borrados")?.let { b -> b.keys().forEach { k -> borrados[k] = maxOf(borrados[k] ?: 0L, b.optLong(k)) } }
        val claves = libros.map { it.clave }.toSet()
        libros.addAll(nuevos.filter { it.clave !in claves })
    }

    fun aTexto(): String = JSONObject().apply {
        put("version", 1)
        put("libros", JSONArray().apply { libros.forEach { put(aJson(it)) } })
        put("borrados", JSONObject().also { b -> borrados.forEach { (k, v) -> b.put(k, v) } })
    }.toString()

    fun guardar() {
        val f = archivo ?: return
        runCatching { val t = File(f.path + ".tmp"); t.writeText(aTexto()); t.renameTo(f) }
        appCtx?.let { c -> SincroP.cambio(c) }
    }

    /** Guarda un libro. Solo cambiar la ficha (título, autor, favorito, estante) cuenta como «cambio»: leer no, para que un dispositivo que solo lee no pise lo editado en otro. */
    fun poner(l: Libro) {
        val i = libros.indexOfFirst { it.id == l.id }
        val nuevo = if (i >= 0 && compartidos(libros[i]) == compartidos(l)) l.copy(mod = libros[i].mod) else l.copy(mod = System.currentTimeMillis())
        if (i >= 0) libros[i] = nuevo else { libros.add(0, nuevo); borrados.remove(nuevo.clave) }
        guardar()
    }

    fun cambiar(id: String, f: (Libro) -> Libro) { por(id)?.let { poner(f(it)) } }

    fun quitar(id: String) { por(id)?.let { borrados[it.clave] = System.currentTimeMillis() }; libros.removeAll { it.id == id }; guardar() }
    fun por(id: String): Libro? = libros.firstOrNull { it.id == id }
    fun existente(titulo: String, autor: String): Libro? = claveDe(titulo, autor).let { k -> libros.firstOrNull { it.clave == k } }

    /** Pone en la biblioteca lo que ha salido de mezclar con tus otros dispositivos (sin tocar la hora de cambio de nadie). */
    fun aplicarMezcla(nuevos: List<Libro>, borr: Map<String, Long>) {
        libros.clear(); libros.addAll(nuevos)
        borrados.clear(); borrados.putAll(borr)
        guardar()
    }

    fun aJson(l: Libro) = JSONObject().apply {
        put("id", l.id); put("clave", l.clave); put("titulo", l.titulo); put("autor", l.autor); put("portada", l.portada); put("uri", l.uri); put("archivo", l.archivo)
        put("favorito", l.favorito); put("estado", l.estado.name); put("anadido", l.anadido); put("ultima", l.ultima); put("fraccion", l.fraccion.toDouble())
        put("mod", l.mod); put("msp", l.msPropios)
        put("ajms", JSONObject().also { o -> l.ajenosMs.forEach { (k, v) -> o.put(k, v) } })
    }

    fun aLibro(o: JSONObject) = Libro(
        id = o.optString("id").ifBlank { nuevoId() }, titulo = o.optString("titulo"), autor = o.optString("autor"), clave = o.optString("clave").ifBlank { claveDe(o.optString("titulo"), o.optString("autor")) }, portada = o.optString("portada"),
        uri = o.optString("uri"), archivo = o.optString("archivo"), favorito = o.optBoolean("favorito"),
        estado = Estado.entries.firstOrNull { it.name == o.optString("estado") } ?: Estado.PENDIENTE,
        anadido = o.optLong("anadido"), ultima = o.optLong("ultima"), fraccion = o.optDouble("fraccion", 0.0).toFloat(), mod = o.optLong("mod"),
        msPropios = o.optLong("msp"), ajenosMs = o.optJSONObject("ajms")?.let { a -> a.keys().asSequence().associateWith { k -> a.optLong(k) } } ?: emptyMap()
    )
}

/** Horas y minutos de lectura: «3 h 20 min», «45 min». */
fun tiempoDe(ms: Long): String {
    val min = ms / 60_000
    return if (min < 60) "$min min" else "${min / 60} h" + if (min % 60 > 0) " ${min % 60} min" else ""
}

/** «Leído 37 %». */
fun porcentaje(f: Float) = "${(f * 100).toInt().coerceIn(0, 100)} %"
