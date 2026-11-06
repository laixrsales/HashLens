# Research: Registro Exclusivo pelo HashLens Genuíno

**Feature**: `020-registro-exclusivo-hashlens` | **Date**: 2026-11-06

Formato por item: **Decision** / **Rationale** / **Alternatives considered**. Referências à feature
anterior usam o prefixo `002/` (ex.: `002/R5` é o item R5 de
`specs/002-registro-rastreamento-imagens/research.md`). Itens com ⚠️ dependem de algo a confirmar
na tarefa de setup.

Esta pesquisa supera `002/R5` (verificação da assinatura só off-chain) e `002/R19` (atestação fora
do escopo). Os demais itens da 002 continuam valendo.

---

## R1. Verificação P-256 on-chain

- **Decision**: o contrato verifica a assinatura do aparelho em `registerCapture` e `registerEdit`
  chamando o pré-compilado P256VERIFY (EIP-7951, endereço `0x100`). A mensagem é a mesma de
  `002/contracts/signature-payload.md` v1, montada com `abi.encodePacked` e resumida com o
  pré-compilado `sha256`. O contrato também exige `s ≤ n/2` (low-S, já obrigatório no payload v1).
  Assinatura inválida reverte com `InvalidDeviceSignature`.
- **Rationale**: confirmado em 2026-11-06 que o pré-compilado responde na Sepolia: um vetor válido
  devolve `1`, e um inválido devolve vazio (`cast call 0x…0100` contra
  `ethereum-sepolia-rpc.publicnode.com`). Custa 6.900 de gás. O payload v1 é concatenação binária
  sem separadores, exatamente o que `abi.encodePacked` produz, então o formato não muda e os
  vetores de `002/signature-payload.md` §4 continuam válidos.
- **Alternatives considered**: biblioteca P-256 em Solidity pura (centenas de milhares de gás, era
  o motivo de `002/R5`); manter só a verificação off-chain (não atende o princípio IX).

## R2. Foundry com Osaka

- **Decision**: `evm_version = "osaka"` em `contracts/foundry.toml`, mantendo `solc 0.8.24`.
- **Rationale**: testado em 2026-11-06 com forge 1.8.4: com `cancun`, a chamada a `0x100` falha
  no teste; com `osaka`, passa (7.913 de gás). A suíte atual (51 testes, inclusive o de escala de
  10.000 registros) passa com `--evm-version osaka`, então o teto de gás por transação do Osaka
  (EIP-7825) não afeta os testes. Os testes geram assinaturas P-256 com o cheatcode `vm.signP256`
  e a chave pública com `vm.publicKeyP256`.
- **Alternatives considered**: subir o solc para 0.8.30+ (não é necessário; pode ser feito à
  parte); simular o pré-compilado com `vm.etch` (testaria um mock, não o comportamento da rede).

## R3. Onde validar a atestação

- **Decision**: a cadeia de atestação é validada **off-chain**, no atestador (vínculo) e no
  aplicativo (verificação). O contrato só confere a autorização EIP-712 do atestador (R5).
- **Rationale**: validar X.509 on-chain exige parser ASN.1/DER em Solidity, verificação RSA-4096
  (raiz antiga) e ECDSA P-384 (raiz nova, sem pré-compilado) e não resolve a lista de revogação,
  que fica fora da cadeia e muda com o tempo. É o que a autora descartou na opção "a".
- **Alternatives considered**: validação completa on-chain (opção "a", descartada); prova ZK da
  validação (fora do alcance do TCC).

## R4. Biblioteca de validação da atestação

- **Decision**: usar a biblioteca Kotlin oficial `android/keyattestation`, recomendada pela
  documentação do Android, num módulo JVM compartilhado (`:attestation`) usado pelo atestador e
  pelo aplicativo. Ela valida a cadeia até as raízes do Google, trata a validade dos certificados
  RKP e de fábrica, consulta a lista de revogação e devolve chave pública, nível de segurança,
  estado de boot e dados do aplicativo. A política do HashLens (R7) fica numa camada própria por
  cima dela. ⚠️ A biblioteca não é publicada no Maven: entra como submódulo Git em
  `android/third_party/keyattestation`, fixado num commit, e incluída no build. Confirmar no
  setup que ela roda no Android (minSdk 26) além da JVM.
- **Rationale**: a documentação oficial diz que a biblioteca "é bem testada e cobre casos que
  verificadores próprios costumam esquecer" (só a primeira extensão da cadeia vale, expiração de
  chaves de fábrica × RKP, rotação de raízes). Um só código para o atestador e para o app garante
  que os dois decidem igual (SC-006).
- **Plano B, se não rodar no Android**: o aplicativo usa um verificador próprio com Bouncy Castle,
  seguindo o exemplo `googlesamples/android-key-attestation`, testado contra as mesmas cadeias
  de referência (R14) que o atestador.
- **Alternatives considered**: parser próprio com Bouncy Castle desde o início (mais código de
  segurança para manter); Play Integrity API (exige distribuição pela Play Store e um servidor do
  Google no caminho da verificação; não é auditável por terceiros).

## R5. Autorização do atestador (EIP-712)

- **Decision**: o atestador assina com uma chave secp256k1 própria uma mensagem EIP-712
  `DeviceAuthorization(address wallet, bytes32 publicKeyHash, bytes32 attestationHash, uint64
deadline)`, no domínio `{name: "HashLens ImageRegistry", version: "2", chainId,
verifyingContract}`. O contrato confere a assinatura com `ecrecover` (com checagem de low-S) e a
  compara com o endereço `attester`, fixo (`immutable`) no deploy. `wallet` precisa ser
  `msg.sender`, e `deadline` precisa ser ≥ `block.timestamp`. O formato normativo está em
  [contracts/device-authorization.md](./contracts/device-authorization.md).
- **Rationale**: EIP-712 é o padrão para autorizações assinadas off-chain e verificadas on-chain,
  com `ecrecover` nativo (~3.000 de gás). O domínio amarra rede e contrato (FR-004), e
  `attestationHash` amarra a cadeia publicada à decisão do atestador (FR-006). Sem nonce: a
  autorização vale para uma chave pública, e o contrato já recusa a mesma chave duas vezes
  (`DeviceAlreadyRegistered`), então ela não pode ser reutilizada.
- **Troca da chave do atestador**: exige novo deploy (o endereço é `immutable`). Não há função de
  administração no contrato: um dono capaz de trocar o atestador seria mais um ponto de confiança.
- **Alternatives considered**: `setAttester` com `owner` (mais flexível, mais confiança);
  atestador enviando a transação de vínculo (exigiria saldo no atestador e quebraria "a carteira da
  pessoa vincula").

## R6. Desafio da atestação

- **Decision**: o desafio é montado pelo **aplicativo**, sem ida ao atestador:
  `"HASHLENS-ATT-v1" ‖ chainId (32 B) ‖ contrato (20 B) ‖ carteira (20 B) ‖ emitidoEm (8 B,
segundos Unix)`, 95 bytes (o limite do Keystore é 128). O atestador aceita se rede, contrato e
  carteira forem os da requisição e se `emitidoEm` estiver entre 15 min no passado e 2 min no
  futuro. Na verificação, a janela vai até 30 min antes de `registeredAt` (15 min até o atestador
  e mais 15 min de prazo da autorização). O uso único vem do contrato (R5). Detalhes em
  [contracts/attestation-policy.md](./contracts/attestation-policy.md) §2.
- **Rationale**: o desafio fica público e verificável por qualquer pessoa (FR-006, princípio V),
  sem segredo e sem estado no atestador (princípio VII: "sem estado de negócio"). Ele amarra a
  atestação a uma carteira, rede e contrato, e o prazo impede o uso de atestações antigas.
- **Interpretação do FR-001/FR-003** ("desafio emitido para aquela carteira e ainda não usado"):
  o desafio é ligado à carteira pelo conteúdo, e "não usado" é garantido pela unicidade da chave
  on-chain, sem lista de desafios no atestador.
- **Alternatives considered**: desafio aleatório emitido pelo atestador com HMAC (estado mínimo,
  mas terceiros não conseguem conferir a ligação com a carteira); nonce guardado num banco do
  atestador (estado, contrário ao princípio VII).

## R7. Política de aceitação

- **Decision**: o atestador autoriza somente se todas as regras de
  [contracts/attestation-policy.md](./contracts/attestation-policy.md) §3 valerem: cadeia válida
  até uma raiz do Google (RSA antiga e ECDSA P-384 nova); nenhum certificado revogado ou suspenso;
  `attestationSecurityLevel` TEE ou StrongBox; `verifiedBootState = Verified` e `deviceLocked =
true`; `osPatchLevel` com no máximo 24 meses em relação a `emitidoEm`; pacote `br.ufv.hashlens`
  com algum certificado da lista do deploy; finalidade só `SIGN`, algoritmo EC, curva P-256; chave
  da folha igual à chave pública enviada; desafio válido (R6).
- **Rationale**: transcreve FR-003 com os esclarecimentos de 2026-11-06 (só boot verificado do
  fabricante; patch de até 24 meses). As raízes vêm de `android.googleapis.com/attestation/root`.
  A nova raiz ECDSA P-384 assina cadeias desde 2026-02-01, e as antigas continuam válidas para
  chaves de fábrica, então as duas são aceitas.
- **Alternatives considered**: aceitar `SelfSigned` (descartado na clarificação); sem limite de
  patch (descartado na clarificação).

## R8. Certificados aceitos por deploy

- **Decision**: cada deploy tem uma lista de SHA-256 de certificados de assinatura do APK:
  desenvolvimento = {debug, release}; avaliação = {release}. A lista é configuração do atestador
  (variável de ambiente) e do aplicativo (`BuildConfig`, para a verificação). Os dois deploys do
  contrato têm atestadores com chaves diferentes, então uma autorização de desenvolvimento não
  vale no contrato de avaliação.
- **Rationale**: clarificação Q1 (B). Com chaves de atestador diferentes, o contrato de avaliação
  só aceita o que o atestador de avaliação autorizou, mesmo que alguém aponte um APK de debug para
  ele.
- **Chave de release**: keystore de release próprio da autora, fora do Git (como
  `local.properties`), configurado em `signingConfigs.release`. ⚠️ Criar na tarefa de setup se
  ainda não existir.
- **Alternatives considered**: um só deploy aceitando debug e release (enfraquece o princípio IX,
  opção C da clarificação).

## R9. Serviço atestador

- **Decision**: serviço HTTP mínimo em Kotlin/JVM com Ktor, no módulo Gradle `:attester` dentro de
  `android/`, reaproveitando `:attestation` (R4) e o web3j JVM para EIP-712. Um único endpoint,
  `POST /v1/authorize`, contrato em [contracts/attester-api.md](./contracts/attester-api.md). Sem
  banco de dados. A configuração vem de variáveis de ambiente: chave do atestador, chainId,
  contrato, lista de certificados e porta.
- **Rationale**: mesma linguagem e mesmo código de validação do app (R4); o build Gradle já
  existe. Um endpoint só, porque o desafio é montado pelo app (R6).
- **Execução** (decisão da autora em 2026-11-06: só local, com túnel):
  - **Desenvolvimento**: `./gradlew :attester:run` no computador da autora; o aparelho acessa por
    `adb reverse tcp:8080 tcp:8080` e `http://localhost:8080`, o que só é aceito no build de debug
    (`network_security_config` de debug).
  - **Avaliação**: o mesmo processo exposto por um túnel HTTPS gratuito. Recomendado: domínio fixo
    gratuito (Cloudflare Tunnel com hostname nomeado ou ngrok com domínio estático), para não
    recompilar o app a cada execução. Com túnel rápido (`trycloudflare.com`), o endereço muda e
    precisa ser atualizado em `local.properties` e o APK de release, recompilado. ⚠️ Escolher o
    túnel na tarefa de setup.
  - O computador precisa estar ligado só durante **vínculos**; registros em aparelhos já
    vinculados não passam pelo atestador (US2 cenário 3).
- **Logs**: o atestador registra só o resultado (autorizado ou recusado, com o motivo) e o
  endereço da carteira abreviado; nunca a cadeia completa nem a chave.
- **Limite de requisições**: não implementado. Uma recusa não custa nada a ninguém, e uma
  autorização só serve para a própria chave TEE da pessoa. O risco é só de negação de serviço,
  aceito na PoC.
- **Alternatives considered**: Cloud Run e Render (apresentados à autora, que preferiu não criar
  conta nem cartão); servidor em Node/Python (duplicaria a validação do app).

## R10. Publicação da cadeia de atestação

- **Decision**: `registerDevice` recebe a cadeia como `bytes[]` (DER, da folha para a raiz),
  calcula `attestationHash = keccak256(abi.encode(chain))`, grava `attestationHash` e o número do
  bloco no `Device` e emite `DeviceAttested(deviceId, chain)`. A cadeia não fica em storage.
- **Rationale**: ~2 a 4 KB de cadeia em storage custariam milhões de gás; em evento custa uma
  fração (calldata + log) e fica pública para sempre (FR-006). O `attestationHash` em storage
  permite conferir que o evento lido é o original, e o bloco gravado permite buscar o evento com
  `eth_getLogs` num único bloco, dentro do limite de intervalo dos nós públicos. ⚠️ Medir o gás
  real de `registerDevice` nos testes (estimativa: 200 a 400 mil).
- **Alternatives considered**: IPFS com hash on-chain (outro serviço para manter; dado pode
  sumir); cadeia em storage (custo).

## R11. Fluxo de vínculo no aplicativo

- **Decision**: `EnsureDeviceBound` passa a:
  1. buscar a chave do alias em `getDevicesOf(carteira)` (como antes); se estiver vinculada, termina;
  2. caso contrário, **apagar e gerar de novo** a chave do alias com
     `setAttestationChallenge(desafio R6)`, porque o desafio só pode ser definido na geração;
  3. ler `getCertificateChain(alias)` e enviar a cadeia ao atestador;
  4. com a autorização, conferir saldo e abrir a carteira para
     `registerDevice(publicKey, chain, deadline, signature)`;
  5. se a autorização vencer antes do envio (carteira demorou), repetir de 2 automaticamente, uma
     vez.

  Recusas do atestador viram `DeviceNotEligibleException(motivo)`; falhas de rede viram
  `AttesterUnavailableException`. As duas acontecem antes de abrir a carteira (FR-010, FR-011).

- **Rationale**: chaves geradas antes desta feature, ou com desafio vencido, não têm atestação
  utilizável. Apagar só acontece se a chave não está vinculada no contrato atual, então nenhum
  vínculo vigente é perdido (a perda no contrato antigo é irrelevante, Q3).
- **StrongBox**: mantém o fallback de `002/R4`; a política aceita TEE e StrongBox.

## R12. Verificação no aplicativo

- **Decision**: para cada registro exibido, `VerifyImage` passa a:
  1. ler `Device` (com `attestationHash` e `registeredBlock`) e o evento `DeviceAttested` daquele
     bloco;
  2. conferir `keccak256(abi.encode(chain)) == attestationHash`;
  3. validar a cadeia com a mesma política do atestador (R7), com três diferenças: o instante de
     referência da validade é `registeredAt` (certificados RKP expiram depois do vínculo); a
     carteira esperada no desafio é `device.owner`; o desafio pode ter até 30 min antes de
     `registeredAt` (attestation-policy.md §2); a lista de revogação é tratada à parte (passo 4);
  4. consultar a lista de revogação: se algum certificado da cadeia aparece nela, manter a
     classificação e marcar `revokedAfterBinding` (FR-017). Como o atestador já recusa cadeias
     revogadas no vínculo, qualquer revogação encontrada depois é posterior a ele;
  5. falha em 2 ou 3, ou assinatura inválida (`002/FR-022`), resulta em "Registro adulterado"
     (FR-013, defesa redundante).
- **Lista de revogação**: `GET https://android.googleapis.com/attestation/status`, cache HTTP do
  OkHttp respeitando `Cache-Control`. Se não for possível consultar, a classificação não muda e o
  resultado mostra "não foi possível consultar a revogação" (FR-017).
- **Cache por aparelho**: o resultado da validação da cadeia (passos 1 a 3) é guardado em memória
  por `deviceId` durante a sessão; a revogação segue o cache HTTP.
- **Rationale**: atende FR-013, FR-014 e FR-017 e mantém a verificação sem login e sem custo
  (leituras RPC e um GET público). A documentação do Android recomenda não validar atestação no
  **próprio** aparelho atestado; aqui o app valida a atestação de **outros** aparelhos, lida da
  rede, o que equivale a um verificador independente.

## R13. Privacidade da cadeia publicada

- **Decision**: a geração da chave não pede atestação de identificadores (sem
  `setDevicePropertiesAttestationIncluded`, IMEI ou número de série). A cadeia publicada contém
  pacote e certificado do app, versão e patch do sistema, estado de boot e hash da chave de boot do
  fabricante, que identificam modelo e versão de firmware, não a pessoa.
- **Consequência aceita**: com RKP, os certificados intermediários são provisionados por aparelho
  e rodam periodicamente. Duas carteiras vinculadas no mesmo aparelho dentro do mesmo período de
  rotação podem ser relacionadas por quem comparar as cadeias públicas. A pessoa já é pseudônima
  (carteira), e nenhum pixel ou dado pessoal é publicado (princípio VI). Fica registrado como
  limitação no texto do TCC.
- **Alternatives considered**: publicar só o hash da cadeia (impediria a auditoria independente,
  FR-006).

## R14. Testes da atestação

- **Decision**:
  - **Contrato (Foundry, test-first)**: autorização válida, carteira, chave, rede, contrato ou
    prazo trocados, assinante errado, s alto no EIP-712, chave já vinculada; P-256 válida,
    inválida, de outra chave, com dados trocados e com s alto; evento e `attestationHash`; fuzz no
    payload; relatório de gás.
  - **Política (`:attestation`, JVM)**: cadeias de referência em `test/resources/attestation/`.
    Cadeias reais do aparelho da autora, capturadas por um teste instrumentado (debug), e cadeias
    públicas de teste da `android/keyattestation`, mais variações montadas (pacote errado,
    certificado fora da lista, patch antigo, boot não verificado, nível software, raiz não Google,
    desafio vencido ou de outra carteira, folha com chave diferente). As cadeias reais são
    publicadas on-chain de qualquer forma, então podem ficar no repositório.
  - **EIP-712 (`:attester`)**: vetor fixo gerado por `cast` (digest e assinatura) e conferido no
    Kotlin e no Foundry.
  - **App**: `EnsureDeviceBound` com atestador e Keystore falsos (recusa, indisponível, autorização
    vencida e repetição); `VerifyImage` com leitor falso devolvendo assinatura inválida, cadeia
    adulterada, hash da cadeia divergente e revogação (SC-008).
  - **Aparelho**: instrumentado de vínculo e registro reais no deploy de desenvolvimento (SC-002);
    emulador recusado (SC-003).
- **Rationale**: princípio IV (test-first em contrato, payload e classificador; agora também
  política e autorização).

## R15. Avaliação: tentativas de origens não autorizadas

- **Decision**: novo script `tools/chain/tentar_origens_nao_autorizadas.py` que, no deploy de
  avaliação, envia cada tentativa do SC-001 **forçando o envio** (`cast send --gas-limit`, sem
  estimativa, para que a transação seja minerada e revertida), e grava hash, erro e bloco em
  `docs/resultados/dados/recusas.csv`. Tentativas: vínculo sem autorização, com autorização de
  outra carteira, vencida ou reutilizada; registro com assinatura de outra chave e com dados
  alterados após assinar (reaproveitando uma assinatura real de um registro existente). "App
  reassinado" é medido pelo APK de debug contra o atestador de avaliação (recusa
  `APP_NOT_GENUINE`). `registrar_adulterados.py` é aposentado: sua premissa (o contrato aceita
  assinatura inválida, `002/R5`) deixou de valer.
- **Rationale**: SC-001 e SC-007; a transação revertida on-chain é a evidência pública da recusa.
  As tentativas de registro usam um `deviceId` real da carteira de avaliação (vinculado pelo app),
  porque nenhuma outra chave consegue ser vinculada.

## R16. Novo deploy

- **Decision**: dois deploys do novo `ImageRegistry`, desenvolvimento e avaliação, cada um com seu
  `attester`. `Deploy.s.sol` recebe o endereço do atestador por variável de ambiente.
  `local.properties` ganha `attester.url` e `attester.allowedCertDigests` e atualiza
  `registry.address` e `registry.deployBlock`. O contrato anterior não é lido (Q3), e o
  `quickstart.md` registra os novos endereços.
- **Rationale**: FR-018 e a regra da constituição "toda alteração no contrato exige novo endereço
  registrado em `quickstart.md`".
