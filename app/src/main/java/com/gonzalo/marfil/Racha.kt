package com.gonzalo.marfil

/**
 * Los «días seguidos» de la familia: la MISMA regla en Hiedra, Marfil, Cobalto y Azabache (está en el docs/CONTRATO-DATOS.md de cada una).
 *
 *  - Un día cuenta si ese día jugaste o leíste al menos un minuto ([MINUTO_MS]), o escribiste al menos una palabra ([PALABRAS]).
 *    Cada app mira lo suyo; Azabache, lo de las tres (basta con una).
 *  - La racha son los días seguidos que cuentan hasta hoy; si hoy todavía no cuenta, hasta ayer (no se rompe a primera hora).
 */
object Racha {
    const val MINUTO_MS = 60_000L
    const val PALABRAS = 1

    fun cuentaTiempo(ms: Long): Boolean = ms >= MINUTO_MS
    fun cuentaPalabras(n: Int): Boolean = n >= PALABRAS

    /** Días seguidos que cuentan, con [hoy] y los días como número de día (epochDay). */
    fun dias(hoy: Long, cuenta: (Long) -> Boolean): Int {
        var d = if (cuenta(hoy)) hoy else hoy - 1
        var n = 0
        while (cuenta(d)) { n++; d-- }
        return n
    }
}
