package com.gonzalo.marfil

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/** El objetivo de lectura tiene un solo dueño (Marfil): manda el cambio más reciente; si no hay nada, se trae una vez lo de Azabache. */
class ObjetivosTest {
    private fun v(dia: Int, ano: Int, mod: Long) = Objetivos.Valor(dia, ano, 2026, mod)

    @Test fun mandaElMasReciente() {
        assertEquals(Objetivos.Paso.ADOPTAR, Objetivos.decidir(v(30, 12, 5), v(45, 20, 9), true))
        assertEquals(Objetivos.Paso.SUBIR, Objetivos.decidir(v(30, 12, 9), v(45, 20, 5), true))
        assertEquals(Objetivos.Paso.NADA, Objetivos.decidir(v(30, 12, 9), v(30, 12, 9), true))
    }

    @Test fun sinNadaEnLaCuenta() {
        assertEquals(Objetivos.Paso.SUBIR, Objetivos.decidir(v(30, 0, 9), null, false))
        // Nunca tocado aquí ni en la cuenta: se mira una vez lo de Azabache
        assertEquals(Objetivos.Paso.IMPORTAR, Objetivos.decidir(v(0, 0, 0), null, false))
        assertEquals(Objetivos.Paso.NADA, Objetivos.decidir(v(0, 0, 0), null, true))
        // Si lo quitaste aquí a propósito (mod > 0), se sube el «sin objetivo»
        assertEquals(Objetivos.Paso.SUBIR, Objetivos.decidir(v(0, 0, 7), null, false))
    }

    @Test fun deAzabache() {
        val o = JSONObject().put("goals", JSONObject().put("g", 10).put("b", 24)).put("ajustes", JSONObject().put("skin", JSONObject().put("meta_lectura", 40)))
        val r = Objetivos.deDatosAzabache(o, ahora = 123L)!!
        assertEquals(40, r.dia); assertEquals(24, r.ano); assertEquals(123L, r.mod)
        assertNull(Objetivos.deDatosAzabache(JSONObject().put("goals", JSONObject().put("g", 5))))
    }

    @Test fun jsonDeIdaYVuelta() {
        val x = v(30, 12, 99)
        assertEquals(x, Objetivos.Valor.deJson(x.aJson()))
        assertNull(Objetivos.Valor.deJson(JSONObject()))
    }
}
