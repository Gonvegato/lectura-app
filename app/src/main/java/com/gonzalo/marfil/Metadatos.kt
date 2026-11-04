package com.gonzalo.marfil

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.util.zip.ZipFile

/** Lo que se saca del libro al añadirlo: su título, su autor y su portada (bytes de imagen). Todo opcional. */
class Meta(val titulo: String = "", val autor: String = "", val portada: ByteArray? = null)

/** Añadir libros: se copia el archivo a la app (así siempre se puede abrir, subir a tu kDrive y bajar en otro dispositivo) y se leen sus datos. */
object Metadatos {
    /** El nombre que tiene un archivo elegido (con extensión). */
    fun nombreDe(ctx: Context, uri: Uri): String {
        var n = ""
        runCatching { ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { if (it.moveToFirst()) n = it.getString(0).orEmpty() } }
        return n.ifBlank { uri.lastPathSegment?.substringAfterLast('/').orEmpty().ifBlank { "libro" } }
    }

    val FORMATOS = setOf("epub", "pdf", "mobi", "azw3", "azw", "fb2", "cbz", "txt")

    fun dirLibros(ctx: Context) = File(ctx.filesDir, "libros").apply { mkdirs() }
    fun dirPortadas(ctx: Context) = File(ctx.filesDir, "portadas").apply { mkdirs() }

    /**
     * Añade un libro desde [uri]: lo copia a la app, lee sus datos y lo pone en la biblioteca (o completa el que ya estaba «en otro dispositivo»
     * con el mismo título y autor). Devuelve el libro, o null si no se pudo leer el archivo.
     */
    fun anadir(ctx: Context, uri: Uri): Libro? {
        val nombre = nombreDe(ctx, uri)
        if (nombre.substringAfterLast('.', "").lowercase() !in FORMATOS) return null
        val id = Biblioteca.nuevoId()
        val destino = File(dirLibros(ctx), "$id.${nombre.substringAfterLast('.').lowercase()}")
        try { ctx.contentResolver.openInputStream(uri)?.use { i -> destino.outputStream().use { o -> i.copyTo(o) } } ?: return null }
        catch (e: Exception) { destino.delete(); return null }
        return registrar(ctx, destino, nombre, id)
    }

    /**
     * Pone el archivo [uri] como el de un libro que ya está en la biblioteca (el que estaba «en otro dispositivo» o uno que quieras cambiar):
     * se copia a la app y el libro sigue siendo el mismo (misma clave, mismo estado, lo leído). Devuelve el libro, o null si no se pudo leer.
     */
    fun adjuntar(ctx: Context, l: Libro, uri: Uri): Libro? {
        val nombre = nombreDe(ctx, uri)
        val ext = nombre.substringAfterLast('.', "").lowercase()
        if (ext !in FORMATOS) return null
        val destino = File(dirLibros(ctx), "${l.id}.$ext")
        val tmp = File(destino.path + ".tmp")
        try { ctx.contentResolver.openInputStream(uri)?.use { i -> tmp.outputStream().use { o -> i.copyTo(o) } } ?: return null }
        catch (e: Exception) { tmp.delete(); return null }
        // Si había otro archivo de otro formato, se borra
        dirLibros(ctx).listFiles { f -> f.name.startsWith(l.id + ".") && f != destino && !f.name.endsWith(".tmp") }?.forEach { it.delete() }
        destino.delete(); tmp.renameTo(destino)
        val ahora = Biblioteca.por(l.id) ?: l
        var conArchivo = ahora.copy(uri = Uri.fromFile(destino).toString(), archivo = nombre)
        Paginas.deArchivo(destino, nombre)?.let { (n, o) -> conArchivo = Paginas.conPaginas(conArchivo, n, o) }
        Biblioteca.poner(conArchivo)
        // (la portada NO se saca del archivo: si no la has puesto tú, el libro lleva su tapa de color con la letra)
        return conArchivo
    }

    /** Borra el archivo de este dispositivo; el libro se queda en la biblioteca (con lo leído) y se puede volver a bajar o a elegir. */
    fun quitarArchivo(ctx: Context, l: Libro): Libro {
        runCatching { File(Uri.parse(l.uri).path.orEmpty()).takeIf { it.exists() }?.delete() }
        return (Biblioteca.por(l.id) ?: l).copy(uri = "").also { Biblioteca.poner(it) }
    }

    /** Pone en la biblioteca un archivo que ya está en la app (también al bajarlo de tu kDrive). */
    fun registrar(ctx: Context, archivo: File, nombre: String, idNuevo: String = Biblioteca.nuevoId()): Libro {
        val m = leer(archivo, nombre)
        val (tFich, aFich) = tituloYAutorDeArchivo(nombre)
        val titulo = m.titulo.ifBlank { tFich }; val autor = m.autor.ifBlank { aFich }
        val previo = Biblioteca.existente(titulo, autor)
        val id = previo?.id ?: idNuevo
        val ahora = System.currentTimeMillis()
        var libro = (previo ?: Libro(id, titulo, autor, anadido = ahora)).copy(uri = android.net.Uri.fromFile(archivo).toString(), archivo = nombre)
        Paginas.deArchivo(archivo, nombre)?.let { (n, o) -> libro = Paginas.conPaginas(libro, n, o) }
        Biblioteca.poner(libro)
        return libro
    }

    /** Los datos de un libro según su formato. */
    fun leer(f: File, nombre: String): Meta = when (nombre.substringAfterLast('.', "").lowercase()) {
        "epub" -> leerEpub(f)
        "pdf" -> Meta()   // (un PDF no da ni título ni portada: la primera página como portada quedaba fatal)
        else -> Meta()
    }

    // ---- EPUB: container.xml → OPF (título, autor, portada)
    private fun leerEpub(f: File): Meta = runCatching {
        ZipFile(f).use { zip ->
            fun bytes(ruta: String) = zip.getEntry(ruta)?.let { e -> zip.getInputStream(e).use { it.readBytes() } }
            val container = bytes("META-INF/container.xml")?.decodeToString() ?: return@runCatching Meta()
            val opfRuta = Regex("""full-path\s*=\s*["']([^"']+)["']""").find(container)?.groupValues?.get(1) ?: return@runCatching Meta()
            val opf = bytes(opfRuta) ?: return@runCatching Meta()
            val base = opfRuta.substringBeforeLast('/', "")
            var titulo = ""; var autor = ""; var coverId = ""
            val items = HashMap<String, Pair<String, String>>()   // id → (href, propiedades)
            val xp = Xml.newPullParser().apply { setInput(opf.inputStream(), null) }
            var ev = xp.eventType
            while (ev != XmlPullParser.END_DOCUMENT) {
                if (ev == XmlPullParser.START_TAG) when (xp.name.substringAfter(':')) {
                    "title" -> if (titulo.isBlank()) titulo = runCatching { xp.nextText() }.getOrDefault("").trim()
                    "creator" -> if (autor.isBlank()) autor = runCatching { xp.nextText() }.getOrDefault("").trim()
                    "meta" -> if (xp.getAttributeValue(null, "name") == "cover") coverId = xp.getAttributeValue(null, "content").orEmpty()
                    "item" -> items[xp.getAttributeValue(null, "id").orEmpty()] = xp.getAttributeValue(null, "href").orEmpty() to xp.getAttributeValue(null, "properties").orEmpty()
                }
                ev = xp.next()
            }
            val href = items[coverId]?.first?.takeIf { it.isNotBlank() }
                ?: items.values.firstOrNull { it.second.contains("cover-image") }?.first
                ?: items.entries.firstOrNull { it.key.contains("cover", true) && it.value.first.substringAfterLast('.').lowercase() in setOf("jpg", "jpeg", "png", "webp") }?.value?.first
            val ruta = href?.let { java.net.URLDecoder.decode(if (base.isEmpty()) it else "$base/$it", "UTF-8").replace("/./", "/") }
            Meta(titulo, autor, ruta?.let { bytes(it) })
        }
    }.getOrDefault(Meta())
}
