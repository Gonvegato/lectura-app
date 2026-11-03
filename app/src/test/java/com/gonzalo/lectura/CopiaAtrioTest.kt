package com.gonzalo.lectura

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.AfterClass
import org.junit.Assert.*
import org.junit.Assume
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Copiar la apariencia que Atrio ha subido a la cuenta, contra el Firebase de mentira de Atrio. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CopiaAtrioTest {
    companion object {
        private var proc: Process? = null
        private val puerto = 9800 + (System.nanoTime() % 150).toInt().let { if (it < 0) -it else it }
        private val js = File("/home/user/Atrio-Suite-2.0/herramientas/fakefirebase.js")
        @BeforeClass @JvmStatic fun arranca() {
            Assume.assumeTrue("falta fakefirebase.js", js.exists())
            proc = ProcessBuilder("node", js.path, puerto.toString()).redirectErrorStream(true).start()
            repeat(60) { try { URL("http://127.0.0.1:$puerto/__dump").openConnection().apply { connectTimeout = 300 }.getInputStream().close(); return } catch (e: java.io.IOException) { if (e.message?.contains("Server returned") == true) return; Thread.sleep(100) } }
            fail("no arranca")
        }
        @AfterClass @JvmStatic fun para() { proc?.destroy() }
        fun http(m: String, p: String, body: String? = null): String {
            val c = URL("http://127.0.0.1:$puerto$p").openConnection() as HttpURLConnection
            if (m == "PUT") { c.requestMethod = "POST"; c.setRequestProperty("X-HTTP-Method-Override", m) } else c.requestMethod = m
            if (body != null) { c.doOutput = true; c.outputStream.use { it.write(body.toByteArray()) } }
            return (if (c.responseCode in 200..299) c.inputStream else c.errorStream).bufferedReader().readText()
        }
    }

    private val ctx get() = ApplicationProvider.getApplicationContext<android.content.Context>()

    @org.junit.After fun limpia() {   // (el tema es un objeto global: que no se lo lleven los demás tests)
        Skin.fondoId = ""; Skin.accentId = "paleta"; Skin.fondoPropio = 0L; Skin.tintaPropia = 0L; Skin.acentoPropio = 0L; Skin.escala = 100
        Skin.display = "Atrio"; Skin.body = "Atrio"; Modo.eleccion = 0; Skin.origenAtrio = true; Skin.invertido = true; Skin.ajustesAt = 0L
    }

    private fun nodoAtrio(at: Long, skin: JSONObject) = JSONObject().put("name", "Tablet").put("device", "android").put("updated", JSONObject().put(".sv", "timestamp"))
        .put("data", JSONObject().put("ajustes", JSONObject().put("at", at).put("skin", skin)).toString()).toString()

    @Test fun leeYCopiaLoQueAtrioTiene() {
        Skin.cargar(ctx)   // (la primera carga marca las preferencias como ya estrenadas)
        Cuenta.dbUrl = "http://127.0.0.1:$puerto"; Cuenta.authBase = Cuenta.dbUrl
        val sign = JSONObject(http("POST", "/v1/accounts:signUp?key=K", """{"email":"gon@ronron.example.com","password":"secreto1","returnSecureToken":true}"""))
        val uid = sign.getString("localId"); val tok = sign.getString("idToken")
        http("PUT", "/config/webKey.json?auth=$tok", "\"K\"")
        // Dos dispositivos de Atrio: el más reciente (por la hora de sus ajustes) manda
        val viejo = JSONObject().put("theme31", 1).put("skin_fondo", "").put("skin_accent_id", "azul")
        val nuevo = JSONObject().put("theme31", 2).put("skin_fondo", "granate").put("skin_accent_id", "verde").put("skin_fondo_propio", 0xFF101820L)
            .put("skin_tinta_propia", 0).put("skin_acento_propio", 0xFF06D6A0L).put("skin_letra_titulos", "Playfair Display").put("skin_letra_texto", "").put("skin_escala", 112)
        http("PUT", "/users/$uid/dev/viejo.json?auth=$tok", nodoAtrio(10, viejo))
        http("PUT", "/users/$uid/dev/nuevo.json?auth=$tok", nodoAtrio(20, nuevo))
        http("PUT", "/users/$uid/stamps.json?auth=$tok", """{"viejo":1000,"nuevo":900}""")   // (el sello más nuevo es del otro: manda la hora de los ajustes)
        ctx.getSharedPreferences("lectura_cuenta", 0).edit().clear().apply(); Cuenta.cargar(ctx)
        runBlocking { Cuenta.entrar("gon", "secreto1") }

        val a = CopiaAtrio.leer()!!
        assertEquals(20L, a.at)
        val filas = CopiaAtrio.filas(a).associateBy { it.id }
        assertEquals("Oscuro", filas["tema"]!!.valor); assertEquals("Granate", filas["fondo"]!!.valor); assertEquals("Verde", filas["acento"]!!.valor)
        assertTrue(filas["c_fondo"]!!.valor, filas["c_fondo"]!!.valor.contains("#101820")); assertTrue(filas["c_acento"]!!.valor, filas["c_acento"]!!.valor.contains("#06D6A0"))
        assertTrue(filas["letras"]!!.valor.contains("Playfair Display")); assertEquals("Grande", filas["tamano"]!!.valor)

        // Copiar solo lo marcado: el tema y las letras; lo demás de Lectura queda como estaba
        Skin.fondoId = ""; Skin.accentId = "paleta"; Skin.fondoPropio = 0L; Skin.escala = 100; Skin.origenAtrio = true; Modo.eleccion = 0
        CopiaAtrio.aplicar(ctx, a, setOf("tema", "letras"))
        assertEquals(2, Modo.eleccion); assertEquals("Playfair Display", Skin.display); assertEquals("Atrio", Skin.body)
        assertEquals("", Skin.fondoId); assertEquals(100, Skin.escala); assertEquals(0L, Skin.fondoPropio)
        // y ahora todo
        CopiaAtrio.aplicar(ctx, a, filas.keys)
        assertEquals("granate", Skin.fondoId); assertEquals("verde", Skin.accentId); assertEquals(0xFF101820L, Skin.fondoPropio); assertEquals(0xFF06D6A0L, Skin.acentoPropio); assertEquals(112, Skin.escala)
        assertFalse("ya no sigue a Atrio en directo: tiene sus propios colores, iguales", Skin.origenAtrio)
        // Se guarda de verdad
        Skin.fondoId = ""; Skin.cargar(ctx); assertEquals("granate", Skin.fondoId)
        Cuenta.salir()
    }
}
