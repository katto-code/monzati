package com.monzatti.app

import android.app.Application
import android.os.Looper
import android.util.Log
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class MonzattiApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        ignoreMlKitBackgroundErrors()
    }

    /**
     * ML Kit libera sus modelos en hilos propios. Si el recorte de fondo falló antes, al liberarlo lanza una
     * MediaPipeException en ese hilo y Android cerraba la app (pasaba al guardar una prenda). Esos errores no
     * afectan a la pantalla, así que se registran y se ignoran; cualquier otro error sigue su curso normal.
     */
    private fun ignoreMlKitBackgroundErrors() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            val fromMlKit = generateSequence(error) { it.cause }.any { e ->
                e.javaClass.name.startsWith("com.google.mediapipe") ||
                    e.stackTrace.any { it.className.startsWith("com.google.mlkit") }
            }
            if (fromMlKit && thread != Looper.getMainLooper().thread) {
                Log.w("Monzatti", "Error de ML Kit ignorado en ${thread.name}", error)
            } else {
                previous?.uncaughtException(thread, error)
            }
        }
    }
}
