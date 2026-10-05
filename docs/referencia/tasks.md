---

description: "Task list for feature 001 - registro, verificação e rastreamento de imagens"
---

# Tasks: Registro, Verificação e Rastreamento de Alterações em Imagens

**Input**: Design documents from `/specs/001-registro-rastreamento-imagens/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/

**Tests**: Incluídos. A Constituição (Princípio IV) exige test-first para contrato, hashing,
payload de assinatura e classificador de verificação.

**Organization**: Tarefas agrupadas por história de usuário para implementação e teste independentes.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: pode rodar em paralelo (arquivos diferentes, sem dependências)
- **[Story]**: história atendida (US1…US6)

## Path Conventions

- `$PKG/` = `android/app/src/main/java/br/ufv/hashlens/`
- `$TEST/` = `android/app/src/test/java/br/ufv/hashlens/`
- `$ITEST/` = `android/app/src/androidTest/java/br/ufv/hashlens/`
- `$RES/` = `android/app/src/test/resources/`
- Contrato: `contracts/`

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: inicialização do monorepo

- [ ] T001 Criar estrutura do monorepo (`contracts/`, `android/`) e `.gitignore` cobrindo `local.properties`, `contracts/out/`, `contracts/cache/`, `.env`
- [ ] T002 Inicializar projeto Foundry em `contracts/foundry.toml` (solc 0.8.24, optimizer 200 runs) com `forge-std`
- [ ] T003 Inicializar projeto Android (Kotlin, Compose, minSdk 26, JVM 17) em `android/` com catálogo `android/gradle/libs.versions.toml`
- [ ] T004 [P] Adicionar dependências (Compose BOM, Navigation, Hilt, Room, CameraX, WorkManager, ExifInterface, OpenCV, web3j, Reown AppKit, JUnit5, MockK, Kotest, Robolectric) em `android/app/build.gradle.kts`
- [ ] T005 [P] Ler `sepolia.rpcUrl`, `registry.address`, `reown.projectId` de `local.properties` para `BuildConfig` em `android/app/build.gradle.kts` e expor em `$PKG/config/ChainConfig.kt` (chainId 11155111, URL do explorador)
- [ ] T006 [P] Conferir `.editorconfig` (ktlint, estilo `android_studio`) e configurar detekt em `android/`; `[fmt]` em `contracts/foundry.toml`
- [ ] T007 Criar tarefa Gradle `copyRegistryAbi` que copia `contracts/out/ImageRegistry.sol/ImageRegistry.json` para `android/app/src/main/assets/abi/ImageRegistry.json`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: contrato, núcleo criptográfico e leitura da blockchain, usados por todas as histórias

**⚠️ CRITICAL**: nenhuma história começa antes desta fase

### Contrato (test-first)

- [ ] T008 Escrever testes em `contracts/test/ImageRegistry.t.sol`: `registerDevice` (válido, chave inválida, duplicada); `registerCapture` (sucesso, SHA zero, SHA duplicado, device inexistente, device de outra carteira, assinatura ausente); `registerEdit` (sucesso, pai inexistente, `originalId` herdado, edição por outra carteira permitida, `operations` vazio/>256 bytes); getters; eventos; fuzz de ids e hashes
- [ ] T009 Implementar `contracts/src/ImageRegistry.sol` conforme `specs/001-registro-rastreamento-imagens/contracts/IImageRegistry.sol` até T008 passar
- [ ] T010 Criar `contracts/script/Deploy.s.sol`, implantar na Sepolia e registrar o endereço em `specs/001-registro-rastreamento-imagens/quickstart.md`

### Núcleo de hashing e assinatura (test-first)

- [ ] T011 [P] Adicionar imagens golden produzidas pela autora em `$RES/golden/` (original, edição leve, comprimida, preto e branco, outro ângulo, rotacionada via EXIF) e `$RES/golden/expected.json` gerado pela implementação de referência (ver research R9)
- [ ] T012 [P] Teste `$TEST/core/hashing/Sha256HasherTest.kt` (vetores NIST + arquivos golden)
- [ ] T013 [P] Teste `$TEST/core/hashing/PerceptualHasherTest.kt` (golden, determinismo em 100 execuções, orientação EXIF, formato hex) — Robolectric com OpenCV nativo ou mover para `$ITEST/` se necessário
- [ ] T014 [P] Teste `$TEST/core/hashing/HammingTest.kt` (d=0, d=64, percentual com 1 casa)
- [ ] T015 [P] Teste `$TEST/core/crypto/SignaturePayloadTest.kt` (195 bytes, ordem e big-endian de cada campo, `operationsHash` de string vazia)
- [ ] T016 [P] Teste `$TEST/core/crypto/SignatureCodecTest.kt` (DER ↔ `r‖s`, normalização low-S, vetores de `$RES/signature/`)
- [ ] T017 [P] Teste `$TEST/domain/model/EditOperationCodecTest.kt` (ida e volta de todas as operações, locale pt-BR com vírgula, limite de 256 bytes, entrada malformada)
- [ ] T018 [P] Implementar `$PKG/core/hashing/Sha256Hasher.kt` (streaming sobre `InputStream`)
- [ ] T019 [P] Implementar `$PKG/core/hashing/PerceptualHasher.kt` conforme `contracts/hashing-spec.md` 2
- [ ] T020 [P] Implementar `$PKG/core/hashing/Hamming.kt`
- [ ] T021 [P] Implementar `$PKG/core/crypto/SignaturePayload.kt` e `$PKG/core/crypto/SignatureCodec.kt`
- [ ] T022 [P] Implementar `$PKG/domain/model/EditOperation.kt` e `$PKG/domain/model/EditOperationCodec.kt`
- [ ] T023 Implementar `$PKG/core/crypto/DeviceSignatureVerifier.kt` (depende de T021) com teste `$TEST/core/crypto/DeviceSignatureVerifierTest.kt`
- [ ] T024 [P] Criar modelos `$PKG/domain/model/ImageRecord.kt`, `Device.kt`, `RecordView.kt`

### Leitura da blockchain

- [ ] T025 [P] Teste `$TEST/data/chain/AbiMapperTest.kt` decodificando respostas de `getRecord`/`getDevice` capturadas com `cast`
- [ ] T026 Implementar `$PKG/data/chain/AbiMapper.kt` e `$PKG/data/chain/RegistryReader.kt` (todas as funções `view` via web3j, sem carteira; erros de rede como exceção tipada)

### Infraestrutura do app

- [ ] T027 Criar `$PKG/HashLensApp.kt` (Hilt + `OpenCVLoader.initLocal()`), `$PKG/MainActivity.kt`, `$PKG/ui/theme/` e `$PKG/ui/navigation/HashLensNavHost.kt` com rotas vazias
- [ ] T028 [P] Módulos Hilt em `$PKG/di/` (`ChainModule`, `CryptoModule`, `DatabaseModule`, `DispatchersModule`)
- [ ] T029 [P] Implementar `$PKG/core/imaging/JpegEncoder.kt` (rotação física, q=95, EXIF `Orientation=1`, `DateTimeOriginal`, `Software`, sem GPS) e `$PKG/core/imaging/ImageDecoder.kt` (aplica orientação EXIF), com teste `$TEST/core/imaging/JpegEncoderTest.kt`
- [ ] T030 [P] Utilitário de permissões em `$PKG/ui/common/Permissions.kt` (câmera; explicação + atalho para configurações)

### Fundação de UI (Princípio VIII)

- [ ] T031 Propor a direção visual em `docs/design/direcao-visual.md` seguindo `.claude/skills/hashlens-ui/SKILL.md` e **aguardar aprovação da autora**
- [ ] T032 Implementar tokens em `$PKG/ui/theme/` (`Color.kt`, `Type.kt`, `Shape.kt`, `Spacing.kt`, `Theme.kt`) conforme a direção aprovada
- [ ] T033 [P] Configurar Roborazzi em `android/app/build.gradle.kts` e base de screenshot tests em `$TEST/ui/ScreenshotTest.kt`
- [ ] T034 [P] Criar dados fictícios em `$PKG/ui/preview/SampleData.kt` cobrindo todos os estados de `docs/referencia/ux.md`
- [ ] T035 Criar componentes em `$PKG/ui/components/` (`StatusHeader`, `StepProgress`, `AlterationMeter`, `TechnicalDetails`, `AddressChip`, `EmptyState`, `ErrorState`) com previews de todos os estados e screenshot tests em `$TEST/ui/components/`
- [ ] T036 Criar telas sem estado Início e Como funciona em `$PKG/ui/home/` e `$PKG/ui/onboarding/` com previews e screenshot tests; **revisão da autora**

**Checkpoint**: contrato implantado; hashing, payload e leitura testados; direção visual aprovada e componentes base prontos.

---

## Phase 3: User Story 1 - Capturar e registrar uma imagem (Priority: P1) 🎯 MVP

**Goal**: capturar, salvar na galeria e registrar a imagem original com assinatura do aparelho e da carteira.

**Independent Test**: roteiro US1 do `quickstart.md`.

### Tests for User Story 1 ⚠️

- [ ] T037 [P] [US1] Teste instrumentado `$ITEST/core/crypto/DeviceKeyManagerTest.kt` (chave gerada uma vez, não exportável, assinatura verificável com a pública)
- [ ] T038 [P] [US1] Teste instrumentado `$ITEST/data/gallery/GalleryRepositoryTest.kt` (bytes relidos == bytes gravados)
- [ ] T039 [P] [US1] Teste `$TEST/domain/usecase/CaptureAndRegisterUseCaseTest.kt` (aborta se SHA relido diverge; transições `LOCAL_ONLY → AWAITING_WALLET → PENDING`; rejeição → `FAILED`; SHA já registrado → erro sem transação)
- [ ] T040 [P] [US1] Teste `$TEST/data/chain/RegistryTxBuilderTest.kt` (calldata igual ao gerado por `cast calldata` para os mesmos argumentos)

### Implementation for User Story 1

- [ ] T041 [P] [US1] Implementar `$PKG/core/crypto/DeviceKeyManager.kt` (Keystore P-256, StrongBox com fallback, exporta chave pública em 65 bytes)
- [ ] T042 [P] [US1] Implementar `$PKG/data/wallet/WalletSession.kt` (Reown AppKit: conectar, desconectar, `StateFlow` de conta/rede, troca para Sepolia, `eth_sendTransaction`)
- [ ] T043 [P] [US1] Implementar Room em `$PKG/data/local/` (`AppDatabase`, `LocalImageEntity`, `DeviceBindingEntity`, `RegistrationStatus`, DAOs)
- [ ] T044 [P] [US1] Implementar `$PKG/data/gallery/GalleryRepository.kt` (MediaStore `Pictures/HashLens`, gravação e releitura)
- [ ] T045 [P] [US1] Implementar `$PKG/data/chain/RegistryTxBuilder.kt` (calldata de `registerDevice`, `registerCapture`, `registerEdit`; estimativa de gás; checagem de saldo)
- [ ] T046 [US1] Implementar `$PKG/data/chain/ReceiptTracker.kt` (polling de recibo, parse de `ImageRegistered`/`DeviceRegistered`)
- [ ] T047 [US1] Implementar `$PKG/domain/usecase/EnsureDeviceBoundUseCase.kt` (consulta `getDevicesOf`, registra aparelho se necessário)
- [ ] T048 [US1] Implementar `$PKG/domain/usecase/RegisterLocalImageUseCase.kt` (monta payload, assina uma vez, envia, atualiza status; reutilizado por edição e reenvio)
- [ ] T049 [US1] Implementar `$PKG/domain/usecase/CaptureAndRegisterUseCase.kt` (encode → hashes → galeria → releitura → `RegisterLocalImageUseCase`)
- [ ] T050 [US1] Implementar `$PKG/work/PendingTxWorker.kt` (acompanha `PENDING` até `CONFIRMED`/`FAILED`)
- [ ] T051 [US1] Criar telas sem estado `WalletSheet`, `CaptureScreen` e `RegistrationProgressScreen` com previews de todos os estados de `ux.md` 4.3–4.5 e screenshot tests; **revisão da autora antes da ligação**
- [ ] T052 [US1] Ligar `$PKG/ui/wallet/WalletSheet.kt` ao `WalletViewModel.kt` (conexão, endereço abreviado, aviso de rede, saldo, link para faucet)
- [ ] T053 [US1] Ligar `$PKG/ui/capture/CaptureScreen.kt` e `RegistrationProgressScreen.kt` ao `CaptureViewModel.kt` (CameraX, captura, etapas do registro, link do Etherscan nos detalhes técnicos)
- [ ] T054 [US1] Tratar saldo insuficiente, transação rejeitada e troca de conta/rede durante a operação em `CaptureViewModel.kt`, com textos em `android/app/src/main/res/values/strings.xml`

**Checkpoint**: US1 funcional e demonstrável (MVP).

---

## Phase 4: User Story 2 - Verificar a autenticidade de uma imagem (Priority: P2)

**Goal**: verificação pública, sem login, com as cinco classificações.

**Independent Test**: roteiro US2 do `quickstart.md` (pode usar registros criados via `cast`).

### Tests for User Story 2 ⚠️

- [ ] T055 [P] [US2] Teste `$TEST/domain/usecase/VerifyImageUseCaseTest.kt` com `RegistryReader` falso: original, versão alterada (percentual correto), correspondência visual com múltiplos candidatos, adulterado, não registrada, erro de rede, arquivo inválido
- [ ] T056 [P] [US2] Teste de UI `$ITEST/ui/verify/VerifyScreenTest.kt` (título e mensagem obrigatória de cada resultado conforme `contracts/verification-result.md` 3)

### Implementation for User Story 2

- [ ] T057 [P] [US2] Criar `$PKG/domain/model/VerificationResult.kt`
- [ ] T058 [US2] Implementar `$PKG/domain/usecase/VerifyImageUseCase.kt` conforme algoritmo de `contracts/verification-result.md` 1
- [ ] T059 [US2] Criar telas sem estado `VerifyScreen` e `VerificationResultScreen` com previews dos seis resultados, do estado analisando e de arquivo não suportado (`ux.md` 4.7–4.8) e screenshot tests; **revisão da autora**
- [ ] T060 [US2] Ligar `$PKG/ui/verify/VerifyScreen.kt` e `VerificationResultScreen.kt` ao `VerifyViewModel.kt` (Photo Picker, etapas da análise, resultado, rodapé fixo)
- [ ] T061 [US2] Garantir rota de verificação sem guarda de sessão em `$PKG/ui/navigation/HashLensNavHost.kt`

**Checkpoint**: US1 e US2 funcionam independentemente.

---

## Phase 5: User Story 3 - Editar imagem própria com rastreabilidade (Priority: P3)

**Goal**: editar imagens `CONFIRMED` com filtros OpenCV e registrar a versão vinculada ao pai.

**Independent Test**: roteiro US3 do `quickstart.md`.

### Tests for User Story 3 ⚠️

- [ ] T062 [P] [US3] Teste instrumentado `$ITEST/core/imaging/FilterPipelineTest.kt` (cada filtro: dimensões, canais, determinismo; crop e rotate90 alteram dimensões corretamente)
- [ ] T063 [P] [US3] Teste `$TEST/domain/usecase/EditAndRegisterUseCaseTest.kt` (recusa pai não `CONFIRMED`; `parentId` correto; operações serializadas na ordem; payload inclui `operationsHash`)

### Implementation for User Story 3

- [ ] T064 [P] [US3] Implementar filtros em `$PKG/core/imaging/filters/` (`ImageFilter` + uma classe por operação de `hashing-spec.md` 4)
- [ ] T065 [US3] Implementar `$PKG/core/imaging/FilterPipeline.kt` (aplica `List<EditOperation>` sobre `Mat`; prévia em resolução reduzida, aplicação final em resolução cheia)
- [ ] T066 [US3] Implementar `$PKG/domain/usecase/EditAndRegisterUseCase.kt` (pipeline → encode → hashes → galeria → `RegisterLocalImageUseCase` com `registerEdit`)
- [ ] T067 [US3] Criar `EditorScreen` sem estado com previews dos estados de `ux.md` 4.6 (inclusive imagem de terceiro e aviso de permanência) e screenshot tests; **revisão da autora**
- [ ] T068 [US3] Ligar `$PKG/ui/editor/EditorScreen.kt` ao `EditorViewModel.kt` (seleção de filtros, parâmetros, prévia, desfazer, salvar)
- [ ] T069 [US3] Bloquear abertura do editor para imagens não `CONFIRMED` com mensagem explicativa

**Checkpoint**: edição própria registrada e verificável pela US2.

---

## Phase 6: User Story 4 - Editar imagem registrada por outra pessoa (Priority: P4)

**Goal**: importar cópia exata de registro de terceiro e editá-la, identificando o editor.

**Independent Test**: roteiro US4 do `quickstart.md` (duas carteiras).

### Tests for User Story 4 ⚠️

- [ ] T070 [P] [US4] Teste `$TEST/domain/usecase/ImportForEditUseCaseTest.kt` (SHA exato → libera com pai; sem registro → recusa; somente correspondência visual → recusa conforme premissa R18)
- [ ] T071 [P] [US4] Garantir em `contracts/test/ImageRegistry.t.sol` o caso `test_registerEdit_byThirdParty_setsRegistrantToEditor`

### Implementation for User Story 4

- [ ] T072 [US4] Implementar `$PKG/domain/usecase/ImportForEditUseCase.kt` (Photo Picker → SHA → `getIdBySha256` → registro pai)
- [ ] T073 [US4] Adicionar ação "Importar para editar" e banner "Editando imagem registrada por 0x…" em `$PKG/ui/editor/EditorScreen.kt`

**Checkpoint**: genealogias mistas (autor A, editor B) registradas.

---

## Phase 7: User Story 5 - Consultar a genealogia (Priority: P5)

**Goal**: linha do tempo e árvore de versões com responsáveis e percentuais.

**Independent Test**: roteiro US5 do `quickstart.md`.

### Tests for User Story 5 ⚠️

- [ ] T074 [P] [US5] Teste `$TEST/domain/usecase/TraceGenealogyUseCaseTest.kt` (3 gerações, ramificações, percentuais pai/original, assinatura inválida sinalizada, limite de 10 níveis/200 nós)

### Implementation for User Story 5

- [ ] T075 [P] [US5] Criar `$PKG/domain/model/Genealogy.kt`
- [ ] T076 [US5] Implementar `$PKG/domain/usecase/TraceGenealogyUseCase.kt`
- [ ] T077 [US5] Criar `GenealogyScreen` sem estado com previews dos estados de `ux.md` 4.9 e screenshot tests; **revisão da autora**
- [ ] T078 [US5] Ligar `$PKG/ui/genealogy/GenealogyScreen.kt` ao `GenealogyViewModel.kt` (caminho destacado + árvore; detalhes do nó)
- [ ] T079 [US5] Adicionar navegação para genealogia a partir de `VerifyScreen` e do acervo

**Checkpoint**: auditoria completa da genealogia disponível.

---

## Phase 8: User Story 6 - Acervo e sessão (Priority: P6)

**Goal**: listar imagens locais agrupadas, reenviar falhas, desconectar.

**Independent Test**: roteiro US6 do `quickstart.md`.

### Tests for User Story 6 ⚠️

- [ ] T080 [P] [US6] Teste `$TEST/ui/library/LibraryViewModelTest.kt` (agrupamento por original, arquivo indisponível, reenvio dispara `RegisterLocalImageUseCase`)

### Implementation for User Story 6

- [ ] T081 [US6] Criar `LibraryScreen` e `WalletAccountScreen` sem estado com previews dos estados de `ux.md` 4.10–4.11 e screenshot tests; **revisão da autora**
- [ ] T082 [US6] Ligar `$PKG/ui/library/LibraryScreen.kt` ao `LibraryViewModel.kt` (grupos por original, status, reenviar, abrir editor/genealogia)
- [ ] T083 [US6] Verificar existência do arquivo na galeria ao abrir o acervo e atualizar `fileAvailable`
- [ ] T084 [US6] Desconexão em `WalletScreen` e guarda de sessão para captura/edição em `HashLensNavHost.kt`
- [ ] T085 [US6] Reagendar `PendingTxWorker` na inicialização em `$PKG/HashLensApp.kt`

**Checkpoint**: todas as histórias funcionais.

---

## Phase 9: Polish & Cross-Cutting Concerns

- [ ] T086 [P] Testes de escalabilidade em `contracts/test/ImageRegistryScale.t.sol` (1k e 10k registros, listas de pHash colidentes, custo de gás e de leitura) — seção 4.4.3 do TCC
- [ ] T087 [P] Benchmark instrumentado `$ITEST/benchmark/HashingBenchmark.kt` (2, 8, 12, 48 MP) — SC-006
- [ ] T088 [P] `$PKG/core/metrics/MetricsLogger.kt` registrando tempos de transação e de verificação, com exportação CSV para o TCC
- [ ] T089 [P] Auditoria de acessibilidade em todas as telas com o checklist de `ux.md` 7 (TalkBack, fonte 200%, contraste, Accessibility Scanner) — SC-011
- [ ] T090 Teste de usabilidade com ao menos 5 pessoas leigas em blockchain (verificar 3 imagens e explicar o resultado; fazer o primeiro registro), com roteiro e resultados em `docs/design/teste-usabilidade.md` — SC-009, SC-010
- [ ] T091 Revisão de segurança: nenhum segredo em log, `local.properties` fora do Git, nenhuma chamada que envie pixels
- [ ] T092 [P] `README.md` na raiz com visão geral e link para `quickstart.md`
- [ ] T093 Executar o roteiro completo de `quickstart.md` e registrar os resultados

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (1)**: sem dependências.
- **Foundational (2)**: depende de 1; bloqueia todas as histórias. T009 depende de T008; T010 de T009; T018–T022 dependem dos testes T012–T017; T026 depende de T009 (ABI) e T025.
- **Fundação de UI**: T031 bloqueia T032, T035 e T036; as telas de todas as histórias dependem de T035.
- **US1 (3)**: depende de 2.
- **US2 (4)**: depende de 2; não depende de US1 (registros podem ser criados via `cast`).
- **US3 (5)**: depende de US1 (T048 `RegisterLocalImageUseCase`).
- **US4 (6)**: depende de US3.
- **US5 (7)**: depende de 2; exercitado plenamente após US3.
- **US6 (8)**: depende de US1.
- **Polish (9)**: após as histórias desejadas.

### Within Each User Story

- Tela sem estado com previews aprovada pela autora antes da ligação com o ViewModel.
- Testes escritos e falhando antes da implementação.
- Modelos → serviços/repositórios → casos de uso → UI.

### Parallel Opportunities

- T004–T006; T011–T017; T018–T022; T028–T030; testes de cada história.
- Após a Fase 2, US1 e US2 podem avançar em paralelo.

---

## Parallel Example: Phase 2

```bash
Task: "Teste Sha256HasherTest em $TEST/core/hashing/Sha256HasherTest.kt"
Task: "Teste PerceptualHasherTest em $TEST/core/hashing/PerceptualHasherTest.kt"
Task: "Teste SignaturePayloadTest em $TEST/core/crypto/SignaturePayloadTest.kt"
Task: "Teste EditOperationCodecTest em $TEST/domain/model/EditOperationCodecTest.kt"
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

- Se a premissa R18 (edição só de cópias exatas) mudar, revisar T070 e T072.
- Toda mudança em `contracts/hashing-spec.md` é incompatível: novo deploy (Constituição II).
- O agente NÃO faz commits: ao fim de cada tarefa ou fase, para, resume as mudanças e sugere uma
  mensagem de commit; a autora revisa e commita. PRs citam FR/US atendidos.
