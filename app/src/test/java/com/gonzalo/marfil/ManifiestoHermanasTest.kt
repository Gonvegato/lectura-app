package com.gonzalo.marfil

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Desde Android 11 una app solo «ve» a las demás si las declara en `<queries>` del manifiesto. Sin eso,
 * `getLaunchIntentForPackage` devuelve null y los enlaces a las hermanas («Escribir en Cobalto», «En Azabache») no salen nunca en un móvil de
 * verdad. Robolectric no filtra paquetes, así que ninguna otra prueba lo nota: esta mira el manifiesto tal cual.
 */
class ManifiestoHermanasTest {
    private fun manifiesto(): File {
        var d: File? = File("").absoluteFile
        while (d != null) {
            for (r in listOf("src/main/AndroidManifest.xml", "app/src/main/AndroidManifest.xml")) File(d, r).takeIf { it.exists() }?.let { return it }
            d = d.parentFile
        }
        error("no encuentro el manifiesto")
    }

    @Test
    fun declaraALasHermanas() {
        val m = manifiesto().readText()
        val queries = m.substringAfter("<queries>", "").substringBefore("</queries>")
        for (p in listOf("com.gonzalo.hiedra", "com.gonzalo.cobalto", "com.gonzalo.gamerate")) assertTrue("falta <package android:name=\"$p\"/> en <queries>", Regex("<package\\s+android:name=\"" + Regex.escape(p) + "\"").containsMatchIn(queries))
    }
}
