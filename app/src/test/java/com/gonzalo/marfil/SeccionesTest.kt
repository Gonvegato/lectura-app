package com.gonzalo.marfil

import android.content.Context
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel7)
/** Las secciones: los filtros de orden de la cabecera responden también en las páginas que no son la primera. */
class SeccionesTest {
    @get:Rule val rule = createComposeRule()
    private val ctx: Context get() = ApplicationProvider.getApplicationContext()
    private fun toca(t: String) { val n = rule.onAllNodesWithText(t); n[n.fetchSemanticsNodes().indexOfFirst { it.boundsInRoot.left >= 0f && it.boundsInRoot.right <= 1080f }].performClick(); rule.waitForIdle() }
    @Test fun elOrdenEnLeidos() {
        Skin.cargar(ctx); Cuenta.cargar(ctx); Biblioteca.cargar(ctx); Biblioteca.libros.clear(); Sagas.cargar(ctx)
        Biblioteca.libros.add(Libro("a", "Dune", "Frank Herbert", estado = Estado.LEIDO, anadido = 1))
        Biblioteca.libros.add(Libro("b", "Ubik", "Philip K. Dick", estado = Estado.LEIDO, anadido = 2))
        rule.setContent { MarfilTheme { Surface(Modifier.fillMaxSize(), color = Paper) { PantSecciones(Destino.Leidos, {}, {}, {}, {}) } } }
        toca("AUTOR")
        assertEquals(OrdenLibros.AUTOR, OrdenGuardado.de(ctx, Estado.LEIDO))
    }
}
