package com.gonzalo.marfil

import android.content.Context
import android.net.ConnectivityManager
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONObject
import androidx.work.*
import java.util.concurrent.TimeUnit
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/** Dónde está el archivo de un libro en tu kDrive: su id, su nombre y su tamaño. */
class NubeLibro(val f: Long, val n: String, val s: Long)

/**
 * Los archivos de los libros por tu kDrive. Cada libro que añades se sube solo (con Wi-Fi) a «Mi carpeta/Marfil/Libros/<título>/»;
 * en tu cuenta queda el índice (/marfil/nube/<libro>: id de kDrive, nombre y tamaño; nunca rutas de tu móvil) y así tus otros dispositivos
 * saben qué hay y lo bajan cuando abres el libro. Los libros que vienen de Atrio apuntan a su archivo de «Atrio/Libros/…»: no se duplican.
 */
object NubeLibros {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val subiendo = AtomicBoolean(false)
    private fun prefs(c: Context) = c.getSharedPreferences("marfil_nube", Context.MODE_PRIVATE)

    fun deJson(o: JSONObject?): NubeLibro? = o?.let { if (it.optLong("f") > 0) NubeLibro(it.optLong("f"), it.optString("n"), it.optLong("s")) else null }
    fun aJson(x: NubeLibro): JSONObject = JSONObject().put("f", x.f).put("n", x.n).put("s", x.s).put("u", System.currentTimeMillis())

    /** El índice que se conoce (el de la cuenta, la última vez que se bajó), por nombre de libro ([MezclaLectura.kid]). */
    fun indice(c: Context): Map<String, NubeLibro> {
        val o = runCatching { JSONObject(prefs(c).getString("indice", "{}")!!) }.getOrDefault(JSONObject())
        return o.keys().asSequence().mapNotNull { k -> deJson(o.optJSONObject(k))?.let { k to it } }.toMap()
    }

    fun de(c: Context, l: Libro): NubeLibro? = indice(c)[MezclaLectura.kid(l.clave)]

    private fun guardaIndice(c: Context, m: Map<String, NubeLibro>) {
        val o = JSONObject(); m.forEach { (k, x) -> o.put(k, JSONObject().put("f", x.f).put("n", x.n).put("s", x.s)) }
        prefs(c).edit().putString("indice", o.toString()).apply()
    }

    /** Pone un libro en el índice (aquí y en la cuenta). Bloqueante (red). */
    fun registrar(c: Context, l: Libro, x: NubeLibro) {
        val k = MezclaLectura.kid(l.clave)
        guardaIndice(c, indice(c) + (k to x))
        if (Cuenta.vinculada) Cuenta.db("PUT", "${Cuenta.raiz}/nube/$k", aJson(x).toString(), "print=silent")
    }

    /** Baja el índice de la cuenta (lo que subieron tus otros dispositivos). Bloqueante (red). */
    fun leerIndice(c: Context): Boolean {
        if (!Cuenta.vinculada) return false
        val raw = Cuenta.db("GET", "${Cuenta.raiz}/nube", null)
        val o = raw?.let { runCatching { JSONObject(it) }.getOrNull() } ?: return false
        val nuevo = HashMap(indice(c))
        for (k in o.keys()) deJson(o.optJSONObject(k))?.let { nuevo[k] = it }
        guardaIndice(c, nuevo)
        return true
    }

    private fun seguro(s: String) = s.replace(Regex("""[\\/:*?"<>|\u0000-\u001f]+"""), " ").replace(Regex("\\s+"), " ").trim().trimEnd('.').take(80).trim().ifBlank { "Sin nombre" }

    private fun sinMedir(c: Context): Boolean = runCatching { !(c.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager).isActiveNetworkMetered }.getOrDefault(true)

    /** Sube los libros que están aquí y aún no están en tu kDrive. Solo con Wi-Fi (o datos sin límite) salvo que se pida a mano. Devuelve cuántos ha subido. */
    fun subirPendientes(c: Context, aMano: Boolean = false, aviso: (String) -> Unit = {}): Int {
        if (!KDrive.on(c) || !Cuenta.vinculada) return 0
        if (!aMano && !sinMedir(c)) return 0
        if (!subiendo.compareAndSet(false, true)) return 0
        var n = 0
        try {
            runCatching { leerIndice(c) }
            for (l in Biblioteca.propios()) {
                if (l.uri.isBlank() || de(c, l) != null) continue
                val f = runCatching { File(android.net.Uri.parse(l.uri).path ?: return@runCatching null) }.getOrNull()?.takeIf { it.exists() && it.length() > 0 } ?: continue
                val ext = (l.archivo.substringAfterLast('.', "").ifBlank { f.extension }).lowercase().take(5).ifBlank { "epub" }
                val nombre = "${seguro(l.titulo)}.$ext"
                try {
                    aviso("Subiendo $nombre…")
                    val id = KDrive.upload(c, "Marfil/Libros/${seguro(l.titulo)}", nombre, f.length()) { f.inputStream() }
                    registrar(c, l, NubeLibro(id, nombre, f.length()))
                    n++
                } catch (e: Exception) { aviso("No he podido subir ${l.titulo}: ${e.message}") }
            }
        } finally { subiendo.set(false) }
        return n
    }

    // ------------------------------------------------------------------ ajustes
    /** Tener en este dispositivo todos los libros de tu kDrive (se bajan solos con Wi-Fi). Por defecto, sí. */
    fun autoBajar(c: Context) = prefs(c).getBoolean("auto_bajar", true)
    fun ponAutoBajar(c: Context, v: Boolean) { prefs(c).edit().putBoolean("auto_bajar", v).apply(); if (v) programar(c) }

    // ------------------------------------------------------------------ portadas
    // Solo viajan las portadas que PONES o QUITAS tú (galería, internet, Pinterest, «la del libro», «Quitar»). Las que no has puesto no existen:
    // el libro lleva su tapa de color con la letra. Su índice en la cuenta: /marfil/portadas/<libro> = {f: id de kDrive, u: versión, p: 1 (puesta por ti), x: 1 (quitada)}.

    /** Una entrada del índice de portadas. */
    class EntradaPortada(val f: Long, val u: Long, val propia: Boolean, val quitada: Boolean)

    /** Qué hacer con la portada de un libro: «subir», «subir-quitada», «bajar», «borrar-local» o «nada». Sin red: se puede probar. */
    fun accionPortada(r: EntradaPortada?, hayLocal: Boolean, versionLocal: Long, pendiente: Boolean, quitadaPendiente: Boolean): String = when {
        quitadaPendiente -> "subir-quitada"
        pendiente && hayLocal -> "subir"
        r == null || !r.propia -> "nada"        // (lo que no puso nadie a mano, no se baja)
        r.quitada -> if (hayLocal && r.u > versionLocal) "borrar-local" else "nada"
        r.f > 0 && (!hayLocal || r.u > versionLocal) -> "bajar"
        else -> "nada"
    }

    private fun idxPortadas(c: Context): Map<String, EntradaPortada> {
        val o = runCatching { JSONObject(prefs(c).getString("portadas", "{}")!!) }.getOrDefault(JSONObject())
        return o.keys().asSequence().mapNotNull { k -> o.optJSONObject(k)?.let { k to EntradaPortada(it.optLong("f"), it.optLong("u"), it.optInt("p") == 1, it.optInt("x") == 1) } }.toMap()
    }
    private fun guardaIdxPortadas(c: Context, m: Map<String, EntradaPortada>) {
        val o = JSONObject(); m.forEach { (k, v) -> o.put(k, JSONObject().put("f", v.f).put("u", v.u).put("p", if (v.propia) 1 else 0).put("x", if (v.quitada) 1 else 0)) }
        prefs(c).edit().putString("portadas", o.toString()).apply()
    }

    /** Has puesto o cambiado la portada de un libro: se anota (versión nueva, pendiente de subir) y se intenta subir ya. */
    fun portadaCambiada(c: Context, l: Libro) {
        val kid = MezclaLectura.kid(l.clave)
        prefs(c).edit().putLong("pu:$kid", System.currentTimeMillis()).putBoolean("pup:$kid", true).putBoolean("pux:$kid", false).apply()
        val app = c.applicationContext
        scope.launch { runCatching { sincronizarPortadas(app) } }
        programar(app)
    }

    /** Has quitado la portada de un libro: se anota para que se quite también en tus otros dispositivos. */
    fun portadaQuitada(c: Context, l: Libro) {
        val kid = MezclaLectura.kid(l.clave)
        prefs(c).edit().putLong("pu:$kid", System.currentTimeMillis()).putBoolean("pux:$kid", true).putBoolean("pup:$kid", false).apply()
        val app = c.applicationContext
        scope.launch { runCatching { sincronizarPortadas(app) } }
        programar(app)
    }

    fun portadaPendiente(c: Context, l: Libro) = prefs(c).getBoolean("pup:${MezclaLectura.kid(l.clave)}", false)
    fun quitadaPendiente(c: Context, l: Libro) = prefs(c).getBoolean("pux:${MezclaLectura.kid(l.clave)}", false)

    /**
     * Una sola vez (al actualizar a esta versión): quita las portadas que no puso nadie. Antes Marfil sacaba la portada del propio archivo (y, en un PDF,
     * dibujaba su primera página), y la subía. Se reconocen así: `<id>.jpg` es la sacada del archivo; `<id>-<hora>.jpg` con esa misma hora como versión
     * en la cuenta es una bajada de otro dispositivo (de origen desconocido); el resto las pusiste tú aquí y se vuelven a subir como tuyas.
     * Devuelve cuántas ha quitado.
     */
    fun limpiarPortadasAutomaticas(c: Context): Int {
        val p = prefs(c)
        if (p.getBoolean("portadas_v2", false)) return 0
        var quitadas = 0
        for (l0 in Biblioteca.propios()) {
            val l = Biblioteca.por(l0.id) ?: continue
            val f = archivoPortada(l) ?: continue
            val kid = MezclaLectura.kid(l.clave)
            val hora = f.name.removePrefix(l.id + "-").removeSuffix(".jpg").toLongOrNull()
            val automatica = f.name == "${l.id}.jpg" || (hora != null && p.getLong("pu:$kid", 0L) == hora)
            if (automatica) { f.delete(); Biblioteca.poner(l.copy(portada = "")); quitadas++ }
            else p.edit().putBoolean("pup:$kid", true).putLong("pu:$kid", System.currentTimeMillis()).apply()
        }
        p.edit().putBoolean("portadas_v2", true).apply()
        if (quitadas > 0 || Biblioteca.libros.isNotEmpty()) programar(c)
        return quitadas
    }

    private fun archivoPortada(l: Libro): File? = l.portada.takeIf { it.isNotBlank() && !it.startsWith("http") }?.let { File(it) }?.takeIf { it.exists() && it.length() > 0 }

    private val sincPortadas = AtomicBoolean(false)

    /** Sube las portadas que has puesto (o quitado) tú y baja las que pusiste en otros dispositivos. Bloqueante (red). */
    fun sincronizarPortadas(c: Context): Int {
        if (!KDrive.on(c) || !Cuenta.vinculada || !sincPortadas.compareAndSet(false, true)) return 0
        var hechas = 0
        try {
            // Las que ya están en el móvil aunque el libro no apunte a ellas: se vuelven a poner (no hace falta bajarlas)
            runCatching { Portadas.reenlazar(c) }
            // El índice de la cuenta
            val raw = Cuenta.db("GET", "${Cuenta.raiz}/portadas", null)
            val o = raw?.let { runCatching { JSONObject(it) }.getOrNull() } ?: JSONObject()
            val remoto = HashMap(idxPortadas(c))
            for (k in o.keys()) o.optJSONObject(k)?.let { x -> remoto[k] = EntradaPortada(x.optLong("f"), x.optLong("u"), x.optInt("p") == 1, x.optInt("x") == 1) }
            guardaIdxPortadas(c, remoto)
            val p = prefs(c)
            for (l0 in Biblioteca.propios()) {
                var l = Biblioteca.por(l0.id) ?: continue
                // Las portadas de internet (las que pusiste en Atrio) se guardan antes en el móvil
                if (l.portada.startsWith("http")) l = Portadas.cachear(c, l) ?: l
                val kid = MezclaLectura.kid(l.clave)
                val local = archivoPortada(l)
                val ver = p.getLong("pu:$kid", 0L)
                val r = remoto[kid]
                try {
                    when (accionPortada(r, local != null, ver, p.getBoolean("pup:$kid", false), p.getBoolean("pux:$kid", false))) {
                        "subir" -> {
                            val u = if (ver > 0) ver else System.currentTimeMillis()
                            val id = KDrive.upload(c, "Marfil/Portadas", "$kid.jpg", local!!.length()) { local.inputStream() }
                            Cuenta.db("PUT", "${Cuenta.raiz}/portadas/$kid", JSONObject().put("f", id).put("u", u).put("p", 1).toString(), "print=silent")
                            remoto[kid] = EntradaPortada(id, u, true, false); guardaIdxPortadas(c, remoto)
                            p.edit().putLong("pu:$kid", u).putBoolean("pup:$kid", false).apply(); hechas++
                        }
                        "subir-quitada" -> {
                            val u = if (ver > 0) ver else System.currentTimeMillis()
                            Cuenta.db("PUT", "${Cuenta.raiz}/portadas/$kid", JSONObject().put("f", 0).put("u", u).put("p", 1).put("x", 1).toString(), "print=silent")
                            remoto[kid] = EntradaPortada(0, u, true, true); guardaIdxPortadas(c, remoto)
                            p.edit().putLong("pu:$kid", u).putBoolean("pux:$kid", false).apply(); hechas++
                        }
                        "bajar" -> {
                            val dir = Metadatos.dirPortadas(c)
                            val f = File(dir, "${l.id}-${r!!.u}.jpg")
                            KDrive.download(c, r.f, f)
                            if (android.graphics.BitmapFactory.decodeFile(f.path) == null) { f.delete(); continue }
                            dir.listFiles { x -> x.name.startsWith(l.id) && x != f }?.forEach { it.delete() }
                            Biblioteca.poner((Biblioteca.por(l.id) ?: l).copy(portada = f.absolutePath))
                            p.edit().putLong("pu:$kid", r.u).apply(); hechas++
                        }
                        "borrar-local" -> {
                            Metadatos.dirPortadas(c).listFiles { x -> x.name.startsWith(l.id) }?.forEach { it.delete() }
                            Biblioteca.poner((Biblioteca.por(l.id) ?: l).copy(portada = ""))
                            p.edit().putLong("pu:$kid", r!!.u).apply(); hechas++
                        }
                    }
                } catch (e: Exception) { /* esta portada se reintenta la próxima vez */ }
            }
        } finally { sincPortadas.set(false) }
        return hechas
    }

    // ------------------------------------------------------------------ todo junto, en segundo plano
    /** Baja los libros que están en tu kDrive y no tienes aquí (con Wi-Fi). Para si queda poco sitio. */
    fun bajarTodos(c: Context, aviso: (String) -> Unit = {}): Int {
        if (!KDrive.on(c)) return 0
        var n = 0
        for (l in Biblioteca.propios()) {
            if (!l.enOtroDispositivo || de(c, l) == null) continue
            if (c.filesDir.usableSpace < 300L * 1024 * 1024) { aviso("Queda poco sitio en el móvil: dejo de bajar libros."); break }
            runCatching { aviso("Bajando ${l.titulo}…"); bajar(c, l); n++ }
        }
        return n
    }

    /** Todo lo de kDrive que toca hacer ahora: índice, subir libros, portadas y (si lo has dejado activo) bajar los que faltan. Bloqueante (red). */
    fun todo(c: Context) {
        runCatching { leerIndice(c) }
        runCatching { subirPendientes(c, aMano = true) }
        runCatching { sincronizarPortadas(c) }
        if (autoBajar(c)) runCatching { bajarTodos(c) }
    }

    /** Pide que se haga en segundo plano (aunque cierres la app) cuando haya Wi-Fi. Varias peticiones seguidas se juntan en una. */
    fun programar(c: Context) {
        if (!KDrive.on(c) || !Cuenta.vinculada) return
        val req = OneTimeWorkRequestBuilder<SincroLibrosWorker>()
            .setInitialDelay(15, TimeUnit.SECONDS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.UNMETERED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES).build()
        WorkManager.getInstance(c.applicationContext).enqueueUniqueWork("marfil-libros", ExistingWorkPolicy.KEEP, req)
    }

    /** En segundo plano, sin molestar (lo llama la sincronización). */
    fun subirEnSegundoPlano(c: Context) = programar(c)

    /** Baja el archivo de un libro de tu kDrive y lo deja aquí. Bloqueante (red). Devuelve el libro ya con su archivo, o lanza el error. */
    fun bajar(c: Context, l: Libro): Libro {
        val x = de(c, l) ?: throw KDrive.Fail("Ese libro no está en tu kDrive todavía.")
        val ext = x.n.substringAfterLast('.', "epub").lowercase().take(5)
        val dest = File(Metadatos.dirLibros(c), "${l.id}.$ext")
        KDrive.download(c, x.f, dest)
        val ahora = Biblioteca.por(l.id) ?: l
        val conArchivo = ahora.copy(uri = android.net.Uri.fromFile(dest).toString(), archivo = x.n)
        return conArchivo.also { Biblioteca.poner(it) }
    }

    /** Lo que pasa al abrir un libro «en otro dispositivo»: si está en tu kDrive, se baja y se abre; si no, se dice por qué. */
    fun abrirBajando(c: Context, l: Libro, goto: String?) {
        val hilo = Handler(Looper.getMainLooper())
        fun dice(t: String) = hilo.post { Toast.makeText(c, t, Toast.LENGTH_LONG).show() }
        if (!KDrive.on(c)) { dice("Este libro está en otro dispositivo. Conecta tu kDrive (Ajustes → Avanzado) para bajarlo."); return }
        scope.launch {
            runCatching { leerIndice(c) }
            if (de(c, l) == null) { dice("Este libro aún no está en tu kDrive: se sube solo, con Wi-Fi, desde el dispositivo donde lo añadiste."); return@launch }
            dice("Bajando «${l.titulo}»…")
            try { val n = bajar(c, l); hilo.post { LectorActivity.abrir(c, n.id, goto) } }
            catch (e: Exception) { dice("No he podido bajarlo: ${e.message}") }
        }
    }
}

/** El trabajo en segundo plano de los libros: subir, bajar y portadas, con Wi-Fi. */
class SincroLibrosWorker(ctx: Context, p: WorkerParameters) : CoroutineWorker(ctx, p) {
    override suspend fun doWork(): Result = try { NubeLibros.todo(applicationContext); Result.success() } catch (e: Exception) { if (runAttemptCount < 4) Result.retry() else Result.failure() }
}
