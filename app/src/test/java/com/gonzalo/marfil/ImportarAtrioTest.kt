package com.gonzalo.marfil

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ImportarAtrioTest {
    private fun arr(vararg o: JSONObject) = JSONArray().also { a -> o.forEach { a.put(it) } }
    private fun valorado(id: Long, nombre: String, autor: String, fav: Boolean = false, upd: Long = 100) =
        JSONObject().put("id", id).put("name", nombre).put("creator", autor).put("cover", "https://x/c$id.jpg").put("fav", fav).put("start", 1000L).put("end", 2000L).put("updated", upd)
    private fun enCurso(id: Long, nombre: String, autor: String, status: Int, upd: Long = 100) =
        JSONObject().put("id", id).put("name", nombre).put("creator", autor).put("cover", "").put("status", status).put("start", 500L).put("updated", upd)
    private fun lectura(id: Long, fr: Double, best: Double = fr) =
        JSONObject().put("id", id).put("updated", 50L).put("s", JSONObject().put("fraction", fr).put("bestFrac", best).put("cfi", "epubcfi(/6/2)").put("read", 777L))
    private fun nube(id: Long) = JSONObject().put("k", "libro:$id").put("f", 4242L + id).put("n", "Libro $id.epub").put("s", 999L).put("updated", 10L)

    @Test fun estadosYDatosDeLaFicha() {
        val blob = JSONObject()
            .put("books", arr(valorado(1, "Dune", "Frank Herbert", fav = true), valorado(2, "1984", "George Orwell")))
            .put("bplays", arr(enCurso(3, "Ubik", "Philip K. Dick", 1), enCurso(4, "Neuromante", "William Gibson", 0), enCurso(5, "Solaris", "Stanislaw Lem", 2)))
            .put("lectura", arr(lectura(3, 0.42), lectura(1, 1.0)))
            .put("nube", arr(nube(1), nube(3)))
        val c = ImportarAtrio.candidatos(listOf(blob)).associateBy { it.libro.titulo }
        assertEquals(5, c.size)
        assertEquals(Estado.LEIDO, c["Dune"]!!.libro.estado); assertTrue(c["Dune"]!!.libro.favorito); assertEquals(1f, c["Dune"]!!.libro.fraccion, 0f)
        assertEquals("https://x/c1.jpg", c["Dune"]!!.libro.portada)
        assertEquals(Estado.LEYENDO, c["Ubik"]!!.libro.estado); assertEquals(0.42f, c["Ubik"]!!.libro.fraccion, 0.001f)
        assertEquals(Estado.PENDIENTE, c["Neuromante"]!!.libro.estado)
        assertEquals(Estado.PENDIENTE, c["Solaris"]!!.libro.estado); assertTrue("el abandonado se avisa", c["Solaris"]!!.abandonado)
        assertEquals(4243L, c["Dune"]!!.nube!!.f); assertEquals("Libro 3.epub", c["Ubik"]!!.nube!!.n)
        assertNull(c["1984"]!!.nube)
        assertEquals(c["Ubik"]!!.libro.autor, "Philip K. Dick")
        assertEquals(claveDe("Dune", "Frank Herbert"), c["Dune"]!!.libro.clave)
    }

    @Test fun losBorradosEnAtrioNoVienen() {
        val blob = JSONObject().put("books", arr(valorado(1, "Dune", "FH", upd = 100), valorado(2, "1984", "GO", upd = 100)))
            .put("tombs", JSONObject().put("books:2", 500L))
        val l = ImportarAtrio.candidatos(listOf(blob)).map { it.libro.titulo }
        assertEquals(listOf("Dune"), l)
    }

    @Test fun siSeVolvioAEditarDespuesDelBorradoSiVieneYLaVersionNuevaGana() {
        val a = JSONObject().put("books", arr(valorado(2, "1984", "GO", upd = 100))).put("tombs", JSONObject().put("books:2", 300L))
        val b = JSONObject().put("books", arr(valorado(2, "1984 (ed.)", "GO", upd = 900)))
        val l = ImportarAtrio.candidatos(listOf(a, b))
        assertEquals(1, l.size); assertEquals("1984 (ed.)", l[0].libro.titulo)
    }

    @Test fun dosDispositivosSeUnenPorId() {
        val a = JSONObject().put("bplays", arr(enCurso(3, "Ubik", "PKD", 0, upd = 100)))
        val b = JSONObject().put("bplays", arr(enCurso(3, "Ubik", "PKD", 1, upd = 200))).put("lectura", arr(lectura(3, 0.7)))
        val l = ImportarAtrio.candidatos(listOf(a, b))
        assertEquals(1, l.size); assertEquals(Estado.LEYENDO, l[0].libro.estado)
    }

    @Test fun sinNombreSeSalta() {
        val blob = JSONObject().put("books", arr(JSONObject().put("id", 9L).put("name", "  ").put("updated", 1L)))
        assertTrue(ImportarAtrio.candidatos(listOf(blob)).isEmpty())
    }

    @Test fun elIndiceDeKDriveSeGuardaYSeLee() {
        val x = NubeLibro(12345L, "Dune.epub", 2048L)
        val y = NubeLibros.deJson(NubeLibros.aJson(x))!!
        assertEquals(12345L, y.f); assertEquals("Dune.epub", y.n); assertEquals(2048L, y.s)
        assertNull(NubeLibros.deJson(JSONObject().put("f", 0L)))
        assertNull(NubeLibros.deJson(null))
    }
}
