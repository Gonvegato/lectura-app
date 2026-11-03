package com.gonzalo.marfil

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONObject
import java.text.DateFormat
import java.util.Date

/** Un subrayado (con su nota, si la tiene) de un libro: lo que guarda el lector. */
class NotaLibro(val value: String, val texto: String, val nota: String, val etiqueta: String, val creada: Long, val fraccion: Double)

/** Los subrayados y notas de un libro, en el orden del libro (y, a igual sitio, por fecha). */
fun notasDe(st: JSONObject): List<NotaLibro> {
    val a = st.optJSONArray("notes") ?: return emptyList()
    return (0 until a.length()).mapNotNull { a.optJSONObject(it) }.filter { it.optString("text").isNotBlank() }.map {
        NotaLibro(it.optString("value"), it.optString("text"), it.optString("note"), it.optString("label"), it.optLong("created"), it.optDouble("frac", -1.0))
    }.sortedWith(compareBy<NotaLibro>({ if (it.fraccion < 0) 2.0 else it.fraccion }, { it.creada }))
}

/** «Subrayados y notas» en la ficha: tocar uno abre el libro justo ahí (si el archivo está en tu kDrive, se baja antes). */
@Composable
fun SeccionNotas(l: Libro) {
    val ctx = LocalContext.current
    val notas = remember(l.id, l.ultima, l.msPropios) { notasDe(EstadoLectura.estado(ctx, l.id)) }
    var todas by remember { mutableStateOf(false) }
    Antetitulo("Subrayados y notas", Modifier.padding(top = 18.dp, bottom = 6.dp))
    if (notas.isEmpty()) {
        Text("Aún no has subrayado nada. En el lector, mantén pulsada una palabra y toca Subrayar o Nota.", color = Muted, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 15.sp, lineHeight = 20.sp)
        return
    }
    val conNota = notas.count { it.nota.isNotBlank() }
    Text(listOfNotNull("${notas.size} subrayado${if (notas.size == 1) "" else "s"}", if (conNota > 0) "$conNota con nota" else null).joinToString(" · ").uppercase(),
        color = Muted, fontFamily = Body, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
    Spacer(Modifier.height(6.dp))
    (if (todas) notas else notas.take(4)).forEachIndexed { i, n ->
        if (i > 0) Filete(1.dp, Line)
        Column(Modifier.fillMaxWidth().clickable { Lector.abrir(ctx, l, goto = n.value) }.padding(vertical = 10.dp)) {
            Text("«${n.texto}»", color = Charcoal, fontFamily = Display, fontSize = 17.sp, lineHeight = 23.sp, maxLines = if (todas) 12 else 5, overflow = TextOverflow.Ellipsis)
            if (n.nota.isNotBlank()) Text(n.nota, color = Acento, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 14.sp, lineHeight = 19.sp, modifier = Modifier.padding(top = 4.dp))
            Text(listOfNotNull(n.etiqueta.ifBlank { null }, if (n.creada > 0) DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(n.creada)) else null).joinToString(" · ").uppercase(),
                color = Muted, fontFamily = Body, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, modifier = Modifier.padding(top = 4.dp))
        }
    }
    if (notas.size > 4) Text(if (todas) "VER MENOS" else "VER LOS ${notas.size} →", color = Acento, fontFamily = Body, fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp,
        modifier = Modifier.clickable { todas = !todas }.padding(top = 6.dp, bottom = 4.dp))
}
