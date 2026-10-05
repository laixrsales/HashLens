# Contract: Resultado de Verificação e Genealogia

Contrato entre a camada de domínio (`VerifyImageUseCase`, `TraceGenealogyUseCase`) e a UI.

## 1. Algoritmo de verificação

```text
entrada: bytes do arquivo
1. se não decodificável → Error(UNSUPPORTED_FILE)
2. sha ← SHA256(bytes)
3. id ← getIdBySha256(sha)                       // erro de rede → Error(NETWORK)
4. se id ≠ 0:
     r ← getRecord(id); dev ← getDevice(r.deviceId)
     se assinatura inválida → Tampered(r)
     se r.parentId = 0     → RegisteredOriginal(r)
     senão                 → RegisteredEdited(r, original ← getRecord(r.originalId),
                                              percent ← pct(r.pHash, original.pHash))
5. p ← pHash(bytes)
6. ids ← getIdsByPHash(p)
7. se ids vazio → NotRegistered
8. para cada id: carregar registro, original, validar assinatura
   → VisualMatch(candidatos ordenados por timestamp crescente)
```

Somente registros com assinatura válida aparecem em `VisualMatch`; se todos forem inválidos,
o resultado é `Tampered` do primeiro.

## 2. Tipos

```kotlin
sealed interface VerificationResult {
    data class RegisteredOriginal(val record: RecordView) : VerificationResult
    data class RegisteredEdited(
        val record: RecordView, val original: RecordView, val percentFromOriginal: Double,
    ) : VerificationResult
    data class VisualMatch(val candidates: List<Candidate>) : VerificationResult {
        data class Candidate(val record: RecordView, val original: RecordView, val percentFromOriginal: Double)
    }
    data class Tampered(val record: RecordView) : VerificationResult
    data object NotRegistered : VerificationResult
    data class Error(val reason: Reason) : VerificationResult {
        enum class Reason { UNSUPPORTED_FILE, NETWORK, UNKNOWN }
    }
}

data class RecordView(
    val id: Long, val sha256: String, val pHash: String,
    val registrant: String, val deviceId: Int, val timestamp: Instant,
    val operations: List<EditOperation>, val txUrl: String?,
)
```

## 3. Textos da UI (pt-BR)

| Resultado            | Título                       | Mensagem obrigatória                                                                                                    |
| -------------------- | ---------------------------- | ----------------------------------------------------------------------------------------------------------------------- |
| `RegisteredOriginal` | Registrada – original        | "Este arquivo é idêntico a uma captura registrada."                                                                     |
| `RegisteredEdited`   | Registrada – versão alterada | "Versão editada registrada. ≈ X% de alteração visual em relação à original."                                            |
| `VisualMatch`        | Correspondência visual       | "O arquivo não é idêntico a nenhum registro, mas é visualmente igual a N registro(s). Pode ser uma cópia recomprimida." |
| `Tampered`           | Registro adulterado          | "A assinatura do aparelho não confere com os dados registrados."                                                        |
| `NotRegistered`      | Não registrada               | "Nenhum registro encontrado. Isso não significa que a imagem seja falsa."                                               |
| `Error(NETWORK)`     | Não foi possível verificar   | "Sem conexão com a rede. Tente novamente."                                                                              |

Rodapé fixo em todos os resultados positivos: "O registro comprova quando e por quem o arquivo
foi registrado, não que a cena retratada seja real."

## 4. Genealogia

```kotlin
data class GenealogyNode(
    val record: RecordView,
    val percentFromParent: Double?,     // null para a original
    val percentFromOriginal: Double,
    val signatureValid: Boolean,
    val children: List<GenealogyNode>,
)
data class Genealogy(
    val root: GenealogyNode,            // árvore completa a partir de originalId
    val pathToQueried: List<Long>,      // ids da original até o registro consultado
)
```

Construção: subir por `parentId` até 0 (caminho) e descer por `getChildren` a partir de
`originalId` (árvore). Limite de exibição: 10 níveis / 200 nós, com aviso se excedido.
