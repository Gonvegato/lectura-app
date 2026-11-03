package com.gonzalo.marfil

import org.junit.Assert.*
import org.junit.Test

/** Las reglas de mezcla entre tus dispositivos, sin móvil ni red. */
class MezclaTest {
    private var n = 0
    private fun libro(t: String, a: String = "Autor", mod: Long = 100, msp: Long = 0, fr: Float = 0f, fav: Boolean = false, estado: Estado = Estado.PENDIENTE, uri: String = "file:///x/$t.epub", ultima: Long = 0) =
        Libro("l${n++}", t, a, uri = uri, archivo = "$t.epub", mod = mod, msPropios = msp, fraccion = fr, favorito = fav, estado = estado, anadido = 50, ultima = ultima)

    private fun blobDe(dev: String, ls: List<Libro>, bor: Map<String, Long> = emptyMap()) = Mezcla.deJson(Mezcla.aJson(Mezcla.aBlob(dev, ls, bor)))!!
    private fun mezclar(l: List<Libro>, r: List<Blob>, b: Map<String, Long> = emptyMap(), ahora: Long = 1_000) = Mezcla.mezclar(l, b, r, "A", ahora) { "nuevo${n++}" }

    @Test fun laClaveNoDependeDeMayusculasNiTildes() {
        assertEquals(libro("El nombre de la rosa", "Umberto Eco").clave, libro("EL NOMBRE DE LA ROSA", "umberto  eco").clave)
        assertEquals(claveDe("Pokémon", "Ñu"), claveDe("pokemon", "nu"))
    }

    @Test fun unLibroDeOtroDispositivoSeCreaSinArchivo() {
        val r = mezclar(emptyList(), listOf(blobDe("B", listOf(libro("Dune", msp = 5_000, fr = 0.4f, estado = Estado.LEYENDO)))))
        assertTrue(r.cambio); assertEquals(1, r.libros.size)
        val l = r.libros[0]
        assertTrue(l.enOtroDispositivo); assertEquals(5_000L, l.msLeidos); assertEquals(0L, l.msPropios); assertEquals(0.4f, l.fraccion, 0.001f); assertEquals(Estado.LEYENDO, l.estado)
    }

    @Test fun elTiempoSeSumaYLoLeidoEsLoMasLejano() {
        val mio = libro("Dune", msp = 3_000, fr = 0.2f, ultima = 10)
        val r = mezclar(listOf(mio), listOf(blobDe("B", listOf(libro("Dune", msp = 7_000, fr = 0.6f, ultima = 90)))))
        assertEquals(10_000L, r.libros[0].msLeidos); assertEquals(3_000L, r.libros[0].msPropios)
        assertEquals("el punto más lejano", 0.6f, r.libros[0].fraccion, 0.001f); assertEquals(90L, r.libros[0].ultima)
        // Mezclar otra vez con lo mismo no suma dos veces
        val r2 = mezclar(r.libros, listOf(blobDe("B", listOf(libro("Dune", msp = 7_000, fr = 0.6f, ultima = 90)))))
        assertFalse(r2.cambio); assertEquals(10_000L, r2.libros[0].msLeidos)
    }

    @Test fun ganaElCambioMasRecienteDeLaFicha() {
        val l = libro("Dune", mod = 100)
        // En el otro dispositivo se editó el autor: la clave (la de cuando se creó) no cambia, así que es el MISMO libro
        val editado = libro("Dune", "Frank Herbert", mod = 200, fav = true, estado = Estado.LEIDO).copy(clave = l.clave)
        val r = mezclar(listOf(l), listOf(blobDe("B", listOf(editado))))
        assertEquals(1, r.libros.size); assertEquals("Frank Herbert", r.libros[0].autor); assertTrue(r.libros[0].favorito); assertEquals(Estado.LEIDO, r.libros[0].estado)
        assertEquals("la clave sigue siendo la de siempre", l.clave, r.libros[0].clave)
        val r2 = mezclar(listOf(l.copy(mod = 300)), listOf(blobDe("B", listOf(editado.copy(favorito = true)))))
        assertEquals("Autor", r2.libros[0].autor); assertFalse(r2.libros[0].favorito)
    }

    @Test fun elBorradoSePropagaSalvoQueSeCambieDespues() {
        val l = libro("Dune", mod = 100)
        assertTrue(mezclar(listOf(l), listOf(blobDe("B", emptyList(), mapOf(l.clave to 500L)))).libros.isEmpty())
        assertEquals(1, mezclar(listOf(l.copy(mod = 800)), listOf(blobDe("B", emptyList(), mapOf(l.clave to 500L)))).libros.size)
        assertTrue(mezclar(emptyList(), listOf(blobDe("C", listOf(libro("Dune", mod = 100)))), mapOf(l.clave to 500L)).libros.isEmpty())
    }

    @Test fun losPropiosBlobsSeIgnoranYLosBorradosViejosSeOlvidan() {
        assertTrue(mezclar(emptyList(), listOf(blobDe("A", listOf(libro("Dune"))))).libros.isEmpty())
        assertTrue(mezclar(emptyList(), emptyList(), mapOf("libro:x:y" to 1L), ahora = Mezcla.OLVIDO + 10).borrados.isEmpty())
    }

    @Test fun laAparienciaViajaEnLaCopia() {
        val aj = """{"at":50,"skin":{"fondoId":"granate"}}"""
        assertEquals(50L, org.json.JSONObject(Mezcla.deJson(Mezcla.aJson(Mezcla.aBlob("B", emptyList(), emptyMap(), aj)))!!.ajustes).getLong("at"))
        assertEquals("", Mezcla.deJson(Mezcla.aJson(Mezcla.aBlob("B", emptyList(), emptyMap())))!!.ajustes)
        assertNull(Mezcla.deJson("{no es json"))
    }

    @Test fun editarElTituloNoCambiaLaClave() {
        val l = libro("Dune"); assertEquals(l.clave, l.copy(titulo = "Dune (edición de bolsillo)", autor = "F. Herbert").clave)
        val o = Biblioteca.aLibro(Biblioteca.aJson(l.copy(titulo = "Otro"))); assertEquals(l.clave, o.clave)
    }

    @Test fun deArchivoATituloYAutor() {
        assertEquals("El nombre de la rosa" to "Umberto Eco", tituloYAutorDeArchivo("El nombre de la rosa - Umberto Eco.epub"))
        assertEquals("Dune" to "", tituloYAutorDeArchivo("Dune.epub")); assertEquals("Mi libro viejo" to "", tituloYAutorDeArchivo("Mi_libro_viejo.pdf"))
    }
}
