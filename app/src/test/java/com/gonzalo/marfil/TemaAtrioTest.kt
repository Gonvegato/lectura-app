package com.gonzalo.marfil

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Marfil lee los colores de Atrio por su ContentProvider y los pone (invertidos o no). Aquí, con un Atrio de mentira. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TemaAtrioTest {
    // Un Atrio con papel crema, tinta casi negra y un acento azul que el usuario habría puesto
    private val PAPEL = 0xFFF3EEE4.toInt(); private val TINTA = 0xFF15120F.toInt(); private val ACENTO = 0xFF2F5D8A.toInt()

    class AtrioFalso : ContentProvider() {
        override fun onCreate() = true
        override fun query(u: Uri, p: Array<out String>?, s: String?, a: Array<out String>?, o: String?): Cursor {
            val c = MatrixCursor(arrayOf("version", "oscuro", "paper", "crudo", "ink", "acento", "sand", "line", "edge", "muted", "oro"))
            c.addRow(arrayOf<Any>(1, 0, 0xFFF3EEE4.toInt(), 0xFFFAF6EE.toInt(), 0xFF15120F.toInt(), 0xFF2F5D8A.toInt(), 0xFFE9E2D5.toInt(), 0xFFD9D0C1.toInt(), 0xFFBDB2A0.toInt(), 0xFF7C746A.toInt(), 0xFFB8861B.toInt()))
            return c
        }
        override fun getType(u: Uri): String? = null
        override fun insert(u: Uri, v: ContentValues?): Uri? = null
        override fun delete(u: Uri, s: String?, a: Array<out String>?) = 0
        override fun update(u: Uri, v: ContentValues?, s: String?, a: Array<out String>?) = 0
    }

    @org.junit.Before fun limpia() { TemaAtrio.tonos = null; TemaAtrio.aviso = "" }

    private fun conAtrio() { Robolectric.setupContentProvider(AtrioFalso::class.java, "com.gonzalo.gamerate.tema") }

    @Test fun leeLosColoresQueTieneAtrio() {
        conAtrio()
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        assertTrue(TemaAtrio.refrescar(ctx))
        val t = TemaAtrio.tonos!!
        assertEquals(PAPEL.toLong() and 0xFFFFFFFFL, t.paper); assertEquals(TINTA.toLong() and 0xFFFFFFFFL, t.ink); assertEquals(ACENTO.toLong() and 0xFFFFFFFFL, t.general)
        assertEquals("", TemaAtrio.aviso)
    }

    @Test fun pocoLectura_losPoneInvertidosYMantieneElAcento() {
        conAtrio()
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        TemaAtrio.refrescar(ctx)
        Skin.origenAtrio = true; Skin.invertido = true
        val t = Skin.tones
        assertEquals("el fondo es la tinta de Atrio", TINTA.toLong() and 0xFFFFFFFFL, t.paper)
        assertEquals("el texto es el papel de Atrio", PAPEL.toLong() and 0xFFFFFFFFL, t.ink)
        assertEquals("el acento es el de Atrio", ACENTO.toLong() and 0xFFFFFFFFL, t.general)
        Skin.invertido = false
        assertEquals("sin invertir, tal cual Atrio", PAPEL.toLong() and 0xFFFFFFFFL, Skin.tones.paper)
        Skin.origenAtrio = false
        assertNotEquals("con los de Marfil ya no es el acento de Atrio", ACENTO.toLong() and 0xFFFFFFFFL, Skin.tones.general)
    }

    @Test fun sinAtrioAvisaYUsaLosDeLectura() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        assertFalse(TemaAtrio.refrescar(ctx))
        assertTrue(TemaAtrio.aviso.isNotBlank())
        Skin.origenAtrio = true; Skin.invertido = false
        // sin colores de Atrio leídos, no se rompe: se ve la paleta de Marfil
        assertNull(TemaAtrio.tonos)
        assertNotNull(Skin.tones)
    }
}
