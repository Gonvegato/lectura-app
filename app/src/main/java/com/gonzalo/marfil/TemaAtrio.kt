package com.gonzalo.marfil

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONObject

/**
 * Los colores que tienes puestos en Atrio. Atrio los ofrece en content://com.gonzalo.gamerate.tema/colores (solo colores:
 * fondo, texto, acento…, ya mezclados con tu fondo de color, tus colores a mano y claro/oscuro) y Marfil los lee cada vez
 * que se abre. Se guarda la última lectura, así se siguen viendo aunque Atrio no conteste.
 */
object TemaAtrio {
    const val PAQUETE = "com.gonzalo.gamerate"
    val URI: Uri = Uri.parse("content://$PAQUETE.tema/colores")
    private val COLUMNAS = listOf("paper", "crudo", "ink", "acento", "sand", "line", "edge", "muted", "oro")

    /** Los colores de Atrio de la última lectura (null: todavía ninguna). */
    var tonos by mutableStateOf<Tones?>(null)
    /** Si no se han podido leer: por qué (texto para Ajustes). Vacío: bien. */
    var aviso by mutableStateOf("")

    /** Los tonos de Marfil a partir de los nueve colores que da Atrio (ARGB), en este orden: [COLUMNAS]. */
    fun tonosDe(v: List<Int>): Tones {
        fun l(i: Int) = v[i].toLong() and 0xFFFFFFFFL
        // criterios (gráficos, historia…): Marfil no los usa; se rellenan con tinta, gris y acento
        return Tones(paper = l(0), crudo = l(1), ink = l(2), general = l(3), gfx = l(2), story = l(7), music = l(7), gameplay = l(3), gold = l(8),
            sand = l(4), line = l(5), edge = l(6), muted = l(7))
    }

    private fun guardado(c: Context) = c.getSharedPreferences("marfil_tema", Context.MODE_PRIVATE)

    /** Lo último que se leyó (al arrancar, antes de preguntar a Atrio). */
    fun cargarGuardado(c: Context) {
        val j = guardado(c).getString("atrio", null) ?: return
        runCatching { val o = JSONObject(j); tonos = tonosDe(COLUMNAS.map { o.getInt(it) }) }
    }

    /** Pregunta a Atrio por sus colores. Devuelve true si los ha leído. */
    fun refrescar(c: Context): Boolean {
        val v = try {
            c.contentResolver.query(URI, null, null, null, null)?.use { q ->
                if (!q.moveToFirst()) null else COLUMNAS.map { q.getInt(q.getColumnIndexOrThrow(it)) }
            }
        } catch (e: Exception) { null }
        if (v == null) {
            aviso = if (instalada(c)) "Atrio no ofrece sus colores todavía: instala la versión 1.0.7 o más nueva de Atrio." else "Atrio no está instalado en este móvil."
            return false
        }
        val nuevos = tonosDe(v)
        val cambio = tonos != nuevos
        tonos = nuevos; aviso = ""
        Widgets.pedir(c)
        guardado(c).edit().putString("atrio", JSONObject().also { o -> COLUMNAS.forEachIndexed { i, k -> o.put(k, v[i]) } }.toString()).apply()
        return true
    }

    fun instalada(c: Context) = try { c.packageManager.getPackageInfo(PAQUETE, 0); true } catch (e: Exception) { false }
}
