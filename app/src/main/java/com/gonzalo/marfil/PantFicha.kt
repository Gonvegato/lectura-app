package com.gonzalo.marfil

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.DateFormat
import java.util.Date

/** Dos casillas de revista, una al lado de la otra, con su filete en medio. */
@Composable
private fun DosCasillas(izq: @Composable ColumnScope.() -> Unit, der: @Composable ColumnScope.() -> Unit) {
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Column(Modifier.weight(1f).padding(top = 16.dp, bottom = 18.dp, end = 14.dp), content = izq)
        Box(Modifier.fillMaxHeight().width(1.5.dp).background(Charcoal))
        Column(Modifier.weight(1f).padding(top = 16.dp, bottom = 18.dp, start = 16.dp), content = der)
    }
}

/** El antetítulo de una casilla (en versalitas) con su acción a la derecha, si la tiene. */
@Composable
private fun CabezaCasilla(texto: String, accion: Pair<String, () -> Unit>? = null) {
    Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(texto.uppercase(), color = Charcoal, fontFamily = Body, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.6.sp, modifier = Modifier.weight(1f))
        accion?.let { (t, f) -> Text(t.uppercase(), color = Acento, fontFamily = Body, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp, modifier = Modifier.clickable(onClick = f).padding(start = 8.dp, top = 2.dp, bottom = 2.dp)) }
    }
}

private fun fecha(ms: Long): String = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(ms))

private fun haceCuando(ms: Long): String {
    if (ms <= 0) return "todavía no"
    val d = ((System.currentTimeMillis() - ms) / 86_400_000L).toInt()
    return when { d <= 0 -> "hoy"; d == 1 -> "ayer"; d < 7 -> "hace $d días"; else -> "el " + fecha(ms) }
}

private fun tamanoLegible(b: Long): String = when { b >= 1_048_576 -> "%.1f MB".format(b / 1_048_576.0); b >= 1024 -> "${b / 1024} KB"; else -> "$b B" }

/**
 * La ficha de un libro, con la estética de la ficha de Atrio: titular enorme, línea en cursiva, una fila de acciones y casillas con filetes.
 * Desde aquí: leerlo (bajándolo antes de tu kDrive si hace falta), cambiar su portada (galería o internet), renombrarlo, elegir o quitar
 * su archivo y quitarlo de la biblioteca.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PantFicha(id: String, alCerrar: () -> Unit, alLeer: (Libro) -> Unit) {
    val ctx = LocalContext.current
    val ambito = rememberCoroutineScope()
    val l = Biblioteca.por(id)
    if (l == null) { LaunchedEffect(Unit) { alCerrar() }; return }
    var quitar by remember { mutableStateOf(false) }
    var quitarArchivo by remember { mutableStateOf(false) }
    var renombrar by remember { mutableStateOf(false) }
    var portada by remember { mutableStateOf(false) }
    var archivo by remember { mutableStateOf(false) }
    var trabajando by remember { mutableStateOf<String?>(null) }
    fun dice(t: String) = Toast.makeText(ctx, t, Toast.LENGTH_LONG).show()
    val st = remember(l.id, l.ultima, l.msPropios, l.fraccion) { EstadoLectura.estado(ctx, l.id) }
    val enNube = NubeLibros.de(ctx, l)
    val ficheroLocal = remember(l.uri) { runCatching { File(Uri.parse(l.uri).path.orEmpty()) }.getOrNull()?.takeIf { l.uri.isNotBlank() && it.exists() } }

    val elegirArchivo = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u ->
        if (u != null) { trabajando = "Copiando el archivo…"; ambito.launch {
            val r = withContext(Dispatchers.IO) { runCatching { Metadatos.adjuntar(ctx, l, u) }.getOrNull() }
            trabajando = null
            dice(if (r == null) "No he podido usar ese archivo (vale EPUB, PDF, MOBI, AZW3, FB2, CBZ y TXT)." else "Archivo puesto: ${r.archivo}")
            if (r != null) NubeLibros.programar(ctx)
        } }
    }
    fun bajar(leer: Boolean) {
        if (trabajando != null) return
        if (!KDrive.on(ctx)) { dice("Conecta tu kDrive (Ajustes → Actualizaciones) para bajarlo."); return }
        trabajando = "Bajando de tu kDrive…"
        ambito.launch {
            val r = withContext(Dispatchers.IO) { runCatching { NubeLibros.leerIndice(ctx); NubeLibros.bajar(ctx, Biblioteca.por(l.id) ?: l) } }
            trabajando = null
            r.onFailure { dice("No he podido bajarlo: ${it.message}") }.onSuccess { n -> if (leer) alLeer(n) else dice("Libro bajado.") }
        }
    }
    fun principal() = when {
        !l.enOtroDispositivo -> alLeer(l)
        enNube != null -> bajar(true)
        else -> elegirArchivo.launch(arrayOf("*/*"))
    }

    val deck = listOfNotNull(
        if (l.ultima > 0) "leído por última vez ${haceCuando(l.ultima)}" else null,
        if (l.msLeidos >= 60_000) "${tiempoDe(l.msLeidos)} leídos" else null
    ).joinToString(" · ")

    Column(Modifier.fillMaxSize().background(Paper).verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 20.dp)) {
        RevistaTopBar(alCerrar, "Volver") {
            EnlaceRevista(if (l.favorito) "★ Favorito" else "☆ Favorito", acento = l.favorito) { Biblioteca.poner(l.copy(favorito = !l.favorito)) }
        }
        Filete(3.dp)
        Spacer(Modifier.height(14.dp))
        RevistaHeadline(l.titulo, listOfNotNull(l.estado.nombre, l.formato.uppercase().ifBlank { null }, l.autor.ifBlank { null }).joinToString(" · "))
        if (deck.isNotBlank()) Text(deck, color = Muted, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 17.sp, modifier = Modifier.padding(top = 6.dp))
        Spacer(Modifier.height(14.dp))

        // La portada y por dónde vas
        Row(Modifier.fillMaxWidth().height(220.dp)) {
            PortadaLibro(l, Modifier.width(150.dp).height(220.dp).clickable { portada = true })
            Column(Modifier.weight(1f).padding(start = 18.dp).fillMaxHeight(), verticalArrangement = Arrangement.Bottom) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("${(l.fraccion * 100).toInt().coerceIn(0, 100)}", color = Acento, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 64.sp, lineHeight = 62.sp)
                    Text(" %", color = Muted, fontFamily = Body, fontSize = 14.sp, modifier = Modifier.padding(bottom = 10.dp))
                }
                Barra(l.fraccion)
                if (l.fraccion <= 0.001f && l.estado == Estado.PENDIENTE) Text("sin empezar", color = Muted, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 15.sp, modifier = Modifier.padding(top = 8.dp))
                st.optString("label").takeIf { it.isNotBlank() }?.let { Text(it, color = Muted, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 15.sp, maxLines = 2, modifier = Modifier.padding(top = 8.dp)) }
            }
        }

        // Las acciones
        Spacer(Modifier.height(16.dp))
        Filete(1.dp)
        androidx.compose.foundation.layout.FlowRow(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            EnlaceRevista(when { !l.enOtroDispositivo -> "Leer →"; enNube != null -> "Bajar y leer →"; else -> "Elegir archivo →" }, acento = true) { principal() }
            EnlaceRevista("Portada →") { portada = true }
            EnlaceRevista("Editar →") { renombrar = true }
            EnlaceRevista("Archivo →") { archivo = true }
        }
        Filete(1.dp)
        trabajando?.let { Text(it, color = Acento, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 16.sp, modifier = Modifier.padding(top = 8.dp)) }
        Spacer(Modifier.height(4.dp))
        Filete(2.dp)

        // Estante y tiempo
        DosCasillas(
            izq = {
                CabezaCasilla("Estante")
                Estado.entries.forEach { e ->
                    val on = e == l.estado
                    Text(e.nombre.uppercase(), color = if (on) Acento else Charcoal, fontFamily = Body, fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 1.4.sp,
                        modifier = Modifier.clickable(enabled = !on) { Biblioteca.poner(l.copy(estado = e)) }.padding(vertical = 6.dp)
                            .then(if (on) Modifier.drawBehind { drawRect(Acento, topLeft = androidx.compose.ui.geometry.Offset(0f, size.height - 1.5.dp.toPx()), size = androidx.compose.ui.geometry.Size(size.width, 1.5.dp.toPx())) } else Modifier))
                }
                Text(when (l.estado) { Estado.PENDIENTE -> "esperando su turno"; Estado.LEYENDO -> "lo estás leyendo"; Estado.LEIDO -> "ya lo has leído" }, color = Muted, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 15.sp, modifier = Modifier.padding(top = 6.dp))
            },
            der = {
                CabezaCasilla("Tiempo")
                FitTitle(if (l.msLeidos >= 60_000) tiempoDe(l.msLeidos) else "—", 40.sp, 42.sp, unaLinea = true, maxLines = 1)
                Text("LEÍDO EN TOTAL", color = Muted, fontFamily = Body, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                Text("Última vez: ${haceCuando(l.ultima)}", color = Muted, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
            }
        )
        Filete(2.dp)

        // Subrayados y notas
        SeccionNotas(l)
        Spacer(Modifier.height(18.dp))
        Filete(2.dp)

        // Datos
        Column(Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 12.dp)) {
            CabezaCasilla("Datos", "Editar" to { renombrar = true })
            MagFact("Título", l.titulo)
            MagFact("Autor", l.autor.ifBlank { "—" })
            MagFact("Formato", l.formato.uppercase().ifBlank { "—" })
            MagFact("Archivo", if (l.enOtroDispositivo) "no está en este dispositivo" else l.archivo)
            if (ficheroLocal != null) MagFact("Tamaño", tamanoLegible(ficheroLocal.length()))
            MagFact("En tu kDrive", when { enNube != null -> "sí"; KDrive.on(ctx) -> "todavía no"; else -> "kDrive sin conectar" })
            if (l.anadido > 0) MagFact("Añadido", fecha(l.anadido))
        }
        Filete(1.dp, Line)
        MagLink("Quitar de la biblioteca", "Se quita también de tus otros dispositivos") { quitar = true }
        Spacer(Modifier.height(30.dp))
    }

    if (portada) SelectorPortada(l) { portada = false }

    if (renombrar) {
        var t by remember { mutableStateOf(l.titulo) }; var a by remember { mutableStateOf(l.autor) }
        AlertDialog(
            onDismissRequest = { renombrar = false }, containerColor = Paper, shape = esquina(4.dp),
            title = { Text("Título y autor", color = Charcoal, fontFamily = Display, fontWeight = FontWeight.Bold) },
            text = { Column { CampoRevista("Título", t, "título") { t = it }; CampoRevista("Autor", a, "autor") { a = it }
                Text("El libro sigue siendo el mismo en todos tus dispositivos: lo leído y tus subrayados no se tocan.", color = Muted, fontFamily = Body, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp)) } },
            confirmButton = { TextButton({ if (t.isNotBlank()) { Biblioteca.poner(l.copy(titulo = t.trim(), autor = a.trim())); renombrar = false } }) { Text("GUARDAR", color = Acento, fontFamily = Body, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp) } },
            dismissButton = { TextButton({ renombrar = false }) { Text("CANCELAR", color = Charcoal, fontFamily = Body, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp) } }
        )
    }

    if (archivo) AlertDialog(
        onDismissRequest = { archivo = false }, containerColor = Paper, shape = esquina(4.dp),
        title = { Text("Archivo del libro", color = Charcoal, fontFamily = Display, fontWeight = FontWeight.Bold) },
        text = { Column {
            Text(when { !l.enOtroDispositivo -> "En este dispositivo: ${l.archivo}"; enNube != null -> "No está aquí, pero sí en tu kDrive."; else -> "No está aquí ni en tu kDrive todavía." }, color = Muted, fontFamily = Body, fontSize = 13.sp)
            if (l.enOtroDispositivo && enNube != null) MagLink("Bajar de tu kDrive", accent = true) { archivo = false; bajar(false) }
            MagLink(if (l.enOtroDispositivo) "Elegir un archivo del móvil" else "Cambiar por otro archivo", "Vale EPUB, PDF, MOBI, AZW3, FB2, CBZ y TXT; el libro sigue siendo el mismo") { archivo = false; elegirArchivo.launch(arrayOf("*/*")) }
            if (!l.enOtroDispositivo && enNube == null && KDrive.on(ctx)) MagLink("Subir a tu kDrive ahora", "Para tenerlo en tus otros dispositivos") {
                archivo = false; trabajando = "Subiendo a tu kDrive…"
                ambito.launch { val n = withContext(Dispatchers.IO) { runCatching { NubeLibros.subirPendientes(ctx, aMano = true) } }; trabajando = null; dice(n.fold({ if (it > 0) "Subido a tu kDrive." else "No he podido subirlo." }, { e -> e.message ?: "No he podido subirlo." })) }
            }
            if (!l.enOtroDispositivo) MagLink("Quitar el archivo de este dispositivo", "El libro sigue en tu biblioteca y puedes volver a bajarlo si está en tu kDrive") { archivo = false; quitarArchivo = true }
        } },
        confirmButton = { TextButton({ archivo = false }) { Text("CERRAR", color = Charcoal, fontFamily = Body, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp) } }
    )

    if (quitarArchivo) AlertDialog(
        onDismissRequest = { quitarArchivo = false }, containerColor = Paper, shape = esquina(4.dp),
        title = { Text("¿Quitar el archivo?", color = Charcoal, fontFamily = Display, fontWeight = FontWeight.Bold) },
        text = { Text(if (enNube != null) "Se borra de este dispositivo y puedes volver a bajarlo de tu kDrive cuando quieras. Lo leído y tus subrayados se quedan." else "Ojo: este libro no está en tu kDrive, así que no podrás recuperarlo si no tienes el archivo. Lo leído y tus subrayados se quedan.", color = Muted, fontFamily = Body) },
        confirmButton = { TextButton({ Metadatos.quitarArchivo(ctx, l); quitarArchivo = false }) { Text("QUITAR", color = Acento, fontFamily = Body, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp) } },
        dismissButton = { TextButton({ quitarArchivo = false }) { Text("CANCELAR", color = Charcoal, fontFamily = Body, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp) } }
    )

    if (quitar) AlertDialog(
        onDismissRequest = { quitar = false }, containerColor = Paper, shape = esquina(4.dp),
        title = { Text("¿Quitar «${l.titulo}»?", color = Charcoal, fontFamily = Display, fontWeight = FontWeight.Bold) },
        text = { Text("Se quita de tu biblioteca (en tus otros dispositivos también) y se borra su archivo de este móvil. En tu kDrive no se toca nada.", color = Muted, fontFamily = Body) },
        confirmButton = { TextButton({
            runCatching { File(Uri.parse(l.uri).path.orEmpty()).takeIf { it.exists() }?.delete() }
            Biblioteca.quitar(l.id); quitar = false; alCerrar()
        }) { Text("QUITAR", color = Acento, fontFamily = Body, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp) } },
        dismissButton = { TextButton({ quitar = false }) { Text("CANCELAR", color = Charcoal, fontFamily = Body, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp) } }
    )
}
