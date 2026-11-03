package com.gonzalo.marfil

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Una portada que se ha encontrado en internet: una miniatura para elegir y la imagen grande para guardar. */
class Candidata(val miniatura: String, val grande: String, val fuente: String, val titulo: String)

/**
 * Las portadas: elegir una imagen del móvil, buscarla en internet (iTunes Libros y Open Library; ambas sin clave) o usar la del propio libro.
 * La que se elige se guarda en el móvil (con un nombre nuevo, para que se vea al momento) y, si tienes kDrive, se sube para que salga en tus otros dispositivos.
 */
object Portadas {
    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private fun get(url: String, ms: Int = 12000): String? = runCatching {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = ms; c.readTimeout = ms; c.setRequestProperty("User-Agent", "Marfil/1 (lector de libros personal)")
        if (c.responseCode !in 200..299) return null
        c.inputStream.use { it.readBytes().decodeToString() }
    }.getOrNull()

    /** iTunes: {"results":[{"trackName","artistName","artworkUrl100"}]} → la misma imagen en pequeño y en grande. */
    fun deItunes(json: String): List<Candidata> = runCatching {
        val a = JSONObject(json).optJSONArray("results") ?: return emptyList()
        (0 until a.length()).mapNotNull { i ->
            val o = a.optJSONObject(i) ?: return@mapNotNull null
            val u = o.optString("artworkUrl100").takeIf { it.contains("100x100") } ?: return@mapNotNull null
            Candidata(u.replace("100x100", "240x240"), u.replace("100x100", "800x800"), "iTunes", listOf(o.optString("trackName"), o.optString("artistName")).filter { it.isNotBlank() }.joinToString(" · "))
        }
    }.getOrDefault(emptyList())

    /** Open Library: {"docs":[{"title","author_name":[…],"cover_i":123}]} → la portada por su número. */
    fun deOpenLibrary(json: String): List<Candidata> = runCatching {
        val a = JSONObject(json).optJSONArray("docs") ?: return emptyList()
        (0 until a.length()).mapNotNull { i ->
            val o = a.optJSONObject(i) ?: return@mapNotNull null
            val id = o.optLong("cover_i", 0L).takeIf { it > 0 } ?: return@mapNotNull null
            val autor = o.optJSONArray("author_name")?.optString(0).orEmpty()
            Candidata("https://covers.openlibrary.org/b/id/$id-M.jpg", "https://covers.openlibrary.org/b/id/$id-L.jpg", "Open Library", listOf(o.optString("title"), autor).filter { it.isNotBlank() }.joinToString(" · "))
        }
    }.getOrDefault(emptyList())

    /** Busca portadas por título y autor (o por lo que escribas). Bloqueante (red). Primero las de iTunes (en español), luego las de Open Library. */
    fun buscar(titulo: String, autor: String = ""): List<Candidata> {
        val q = (titulo + " " + autor).trim()
        if (q.isBlank()) return emptyList()
        val it = get("https://itunes.apple.com/search?term=${enc(q)}&entity=ebook&limit=14&country=es")?.let { deItunes(it) }.orEmpty()
        val ol = get("https://openlibrary.org/search.json?q=${enc(q)}&limit=14&fields=title,author_name,cover_i")?.let { deOpenLibrary(it) }.orEmpty()
        return (it + ol).distinctBy { it.grande }
    }

    /** Baja una imagen (como mucho 8 MB). Bloqueante (red). */
    fun descargar(url: String): ByteArray? = runCatching {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 15000; c.readTimeout = 20000; c.setRequestProperty("User-Agent", "Marfil/1")
        if (c.responseCode !in 200..299) return null
        c.inputStream.use { i -> val b = i.readBytes(); b.takeIf { it.size in 1000..8_000_000 } }
    }.getOrNull()

    /** ¿Empiezan estos bytes como una imagen de verdad (JPEG, PNG, GIF o WebP)? Una página de error con código 200 no vale. */
    fun esImagen(b: ByteArray): Boolean {
        if (b.size < 12) return false
        fun u(i: Int) = b[i].toInt() and 0xFF
        return (u(0) == 0xFF && u(1) == 0xD8) || (u(0) == 0x89 && u(1) == 0x50 && u(2) == 0x4E && u(3) == 0x47) ||
            (u(0) == 'G'.code && u(1) == 'I'.code && u(2) == 'F'.code) || (u(0) == 'R'.code && u(1) == 'I'.code && u(2) == 'F'.code && u(3) == 'F'.code && u(8) == 'W'.code && u(9) == 'E'.code)
    }

    /** Guarda una imagen como portada de un libro (reducida) con un nombre nuevo y borra las anteriores. Devuelve el libro con su portada, o null si la imagen no vale. */
    fun poner(ctx: Context, l: Libro, bytes: ByteArray): Libro? {
        if (!esImagen(bytes)) return null
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, o)
        if (o.outWidth <= 0 || o.outHeight <= 0) return null
        var s = 1; while (o.outWidth / s > 1000 || o.outHeight / s > 1500) s *= 2
        val b = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = s }) ?: return null
        val dir = Metadatos.dirPortadas(ctx)
        val f = File(dir, "${l.id}-${System.currentTimeMillis()}.jpg")
        f.outputStream().use { b.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        dir.listFiles { x -> x.name.startsWith(l.id) && x != f }?.forEach { it.delete() }
        NubeLibros.portadaCambiada(ctx, l)
        return (Biblioteca.por(l.id) ?: l).copy(portada = f.absolutePath).also { Biblioteca.poner(it) }
    }

    /** Quita la portada elegida (el libro vuelve a enseñar la suya o, si no tiene, la tela). */
    fun quitar(ctx: Context, l: Libro): Libro {
        val dir = Metadatos.dirPortadas(ctx)
        dir.listFiles { x -> x.name.startsWith(l.id) }?.forEach { it.delete() }
        return (Biblioteca.por(l.id) ?: l).copy(portada = "").also { Biblioteca.poner(it) }
    }

    /** Pone la portada que lleva el propio archivo del libro (si la lleva). */
    fun delLibro(ctx: Context, l: Libro): Libro? {
        val f = runCatching { File(android.net.Uri.parse(l.uri).path ?: return null) }.getOrNull()?.takeIf { it.exists() } ?: return null
        val bytes = Metadatos.leer(f, l.archivo.ifBlank { f.name }).portada ?: return null
        return poner(ctx, l, bytes)
    }

    /** Las portadas con una dirección de internet (las que trajo Atrio) se guardan en el móvil para que no dependan de la red ni de que esa web siga ahí. */
    fun cachear(ctx: Context, l: Libro): Libro? {
        if (!l.portada.startsWith("http")) return null
        val bytes = descargar(l.portada) ?: return null
        return poner(ctx, l, bytes)
    }
}
