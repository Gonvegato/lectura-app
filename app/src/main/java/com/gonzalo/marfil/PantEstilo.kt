package com.gonzalo.marfil

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import kotlinx.coroutines.launch
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

const val VERSION = "0.3.0"

/** «Ajustes»: Apariencia (como la de Atrio), cuenta de Atrio, actualizaciones y copias. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PantEstilo(alVolver: () -> Unit) {
    val ctx = LocalContext.current
    var mensaje by remember { mutableStateOf<String?>(null) }
    fun cambia(f: () -> Unit) { f(); Skin.guardar(ctx) }
    val exportar = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { u ->
        if (u != null) mensaje = runCatching { ctx.contentResolver.openOutputStream(u)!!.use { it.write(Biblioteca.aTexto().toByteArray()) }; "Copia guardada" }.getOrElse { "No se pudo guardar la copia" }
    }
    val importar = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u ->
        if (u != null) mensaje = runCatching { Biblioteca.leer(ctx.contentResolver.openInputStream(u)!!.use { it.readBytes().decodeToString() }, reemplazar = false); Biblioteca.guardar(); "Copia importada" }.getOrElse { "Ese archivo no es una copia de Marfil" }
    }
    Column(Modifier.fillMaxSize().background(Paper).verticalScroll(rememberScrollState()).statusBarsPadding().navigationBarsPadding().padding(horizontal = 20.dp)) {
        RevistaTopBar(alVolver, "Marfil")
        Antetitulo("Sección", Modifier.padding(top = 10.dp), color = Acento, conCuadro = false)
        FitTitle("Ajustes", 52.sp, 56.sp, letterSpacing = (-1.5).sp, modifier = Modifier.padding(top = 2.dp), unaLinea = true)
        Filete(3.dp)
        Spacer(Modifier.height(14.dp))

        SeccionApariencia()


        Spacer(Modifier.height(14.dp))
        Filete(2.dp)
        Spacer(Modifier.height(12.dp))
        Antetitulo("Cuenta de Atrio", Modifier.padding(bottom = 4.dp))
        SeccionCuenta()

        Spacer(Modifier.height(14.dp))
        Filete(2.dp)
        Spacer(Modifier.height(12.dp))
        Antetitulo("Actualizaciones", Modifier.padding(bottom = 4.dp))
        SeccionActualizaciones()

        Spacer(Modifier.height(14.dp))
        Filete(2.dp)
        Spacer(Modifier.height(12.dp))
        Antetitulo("Copias", Modifier.padding(bottom = 4.dp))
        MagLink("Guardar copia de la biblioteca", "Un archivo con tus libros y estantes (no incluye los archivos de los libros)") { exportar.launch("lectura-biblioteca.json") }
        Filete(1.dp, Line)
        MagLink("Importar copia", "Suma los libros de una copia a los que ya tienes") { importar.launch(arrayOf("application/json", "*/*")) }
        mensaje?.let { Text(it, color = Acento, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 15.sp, modifier = Modifier.padding(top = 4.dp)) }

        Spacer(Modifier.height(26.dp))
        Filete(3.dp)
        Spacer(Modifier.height(10.dp))
        Etiqueta("Marfil $VERSION")
        Text("Hermana de Atrio. Los comandos para abrir emuladores salen de ES-DE Frontend (licencia MIT, © Northwestern Software AB, Leon Styhre y Alec Lofquist). Las portadas, de libretro.", color = Muted, fontFamily = Body, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.padding(top = 4.dp))
        Spacer(Modifier.height(30.dp))
    }
}


/** Una casilla de texto con el estilo de la revista: etiqueta en versalitas, texto en cursiva sobre un filete. */
@Composable
fun CampoRevista(etiqueta: String, valor: String, pista: String, secreto: Boolean = false, alCambiar: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 6.dp)) {
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.Bottom) {
            Text(etiqueta.uppercase(), color = Muted, fontFamily = Body, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, modifier = Modifier.width(96.dp).padding(bottom = 3.dp))
            Box(Modifier.weight(1f)) {
                if (valor.isEmpty()) Text(pista, color = Muted, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 18.sp)
                BasicTextField(valor, alCambiar, singleLine = true, textStyle = TextStyle(color = Charcoal, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 18.sp),
                    cursorBrush = SolidColor(Acento), visualTransformation = if (secreto) PasswordVisualTransformation() else VisualTransformation.None,
                    keyboardOptions = KeyboardOptions(keyboardType = if (secreto) KeyboardType.Password else KeyboardType.Text, autoCorrect = false), modifier = Modifier.fillMaxWidth())
            }
        }
        Filete(2.dp)
    }
}

/** «Cuenta de Atrio»: entrar con tu usuario y contraseña de Atrio, ver cómo va la sincronización, sincronizar ahora o salir. */
@Composable
private fun SeccionCuenta() {
    val ctx = LocalContext.current
    val ambito = rememberCoroutineScope()
    var usuario by remember { mutableStateOf("") }
    var clave by remember { mutableStateOf("") }
    var ocupado by remember { mutableStateOf(false) }
    var aviso by remember { mutableStateOf<String?>(null) }
    if (!Cuenta.vinculada) {
        Text("Entra con la misma cuenta que usas en Atrio y tus juegos, horas, notas y diseños de ficha se sincronizan entre tus Marfiles. Las ROMs, los guardados y las imágenes no se suben (todavía).",
            color = Muted, fontFamily = Body, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.padding(top = 4.dp))
        CampoRevista("Usuario", usuario, "tu usuario de Atrio") { usuario = it }
        CampoRevista("Contraseña", clave, "••••••••", secreto = true) { clave = it }
        MagLink(if (ocupado) "Entrando…" else "Vincular cuenta", "Se guarda un pase de acceso, no tu contraseña") {
            if (!ocupado) {
                ocupado = true; aviso = null
                ambito.launch {
                    aviso = try { Cuenta.entrar(usuario, clave); clave = ""; SincroP.sincronizar(ctx, desdeCero = true); SincroP.escuchar(ctx); null }
                    catch (e: Cuenta.Fail) { e.message } catch (e: Exception) { "No se pudo entrar: ${e.message ?: "sin conexión"}" }
                    ocupado = false
                }
            }
        }
    } else {
        Etiqueta("Vinculada a", Modifier.padding(top = 8.dp))
        Text(Cuenta.usuario, color = Charcoal, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 22.sp)
        val cuando = SincroP.ultima.takeIf { it > 0 }?.let { java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT, java.text.DateFormat.SHORT).format(java.util.Date(it)) }
        Text(listOf(SincroP.estado.ifBlank { "Sin sincronizar todavía" }, cuando?.let { "última vez $it" }).filterNotNull().joinToString(" · "),
            color = Muted, fontFamily = Body, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
        Spacer(Modifier.height(6.dp))
        MagLink(if (ocupado) "Sincronizando…" else "Sincronizar ahora", "Baja lo de tus otros Marfiles y sube lo de este") {
            if (!ocupado) { ocupado = true; ambito.launch { SincroP.sincronizar(ctx, desdeCero = true); ocupado = false } }
        }
        Filete(1.dp, Line)
        MagLink("Salir de la cuenta", "Tus libros se quedan aquí; dejan de sincronizarse") { SincroP.parar(); Cuenta.salir() }
    }
    aviso?.let { Text(it, color = Acento, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 15.sp, modifier = Modifier.padding(top = 4.dp)) }
}


/** «Actualizaciones»: Marfil se actualiza sola desde la carpeta «versiones» de tu kDrive (la de Atrio), con tu token de kDrive. */
@Composable
private fun SeccionActualizaciones() {
    val ctx = LocalContext.current
    val ambito = rememberCoroutineScope()
    var conectado by remember { mutableStateOf(KDrive.on(ctx)) }
    var token by remember { mutableStateOf("") }
    var unidad by remember { mutableStateOf("") }
    var ocupado by remember { mutableStateOf(false) }
    var aviso by remember { mutableStateOf<String?>(null) }
    var auto by remember { mutableStateOf(Actualizador.activo(ctx)) }
    var sobre by remember { mutableStateOf(Actualizador.puedeReabrirSola(ctx)) }
    val estado = Actualizador.estado.ifBlank { Actualizador.estadoGuardado(ctx) }
    DisposableEffect(Unit) {
        val owner = androidx.lifecycle.ProcessLifecycleOwner.get()
        val ob = androidx.lifecycle.LifecycleEventObserver { _, ev -> if (ev == androidx.lifecycle.Lifecycle.Event.ON_RESUME) sobre = Actualizador.puedeReabrirSola(ctx) }
        owner.lifecycle.addObserver(ob); onDispose { owner.lifecycle.removeObserver(ob) }
    }
    Fila("Actualizar sola", "Mira la carpeta «versiones» de tu kDrive (Marfil-<versión>.apk) y se instala sin preguntar cuando no la usas.") {
        Interruptor(auto) { auto = it; Actualizador.ponActivo(ctx, it) }
    }
    if (!conectado) {
        Text("Pega tu token de kDrive y el número de tu kDrive (los mismos que en Atrio). Se guardan solo en este móvil.", color = Muted, fontFamily = Body, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.padding(top = 4.dp))
        CampoRevista("Token", token, "token de kDrive", secreto = true) { token = it }
        CampoRevista("kDrive", unidad, "número o dirección") { unidad = it }
        MagLink(if (ocupado) "Comprobando…" else "Conectar", "Comprueba el token y busca tu «Mi carpeta»") {
            if (!ocupado) {
                ocupado = true; aviso = null
                ambito.launch {
                    aviso = try { KDrive.save(ctx, token, unidad); KDrive.test(ctx); conectado = true; token = ""; withContext(Dispatchers.IO) { runCatching { Actualizador.comprueba(ctx, manual = true) } }; null }
                    catch (e: Exception) { KDrive.forget(ctx); e.message ?: "No se pudo conectar" }
                    ocupado = false
                }
            }
        }
    } else {
        val vers = runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull().orEmpty()
        Text("Versión instalada: $vers" + if (estado.isNotBlank()) " · última comprobación: $estado" else "", color = Muted, fontFamily = Body, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.padding(top = 4.dp, bottom = 4.dp))
        // Actualizar ya: mira, baja y instala sin esperar a que cierres la app (para cuando lo automático falla)
        MagLink(if (ocupado) "Actualizando…" else "Actualizar ahora", "Busca la última versión en tu kDrive y la instala ahora", accent = true) {
            if (!ocupado) { ocupado = true; aviso = null; ambito.launch { aviso = withContext(Dispatchers.IO) { Actualizador.actualizarAhora(ctx) }; ocupado = false } }
        }
        Filete(1.dp, Line)
        MagLink("Solo comprobar") {
            if (!ocupado) { ocupado = true; ambito.launch { withContext(Dispatchers.IO) { Actualizador.comprueba(ctx, manual = true) }; ocupado = false } }
        }
        Filete(1.dp, Line)
        MagLink(if (sobre) "Tras actualizar, Marfil se vuelve a abrir sola" else "Permitir que se reabra sola tras actualizar", if (sobre) null else "Hace falta el permiso «Mostrar sobre otras apps»; si no, te avisa para abrirla") {
            if (!sobre) runCatching { ctx.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, android.net.Uri.parse("package:${ctx.packageName}"))) }
        }
        Filete(1.dp, Line)
        MagLink("Desconectar kDrive", "Se olvida el token de este móvil") { KDrive.forget(ctx); conectado = false }
    }
    aviso?.let { Text(it, color = Acento, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 15.sp, modifier = Modifier.padding(top = 4.dp)) }
}

/** Si hay una versión nueva ya bajada que Android no deja instalar en silencio, se pregunta (o se lleva al ajuste de «instalar apps»). */
@Composable
fun AvisoActualizacion() {
    val ctx = LocalContext.current
    val n = Actualizador.nueva?.takeIf { !it.silenciosa } ?: return
    var luego by remember(n.nombre) { mutableStateOf(false) }
    if (luego) return
    AlertDialog(
        onDismissRequest = { luego = true }, containerColor = Paper, shape = esquina(4.dp),
        title = { Text("Marfil tiene una versión nueva", color = Charcoal, fontFamily = Display, fontWeight = FontWeight.Bold) },
        text = { Text(if (Actualizador.faltaPermiso) "Hay una versión nueva en tu kDrive. Para instalarla, Android tiene que dejar a Marfil instalar apps: te llevo a ese ajuste." else "Hay una versión nueva en tu kDrive, ya descargada. Android te pedirá confirmar la instalación.", color = Muted, fontFamily = Body) },
        confirmButton = { TextButton({
            if (Actualizador.faltaPermiso) { Actualizador.faltaPermiso = false; ctx.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, android.net.Uri.parse("package:${ctx.packageName}"))) }
            else runCatching { Actualizador.instala(ctx, n) }.onFailure { android.widget.Toast.makeText(ctx, "No se pudo instalar: ${it.message}", android.widget.Toast.LENGTH_LONG).show() }
        }) { Text(if (Actualizador.faltaPermiso) "PERMITIR" else "ACTUALIZAR AHORA", color = Acento, fontFamily = Body, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp) } },
        dismissButton = { TextButton({ luego = true }) { Text("LUEGO", color = Charcoal, fontFamily = Body, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp) } }
    )
}
