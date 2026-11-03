package com.gonzalo.marfil

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.DateFormat
import java.util.Date

/** La ficha de un libro: portada, título y autor, por dónde vas, su estante, y leerlo o quitarlo. */
@Composable
fun PantFicha(id: String, alCerrar: () -> Unit, alLeer: (Libro) -> Unit) {
    val l = Biblioteca.por(id)
    if (l == null) { LaunchedEffect(Unit) { alCerrar() }; return }
    var quitar by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().background(Paper).verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 28.dp)) {
        RevistaTopBar(alCerrar, "Volver") {
            EnlaceRevista(if (l.favorito) "★ Favorito" else "☆ Favorito", acento = l.favorito) { Biblioteca.poner(l.copy(favorito = !l.favorito)) }
        }
        Filete(3.dp)
        Spacer(Modifier.height(14.dp))
        RevistaHeadline(l.titulo, l.autor.ifBlank { "Libro" })
        Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 14.dp)) {
            PortadaLibro(l, Modifier.width(140.dp).aspectRatio(0.68f))
            Column(Modifier.weight(1f).padding(start = 18.dp)) {
                Text(porcentaje(l.fraccion), color = Acento, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 48.sp, lineHeight = 48.sp)
                Barra(l.fraccion)
                if (l.msLeidos >= 60_000) Text("${tiempoDe(l.msLeidos)} leídos".uppercase(), color = Muted, fontFamily = Body, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, modifier = Modifier.padding(top = 8.dp))
                if (l.ultima > 0) Text("Leído por última vez el ${DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(l.ultima))}", color = Muted, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 14.sp, modifier = Modifier.padding(top = 6.dp))
            }
        }
        Filete(1.dp)
        Row(Modifier.fillMaxWidth()) {
            EnlaceRevista(if (l.enOtroDispositivo) "Sin archivo aquí" else "Leer →", acento = !l.enOtroDispositivo) { alLeer(l) }
        }
        Filete(1.dp)
        Etiqueta("Estante", Modifier.padding(top = 14.dp))
        MagChoice(Estado.entries.map { it.nombre }, l.estado.ordinal) { i -> Biblioteca.poner(l.copy(estado = Estado.entries[i])) }
        Spacer(Modifier.height(10.dp))
        MagFact("Formato", l.formato.uppercase().ifBlank { "—" })
        MagFact("Archivo", if (l.enOtroDispositivo) "en otro de tus dispositivos" else l.archivo)
        Spacer(Modifier.height(6.dp))
        Filete(1.dp)
        SeccionNotas(l)
        Spacer(Modifier.height(16.dp))
        Filete(1.dp, Line)
        MagLink("Quitar de la biblioteca", "El archivo se borra de este móvil") { quitar = true }
        Spacer(Modifier.height(30.dp))
    }
    if (quitar) AlertDialog(
        onDismissRequest = { quitar = false }, containerColor = Paper, shape = esquina(4.dp),
        title = { Text("¿Quitar «${l.titulo}»?", color = Charcoal, fontFamily = Display, fontWeight = FontWeight.Bold) },
        text = { Text("Se quita de tu biblioteca (en tus otros dispositivos también) y se borra su archivo de este móvil.", color = Muted, fontFamily = Body) },
        confirmButton = { TextButton({
            runCatching { java.io.File(android.net.Uri.parse(l.uri).path.orEmpty()).takeIf { it.exists() }?.delete() }
            Biblioteca.quitar(l.id); quitar = false; alCerrar()
        }) { Text("QUITAR", color = Acento, fontFamily = Body, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp) } },
        dismissButton = { TextButton({ quitar = false }) { Text("CANCELAR", color = Charcoal, fontFamily = Body, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp) } }
    )
}
