package com.gonzalo.lectura

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Una lista de libros (según el filtro): su título, su autor y por dónde vas. Un toque abre la ficha; «Leer →», el libro. */
@Composable
fun PantLibros(f: FiltroLibros, d: Destino, alVolver: () -> Unit, alAbrir: (Libro) -> Unit, alLeer: (Libro) -> Unit, alAnadir: () -> Unit) {
    val todos = Biblioteca.libros.toList()
    val lista = filtrar(todos, f)
    val titulo = when {
        f.autor != null -> f.autor!!
        d == Destino.Leyendo -> "Leyendo"; d == Destino.Pendientes -> "Pendientes"; d == Destino.Leidos -> "Leídos"; d == Destino.Favoritos -> "Favoritos"
        else -> "Todos"
    }
    LazyColumn(Modifier.fillMaxSize().background(Paper).statusBarsPadding().navigationBarsPadding().padding(horizontal = 28.dp)) {
        item {
            Column {
                RevistaTopBar(alVolver, "Lectura") { EnlaceRevista("+ Añadir", acento = true) { alAnadir() } }
                Antetitulo("Sección", Modifier.padding(top = 10.dp), color = Acento, conCuadro = false)
                FitTitle(titulo, 52.sp, 56.sp, letterSpacing = (-1.5).sp, modifier = Modifier.padding(top = 2.dp), unaLinea = true)
                Filete(3.dp)
                // Los estantes, como filtros de la misma lista (más el que has elegido en el menú)
                FiltrosRevista(listOf("Todos ${todos.size}", "Leyendo ${todos.count { it.estado == Estado.LEYENDO }}", "Pendientes ${todos.count { it.estado == Estado.PENDIENTE }}", "Leídos ${todos.count { it.estado == Estado.LEIDO }}"),
                    when (f.estado) { null -> 0; Estado.LEYENDO -> 1; Estado.PENDIENTE -> 2; Estado.LEIDO -> 3 }, Modifier.padding(top = 4.dp)) { i ->
                    f.estado = when (i) { 1 -> Estado.LEYENDO; 2 -> Estado.PENDIENTE; 3 -> Estado.LEIDO; else -> null }
                }
                CampoRevista("Buscar", f.busca, "título o autor") { f.busca = it }
                Filete(1.dp, Line)
            }
        }
        if (lista.isEmpty()) item {
            Column(Modifier.fillMaxWidth().padding(top = 36.dp)) {
                Text(if (todos.isEmpty()) "Todavía no hay nada aquí." else "Nada con ese filtro.", color = Muted, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 18.sp)
                if (todos.isEmpty()) MagLink("Añadir un libro", accent = true, onClick = alAnadir)
            }
        }
        items(lista, key = { it.id }) { l ->
            Row(Modifier.fillMaxWidth().clickable { alAbrir(l) }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                PortadaLibro(l, Modifier.width(54.dp).aspectRatio(0.68f), radio = 5, conTitulo = false)
                Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                    Text(l.titulo, color = Charcoal, fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 22.sp, maxLines = 2)
                    Text(listOf(l.autor, when (l.estado) { Estado.LEYENDO -> porcentaje(l.fraccion); Estado.LEIDO -> "leído"; else -> "pendiente" }, if (l.enOtroDispositivo) "en otro dispositivo" else "").filter { it.isNotBlank() }.joinToString(" · ").uppercase(),
                        color = Muted, fontFamily = Body, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, maxLines = 2, modifier = Modifier.padding(top = 2.dp))
                }
                if (!l.enOtroDispositivo) Text("LEER →", color = Acento, fontFamily = Body, fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 1.4.sp,
                    modifier = Modifier.clickable { alLeer(l) }.padding(start = 8.dp, top = 12.dp, bottom = 12.dp))
            }
            Filete(1.dp, Line)
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

/** Los autores de tu biblioteca, con cuántos libros tienes de cada uno; un toque abre sus libros. */
@Composable
fun PantAutores(alVolver: () -> Unit, alElegir: (String) -> Unit) {
    val autores = Biblioteca.libros.toList().filter { it.autor.isNotBlank() }.groupBy { norm(it.autor) }.map { (_, ls) -> ls.first().autor to ls.size }.sortedBy { norm(it.first) }
    LazyColumn(Modifier.fillMaxSize().background(Paper).statusBarsPadding().navigationBarsPadding().padding(horizontal = 28.dp)) {
        item {
            Column {
                RevistaTopBar(alVolver, "Lectura")
                Antetitulo("Sección", Modifier.padding(top = 10.dp), color = Acento, conCuadro = false)
                FitTitle("Autores", 52.sp, 56.sp, letterSpacing = (-1.5).sp, modifier = Modifier.padding(top = 2.dp), unaLinea = true)
                Filete(3.dp)
            }
        }
        if (autores.isEmpty()) item { Text("Aún no hay autores: los libros sin autor no salen aquí.", color = Muted, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 18.sp, modifier = Modifier.padding(top = 36.dp)) }
        items(autores, key = { it.first }) { (a, n) ->
            Row(Modifier.fillMaxWidth().clickable { alElegir(a) }.padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(a, color = Charcoal, fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, modifier = Modifier.weight(1f))
                Text("$n ${if (n == 1) "libro" else "libros"}".uppercase(), color = Muted, fontFamily = Body, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            }
            Filete(1.dp, Line)
        }
    }
}
