package com.gonzalo.lectura

import android.content.Context
import android.widget.Toast

/** El lector. (En obras: la página del lector de Atrio —foliate-js y pdf.js— llega en el siguiente paso.) */
object Lector {
    fun abrir(ctx: Context, l: Libro) {
        if (l.enOtroDispositivo) { Toast.makeText(ctx, "Este libro está en otro de tus dispositivos: su archivo aún no está aquí.", Toast.LENGTH_LONG).show(); return }
        Biblioteca.poner(l.copy(ultima = System.currentTimeMillis(), estado = if (l.estado == Estado.PENDIENTE) Estado.LEYENDO else l.estado))
        Toast.makeText(ctx, "El lector llega en el siguiente paso.", Toast.LENGTH_SHORT).show()
    }
}
