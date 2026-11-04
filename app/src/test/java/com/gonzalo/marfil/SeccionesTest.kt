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
        org.junit.Assert.assertEquals(OrdenLibros.TERMINADO, ordenInicial(Estado.LEIDO))
        for (e in listOf(Estado.LEYENDO, Estado.ABANDONADO)) org.junit.Assert.assertEquals(OrdenLibros.RECIENTES, ordenInicial(e))
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


/** Fecha de terminado y reto anual. */
@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [34])
class TerminadoTest {
    private val ctx get() = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
    private val H = 3600_000L
    private val DIA = 24 * H

    private fun inicio() { Skin.cargar(ctx); Cuenta.cargar(ctx); Biblioteca.cargar(ctx); Biblioteca.libros.clear(); Biblioteca.borrados.clear() }

    @org.junit.Test fun alPasarALeidoSeApuntaLaFechaUnaVez() {
        inicio()
        val l = Libro("a", "Dune", "FH", estado = Estado.LEYENDO).also { Biblioteca.libros.add(it) }
        val antes = System.currentTimeMillis()
        Biblioteca.poner(l.copy(estado = Estado.LEIDO))
        val t = Biblioteca.por("a")!!
        org.junit.Assert.assertEquals(1, t.terminados.size); org.junit.Assert.assertTrue(t.terminado >= antes)
        // Guardar otra vez, ya leído: no se duplica
        Biblioteca.poner(t.copy(favorito = true)); org.junit.Assert.assertEquals(1, Biblioteca.por("a")!!.terminados.size)
        // Lo desmarcas y lo vuelves a marcar enseguida (un despiste): sigue siendo una sola vez
        Biblioteca.poner(Biblioteca.por("a")!!.copy(estado = Estado.LEYENDO)); Biblioteca.poner(Biblioteca.por("a")!!.copy(estado = Estado.LEIDO))
        org.junit.Assert.assertEquals(1, Biblioteca.por("a")!!.terminados.size)
    }

    @org.junit.Test fun unaRelecturaDespuesDeMuchoTiempoEsLaSegundaVez() {
        inicio()
        val hace = System.currentTimeMillis() - 30 * DIA
        val l = Libro("a", "Dune", "FH", estado = Estado.LEYENDO, terminados = listOf(hace)).also { Biblioteca.libros.add(it) }
        Biblioteca.poner(l.copy(estado = Estado.LEIDO))
        org.junit.Assert.assertEquals(2, Biblioteca.por("a")!!.terminados.size)
    }

    @org.junit.Test fun mezclarNoApuntaFechasNuevas() {
        inicio()
        val l = Libro("a", "Dune", "FH", estado = Estado.LEYENDO).also { Biblioteca.libros.add(it) }
        Biblioteca.aplicarMezcla(listOf(l.copy(estado = Estado.LEIDO)), emptyMap())
        org.junit.Assert.assertTrue(Biblioteca.por("a")!!.terminados.isEmpty())
    }

    @org.junit.Test fun juntarFechasQuitaLasQueEstanAMenosDe12Horas() {
        org.junit.Assert.assertEquals(listOf(1000L, 1000L + 2 * DIA), juntarFechas(listOf(1000L + 2 * DIA), listOf(1000L, 1000L + 3 * H)))
        org.junit.Assert.assertEquals(emptyList<Long>(), juntarFechas(emptyList(), listOf(0L)))
    }

    @org.junit.Test fun losDispositivosJuntanSusFechas() {
        val a = Libro("a", "Dune", "FH", estado = Estado.LEIDO, terminados = listOf(10 * DIA))
        val remoto = Blob("otro", Mezcla.aBlob("otro", listOf(Libro("x", "Dune", "FH", estado = Estado.LEIDO, terminados = listOf(10 * DIA + H, 50 * DIA), mod = 5)), emptyMap()).libros, emptyMap())
        val r = Mezcla.mezclar(listOf(a), emptyMap(), listOf(remoto), "yo")
        org.junit.Assert.assertEquals(listOf(10 * DIA, 50 * DIA), r.libros[0].terminados)
        // Y la fecha viaja en el blob
        org.junit.Assert.assertEquals(listOf(10 * DIA, 50 * DIA), Mezcla.deJson(Mezcla.aJson(Mezcla.aBlob("d", r.libros, emptyMap())))!!.libros[0].terminados)
        org.junit.Assert.assertEquals(listOf(7L, 9L), Biblioteca.aLibro(Biblioteca.aJson(Libro("z", "T", terminados = listOf(7L, 9L)))).terminados)
    }

    @org.junit.Test fun elRetoCuentaUnLibroUnaVezPorAno() {
        val ahora = System.currentTimeMillis(); val ano = anoDe(ahora)
        val libros = listOf(
            Libro("a", "A", terminados = listOf(ahora - 400 * DIA, ahora)),       // un año y este
            Libro("b", "B", terminados = listOf(ahora - 2 * DIA, ahora - 1 * DIA)), // dos veces este año: cuenta una
            Libro("c", "C", terminados = listOf(ahora - 800 * DIA)),             // otro año
            Libro("d", "D")
        )
        org.junit.Assert.assertEquals(2, terminadosEn(libros, ano))
        org.junit.Assert.assertEquals(1, terminadosEn(libros, ano - 1).coerceAtMost(1).let { if (terminadosEn(libros, anoDe(ahora - 400 * DIA)) >= 1) 1 else 0 })
    }

    @org.junit.Test fun laMetaAnualSeGuarda() {
        Ajustes.ponMetaAnual(ctx, 24); org.junit.Assert.assertEquals(24, Ajustes.metaAnual(ctx)); Ajustes.ponMetaAnual(ctx, 0)
    }

    @org.junit.Test fun deAtrioVieneLaFechaDeTerminado() {
        val blob = org.json.JSONObject().put("books", org.json.JSONArray().put(org.json.JSONObject().put("id", 1L).put("name", "Dune").put("creator", "FH").put("start", 100L).put("end", 5_000L).put("updated", 1L)))
        org.junit.Assert.assertEquals(listOf(5_000L), ImportarAtrio.candidatos(listOf(blob)).single().libro.terminados)
    }

    @org.junit.Test fun ordenarPorTerminado() {
        val x = listOf(Libro("a", "A", terminados = listOf(5L)), Libro("b", "B", terminados = listOf(9L, 20L)), Libro("c", "C"))
        org.junit.Assert.assertEquals(listOf("b", "a", "c"), ordenar(OrdenLibros.TERMINADO, x).map { it.id })
    }
}

/** Los ajustes del lector, iguales en todos tus dispositivos. */
@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [34])
class AjustesLectorTest {
    private val ctx get() = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
    private fun prefs() = ctx.getSharedPreferences("lector", android.content.Context.MODE_PRIVATE)

    @org.junit.Test fun loPropioDelDispositivoNoSube() {
        prefs().edit().clear().putString("ajustes", """{"size":120,"theme":"sepia","brightness":0.3,"pdfTexto":true}""").putLong("ajustesAt", 500L).apply()
        val o = org.json.JSONObject(AjustesLector.paraSubir(ctx))
        org.junit.Assert.assertEquals(500L, o.getLong("at")); org.junit.Assert.assertEquals(120, o.getJSONObject("s").getInt("size"))
        org.junit.Assert.assertFalse(o.getJSONObject("s").has("brightness")); org.junit.Assert.assertFalse(o.getJSONObject("s").has("pdfTexto"))
        prefs().edit().clear().apply(); org.junit.Assert.assertEquals("", AjustesLector.paraSubir(ctx))
    }

    @org.junit.Test fun gana_el_cambio_mas_reciente_y_se_conserva_el_brillo_de_aqui() {
        prefs().edit().clear().putString("ajustes", """{"size":100,"theme":"claro","brightness":0.7}""").putLong("ajustesAt", 500L).apply()
        // Uno más viejo no cambia nada
        org.junit.Assert.assertFalse(AjustesLector.aplicar(ctx, """{"at":400,"s":{"size":200}}"""))
        // Uno más nuevo sí, sin pisar el brillo de este dispositivo
        org.junit.Assert.assertTrue(AjustesLector.aplicar(ctx, """{"at":900,"s":{"size":140,"theme":"negro","brightness":0.1}}"""))
        val s = org.json.JSONObject(prefs().getString("ajustes", "{}")!!)
        org.junit.Assert.assertEquals(140, s.getInt("size")); org.junit.Assert.assertEquals("negro", s.getString("theme")); org.junit.Assert.assertEquals(0.7, s.getDouble("brightness"), 1e-9)
        org.junit.Assert.assertEquals(900L, prefs().getLong("ajustesAt", 0))
        org.junit.Assert.assertFalse(AjustesLector.aplicar(ctx, "no es json"))
    }

    @org.junit.Test fun viajanEnElBlob() {
        val l = """{"at":900,"s":{"size":140}}"""
        val b = Mezcla.deJson(Mezcla.aJson(Mezcla.aBlob("d", emptyList(), emptyMap(), lector = l)))!!
        org.junit.Assert.assertEquals(900L, org.json.JSONObject(b.lector).getLong("at"))
        org.junit.Assert.assertEquals("", Mezcla.deJson(Mezcla.aJson(Mezcla.aBlob("d", emptyList(), emptyMap())))!!.lector)
    }
}

class SubrayadosLigerosTest {
    @org.junit.Test fun soloLoQueLeeUnDiario() {
        val st = org.json.JSONObject().put("cfi", "x").put("stats", org.json.JSONObject()).put("notes", org.json.JSONArray()
            .put(org.json.JSONObject().put("value", "a").put("created", 20L).put("text", "Segundo").put("note", "mi nota").put("label", "Cap. 2").put("frac", 0.5))
            .put(org.json.JSONObject().put("value", "b").put("created", 10L).put("text", "Primero").put("note", "").put("label", "Cap. 1").put("frac", 0.1))
            .put(org.json.JSONObject().put("value", "c").put("created", 30L).put("text", "  ").put("note", "sin texto")))
        val o = subrayadosLigeros("Dune", "FH", st)!!
        org.junit.Assert.assertEquals("Dune", o.getString("t")); org.junit.Assert.assertEquals("FH", o.getString("a"))
        val n = o.getJSONArray("n")
        org.junit.Assert.assertEquals("en el orden del libro y sin los vacíos", listOf("Primero", "Segundo"), (0 until n.length()).map { n.getJSONObject(it).getString("x") })
        org.junit.Assert.assertEquals("mi nota", n.getJSONObject(1).getString("o")); org.junit.Assert.assertEquals(20L, n.getJSONObject(1).getLong("c")); org.junit.Assert.assertEquals("b", n.getJSONObject(0).getString("k"))
        org.junit.Assert.assertFalse("no se cuela el estado del lector", o.has("cfi") || o.has("stats"))
        org.junit.Assert.assertNull(subrayadosLigeros("Dune", "FH", org.json.JSONObject().put("notes", org.json.JSONArray())))
    }
}

class NotaAzabacheTest {
    private fun libro(id: Long, nombre: String, autor: String, general: Int, resena: String = "", upd: Long = 1L) =
        org.json.JSONObject().put("id", id).put("name", nombre).put("creator", autor).put("general", general).put("review", resena).put("updated", upd)

    @org.junit.Test fun laNotaSeEnlazaPorLaClaveDelLibro() {
        val blob = org.json.JSONObject().put("books", org.json.JSONArray().put(libro(7, "Dune", "Frank Herbert", 91, "  Un clásico.  ")).put(libro(8, "1984", "George Orwell", 120)))
        val m = NotaAzabache.deBlobs(listOf(blob))
        val n = m[claveDe("dune", "frank herbert")]!!
        org.junit.Assert.assertEquals(91, n.general); org.junit.Assert.assertEquals("Un clásico.", n.resena); org.junit.Assert.assertEquals(7L, n.atrioId)
        org.junit.Assert.assertEquals("la nota se queda entre 0 y 100", 100, m[claveDe("1984", "George Orwell")]!!.general)
        org.junit.Assert.assertNull(m[claveDe("Ubik", "PKD")])
    }

    @org.junit.Test fun gananLasMasRecientesYLosBorradosNoSalen() {
        val a = org.json.JSONObject().put("books", org.json.JSONArray().put(libro(7, "Dune", "FH", 50, upd = 10)).put(libro(8, "Ubik", "PKD", 70, upd = 5))).put("tombs", org.json.JSONObject().put("books:8", 9L))
        val b = org.json.JSONObject().put("books", org.json.JSONArray().put(libro(7, "Dune", "FH", 88, upd = 20)))
        val m = NotaAzabache.deBlobs(listOf(a, b))
        org.junit.Assert.assertEquals(88, m[claveDe("Dune", "FH")]!!.general)
        org.junit.Assert.assertNull("borrado en Atrio", m[claveDe("Ubik", "PKD")])
    }
}

class FotoDeAtrioTest {
    @org.junit.Test fun laFotoDeLaPortadaSeEmparejaConSuArchivoEnKDrive() {
        val blob = org.json.JSONObject()
            .put("books", org.json.JSONArray()
                .put(org.json.JSONObject().put("id", 1L).put("name", "Dune").put("creator", "FH").put("cover", "file:///data/user/0/com.gonzalo.gamerate/files/fotos/foto_1700.jpg").put("updated", 1L))
                .put(org.json.JSONObject().put("id", 2L).put("name", "Ubik").put("creator", "PKD").put("cover", "https://x/y.jpg").put("updated", 1L))
                .put(org.json.JSONObject().put("id", 3L).put("name", "Solaris").put("creator", "SL").put("cover", "file:///x/foto_999.jpg").put("updated", 1L)))
            .put("nube", org.json.JSONArray()
                .put(org.json.JSONObject().put("k", "foto:foto_1700.jpg").put("f", 777L).put("n", "foto_1700.jpg").put("s", 50L).put("updated", 1L))
                .put(org.json.JSONObject().put("k", "libro:1").put("f", 11L).put("n", "Dune.epub").put("s", 5L).put("updated", 1L)))
        val c = ImportarAtrio.candidatos(listOf(blob)).associateBy { it.libro.titulo }
        org.junit.Assert.assertEquals(777L, c["Dune"]!!.foto!!.f)
        org.junit.Assert.assertEquals("el archivo del libro sigue siendo el suyo", 11L, c["Dune"]!!.nube!!.f)
        org.junit.Assert.assertNull("una portada de internet no es una foto", c["Ubik"]!!.foto)
        org.junit.Assert.assertNull("una foto que no se subió a kDrive no se puede traer", c["Solaris"]!!.foto)
        org.junit.Assert.assertEquals("la de internet se sigue trayendo como antes", "https://x/y.jpg", c["Ubik"]!!.libro.portada)
    }
}
