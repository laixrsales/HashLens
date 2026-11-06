---
description: "Task list for feature 020 - registro exclusivo pelo HashLens genuíno"
---

# Tasks: Registro Exclusivo pelo HashLens Genuíno

**Input**: Design documents from `/specs/020-registro-exclusivo-hashlens/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/, quickstart.md

**Tests**: Incluídos. A Constituição (Princípio IV) exige test-first para o contrato, o payload,
o classificador e, nesta feature, a política de atestação e a autorização EIP-712 (research R14).

**Organization**: Tarefas agrupadas por história de usuário (US1…US3 da spec). Os IDs recomeçam em
T001 nesta feature; para não confundir com os da 002 em mensagens de commit, cite `020/T0xx`.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: pode rodar em paralelo (arquivos diferentes, sem dependências pendentes)
- **[Story]**: história atendida (US1…US3)

## Path Conventions

- `$PKG/` = `android/app/src/main/java/br/ufv/hashlens/`
- `$TEST/` = `android/app/src/test/java/br/ufv/hashlens/`
- `$ITEST/` = `android/app/src/androidTest/java/br/ufv/hashlens/`
- `$ATT/` = `android/attestation/src/main/kotlin/br/ufv/hashlens/attestation/`
- `$ATT_TEST/` = `android/attestation/src/test/kotlin/br/ufv/hashlens/attestation/`
- `$ATR/` = `android/attester/src/main/kotlin/br/ufv/hashlens/attester/`
- `$ATR_TEST/` = `android/attester/src/test/kotlin/br/ufv/hashlens/attester/`
- `$SPEC/` = `specs/020-registro-exclusivo-hashlens/`
- `$SPEC002/` = `specs/002-registro-rastreamento-imagens/`
- Contrato: `contracts/`

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: módulos novos, dependências e configuração dos dois deploys.

- [ ] T001 Adicionar a biblioteca `android/keyattestation` como submódulo Git em `android/third_party/keyattestation`, fixada no commit mais recente da `main`, e incluí-la no build por `includeBuild` em `android/settings.gradle.kts`; anotar o commit em `$SPEC/research.md` R4
- [ ] T002 Criar o módulo Kotlin/JVM `:attestation` em `android/attestation/build.gradle.kts` (JDK 17, JUnit 5 + Kotest, dependência da biblioteca de T001 e do Bouncy Castle para Keccak) e registrá-lo em `android/settings.gradle.kts`
- [ ] T003 Criar o módulo `:attester` em `android/attester/build.gradle.kts` (plugin `application`, Ktor server Netty + content negotiation JSON, web3j core JVM, `ktor-server-test-host`, dependência de `:attestation`) e registrá-lo em `android/settings.gradle.kts`; versões no catálogo `android/gradle/libs.versions.toml`
- [ ] T004 Fazer o app depender de `:attestation` em `android/app/build.gradle.kts` e criar o teste instrumentado `$ITEST/attestation/AttestationLibraryOnDeviceTest.kt` que gera uma chave atestada e roda o `Verifier` da biblioteca no próprio aparelho (minSdk 26); se falhar, registrar o plano B (verificador com Bouncy Castle) em `$SPEC/research.md` R4 antes de seguir (⚠️ R4)
- [ ] T005 [P] Trocar `evm_version` para `"osaka"` em `contracts/foundry.toml`, mantendo `solc_version = "0.8.24"`, e confirmar `forge test` verde (research R2)
- [ ] T006 [P] Criar o keystore de release fora do Git e configurar `signingConfigs.release` em `android/app/build.gradle.kts` lendo caminho, alias e senhas de `local.properties` (`release.storeFile`, `release.storePassword`, `release.keyAlias`, `release.keyPassword`); anotar em `$SPEC/quickstart.md` §1 como obter o SHA-256 dos certificados de debug e de release (⚠️ R8)
- [ ] T007 [P] Ler `attester.url` e `attester.allowedCertDigests` de `local.properties` para `BuildConfig` em `android/app/build.gradle.kts` e expor em `$PKG/config/ChainConfig.kt` (`attesterUrl`, `allowedCertDigests: Set<String>` em hex minúsculo)
- [ ] T008 [P] Criar `android/app/src/debug/res/xml/network_security_config.xml` liberando texto claro só para `localhost` e referenciá-lo no manifesto de debug `android/app/src/debug/AndroidManifest.xml` (research R9); o build de release continua só HTTPS

**Checkpoint**: `./gradlew :attestation:build :attester:build :app:assembleDebug` e `forge test` verdes; decisão do R4 registrada.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: contrato v2 implantado no deploy de desenvolvimento, política de atestação testada e
camada de dados do app pronta para o vínculo (US1) e para a verificação (US3).

**⚠️ CRITICAL**: nenhuma história começa antes desta fase.

### Contrato v2 (test-first)

- [ ] T009 Gerar o vetor EIP-712 fixo de `$SPEC/contracts/device-authorization.md` §5 com `cast` (chave de atestador de teste, domínio com `verifyingContract` fixo, mensagem, `structHash`, `digest`, assinatura) em `contracts/test/vectors/device-authorization.json` e copiar o mesmo arquivo para `android/attester/src/test/resources/device-authorization.json`
- [ ] T010 Escrever testes de `registerDevice` v2 em `contracts/test/ImageRegistry.t.sol`: autorização válida (evento `DeviceAttested` com a cadeia, `attestationHash` = `keccak256(abi.encode(chain))`, `registeredBlock`); `InvalidPublicKey` ("`publicKey.length == 65` e `publicKey[0] == 0x04`"); `EmptyAttestation`; `AuthorizationExpired` (`deadline < block.timestamp`); `InvalidAuthorization` para carteira, chave, cadeia, `chainId` e contrato trocados, assinante diferente de `attester`, assinatura com tamanho ≠ 65 e `s > n/2`; reuso da autorização → `DeviceAlreadyRegistered`; conferência do digest contra o vetor de T009, com o contrato de teste colocado no `verifyingContract` do vetor por `deployCodeTo`; `attester()` e `domainSeparator()`
- [ ] T011 Escrever testes de assinatura P-256 em `contracts/test/ImageRegistry.t.sol` com `vm.signP256` e `vm.publicKeyP256`, sobre o payload v1 de `$SPEC002/contracts/signature-payload.md` §1: `registerCapture` e `registerEdit` com assinatura válida; assinatura de outra chave, dados alterados (sha, pHash, parentId, operations, deviceId, carteira), `s > n/2` e `r = s = 0` → `InvalidDeviceSignature`; fuzz do payload; um caso com os vetores de `android/app/src/test/resources/signature/` para provar que o contrato monta o mesmo payload do app. Adaptar os testes existentes, que usam assinaturas arbitrárias, para assinar com um helper `_sign(...)`
- [ ] T012 Adaptar `contracts/test/ImageRegistryScale.t.sol` à v2 (vínculo com autorização de teste; 10.000 registros assinados com `vm.signP256`) e confirmar que continua dentro do `gas_limit` da suíte
- [ ] T013 Copiar `$SPEC/contracts/IImageRegistry.sol` para `contracts/src/IImageRegistry.sol` e implementar a v2 em `contracts/src/ImageRegistry.sol` até T010–T012 passarem: `attester` e `domainSeparator` `immutable` (construtor recebe o atestador), `ecrecover` com low-S, `abi.encodePacked` do payload v1 + `sha256` + `staticcall` a `0x100` com `digest ‖ r ‖ s ‖ x ‖ y` (160 bytes), saída `1` = válida; remover `MissingSignature`
- [ ] T014 Atualizar `contracts/script/Deploy.s.sol` para ler `ATTESTER_ADDRESS` do ambiente; fazer o deploy de **desenvolvimento** na Sepolia com o atestador de desenvolvimento e registrar endereço, bloco, atestador e commit na tabela de `$SPEC/quickstart.md` §2; rodar `forge test --gas-report` e anotar o gás de `registerDevice`, `registerCapture` e `registerEdit` em `$SPEC/research.md` R10
- [ ] T015 Atualizar o ABI copiado em `android/app/src/main/assets/abi/ImageRegistry.json` (tarefa `copyRegistryAbi`) e `local.properties` (`registry.address`, `registry.deployBlock`) com o deploy de desenvolvimento

### Política de atestação (test-first, `:attestation`)

- [ ] T016 [P] Capturar a cadeia de referência real: teste instrumentado `$ITEST/attestation/CaptureReferenceChainTest.kt` que gera uma chave com o desafio de `$SPEC/contracts/attestation-policy.md` §2 e grava a cadeia (DER em base64) e o desafio em `getExternalFilesDir`; copiar com `adb pull` para `android/attestation/src/test/resources/attestation/real/` e descrever o aparelho (modelo, nível TEE/StrongBox, patch) em `android/attestation/src/test/resources/attestation/README.md`
- [ ] T017 [P] Criar o gerador de cadeias sintéticas de teste `$ATT_TEST/fixtures/TestChains.kt` (Bouncy Castle): raiz de teste injetável como âncora, intermediária e folha com a extensão de atestação (OID `1.3.6.1.4.1.11129.2.1.17`) parametrizável, com uma variação por motivo de recusa da policy §3 (`CHAIN_INVALID`, `REVOKED`, `NOT_HARDWARE`, `BOOT_NOT_VERIFIED`, `PATCH_TOO_OLD`, `APP_NOT_GENUINE`, `KEY_PROPERTIES`, `KEY_MISMATCH`, `CHALLENGE_INVALID`) e duas extensões na cadeia (só a primeira vale)
- [ ] T018 [P] Teste `$ATT_TEST/AttestationChallengeTest.kt`: 95 bytes, `HASHLENS-ATT-v1`, ordem e big-endian dos campos, leitura de volta, janela "`referenceTime − 15 min ≤ issuedAt ≤ referenceTime + 2 min`" nas duas bordas
- [ ] T019 [P] Teste `$ATT_TEST/AttestationHashTest.kt`: `keccak256(abi.encode(bytes[]))` igual ao valor de `cast keccak $(cast abi-encode "f(bytes[])" ...)` para 1, 3 e 5 certificados
- [ ] T020 Teste `$ATT_TEST/AttestationPolicyTest.kt`: cadeia real de T016 aceita (com as raízes do Google e `referenceTime` do momento da captura); cada variação de T017 recusada com o motivo esperado; ordem dos motivos quando várias regras falham (policy §3); patch exatamente com 24 meses aceito e com 24 meses + 1 recusado; janela do desafio nas bordas dos dois contextos da policy §2 (atestador: −15 min/+2 min; verificação: −30 min/+2 min em relação a `registeredAt`); `allowedCertDigests` sem o certificado → `APP_NOT_GENUINE`; `Accepted` com nível, patch, `issuedAt` e seriais em hex minúsculo
- [ ] T021 Implementar `$ATT/AttestationChallenge.kt`, `$ATT/AttestationHash.kt` e `$ATT/AttestationVerdict.kt` (tipos de `$SPEC/contracts/attestation-policy.md` §4) até T018 e T019 passarem
- [ ] T022 Implementar `$ATT/AttestationPolicy.kt` sobre o `Verifier` da biblioteca (ou do plano B de T004), com âncoras, fonte de revogação e instante de referência injetáveis, até T020 passar

### Camada de dados do app

- [ ] T023 [P] Atualizar `$TEST/data/chain/AbiMapperTest.kt` com respostas capturadas por `cast` no deploy de desenvolvimento: `getDevice` v2 (com `attestationHash` e `registeredBlock`) e log `DeviceAttested` (`bytes[]`)
- [ ] T024 Atualizar `$PKG/domain/model/Device.kt` conforme `$SPEC/data-model.md` §3 (`registeredAt: Instant`, `attestationHash: ByteArray`, `registeredBlock: Long`, com `equals`/`hashCode` por conteúdo) e `$PKG/data/chain/AbiMapper.kt` e `$PKG/data/chain/RegistryReader.kt` com `getDevice` v2 e `getAttestationChain(deviceId, block)` por `eth_getLogs` com `fromBlock = toBlock = registeredBlock` e tópico `deviceId`, até T023 passar
- [ ] T025 [P] Atualizar `$PKG/data/chain/RegistryTxBuilder.kt` com `registerDevice(publicKey, chain, deadline, authorization)` e o teste `$TEST/data/chain/RegistryTxBuilderTest.kt` contra calldata gerado com `cast calldata "registerDevice(bytes,bytes[],uint64,bytes)" ...`
- [ ] T026 [P] Adicionar `failureReason: BindingFailure?` a `$PKG/data/local/DeviceBindingEntity.kt` ("nova coluna anulável, sem perda de dados"; valores de `$SPEC/data-model.md` §2.1), com migração Room em `$PKG/data/local/AppDatabase.kt` e teste de migração em `$ITEST/data/local/MigrationTest.kt`
- [ ] T027 Atualizar `$PKG/core/crypto/DeviceKeyManager.kt`: `regenerateWithChallenge(wallet, challenge)` (apaga o alias e gera com `setAttestationChallenge`, mantendo StrongBox com fallback para TEE, `PURPOSE_SIGN`, `DIGEST_SHA256`, P-256, sem atestação de identificadores; research R11, R13) e `getAttestationChain(wallet): List<ByteArray>`; atualizar `$ITEST/core/crypto/DeviceKeyManagerTest.kt` (cadeia com ≥ 2 certificados, desafio presente na extensão, chave pública da folha igual à do alias)

**Checkpoint**: contrato v2 no deploy de desenvolvimento; política de atestação testada com cadeia real e sintéticas; leitura e escrita v2 do contrato prontas no app.

---

## Phase 3: User Story 1 - Só o HashLens genuíno consegue registrar (Priority: P1) 🎯 MVP

**Goal**: o HashLens genuíno vincula o aparelho pelo atestador e registra; qualquer outra origem é recusada pela rede.

**Independent Test**: roteiro US1 do `$SPEC/quickstart.md` §5 no deploy de desenvolvimento (passos 1, 2 e 3; o passo 4 e o SC-001 completo rodam no deploy de avaliação, Fase 6).

### Tests for User Story 1

- [ ] T028 [P] [US1] Teste `$ATR_TEST/DeviceAuthorizationSignerTest.kt`: digest e assinatura iguais ao vetor de `android/attester/src/test/resources/device-authorization.json` (T009), `v ∈ {27, 28}`, `s ≤ n/2`
- [ ] T029 [P] [US1] Teste `$ATR_TEST/RevocationListTest.kt`: leitura do JSON de `android.googleapis.com/attestation/status` (formato da documentação, chaves em hex minúsculo), respeito ao `Cache-Control`, uso do cache quando a rede falha e erro quando não há cache válido
- [ ] T030 [P] [US1] Teste `$ATR_TEST/AuthorizeRouteTest.kt` (`testApplication`): 200 com `deadline = agora + 15 min`, `attestationHash` e `authorization`; 422 com cada `RejectReason`; 400 `MALFORMED` (JSON, endereço, chave ou base64 inválidos); limites "corpo ≤ 32 KB, cadeia com 2 a 8 certificados"; 503 `REVOCATION_UNAVAILABLE`; `GET /v1/health`; o log não contém a cadeia nem a chave e a requisição só aceita `wallet`, `publicKey` e `chain` (FR-002)
- [ ] T031 [P] [US1] Teste `$TEST/data/attester/AttesterClientTest.kt` (MockWebServer): mapeamento completo da tabela de `$SPEC/contracts/attester-api.md` §4, timeout e sem conexão → `AttesterUnavailableException`
- [ ] T032 [P] [US1] Atualizar `$TEST/domain/usecase/EnsureDeviceBoundUseCaseTest.kt` (fluxo R11): chave já vinculada → não chama o atestador nem regenera; chave não vinculada → regenera com desafio da carteira atual, autoriza, confere saldo e envia `registerDevice` v2; recusa → `DeviceNotEligibleException` sem abrir a carteira e `failureReason` gravado; indisponível → `AttesterUnavailableException` sem abrir a carteira; `AuthorizationExpired` ou `CHALLENGE_INVALID`/`KEY_MISMATCH` → uma nova tentativa automática e, se repetir, falha; troca de carteira no meio → cancela e recomeça para a carteira atual

### Implementation for User Story 1

- [ ] T033 [P] [US1] Implementar `$ATR/DeviceAuthorizationSigner.kt` (EIP-712 de `$SPEC/contracts/device-authorization.md`) até T028 passar
- [ ] T034 [P] [US1] Implementar `$ATR/RevocationList.kt` até T029 passar
- [ ] T035 [US1] Implementar `$ATR/Main.kt` (variáveis de `$SPEC/contracts/attester-api.md` §3, falha na partida se faltar alguma) e `$ATR/AuthorizeRoute.kt` (`POST /v1/authorize`, `GET /v1/health`) usando `AttestationPolicy` com `referenceTime = agora`, até T030 passar
- [ ] T036 [US1] Criar as exceções e o motivo em `$PKG/domain/model/BindingErrors.kt` (`DeviceNotEligibleException(reason)`, `NotEligibleReason { MODIFIED, OUTDATED, NO_SECURE_HARDWARE, APP_NOT_GENUINE, REVOKED }`, `AttesterUnavailableException`) e `DeviceAuthorization` em `$PKG/domain/model/DeviceAuthorization.kt`; implementar `$PKG/data/attester/AttesterClient.kt` com módulo Hilt em `$PKG/di/AttesterModule.kt` até T031 passar
- [ ] T037 [US1] Reescrever `$PKG/domain/usecase/EnsureDeviceBoundUseCase.kt` conforme research R11 e `$SPEC/data-model.md` §2.2 até T032 passar; `RegistrationProgress` ganha a etapa `VerifyingDevice` antes de `BindingDevice` (FR-012)
- [ ] T038 [US1] Etapa "Verificando a integridade do aparelho" no progresso de registro, seguindo a skill `hashlens-ui`: string em `android/app/src/main/res/values/strings.xml`, estado em `$PKG/ui/capture/RegistrationProgressUiState.kt`, previews em `$PKG/ui/capture/RegistrationProgressScreen.kt` e screenshot em `$TEST/ui/capture/` (Roborazzi); **parar e mostrar os PNGs para aprovação da autora**
- [ ] T039 [US1] Após a aprovação, ligar a etapa ao `$PKG/ui/capture/CaptureViewModel.kt` e ao fluxo de edição (`$PKG/ui/editor/`), mapeando `RegistrationProgress.VerifyingDevice`
- [ ] T040 [US1] Teste instrumentado `$ITEST/evaluation/AttestedBindingTest.kt` no deploy de desenvolvimento, com o atestador rodando (`adb reverse tcp:8080 tcp:8080`): vínculo real e registro de uma captura confirmados (SC-002); anotar o tempo da etapa de integridade (SC-004)
- [ ] T041 [US1] Criar `tools/chain/tentar_origens_nao_autorizadas.py` (research R15): vínculo sem autorização, com autorização de outra carteira, vencida e reutilizada; registro com assinatura de outra chave e com dados alterados reaproveitando uma assinatura real; cada tentativa com `cast send --gas-limit` forçado, gravando arquivo, tentativa, erro esperado, hash, bloco e `status` em `docs/resultados/dados/recusas.csv`; opção `--simular`. Remover `tools/chain/registrar_adulterados.py` e atualizar as referências a ele em `tools/chain/montar_conjunto.sh` e `docs/resultados/matriz-classificacao.md`
- [ ] T042 [US1] Rodar `$ITEST/` completo (`./gradlew :app:connectedDebugAndroidTest`) e o roteiro US1 (passos 1 a 3) de `$SPEC/quickstart.md` §5 no aparelho, no deploy de desenvolvimento, e registrar o resultado no resumo da US

**Checkpoint**: US1 funcional: vínculo atestado e registro no aparelho; contrato recusa as origens não autorizadas.

---

## Phase 4: User Story 2 - Aparelho sem integridade é informado com clareza (Priority: P2)

**Goal**: recusas e falhas do atestador explicadas em linguagem leiga, sem abrir a carteira; o resto do app funciona.

**Independent Test**: roteiro US2 do `$SPEC/quickstart.md` §5 (emulador e atestador parado).

### Tests for User Story 2

- [ ] T043 [P] [US2] Teste `$TEST/ui/capture/CaptureMappingsTest.kt` (novo ou estendido): cada `NotEligibleReason` e `AttesterUnavailableException` vira o estado de UI correto, com "Tentar de novo" só para a falha temporária; a imagem continua `LOCAL_ONLY` e pode ser reenviada sem nova captura
- [ ] T044 [P] [US2] Estender `$TEST/domain/usecase/CaptureAndRegisterUseCaseTest.kt`, `$TEST/domain/usecase/EditAndRegisterUseCaseTest.kt` e `$TEST/domain/usecase/RetryRegistrationUseCaseTest.kt`: recusa ou indisponibilidade no vínculo não abre a carteira nem cobra taxa, e o reenvio depois do atestador voltar registra sem nova captura; aparelho já vinculado registra com o atestador fora do ar (US2 cenário 3)

### Implementation for User Story 2

- [ ] T045 [US2] Estados de erro do vínculo seguindo a skill `hashlens-ui` e `docs/referencia/ux.md`: textos em `android/app/src/main/res/values/strings.xml` para aparelho modificado, sistema desatualizado (patch com mais de 24 meses), sem hardware seguro, aplicativo não original, certificado revogado e falha temporária (com dica de conferir data e hora), usando `ErrorState`/`MessageState`; previews de todos os estados em `$PKG/ui/capture/` e `$PKG/ui/editor/` e screenshots em `$TEST/ui/capture/` e `$TEST/ui/editor/`; **parar e mostrar os PNGs para aprovação da autora**
- [ ] T046 [US2] Após a aprovação, ligar os estados aos ViewModels de captura, edição e reenvio (`$PKG/ui/capture/CaptureViewModel.kt`, `$PKG/ui/editor/`, `$PKG/ui/library/`) até T043 e T044 passarem
- [ ] T047 [US2] Rodar no emulador e no aparelho o roteiro US2 de `$SPEC/quickstart.md` §5 (SC-003; atestador parado com e sem vínculo; verificação, genealogia e acervo no emulador) e `./gradlew :app:connectedDebugAndroidTest`

**Checkpoint**: US1 e US2 funcionam; nenhuma recusa aparece como erro genérico.

---

## Phase 5: User Story 3 - A verificação mostra a origem atestada e qualquer pessoa pode auditá-la (Priority: P3)

**Goal**: a verificação revalida a atestação, mostra a origem e avisa sobre revogação; a decisão do atestador pode ser refeita fora do app.

**Independent Test**: roteiro US3 do `$SPEC/quickstart.md` §5.

### Tests for User Story 3

- [ ] T048 [P] [US3] Teste `$TEST/data/attestation/RevocationRepositoryTest.kt` (MockWebServer + cache do OkHttp): serial revogado ou suspenso → `REVOKED_AFTER_BINDING`; ausente → `NOT_REVOKED`; sem rede e sem cache → `UNKNOWN`
- [ ] T049 [P] [US3] Teste `$TEST/data/attestation/DeviceOriginVerifierTest.kt` (`$SPEC/contracts/attestation-policy.md` §5 e `$SPEC/contracts/verification-result.md` §1): hash da cadeia divergente → adulterado; política recusa → adulterado; `referenceTime = registeredAt` (certificado RKP já vencido hoje continua aceito); vínculo com desafio emitido 29 min antes de `registeredAt` aceito e 31 min antes recusado; carteira do desafio = `device.owner`; revogação não muda a aceitação; cache por `deviceId` na sessão; erro de rede no `eth_getLogs` → erro de rede, não adulterado
- [ ] T050 [P] [US3] Estender `$TEST/domain/usecase/VerifyImageUseCaseTest.kt` e `$TEST/domain/usecase/TraceGenealogyUseCaseTest.kt` com leitor falso (SC-008): assinatura inválida, cadeia adulterada, `attestationHash` divergente e cadeia de outro pacote → "Registro adulterado"; registro válido com `origin` preenchido; revogado → classificação mantida com aviso; revogação desconhecida → classificação mantida com nota; `VisualMatch` só com candidatos válidos

### Implementation for User Story 3

- [ ] T051 [P] [US3] Implementar `$PKG/data/attestation/RevocationRepository.kt` (GET `https://android.googleapis.com/attestation/status` com cache HTTP do OkHttp) até T048 passar
- [ ] T052 [US3] Criar `DeviceOrigin`, `SecurityLevel`, `BootState` e `RevocationStatus` em `$PKG/domain/model/DeviceOrigin.kt`, acrescentar `origin: DeviceOrigin` a `$PKG/domain/model/RecordView.kt` e implementar `$PKG/data/attestation/DeviceOriginVerifier.kt` (com `allowedCertDigests` do `ChainConfig`) até T049 passar
- [ ] T053 [US3] Integrar `DeviceOriginVerifier` em `$PKG/domain/usecase/VerifyImageUseCase.kt` e `$PKG/domain/usecase/TraceGenealogyUseCase.kt` (função `valid(r)` de `$SPEC/contracts/verification-result.md` §1) até T050 passar
- [ ] T054 [US3] Interface da origem seguindo a skill `hashlens-ui`: linha de origem, aviso de revogação (ícone, título e frase), nota de revogação não consultada, linhas novas em `TechnicalDetails` (nível de segurança, sistema verificado, patch mês/ano, data do vínculo) e novo rodapé em `$PKG/ui/verify/VerificationResultScreen.kt` e `$PKG/ui/verify/VerificationFacts.kt`; aviso no nó em `$PKG/ui/genealogy/`; frase de FR-016 em `$PKG/ui/onboarding/`; textos em `strings.xml`; previews de todos os estados e screenshots em `$TEST/ui/verify/VerifyScreenshotTest.kt`, `$TEST/ui/genealogy/GenealogyScreenshotTest.kt` e onboarding; **parar e mostrar os PNGs para aprovação da autora**
- [ ] T055 [US3] Após a aprovação, ligar os estados aos ViewModels em `$PKG/ui/verify/VerifyViewModel.kt`, `$PKG/ui/genealogy/` e `$PKG/ui/preview/SampleData.kt`
- [ ] T056 [US3] Ferramenta de auditoria independente (SC-006): `$ATR/AuditMain.kt` com a tarefa Gradle `:attester:audit` (`./gradlew :attester:audit --args="<deviceId>"`), que lê `getDevice` e o evento `DeviceAttested` por RPC, confere o hash e aplica `AttestationPolicy` sem a chave do atestador; atualizar o comando no roteiro US3 de `$SPEC/quickstart.md` §5
- [ ] T057 [US3] Rodar no aparelho o roteiro US3 de `$SPEC/quickstart.md` §5 (inclui bloqueio de `android.googleapis.com` e a auditoria de T056) e `./gradlew :app:connectedDebugAndroidTest`

**Checkpoint**: as três histórias funcionam no deploy de desenvolvimento.

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: deploy de avaliação, evidências para o TCC e revisão final.

- [ ] T058 Escolher o túnel HTTPS da avaliação (⚠️ R9) e anotar a escolha e os comandos em `$SPEC/research.md` R9 e `$SPEC/quickstart.md` §3
- [ ] T059 Deploy de **avaliação**: nova chave de atestador, `Deploy.s.sol` com o `ATTESTER_ADDRESS` dela, atestador com `ATTESTER_ALLOWED_CERT_DIGESTS` só com o release; registrar na tabela de `$SPEC/quickstart.md` §2 e gerar o APK de release (`./gradlew :app:assembleRelease`) apontando para esse deploy
- [ ] T060 Rodar `tools/chain/tentar_origens_nao_autorizadas.py` no deploy de avaliação e o passo 4 do roteiro US1 (APK de debug contra o atestador de avaliação → "aplicativo não original"); consolidar a tabela de recusas em `docs/resultados/recusas.md` com os hashes das transações revertidas (SC-001)
- [ ] T061 Refazer a matriz de classificação no deploy de avaliação (SC-007): adaptar `tools/chain/montar_conjunto.sh` e `$ITEST/evaluation/ClassificationMatrixTest.kt` à v2, trocar a linha do SC-003 da 002 pela recusa na rede (T060) e atualizar `docs/resultados/matriz-classificacao.md`; isto substitui a T101 pendente da 002
- [ ] T062 [P] Registrar em `docs/resultados/` o gás v2 contra v1 (`registerDevice`, `registerCapture`, `registerEdit`) e os tempos de SC-004 e SC-005 medidos no aparelho, com o tipo de conexão usado (Wi-Fi ou 4G, `adb reverse` ou túnel)
- [ ] T063 [P] Atualizar a documentação: notas de "superado pela 020" em `$SPEC002/research.md` R5 e R19 e na premissa correspondente de `$SPEC002/spec.md`; seção do atestador e dos dois deploys no `README.md`; comandos `:attester:run`, `:attester:test`, `:attestation:test` e `adb reverse` em `CLAUDE.md`; nota no topo de `$SPEC002/quickstart.md` §2 e no `README.md` apontando para a tabela de deploys de `$SPEC/quickstart.md` §2 (o contrato da 002 deixa de ser o atual)
- [ ] T064 [P] Auditoria de acessibilidade (ATF, como na 002 T097) dos estados novos de captura, edição, verificação, genealogia e onboarding
- [ ] T065 Revisão de segurança do atestador e dos segredos: nenhuma chave ou keystore no Git (`git ls-files`), logs sem cadeia nem chave, limites de requisição, `network_security_config` de release sem texto claro, `ATTESTER_PRIVATE_KEY` só por ambiente (FR-019); o atestador não guarda chaves de aparelho nem tem como registrar imagens (FR-007)
- [ ] T066 Rodar tudo: `forge fmt --check && forge test`, `./gradlew ktlintCheck detekt :attestation:test :attester:test :app:testDebugUnitTest :app:recordRoborazziDebug` e `:app:connectedDebugAndroidTest`; corrigir o que falhar

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (1)**: sem dependências. T002 e T003 dependem de T001; T004 depende de T002 e decide o plano B antes da Fase 2.
- **Foundational (2)**: depende de 1; bloqueia as histórias. T010–T012 dependem de T009 e T005; T013 depende de T010–T012; T014 de T013; T015 de T014; T020 depende de T016 e T017; T021 de T018 e T019; T022 de T020 e T021; T023 depende de T015; T024 de T023; T027 depende de T021 (desafio).
- **US1 (3)**: depende de 2. T035 depende de T022, T033 e T034; T037 de T027, T024–T026 e T036; T039 depende da aprovação de T038; T040 depende de T035, T037 e T039; T041 depende de T040 (usa um `deviceId` vinculado pelo app).
- **US2 (4)**: depende de US1 (T036, T037: as exceções e o fluxo de vínculo).
- **US3 (5)**: depende de 2 (T022, T024); pode avançar em paralelo à US1 com registros criados pelo instrumentado T040 ou pelo `cast` com um aparelho já vinculado.
- **Polish (6)**: depois das três histórias. T060 e T061 dependem de T059; T059 de T058.

### Within Each User Story

- Testes escritos e falhando antes da implementação.
- Tela sem estado com previews e screenshots aprovada pela autora antes da ligação ao ViewModel.
- Testes no aparelho (instrumentados + roteiro do quickstart) antes de dar a história por concluída.

### Parallel Opportunities

- Setup: T005–T008.
- Fundação: T016–T019 (fixtures e testes do `:attestation`) em paralelo aos testes do contrato T010–T012; T025 e T026.
- US1: T028–T032 (testes); T033 e T034.
- US3: T048–T050 (testes); T051.

---

## Parallel Example: Phase 2

```bash
Task: "Capturar cadeia real em $ITEST/attestation/CaptureReferenceChainTest.kt"
Task: "Gerador de cadeias sintéticas em $ATT_TEST/fixtures/TestChains.kt"
Task: "Teste AttestationChallengeTest em $ATT_TEST/AttestationChallengeTest.kt"
Task: "Teste AttestationHashTest em $ATT_TEST/AttestationHashTest.kt"
```

## Parallel Example: User Story 1

```bash
Task: "Teste DeviceAuthorizationSignerTest em $ATR_TEST/DeviceAuthorizationSignerTest.kt"
Task: "Teste RevocationListTest em $ATR_TEST/RevocationListTest.kt"
Task: "Teste AuthorizeRouteTest em $ATR_TEST/AuthorizeRouteTest.kt"
Task: "Teste AttesterClientTest em $TEST/data/attester/AttesterClientTest.kt"
Task: "Teste EnsureDeviceBoundUseCaseTest em $TEST/domain/usecase/EnsureDeviceBoundUseCaseTest.kt"
```

## Parallel Example: User Story 3

```bash
Task: "Teste RevocationRepositoryTest em $TEST/data/attestation/RevocationRepositoryTest.kt"
Task: "Teste DeviceOriginVerifierTest em $TEST/data/attestation/DeviceOriginVerifierTest.kt"
Task: "Teste VerifyImageUseCaseTest em $TEST/domain/usecase/VerifyImageUseCaseTest.kt"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Fases 1 e 2.
2. Fase 3 (US1): o requisito central do princípio IX.
3. **STOP and VALIDATE** com o roteiro US1 no deploy de desenvolvimento.

### Incremental Delivery

1. Setup + Foundational → contrato v2 no deploy de desenvolvimento e política testada.
2. US1 → vínculo atestado e recusas na rede (MVP).
3. US2 → recusas explicadas.
4. US3 → origem na verificação e auditoria.
5. Polish → deploy de avaliação, recusas, matriz refeita e métricas para o TCC.

---

## Notes

- Branches: seguir a memória de branches da autora (um grupo da fundação por branch, uma branch
  por história). A `feature/020-registro-exclusivo-hashlens` hoje tem só a documentação;
  **perguntar à autora antes de abrir cada branch nova** no `/speckit-implement`.
- O agente NÃO faz commits: ao fim de cada tarefa ou fase, para, resume as mudanças e sugere uma
  mensagem de commit; a autora revisa e commita.
- Marcar `[X]` logo ao concluir cada tarefa, editando `tasks.md` sem o Prettier (ele troca `[X]`
  por `[x]`).
- O contrato da 002 deixa de ser lido (Q3); as linhas antigas de `DeviceBindingEntity` são
  ignoradas porque o vínculo é sempre conferido no contrato configurado.
- Toda mudança no contrato exige `forge test` verde e novo endereço em `$SPEC/quickstart.md` §2.
