package com.gonzalo.marfil

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.RadialGradient
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import androidx.core.provider.FontRequest
import androidx.core.provider.FontsContractCompat
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.coroutines.resume
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import android.widget.RemoteViews
import androidx.compose.ui.graphics.toArgb
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/*
 * Widgets de la pantalla de inicio de Marfil: los de Atrio (Widgets.kt) con la misma estética de revista — papel y tinta,
 * titulares grandes en Fraunces, portadas recortadas, esquinas redondas y el reborde fino de tinta — pero con los libros.
 * Cada widget se dibuja entero (así la letra y las portadas quedan como en la app) y encima lleva zonas transparentes para
 * tocar. Los colores son los del tema que tengas puesto: los de Marfil o los de Atrio (invertidos o no).
 */

/** Los widgets que hay. */
enum class WTipo { LEYENDO, ESTANTE, PORTADA, SIGUIENTE, SEMANA, CITA, MARCA, MARCA_PAPEL }

abstract class WBase(private val tipo: WTipo) : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) = Widgets.pintar(ctx, tipo, ids, goAsync())
    override fun onAppWidgetOptionsChanged(ctx: Context, mgr: AppWidgetManager, id: Int, o: Bundle) =
        Widgets.pintar(ctx, tipo, intArrayOf(id), goAsync())
    override fun onDeleted(ctx: Context, ids: IntArray) = Widgets.olvidar(ctx, ids)
    override fun onReceive(ctx: Context, i: Intent) {
        if (i.action == Widgets.ACCION) {
            val id = i.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, 0)
            Widgets.accion(ctx, id, i.getStringExtra("op").orEmpty())
            Widgets.pintar(ctx, tipo, intArrayOf(id), goAsync())
        } else super.onReceive(ctx, i)
    }
}

class WLeyendo : WBase(WTipo.LEYENDO)
class WEstante : WBase(WTipo.ESTANTE)
class WPortada : WBase(WTipo.PORTADA)
class WSiguiente : WBase(WTipo.SIGUIENTE)
class WSemana : WBase(WTipo.SEMANA)
class WCita : WBase(WTipo.CITA)
class WMarca : WBase(WTipo.MARCA)
class WMarcaPapel : WBase(WTipo.MARCA_PAPEL)

/** Una zona que se puede tocar (en dp, desde la esquina de arriba a la izquierda); con [celdas], una rejilla. */
class Zona(
    val x: Float, val y: Float, val w: Float, val h: Float, val toque: PendingIntent?, val desc: String = "",
    val celdas: List<List<Pair<PendingIntent?, String>>>? = null
) {
    fun vista(ctx: Context, ancho: Float, alto: Float, d: Float): RemoteViews {
        fun px(v: Float) = (max(0f, v) * d).roundToInt()
        val (l, t, r, b) = listOf(px(x), px(y), px(ancho - x - w), px(alto - y - h))
        val rejilla = celdas
        if (rejilla == null) {
            val v = RemoteViews(ctx.packageName, R.layout.w_zona)
            v.setViewPadding(R.id.w_hueco, l, t, r, b)
            toque?.let { v.setOnClickPendingIntent(R.id.w_toque, it) }
            if (desc.isNotEmpty()) v.setContentDescription(R.id.w_toque, desc)
            return v
        }
        val v = RemoteViews(ctx.packageName, R.layout.w_rejilla)
        v.setViewPadding(R.id.w_hueco, l, t, r, b)
        rejilla.forEach { fila ->
            val f = RemoteViews(ctx.packageName, R.layout.w_fila)
            fila.forEach { (pi, ds) ->
                val c = RemoteViews(ctx.packageName, R.layout.w_celda)
                pi?.let { c.setOnClickPendingIntent(R.id.w_celda, it) }
                if (ds.isNotEmpty()) c.setContentDescription(R.id.w_celda, ds)
                f.addView(R.id.w_fila, c)
            }
            v.addView(R.id.w_filas, f)
        }
        return v
    }
}

/** Un widget listo para dibujar: sus zonas y cómo se pinta (en claro y en oscuro). */
class Pagina(val zonas: List<Zona>, val dibujo: Hoja.() -> Unit) {
    /** Sin papel ni esquinas: solo lo dibujado, sobre el fondo de pantalla. */
    var sinFondo = false
    fun transparente() = apply { sinFondo = true }
    fun bitmap(ctx: Context, w: Float, h: Float, noche: Boolean, s: Float, propio: Int? = null, dentro: Boolean = false, acentoPropio: Int? = null): Bitmap = Hoja(ctx, w, h, noche, s, propio, dentro, acentoPropio).apply { dibujo(); if (!sinFondo) terminar() }.bm
}

private val ES = Locale("es", "ES")

/** El margen de dentro de un widget en la app (más holgado). */
fun padDe(h: Float) = if (h < 90f) 17f else 20f

/** El lienzo de un widget: se dibuja en dp, con los colores del tema y las letras de la revista. */
class Hoja(val ctx: Context, val w: Float, val h: Float, val noche: Boolean, s: Float, val propio: Int? = null, val dentro: Boolean = false, val acentoPropio: Int? = null) {
    val bm: Bitmap = Bitmap.createBitmap(max(1, (w * s).roundToInt()), max(1, (h * s).roundToInt()), Bitmap.Config.ARGB_8888)
    val c = Canvas(bm).apply { scale(bm.width / w, bm.height / h) }

    // Los colores del tema que tengas (los de Marfil o los de Atrio, invertidos o no), en claro o en oscuro
    private val tonos = Skin.tonosPara(noche)
    // Con un color propio (el que elegiste para este widget dentro de la app): ese es el papel y la tinta, el gris y las líneas se sacan de los dos
    private fun mezcla(a: Int, b: Int, t: Float): Int {
        fun m(x: Int, y: Int) = (x + (y - x) * t).roundToInt().coerceIn(0, 255)
        return android.graphics.Color.rgb(m(android.graphics.Color.red(a), android.graphics.Color.red(b)), m(android.graphics.Color.green(a), android.graphics.Color.green(b)), m(android.graphics.Color.blue(a), android.graphics.Color.blue(b)))
    }
    private fun lum(c: Int) = (0.299f * android.graphics.Color.red(c) + 0.587f * android.graphics.Color.green(c) + 0.114f * android.graphics.Color.blue(c)) / 255f
    private val base: Int? = propio?.let { 0xFF000000.toInt() or it }
    val papel = base ?: tonos.cPaper.toArgb()
    val tinta = base?.let { if (lum(it) > 0.5f) 0xFF15120F.toInt() else 0xFFF6F1E7.toInt() } ?: tonos.cInk.toArgb()
    val crudo = base?.let { mezcla(it, tinta, 0.08f) } ?: tonos.cCrudo.toArgb()
    val gris = base?.let { mezcla(tinta, it, 0.4f) } ?: tonos.cMuted.toArgb()
    val linea = base?.let { mezcla(tinta, it, 0.78f) } ?: tonos.cLine.toArgb()
    val arena = base?.let { mezcla(it, tinta, 0.1f) } ?: tonos.cSand.toArgb()
    val acento = acentoPropio?.let { 0xFF000000.toInt() or it } ?: tonos.cGeneral().toArgb().let { a -> if (base != null && kotlin.math.abs(lum(a) - lum(base)) < 0.22f) tinta else a }
    /** El acento sobre una foto oscura. */
    val acentoFoto = Skin.tonosPara(true).cGeneral().toArgb()
    val radio = 22f
    /** El color del reborde (por defecto, el del texto; sobre una foto a sangre, blanco). */
    var reborde: Int? = null

    // Las letras del tema (Ajustes → Estilo): las que vienen con la app, Vitrina o las de Google Fonts; ver Widgets.cargarLetras
    private val letras = Widgets.letrasActuales(ctx)
    val fTitular: Typeface get() = letras.titular
    val fCursiva: Typeface get() = letras.cursiva
    val fNegrita: Typeface get() = letras.negrita
    val fTexto: Typeface get() = letras.texto

    /** Recorta las esquinas (suaves, con antialias) y traza el reborde: una línea fina del color del texto, metida hacia dentro. */
    fun terminar() {
        // Dentro de la app el reborde fino va más metido: más aire entre el borde y el contenido
        val m = if (dentro) 9f else 6f
        c.drawRoundRect(RectF(m, m, w - m, h - m), radio - m, radio - m, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = 1.1f; color = reborde ?: tinta })
        val p = Path().apply { addRoundRect(RectF(0f, 0f, w, h), radio, radio, Path.Direction.CW); fillType = Path.FillType.INVERSE_WINDING }
        c.drawPath(p, Paint(Paint.ANTI_ALIAS_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR) })
    }

    fun fondo(color: Int = papel) = c.drawColor(color)
    /** Un lavado suave del acento elegido desde la esquina de arriba a la derecha: menos papel vacío. */
    fun lavado(@Suppress("UNUSED_PARAMETER") color: Int = 0) {
        // Solo con un acento que hayas elegido tú: nunca un color inventado por juego
        if (!dentro || acentoPropio == null) return
        val base = acento and 0xFFFFFF
        val a = if (noche) 0x4A else 0x38
        val p = Paint().apply { shader = RadialGradient(w, 0f, max(w, h) * 0.95f, intArrayOf((a shl 24) or base, base), null, Shader.TileMode.CLAMP) }
        c.drawRect(0f, 0f, w, h, p)
    }

    /** Un botón de verdad: píldora de acento con el texto en versalitas. [derecha]: [x] es el borde derecho. */
    fun boton(texto: String, x: Float, top: Float, alto: Float = 22f, derecha: Boolean = false, color: Int = acento): RectF {
        val t = texto.uppercase(ES)
        val p = tp(fNegrita, if (alto < 20f) 8.5f else 9f, if (lum(color) > 0.55f) 0xFF14151A.toInt() else 0xFFFFFFFF.toInt(), 0.16f)
        val ancho = p.measureText(t) + 2 * (alto * 0.55f)
        val x0 = if (derecha) x - ancho else x
        val r = RectF(x0, top, x0 + ancho, top + alto)
        c.drawRoundRect(r, alto / 2, alto / 2, lleno(color))
        val fm = p.fontMetrics
        c.drawText(t, r.left + alto * 0.55f, r.centerY() - (fm.ascent + fm.descent) / 2, p)
        return r
    }

    fun lleno(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }

    fun tp(f: Typeface, size: Float, color: Int, track: Float = 0f) =
        TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.LINEAR_TEXT_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            typeface = f; textSize = size; this.color = color; letterSpacing = track; textLocale = ES
        }

    fun capa(t: CharSequence, p: TextPaint, ancho: Float, lineas: Int = Int.MAX_VALUE, inter: Float = 1f, al: Layout.Alignment = Layout.Alignment.ALIGN_NORMAL): StaticLayout =
        StaticLayout.Builder.obtain(t, 0, t.length, p, max(1, ancho.toInt())).setAlignment(al).setMaxLines(lineas)
            .setEllipsize(TextUtils.TruncateAt.END).setLineSpacing(0f, inter).setIncludePad(false).build()

    /** Pinta un texto ya compuesto; devuelve su alto. */
    fun pon(l: Layout, x: Float, y: Float): Float { c.save(); c.translate(x, y); l.draw(c); c.restore(); return l.height.toFloat() }

    /** Un titular tan grande como quepa (sin partir palabras) en [lineas] y [alto]. */
    fun titular(
        t: String, ancho: Float, alto: Float, maxS: Float, minS: Float, lineas: Int, color: Int = tinta,
        f: Typeface = fTitular, inter: Float = 0.95f, al: Layout.Alignment = Layout.Alignment.ALIGN_NORMAL
    ): StaticLayout {
        var s = maxS
        while (true) {
            val p = tp(f, s, color, -0.01f)
            val palabra = t.split(' ').maxOfOrNull { p.measureText(it) } ?: 0f
            val l = capa(t, p, ancho, Int.MAX_VALUE, inter, al)
            if (l.lineCount <= lineas && l.height <= alto && palabra <= ancho) return l
            if (s <= minS) return capa(t, p, ancho, max(1, min(lineas, (alto / (s * 1.15f)).toInt())), inter, al)
            s = max(minS, s - max(0.5f, s * 0.06f))
        }
    }

    /** Antetítulo: mayúsculas pequeñas, negras y espaciadas. Devuelve el alto. */
    fun ante(t: String, x: Float, y: Float, ancho: Float, color: Int = tinta, size: Float = 9f, al: Layout.Alignment = Layout.Alignment.ALIGN_NORMAL): Float =
        pon(capa(t.uppercase(ES), tp(fNegrita, size, color, 0.16f), ancho, 1, 1f, al), x, y)

    fun anchoAnte(t: String, size: Float = 9f) = tp(fNegrita, size, tinta, 0.16f).measureText(t.uppercase(ES))

    /** Cabecera de sección: el cuadradito de acento y el antetítulo. */
    fun seccion(t: String, x: Float, y: Float, ancho: Float, color: Int = tinta, cuadro: Int = acento) {
        c.drawRect(x, y + 1.6f, x + 7.4f, y + 9f, lleno(cuadro))
        ante(t, x + 12f, y, ancho - 12f, color)
    }

    /** Una portada recortada (centrada) con esquinas redondas; sin imagen, una portada de letra. */
    fun portada(img: Bitmap?, r: RectF, nombre: String, rad: Float = 9f, filo: Boolean = true, autor: String = "") {
        if (img == null || img.width <= 0) { tapa(r, nombre, rad, autor); return }
        val s = max(r.width() / img.width, r.height() / img.height)
        val m = Matrix().apply { setScale(s, s); postTranslate(r.left + (r.width() - img.width * s) / 2, r.top + (r.height() - img.height * s) / 2) }
        val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply { shader = BitmapShader(img, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply { setLocalMatrix(m) } }
        c.drawRoundRect(r, rad, rad, p)
        if (filo) c.drawRoundRect(r, rad, rad, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 0.6f; color = 0x26000000 })
    }

    /** La tapa de un libro sin portada: tela de color con marco fino; en pequeño, la inicial; en grande, el título y el autor (como en la app). */
    fun tapa(r: RectF, nombre: String, rad: Float = 9f, autor: String = "") {
        val tela = telaDe(nombre).toArgb()
        c.drawRoundRect(r, rad, rad, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(0f, r.top, 0f, r.bottom, mezcla(tela, 0xFFFFFFFF.toInt(), 0.10f), mezcla(tela, 0xFF000000.toInt(), 0.32f), Shader.TileMode.CLAMP)
        })
        val papel = 0xFFF3EDE0.toInt()
        val m = r.width() * 0.07f
        c.drawRect(RectF(r.left + m, r.top + m, r.right - m, r.bottom - m), Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 0.6f; color = (papel and 0xFFFFFF) or 0x52000000 })
        if (r.width() >= 70f && r.height() >= 100f) {
            val ancho = r.width() * 0.7f
            val ax = r.left + r.width() * 0.15f
            val pie = if (autor.isNotBlank()) capa(autor.uppercase(ES), tp(fNegrita, r.width() * 0.052f, papel and 0xFFFFFF or 0xC7000000.toInt(), 0.08f), ancho, 2, 1f, Layout.Alignment.ALIGN_CENTER) else null
            val reserva = (pie?.height ?: 0) + if (pie != null) 8f else 0f
            val t = titular(nombre, ancho, r.height() * 0.72f - reserva, r.width() * 0.13f, 7f, 5, papel, al = Layout.Alignment.ALIGN_CENTER)
            val yT = r.top + (r.height() - reserva - t.height) / 2
            pon(t, ax, yT)
            if (pie != null) {
                c.drawRect(r.centerX() - r.width() * 0.06f, r.bottom - r.height() * 0.1f - pie.height - 5f, r.centerX() + r.width() * 0.06f, r.bottom - r.height() * 0.1f - pie.height - 4.4f, lleno(papel and 0xFFFFFF or 0x8C000000.toInt()))
                pon(pie, ax, r.bottom - r.height() * 0.1f - pie.height)
            }
        } else {
            val ini = nombre.trim().firstOrNull()?.uppercase() ?: "·"
            val big = capa(ini, tp(fTitular, r.height() * 0.40f, papel and 0xFFFFFF or 0xE0000000.toInt()), r.width(), 1, 1f, Layout.Alignment.ALIGN_CENTER)
            pon(big, r.left, r.top + (r.height() - big.height) / 2)
        }
    }


    /** Degradado vertical (para leer encima de una foto). */
    fun velo(top: Float, bottom: Float, de: Int, a: Int, medio: Int? = null) {
        val cs = if (medio != null) intArrayOf(de, medio, a) else intArrayOf(de, a)
        val p = Paint().apply { shader = LinearGradient(0f, top, 0f, bottom, cs, null, Shader.TileMode.CLAMP) }
        c.drawRect(0f, top, w, bottom, p)
    }

    /** «2/5 ›»: hay más, y tocando aquí se pasa al siguiente. */
    fun paginas(i: Int, n: Int, xDer: Float, y: Float, color: Int = gris) {
        if (n < 2) return
        ante("${i + 1}/$n  ›", xDer - 80f, y, 80f, color, 9f, Layout.Alignment.ALIGN_OPPOSITE)
    }
}

/** Un subrayado con su libro (para el widget de la cita). */
class CitaW(val libro: Libro, val texto: String, val nota: String, val value: String)

/** Lo que hay guardado en la app, como lo ven los widgets. */
class WDatos(val ctx: Context) {
    val todos: List<Libro> = Biblioteca.propios()
    private fun porReciente(l: List<Libro>) = l.sortedWith(compareByDescending<Libro> { it.ultima }.thenByDescending { it.anadido })
    /** Lo que estás leyendo, lo último abierto primero. */
    val leyendo: List<Libro> = porReciente(todos.filter { it.estado == Estado.LEYENDO })
    val pendientes: List<Libro> = todos.filter { it.estado == Estado.PENDIENTE }.sortedByDescending { it.anadido }
    /** Lo último que has abierto (y, si aún no has abierto nada, lo último que has añadido). */
    val recientes: List<Libro> = porReciente(todos.filter { it.ultima > 0 }.ifEmpty { todos })
    /** La mesilla: lo que lees a medias, menos lo último que abriste. La estantería: lo pendiente. */
    val mesilla: List<Libro> = leyendo.drop(1)
    val estanteria: List<Libro> = pendientes
    /** Tus subrayados de un tamaño que se lee bien en un widget. */
    val citas: List<CitaW> by lazy {
        todos.flatMap { l -> notasDe(EstadoLectura.estado(ctx, l.id)).filter { it.texto.length in 20..420 }.map { CitaW(l, it.texto, it.nota, it.value) } }
    }

    /** «37 % · 3 h 20 min», «Pendiente», «Leído»… */
    fun estado(l: Libro): String = when (l.estado) {
        Estado.LEYENDO -> listOfNotNull(porcentaje(l.fraccion), Paginas.texto(l).ifBlank { null }).joinToString(" · ")
        Estado.LEIDO -> "Leído"
        Estado.PENDIENTE -> "Pendiente"
        Estado.ABANDONADO -> "Abandonado"
    }

    /** El autor o, si no se sabe, el formato. */
    fun origen(l: Libro): String = l.autor.ifBlank { l.formato.uppercase().ifBlank { "Libro" } }
}

object Widgets {
    const val ACCION = "com.gonzalo.marfil.WIDGET"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    @Volatile private var pendiente: Job? = null
    private val fuentes = HashMap<Int, Typeface>()

    fun clase(t: WTipo): Class<out AppWidgetProvider> = when (t) {
        WTipo.LEYENDO -> WLeyendo::class.java
        WTipo.ESTANTE -> WEstante::class.java
        WTipo.PORTADA -> WPortada::class.java
        WTipo.SIGUIENTE -> WSiguiente::class.java
        WTipo.SEMANA -> WSemana::class.java
        WTipo.CITA -> WCita::class.java
        WTipo.MARCA -> WMarca::class.java
        WTipo.MARCA_PAPEL -> WMarcaPapel::class.java
    }

    @Synchronized fun fuente(ctx: Context, id: Int): Typeface =
        fuentes.getOrPut(id) { runCatching { ctx.resources.getFont(id) }.getOrDefault(Typeface.SERIF) }

    // ------------------------------------------------------------------ letras

    /** Las cuatro letras de un widget: titulares, cursiva, negrita (antetítulos) y texto. */
    class Letras(val titular: Typeface, val cursiva: Typeface, val negrita: Typeface, val texto: Typeface)

    @Volatile private var letras: Letras? = null
    private val bajadas = HashMap<String, Typeface>()

    private fun porDefecto(ctx: Context) = Letras(fuente(ctx, R.font.display_semibold), fuente(ctx, R.font.display_italic), fuente(ctx, R.font.body_bold), fuente(ctx, R.font.body_medium))

    /** Las letras ya preparadas (las del tema) o, si aún no se han preparado, las que vienen con la app. */
    fun letrasActuales(ctx: Context): Letras = letras ?: porDefecto(ctx)

    /**
     * Prepara las letras del tema que tengas (Skin.display para titulares, Skin.body para el resto). Las de Google Fonts se piden
     * a los servicios de Google (si ya las ha bajado la app, salen al momento); si no llegan, se usa la letra de la app.
     */
    suspend fun cargarLetras(ctx: Context) {
        val d = porDefecto(ctx)
        val dis = Skin.display; val cue = Skin.body
        val titular = tipografia(ctx, dis, 600, false, DISPLAY_FONTS)
        val cursiva = if (dis == "Atrio") null else tipografia(ctx, dis, 500, true, DISPLAY_FONTS) ?: tipografia(ctx, dis, 400, false, DISPLAY_FONTS)
        letras = Letras(
            titular = titular ?: d.titular,
            cursiva = cursiva ?: if (dis == "Atrio" || titular == null) d.cursiva else titular,
            // Las etiquetas (SEGUIR →, las horas, los antetítulos) llevan siempre la negrita de la app, como en Atrio: la del tema, bajada de Google Fonts, salía más fina
            negrita = d.negrita,
            texto = tipografia(ctx, cue, 500, false, BODY_FONTS) ?: d.texto
        )
    }

    /** Una letra del tema con el grueso más cercano al pedido; null = la de la app (o no se ha podido conseguir). */
    private suspend fun tipografia(ctx: Context, nombre: String, peso: Int, cursiva: Boolean, lista: List<FontChoice>): Typeface? {
        if (nombre.isBlank() || nombre == "Atrio") return null
        if (nombre == "Vitrina") return vitrina(ctx, if (peso >= 600) 700 else 500)
        val pesos = lista.firstOrNull { it.name == nombre }?.weights?.takeIf { it.isNotEmpty() } ?: intArrayOf(400, 700)
        val w = pesos.minByOrNull { kotlin.math.abs(it - peso) } ?: 400
        val clave = "$nombre|$w|$cursiva"
        synchronized(bajadas) { bajadas[clave]?.let { return it } }
        val t = bajar(ctx, nombre, w, cursiva) ?: return null
        synchronized(bajadas) { bajadas[clave] = t }
        return t
    }

    /** Bricolage Grotesque (la letra de Vitrina) es una letra variable: se pide su grueso y su anchura. */
    private fun vitrina(ctx: Context, peso: Int): Typeface? = runCatching {
        val clave = "vitrina|$peso"
        synchronized(bajadas) { bajadas[clave]?.let { return it } }
        val f = File(ctx.cacheDir, "bricolage.ttf")
        if (!f.exists()) ctx.resources.openRawResource(R.font.bricolage).use { i -> f.outputStream().use { o -> i.copyTo(o) } }
        Typeface.Builder(f).setFontVariationSettings("'wght' $peso, 'wdth' ${if (peso >= 700) 92 else 100}").build()
            .also { synchronized(bajadas) { bajadas[clave] = it } }
    }.getOrNull()

    /** Pide una letra de Google Fonts a los servicios de Google (como hace la app para sus pantallas). */
    private suspend fun bajar(ctx: Context, nombre: String, peso: Int, cursiva: Boolean): Typeface? = withTimeoutOrNull(5_000) {
        suspendCancellableCoroutine { k ->
            val th = HandlerThread("letras-widget").also { it.start() }
            try {
                val pet = FontRequest("com.google.android.gms.fonts", "com.google.android.gms", "name=$nombre&weight=$peso" + if (cursiva) "&italic=1" else "", R.array.com_google_android_gms_fonts_certs)
                FontsContractCompat.requestFont(ctx, pet, object : FontsContractCompat.FontRequestCallback() {
                    override fun onTypefaceRetrieved(typeface: Typeface) { if (k.isActive) k.resume(typeface); th.quitSafely() }
                    override fun onTypefaceRequestFailed(reason: Int) { if (k.isActive) k.resume(null); th.quitSafely() }
                }, Handler(th.looper))
            } catch (e: Exception) { th.quitSafely(); if (k.isActive) k.resume(null) }
            k.invokeOnCancellation { th.quitSafely() }
        }
    }

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("marfil_widgets", Context.MODE_PRIVATE)
    private fun estado(ctx: Context, id: Int, k: String, def: Int) = prefs(ctx).getInt("${id}_$k", def)

    /** Lo que se toca dentro del widget: el siguiente, otra propuesta. */
    fun accion(ctx: Context, id: Int, op: String) {
        val p = prefs(ctx)
        if (op == "sig") p.edit().putInt("${id}_i", p.getInt("${id}_i", 0) + 1).apply()
    }

    fun olvidar(ctx: Context, ids: IntArray) {
        val ed = prefs(ctx).edit()
        ids.forEach { id -> ed.remove("${id}_i"); ed.remove("${id}_fijo") }
        ed.apply()
    }

    /** El libro que elegiste para ese widget al ponerlo (o null: el que toque). */
    fun fijoDe(ctx: Context, id: Int): Libro? = prefs(ctx).getString("${id}_fijo", null)?.let { Biblioteca.por(it) }
    fun ponFijo(ctx: Context, id: Int, libroId: String?) { prefs(ctx).edit().apply { if (libroId == null) remove("${id}_fijo") else putString("${id}_fijo", libroId) }.apply() }

    /** Lee lo guardado (el tema, la biblioteca y los colores de Atrio): un widget puede pintarse sin que la app esté abierta. */
    fun preparar(ctx: Context) {
        Skin.cargar(ctx); Biblioteca.cargar(ctx)
        if (TemaAtrio.tonos == null) TemaAtrio.cargarGuardado(ctx)
    }

    /** Repinta esos widgets (en segundo plano: las portadas pueden venir de internet). */
    fun pintar(ctx: Context, tipo: WTipo, ids: IntArray, res: BroadcastReceiver.PendingResult?) {
        val app = ctx.applicationContext
        scope.launch {
            try {
                withTimeoutOrNull(9_000) {
                    preparar(app)
                    runCatching { TemaAtrio.refrescar(app) }
                    ids.forEach { id -> try { pintarUno(app, tipo, id) } catch (e: CancellationException) { throw e } catch (e: Exception) { } }
                }
            } finally { res?.finish() }
        }
    }

    /** Algo cambió en la app (un juego, el tema, los colores de Atrio): se repintan los widgets que haya, sin prisa. */
    fun pedir(ctx: Context) {
        val app = ctx.applicationContext
        val mgr = runCatching { AppWidgetManager.getInstance(app) }.getOrNull() ?: return
        val hay = WTipo.entries.mapNotNull { t ->
            runCatching { mgr.getAppWidgetIds(ComponentName(app, clase(t))) }.getOrNull()?.takeIf { it.isNotEmpty() }?.let { t to it }
        }
        if (hay.isEmpty()) return
        pendiente?.cancel()
        pendiente = scope.launch {
            delay(1_200)
            hay.forEach { (t, ids) -> ids.forEach { id -> try { pintarUno(app, t, id) } catch (e: CancellationException) { throw e } catch (e: Exception) { } } }
        }
    }

    suspend fun pintarUno(ctx: Context, tipo: WTipo, id: Int) {
        val mgr = AppWidgetManager.getInstance(ctx)
        val (w, h) = tamano(ctx, mgr.getAppWidgetOptions(id), tipo)
        mgr.updateAppWidget(id, vistas(ctx, tipo, id, w, h))
    }

    /** Claro, oscuro, o null = como el sistema (se mandan los dos y el sistema elige). Con los colores de Atrio, que no dependen de eso, uno solo. */
    fun noche(): Boolean? = if (Skin.coloresFijos) false else when (Modo.eleccion) { 1 -> false; 2 -> true; else -> null }

    /** El tamaño del widget en dp (en vertical: el ancho mínimo y el alto máximo, como hace Android). */
    fun tamano(ctx: Context, o: Bundle?, tipo: WTipo): Pair<Float, Float> {
        val vertical = ctx.resources.configuration.orientation != Configuration.ORIENTATION_LANDSCAPE
        val w = o?.getInt(if (vertical) AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH else AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH) ?: 0
        val h = o?.getInt(if (vertical) AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT else AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT) ?: 0
        val (dw, dh) = porDefecto(tipo)
        return (if (w > 40) w.toFloat() else dw) to (if (h > 40) h.toFloat() else dh)
    }

    fun porDefecto(t: WTipo): Pair<Float, Float> = when (t) {
        WTipo.PORTADA -> 330f to 250f
        WTipo.MARCA -> 330f to 80f
        WTipo.CITA -> 330f to 200f
        WTipo.MARCA_PAPEL -> 330f to 165f
        else -> 330f to 165f
    }

    /** Píxeles por dp del dibujo: los de la pantalla, sin pasarse de memoria (Android limita lo que pesa un widget). */
    fun escala(ctx: Context, w: Float, h: Float, copias: Int): Float {
        val d = ctx.resources.displayMetrics.density
        val tope = if (copias == 2) 1_100_000f else 1_800_000f
        return min(d, sqrt(tope / (w * h)))
    }

    suspend fun vistas(ctx: Context, tipo: WTipo, id: Int, w: Float, h: Float): RemoteViews {
        cargarLetras(ctx)
        val pag = componer(ctx, tipo, id, w, h)
        val rv = RemoteViews(ctx.packageName, R.layout.w_marco)
        when (val n = noche()) {
            null -> {
                val s = escala(ctx, w, h, 2)
                rv.setImageViewBitmap(R.id.w_dia, pag.bitmap(ctx, w, h, false, s))
                rv.setImageViewBitmap(R.id.w_noche, pag.bitmap(ctx, w, h, true, s))
            }
            else -> rv.setImageViewBitmap(R.id.w_img, pag.bitmap(ctx, w, h, n, escala(ctx, w, h, 1)))
        }
        val d = ctx.resources.displayMetrics.density
        pag.zonas.forEach { rv.addView(R.id.w_zonas, it.vista(ctx, w, h, d)) }
        return rv
    }

    suspend fun componer(ctx: Context, tipo: WTipo, id: Int, w: Float, h: Float, dentro: Boolean = false): Pagina {
        val d = WDatos(ctx)
        return when (tipo) {
            WTipo.LEYENDO -> leyendo(d, id, w, h, fijo = fijoDe(ctx, id))
            WTipo.ESTANTE -> estante(d, w, h)
            WTipo.PORTADA -> enPortada(d, id, w, h, fijo = fijoDe(ctx, id))
            WTipo.SIGUIENTE -> siguiente(d, id, w, h)
            WTipo.SEMANA -> semana(d, w, h)
            WTipo.CITA -> cita(d, id, w, h)
            WTipo.MARCA -> marca(d, w, h)
            WTipo.MARCA_PAPEL -> marcaPapel(d, w, h)
        }
    }

    // ------------------------------------------------------------------ toques

    private const val PI = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

    private fun op(ctx: Context, tipo: WTipo, id: Int, op: String): PendingIntent {
        val i = Intent(ctx, clase(tipo)).setAction(ACCION).setData(Uri.parse("marfil://w/$id/$op"))
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id).putExtra("op", op)
        return PendingIntent.getBroadcast(ctx, id, i, PI)
    }

    /** Abre Marfil en su portada. */
    fun abrirApp(ctx: Context): PendingIntent {
        val i = Intent(ctx, MainActivity::class.java).setAction(Intent.ACTION_MAIN).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(ctx, 1, i, PI)
    }

    /** Abre la ficha de un libro. */
    fun abrirFicha(ctx: Context, id: String): PendingIntent {
        val i = Intent(ctx, MainActivity::class.java).setAction(Intent.ACTION_VIEW).setData(Uri.parse("marfil://ficha/$id"))
            .putExtra("ficha", id).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(ctx, 2, i, PI)
    }

    /** Abre el lector en ese libro (o, si su archivo no está aquí, su ficha, que sabe bajarlo). [goto]: un punto del libro (el de un subrayado). */
    fun leer(ctx: Context, l: Libro, goto: String? = null): PendingIntent {
        if (l.enOtroDispositivo) return abrirFicha(ctx, l.id)
        val i = Intent(ctx, LectorActivity::class.java).putExtra("id", l.id).putExtra("goto", goto)
            .setData(Uri.parse("marfil://leer/${l.id}/${goto.orEmpty().hashCode()}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return PendingIntent.getActivity(ctx, 3, i, PI)
    }


    // ------------------------------------------------------------------ imágenes

    private fun px(ctx: Context, dp: Float) = (dp * ctx.resources.displayMetrics.density).roundToInt().coerceIn(32, 1400)

    private fun decodificar(path: String, lado: Int): Bitmap? {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, o)
        if (o.outWidth <= 0) return null
        var s = 1
        while (min(o.outWidth, o.outHeight) / (s * 2) >= lado) s *= 2
        return BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = s })
    }

    /** La portada de un libro a unos [lado] píxeles: su archivo en el móvil o, si es de internet, bajada; si no hay, null (tapa de tela). */
    suspend fun imagen(ctx: Context, l: Libro, lado: Int): Bitmap? = withContext(Dispatchers.IO) {
        try {
            if (l.portada.isBlank()) return@withContext null
            if (l.portada.startsWith("http")) {
                withTimeoutOrNull(4_500) {
                    val r = ctx.imageLoader.execute(ImageRequest.Builder(ctx).data(l.portada).allowHardware(false).size(lado).build())
                    ((r as? SuccessResult)?.drawable as? BitmapDrawable)?.bitmap
                }
            } else decodificar(l.portada, lado)
        } catch (e: CancellationException) { throw e } catch (e: Exception) { null }
    }

    // ------------------------------------------------------------------ Sigue leyendo

    /** Un widget sin nada que enseñar todavía: lo dice con la misma letra y lleva a donde empezar. */
    private fun vacio(ctx: Context, sec: String, titulo: String, texto: String, w: Float, h: Float): Pagina =
        Pagina(listOf(Zona(0f, 0f, w, h, abrirApp(ctx), titulo))) {
            fondo()
            val pad = 16f
            seccion(sec, pad, pad, w - 2 * pad)
            val l = titular(titulo, w - 2 * pad, h * 0.42f, 28f, 13f, 3)
            pon(l, pad, pad + 22f)
            val t = capa(texto, tp(fTexto, 11.5f, gris), w - 2 * pad, max(1, ((h - pad * 2 - 30f - l.height) / 15f).toInt()), 1.05f)
            pon(t, pad, pad + 28f + l.height)
        }

    private fun barra(h: Hoja, x: Float, y: Float, ancho: Float, f: Float) {
        h.c.drawRect(x, y, x + ancho, y + 2.2f, h.lleno(h.linea))
        h.c.drawRect(x, y, x + ancho * f.coerceIn(0f, 1f), y + 2.2f, h.lleno(h.acento))
    }

    private suspend fun leyendo(d: WDatos, id: Int, w: Float, h: Float, fijo: Libro? = null): Pagina {
        val ctx = d.ctx
        val lista = if (fijo != null) listOf(fijo) else d.leyendo.ifEmpty { d.recientes.filter { it.estado != Estado.LEIDO }.take(1) }
        if (lista.isEmpty()) return vacio(ctx, "Sigue leyendo", "Nada que leer todavía", "Añade un libro a Marfil y lo tendrás aquí, a un toque de seguir.", w, h)
        val i = Math.floorMod(estado(ctx, id, "i", 0), lista.size)
        val e = lista[i]
        val compacto = w < 200f && h < 200f
        val img = imagen(ctx, e, px(ctx, if (compacto) max(w, h) else h * 0.9f))
        val accion = if (e.enOtroDispositivo) "Bajar y leer  →" else "Seguir  →"
        val horas = if (e.msLeidos >= 60_000) tiempoDe(e.msLeidos) else ""
        val zonas = mutableListOf(
            Zona(0f, 0f, w, h, abrirFicha(ctx, e.id), "Abrir ${e.titulo}"),
            Zona(0f, h - 44f, w * 0.5f, 44f, leer(ctx, e), "Seguir leyendo ${e.titulo}")
        )
        if (lista.size > 1) zonas += Zona(w - 72f, 0f, 72f, 42f, op(ctx, WTipo.LEYENDO, id, "sig"), "Otro libro")
        return Pagina(zonas) {
            val pad = if (h < 90f) 12f else 14f
            when {
                compacto -> {
                    portada(img, RectF(0f, 0f, w, h), e.titulo, 0f, false, e.autor); reborde = 0xCCFFFFFF.toInt()
                    velo(h * 0.2f, h, 0x00000000, 0xF0000000.toInt(), 0x88000000.toInt())
                    velo(0f, 52f, 0xB0000000.toInt(), 0x00000000)
                    seccion("Sigue leyendo", pad, pad, w - 2 * pad, 0xFFFFFFFF.toInt(), acentoFoto)
                    val bottom = h - pad - 4f
                    ante(accion, pad, bottom - 8f, w * 0.6f, acentoFoto, 8.5f)
                    ante(porcentaje(e.fraccion), w - pad - 60f, bottom - 8f, 60f, 0xCCFFFFFF.toInt(), 8.5f, Layout.Alignment.ALIGN_OPPOSITE)
                    val l = titular(e.titulo, w - 2 * pad, h * 0.42f, 24f, 12f, 3, 0xFFFFFFFF.toInt())
                    pon(l, pad, bottom - 20f - l.height)
                }
                h > w * 1.15f -> {
                    fondo()
                    seccion("Sigue leyendo", pad, pad, w - 2 * pad - (if (lista.size > 1) 50f else 0f), tinta, acento)
                    val top = pad + 20f
                    val ch = h * 0.5f - top
                    val cw = min(ch * 0.66f, w * 0.55f)
                    portada(img, RectF(pad, top, pad + cw, top + ch), e.titulo, autor = e.autor)
                    val x0 = pad + cw + 10f
                    pon(capa(porcentaje(e.fraccion), tp(fTitular, min(30f, (w - x0 - pad) / 3f), acento), w - x0 - pad + 4f, 1), x0, top + ch - 34f)
                    var y = top + ch + 12f
                    val l = titular(e.titulo, w - 2 * pad, h - y - pad - 26f - 16f, 30f, 13f, 4)
                    y += pon(l, pad, y) + 3f
                    pon(capa(d.origen(e), tp(fCursiva, 12f, gris), w - 2 * pad, 1), pad, y)
                    barra(this, pad, h - pad - 30f, w - 2 * pad, e.fraccion)
                    ante(accion, pad, h - pad - 11f, w - 2 * pad, acento)
                }
                else -> {
                    fondo()
                    val ch = h - 2 * pad
                    val cw = min(ch * 0.66f, w * 0.36f)
                    portada(img, RectF(pad, pad, pad + cw, pad + ch), e.titulo, autor = e.autor)
                    val x0 = pad + cw + 14f
                    val rw = w - x0 - pad
                    seccion("Sigue leyendo", x0, pad + 1f, rw, tinta, acento)
                    val altoAnte = capa("L", tp(fNegrita, 9f, tinta, 0.16f), 40f, 1).height.toFloat()
                    val bajo = h < 110f
                    val pieY = if (bajo) h - pad - altoAnte else h - pad - 17f
                    ante(accion, x0, pieY, rw, acento)
                    if (horas.isNotEmpty()) ante(horas, x0, pieY, rw, gris, 9f, Layout.Alignment.ALIGN_OPPOSITE)
                    if (!bajo) c.drawRect(x0, h - pad - 25f, w - pad, h - pad - 24.4f, lleno(linea))
                    val arriba = pad + 1f + altoAnte
                    val aire = if (bajo) 7f else 10f
                    val hueco = pieY - arriba - 2 * aire - if (bajo) 0f else 4f
                    // Lo que llevas, en grande y con su barra (si hay sitio)
                    val conPct = h >= 130f
                    val pct = if (conPct) capa(porcentaje(e.fraccion), tp(fTitular, 24f, acento), rw, 1) else null
                    val altoPct = pct?.let { it.height + 8f } ?: 0f
                    val autor = if (h >= 130f) capa(d.origen(e), tp(fCursiva, 12.5f, gris), rw, 1) else null
                    val altoAutor = autor?.let { it.height + 4f } ?: 0f
                    val l = titular(e.titulo, rw, hueco - altoAutor - altoPct, (h * 0.19f).coerceIn(28f, 44f), if (bajo) 12f else 13f, if (bajo) 1 else 3)
                    val y = arriba + (pieY - arriba - l.height - altoAutor - altoPct) / 2
                    pon(l, x0, y)
                    autor?.let { pon(it, x0, y + l.height + 4f) }
                    pct?.let { pon(it, x0, y + l.height + altoAutor + 6f); barra(this, x0 + it.getLineWidth(0) + 10f, y + l.height + altoAutor + 6f + it.height / 2f, rw - it.getLineWidth(0) - 10f, e.fraccion) }
                }
            }
        }
    }

    // ------------------------------------------------------------------ En la mesilla (estantería)

    private suspend fun estante(d: WDatos, w: Float, h: Float): Pagina {
        val ctx = d.ctx
        val pad = 14f
        val enMesilla = d.mesilla.isNotEmpty()
        val todas = if (enMesilla) d.mesilla else d.estanteria
        val titulo = if (enMesilla) "En la mesilla" else "En la estantería"
        if (todas.isEmpty()) return vacio(ctx, "En la mesilla", "Nada en la mesilla", "Los libros que leas a medias aparecerán aquí en fila; si no hay, los pendientes.", w, h)
        val bajo = h < 150f
        val n = ((w - 2 * pad) / (if (bajo) 140f else 78f)).toInt().coerceIn(1, 6).coerceAtMost(todas.size)
        val items = todas.take(n)
        val slot = (w - 2 * pad) / n
        val top = pad + 22f
        val textos = if (bajo) 0f else 44f
        val ch = h - top - pad - textos
        val imgs = items.map { imagen(ctx, it, px(ctx, ch)) }
        val zonas = listOf(
            Zona(0f, 0f, w, top, abrirApp(ctx), "Abrir Marfil"),
            Zona(pad, top, w - 2 * pad, h - top, null, "", listOf(items.map { leer(ctx, it) to "Leer ${it.titulo}" }))
        )
        return Pagina(zonas) {
            fondo()
            seccion(titulo, pad, pad, w - 2 * pad, tinta, acento)
            pon(capa("Marfil", tp(fTitular, 13f, tinta), 80f, 1, 1f, Layout.Alignment.ALIGN_OPPOSITE), w - pad - 80f, pad - 3f)
            c.drawRect(pad, pad + 14.5f, w - pad, pad + 15.2f, lleno(linea))
            items.forEachIndexed { k, e ->
                val gap = 5f
                val x = pad + slot * k + gap
                val cw = min(slot - 2 * gap, ch * 0.68f)
                portada(imgs[k], RectF(x, top, x + cw, top + ch), e.titulo, 8f, autor = e.autor)
                if (bajo) {
                    val tx = x + cw + 8f
                    val tw = slot - 2 * gap - cw - 8f
                    val est = capa(d.estado(e).uppercase(ES), tp(fNegrita, 7.5f, acento, 0.16f), tw, 2)
                    val l = titular(e.titulo, tw, ch - est.height - 4f, 15f, 10f, 3)
                    pon(l, tx, top)
                    pon(est, tx, top + ch - est.height)
                } else {
                    val tw = slot - 2 * gap
                    val l = capa(e.titulo, tp(fTitular, 12f, tinta, -0.01f), tw, 2, 0.95f)
                    pon(l, x, top + ch + 5f)
                    ante(d.estado(e), x, top + ch + 7f + l.height, tw, acento, 7.5f)
                }
            }
        }
    }

    // ------------------------------------------------------------------ En portada

    private suspend fun enPortada(d: WDatos, id: Int, w: Float, h: Float, fijo: Libro? = null): Pagina {
        val ctx = d.ctx
        val items = if (fijo != null) listOf(fijo) else d.recientes
        if (items.isEmpty()) return vacio(ctx, "En portada", "Tu revista empieza aquí", "Añade o lee algo y saldrá en la portada.", w, h)
        val i = Math.floorMod(estado(ctx, id, "i", 0), items.size)
        val e = items[i]
        val img = imagen(ctx, e, px(ctx, max(w, h)))
        val zonas = mutableListOf(
            Zona(0f, 0f, w, h, abrirFicha(ctx, e.id), e.titulo),
            Zona(w - 110f, h - 40f, 110f, 40f, leer(ctx, e), "Leer ${e.titulo}")
        )
        if (items.size > 1) zonas += Zona(w - 80f, 0f, 80f, 52f, op(ctx, WTipo.PORTADA, id, "sig"), "Siguiente")
        val fecha = LocalDate.now().let { "${it.dayOfMonth} ${it.month.getDisplayName(TextStyle.SHORT, ES).trimEnd('.')} ${it.year}" }
        return Pagina(zonas) {
            val pad = 16f
            val blanco = 0xFFFFFFFF.toInt()
            if (img != null) {
                portada(img, RectF(0f, 0f, w, h), e.titulo, 0f, false); reborde = 0xCCFFFFFF.toInt()
                velo(0f, min(96f, h * 0.42f), 0xC8000000.toInt(), 0x00000000, 0x70000000)
                velo(h * 0.18f, h, 0x00000000, 0xF2000000.toInt(), 0x99000000.toInt())
            } else { fondo(0xFF15120F.toInt()); reborde = 0xCCFFFFFF.toInt() }
            val mast = capa("Marfil", tp(fTitular, if (h < 180f) 22f else 28f, blanco, -0.02f), w * 0.6f, 1)
            pon(mast, pad, pad - 4f)
            val fw = w - 2 * pad - mast.getLineWidth(0) - 8f
            ante(if (fw < anchoAnte(fecha, 8.5f)) fecha.substringBeforeLast(' ') else fecha, w - pad - fw, pad, fw, 0xDDFFFFFF.toInt(), 8.5f, Layout.Alignment.ALIGN_OPPOSITE)
            c.drawRect(pad, pad + mast.height + 1f, w - pad, pad + mast.height + 1.8f, lleno(0x66FFFFFF))
            val meta = d.origen(e).takeIf { h >= 170f }
            var y = h - pad
            if (meta != null) { val ml = capa(meta, tp(fCursiva, 12.5f, 0xCCFFFFFF.toInt()), w - 2 * pad - 90f, 1); y -= ml.height; pon(ml, pad, y); y -= 4f }
            val l = titular(e.titulo, w - 2 * pad, h * 0.46f, if (w > 280f) 40f else 30f, 14f, 3, blanco, inter = 0.92f)
            y -= l.height
            pon(l, pad, y)
            val est = d.estado(e)
            val ew = min(w - 2 * pad, anchoAnte(est, 8.5f) + 12f)
            c.drawRoundRect(pad, y - 19f, pad + ew, y - 4f, 4f, 4f, lleno(acento))
            ante(est, pad + 6f, y - 16.5f, ew - 10f, 0xFFFFFFFF.toInt(), 8.5f)
            ante(if (e.enOtroDispositivo) "Abrir  →" else "Leer  →", w - pad - 90f, h - pad - 10f, 90f, acentoFoto, 9f, Layout.Alignment.ALIGN_OPPOSITE)
        }
    }

    // ------------------------------------------------------------------ ¿Y ahora qué?

    private suspend fun siguiente(d: WDatos, id: Int, w: Float, h: Float): Pagina {
        val ctx = d.ctx
        val lista = d.pendientes.sortedBy { (it.id.hashCode().toLong() * 2654435761L) and 0xFFFFFF }
        if (lista.isEmpty()) return vacio(ctx, "¿Y ahora qué?", "Nada pendiente", "Añade libros a Marfil y aquí te propondré cuál empezar.", w, h)
        // Una propuesta distinta cada día (y la que quieras con «Otro»)
        val i = Math.floorMod(LocalDate.now().toEpochDay() + estado(ctx, id, "i", 0), lista.size.toLong()).toInt()
        val e = lista[i]
        val compacto = w < 200f && h < 200f
        val img = imagen(ctx, e, px(ctx, if (compacto) max(w, h) else h))
        val pad = 14f
        val zonas = mutableListOf(
            Zona(0f, 0f, w, h, abrirFicha(ctx, e.id), e.titulo),
            Zona(0f, h - 40f, w * 0.55f, 40f, leer(ctx, e), "Empezar ${e.titulo}")
        )
        if (lista.size > 1) zonas += Zona(w - 86f, h - 40f, 86f, 40f, op(ctx, WTipo.SIGUIENTE, id, "sig"), "Otra propuesta")
        return Pagina(zonas) {
            if (compacto) {
                portada(img, RectF(0f, 0f, w, h), e.titulo, 0f, false, e.autor); reborde = 0xCCFFFFFF.toInt()
                velo(h * 0.15f, h, 0x00000000, 0xF0000000.toInt(), 0x99000000.toInt())
                velo(0f, 46f, 0xA0000000.toInt(), 0x00000000)
                seccion("¿Y ahora qué?", pad, pad, w - 2 * pad, 0xFFFFFFFF.toInt(), acentoFoto)
                val l = titular(e.titulo, w - 2 * pad, h * 0.42f, 24f, 12f, 3, 0xFFFFFFFF.toInt())
                pon(l, pad, h - pad - 22f - l.height)
                ante("Empezar  →", pad, h - pad - 11f, w * 0.6f, acentoFoto, 8.5f)
                if (lista.size > 1) ante("Otro  ›", w - pad - 60f, h - pad - 11f, 60f, 0xCCFFFFFF.toInt(), 8.5f, Layout.Alignment.ALIGN_OPPOSITE)
                return@Pagina
            }
            fondo()
            val ch = h - 2 * pad
            val cw = min(ch * 0.68f, w * 0.34f)
            portada(img, RectF(pad, pad, pad + cw, pad + ch), e.titulo, autor = e.autor)
            val x0 = pad + cw + 14f
            val rw = w - x0 - pad
            seccion("¿Y ahora qué?", x0, pad + 1f, rw, tinta, acento)
            val y0 = pad + 20f
            val l = titular(e.titulo, rw, h - y0 - pad - 30f - 16f, (h * 0.19f).coerceIn(30f, 46f), 13f, 3)
            pon(l, x0, y0)
            pon(capa(d.origen(e), tp(fCursiva, 12f, gris), rw, 1), x0, y0 + l.height + 4f)
            c.drawRect(x0, h - pad - 22f, w - pad, h - pad - 21.4f, lleno(linea))
            ante("Empezar  →", x0, h - pad - 12f, rw * 0.7f, acento)
            if (lista.size > 1) ante("Otro  ›", w - pad - 70f, h - pad - 12f, 70f, gris, 9f, Layout.Alignment.ALIGN_OPPOSITE)
        }
    }

    // ------------------------------------------------------------------ Lectura de la semana

    private fun semana(d: WDatos, w: Float, h: Float): Pagina {
        val ctx = d.ctx
        val dias = diasLeidos(ctx, d.todos)
        val meta = Ajustes.metaLectura(ctx)
        val hoy = LocalDate.now()
        val hoyMin = (dias[hoy.toString()] ?: 0L) / 60_000L
        val racha = rachaDeLectura(dias)
        val sem = (6 downTo 0).map { hoy.minusDays(it.toLong()) }.map { dia -> dia to (dias[dia.toString()] ?: 0L) / 60_000L }
        return Pagina(listOf(Zona(0f, 0f, w, h, abrirApp(ctx), "Abrir Marfil"))) {
            fondo()
            val pad = if (h < 90f) 12f else 16f
            seccion("Lectura", pad, pad, w - 2 * pad, tinta, acento)
            pon(capa("Marfil", tp(fTitular, 13f, tinta), 80f, 1, 1f, Layout.Alignment.ALIGN_OPPOSITE), w - pad - 80f, pad - 3f)
            c.drawRect(pad, pad + 14.5f, w - pad, pad + 15.2f, lleno(linea))
            val top = pad + 26f
            val ancho = w - 2 * pad
            val izqW = if (w >= 230f) ancho * 0.38f else 0f
            if (izqW > 0f) {
                val txt = if (hoyMin >= 60) readTime(hoyMin) else "$hoyMin min"
                val big = titular(txt, izqW - 6f, 52f, 34f, 14f, 1, acento)
                pon(big, pad, top)
                ante("hoy", pad, top + big.height + 2f, izqW, gris, 8.5f)
                var y = top + big.height + 20f
                val retoAnual = Ajustes.metaAnual(ctx)
                val extra = listOfNotNull(
                    if (meta > 0) (if (hoyMin >= meta) "Objetivo cumplido" else "Objetivo: $meta min") else null,
                    if (retoAnual > 0) "Reto: ${terminadosEn(d.todos, anoDe(System.currentTimeMillis()))}/$retoAnual" else null,
                    if (racha >= 2) "$racha días seguidos" else null
                )
                for (t in extra) { if (y + 14f < h - pad) y += pon(capa(t, tp(fCursiva, 12f, gris), izqW, 1), pad, y) + 2f }
            }
            val x0 = pad + izqW + if (izqW > 0f) 14f else 0f
            val bw = w - pad - x0
            val alto = h - top - pad - 14f
            val tope = max(sem.maxOf { it.second }, max(meta, 1).toLong()).toFloat()
            val slot = bw / 7f
            sem.forEachIndexed { k, (dia, m) ->
                val bx = x0 + slot * k + slot * 0.19f
                val bwid = slot * 0.62f
                val hh = if (m >= 1) max(3f, alto * m / tope) else 1f
                c.drawRect(bx, top + alto - hh, bx + bwid, top + alto, lleno(if (m >= 1) (if (meta > 0 && m >= meta) acento else tinta) else linea))
                ante(dia.dayOfWeek.getDisplayName(TextStyle.NARROW, ES), bx, top + alto + 3f, bwid, if (dia == hoy) acento else gris, 8.5f, Layout.Alignment.ALIGN_CENTER)
            }
            if (meta > 0) { val yy = top + alto - alto * meta / tope; c.drawRect(x0, yy, w - pad, yy + 0.7f, lleno(acento and 0xFFFFFF or 0x99000000.toInt())) }
        }
    }

    // ------------------------------------------------------------------ Una cita

    private fun cita(d: WDatos, id: Int, w: Float, h: Float): Pagina {
        val ctx = d.ctx
        val lista = d.citas.sortedBy { (it.texto.hashCode().toLong() * 2654435761L) and 0xFFFFFF }
        if (lista.isEmpty()) return vacio(ctx, "Una cita", "Aún no hay citas", "Lo que subrayes en tus libros saldrá aquí, una cita cada día.", w, h)
        val i = Math.floorMod(LocalDate.now().toEpochDay() + estado(ctx, id, "i", 0), lista.size.toLong()).toInt()
        val e = lista[i]
        val zonas = mutableListOf(Zona(0f, 0f, w, h, leer(ctx, e.libro, e.value), "Abrir ${e.libro.titulo} en esta cita"))
        if (lista.size > 1) zonas += Zona(w - 80f, 0f, 80f, 44f, op(ctx, WTipo.CITA, id, "sig"), "Otra cita")
        return Pagina(zonas) {
            fondo()
            val pad = 16f
            seccion("Una cita", pad, pad, w - 2 * pad - 60f, tinta, acento)
            if (lista.size > 1) paginas(i % lista.size, lista.size, w - pad, pad)
            val pie = capa(e.libro.titulo, tp(fTitular, 13f, tinta), w - 2 * pad, 1)
            val autor = if (e.libro.autor.isNotBlank()) capa(e.libro.autor, tp(fCursiva, 12f, gris), w - 2 * pad, 1) else null
            val altoPie = pie.height + (autor?.height ?: 0) + 10f
            val top = pad + 22f
            val q = titular("«${e.texto}»", w - 2 * pad, h - top - pad - altoPie, 24f, 10.5f, 12, tinta, f = fCursiva, inter = 1.04f)
            pon(q, pad, top)
            c.drawRect(pad, h - pad - altoPie - 3f, pad + 26f, h - pad - altoPie - 2.2f, lleno(acento))
            pon(pie, pad, h - pad - altoPie + 2f)
            autor?.let { pon(it, pad, h - pad - autor.height) }
        }
    }

    // ------------------------------------------------------------------ La marca

    private fun marca(d: WDatos, w: Float, h: Float): Pagina {
        val ctx = d.ctx
        val hoy = LocalDate.now()
        val fecha = "${hoy.dayOfWeek.getDisplayName(TextStyle.FULL, ES)} ${hoy.dayOfMonth} de ${hoy.month.getDisplayName(TextStyle.FULL, ES)}"
        return Pagina(listOf(Zona(0f, 0f, w, h, abrirApp(ctx), "Abrir Marfil"))) {
            val color = if (noche) 0xFFF6F1E7.toInt() else tinta
            val sombra = if (noche) 0x99000000.toInt() else 0x55FFFFFF
            val pie = if (h >= 84f) 16f else 0f
            val p = tp(fTitular, 10f, color, -0.03f)
            val ancho = p.measureText("MARFIL")
            val cuadro = 0.16f
            var s = min((w - 8f) / (ancho / 10f * (1f + cuadro)), (h - pie - 4f) / 0.95f)
            s = s.coerceAtLeast(10f)
            val tpa = tp(fTitular, s, color, -0.03f).apply { setShadowLayer(s * 0.06f, 0f, s * 0.02f, sombra) }
            val fm = tpa.fontMetrics
            val tw = tpa.measureText("MARFIL")
            val total = tw + s * cuadro
            val x = (w - total) / 2
            val capH = s * 0.72f
            val base = (h - pie) / 2 + capH / 2
            c.drawText("MARFIL", x, base, tpa)
            val q = s * 0.12f
            c.drawRect(x + tw + s * 0.03f, base - q, x + tw + s * 0.03f + q, base, lleno(acento).apply { setShadowLayer(s * 0.04f, 0f, s * 0.015f, sombra) })
            if (pie > 0f) {
                val l = capa(("Tus libros · $fecha").uppercase(ES), tp(fNegrita, 8.5f, color, 0.2f).apply { setShadowLayer(2f, 0f, 0.6f, sombra) }, w - 8f, 1, 1f, Layout.Alignment.ALIGN_CENTER)
                pon(l, 4f, min(h - l.height - 2f, base + fm.descent * 0.4f + 2f))
            }
        }.transparente()
    }

    /** MARFIL en papel: la cabecera de la portada (el nombre, el número del día y la fecha), con su papel de fondo. */
    private fun marcaPapel(d: WDatos, w: Float, h: Float): Pagina {
        val ctx = d.ctx
        val hoy = LocalDate.now()
        val fecha = "${hoy.dayOfWeek.getDisplayName(TextStyle.FULL, ES)} ${hoy.dayOfMonth} de ${hoy.month.getDisplayName(TextStyle.FULL, ES)}"
        return Pagina(listOf(Zona(0f, 0f, w, h, abrirApp(ctx), "Abrir Marfil"))) {
            fondo()
            val pad = if (h < 90f) 12f else 16f
            val conLinea = h >= 70f
            val conEntradilla = h >= 140f
            val bajo = (if (conLinea) 22f else 0f) + (if (conEntradilla) 24f else 0f)
            val alto = h - 2 * pad - bajo
            val p = tp(fTitular, 10f, tinta, -0.03f)
            val s = min((w - 2 * pad) / (p.measureText("MARFIL") / 10f), alto / 0.78f).coerceAtLeast(10f)
            val tpa = tp(fTitular, s, tinta, -0.03f)
            val capH = s * 0.72f
            val bloque = capH + s * 0.08f + 4f + bajo
            val base = max(pad, (h - bloque) / 2) + capH
            val tw = tpa.measureText("MARFIL")
            c.drawText("MARFIL", (w - tw) / 2, base, tpa)
            var y = base + s * 0.08f + 4f
            if (conLinea) {
                c.drawRect(pad, y, w - pad, y + 1.6f, lleno(tinta))
                y += 5f
                val iz = "Nº ${hoy.dayOfYear}"
                ante(iz, pad, y, w / 2, tinta, 8.5f)
                val sitio = w - 2 * pad - anchoAnte(iz, 8.5f) - 12f
                val corta = "${hoy.dayOfWeek.getDisplayName(TextStyle.SHORT, ES).trimEnd('.')} ${hoy.dayOfMonth} ${hoy.month.getDisplayName(TextStyle.SHORT, ES).trimEnd('.')}"
                ante(if (anchoAnte(fecha, 8.5f) > sitio) corta else fecha, pad + anchoAnte(iz, 8.5f) + 12f, y, w - 2 * pad - anchoAnte(iz, 8.5f) - 12f, tinta, 8.5f, Layout.Alignment.ALIGN_OPPOSITE)
                y += 14f
                c.drawRect(pad, y, w - pad, y + 0.6f, lleno(tinta))
                y += 6f
            }
            if (conEntradilla) pon(capa("Tus libros, al día", tp(fCursiva, 13f, gris), w - 2 * pad, 1), pad, y)
        }
    }
}
