package com.gonzalo.marfil

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.concurrent.ConcurrentHashMap
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import kotlin.math.roundToInt
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush

/*
 * Las piezas de la interfaz «Revista» de Atrio (RevistaUI.kt, Revista.kt, Ediciones.kt, Vitrina.kt), tal cual y con los mismos
 * nombres: papel, serif, filetes, antetítulos en versalitas con su cuadradito de acento, pestañas subrayadas, enlaces con flecha,
 * cifras enormes y puntos guía. Todas las pantallas de Marfil usan estas, para que se vean como las de Atrio.
 */

/** Filete de la revista (horizontal). */
@Composable
fun Filete(alto: Dp = 2.dp, color: Color = Charcoal, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(alto).background(color))
}

/** El filete bajo la cabecera. */
@Composable
fun FileteCabecera(alto: Dp = 3.dp) = Filete(alto)

/** Antetítulo de casilla o de sección: cuadradito de acento y versalitas espaciadas. */
@Composable
fun Antetitulo(texto: String, modifier: Modifier = Modifier, tam: TextUnit = 13.sp, color: Color = Charcoal, conCuadro: Boolean = true) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        if (conCuadro) { Box(Modifier.size((tam.value * 0.77f).dp).background(Acento)); Spacer(Modifier.width(8.dp)) }
        Text(texto.uppercase(), color = color, fontFamily = Body, fontSize = tam, fontWeight = FontWeight.Black, letterSpacing = (tam.value * 0.15f).sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Un enlace de acción en versalitas («BUSCAR», «EDITAR», «+ AÑADIR»…). */
@Composable
fun EnlaceRevista(texto: String, modifier: Modifier = Modifier, acento: Boolean = false, onClick: () -> Unit) {
    Text(texto.uppercase(), color = if (acento) Acento else Charcoal, fontFamily = Body, fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 1.4.sp, maxLines = 1,
        modifier = modifier.semantics { role = Role.Button }.clickable(onClick = onClick).padding(vertical = 10.dp))
}

/** «← VOLVER» (o «← LECTURA» en lo que cuelga de la portada). */
@Composable
fun VolverRevista(onClick: () -> Unit, texto: String = "Volver") = EnlaceRevista("← $texto", onClick = onClick)

/** Las pestañas de una página: versalitas; la elegida en tinta con el subrayado de acento; su número, en acento. */
@Composable
fun PestanasRevista(nombres: List<String>, sel: Int, modifier: Modifier = Modifier, cuentas: List<Int?>? = null, onSel: (Int) -> Unit) {
    // La fila se desplaza sola hasta la pestaña elegida si no se ve entera (al entrar en «Sagas» desde la portada, por ejemplo)
    val desplazamiento = rememberScrollState()
    val sitios = remember { mutableStateMapOf<Int, Pair<Int, Int>>() }   // pestaña → (x, ancho), en coordenadas del contenido
    var anchoFila by remember { mutableIntStateOf(0) }
    LaunchedEffect(sel, sitios[sel], anchoFila) {
        val (x, w) = sitios[sel] ?: return@LaunchedEffect
        if (anchoFila <= 0) return@LaunchedEffect
        if (x < desplazamiento.value || x + w > desplazamiento.value + anchoFila) desplazamiento.animateScrollTo((x + w / 2 - anchoFila / 2).coerceIn(0, desplazamiento.maxValue))
    }
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().onGloballyPositioned { anchoFila = it.size.width }.drawWithContent {
                // Un degradado en los bordes cuando hay más pestañas por un lado o por otro (para que no parezcan cortadas)
                drawContent()
                val f = 28.dp.toPx()
                if (desplazamiento.value > 0) drawRect(Brush.horizontalGradient(listOf(Paper, Color.Transparent), 0f, f), Offset.Zero, Size(f, size.height))
                if (desplazamiento.value < desplazamiento.maxValue) drawRect(Brush.horizontalGradient(listOf(Color.Transparent, Paper), size.width - f, size.width), Offset(size.width - f, 0f), Size(f, size.height))
            }.horizontalScroll(desplazamiento), horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.Bottom) {
            nombres.forEachIndexed { i, n ->
                val on = i == sel
                Row(Modifier.onGloballyPositioned { c -> sitios[i] = (c.positionInParent().x + desplazamiento.value).roundToInt() to c.size.width }
                    .semantics { selected = on; role = Role.Tab }.clickable { onSel(i) }
                    .drawBehind { if (on) drawRect(Acento, topLeft = Offset(0f, size.height - 3.dp.toPx()), size = Size(size.width, 3.dp.toPx())) }
                    .padding(top = 12.dp, bottom = 10.dp), verticalAlignment = Alignment.Bottom) {
                    Text(n.uppercase(), color = if (on) Charcoal else Muted, fontFamily = Body, fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 1.6.sp, maxLines = 1)
                    cuentas?.getOrNull(i)?.let { c -> Text(" $c", color = Acento, fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, maxLines = 1) }
                }
            }
        }
        Filete(1.dp, Line)
    }
}

/** Filtros u opciones de una sola elección, en una fila que se desliza: versalitas; el elegido en acento y subrayado. */
@Composable
fun FiltrosRevista(opciones: List<String>, sel: Int, modifier: Modifier = Modifier, onSel: (Int) -> Unit) {
    val desplazamiento = rememberScrollState()
    Row(modifier.fillMaxWidth().drawWithContent {
            // Un degradado en los bordes cuando hay más opciones por un lado o por otro (para que no parezcan cortadas)
            drawContent()
            val f = 28.dp.toPx()
            if (desplazamiento.value > 0) drawRect(Brush.horizontalGradient(listOf(Paper, Color.Transparent), 0f, f), Offset.Zero, Size(f, size.height))
            if (desplazamiento.value < desplazamiento.maxValue) drawRect(Brush.horizontalGradient(listOf(Color.Transparent, Paper), size.width - f, size.width), Offset(size.width - f, 0f), Size(f, size.height))
        }.horizontalScroll(desplazamiento), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        opciones.forEachIndexed { i, o -> FiltroRevista(o, i == sel) { onSel(i) } }
    }
}

/** Un filtro suelto (que se enciende y se apaga): versalitas, encendido en acento y subrayado. */
@Composable
fun FiltroRevista(texto: String, on: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Text(texto.uppercase(), color = if (on) Acento else Charcoal, fontFamily = Body, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp, maxLines = 1,
        modifier = modifier.semantics { selected = on }.clickable(onClick = onClick).padding(top = 8.dp, bottom = 13.dp)
            .drawBehind { if (on) drawRect(Acento, topLeft = Offset(0f, size.height - 4.dp.toPx()), size = Size(size.width, 1.5.dp.toPx())) })
}

/** Barra de arriba de las páginas: «← Volver» a la izquierda y los enlaces de la derecha. */
@Composable
fun RevistaTopBar(onBack: () -> Unit, texto: String = "Volver", actions: @Composable RowScope.() -> Unit = {}) {
    Row(Modifier.fillMaxWidth().statusBarsPadding().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        VolverRevista(onBack, texto)
        Spacer(Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically, content = actions)
    }
}

/** Tamaño del titular de revista según lo largo que sea. */
fun headlineSize(title: String): Pair<TextUnit, TextUnit> {
    val longest = title.split(' ').maxOfOrNull { it.length } ?: 0
    return when {
        title.length <= 8 && longest <= 8 -> 86.sp to 84.sp
        title.length <= 16 && longest <= 10 -> 66.sp to 66.sp
        title.length <= 30 && longest <= 13 -> 50.sp to 52.sp
        else -> 38.sp to 41.sp
    }
}

/** Titular de ficha: antetítulo de acento y el nombre enorme, a todo lo ancho. */
@Composable
fun RevistaHeadline(title: String, overline: String?) {
    val (fs, lh) = headlineSize(title)
    Column(Modifier.fillMaxWidth()) {
        if (overline != null) Text(overline.uppercase(), color = Acento, fontFamily = Body, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        FitTitle(title, fs, lh, letterSpacing = (-1.5).sp)
    }
}

/** La cabecera de la revista (el nombre, enorme) en una sola línea. */
@Composable
fun Cabecera(tam: TextUnit, modifier: Modifier = Modifier, color: Color = Charcoal, texto: String = "MARFIL") {
    Box(modifier.fillMaxWidth()) {
        FitTitle(texto, tam, tam, color = color, letterSpacing = (-0.03).em, maxLines = 1, modifier = Modifier.fillMaxWidth(), minSize = 12f, unaLinea = true)
    }
}
private val Float.em get() = androidx.compose.ui.unit.TextUnit(this, androidx.compose.ui.unit.TextUnitType.Em)
private val Double.em get() = this.toFloat().em

/** Un enlace de texto (serif, con flecha): «Cambiar emulador →». */
@Composable
fun MagLink(text: String, sub: String? = null, accent: Boolean = false, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 5.dp)) {
        Text("$text →", fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 21.sp, color = if (accent) Acento else Charcoal)
        if (sub != null) Text(sub.uppercase(), color = Muted, fontFamily = Body, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
    }
}

/** Elegir una de pocas opciones: mayúsculas pequeñas, la elegida en acento y subrayada. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MagChoice(options: List<String>, selected: Int, label: String? = null, onPick: (Int) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        if (label != null) Text(label.uppercase(), color = Muted, fontFamily = Body, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            options.forEachIndexed { i, o ->
                val on = i == selected
                Text(
                    o.uppercase(), color = if (on) Acento else Charcoal, fontFamily = Body, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp,
                    modifier = Modifier.clickable(enabled = !on) { onPick(i) }.padding(vertical = 8.dp)
                        .drawBehind { if (on) drawRect(Acento, topLeft = Offset(0f, size.height - 6.dp.toPx()), size = Size(size.width, 1.5.dp.toPx())) }
                )
            }
        }
    }
}

/** Una línea dato · valor con puntos guía (como el índice de una revista). */
@Composable
fun MagFact(label: String, value: String) {
    Layout(
        content = {
            Text(label, fontFamily = Body, fontSize = 14.sp, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Box(Modifier.drawBehind {
                drawLine(Muted.copy(alpha = 0.6f), Offset(0f, size.height - 2), Offset(size.width, size.height - 2), 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(2f, 6f)))
            })
            Text(value, fontFamily = Body, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Charcoal, maxLines = 1, softWrap = false)
        },
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
    ) { ms, c ->
        val v = ms[2].measure(Constraints())
        val hueco = 6.dp.roundToPx()
        val ancho = if (c.hasBoundedWidth) c.maxWidth else null
        val l = ms[0].measure(Constraints(maxWidth = ancho?.let { ((it - v.width) * 0.75f).toInt().coerceAtLeast(0) } ?: Constraints.Infinity))
        val w = ancho ?: (l.width + v.width + hueco * 6)
        val puntos = (w - l.width - v.width - hueco * 2).coerceAtLeast(0)
        val h = maxOf(l.height, v.height)
        val d = ms[1].measure(Constraints.fixed(puntos, 12.dp.roundToPx().coerceAtMost(h)))
        layout(w, h) {
            l.place(0, h - l.height)
            d.place(l.width + hueco, h - d.height - 3.dp.roundToPx())
            v.place(w - v.width, h - v.height)
        }
    }
}

/** Final de la ficha: «Editar» grande, como pie de página. */
@Composable
fun MagFooter(text: String = "Editar", onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().height(3.dp).background(Charcoal))
        Text("${text.uppercase()} →", color = Acento, fontFamily = Body, fontSize = 14.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp,
            modifier = Modifier.clickable(onClick = onClick).padding(vertical = 18.dp))
    }
}

/** Una línea de la lista: título en serif, datos en versalitas y, a la derecha, lo que se pase. */
@Composable
fun FilaRevista(titulo: String, meta: String, modifier: Modifier = Modifier, derecha: @Composable RowScope.() -> Unit = {}, onClick: () -> Unit) {
    Row(modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            FitTitle(titulo, 26.sp, 28.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (meta.isNotBlank()) Text(meta.uppercase(), color = Muted, fontFamily = Body, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, maxLines = 2, modifier = Modifier.padding(top = 3.dp))
        }
        derecha()
    }
    Filete(1.dp, Line)
}

/**
 * Titular que nunca parte una palabra: si la palabra más larga no cabe a lo ancho, baja el tamaño de la letra
 * (y el interlineado en proporción) hasta que quepa. Es el de Atrio.
 */
@Composable
fun FitTitle(
    text: String, fontSize: TextUnit, lineHeight: TextUnit,
    modifier: Modifier = Modifier, color: Color = Color.Unspecified,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    fontWeight: FontWeight = FontWeight.Bold, maxLines: Int = Int.MAX_VALUE, overflow: TextOverflow = TextOverflow.Clip,
    fontStyle: FontStyle? = null, minSize: Float = 14f,
    family: FontFamily = Display, align: TextAlign? = null,
    unaLinea: Boolean = false,
) {
    val claveAncho = "$text|${fontSize.value}"
    var maxPx by remember { mutableStateOf(anchosFit[claveAncho] ?: 0) }
    val measurer = rememberTextMeasurer()
    fun estilo(s: Float) = TextStyle(fontFamily = family, fontWeight = fontWeight, fontSize = s.sp, letterSpacing = letterSpacing, fontStyle = fontStyle)
    fun ajusta(ancho: Int): Float {
        if (ancho <= 0 || ancho == Constraints.Infinity) return fontSize.value
        val words = if (unaLinea) listOf(text) else text.split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.isEmpty()) return fontSize.value
        var s = fontSize.value
        while (s > minSize) {
            val widest = words.maxOf { measurer.measure(it, estilo(s), softWrap = false, maxLines = 1).size.width }
            if (widest <= ancho * 0.97f) break
            s -= 1f
        }
        return s
    }
    val size = remember(text, fontSize, maxPx, family, fontWeight, letterSpacing, unaLinea) { ajusta(maxPx) }
    fun alto(s: Float) = if (lineHeight.isSp) (lineHeight.value * s / fontSize.value).sp else lineHeight
    Text(
        text, color = color, fontFamily = family, fontWeight = fontWeight, fontStyle = fontStyle, fontSize = size.sp,
        lineHeight = alto(size), letterSpacing = letterSpacing, maxLines = maxLines, overflow = overflow, textAlign = align,
        modifier = modifier.layout { measurable, constraints ->
            val w = constraints.maxWidth
            if (w != maxPx) { maxPx = w; if (w > 0 && w != Constraints.Infinity) anchosFit[claveAncho] = w }
            val pl = measurable.measure(constraints)
            layout(pl.width, pl.height) { pl.place(0, 0) }
        }
    )
}
private val anchosFit = ConcurrentHashMap<String, Int>()
