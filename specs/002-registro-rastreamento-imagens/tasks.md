---
description: "Task list for feature 002 - registro, verificação e rastreamento de imagens"
---

# Tasks: Registro, Verificação e Rastreamento de Alterações em Imagens

**Input**: Design documents from `/specs/002-registro-rastreamento-imagens/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/

**Tests**: Incluídos. A Constituição (Princípio IV) exige test-first para contrato, hashing,
payload de assinatura e classificador de verificação; a máquina de estados com prazo (R23) e o
vínculo de aparelho (R4/R25) também são test-first.

**Organization**: Tarefas agrupadas por história de usuário para implementação e teste independentes.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: pode rodar em paralelo (arquivos diferentes, sem dependências)
- **[Story]**: história atendida (US1…US6)

## Path Conventions

- `$PKG/` = `android/app/src/main/java/br/ufv/hashlens/`
- `$TEST/` = `android/app/src/test/java/br/ufv/hashlens/`
- `$ITEST/` = `android/app/src/androidTest/java/br/ufv/hashlens/`
- `$RES/` = `android/app/src/test/resources/`
- `$SPEC/` = `specs/002-registro-rastreamento-imagens/`
- Contrato: `contracts/`

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: inicialização do monorepo

- [X] T001 Criar estrutura do monorepo (`contracts/`, `android/`) e `.gitignore` cobrindo `local.properties`, `contracts/out/`, `contracts/cache/`, `.env`
- [X] T002 Inicializar projeto Foundry em `contracts/foundry.toml` (solc 0.8.24, optimizer 200 runs) com `forge-std`
- [X] T003 Inicializar projeto Android (Kotlin, Compose, minSdk 26, JVM 17) em `android/` com catálogo `android/gradle/libs.versions.toml`, fixando as versões estáveis atuais dos itens ⚠️ de `$SPEC/research.md` (Reown AppKit, web3j, Roborazzi)
- [X] T004 [P] Adicionar dependências (Compose BOM, Navigation, Hilt, Room, CameraX, WorkManager, ExifInterface, OpenCV, web3j, Reown AppKit, JUnit5, MockK, Kotest, Robolectric) em `android/app/build.gradle.kts`
- [X] T005 [P] Ler `sepolia.rpcUrl`, `registry.address`, `reown.projectId` e `registry.pendingTimeoutMinutes` (opcional, padrão 30; research R23) de `local.properties` para `BuildConfig` em `android/app/build.gradle.kts` e expor em `$PKG/config/ChainConfig.kt` (chainId 11155111, URL do explorador, prazo de pendência)
- [X] T006 [P] Conferir `.editorconfig` (ktlint, estilo `android_studio`) e configurar detekt em `android/`; `[fmt]` em `contracts/foundry.toml`
- [X] T007 Criar tarefa Gradle `copyRegistryAbi` que copia `contracts/out/ImageRegistry.sol/ImageRegistry.json` para `android/app/src/main/assets/abi/ImageRegistry.json`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: contrato, núcleo criptográfico e leitura da blockchain, usados por todas as histórias

**⚠️ CRITICAL**: nenhuma história começa antes desta fase

### Contrato (test-first)

- [X] T008 Escrever testes em `contracts/test/ImageRegistry.t.sol`: `registerDevice` (válido, chave inválida — "`publicKey.length == 65` e `publicKey[0] == 0x04`" —, chave duplicada na mesma carteira, mesma chave por outra carteira → `DeviceAlreadyRegistered`, mesma carteira com duas chaves → `getDevicesOf` retorna os dois ids); `registerCapture` (sucesso, SHA zero, SHA duplicado, device inexistente, device de outra carteira, assinatura ausente); `registerEdit` (sucesso, pai inexistente, `originalId` herdado, edição por outra carteira permitida, `operations` vazio/>256 bytes); getters; eventos; fuzz de ids e hashes
- [X] T009 Implementar `contracts/src/ImageRegistry.sol` conforme `$SPEC/contracts/IImageRegistry.sol` até T008 passar
- [X] T010 Criar `contracts/script/Deploy.s.sol`, implantar na Sepolia e registrar o endereço na tabela de `$SPEC/quickstart.md`

### Núcleo de hashing e assinatura (test-first)

- [X] T011 [P] Adicionar imagens golden produzidas pela autora em `$RES/golden/` (original, edição leve, comprimida, preto e branco, outro ângulo, rotacionada via EXIF) e `$RES/golden/expected.json` gerado pela implementação de referência (ver research R9)
- [X] T012 [P] Teste `$TEST/core/hashing/Sha256HasherTest.kt` (vetores NIST + arquivos golden)
- [X] T013 [P] Teste `$TEST/core/hashing/PerceptualHasherTest.kt` (golden, determinismo em 100 execuções, orientação EXIF, formato hex de 16 caracteres minúsculos) — Robolectric com OpenCV nativo ou mover para `$ITEST/` se necessário
- [X] T014 [P] Teste `$TEST/core/hashing/HammingTest.kt` (d=0, d=64, percentual arredondado a 1 casa)
- [X] T015 [P] Teste `$TEST/core/crypto/SignaturePayloadTest.kt` (195 bytes, ordem e big-endian de cada campo, `operationsHash` de string vazia)
- [X] T016 [P] Teste `$TEST/core/crypto/SignatureCodecTest.kt` (DER ↔ `r‖s`, normalização low-S, vetores de `$RES/signature/`)
- [X] T017 [P] Teste `$TEST/domain/model/EditOperationCodecTest.kt` (ida e volta de todas as operações, locale pt-BR serializa com `.`, limite de 256 bytes, entrada malformada)
- [X] T018 [P] Teste `$TEST/ui/common/PercentFormatTest.kt` (exibição pt-BR com vírgula: `12.5` → "≈ 12,5% de alteração visual"; `0.0` → "Sem alteração visual detectável"; `0.0` em `RegisteredEdited` → "Sem alteração visual detectável — mas este arquivo não é idêntico à original") — research R24, spec Edge Cases, hashing-spec §3
- [X] T019 [P] Implementar `$PKG/core/hashing/Sha256Hasher.kt` (streaming sobre `InputStream`)
- [X] T020 [P] Implementar `$PKG/core/hashing/PerceptualHasher.kt` conforme `$SPEC/contracts/hashing-spec.md` §2
- [X] T021 [P] Implementar `$PKG/core/hashing/Hamming.kt`
- [X] T022 [P] Implementar `$PKG/core/crypto/SignaturePayload.kt` e `$PKG/core/crypto/SignatureCodec.kt`
- [X] T023 [P] Implementar `$PKG/domain/model/EditOperation.kt` e `$PKG/domain/model/EditOperationCodec.kt`
- [X] T024 [P] Implementar `$PKG/ui/common/PercentFormat.kt` até T018 passar
- [X] T025 Implementar `$PKG/core/crypto/DeviceSignatureVerifier.kt` (depende de T022) com teste `$TEST/core/crypto/DeviceSignatureVerifierTest.kt`
- [X] T026 [P] Criar modelos `$PKG/domain/model/ImageRecord.kt`, `Device.kt`, `RecordView.kt` conforme `$SPEC/data-model.md` §3

### Leitura da blockchain

- [X] T027 [P] Teste `$TEST/data/chain/AbiMapperTest.kt` decodificando respostas de `getRecord`/`getDevice`/`getDevicesOf` capturadas com `cast`
- [X] T028 Implementar `$PKG/data/chain/AbiMapper.kt` e `$PKG/data/chain/RegistryReader.kt` (todas as funções `view` via web3j, sem carteira; erros de rede como exceção tipada)

### Infraestrutura do app

- [X] T029 Criar `$PKG/HashLensApp.kt` (Hilt + `OpenCVLoader.initLocal()`), `$PKG/MainActivity.kt` (sem `intent-filter` de `ACTION_SEND`/`ACTION_VIEW` em `android/app/src/main/AndroidManifest.xml`; research R16), `$PKG/ui/theme/` e `$PKG/ui/navigation/HashLensNavHost.kt` com rotas vazias
- [X] T030 [P] Módulos Hilt em `$PKG/di/` (`ChainModule`, `CryptoModule`, `DatabaseModule`, `DispatchersModule`, `ClockModule` com `java.time.Clock` injetável para os testes de prazo)
- [X] T031 [P] Implementar `$PKG/core/imaging/JpegEncoder.kt` (rotação física, q=95, EXIF `Orientation=1`, `DateTimeOriginal`, `Software`, sem GPS) e `$PKG/core/imaging/ImageDecoder.kt` (aplica orientação EXIF), com teste `$TEST/core/imaging/JpegEncoderTest.kt`
- [X] T032 [P] Utilitário de permissões em `$PKG/ui/common/Permissions.kt` (câmera; explicação + atalho para configurações)

### Fundação de UI (Princípio VIII)

- [X] T033 Propor a direção visual em `docs/design/direcao-visual.md` seguindo `.claude/skills/hashlens-ui/SKILL.md` e **aguardar aprovação da autora**
- [X] T034 Implementar tokens em `$PKG/ui/theme/` (`Color.kt`, `Type.kt`, `Shape.kt`, `Spacing.kt`, `Theme.kt`) conforme a direção aprovada
- [X] T035 [P] Configurar Roborazzi em `android/app/build.gradle.kts` e base de screenshot tests em `$TEST/ui/ScreenshotTest.kt`
- [X] T036 [P] Criar dados fictícios em `$PKG/ui/preview/SampleData.kt` cobrindo todos os estados de `docs/referencia/ux.md`, incluindo "falhou por prazo" e "novo vínculo de aparelho"
- [X] T037 Criar componentes em `$PKG/ui/components/` (`StatusHeader`, `StepProgress`, `AlterationMeter`, `TechnicalDetails`, `AddressChip`, `EmptyState`, `ErrorState`) com previews de todos os estados e screenshot tests em `$TEST/ui/components/`
- [ ] T038 Criar telas sem estado Início e Como funciona em `$PKG/ui/home/` e `$PKG/ui/onboarding/` com previews e screenshot tests; **revisão da autora**

**Checkpoint**: contrato implantado; hashing, payload e leitura testados; direção visual aprovada e componentes base prontos.

---

## Phase 3: User Story 1 - Capturar e registrar uma imagem (Priority: P1) 🎯 MVP

**Goal**: capturar, salvar na galeria e registrar a imagem original com assinatura do aparelho e da carteira, acompanhando o registro até a confirmação ou o prazo de 30 min.

**Independent Test**: roteiro US1 do `$SPEC/quickstart.md` (inclui o passo 6, chave perdida).

### Tests for User Story 1 ⚠️

- [ ] T039 [P] [US1] Teste instrumentado `$ITEST/core/crypto/DeviceKeyManagerTest.kt` (alias `hashlens-device-<endereço em minúsculas>`; carteiras diferentes geram chaves diferentes; chave não exportável; assinatura verificável com a pública; apagar o alias e pedir de novo gera chave nova) — research R4, R25
- [ ] T040 [P] [US1] Teste instrumentado `$ITEST/data/gallery/GalleryRepositoryTest.kt` (bytes relidos == bytes gravados)
- [ ] T041 [P] [US1] Teste `$TEST/domain/usecase/EnsureDeviceBoundUseCaseTest.kt` com `RegistryReader` falso (chave já em `getDevicesOf` → nenhuma transação; alias ausente → gera e chama `registerDevice`; chave nova com aparelho antigo on-chain → novo `deviceId`, antigo intacto; segunda carteira no mesmo aparelho → `deviceId` próprio; `DeviceBindingEntity.publicKeyHex` substituído quando a chave muda)
- [ ] T042 [P] [US1] Teste `$TEST/domain/usecase/CaptureAndRegisterUseCaseTest.kt` (aborta se SHA relido diverge com `failureReason = HASH_MISMATCH`; transições `LOCAL_ONLY → AWAITING_WALLET → PENDING` gravando `txHash` e `submittedAt`; rejeição → `FAILED(REJECTED)`; SHA já registrado → nenhuma transação; resultado `AlreadyRegistered(recordId)`)
- [ ] T043 [P] [US1] Teste `$TEST/data/chain/RegistryTxBuilderTest.kt` (calldata igual ao gerado por `cast calldata` para os mesmos argumentos)
- [ ] T044 [P] [US1] Teste `$TEST/work/PendingTxWorkerTest.kt` com `Clock` falso (recibo status=1 → `CONFIRMED(recordId)`; status=0 com `getIdBySha256 = 0` → `FAILED(REVERTED)`; status=0 com `getIdBySha256 ≠ 0` (transação antiga confirmada depois do reenvio, nova reverte com `AlreadyRegistered`) → `CONFIRMED` com o `recordId` on-chain; 30 min desde `submittedAt` sem recibo e `getIdBySha256 = 0` → `FAILED(TIMEOUT)`; idem com `getIdBySha256 ≠ 0` → `CONFIRMED`; 29 min → continua `PENDING`; erro de rede na reconciliação → continua `PENDING`; registro em `FAILED(TIMEOUT)` reconciliado em ciclo posterior → `CONFIRMED`; prazo lido de `ChainConfig`) — data-model §2.3 — FR-014, research R23

### Implementation for User Story 1

- [ ] T045 [P] [US1] Implementar `$PKG/core/crypto/DeviceKeyManager.kt` (Keystore P-256, um alias por carteira `hashlens-device-<endereço em minúsculas>`, StrongBox com fallback, exporta chave pública em 65 bytes `0x04 ‖ X ‖ Y`)
- [ ] T046 [P] [US1] Implementar `$PKG/data/wallet/WalletSession.kt` (Reown AppKit: conectar, desconectar, `StateFlow` de conta/rede, troca para Sepolia, `eth_sendTransaction`)
- [ ] T047 [P] [US1] Implementar Room em `$PKG/data/local/` (`AppDatabase`, DAOs, `RegistrationStatus`, `FailureReason` = `REJECTED`, `REVERTED`, `TIMEOUT`, `HASH_MISMATCH`, `NETWORK`; `LocalImageEntity` com `sha256Hex` "`String` (64) … Índice único", `pHashHex` "`String` (16)", `origin` "`CAPTURE`, `EDIT`", `signatureHex` "`r‖s` (128 hex), gerada uma vez e reutilizada em reenvios", `submittedAt` "epoch ms em que o `txHash` foi recebido", `fileAvailable`; `DeviceBindingEntity` com `walletAddress` (PK, minúsculo), `publicKeyHex` "`String` (130)", `deviceId` "Nulo até a confirmação") conforme `$SPEC/data-model.md` §2
- [ ] T048 [P] [US1] Implementar `$PKG/data/gallery/GalleryRepository.kt` (MediaStore `Pictures/HashLens`, gravação e releitura)
- [ ] T049 [P] [US1] Implementar `$PKG/data/chain/RegistryTxBuilder.kt` (calldata de `registerDevice`, `registerCapture`, `registerEdit`; estimativa de gás; checagem de saldo)
- [ ] T050 [US1] Implementar `$PKG/data/chain/ReceiptTracker.kt` (polling de recibo, parse de `ImageRegistered`/`DeviceRegistered`)
- [ ] T051 [US1] Implementar `$PKG/domain/usecase/EnsureDeviceBoundUseCase.kt` até T041 passar (alias da carteira → procura a chave em `getDevicesOf` → `registerDevice` se ausente; nunca revoga vínculos)
- [ ] T052 [US1] Implementar `$PKG/domain/usecase/RegisterLocalImageUseCase.kt` (monta payload, assina uma vez, envia, grava `txHash` e `submittedAt`, atualiza status; reutilizado por edição e reenvio)
- [ ] T053 [US1] Implementar `$PKG/domain/usecase/CaptureAndRegisterUseCase.kt` (encode → hashes → galeria → releitura → `EnsureDeviceBoundUseCase` → `RegisterLocalImageUseCase`)
- [ ] T054 [US1] Implementar `$PKG/work/PendingTxWorker.kt` até T044 passar (acompanha `PENDING` e `FAILED(TIMEOUT)`; reconcilia por `getIdBySha256` antes de qualquer transição para `FAILED`; aplica o prazo de `ChainConfig`)
- [ ] T055 [US1] Criar telas sem estado `WalletSheet`, `CaptureScreen` e `RegistrationProgressScreen` com previews de todos os estados de `docs/referencia/ux.md` 4.3–4.5 (inclusive vínculo de aparelho e falha por prazo) e screenshot tests; **revisão da autora antes da ligação**
- [ ] T056 [US1] Ligar `$PKG/ui/wallet/WalletSheet.kt` ao `WalletViewModel.kt` (conexão, endereço abreviado, aviso de rede, saldo, link para faucet)
- [ ] T057 [US1] Ligar `$PKG/ui/capture/CaptureScreen.kt` e `RegistrationProgressScreen.kt` ao `CaptureViewModel.kt` (CameraX, captura, etapas do registro, link do Etherscan nos detalhes técnicos; em `AlreadyRegistered`, mostrar "Esta imagem já está registrada" com o botão "Ver registro", que abre `VerificationResultScreen` desse registro — spec Edge Cases)
- [ ] T058 [US1] Tratar saldo insuficiente, transação rejeitada e troca de conta/rede durante a operação em `CaptureViewModel.kt`, com textos em `android/app/src/main/res/values/strings.xml`

**Checkpoint**: US1 funcional e demonstrável (MVP).

---

## Phase 4: User Story 2 - Verificar a autenticidade de uma imagem (Priority: P2)

**Goal**: verificação pública, sem login, iniciada pelo seletor de fotos do app, com as cinco classificações.

**Independent Test**: roteiro US2 do `$SPEC/quickstart.md` (pode usar registros criados via `cast`).

### Tests for User Story 2 ⚠️

- [ ] T059 [P] [US2] Teste `$TEST/domain/usecase/VerifyImageUseCaseTest.kt` com `RegistryReader` falso: original, versão alterada (percentual correto), correspondência visual com múltiplos candidatos, adulterado, não registrada, erro de rede, arquivo inválido; registro assinado por aparelho antigo da mesma carteira continua válido (R25)
- [ ] T060 [P] [US2] Teste de UI `$ITEST/ui/verify/VerifyScreenTest.kt` (título e mensagem obrigatória de cada resultado conforme `$SPEC/contracts/verification-result.md` §3)
- [ ] T061 [P] [US2] Teste Robolectric `$TEST/ManifestShareTargetTest.kt`: nenhuma activity do app resolve `ACTION_SEND` ou `ACTION_VIEW` com `image/*` — FR-019

### Implementation for User Story 2

- [ ] T062 [P] [US2] Criar `$PKG/domain/model/VerificationResult.kt`
- [ ] T063 [US2] Implementar `$PKG/domain/usecase/VerifyImageUseCase.kt` conforme algoritmo de `$SPEC/contracts/verification-result.md` §1
- [ ] T064 [US2] Criar telas sem estado `VerifyScreen` e `VerificationResultScreen` com previews dos seis resultados, de versão alterada com 0%, do estado analisando e de arquivo não suportado (`docs/referencia/ux.md` 4.7–4.8) e screenshot tests; **revisão da autora**
- [ ] T065 [US2] Ligar `$PKG/ui/verify/VerifyScreen.kt` e `VerificationResultScreen.kt` ao `VerifyViewModel.kt` (Photo Picker como único ponto de entrada, etapas da análise, resultado, rodapé fixo)
- [ ] T066 [US2] Garantir rota de verificação sem guarda de sessão em `$PKG/ui/navigation/HashLensNavHost.kt`

**Checkpoint**: US1 e US2 funcionam independentemente.

---

## Phase 5: User Story 3 - Editar imagem própria com rastreabilidade (Priority: P3)

**Goal**: editar imagens `CONFIRMED` com filtros OpenCV e registrar a versão vinculada ao pai.

**Independent Test**: roteiro US3 do `$SPEC/quickstart.md`.

### Tests for User Story 3 ⚠️

- [ ] T067 [P] [US3] Teste instrumentado `$ITEST/core/imaging/FilterPipelineTest.kt` (cada filtro: dimensões, canais, determinismo; crop e rotate90 alteram dimensões corretamente)
- [ ] T068 [P] [US3] Teste `$TEST/domain/usecase/EditAndRegisterUseCaseTest.kt` (recusa pai não `CONFIRMED`; `parentId` correto; operações serializadas na ordem; payload inclui `operationsHash`)

### Implementation for User Story 3

- [ ] T069 [P] [US3] Implementar filtros em `$PKG/core/imaging/filters/` (`ImageFilter` + uma classe por operação de `$SPEC/contracts/hashing-spec.md` §4)
- [ ] T070 [US3] Implementar `$PKG/core/imaging/FilterPipeline.kt` (aplica `List<EditOperation>` sobre `Mat`; prévia em resolução reduzida, aplicação final em resolução cheia)
- [ ] T071 [US3] Implementar `$PKG/domain/usecase/EditAndRegisterUseCase.kt` (pipeline → encode → hashes → galeria → `EnsureDeviceBoundUseCase` → `RegisterLocalImageUseCase` com `registerEdit`)
- [ ] T072 [US3] Criar `EditorScreen` sem estado com previews dos estados de `docs/referencia/ux.md` 4.6 (inclusive imagem de terceiro e aviso de permanência) e screenshot tests; **revisão da autora**
- [ ] T073 [US3] Ligar `$PKG/ui/editor/EditorScreen.kt` ao `EditorViewModel.kt` (seleção de filtros, parâmetros, prévia, desfazer, salvar)
- [ ] T074 [US3] Bloquear abertura do editor para imagens não `CONFIRMED` com mensagem explicativa em `$PKG/ui/editor/EditorViewModel.kt`

**Checkpoint**: edição própria registrada e verificável pela US2.

---

## Phase 6: User Story 4 - Editar imagem registrada por outra pessoa (Priority: P4)

**Goal**: importar cópia exata de registro de terceiro e editá-la, identificando o editor.

**Independent Test**: roteiro US4 do `$SPEC/quickstart.md` (duas carteiras, podendo ser no mesmo aparelho).

### Tests for User Story 4 ⚠️

- [ ] T075 [P] [US4] Teste `$TEST/domain/usecase/ImportForEditUseCaseTest.kt` (SHA exato → libera com pai; sem registro → recusa; somente correspondência visual → recusa mesmo com um único candidato — FR-015, research R18)
- [ ] T076 [P] [US4] Garantir em `contracts/test/ImageRegistry.t.sol` o caso `test_registerEdit_byThirdParty_setsRegistrantToEditor`

### Implementation for User Story 4

- [ ] T077 [US4] Implementar `$PKG/domain/usecase/ImportForEditUseCase.kt` (Photo Picker → SHA → `getIdBySha256` → registro pai; sem consulta por pHash)
- [ ] T078 [US4] Adicionar ação "Importar para editar", banner "Editando imagem registrada por 0x…" e mensagem de recusa ("apenas cópias exatas de imagens registradas podem ser editadas") em `$PKG/ui/editor/EditorScreen.kt`

**Checkpoint**: genealogias mistas (autor A, editor B) registradas.

---

## Phase 7: User Story 5 - Consultar a genealogia (Priority: P5)

**Goal**: linha do tempo e árvore de versões com responsáveis e percentuais.

**Independent Test**: roteiro US5 do `$SPEC/quickstart.md`.

### Tests for User Story 5 ⚠️

- [ ] T079 [P] [US5] Teste `$TEST/domain/usecase/TraceGenealogyUseCaseTest.kt` (3 gerações, ramificações, percentuais pai/original, assinatura inválida sinalizada, limite de 10 níveis/200 nós)

### Implementation for User Story 5

- [ ] T080 [P] [US5] Criar `$PKG/domain/model/Genealogy.kt`
- [ ] T081 [US5] Implementar `$PKG/domain/usecase/TraceGenealogyUseCase.kt`
- [ ] T082 [US5] Criar `GenealogyScreen` sem estado com previews dos estados de `docs/referencia/ux.md` 4.9 e screenshot tests; **revisão da autora**
- [ ] T083 [US5] Ligar `$PKG/ui/genealogy/GenealogyScreen.kt` ao `GenealogyViewModel.kt` (caminho destacado + árvore; detalhes do nó)
- [ ] T084 [US5] Adicionar navegação para genealogia a partir de `VerificationResultScreen` e do acervo em `$PKG/ui/navigation/HashLensNavHost.kt`

**Checkpoint**: auditoria completa da genealogia disponível.

---

## Phase 8: User Story 6 - Acervo e sessão (Priority: P6)

**Goal**: listar imagens locais agrupadas, reenviar falhas com checagem prévia, desconectar.

**Independent Test**: roteiro US6 do `$SPEC/quickstart.md` (inclui o passo 4, prazo de 30 min).

### Tests for User Story 6 ⚠️

- [ ] T085 [P] [US6] Teste `$TEST/domain/usecase/RetryRegistrationUseCaseTest.kt` (`getIdBySha256(sha) ≠ 0` → `CONFIRMED` com esse `recordId`, sem abrir a carteira; `= 0` → `AWAITING_WALLET` reutilizando `signatureHex`; erro de rede na checagem → permanece `FAILED(NETWORK)` sem enviar; carteira conectada ≠ `walletAddress` do registro → reenvio bloqueado com a mensagem "Conecte a carteira 0x…abcd usada neste registro para tentar de novo", sem abrir a carteira e sem nova assinatura) — FR-014, research R23, data-model §2.3
- [ ] T086 [P] [US6] Teste `$TEST/ui/library/LibraryViewModelTest.kt` (agrupamento por original, arquivo indisponível, reenvio dispara `RetryRegistrationUseCase`, `FAILED(TIMEOUT)` exibido como "Não registrada – tentar de novo")

### Implementation for User Story 6

- [ ] T087 [US6] Implementar `$PKG/domain/usecase/RetryRegistrationUseCase.kt` até T085 passar
- [ ] T088 [US6] Criar `LibraryScreen` e `WalletAccountScreen` sem estado com previews dos estados de `docs/referencia/ux.md` 4.10–4.11 e screenshot tests; **revisão da autora**
- [ ] T089 [US6] Ligar `$PKG/ui/library/LibraryScreen.kt` ao `LibraryViewModel.kt` (grupos por original, status, reenviar via `RetryRegistrationUseCase`, abrir editor/genealogia)
- [ ] T090 [US6] Verificar existência do arquivo na galeria ao abrir o acervo e atualizar `fileAvailable` em `$PKG/ui/library/LibraryViewModel.kt`
- [ ] T091 [US6] Desconexão em `$PKG/ui/wallet/WalletAccountScreen.kt` e guarda de sessão para captura/edição em `$PKG/ui/navigation/HashLensNavHost.kt`
- [ ] T092 [US6] Reagendar `PendingTxWorker` na inicialização em `$PKG/HashLensApp.kt`

**Checkpoint**: todas as histórias funcionais.

---

## Phase 9: Polish & Cross-Cutting Concerns

- [ ] T093 [P] Testes de escalabilidade em `contracts/test/ImageRegistryScale.t.sol` (1k e 10k registros, listas de pHash colidentes, custo de gás e de leitura) — seção 4.4.3 do TCC
- [ ] T094 [P] Benchmark instrumentado `$ITEST/benchmark/HashingBenchmark.kt` (2, 8, 12, 48 MP) — SC-006
- [ ] T095 [P] Avaliação de recompressão para SC-004 em `$ITEST/evaluation/RecompressionMatchTest.kt`: para cada imagem golden (copiada para `android/app/src/androidTest/assets/golden/`), recodificar com JPEG qualidade 70, 75, 80, 85, 90 e 95 (sem redimensionar), calcular o pHash com `PerceptualHasher` e medir a taxa de igualdade exata com o pHash do arquivo original; o teste falha se a taxa global for < 90% e grava a tabela por imagem × qualidade em CSV no diretório de saída do teste. Complementar com cópias reais enviadas por um app de mensagens (roteiro US2 passo 3 do `$SPEC/quickstart.md`) e registrar os resultados em `docs/resultados/sc-004-recompressao.md` — SC-004, research R11
- [ ] T096 [P] `$PKG/core/metrics/MetricsLogger.kt` registrando tempos de transação e de verificação, com exportação CSV para o TCC — SC-007, SC-008
- [ ] T097 [P] Auditoria de acessibilidade em todas as telas com o checklist de `docs/referencia/ux.md` 7 (TalkBack, fonte 200%, contraste, Accessibility Scanner) — FR-036
- [ ] T098 Teste de usabilidade com ao menos 5 pessoas leigas em blockchain (verificar 3 imagens e explicar o resultado; fazer o primeiro registro), com roteiro e resultados em `docs/design/teste-usabilidade.md` — FR-032–FR-038
- [ ] T099 Revisão de segurança e idioma: nenhum segredo em log, `local.properties` fora do Git, nenhuma chamada que envie pixels, nenhuma pasta `values-<idioma>` em `android/app/src/main/res/` (FR-039, research R24)
- [ ] T100 [P] `README.md` na raiz com visão geral e link para `$SPEC/quickstart.md`
- [ ] T101 Executar o roteiro completo de `$SPEC/quickstart.md` e montar o conjunto rotulado (≥ 20 arquivos idênticos a registros, ≥ 20 não registrados, ≥ 3 registros com assinatura adulterada via `cast send` com `sigS` alterado), registrando a matriz esperado × obtido em `docs/resultados/matriz-classificacao.md` — SC-001, SC-002, SC-003

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (1)**: sem dependências.
- **Foundational (2)**: depende de 1; bloqueia todas as histórias. T009 depende de T008; T010 de T009; T019–T024 dependem dos testes T012–T018; T025 depende de T022; T028 depende de T009 (ABI) e T027.
- **Fundação de UI**: T033 bloqueia T034, T037 e T038; as telas de todas as histórias dependem de T037.
- **US1 (3)**: depende de 2. T051 depende de T041, T045 e T049; T054 depende de T044 e T050.
- **US2 (4)**: depende de 2; não depende de US1 (registros podem ser criados via `cast`).
  O botão "Ver registro" de T057 usa `VerificationResultScreen` (T064–T065); enquanto US2 não
  estiver pronta, ele abre o registro no explorador de blocos.
- **US3 (5)**: depende de US1 (T051 `EnsureDeviceBoundUseCase`, T052 `RegisterLocalImageUseCase`).
- **US4 (6)**: depende de US3.
- **US5 (7)**: depende de 2; exercitado plenamente após US3.
- **US6 (8)**: depende de US1 (T052, T054).
- **Polish (9)**: após as histórias desejadas.

### Within Each User Story

- Tela sem estado com previews aprovada pela autora antes da ligação com o ViewModel.
- Testes escritos e falhando antes da implementação.
- Modelos → serviços/repositórios → casos de uso → UI.

### Parallel Opportunities

- T004–T006; T011–T018; T019–T024; T030–T032; T035–T036; testes de cada história.
- Após a Fase 2, US1 e US2 podem avançar em paralelo.

---

## Parallel Example: Phase 2

```bash
Task: "Teste Sha256HasherTest em $TEST/core/hashing/Sha256HasherTest.kt"
Task: "Teste PerceptualHasherTest em $TEST/core/hashing/PerceptualHasherTest.kt"
Task: "Teste SignaturePayloadTest em $TEST/core/crypto/SignaturePayloadTest.kt"
Task: "Teste EditOperationCodecTest em $TEST/domain/model/EditOperationCodecTest.kt"
Task: "Teste PercentFormatTest em $TEST/ui/common/PercentFormatTest.kt"
```

## Parallel Example: User Story 1

```bash
Task: "Teste DeviceKeyManagerTest em $ITEST/core/crypto/DeviceKeyManagerTest.kt"
Task: "Teste EnsureDeviceBoundUseCaseTest em $TEST/domain/usecase/EnsureDeviceBoundUseCaseTest.kt"
Task: "Teste PendingTxWorkerTest em $TEST/work/PendingTxWorkerTest.kt"
Task: "Teste RegistryTxBuilderTest em $TEST/data/chain/RegistryTxBuilderTest.kt"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Fases 1 e 2.
2. Fase 3 (US1).
3. **STOP and VALIDATE** com o roteiro US1.

### Incremental Delivery

1. Setup + Foundational → contrato implantado e núcleo testado.
2. US1 → validar → demo (MVP: módulo de captura).
3. US2 → validar → demo (módulo de verificação).
4. US3 + US4 → validar → demo (módulo de edição).
5. US5 + US6 → validar.
6. Polish → coleta de métricas para o capítulo de resultados do TCC.

---

## Notes

- Toda mudança em `$SPEC/contracts/hashing-spec.md` é incompatível: novo deploy (Constituição II).
- O alias do Keystore depende do endereço da carteira (R4): trocar o formato do alias equivale a
  perder as chaves (R25) e gera novos vínculos de aparelho.
- O agente NÃO faz commits: ao fim de cada tarefa ou fase, para, resume as mudanças e sugere uma
  mensagem de commit; a autora revisa e commita. PRs citam FR/US atendidos.
