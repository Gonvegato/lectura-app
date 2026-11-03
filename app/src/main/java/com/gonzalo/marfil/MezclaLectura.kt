package com.gonzalo.marfil

import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

/*
 * Mezclar lo leído de un libro entre tus dispositivos. El estado de un libro (lo que guarda el lector) viaja entero y se une así:
 *  - dónde vas (cfi, fracción, etiqueta, última lectura): lo más reciente; «lo más lejos que has llegado»: lo que más lejos llegó;
 *  - los ajustes del libro: los que cambiaste más tarde;
 *  - subrayados, notas y marcadores: se juntan los de todos (cada uno se reconoce por su sitio y su hora de creación); si uno se
 *    editó en dos sitios, gana la edición más reciente; lo que quitas viaja como «borrado» con su hora y se quita en los demás,
 *    salvo que se haya vuelto a poner después;
 *  - el tiempo leído se suma por dispositivo (cada uno solo escribe en el suyo), como siempre.
 */
object MezclaLectura {
    /** Los borrados de subrayados y marcadores se olvidan a los 180 días. */
    private const val OLVIDO = 180L * 86_400_000L

    /** Lo que identifica un subrayado o un marcador: dónde está y cuándo se creó. */
    fun clave(n: JSONObject) = n.optString("value") + "|" + n.optLong("created")

    /** El nombre de un libro en tu cuenta (la clave tiene puntos y barras, que Firebase no admite). */
    fun kid(clave: String): String = MessageDigest.getInstance("SHA-1").digest(clave.toByteArray()).joinToString("") { "%02x".format(it) }.take(20)

    private fun sinMod(n: JSONObject) = JSONObject(n.toString()).apply { remove("mod") }.toString()

    /**
     * Antes de guardar la lista que manda el lector ([campo]: «notes» o «marks»): a lo nuevo o editado se le pone la hora de
     * ahora («mod») y lo que ya no está se apunta como borrado («borr_notes» / «borr_marks»).
     */
    fun actualizar(st: JSONObject, campo: String, nuevas: JSONArray, ahora: Long = System.currentTimeMillis()) {
        val antes = HashMap<String, JSONObject>()
        st.optJSONArray(campo)?.let { a -> for (i in 0 until a.length()) a.optJSONObject(i)?.let { antes[clave(it)] = it } }
        val borr = JSONObject(st.optJSONObject("borr_$campo")?.toString() ?: "{}")
        val vistas = HashSet<String>()
        val salida = JSONArray()
        for (i in 0 until nuevas.length()) {
            val n = nuevas.optJSONObject(i) ?: continue
            val k = clave(n)
            if (!vistas.add(k)) continue
            val o = antes[k]
            val x = JSONObject(n.toString())
            when {
                o == null -> x.put("mod", n.optLong("mod", 0L).takeIf { it > 0 } ?: ahora)
                sinMod(o) == sinMod(n) -> x.put("mod", o.optLong("mod", n.optLong("created")))
                else -> x.put("mod", ahora)
            }
            salida.put(x)
        }
        for (k in antes.keys) if (k !in vistas) borr.put(k, ahora)
        for (k in borr.keys().asSequence().toList()) if (ahora - borr.optLong(k) > OLVIDO) borr.remove(k)
        st.put(campo, salida)
        if (borr.length() > 0) st.put("borr_$campo", borr) else st.remove("borr_$campo")
    }

    private fun lista(a: JSONObject, b: JSONObject, campo: String, out: JSONObject) {
        val borr = JSONObject()
        for (s in listOf(a, b)) s.optJSONObject("borr_$campo")?.let { t -> for (k in t.keys()) borr.put(k, maxOf(borr.optLong(k, 0L), t.optLong(k))) }
        val por = LinkedHashMap<String, JSONObject>()
        for (s in listOf(a, b)) s.optJSONArray(campo)?.let { arr ->
            for (i in 0 until arr.length()) {
                val n = arr.optJSONObject(i) ?: continue
                val k = clave(n)
                val cur = por[k]
                if (cur == null || modDe(n) > modDe(cur)) por[k] = n
            }
        }
        val res = JSONArray()
        // (en un orden fijo, para que mezclar en un sentido o en otro dé lo mismo)
        for ((k, n) in por.entries.sortedWith(compareBy({ it.value.optLong("created") }, { it.key }))) if (borr.optLong(k, 0L) <= modDe(n)) res.put(n)
        if (res.length() > 0 || a.has(campo) || b.has(campo)) out.put(campo, res)
        if (borr.length() > 0) out.put("borr_$campo", borr)
    }

    private fun modDe(n: JSONObject) = n.optLong("mod", n.optLong("created", 0L))

    /** Une el estado de un libro en dos dispositivos. [yo]: este dispositivo (para el tiempo leído antiguo sin repartir). */
    fun mezclar(a: JSONObject?, b: JSONObject?, yo: String): JSONObject {
        val x = a ?: JSONObject(); val y = b ?: JSONObject()
        // Dónde vas: lo más reciente; lo demás que solo tenga una de las dos se conserva
        val (base, otra) = if (y.optLong("read") > x.optLong("read")) y to x else x to y
        val out = JSONObject(otra.toString())
        for (k in base.keys()) out.put(k, base.get(k))
        // Los ajustes del libro: los de la edición más reciente
        val sa = x.optLong("settingsAt"); val sb = y.optLong("settingsAt")
        (if (sb > sa) y else x).optJSONObject("settings")?.let { out.put("settings", it) } ?: (if (sb > sa) x else y).optJSONObject("settings")?.let { out.put("settings", it) }
        out.put("settingsAt", maxOf(sa, sb))
        // Lo más lejos que has llegado: el que más lejos llegó (con su sitio y su etiqueta)
        val lejos = if (y.optDouble("bestFrac", 0.0) > x.optDouble("bestFrac", 0.0)) y else x
        for (k in listOf("bestCfi", "bestFrac", "bestLabel")) if (lejos.has(k)) out.put(k, lejos.get(k))
        out.put("read", maxOf(x.optLong("read"), y.optLong("read")))
        // Tiempo leído, por dispositivo
        val st = ReadStats.merge(x.optJSONObject("stats"), y.optJSONObject("stats"), yo, "legacy")
        out.put("stats", st)
        lista(x, y, "notes", out); lista(x, y, "marks", out)
        val auto = maxOf(x.optString("autoLeyendo"), y.optString("autoLeyendo")); if (auto.isNotEmpty()) out.put("autoLeyendo", auto)
        out.put("syncAt", maxOf(x.optLong("syncAt"), y.optLong("syncAt")))
        return out
    }

    /** El mismo contenido con las claves en orden (dos estados iguales dan el mismo texto, vengan como vengan). */
    private fun canon(v: Any?): String = when (v) {
        is JSONObject -> v.keys().asSequence().sorted().joinToString(",", "{", "}") { "\"$it\":" + canon(v.get(it)) }
        is JSONArray -> (0 until v.length()).joinToString(",", "[", "]") { canon(v.get(it)) }
        else -> v.toString()
    }

    /** ¿Dicen lo mismo los dos estados (sin contar la hora de guardado)? Para no guardar ni subir dos veces lo mismo. */
    fun igual(a: JSONObject, b: JSONObject): Boolean = canon(JSONObject(a.toString()).apply { remove("syncAt") }) == canon(JSONObject(b.toString()).apply { remove("syncAt") })
}
