# Revisão de segurança e idioma (T099)

Revisão feita em 13/10/2026 sobre a `main` (0e9b656). Critérios: FR-039 e research R24.

Resultado: **nenhum problema encontrado**. Nada precisou ser corrigido.

## Segredos fora do Git

- `android/local.properties` (URL do RPC com chave, endereço do contrato, projectId da Reown) e
  `contracts/.env` são ignorados pelo `.gitignore`. Conferido com `git check-ignore`.
- Nenhum desses arquivos aparece no histórico (`git log --all --diff-filter=A`).
- Busca no conteúdo versionado e em todo o histórico por URL de RPC com chave (Infura, Alchemy),
  `PRIVATE_KEY=0x…` e projectId: nenhuma ocorrência.
- O deploy lê a chave de `DEPLOYER_PRIVATE_KEY` do ambiente (`contracts/script/Deploy.s.sol`) e só
  imprime o endereço do contrato e o chainId.

## Nenhum segredo em log

- O app só registra seis mensagens no logcat (`HashLensApp`, `WalletSession`): falha do OpenCV e erros
  do relay ou do AppKit, sem URL, chave, endereço ou hash montados pelo app.
- O web3j não registra as requisições HTTP no Android (o `HttpLoggingInterceptor` dele só é ativado
  com um logger SLF4J em nível debug, e o app não inclui nenhum).
- A mensagem de erro de rede guardada em `LocalImageEntity.errorMessage` fica só no banco local. Não é
  exibida nem registrada no log.
- `metricas.csv` (T096) grava só momento, medida, duração e resultado.
- No aparelho (Galaxy M52), no logcat de uma sessão real de captura, edição, confirmação e verificação
  (processo do app iniciado às 17:40, 1.833 linhas do app), a chave do RPC e o projectId aparecem 0
  vezes. Também não há nenhuma URL nem endereço Ethereum nas linhas do app.

## Nenhuma chamada que envie pixels

O app usa a rede só por dois caminhos:

| Caminho                    | O que sai do aparelho                                                                                                                                                                                               |
| -------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| RPC (web3j, `ChainModule`) | leituras do contrato e recibos: SHA-256, pHash, ids de registro, hash da transação                                                                                                                                  |
| Carteira (Reown AppKit)    | `eth_sendTransaction` com `registerDevice`, `registerCapture` ou `registerEdit` (`RegistryTxBuilder`): chave pública do aparelho, SHA-256, pHash, id do aparelho, assinatura (r, s); e `wallet_switchEthereumChain` |

Nenhum dos dois recebe bytes da imagem. O manifesto não declara `ACTION_SEND` nem `ACTION_VIEW` de
imagens (R16), e o app não usa nenhum outro cliente HTTP.

## Idioma

`android/app/src/main/res/` só tem `values` e `values-night`. Não há nenhuma pasta `values-<idioma>`,
então todos os textos ficam em pt-BR (R24).

## Observações (sem correção necessária)

- A URL do RPC e o projectId vão para o `BuildConfig` e, portanto, ficam dentro do APK. É inevitável
  para um app que chama o RPC direto. Por isso, use uma chave de plano gratuito e só de testnet, e
  revogue-a se o APK for distribuído fora da banca.
- O projectId da Reown é um identificador público do app (vai em toda conexão com o relay), não um
  segredo.
