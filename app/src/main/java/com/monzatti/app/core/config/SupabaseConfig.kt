package com.monzatti.app.core.config

import android.app.Activity
import android.content.Context
import android.content.Intent
import kotlin.system.exitProcess

/**
 * Claves de Supabase leídas de local.properties vía BuildConfig.
 * Sin claves, o con el modo demo forzado, la app usa los datos de ejemplo en memoria.
 */
data class SupabaseConfig(val url: String, val anonKey: String, val demoForced: Boolean = false) {
    val hasKeys: Boolean get() = url.isNotBlank() && anonKey.isNotBlank()
    val isConfigured: Boolean get() = hasKeys && !demoForced
}

/**
 * Interruptor de "modo demo" para la presentación: si falla internet o Supabase, la app sigue funcionando
 * con datos de ejemplo. Se lee al arrancar (antes de crear los repositorios), por eso cambiarlo reinicia la app.
 */
object DemoMode {
    private const val PREFS = "monzatti_demo"
    private const val KEY = "forced"

    fun isForced(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY, false)

    fun setAndRestart(activity: Activity, forced: Boolean) {
        activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY, forced).commit()
        val intent = activity.packageManager.getLaunchIntentForPackage(activity.packageName)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        activity.startActivity(intent)
        activity.finishAffinity()
        exitProcess(0)
    }
}
