package com.gonzalo.lectura

import android.content.Context
import org.json.JSONObject
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font as GFont
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Formas: cómo dibuja cada estilo las esquinas y los bordes. Son las mismas ideas que las «formas» de Atrio, más simples:
 * en Lectura la portada es un ARCO (la puerta de Atrio, repetida), y según la forma el arco es redondo, biselado o recto.
 */
enum class Esq { Redonda, Cortada, Recta }
data class Forma(val id: String, val esq: Esq, val radio: Float, val borde: Dp, val bordeTinta: Boolean, val sombraDura: Boolean, val arco: Float)

val FORMAS: List<Forma> = listOf(
    Forma("vitrina", Esq.Redonda, 1.3f, 0.dp, false, false, 1f),
    Forma("clasico", Esq.Redonda, 1f, 1.dp, false, false, 0.85f),
    Forma("editorial", Esq.Redonda, 0.3f, 1.dp, false, false, 0.55f),
    Forma("suave", Esq.Redonda, 1.8f, 0.dp, false, false, 1f),
    Forma("minimal", Esq.Redonda, 0.5f, 0.dp, false, false, 0.35f),
    Forma("comic", Esq.Redonda, 0.7f, 2.5.dp, true, true, 0.7f),
    Forma("neon", Esq.Cortada, 0.6f, 1.5.dp, false, false, 0.6f),
    Forma("militar", Esq.Cortada, 0.4f, 2.dp, true, false, 0.5f),
    Forma("pixel", Esq.Recta, 0f, 3.dp, true, true, 0f),
    Forma("terminal", Esq.Recta, 0f, 1.dp, true, false, 0f),
    Forma("brutal", Esq.Recta, 0f, 3.dp, true, true, 0f),
    Forma("revista", Esq.Recta, 0f, 0.dp, false, false, 0f),
)

/** Esquina normal de radio [r] según la forma. */
fun esquina(r: Dp): Shape {
    val f = Skin.forma
    val k = (r.value * f.radio).dp
    return when (f.esq) {
        Esq.Recta -> if (k.value < 0.5f) RectangleShape else RoundedCornerShape(k)
        Esq.Cortada -> if (k.value < 0.5f) RectangleShape else CutCornerShape(k)
        Esq.Redonda -> RoundedCornerShape(k)
    }
}

/** Botones y chips «píldora». */
fun pildora(): Shape = when (Skin.forma.esq) {
    Esq.Redonda -> if (Skin.forma.radio < 0.6f) RoundedCornerShape(10.dp) else RoundedCornerShape(50)
    Esq.Cortada -> CutCornerShape(30)
    Esq.Recta -> RectangleShape
}

/**
 * La portada de Lectura: un arco. Arriba, media circunferencia (o menos, según la forma); abajo, esquinas pequeñas.
 * En las formas rectas es un rectángulo con borde; en las biseladas, el arco es un chaflán.
 */
class ArcoShape(private val f: Forma) : Shape {
    override fun createOutline(size: androidx.compose.ui.geometry.Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val w = size.width; val h = size.height
        val abajo = (minOf(w, h) * 0.07f * f.radio.coerceAtLeast(0.6f)).coerceAtMost(minOf(w, h) / 4f)
        return when (f.esq) {
            Esq.Recta -> Outline.Rectangle(Rect(0f, 0f, w, h))
            Esq.Cortada -> {
                val c = minOf(w * 0.28f * f.arco.coerceAtLeast(0.5f), h / 3f)
                Outline.Generic(Path().apply {
                    moveTo(c, 0f); lineTo(w - c, 0f); lineTo(w, c); lineTo(w, h); lineTo(0f, h); lineTo(0f, c); close()
                })
            }
            Esq.Redonda -> {
                val r = (w / 2f * f.arco).coerceAtMost(h / 2f)
                Outline.Rounded(RoundRect(0f, 0f, w, h, CornerRadius(r), CornerRadius(r), CornerRadius(abajo), CornerRadius(abajo)))
            }
        }
    }
}

/** Mismo arco, más pequeño en el radio de las esquinas de abajo y menos alto: para el cartel del juego (apaisado). */
fun arco(): Shape = ArcoShape(Skin.forma)

/** Estado del modo claro/oscuro: lo pone la raíz de la app según Ajustes y el sistema. */
object Modo {
    var oscuro by mutableStateOf(true)
    /** 0 como el sistema, 1 claro, 2 oscuro. */
    var eleccion by mutableStateOf(0)
}

object Skin {
    /** Los tamaños del texto: por ciento → nombre. */
    val ESCALAS_NOMBRES = mapOf(90 to "Pequeño", 100 to "Normal", 112 to "Grande", 125 to "Muy grande")
    var palette by mutableStateOf(PALETTES.first { it.id == "revista" })
    var display by mutableStateOf("Atrio")
    var body by mutableStateOf("Atrio")
    var forma by mutableStateOf(FORMAS.first { it.id == "revista" })
    /** Id de [ACCENT_COLORS] o "paleta" (el de la paleta). */
    var accentId by mutableStateOf("paleta")
    /** Tamaño del texto en por ciento. */
    var escala by mutableStateOf(100)
    /** Colores invertidos: el fondo con el color del texto y el texto con el del fondo; el acento se mantiene. */
    var invertido by mutableStateOf(true)
    /** Colores de Atrio (los que tienes puestos allí) en lugar de los de la paleta de Lectura. */
    var origenAtrio by mutableStateOf(true)
    /** Colores elegidos a mano, como «A tu manera» de Atrio (0 = automático): por encima de todo lo demás. */
    var fondoPropio by mutableStateOf(0L)
    /** Un fondo de color de [FONDOS] (como en Atrio); "" = papel. */
    var fondoId by mutableStateOf("")
    var tintaPropia by mutableStateOf(0L)
    var acentoPropio by mutableStateOf(0L)

    /** El fondo (las tarjetas, líneas y grises salen de él y del texto), el texto y el acento elegidos a mano, sobre [t]. */
    fun conPropios(t: Tones, fondo: Long = fondoPropio, tintaP: Long = tintaPropia, acentoP: Long = acentoPropio): Tones {
        var r = t
        if (fondo != 0L || tintaP != 0L) {
            val papel = if (fondo != 0L) fondo else r.paper
            val tinta = when {
                tintaP != 0L -> tintaP
                fondo != 0L -> if (Color(papel).luminance() < 0.4f) 0xFFF1EBE0 else 0xFF15120F
                else -> r.ink
            }
            val crudo = lerp(Color(papel), Color(tinta), 0.04f).toArgb().toLong() and 0xFFFFFFFFL
            r = r.copy(paper = papel, crudo = crudo, ink = tinta, gfx = tinta, sand = 0, line = 0, edge = 0, muted = 0)
        }
        if (acentoP != 0L) r = r.copy(general = acentoP)
        return r
    }

    /** La paleta de Lectura (con su acento elegido), en claro o en oscuro. */
    private fun propiosPara(noche: Boolean): Tones = (if (noche) palette.dark else palette.light).let { b ->
        ACCENT_COLORS.firstOrNull { it.id == accentId }?.let { a -> b.copy(general = (if (noche) a.dark else a.light) or 0xFF000000) } ?: b
    }

    /**
     * Los colores que se ven en claro o en oscuro ([noche]): los de Atrio (si se piden y se han leído; ya vienen en el modo que
     * tengas allí) o los de Lectura, y al revés si está «invertidos». Los widgets los piden de las dos maneras.
     */
    fun tonosPara(noche: Boolean): Tones {
        val fondo = FONDOS.firstOrNull { it.id == fondoId }
        if (fondo != null) {
            // Como en Atrio: con un fondo de color, el acento «de la paleta» (o el rojo) es el del propio fondo
            val b = fondo.tones
            val a = ACCENT_COLORS.firstOrNull { it.id == accentId }
            return conPropios(if (a == null || accentId == "paleta" || accentId == "rojo") b else b.copy(general = (if (fondo.oscuro) a.dark else a.light) or 0xFF000000))
        }
        return conPropios(invertidoSi(invertido, TemaAtrio.tonos.takeIf { origenAtrio } ?: propiosPara(noche)))
    }

    /** Los colores que se ven ahora. */
    val tones: Tones get() = tonosPara(Modo.oscuro)

    /** ¿Los colores no dependen de claro/oscuro? (los de Atrio ya vienen resueltos) */
    val coloresFijos: Boolean get() = (origenAtrio && TemaAtrio.tonos != null) || fondoId.isNotEmpty()

    private fun invertidoSi(on: Boolean, t: Tones) = if (on) invertir(t) else t

    private val provider = GoogleFont.Provider(
        providerAuthority = "com.google.android.gms.fonts",
        providerPackage = "com.google.android.gms",
        certificates = R.array.com_google_android_gms_fonts_certs
    )
    private val cache = HashMap<String, FontFamily>()

    /** La letra de Google Fonts por su nombre (con sus gruesos reales), o null si es una de las que van dentro de la app. */
    fun family(name: String): FontFamily? {
        if (name.isBlank() || name == "Atrio") return null
        if (name == "Vitrina") return VitrinaDisplay
        return cache.getOrPut(name) {
            val c = (DISPLAY_FONTS + BODY_FONTS).firstOrNull { it.name == name }
            val ws = c?.weights?.takeIf { it.isNotEmpty() } ?: intArrayOf(400, 700)
            FontFamily(ws.map { GFont(GoogleFont(name), provider, FontWeight(it)) })
        }
    }

    fun aplicar(s: SkinStyle) {
        palette = PALETTES.firstOrNull { it.id == s.palette } ?: palette
        display = s.display; body = s.body
        forma = FORMAS.firstOrNull { it.id == s.form } ?: forma
    }

    /** El estilo listo que coincide con lo elegido ahora (si es uno de la lista). */
    fun estiloActual(): SkinStyle? = STYLES.firstOrNull { it.palette == palette.id && it.display == display && it.body == body && it.form == forma.id }

    private fun p(c: Context) = c.getSharedPreferences("lectura_tema", Context.MODE_PRIVATE)

    fun cargar(c: Context) {
        val p = p(c)
        if (!p.getBoolean("revista_v1", false)) { p.edit().clear().putBoolean("revista_v1", true).apply() }
        palette = PALETTES.firstOrNull { it.id == p.getString("paleta", "revista") } ?: palette
        display = p.getString("display", "Atrio") ?: "Atrio"
        body = p.getString("body", "Atrio") ?: "Atrio"
        forma = FORMAS.firstOrNull { it.id == p.getString("forma", "revista") } ?: forma
        accentId = p.getString("acento", "paleta")?.takeIf { id -> id == "paleta" || ACCENT_COLORS.any { it.id == id } } ?: "paleta"
        escala = p.getInt("escala", 100).coerceIn(80, 140)
        // La primera vez con esta versión: los colores de Atrio, invertidos
        if (!p.getBoolean("atrio_v1", false)) p.edit().putBoolean("atrio_v1", true).putBoolean("invertido", true).putBoolean("origen_atrio", true).apply()
        invertido = p.getBoolean("invertido", true)
        origenAtrio = p.getBoolean("origen_atrio", true)
        Modo.eleccion = p.getInt("modo", 0).coerceIn(0, 2)
        fondoId = p.getString("fondo_id", "")?.takeIf { id -> FONDOS.any { it.id == id } } ?: ""
        ajustesAt = p.getLong("ajustes_at", 0L)
        fondoPropio = p.getLong("fondo_propio", 0L); tintaPropia = p.getLong("tinta_propia", 0L); acentoPropio = p.getLong("acento_propio", 0L)
    }

    /** Cuándo se cambió la apariencia (milisegundos): al sincronizar con tus otros Lecturas gana la más reciente. */
    var ajustesAt = 0L

    /** Guarda la apariencia (y la marca como cambiada ahora, para que viaje a tus otros Lecturas). */
    fun guardar(c: Context) {
        ajustesAt = System.currentTimeMillis()
        persistir(c)
        SincroP.cambio(c)
    }

    /** La apariencia que viaja con la cuenta (tema, fondo, acento, colores a mano, letras, tamaño…). */
    fun ajustesJson(): JSONObject = JSONObject().put("at", ajustesAt).put("skin", JSONObject()
        .put("paleta", palette.id).put("display", display).put("body", body).put("acento", accentId).put("escala", escala)
        .put("invertido", invertido).put("origenAtrio", origenAtrio).put("modo", Modo.eleccion)
        .put("fondoId", fondoId).put("fondoPropio", fondoPropio).put("tintaPropia", tintaPropia).put("acentoPropio", acentoPropio))

    /** Aplica la apariencia de otro de tus Lecturas si es más nueva que la de aquí. Devuelve true si ha cambiado algo. */
    fun aplicarAjustes(c: Context, o: JSONObject): Boolean {
        val at = o.optLong("at", 0L)
        if (at <= ajustesAt) return false
        val k = o.optJSONObject("skin") ?: return false
        palette = PALETTES.firstOrNull { it.id == k.optString("paleta") } ?: palette
        display = k.optString("display", display).ifBlank { "Atrio" }; body = k.optString("body", body).ifBlank { "Atrio" }
        accentId = k.optString("acento", accentId).takeIf { id -> id == "paleta" || ACCENT_COLORS.any { it.id == id } } ?: "paleta"
        escala = k.optInt("escala", escala).coerceIn(80, 140)
        invertido = k.optBoolean("invertido", invertido); origenAtrio = k.optBoolean("origenAtrio", origenAtrio)
        Modo.eleccion = k.optInt("modo", Modo.eleccion).coerceIn(0, 2)
        fondoId = k.optString("fondoId").takeIf { id -> FONDOS.any { it.id == id } } ?: ""
        fondoPropio = k.optLong("fondoPropio", 0L); tintaPropia = k.optLong("tintaPropia", 0L); acentoPropio = k.optLong("acentoPropio", 0L)
        ajustesAt = at
        persistir(c)
        return true
    }

    private fun persistir(c: Context) {
        Widgets.pedir(c)
        p(c).edit().putLong("ajustes_at", ajustesAt).putString("paleta", palette.id).putString("display", display).putString("body", body).putString("forma", forma.id)
            .putString("acento", accentId).putBoolean("invertido", invertido).putBoolean("origen_atrio", origenAtrio).putInt("escala", escala).putInt("modo", Modo.eleccion)
            .putString("fondo_id", fondoId).putLong("fondo_propio", fondoPropio).putLong("tinta_propia", tintaPropia).putLong("acento_propio", acentoPropio).apply()
    }
}

// ───── colores (los mismos nombres que en Atrio) ─────
val Paper: Color get() = Skin.tones.cPaper
val Crudo: Color get() = Skin.tones.cCrudo
val Sand: Color get() = Skin.tones.cSand
val Line: Color get() = Skin.tones.cLine
val Charcoal: Color get() = Skin.tones.cInk
val Edge: Color get() = Skin.tones.cEdge
val Muted: Color get() = Skin.tones.cMuted
/** El acento (el coral de Vitrina). */
val Acento: Color get() = Color(Skin.tones.general)
val Dorado: Color get() = Color(Skin.tones.gold)
/** Texto sobre el acento: blanco o tinta oscura, el que se lea. */
val SobreAcento: Color get() = if (Acento.luminance2() > 0.5f) Color(0xFF14151A) else Color.White
fun Color.luminance2(): Float = 0.299f * red + 0.587f * green + 0.114f * blue

fun appColors() = if (Skin.tones.darkBg) darkColorScheme(
    primary = Charcoal, onPrimary = Crudo, secondary = Acento, onSecondary = SobreAcento,
    background = Paper, onBackground = Charcoal, surface = Crudo, onSurface = Charcoal,
    surfaceVariant = Sand, onSurfaceVariant = Charcoal, surfaceContainer = Crudo, surfaceContainerHigh = Crudo,
    surfaceContainerHighest = Sand, surfaceContainerLow = Crudo, outline = Edge, outlineVariant = Line, error = Acento
) else lightColorScheme(
    primary = Charcoal, onPrimary = Crudo, secondary = Acento, onSecondary = SobreAcento,
    background = Paper, onBackground = Charcoal, surface = Crudo, onSurface = Charcoal,
    surfaceVariant = Sand, onSurfaceVariant = Charcoal, surfaceContainer = Crudo, surfaceContainerHigh = Crudo,
    surfaceContainerHighest = Sand, surfaceContainerLow = Crudo, outline = Edge, outlineVariant = Line, error = Acento
)

// ───── letras ─────
val AtrioDisplay = FontFamily(
    Font(R.font.display_regular, FontWeight.Normal), Font(R.font.display_medium, FontWeight.Medium),
    Font(R.font.display_semibold, FontWeight.SemiBold), Font(R.font.display_semibold, FontWeight.Bold),
    Font(R.font.display_italic, FontWeight.Normal, FontStyle.Italic)
)

@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
val VitrinaDisplay = FontFamily(
    listOf(400, 500, 600, 700, 800).map { w ->
        Font(R.font.bricolage, FontWeight(w), variationSettings = FontVariation.Settings(FontVariation.weight(w), FontVariation.width(if (w >= 700) 92f else 100f)))
    }
)

val AtrioBody = FontFamily(
    Font(R.font.body_regular, FontWeight.Normal), Font(R.font.body_italic, FontWeight.Normal, FontStyle.Italic),
    Font(R.font.body_medium, FontWeight.Medium), Font(R.font.body_semibold, FontWeight.SemiBold),
    Font(R.font.body_bold, FontWeight.Bold), Font(R.font.body_bold, FontWeight.ExtraBold)
)

val Display: FontFamily get() = Skin.family(Skin.display) ?: AtrioDisplay
val Body: FontFamily get() = Skin.family(Skin.body) ?: AtrioBody

private val base = Typography()
private fun TextStyle.body() = copy(fontFamily = Body)
private fun TextStyle.display() = copy(fontFamily = Display, fontWeight = FontWeight.Normal)

val AppTypography: Typography get() = Typography(
    displayLarge = base.displayLarge.display(), displayMedium = base.displayMedium.display(), displaySmall = base.displaySmall.display(),
    headlineLarge = base.headlineLarge.display(), headlineMedium = base.headlineMedium.display(), headlineSmall = base.headlineSmall.display(),
    titleLarge = base.titleLarge.display(), titleMedium = base.titleMedium.body(), titleSmall = base.titleSmall.body(),
    bodyLarge = base.bodyLarge.body().copy(fontSize = 15.sp, lineHeight = 22.sp), bodyMedium = base.bodyMedium.body(), bodySmall = base.bodySmall.body(),
    labelLarge = base.labelLarge.body().copy(fontWeight = FontWeight.SemiBold), labelMedium = base.labelMedium.body().copy(fontWeight = FontWeight.Medium),
    labelSmall = base.labelSmall.body()
)

/** La raíz del tema: modo claro/oscuro, colores, letras y tamaño del texto. */
@Composable
fun LecturaTheme(content: @Composable () -> Unit) {
    val sistema = isSystemInDarkTheme()
    Modo.oscuro = when (Modo.eleccion) { 1 -> false; 2 -> true; else -> sistema }
    val d = LocalDensity.current
    androidx.compose.runtime.CompositionLocalProvider(
        LocalDensity provides Density(d.density, d.fontScale * Skin.escala / 100f)
    ) {
        MaterialTheme(colorScheme = appColors(), typography = AppTypography) {
            // El texto sin color propio sale con la tinta del tema (si no, negro: se veía mal sobre un fondo oscuro)
            androidx.compose.runtime.CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides Charcoal, content = content)
        }
    }
}
