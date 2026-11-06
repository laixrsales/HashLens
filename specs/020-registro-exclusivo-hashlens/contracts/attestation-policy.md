# Contract: Política de Atestação do Aparelho

**Versão**: `v1` | Normativo para o atestador (vínculo) e para a verificação no aplicativo. As duas
pontas usam o mesmo módulo `:attestation` (research R4). Decisões em
[research.md](../research.md) R6, R7, R8 e R12.

## 1. Entrada

- `chain`: certificados X.509 DER, da folha para a raiz, como devolvidos por
  `KeyStore.getCertificateChain(alias)`.
- `expected`: `{chainId, contract, wallet, publicKey, allowedCertDigests, referenceTime}`.
  - No atestador: `wallet` e `publicKey` da requisição, `referenceTime = agora`.
  - Na verificação: `wallet = device.owner`, `publicKey = device.publicKey`,
    `referenceTime = device.registeredAt`.

## 2. Desafio (`attestationChallenge`)

Concatenação binária, inteiros em big-endian, 95 bytes:

| #   | Campo      | Tamanho | Conteúdo                    |
| --- | ---------- | ------- | --------------------------- |
| 1   | `domain`   | 15 B    | ASCII `HASHLENS-ATT-v1`     |
| 2   | `chainId`  | 32 B    | `11155111`                  |
| 3   | `contract` | 20 B    | endereço do `ImageRegistry` |
| 4   | `wallet`   | 20 B    | carteira que vai vincular   |
| 5   | `issuedAt` | 8 B     | `uint64`, segundos Unix     |

Válido se os campos 1 a 4 forem iguais aos esperados e
`referenceTime − 15 min ≤ issuedAt ≤ referenceTime + 2 min`.

## 3. Regras (todas obrigatórias)

| #   | Regra                                                                                                                                                                           | Motivo de recusa    |
| --- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------- |
| 1   | Cadeia válida até uma raiz de `android.googleapis.com/attestation/root` (RSA e ECDSA P-384), com a validade dos certificados conferida em `referenceTime` conforme a biblioteca | `CHAIN_INVALID`     |
| 2   | Nenhum certificado da cadeia `REVOKED` ou `SUSPENDED` em `android.googleapis.com/attestation/status` (só no atestador; na verificação, ver §5)                                  | `REVOKED`           |
| 3   | `attestationSecurityLevel` ∈ {`TrustedEnvironment`, `StrongBox`} e `keyMintSecurityLevel` igual                                                                                 | `NOT_HARDWARE`      |
| 4   | `rootOfTrust.verifiedBootState = Verified` e `rootOfTrust.deviceLocked = true`                                                                                                  | `BOOT_NOT_VERIFIED` |
| 5   | `osPatchLevel` (AAAAMM) com no máximo 24 meses antes de `issuedAt`                                                                                                              | `PATCH_TOO_OLD`     |
| 6   | `attestationApplicationId` com pacote `br.ufv.hashlens` e algum `signatureDigest` em `allowedCertDigests`                                                                       | `APP_NOT_GENUINE`   |
| 7   | `purpose = {SIGN}`, `algorithm = EC`, `ecCurve = P_256`, `digest ∋ SHA_256`                                                                                                     | `KEY_PROPERTIES`    |
| 8   | Chave pública da folha igual a `publicKey`                                                                                                                                      | `KEY_MISMATCH`      |
| 9   | Desafio válido (§2)                                                                                                                                                             | `CHALLENGE_INVALID` |

Quando mais de uma regra falha, o motivo devolvido é o da primeira na ordem da tabela.

## 4. Resultado

```kotlin
sealed interface AttestationVerdict {
    data class Accepted(
        val securityLevel: SecurityLevel,   // TEE | STRONGBOX
        val osPatchLevel: YearMonth,
        val bootState: BootState,           // sempre VERIFIED quando aceito
        val issuedAt: Instant,
        val certSerials: List<String>,      // hex minúsculo, para a consulta de revogação
    ) : AttestationVerdict
    data class Rejected(val reason: RejectReason) : AttestationVerdict
}
enum class RejectReason {
    CHAIN_INVALID, REVOKED, NOT_HARDWARE, BOOT_NOT_VERIFIED, PATCH_TOO_OLD,
    APP_NOT_GENUINE, KEY_PROPERTIES, KEY_MISMATCH, CHALLENGE_INVALID,
}
```

## 5. Diferenças na verificação (aplicativo)

- Antes das regras, conferir `keccak256(abi.encode(chain)) == device.attestationHash`; se não
  conferir → `Tampered`.
- Regra 2 não reprova: a consulta à lista é feita à parte e só marca `revokedAfterBinding`
  (FR-017). Falha ao consultar marca `revocationUnknown`.
- Qualquer `Rejected` das demais regras → `Tampered` (FR-013).

## 6. Cadeias de referência

`android/attestation/src/test/resources/attestation/` DEVE conter, com um `README.md` que descreve
cada caso: ao menos uma cadeia real aceita de cada aparelho de teste (TEE; StrongBox se houver),
cadeias públicas de teste da biblioteca e variações para cada motivo de recusa da §3.
