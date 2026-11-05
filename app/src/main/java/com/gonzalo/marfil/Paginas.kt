package com.gonzalo.marfil

import android.content.Context
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.zip.ZipFile

/**
 * Las páginas de cada libro. Un EPUB no tiene páginas de verdad, así que el número sale de la mejor fuente que haya (de más a menos fiable):
 *  - «manual»: lo que escribes tú en Editar;
 *  - «atrio» / «pdf»: el de Atrio (lo que apuntaste) o el del propio PDF;
 *  - «openlibrary»: las de la edición del libro en Open Library (la mediana de sus ediciones);
 *  - «estimado»: contando el texto del EPUB (unas 1 800 letras por página).
 * Una fuente mejor sustituye a una peor, nunca al revés; lo que escribes tú no lo toca nadie.
 */
object Paginas {
    fun rango(origen: String) = when (origen) { "manual" -> 5; "atrio", "pdf" -> 4; "openlibrary" -> 3; "estimado" -> 1; else -> 0 }

    /** Pone [n] páginas con ese [origen] si es mejor que lo que ya había. Devuelve el libro (cambiado o no). */
    fun conPaginas(l: Libro, n: Int, origen: String): Libro =
        if (n > 0 && (rango(origen) > rango(l.paginasOrigen) || (l.paginas == 0))) l.copy(paginas = n, paginasOrigen = origen) else l

    fun contarPdf(f: File): Int = runCatching {
        ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY).use { fd -> PdfRenderer(fd).use { it.pageCount } }
    }.getOrDefault(0)

    private val ETIQUETAS = Regex("<[^>]*>")
    private val ENTIDADES = Regex("&[a-zA-Z#0-9]+;")
    private const val LETRAS_POR_PAGINA = 1800

    /** Las páginas de un EPUB a ojo: las letras de todos sus capítulos entre lo que cabe en una página. 0 si es muy corto o no se puede leer. */
    fun estimarEpub(f: File): Int = runCatching {
        var letras = 0L
        ZipFile(f).use { zip ->
            for (e in zip.entries()) {
                val n = e.name.lowercase()
                if (e.isDirectory || !(n.endsWith(".xhtml") || n.endsWith(".html") || n.endsWith(".htm"))) continue
                if (n.contains("nav") || n.contains("toc") || n.contains("cover") || n.contains("copyright")) continue
                val txt = zip.getInputStream(e).use { it.readBytes().decodeToString() }
                val cuerpo = txt.substringAfter("<body", txt)
                letras += ENTIDADES.replace(ETIQUETAS.replace(cuerpo, " "), " ").replace(Regex("\\s+"), " ").trim().length
            }
        }
        if (letras < 3_000) 0 else (letras / LETRAS_POR_PAGINA).toInt().coerceAtLeast(1)
    }.getOrDefault(0)

    /** Las páginas que se pueden sacar del archivo: (número, origen) o null. */
    fun deArchivo(f: File, nombre: String): Pair<Int, String>? = when (nombre.substringAfterLast('.', "").lowercase()) {
        "pdf" -> contarPdf(f).takeIf { it > 0 }?.let { it to "pdf" }
        "epub" -> estimarEpub(f).takeIf { it > 0 }?.let { it to "estimado" }
        else -> null
    }

    /** Open Library: {"docs":[{"title","author_name":[…],"number_of_pages_median":608}]} → las páginas del que se llama igual y es del mismo autor. */
    fun deOpenLibrary(json: String, titulo: String, autor: String): Int? = runCatching {
        val a = JSONObject(json).optJSONArray("docs") ?: return null
        fun base(x: String) = norm(x.substringBefore('(').substringBefore(':'))
        val t = base(titulo)
        val apellido = norm(autor).split(' ').lastOrNull { it.length > 2 }.orEmpty()
        (0 until a.length()).mapNotNull { a.optJSONObject(it) }.firstNotNullOfOrNull { d ->
            val n = d.optInt("number_of_pages_median", 0)
            if (n !in 30..2500) return@firstNotNullOfOrNull null
            val nt = base(d.optString("title"))
            val autores = d.optJSONArray("author_name")?.let { x -> (0 until x.length()).joinToString(" ") { norm(x.optString(it)) } }.orEmpty()
            val mismoTitulo = nt == t
            val mismoAutor = apellido.isEmpty() || autores.contains(apellido)
            if (mismoTitulo && mismoAutor) n else null
        }
    }.getOrNull()

    /** Busca las páginas del libro en Open Library. Bloqueante (red). */
    fun buscar(titulo: String, autor: String): Int? = runCatching {
        fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
        val url = "https://openlibrary.org/search.json?title=${enc(titulo)}" + (if (autor.isNotBlank()) "&author=${enc(autor)}" else "") + "&limit=10&fields=title,author_name,number_of_pages_median"
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 10000; c.readTimeout = 12000; c.setRequestProperty("User-Agent", "Marfil/1 (lector de libros personal)")
        if (c.responseCode !in 200..299) return null
        deOpenLibrary(c.inputStream.use { it.readBytes().decodeToString() }, titulo, autor)
    }.getOrNull()

    private fun prefs(c: Context) = c.getSharedPreferences("marfil_paginas", Context.MODE_PRIVATE)

    /**
     * Pone al día las páginas de todos los libros: las del archivo (si lo hay) y, para los que no tienen una fuente buena, las de Open Library
     * (se pregunta una vez por semana por libro). Bloqueante (red y disco): se llama en segundo plano.
     */
    fun rellenar(c: Context, conRed: Boolean = true): Int {
        var n = 0
        val p = prefs(c)
        for (l0 in Biblioteca.libros.toList()) {
            var l = Biblioteca.por(l0.id) ?: continue
            if (l.paginas == 0 && !l.enOtroDispositivo) {
                val f = runCatching { File(android.net.Uri.parse(l.uri).path.orEmpty()) }.getOrNull()?.takeIf { it.exists() }
                f?.let { deArchivo(it, l.archivo.ifBlank { it.name }) }?.let { (num, org) -> l = conPaginas(l, num, org) }
            }
            if (conRed && rango(l.paginasOrigen) < rango("openlibrary") && l.titulo.isNotBlank()) {
                val ult = p.getLong("t:${l.clave}", 0L)
                if (System.currentTimeMillis() - ult > 7 * 86_400_000L) {
                    p.edit().putLong("t:${l.clave}", System.currentTimeMillis()).apply()
                    buscar(l.titulo, l.autor)?.let { l = conPaginas(l, it, "openlibrary") }
                }
            }
            if (l != l0) { Biblioteca.poner(l); n++ }
        }
        return n
    }

    fun enSegundoPlano(c: Context) { val app = c.applicationContext; kotlin.concurrent.thread(name = "paginas") { runCatching { rellenar(app) } } }

    /** «118/320» (lo que llevas) o «320 págs» (lo que tiene): para ponerlo en acento a la derecha. Vacío si no se sabe. */
    fun texto(l: Libro): String {
        if (l.paginas <= 0) return ""
        return if (l.estado == Estado.LEYENDO && l.fraccion > 0.001f) "${Formato.cifra(Math.round(l.fraccion * l.paginas).coerceIn(1, l.paginas))}/${Formato.cifra(l.paginas)}" else "${Formato.cifra(l.paginas)} págs"
    }
}
