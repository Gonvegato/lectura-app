package com.gonzalo.lectura

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import java.io.File

/** Capturas de la interfaz en el ordenador, con libros de ejemplo (solo con -Proborazzi.test.record=true). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel7)
class CapturasTest {
    @get:Rule val rule = createComposeRule()
    private val ctx: Context get() = ApplicationProvider.getApplicationContext()

    private fun portada(nombre: String, a: Int, b: Int): String {
        val f = File(ctx.cacheDir, "p_${nombre.hashCode()}.png")
        val bmp = Bitmap.createBitmap(300, 440, Bitmap.Config.ARGB_8888)
        Canvas(bmp).apply {
            drawRect(0f, 0f, 300f, 440f, Paint().apply { shader = LinearGradient(0f, 0f, 300f, 440f, a, b, Shader.TileMode.CLAMP) })
            drawText(nombre, 24f, 380f, Paint().apply { color = 0xCCFFFFFF.toInt(); textSize = 30f; isAntiAlias = true; isFakeBoldText = true })
        }
        f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return f.absolutePath
    }

    private fun siembra() {
        Skin.cargar(ctx); Biblioteca.cargada = true; Biblioteca.libros.clear(); Biblioteca.borrados.clear()
        val ahora = System.currentTimeMillis()
        Biblioteca.libros.addAll(listOf(
            Libro("1", "El nombre de la rosa", "Umberto Eco", portada = portada("Rosa", 0xFF8C2A22.toInt(), 0xFF2B1410.toInt()), uri = "file:///x", archivo = "rosa.epub", estado = Estado.LEYENDO, fraccion = 0.37f, ultima = ahora - 3600_000, msPropios = 5 * 3600_000L + 1_200_000, anadido = ahora - 9, favorito = true),
            Libro("2", "Dune", "Frank Herbert", portada = portada("Dune", 0xFFD9A53A.toInt(), 0xFF5A3A0A.toInt()), uri = "file:///x", archivo = "dune.epub", estado = Estado.LEYENDO, fraccion = 0.8f, ultima = ahora - 86_400_000L, msPropios = 9 * 3600_000L, anadido = ahora - 8),
            Libro("3", "Cien años de soledad", "Gabriel García Márquez", portada = portada("Cien", 0xFF2F5D8A.toInt(), 0xFF0B1B2E.toInt()), uri = "file:///x", archivo = "cien.epub", anadido = ahora - 7),
            Libro("4", "La sombra del viento", "Carlos Ruiz Zafón", uri = "file:///x", archivo = "sombra.epub", anadido = ahora - 6),
            Libro("5", "Fahrenheit 451", "Ray Bradbury", portada = portada("F451", 0xFFE0603A.toInt(), 0xFF5A1D0A.toInt()), uri = "file:///x", archivo = "f451.epub", estado = Estado.LEIDO, fraccion = 1f, msPropios = 4 * 3600_000L, anadido = ahora - 5),
            Libro("6", "1984", "George Orwell", uri = "file:///x", archivo = "1984.pdf", estado = Estado.LEIDO, fraccion = 1f, anadido = ahora - 4),
            Libro("7", "Los pilares de la Tierra", "Ken Follett", archivo = "pilares.epub", anadido = ahora - 3, estado = Estado.PENDIENTE),
        ))
    }

    private fun foto(nombre: String) {
        rule.waitForIdle(); Thread.sleep(500); rule.waitForIdle()
        com.github.takahirom.roborazzi.captureScreenRoboImage("build/outputs/roborazzi/$nombre.png")
    }

    private fun montar(oscuro: Boolean) {
        coil.Coil.setImageLoader(coil.ImageLoader.Builder(ctx).dispatcher(Dispatchers.Unconfined).allowHardware(false).build())
        Modo.eleccion = if (oscuro) 2 else 1
        rule.setContent { PorticoLike { LecturaApp() } }
    }

    @androidx.compose.runtime.Composable
    private fun PorticoLike(c: @androidx.compose.runtime.Composable () -> Unit) = LecturaTheme { Surface(Modifier.fillMaxSize(), color = Paper) { c() } }

    @Test fun portada() {
        org.junit.Assume.assumeTrue(System.getProperty("roborazzi.test.record") == "true")
        siembra(); montar(false); foto("1_portada")
    }

    @Test fun portadaOscuro() {
        org.junit.Assume.assumeTrue(System.getProperty("roborazzi.test.record") == "true")
        siembra(); montar(true); foto("2_portada_oscuro")
    }

    @Test fun listaYFicha() {
        org.junit.Assume.assumeTrue(System.getProperty("roborazzi.test.record") == "true")
        siembra(); montar(false)
        rule.onNodeWithText("TODOS").performClick(); foto("3_lista")
        rule.onNodeWithText("Dune", useUnmergedTree = true).performClick(); foto("4_ficha")
    }

    @Test fun vacia() {
        org.junit.Assume.assumeTrue(System.getProperty("roborazzi.test.record") == "true")
        Skin.cargar(ctx); Biblioteca.cargada = true; Biblioteca.libros.clear(); montar(false); foto("5_portada_vacia")
    }
}
