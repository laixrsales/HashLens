package br.ufv.hashlens.testing

import br.ufv.hashlens.domain.model.Device
import java.time.Instant

/** [Device] para testes que não dependem dos campos de atestação da v2 (020 data-model §3). */
object TestDevices {
    val REGISTERED_AT: Instant = Instant.parse("2026-10-06T21:00:00Z")

    fun device(
        id: Int,
        owner: String,
        publicKey: ByteArray,
        registeredAt: Instant = REGISTERED_AT,
        attestationHash: ByteArray = ByteArray(32),
        registeredBlock: Long = 1
    ) = Device(id, owner, publicKey, registeredAt, attestationHash, registeredBlock)
}
