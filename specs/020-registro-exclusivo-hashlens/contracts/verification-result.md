# Contract: Resultado de Verificação (alterações da 020)

Delta sobre `specs/002-registro-rastreamento-imagens/contracts/verification-result.md`. Tudo o que
não aparece aqui continua igual.

## 1. Algoritmo (passos alterados)

Onde a 002 diz "validar assinatura" (passos 4 e 8), passa a valer:

```text
valid(r):
  dev ← getDevice(r.deviceId)
  se assinatura P-256 inválida                         → Tampered     (002, mantido)
  chain ← evento DeviceAttested(dev.id) no bloco dev.registeredBlock   // erro de rede → Error(NETWORK)
  se keccak256(abi.encode(chain)) ≠ dev.attestationHash → Tampered
  se política(chain, dev) = Rejected                   → Tampered     (attestation-policy.md §5)
  origin ← DeviceOrigin(..., revocation ← consultarRevogação(chain))   // falha → UNKNOWN
  → válido, com origin
```

A validação da cadeia é guardada por `deviceId` durante a sessão (research R12).

## 2. Tipos

`RecordView` ganha `val origin: DeviceOrigin` (data-model.md §3). `Tampered(record)` continua
sem origem.

## 3. Textos da UI (pt-BR)

Acréscimos aos resultados positivos (`RegisteredOriginal`, `RegisteredEdited` e cada candidato de
`VisualMatch`). Os textos finais saem da etapa de interface (hashlens-ui) e são aprovados pela
autora; os abaixo são o conteúdo obrigatório.

| Situação                 | Conteúdo obrigatório                                                                                                                                                    |
| ------------------------ | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Origem atestada (sempre) | Linha de origem: registrado pelo HashLens original num aparelho verificado (FR-014, FR-015).                                                                            |
| `REVOKED_AFTER_BINDING`  | Aviso visível (ícone, título, frase): o certificado de segurança deste aparelho foi revogado pelo Google depois do vínculo; os registros dele merecem cautela (FR-017). |
| `UNKNOWN` (revogação)    | Nota discreta: não foi possível consultar se o certificado do aparelho foi revogado (FR-017).                                                                           |
| Detalhes técnicos        | Nível de segurança da chave (TEE / chip dedicado), estado do sistema (verificado), patch de segurança (mês/ano), data do vínculo (FR-014).                              |

O rodapé fixo da 002 passa a dizer que o registro comprova quando, por quem e por qual aplicativo o
arquivo foi registrado, segundo a atestação do Google, e não que a cena retratada seja real (FR-015).

## 4. Genealogia

`GenealogyNode.signatureValid` passa a significar "assinatura e atestação válidas" (mesma função
`valid` da §1). O nó mostra o aviso de revogação quando houver.
