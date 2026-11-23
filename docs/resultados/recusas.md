# Recusas de origens não autorizadas (020 SC-001, T060)

**Critério (SC-001)**: nenhuma imagem é registrada por fora do HashLens genuíno. Toda tentativa de
vincular um aparelho ou registrar uma imagem sem a autorização do atestador, ou com uma assinatura
que não seja a da chave do aparelho vinculado, é recusada pela rede.

**Resultado no deploy de avaliação**: 6 de 6 tentativas recusadas com o erro esperado (5 transações
mineradas e revertidas, `status 0`, e 1 simulada). O app de debug apontado para o atestador de
avaliação também foi recusado como "aplicativo não original" (seção própria).

- Contrato: `ImageRegistry` de avaliação `0xEd920f008400E817601a9Afa51d86dD975A1e6D3` (bloco
  12197165), cujo atestador (`0x447F456DbFd9A5Df5a8a7Bd8089D9b3B693EA21D`) aceita só o certificado
  de release (research R8; tabela de deploys em
  `specs/020-registro-exclusivo-hashlens/quickstart.md` §2).
- Aparelho: Samsung Galaxy M52 5G (SM-M526B), Android 13, vinculado pelo APK de release como aparelho
  1 (tx `0xaa1a5934…71f9`, bloco 12197203), com o registro 1.
- Ferramenta: `tools/chain/tentar_origens_nao_autorizadas.py` (research R15). Cada tentativa é
  enviada com `cast send --gas-limit`, sem estimativa, para que a transação seja minerada e revertida;
  o erro vem de repetir a chamada com `cast call` no bloco anterior. Dados brutos:
  [`dados/recusas.csv`](dados/recusas.csv) (as linhas do contrato `0xfBa7…AE6E` são da execução no
  deploy de desenvolvimento, na 020/T041).

## Tentativas na rede

| #   | Tentativa                                                          | Erro esperado e obtido          | Transação (revertida)                                                | Bloco    |
| --- | ------------------------------------------------------------------ | ------------------------------- | -------------------------------------------------------------------- | -------- |
| 1   | Vínculo com chave nova e cadeia real, sem autorização do atestador | `InvalidAuthorization()`        | `0x3ff1897942047cebddcd69c4b94fec523d41edeeda80c75d0422a6e4ff2bbd75` | 12197223 |
| 2   | Autorização real enviada por outra carteira                        | `InvalidAuthorization()`        | simulada (`cast call --from` outro endereço, sem envio)              | 12197223 |
| 3   | Autorização real reutilizada pela carteira dona                    | `DeviceAlreadyRegistered(1)`    | `0xabdfbaeb1ed9a808d414a3626df661405998bef52d5948256030f98024f3840c` | 12197224 |
| 4   | Registro assinado por outra chave P-256                            | `InvalidDeviceSignature()`      | `0xebda29c7e5c4aef5296a7ea84403a7531293808b86dc058cf00ec002c57dddeb` | 12197225 |
| 5   | Assinatura real do registro 1 com outro SHA-256 e pHash            | `InvalidDeviceSignature()`      | `0xe79893e88e91b4cc58e17283a17bf7cfd754ab1902aa0f2b2f25ea29899c20e3` | 12197226 |
| 6   | Autorização real enviada depois do prazo de 15 min                 | `AuthorizationExpired(…456777)` | `0x0d129f5d035672b7eb8c222cb114fc425c5bacdb512dfac640fc49d07e9f9483` | 12197277 |

Nas tentativas 4 e 5, os dados alterados são o SHA-256 e o pHash de uma cópia recomprimida (JPEG
q85) da foto do registro 1, que não está registrada. A tentativa 2 é simulada porque a avaliação usa
uma carteira só (research R15).

## Aplicativo não original (roteiro US1, passo 4)

O APK de debug, assinado com outro certificado, pede o vínculo ao atestador de avaliação.

Executado em 23/11/2026, às 16:09, no mesmo aparelho, depois das capturas da matriz. O APK de
debug (certificado `afaa9a6a…79eda2`, fora de `ATTESTER_ALLOWED_CERT_DIGESTS`) substituiu o release,
e o atestador de avaliação estava no ar pelo túnel ngrok, conferido por `/v1/health`
(`attester` `0x447f…a21d`, o mesmo do contrato).

- Uma captura no app criou a chave nova do aparelho no Android Keystore (logcat, `keystore2`,
  16:09:41) e pediu o vínculo ao atestador.
- Resultado no app: recusa **"aplicativo não original"**, sem abrir a carteira.
- Na rede, nada mudou: `getDevicesOf(carteira)` continuou `[1]` e `totalRecords()` continuou 22
  (bloco 12197613). Não houve transação, porque o atestador recusou antes de autorizar.

## Limites

- Uma carteira e um aparelho: a tentativa vinda de outra carteira é simulada.
- A recusa de aparelho modificado, sem hardware seguro ou com sistema desatualizado é decidida pelo
  atestador antes de qualquer transação e está coberta pelos testes de `:attestation` e
  `:attester` e pelos estados da US2, não por transações na rede.
