package com.gonzalo.marfil

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/**
 * El contrato con Azabache (docs/CONTRATO-DATOS.md), de verdad: lo que Marfil sube a la cuenta, tal cual lo escribe, se deja en
 * `build/outputs/contrato/marfil.json`. Azabache lo lee con su prueba `ContratoHermanasTest` (copia en `herramientas/casos/hermanas/`).
 */
class ContratoTest {
    @Test fun loQueSubeMarfil() {
        val dune = Libro("l1", "Dune", "Frank Herbert", uri = "file:///x/dune.epub", archivo = "dune.epub", mod = 100, msPropios = 7_200_000L, fraccion = 0.8f, favorito = true,
            estado = Estado.LEYENDO, anadido = 50, ultima = 1_700_000_000_000L, paginas = 608, paginasOrigen = "openlibrary", portadaUrl = "https://covers.openlibrary.org/b/id/1-L.jpg")
        val nada = Libro("l2", "Nada", "Carmen Laforet", archivo = "nada.epub", mod = 100, msPropios = 3_600_000L, fraccion = 1f, estado = Estado.LEIDO, anadido = 60, terminados = listOf(1_700_000_000_000L))
        val data = Mezcla.aJson(Mezcla.aBlob("marfil-A", listOf(dune, nada), mapOf("libro:viejo:nadie" to 10L)))
        val kid = MezclaLectura.kid(dune.clave)
        // El estado de lectura de Dune en este dispositivo: tiempo por día y dos subrayados
        val st = JSONObject()
            .put("stats", JSONObject().put("dev", JSONObject().put("marfil-A", JSONObject().put("total", 7_200_000L).put("days", JSONObject().put("2026-01-01", 1_000L).put("2026-01-02", 3_600_000L)))))
            .put("notes", JSONArray()
                .put(JSONObject().put("value", "epubcfi(/6/4!/4/2)").put("text", "El miedo mata la mente.").put("note", "Letanía").put("label", "Capítulo 1").put("created", 100L).put("frac", 0.1))
                .put(JSONObject().put("value", "epubcfi(/6/8!/4/2)").put("text", "Quien controla la especia controla el universo.").put("note", "").put("label", "Capítulo 11").put("created", 200L).put("frac", 0.55)))
        val tiempo = JSONObject().put(kid, JSONObject().put("marfil-A", JSONObject().put("t", dune.titulo).put("a", dune.autor).put("dev", st.getJSONObject("stats").getJSONObject("dev"))))
        val subr = JSONObject().put(kid, JSONObject().put("marfil-A", subrayadosLigeros(dune.titulo, dune.autor, st)!!))
        val out = JSONObject().put("app", "marfil").put("version", VERSION).put("claveDune", dune.clave).put("kidDune", kid)
            .put("dev", JSONObject().put("marfil-A", JSONObject().put("data", data)))
            .put("tiempo", tiempo).put("subrayados", subr)
            .put("objetivos", Objetivos.Valor(30, 24, 2026, 1_700_000_000_000L).aJson())
        assertEquals("https://covers.openlibrary.org/b/id/1-L.jpg", JSONObject(data).getJSONArray("libros").getJSONObject(0).getString("pu"))
        val f = File("build/outputs/contrato/marfil.json").apply { parentFile.mkdirs() }
        f.writeText(out.toString(2))
        assertTrue(f.length() > 100)
    }
}
