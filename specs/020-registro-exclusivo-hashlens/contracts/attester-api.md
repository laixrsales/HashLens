# Contract: API do Atestador

**Versão**: `v1` | Contrato entre o aplicativo (`AttesterClient`) e o serviço `:attester`.
Decisões em [research.md](../research.md) R9.

## 1. `POST /v1/authorize`

Requisição (`application/json`):

```json
{
  "wallet": "0x…",
  "publicKey": "0x04…",
  "chain": ["<base64 DER folha>", "…", "<base64 DER raiz>"]
}
```

`chainId` e `contract` não vêm na requisição: são os da configuração do atestador e são conferidos
contra o desafio dentro da cadeia (attestation-policy.md §2).

Respostas:

| Status | Corpo                                                                              | Quando                                            |
| ------ | ---------------------------------------------------------------------------------- | ------------------------------------------------- |
| 200    | `{"deadline": 1700000000, "attestationHash": "0x…", "authorization": "0x…(65 B)"}` | Política aceita (attestation-policy.md §3)        |
| 422    | `{"reason": "<RejectReason>"}`                                                     | Política recusa; motivo da §3                     |
| 400    | `{"reason": "MALFORMED"}`                                                          | JSON, endereço, chave ou base64 inválidos         |
| 503    | `{"reason": "REVOCATION_UNAVAILABLE"}`                                             | Lista de revogação inacessível e sem cache válido |

- Limites: corpo ≤ 32 KB, cadeia com 2 a 8 certificados.
- O atestador não guarda nada da requisição; o log contém só status, motivo e carteira abreviada.

## 2. `GET /v1/health`

`200 {"chainId": 11155111, "contract": "0x…", "attester": "0x…"}`. O app usa para mostrar, nos
detalhes técnicos de erro, qual deploy o atestador atende.

## 3. Configuração (variáveis de ambiente)

| Variável                        | Conteúdo                                                             |
| ------------------------------- | -------------------------------------------------------------------- |
| `ATTESTER_PRIVATE_KEY`          | chave secp256k1 do atestador (nunca no Git, FR-019)                  |
| `ATTESTER_CHAIN_ID`             | `11155111`                                                           |
| `ATTESTER_CONTRACT`             | endereço do `ImageRegistry` do deploy                                |
| `ATTESTER_ALLOWED_CERT_DIGESTS` | SHA-256 (hex) dos certificados do APK aceitos, separados por vírgula |
| `ATTESTER_PORT`                 | padrão `8080`                                                        |

## 4. Mapeamento no aplicativo

| Resposta                                              | Exceção no domínio                                                         | Texto (FR-010, FR-011)           |
| ----------------------------------------------------- | -------------------------------------------------------------------------- | -------------------------------- |
| 200                                                   | —                                                                          | segue para a carteira            |
| 422 `BOOT_NOT_VERIFIED`                               | `DeviceNotEligibleException(MODIFIED)`                                     | aparelho modificado              |
| 422 `PATCH_TOO_OLD`                                   | `DeviceNotEligibleException(OUTDATED)`                                     | sistema desatualizado            |
| 422 `NOT_HARDWARE`, `CHAIN_INVALID`, `KEY_PROPERTIES` | `DeviceNotEligibleException(NO_SECURE_HARDWARE)`                           | sem hardware seguro              |
| 422 `APP_NOT_GENUINE`                                 | `DeviceNotEligibleException(APP_NOT_GENUINE)`                              | aplicativo não original          |
| 422 `REVOKED`                                         | `DeviceNotEligibleException(REVOKED)`                                      | certificado revogado             |
| 422 `CHALLENGE_INVALID`, `KEY_MISMATCH`               | nova tentativa automática (uma vez), depois `AttesterUnavailableException` | falha temporária                 |
| 400, 503, timeout, sem conexão                        | `AttesterUnavailableException`                                             | falha temporária, tentar de novo |

`CHALLENGE_INVALID` costuma indicar relógio do aparelho errado; a mensagem de falha temporária
sugere conferir data e hora. Os textos finais são definidos na etapa de interface (hashlens-ui).
