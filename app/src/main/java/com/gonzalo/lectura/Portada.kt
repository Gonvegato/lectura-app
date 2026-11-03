package com.gonzalo.lectura

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import java.io.File

/** La portada de un libro: su imagen o, si no tiene, un papel con la inicial en grande y el título. */
@Composable
fun PortadaLibro(l: Libro, modifier: Modifier = Modifier, radio: Int = 8, conTitulo: Boolean = true) {
    val f = remember(l.portada) { l.portada.takeIf { it.isNotBlank() }?.let { File(it) }?.takeIf { it.exists() } }
    Box(modifier.clip(RoundedCornerShape(radio.dp)).background(Sand), contentAlignment = Alignment.Center) {
        if (f != null) AsyncImage(f, l.titulo, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else {
            Text(l.titulo.trim().firstOrNull()?.uppercase() ?: "·", color = Acento, fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 54.sp)
            if (conTitulo) Text(l.titulo, color = Charcoal, fontFamily = Display, fontSize = 11.sp, maxLines = 3, textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.BottomCenter).padding(horizontal = 6.dp, vertical = 8.dp))
        }
    }
}

