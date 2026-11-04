package com.gonzalo.marfil

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/*
 * «Escritos»: lo que escribes en Cobalto, leído aquí, con el lector de Marfil. Cobalto publica en tu cuenta una copia de solo lectura de cada
 * capítulo que das por terminado (`/users/<uid>/cobalto/lectura/<obra>/<capítulo>`, ver cobalto/docs/CONTRATO-DATOS.md). Marfil los lee, hace de
 * cada obra un EPUB (los capítulos terminados, en orden, por partes) y lo abre en el lector como un libro más: con su posición, sus subrayados y
 * sus ajustes de lectura. Cuando Cobalto termina otro capítulo, el EPUB se rehace solo.
 * Estos libros son de aquí y no viajan con la biblioteca (ni a tu cuenta ni a kDrive, ni a Azabache): su clave empieza por «escrito:».
 */
/** Un libro que es una obra de Cobalto (no se sube, no va a los estantes ni a Azabache). */
val Libro.esEscrito: Boolean get() = clave.startsWith("escrito:")

object Escritos {
    const val PAQ_COBALTO = "com.gonzalo.cobalto"

    fun claveDe(obra: String) = "escrito:$obra"
    fun idDe(obra: String) = "escrito_$obra"

    class Capitulo(val obra: String, val id: String, val titulo: String, val autor: String, val nombre: String, val numero: Int, val parte: String, val palabras: Int, val texto: String, val u: Long)
    class Obra(val id: String, val titulo: String, val autor: String, val capitulos: List<Capitulo>) {
        val palabras: Int get() = capitulos.sumOf { it.palabras }
        /** La última edición de cualquiera de sus capítulos: si cambia, el EPUB se rehace. */
        val u: Long get() = capitulos.maxOfOrNull { it.u } ?: 0L
    }

    /** Sube cuando llega algo nuevo (las pantallas vuelven a leer). */
    var version by mutableIntStateOf(0)
        private set
    private fun prefs(c: Context) = c.getSharedPreferences("marfil_escritos", Context.MODE_PRIVATE)

    // ---------------------------------------------------------------- lo que publica Cobalto (sin red: se puede probar)

    fun capitulosDe(lectura: JSONObject?): List<Capitulo> {
        lectura ?: return emptyList()
        val out = ArrayList<Capitulo>()
        for (obra in lectura.keys()) {
            val caps = lectura.optJSONObject(obra) ?: continue
            for (c in caps.keys()) {
                val o = caps.optJSONObject(c) ?: continue
                out += Capitulo(obra, c, o.optString("t"), o.optString("a"), o.optString("c"), o.optInt("n"), o.optString("pa"), o.optInt("p"), o.optString("x"), o.optLong("u"))
            }
        }
        return out.sortedWith(compareBy<Capitulo> { norm(it.titulo) }.thenBy { it.numero })
    }

    fun obrasDe(lectura: JSONObject?): List<Obra> = capitulosDe(lectura).groupBy { it.obra }.map { (id, cs) -> Obra(id, cs.first().titulo.ifBlank { "Sin título" }, cs.first().autor, cs.sortedBy { it.numero }) }
        .sortedByDescending { it.u }

    // ---------------------------------------------------------------- el EPUB

    private fun xml(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    /** El Markdown ligero de Cobalto a XHTML: *cursiva*, **negrita**. */
    fun enLinea(t: String): String {
        var x = xml(t)
        x = x.replace(Regex("\\*\\*(.+?)\\*\\*"), "<strong>$1</strong>")
        x = x.replace(Regex("(?<!\\*)\\*(?!\\*)(.+?)(?<!\\*)\\*(?!\\*)"), "<em>$1</em>")
        return x
    }

    /** El cuerpo de un capítulo (`# `, `## `, `> `, `***` = salto de escena, párrafos) a XHTML. */
    fun cuerpo(texto: String): String {
        val sb = StringBuilder()
        var primero = true
        for (linea in texto.replace("\r\n", "\n").split('\n')) {
            val l = linea.trimEnd()
            when {
                l.isBlank() -> {}
                l == "***" || l == "---" || l == "* * *" -> { sb.append("<p class=\"salto\">* * *</p>"); primero = true }
                l.startsWith("## ") -> { sb.append("<h3>${enLinea(l.removePrefix("## "))}</h3>"); primero = true }
                l.startsWith("# ") -> { sb.append("<h2>${enLinea(l.removePrefix("# "))}</h2>"); primero = true }
                l.startsWith("> ") -> sb.append("<blockquote><p>${enLinea(l.removePrefix("> "))}</p></blockquote>")
                else -> { sb.append("<p${if (primero) " class=\"primero\"" else ""}>${enLinea(l)}</p>"); primero = false }
            }
        }
        return sb.toString()
    }

    private fun zip(entradas: List<Triple<String, ByteArray, Boolean>>): ByteArray {
        val bos = ByteArrayOutputStream()
        ZipOutputStream(bos).use { z ->
            for ((nombre, datos, guardado) in entradas) {
                val en = ZipEntry(nombre)
                if (guardado) { en.method = ZipEntry.STORED; en.size = datos.size.toLong(); en.compressedSize = datos.size.toLong(); en.crc = CRC32().apply { update(datos) }.value }
                z.putNextEntry(en); z.write(datos); z.closeEntry()
            }
        }
        return bos.toByteArray()
    }

    /** El EPUB de una obra: la portada con el título, y una página por capítulo (con el título de su parte cuando empieza una). */
    fun epub(o: Obra): ByteArray {
        class Pag(val titulo: String, val cuerpo: String)
        val pags = ArrayList<Pag>()
        pags += Pag(o.titulo, "<div class=\"portada\"><h1>${xml(o.titulo)}</h1>${if (o.autor.isNotBlank()) "<p class=\"autor\">${xml(o.autor)}</p>" else ""}<p class=\"nota\">Lo terminado hasta ahora en Cobalto</p></div>")
        var parte = ""
        for (c in o.capitulos) {
            if (c.parte.isNotBlank() && c.parte != parte) { parte = c.parte; pags += Pag(parte, "<h1 class=\"parte\">${xml(parte)}</h1>") }
            val nombre = c.nombre.ifBlank { "Capítulo ${c.numero}" }
            pags += Pag(nombre, "<h2>${xml(nombre)}</h2>" + cuerpo(c.texto))
        }
        val cab = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><html xmlns=\"http://www.w3.org/1999/xhtml\" xml:lang=\"es\"><head><meta charset=\"utf-8\"/><title>%s</title><link rel=\"stylesheet\" type=\"text/css\" href=\"estilo.css\"/></head><body>%s</body></html>"
        val manifiesto = pags.indices.joinToString("") { "<item id=\"p$it\" href=\"p$it.xhtml\" media-type=\"application/xhtml+xml\"/>" }
        val espina = pags.indices.joinToString("") { "<itemref idref=\"p$it\"/>" }
        val fecha = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US).apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }.format(java.util.Date(if (o.u > 0) o.u else System.currentTimeMillis()))
        val opf = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><package xmlns=\"http://www.idpf.org/2007/opf\" version=\"3.0\" unique-identifier=\"uid\" xml:lang=\"es\"><metadata xmlns:dc=\"http://purl.org/dc/elements/1.1/\">" +
            "<dc:identifier id=\"uid\">cobalto-${xml(o.id)}</dc:identifier><dc:title>${xml(o.titulo)}</dc:title><dc:creator>${xml(o.autor.ifBlank { "Anónimo" })}</dc:creator><dc:language>es</dc:language>" +
            "<meta property=\"dcterms:modified\">$fecha</meta></metadata>" +
            "<manifest><item id=\"nav\" href=\"nav.xhtml\" media-type=\"application/xhtml+xml\" properties=\"nav\"/><item id=\"css\" href=\"estilo.css\" media-type=\"text/css\"/>$manifiesto</manifest><spine>$espina</spine></package>"
        val nav = String.format(cab.replace("<body>", "<body><nav xmlns:epub=\"http://www.idpf.org/2007/ops\" epub:type=\"toc\"><h1>Índice</h1><ol>").replace("%s</body>", "%s</ol></nav></body>"), xml(o.titulo),
            pags.mapIndexed { i, p -> "<li><a href=\"p$i.xhtml\">${xml(p.titulo)}</a></li>" }.joinToString(""))
        val css = "body{font-family:Georgia,serif;line-height:1.5;margin:5%}p{margin:0;text-align:justify;text-indent:1.4em}p.primero,p.autor,p.salto,p.nota{text-indent:0}p.salto{text-align:center;margin:1.2em 0}p.nota{text-align:center;font-style:italic;opacity:.6;margin-top:2em}" +
            "h1,h2,h3{text-align:center;text-indent:0;margin:1.6em 0 1em}.portada{margin-top:30%;text-align:center}blockquote{margin:1em 2em;font-style:italic}"
        return zip(
            listOf(Triple("mimetype", "application/epub+zip".toByteArray(), true),
                Triple("META-INF/container.xml", "<?xml version=\"1.0\"?><container version=\"1.0\" xmlns=\"urn:oasis:names:tc:opendocument:xmlns:container\"><rootfiles><rootfile full-path=\"OEBPS/content.opf\" media-type=\"application/oebps-package+xml\"/></rootfiles></container>".toByteArray(), false),
                Triple("OEBPS/content.opf", opf.toByteArray(), false), Triple("OEBPS/nav.xhtml", nav.toByteArray(), false), Triple("OEBPS/estilo.css", css.toByteArray(), false)) +
                pags.mapIndexed { i, p -> Triple("OEBPS/p$i.xhtml", String.format(cab, xml(p.titulo), p.cuerpo).toByteArray(), false) }
        )
    }

    // ---------------------------------------------------------------- en este móvil

    fun obras(c: Context): List<Obra> = obrasDe(prefs(c).getString("lectura", null)?.let { runCatching { JSONObject(it) }.getOrNull() })
    fun ultimaVez(c: Context) = prefs(c).getLong("ultima", 0L)

    /** Lee lo que Cobalto ha publicado en tu cuenta. Bloqueante (red). Devuelve si ha cambiado algo. */
    fun refrescar(c: Context): Boolean {
        if (!Cuenta.vinculada) return false
        val raw = runCatching { Cuenta.db("GET", "/users/${Cuenta.uid}/cobalto/lectura", null) }.getOrNull() ?: "{}"
        val antes = prefs(c).getString("lectura", "")
        prefs(c).edit().putString("lectura", raw).putLong("ultima", System.currentTimeMillis()).apply()
        val cambio = antes != raw
        if (cambio) { for (o in obras(c)) if (Biblioteca.por(idDe(o.id)) != null) runCatching { preparar(c, o) }; android.os.Handler(android.os.Looper.getMainLooper()).post { version++ } }
        return cambio
    }

    /**
     * Deja la obra lista para leerla: su EPUB en la carpeta de libros (se rehace si Cobalto cambió algo) y su ficha en la biblioteca
     * (con su clave «escrito:», que no viaja). Devuelve el libro.
     */
    fun preparar(c: Context, o: Obra): Libro {
        val id = idDe(o.id)
        val f = File(Metadatos.dirLibros(c), "$id.epub")
        val hecho = prefs(c).getLong("u:${o.id}", -1L)
        if (!f.exists() || hecho != o.u) { f.writeBytes(epub(o)); prefs(c).edit().putLong("u:${o.id}", o.u).apply() }
        val previo = Biblioteca.por(id)
        val nombre = o.titulo.replace(Regex("[\\\\/:*?\"<>|]"), " ").trim().ifBlank { "Obra" } + ".epub"
        val l = (previo ?: Libro(id, o.titulo, o.autor, clave = claveDe(o.id), anadido = System.currentTimeMillis())).copy(titulo = o.titulo, autor = o.autor, uri = android.net.Uri.fromFile(f).toString(), archivo = nombre)
        val conPaginas = Paginas.deArchivo(f, nombre)?.let { (n, origen) -> Paginas.conPaginas(l, n, origen) } ?: l
        if (conPaginas != previo) Biblioteca.poner(conPaginas)
        return Biblioteca.por(id) ?: conPaginas
    }
}

/** «Escritos»: tus obras de Cobalto con capítulos terminados, para leerlas aquí. */
@Composable
fun PantEscritos(alVolver: () -> Unit, alLeer: (Libro) -> Unit) {
    val ctx = LocalContext.current
    val ambito = rememberCoroutineScope()
    val obras = remember(Escritos.version) { Escritos.obras(ctx) }
    var mirando by remember { mutableStateOf(false) }
    var mensaje by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { if (Cuenta.vinculada) { mirando = true; withContext(Dispatchers.IO) { runCatching { Escritos.refrescar(ctx) } }; mirando = false } }
    Column(Modifier.fillMaxSize().background(Paper).verticalScroll(rememberScrollState()).statusBarsPadding().navigationBarsPadding().padding(horizontal = 28.dp)) {
        RevistaTopBar(alVolver, "Marfil") {
            EnlaceRevista(if (mirando) "Mirando…" else "Actualizar", acento = !mirando) {
                if (!mirando) ambito.launch { mirando = true; val r = withContext(Dispatchers.IO) { runCatching { Escritos.refrescar(ctx) } }; mirando = false; mensaje = r.fold({ if (it) "Hay capítulos nuevos." else "Nada nuevo en Cobalto." }, { it.message ?: "Sin conexión." }) }
            }
        }
        Filete(3.dp)
        Antetitulo("Sección · ${obras.size} ${if (obras.size == 1) "obra" else "obras"}", Modifier.padding(top = 12.dp), color = Acento, conCuadro = false)
        FitTitle("Escritos", 52.sp, 56.sp, letterSpacing = (-1.5).sp, modifier = Modifier.padding(top = 2.dp), unaLinea = true)
        Text("Lo que escribes en Cobalto, aquí, con el lector: cada obra con los capítulos que has dado por terminados.", color = Muted, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 16.sp, lineHeight = 22.sp, modifier = Modifier.padding(top = 6.dp, bottom = 12.dp))
        if (mensaje.isNotBlank()) Text(mensaje, color = Acento, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 15.sp, modifier = Modifier.padding(bottom = 8.dp))
        Filete(2.dp)
        if (obras.isEmpty()) Text(
            when { !Cuenta.vinculada -> "Vincula tu cuenta de Atrio (Ajustes) para ver tus obras de Cobalto."; Escritos.ultimaVez(ctx) == 0L -> "Todavía no se ha leído tu cuenta."; else -> "Cobalto aún no ha publicado ningún capítulo terminado. Se publican al marcarlos como terminados allí." },
            color = Muted, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 16.sp, lineHeight = 22.sp, modifier = Modifier.padding(vertical = 14.dp)
        )
        obras.forEach { o ->
            val libro = Biblioteca.por(Escritos.idDe(o.id))
            Column(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                Text(o.titulo, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 26.sp, lineHeight = 30.sp, color = Charcoal)
                Text(listOfNotNull(o.autor.ifBlank { null }, "${o.capitulos.size} ${if (o.capitulos.size == 1) "capítulo terminado" else "capítulos terminados"}", "${o.palabras} palabras",
                    libro?.takeIf { it.fraccion > 0.005f }?.let { "vas por el ${(it.fraccion * 100).toInt()} %" }).joinToString(" · "),
                    color = Muted, fontFamily = Body, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
                Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    EnlaceRevista(if (libro != null && libro.fraccion > 0.005f) "Seguir leyendo →" else "Leer →", acento = true) {
                        ambito.launch { val l = withContext(Dispatchers.IO) { runCatching { Escritos.preparar(ctx, o) }.getOrNull() }; if (l != null) alLeer(l) else mensaje = "No he podido preparar el libro." }
                    }
                    if (Escritos.instaladoCobalto(ctx)) EnlaceRevista("Escribir en Cobalto →") { Escritos.abrirCobalto(ctx) }
                }
            }
            Filete(1.dp, Line)
        }
        Spacer(Modifier.height(30.dp))
    }
}

fun Escritos.instaladoCobalto(c: Context) = runCatching { c.packageManager.getLaunchIntentForPackage(Escritos.PAQ_COBALTO) != null }.getOrDefault(false)
fun Escritos.abrirCobalto(c: Context) { c.packageManager.getLaunchIntentForPackage(Escritos.PAQ_COBALTO)?.let { c.startActivity(it.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) } }
