package br.ufv.hashlens.config

import br.ufv.hashlens.BuildConfig
import java.time.Duration

/**
 * Parâmetros da rede e do contrato `ImageRegistry`.
 *
 * Os valores de produção vêm de `android/local.properties` via [BuildConfig] ([fromBuildConfig]);
 * os testes constroem instâncias próprias (por exemplo, com outro [pendingTimeout]).
 */
data class ChainConfig(
    val chainId: Long = SEPOLIA_CHAIN_ID,
    val rpcUrl: String,
    val registryAddress: String,
    val reownProjectId: String,
    val explorerUrl: String = SEPOLIA_EXPLORER_URL,
    /** Onde pedir saldo de teste gratuito (FR-003). */
    val faucetUrl: String = SEPOLIA_FAUCET_URL,
    /** Bloco do deploy do contrato: início da busca de eventos (`eth_getLogs`); 0 busca desde a origem. */
    val deployBlock: Long = 0,
    /** Prazo sem recibo até a transação pendente passar a `FAILED(TIMEOUT)` (research R23). */
    val pendingTimeout: Duration = Duration.ofMinutes(DEFAULT_PENDING_TIMEOUT_MINUTES)
) {
    fun transactionUrl(txHash: String): String = "$explorerUrl/tx/$txHash"

    fun addressUrl(address: String): String = "$explorerUrl/address/$address"

    companion object {
        const val SEPOLIA_CHAIN_ID = 11_155_111L
        const val SEPOLIA_EXPLORER_URL = "https://sepolia.etherscan.io"
        const val SEPOLIA_FAUCET_URL = "https://cloud.google.com/application/web3/faucet/ethereum/sepolia"
        const val DEFAULT_PENDING_TIMEOUT_MINUTES = 30L

        fun fromBuildConfig(): ChainConfig = ChainConfig(
            rpcUrl = BuildConfig.SEPOLIA_RPC_URL,
            registryAddress = BuildConfig.REGISTRY_ADDRESS,
            reownProjectId = BuildConfig.REOWN_PROJECT_ID,
            deployBlock = BuildConfig.REGISTRY_DEPLOY_BLOCK,
            pendingTimeout = Duration.ofMinutes(BuildConfig.PENDING_TIMEOUT_MINUTES.toLong())
        )
    }
}
