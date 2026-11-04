package com.gonzalo.marfil

import android.content.Context
import org.json.JSONObject

/**
 * La valoración que tiene un libro en Azabache (por ahora, la de Atrio: lo mismo con otro nombre), para verla en su ficha. Solo se LEE: aquí no se
 * edita nada; la valoración se hace en Azabache. Se enlaza por la clave del libro (título y autor).
 */
object NotaAzabache {
    /** [general]: de 0 a 100. [atrioId]: para abrir esa ficha en Azabache. */
    class Nota(val atrioId: Long, val general: Int, val resena: String)

    private fun cambio(o: JSONObject) = maxOf(o.optLong("updated", 0L), o.optLong("sv", 0L))

    /** Las valoraciones de los libros valorados, por clave, a partir de lo que subieron los dispositivos de Atrio. Sin red: se puede probar. */
    fun deBlobs(blobs: List<JSONObject>): Map<String, Nota> {
        val tombs = HashMap<String, Long>()
        blobs.forEach { b -> b.optJSONObject("tombs")?.let { t -> for (k in t.keys()) tombs[k] = maxOf(tombs[k] ?: 0L, t.optLong(k)) } }
        val porId = HashMap<Long, JSONObject>()
        for (b in blobs) b.optJSONArray("books")?.let { a ->
            for (i in 0 until a.length()) {
                val o = a.optJSONObject(i) ?: continue
                val id = o.optLong("id"); if (id == 0L) continue
                val t = tombs["books:$id"]
                if (t != null && t >= cambio(o)) continue
                val cur = porId[id]; if (cur == null || cambio(o) > cambio(cur)) porId[id] = o
            }
        }
        val out = HashMap<String, Nota>()
        for ((id, o) in porId) {
            val nombre = o.optString("name").trim(); if (nombre.isBlank()) continue
            out[claveDe(nombre, o.optString("creator").trim())] = Nota(id, o.optInt("general", 0).coerceIn(0, 100), o.optString("review").trim())
        }
        return out
    }

    private fun prefs(c: Context) = c.getSharedPreferences("marfil_azabache", Context.MODE_PRIVATE)

    /** Lo último que se bajó (guardado en el móvil, para verlo al momento y sin red). */
    fun cargar(c: Context): Map<String, Nota> {
        val o = runCatching { JSONObject(prefs(c).getString("notas", "{}")!!) }.getOrDefault(JSONObject())
        return o.keys().asSequence().mapNotNull { k -> o.optJSONObject(k)?.let { x -> k to Nota(x.optLong("i"), x.optInt("g"), x.optString("r")) } }.toMap()
    }

    fun guardar(c: Context, m: Map<String, Nota>, ahora: Long = System.currentTimeMillis()) {
        val o = JSONObject(); m.forEach { (k, n) -> o.put(k, JSONObject().put("i", n.atrioId).put("g", n.general).put("r", n.resena)) }
        prefs(c).edit().putString("notas", o.toString()).putLong("en", ahora).apply()
    }

    /** ¿Hace más de 15 minutos que no se baja? */
    fun vieja(c: Context, ahora: Long = System.currentTimeMillis()) = ahora - prefs(c).getLong("en", 0L) > 15 * 60_000L

    /** Baja las valoraciones y las guarda. Devuelve false si no ha podido. Bloqueante (red). */
    fun refrescar(c: Context): Boolean {
        val b = runCatching { ImportarAtrio.blobs() }.getOrNull() ?: return false
        guardar(c, deBlobs(b)); return true
    }

    /** Atrio (Azabache) está instalado en este móvil. */
    fun instalado(c: Context): Boolean = runCatching { c.packageManager.getLaunchIntentForPackage("com.gonzalo.gamerate") != null }.getOrDefault(false)

    /** Abre la ficha de ese libro en Azabache (o Azabache a secas, si no se sabe cuál). Atrio ya entiende `go = item:<categoría>:<id>`; los libros son la categoría 1. */
    fun abrir(c: Context, n: Nota?) {
        val i = c.packageManager.getLaunchIntentForPackage("com.gonzalo.gamerate") ?: return
        if (n != null && n.atrioId != 0L) i.putExtra("go", "item:1:${n.atrioId}")
        c.startActivity(i.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
