package com.gonzalo.marfil

import org.junit.Assert.assertEquals
import org.junit.Test

/** La regla común de la racha, con LOS MISMOS CASOS en las cuatro apps (si cambias uno, cámbialo en todas). */
class RachaTest {
    private val h = 20_762L // 5 nov 2026 (da igual cuál)
    private fun tiempo(m: Map<Long, Long>) = Racha.dias(h) { Racha.cuentaTiempo(m[it] ?: 0L) }
    private fun palabras(m: Map<Long, Int>) = Racha.dias(h) { Racha.cuentaPalabras(m[it] ?: 0) }

    @Test fun unMinutoCuentaYMenosNo() {
        assertEquals(2, tiempo(mapOf(h to 0L, h - 1 to 60_000L, h - 2 to 120_000L, h - 3 to 59_999L, h - 4 to 600_000L)))
        assertEquals(1, tiempo(mapOf(h to 30_000L, h - 1 to 60_000L)))
    }

    @Test fun hoyCuentaSiYaLlega() {
        assertEquals(2, tiempo(mapOf(h to 60_000L, h - 1 to 60_000L)))
        assertEquals(0, tiempo(emptyMap()))
    }

    @Test fun unaPalabraCuenta() {
        assertEquals(2, palabras(mapOf(h to 0, h - 1 to 1, h - 2 to 5, h - 3 to 0, h - 4 to 9)))
        assertEquals(3, palabras(mapOf(h to 120, h - 1 to 1, h - 2 to 5)))
    }
}
