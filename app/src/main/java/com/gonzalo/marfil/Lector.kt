package com.gonzalo.marfil

import android.content.Context

/** Abrir el lector (la página de lectura de Atrio —foliate-js y pdf.js— en una pantalla propia). */
object Lector {
    fun abrir(ctx: Context, l: Libro, goto: String? = null) {
        if (l.enOtroDispositivo) { NubeLibros.abrirBajando(ctx.applicationContext, l, goto); return }
        Biblioteca.poner(l.copy(ultima = System.currentTimeMillis(), estado = if (l.estado == Estado.PENDIENTE || l.estado == Estado.ABANDONADO) Estado.LEYENDO else l.estado))
        LectorActivity.abrir(ctx, l.id, goto)
    }
}

/** Qué es un salto largo en el lector: un cuarto del libro o más de golpe (del 98 % al 2 %, o al revés). Puro (se prueba). */
object Salto {
    const val MINIMO = 0.25
    fun hay(desde: Double, hasta: Double): Boolean = kotlin.math.abs(hasta - desde) >= MINIMO - 1e-9
}
