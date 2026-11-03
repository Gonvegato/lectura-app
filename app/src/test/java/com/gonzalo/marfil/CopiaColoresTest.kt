package com.gonzalo.marfil

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Copiar de Atrio los colores uno a uno, y escribir el código de un color directamente. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class CopiaColoresTest {
    @get:Rule val rule = createComposeRule()
    private val ctx get() = ApplicationProvider.getApplicationContext<android.content.Context>()

    @After fun limpia() {
        Skin.fondoId = ""; Skin.accentId = "paleta"; Skin.fondoPropio = 0L; Skin.tintaPropia = 0L; Skin.acentoPropio = 0L; Skin.escala = 100
        Skin.display = "Atrio"; Skin.body = "Atrio"; Modo.eleccion = 0; Skin.origenAtrio = true; Skin.invertido = true; Skin.ajustesAt = 0L
    }

    @Test fun elCodigoDeUnColor() {
        assertEquals(Color(0xFF4F8A5B), colorDeCodigo("#4F8A5B")); assertEquals(Color(0xFF4F8A5B), colorDeCodigo("4f8a5b")); assertEquals(Color(0xFF4F8A5B), colorDeCodigo(" #4f8a5b "))
        assertEquals("tres cifras", Color(0xFFAABBCC), colorDeCodigo("#abc")); assertEquals("con opacidad delante", Color(0xFF123456), colorDeCodigo("#FF123456"))
        assertEquals(Color(0xFF123456), colorDeCodigo("0xFF123456"))
        assertNull(colorDeCodigo("#12345")); assertNull(colorDeCodigo("#GGGGGG")); assertNull(colorDeCodigo("")); assertNull(colorDeCodigo("#"))
    }

    @Test fun escribirElCodigoAplicaElColorExacto() {
        Skin.cargar(ctx)
        var cambiado = 0L; var guardado = 0
        rule.mainClock.autoAdvance = false   // (el cursor parpadeante del campo no deja nunca «quieto» a Compose en las pruebas)
        rule.setContent { MarfilTheme { Column { SelectorColor("Fondo", cambiado, Paper, { cambiado = it }, { guardado++ }) } } }
        rule.mainClock.advanceTimeBy(100)
        rule.onNodeWithText("Fondo").performClick(); rule.mainClock.advanceTimeBy(100)
        val campo = rule.onNode(hasSetTextAction())
        campo.performTextReplacement("#102030"); rule.mainClock.advanceTimeBy(100)
        assertEquals(0xFF102030L, cambiado); assertTrue(guardado >= 1)
        // En minúsculas y sin #
        campo.performTextReplacement("a1b2c3"); rule.mainClock.advanceTimeBy(100)
        assertEquals(0xFFA1B2C3L, cambiado)
        // Mientras no es un color válido, no cambia nada (y se puede seguir escribiendo)
        val antes = cambiado
        campo.performTextReplacement("#12"); rule.mainClock.advanceTimeBy(100)
        assertEquals(antes, cambiado)
        campo.performTextReplacement("#1234567"); rule.mainClock.advanceTimeBy(100)
        assertEquals("siete cifras no valen", antes, cambiado)
    }

    private fun atrio(vararg kv: Pair<String, Any>) = CopiaAtrio.Ajustes(1L, JSONObject().also { o -> kv.forEach { (k, v) -> o.put(k, v) } })

    @Test fun losColoresDeAtrioSeCopianPorSeparado() {
        Skin.cargar(ctx)
        // Un Atrio claro, con acento azul y sin colores a mano: lo que se ve es el papel y la tinta de su tema, y el azul
        val claro = atrio("theme31" to 1, "skin_accent_id" to "azul")
        val revista = PALETTES.first { it.id == "revista" }.light
        assertEquals(revista.paper, CopiaAtrio.colorEfectivo(claro, "fondo")); assertEquals(revista.ink, CopiaAtrio.colorEfectivo(claro, "texto"))
        assertEquals(0xFF2F5D8AL, CopiaAtrio.colorEfectivo(claro, "acento"))
        val filas = CopiaAtrio.filas(claro).associateBy { it.id }
        assertTrue(filas["c_fondo"]!!.valor, filas["c_fondo"]!!.valor.contains("(el de su tema)")); assertTrue(filas.containsKey("c_texto")); assertTrue(filas.containsKey("c_acento"))
        // Copiar SOLO el acento: el fondo y el texto de Marfil no se tocan
        Skin.fondoPropio = 0xFF222222L; Skin.tintaPropia = 0xFFEEEEEEL; Skin.acentoPropio = 0L
        CopiaAtrio.copiarColor(ctx, claro, "acento")
        assertEquals(0xFF2F5D8AL, Skin.acentoPropio); assertEquals(0xFF222222L, Skin.fondoPropio); assertEquals(0xFFEEEEEEL, Skin.tintaPropia)
        // Un Atrio con fondo y texto a mano: se copian esos, cada uno por su lado
        val manual = atrio("theme31" to 2, "skin_fondo_propio" to 0xFF101820L, "skin_tinta_propia" to 0xFFFFD166L)
        assertEquals(0xFF101820L, CopiaAtrio.colorEfectivo(manual, "fondo")); assertEquals(0xFFFFD166L, CopiaAtrio.colorEfectivo(manual, "texto"))
        CopiaAtrio.copiarColor(ctx, manual, "texto")
        assertEquals(0xFFFFD166L, Skin.tintaPropia); assertEquals("el fondo sigue siendo el de Marfil", 0xFF222222L, Skin.fondoPropio)
        // Con solo el fondo a mano, el texto que se ve sale solo (claro sobre fondo oscuro)
        val soloFondo = atrio("theme31" to 1, "skin_fondo_propio" to 0xFF101820L)
        assertTrue(Color(CopiaAtrio.colorEfectivo(soloFondo, "texto")).luminance2() > 0.5f)
        // Un fondo de color de Atrio: su papel, su tinta y su acento
        val granate = atrio("skin_fondo" to "granate")
        assertEquals(FONDOS.first { it.id == "granate" }.tones.paper, CopiaAtrio.colorEfectivo(granate, "fondo"))
        assertEquals(FONDOS.first { it.id == "granate" }.tones.general, CopiaAtrio.colorEfectivo(granate, "acento"))
    }
}
