package com.monzatti.app.di

import com.monzatti.app.BuildConfig
import android.content.Context
import com.monzatti.app.core.config.DemoMode
import com.monzatti.app.core.config.SupabaseConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import com.monzatti.app.data.StyleRepositoryImpl
import com.monzatti.app.data.fake.FakeAuthRepository
import com.monzatti.app.data.fake.FakeClosetRepository
import com.monzatti.app.data.fake.FakeMirrorRepository
import com.monzatti.app.data.update.NoUpdatesRepository
import com.monzatti.app.data.update.SupabaseUpdateRepository
import com.monzatti.app.domain.update.UpdateRepository
import com.monzatti.app.data.fake.FakeCartRepository
import com.monzatti.app.data.supabase.SupabaseCartRepository
import com.monzatti.app.domain.shop.CartRepository
import com.monzatti.app.data.supabase.SupabaseMirrorRepository
import com.monzatti.app.domain.repository.MirrorRepository
import com.monzatti.app.data.fake.FakeProductRepository
import com.monzatti.app.data.fake.FakeProfileRepository
import com.monzatti.app.data.supabase.SupabaseAuthRepository
import com.monzatti.app.data.supabase.SupabaseClosetRepository
import com.monzatti.app.data.supabase.SupabaseProductRepository
import com.monzatti.app.data.supabase.SupabaseProfileRepository
import com.monzatti.app.domain.repository.AuthRepository
import com.monzatti.app.domain.repository.ClosetRepository
import com.monzatti.app.domain.repository.ProductRepository
import com.monzatti.app.domain.repository.ProfileRepository
import com.monzatti.app.domain.repository.StyleRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.storage.Storage
import javax.inject.Provider
import javax.inject.Singleton

/**
 * Elige la implementación de cada repositorio: Supabase si hay claves en local.properties, demo si no.
 * Los Provider evitan crear el cliente de Supabase cuando no hay claves.
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideSupabaseConfig(@ApplicationContext context: Context): SupabaseConfig =
        SupabaseConfig(BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_ANON_KEY, DemoMode.isForced(context))

    @Provides
    @Singleton
    fun provideSupabaseClient(config: SupabaseConfig): SupabaseClient =
        createSupabaseClient(supabaseUrl = config.url, supabaseKey = config.anonKey) {
            install(Auth)
            install(Postgrest)
            install(Realtime)
            install(Storage)
        }

    @Provides
    @Singleton
    fun provideAuthRepository(
        config: SupabaseConfig,
        fake: Provider<FakeAuthRepository>,
        supabase: Provider<SupabaseAuthRepository>
    ): AuthRepository = if (config.isConfigured) supabase.get() else fake.get()

    @Provides
    @Singleton
    fun provideProfileRepository(
        config: SupabaseConfig,
        fake: Provider<FakeProfileRepository>,
        supabase: Provider<SupabaseProfileRepository>
    ): ProfileRepository = if (config.isConfigured) supabase.get() else fake.get()

    @Provides
    @Singleton
    fun provideClosetRepository(
        config: SupabaseConfig,
        fake: Provider<FakeClosetRepository>,
        supabase: Provider<SupabaseClosetRepository>
    ): ClosetRepository = if (config.isConfigured) supabase.get() else fake.get()

    @Provides
    @Singleton
    fun provideProductRepository(
        config: SupabaseConfig,
        fake: Provider<FakeProductRepository>,
        supabase: Provider<SupabaseProductRepository>
    ): ProductRepository = if (config.isConfigured) supabase.get() else fake.get()

    @Provides
    @Singleton
    fun provideCartRepository(
        config: SupabaseConfig,
        fake: Provider<FakeCartRepository>,
        supabase: Provider<SupabaseCartRepository>
    ): CartRepository = if (config.isConfigured) supabase.get() else fake.get()

    @Provides
    @Singleton
    fun provideUpdateRepository(
        config: SupabaseConfig,
        none: Provider<NoUpdatesRepository>,
        supabase: Provider<SupabaseUpdateRepository>
    ): UpdateRepository = if (config.isConfigured) supabase.get() else none.get()

    @Provides
    @Singleton
    fun provideMirrorRepository(
        config: SupabaseConfig,
        fake: Provider<FakeMirrorRepository>,
        supabase: Provider<SupabaseMirrorRepository>
    ): MirrorRepository = if (config.isConfigured) supabase.get() else fake.get()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class BindingsModule {
    @Binds
    abstract fun bindStyleRepository(impl: StyleRepositoryImpl): StyleRepository
}
