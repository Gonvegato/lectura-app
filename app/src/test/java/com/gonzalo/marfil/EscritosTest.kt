package com.gonzalo.marfil

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.File
import java.util.zip.ZipInputStream

/** Los Escritos de Cobalto: lo que publica, el EPUB que se hace con ello y cómo entra (y no viaja) en la biblioteca. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EscritosTest {
    private val ctx get() = ApplicationProvider.getApplicationContext<Context>()

    /** Lo que Cobalto deja en `/users/<uid>/cobalto/lectura` (`docs/CONTRATO-DATOS.md` de Cobalto). */
    private fun lectura() = JSONObject().put("novela", JSONObject()
        .put("c2", JSONObject().put("t", "La casa").put("a", "Gonzalo").put("c", "El regreso").put("n", 2).put("pa", "Segunda parte").put("p", 20).put("x", "Volvió **tarde**.\n\n***\n\nY *nadie* lo vio.").put("u", 200L))
        .put("c1", JSONObject().put("t", "La casa").put("a", "Gonzalo").put("c", "").put("n", 1).put("pa", "Primera parte").put("p", 10).put("x", "# Uno\n\nEra de noche.\n> Una cita").put("u", 100L)))
        .put("relato", JSONObject().put("c1", JSONObject().put("t", "").put("a", "").put("c", "Solo").put("n", 1).put("pa", "").put("p", 5).put("x", "Hola & adiós <fin>").put("u", 300L)))

    private fun zipDe(bytes: ByteArray): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { z -> generateSequence { z.nextEntry }.forEach { out[it.name] = z.readBytes().decodeToString() } }
        return out
    }

    @Test fun losCapitulosSeAgrupanPorObraYSeOrdenanPorNumero() {
        val obras = Escritos.obrasDe(lectura())
        assertEquals(listOf("relato", "novela"), obras.map { it.id })  // la obra tocada más recientemente va primero
        val n = obras.first { it.id == "novela" }
        assertEquals("La casa", n.titulo); assertEquals("Gonzalo", n.autor); assertEquals(listOf(1, 2), n.capitulos.map { it.numero }); assertEquals(30, n.palabras); assertEquals(200L, n.u)
        assertEquals("Sin título", obras.first { it.id == "relato" }.titulo)
        assertTrue(Escritos.obrasDe(null).isEmpty())
    }

    @Test fun elMarkdownLigeroDeCobaltoPasaAXhtml() {
        assertEquals("Volvió <strong>tarde</strong>.", Escritos.enLinea("Volvió **tarde**."))
        assertEquals("Y <em>nadie</em> lo vio.", Escritos.enLinea("Y *nadie* lo vio."))
        assertEquals("Hola &amp; adiós &lt;fin&gt;", Escritos.enLinea("Hola & adiós <fin>"))
        val c = Escritos.cuerpo("# Uno\n\nEra de noche.\nSeguía.\n> Una cita\n***\nOtra escena.")
        assertEquals("<h2>Uno</h2><p class=\"primero\">Era de noche.</p><p>Seguía.</p><blockquote><p>Una cita</p></blockquote><p class=\"salto\">* * *</p><p class=\"primero\">Otra escena.</p>", c)
    }

    @Test fun elEpubEsValidoYLlevaPortadaPartesYCapitulos() {
        val o = Escritos.obrasDe(lectura()).first { it.id == "novela" }
        val bytes = Escritos.epub(o)
        // Se deja en disco para abrirlo con el lector de verdad (herramientas/lector-prueba/escrito.js)
        runCatching { File("build/outputs/escritos").apply { mkdirs() }.resolve("escrito.epub").writeBytes(bytes) }
        val z = zipDe(bytes)
        assertEquals("el mimetype va primero y sin comprimir", "mimetype", z.keys.first()); assertEquals("application/epub+zip", z["mimetype"])
        assertTrue(z["META-INF/container.xml"]!!.contains("OEBPS/content.opf"))
        val opf = z["OEBPS/content.opf"]!!
        assertTrue(opf.contains("<dc:title>La casa</dc:title>")); assertTrue(opf.contains("<dc:creator>Gonzalo</dc:creator>")); assertTrue(opf.contains("cobalto-novela"))
        // portada + (Primera parte + cap 1) + (Segunda parte + cap 2) = 5 páginas
        val pags = z.keys.filter { it.matches(Regex("OEBPS/p\\d+\\.xhtml")) }
        assertEquals(5, pags.size); assertEquals(5, Regex("<itemref ").findAll(opf).count())
        assertTrue(z["OEBPS/p0.xhtml"]!!.contains("<h1>La casa</h1>"))
        assertTrue(z["OEBPS/p1.xhtml"]!!.contains("Primera parte")); assertTrue(z["OEBPS/p2.xhtml"]!!.contains("<h2>Capítulo 1</h2>"))
        assertTrue(z["OEBPS/p3.xhtml"]!!.contains("Segunda parte")); assertTrue(z["OEBPS/p4.xhtml"]!!.contains("<h2>El regreso</h2>")); assertTrue(z["OEBPS/p4.xhtml"]!!.contains("<strong>tarde</strong>"))
        assertTrue(z["OEBPS/nav.xhtml"]!!.contains("El regreso"))
        // una obra sin partes ni nombres: portada + un capítulo
        val r = zipDe(Escritos.epub(Escritos.obrasDe(lectura()).first { it.id == "relato" }))
        assertEquals(2, r.keys.count { it.matches(Regex("OEBPS/p\\d+\\.xhtml")) }); assertTrue(r["OEBPS/p1.xhtml"]!!.contains("Hola &amp; adiós &lt;fin&gt;"))
    }

    @Test fun prepararDejaElLibroEnLaBibliotecaPeroFueraDeLoQueViaja() {
        Skin.cargar(ctx); Cuenta.cargar(ctx); Biblioteca.cargar(ctx); Biblioteca.libros.clear(); Biblioteca.borrados.clear()
        File(ctx.filesDir, "libros").deleteRecursively()
        ctx.getSharedPreferences("marfil_escritos", Context.MODE_PRIVATE).edit().clear().commit()
        val o = Escritos.obrasDe(lectura()).first { it.id == "novela" }
        val l = Escritos.preparar(ctx, o)
        assertEquals("escrito_novela", l.id); assertEquals("escrito:novela", l.clave); assertTrue(l.esEscrito)
        assertEquals("La casa", l.titulo); assertEquals("Gonzalo", l.autor); assertEquals("La casa.epub", l.archivo)
        val f = File(Metadatos.dirLibros(ctx), "escrito_novela.epub")
        assertTrue(f.exists()); assertTrue(l.uri.endsWith("escrito_novela.epub"))
        assertNotNull(Biblioteca.por("escrito_novela"))
        assertTrue("no sale en las listas de libros propios", Biblioteca.propios().none { it.esEscrito })
        assertTrue("no viaja a otros dispositivos", Mezcla.aBlob("A", Biblioteca.libros, emptyMap()).libros.none { it.clave == "escrito:novela" })
        // Si Cobalto no cambió nada, el archivo no se rehace; si cambió, sí
        val antes = f.lastModified(); f.setLastModified(antes - 10_000)
        Escritos.preparar(ctx, o); assertEquals(antes - 10_000, f.lastModified())
        val o2 = Escritos.obrasDe(lectura().apply { getJSONObject("novela").getJSONObject("c2").put("x", "Nuevo texto").put("u", 500L) }).first { it.id == "novela" }
        Escritos.preparar(ctx, o2)
        assertTrue(zipDe(f.readBytes())["OEBPS/p4.xhtml"]!!.contains("Nuevo texto"))
        assertEquals("sigue siendo la misma ficha", 1, Biblioteca.libros.count { it.esEscrito })
    }
}
