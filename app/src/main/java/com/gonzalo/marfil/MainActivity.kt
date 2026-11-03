package com.gonzalo.marfil

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

class MainActivity : ComponentActivity() {
    /** El libro que pide abrir un widget (se consume al abrirlo). */
    private var fichaPedida by androidx.compose.runtime.mutableStateOf<String?>(null)

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent); fichaPedida = intent.getStringExtra("ficha")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        iniciar(this)
        Actualizador.init(this)
        fichaPedida = intent?.getStringExtra("ficha")
        setContent {
            MarfilTheme {
                // Iconos de la barra de estado claros u oscuros según el fondo
                SideEffect {
                    val oscuro = Skin.tones.darkBg
                    enableEdgeToEdge(
                        statusBarStyle = if (oscuro) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT) else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
                        navigationBarStyle = if (oscuro) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT) else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
                    )
                }
                MarfilApp(fichaPedida) { fichaPedida = null }
            }
        }
    }

    /** Al volver a la app: colores de Atrio al día y la cuenta en directo. */
    override fun onStart() {
        super.onStart()
        // Los colores de Atrio pueden haber cambiado mientras estabas en ella: se vuelven a leer
        val c = applicationContext
        kotlin.concurrent.thread { TemaAtrio.refrescar(c) }
        // Tu cuenta de Atrio, en directo mientras Marfil se ve
        SincroP.escuchar(c)
    }

    override fun onStop() { super.onStop(); SincroP.parar() }
}
