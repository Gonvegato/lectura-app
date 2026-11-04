package com.gonzalo.marfil

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate

/** Los widgets, dibujados en el ordenador con libros de ejemplo. Sin -Proborazzi.test.record solo se comprueba que se dibujan. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class WidgetsTest {
    private val ctx: Context get() = ApplicationProvider.getApplicationContext()
    private val grabar get() = System.getProperty("roborazzi.test.record") == "true"

    private fun imagen(nombre: String, a: Int, b: Int): String {
        val f = File(ctx.cacheDir, "w_${nombre.hashCode()}.png")
        val bmp = Bitmap.createBitmap(300, 440, Bitmap.Config.ARGB_8888)
        Canvas(bmp).apply {
            drawPaint(Paint().apply { shader = LinearGradient(0f, 0f, 300f, 440f, a, b, Shader.TileMode.CLAMP) })
            drawText(nombre, 24f, 380f, Paint().apply { color = 0xCCFFFFFF.toInt(); textSize = 34f; isAntiAlias = true; isFakeBoldText = true })
        }
        f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return f.absolutePath
    }

    private fun siembra() {
        Skin.cargar(ctx); Cuenta.cargar(ctx); Biblioteca.cargar(ctx); Biblioteca.libros.clear(); Biblioteca.borrados.clear()
        val ahora = System.currentTimeMillis()
        Biblioteca.libros.addAll(listOf(
            Libro("1", "El nombre de la rosa", "Umberto Eco", portada = imagen("Rosa", 0xFF8C2A22.toInt(), 0xFF2B1410.toInt()), uri = "file:///x", archivo = "rosa.epub", estado = Estado.LEYENDO, fraccion = 0.37f, ultima = ahora, msPropios = 5 * 3600_000L + 20 * 60_000L, anadido = ahora - 9),
            Libro("2", "Dune", "Frank Herbert", portada = imagen("Dune", 0xFFD9A53A.toInt(), 0xFF5A3A0A.toInt()), uri = "file:///x", archivo = "dune.epub", estado = Estado.LEYENDO, fraccion = 0.8f, ultima = ahora - 86_400_000L, msPropios = 9 * 3600_000L, anadido = ahora - 8),
            Libro("3", "Cien años de soledad", "Gabriel García Márquez", portada = imagen("Cien", 0xFF2F5D8A.toInt(), 0xFF0B1B2E.toInt()), uri = "file:///x", archivo = "cien.epub", anadido = ahora - 7),
            Libro("4", "La sombra del viento", "Carlos Ruiz Zafón", uri = "file:///x", archivo = "sombra.epub", anadido = ahora - 6),
            Libro("5", "Los pilares de la Tierra", "Ken Follett", uri = "file:///x", archivo = "pilares.epub", anadido = ahora - 3),
            Libro("6", "Fahrenheit 451", "Ray Bradbury", portada = imagen("F451", 0xFFE0603A.toInt(), 0xFF5A1D0A.toInt()), uri = "file:///x", archivo = "f451.epub", estado = Estado.LEIDO, fraccion = 1f, anadido = ahora - 5)
        ))
        val hoy = LocalDate.now()
        val dias = JSONObject().apply { listOf(0 to 52, 1 to 35, 2 to 0, 3 to 70, 4 to 25, 5 to 41, 6 to 18).forEach { (atras, min) -> if (min > 0) put(hoy.minusDays(atras.toLong()).toString(), min * 60_000L) } }
        val notas = JSONArray()
            .put(JSONObject().put("value", "a").put("created", ahora - 5_000_000L).put("text", "El miedo es la pequeña muerte que conduce a la aniquilación total.").put("note", "").put("label", "Cap. 2").put("frac", 0.2))
            .put(JSONObject().put("value", "b").put("created", ahora - 3_000_000L).put("text", "Quien controla la especia controla el universo.").put("note", "").put("label", "Cap. 11").put("frac", 0.55))
        EstadoLectura.ponerSinAvisar(ctx, "1", JSONObject().put("stats", JSONObject().put("days", dias)))
        EstadoLectura.ponerSinAvisar(ctx, "2", JSONObject().put("notes", notas))
        Ajustes.ponMetaLectura(ctx, 30)
    }

    private val PEDIDOS = listOf(
        Triple("leyendo", WTipo.LEYENDO, 330f to 165f), Triple("leyendo_compacto", WTipo.LEYENDO, 160f to 160f), Triple("leyendo_alto", WTipo.LEYENDO, 160f to 300f),
        Triple("estante", WTipo.ESTANTE, 330f to 165f), Triple("estante_bajo", WTipo.ESTANTE, 330f to 110f),
        Triple("portada", WTipo.PORTADA, 330f to 250f), Triple("siguiente", WTipo.SIGUIENTE, 330f to 165f),
        Triple("semana", WTipo.SEMANA, 330f to 165f), Triple("cita", WTipo.CITA, 330f to 200f),
        Triple("marca", WTipo.MARCA, 330f to 80f), Triple("marcapapel", WTipo.MARCA_PAPEL, 330f to 165f)
    )

    @Test fun todosSeDibujan() {
        siembra()
        val dir = File("build/outputs/roborazzi").apply { mkdirs() }
        for ((nombre, tipo, tam) in PEDIDOS) for (noche in listOf(false, true)) {
            val pag = runBlocking { Widgets.componer(ctx, tipo, 1, tam.first, tam.second) }
            val bm = pag.bitmap(ctx, tam.first, tam.second, noche, 2f)
            assertEquals((tam.first * 2).toInt(), bm.width)
            // No es una hoja en blanco: hay más de un color
            val pix = IntArray(bm.width * bm.height).also { bm.getPixels(it, 0, bm.width, 0, 0, bm.width, bm.height) }
            assertTrue("$nombre se ve vacío", pix.toSet().size > 8)
            if (grabar) File(dir, "w_${nombre}${if (noche) "_noche" else ""}.png").outputStream().use { bm.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    @Test fun sinLibrosCadaWidgetDiceQueFalta() {
        Skin.cargar(ctx); Cuenta.cargar(ctx); Biblioteca.cargar(ctx); Biblioteca.libros.clear()
        for ((nombre, tipo, tam) in PEDIDOS) {
            if (tipo == WTipo.MARCA || tipo == WTipo.MARCA_PAPEL) continue
            val pag = runBlocking { Widgets.componer(ctx, tipo, 1, tam.first, tam.second) }
            assertTrue("$nombre sin zonas", pag.zonas.isNotEmpty())
            pag.bitmap(ctx, tam.first, tam.second, false, 1f)
        }
    }

    @Test fun losToquesAbrenLoQueDicen() {
        siembra()
        val pag = runBlocking { Widgets.componer(ctx, WTipo.LEYENDO, 1, 330f, 165f) }
        assertTrue("fondo + seguir (+ siguiente)", pag.zonas.size >= 3)
        assertTrue(pag.zonas.all { it.toque != null })
    }

    @Test fun lasPreviasDelSelector() {
        org.junit.Assume.assumeTrue(grabar)
        siembra()
        val dir = File("src/main/res/drawable-nodpi").apply { mkdirs() }
        for ((nombre, tipo, tam) in PEDIDOS) {
            if (nombre !in setOf("leyendo", "estante", "portada", "siguiente", "semana", "cita", "marca", "marcapapel")) continue
            val pag = runBlocking { Widgets.componer(ctx, tipo, 1, tam.first, tam.second) }
            File(dir, "w_prev_$nombre.png").outputStream().use { pag.bitmap(ctx, tam.first, tam.second, false, 2f).compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    @Test fun unWidgetPuedeAbrirSiempreElLibroQueElijas() {
        siembra()
        val fijo = Biblioteca.libros.last()
        Widgets.ponFijo(ctx, 77, fijo.id)
        val pag = runBlocking { Widgets.componer(ctx, WTipo.LEYENDO, 77, 330f, 165f) }
        assertTrue(pag.zonas.any { it.desc == "Abrir ${fijo.titulo}" })
        assertTrue(runBlocking { Widgets.componer(ctx, WTipo.PORTADA, 77, 330f, 250f) }.zonas.any { it.desc == fijo.titulo })
        Widgets.olvidar(ctx, intArrayOf(77)); assertNull(Widgets.fijoDe(ctx, 77))
    }
}
