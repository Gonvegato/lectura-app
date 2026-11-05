package com.gonzalo.marfil

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/** Fechas y cifras: LOS MISMOS CASOS en las cuatro apps (si cambias uno, cámbialo en todas). */
class FormatoTest {
    @Test fun fechas() {
        assertEquals("16 oct 2026", Formato.fecha(LocalDate.of(2026, 10, 16)))
        assertEquals("2 sept 2026", Formato.fecha(LocalDate.of(2026, 9, 2)))
        assertEquals("16/10/2026", Formato.fechaTabla(LocalDate.of(2026, 10, 16)))
        assertEquals("06/09/2026", Formato.fechaTabla(LocalDate.of(2026, 9, 6)))
        assertEquals("16 oct", Formato.fechaSinAno(LocalDate.of(2026, 10, 16), LocalDate.of(2026, 11, 5)))
        assertEquals("16 oct 2025", Formato.fechaSinAno(LocalDate.of(2025, 10, 16), LocalDate.of(2026, 11, 5)))
    }

    @Test fun cifras() {
        assertEquals("4.010", Formato.cifra(4010)); assertEquals("80.000", Formato.cifra(80_000))
        assertEquals("999", Formato.cifra(999)); assertEquals("1.234.567", Formato.cifra(1_234_567L)); assertEquals("0", Formato.cifra(0))
    }
}
