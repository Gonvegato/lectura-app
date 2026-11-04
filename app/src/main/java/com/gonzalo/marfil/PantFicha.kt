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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
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

fun tamanoLegible(b: Long): String = when { b >= 1_048_576 -> "%.1f MB".format(b / 1_048_576.0); b >= 1024 -> "${b / 1024} KB"; else -> "$b B" }

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
    var saga by remember { mutableStateOf(false) }
    var menuArchivo by remember { mutableStateOf(false) }
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
        if (l.terminado > 0) "terminado el ${fecha(l.terminado)}" + if (l.terminados.size > 1) " · ${l.terminados.size}.ª vez" else "" else null,
        // Si ya lo terminaste, la fecha de terminado dice lo importante: no se repite «leído por última vez»
        if (l.ultima > 0 && l.terminado == 0L) "leído por última vez ${haceCuando(l.ultima)}" else null,
        if (l.msLeidos >= 60_000) "${tiempoDe(l.msLeidos)} leídos" else null
    ).joinToString(" · ")
    val miSaga = Sagas.de(l.clave)
    val frac = l.fraccion

    Column(Modifier.fillMaxSize().background(Paper).verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 20.dp)) {
        // Arriba, como en Atrio: Portada · Editar · Borrar
        RevistaTopBar(alCerrar, "Volver") {
            EnlaceRevista("Portada") { portada = true }
            EnlaceRevista("Editar") { renombrar = true }
            EnlaceRevista("Borrar") { quitar = true }
        }
        Filete(3.dp)
        // El estante, como las pestañas de estado de Atrio, pegado a la barra de arriba
        FiltrosRevista(Estado.entries.map { it.nombre }, l.estado.ordinal, Modifier.padding(top = 4.dp)) { i -> Biblioteca.poner(l.copy(estado = Estado.entries[i])) }
        Filete(1.dp)
        Spacer(Modifier.height(14.dp))
        RevistaHeadline(l.titulo, listOfNotNull(l.formato.uppercase().ifBlank { null }, l.autor.ifBlank { null }).joinToString(" · "))
        if (deck.isNotBlank()) Text(deck, color = Muted, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 17.sp, modifier = Modifier.padding(top = 6.dp))
        Spacer(Modifier.height(14.dp))

        // La portada y por dónde vas (con las páginas, al lado del porcentaje)
        Row(Modifier.fillMaxWidth().height(220.dp)) {
            PortadaLibro(l, Modifier.width(150.dp).height(220.dp).clickable { portada = true })
            Column(Modifier.weight(1f).padding(start = 18.dp).fillMaxHeight(), verticalArrangement = Arrangement.Bottom) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("${(frac * 100).toInt().coerceIn(0, 100)}", color = Acento, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 64.sp, lineHeight = 62.sp)
                    Text(" %", color = Muted, fontFamily = Body, fontSize = 14.sp, modifier = Modifier.padding(bottom = 10.dp))
                    Spacer(Modifier.weight(1f))
                    Paginas.texto(l).takeIf { it.isNotBlank() }?.let { Text(it, color = Acento, fontFamily = Body, fontWeight = FontWeight.Black, fontSize = 14.sp, letterSpacing = 0.6.sp, modifier = Modifier.padding(bottom = 12.dp)) }
                }
                Barra(l.fraccion)
                if (frac <= 0.001f && l.estado == Estado.PENDIENTE) Text("sin empezar", color = Muted, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 15.sp, modifier = Modifier.padding(top = 8.dp))
                st.optString("label").takeIf { it.isNotBlank() }?.let { Text(it, color = Muted, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 15.sp, maxLines = 2, modifier = Modifier.padding(top = 8.dp)) }
            }
        }
        Spacer(Modifier.height(16.dp))

        // Las acciones: la saga y el favorito
        Filete(1.dp)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.CenterVertically) {
            EnlaceRevista(if (miSaga != null) "Saga: ${miSaga.nombre} →" else "Saga →", acento = miSaga != null) { saga = true }
            EnlaceRevista(if (l.favorito) "★ Favorito" else "☆ Favorito", acento = l.favorito) { Biblioteca.poner(l.copy(favorito = !l.favorito)) }
        }
        Filete(1.dp)
        Spacer(Modifier.height(4.dp))
        Filete(2.dp)

        // La casilla de lectura (la de Atrio): bajar de tu kDrive, elegir archivo, seguir leyendo
        Column(Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 18.dp)) {
            CabezaCasilla("Lectura")
            if (l.enOtroDispositivo) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.AutoStories, null, tint = Acento, modifier = Modifier.size(28.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Léelo aquí", color = Charcoal, fontFamily = Body, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text(if (KDrive.on(ctx)) "Elige su EPUB o PDF: se guarda también en tu kDrive." else "Elige su EPUB o PDF del móvil: no se sube a ningún sitio.", color = Muted, fontFamily = Body, fontSize = 12.sp, lineHeight = 16.sp)
                    }
                }
                Spacer(Modifier.height(10.dp))
                if (enNube != null) {
                    AccionGrande(Icons.Rounded.CloudDownload, if (trabajando != null) "Bajando…" else "Bajar de tu kDrive (${tamanoLegible(enNube.s)})", Modifier.fillMaxWidth()) { bajar(true) }
                    Spacer(Modifier.height(8.dp))
                }
                AccionGrande(Icons.Rounded.AttachFile, "Elegir archivo", Modifier.fillMaxWidth(), acento = false) { elegirArchivo.launch(arrayOf("*/*")) }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AccionGrande(Icons.Rounded.AutoStories, if (l.estado == Estado.LEIDO && frac >= 0.99f) "Volver a leer" else if (frac > 0.005f) "Seguir leyendo" else "Empezar a leer", Modifier.weight(1f)) { alLeer(l) }
                    Spacer(Modifier.width(8.dp))
                    Box {
                        Icon(Icons.Rounded.MoreVert, "Opciones del archivo", tint = Charcoal, modifier = Modifier.size(44.dp).clickable { menuArchivo = true }.padding(10.dp))
                        DropdownMenu(menuArchivo, { menuArchivo = false }) {
                            DropdownMenuItem({ Text("Cambiar de archivo") }, { menuArchivo = false; elegirArchivo.launch(arrayOf("*/*")) }, leadingIcon = { Icon(Icons.Rounded.SwapHoriz, null) })
                            if (enNube != null) DropdownMenuItem({ Text("Quitar del móvil (sigue en tu kDrive)") }, { menuArchivo = false; quitarArchivo = true }, leadingIcon = { Icon(Icons.Rounded.CloudDone, null) })
                            else {
                                DropdownMenuItem({ Text("Quitar el archivo") }, { menuArchivo = false; quitarArchivo = true }, leadingIcon = { Icon(Icons.Rounded.LinkOff, null) })
                                if (KDrive.on(ctx)) DropdownMenuItem({ Text("Subir a tu kDrive ahora") }, {
                                    menuArchivo = false; trabajando = "Subiendo a tu kDrive…"
                                    ambito.launch { val n = withContext(Dispatchers.IO) { runCatching { NubeLibros.subirPendientes(ctx, aMano = true) } }; trabajando = null; dice(n.fold({ if (it > 0) "Subido a tu kDrive." else "No he podido subirlo." }, { e -> e.message ?: "No he podido subirlo." })) }
                                }, leadingIcon = { Icon(Icons.Rounded.CloudDownload, null) })
                            }
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Barra(frac)
                Spacer(Modifier.height(6.dp))
                val notas = st.optJSONArray("notes")?.length() ?: 0
                val min = l.msLeidos / 60_000L
                Text(listOfNotNull("${(frac * 100).toInt()} % leído", if (min >= 1) (if (frac >= 0.99f) "leído en ${readTime(min)}" else "${readTime(min)} leyendo") else null, st.optString("label").ifBlank { null }, if (notas > 0) "$notas subrayados" else null).joinToString(" · "),
                    color = Muted, fontFamily = Body, fontSize = 12.sp, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                if (frac in 0.02f..0.97f && min >= 5) Text("Te quedan unas ${readTime((min * (1 - frac) / frac).toLong().coerceAtLeast(1))} a tu ritmo", color = Acento, fontFamily = Body, fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
            }
            trabajando?.let { Text(it, color = Acento, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 16.sp, modifier = Modifier.padding(top = 8.dp)) }
        }
        Filete(2.dp)

        // Tiempo y páginas
        DosCasillas(
            izq = {
                CabezaCasilla("Tiempo")
                FitTitle(if (l.msLeidos >= 60_000) tiempoDe(l.msLeidos) else "—", 40.sp, 42.sp, unaLinea = true, maxLines = 1)
                Text("LEÍDO EN TOTAL", color = Muted, fontFamily = Body, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                Text("Última vez: ${haceCuando(l.ultima)}", color = Muted, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
            },
            der = {
                CabezaCasilla("Páginas", "Editar" to { renombrar = true })
                FitTitle(if (l.paginas > 0) "${l.paginas}" else "—", 40.sp, 42.sp, unaLinea = true, maxLines = 1)
                Text("EN TOTAL", color = Muted, fontFamily = Body, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                Text(when (l.paginasOrigen) { "manual" -> "las que has puesto tú"; "atrio" -> "las de Atrio"; "pdf" -> "las del PDF"; "openlibrary" -> "las de su edición (Open Library)"; "estimado" -> "calculadas por el texto"; else -> "sin dato: ponlas en Editar" },
                    color = Muted, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
            }
        )
        Filete(2.dp)

        // La valoración de Azabache (solo se ve; se hace allí)
        if (Cuenta.vinculada) {
            var nota by remember(l.clave) { mutableStateOf(NotaAzabache.cargar(ctx)[l.clave]) }
            LaunchedEffect(l.clave) {
                if (NotaAzabache.vieja(ctx)) withContext(Dispatchers.IO) { NotaAzabache.refrescar(ctx) }.let { nota = NotaAzabache.cargar(ctx)[l.clave] }
            }
            Column(Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 16.dp)) {
                CabezaCasilla("En Azabache")
                if (nota != null) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        FitTitle("${nota!!.general}", 48.sp, 50.sp, unaLinea = true, maxLines = 1)
                        Text(" /100", color = Muted, fontFamily = Body, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 10.dp))
                    }
                    if (nota!!.resena.isNotBlank()) Text(nota!!.resena, color = Muted, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 15.sp, lineHeight = 21.sp, maxLines = 3, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
                } else Text("Sin valorar todavía.", color = Muted, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 16.sp)
                if (NotaAzabache.instalado(ctx)) EnlaceRevista(if (nota != null) "Abrir en Azabache →" else "Valorar en Azabache →", acento = true) { NotaAzabache.abrir(ctx, nota) }
            }
            Filete(2.dp)
        }

        // Subrayados y notas
        SeccionNotas(l)
        Spacer(Modifier.height(18.dp))
        Filete(2.dp)

        // Datos
        Column(Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 12.dp)) {
            CabezaCasilla("Datos", "Editar" to { renombrar = true })
            MagFact("Título", l.titulo)
            MagFact("Autor", l.autor.ifBlank { "—" })
            if (miSaga != null) MagFact("Saga", "${miSaga.nombre} · ${miSaga.claves.indexOf(l.clave) + 1}")
            MagFact("Formato", l.formato.uppercase().ifBlank { "—" })
            if (l.terminado > 0) MagFact("Terminado", fecha(l.terminado))
            if (l.terminados.size > 1) MagFact("Lecturas", "${l.terminados.size}")
            if (l.paginas > 0) MagFact("Páginas", "${l.paginas}")
            MagFact("Archivo", if (l.enOtroDispositivo) "no está en este dispositivo" else l.archivo)
            if (ficheroLocal != null) MagFact("Tamaño", tamanoLegible(ficheroLocal.length()))
            MagFact("En tu kDrive", when { enNube != null -> "sí"; KDrive.on(ctx) -> "todavía no"; else -> "kDrive sin conectar" })
            if (l.anadido > 0) MagFact("Añadido", fecha(l.anadido))
        }
        Spacer(Modifier.height(30.dp))
    }

    if (portada) SelectorPortada(l) { portada = false }

    if (saga) DialogoSagaDeLibro(l) { saga = false }

    if (renombrar) {
        var t by remember { mutableStateOf(l.titulo) }; var a by remember { mutableStateOf(l.autor) }; var pg by remember { mutableStateOf(if (l.paginas > 0) l.paginas.toString() else "") }
        AlertDialog(
            onDismissRequest = { renombrar = false }, containerColor = Paper, shape = esquina(4.dp),
            title = { Text("Datos del libro", color = Charcoal, fontFamily = Display, fontWeight = FontWeight.Bold) },
            text = { Column { CampoRevista("Título", t, "título") { t = it }; CampoRevista("Autor", a, "autor") { a = it }; CampoRevista("Páginas", pg, "nº de páginas") { pg = it.filter { c -> c.isDigit() }.take(5) }
                Text("El libro sigue siendo el mismo en todos tus dispositivos: lo leído y tus subrayados no se tocan.", color = Muted, fontFamily = Body, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp)) } },
            confirmButton = { TextButton({
                if (t.isNotBlank()) {
                    val n = pg.toIntOrNull() ?: 0
                    val base = l.copy(titulo = t.trim(), autor = a.trim())
                    Biblioteca.poner(if (n == l.paginas) base else if (n > 0) base.copy(paginas = n, paginasOrigen = "manual") else base.copy(paginas = 0, paginasOrigen = ""))
                    renombrar = false
                }
            }) { Text("GUARDAR", color = Acento, fontFamily = Body, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp) } },
            dismissButton = { TextButton({ renombrar = false }) { Text("CANCELAR", color = Charcoal, fontFamily = Body, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp) } }
        )
    }

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
