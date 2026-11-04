package com.gonzalo.marfil

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
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
        Skin.cargar(ctx); Biblioteca.cargada = true; Biblioteca.libros.clear(); Biblioteca.borrados.clear(); Sagas.cargar(ctx); Sagas.lista.clear(); Sagas.borradas.clear()
        val ahora = System.currentTimeMillis(); val dia = 86_400_000L
        fun l(id: String, t: String, a: String, e: Estado, fr: Float = 0f, pag: Int = 0, ult: Long = 0, ms: Long = 0, anad: Long = 0, img: String = "") =
            Libro(id, t, a, portada = img, uri = "file:///x", archivo = "$id.epub", estado = e, fraccion = fr, paginas = pag, paginasOrigen = if (pag > 0) "openlibrary" else "", ultima = ult, msPropios = ms, anadido = ahora - anad)
        Biblioteca.libros.addAll(listOf(
            l("1", "El nombre de la rosa", "Umberto Eco", Estado.LEYENDO, 0.37f, 540, ahora, 5 * 3600_000L + 20 * 60_000L, 9, portada("Rosa", 0xFF8C2A22.toInt(), 0xFF2B1410.toInt())),
            l("2", "Dune", "Frank Herbert", Estado.LEYENDO, 0.8f, 608, ahora - dia, 9 * 3600_000L, 8, portada("Dune", 0xFFD9A53A.toInt(), 0xFF5A3A0A.toInt())),
            l("11", "Ubik", "Philip K. Dick", Estado.LEYENDO, 0.1f, 224, ahora - 2 * dia, 30 * 60_000L, 7),
            l("3", "Cien años de soledad", "Gabriel García Márquez", Estado.PENDIENTE, 0f, 471, 0, 0, 7, portada("Cien", 0xFF2F5D8A.toInt(), 0xFF0B1B2E.toInt())),
            l("4", "La sombra del viento", "Carlos Ruiz Zafón", Estado.PENDIENTE, 0f, 528, 0, 0, 6),
            l("7", "Los pilares de la Tierra", "Ken Follett", Estado.PENDIENTE, 0f, 1056, 0, 0, 3),
            l("8", "Mesías de Dune", "Frank Herbert", Estado.PENDIENTE, 0f, 279, 0, 0, 2),
            l("9", "Hijos de Dune", "Frank Herbert", Estado.PENDIENTE, 0f, 504, 0, 0, 1),
            l("5", "Fahrenheit 451", "Ray Bradbury", Estado.LEIDO, 1f, 249, ahora - 20 * dia, 4 * 3600_000L, 4, portada("F451", 0xFFE0603A.toInt(), 0xFF5A1D0A.toInt())),
            l("6", "1984", "George Orwell", Estado.LEIDO, 1f, 328, ahora - 40 * dia, 6 * 3600_000L, 4),
            l("10", "Solaris", "Stanislaw Lem", Estado.ABANDONADO, 0.22f, 230, ahora - 60 * dia, 2 * 3600_000L, 5),
        ))
        // Dos terminados este año (y uno de ellos, por segunda vez) y un reto de 12
        Biblioteca.libros[8] = Biblioteca.libros[8].copy(terminados = listOf(ahora - 200 * dia, ahora - 20 * dia))
        Biblioteca.libros[9] = Biblioteca.libros[9].copy(terminados = listOf(ahora - 40 * dia))
        Ajustes.ponMetaAnual(ctx, 12)
        Sagas.poner(Saga("sd", "Crónicas de Dune", listOf(Biblioteca.libros[1].clave, Biblioteca.libros[6].clave, Biblioteca.libros[7].clave)))
        // Unos días de lectura para que se vean las barras de la semana, y subrayados en Dune
        val hoy = java.time.LocalDate.now()
        val dias = org.json.JSONObject().apply { listOf(0 to 52, 1 to 35, 2 to 0, 3 to 70, 4 to 25, 5 to 41, 6 to 18).forEach { (atras, min) -> if (min > 0) put(hoy.minusDays(atras.toLong()).toString(), min * 60_000L) } }
        val subr = org.json.JSONArray()
            .put(org.json.JSONObject().put("value", "a").put("created", ahora - 5_000_000L).put("text", "El miedo es la pequeña muerte que conduce a la aniquilación total.").put("note", "Frase clave de la Letanía").put("label", "Capítulo 2").put("frac", 0.2))
            .put(org.json.JSONObject().put("value", "b").put("created", ahora - 3_000_000L).put("text", "Quien controla la especia controla el universo.").put("note", "").put("label", "Capítulo 11").put("frac", 0.55))
        EstadoLectura.ponerSinAvisar(ctx, "2", org.json.JSONObject().put("notes", subr).put("label", "Capítulo 11"))
        EstadoLectura.ponerSinAvisar(ctx, "1", org.json.JSONObject().put("stats", org.json.JSONObject().put("days", dias)))
    }

    private fun foto(nombre: String) {
        rule.waitForIdle(); Thread.sleep(500); rule.waitForIdle()
        com.github.takahirom.roborazzi.captureScreenRoboImage("build/outputs/roborazzi/$nombre.png")
    }

    private fun montar(oscuro: Boolean) {
        coil.Coil.setImageLoader(coil.ImageLoader.Builder(ctx).dispatcher(Dispatchers.Unconfined).allowHardware(false).build())
        Modo.eleccion = if (oscuro) 2 else 1
        rule.setContent { PorticoLike { MarfilApp() } }
    }

    @androidx.compose.runtime.Composable
    private fun PorticoLike(c: @androidx.compose.runtime.Composable () -> Unit) = MarfilTheme { Surface(Modifier.fillMaxSize(), color = Paper) { c() } }

    @Test fun portada() {
        org.junit.Assume.assumeTrue(System.getProperty("roborazzi.test.record") == "true")
        siembra(); montar(false); foto("1_portada")
    }

    @Test fun portadaOscuro() {
        org.junit.Assume.assumeTrue(System.getProperty("roborazzi.test.record") == "true")
        siembra(); montar(true); foto("2_portada_oscuro")
    }

    @Test fun seccionesYSagas() {
        org.junit.Assume.assumeTrue(System.getProperty("roborazzi.test.record") == "true")
        siembra(); montar(false)
        rule.onNodeWithText("LEYENDO").performClick(); foto("3_lista")
        rule.onAllNodesWithText("PENDIENTES")[0].performClick(); rule.waitForIdle(); foto("3b_pendientes")
        rule.onNodeWithText("Crónicas de Dune", useUnmergedTree = true).performClick(); foto("3c_saga_abierta")
        rule.onNodeWithText("VER LA SAGA →", useUnmergedTree = true).performClick(); foto("3d_pagina_saga")
    }

    @Test fun otrasSecciones() {
        org.junit.Assume.assumeTrue(System.getProperty("roborazzi.test.record") == "true")
        siembra(); montar(false)
        rule.onNodeWithText("LEÍDOS").performClick(); rule.waitForIdle(); foto("3e_leidos")
        rule.onAllNodesWithText("ABANDONADOS")[0].performClick(); rule.waitForIdle(); foto("3f_abandonados")
        rule.onAllNodesWithText("AUTORES")[0].performClick(); rule.waitForIdle()
        rule.onNodeWithText("Frank Herbert", useUnmergedTree = true).performClick(); foto("3g_autores")
    }

    @Test fun vacia() {
        org.junit.Assume.assumeTrue(System.getProperty("roborazzi.test.record") == "true")
        Skin.cargar(ctx); Biblioteca.cargada = true; Biblioteca.libros.clear(); montar(false); foto("5_portada_vacia")
    }

    @Test fun fichaCompleta() {
        org.junit.Assume.assumeTrue(System.getProperty("roborazzi.test.record") == "true")
        siembra(); montar(false)
        rule.onNodeWithText("LEYENDO").performClick()
        rule.onNodeWithText("Crónicas de Dune", useUnmergedTree = true).performClick()
        rule.onAllNodesWithText("Dune", useUnmergedTree = true)[0].performClick(); foto("4_ficha")
        rule.onNodeWithText("Añadido", useUnmergedTree = true).performScrollTo(); foto("4b_ficha_abajo")
    }

    @Test fun fichaSinArchivoNiPortada() {
        org.junit.Assume.assumeTrue(System.getProperty("roborazzi.test.record") == "true")
        siembra(); montar(false)
        Biblioteca.libros[5] = Biblioteca.libros[5].copy(uri = "")
        rule.onNodeWithText("PENDIENTES").performClick(); rule.waitForIdle()
        rule.onNodeWithText("Los pilares de la Tierra", useUnmergedTree = true).performClick(); foto("4c_ficha_sin_archivo")
    }

    @Test fun tapas() {
        org.junit.Assume.assumeTrue(System.getProperty("roborazzi.test.record") == "true")
        Skin.cargar(ctx)
        rule.setContent { MarfilTheme { Surface(Modifier.fillMaxSize(), color = Paper) {
            androidx.compose.foundation.layout.Column(Modifier.padding(20.dp), verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp)) {
                androidx.compose.foundation.layout.Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(14.dp)) {
                    listOf("Los pilares de la Tierra" to "Ken Follett", "La sombra del viento" to "Carlos Ruiz Zafón", "Cien años de soledad" to "Gabriel García Márquez").forEach { (t, a) ->
                        PortadaLibro(Libro("x", t, a), Modifier.width(104.dp).aspectRatio(0.68f))
                    }
                }
                androidx.compose.foundation.layout.Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(14.dp)) {
                    listOf("Dune", "1984", "Ulises", "Fahrenheit 451", "Don Quijote de la Mancha", "El Principito").forEach { t -> PortadaLibro(Libro("x", t), Modifier.width(50.dp).aspectRatio(0.68f)) }
                }
                PortadaLibro(Libro("x", "El nombre de la rosa", "Umberto Eco"), Modifier.width(210.dp).aspectRatio(0.68f))
            }
        } } }
        foto("6_tapas")
    }

    @Test fun selectorDePortada() {
        org.junit.Assume.assumeTrue(System.getProperty("roborazzi.test.record") == "true")
        siembra(); Skin.cargar(ctx)
        val c = listOf("Rosa" to 0xFF8C2A22.toInt(), "Dune" to 0xFFD9A53A.toInt(), "Cien" to 0xFF2F5D8A.toInt(), "F451" to 0xFFE0603A.toInt(), "Otra" to 0xFF35523F.toInt(), "Mas" to 0xFF5B3F6B.toInt())
            .mapIndexed { i, (n, col) -> portada(n, col, col and 0x7f7f7f).let { Candidata(it, it, if (i % 2 == 0) "iTunes" else "Open Library", "El nombre de la rosa · Umberto Eco") } }
        coil.Coil.setImageLoader(coil.ImageLoader.Builder(ctx).dispatcher(Dispatchers.Unconfined).allowHardware(false).build())
        rule.setContent { MarfilTheme { Surface(Modifier.fillMaxSize(), color = Paper) { ContenidoSelectorPortada(Biblioteca.libros.first(), {}, hallados = c) } } }
        foto("7_selector_portada")
    }

    @Test fun ajustesLibros() {
        org.junit.Assume.assumeTrue(System.getProperty("roborazzi.test.record") == "true")
        siembra(); montar(false)
        rule.onNodeWithContentDescription("Ajustes").performClick()
        rule.onNodeWithText("Tener todos mis libros aquí", useUnmergedTree = true).performScrollTo(); foto("8_ajustes_libros")
    }

    @Test fun fichaDeUnLeido() {
        org.junit.Assume.assumeTrue(System.getProperty("roborazzi.test.record") == "true")
        siembra(); montar(false)
        rule.onNodeWithText("LEÍDOS").performClick(); rule.waitForIdle()
        rule.onNodeWithText("Fahrenheit 451", useUnmergedTree = true).performClick(); foto("4d_ficha_leido")
        rule.onNodeWithText("Añadido", useUnmergedTree = true).performScrollTo(); foto("4e_ficha_leido_abajo")
    }
}
