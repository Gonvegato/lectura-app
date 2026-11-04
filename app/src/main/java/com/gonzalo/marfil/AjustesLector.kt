package com.gonzalo.marfil

import android.content.Context
import org.json.JSONObject

/**
 * Los ajustes por defecto del lector (letra, tamaño, tema, márgenes…): lo que eliges en un dispositivo se pone también en los demás.
 * Lo de cada dispositivo (el brillo y lo propio de los PDF) no viaja. Los ajustes de cada libro ya viajaban con su estado de lectura.
 * Viajan como `{"at": ms, "s": {…}}`; gana el cambio más reciente.
 */
object AjustesLector {
    /** Lo que es de cada dispositivo y no se sincroniza. */
    val PROPIOS = listOf("brightness", "pdfTexto", "pdfForzar")

    private fun p(c: Context) = c.getSharedPreferences("lector", Context.MODE_PRIVATE)

    /** Los ajustes de este dispositivo para subirlos («» si nunca se han cambiado aquí). */
    fun paraSubir(c: Context): String {
        val at = p(c).getLong("ajustesAt", 0L)
        if (at <= 0L) return ""
        val s = runCatching { JSONObject(p(c).getString("ajustes", "{}") ?: "{}") }.getOrNull() ?: return ""
        PROPIOS.forEach { s.remove(it) }
        return JSONObject().put("at", at).put("s", s).toString()
    }

    /** Pone los ajustes que llegan de otro dispositivo si son más recientes que los de aquí (conservando lo propio de este). Devuelve true si los cambió. */
    fun aplicar(c: Context, remoto: String): Boolean {
        val o = runCatching { JSONObject(remoto) }.getOrNull() ?: return false
        val at = o.optLong("at"); val s = o.optJSONObject("s") ?: return false
        if (at <= p(c).getLong("ajustesAt", 0L)) return false
        val local = runCatching { JSONObject(p(c).getString("ajustes", "{}") ?: "{}") }.getOrDefault(JSONObject())
        val nuevo = JSONObject(s.toString())
        PROPIOS.forEach { k -> if (local.has(k)) nuevo.put(k, local.get(k)) else nuevo.remove(k) }
        p(c).edit().putString("ajustes", nuevo.toString()).putLong("ajustesAt", at).apply()
        return true
    }
}
