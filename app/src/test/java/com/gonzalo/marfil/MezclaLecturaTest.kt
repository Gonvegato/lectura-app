package com.gonzalo.marfil

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class MezclaLecturaTest {
    private fun nota(v: String, c: Long, texto: String = "t", nota: String = "") = JSONObject().put("value", v).put("created", c).put("text", texto).put("note", nota)
    private fun arr(vararg n: JSONObject) = JSONArray().also { a -> n.forEach { a.put(it) } }
    private fun dias(dev: String, ms: Long, dia: String = "2026-11-03") = JSONObject().put("dev", JSONObject().put(dev, JSONObject().put("total", ms).put("days", JSONObject().put(dia, ms))))

    @Test fun subrayadosDeLosDosSeJuntan() {
        val a = JSONObject(); MezclaLectura.actualizar(a, "notes", arr(nota("c1", 10)), 100)
        val b = JSONObject(); MezclaLectura.actualizar(b, "notes", arr(nota("c2", 20)), 200)
        val m = MezclaLectura.mezclar(a, b, "x")
        assertEquals(2, m.getJSONArray("notes").length())
        assertTrue("es conmutativa", MezclaLectura.igual(m, MezclaLectura.mezclar(b, a, "x")))
    }

    @Test fun quitarUnSubrayadoLoQuitaEnElOtro() {
        val a = JSONObject(); MezclaLectura.actualizar(a, "notes", arr(nota("c1", 10), nota("c2", 20)), 100)
        val b = JSONObject(a.toString())
        MezclaLectura.actualizar(a, "notes", arr(nota("c2", 20)), 500)          // en A se quita c1 a las 500
        val m = MezclaLectura.mezclar(a, b, "x")
        assertEquals(1, m.getJSONArray("notes").length())
        assertEquals("c2", m.getJSONArray("notes").getJSONObject(0).getString("value"))
    }

    @Test fun ponerloDeNuevoDespuesDelBorradoGana() {
        val a = JSONObject(); MezclaLectura.actualizar(a, "notes", arr(nota("c1", 10)), 100)
        val b = JSONObject(a.toString())
        MezclaLectura.actualizar(a, "notes", arr(), 300)                         // quitado a las 300
        MezclaLectura.actualizar(b, "notes", arr(nota("c1", 10, nota = "ahora con nota")), 900)   // editado en B después
        val m = MezclaLectura.mezclar(a, b, "x")
        assertEquals(1, m.getJSONArray("notes").length())
        assertEquals("ahora con nota", m.getJSONArray("notes").getJSONObject(0).getString("note"))
    }

    @Test fun laEdicionMasRecienteGana() {
        val a = JSONObject(); MezclaLectura.actualizar(a, "notes", arr(nota("c1", 10)), 100)
        val b = JSONObject(a.toString())
        MezclaLectura.actualizar(a, "notes", arr(nota("c1", 10, nota = "A")), 400)
        MezclaLectura.actualizar(b, "notes", arr(nota("c1", 10, nota = "B")), 700)
        assertEquals("B", MezclaLectura.mezclar(a, b, "x").getJSONArray("notes").getJSONObject(0).getString("note"))
        assertEquals("B", MezclaLectura.mezclar(b, a, "x").getJSONArray("notes").getJSONObject(0).getString("note"))
    }

    @Test fun guardarSinCambiosNoTocaLaHora() {
        val a = JSONObject(); MezclaLectura.actualizar(a, "notes", arr(nota("c1", 10)), 100)
        MezclaLectura.actualizar(a, "notes", arr(nota("c1", 10)), 9999)
        assertEquals(100L, a.getJSONArray("notes").getJSONObject(0).getLong("mod"))
    }

    @Test fun posicionLaMasRecienteYLoMasLejosElMayor() {
        val a = JSONObject().put("cfi", "A").put("fraction", 0.2).put("read", 100L).put("bestFrac", 0.6).put("bestCfi", "bA")
        val b = JSONObject().put("cfi", "B").put("fraction", 0.4).put("read", 300L).put("bestFrac", 0.4).put("bestCfi", "bB")
        val m = MezclaLectura.mezclar(a, b, "x")
        assertEquals("B", m.getString("cfi")); assertEquals(0.4, m.getDouble("fraction"), 1e-9)
        assertEquals("bA", m.getString("bestCfi")); assertEquals(0.6, m.getDouble("bestFrac"), 1e-9)
    }

    @Test fun elTiempoSeSumaPorDispositivo() {
        val a = JSONObject().put("stats", dias("movil", 10 * 60_000L))
        val b = JSONObject().put("stats", dias("tablet", 20 * 60_000L))
        val m = MezclaLectura.mezclar(a, b, "movil").getJSONObject("stats")
        assertEquals(30 * 60_000L, m.getLong("total"))
        assertEquals(30 * 60_000L, m.getJSONObject("days").getLong("2026-11-03"))
    }

    @Test fun losAjustesDelUltimoCambio() {
        val a = JSONObject().put("settings", JSONObject().put("size", 20)).put("settingsAt", 100L)
        val b = JSONObject().put("settings", JSONObject().put("size", 30)).put("settingsAt", 200L)
        assertEquals(30, MezclaLectura.mezclar(a, b, "x").getJSONObject("settings").getInt("size"))
        assertEquals(30, MezclaLectura.mezclar(b, a, "x").getJSONObject("settings").getInt("size"))
    }

    @Test fun mezclarDosVecesDaLoMismo() {
        val a = JSONObject().put("cfi", "A").put("read", 100L).put("stats", dias("m", 60_000L)); MezclaLectura.actualizar(a, "notes", arr(nota("c1", 10)), 100)
        val b = JSONObject().put("cfi", "B").put("read", 200L).put("stats", dias("t", 120_000L)); MezclaLectura.actualizar(b, "marks", arr(nota("m1", 30)), 200)
        val uno = MezclaLectura.mezclar(a, b, "m")
        assertTrue(MezclaLectura.igual(uno, MezclaLectura.mezclar(uno, b, "m")))
        assertTrue(MezclaLectura.igual(uno, MezclaLectura.mezclar(uno, a, "m")))
    }

    @Test fun elNombreDelLibroEnLaCuentaEsUnaClaveValida() {
        val k = MezclaLectura.kid("libro:el nombre de la rosa:umberto eco")
        assertTrue(k.matches(Regex("[0-9a-f]{20}")))
        assertEquals(k, MezclaLectura.kid("libro:el nombre de la rosa:umberto eco"))
    }
}
