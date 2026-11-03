package com.gonzalo.marfil

import android.graphics.Bitmap
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Portadas (buscar, elegir, quitar) y archivo del libro (elegir, quitar), sin red. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PortadasTest {
    private val ctx get() = ApplicationProvider.getApplicationContext<android.content.Context>()

    private fun png(color: Int, w: Int = 200, h: Int = 300): ByteArray = ByteArrayOutputStream().also {
        Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }.compress(Bitmap.CompressFormat.PNG, 100, it)
    }.toByteArray()

    private fun inicio(): Libro {
        Skin.cargar(ctx); Cuenta.cargar(ctx); Biblioteca.cargar(ctx); Biblioteca.libros.clear(); Biblioteca.borrados.clear()
        File(ctx.filesDir, "portadas").deleteRecursively(); File(ctx.filesDir, "libros").deleteRecursively()
        return Libro("abc", "Dune", "Frank Herbert", archivo = "dune.epub", anadido = 1L).also { Biblioteca.libros.add(it) }
    }

    @Test fun iTunesSeLeeComoResponde() {
        val json = """{"resultCount":2,"results":[{"trackName":"El nombre de la rosa","artistName":"Umberto Eco","artworkUrl100":"https://is1-ssl.mzstatic.com/image/thumb/Publication126/v4/7a/d0/0f/x/9788426418197.jpg/100x100bb.jpg"},{"trackName":"Sin imagen","artistName":"X"}]}"""
        val c = Portadas.deItunes(json)
        assertEquals(1, c.size)
        assertTrue(c[0].miniatura.endsWith("240x240bb.jpg")); assertTrue(c[0].grande.endsWith("800x800bb.jpg"))
        assertEquals("iTunes", c[0].fuente); assertEquals("El nombre de la rosa · Umberto Eco", c[0].titulo)
    }

    @Test fun openLibrarySoloConPortada() {
        val json = """{"numFound":3,"docs":[{"author_name":["Frank Herbert"],"title":"Dune","cover_i":11481354},{"author_name":["Otro"],"title":"Sin portada"},{"title":"Otra","cover_i":980253}]}"""
        val c = Portadas.deOpenLibrary(json)
        assertEquals(2, c.size)
        assertEquals("https://covers.openlibrary.org/b/id/11481354-L.jpg", c[0].grande); assertEquals("Open Library", c[0].fuente)
        assertEquals("Dune · Frank Herbert", c[0].titulo)
    }

    @Test fun respuestasRotasNoRompenNada() {
        assertTrue(Portadas.deItunes("no es json").isEmpty()); assertTrue(Portadas.deOpenLibrary("{}").isEmpty()); assertTrue(Portadas.deItunes("""{"results":[null,1]}""").isEmpty())
    }

    @Test fun ponerCambiaLaPortadaYBorraLaAnterior() {
        val l = inicio()
        val a = Portadas.poner(ctx, l, png(0xFFAA0000.toInt()))!!
        assertTrue(File(a.portada).exists()); assertEquals(a.portada, Biblioteca.por("abc")!!.portada)
        Thread.sleep(5)
        val b = Portadas.poner(ctx, a, png(0xFF0000AA.toInt()))!!
        assertNotEquals("nombre nuevo para que se vea al momento", a.portada, b.portada)
        assertFalse("la anterior se borra", File(a.portada).exists()); assertTrue(File(b.portada).exists())
        val q = Portadas.quitar(ctx, b)
        assertEquals("", q.portada); assertFalse(File(b.portada).exists())
    }

    @Test fun unaImagenRotaNoSirve() {
        val l = inicio()
        assertNull(Portadas.poner(ctx, l, "<html><body>Esto no es una imagen, es una página de error</body></html>".repeat(30).toByteArray()))
        assertEquals("", Biblioteca.por("abc")!!.portada)
    }

    private fun epub(): File {
        val f = File(ctx.cacheDir, "nuevo.epub")
        ZipOutputStream(f.outputStream()).use { z ->
            fun pon(r: String, b: ByteArray) { z.putNextEntry(ZipEntry(r)); z.write(b); z.closeEntry() }
            pon("mimetype", "application/epub+zip".toByteArray())
            pon("META-INF/container.xml", """<?xml version="1.0"?><container xmlns="urn:oasis:names:tc:opendocument:xmlns:container" version="1.0"><rootfiles><rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/></rootfiles></container>""".toByteArray())
            pon("OEBPS/content.opf", """<?xml version="1.0"?><package xmlns="http://www.idpf.org/2007/opf" version="2.0"><metadata xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:opf="http://www.idpf.org/2007/opf"><dc:title>Otro título distinto</dc:title><dc:creator>Otro autor</dc:creator><meta name="cover" content="c"/></metadata><manifest><item id="c" href="c.png" media-type="image/png"/></manifest></package>""".toByteArray())
            pon("OEBPS/c.png", png(0xFF00AA00.toInt()))
        }
        return f
    }

    @Test fun elegirArchivoDejaElMismoLibroConSuPortada() {
        val l = inicio()
        val n = Metadatos.adjuntar(ctx, l, Uri.fromFile(epub()))!!
        assertEquals("el libro sigue siendo el mismo (misma clave y misma ficha)", l.clave, n.clave); assertEquals("Dune", n.titulo)
        assertFalse(n.enOtroDispositivo); assertTrue(File(Uri.parse(n.uri).path!!).exists())
        assertTrue("saca la portada del archivo", n.portada.isNotBlank() && File(n.portada).exists())
        assertEquals(1, Biblioteca.libros.size)
    }

    @Test fun quitarElArchivoDejaLaFicha() {
        val l = inicio()
        val n = Metadatos.adjuntar(ctx, l, Uri.fromFile(epub()))!!
        val f = File(Uri.parse(n.uri).path!!)
        val q = Metadatos.quitarArchivo(ctx, n)
        assertTrue(q.enOtroDispositivo); assertFalse(f.exists()); assertEquals(1, Biblioteca.libros.size); assertEquals("Dune", Biblioteca.por("abc")!!.titulo)
    }

    @Test fun unFormatoQueNoEsDeLibroSeRechaza() {
        val l = inicio()
        val f = File(ctx.cacheDir, "foto.jpg").apply { writeBytes(png(0xFF111111.toInt())) }
        assertNull(Metadatos.adjuntar(ctx, l, Uri.fromFile(f)))
        assertTrue(Biblioteca.por("abc")!!.enOtroDispositivo)
    }

    @Test fun laTelaEsSiempreLaMismaParaUnTitulo() {
        assertEquals(telaDe("Dune"), telaDe("dune!")); assertEquals(telaDe("Los pilares de la Tierra"), telaDe("Los Pilares de la tierra"))
    }
}
