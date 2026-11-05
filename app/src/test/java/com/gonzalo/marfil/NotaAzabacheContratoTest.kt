package com.gonzalo.marfil

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** «En Azabache» con lo que Azabache sube DE VERDAD (`herramientas/casos/azabache.json`, generado por su `ContratoTest`). */
class NotaAzabacheContratoTest {
    private fun raiz(): File { var d: File? = File("").absoluteFile; while (d != null && !File(d, "herramientas/casos/azabache.json").exists()) d = d.parentFile; return d ?: error("no encuentro herramientas/casos") }

    @Test fun laNotaDeAzabacheSeLeeDeSuBlob() {
        val c = JSONObject(File(raiz(), "herramientas/casos/azabache.json").readText())
        val blobs = c.getJSONObject("dev").let { d -> d.keys().asSequence().map { JSONObject(d.getJSONObject(it).getString("data")) }.toList() }
        val notas = NotaAzabache.deBlobs(blobs)
        val n = notas[claveDe("Dune", "Frank Herbert")]!!
        assertEquals(89, n.general); assertEquals("La especia.", n.resena); assertEquals(c.getLong("idDune"), n.atrioId)
        assertEquals("solo los libros", 1, notas.size)
    }
}
