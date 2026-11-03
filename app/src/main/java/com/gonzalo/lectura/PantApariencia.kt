package com.gonzalo.lectura

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/*
 * Ajustes → Apariencia, como la de Atrio: Tema, Fondo, Tamaño del texto, Acento y «A tu manera» (colores y letras a mano), más
 * «Colores de Atrio» (seguir en directo los de la app instalada) y «Copiar de Atrio» (lo que Atrio tiene guardado en tu cuenta).
 */

@Composable
private fun Seccion(titulo: String) {
    Spacer(Modifier.height(18.dp)); Filete(2.dp); Spacer(Modifier.height(12.dp))
    Antetitulo(titulo, Modifier.padding(bottom = 4.dp))
}

@Composable
fun SeccionApariencia() {
    val ctx = LocalContext.current
    fun guarda() = Skin.guardar(ctx)
    // Elegir algo aquí manda sobre «seguir los colores de Atrio»
    fun propio() { Skin.origenAtrio = false; Skin.invertido = false }

    // ---- Tema
    Antetitulo("Tema", Modifier.padding(bottom = 4.dp))
    listOf(1 to "Claro", 2 to "Oscuro", 0 to "Como el móvil").forEachIndexed { i, (modo, nombre) ->
        if (i > 0) Filete(1.dp, Line)
        val on = Modo.eleccion == modo
        Row(Modifier.fillMaxWidth().clickable { if (!on) { Modo.eleccion = modo; Skin.fondoId = ""; propio(); guarda() } }.padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(nombre, fontFamily = Display, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal, fontSize = 24.sp, color = if (on) Charcoal else Muted, modifier = Modifier.weight(1f))
            if (on) Text("✓", color = Acento, fontWeight = FontWeight.Black, fontSize = 20.sp)
        }
    }

    // ---- Fondo
    Seccion("Fondo")
    Text("Colores hondos y sobrios para toda la app, en lugar de papel claro u oscuro. «Papel» vuelve al tema de arriba.", color = Muted, fontFamily = Body, fontSize = 13.sp)
    Spacer(Modifier.height(12.dp))
    listOf<Fondo?>(null).plus(FONDOS).chunked(4).forEach { fila ->
        Row(Modifier.fillMaxWidth().padding(bottom = 14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            fila.forEach { f ->
                val on = Skin.fondoId == (f?.id ?: "")
                Column(Modifier.weight(1f).clickable { if (!on) { Skin.fondoId = f?.id ?: ""; Skin.fondoPropio = 0L; propio(); guarda() } }, horizontalAlignment = Alignment.CenterHorizontally) {
                    val papel = f?.tones?.cPaper ?: Color(if (Modo.eleccion == 1) 0xFFF3EEE4 else 0xFF141210)
                    Box(Modifier.fillMaxWidth().aspectRatio(1f).background(papel).border(if (on) 3.dp else 1.dp, if (on) Acento else Line), contentAlignment = Alignment.Center) {
                        Text("Aa", color = f?.tones?.cInk ?: Charcoal, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 22.sp)
                        if (on) Text("✓", color = f?.tones?.cInk ?: Charcoal, fontWeight = FontWeight.Black, fontSize = 13.sp, modifier = Modifier.align(Alignment.TopEnd).padding(6.dp))
                    }
                    Text((f?.name ?: "Papel").uppercase(), color = if (on) Charcoal else Muted, fontFamily = Body, fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp, lineHeight = 12.sp,
                        textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp), maxLines = 2)
                }
            }
            repeat(4 - fila.size) { Spacer(Modifier.weight(1f)) }
        }
    }

    // ---- Tamaño del texto
    Seccion("Tamaño del texto")
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.Bottom) {
        listOf(90 to "Pequeño", 100 to "Normal", 112 to "Grande", 125 to "Muy grande").forEach { (pct, nombre) ->
            val on = Skin.escala == pct
            Column(Modifier.weight(1f).clickable { if (!on) { Skin.escala = pct; guarda() } }.padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Aa", fontFamily = Display, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal, fontSize = (22f * pct / 100f / (Skin.escala / 100f)).sp, color = if (on) Charcoal else Muted)
                Text(nombre.uppercase(), color = if (on) Acento else Muted, fontFamily = Body, fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp, maxLines = 1, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }

    // ---- Acento
    Seccion("Acento")
    Text("El color de los antetítulos, las cifras, los enlaces y los botones.", color = Muted, fontFamily = Body, fontSize = 13.sp)
    Spacer(Modifier.height(12.dp))
    ACCENT_COLORS.chunked(4).forEach { fila ->
        Row(Modifier.fillMaxWidth().padding(bottom = 14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            fila.forEach { a ->
                val on = Skin.accentId == a.id || (a.id == "rojo" && Skin.accentId == "paleta")
                val c = Color((if (Skin.tones.darkBg) a.dark else a.light) or 0xFF000000)
                Column(Modifier.weight(1f).clickable { if (!on) { Skin.accentId = a.id; Skin.acentoPropio = 0L; propio(); guarda() } }, horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.fillMaxWidth().aspectRatio(1f).background(c).then(if (on) Modifier.border(3.dp, Charcoal) else Modifier), contentAlignment = Alignment.Center) {
                        if (on) Text("✓", color = if (a.id == "tinta") Paper else Color.White, fontWeight = FontWeight.Black, fontSize = 22.sp)
                    }
                    Text(a.name.uppercase(), color = if (on) Charcoal else Muted, fontFamily = Body, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp, modifier = Modifier.padding(top = 6.dp), maxLines = 1)
                }
            }
            repeat(4 - fila.size) { Spacer(Modifier.weight(1f)) }
        }
    }

    // ---- A tu manera
    Seccion("A tu manera")
    Text("El color que quieras para el fondo, el texto y el acento, y la letra de los títulos y del texto. Mandan sobre lo de arriba; «Quitar» vuelve a ello.", color = Muted, fontFamily = Body, fontSize = 13.sp)
    // Cada color se puede copiar de Atrio por separado (el del fondo, el del texto o el del acento)
    val ambito = rememberCoroutineScope()
    var avisoCopia by remember { mutableStateOf<String?>(null) }
    fun copiaDe(que: String, nombre: String) {
        if (!Cuenta.vinculada) { avisoCopia = "Primero vincula tu cuenta de Atrio (en «Cuenta de Atrio»)."; return }
        avisoCopia = "Leyendo lo de Atrio…"
        ambito.launch {
            avisoCopia = try {
                val a = withContext(Dispatchers.IO) { CopiaAtrio.leer() }
                if (a == null) "Atrio todavía no ha subido su apariencia a tu cuenta (ábrelo y espera a que sincronice)."
                else "Copiado el color del $nombre de Atrio: " + "#%06X".format(CopiaAtrio.copiarColor(ctx, a, que) and 0xFFFFFF)
            } catch (e: Exception) { e.message ?: "No se pudo leer" }
        }
    }
    SelectorColor("Fondo", Skin.fondoPropio, Paper, { Skin.fondoPropio = it }, { guarda() }, copiar = { copiaDe("fondo", "fondo") })
    Filete(1.dp, Line)
    SelectorColor("Texto", Skin.tintaPropia, Charcoal, { Skin.tintaPropia = it }, { guarda() }, copiar = { copiaDe("texto", "texto") })
    Filete(1.dp, Line)
    SelectorColor("Acento", Skin.acentoPropio, Acento, { Skin.acentoPropio = it }, { guarda() }, copiar = { copiaDe("acento", "acento") })
    avisoCopia?.let { Text(it, color = Acento, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 15.sp, modifier = Modifier.padding(top = 6.dp)) }
    Filete(1.dp, Line)
    val letras = remember { (DISPLAY_FONTS + BODY_FONTS).map { it.name }.distinct() }
    SelectorLetra("Letra de los títulos", Skin.display.takeIf { it != "Atrio" }.orEmpty(), letras) { Skin.display = it.ifBlank { "Atrio" }; guarda() }
    Filete(1.dp, Line)
    SelectorLetra("Letra del texto", Skin.body.takeIf { it != "Atrio" }.orEmpty(), remember { (BODY_FONTS + DISPLAY_FONTS).map { it.name }.distinct() }) { Skin.body = it.ifBlank { "Atrio" }; guarda() }

    // ---- Colores de Atrio (en directo)
    Seccion("Colores de Atrio, en directo")
    Text("Usa los colores que tienes ahora en la app de Atrio de este móvil (los que cambies allí cambian aquí), con la opción de ponerlos del revés.", color = Muted, fontFamily = Body, fontSize = 13.sp)
    Etiqueta("Seguir a Atrio", Modifier.padding(top = 10.dp))
    MagChoice(listOf("No", "Sí"), if (Skin.origenAtrio) 1 else 0) { i -> Skin.origenAtrio = i == 1; if (i == 1) Skin.fondoId = ""; guarda() }
    if (Skin.origenAtrio) {
        Etiqueta("Invertir", Modifier.padding(top = 6.dp))
        MagChoice(listOf("No", "Sí"), if (Skin.invertido) 1 else 0) { i -> Skin.invertido = i == 1; guarda() }
        LaunchedEffect(Unit) { kotlin.concurrent.thread { TemaAtrio.refrescar(ctx.applicationContext) } }
        Text(
            if (TemaAtrio.aviso.isNotBlank()) TemaAtrio.aviso + (if (TemaAtrio.tonos != null) " Se usan los últimos colores que se leyeron." else " Mientras tanto, los de Lectura.")
            else "Lectura lee los colores que tienes ahora en Atrio" + (if (Skin.invertido) " y los pone del revés: el fondo con el color del texto y el texto con el del fondo; el acento se mantiene." else "."),
            color = if (TemaAtrio.aviso.isNotBlank()) Acento else Muted, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 14.sp, lineHeight = 19.sp, modifier = Modifier.padding(top = 4.dp)
        )
    }

    // ---- Copiar de Atrio (lo guardado en tu cuenta)
    Seccion("Copiar de Atrio")
    SeccionCopiar()
}

/** Lee de tu cuenta lo que Atrio tiene (tema, fondo, acento, colores, letras, tamaño) y copia lo que marques. */
@Composable
private fun SeccionCopiar() {
    val ctx = LocalContext.current
    val ambito = rememberCoroutineScope()
    var leyendo by remember { mutableStateOf(false) }
    var aviso by remember { mutableStateOf<String?>(null) }
    var ajustes by remember { mutableStateOf<CopiaAtrio.Ajustes?>(null) }
    var marcadas by remember { mutableStateOf(setOf<String>()) }
    Text("Atrio sube su apariencia a tu cuenta. Aquí la lees y copias solo lo que quieras (no se cambia nada en Atrio).", color = Muted, fontFamily = Body, fontSize = 13.sp)
    if (!Cuenta.vinculada) {
        Text("Primero vincula tu cuenta de Atrio (en «Cuenta de Atrio», más abajo).", color = Acento, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 15.sp, modifier = Modifier.padding(top = 6.dp))
        return
    }
    MagLink(if (leyendo) "Leyendo…" else "Leer lo que tiene mi Atrio", accent = true) {
        if (!leyendo) {
            leyendo = true; aviso = null
            ambito.launch {
                try {
                    val r = withContext(Dispatchers.IO) { CopiaAtrio.leer() }
                    ajustes = r
                    marcadas = r?.let { CopiaAtrio.filas(it).map { f -> f.id }.toSet() } ?: emptySet()
                    if (r == null) aviso = "Atrio todavía no ha subido su apariencia a tu cuenta (ábrelo y espera a que sincronice)."
                } catch (e: Exception) { aviso = (e.message ?: "No se pudo leer") }
                leyendo = false
            }
        }
    }
    ajustes?.let { a ->
        CopiaAtrio.filas(a).forEach { f ->
            Row(Modifier.fillMaxWidth().clickable { marcadas = if (f.id in marcadas) marcadas - f.id else marcadas + f.id }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(22.dp).border(1.5.dp, Charcoal), contentAlignment = Alignment.Center) { if (f.id in marcadas) Text("✓", color = Acento, fontWeight = FontWeight.Black, fontSize = 16.sp) }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(f.titulo, color = Charcoal, fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 19.sp)
                    Text(f.valor.uppercase(), color = Muted, fontFamily = Body, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                }
            }
            Filete(1.dp, Line)
        }
        MagLink("Copiar lo marcado", "Se aplica a Lectura ahora", accent = true) {
            if (marcadas.isNotEmpty()) { CopiaAtrio.aplicar(ctx, a, marcadas); aviso = "Copiado a Lectura." }
        }
    }
    aviso?.let { Text(it, color = Acento, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 15.sp, modifier = Modifier.padding(top = 4.dp)) }
}

/** Una letra elegida a mano: su nombre escrito en ella; al tocarla, la lista de letras (cada una en sí misma). */
@Composable
fun SelectorLetra(nombre: String, valor: String, opciones: List<String>, onCambia: (String) -> Unit) {
    var abierto by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().clickable { abierto = true }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("Aa", fontFamily = valor.takeIf { it.isNotBlank() }?.let { Skin.family(it) } ?: Display, fontSize = 28.sp, color = Charcoal, modifier = Modifier.width(50.dp))
        Column(Modifier.weight(1f)) {
            Text(nombre, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 22.sp, color = Charcoal)
            Text(valor.ifEmpty { "La de Lectura" }, color = Muted, fontFamily = Body, fontSize = 12.sp)
        }
        if (valor.isNotEmpty()) EnlaceRevista("Quitar", Modifier.padding(end = 12.dp)) { onCambia("") }
        Text("▾", color = Muted, fontSize = 16.sp)
    }
    if (abierto) AlertDialog(
        onDismissRequest = { abierto = false }, containerColor = Paper, shape = esquina(4.dp),
        title = { Text(nombre, color = Charcoal, fontFamily = Display, fontWeight = FontWeight.Bold) },
        text = {
            LazyColumn(Modifier.heightIn(max = 460.dp)) {
                item {
                    Text("La de Lectura", fontSize = 18.sp, color = if (valor.isEmpty()) Acento else Charcoal, modifier = Modifier.fillMaxWidth().clickable { onCambia(""); abierto = false }.padding(vertical = 10.dp))
                }
                items(opciones) { o ->
                    // (cada letra se baja de Google Fonts la primera vez: hasta entonces sale con la del sistema)
                    Text(o, fontFamily = Skin.family(o), fontSize = 20.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = if (o == valor) Acento else Charcoal,
                        modifier = Modifier.fillMaxWidth().clickable { onCambia(o); abierto = false }.padding(vertical = 10.dp))
                }
            }
        },
        confirmButton = { TextButton({ abierto = false }) { Text("CERRAR", color = Acento, fontFamily = Body, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp) } }
    )
}
