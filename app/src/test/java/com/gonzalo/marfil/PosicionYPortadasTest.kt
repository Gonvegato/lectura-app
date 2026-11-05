package com.gonzalo.marfil

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/**
 * Lo que arregla la 0.12.18: «voy por aquí» tras un salto largo baja lo leído (también en tus otros dispositivos), y las portadas no se
 * pierden al mezclar la biblioteca mientras se bajan.
 */
class PosicionYPortadasTest {
    @Test fun unSaltoLargoEsUnCuartoDelLibro() {
        assertTrue(Salto.hay(0.98, 0.02)); assertTrue(Salto.hay(0.02, 0.98)); assertTrue(Salto.hay(0.10, 0.35))
        assertFalse(Salto.hay(0.40, 0.45)); assertFalse(Salto.hay(0.10, 0.34))
    }

    @Test fun laMarcaAManoMandaSobreLoMasLejos() {
        // Sin marcas: lo más lejano
        assertEquals(0L to 0.98f, Mezcla.fraccionDe(listOf(0L to 0.98f, 0L to 0.4f)))
        // Marcaste el 2 % en un dispositivo: manda aunque el otro llegara al 98 %
        assertEquals(500L to 0.02f, Mezcla.fraccionDe(listOf(0L to 0.98f, 500L to 0.02f)))
        // Y luego sigues leyendo desde ahí (la misma marca): lo más lejano entre los de esa marca
        assertEquals(500L to 0.10f, Mezcla.fraccionDe(listOf(500L to 0.02f, 500L to 0.10f, 0L to 0.98f)))
    }

    @Test fun laMarcaViajaEnLaBiblioteca() {
        val l = Libro("l1", "Dune", "Frank Herbert", fraccion = 0.02f, fraccionFijada = 777L, mod = 1)
        assertEquals(777L, Biblioteca.aLibro(Biblioteca.aJson(l)).fraccionFijada)
        val b = Mezcla.deJson(Mezcla.aJson(Mezcla.aBlob("A", listOf(l), emptyMap())))!!
        assertEquals(777L, b.libros.single().ff)
        // En otro dispositivo que llegó al 98 %: la mezcla deja el 2 % marcado
        val aqui = Libro("x", "Dune", "Frank Herbert", fraccion = 0.98f, mod = 1)
        val r = Mezcla.mezclar(listOf(aqui), emptyMap(), listOf(b), "B")
        assertEquals(0.02f, r.libros.single().fraccion, 0.0001f); assertEquals(777L, r.libros.single().fraccionFijada)
    }

    @Test fun elEstadoDelLectorRespetaLaMarca() {
        val lejos = JSONObject().put("read", 10L).put("bestFrac", 0.98).put("bestCfi", "fin").put("bestLabel", "Epílogo")
        val marcado = JSONObject().put("read", 5L).put("bestFrac", 0.02).put("bestCfi", "inicio").put("bestLabel", "Uno").put("bestSet", 900L)
        for (m in listOf(MezclaLectura.mezclar(lejos, marcado, "A"), MezclaLectura.mezclar(marcado, lejos, "A"))) {
            assertEquals(0.02, m.optDouble("bestFrac"), 1e-9); assertEquals("inicio", m.optString("bestCfi")); assertEquals(900L, m.optLong("bestSet"))
        }
        // Sin marcas, como siempre: lo más lejos
        val a = JSONObject().put("bestFrac", 0.3).put("bestCfi", "a"); val b = JSONObject().put("bestFrac", 0.6).put("bestCfi", "b")
        assertEquals("b", MezclaLectura.mezclar(a, b, "A").optString("bestCfi"))
    }

    @Test fun mezclarNoQuitaLaPortadaQueLlegoMientras() {
        Biblioteca.libros.clear()
        val sin = Libro("l1", "Nada", "Carmen Laforet", mod = 1)
        Biblioteca.libros.add(sin)
        val mezcla = listOf(Biblioteca.libros.single().copy(titulo = "Nada", mod = 2))   // la mezcla, hecha con la copia de antes
        // Mientras se mezclaba, se bajó su portada (y su archivo)
        Biblioteca.libros[0] = sin.copy(portada = "/files/portadas/l1-5.jpg", uri = "file:///libros/l1.epub")
        Biblioteca.aplicarMezcla(mezcla, emptyMap())
        val l = Biblioteca.por("l1")!!
        assertEquals("/files/portadas/l1-5.jpg", l.portada); assertEquals("file:///libros/l1.epub", l.uri); assertEquals(2L, l.mod)
        Biblioteca.libros.clear()
    }

    @Test fun laPortadaGuardadaDeUnLibro() {
        val fs = listOf("l1-100.jpg", "l1-300.jpg", "l10-999.jpg", "l1.jpg", "otro.jpg").map { File("/p", it) }
        assertEquals("l1-300.jpg", Portadas.archivoDe("l1", fs)?.name)
        assertEquals("l10-999.jpg", Portadas.archivoDe("l10", fs)?.name)
        assertNull(Portadas.archivoDe("l2", fs))
        assertEquals("l3.jpg", Portadas.archivoDe("l3", listOf(File("/p", "l3.jpg")))?.name)
    }
}
