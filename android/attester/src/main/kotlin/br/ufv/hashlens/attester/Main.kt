package br.ufv.hashlens.attester

import br.ufv.hashlens.attestation.AttestationPolicy
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import kotlin.system.exitProcess

/** `./gradlew :attester:run`: valida o ambiente e sobe o servidor HTTP (quickstart §3). */
fun main() {
    val config = try {
        AttesterConfig.fromEnv()
    } catch (e: IllegalArgumentException) {
        System.err.println("atestador: ${e.message}")
        exitProcess(1)
    }
    val signer = DeviceAuthorizationSigner(config.privateKey, config.chainId, config.contract)
    val revocation = RevocationList()
    val policy = AttestationPolicy(revokedSerials = revocation::revokedSerials)
    println("atestador ${signer.address}, contrato ${config.contract}, chainId ${config.chainId}")
    println("porta ${config.port}, certificados aceitos: ${config.allowedCertDigests.size}")
    embeddedServer(Netty, port = config.port) {
        attesterModule(AttesterDependencies(config, signer, policy))
    }.start(wait = true)
}
