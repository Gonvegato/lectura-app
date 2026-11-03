package com.gonzalo.marfil

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** La hoja de «Portada» de un libro, a pantalla completa: galería, la del libro, quitar y buscar en internet. */
@Composable
fun SelectorPortada(l: Libro, alCerrar: () -> Unit) {
    Dialog(onDismissRequest = alCerrar, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(Modifier.fillMaxSize().background(Paper)) { ContenidoSelectorPortada(l, alCerrar) }
    }
}

/** Lo de dentro de la hoja (aparte, para poder verlo en las capturas). [hallados]: resultados ya hechos (para las capturas). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ContenidoSelectorPortada(l: Libro, alCerrar: () -> Unit, hallados: List<Candidata>? = null) {
    val ctx = LocalContext.current
    val ambito = rememberCoroutineScope()
    var consulta by remember { mutableStateOf(listOf(l.titulo, l.autor).filter { it.isNotBlank() }.joinToString(" ")) }
    var resultados by remember { mutableStateOf(hallados) }
    var buscando by remember { mutableStateOf(false) }
    var guardando by remember { mutableStateOf(false) }
    var pinterest by remember { mutableStateOf(false) }
    val tieneArchivo = !l.enOtroDispositivo
    fun dice(t: String) = Toast.makeText(ctx, t, Toast.LENGTH_LONG).show()

    fun buscar() {
        if (buscando || consulta.isBlank()) return
        buscando = true
        ambito.launch {
            val r = withContext(Dispatchers.IO) { runCatching { Portadas.buscar(consulta) }.getOrDefault(emptyList()) }
            resultados = r; buscando = false
            if (r.isEmpty()) dice("No he encontrado portadas. Prueba con otro título o con el autor.")
        }
    }
    suspend fun guardar(bytes: ByteArray?) {
        if (bytes == null) { dice("No he podido leer esa imagen."); guardando = false; return }
        val n = withContext(Dispatchers.IO) { Portadas.poner(ctx, l, bytes) }
        guardando = false
        if (n == null) dice("Esa imagen no vale como portada.") else alCerrar()
    }
    val galeria = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { u ->
        if (u != null) { guardando = true; ambito.launch { guardar(withContext(Dispatchers.IO) { runCatching { ctx.contentResolver.openInputStream(u)?.use { it.readBytes() } }.getOrNull() }) } }
    }
    LaunchedEffect(Unit) { if (hallados == null) buscar() }
    if (pinterest) InternetPicker(consulta, alElegir = { bytes -> pinterest = false; guardando = true; ambito.launch { guardar(bytes) } }, alCerrar = { pinterest = false })

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 20.dp)) {
        RevistaTopBar(alCerrar, "Cerrar")
        Filete(3.dp)
        Antetitulo("Portada", Modifier.padding(top = 10.dp), color = Acento, conCuadro = false)
        FitTitle(l.titulo, 34.sp, 36.sp, letterSpacing = (-1).sp, maxLines = 2, modifier = Modifier.padding(top = 2.dp, bottom = 6.dp))
        Filete(1.dp)
        androidx.compose.foundation.layout.FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            EnlaceRevista("Galería →") { galeria.launch("image/*") }
            EnlaceRevista("Pinterest →") { pinterest = true }
            if (tieneArchivo) EnlaceRevista("La del libro →") {
                guardando = true
                ambito.launch {
                    val n = withContext(Dispatchers.IO) { Portadas.delLibro(ctx, l) }
                    guardando = false
                    if (n == null) dice("Este libro no trae portada propia.") else alCerrar()
                }
            }
            if (l.portada.isNotBlank()) EnlaceRevista("Quitar →") { Portadas.quitar(ctx, l); alCerrar() }
        }
        Filete(1.dp)
        CampoRevista("Buscar", consulta, "título y autor") { consulta = it }
        EnlaceRevista(if (buscando) "Buscando…" else "Buscar en internet →", Modifier.padding(top = 4.dp), acento = true) { buscar() }
        Spacer(Modifier.height(10.dp))
        val lista = resultados
        when {
            guardando -> Text("Guardando la portada…", color = Muted, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 17.sp)
            buscando && lista == null -> Text("Buscando portadas…", color = Muted, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 17.sp)
            lista != null && lista.isEmpty() -> Text("Nada todavía. Cambia lo que buscas.", color = Muted, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 17.sp)
            lista != null -> LazyVerticalGrid(GridCells.Fixed(3), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
                items(lista, key = { it.grande }) { c ->
                    Column(Modifier.clickable(enabled = !guardando) {
                        guardando = true
                        ambito.launch { guardar(withContext(Dispatchers.IO) { Portadas.descargar(c.grande) }) }
                    }) {
                        Box(Modifier.fillMaxWidth().aspectRatio(0.68f).background(Sand)) {
                            AsyncImage(c.miniatura, c.titulo, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        }
                        Text(c.fuente.uppercase(), color = Acento, fontFamily = Body, fontSize = 8.sp, fontWeight = FontWeight.Black, letterSpacing = 0.8.sp, modifier = Modifier.padding(top = 4.dp))
                        Text(c.titulo, color = Muted, fontFamily = Display, fontSize = 11.sp, lineHeight = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}
