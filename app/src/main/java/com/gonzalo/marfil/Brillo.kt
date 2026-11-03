package com.gonzalo.marfil

import android.content.Context

/**
 * El brillo del lector va en la escala de la barra de brillo de Android (perceptual) y la ventana lo quiere en luz
 * real: la misma conversión que hace Android (BrightnessUtils.convertGammaToLinear, curva HLG). 0 es el mínimo de la
 * pantalla (BRIGHTNESS_OVERRIDE_OFF: lo más bajo, sin apagarla), igual que el mínimo del sistema.
 */
internal fun brilloLineal(g: Float): Float {
    val x = g.coerceIn(0f, 1f)
    val r = if (x <= 0.5f) (x / 0.5f) * (x / 0.5f) else kotlin.math.exp((x - 0.55991073f) / 0.17883277f) + 0.28466892f
    // (nunca 0 del todo: hay móviles que con 0 apagan la luz de la pantalla en lugar de dejarla al mínimo)
    return (r / 12f).coerceIn(BRILLO_MINIMO, 1f)
}

/** El mínimo de luz que se pide (un paso de 255: lo más bajo que da la pantalla sin apagarse). */
internal const val BRILLO_MINIMO = 1f / 255f

/** Lo contrario de [brilloLineal]: de luz real (0–1) a la posición en la barra (0–1). */
internal fun brilloBarra(lineal: Float): Float {
    val r = lineal.coerceIn(0f, 1f) * 12f
    val x = if (r <= 1f) 0.5f * kotlin.math.sqrt(r) else 0.17883277f * kotlin.math.ln(r - 0.28466892f) + 0.55991073f
    return if (lineal <= BRILLO_MINIMO) 0f else x.coerceIn(0f, 1f)
}

/**
 * El brillo del móvil en luz real (0–1), o null si no se puede leer. En Android 11+ está en coma flotante
 * («screen_brightness_float»); si no, el entero de siempre, que en la mayoría va de 0 a 255 pero en algunos (Xiaomi,
 * OPPO…) llega a 1023, 2047 o 4095: se toma el tope más pequeño en el que cabe.
 */
internal fun brilloSistema(cr: android.content.ContentResolver): Float? {
    runCatching { android.provider.Settings.System.getFloat(cr, "screen_brightness_float") }.getOrNull()?.let { if (it in 0f..1f) return it }
    val v = runCatching { android.provider.Settings.System.getInt(cr, android.provider.Settings.System.SCREEN_BRIGHTNESS) }.getOrNull() ?: return null
    val tope = listOf(255, 1023, 2047, 4095, 8191).firstOrNull { v <= it } ?: return null
    return v.toFloat() / tope
}

private typealias AjustesSistema = android.provider.Settings.System

/** El brillo del móvil (el de sus ajustes): lo que cambia el lector al deslizar por el borde, con tu permiso. */
object BrilloMovil {
    /**
     * El tope de la escala entera del brillo en este móvil: 255 en casi todos; en algunos (Xiaomi, OPPO…) 1023, 2047 o
     * 4095. Se mira en la configuración del sistema y, por si acaso, en el valor de ahora (nunca por debajo de él).
     */
    fun tope(ctx: Context): Int {
        val res = android.content.res.Resources.getSystem()
        val id = res.getIdentifier("config_screenBrightnessSettingMaximum", "integer", "android")
        val cfg = if (id != 0) runCatching { res.getInteger(id) }.getOrNull()?.takeIf { it in 100..65535 } else null
        val v = runCatching { android.provider.Settings.System.getInt(ctx.contentResolver, android.provider.Settings.System.SCREEN_BRIGHTNESS) }.getOrNull() ?: 0
        val porValor = listOf(255, 1023, 2047, 4095, 8191).firstOrNull { v <= it } ?: 255
        return maxOf(cfg ?: 255, porValor)
    }

    /** Pone el brillo del móvil (luz real, 0–1). Si estaba en automático lo pasa a manual (si no, Android lo cambiaría solo): devuelve si lo hizo. */
    fun pon(ctx: Context, lineal: Float): Boolean {
        val cr = ctx.contentResolver
        var quitoAuto = false
        if (runCatching { AjustesSistema.getInt(cr, AjustesSistema.SCREEN_BRIGHTNESS_MODE) }.getOrNull() == AjustesSistema.SCREEN_BRIGHTNESS_MODE_AUTOMATIC) {
            AjustesSistema.putInt(cr, AjustesSistema.SCREEN_BRIGHTNESS_MODE, AjustesSistema.SCREEN_BRIGHTNESS_MODE_MANUAL); quitoAuto = true
        }
        val t = tope(ctx)
        AjustesSistema.putInt(cr, AjustesSistema.SCREEN_BRIGHTNESS, kotlin.math.round(lineal.coerceIn(0f, 1f) * t).toInt().coerceIn(1, t))
        return quitoAuto
    }
}
