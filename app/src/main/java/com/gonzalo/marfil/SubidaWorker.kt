package com.gonzalo.marfil

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/**
 * Lo que no llegó a subirse (sin red al salir del lector, o Android congeló la app antes de acabar) lo sube un trabajo de fondo en
 * cuanto hay red, aunque la app siga congelada. No sube nada que ya esté subido (la biblioteca y lo leído de cada libro llevan su
 * huella): si la app lo subió a tiempo, aquí no hay nada que hacer.
 */
class SubidaWorker(ctx: Context, p: WorkerParameters) : CoroutineWorker(ctx, p) {
    override suspend fun doWork(): Result {
        val c = applicationContext
        iniciar(c)
        if (!Cuenta.vinculada) return Result.success()
        var ok = runCatching { SincroP.subir(c) }.getOrDefault(false)
        for (l in Biblioteca.libros.toList()) if (!runCatching { SincroP.subirLectura(c, l.id) }.getOrDefault(false)) ok = false
        return if (ok || runAttemptCount >= 6) Result.success() else Result.retry()
    }

    companion object {
        /** Cuando algo queda por subir: una sola vez (si ya está encolado, se deja), con red, y reintentos cada vez más espaciados. */
        fun encolar(c: Context) = runCatching {
            val req = OneTimeWorkRequestBuilder<SubidaWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(c.applicationContext).enqueueUniqueWork("subida", ExistingWorkPolicy.KEEP, req)
        }
    }
}
