package com.gonzalo.marfil

import androidx.test.core.app.ApplicationProvider
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Importar de Atrio de principio a fin (sin red): fichas, lo leído, el tiempo y que repetirlo no duplique. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ImportarEnLaAppTest {
    private val ctx get() = ApplicationProvider.getApplicationContext<android.content.Context>()

    private fun blob(): JSONObject {
        val estado = JSONObject().put("cfi", "epubcfi(/6/4)").put("fraction", 0.5).put("bestFrac", 0.6).put("read", 5000L).put("uri", "content://x").put("name", "x.epub")
            .put("notes", JSONArray().put(JSONObject().put("value", "c1").put("created", 10L).put("text", "hola")))
            .put("stats", JSONObject().put("dev", JSONObject().put("tablet-atrio", JSONObject().put("total", 3_600_000L).put("days", JSONObject().put("2026-10-01", 3_600_000L)))))
        return JSONObject()
            .put("bplays", JSONArray().put(JSONObject().put("id", 7L).put("name", "Ubik").put("creator", "Philip K. Dick").put("cover", "").put("status", 1).put("updated", 10L)))
            .put("lectura", JSONArray().put(JSONObject().put("id", 7L).put("updated", 9L).put("s", estado)))
            .put("nube", JSONArray().put(JSONObject().put("k", "libro:7").put("f", 555L).put("n", "Ubik.epub").put("s", 10L).put("updated", 1L)))
    }

    @Test fun importaFichaLeidoYTiempoYNoDuplica() {
        Skin.cargar(ctx); Cuenta.cargar(ctx); Biblioteca.cargar(ctx); Biblioteca.libros.clear(); Biblioteca.borrados.clear()
        val c = ImportarAtrio.candidatos(listOf(blob()))
        val r = ImportarAtrio.importar(ctx, c)
        assertEquals(1, r.nuevos); assertEquals(1, r.conArchivo)
        val l = Biblioteca.libros.single()
        assertEquals("Ubik", l.titulo); assertTrue("sin archivo aquí: se bajará de kDrive", l.enOtroDispositivo)
        assertEquals(Estado.LEYENDO, l.estado); assertEquals(0.6f, l.fraccion, 0.001f)
        assertEquals("el tiempo de Atrio se ve sumado", 3_600_000L, l.msLeidos)
        val st = EstadoLectura.estado(ctx, l.id)
        assertEquals(1, st.getJSONArray("notes").length()); assertFalse(st.has("uri"))
        assertEquals(555L, NubeLibros.de(ctx, l)!!.f)
        // Repetir no duplica
        val r2 = ImportarAtrio.importar(ctx, ImportarAtrio.candidatos(listOf(blob())))
        assertEquals(0, r2.nuevos); assertEquals(1, r2.yaEstaban)
        assertEquals(1, Biblioteca.libros.size); assertEquals(3_600_000L, Biblioteca.libros.single().msLeidos)
        assertEquals(1, EstadoLectura.estado(ctx, l.id).getJSONArray("notes").length())
    }
}
