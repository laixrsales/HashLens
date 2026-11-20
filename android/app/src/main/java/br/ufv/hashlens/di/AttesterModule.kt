package br.ufv.hashlens.di

import br.ufv.hashlens.config.ChainConfig
import br.ufv.hashlens.data.attester.AttesterClient
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.Duration
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import okhttp3.OkHttpClient

/** Cliente do atestador do deploy (020 research R9). */
@Module
@InstallIn(SingletonComponent::class)
object AttesterModule {
    /** A validação no atestador busca a lista de revogação do Google: até 15 s por chamada. */
    private val CONNECT_TIMEOUT: Duration = Duration.ofSeconds(10)
    private val CALL_TIMEOUT: Duration = Duration.ofSeconds(30)

    @Provides
    @Singleton
    fun attesterClient(config: ChainConfig, @IoDispatcher ioDispatcher: CoroutineDispatcher): AttesterClient {
        val http = OkHttpClient.Builder()
            .connectTimeout(CONNECT_TIMEOUT)
            .callTimeout(CALL_TIMEOUT)
            .build()
        return AttesterClient(config.attesterUrl, http, ioDispatcher)
    }
}
