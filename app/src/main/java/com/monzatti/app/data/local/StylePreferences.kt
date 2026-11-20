package com.monzatti.app.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.monzatti.app.domain.model.StylePalette
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "monzatti_prefs")

/** Guarda en el dispositivo el último estilo, para pintar la app desde el arranque. */
@Singleton
class StylePreferences @Inject constructor(@ApplicationContext context: Context) {

    private val store = context.dataStore
    private val styleKey = stringPreferencesKey("style_palette")

    val style: Flow<StylePalette> = store.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { StylePalette.fromKey(it[styleKey]) }

    suspend fun save(style: StylePalette) {
        store.edit { it[styleKey] = style.key }
    }
}
