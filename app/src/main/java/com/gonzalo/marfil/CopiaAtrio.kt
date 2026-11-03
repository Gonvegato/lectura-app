package com.gonzalo.marfil

import android.content.Context
import org.json.JSONObject
import org.json.JSONTokener

/**
 * Copiar la apariencia de Atrio. Atrio guarda su apariencia (tema, fondo, acento, colores a mano, letras y tamaño del texto) y la sube a tu
 * cuenta junto con sus datos (/users/<uid>/dev/<dispositivo>/data → "ajustes" → "skin"). Marfil lee de ahí lo más reciente, enseña qué tiene
 * Atrio y copia lo que marques. No hace falta que Atrio esté instalado en este móvil; sí la cuenta vinculada. Solo se lee: no se cambia nada en Atrio.
 */
object CopiaAtrio {
    class Ajustes(val at: Long, val skin: JSONObject)
    class Fila(val id: String, val titulo: String, val valor: String)

    /** Lo último que Atrio subió a tu cuenta (null si no hay nada de Atrio). Bloqueante (red). */
    fun leer(): Ajustes? {
        val st = Cuenta.db("GET", "/users/${Cuenta.uid}/stamps", null)?.let { JSONObject(it) } ?: return null
        val devs = st.keys().asSequence().map { it to st.optLong(it) }.sortedByDescending { it.second }.take(4).toList()
        var mejor: Ajustes? = null
        for ((dev, _) in devs) {
            val raw = Cuenta.db("GET", "/users/${Cuenta.uid}/dev/$dev/data", null) ?: continue
            val datos = (JSONTokener(raw).nextValue() as? String) ?: continue
            val aj = runCatching { JSONObject(datos).optJSONObject("ajustes") }.getOrNull() ?: continue
            val skin = aj.optJSONObject("skin") ?: continue
            if (skin.length() == 0) continue
            val a = Ajustes(aj.optLong("at"), skin)
            if (mejor == null || a.at > mejor.at) mejor = a
        }
        return mejor
    }

    private fun largo(o: JSONObject, k: String): Long = if (o.has(k)) (o.opt(k) as? Number)?.toLong() ?: 0L else 0L
    private fun hex(c: Long) = "#%06X".format(c and 0xFFFFFF)

    /** Qué tiene Atrio, en filas que se pueden copiar (solo las que Atrio tiene). */
    fun filas(a: Ajustes): List<Fila> {
        val s = a.skin
        val out = ArrayList<Fila>()
        if (s.has("theme31")) out += Fila("tema", "Tema", when (s.optInt("theme31")) { 1 -> "Claro"; 2 -> "Oscuro"; else -> "Como el móvil" })
        out += Fila("fondo", "Fondo", FONDOS.firstOrNull { it.id == s.optString("skin_fondo") }?.name ?: "Papel")
        out += Fila("acento", "Acento", ACCENT_COLORS.firstOrNull { it.id == s.optString("skin_accent_id") }?.name ?: "Rojo")
        // Los colores de «A tu manera», uno a uno: el que Atrio tiene puesto a mano o, si no, el que se ve en Atrio
        for ((id, nombre, clave) in listOf(Triple("c_fondo", "Color del fondo", "skin_fondo_propio"), Triple("c_texto", "Color del texto", "skin_tinta_propia"), Triple("c_acento", "Color del acento", "skin_acento_propio")))
            out += Fila(id, nombre, hex(colorEfectivo(a, id.removePrefix("c_"))) + if (largo(s, clave) != 0L) " (a tu manera)" else " (el de su tema)")
        val lt = s.optString("skin_letra_titulos"); val lx = s.optString("skin_letra_texto")
        out += Fila("letras", "Letras", "títulos: ${lt.ifEmpty { "la de Atrio" }} · texto: ${lx.ifEmpty { "la de Atrio" }}")
        if (s.has("skin_escala")) out += Fila("tamano", "Tamaño del texto", Skin.ESCALAS_NOMBRES[s.optInt("skin_escala", 100)] ?: "${s.optInt("skin_escala")} %")
        return out
    }

    /** El color que se ve en Atrio ([que]: fondo, texto o acento): el elegido a mano, o el que sale de su tema, fondo y acento (las mismas reglas que Atrio). */
    fun colorEfectivo(a: Ajustes, que: String): Long {
        val s = a.skin
        val fondo = FONDOS.firstOrNull { it.id == s.optString("skin_fondo") }
        val oscuro = fondo?.oscuro ?: when (s.optInt("theme31", 2)) { 1 -> false; 2 -> true; else -> Modo.oscuro }
        val paleta = PALETTES.first { it.id == "revista" }
        val base = fondo?.tones ?: if (oscuro) paleta.dark else paleta.light
        val accentId = s.optString("skin_accent_id", "edicion")
        val conAcento = when {
            fondo != null && (accentId == "edicion" || accentId == "rojo") -> base
            else -> ACCENT_COLORS.firstOrNull { it.id == accentId }?.let { x -> base.copy(general = (if (oscuro) x.dark else x.light) or 0xFF000000) } ?: base
        }
        val t = Skin.conPropios(conAcento, largo(s, "skin_fondo_propio"), largo(s, "skin_tinta_propia"), largo(s, "skin_acento_propio"))
        return when (que) { "fondo" -> t.paper; "texto" -> t.ink; else -> t.general } and 0xFFFFFFFFL
    }

    /** Copia a Marfil SOLO ese color de Atrio ([que]: fondo, texto o acento); lo demás de Marfil no se toca. Devuelve el color copiado. */
    fun copiarColor(ctx: Context, a: Ajustes, que: String): Long {
        val c = colorEfectivo(a, que)
        when (que) { "fondo" -> Skin.fondoPropio = c; "texto" -> Skin.tintaPropia = c; else -> Skin.acentoPropio = c }
        Skin.guardar(ctx)
        return c
    }

    /** Copia a Marfil lo marcado en [ids]. Marfil deja de seguir los colores de Atrio en directo (ahora tiene los suyos, iguales). */
    fun aplicar(ctx: Context, a: Ajustes, ids: Set<String>) {
        val s = a.skin
        if ("tema" in ids && s.has("theme31")) Modo.eleccion = s.optInt("theme31").coerceIn(0, 2)
        if ("fondo" in ids) Skin.fondoId = s.optString("skin_fondo").takeIf { id -> FONDOS.any { it.id == id } } ?: ""
        if ("acento" in ids) Skin.accentId = s.optString("skin_accent_id").takeIf { id -> ACCENT_COLORS.any { it.id == id } } ?: "paleta"
        if ("c_fondo" in ids) Skin.fondoPropio = colorEfectivo(a, "fondo")
        if ("c_texto" in ids) Skin.tintaPropia = colorEfectivo(a, "texto")
        if ("c_acento" in ids) Skin.acentoPropio = colorEfectivo(a, "acento")
        if ("letras" in ids) { Skin.display = s.optString("skin_letra_titulos").ifBlank { "Atrio" }; Skin.body = s.optString("skin_letra_texto").ifBlank { "Atrio" } }
        if ("tamano" in ids && s.has("skin_escala")) Skin.escala = s.optInt("skin_escala", 100).coerceIn(80, 140)
        if (ids.any { it in setOf("fondo", "acento", "tema") }) { Skin.origenAtrio = false; Skin.invertido = false }
        Skin.guardar(ctx)
    }
}
