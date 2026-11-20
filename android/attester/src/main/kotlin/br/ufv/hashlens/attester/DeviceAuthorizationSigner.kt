package br.ufv.hashlens.attester

import br.ufv.hashlens.attestation.AttestationChallenge
import br.ufv.hashlens.attestation.AttestationHash
import java.math.BigInteger
import org.web3j.crypto.Credentials
import org.web3j.crypto.Hash
import org.web3j.crypto.Sign

/**
 * Autorização de vínculo EIP-712 (`contracts/device-authorization.md`, research R5): o atestador
 * assina `DeviceAuthorization(wallet, keccak256(publicKey), keccak256(abi.encode(chain)), deadline)`
 * no domínio `{"HashLens ImageRegistry", "2", chainId, contract}`, e o contrato confere com
 * `ecrecover` contra o endereço `attester` fixado no deploy.
 *
 * @param privateKey chave secp256k1 do atestador, hex com ou sem `0x` (FR-019: só por ambiente).
 */
class DeviceAuthorizationSigner(privateKey: String, chainId: Long, contract: String) {
    private val credentials = Credentials.create(privateKey.removePrefix("0x"))

    /** Endereço do atestador em hex minúsculo com `0x`. */
    val address: String = credentials.address.lowercase()

    val domainSeparator: ByteArray = keccak(
        DOMAIN_TYPEHASH,
        keccak("HashLens ImageRegistry".toByteArray()),
        keccak("2".toByteArray()),
        uint256(BigInteger.valueOf(chainId)),
        address(contract)
    )

    fun structHash(wallet: String, publicKey: ByteArray, chain: List<ByteArray>, deadline: Long): ByteArray = keccak(
        AUTHORIZATION_TYPEHASH,
        address(wallet),
        keccak(publicKey),
        AttestationHash.of(chain),
        uint256(BigInteger.valueOf(deadline))
    )

    fun digest(wallet: String, publicKey: ByteArray, chain: List<ByteArray>, deadline: Long): ByteArray =
        keccak(EIP712_PREFIX, domainSeparator, structHash(wallet, publicKey, chain, deadline))

    /**
     * Assinatura `r ‖ s ‖ v` (65 bytes, `v ∈ {27, 28}`). O web3j assina com nonce determinístico
     * (RFC 6979) e já devolve `s ≤ n/2`, a única forma que o contrato aceita.
     */
    fun sign(wallet: String, publicKey: ByteArray, chain: List<ByteArray>, deadline: Long): ByteArray {
        val signature = Sign.signMessage(digest(wallet, publicKey, chain, deadline), credentials.ecKeyPair, false)
        return signature.r + signature.s + signature.v
    }

    private companion object {
        const val WORD = 32
        const val HEX_DIGITS_PER_BYTE = 2
        const val HEX_RADIX = 16

        /** `"\x19\x01"` do EIP-712. */
        val EIP712_PREFIX = byteArrayOf(0x19, 0x01)

        val DOMAIN_TYPEHASH: ByteArray =
            keccak("EIP712Domain(string name,string version,uint256 chainId,address verifyingContract)".toByteArray())
        val AUTHORIZATION_TYPEHASH: ByteArray = keccak(
            "DeviceAuthorization(address wallet,bytes32 publicKeyHash,bytes32 attestationHash,uint64 deadline)"
                .toByteArray()
        )

        fun keccak(vararg parts: ByteArray): ByteArray = Hash.sha3(parts.fold(ByteArray(0)) { acc, part -> acc + part })

        fun uint256(value: BigInteger): ByteArray {
            val magnitude = value.toByteArray().dropWhile { it == 0.toByte() }.toByteArray()
            return ByteArray(WORD - magnitude.size) + magnitude
        }

        /** Endereço como palavra ABI (12 bytes zero + 20 bytes). */
        fun address(value: String): ByteArray {
            val normalized = AttestationChallenge.normalizeAddress(value).removePrefix("0x")
            val bytes = normalized.chunked(HEX_DIGITS_PER_BYTE).map { it.toInt(HEX_RADIX).toByte() }.toByteArray()
            return ByteArray(WORD - bytes.size) + bytes
        }
    }
}
