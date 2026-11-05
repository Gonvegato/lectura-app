package com.gonzalo.marfil

import android.content.Context
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.json.JSONTokener
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.UUID

/**
 * La cuenta de Atrio, vinculada a Marfil. Es la misma cuenta (usuario y contraseña de Firebase Authentication, por su API REST),
 * y se usa igual que Atrio: cada dispositivo escribe solo en su hueco, bajo /users/<tu uid>/marfil/:
 *   dev/<dispositivo>     { name, device, updated, data }   — la biblioteca de ese dispositivo (ver [Mezcla])
 *   stamps/<dispositivo>  hora de su última subida (para mirar novedades con una consulta mínima y para escuchar en directo)
 * Marfil no crea cuentas: entra en la que ya tienes en Atrio. La contraseña no se guarda; solo el pase de acceso (se renueva solo).
 */
object Cuenta {
    const val DEFAULT_DB = "https://app-ratear-default-rtdb.europe-west1.firebasedatabase.app"
    /** Firebase pide un correo: el usuario se convierte en uno que no existe (como en Atrio). */
    private const val MAIL = "ronron.example.com"
    /** Lo que lleve más de 150 días sin subir no se mezcla. */
    const val STALE = 150L * 86_400_000L

    /** Direcciones: en las pruebas se cambian por las del Firebase de mentira. */
    var dbUrl = DEFAULT_DB
    var authBase = ""

    class Fail(msg: String) : Exception(msg)

    private lateinit var p: android.content.SharedPreferences

    var usuario by mutableStateOf("")
        private set
    var uid = ""; private set
    private var pase = ""; private var refresco = ""; private var caduca = 0L
    private var webKey = ""
    /** Este dispositivo, para su hueco en la cuenta. */
    var dispositivo = ""; private set

    val vinculada: Boolean get() = uid.isNotBlank() && refresco.isNotBlank()

    fun cargar(c: Context) {
        p = c.getSharedPreferences("marfil_cuenta", Context.MODE_PRIVATE)
        usuario = p.getString("usuario", "").orEmpty(); uid = p.getString("uid", "").orEmpty()
        pase = p.getString("pase", "").orEmpty(); refresco = p.getString("refresco", "").orEmpty(); caduca = p.getLong("caduca", 0L)
        webKey = p.getString("webKey", "").orEmpty()
        dispositivo = p.getString("dispositivo", null) ?: ("marfil-" + UUID.randomUUID().toString().take(8)).also { p.edit().putString("dispositivo", it).apply() }
    }

    val nombreDispositivo: String get() = Build.MODEL?.takeIf { it.isNotBlank() } ?: "Android"

    fun normUser(s: String) = s.trim().lowercase()
    private fun email(u: String) = "$u@$MAIL"

    // ---------------------------------------------------------------- HTTP

    private class Res(val code: Int, val text: String)

    private fun http(method: String, url: String, body: String?, form: Boolean = false): Res {
        val c = URL(url).openConnection() as HttpURLConnection
        try { c.requestMethod = method } catch (e: java.net.ProtocolException) {
            // (el Java de las pruebas no conoce PATCH: Firebase acepta un POST que diga cuál es)
            c.requestMethod = "POST"; c.setRequestProperty("X-HTTP-Method-Override", method)
        }
        c.connectTimeout = 15000; c.readTimeout = 20000
        if (body != null) {
            c.doOutput = true
            c.setRequestProperty("Content-Type", if (form) "application/x-www-form-urlencoded" else "application/json")
            c.outputStream.use { it.write(body.toByteArray()) }
        }
        val code = c.responseCode
        val txt = (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
        return Res(code, txt)
    }

    private fun authMsg(text: String): String {
        val raw = try { JSONObject(text).optJSONObject("error")?.optString("message") ?: text } catch (e: Exception) { text }
        if (raw.contains("API key not valid", true) || raw.contains("API_KEY_INVALID")) return "La clave web de Firebase no es válida."
        return when (raw.substringBefore(" ").substringBefore(":").trim()) {
            "EMAIL_NOT_FOUND", "INVALID_PASSWORD", "INVALID_LOGIN_CREDENTIALS", "INVALID_EMAIL" -> "Usuario o contraseña incorrectos."
            "TOO_MANY_ATTEMPTS_TRY_LATER" -> "Demasiados intentos seguidos. Espera un rato."
            "USER_DISABLED" -> "Esta cuenta está desactivada."
            "TOKEN_EXPIRED", "INVALID_REFRESH_TOKEN", "USER_NOT_FOUND", "INVALID_ID_TOKEN", "CREDENTIAL_TOO_OLD_LOGIN_AGAIN" -> "La sesión ha caducado. Vuelve a entrar con tu contraseña."
            "OPERATION_NOT_ALLOWED", "PASSWORD_LOGIN_DISABLED" -> "En Firebase no está activado el acceso con correo y contraseña."
            else -> "Firebase: $raw"
        }
    }

    private fun idUrl(op: String) = authBase.ifBlank { "https://identitytoolkit.googleapis.com" } + "/v1/accounts:$op?key=" + webKey
    private fun tokenUrl() = authBase.ifBlank { "https://securetoken.googleapis.com" } + "/v1/token?key=" + webKey

    // ---------------------------------------------------------------- Sesión

    /** La clave web de Firebase: la que dejó publicada Atrio en la base de datos (es pública, para que los dispositivos no la tengan que pegar). */
    private fun buscarClave(): String {
        if (webKey.isNotBlank()) return webKey
        val r = try { http("GET", "$dbUrl/config/webKey.json", null) } catch (e: Exception) { throw Fail("No hay conexión con Firebase.") }
        val k = if (r.code == 200) (JSONTokener(r.text.trim()).nextValue() as? String ?: "") else ""
        if (k.isBlank()) throw Fail("No encuentro la clave de Firebase de tu cuenta. Entra una vez en Azabache con ella y vuelve a intentarlo.")
        webKey = k; p.edit().putString("webKey", k).apply()
        return k
    }

    private fun guardar(user: String, o: JSONObject) {
        usuario = user; uid = o.getString("localId"); pase = o.getString("idToken"); refresco = o.getString("refreshToken")
        caduca = System.currentTimeMillis() + (o.optString("expiresIn", "3600").toLongOrNull() ?: 3600L) * 1000L
        p.edit().putString("usuario", usuario).putString("uid", uid).putString("pase", pase).putString("refresco", refresco).putLong("caduca", caduca).apply()
    }

    /** Entra en tu cuenta de Atrio (la que ya existe). Devuelve el uid. */
    suspend fun entrar(user: String, pass: String): String = withContext(Dispatchers.IO) {
        val u = normUser(user)
        if (u.isBlank() || pass.isBlank()) throw Fail("Escribe tu usuario y tu contraseña de Atrio.")
        buscarClave()
        val body = JSONObject().put("email", email(u)).put("password", pass).put("returnSecureToken", true)
        val r = http("POST", idUrl("signInWithPassword"), body.toString())
        if (r.code !in 200..299) throw Fail(authMsg(r.text))
        val previo = uid
        guardar(u, JSONObject(r.text))
        if (previo != uid) SincroP.olvidarVistos()
        uid
    }

    fun salir() {
        uid = ""; pase = ""; refresco = ""; caduca = 0L; usuario = ""
        p.edit().remove("uid").remove("pase").remove("refresco").remove("caduca").remove("usuario").apply()
        SincroP.olvidarVistos()
    }

    /** Pase de acceso vigente (se renueva solo; dura una hora). */
    fun token(force: Boolean = false): String {
        if (!force && pase.isNotBlank() && System.currentTimeMillis() < caduca - 60_000L) return pase
        if (refresco.isBlank()) throw Fail("No has vinculado tu cuenta.")
        buscarClave()
        val body = "grant_type=refresh_token&refresh_token=" + URLEncoder.encode(refresco, "UTF-8")
        val r = http("POST", tokenUrl(), body, form = true)
        if (r.code !in 200..299) throw Fail(authMsg(r.text))
        val o = JSONObject(r.text)
        pase = o.getString("id_token"); refresco = o.optString("refresh_token", refresco)
        caduca = System.currentTimeMillis() + (o.optString("expires_in", "3600").toLongOrNull() ?: 3600L) * 1000L
        p.edit().putString("pase", pase).putString("refresco", refresco).putLong("caduca", caduca).apply()
        return pase
    }

    // ---------------------------------------------------------------- Base de datos

    /**
     * Petición a la base de datos con tu sesión. Devuelve el texto, o null si no hay nada. Si el pase ha caducado se renueva y se repite una vez.
     */
    fun db(method: String, path: String, body: String?, query: String = ""): String? {
        fun go(force: Boolean): Res {
            val t = token(force)
            val sep = if (query.isBlank()) "" else "&$query"
            return http(method, dbUrl + path + ".json?auth=" + t + sep, body)
        }
        var r = go(false)
        if (r.code == 401) r = go(true)
        if (r.code == 401 || r.code == 403) throw Fail("Firebase no deja hacer eso. Revisa las reglas de la base de datos (en Azabache: Ajustes → Cuenta y pareja).")
        if (r.code == 404) throw Fail("No encuentro la base de datos. Revisa la dirección.")
        if (r.code !in 200..299) throw Fail("Firebase respondió ${r.code}.")
        if (r.text.trimStart().startsWith("<")) throw Fail("La dirección no es la de la base de datos (devuelve una página web).")
        val t = r.text.trim()
        return if (t.isEmpty() || t == "null") null else t
    }

    /** La dirección (con el pase puesto) para escuchar un sitio de tu cuenta en directo. */
    fun urlFlujo(path: String, renovar: Boolean): String? {
        if (!vinculada) return null
        return dbUrl + path + ".json?auth=" + token(renovar)
    }

    val raiz: String get() = "/users/$uid/marfil"
}
