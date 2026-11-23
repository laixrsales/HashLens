# Gás e tempos do contrato v2 contra v1 (020/T062)

Quanto o vínculo atestado e a conferência da assinatura do aparelho na rede (020) custam a mais que
o contrato da 002, em `registerDevice`, `registerCapture` e `registerEdit`.

## 1. Mesmas condições (Foundry, local)

Gás de **execução** (sem a base de 21.000 e sem o calldata), medido por
`forge test --gas-report --match-contract '^ImageRegistryTest$'` nas duas versões do contrato, com o
mesmo compilador (solc 0.8.24, otimizador 200 runs), a mesma EVM (`osaka`) e Foundry 1.8.4. A v1 é o
código de `ac29e33` (contrato da 002), compilado com `evm_version = "osaka"` em vez de `cancun` só
para igualar as condições.

| Função            | v1 (mediana) | v2 (mediana) | Diferença      |
| ----------------- | ------------ | ------------ | -------------- |
| `registerDevice`  | 249.507      | 310.010      | +60.503 (+24%) |
| `registerCapture` | 282.302      | 302.262      | +19.960 (+7%)  |
| `registerEdit`    | 352.899      | 372.938      | +20.039 (+6%)  |

- `registerDevice` na v2 confere a autorização EIP-712 do atestador (`ecrecover`), guarda o
  `attestationHash` e emite o evento com a cadeia de certificados. O custo da cadeia no calldata e
  no log não aparece aqui: os testes usam cadeias curtas (ver a seção 2).
- `registerCapture` e `registerEdit` na v2 conferem a assinatura P-256 do aparelho no pré-compilado
  `P256VERIFY` (EIP-7951), o que acrescenta cerca de 20 mil de gás por registro.

## 2. Na Sepolia (recibos reais)

`gasUsed` dos recibos das transações confirmadas (`status 1`), que é o que a carteira paga: inclui a
base, o calldata e, no vínculo, a cadeia de atestação completa publicada no evento (quatro
certificados, research R10). Dados brutos por transação em
[`dados/gas-v2.csv`](dados/gas-v2.csv).

| Função            | v1 (`0xF2f4…65Ba`)       | v2 desenvolvimento (`0xfBa7…AE6E`) | v2 avaliação (`0xEd92…6D3`) |
| ----------------- | ------------------------ | ---------------------------------- | --------------------------- |
| `registerDevice`  | 829.685 (mediana de 3)   | 1.150.233 (mediana de 6)           | 1.443.919 e 1.150.185 (2)   |
| `registerCapture` | 1.251.826 (mediana de 5) | 1.271.066 (mediana de 8)           | 1.271.066 (mediana de 13)   |
| `registerEdit`    | 1.593.319 (1)            | sem edições neste deploy           | 1.710.576 (mediana de 10)   |

- O primeiro vínculo e o primeiro registro de cada contrato custam mais, porque criam os
  contadores (escrita de zero para não zero): na avaliação, o primeiro vínculo custou 1.443.919 e o
  segundo, 1.150.185, e o primeiro registro (1.368.974) entra na mediana de `registerCapture`
  (13 capturas).
- `registerEdit` na avaliação variou de 1.612.590 a 2.041.976. O maior valor é o da edição com a
  lista de operações mais longa (65 caracteres, seis rotações seguidas), a única acima de 31 bytes.
- Os recibos na Sepolia ficam bem acima da simulação local nas duas versões (cerca de 4 vezes
  em `registerCapture`), como já tinha acontecido na criação do contrato (quickstart §2). Por isso a
  comparação entre versões vale pela seção 1, e esta seção mostra o custo real na rede de teste.

## 3. Tempos no aparelho (SC-004 e SC-005)

Do momento em que a foto foi salva no aparelho até o bloco que confirmou o registro: a hora da
captura (ou da edição salva) vem do nome do arquivo, que o app grava com milissegundos, e a da
confirmação vem do `timestamp` do registro no contrato, que é a hora do bloco. O intervalo inclui a
assinatura do aparelho, a aprovação na carteira, o envio e a espera pelo bloco. Na Sepolia sai um
bloco a cada 12 s, então cada valor pode variar até 12 s conforme o momento do envio.

Condições: Samsung Galaxy M52 5G, release no deploy de avaliação, **Wi-Fi**, atestador pelo túnel
ngrok, 23/11/2026. Dados por registro em [`dados/tempos-v2.csv`](dados/tempos-v2.csv).

| Registro                                | Quantidade | Mediana | Mínimo | Máximo |
| --------------------------------------- | ---------- | ------- | ------ | ------ |
| Primeiro registro, com o vínculo (nº 1) | 1          | 25,1 s  | —      | —      |
| Capturas seguintes, aparelho vinculado  | 11         | 11,3 s  | 6,0 s  | 22,2 s |
| Edições, aparelho vinculado             | 10         | 14,0 s  | 8,9 s  | 24,6 s |

- **SC-005** (registros seguintes sem atraso pela atestação, mesmo critério da 002 SC-008, até
  30 s): **atendido**. Com o aparelho vinculado, o app não chama o atestador; todos os 21 registros
  seguintes ficaram abaixo de 25 s, e isso já conta a espera pelo bloco.
- **SC-004** (a verificação de integridade acrescenta no máximo 10 s ao primeiro registro):
  **atendido no Wi-Fi**, com **3,0 s**. O aparelho não tem 4G, então o critério foi medido no
  Wi-Fi, com o atestador pelo túnel ngrok. Foi medido num vínculo novo (aparelho 2), depois de
  reinstalar o release, com o logcat gravando:

  | Hora (−03)   | Evento                                                                     |
  | ------------ | -------------------------------------------------------------------------- |
  | 16:27:15.452 | disparo da câmera (`ImageCapture takePictureInternal`)                     |
  | 16:27:16.265 | foto salva (`HashLens_20261123_162716_265.jpg`)                            |
  | 16:27:17.786 | chave do aparelho e cadeia de atestação gravadas no Keystore (`keystore2`) |
  | 16:27:18.423 | atestador responde `POST /v1/authorize 200 OK` (relógio do computador)     |
  | 16:27:19.241 | MetaMask abre para aprovar o vínculo (`registerDevice`)                    |
  | 16:27:30.330 | volta ao HashLens depois da aprovação                                      |
  | 16:27:36     | `registerDevice` confirmado (bloco 12197695)                               |
  | 16:27:39.445 | MetaMask abre para aprovar o registro (`registerCapture`)                  |
  | 16:28:12     | `registerCapture` confirmado (bloco 12197698), registro 23                 |

  A etapa de integridade vai da foto salva até a carteira abrir para o vínculo: 3,0 s, ou 3,8 s
  contando do disparo. Ela inclui gerar a chave com atestação e a chamada ao atestador. O
  primeiro registro inteiro levou 55,7 s, contra a mediana de 11,3 s dos seguintes. A diferença
  vem de duas aprovações na carteira e da espera por dois blocos, não da atestação.

## Limites

- Uma única rede de teste (Sepolia) e um único aparelho e carteira.
- Os tempos foram medidos só no Wi-Fi: o aparelho não tem 4G, que é a conexão citada no SC-004.
- As transações da v1 são de outubro de 2026, e as da v2, de novembro de 2026.
