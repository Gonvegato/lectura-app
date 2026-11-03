package com.gonzalo.marfil

import android.content.Context
import android.widget.Toast

/** Abrir el lector (la página de lectura de Atrio —foliate-js y pdf.js— en una pantalla propia). */
object Lector {
    fun abrir(ctx: Context, l: Libro, goto: String? = null) {
        if (l.enOtroDispositivo) { Toast.makeText(ctx, "Este libro está en otro de tus dispositivos: su archivo aún no está aquí.", Toast.LENGTH_LONG).show(); return }
        Biblioteca.poner(l.copy(ultima = System.currentTimeMillis(), estado = if (l.estado == Estado.PENDIENTE) Estado.LEYENDO else l.estado))
        LectorActivity.abrir(ctx, l.id, goto)
    }
}
