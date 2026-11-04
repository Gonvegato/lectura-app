package com.gonzalo.marfil

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

/** Cómo se puede ordenar un estante. */
enum class OrdenLibros(val nombre: String) { RECIENTES("Recientes"), ANADIDOS("Añadidos"), TERMINADO("Terminado"), TITULO("Título"), AUTOR("Autor"), PAGINAS("Páginas") }

/** El orden con el que empieza cada estante: lo pendiente, por lo último añadido; el resto, por lo último abierto. */
fun ordenInicial(e: Estado): OrdenLibros = when (e) { Estado.PENDIENTE -> OrdenLibros.ANADIDOS; Estado.LEIDO -> OrdenLibros.TERMINADO; else -> OrdenLibros.RECIENTES }

/** Ordena una lista de libros. Los que no tienen autor (o páginas) van al final. */
fun ordenar(o: OrdenLibros, l: List<Libro>): List<Libro> = when (o) {
    OrdenLibros.RECIENTES -> l.sortedWith(compareByDescending<Libro> { it.ultima }.thenByDescending { it.anadido })
    OrdenLibros.ANADIDOS -> l.sortedByDescending { it.anadido }
    OrdenLibros.TERMINADO -> l.sortedWith(compareByDescending<Libro> { it.terminado }.thenByDescending { it.ultima })
    OrdenLibros.TITULO -> l.sortedBy { norm(it.titulo) }
    OrdenLibros.AUTOR -> l.sortedWith(compareBy<Libro> { it.autor.isBlank() }.thenBy { norm(it.autor) }.thenBy { norm(it.titulo) })
    OrdenLibros.PAGINAS -> l.sortedWith(compareBy<Libro> { it.paginas <= 0 }.thenByDescending { it.paginas }.thenBy { norm(it.titulo) })
}

/** El orden que has elegido para cada estante (se recuerda al cerrar la app). */
object OrdenGuardado {
    private fun prefs(c: android.content.Context) = c.getSharedPreferences("marfil_orden", android.content.Context.MODE_PRIVATE)
    fun de(c: android.content.Context, e: Estado): OrdenLibros = OrdenLibros.entries.firstOrNull { it.name == prefs(c).getString(e.name, null) && (it != OrdenLibros.TERMINADO || e == Estado.LEIDO) } ?: ordenInicial(e)
    fun pon(c: android.content.Context, e: Estado, o: OrdenLibros) { prefs(c).edit().putString(e.name, o.name).apply() }
}

/** Lo que dice debajo del título de un libro: autor y cómo va. */
fun metaLibro(l: Libro, conEstado: Boolean = false): String = listOf(
    l.autor,
    when (l.estado) { Estado.LEYENDO -> porcentaje(l.fraccion); Estado.LEIDO -> if (conEstado) "leído" else ""; Estado.PENDIENTE -> if (conEstado) "pendiente" else ""; Estado.ABANDONADO -> if (conEstado) "abandonado" else "" },
    if (l.enOtroDispositivo) "en otro dispositivo" else ""
).filter { it.isNotBlank() }.joinToString(" · ")

/**
 * Un libro en una lista: su portada, el título y el autor a la izquierda y, a la derecha y en el acento, sus páginas («320 págs», o «118/320» si lo
 * llevas empezado). [numero]: el que tiene dentro de su saga.
 */
@Composable
fun FilaLibro(l: Libro, alAbrir: (Libro) -> Unit, modifier: Modifier = Modifier, portada: Dp = 54.dp, numero: Int = 0, conEstado: Boolean = false, sangria: Dp = 0.dp, extra: String = "") {
    Row(modifier.fillMaxWidth().clickable { alAbrir(l) }.padding(start = sangria, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        if (numero > 0) Text("$numero", color = Acento, fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, modifier = Modifier.width(26.dp))
        PortadaLibro(l, Modifier.width(portada).aspectRatio(0.68f), radio = 4, conTitulo = false)
        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
            Text(l.titulo, color = Charcoal, fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = if (portada >= 54.dp) 20.sp else 18.sp, lineHeight = if (portada >= 54.dp) 22.sp else 20.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            val meta = listOf(metaLibro(l, conEstado), extra).filter { it.isNotBlank() }.joinToString(" · ")
            if (meta.isNotBlank()) Text(meta.uppercase(), color = Muted, fontFamily = Body, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, maxLines = 2, modifier = Modifier.padding(top = 2.dp))
        }
        val pag = Paginas.texto(l)
        if (pag.isNotBlank()) Text(pag, color = Acento, fontFamily = Body, fontWeight = FontWeight.Black, fontSize = 12.sp, letterSpacing = 0.6.sp, maxLines = 1)
    }
}

/** El abanico de portadas de una saga (hasta tres, una sobre otra). */
@Composable
fun AbanicoSaga(libros: List<Libro>, modifier: Modifier = Modifier) {
    val v = libros.take(3)
    Box(modifier.width(38.dp + 14.dp * (v.size - 1).coerceAtLeast(0)).height(56.dp)) {
        v.asReversed().forEachIndexed { i, l ->
            val k = v.size - 1 - i
            PortadaLibro(l, Modifier.padding(start = 14.dp * k).width(38.dp).height(56.dp), radio = 3, conTitulo = false)
        }
    }
}

/** «3 libros · 1 leído»: lo que dice una saga. */
fun resumenSaga(todos: List<Libro>): String {
    val leidos = todos.count { it.estado == Estado.LEIDO }
    return listOfNotNull(
        "${todos.size} ${if (todos.size == 1) "libro" else "libros"}",
        if (leidos > 0) "$leidos ${if (leidos == 1) "leído" else "leídos"}" else null,
        if (todos.any { it.estado == Estado.LEYENDO }) "leyendo" else null
    ).joinToString(" · ")
}

/** Una saga en una lista: el abanico, su nombre y cuántos hay; un toque la abre y enseña sus libros; «Ver saga →» va a su página. */
@Composable
fun FilaSaga(f: FilaLista.DeSaga, abierta: Boolean, alAlternar: () -> Unit, alAbrirSaga: (Saga) -> Unit, alAbrirLibro: (Libro) -> Unit) {
    val todos = remember(f.saga, Biblioteca.libros.size) { Sagas.libros(f.saga) }
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().clickable(onClick = alAlternar).padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            AbanicoSaga(f.miembros)
            Column(Modifier.weight(1f).padding(start = 16.dp, end = 8.dp)) {
                Text(f.saga.nombre, color = Charcoal, fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 22.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(("Saga · " + resumenSaga(todos)).uppercase(), color = Muted, fontFamily = Body, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, maxLines = 2, modifier = Modifier.padding(top = 2.dp))
            }
            Text(if (abierta) "−" else "+", color = Acento, fontFamily = Body, fontWeight = FontWeight.Black, fontSize = 22.sp)
        }
        if (abierta) {
            Column(Modifier.fillMaxWidth().padding(start = 10.dp)) {
                f.miembros.forEach { l ->
                    Filete(1.dp, Line)
                    FilaLibro(l, alAbrirLibro, portada = 46.dp, numero = f.saga.claves.indexOf(l.clave) + 1)
                }
                EnlaceRevista("Ver la saga →", acento = true) { alAbrirSaga(f.saga) }
            }
        }
    }
    Filete(1.dp, Line)
}

/** Las filas (libros sueltos y sagas) de una lista de libros, con las sagas que estén abiertas. */
@OptIn(ExperimentalFoundationApi::class)
private fun androidx.compose.foundation.lazy.LazyListScope.filas(
    libros: List<Libro>, abiertas: Set<String>, alternar: (String) -> Unit, alAbrir: (Libro) -> Unit, alAbrirSaga: (Saga) -> Unit, conEstado: Boolean = false
) {
    val filas = Sagas.agrupar(libros)
    items(filas, key = { when (it) { is FilaLista.Suelto -> "l:" + it.libro.id; is FilaLista.DeSaga -> "s:" + it.saga.id } }) { f ->
        when (f) {
            is FilaLista.Suelto -> { FilaLibro(f.libro, alAbrir, conEstado = conEstado, extra = f.saga?.let { "Saga ${it.nombre} · ${f.numero}" }.orEmpty()); Filete(1.dp, Line) }
            is FilaLista.DeSaga -> FilaSaga(f, f.saga.id in abiertas, { alternar(f.saga.id) }, alAbrirSaga, alAbrir)
        }
    }
}

private fun vacioDe(e: Estado) = when (e) {
    Estado.LEYENDO -> "No tienes nada a medias. Abre un libro y saldrá aquí."
    Estado.PENDIENTE -> "No tienes nada pendiente: lo que añadas sin empezar saldrá aquí."
    Estado.LEIDO -> "Aún no has terminado ninguno. Cuando llegues al final, saldrá aquí."
    Estado.ABANDONADO -> "No has abandonado ninguno. Si dejas un libro, márcalo como abandonado desde su ficha."
}

/** Una página de un estante: título enorme y sus libros (con las sagas juntas). */
@Composable
private fun PaginaEstante(estado: Estado, todos: List<Libro>, alAbrir: (Libro) -> Unit, alAbrirSaga: (Saga) -> Unit, alAnadir: () -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var orden by remember(estado) { mutableStateOf(OrdenGuardado.de(ctx, estado)) }
    val libros = remember(todos, estado, orden) { ordenar(orden, todos.filter { it.estado == estado }) }
    var abiertas by rememberSaveable(estado) { mutableStateOf(setOf<String>()) }
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Column {
                Antetitulo("Sección · ${libros.size} ${if (libros.size == 1) "libro" else "libros"}", Modifier.padding(top = 12.dp), color = Acento, conCuadro = false)
                FitTitle(if (estado == Estado.LEYENDO) "Leyendo" else estado.nombre + "s", 52.sp, 56.sp, letterSpacing = (-1.5).sp, modifier = Modifier.padding(top = 2.dp), unaLinea = true)
                Filete(1.dp, Line)
                // Cómo se ordena este estante (se recuerda)
                // («Terminado» solo en Leídos)
                val ordenes = OrdenLibros.entries.filter { it != OrdenLibros.TERMINADO || estado == Estado.LEIDO }
                FiltrosRevista(ordenes.map { it.nombre }, ordenes.indexOf(orden).coerceAtLeast(0), Modifier.padding(top = 4.dp)) { i -> orden = ordenes[i]; OrdenGuardado.pon(ctx, estado, orden) }
                Filete(1.dp, Line)
            }
        }
        if (libros.isEmpty()) item {
            Column(Modifier.fillMaxWidth().padding(top = 30.dp)) {
                Text(if (todos.isEmpty()) "Todavía no hay nada aquí." else vacioDe(estado), color = Muted, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 18.sp, lineHeight = 24.sp)
                if (todos.isEmpty()) MagLink("Añadir un libro", accent = true, onClick = alAnadir)
            }
        }
        filas(libros, abiertas, { id -> abiertas = if (id in abiertas) abiertas - id else abiertas + id }, alAbrir, alAbrirSaga)
        item { Spacer(Modifier.height(24.dp)) }
    }
}

/** Los autores: un toque en uno enseña sus libros (con sus sagas juntas). */
@Composable
private fun PaginaAutores(todos: List<Libro>, alAbrir: (Libro) -> Unit, alAbrirSaga: (Saga) -> Unit) {
    val autores = remember(todos) { todos.filter { it.autor.isNotBlank() }.groupBy { norm(it.autor) }.entries.sortedBy { it.key }.map { (k, ls) -> Triple(k, ls.first().autor, ls) } }
    var abiertos by rememberSaveable { mutableStateOf(setOf<String>()) }
    var sagasAbiertas by rememberSaveable { mutableStateOf(setOf<String>()) }
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Column {
                Antetitulo("Sección · ${autores.size} ${if (autores.size == 1) "autor" else "autores"}", Modifier.padding(top = 12.dp), color = Acento, conCuadro = false)
                FitTitle("Autores", 52.sp, 56.sp, letterSpacing = (-1.5).sp, modifier = Modifier.padding(top = 2.dp), unaLinea = true)
                Filete(1.dp, Line)
            }
        }
        if (autores.isEmpty()) item { Text("Aún no hay autores: los libros sin autor no salen aquí.", color = Muted, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 18.sp, modifier = Modifier.padding(top = 30.dp)) }
        autores.forEach { (k, nombre, ls) ->
            item(key = "a:$k") {
                val abierto = k in abiertos
                Row(Modifier.fillMaxWidth().clickable { abiertos = if (abierto) abiertos - k else abiertos + k }.padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(nombre, color = Charcoal, fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, modifier = Modifier.weight(1f))
                    Text("${ls.size} ${if (ls.size == 1) "libro" else "libros"}".uppercase(), color = Muted, fontFamily = Body, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    Text(if (abierto) "  −" else "  +", color = Acento, fontFamily = Body, fontWeight = FontWeight.Black, fontSize = 20.sp)
                }
                Filete(1.dp, Line)
            }
            if (k in abiertos) {
                val filas = Sagas.agrupar(ls.sortedBy { norm(it.titulo) })
                items(filas, key = { "a:$k:" + when (it) { is FilaLista.Suelto -> "l" + it.libro.id; is FilaLista.DeSaga -> "s" + it.saga.id } }) { f ->
                    Column(Modifier.padding(start = 12.dp)) {
                        when (f) {
                            is FilaLista.Suelto -> { FilaLibro(f.libro, alAbrir, conEstado = true, extra = f.saga?.let { "Saga ${it.nombre} · ${f.numero}" }.orEmpty()); Filete(1.dp, Line) }
                            is FilaLista.DeSaga -> FilaSaga(f, f.saga.id in sagasAbiertas, { sagasAbiertas = if (f.saga.id in sagasAbiertas) sagasAbiertas - f.saga.id else sagasAbiertas + f.saga.id }, alAbrirSaga, alAbrir)
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

/** Todas las sagas, por nombre: cada una con su abanico y sus libros desplegables; desde aquí se crea una nueva (y se abre para meterle libros). */
@Composable
private fun PaginaSagas(todos: List<Libro>, alAbrir: (Libro) -> Unit, alAbrirSaga: (Saga) -> Unit) {
    val sagas = Sagas.lista.toList()
    val filas = remember(sagas, todos) { sagas.sortedBy { norm(it.nombre) }.map { FilaLista.DeSaga(it, Sagas.libros(it, todos)) } }
    var abiertas by rememberSaveable { mutableStateOf(setOf<String>()) }
    var nueva by rememberSaveable { mutableStateOf<String?>(null) }
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Column {
                Antetitulo("Sección · ${sagas.size} ${if (sagas.size == 1) "saga" else "sagas"}", Modifier.padding(top = 12.dp), color = Acento, conCuadro = false)
                FitTitle("Sagas", 52.sp, 56.sp, letterSpacing = (-1.5).sp, modifier = Modifier.padding(top = 2.dp), unaLinea = true)
                Filete(1.dp, Line)
                EnlaceRevista("+ Nueva saga", Modifier.padding(vertical = 4.dp), acento = true) { nueva = "" }
                Filete(1.dp, Line)
            }
        }
        if (filas.isEmpty()) item {
            Text("Aún no hay sagas. Créala aquí o desde la ficha de un libro («Saga»), y luego ve metiendo sus libros en orden.",
                color = Muted, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 18.sp, lineHeight = 24.sp, modifier = Modifier.padding(top = 30.dp))
        }
        items(filas, key = { "s:" + it.saga.id }) { f ->
            FilaSaga(f, f.saga.id in abiertas, { abiertas = if (f.saga.id in abiertas) abiertas - f.saga.id else abiertas + f.saga.id }, alAbrirSaga, alAbrir)
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
    nueva?.let { n ->
        DialogoNuevaSaga(n, alCambiar = { nueva = it }, alCerrar = { nueva = null }) { nombre -> nueva = null; alAbrirSaga(Sagas.crear(nombre)) }
    }
}

/** Lo que sale al buscar: todos los libros que cuadran, estén en el estante que estén. */
@Composable
private fun Resultados(busca: String, todos: List<Libro>, alAbrir: (Libro) -> Unit) {
    val q = norm(busca)
    val l = remember(busca, todos) {
        todos.filter { norm(it.titulo).contains(q) || norm(it.autor).contains(q) || Sagas.de(it.clave)?.let { s -> norm(s.nombre).contains(q) } == true }
            .sortedWith(compareByDescending<Libro> { it.ultima }.thenByDescending { it.anadido })
    }
    LazyColumn(Modifier.fillMaxSize()) {
        item { Antetitulo("Resultados · ${l.size}", Modifier.padding(top = 14.dp, bottom = 6.dp), color = Acento, conCuadro = false); Filete(1.dp, Line) }
        if (l.isEmpty()) item { Text("Nada con «$busca».", color = Muted, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 18.sp, modifier = Modifier.padding(top = 24.dp)) }
        items(l, key = { it.id }) { x -> FilaLibro(x, alAbrir, conEstado = true); Filete(1.dp, Line) }
    }
}

/**
 * Las secciones de la biblioteca como páginas por las que te mueves (con el dedo o con las pestañas): Leyendo, Pendientes, Leídos, Abandonados, Autores y Sagas.
 * Buscar mira en todos los estantes a la vez.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PantSecciones(inicial: Destino, alVolver: () -> Unit, alAbrir: (Libro) -> Unit, alAbrirSaga: (Saga) -> Unit, alAnadir: () -> Unit, buscandoInicial: Boolean = false) {
    val paginas = listOf(Destino.Leyendo, Destino.Pendientes, Destino.Leidos, Destino.Abandonados, Destino.Autores, Destino.Sagas)
    val pager = rememberPagerState(initialPage = paginas.indexOf(inicial).coerceAtLeast(0)) { paginas.size }
    val ambito = rememberCoroutineScope()
    var buscando by rememberSaveable { mutableStateOf(buscandoInicial) }
    var busca by rememberSaveable { mutableStateOf("") }
    val todos = Biblioteca.libros.toList()
    val cuentas = listOf(Estado.LEYENDO, Estado.PENDIENTE, Estado.LEIDO, Estado.ABANDONADO).map { e -> todos.count { it.estado == e } } + todos.filter { it.autor.isNotBlank() }.map { norm(it.autor) }.distinct().size + Sagas.lista.size
    Column(Modifier.fillMaxSize().background(Paper).navigationBarsPadding().padding(horizontal = 28.dp)) {
        RevistaTopBar(alVolver, "Marfil") {
            EnlaceRevista("Buscar", acento = buscando) { buscando = !buscando; if (!buscando) busca = "" }
            EnlaceRevista("+ Añadir", acento = true) { alAnadir() }
        }
        Filete(3.dp)
        if (buscando) CampoRevista("Buscar", busca, "título, autor o saga") { busca = it }
        PestanasPaginas(listOf("Leyendo", "Pendientes", "Leídos", "Abandonados", "Autores", "Sagas"), pager.currentPage, cuentas) { i -> ambito.launch { pager.animateScrollToPage(i) } }
        if (busca.isNotBlank()) Resultados(busca, todos, alAbrir)
        else HorizontalPager(pager, Modifier.weight(1f).fillMaxWidth(), beyondViewportPageCount = 1, key = { paginas[it].name }) { i ->
            when (paginas[i]) {
                Destino.Leyendo -> PaginaEstante(Estado.LEYENDO, todos, alAbrir, alAbrirSaga, alAnadir)
                Destino.Pendientes -> PaginaEstante(Estado.PENDIENTE, todos, alAbrir, alAbrirSaga, alAnadir)
                Destino.Leidos -> PaginaEstante(Estado.LEIDO, todos, alAbrir, alAbrirSaga, alAnadir)
                Destino.Abandonados -> PaginaEstante(Estado.ABANDONADO, todos, alAbrir, alAbrirSaga, alAnadir)
                Destino.Sagas -> PaginaSagas(todos, alAbrir, alAbrirSaga)
                else -> PaginaAutores(todos, alAbrir, alAbrirSaga)
            }
        }
    }
}

/** Las pestañas de las secciones (como las de Atrio): la elegida en tinta con el subrayado de acento; su número, en acento. Si no caben, se deslizan y la elegida siempre se ve. */
@Composable
private fun PestanasPaginas(nombres: List<String>, sel: Int, cuentas: List<Int>, onSel: (Int) -> Unit) {
    val scroll = androidx.compose.foundation.rememberScrollState()
    val xs = remember { androidx.compose.runtime.mutableStateMapOf<Int, Int>() }
    LaunchedEffect(sel, xs[sel]) { xs[sel]?.let { scroll.animateScrollTo((it - 12).coerceAtLeast(0)) } }
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().horizontalScroll(scroll), horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.Bottom) {
            nombres.forEachIndexed { i, n ->
                val on = i == sel
                Row(Modifier.onGloballyPositioned { xs[i] = it.positionInParent().x.toInt() }.clickable { onSel(i) }
                    .drawBehind { if (on) drawRect(Acento, topLeft = androidx.compose.ui.geometry.Offset(0f, size.height - 3.dp.toPx()), size = androidx.compose.ui.geometry.Size(size.width, 3.dp.toPx())) }
                    .padding(top = 12.dp, bottom = 10.dp), verticalAlignment = Alignment.Bottom) {
                    Text(n.uppercase(), color = if (on) Charcoal else Muted, fontFamily = Body, fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 1.6.sp, maxLines = 1)
                    Text(" ${cuentas.getOrElse(i) { 0 }}", color = Acento, fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, maxLines = 1)
                }
            }
        }
        Filete(1.dp, Line)
    }
}
