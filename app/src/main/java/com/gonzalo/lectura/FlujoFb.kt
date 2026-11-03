package com.gonzalo.lectura

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL

// Copiado de Atrio (Suite.kt): la escucha en directo de un sitio de la base de datos de Firebase (eventos del servidor).

/** Lee un sitio de la base de datos en streaming y mantiene una copia del árbol. */
class FbStream(
    private val name: String,
    private val url: (Boolean) -> String?,
    private val onTree: (JSONObject?, String) -> Unit,
    private val onState: (String) -> Unit = {}
) {
    @Volatile private var conn: HttpURLConnection? = null
    private var job: Job? = null
    private var tree: Any? = null

    fun start(scope: CoroutineScope) {
        if (job?.isActive == true) return
        job = scope.launch(Dispatchers.IO) {
            var retry = 0
            while (isActive) {
                val u = try { url(retry > 0) } catch (e: Exception) { null }
                if (u == null) { onState("sin sesión"); delay(15_000); retry++; continue }
                try {
                    val c = URL(u).openConnection() as HttpURLConnection
                    conn = c
                    c.setRequestProperty("Accept", "text/event-stream")
                    c.instanceFollowRedirects = true
                    c.connectTimeout = 15_000
                    // Firebase manda un «keep-alive» cada medio minuto: si en 75 s no llega nada, se reconecta
                    c.readTimeout = 75_000
                    val code = c.responseCode
                    if (code == 401 || code == 403) { onState("sin permiso"); c.disconnect(); delay(30_000); retry++; continue }
                    if (code !in 200..299) throw java.io.IOException("respuesta $code")
                    retry = 0
                    onState("conectado")
                    val reader = c.inputStream.bufferedReader(Charsets.UTF_8)
                    var event = ""
                    val data = StringBuilder()
                    while (isActive) {
                        val line = reader.readLine() ?: break
                        when {
                            line.startsWith("event:") -> event = line.substring(6).trim()
                            line.startsWith("data:") -> { if (data.isNotEmpty()) data.append('\n'); data.append(line.substring(5).trimStart()) }
                            line.isEmpty() -> {
                                handle(event, data.toString())
                                if (event == "auth_revoked" || event == "cancel") break
                                event = ""
                                data.setLength(0)
                            }
                        }
                    }
                    c.disconnect()
                    onState("reconectando")
                    delay(1000)
                } catch (e: SocketTimeoutException) {
                    onState("reconectando")
                } catch (e: Exception) {
                    if (!isActive) break
                    onState("sin conexión")
                    retry++
                    delay(minOf(60_000L, 1000L shl minOf(retry, 6)))
                }
            }
        }
    }

    private fun handle(event: String, raw: String) {
        if (event != "put" && event != "patch") return
        val o = try { JSONObject(raw) } catch (e: Exception) { return }
        val path = o.optString("path", "/")
        val data = o.opt("data")
        tree = setIn(tree, path.split('/').filter { it.isNotEmpty() }, data, event == "patch")
        onTree(tree as? JSONObject, path)
    }

    private fun setIn(root: Any?, ps: List<String>, v: Any?, merge: Boolean): Any? {
        if (ps.isEmpty()) {
            if (merge && root is JSONObject && v is JSONObject) {
                val out = JSONObject(root.toString())
                v.keys().forEach { k -> if (v.isNull(k)) out.remove(k) else out.put(k, v.get(k)) }
                return out
            }
            return if (v == JSONObject.NULL) null else v
        }
        val out = if (root is JSONObject) root else JSONObject()
        val next = setIn(out.opt(ps[0]), ps.drop(1), v, merge)
        if (next == null || (next is JSONObject && next.length() == 0)) out.remove(ps[0]) else out.put(ps[0], next)
        return out
    }

    fun stop() {
        job?.cancel()
        job = null
        val c = conn
        conn = null
        Thread { try { c?.disconnect() } catch (e: Exception) { } }.start()
        tree = null
    }
}
