package com.gonzalo.lectura

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * Apariencia → A tu manera (como en Atrio): el color que quieras para el fondo, el texto y el acento (tono, saturación y brillo, o su
 * código). Se ve al momento; se guarda al soltar. Todo lo demás (tarjetas, líneas, grises) sale de ellos.
 */

private fun hexDe(c: Color) = "#%06X".format(c.toArgb() and 0xFFFFFF)
private fun largo(c: Color) = c.toArgb().toLong() and 0xFFFFFFFFL

/** Un color elegido a mano: la muestra y su código; al tocarlo se abren el tono, la saturación, el brillo y el código. */
@Composable
fun SelectorColor(nombre: String, valor: Long, actual: Color, onCambia: (Long) -> Unit, onGuarda: () -> Unit, copiar: (() -> Unit)? = null) {
    var abierto by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().clickable { abierto = !abierto }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(36.dp).background(actual).border(1.dp, Charcoal))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(nombre, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 22.sp, color = Charcoal)
            Text(if (valor == 0L) "Automático" else hexDe(Color(valor)), color = Muted, fontFamily = Body, fontSize = 12.sp)
        }
        if (copiar != null) EnlaceRevista("Copiar de Atrio", Modifier.padding(end = 12.dp)) { copiar() }
        if (valor != 0L) EnlaceRevista("Quitar", Modifier.padding(end = 12.dp)) { onCambia(0L); onGuarda(); abierto = false }
        Text(if (abierto) "▴" else "▾", color = Muted, fontSize = 16.sp)
    }
    if (!abierto) return
    // Tono, saturación y brillo de lo que se ve ahora (el automático, si aún no hay ninguno elegido)
    val hsv = remember { FloatArray(3).also { android.graphics.Color.colorToHSV(actual.toArgb(), it) } }
    var h by remember { mutableFloatStateOf(hsv[0]) }
    var s by remember { mutableFloatStateOf(hsv[1]) }
    var v by remember { mutableFloatStateOf(hsv[2]) }
    var texto by remember { mutableStateOf(hexDe(actual)) }
    fun pon() { val c = Color.hsv(h, s, v); texto = hexDe(c); onCambia(largo(c)) }
    Column(Modifier.fillMaxWidth().padding(bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Barra("Tono", h / 360f, Brush.horizontalGradient((0..6).map { Color.hsv(it * 60f % 360f, 1f, 1f) }), { h = it * 360f; pon() }, onGuarda)
        Barra("Saturación", s, Brush.horizontalGradient(listOf(Color.hsv(h, 0f, v), Color.hsv(h, 1f, v))), { s = it; pon() }, onGuarda)
        Barra("Brillo", v, Brush.horizontalGradient(listOf(Color.Black, Color.hsv(h, s, 1f))), { v = it; pon() }, onGuarda)
        Etiqueta("Código (#RRGGBB)", Modifier.padding(top = 6.dp))
        CampoCodigo(texto) { t, c ->
            texto = t
            if (c != null) {
                FloatArray(3).also { android.graphics.Color.colorToHSV(c.toArgb(), it) }.let { x -> h = x[0]; s = x[1]; v = x[2] }
                onCambia(largo(c)); onGuarda()
            }
        }
    }
}

/** Una barra con su degradado debajo (tono, saturación o brillo). */
@Composable
private fun Barra(nombre: String, valor: Float, fondo: Brush, onCambia: (Float) -> Unit, onSuelta: () -> Unit) {
    Column {
        Text(nombre.uppercase(), color = Muted, fontFamily = Body, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp)
        Box(contentAlignment = Alignment.Center) {
            Box(Modifier.fillMaxWidth().padding(horizontal = 10.dp).height(12.dp).background(fondo).border(1.dp, Line))
            Slider(valor, onCambia, onValueChangeFinished = onSuelta, modifier = Modifier.fillMaxWidth(),
                colors = SliderDefaults.colors(thumbColor = Charcoal, activeTrackColor = Color.Transparent, inactiveTrackColor = Color.Transparent))
        }
    }
}

/** Lo que vale como código de color: «#RRGGBB», «RRGGBB», «#RGB» o con la opacidad delante («#FFRRGGBB»); null si no es un color. */
fun colorDeCodigo(t: String): Color? {
    val limpio = t.trim().removePrefix("#").removePrefix("0x").removePrefix("0X").uppercase()
    if (limpio.isEmpty() || limpio.any { it !in "0123456789ABCDEF" }) return null
    val seis = when (limpio.length) {
        3 -> limpio.map { "$it$it" }.joinToString("")
        6 -> limpio
        8 -> limpio.drop(2)
        else -> return null
    }
    return Color(("FF$seis").toLong(16))
}

/**
 * El campo del código de un color: acepta lo que escribas o pegues (mayúsculas o minúsculas, con o sin #, también de 3 cifras) y aplica el color
 * en cuanto es válido. Sin tocar lo que escribes mientras tanto (así el teclado no se lía) y sin límite que impida corregir.
 */
@Composable
fun CampoCodigo(valor: String, alCambiar: (String, Color?) -> Unit) {
    var campo by remember { mutableStateOf(androidx.compose.ui.text.input.TextFieldValue(valor)) }
    // Si el color cambia desde fuera (las barras), el campo enseña su código
    LaunchedEffect(valor) { if (valor != campo.text && colorDeCodigo(campo.text)?.let { hexDe(it) } != valor) campo = androidx.compose.ui.text.input.TextFieldValue(valor) }
    Column(Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            if (campo.text.isEmpty()) Text("#RRGGBB", color = Muted, fontFamily = Display, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic, fontSize = 18.sp)
            androidx.compose.foundation.text.BasicTextField(
                campo, { n -> campo = n; alCambiar(n.text, colorDeCodigo(n.text)) }, singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(color = Charcoal, fontFamily = Display, fontSize = 18.sp),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(Acento),
                keyboardOptions = KeyboardOptions(capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Characters, keyboardType = androidx.compose.ui.text.input.KeyboardType.Ascii, autoCorrect = false),
                modifier = Modifier.fillMaxWidth()
            )
        }
        Filete(1.5.dp)
        Text(if (colorDeCodigo(campo.text) != null) "Aplicado" else "Escribe o pega un código de 3, 6 u 8 cifras", color = Muted, fontFamily = Body, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
    }
}
