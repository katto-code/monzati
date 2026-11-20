package com.monzatti.app.core.config

/** Claves de Supabase leídas de local.properties vía BuildConfig. Vacías = modo demo con datos de ejemplo. */
data class SupabaseConfig(val url: String, val anonKey: String) {
    val isConfigured: Boolean get() = url.isNotBlank() && anonKey.isNotBlank()
}
