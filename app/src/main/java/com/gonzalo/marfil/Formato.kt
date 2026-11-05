package com.gonzalo.marfil

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Fechas y cifras, iguales en Hiedra, Marfil, Cobalto y Azabache (mismas pruebas en las cuatro, `FormatoTest`):
 *  - en fichas y textos corridos: «16 oct 2026» ([fecha]);
 *  - en tablas de datos: «16/10/2026» ([fechaTabla]);
 *  - las cifras, siempre con punto de millar: «4.010 palabras», «80.000» ([cifra]).
 * Los meses van escritos aquí (no los de Android, que según la versión les pone punto: «oct.»).
 */
object Formato {
    private val MESES = listOf("ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sept", "oct", "nov", "dic")

    fun fecha(d: LocalDate): String = "${d.dayOfMonth} ${MESES[d.monthValue - 1]} ${d.year}"
    fun fecha(ms: Long): String = fecha(Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate())
    fun fechaDia(epochDay: Long): String = fecha(LocalDate.ofEpochDay(epochDay))
    /** «16 oct» (sin el año, si es de este año). */
    fun fechaSinAno(d: LocalDate, hoy: LocalDate = LocalDate.now()): String = if (d.year == hoy.year) "${d.dayOfMonth} ${MESES[d.monthValue - 1]}" else fecha(d)

    fun fechaTabla(d: LocalDate): String = "%02d/%02d/%d".format(d.dayOfMonth, d.monthValue, d.year)
    fun fechaTablaDia(epochDay: Long): String = fechaTabla(LocalDate.ofEpochDay(epochDay))

    fun cifra(n: Long): String {
        val s = kotlin.math.abs(n).toString()
        val out = StringBuilder()
        s.forEachIndexed { i, ch -> if (i > 0 && (s.length - i) % 3 == 0) out.append('.'); out.append(ch) }
        return (if (n < 0) "-" else "") + out
    }
    fun cifra(n: Int): String = cifra(n.toLong())
}
