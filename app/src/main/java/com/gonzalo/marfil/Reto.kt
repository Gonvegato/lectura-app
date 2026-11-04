package com.gonzalo.marfil

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.Instant
import java.time.ZoneId

/** El año de una fecha (ms), con la hora de este móvil. */
fun anoDe(ms: Long): Int = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).year

/** Cuántos libros has terminado en [ano] (cada libro cuenta una vez al año, aunque lo termines dos veces). */
fun terminadosEn(libros: List<Libro>, ano: Int): Int = libros.count { l -> l.terminados.any { anoDe(it) == ano } }

/** El reto del año en la portada: «RETO 2026 · 7 DE 12 LIBROS» y una barra fina. Si no hay reto puesto, no sale. */
@Composable
fun LineaReto(libros: List<Libro>) {
    val ctx = LocalContext.current
    val meta = remember(libros) { Ajustes.metaAnual(ctx) }
    if (meta <= 0) return
    val ano = remember { anoDe(System.currentTimeMillis()) }
    val n = terminadosEn(libros, ano)
    Column(Modifier.fillMaxWidth().padding(top = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).background(Acento)); Spacer(Modifier.width(8.dp))
            Text(if (n >= meta) "RETO $ano CUMPLIDO · $n ${if (n == 1) "LIBRO" else "LIBROS"}" else "RETO $ano · $n DE $meta ${if (meta == 1) "LIBRO" else "LIBROS"}",
                fontFamily = Body, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.4.sp, color = if (n >= meta) Acento else Charcoal)
        }
        Barra((n.toFloat() / meta).coerceIn(0f, 1f), Modifier.padding(top = 8.dp))
    }
}
