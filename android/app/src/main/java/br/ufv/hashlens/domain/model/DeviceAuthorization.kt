package br.ufv.hashlens.domain.model

/**
 * Autorização de vínculo devolvida pelo atestador (`attester-api.md` §1): vale para a carteira, a
 * chave e a cadeia enviadas, neste contrato, até [deadline] (segundos Unix).
 *
 * @property attestationHash `keccak256(abi.encode(chain))`, o que o contrato vai gravar.
 * @property signature assinatura EIP-712 `r ‖ s ‖ v` do atestador (65 bytes).
 */
data class DeviceAuthorization(val deadline: Long, val attestationHash: ByteArray, val signature: ByteArray) {
    override fun equals(other: Any?): Boolean = this === other ||
        other is DeviceAuthorization &&
        deadline == other.deadline &&
        attestationHash.contentEquals(other.attestationHash) &&
        signature.contentEquals(other.signature)

    override fun hashCode(): Int =
        listOf(deadline, attestationHash.contentHashCode(), signature.contentHashCode()).hashCode()
}
