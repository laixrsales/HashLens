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

## Atestador e segredos da 020 (020/T065)

Revisão feita em 23/11/2026 na branch `chore/028-avaliacao-polimento`, sobre a `main` (`8ea0e22`).
Critérios: 020 FR-002, FR-007 e FR-019 e research R8 e R9.

Resultado: **um problema encontrado e corrigido** (chave malformada no erro de inicialização, abaixo).

- **Nenhuma chave ou keystore no Git.** `git ls-files` não tem `.jks`, `.keystore`, `.p12`, `.pem`,
  `.env` nem `local.properties`, e nenhum desses arquivos aparece no histórico
  (`git log --all --diff-filter=A`). `android/local.properties`, `contracts/.env` e
  `contracts/broadcast/` são ignorados (`git check-ignore`). A chave do atestador de avaliação fica
  em `~/.hashlens/`, fora do repositório, com permissão 600. A única chave privada versionada é a dos
  vetores de teste (`device-authorization.json`, atestador `0x646B…f6fc`), que não é a de nenhum
  deploy (desenvolvimento `0x308F…586e`, avaliação `0x447F…A21D`).
- **Chave do atestador só por ambiente (FR-019).** `AttesterConfig.fromEnv` lê
  `ATTESTER_PRIVATE_KEY` e o `toString` não a inclui. **Corrigido:** uma chave malformada (ex.: um
  dígito a mais ao colar) passava pela leitura e fazia o web3j lançar, fora do tratamento de erro do
  `main`, uma exceção cujo texto pode repetir o valor no terminal. Agora a leitura recusa a chave com
  "ATTESTER_PRIVATE_KEY inválida", sem o valor (`AttesterConfigTest`).
- **Logs sem cadeia nem chave (FR-002).** O atestador escreve uma linha na inicialização (endereço
  do atestador, contrato, chainId, porta e quantidade de certificados aceitos) e uma por requisição
  (status, motivo e carteira abreviada, ex.: `0x88db…d31a`). A auditoria (`:attester:audit`) só
  imprime dados públicos da rede.
- **Limites de requisição.** Corpo de até 32 KB, cadeia de 2 a 8 certificados, chave pública de 65
  bytes e só os campos `wallet`, `publicKey` e `chain`; fora disso, 400 `MALFORMED`. Não há limite de
  taxa (research R9): uma recusa não custa nada, e uma autorização só serve para a chave TEE de quem a
  pediu. O risco aceito na PoC é só de negação de serviço, e o atestador fica no ar só durante
  vínculos.
- **Release sem texto claro.** A exceção de HTTP para `localhost` e `127.0.0.1` existe só em
  `src/debug/res/xml/network_security_config.xml`. O manifesto mesclado do release não declara
  `networkSecurityConfig` nem `usesCleartextTraffic`, e com `targetSdk` 37 o HTTP sem TLS fica
  bloqueado. Na avaliação, o atestador é acessado por HTTPS (túnel ngrok).
- **O atestador não guarda chaves de aparelho nem registra imagens (FR-007).** Não tem banco nem
  arquivos, não envia transações (nenhuma chamada a `registerCapture`, `registerEdit` ou
  `eth_sendRawTransaction`) e a sua chave não tem saldo. Ele só assina autorizações de vínculo
  presas à carteira, à chave pública e ao hash da cadeia, válidas por 15 minutos.
- **Novos caminhos de rede do app.** Além do RPC e da carteira, o app agora chama o atestador
  (`POST /v1/authorize`: carteira, chave pública do aparelho e cadeia de atestação) e a lista de
  revogação do Google (`GET android.googleapis.com/attestation/status`, sem dados do usuário).
  Nenhum dos dois recebe bytes da imagem.

Limite: quem tiver a chave de um atestador pode autorizar vínculos de qualquer chave no contrato
daquele deploy. É a confiança que o desenho deposita no atestador (research R9). Por isso as chaves
de desenvolvimento e de avaliação são diferentes (R8) e ficam só no computador da autora.
