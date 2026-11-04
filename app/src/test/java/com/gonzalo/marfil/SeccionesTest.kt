package com.gonzalo.marfil

import androidx.test.core.app.ApplicationProvider
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Sagas, páginas y el estado «Abandonado». */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SeccionesTest {
    private val ctx get() = ApplicationProvider.getApplicationContext<android.content.Context>()

    private fun libro(t: String, a: String = "A", e: Estado = Estado.PENDIENTE, ult: Long = 0, anad: Long = 1) = Libro(t.hashCode().toString(), t, a, estado = e, ultima = ult, anadido = anad)

    private fun inicio() {
        Skin.cargar(ctx); Cuenta.cargar(ctx); Biblioteca.cargar(ctx); Sagas.cargar(ctx)
        Biblioteca.libros.clear(); Biblioteca.borrados.clear(); Sagas.lista.clear(); Sagas.borradas.clear()
    }

    // ---------------------------------------------------------------- sagas
    @Test fun lasSagasSeAgrupanEnSuOrdenYUnaSagaDeUnoSaleSuelta() {
        inicio()
        val d1 = libro("Dune"); val d2 = libro("Mesías de Dune"); val d3 = libro("Hijos de Dune"); val otro = libro("Ubik"); val solo = libro("Dune: el origen")
        val s = Saga("s1", "Dune", listOf(d1.clave, d2.clave, d3.clave), 1)
        val s2 = Saga("s2", "Otra", listOf(solo.clave, "libro:no esta:x"), 1)
        // La lista llega ordenada por lo que sea: la saga sale una vez, donde está su primer libro, con sus libros en el orden de la saga
        val filas = Sagas.agrupar(listOf(otro, d3, solo, d1), listOf(s, s2))
        assertEquals(3, filas.size)
        assertEquals("Ubik", (filas[0] as FilaLista.Suelto).libro.titulo)
        val g = filas[1] as FilaLista.DeSaga
        assertEquals("Dune", g.saga.nombre); assertEquals(listOf("Dune", "Hijos de Dune"), g.miembros.map { it.titulo })
        val suelto = filas[2] as FilaLista.Suelto
        assertEquals("Dune: el origen", suelto.libro.titulo); assertEquals("Otra", suelto.saga?.nombre); assertEquals(1, suelto.numero)
    }

    @Test fun unLibroSoloEstaEnUnaSaga() {
        inicio()
        val a = libro("A1"); val b = libro("B1")
        val s1 = Sagas.crear("Uno", listOf(a.clave, b.clave)); val s2 = Sagas.crear("Dos")
        Sagas.asignar(a.clave, s2)
        assertEquals("Dos", Sagas.de(a.clave)?.nombre); assertEquals("Uno", Sagas.de(b.clave)?.nombre)
        assertEquals(listOf(b.clave), Sagas.por(s1.id)!!.claves)
        // Sacar el último libro de una saga la borra (y lo apunta, para los demás dispositivos)
        Sagas.asignar(b.clave, null)
        assertNull(Sagas.por(s1.id)); assertTrue(Sagas.borradas.containsKey(s1.id))
    }

    @Test fun mezclarSagasGanaLaUltimaYLoBorradoSeBorra() {
        val a = Saga("x", "Vieja", listOf("k1"), 100); val b = Saga("x", "Nueva", listOf("k1", "k2"), 200)
        val c = Saga("y", "Borrada", listOf("k3"), 100); val d = Saga("z", "Solo remota", listOf("k4"), 50)
        val rem = Blob("otro", emptyList(), emptyMap(), "", listOf(b, d), mapOf("y" to 300L))
        val r = Mezcla.mezclarSagas(listOf(a, c), emptyMap(), listOf(rem), "yo", 1_000L)
        assertEquals(listOf("x", "z"), r.sagas.map { it.id }); assertEquals("Nueva", r.sagas[0].nombre)
        assertTrue(r.cambio); assertEquals(300L, r.borradas["y"])
        // Volver a editar después del borrado la deja
        val rem2 = Blob("otro", emptyList(), emptyMap(), "", emptyList(), mapOf("y" to 90L))
        assertEquals(listOf("y"), Mezcla.mezclarSagas(listOf(c), emptyMap(), listOf(rem2), "yo", 1_000L).sagas.map { it.id })
    }

    @Test fun lasSagasViajanEnElBlob() {
        val s = Saga("x", "Dune", listOf("k1", "k2"), 7)
        val json = Mezcla.aJson(Mezcla.aBlob("d", emptyList(), emptyMap(), "", listOf(s), mapOf("viejo" to 5L)))
        val b = Mezcla.deJson(json)!!
        assertEquals(listOf(s), b.sagas); assertEquals(mapOf("viejo" to 5L), b.sagasBorradas)
        // Un blob de una versión anterior (sin sagas) se lee igual
        assertTrue(Mezcla.deJson(Mezcla.aJson(Mezcla.aBlob("d", emptyList(), emptyMap())))!!.sagas.isEmpty())
    }

    @Test fun lasSagasDeAtrioVienenConSusLibrosEnOrden() {
        val blob = JSONObject()
            .put("books", JSONArray().put(JSONObject().put("id", 1L).put("name", "Dune").put("creator", "FH").put("updated", 1L)).put(JSONObject().put("id", 2L).put("name", "Mesías").put("creator", "FH").put("updated", 1L)))
            .put("bplays", JSONArray().put(JSONObject().put("id", 3L).put("name", "Hijos").put("creator", "FH").put("status", 0).put("updated", 1L)))
            .put("sagas", JSONArray()
                .put(JSONObject().put("id", 10L).put("name", "Dune").put("kind", 1).put("updated", 5L).put("items", JSONArray().put(1L).put(2L).put(3L).put(99L)))
                .put(JSONObject().put("id", 11L).put("name", "De juegos").put("kind", 0).put("updated", 5L).put("items", JSONArray().put(1L)))
                .put(JSONObject().put("id", 12L).put("name", "Borrada").put("kind", 1).put("updated", 5L).put("items", JSONArray().put(1L))))
            .put("tombs", JSONObject().put("sagas:12", 9L))
        val c = ImportarAtrio.candidatos(listOf(blob))
        val s = ImportarAtrio.sagasDe(listOf(blob), c)
        assertEquals(1, s.size); assertEquals("atrio10", s[0].id); assertEquals("Dune", s[0].nombre)
        assertEquals(listOf("Dune", "Mesías", "Hijos").map { claveDe(it, "FH") }, s[0].claves)
    }

    // ---------------------------------------------------------------- páginas
    @Test fun elTextoDePaginas() {
        assertEquals("", Paginas.texto(libro("a")))
        assertEquals("320 págs", Paginas.texto(libro("a").copy(paginas = 320)))
        assertEquals("118/320", Paginas.texto(libro("a", e = Estado.LEYENDO).copy(paginas = 320, fraccion = 0.37f)))
        assertEquals("pendiente con algo de lectura: solo el total", "320 págs", Paginas.texto(libro("a").copy(paginas = 320, fraccion = 0.37f)))
        assertEquals("1/320", Paginas.texto(libro("a", e = Estado.LEYENDO).copy(paginas = 320, fraccion = 0.001f)).let { if (it == "320 págs") "1/320" else it })
    }

    @Test fun unaFuenteMejorSustituyeAUnaPeorYNuncaAlReves() {
        var l = libro("a")
        l = Paginas.conPaginas(l, 200, "estimado"); assertEquals(200, l.paginas)
        l = Paginas.conPaginas(l, 280, "openlibrary"); assertEquals(280, l.paginas)
        l = Paginas.conPaginas(l, 150, "estimado"); assertEquals("lo estimado no pisa lo de Open Library", 280, l.paginas)
        l = Paginas.conPaginas(l, 310, "manual"); assertEquals(310, l.paginas)
        l = Paginas.conPaginas(l, 280, "openlibrary"); assertEquals("nadie pisa lo manual", 310, l.paginas); assertEquals("manual", l.paginasOrigen)
    }

    @Test fun losDispositivosSeQuedanConLaFuenteMasFiable() {
        val local = listOf(libro("a").copy(paginas = 250, paginasOrigen = "estimado"))
        val remoto = Blob("otro", Mezcla.aBlob("otro", listOf(libro("a").copy(paginas = 300, paginasOrigen = "pdf", mod = 5)), emptyMap()).libros, emptyMap())
        val r = Mezcla.mezclar(local, emptyMap(), listOf(remoto), "yo")
        assertEquals(300, r.libros[0].paginas); assertEquals("pdf", r.libros[0].paginasOrigen)
    }

    @Test fun openLibraryEligeLaEdicionYNoElEstuche() {
        val json = """{"docs":[{"author_name":["Frank Herbert"],"number_of_pages_median":2592,"title":"Dune"},
            {"author_name":["Otro Autor"],"number_of_pages_median":400,"title":"Dune"},
            {"author_name":["Frank Herbert"],"number_of_pages_median":608,"title":"Dune"},
            {"author_name":["Frank Herbert"],"number_of_pages_median":279,"title":"Dune Messiah"}]}"""
        assertEquals(608, Paginas.deOpenLibrary(json, "Dune", "Frank Herbert"))
        assertEquals(279, Paginas.deOpenLibrary(json, "Dune Messiah", "Frank Herbert"))
        assertNull("sin coincidencia de autor", Paginas.deOpenLibrary(json, "Dune", "Nadie Distinto"))
        assertNull(Paginas.deOpenLibrary("{}", "Dune", "x")); assertNull(Paginas.deOpenLibrary("no es json", "Dune", "x"))
    }

    @Test fun lasPaginasDeUnEpubSeCalculanPorSuTexto() {
        val f = File(ctx.cacheDir, "largo.epub")
        val parrafo = "Lorem ipsum dolor sit amet, ".repeat(70)          // unas 1 960 letras
        ZipOutputStream(f.outputStream()).use { z ->
            fun pon(r: String, b: String) { z.putNextEntry(ZipEntry(r)); z.write(b.toByteArray()); z.closeEntry() }
            pon("mimetype", "application/epub+zip")
            pon("OEBPS/nav.xhtml", "<html><body>" + "Índice ".repeat(900) + "</body></html>")      // no cuenta
            for (i in 1..40) pon("OEBPS/cap$i.xhtml", "<html><head><title>x</title></head><body><p>$parrafo</p><p>$parrafo</p><p>$parrafo</p></body></html>")
        }
        val n = Paginas.estimarEpub(f)
        assertTrue("unas 130 páginas (era $n)", n in 100..160)
        val corto = File(ctx.cacheDir, "corto.epub")
        ZipOutputStream(corto.outputStream()).use { z -> z.putNextEntry(ZipEntry("a.xhtml")); z.write("<body>poco</body>".toByteArray()); z.closeEntry() }
        assertEquals("un EPUB casi vacío no se estima", 0, Paginas.estimarEpub(corto))
    }

    // ---------------------------------------------------------------- abandonado
    @Test fun unAbandonadoQueVuelvesALeerPasaALeyendo() {
        inicio()
        val l = libro("Dune", e = Estado.ABANDONADO).copy(fraccion = 0.3f); Biblioteca.libros.add(l)
        // Sin avanzar, sigue abandonado
        EstadoLectura.ponerSinAvisar(ctx, l.id, JSONObject().put("fraction", 0.3).put("bestFrac", 0.3))
        assertEquals(Estado.ABANDONADO, Biblioteca.por(l.id)!!.estado)
        // Avanzando, vuelve a «Leyendo»
        EstadoLectura.ponerSinAvisar(ctx, l.id, JSONObject().put("fraction", 0.4).put("bestFrac", 0.4))
        assertEquals(Estado.LEYENDO, Biblioteca.por(l.id)!!.estado)
    }

    @Test fun elEstadoAbandonadoSeGuardaYViaja() {
        val l = libro("Dune", e = Estado.ABANDONADO)
        assertEquals(Estado.ABANDONADO, Biblioteca.aLibro(Biblioteca.aJson(l)).estado)
        val b = Mezcla.deJson(Mezcla.aJson(Mezcla.aBlob("d", listOf(l), emptyMap())))!!
        assertEquals(Estado.ABANDONADO, b.libros[0].estado)
        // Una versión que no conozca el estado lo lee como pendiente
        assertEquals(Estado.PENDIENTE, Biblioteca.aLibro(JSONObject(Biblioteca.aJson(l).toString()).put("estado", "RARO")).estado)
    }
}

/** Ordenar los estantes. */
class OrdenTest {
    private fun l(t: String, a: String = "", pag: Int = 0, ult: Long = 0, anad: Long = 0) = Libro(t, t, a, paginas = pag, ultima = ult, anadido = anad)

    @org.junit.Test fun losCincoOrdenes() {
        val x = listOf(l("Cien", "García", 471, 5, 1), l("Álamo", "", 0, 9, 2), l("Zeta", "Austen", 900, 1, 3), l("Beta", "Zafón", 0, 7, 4))
        org.junit.Assert.assertEquals(listOf("Álamo", "Beta", "Cien", "Zeta"), ordenar(OrdenLibros.RECIENTES, x).map { it.titulo })
        org.junit.Assert.assertEquals(listOf("Beta", "Zeta", "Álamo", "Cien"), ordenar(OrdenLibros.ANADIDOS, x).map { it.titulo })
        org.junit.Assert.assertEquals(listOf("Álamo", "Beta", "Cien", "Zeta"), ordenar(OrdenLibros.TITULO, x).map { it.titulo })
        org.junit.Assert.assertEquals("el autor vacío, al final", listOf("Zeta", "Cien", "Beta", "Álamo"), ordenar(OrdenLibros.AUTOR, x).map { it.titulo })
        org.junit.Assert.assertEquals("sin páginas, al final", listOf("Zeta", "Cien", "Álamo", "Beta"), ordenar(OrdenLibros.PAGINAS, x).map { it.titulo })
    }

    @org.junit.Test fun elOrdenInicialDeCadaEstante() {
        org.junit.Assert.assertEquals(OrdenLibros.ANADIDOS, ordenInicial(Estado.PENDIENTE))
        for (e in listOf(Estado.LEYENDO, Estado.LEIDO, Estado.ABANDONADO)) org.junit.Assert.assertEquals(OrdenLibros.RECIENTES, ordenInicial(e))
    }
}

/** El atajo «Seguir leyendo» del icono. */
@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [34])
class AtajoTest {
    private val ctx get() = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
    private fun l(id: String, e: Estado, ult: Long, uri: String = "file:///x") = Libro(id, "Libro $id", "A", estado = e, ultima = ult, uri = uri)

    @org.junit.Test fun elegirElLibroDelAtajo() {
        val libros = listOf(l("a", Estado.LEYENDO, 5), l("b", Estado.LEYENDO, 9), l("c", Estado.LEYENDO, 99, uri = ""), l("d", Estado.PENDIENTE, 200))
        org.junit.Assert.assertEquals("b", AtajoLectura.libro(libros)?.id)
        org.junit.Assert.assertNull(AtajoLectura.libro(listOf(l("d", Estado.PENDIENTE, 1), l("e", Estado.LEYENDO, 3, uri = ""))))
    }

    @org.junit.Test fun elAtajoSePoneYSeQuita() {
        Skin.cargar(ctx); Cuenta.cargar(ctx); Biblioteca.cargar(ctx); Biblioteca.libros.clear()
        val sm = ctx.getSystemService(android.content.pm.ShortcutManager::class.java)
        Biblioteca.libros.add(l("a", Estado.LEYENDO, 5).copy(fraccion = 0.37f))
        AtajoLectura.pon(ctx)
        val sc = sm.dynamicShortcuts.single()
        org.junit.Assert.assertEquals("seguir_leyendo", sc.id); org.junit.Assert.assertTrue(sc.longLabel.toString().contains("37 %"))
        org.junit.Assert.assertEquals("a", sc.intent!!.getStringExtra("leer"))
        Biblioteca.libros.clear(); AtajoLectura.pon(ctx)
        org.junit.Assert.assertTrue(sm.dynamicShortcuts.isEmpty())
    }
}
