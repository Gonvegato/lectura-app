package com.gonzalo.marfil

import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon

/**
 * El atajo del icono de la app (mantenerlo pulsado): «Seguir leyendo» el último libro que estás leyendo, directo a tu página.
 * Se pone al abrir la app y al cerrar el lector; si no hay ningún libro leyéndose con su archivo aquí, se quita.
 */
object AtajoLectura {
    private const val ID = "seguir_leyendo"

    /** El libro del atajo: el que estás leyendo y abriste más recientemente, con su archivo en este dispositivo. */
    fun libro(libros: List<Libro>): Libro? = libros.filter { it.estado == Estado.LEYENDO && !it.enOtroDispositivo }.maxByOrNull { it.ultima }

    fun pon(ctx: Context) = runCatching {
        val sm = ctx.getSystemService(ShortcutManager::class.java) ?: return@runCatching
        val l = libro(Biblioteca.libros.toList())
        if (l == null) { sm.removeDynamicShortcuts(listOf(ID)); return@runCatching }
        val intent = Intent(ctx, MainActivity::class.java).setAction(Intent.ACTION_VIEW).putExtra("leer", l.id).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val corto = if (l.titulo.length > 22) l.titulo.take(21).trimEnd() + "…" else l.titulo
        val sc = ShortcutInfo.Builder(ctx, ID)
            .setShortLabel(corto)
            .setLongLabel("Seguir leyendo «${l.titulo}»" + if (l.fraccion > 0.005f) " · ${(l.fraccion * 100).toInt()} %" else "")
            .setIcon(Icon.createWithResource(ctx, R.drawable.sc_book))
            .setIntent(intent)
            .setRank(0)
            .build()
        sm.dynamicShortcuts = listOf(sc)
    }
}
