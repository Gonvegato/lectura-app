package com.gonzalo.marfil

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * La portada de Marfil. La cabecera y el menú, con las mismas medidas que los de Atrio (y que los de Pórtico); lo de debajo, propio:
 * «Ahora» (el libro que estás leyendo, enmarcado, con lo que llevas y un botón para seguir) y «En la mesilla» (los pendientes, con la portada a la izquierda).
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun PantInicio(alAbrir: (Libro) -> Unit, alLeer: (Libro) -> Unit, alAnadir: () -> Unit, alIr: (Destino) -> Unit, alBuscar: () -> Unit) {
    val hoy = remember { Calendar.getInstance() }
    val libros = Biblioteca.libros.toList()
    val ahora = libros.filter { it.estado == Estado.LEYENDO }.maxByOrNull { it.ultima } ?: libros.firstOrNull { it.estado == Estado.LEYENDO }
    val mesilla = libros.filter { it.estado == Estado.PENDIENTE }.sortedByDescending { it.anadido }.take(6)
    val leidos = libros.filter { it.estado == Estado.LEIDO }
    val secciones = listOf<Pair<String, () -> Unit>>(
        "Leyendo" to { alIr(Destino.Leyendo) }, "Pendientes" to { alIr(Destino.Pendientes) }, "Leídos" to { alIr(Destino.Leidos) }, "Autores" to { alIr(Destino.Autores) }
    )

    // La cabecera y el menú, exactamente como en la portada de Atrio (RevistaHome): mismos márgenes, mismo tamaño de cabecera, de filetes y de letra
    Column(Modifier.fillMaxSize().background(Paper).verticalScroll(rememberScrollState()).statusBarsPadding().padding(horizontal = 28.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            EnlaceRevista("Buscar") { alBuscar() }
            EnlaceRevista("Añadir", Modifier.padding(start = 14.dp)) { alAnadir() }
            Spacer(Modifier.weight(1f))
            Icon(Icons.Filled.Settings, "Ajustes", tint = Charcoal, modifier = Modifier.padding(start = 6.dp).combinedClickable(onClick = { alIr(Destino.Ajustes) }).padding(8.dp).size(22.dp))
        }
        val ancho = minOf(androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp, 1080)
        Cabecera(if (ancho >= 700) 132.sp else 92.sp, Modifier.padding(top = 8.dp))
        FileteCabecera(3.dp)
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
            Text("Nº ${hoy.get(Calendar.DAY_OF_YEAR)}", color = Charcoal, fontFamily = Body, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Spacer(Modifier.weight(1f))
            Text(SimpleDateFormat("EEEE d 'de' MMMM", Locale("es", "ES")).format(hoy.time).uppercase(), color = Charcoal, fontFamily = Body, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        }
        Filete(1.dp)
        BarrasLectura(libros)
        Column(Modifier.fillMaxWidth().padding(top = 18.dp)) {
            Filete(1.dp)
            FlowRow(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                secciones.forEach { (t, f) ->
                    Text(t.uppercase(), color = Charcoal, fontFamily = Body, fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 1.5.sp, maxLines = 1,
                        modifier = Modifier.clickable(onClick = f).padding(vertical = 9.dp))
                }
            }
            Filete(1.dp)
        }
        Spacer(Modifier.height(18.dp))

        if (libros.isEmpty()) {
            Text("Tu biblioteca está vacía. Añade tu primer libro (EPUB, PDF, MOBI, AZW3, FB2, CBZ o TXT) o trae los de Atrio desde tu cuenta.", color = Muted, fontFamily = Body, fontSize = 15.sp, lineHeight = 22.sp)
            Spacer(Modifier.height(6.dp))
            MagLink("Añadir un libro", "Del móvil: se copia a la app y se lee su portada", accent = true) { alAnadir() }
        }

        // ---- Ahora: el libro en curso, enmarcado (un cuadrado del color del texto alrededor, como «En portada» de Atrio)
        if (ahora != null) Column(Modifier.fillMaxWidth().border(1.5.dp, Charcoal).padding(20.dp)) {
            Antetitulo("Ahora", Modifier.padding(bottom = 12.dp))
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                PortadaLibro(ahora, Modifier.width(112.dp).aspectRatio(0.68f).combinedClickable(onClick = { alAbrir(ahora) }))
                Column(Modifier.weight(1f).padding(start = 18.dp).fillMaxHeight(), verticalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        FitTitle(ahora.titulo, 30.sp, 32.sp, maxLines = 3, minSize = 16f, letterSpacing = (-0.5).sp, modifier = Modifier.clickable { alAbrir(ahora) })
                        if (ahora.autor.isNotBlank()) Text(ahora.autor, color = Muted, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 16.sp, modifier = Modifier.padding(top = 2.dp))
                    }
                    Column {
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text("${(ahora.fraccion * 100).toInt().coerceIn(0, 100)}", color = Acento, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 56.sp, lineHeight = 56.sp)
                            Text(" %", color = Muted, fontFamily = Body, fontSize = 14.sp, modifier = Modifier.padding(bottom = 10.dp))
                        }
                        Barra(ahora.fraccion)
                        if (ahora.msLeidos >= 60_000) Text("${tiempoDe(ahora.msLeidos)} leídos".uppercase(), color = Muted, fontFamily = Body, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, modifier = Modifier.padding(top = 6.dp))
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(if (ahora.enOtroDispositivo) "ESTÁ EN OTRO DISPOSITIVO" else "SEGUIR LEYENDO →", color = if (ahora.enOtroDispositivo) Muted else Acento, fontFamily = Body, fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 1.4.sp,
                modifier = Modifier.clickable { alLeer(ahora) }.padding(vertical = 8.dp))
        }

        // ---- En la mesilla: los pendientes, con la portada a la izquierda y el nombre a su derecha (más pequeños que «Ahora»)
        if (mesilla.isNotEmpty()) {
            Spacer(Modifier.height(30.dp))
            Antetitulo("En la mesilla", Modifier.padding(bottom = 6.dp))
            Filete(1.dp)
            mesilla.forEach { l ->
                Row(Modifier.fillMaxWidth().combinedClickable(onClick = { alAbrir(l) }).padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    PortadaLibro(l, Modifier.width(56.dp).aspectRatio(0.68f))
                    Column(Modifier.weight(1f).padding(start = 14.dp)) {
                        Text(l.titulo, color = Charcoal, fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 20.sp, maxLines = 2)
                        if (l.autor.isNotBlank()) Text(l.autor, color = Muted, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 14.sp, maxLines = 1, modifier = Modifier.padding(top = 2.dp))
                    }
                }
                Filete(1.dp)
            }
            Text("VER TODOS →", color = Acento, fontFamily = Body, fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 1.4.sp,
                modifier = Modifier.clickable { alIr(Destino.Pendientes) }.padding(vertical = 12.dp))
        }

        Text("Marfil · hecho en casa", color = Muted, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 12.sp, modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 18.dp))
        Spacer(Modifier.height(24.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()))
    }
}

/** Una barra fina de lo leído. */
@Composable
fun Barra(f: Float, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(3.dp).background(Line)) { Box(Modifier.fillMaxWidth(f.coerceIn(0f, 1f)).fillMaxHeight().background(Acento)) }
}
