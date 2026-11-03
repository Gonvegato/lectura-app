package com.gonzalo.lectura

import androidx.compose.ui.graphics.Color
import org.junit.Assert.*
import org.junit.Test

class TemaTest {
    private val revista = PALETTES.first { it.id == "revista" }

    @Test fun invertirCambiaFondoPorTextoYDejaElAcento() {
        for (t in listOf(revista.light, revista.dark)) {
            val i = invertir(t)
            assertEquals("el fondo pasa a ser el color del texto", t.ink or 0xFF000000, i.paper)
            assertEquals("el texto pasa a ser el color del fondo", t.paper or 0xFF000000, i.ink)
            assertEquals("el acento no cambia", t.general, i.general)
            assertEquals("el oro tampoco", t.gold, i.gold)
        }
    }

    @Test fun invertirDosVecesVuelveAlFondoYAlTextoDeOrigen() {
        val t = revista.light
        val d = invertir(invertir(t))
        assertEquals(t.paper or 0xFF000000, d.paper); assertEquals(t.ink or 0xFF000000, d.ink); assertEquals(t.general, d.general)
    }

    @Test fun elClaroSeVuelveOscuroYViceversa() {
        assertFalse(revista.light.darkBg); assertTrue(invertir(revista.light).darkBg)
        assertTrue(revista.dark.darkBg); assertFalse(invertir(revista.dark).darkBg)
    }

    @Test fun losGrisesSeRecalculanEntreLosNuevosColores() {
        val i = invertir(revista.light)
        // la línea fina queda entre el fondo nuevo (oscuro) y el texto nuevo (claro): ni uno ni otro
        assertNotEquals(i.cPaper, i.cLine); assertNotEquals(i.cInk, i.cLine)
        assertTrue(i.cLine.red > i.cPaper.red && i.cLine.red < i.cInk.red)
        // y el texto se lee sobre el fondo: gran diferencia de luminosidad
        val lum = { c: Color -> 0.299f * c.red + 0.587f * c.green + 0.114f * c.blue }
        assertTrue(kotlin.math.abs(lum(i.cInk) - lum(i.cPaper)) > 0.7f)
    }

    @Test fun todasLasPaletasSeInviertenSinPerderElAcento() {
        for (p in PALETTES) for (t in listOf(p.light, p.dark)) assertEquals(t.general, invertir(t).general)
    }

    private val base = PALETTES.first { it.id == "revista" }.light

    @Test fun losColoresAMano() {
        // Sin nada elegido, no cambia nada
        assertEquals(base, Skin.conPropios(base, 0L, 0L, 0L))
        // Un fondo oscuro: el texto pasa solo a claro, y las tarjetas, líneas y grises salen de los dos
        val oscuro = Skin.conPropios(base, 0xFF101820, 0L, 0L)
        assertEquals(0xFF101820, oscuro.paper); assertTrue(Color(oscuro.ink).luminance2() > 0.5f); assertEquals(0L, oscuro.sand)
        assertEquals("el acento se queda", base.general, oscuro.general)
        // Un fondo claro: texto oscuro
        assertTrue(Color(Skin.conPropios(base, 0xFFF4F4F4, 0L, 0L).ink).luminance2() < 0.5f)
        // Texto y acento elegidos a mano mandan
        val m = Skin.conPropios(base, 0xFF101820, 0xFFFFD166, 0xFF06D6A0)
        assertEquals(0xFFFFD166, m.ink); assertEquals(0xFF06D6A0, m.general)
        // Solo el acento: fondo y texto intactos
        val a = Skin.conPropios(base, 0L, 0L, 0xFF06D6A0)
        assertEquals(base.paper, a.paper); assertEquals(base.ink, a.ink); assertEquals(0xFF06D6A0, a.general)
    }
}
