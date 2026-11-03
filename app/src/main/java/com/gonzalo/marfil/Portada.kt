package com.gonzalo.marfil

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import java.io.File

/** Los colores de tela de los libros sin portada: uno por título, siempre el mismo. */
private val TELAS = listOf(0xFF7A2E2E, 0xFF2F4A6B, 0xFF35523F, 0xFF5B3F6B, 0xFF8A5A2B, 0xFF3F5E63, 0xFF6B6B3A, 0xFF4A4A52).map { Color(it) }
private val PAPEL_CLARO = Color(0xFFF3EDE0)

fun telaDe(titulo: String): Color = TELAS[(norm(titulo).hashCode() and 0x7fffffff) % TELAS.size]

/**
 * La portada de un libro: su imagen, o una tela de color con sus letras. En pequeño solo la inicial (el título ya está al lado); en grande,
 * el título y el autor bien puestos, como en un libro de verdad. El lomo lleva una sombra fina.
 */
@Composable
fun PortadaLibro(l: Libro, modifier: Modifier = Modifier, radio: Int = 5, conTitulo: Boolean = true) {
    val f = remember(l.portada) { l.portada.takeIf { it.isNotBlank() }?.let { File(it) }?.takeIf { it.exists() } }
    val url = l.portada.takeIf { f == null && it.startsWith("http") }
    BoxWithConstraints(modifier.clip(RoundedCornerShape(radio.dp))) {
        val ancho = maxWidth
        if (f != null || url != null) {
            Box(Modifier.fillMaxSize().background(Sand))
            AsyncImage(f ?: url, l.titulo, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            val tela = telaDe(l.titulo)
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(lerpColor(tela, Color.White, 0.10f), lerpColor(tela, Color.Black, 0.32f)))))
            // El marco fino de una tapa
            Box(Modifier.fillMaxSize().padding(ancho * 0.07f).border(0.8.dp, PAPEL_CLARO.copy(alpha = 0.32f)))
            fun sp(f: Float) = (ancho.value * f).sp
            if (ancho < 96.dp || !conTitulo) {
                Text(l.titulo.trim().firstOrNull()?.uppercase() ?: "·", color = PAPEL_CLARO.copy(alpha = 0.88f), fontFamily = Display, fontWeight = FontWeight.SemiBold,
                    fontSize = sp(0.46f), modifier = Modifier.align(Alignment.Center))
            } else {
                Column(Modifier.fillMaxSize().padding(horizontal = ancho * 0.15f, vertical = ancho * 0.16f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Text(l.titulo, color = PAPEL_CLARO, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = sp(0.115f), lineHeight = sp(0.135f), textAlign = TextAlign.Center,
                            maxLines = 5, overflow = TextOverflow.Ellipsis)
                    }
                    if (l.autor.isNotBlank()) {
                        Box(Modifier.padding(vertical = ancho * 0.03f).width(ancho * 0.12f).height(1.dp).background(PAPEL_CLARO.copy(alpha = 0.55f)))
                        Text(l.autor.uppercase(), color = PAPEL_CLARO.copy(alpha = 0.78f), fontFamily = Body, fontWeight = FontWeight.Bold, fontSize = sp(0.052f), letterSpacing = sp(0.006f),
                            textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
        // El lomo: una sombra fina a la izquierda
        Box(Modifier.fillMaxHeight().width(ancho * 0.07f).background(Brush.horizontalGradient(listOf(Color.Black.copy(alpha = 0.22f), Color.Transparent))))
    }
}

private fun lerpColor(a: Color, b: Color, t: Float) = Color(a.red + (b.red - a.red) * t, a.green + (b.green - a.green) * t, a.blue + (b.blue - a.blue) * t, 1f)
