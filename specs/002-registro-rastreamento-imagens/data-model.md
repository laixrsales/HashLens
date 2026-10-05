# Data Model: Registro, Verificação e Rastreamento de Alterações em Imagens

**Feature**: `002-registro-rastreamento-imagens` | **Date**: 2026-10-04

O modelo tem três camadas: **on-chain** (contrato `ImageRegistry`, fonte da verdade), **local**
(Room, acervo do aparelho) e **domínio** (tipos Kotlin usados pela UI e pelos casos de uso).

---

## 1. On-chain (`ImageRegistry.sol`)

### 1.1 Device

| Campo          | Tipo Solidity | Descrição                                            |
| -------------- | ------------- | ---------------------------------------------------- |
| `id`           | `uint32`      | Sequencial, inicia em 1 (0 = inexistente).           |
| `owner`        | `address`     | Carteira que registrou o aparelho (`msg.sender`).    |
| `publicKey`    | `bytes` (65)  | Chave pública P-256 não comprimida (`0x04 ‖ X ‖ Y`). |
| `registeredAt` | `uint64`      | `block.timestamp`.                                   |

**Validações**: `publicKey.length == 65` e `publicKey[0] == 0x04`; a mesma chave pública não pode
ser registrada duas vezes (`mapping(bytes32 keccakPubKey => uint32)`).

### 1.2 ImageRecord

| Campo        | Tipo Solidity | Descrição                                                |
| ------------ | ------------- | -------------------------------------------------------- |
| `id`         | `uint256`     | Sequencial, inicia em 1 (0 = inexistente / "null").      |
| `sha256Hash` | `bytes32`     | SHA-256 dos bytes do arquivo. Único.                     |
| `pHash`      | `uint64`      | Hash perceptual de 64 bits. Não único.                   |
| `parentId`   | `uint256`     | 0 para originais (equivale a `parentId == null` do TCC). |
| `originalId` | `uint256`     | Raiz da cadeia; igual a `id` para originais.             |
| `registrant` | `address`     | `msg.sender`: autor (original) ou editor (versão).       |
| `deviceId`   | `uint32`      | Aparelho que assinou; DEVE pertencer a `registrant`.     |
| `sigR`       | `bytes32`     | Componente `r` da assinatura P-256.                      |
| `sigS`       | `bytes32`     | Componente `s` da assinatura P-256.                      |
| `timestamp`  | `uint64`      | `block.timestamp`.                                       |
| `operations` | `string`      | Vazio para originais; `op[:param];...` para edições.     |

**Índices (mappings)**:

- `recordById: uint256 → ImageRecord`
- `idBySha256: bytes32 → uint256` (único)
- `idsByPHash: uint64 → uint256[]`
- `childrenOf: uint256 → uint256[]`
- `devicesOf: address → uint32[]`

**Validações** (ver `contracts/IImageRegistry.sol`):

- `sha256Hash != 0` e ainda não registrado.
- `deviceId` existe e `devices[deviceId].owner == msg.sender`.
- `sigR != 0 && sigS != 0` (a validade criptográfica é verificada off-chain — research R5).
- Em edições: `parentId` existe; `originalId` é herdado de `records[parentId].originalId`
  (nunca informado pelo chamador); `bytes(operations).length` entre 1 e 256.

**Regras de relacionamento**:

- Original: `parentId = 0`, `originalId = id`.
- Versão: `parentId = pai.id`, `originalId = pai.originalId`.
- A genealogia é uma árvore enraizada em `originalId`; ciclos são impossíveis porque `parentId < id`.

---

## 2. Local (Room)

### 2.1 LocalImageEntity

| Campo              | Tipo Kotlin          | Descrição                                                               |
| ------------------ | -------------------- | ----------------------------------------------------------------------- |
| `localId`          | `Long` (PK, autogen) |                                                                         |
| `contentUri`       | `String`             | URI MediaStore do arquivo na galeria.                                   |
| `sha256Hex`        | `String` (64)        | Calculado conforme `hashing-spec.md`. Índice único.                     |
| `pHashHex`         | `String` (16)        |                                                                         |
| `origin`           | `ImageOrigin`        | `CAPTURE`, `EDIT`.                                                      |
| `parentRecordId`   | `Long?`              | `recordId` do pai (edições).                                            |
| `originalRecordId` | `Long?`              | Preenchido após confirmação.                                            |
| `operations`       | `String?`            | Operações aplicadas (edições).                                          |
| `walletAddress`    | `String`             | Carteira que solicitou o registro.                                      |
| `deviceId`         | `Int?`               |                                                                         |
| `signatureHex`     | `String?`            | `r‖s` (128 hex), gerada uma vez e reutilizada em reenvios.              |
| `status`           | `RegistrationStatus` | Ver máquina de estados.                                                 |
| `txHash`           | `String?`            |                                                                         |
| `recordId`         | `Long?`              | Lido do evento `ImageRegistered` no recibo.                             |
| `submittedAt`      | `Long?`              | epoch ms em que o `txHash` foi recebido; base do prazo de 30 min (R23). |
| `failureReason`    | `FailureReason?`     | `REJECTED`, `REVERTED`, `TIMEOUT`, `HASH_MISMATCH`, `NETWORK`.          |
| `errorMessage`     | `String?`            |                                                                         |
| `createdAt`        | `Long`               | epoch ms.                                                               |
| `fileAvailable`    | `Boolean`            | Falso se o arquivo foi removido da galeria.                             |

Arquivos importados de terceiros para edição não são persistidos como `LocalImageEntity`;
apenas a versão editada resultante (`origin = EDIT`).

### 2.2 DeviceBindingEntity

| Campo           | Tipo                 | Descrição                                                                               |
| --------------- | -------------------- | --------------------------------------------------------------------------------------- |
| `walletAddress` | `String` (PK)        | Minúsculo; também define o alias do Keystore (research R4).                             |
| `publicKeyHex`  | `String` (130)       | Chave pública vinculada. Se a chave do alias mudar (perda, R25), a linha é substituída. |
| `deviceId`      | `Int?`               | Nulo até a confirmação.                                                                 |
| `txHash`        | `String?`            |                                                                                         |
| `status`        | `RegistrationStatus` | Mesma máquina de estados.                                                               |

### 2.3 Máquina de estados — `RegistrationStatus`

```text
LOCAL_ONLY ──(usuário solicita registro)──▶ AWAITING_WALLET
AWAITING_WALLET ──(aprovada)──▶ PENDING          (txHash definido)
AWAITING_WALLET ──(rejeitada / timeout)──▶ FAILED
PENDING ──(recibo status=1)──▶ CONFIRMED          (recordId definido)
PENDING ──(recibo status=0 ou 30 min sem recibo)──▶ reconciliação
reconciliação ──(getIdBySha256(sha) ≠ 0)──▶ CONFIRMED       (recordId lido on-chain)
reconciliação ──(getIdBySha256(sha) = 0, recibo status=0)──▶ FAILED   (failureReason = REVERTED)
reconciliação ──(getIdBySha256(sha) = 0, sem recibo)──▶ FAILED        (failureReason = TIMEOUT)
reconciliação ──(erro de rede)──▶ PENDING                   (nova tentativa no próximo ciclo)
FAILED ──(reenviar; getIdBySha256(sha) ≠ 0)──▶ CONFIRMED   (recordId lido on-chain, sem nova tx)
FAILED ──(reenviar; getIdBySha256(sha) = 0)──▶ AWAITING_WALLET
```

`CONFIRMED` é terminal. Só imagens `CONFIRMED` podem ser editadas (FR-015).

**Reenvio e carteira**: o reenvio exige a mesma carteira (`walletAddress`): `signatureHex` inclui
o `registrant` no payload (signature-payload §1) e não vale para outra carteira. Com outra
carteira conectada, o reenvio é bloqueado e o app pede a carteira original.

**Reconciliação**: nenhum registro sai de `PENDING` para `FAILED` sem antes consultar
`getIdBySha256(sha)`. O hash é a identidade do registro, não o `txHash`: se uma transação anterior
(por exemplo, a que estourou o prazo antes de um reenvio) for confirmada depois, a transação nova
reverte com `AlreadyRegistered`, e a reconciliação leva o registro a `CONFIRMED` com o `recordId`
on-chain em vez de marcá-lo `FAILED(REVERTED)`. Um registro em `FAILED(TIMEOUT)` continua sendo
reconciliado pelo worker em cada ciclo até ser reenviado; por isso não é preciso guardar os
`txHash` anteriores.

---

## 3. Domínio (Kotlin)

```kotlin
@JvmInline value class Sha256(val hex: String)      // 64 hex, minúsculo
@JvmInline value class PHash(val value: ULong)      // 64 bits

data class ImageRecord(
    val id: Long, val sha256: Sha256, val pHash: PHash,
    val parentId: Long?,          // null quando on-chain = 0
    val originalId: Long,
    val registrant: String,       // endereço 0x...
    val deviceId: Int, val signature: ByteArray,   // r‖s (64 bytes)
    val timestamp: Instant, val operations: List<EditOperation>,
)

data class Device(val id: Int, val owner: String, val publicKey: ByteArray)

sealed interface EditOperation {
    data class Brightness(val delta: Int) : EditOperation        // -100..+100
    data class Contrast(val factor: Float) : EditOperation       // 0.5..2.0
    data object Grayscale : EditOperation
    data object Sepia : EditOperation
    data class Blur(val kernel: Int) : EditOperation             // ímpar, 3..25
    data object Sharpen : EditOperation
    data class Edges(val low: Int, val high: Int) : EditOperation
    data class Crop(val x: Float, val y: Float, val w: Float, val h: Float) : EditOperation // frações
    data class Rotate90(val turns: Int) : EditOperation          // 1..3
}
```

Serialização de `EditOperation` ↔ string on-chain: ver `contracts/hashing-spec.md` §4.

Resultado de verificação e nós de genealogia: ver `contracts/verification-result.md`.
