package com.gonzalo.marfil

import org.json.JSONObject

/** El tiempo leído de un libro, por dispositivo: stats.dev = { <dispositivo>: { total, days, best } }. Cada dispositivo solo suma en el suyo; «total» y «days» son la suma de todos. */
object ReadStats {
    private fun dev(st: JSONObject?, legacyKey: String): JSONObject {
        st ?: return JSONObject()
        st.optJSONObject("dev")?.let { return it }
        // Antes del reparto por dispositivo: todo el tiempo era de un solo cubo
        val total = st.optLong("total", 0L)
        return if (total > 0L) JSONObject().put(legacyKey, JSONObject().put("total", total).put("days", st.optJSONObject("days") ?: JSONObject())) else JSONObject()
    }

    /** Une dos estados de tiempo. aKey/bKey: de qué dispositivo es un tiempo antiguo sin repartir (el de aquí, o «legacy»). */
    fun merge(a: JSONObject?, b: JSONObject?, aKey: String, bKey: String = "legacy"): JSONObject {
        val da = dev(a, aKey)
        val db = dev(b, bKey)
        val hasReal = (a?.has("dev") == true) || (b?.has("dev") == true)
        val out = JSONObject()
        for (src in listOf(da, db)) for (id in src.keys()) {
            if (hasReal && id == "legacy") continue   // lo antiguo ya está dentro del cubo de su dispositivo
            val x = src.getJSONObject(id)
            val cur = out.optJSONObject(id)
            if (cur == null) { out.put(id, JSONObject(x.toString())); continue }
            val days = JSONObject(cur.optJSONObject("days")?.toString() ?: "{}")
            x.optJSONObject("days")?.let { d -> for (k in d.keys()) days.put(k, maxOf(days.optLong(k, 0L), d.optLong(k, 0L))) }
            val best = JSONObject(cur.optJSONObject("best")?.toString() ?: "{}")
            x.optJSONObject("best")?.let { d -> for (k in d.keys()) best.put(k, maxOf(best.optLong(k, 0L), d.optLong(k, 0L))) }
            out.put(id, JSONObject().put("total", maxOf(cur.optLong("total", 0L), x.optLong("total", 0L))).put("days", days).apply { if (best.length() > 0) put("best", best) })
        }
        return derived(out)
    }

    /** total y days = la suma de todos los dispositivos. */
    fun derived(dev: JSONObject): JSONObject {
        var total = 0L
        val days = JSONObject()
        val best = JSONObject()
        for (id in dev.keys()) {
            val b = dev.getJSONObject(id)
            total += b.optLong("total", 0L)
            b.optJSONObject("days")?.let { d -> for (k in d.keys()) days.put(k, days.optLong(k, 0L) + d.optLong(k, 0L)) }
            // La racha más larga de un día: la mejor de entre los dispositivos (no se suman: no leíste a la vez)
            b.optJSONObject("best")?.let { d -> for (k in d.keys()) best.put(k, maxOf(best.optLong(k, 0L), d.optLong(k, 0L))) }
        }
        return JSONObject().put("dev", dev).put("total", total).put("days", days).apply { if (best.length() > 0) put("best", best) }
    }

    /** Lo mínimo seguido para que un día cuente como leído o jugado: 5 minutos. */
    const val SEGUIDO_MS = 5 * 60_000L

    /**
     * ¿Cuenta ese día como leído? Hace falta una racha de [SEGUIDO_MS] seguidos. Los días anteriores a esta versión no
     * guardaron su racha: ahí vale con haber leído ese tiempo en total.
     */
    fun cuenta(st: JSONObject?, dia: String): Boolean {
        st ?: return false
        val total = st.optJSONObject("days")?.optLong(dia, 0L) ?: 0L
        if (total <= 0L) return false
        val best = st.optJSONObject("best")
        return if (best != null && best.has(dia)) best.optLong(dia, 0L) >= SEGUIDO_MS else total >= SEGUIDO_MS
    }

    fun total(st: JSONObject?): Long = st?.optLong("total", 0L) ?: 0L
}
