package com.monzatti.app.data

import com.monzatti.app.data.local.StylePreferences
import com.monzatti.app.domain.model.StylePalette
import com.monzatti.app.domain.repository.AuthRepository
import com.monzatti.app.domain.repository.ProfileRepository
import com.monzatti.app.domain.repository.StyleRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

/** Siempre guarda en el dispositivo; si hay sesión, también en el perfil (Supabase o demo). */
@Singleton
class StyleRepositoryImpl @Inject constructor(
    private val preferences: StylePreferences,
    private val auth: AuthRepository,
    private val profiles: ProfileRepository
) : StyleRepository {

    override val style: Flow<StylePalette> = preferences.style

    override suspend fun setStyle(style: StylePalette) {
        preferences.save(style)
        auth.currentUser.value?.let { user -> profiles.updateStyle(user.id, style) }
    }
}
