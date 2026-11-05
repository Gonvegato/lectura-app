package com.gonzalo.marfil

import android.content.Context
import org.json.JSONObject
import org.json.JSONTokener
import java.time.LocalDate

/**
 * Tus objetivos de lectura: los minutos al día y los libros del año. Los fija Marfil, que es quien lee; viajan con tu cuenta en
 * `/users/<uid>/marfil/objetivos` (`{dia, ano, anio, mod}`) a tus otros Marfil y a Azabache, que los enseña sin cambiarlos.
 * Manda el que se cambió más tarde (`mod`).
 *
 * La primera vez, si Marfil no tiene ninguno y en tu cuenta no hay ninguno, se traen una vez los que tenías puestos en Azabache.
 */
object Objetivos {
    data class Valor(val dia: Int, val ano: Int, val anio: Int, val mod: Long) {
        fun aJson(): JSONObject = JSONObject().put("dia", dia).put("ano", ano).put("anio", anio).put("mod", mod)
        val vacio: Boolean get() = dia <= 0 && ano <= 0
        companion object {
            fun deJson(o: JSONObject?): Valor? = o?.takeIf { it.has("mod") }?.let {
                Valor(it.optInt("dia").coerceIn(0, 600), it.optInt("ano").coerceIn(0, 999), it.optInt("anio"), it.optLong("mod"))
            }
        }
    }

    /** Qué hacer al comparar lo de aquí con lo de la cuenta. */
    enum class Paso { NADA, SUBIR, ADOPTAR, IMPORTAR }

    /** La regla (pura): manda el más reciente; si no hay nada en ningún sitio, se mira una vez lo de Azabache. */
    fun decidir(local: Valor, nube: Valor?, yaImportado: Boolean): Paso = when {
        nube == null && local.mod == 0L && local.vacio -> if (yaImportado) Paso.NADA else Paso.IMPORTAR
        nube == null -> Paso.SUBIR
        nube.mod > local.mod -> Paso.ADOPTAR
        local.mod > nube.mod -> Paso.SUBIR
        else -> Paso.NADA
    }

    private fun p(c: Context) = c.getSharedPreferences("marfil_ajustes", Context.MODE_PRIVATE)

    fun local(c: Context): Valor = p(c).let { Valor(it.getInt("meta_lectura", 0), it.getInt("meta_anual", 0), it.getInt("objetivos_anio", LocalDate.now().year), it.getLong("objetivos_mod", 0L)) }

    private fun escribir(c: Context, v: Valor) {
        p(c).edit().putInt("meta_lectura", v.dia).putInt("meta_anual", v.ano).putInt("objetivos_anio", v.anio).putLong("objetivos_mod", v.mod).apply()
    }

    /** Has cambiado un objetivo aquí: se apunta la hora y se sube. */
    fun cambiado(c: Context, dia: Int, ano: Int) {
        escribir(c, Valor(dia.coerceIn(0, 600), ano.coerceIn(0, 999), LocalDate.now().year, System.currentTimeMillis()))
        val app = c.applicationContext
        // Se sube y se sella «stamps» de este dispositivo: tus otros Marfil y Azabache lo oyen y lo leen
        if (Cuenta.vinculada) kotlin.concurrent.thread {
            runCatching { sincronizar(app); Cuenta.db("PUT", "${Cuenta.raiz}/stamps/${Cuenta.dispositivo}", "{\".sv\":\"timestamp\"}", "print=silent") }
        }
    }

    /** Compara con la cuenta y deja los dos iguales. Bloqueante (red). Devuelve true si ha cambiado algo aquí. */
    fun sincronizar(c: Context): Boolean {
        if (!Cuenta.vinculada) return false
        val raw = Cuenta.db("GET", "${Cuenta.raiz}/objetivos", null)
        val nube = raw?.takeIf { it != "null" }?.let { runCatching { Valor.deJson(JSONObject(it)) }.getOrNull() }
        val local = local(c)
        return when (decidir(local, nube, p(c).getBoolean("objetivos_importados", false))) {
            Paso.NADA -> false
            Paso.SUBIR -> { Cuenta.db("PUT", "${Cuenta.raiz}/objetivos", local.aJson().toString(), "print=silent"); false }
            Paso.ADOPTAR -> { escribir(c, nube!!); true }
            Paso.IMPORTAR -> {
                p(c).edit().putBoolean("objetivos_importados", true).apply()
                val az = deAzabache() ?: return false
                if (az.vacio) return false
                escribir(c, az)
                Cuenta.db("PUT", "${Cuenta.raiz}/objetivos", az.aJson().toString(), "print=silent")
                true
            }
        }
    }

    /**
     * Los objetivos que tenías en Azabache (lo más reciente que subió a tu cuenta: `dev/<dispositivo>/data` → `goals.b`, los libros del
     * año, y `ajustes.skin.meta_lectura`, los minutos al día). Solo se lee.
     */
    private fun deAzabache(): Valor? {
        val st = Cuenta.db("GET", "/users/${Cuenta.uid}/stamps", null)?.let { runCatching { JSONObject(it) }.getOrNull() } ?: return null
        val devs = st.keys().asSequence().map { it to st.optLong(it) }.sortedByDescending { it.second }.take(4).toList()
        for ((dev, _) in devs) {
            val raw = Cuenta.db("GET", "/users/${Cuenta.uid}/dev/$dev/data", null) ?: continue
            val datos = (runCatching { JSONTokener(raw).nextValue() }.getOrNull() as? String) ?: continue
            val o = runCatching { JSONObject(datos) }.getOrNull() ?: continue
            return deDatosAzabache(o)
        }
        return null
    }

    /** De los datos de Azabache, sus objetivos de lectura (puro, se prueba). */
    fun deDatosAzabache(o: JSONObject, ahora: Long = System.currentTimeMillis()): Valor? {
        val libros = o.optJSONObject("goals")?.optInt("b", 0) ?: 0
        val dia = o.optJSONObject("ajustes")?.optJSONObject("skin")?.optInt("meta_lectura", 0) ?: 0
        if (libros <= 0 && dia <= 0) return null
        return Valor(dia.coerceIn(0, 600), libros.coerceIn(0, 999), LocalDate.now().year, ahora)
    }
}
