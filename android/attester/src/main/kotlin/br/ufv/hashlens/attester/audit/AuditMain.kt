package br.ufv.hashlens.attester.audit

import br.ufv.hashlens.attestation.AttestationChallenge
import br.ufv.hashlens.attestation.AttestationVerdict
import br.ufv.hashlens.attester.RevocationList
import br.ufv.hashlens.attester.RevocationUnavailableException
import java.time.ZoneOffset
import kotlin.system.exitProcess

/**
 * `./gradlew :attester:audit --args="<deviceId>"` (020 SC-006, T056): refaz, fora do app e sem a
 * chave do atestador, a decisão sobre o vínculo de um aparelho a partir só dos dados públicos.
 *
 * Ambiente: `AUDIT_RPC_URL` (nó da Sepolia), `AUDIT_CONTRACT`, `AUDIT_CHAIN_ID` e
 * `AUDIT_ALLOWED_CERT_DIGESTS`; os três últimos caem nos `ATTESTER_*` do atestador se faltarem.
 *
 * Saída: 0 aceito, 1 recusado ou adulterado, 2 erro de uso ou de rede.
 */
fun main(args: Array<String>) {
    val code = try {
        run(args, System.getenv())
    } catch (e: IllegalArgumentException) {
        println("erro: ${e.message}")
        println("uso: ./gradlew :attester:audit --args=\"<deviceId>\"")
        EXIT_ERROR
    } catch (e: java.io.IOException) {
        println("erro de rede: ${e.message}")
        EXIT_ERROR
    }
    exitProcess(code)
}

internal fun run(args: Array<String>, env: Map<String, String>): Int {
    val deviceId = requireNotNull(args.singleOrNull()?.toIntOrNull()?.takeIf { it > 0 }) {
        "informe um deviceId positivo"
    }
    fun setting(name: String): String =
        requireNotNull((env["AUDIT_$name"] ?: env["ATTESTER_$name"])?.trim()?.takeIf { it.isNotEmpty() }) {
            "defina AUDIT_$name (ou ATTESTER_$name)"
        }

    val rpcUrl = requireNotNull(env["AUDIT_RPC_URL"]?.trim()?.takeIf { it.isNotEmpty() }) { "defina AUDIT_RPC_URL" }
    val contract = AttestationChallenge.normalizeAddress(setting("CONTRACT"))
    val chainId = requireNotNull(setting("CHAIN_ID").toLongOrNull()) { "CHAIN_ID inválido" }
    val digests = setting("ALLOWED_CERT_DIGESTS").split(",")
        .map { it.trim().lowercase().removePrefix("0x").replace(":", "") }
        .filter { it.isNotEmpty() }
        .toSet()

    val revocation = RevocationList()
    val audit = DeviceAudit(
        rpc = JsonRpc(rpcUrl),
        chainId = chainId,
        contract = contract,
        allowedCertDigests = digests,
        revokedSerials = {
            try {
                revocation.revokedSerials()
            } catch (_: RevocationUnavailableException) {
                null
            }
        }
    )
    val report = audit.audit(deviceId)
    println("contrato $contract, chainId $chainId, aparelho $deviceId")
    report.describe().forEach(::println)
    return if (report is AuditReport.Evaluated &&
        report.verdict is AttestationVerdict.Accepted
    ) {
        EXIT_OK
    } else {
        EXIT_REJECTED
    }
}

/** Linhas do relatório; os motivos usam os nomes de `RejectReason`, os mesmos do log do atestador. */
internal fun AuditReport.describe(): List<String> = when (this) {
    is AuditReport.NotFound -> listOf("resultado: aparelho inexistente neste contrato")

    is AuditReport.EventMissing -> device.summary() +
        "resultado: ADULTERADO (sem evento DeviceAttested no bloco ${device.registeredBlock})"

    is AuditReport.HashMismatch -> device.summary() + listOf(
        "hash calculado: ${Abi.hex(computed)}",
        "resultado: ADULTERADO (hash da cadeia diferente do gravado)"
    )

    is AuditReport.Evaluated -> device.summary() + listOf("cadeia: ${chain.size} certificados, hash confere") +
        when (verdict) {
            is AttestationVerdict.Accepted -> listOf(
                "nível de segurança: ${verdict.securityLevel}",
                "sistema: ${verdict.bootState}",
                "patch de segurança: ${verdict.osPatchLevel}",
                "desafio emitido em: ${verdict.issuedAt}",
                "revogação hoje: ${
                    when (revokedNow) {
                        true -> "REVOGADO depois do vínculo"
                        false -> "não revogado"
                        null -> "não consultada"
                    }
                }",
                "resultado: ACEITO"
            )

            is AttestationVerdict.Rejected -> listOf("resultado: RECUSADO (${verdict.reason})")
        }
}

private fun AuditedDevice.summary(): List<String> = listOf(
    "dono: $owner",
    "vinculado em: ${registeredAt.atOffset(ZoneOffset.UTC)} (bloco $registeredBlock)",
    "attestationHash: ${Abi.hex(attestationHash)}"
)

private const val EXIT_OK = 0
private const val EXIT_REJECTED = 1
private const val EXIT_ERROR = 2
