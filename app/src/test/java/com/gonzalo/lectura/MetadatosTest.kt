package com.gonzalo.lectura

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Añadir libros: se leen el título, el autor y la portada del propio EPUB. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MetadatosTest {
    private val ctx get() = ApplicationProvider.getApplicationContext<android.content.Context>()

    private fun epub(nombre: String, titulo: String, autor: String): File {
        val f = File(ctx.cacheDir, nombre)
        val png = java.io.ByteArrayOutputStream().also { android.graphics.Bitmap.createBitmap(20, 30, android.graphics.Bitmap.Config.ARGB_8888).apply { eraseColor(0xFF336699.toInt()) }.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
        ZipOutputStream(f.outputStream()).use { z ->
            fun pon(ruta: String, b: ByteArray) { z.putNextEntry(ZipEntry(ruta)); z.write(b); z.closeEntry() }
            pon("mimetype", "application/epub+zip".toByteArray())
            pon("META-INF/container.xml", """<?xml version="1.0"?><container xmlns="urn:oasis:names:tc:opendocument:xmlns:container" version="1.0"><rootfiles><rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/></rootfiles></container>""".toByteArray())
            pon("OEBPS/content.opf", """<?xml version="1.0"?><package xmlns="http://www.idpf.org/2007/opf" version="2.0"><metadata xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:opf="http://www.idpf.org/2007/opf">
                <dc:title>$titulo</dc:title><dc:creator opf:role="aut">$autor</dc:creator><meta name="cover" content="portada"/></metadata>
                <manifest><item id="portada" href="img/Port%20ada.png" media-type="image/png"/><item id="c1" href="c1.xhtml" media-type="application/xhtml+xml"/></manifest></package>""".toByteArray())
            pon("OEBPS/img/Port ada.png", png)
        }
        return f
    }

    @Test fun leeTituloAutorYPortadaDeUnEpub() {
        val m = Metadatos.leer(epub("rosa.epub", "El nombre de la rosa", "Umberto Eco"), "rosa.epub")
        assertEquals("El nombre de la rosa", m.titulo); assertEquals("Umberto Eco", m.autor)
        assertNotNull("la portada (aunque el nombre lleve espacios codificados)", m.portada); assertTrue(m.portada!!.size > 50)
    }

    @Test fun unArchivoRotoNoHaceFallar() {
        val f = File(ctx.cacheDir, "roto.epub").apply { writeText("esto no es un zip") }
        val m = Metadatos.leer(f, "roto.epub")
        assertEquals("", m.titulo); assertNull(m.portada)
    }

    @Test fun registrarPoneElLibroEnLaBibliotecaYSeUneAlQueEstabaEnOtroDispositivo() {
        Biblioteca.libros.clear(); Biblioteca.borrados.clear(); Biblioteca.cargada = true
        // Antes llegó de otro dispositivo, sin archivo
        Biblioteca.poner(Libro("x1", "Dune", "Frank Herbert", fraccion = 0.5f, estado = Estado.LEYENDO, anadido = 5))
        assertTrue(Biblioteca.libros[0].enOtroDispositivo)
        val archivo = epub("dune.epub", "Dune", "Frank Herbert")
        val l = Metadatos.registrar(ctx, archivo, "dune.epub")
        assertEquals("se une al que ya estaba (misma clave)", 1, Biblioteca.libros.size); assertEquals("x1", l.id)
        assertFalse(l.enOtroDispositivo); assertEquals(0.5f, l.fraccion, 0.001f); assertEquals(Estado.LEYENDO, l.estado); assertTrue(File(l.portada).exists())
        Biblioteca.libros.clear()
    }
}
