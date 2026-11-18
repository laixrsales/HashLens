# Cadeias de atestação de referência

Usadas pelos testes do módulo `:attestation` (`contracts/attestation-policy.md` §6, 020/T016 e
T017).

## `real/`: cadeias reais

Capturadas por `CaptureReferenceChainTest` (teste instrumentado do app), que gera uma chave como o
app gera (P-256, só assinatura, SHA-256) com o desafio da policy §2 para o contrato de
desenvolvimento e a carteira fixa de teste `0x1234567890AbcdEF1234567890aBcdef12345678`. Cada JSON
traz `chain` (DER em base64, da folha para a raiz), `challenge` e `publicKey` em hex, `capturedAt`
(segundos Unix, o `issuedAt` do desafio) e os dados do aparelho. Para capturar de novo, ver o
comentário do teste e `tools/attestation/remontar_cadeia.py`.

| Arquivo             | Aparelho                         | Android     | Patch      | Nível | Provisionamento                                          | Capturado em |
| ------------------- | -------------------------------- | ----------- | ---------- | ----- | -------------------------------------------------------- | ------------ |
| `SM_M526B-tee.json` | Samsung Galaxy M52 5G (SM-M526B) | 13 (API 33) | 2025-05-01 | TEE   | de fábrica (4 certificados, raiz RSA `f92009e853b6b045`) | 2026-11-18   |

O M52 não tem StrongBox (`FEATURE_STRONGBOX_KEYSTORE` ausente), então não há cadeia StrongBox. O
certificado da folha vale de 2021 a 2031 (a validade da folha não é conferida pela biblioteca) e a
raiz até 2034.

## Cadeias sintéticas

Não ficam em arquivo: `fixtures/TestChains.kt` gera, com uma raiz de teste injetada como âncora,
uma cadeia aceita e uma variação para cada motivo de recusa da policy §3. A tabela de casos está
no próprio `TestChains.kt` e em `AttestationPolicyTest.kt`.
