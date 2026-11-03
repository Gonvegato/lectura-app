package com.gonzalo.marfil

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Arranca los datos de la app (una vez). */
fun iniciar(c: Context) {
    Skin.cargar(c); Biblioteca.cargar(c)
    TemaAtrio.cargarGuardado(c)
    Cuenta.cargar(c); SincroP.iniciar(c)
}

/** Dónde estás: la portada y sus secciones (se llega a ellas desde el menú de la portada, como en Atrio). */
enum class Destino { Portada, Leyendo, Pendientes, Leidos, Favoritos, Todos, Autores, Ajustes }

/** Qué libros se ven en la lista y cómo se buscan. */
class FiltroLibros {
    var estado by mutableStateOf<Estado?>(null)
    var favoritos by mutableStateOf(false)
    var autor by mutableStateOf<String?>(null)
    var busca by mutableStateOf("")
    fun limpiar() { estado = null; favoritos = false; autor = null; busca = "" }
}

fun filtrar(todos: List<Libro>, f: FiltroLibros): List<Libro> {
    val q = norm(f.busca)
    return todos.filter { l ->
        (f.estado == null || l.estado == f.estado) && (!f.favoritos || l.favorito) && (f.autor == null || norm(l.autor) == norm(f.autor!!)) &&
            (q.isEmpty() || norm(l.titulo).contains(q) || norm(l.autor).contains(q))
    }.sortedWith(compareByDescending<Libro> { it.ultima }.thenByDescending { it.anadido })
}

/** La app: portada de revista y sus secciones, la ficha de un libro por encima y el selector de archivos para añadir. */
@Composable
fun MarfilApp(libroPedido: String? = null, alAbrirPedido: () -> Unit = {}) {
    val ctx = LocalContext.current
    val ambito = rememberCoroutineScope()
    var destino by rememberSaveable { mutableIntStateOf(0) }
    var fichaId by rememberSaveable { mutableStateOf<String?>(null) }
    val filtro = remember { FiltroLibros() }
    val d = Destino.entries[destino]
    // Un widget (o un aviso) pide abrir un libro
    LaunchedEffect(libroPedido) { if (libroPedido != null && Biblioteca.por(libroPedido) != null) { fichaId = libroPedido; destino = 0 }; if (libroPedido != null) alAbrirPedido() }

    // Añadir libros: se eligen uno o varios archivos (EPUB, PDF, MOBI…); se copian a la app y se leen sus datos
    val elegir = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) ambito.launch {
            val hechos = withContext(Dispatchers.IO) { uris.mapNotNull { runCatching { Metadatos.anadir(ctx, it) }.getOrNull() } }
            Toast.makeText(ctx, if (hechos.isEmpty()) "No he podido añadir ese archivo (vale EPUB, PDF, MOBI, AZW3, FB2, CBZ y TXT)" else if (hechos.size == 1) "Añadido: ${hechos[0].titulo}" else "Añadidos ${hechos.size} libros", Toast.LENGTH_LONG).show()
        }
    }
    fun anadir() = elegir.launch(arrayOf("application/epub+zip", "application/pdf", "application/x-mobipocket-ebook", "application/vnd.amazon.ebook", "application/x-fictionbook+xml", "application/vnd.comicbook+zip", "text/plain", "application/octet-stream"))
    fun leer(l: Libro) { Lector.abrir(ctx, l) }
    fun ir(x: Destino) {
        filtro.limpiar()
        when (x) {
            Destino.Leyendo -> filtro.estado = Estado.LEYENDO
            Destino.Pendientes -> filtro.estado = Estado.PENDIENTE
            Destino.Leidos -> filtro.estado = Estado.LEIDO
            Destino.Favoritos -> filtro.favoritos = true
            else -> {}
        }
        destino = x.ordinal
    }

    AvisoActualizacion()
    BackHandler(enabled = fichaId == null && d != Destino.Portada) { destino = 0 }
    BackHandler(enabled = fichaId != null) { fichaId = null }

    Box(Modifier.fillMaxSize().background(Paper)) {
        when (d) {
            Destino.Portada -> PantInicio(alAbrir = { fichaId = it.id }, alLeer = ::leer, alAnadir = ::anadir, alIr = { ir(it) }, alBuscar = { ir(Destino.Todos); filtro.busca = " ".trim() })
            Destino.Autores -> PantAutores(alVolver = { destino = 0 }, alElegir = { a -> filtro.limpiar(); filtro.autor = a; destino = Destino.Todos.ordinal })
            Destino.Ajustes -> PantEstilo(alVolver = { destino = 0 })
            else -> PantLibros(filtro, d, alVolver = { destino = 0 }, alAbrir = { fichaId = it.id }, alLeer = ::leer, alAnadir = ::anadir)
        }
        AnimatedVisibility(fichaId != null, enter = slideInVertically { it / 8 } + fadeIn(), exit = slideOutVertically { it / 8 } + fadeOut()) {
            fichaId?.let { id -> PantFicha(id, alCerrar = { fichaId = null }, alLeer = ::leer) }
        }
    }
}
