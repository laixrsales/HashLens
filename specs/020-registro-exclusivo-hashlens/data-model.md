# Data Model: Registro Exclusivo pelo HashLens Genuíno

Só o que muda em relação a `specs/002-registro-rastreamento-imagens/data-model.md`; o resto
continua valendo. Decisões em [research.md](./research.md).

## 1. On-chain (`ImageRegistry.sol` v2)

Interface completa em [contracts/IImageRegistry.sol](./contracts/IImageRegistry.sol).

### 1.1 Device (alterado)

| Campo             | Tipo      | Descrição                                                                |
| ----------------- | --------- | ------------------------------------------------------------------------ |
| `id`              | `uint32`  | Igual à v1.                                                              |
| `owner`           | `address` | Igual à v1.                                                              |
| `publicKey`       | `bytes`   | Igual à v1 (65 bytes).                                                   |
| `registeredAt`    | `uint64`  | Igual à v1. Na verificação, é o instante de referência da atestação.     |
| `attestationHash` | `bytes32` | **Novo.** `keccak256(abi.encode(attestationChain))` (R10).               |
| `registeredBlock` | `uint64`  | **Novo.** Bloco do `DeviceAttested`, para `eth_getLogs` num bloco (R10). |

Regras novas: só existe com autorização válida do atestador (device-authorization.md §4). A
cadeia em si fica só no evento `DeviceAttested`.

### 1.2 ImageRecord

Sem mudança de campos. Regra nova: `sigR ‖ sigS` precisa ser uma assinatura P-256 válida, com
low-S, da `publicKey` do aparelho sobre o payload v1 (research R1). `MissingSignature` sai, porque
uma assinatura zerada não passa no P256VERIFY e cai em `InvalidDeviceSignature`.

### 1.3 Estado do contrato (novo)

| Campo             | Tipo                | Descrição                                      |
| ----------------- | ------------------- | ---------------------------------------------- |
| `attester`        | `address immutable` | Atestador do deploy; troca = novo deploy (R5). |
| `domainSeparator` | `bytes32 immutable` | EIP-712, calculado no construtor.              |

## 2. Local (Room)

### 2.1 DeviceBindingEntity (alterado)

| Campo           | Tipo              | Descrição                                                                      |
| --------------- | ----------------- | ------------------------------------------------------------------------------ |
| `failureReason` | `BindingFailure?` | **Novo.** Motivo da última falha, para a UI e para o reenvio (FR-010, FR-011). |

`BindingFailure`: `NOT_ELIGIBLE_MODIFIED`, `NOT_ELIGIBLE_OUTDATED`,
`NOT_ELIGIBLE_NO_SECURE_HARDWARE`, `NOT_ELIGIBLE_APP`, `NOT_ELIGIBLE_REVOKED`,
`ATTESTER_UNAVAILABLE`, mais os já existentes (`INSUFFICIENT_FUNDS`, `REVERTED`, `TIMEOUT`).

Migração Room: nova coluna anulável, sem perda de dados. As linhas do contrato anterior ficam,
mas não valem: `EnsureDeviceBound` sempre consulta o contrato configurado (`002/R25`).

### 2.2 Estados do vínculo (alterado)

```text
LOCAL_ONLY ──(chave do alias já vinculada no contrato)──▶ CONFIRMED
LOCAL_ONLY ──(gera chave com desafio; atestador aceita; saldo ok)──▶ AWAITING_WALLET
LOCAL_ONLY ──(atestador recusa)──▶ FAILED (NOT_ELIGIBLE_*, sem abrir a carteira)
LOCAL_ONLY ──(atestador inacessível)──▶ FAILED (ATTESTER_UNAVAILABLE, sem abrir a carteira)
LOCAL_ONLY ──(saldo insuficiente)──▶ FAILED (INSUFFICIENT_FUNDS)
AWAITING_WALLET ──(aprovada)──▶ PENDING
AWAITING_WALLET ──(autorização venceu antes do envio)──▶ LOCAL_ONLY  (uma nova tentativa automática)
PENDING ──(evento DeviceRegistered)──▶ CONFIRMED (deviceId)
PENDING ──(revert AuthorizationExpired)──▶ LOCAL_ONLY (uma nova tentativa automática)
FAILED ──(reenviar)──▶ LOCAL_ONLY
```

A imagem que disparou o vínculo segue a máquina de `002/data-model.md` §2.3: se o vínculo falha,
ela fica `LOCAL_ONLY` (não registrada) e pode ser reenviada sem nova captura (US2 cenário 2).

## 3. Domínio (Kotlin)

```kotlin
// Alterado: dados públicos do vínculo
data class Device(
    val id: Int, val owner: String, val publicKey: ByteArray,
    val registeredAt: Instant, val attestationHash: ByteArray, val registeredBlock: Long,
)

// Novo: resultado da revalidação da atestação na verificação (attestation-policy.md §5)
data class DeviceOrigin(
    val securityLevel: SecurityLevel,      // TEE | STRONGBOX
    val bootState: BootState,              // VERIFIED
    val osPatchLevel: YearMonth,
    val boundAt: Instant,                  // Device.registeredAt
    val revocation: RevocationStatus,      // NOT_REVOKED | REVOKED_AFTER_BINDING | UNKNOWN
)

// Novo: autorização devolvida pelo atestador (attester-api.md §1)
data class DeviceAuthorization(val deadline: Long, val attestationHash: ByteArray, val signature: ByteArray)

// Novas exceções do vínculo (attester-api.md §4)
class DeviceNotEligibleException(val reason: NotEligibleReason) : Exception()
enum class NotEligibleReason { MODIFIED, OUTDATED, NO_SECURE_HARDWARE, APP_NOT_GENUINE, REVOKED }
class AttesterUnavailableException(cause: Throwable? = null) : Exception(cause)
```

`RecordView` (002 verification-result.md §2) ganha `origin: DeviceOrigin`; resultados
`Tampered` não têm origem. Ver [contracts/verification-result.md](./contracts/verification-result.md).

## 4. Configuração (`BuildConfig`, a partir de `local.properties`)

| Chave                         | Uso                                                                  |
| ----------------------------- | -------------------------------------------------------------------- |
| `attester.url`                | Base do atestador (debug: `http://localhost:8080` com `adb reverse`) |
| `attester.allowedCertDigests` | Certificados aceitos na verificação (R8), mesmo valor do atestador   |
| `registry.address`            | Novo contrato do deploy                                              |
| `registry.deployBlock`        | Bloco do novo deploy                                                 |
