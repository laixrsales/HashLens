package br.ufv.hashlens.di

import android.content.Context
import br.ufv.hashlens.config.ChainConfig
import br.ufv.hashlens.data.attestation.DeviceOriginVerifier
import br.ufv.hashlens.data.attestation.RevocationRepository
import br.ufv.hashlens.data.chain.RegistryReader
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import java.time.Duration
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import okhttp3.Cache
import okhttp3.OkHttpClient

/** Revalidação da origem atestada na verificação (020 research R12). */
@Module
@InstallIn(SingletonComponent::class)
object AttestationModule {
    private val CONNECT_TIMEOUT: Duration = Duration.ofSeconds(10)
    private val CALL_TIMEOUT: Duration = Duration.ofSeconds(20)

    /** A lista do Google tem algumas centenas de KB. */
    private const val REVOCATION_CACHE_BYTES = 5L * 1024 * 1024

    @Provides
    @Singleton
    fun revocationRepository(
        @ApplicationContext context: Context,
        @IoDispatcher ioDispatcher: CoroutineDispatcher
    ): RevocationRepository {
        val http = OkHttpClient.Builder()
            .cache(Cache(File(context.cacheDir, "revocation"), REVOCATION_CACHE_BYTES))
            .connectTimeout(CONNECT_TIMEOUT)
            .callTimeout(CALL_TIMEOUT)
            .build()
        return RevocationRepository(http, ioDispatcher)
    }

    /** Singleton: a validação de cada aparelho fica guardada enquanto o app está aberto. */
    @Provides
    @Singleton
    fun deviceOriginVerifier(
        reader: RegistryReader,
        config: ChainConfig,
        revocation: RevocationRepository
    ): DeviceOriginVerifier = DeviceOriginVerifier(reader, config, revocation)
}
