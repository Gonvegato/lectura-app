package com.gonzalo.marfil

import android.content.Context
import org.json.JSONObject

/** «7 h 32 min», «45 min». */
fun readTime(min: Long): String = if (min < 60) "$min min" else "${min / 60} h${if (min % 60 > 0) " ${min % 60} min" else ""}"

/** Los ajustes que no son de un libro (el objetivo diario de lectura). */
object Ajustes {
    fun metaLectura(c: Context): Int = c.getSharedPreferences("marfil_ajustes", Context.MODE_PRIVATE).getInt("meta_lectura", 0)
    /** El reto del año: cuántos libros quieres terminar (0 = sin reto). */
    fun metaAnual(c: Context): Int = c.getSharedPreferences("marfil_ajustes", Context.MODE_PRIVATE).getInt("meta_anual", 0)
    /** Cambiar un objetivo lo apunta y lo sube a tu cuenta (ver [Objetivos]: lo leen tus otros Marfil y Azabache). */
    fun ponMetaAnual(c: Context, n: Int) = Objetivos.cambiado(c, metaLectura(c), n)
    fun ponMetaLectura(c: Context, min: Int) = Objetivos.cambiado(c, min, metaAnual(c))
}

/**
 * Lo que el lector guarda de cada libro (en este dispositivo): por dónde vas (cfi, fracción, etiqueta), lo más lejos que has llegado,
 * los ajustes con los que se abre, los subrayados y notas, los marcadores y el tiempo leído (por día y por dispositivo).
 * La ficha del libro (su porcentaje, su estante, su tiempo) se mantiene al día en la [Biblioteca].
 */
object EstadoLectura {
    private fun prefs(ctx: Context) = ctx.getSharedPreferences("lector", Context.MODE_PRIVATE)

    fun estado(ctx: Context, id: String): JSONObject =
        prefs(ctx).getString(id, null)?.let { runCatching { JSONObject(it) }.getOrNull() } ?: JSONObject()

    /** Guarda el estado de un libro y pone al día su ficha. */
    fun poner(ctx: Context, id: String, o: JSONObject) {
        o.put("syncAt", System.currentTimeMillis())
        prefs(ctx).edit().putString(id, o.toString()).apply()
        alDia(ctx, id, o)
        SincroP.cambioLectura(ctx, id)
    }

    /** Lo que llega de otro dispositivo: se guarda sin volver a subirlo. */
    fun ponerSinAvisar(ctx: Context, id: String, o: JSONObject) { prefs(ctx).edit().putString(id, o.toString()).apply(); alDia(ctx, id, o, forzar = true) }

    /** El archivo del libro (su dirección) o null. */
    fun archivo(ctx: Context, id: String): String? = Biblioteca.por(id)?.uri?.ifBlank { null }

    fun olvidar(ctx: Context, id: String) { prefs(ctx).edit().remove(id).apply() }

    private val ultimaVez = HashMap<String, Long>()

    /** Pone al día la ficha: lo leído (el punto más lejano), la última vez, el tiempo de este dispositivo y, si toca, «Leyendo» o «Leído». */
    private fun alDia(ctx: Context, id: String, o: JSONObject, forzar: Boolean = false) {
        val l = Biblioteca.por(id) ?: return
        // Lo leído: donde estás o lo más lejos que llegaste; donde estás no cuenta si es un salto largo sin confirmar (mirar el final no es leerlo)
        val fr = maxOf(if (o.optBoolean("enSalto")) 0.0 else o.optDouble("fraction", 0.0), o.optDouble("bestFrac", 0.0)).toFloat().coerceIn(0f, 1f)
        val ms = o.optJSONObject("stats")?.optJSONObject("dev")?.optJSONObject(Cuenta.dispositivo)?.optLong("total", 0L) ?: 0L
        // El tiempo de los demás dispositivos (y el que trajo la importación de Atrio) viaja dentro del estado: se muestra sumado
        val dev = o.optJSONObject("stats")?.optJSONObject("dev")
        val ajenos = HashMap(l.ajenosMs)
        if (dev != null) for (k in dev.keys()) if (k != Cuenta.dispositivo) ajenos[k] = maxOf(ajenos[k] ?: 0L, dev.optJSONObject(k)?.optLong("total", 0L) ?: 0L)
        val ultima = o.optLong("read", l.ultima).coerceAtLeast(l.ultima)
        // Si marcaste a mano por dónde vas (y es más nuevo que lo que sabe la ficha), lo leído es eso, aunque sea menos
        val fijada = o.optLong("bestSet", 0L).takeIf { it > l.fraccionFijada }
        val estado = when {
            fr >= 0.985f -> Estado.LEIDO
            fijada != null && l.estado == Estado.LEIDO -> Estado.LEYENDO
            // Marcar que vas por el principio: es que aún no lo has empezado
            fijada != null && fr <= 0.003f && l.estado == Estado.LEYENDO -> Estado.PENDIENTE
            l.estado == Estado.PENDIENTE && (fr > 0.003f || ms >= 60_000L) -> Estado.LEYENDO
            // Un abandonado que vuelves a leer (avanzas) pasa otra vez a «Leyendo»
            l.estado == Estado.ABANDONADO && fr > l.fraccion + 0.003f -> Estado.LEYENDO
            else -> l.estado
        }
        val ahora = System.currentTimeMillis()
        val cambioGrande = fijada != null || estado != l.estado || kotlin.math.abs(fr - l.fraccion) >= 0.004f || ajenos != l.ajenosMs
        // No se reescribe la biblioteca en cada página: solo si cambia algo de peso o han pasado 20 s
        if (!forzar && !cambioGrande && ahora - (ultimaVez[id] ?: 0L) < 20_000L) return
        ultimaVez[id] = ahora
        val n = l.copy(fraccion = if (fijada != null) fr else maxOf(fr, l.fraccion), fraccionFijada = fijada ?: l.fraccionFijada, ultima = ultima, msPropios = maxOf(ms, 0L).takeIf { it > 0 } ?: l.msPropios, ajenosMs = ajenos, estado = estado)
        if (n != l) Biblioteca.poner(n)
    }

    /** Tras 5 minutos leyendo seguidos hoy, el libro pasa solo a «Leyendo» si estaba pendiente (una sola vez al día). Devuelve true si lo ha movido. */
    fun pasaALeyendo(ctx: Context, id: String, st: JSONObject, hoy: java.time.LocalDate = java.time.LocalDate.now()): Boolean {
        if (st.optString("autoLeyendo") == hoy.toString()) return false
        if (!ReadStats.cuenta(st.optJSONObject("stats"), hoy.toString())) return false
        st.put("autoLeyendo", hoy.toString())
        val l = Biblioteca.por(id) ?: return false
        if (l.estado != Estado.PENDIENTE && l.estado != Estado.ABANDONADO) return false
        Biblioteca.poner(l.copy(estado = Estado.LEYENDO))
        return true
    }
}
