package com.gonzalo.marfil

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Texto con la letra de títulos. */
@Composable
fun Titulo(t: String, tam: Int = 28, modifier: Modifier = Modifier, color: Color = Charcoal, lineas: Int = 2) =
    Text(t, modifier, color = color, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = tam.sp, lineHeight = (tam * 1.1f).sp, maxLines = lineas, overflow = TextOverflow.Ellipsis)

@Composable
fun Etiqueta(t: String, modifier: Modifier = Modifier, color: Color = Muted) =
    Text(t.uppercase(), modifier, color = color, fontFamily = Body, fontWeight = FontWeight.Bold, fontSize = 10.sp, letterSpacing = 1.sp)

@Composable
fun Cuerpo(t: String, modifier: Modifier = Modifier, color: Color = Muted, tam: Int = 14, lineas: Int = Int.MAX_VALUE) =
    Text(t, modifier, color = color, fontFamily = Body, fontSize = tam.sp, lineHeight = (tam * 1.4f).sp, maxLines = lineas, overflow = TextOverflow.Ellipsis)

/** Botón principal: relleno de acento, en versalitas (el «BigAction» de Atrio). */
@Composable
fun BotonAcento(texto: String, icono: ImageVector? = null, modifier: Modifier = Modifier, alto: Dp = 52.dp, alHacer: () -> Unit) {
    Row(
        modifier.heightIn(min = alto).clip(pildora()).background(Acento).clickable(onClick = alHacer).padding(horizontal = 22.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center
    ) {
        if (icono != null) { Icon(icono, null, tint = SobreAcento, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)) }
        Text(texto.uppercase(), color = SobreAcento, fontFamily = Body, fontWeight = FontWeight.Black, fontSize = 13.sp, letterSpacing = 1.5.sp, maxLines = 1)
    }
}

/**
 * El botón ancho de la ficha de Atrio («BigAction»): ocupa todo el ancho, relleno del acento (o de la tinta, el secundario), con su icono y el texto en
 * versalitas. Con las esquinas rectas de la revista son los «cuadrados anchos».
 */
@Composable
fun AccionGrande(icono: ImageVector?, texto: String, modifier: Modifier = Modifier, acento: Boolean = true, enabled: Boolean = true, alHacer: () -> Unit) {
    val fondo = if (acento) Acento else Charcoal
    val letra = if (acento) SobreAcento else Paper
    Row(
        modifier.heightIn(min = 52.dp).clip(pildora()).background(if (enabled) fondo else fondo.copy(alpha = 0.35f)).clickable(enabled = enabled, onClick = alHacer)
            .padding(horizontal = 22.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center
    ) {
        if (icono != null) { Icon(icono, null, tint = letra, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)) }
        Text(texto.uppercase(), color = letra, fontFamily = Body, fontWeight = FontWeight.Black, fontSize = 13.sp, letterSpacing = 1.5.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
    }
}

/** Botón secundario: contorno de tinta, en versalitas. */
@Composable
fun BotonSuave(texto: String, icono: ImageVector? = null, modifier: Modifier = Modifier, peligro: Boolean = false, alHacer: () -> Unit) {
    val col = if (peligro) Acento else Charcoal
    Row(
        modifier.heightIn(min = 44.dp).clip(pildora()).border(1.5.dp, col, pildora()).clickable(onClick = alHacer).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center
    ) {
        if (icono != null) { Icon(icono, null, tint = col, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)) }
        Text(texto.uppercase(), color = col, fontFamily = Body, fontWeight = FontWeight.Black, fontSize = 12.sp, letterSpacing = 1.2.sp, maxLines = 1)
    }
}

/** Icono con acción. En la revista, solo el icono (sin círculo ni caja), como en Atrio. */
@Composable
fun BotonIcono(icono: ImageVector, descripcion: String, modifier: Modifier = Modifier, fondo: Color = Color.Transparent, tinta: Color = Charcoal, tam: Dp = 44.dp, alHacer: () -> Unit) {
    val conCaja = Skin.forma.esq != Esq.Recta || fondo != Color.Transparent
    Box(
        modifier.size(tam).let { if (conCaja) it.clip(if (Skin.forma.esq == Esq.Redonda) CircleShape else esquina(12.dp)).background(if (fondo == Color.Transparent) Sand else fondo) else it }
            .clickable(onClick = alHacer).semantics { contentDescription = descripcion },
        contentAlignment = Alignment.Center
    ) { Icon(icono, null, tint = tinta, modifier = Modifier.size(tam * 0.5f)) }
}

/** Filtro: texto en versalitas, el elegido en acento y subrayado (el de Atrio). */
@Composable
fun Chip(texto: String, activo: Boolean, modifier: Modifier = Modifier, cuenta: Int? = null, alHacer: () -> Unit) {
    FiltroRevista(texto + (if (cuenta != null) " $cuenta" else ""), activo, modifier.padding(end = 14.dp), alHacer)
}

/** Panel: en la revista, sin tarjeta; un filete grueso arriba separa los bloques. */
@Composable
fun Panel(modifier: Modifier = Modifier, alHacer: (() -> Unit)? = null, contenido: @Composable ColumnScope.() -> Unit) {
    val f = Skin.forma
    if (f.esq == Esq.Recta && f.borde.value == 0f) {
        val tinta = Charcoal
        Column(
            modifier.fillMaxWidth().let { if (alHacer != null) it.clickable(onClick = alHacer) else it }
                .drawBehind { drawRect(tinta, topLeft = Offset(0f, 0f), size = Size(size.width, 2.dp.toPx())) }.padding(top = 14.dp, bottom = 10.dp),
            content = contenido
        )
        return
    }
    val forma = esquina(22.dp)
    Column(
        modifier.clip(forma).background(Crudo).let { if (f.borde.value > 0f) it.border(f.borde, if (f.bordeTinta) Charcoal else Line, forma) else it }
            .let { if (alHacer != null) it.clickable(onClick = alHacer) else it }.padding(16.dp),
        content = contenido
    )
}

/** Una fila de ajuste: título, descripción y algo a la derecha. */
@Composable
fun Fila(titulo: String, descripcion: String? = null, modifier: Modifier = Modifier, alHacer: (() -> Unit)? = null, final: @Composable (() -> Unit)? = null) {
    Row(
        modifier.fillMaxWidth().let { if (alHacer != null) it.clickable(onClick = alHacer) else it }.padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(titulo, color = Charcoal, fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 19.sp)
            if (descripcion != null) Cuerpo(descripcion, tam = 12)
        }
        if (final != null) { Spacer(Modifier.width(12.dp)); final() }
    }
}

/** Interruptor (cuadrado en la revista, como el de Atrio). */
@Composable
fun Interruptor(activo: Boolean, alCambiar: (Boolean) -> Unit) {
    val fondo by animateColorAsState(if (activo) Acento else Sand, label = "sw")
    val forma = if (Skin.forma.esq == Esq.Redonda) RoundedCornerShape(50) else esquina(4.dp)
    Box(Modifier.width(48.dp).height(28.dp).clip(forma).background(fondo).border(1.dp, if (activo) Acento else Line, forma).clickable(role = Role.Switch) { alCambiar(!activo) }.padding(3.dp)) {
        Box(Modifier.align(if (activo) Alignment.CenterEnd else Alignment.CenterStart).size(22.dp).clip(if (Skin.forma.esq == Esq.Redonda) CircleShape else esquina(3.dp)).background(if (activo) SobreAcento else Muted))
    }
}
