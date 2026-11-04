package com.gonzalo.marfil

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Lo que Marfil entiende cuando la abre otra app (Azabache) o un widget. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class IntentsTest {
    private val ctx get() = ApplicationProvider.getApplicationContext<Context>()

    @Test fun laClaveDelLibroAbreSuFicha() {
        Skin.cargar(ctx); Cuenta.cargar(ctx); Biblioteca.cargar(ctx); Biblioteca.libros.clear(); Biblioteca.borrados.clear()
        val l = Libro("abc", "Dune", "Frank Herbert", archivo = "dune.epub", anadido = 1L)
        Biblioteca.libros.add(l)
        assertEquals("abc", fichaPedidaDe(Intent().putExtra("clave", l.clave)))
        assertEquals("por id, como los widgets", "abc", fichaPedidaDe(Intent().putExtra("ficha", "abc")))
        assertNull("una clave que no está no pide nada", fichaPedidaDe(Intent().putExtra("clave", "libro:nada:nadie")))
        assertNull(fichaPedidaDe(null))
    }

    @Test fun laSeccionPedidaYLaObraDeCobalto() {
        assertEquals("escritos", irPedidoDe(Intent().putExtra("go", "escritos")))
        assertEquals("escritos:p_abc", irPedidoDe(Intent().putExtra("go", "escritos").putExtra("obra", "p_abc")))
        assertEquals("la obra solo cuenta con Escritos", "leyendo", irPedidoDe(Intent().putExtra("go", "leyendo").putExtra("obra", "p_abc")))
        assertNull(irPedidoDe(Intent()))
    }
}
