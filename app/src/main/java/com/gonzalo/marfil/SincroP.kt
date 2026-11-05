package com.gonzalo.marfil

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.json.JSONTokener
import java.util.concurrent.atomic.AtomicBoolean

/**
 * La sincronización de Marfil con tu cuenta de Atrio, como la de Atrio entre sus dispositivos:
 *  - lo que cambias aquí (un juego, tus horas, una nota…) se sube en cuanto pasan [ESPERA] ms (por si llegan varios cambios seguidos);
 *  - mientras Marfil se ve, se escucha tu cuenta en directo: lo que sube otro de tus Marfiles se baja y se mezcla al momento;
 *  - una sola subida o mezcla a la vez; sin conexión se reintenta solo.
 * Lo que se mezcla y cómo, en [Mezcla].
 */
object SincroP {
    const val ESPERA = 600L
    private val candado = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var app: Context? = null
    private var programada: Job? = null
    private var escucha: FbStream? = null
    private val bajando = AtomicBoolean(false)
    @Volatile private var fallos = 0

    /** Qué está pasando (para Ajustes): "" sin vincular, «Sincronizado», o el último problema. */
    var estado by mutableStateOf("")
        private set
    /** Cuándo se sincronizó por última vez (milisegundos; 0 = todavía no). */
    var ultima by mutableStateOf(0L)
        private set

    private fun prefs(c: Context) = c.getSharedPreferences("marfil_sync", Context.MODE_PRIVATE)
    private val pendientes = java.util.Collections.synchronizedSet(HashSet<String>())
    private var escuchaLec: FbStream? = null
    private var escuchaCobalto: FbStream? = null
    private var escuchaAzabache: FbStream? = null

    // ---- el estado de lectura de cada libro: /lec/<libro>/<dispositivo> (el estado entero) y /lstamps/<libro>/<dispositivo> (su hora)
    private fun vistosLec(c: Context): JSONObject = runCatching { JSONObject(prefs(c).getString("vistosLec", "{}")!!) }.getOrDefault(JSONObject())

    /** Mientras lees, lo leído sube como mucho cada minuto (cada página guardada avisa; al salir del lector sube ya). Azabache y tus otros Marfiles lo escuchan: no hace falta más. */
    const val ESPERA_LECTURA = 60_000L

    /** Ha cambiado lo leído de un libro: se sube en [ms] (varias páginas seguidas se juntan en una sola subida). */
    fun cambioLectura(c: Context, id: String, ms: Long = ESPERA_LECTURA) {
        val a = app ?: run { iniciar(c); app }
        if (a == null || !Cuenta.vinculada) return
        if (!pendientes.add(id)) { if (ms > 0) return }
        scope.launch {
            delay(ms)
            withContext(NonCancellable) { subirLectura(a, id) }
        }
    }

    /** Sube el estado de lectura de un libro (si ha cambiado desde la última vez). */
    suspend fun subirLectura(c: Context, id: String): Boolean = candado.withLock {
        pendientes.remove(id)
        if (!Cuenta.vinculada) return@withLock true
        val l = Biblioteca.por(id) ?: return@withLock true
        val st = EstadoLectura.estado(c, id)
        if (st.length() == 0) return@withLock true
        try {
            val kid = MezclaLectura.kid(l.clave)
            val cuerpo = JSONObject(st.toString()).apply { remove("syncAt") }.toString()
            val hash = cuerpo.hashCode().toString() + ":" + cuerpo.length
            val p = prefs(c)
            if (p.getString("hashLec:$kid", "") == hash) return@withLock true
            val nodo = JSONObject().put("t", l.titulo).put("u", JSONObject().put(".sv", "timestamp")).put("s", st)
            Cuenta.db("PUT", "${Cuenta.raiz}/lec/$kid/${Cuenta.dispositivo}", nodo.toString(), "print=silent")
            val ts = Cuenta.db("GET", "${Cuenta.raiz}/lec/$kid/${Cuenta.dispositivo}/u", null)?.toLongOrNull() ?: throw Cuenta.Fail("Firebase no ha guardado lo leído.")
            // Los nodos ligeros van ANTES del sello: Azabache lee «tiempo» y «subrayados» en cuanto oye el sello (ver docs/CONTRATO-DATOS.md)
            // El tiempo leído, sin subrayados ni notas (ligero): lo que el Diario de Azabache lee
            st.optJSONObject("stats")?.optJSONObject("dev")?.let { d ->
                Cuenta.db("PUT", "${Cuenta.raiz}/tiempo/$kid/${Cuenta.dispositivo}", JSONObject().put("t", l.titulo).put("a", l.autor).put("dev", d).toString(), "print=silent")
            }
            // Los subrayados, en su nodo ligero (para la cita del Diario); sin ninguno, el nodo se borra
            Cuenta.db("PUT", "${Cuenta.raiz}/subrayados/$kid/${Cuenta.dispositivo}", subrayadosLigeros(l.titulo, l.autor, st)?.toString() ?: "null", "print=silent")
            Cuenta.db("PUT", "${Cuenta.raiz}/lstamps/$kid/${Cuenta.dispositivo}", ts.toString(), "print=silent")
            p.edit().putString("hashLec:$kid", hash).apply()
            guardaVistosLec(c, vistosLec(c).put("$kid/${Cuenta.dispositivo}", ts))
            bien(c); true
        } catch (e: Exception) { mal(c, e); cambioLectura(c, id, 15_000L); false }
    }

    private fun guardaVistosLec(c: Context, o: JSONObject) = prefs(c).edit().putString("vistosLec", o.toString()).apply()

    /** Baja y mezcla lo leído en tus otros dispositivos ([soloId]: solo de ese libro). Sin candado: lo llaman quienes ya lo tienen. */
    private fun bajarLecturaYa(c: Context, soloId: String? = null): Boolean {
        val libros = Biblioteca.libros.toList()
        val porKid = libros.associateBy { MezclaLectura.kid(it.clave) }
        val solo = soloId?.let { id -> libros.firstOrNull { it.id == id }?.let { MezclaLectura.kid(it.clave) } }
        if (soloId != null && solo == null) return false
        val raw = Cuenta.db("GET", if (solo != null) "${Cuenta.raiz}/lstamps/$solo" else "${Cuenta.raiz}/lstamps", null) ?: return false
        val t = runCatching { JSONObject(raw) }.getOrNull() ?: return false
        val sellos = if (solo != null) JSONObject().put(solo, t) else t
        val vistas = vistosLec(c)
        val nuevas = JSONObject(vistas.toString())
        var cambio = false
        for (kid in sellos.keys()) {
            val libro = porKid[kid] ?: continue            // aún no hay ese libro aquí: se mirará cuando llegue
            val devs = sellos.optJSONObject(kid) ?: continue
            for (dev in devs.keys()) {
                val ts = devs.optLong(dev, 0L)
                if (dev == Cuenta.dispositivo || ts <= vistas.optLong("$kid/$dev", 0L)) continue
                val s = Cuenta.db("GET", "${Cuenta.raiz}/lec/$kid/$dev/s", null) ?: continue
                val remoto = runCatching { JSONObject(s) }.getOrNull() ?: continue
                val local = EstadoLectura.estado(c, libro.id)
                val unido = MezclaLectura.mezclar(local, remoto, Cuenta.dispositivo)
                if (!MezclaLectura.igual(unido, local)) { EstadoLectura.ponerSinAvisar(c, libro.id, unido); cambio = true }
                nuevas.put("$kid/$dev", ts)
            }
        }
        guardaVistosLec(c, nuevas)
        // Si al unir salió algo que los demás no tienen (tus subrayados, tras mezclar), se sube
        return cambio
    }

    /** Al abrir un libro: lo último leído en otros dispositivos, antes de que el lector lo pida. Con un tope de tiempo. */
    suspend fun alAbrir(c: Context, id: String, tope: Long = 2500L) {
        if (!Cuenta.vinculada) return
        kotlinx.coroutines.withTimeoutOrNull(tope) { withContext(Dispatchers.IO) { candado.withLock { runCatching { bajarLecturaYa(c, id) } } } }
    }

    fun iniciar(c: Context) {
        app = c.applicationContext
        ultima = prefs(c).getLong("ultima", 0L)
    }

    // ---- lo ya visto de cada dispositivo (su hora de subida) y lo último que subí
    private fun vistos(c: Context): JSONObject = runCatching { JSONObject(prefs(c).getString("vistos", "{}")!!) }.getOrDefault(JSONObject())
    private fun guardaVistos(c: Context, o: JSONObject) = prefs(c).edit().putString("vistos", o.toString()).apply()
    fun olvidarVistos() { app?.let { prefs(it).edit().remove("vistos").remove("hash").apply() } }

    /** Algo ha cambiado aquí (Biblioteca.guardar lo avisa): se sube dentro de [ms]. */
    fun cambio(c: Context, ms: Long = ESPERA) {
        val a = app ?: run { iniciar(c); app }
        if (a == null || !Cuenta.vinculada) return
        synchronized(this) {
            programada?.cancel()
            programada = scope.launch {
                delay(ms)
                // Una subida empezada se termina siempre (si se cortara a medias, los demás no se enterarían)
                withContext(NonCancellable) { subir(a) }
            }
        }
    }

    /** Sube la biblioteca de este dispositivo si ha cambiado desde la última vez. Devuelve false si no ha podido (se reintenta solo). */
    suspend fun subir(c: Context, forzar: Boolean = false): Boolean = candado.withLock {
        if (!Cuenta.vinculada) return@withLock true
        try {
            val blob = Mezcla.aJson(Mezcla.aBlob(Cuenta.dispositivo, Biblioteca.libros.toList(), HashMap(Biblioteca.borrados), if (Skin.ajustesAt > 0L) Skin.ajustesJson().toString() else "", Sagas.lista.toList(), HashMap(Sagas.borradas), AjustesLector.paraSubir(c)))
            val hash = blob.hashCode().toString() + ":" + blob.length
            val p = prefs(c)
            if (!forzar && p.getString("hash", "") == hash) return@withLock true
            val nodo = JSONObject().put("name", Cuenta.nombreDispositivo).put("device", "marfil").put("updated", JSONObject().put(".sv", "timestamp")).put("data", blob)
            Cuenta.db("PUT", "${Cuenta.raiz}/dev/${Cuenta.dispositivo}", nodo.toString(), "print=silent")
            val ts = Cuenta.db("GET", "${Cuenta.raiz}/dev/${Cuenta.dispositivo}/updated", null)?.toLongOrNull() ?: throw Cuenta.Fail("Firebase no ha guardado la copia.")
            Cuenta.db("PUT", "${Cuenta.raiz}/stamps/${Cuenta.dispositivo}", ts.toString(), "print=silent")
            p.edit().putString("hash", hash).apply()
            runCatching { portadas(c) }
            // Mi propio sello ya lo conozco: no hace falta bajarlo
            guardaVistos(c, vistos(c).put(Cuenta.dispositivo, ts))
            bien(c)
            true
        } catch (e: Exception) { mal(c, e); false }
    }

    /** Lo que va por kDrive (los libros): se hace aparte y sus errores no estropean la sincronización. */
    private fun portadas(c: Context) { NubeLibros.subirEnSegundoPlano(c) }

    /** Baja y mezcla lo nuevo de tus otros dispositivos. Devuelve true si ha cambiado algo aquí. */
    suspend fun bajar(c: Context): Boolean = candado.withLock {
        if (!Cuenta.vinculada) return@withLock false
        try {
            val t = Cuenta.db("GET", "${Cuenta.raiz}/stamps", null)
            val sellos = t?.let { JSONObject(it) }
            val vistas = vistos(c)
            val ahora = System.currentTimeMillis()
            val nuevos = ArrayList<Blob>()
            val vistosNuevos = JSONObject(vistas.toString())
            if (sellos != null) for (dev in sellos.keys()) {
                val ts = sellos.optLong(dev, 0L)
                if (dev == Cuenta.dispositivo || ts <= vistas.optLong(dev, 0L) || ahora - ts > Cuenta.STALE) continue
                val raw = Cuenta.db("GET", "${Cuenta.raiz}/dev/$dev/data", null) ?: continue
                val datos = JSONTokener(raw).nextValue() as? String ?: continue
                Mezcla.deJson(datos)?.let { nuevos += Blob(dev, it.libros, it.borrados, it.ajustes, it.sagas, it.sagasBorradas, it.lector) }
                vistosNuevos.put(dev, ts)
            }
            var cambio = false
            // La apariencia (tema, fondo, acento, colores a mano, letras…) más reciente de tus otras instalaciones
            nuevos.mapNotNull { b -> b.ajustes.takeIf { it.isNotBlank() }?.let { runCatching { JSONObject(it) }.getOrNull() } }.maxByOrNull { it.optLong("at") }
                ?.let { if (Skin.aplicarAjustes(c, it)) cambio = true }
            // Los ajustes del lector (letra, tema, márgenes…): los del cambio más reciente de tus otros dispositivos
            nuevos.map { it.lector }.filter { it.isNotBlank() }.maxByOrNull { runCatching { JSONObject(it).optLong("at") }.getOrDefault(0L) }?.let { if (AjustesLector.aplicar(c, it)) cambio = true }
            if (nuevos.isNotEmpty()) {
                val r = Mezcla.mezclar(Biblioteca.libros.toList(), HashMap(Biblioteca.borrados), nuevos, Cuenta.dispositivo)
                if (r.cambio) { Biblioteca.aplicarMezcla(r.libros, r.borrados); cambio = true }
                val rs = Mezcla.mezclarSagas(Sagas.lista.toList(), HashMap(Sagas.borradas), nuevos, Cuenta.dispositivo)
                if (rs.cambio) { Sagas.aplicarMezcla(rs.sagas, rs.borradas); cambio = true }
            }
            guardaVistos(c, vistosNuevos)
            // Un subrayado hecho en otro dispositivo no cambia la biblioteca (que ya avisa sola): los widgets («La cita») se repintan aquí
            if (runCatching { bajarLecturaYa(c) }.getOrDefault(false)) { cambio = true; Widgets.pedir(c) }
            bien(c)
            cambio
        } catch (e: Exception) { mal(c, e); false }
    }

    /** Al vincular una cuenta (o a mano, «Sincronizar ahora»): baja lo de los demás y sube lo de este dispositivo. */
    suspend fun sincronizar(c: Context, desdeCero: Boolean = false): Boolean {
        if (desdeCero) olvidarVistos()
        bajar(c)
        val ok = subir(c, forzar = true)
        // Lo leído de cada libro que aún no esté en la cuenta (los que ya están se saltan solos)
        Biblioteca.libros.toList().forEach { runCatching { subirLectura(c, it.id) } }
        portadas(c)
        return ok
    }

    /** Varias avisos de la escucha seguidos se juntan en una sola bajada. */
    fun bajarPronto() {
        val c = app ?: return
        if (!Cuenta.vinculada || !bajando.compareAndSet(false, true)) return
        scope.launch {
            try { delay(150); bajar(c) } finally { bajando.set(false) }
        }
    }

    /** Mientras Marfil se ve: escucha tu cuenta en directo (lo que suba otro dispositivo se baja al momento). */
    fun escuchar(c: Context) {
        iniciar(c)
        if (!Cuenta.vinculada) { parar(); return }
        if (escucha != null) return
        // Al volver a Marfil: se mira lo que haya pasado mientras tanto
        bajarPronto()
        val f = FbStream("marfil", { renovar -> Cuenta.urlFlujo("${Cuenta.raiz}/stamps", renovar) }, { _, _ -> bajarPronto() }, { })
        escucha = f
        f.start(scope)
        val g = FbStream("marfil-lec", { renovar -> Cuenta.urlFlujo("${Cuenta.raiz}/lstamps", renovar) }, { _, _ -> bajarPronto() }, { })
        escuchaLec = g
        g.start(scope)
        // Cobalto: en cuanto publica un capítulo terminado (sella su «stamps»), los Escritos se traen y sus EPUB se rehacen
        val h = FbStream("marfil-cobalto", { renovar -> Cuenta.urlFlujo("/users/${Cuenta.uid}/cobalto/stamps", renovar) }, { _, _ -> escritosPronto() }, { })
        escuchaCobalto = h
        h.start(scope)
        // Azabache: en cuanto valoras un libro allí (sella su «stamps»), la nota se ve aquí al momento
        val k = FbStream("marfil-azabache", { renovar -> Cuenta.urlFlujo("/users/${Cuenta.uid}/stamps", renovar) }, { _, _ -> NotaAzabache.pronto(c) }, { })
        escuchaAzabache = k
        k.start(scope)
    }

    private val mirandoEscritos = java.util.concurrent.atomic.AtomicBoolean(false)
    /** Varios avisos de Cobalto seguidos se juntan en una sola lectura de sus Escritos. */
    fun escritosPronto() {
        val c = app ?: return
        if (!Cuenta.vinculada || !mirandoEscritos.compareAndSet(false, true)) return
        scope.launch {
            try { delay(600); runCatching { Escritos.refrescar(c) } } finally { mirandoEscritos.set(false) }
        }
    }

    fun parar() { escucha?.stop(); escucha = null; escuchaLec?.stop(); escuchaLec = null; escuchaCobalto?.stop(); escuchaCobalto = null; escuchaAzabache?.stop(); escuchaAzabache = null }

    private fun bien(c: Context) {
        fallos = 0
        val ahora = System.currentTimeMillis()
        ultima = ahora; prefs(c).edit().putLong("ultima", ahora).apply()
        estado = "Sincronizado"
    }

    private fun mal(c: Context, e: Exception) {
        fallos++
        estado = (e.message ?: "sin conexión").let { if (e is java.io.IOException) "Sin conexión: se reintenta solo." else it }
        // Se reintenta solo: 5 s, 15 s, 45 s… hasta 5 minutos
        if (fallos <= 8 && Cuenta.vinculada) cambio(c, minOf(300_000L, 5_000L * Math.pow(3.0, (fallos - 1).toDouble()).toLong()))
    }
}
