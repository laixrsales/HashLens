package br.ufv.hashlens.di

import br.ufv.hashlens.config.ChainConfig
import br.ufv.hashlens.data.chain.ChainRpc
import br.ufv.hashlens.data.chain.EthCall
import br.ufv.hashlens.data.chain.RegistryReader
import br.ufv.hashlens.data.chain.Web3jChainRpc
import br.ufv.hashlens.data.chain.Web3jEthCall
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import org.web3j.protocol.Web3j
import org.web3j.protocol.http.HttpService

/** Rede, contrato, leitura da blockchain e acompanhamento de transações (sem carteira). */
@Module
@InstallIn(SingletonComponent::class)
object ChainModule {
    @Provides
    @Singleton
    fun chainConfig(): ChainConfig = ChainConfig.fromBuildConfig()

    @Provides
    @Singleton
    fun web3j(config: ChainConfig): Web3j = Web3j.build(HttpService(config.rpcUrl))

    @Provides
    @Singleton
    fun ethCall(web3j: Web3j): EthCall = Web3jEthCall(web3j)

    @Provides
    @Singleton
    fun chainRpc(web3j: Web3j): ChainRpc = Web3jChainRpc(web3j)

    @Provides
    @Singleton
    fun registryReader(
        config: ChainConfig,
        ethCall: EthCall,
        @IoDispatcher ioDispatcher: CoroutineDispatcher
    ): RegistryReader = RegistryReader(config, ethCall, ioDispatcher)
}
