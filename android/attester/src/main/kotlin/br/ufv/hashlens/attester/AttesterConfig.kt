package br.ufv.hashlens.attester

import br.ufv.hashlens.attestation.AttestationChallenge
import br.ufv.hashlens.attestation.AttestationPolicy
import java.math.BigInteger
import java.time.Instant

/**
 * Configuração do atestador, só por variáveis de ambiente (`contracts/attester-api.md` §3, FR-019).
 *
 * @property allowedCertDigests SHA-256 (hex minúsculo, sem `:`) dos certificados do APK aceitos:
 *   desenvolvimento = {debug, release}; avaliação = {release} (research R8).
 */
data class AttesterConfig(
    val privateKey: String,
    val chainId: Long,
    val contract: String,
    val allowedCertDigests: Set<String>,
    val port: Int = DEFAULT_PORT
) {
    // Nunca imprimir a chave
    override fun toString(): String =
        "AttesterConfig(chainId=$chainId, contract=$contract, allowedCertDigests=$allowedCertDigests, port=$port)"

    companion object {
        const val DEFAULT_PORT = 8080

        /** Lê o ambiente; lança [IllegalArgumentException] nomeando a variável que falta ou é inválida. */
        fun fromEnv(env: Map<String, String> = System.getenv()): AttesterConfig {
            fun required(name: String): String =
                env[name]?.trim()?.takeIf { it.isNotEmpty() } ?: throw IllegalArgumentException("defina $name")

            val digests = required("ATTESTER_ALLOWED_CERT_DIGESTS").split(",")
                .map { it.trim().lowercase().removePrefix("0x").replace(":", "") }
                .filter { it.isNotEmpty() }
                .toSet()
            require(digests.isNotEmpty()) { "ATTESTER_ALLOWED_CERT_DIGESTS vazio" }
            val contract = required("ATTESTER_CONTRACT")
            require(runCatching { AttestationChallenge.normalizeAddress(contract) }.isSuccess) {
                "ATTESTER_CONTRACT inválido"
            }
            // Validada aqui para que um valor malformado não chegue ao web3j, cuja exceção repete o texto
            val privateKey = required("ATTESTER_PRIVATE_KEY")
            require(isValidPrivateKey(privateKey)) {
                "ATTESTER_PRIVATE_KEY inválida (64 dígitos hexadecimais, com ou sem 0x)"
            }
            return AttesterConfig(
                privateKey = privateKey,
                chainId =
                required("ATTESTER_CHAIN_ID").toLongOrNull()
                    ?: throw IllegalArgumentException("ATTESTER_CHAIN_ID inválido"),
                contract = contract,
                allowedCertDigests = digests,
                port = env["ATTESTER_PORT"]?.toIntOrNull() ?: DEFAULT_PORT
            )
        }

        private const val HEX_RADIX = 16
        private val PRIVATE_KEY_HEX = Regex("[0-9a-fA-F]{64}")
        private const val SECP256K1_N_HEX = "FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEBAAEDCE6AF48A03BBFD25E8CD0364141"
        private val SECP256K1_N = BigInteger(SECP256K1_N_HEX, HEX_RADIX)

        /** 32 bytes em hex, entre 1 e a ordem da curva secp256k1 menos 1. */
        private fun isValidPrivateKey(value: String): Boolean {
            val hex = value.removePrefix("0x")
            if (!PRIVATE_KEY_HEX.matches(hex)) return false
            val key = BigInteger(hex, HEX_RADIX)
            return key.signum() > 0 && key < SECP256K1_N
        }
    }
}

/** O que a rota usa; no [main], a configuração do ambiente; nos testes, dublês. */
class AttesterDependencies(
    val config: AttesterConfig,
    val signer: DeviceAuthorizationSigner,
    val policy: AttestationPolicy,
    val clock: () -> Instant = Instant::now,
    /** Uma linha por requisição: status, motivo e carteira abreviada, nunca cadeia nem chave (FR-002). */
    val log: (String) -> Unit = ::println
)
