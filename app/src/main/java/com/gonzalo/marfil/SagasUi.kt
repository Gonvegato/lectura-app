package com.gonzalo.marfil

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/** Los autores de una saga: «Frank Herbert», «A y B», «A y otros». */
fun autoresDe(libros: List<Libro>): String = libros.map { it.autor.trim() }.filter { it.isNotEmpty() }.distinct().let {
    when (it.size) { 0 -> ""; 1 -> it[0]; 2 -> "${it[0]} y ${it[1]}"; else -> "${it[0]} y otros" }
}

/** El que toca ahora en la saga: el que estás leyendo o, si no, el primero sin leer (ni abandonar). */
fun siguienteDe(libros: List<Libro>): Libro? = libros.firstOrNull { it.estado == Estado.LEYENDO } ?: libros.firstOrNull { it.estado == Estado.PENDIENTE }

@Composable
private fun BotonTexto(texto: String, acento: Boolean = false, onClick: () -> Unit) =
    TextButton(onClick) { Text(texto.uppercase(), color = if (acento) Acento else Charcoal, fontFamily = Body, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp) }

/** La página de una saga: sus libros en orden, lo que toca leer ahora y cómo editarla (nombre, orden, libros). */
@Composable
fun PantSaga(id: String, alCerrar: () -> Unit, alAbrir: (Libro) -> Unit) {
    val s = Sagas.por(id)
    if (s == null) { LaunchedEffect(Unit) { alCerrar() }; return }
    val libros = Sagas.libros(s)
    var editando by remember { mutableStateOf(false) }
    var renombrar by remember { mutableStateOf(false) }
    var borrar by remember { mutableStateOf(false) }
    var anadir by remember { mutableStateOf(false) }
    val siguiente = siguienteDe(libros)
    fun mover(k: String, d: Int) {
        val l = s.claves.toMutableList(); val i = l.indexOf(k); val j = i + d
        if (i < 0 || j !in l.indices) return
        l[i] = l[j].also { l[j] = l[i] }; Sagas.poner(s.copy(claves = l))
    }
    Column(Modifier.fillMaxSize().background(Paper).verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 28.dp)) {
        RevistaTopBar(alCerrar, "Volver") { EnlaceRevista(if (editando) "Listo" else "Editar", acento = editando) { editando = !editando } }
        Filete(3.dp)
        Spacer(Modifier.height(14.dp))
        RevistaHeadline(s.nombre, listOfNotNull("Saga", autoresDe(libros).ifBlank { null }).joinToString(" · "))
        Text(resumenSaga(libros), color = Muted, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 17.sp, modifier = Modifier.padding(top = 6.dp, bottom = 10.dp))
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            val leidos = libros.count { it.estado == Estado.LEIDO }
            Column(Modifier.weight(1f)) {
                FitTitle("$leidos/${libros.size}", 44.sp, 46.sp, unaLinea = true, maxLines = 1)
                Text("LEÍDOS", color = Muted, fontFamily = Body, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            }
            val pags = libros.sumOf { it.paginas }
            if (pags > 0) Column(Modifier.weight(1f)) {
                FitTitle("$pags", 44.sp, 46.sp, unaLinea = true, maxLines = 1)
                Text("PÁGINAS EN TOTAL", color = Muted, fontFamily = Body, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            }
        }
        Spacer(Modifier.height(10.dp))
        if (siguiente != null) {
            Filete(1.dp)
            Row(Modifier.fillMaxWidth().clickable { alAbrir(siguiente) }.padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(if (siguiente.estado == Estado.LEYENDO) "AHORA" else "TOCA ESTE", color = Acento, fontFamily = Body, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.4.sp)
                    Text(siguiente.titulo, color = Charcoal, fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 24.sp, maxLines = 2)
                }
                Text("ABRIR →", color = Acento, fontFamily = Body, fontWeight = FontWeight.Black, fontSize = 12.sp, letterSpacing = 1.4.sp)
            }
        }
        Filete(2.dp)
        if (libros.isEmpty()) Text("Esta saga no tiene libros todavía.", color = Muted, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 17.sp, modifier = Modifier.padding(vertical = 16.dp))
        libros.forEachIndexed { i, l ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { FilaLibro(l, alAbrir, numero = i + 1, conEstado = true) }
                if (editando) Row {
                    Text("▲", color = if (i > 0) Charcoal else Line, fontSize = 18.sp, modifier = Modifier.clickable(enabled = i > 0) { mover(l.clave, -1) }.padding(8.dp))
                    Text("▼", color = if (i < libros.size - 1) Charcoal else Line, fontSize = 18.sp, modifier = Modifier.clickable(enabled = i < libros.size - 1) { mover(l.clave, 1) }.padding(8.dp))
                    Text("✕", color = Acento, fontSize = 18.sp, modifier = Modifier.clickable { val r = s.claves - l.clave; if (r.isEmpty()) { Sagas.quitar(s.id); alCerrar() } else Sagas.poner(s.copy(claves = r)) }.padding(8.dp))
                }
            }
            Filete(1.dp, Line)
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            EnlaceRevista("Añadir libros →", acento = true) { anadir = true }
            EnlaceRevista("Renombrar →") { renombrar = true }
        }
        Filete(1.dp, Line)
        MagLink("Borrar la saga", "Los libros se quedan donde están; solo se deshace el grupo") { borrar = true }
        Spacer(Modifier.height(30.dp))
    }
    if (anadir) SelectorLibros("Añadir a «${s.nombre}»", Biblioteca.propios().filter { it.clave !in s.claves }, { anadir = false }) { claves -> anadir = false; Sagas.poner(s.copy(claves = s.claves + claves)) }
    if (renombrar) {
        var t by remember { mutableStateOf(s.nombre) }
        AlertDialog(onDismissRequest = { renombrar = false }, containerColor = Paper, shape = esquina(4.dp),
            title = { Text("Nombre de la saga", color = Charcoal, fontFamily = Display, fontWeight = FontWeight.Bold) },
            text = { CampoRevista("Nombre", t, "nombre") { t = it } },
            confirmButton = { BotonTexto("Guardar", true) { if (t.isNotBlank()) { Sagas.poner(s.copy(nombre = t.trim())); renombrar = false } } },
            dismissButton = { BotonTexto("Cancelar") { renombrar = false } })
    }
    if (borrar) AlertDialog(onDismissRequest = { borrar = false }, containerColor = Paper, shape = esquina(4.dp),
        title = { Text("¿Borrar «${s.nombre}»?", color = Charcoal, fontFamily = Display, fontWeight = FontWeight.Bold) },
        text = { Text("Se deshace la saga en todos tus dispositivos. Los ${libros.size} libros siguen en tu biblioteca, cada uno en su estante.", color = Muted, fontFamily = Body) },
        confirmButton = { BotonTexto("Borrar", true) { Sagas.quitar(s.id); borrar = false; alCerrar() } },
        dismissButton = { BotonTexto("Cancelar") { borrar = false } })
}

/** Elegir una saga para un libro: una de las que hay, ninguna, o una nueva con el nombre que escribas. */
@Composable
fun DialogoSagaDeLibro(l: Libro, alCerrar: () -> Unit) {
    val actual = Sagas.de(l.clave)
    var nueva by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = alCerrar, containerColor = Paper, shape = esquina(4.dp),
        title = { Text("Saga de «${l.titulo}»", color = Charcoal, fontFamily = Display, fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (Sagas.lista.isEmpty()) Text("Aún no hay sagas. Escribe un nombre para crear la primera.", color = Muted, fontFamily = Body, fontSize = 13.sp)
                Sagas.lista.sortedBy { norm(it.nombre) }.forEach { s ->
                    val on = s.id == actual?.id
                    Text((if (on) "● " else "○ ") + s.nombre, color = if (on) Acento else Charcoal, fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 18.sp,
                        modifier = Modifier.fillMaxWidth().clickable { Sagas.asignar(l.clave, s); alCerrar() }.padding(vertical = 8.dp))
                }
                if (actual != null) Text("Quitar de su saga", color = Muted, fontFamily = Body, fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.fillMaxWidth().clickable { Sagas.asignar(l.clave, null); alCerrar() }.padding(vertical = 10.dp))
                CampoRevista("Nueva", nueva, "nombre de la saga") { nueva = it }
            }
        },
        confirmButton = { BotonTexto("Crear y meterlo", true) { if (nueva.isNotBlank()) { val s = Sagas.crear(nueva); Sagas.asignar(l.clave, s); alCerrar() } } },
        dismissButton = { BotonTexto("Cerrar") { alCerrar() } }
    )
}

/** Crear una saga vacía (se abre después para meterle libros). */
@Composable
fun DialogoNuevaSaga(nombre: String, alCambiar: (String) -> Unit, alCerrar: () -> Unit, alCrear: (String) -> Unit) {
    AlertDialog(
        onDismissRequest = alCerrar, containerColor = Paper, shape = esquina(4.dp),
        title = { Text("Nueva saga", color = Charcoal, fontFamily = Display, fontWeight = FontWeight.Bold) },
        text = { CampoRevista("Nombre", nombre, "p. ej. Dune") { alCambiar(it) } },
        confirmButton = { BotonTexto("Crear", true) { if (nombre.isNotBlank()) alCrear(nombre.trim()) } },
        dismissButton = { BotonTexto("Cerrar") { alCerrar() } }
    )
}

/** Elegir varios libros de la biblioteca (a pantalla completa, con buscador): devuelve las claves de los marcados. */
@Composable
fun SelectorLibros(titulo: String, candidatos: List<Libro>, alCerrar: () -> Unit, alElegir: (List<String>) -> Unit) {
    Dialog(onDismissRequest = alCerrar, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        var marcados by remember { mutableStateOf(listOf<String>()) }
        var busca by remember { mutableStateOf("") }
        val q = norm(busca)
        val lista = candidatos.filter { q.isEmpty() || norm(it.titulo).contains(q) || norm(it.autor).contains(q) }.sortedBy { norm(it.titulo) }
        Column(Modifier.fillMaxSize().background(Paper).statusBarsPadding().navigationBarsPadding().padding(horizontal = 24.dp)) {
            RevistaTopBar(alCerrar, "Cerrar") { EnlaceRevista(if (marcados.isEmpty()) "Elige" else "Añadir ${marcados.size} →", acento = marcados.isNotEmpty()) { if (marcados.isNotEmpty()) alElegir(marcados) } }
            Filete(3.dp)
            Text(titulo.uppercase(), color = Acento, fontFamily = Body, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.6.sp, modifier = Modifier.padding(top = 12.dp))
            CampoRevista("Buscar", busca, "título o autor") { busca = it }
            LazyColumn(Modifier.fillMaxSize()) {
                if (lista.isEmpty()) item { Text("No hay más libros para añadir.", color = Muted, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 17.sp, modifier = Modifier.padding(top = 20.dp)) }
                items(lista, key = { it.id }) { l ->
                    val on = l.clave in marcados
                    Row(Modifier.fillMaxWidth().clickable { marcados = if (on) marcados - l.clave else marcados + l.clave }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(if (on) "■" else "□", color = if (on) Acento else Muted, fontSize = 22.sp, modifier = Modifier.width(34.dp))
                        PortadaLibro(l, Modifier.width(40.dp).aspectRatio(0.68f), radio = 3, conTitulo = false)
                        Column(Modifier.weight(1f).padding(start = 12.dp)) {
                            Text(l.titulo, color = Charcoal, fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, maxLines = 2)
                            if (l.autor.isNotBlank()) Text(l.autor.uppercase(), color = Muted, fontFamily = Body, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                        }
                    }
                    Filete(1.dp, Line)
                }
            }
        }
    }
}
