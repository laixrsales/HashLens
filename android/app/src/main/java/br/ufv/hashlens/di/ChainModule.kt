package br.ufv.hashlens.di

import br.ufv.hashlens.config.ChainConfig
import br.ufv.hashlens.data.chain.EthCall
import br.ufv.hashlens.data.chain.RegistryReader
import br.ufv.hashlens.data.chain.Web3jEthCall
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher

/** Rede, contrato e leitura da blockchain (sem carteira). */
@Module
@InstallIn(SingletonComponent::class)
object ChainModule {
    @Provides
    @Singleton
    fun chainConfig(): ChainConfig = ChainConfig.fromBuildConfig()

    @Provides
    @Singleton
    fun ethCall(config: ChainConfig): EthCall = Web3jEthCall(config.rpcUrl)

    @Provides
    @Singleton
    fun registryReader(
        config: ChainConfig,
        ethCall: EthCall,
        @IoDispatcher ioDispatcher: CoroutineDispatcher
    ): RegistryReader = RegistryReader(config, ethCall, ioDispatcher)
}
