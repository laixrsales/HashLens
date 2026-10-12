# Quickstart: Registro, Verificação e Rastreamento de Alterações em Imagens

Guia para preparar o ambiente e validar manualmente cada história de usuário.

## 1. Pré-requisitos

- Android Studio (versão estável atual), JDK 17, aparelho Android 8.0+ (recomendado físico, para
  câmera e Keystore em hardware).
- Foundry instalado (`curl -L https://foundry.paradigm.xyz | bash && foundryup`).
- Duas carteiras de teste (A e B) em um app de carteira no aparelho (ex.: MetaMask), com
  SepoliaETH obtido em um faucet.
- URL de RPC da Sepolia (Alchemy, Infura ou outro provedor).
- Project ID da Reown (cloud.reown.com).

## 2. Contrato

```bash
cd contracts
forge install            # dependências (forge-std)
forge test -vv           # deve passar 100%
forge test --gas-report  # registrar custo de registerCapture/registerEdit

export SEPOLIA_RPC_URL=...        # não versionar
export DEPLOYER_PRIVATE_KEY=...   # carteira de deploy, apenas testnet
forge script script/Deploy.s.sol --rpc-url $SEPOLIA_RPC_URL --broadcast --gas-estimate-multiplier 800
```

- `DEPLOYER_PRIVATE_KEY` precisa ter o prefixo `0x`; sem ele o script ignora a chave e o forge
  falha com "You seem to be using Foundry's default sender".
- A simulação local do forge subestima o gás de criação de contrato na Sepolia (≈1,4 M estimado
  contra ≈7,5 M exigido pela rede), e sem o multiplicador a transação falha com `OutOfGas`.
  Para conferir antes de enviar: `cast estimate --create $(forge inspect ImageRegistry bytecode) --from <carteira> --rpc-url $SEPOLIA_RPC_URL`.

Anotar o endereço e o bloco do deploy na tabela abaixo. O bloco aparece como `blockNumber` (hex) no
recibo em `contracts/broadcast/Deploy.s.sol/11155111/run-latest.json`.

| Data       | Endereço `ImageRegistry` (Sepolia)           | Bloco do deploy | Commit    |
| ---------- | -------------------------------------------- | --------------- | --------- |
| 2026-10-06 | `0xF2f42B34414936e456c6AC10c4844f456CAD65Ba` | 11858572        | `ac29e33` |

## 3. Aplicativo

`android/local.properties` (não versionado):

```properties
sepolia.rpcUrl=https://...
registry.address=0x...
# Bloco do deploy (tabela acima): a busca da transação de cada registro na genealogia começa nele.
# Sem ele, a busca vai desde a origem da rede, e RPCs públicos costumam recusar o intervalo.
registry.deployBlock=11858572
reown.projectId=...
```

**RPC e link da transação.** O contrato não guarda a transação de um registro; o Histórico da
imagem busca os eventos `ImageRegistered` com `eth_getLogs` desde `registry.deployBlock`. Planos
gratuitos de alguns provedores recusam essa busca (ex.: Chainstack responde HTTP 403, "Archive …
requests are not available on your current plan"). Nesse caso o Histórico continua funcionando, e o
link "Ver registro na rede" aparece só para as fotos registradas neste aparelho (vem do acervo
local). Para ter o link em todos os nós, troque `sepolia.rpcUrl` por um RPC da Sepolia que aceite
`eth_getLogs` num intervalo grande e reinstale o app (`./gradlew :app:installDebug`; o valor entra
no `BuildConfig`). `registry.address` e `registry.deployBlock` continuam os mesmos. Alguns provedores
limitam o intervalo de blocos (10 mil, 50 mil): como o intervalo cresce desde o deploy, a busca pode
passar a ser recusada com o tempo; aí ela precisa ser feita em partes no `RegistrationTxFinder`.

```bash
cd android
./gradlew :app:testDebugUnitTest          # unitários (hashing, payload, classificador)
./gradlew :app:connectedDebugAndroidTest  # instrumentados (com aparelho conectado)
./gradlew :app:installDebug
```

Aparelho por Wi-Fi: conecte por pareamento (`adb pair <ip>:<porta-de-pareamento> <código>`, em
Opções do desenvolvedor → Depuração por Wi-Fi → Parear com código), não por
`adb connect <ip>:<porta>`. Depois do pareamento o adb conecta sozinho pelo nome mDNS
(`adb-<serial>-<id>._adb-tls-connect._tcp`); confira em `adb devices`. Com um serial `ip:porta`,
o AGP 9.4.1 encerra `connectedDebugAndroidTest` com `BUILD FAILED` ("There were failing tests")
mesmo com todos os testes passando: o `:` é codificado como `%3A` no id do teste e o resultado do
aparelho não é encontrado (corrigido na linha 9.5 do AGP). Por USB ou no emulador o problema não
ocorre.

## 4. Roteiro de validação

### US1 — Captura e registro

1. Abrir o app sem sessão → tentar capturar → deve pedir conexão da carteira A.
2. Conectar com a carteira em outra rede → deve pedir troca para Sepolia.
3. Capturar foto → aprovar vínculo do aparelho (1ª vez) → aprovar registro.
4. Conferir: foto na galeria; status `Pendente` → `Confirmado`; link abre o Etherscan da Sepolia.
5. `cast call <registry> "getIdBySha256(bytes32)(uint256)" 0x<sha>` retorna o id do registro.
6. Chave perdida (R25): em Configurações do Android, apagar os dados do app → reconectar a carteira
   A → capturar → o app pede um novo vínculo de aparelho. Conferir com
   `cast call <registry> "getDevicesOf(address)(uint32[])" <carteiraA>` que há 2 aparelhos, e
   verificar a foto do passo 3: continua **Registrada – original** (assinatura válida pelo
   aparelho antigo).

### US2 — Verificação (sem login)

1. Desconectar a carteira.
2. Verificar o arquivo da US1 → **Registrada – original**, 0%.
3. Enviar o mesmo arquivo por um app de mensagens, salvar e verificar → **Correspondência visual**.
4. Verificar uma foto tirada pela câmera nativa → **Não registrada**.
5. Desligar a rede e verificar → **Não foi possível verificar** (não "Não registrada").
6. No WhatsApp ou na galeria, usar "Compartilhar": o HashLens **não** aparece na lista (FR-019).

### US3 — Edição própria

1. Com a carteira A, abrir a imagem da US1 → aplicar `grayscale` → salvar → aprovar.
2. Verificar o novo arquivo → **Registrada – versão alterada**, percentual > 0 (ou 0% com aviso).
3. Editar a versão novamente (`brightness:+30`) → confirmar que o pai é a versão anterior.

### US4 — Edição de terceiro

1. Transferir o arquivo original da US1 (bit a bit, ex.: cabo USB ou Drive) para o contexto da carteira B
   (pode ser o mesmo aparelho: cada carteira tem sua própria chave de aparelho, research R4).
2. Conectar carteira B → vincular o aparelho (novo `deviceId`) → importar para edição → aplicar
   `crop` → registrar.
3. Verificar → original com autor A, edição com responsável B.
4. Importar para edição a cópia recebida pelo app de mensagens (US2 passo 3) → recusada com a
   explicação de que só cópias exatas podem ser editadas (FR-015).

### US5 — Genealogia

1. Abrir a genealogia da versão de 3ª geração → caminho com 3 nós em ordem.
2. Abrir a genealogia da original → árvore com todas as versões (inclusive a da carteira B).

### US6 — Acervo e sessão

1. Capturar e **rejeitar** a transação → status `Falhou` → reenviar pelo acervo → `Confirmado`.
2. Capturar, aprovar e fechar o app imediatamente → reabrir → status atualizado sozinho.
3. Desconectar → captura e edição bloqueadas; verificação disponível.
4. Prazo de 30 min (R23): coberto pelo teste unitário do `PendingTxWorker` com relógio falso
   (sem recibo por 30 min → `Falhou`; reenvio com o hash já on-chain → `Confirmado` sem abrir a
   carteira). Manualmente: no build de debug, reduzir o prazo em `local.properties`
   (`registry.pendingTimeoutMinutes=1`), aprovar uma transação com gás muito baixo na carteira e
   conferir a passagem para **Não registrada – tentar de novo**.

## 5. Dados para o capítulo de resultados do TCC

Registrar em planilha: tempo de cálculo dos identificadores por resolução; tempo entre envio e
confirmação de cada transação; gás usado por operação; tempo total de verificação; matriz de
classificação (esperado × obtido) por tipo de edição.
