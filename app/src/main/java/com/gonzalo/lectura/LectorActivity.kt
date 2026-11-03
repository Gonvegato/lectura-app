package com.gonzalo.lectura

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color as AColor
import android.net.Uri
import android.os.Bundle
import android.view.ActionMode
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.compose.ui.graphics.toArgb
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.webkit.WebViewAssetLoader
import org.json.JSONObject
import java.io.ByteArrayInputStream

/** El lector: una página web propia (assets/lector) que pinta el libro a pantalla completa. */
class LectorActivity : ComponentActivity() {
    companion object {
        /** [goto]: abrir directamente en ese punto (el de un subrayado). */
        fun abrir(ctx: Context, id: String, goto: String? = null) {
            ctx.startActivity(Intent(ctx, LectorActivity::class.java).putExtra("id", id).putExtra("goto", goto)
                .addFlags(if (ctx is Activity) 0 else Intent.FLAG_ACTIVITY_NEW_TASK))
        }

        /** Tipo de archivo por la extensión (el WebView es estricto con los módulos de JavaScript). */
        fun mime(path: String): String = when (path.substringAfterLast('.', "").lowercase()) {
            "html" -> "text/html"
            "js", "mjs" -> "text/javascript"
            "css" -> "text/css"
            "json" -> "application/json"
            "svg" -> "image/svg+xml"
            "png" -> "image/png"
            "woff2" -> "font/woff2"
            "ttf" -> "font/ttf"
            "pdf" -> "application/pdf"
            "epub" -> "application/epub+zip"
            else -> "application/octet-stream"
        }
    }

    private lateinit var web: WebView
    private var id = ""
    private var uri: Uri? = null

    // Leer en voz alta: la voz de Android. Lo que se pide antes de que esté lista, espera en [pending].
    private var tts: android.speech.tts.TextToSpeech? = null
    private var ttsReady = false
    private var pending: (() -> Unit)? = null
    // La última frase pedida mientras la voz arrancaba (si no arranca, se le contesta con error a esa)
    private var pendingUid: String? = null

    private fun speakNow(text: String, uid: String, rate: Float, lang: String) {
        val t = tts
        if (t == null || !ttsReady) {
            pending = { speakNow(text, uid, rate, lang) }
            pendingUid = uid
            // El aviso de «lista» se atiende en el siguiente paso del hilo principal: si no hay motor de voz llega
            // dentro del propio constructor, antes de que [tts] tenga la voz nueva
            if (t == null) tts = android.speech.tts.TextToSpeech(this) { status -> android.os.Handler(mainLooper).post { ttsInit(status) } }
            return
        }
        // El idioma del libro, si el móvil tiene esa voz (si no, la que tenga por defecto)
        val loc = java.util.Locale.forLanguageTag(lang.ifBlank { "es" })
        if (t.isLanguageAvailable(loc) >= android.speech.tts.TextToSpeech.LANG_AVAILABLE && t.voice?.locale?.language != loc.language) t.language = loc
        t.setSpeechRate(rate.coerceIn(0.5f, 2.5f))
        // Android no lee de una vez más de getMaxSpeechInputLength() letras: una frase larguísima va en trozos
        // (solo el último lleva el id que espera el lector; los de antes no los mira)
        val max = (android.speech.tts.TextToSpeech.getMaxSpeechInputLength() - 1).coerceAtLeast(100)
        val parts = if (text.length <= max) listOf(text) else text.chunked(max)
        var ok = true
        parts.forEachIndexed { i, part ->
            val last = i == parts.lastIndex
            val r = t.speak(part, if (i == 0) android.speech.tts.TextToSpeech.QUEUE_FLUSH else android.speech.tts.TextToSpeech.QUEUE_ADD,
                null, if (last) uid else "$uid.$i")
            if (r != android.speech.tts.TextToSpeech.SUCCESS) ok = false
        }
        // Si no se ha podido ni poner en cola, no llegará ningún aviso: se contesta ya con error
        if (!ok) spoken(uid, true)
    }

    private fun ttsInit(status: Int) {
        if (tts == null) return // ya se ha cerrado el lector
        ttsReady = status == android.speech.tts.TextToSpeech.SUCCESS
        if (ttsReady) {
            tts?.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}
                override fun onDone(utteranceId: String?) { spoken(utteranceId, false) }
                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) { spoken(utteranceId, true) }
                override fun onError(utteranceId: String?, errorCode: Int) { spoken(utteranceId, true) }
            })
            val p = pending; pending = null; pendingUid = null
            p?.invoke()
        } else {
            // No hay motor de voz (o no arranca): se suelta para volver a intentarlo la próxima vez
            val u = pendingUid
            pending = null; pendingUid = null
            tts?.shutdown(); tts = null
            spoken(u, true)
        }
    }

    private fun spoken(uid: String?, error: Boolean) = runOnUiThread {
        if (::web.isInitialized && uid != null) web.evaluateJavascript("window.atrioSpoken && atrioSpoken(${JSONObject.quote(uid)}, $error)", null)
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        id = intent.getStringExtra("id").orEmpty()
        uri = EstadoLectura.archivo(this, id)?.let { Uri.parse(it) }
        WindowCompat.setDecorFitsSystemWindows(window, false)
        // La página también bajo la cámara / la franja de la barra de estado: con las barras ocultas, Android deja por
        // defecto esa zona en negro (ahí van ahora la hora y la batería)
        window.attributes = window.attributes.also {
            it.layoutInDisplayCutoutMode = if (android.os.Build.VERSION.SDK_INT >= 30) WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                else WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        window.statusBarColor = AColor.TRANSPARENT
        window.navigationBarColor = AColor.TRANSPARENT
        val loader = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/") { path ->
                try { WebResourceResponse(mime(path), "utf-8", assets.open(path)) } catch (e: Exception) { null }
            }
            .addPathHandler("/libro/") { path ->
                val u = uri ?: return@addPathHandler WebResourceResponse("text/plain", "utf-8", 404, "No hay libro", null, ByteArrayInputStream(ByteArray(0)))
                try {
                    val input = contentResolver.openInputStream(u) ?: throw IllegalStateException()
                    WebResourceResponse(mime(path), null, 200, "OK", mapOf("Access-Control-Allow-Origin" to "*"), input)
                } catch (e: Exception) {
                    WebResourceResponse("text/plain", "utf-8", 404, "No se puede leer", null, ByteArrayInputStream(ByteArray(0)))
                }
            }
            .build()
        web = object : WebView(this) {
            // El menú de copiar/compartir de Android se abre vacío: el lector tiene el suyo (subrayar, nota, copiar…).
            // Ojo: devolver null aquí hace que el WebView cancele la selección al momento; hay que dejarlo abrir.
            override fun startActionMode(callback: ActionMode.Callback?, type: Int): ActionMode? =
                super.startActionMode(emptyMenu(callback), type)
            override fun startActionMode(callback: ActionMode.Callback?): ActionMode? =
                super.startActionMode(emptyMenu(callback))
        }
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.settings.allowFileAccess = false
        web.settings.allowContentAccess = false
        // Desde el primer fotograma, el fondo del tema que usas (si no, al abrir con Negro se veía un destello claro)
        val tema0 = (EstadoLectura.estado(this, id).optJSONObject("settings")
            ?: runCatching { JSONObject(getSharedPreferences("lector", MODE_PRIVATE).getString("ajustes", "{}") ?: "{}") }.getOrNull())?.optString("theme").orEmpty()
        val bg0 = AColor.parseColor(themeBg(tema0))
        window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(bg0))
        web.setBackgroundColor(bg0)
        web.webChromeClient = object : WebChromeClient() {
            // La consola del lector va al registro de Android (adb logcat -s LecturaLector)
            override fun onConsoleMessage(m: android.webkit.ConsoleMessage): Boolean {
                android.util.Log.i("LecturaLector", "${m.messageLevel()} ${m.message()} (${m.sourceId()}:${m.lineNumber()})")
                return true
            }
        }
        web.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? = loader.shouldInterceptRequest(request.url)
            override fun onPageFinished(view: WebView, url: String) { pushInsets(); pushBattery() }
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val u = request.url
                // Los capítulos se cargan en iframes con direcciones blob:/data:/about: — eso no se toca.
                // Solo los enlaces web de verdad, en la página principal, se abren fuera (en el navegador).
                if (!request.isForMainFrame || u.scheme !in setOf("http", "https") || u.host == "appassets.androidplatform.net") return false
                fuera(Intent(Intent.ACTION_VIEW, u))
                return true
            }
        }
        web.addJavascriptInterface(Bridge(), "Atrio")
        setContentView(web)
        // El lector necesita un WebView reciente (se actualiza solo desde Google Play)
        val wv = androidx.webkit.WebViewCompat.getCurrentWebViewPackage(this)
        val major = wv?.versionName?.substringBefore('.')?.toIntOrNull() ?: 0
        if (major in 1..99) android.widget.Toast.makeText(
            this, "Tu WebView es antiguo (versión $major): actualiza «Android System WebView» en Google Play si el libro no se abre.",
            android.widget.Toast.LENGTH_LONG
        ).show()
        web.loadUrl("https://appassets.androidplatform.net/assets/lector/index.html")
        // Atrás cierra primero lo que haya abierto encima (una nota, un panel, el menú); con nada encima, pulsado dos
        // veces seguidas sale del libro (una sola vez no, para no salir sin querer)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                web.evaluateJavascript("window.atrioBack ? atrioBack() : false") { r ->
                    if (r == "true") return@evaluateJavascript
                    val now = android.os.SystemClock.uptimeMillis()
                    if (now - atrasEn < 2200) web.evaluateJavascript("window.atrioPausa && atrioPausa(true)") { salir() }
                    else { atrasEn = now; android.widget.Toast.makeText(this@LectorActivity, "Pulsa atrás otra vez para salir del libro", android.widget.Toast.LENGTH_SHORT).show() }
                }
            }
        })
        // El WebView no le dice al CSS cuánto ocupan la barra de estado y la de navegación: se lo decimos nosotros,
        // para que las barras del lector no queden debajo de la hora ni de los botones de Android.
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(web) { _, ins ->
            val d = resources.displayMetrics.density
            val sys = ins.getInsetsIgnoringVisibility(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            // (solo si cambian: volver a aplicarlos recoloca el texto)
            if (sys.top / d != safeTop || sys.bottom / d != safeBottom) {
                safeTop = sys.top / d; safeBottom = sys.bottom / d
                pushInsets()
            }
            ins
        }
        bars(false)
    }

    /** El menú de selección de Android, pero sin opciones (y sin barra visible): la selección se mantiene. */
    private fun emptyMenu(cb: ActionMode.Callback?): ActionMode.Callback = object : ActionMode.Callback2() {
        override fun onCreateActionMode(mode: ActionMode, menu: android.view.Menu): Boolean { cb?.onCreateActionMode(mode, menu); menu.clear(); return true }
        override fun onPrepareActionMode(mode: ActionMode, menu: android.view.Menu): Boolean { cb?.onPrepareActionMode(mode, menu); menu.clear(); return true }
        override fun onActionItemClicked(mode: ActionMode, item: android.view.MenuItem): Boolean = false
        override fun onDestroyActionMode(mode: ActionMode) { cb?.onDestroyActionMode(mode) }
        override fun onGetContentRect(mode: ActionMode, view: View, outRect: android.graphics.Rect) {
            if (cb is ActionMode.Callback2) cb.onGetContentRect(mode, view, outRect) else super.onGetContentRect(mode, view, outRect)
        }
    }

    private var safeTop = 0f
    private var safeBottom = 0f

    private fun pushInsets() {
        if (!::web.isInitialized) return
        // (antes de que cargue la página aún no hay documento: entonces no se hace nada y llega en onPageFinished)
        web.evaluateJavascript(
            "if (document.documentElement) {" +
                "document.documentElement.style.setProperty('--safe-top','${safeTop}px');" +
                "document.documentElement.style.setProperty('--safe-bottom','${safeBottom}px');" +
                "window.atrioInsets && atrioInsets() }", null
        )
    }

    /**
     * Los ajustes con los que se abre un libro: los suyos (que viajan con él a tus otros dispositivos) encima de los
     * de este dispositivo; así, un libro que llega de otro sin tamaño de letra ni brillo usa los de aquí.
     */
    private fun ajustesDe(o: JSONObject): JSONObject {
        val base = runCatching { JSONObject(getSharedPreferences("lector", MODE_PRIVATE).getString("ajustes", "{}") ?: "{}") }.getOrElse { JSONObject() }
        base.remove("pdfTexto"); base.remove("pdfForzar")   // el formato del PDF (texto adaptable o página) es de cada libro, no un punto de partida para los demás
        o.optJSONObject("settings")?.let { b -> for (k in b.keys()) base.put(k, b.get(k)) }
        return base
    }

    /** Dónde se abre el libro: lo más lejos que has llegado en cualquier dispositivo (o, si no hay, donde lo dejaste aquí). */
    private fun empiezaEn(o: JSONObject): Pair<String?, Double> {
        val best = o.optDouble("bestFrac", 0.0)
        return if (best > 0.0 && best >= o.optDouble("fraction", 0.0) && o.optString("bestCfi").isNotBlank()) o.optString("bestCfi") to best
        else o.optString("cfi") to o.optDouble("fraction", 0.0)
    }

    /** El fondo de cada tema del lector (el mismo que en lector.css). */
    private fun themeBg(t: String) = when (t) {
        "sepia" -> "#F1E6CF"; "gris" -> "#2B2D31"; "noche" -> "#121317"; "negro" -> "#000000"
        // El tema de la app (granate…): el fondo de color que hayas elegido
        "app" -> String.format("#%06X", Skin.tones.paper and 0xFFFFFF)
        else -> "#FBF8F1"
    }


    /** «Bajar volumen» pasa página (no leyendo PDF/cómics hacia abajo: ahí baja el volumen). */
    @Volatile private var volumeTurns = true

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (!volumeTurns) return super.onKeyDown(keyCode, event)
        // Volumen para pasar página
        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) { web.evaluateJavascript("window.atrioPage && atrioPage(1)", null); return true }
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) { web.evaluateJavascript("window.atrioPage && atrioPage(-1)", null); return true }
        return super.onKeyDown(keyCode, event)
    }

    // (Sin «Fijar pantalla»: Android obliga a confirmarlo con su aviso cada vez. Atrás no saca del libro y las
    // barras del sistema siguen ocultas; de él se sale con ✕ en el menú, que se abre tocando el centro.)

    /** Abrir otra app (compartir, el navegador). */
    private fun fuera(i: Intent) { try { startActivity(i) } catch (e: Exception) { } }

    private var atrasEn = 0L

    /** Al cerrar el libro, lo que llevas hoy: «Hoy has leído 25 min · vas por el 34 %». */
    private fun salir() {
        resumenDeHoy()?.let { android.widget.Toast.makeText(applicationContext, it, android.widget.Toast.LENGTH_SHORT).show() }
        finish()
    }

    private fun resumenDeHoy(): String? {
        val o = EstadoLectura.estado(this, id)
        val ms = o.optJSONObject("stats")?.optJSONObject("days")?.optLong(java.time.LocalDate.now().toString()) ?: 0L
        val min = ms / 60000L
        if (min < 1) return null
        val pct = (o.optDouble("fraction", 0.0) * 100).toInt()
        return "Hoy has leído ${readTime(min)}" + if (pct in 1..99) " · vas por el $pct %" else ""
    }

    // El tiempo leído se para al salir a otra app o apagar la pantalla (salvo si te lo está leyendo en voz alta)
    override fun onPause() {
        super.onPause()
        if (::web.isInitialized && tts?.isSpeaking != true) web.evaluateJavascript("window.atrioPausa && atrioPausa(true)", null)
    }

    override fun onResume() {
        super.onResume()
        if (::web.isInitialized) web.evaluateJavascript("window.atrioPausa && atrioPausa(false)", null)
    }

    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    @Volatile private var candidata: Triple<String, Double, String>? = null
    private val confirmar = Runnable { confirmarMasLejos() }
    /** Lo más lejos que has llegado en este libro (en cualquiera de tus dispositivos): todos se ponen ahí. */
    private fun confirmarMasLejos() {
        val c = candidata ?: return
        candidata = null
        val o = EstadoLectura.estado(this, id)
        if (c.second <= o.optDouble("bestFrac", 0.0)) return
        o.put("bestCfi", c.first).put("bestFrac", c.second).put("bestLabel", c.third)
        EstadoLectura.poner(this, id, o)
        subirEnSeguida()
    }
    /** Por dónde vas sube a tu cuenta unos segundos después de llegar más lejos: tu otro dispositivo la ve casi al momento. */
    private fun subirEnSeguida() = SincroP.cambio(applicationContext, 4000)

    // Al dejar de leer (cerrar el libro, apagar la pantalla, cambiar de app), lo leído sube a tu cuenta: en la tablet
    // sigues por la misma página sin tener que tocar nada más
    // La batería, para la línea de arriba (solo mientras el lector se ve; Android avisa cada vez que cambia 1 %)
    private var batteryOn = false
    private val battery = object : android.content.BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) { pushBattery(i) }
    }
    private fun pushBattery(i: Intent? = null) {
        if (!::web.isInitialized) return
        val b = i ?: registerReceiver(null, android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return
        val level = b.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1)
        val scale = b.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, 100)
        if (level < 0 || scale <= 0) return
        val st = b.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, -1)
        val charging = st == android.os.BatteryManager.BATTERY_STATUS_CHARGING || st == android.os.BatteryManager.BATTERY_STATUS_FULL
        web.evaluateJavascript("window.atrioBateria && atrioBateria(${level * 100 / scale}, $charging)", null)
    }

    override fun onStart() {
        super.onStart()
        if (!batteryOn) { batteryOn = true; registerReceiver(battery, android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED)) }
    }

    override fun onStop() {
        super.onStop()
        if (batteryOn) { batteryOn = false; runCatching { unregisterReceiver(battery) } }
        confirmarMasLejos()
        // Al dejar de leer, lo leído sube ya: en la tablet sigues por la misma página sin tocar nada
        SincroP.cambio(applicationContext, 0)
    }

    override fun onDestroy() {
        tts?.stop(); tts?.shutdown(); tts = null
        (web.parent as? android.view.ViewGroup)?.removeView(web)
        web.destroy()
        super.onDestroy()
    }

    private fun bars(show: Boolean) {
        val c = WindowInsetsControllerCompat(window, window.decorView)
        c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (show) c.show(WindowInsetsCompat.Type.systemBars()) else c.hide(WindowInsetsCompat.Type.systemBars())
    }

    private fun edit(f: (JSONObject) -> Unit) {
        val o = EstadoLectura.estado(this, id)
        f(o)
        EstadoLectura.poner(this, id, o)
    }

    /** Lo que la página del lector puede pedirle a la app. */
    inner class Bridge {
        @JavascriptInterface
        fun state(): String {
            val o = EstadoLectura.estado(this@LectorActivity, id)
            val name = o.optString("name").ifBlank { "libro" }
            return JSONObject()
                .put("title", Biblioteca.por(id)?.titulo.orEmpty())
                .put("file", Biblioteca.por(id)?.archivo?.ifBlank { null } ?: name)
                .put("location", empiezaEn(o).first?.ifBlank { null } ?: JSONObject.NULL)
                .put("fraction", empiezaEn(o).second)
                .put("goto", intent.getStringExtra("goto") ?: JSONObject.NULL)
                .put("stats", o.optJSONObject("stats") ?: JSONObject())
                .put("device", Cuenta.dispositivo)
                .put("accent", accentJson())
                .put("fondo", fondoJson())
                .put("meta", Ajustes.metaLectura(this@LectorActivity))
                .put("autor", Biblioteca.por(id)?.autor.orEmpty())
                .put("settings", ajustesDe(o))
                .put("annotations", o.optJSONArray("notes") ?: org.json.JSONArray())
                .put("bookmarks", o.optJSONArray("marks") ?: org.json.JSONArray())
                .toString()
        }

        @JavascriptInterface
        fun saveProgress(cfi: String, fraction: Double, label: String) {
            val o = EstadoLectura.estado(this@LectorActivity, id)
            // Al abrir el libro el lector «llega» a la posición guardada y avisa igualmente: eso no es leer
            if (o.has("cfi") && kotlin.math.abs(o.optDouble("fraction", -1.0) - fraction) < 0.0005) return
            o.put("cfi", cfi).put("fraction", fraction).put("label", label).put("read", System.currentTimeMillis())
            EstadoLectura.poner(this@LectorActivity, id, o)
            // Si es lo más lejos que has llegado, se apunta como candidata: cuenta si te quedas ahí unos segundos o
            // al salir (un salto sin querer por la barra no cuenta si sigues leyendo desde otro sitio)
            if (fraction > o.optDouble("bestFrac", 0.0)) { candidata = Triple(cfi, fraction, label); handler.removeCallbacks(confirmar); handler.postDelayed(confirmar, 6000) }
            else { candidata = null; handler.removeCallbacks(confirmar) }
        }

        /** Los ajustes valen para este libro y como punto de partida para los siguientes. */
        @JavascriptInterface
        fun saveSettings(json: String) {
            edit { it.put("settings", JSONObject(json)) }
            getSharedPreferences("lector", MODE_PRIVATE).edit().putString("ajustes", runCatching { JSONObject(json).apply { remove("pdfTexto"); remove("pdfForzar") }.toString() }.getOrDefault(json)).apply()
        }

        @JavascriptInterface
        fun saveAnnotations(json: String) = edit { it.put("notes", org.json.JSONArray(json)) }

        /** Lo que llevas leído de este libro (tiempo total y por día). */
        @JavascriptInterface
        fun saveStats(json: String) = edit {
            // Mezclado con lo guardado: si mientras leías llegó el tiempo de otro dispositivo, no se pierde
            it.put("stats", ReadStats.merge(it.optJSONObject("stats"), JSONObject(json), Cuenta.dispositivo, Cuenta.dispositivo))
            EstadoLectura.pasaALeyendo(this@LectorActivity, id, it)
        }

        /** Lee una frase en voz alta; al acabar contesta en window.atrioSpoken(id, error). */
        @JavascriptInterface
        fun speak(text: String, id: String, rate: Double, lang: String) = runOnUiThread { speakNow(text, id, rate.toFloat(), lang) }

        @JavascriptInterface
        fun stopSpeaking() = runOnUiThread { pending = null; tts?.stop() }

        @JavascriptInterface
        fun saveBookmarks(json: String) = edit { it.put("marks", org.json.JSONArray(json)) }

        /** El brillo de la ventana mientras deslizas (o el de esta lectura, sin permiso); -1: el del móvil. */
        @JavascriptInterface
        fun setBrightness(v: Double) = runOnUiThread {
            window.attributes = window.attributes.apply { screenBrightness = if (v < 0) WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE else brilloLineal(v.toFloat()) }
        }

        /**
         * Al soltar el dedo: el brillo del móvil (el de sus ajustes), en la escala de la barra (0–1). Contesta «ok»,
         * «auto» (ok, y el brillo automático se ha apagado para que se quede donde lo pusiste) o «permiso» (Android no
         * deja: falta «Modificar ajustes del sistema»; el lector se queda con ese brillo solo mientras lo tienes abierto).
         */
        @JavascriptInterface
        fun brilloMovil(v: Double): String {
            if (!android.provider.Settings.System.canWrite(this@LectorActivity)) return "permiso"
            val r = runCatching { BrilloMovil.pon(this@LectorActivity, brilloLineal(v.toFloat())) }.getOrNull() ?: return "permiso"
            // Ya lo pone el móvil: la ventana deja de llevar el suyo
            runOnUiThread { window.attributes = window.attributes.apply { screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE } }
            return if (r) "auto" else "ok"
        }

        /** Los ajustes de Android para dar el permiso de cambiar el brillo del móvil. */
        @JavascriptInterface
        fun pedirPermisoBrillo() = runOnUiThread {
            fuera(Intent(android.provider.Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:$packageName")))
        }

        /** El brillo que hay ahora (el del lector o, en automático, el del móvil), en la escala de la barra (0–1). */
        @JavascriptInterface
        fun brilloActual(): Double {
            val o = window.attributes.screenBrightness
            if (o >= 0f) return brilloBarra(o).toDouble()
            return (brilloSistema(contentResolver)?.let { brilloBarra(it) } ?: 0.5f).toDouble()
        }

        @JavascriptInterface
        fun volumeKeys(on: Boolean) { volumeTurns = on }

        @JavascriptInterface
        fun keepScreenOn(on: Boolean) = runOnUiThread {
            if (on) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }

        @JavascriptInterface
        fun setTheme(t: String) = runOnUiThread {
            val bg = AColor.parseColor(themeBg(t))
            web.setBackgroundColor(bg)
            window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(bg))
            WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = t == "papel" || t == "sepia" || (t == "app" && !Skin.tones.darkBg)
        }

        @JavascriptInterface
        fun systemBars(show: Boolean) = runOnUiThread { bars(show) }

        @JavascriptInterface
        fun copy(text: String) = runOnUiThread {
            (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Atrio", text))
        }

        @JavascriptInterface
        fun share(text: String) = runOnUiThread {
            fuera(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), "Compartir"))
        }

        /** Compartir una imagen (la lámina de una cita, en PNG y base64). */
        @JavascriptInterface
        fun shareImage(b64: String) {
            val dir = java.io.File(cacheDir, "compartir").apply { mkdirs() }
            val f = java.io.File(dir, "cita.png")
            f.writeBytes(android.util.Base64.decode(b64, android.util.Base64.DEFAULT))
            val uri = androidx.core.content.FileProvider.getUriForFile(this@LectorActivity, "$packageName.archivos", f)
            runOnUiThread {
                fuera(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("image/png").putExtra(Intent.EXTRA_STREAM, uri)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), "Compartir la cita"))
            }
        }

        @JavascriptInterface
        fun openUrl(url: String) = runOnUiThread { fuera(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }

        @JavascriptInterface
        fun close() = runOnUiThread { salir() }

        /** Un toque de vibración al coger una palabra. */
        @JavascriptInterface
        fun buzz() = runOnUiThread { web.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS) }

        /** Significado de una palabra (y en qué idioma está su frase). Contesta en window.atrioResult(id, …). */
        @JavascriptInterface
        fun lookup(word: String, sentence: String, id: String) {
            Thread {
                val o = JSONObject()
                val lang = Diccionario.identify(sentence.ifBlank { word })
                o.put("lang", lang)
                if (lang != "und") o.put("langName", Diccionario.langName(lang))
                try {
                    val d = Diccionario.define(word, if (lang == "es" || lang == "und") "Español" else Diccionario.langName(lang))
                    val (found, defs) = d.palabra to d.defs
                    o.put("defs", org.json.JSONArray(defs))
                    if (defs.isNotEmpty() && found.lowercase() != word.lowercase()) o.put("lemma", found)
                    if (d.nota.isNotBlank()) o.put("nota", d.nota.trimEnd('.'))
                    if (defs.isEmpty()) o.put("msg", "El Wikcionario no tiene esta palabra. Prueba con la RAE.")
                } catch (e: Exception) {
                    o.put("msg", "No se ha podido consultar el Wikcionario (¿sin conexión?).")
                }
                answer(id, o)
            }.start()
        }

        /** Traduce la palabra y su frase al español (el idioma se baja la primera vez, unos 30 MB). */
        @JavascriptInterface
        fun translate(word: String, sentence: String, lang: String, id: String) {
            Thread {
                val o = JSONObject()
                try {
                    val from = if (lang == "und") Diccionario.identify(sentence.ifBlank { word }) else lang
                    o.put("word", Diccionario.translate(word, from))
                    if (sentence.isNotBlank() && sentence != word) o.put("sentence", Diccionario.translate(sentence, from))
                } catch (e: Exception) {
                    o.put("msg", "No se ha podido traducir: ${e.message ?: "sin conexión para bajar el idioma"}")
                }
                answer(id, o)
            }.start()
        }
    }

    /** El tema de la app (sus colores: fondo, texto, acento…), para que el lector sea un tema más igual que la app. */
    private fun fondoJson(): JSONObject {
        fun hex(c: Long) = String.format("#%06X", (c and 0xFFFFFF))
        val t = Skin.tones
        return JSONObject().put("id", "app").put("name", "Como la app").put("dark", t.darkBg)
            .put("bg", hex(t.paper)).put("surface", hex(t.crudo)).put("fg", hex(t.ink)).put("muted", hex(t.cMuted.toArgb().toLong())).put("line", hex(t.cLine.toArgb().toLong())).put("accent", hex(t.general))
    }

    /** El acento de la app (claro y oscuro), para que el lector vaya a juego. */
    private fun accentJson(): JSONObject {
        fun hex(c: Long) = String.format("#%06X", (c and 0xFFFFFF))
        return JSONObject().put("light", hex(Skin.tonosPara(false).general)).put("dark", hex(Skin.tonosPara(true).general))
    }

    private fun answer(id: String, o: JSONObject) = runOnUiThread {
        if (::web.isInitialized) web.evaluateJavascript("window.atrioResult && atrioResult(${JSONObject.quote(id)}, $o)", null)
    }
}
