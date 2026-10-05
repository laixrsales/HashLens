# Implementation Plan: Registro, Verificação e Rastreamento de Alterações em Imagens

**Branch**: `feature/002-registro-rastreamento-imagens` | **Date**: 2026-10-04 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/002-registro-rastreamento-imagens/spec.md`

## Summary

Aplicativo Android (Kotlin + Jetpack Compose) que captura fotos, grava-as na galeria e registra
seus identificadores (SHA-256 e pHash de 64 bits) em um contrato Solidity na Ethereum Sepolia,
com duas assinaturas: a do aparelho (ECDSA P-256 no Android Keystore, uma chave por carteira,
sobre um payload determinístico) e a da pessoa (a própria transação assinada pela carteira
conectada via Reown AppKit). Imagens registradas, próprias ou de terceiros, podem ser editadas
com filtros OpenCV desde que o arquivo seja cópia exata de um registro confirmado; cada versão é
registrada com `parentId`, `originalId`, operações aplicadas e editor. A verificação é pública e
sem login, iniciada pelo seletor de fotos do app: consulta por igualdade de SHA-256 e, em
seguida, de pHash, classificando o arquivo e calculando o percentual de alteração pela distância
de Hamming em relação à original. Registros sem confirmação em 30 min passam a "falhou" e só são
reenviados após checar o hash on-chain. Interface somente em português (Brasil). Decisões em
[research.md](./research.md).

## Technical Context

**Language/Version**: Kotlin 2.2+ (JDK 17) para o app; Solidity ^0.8.24 para o contrato.

**Primary Dependencies**: Jetpack Compose (BOM), Navigation Compose, Hilt, Kotlin Coroutines/Flow,
CameraX, Room, WorkManager, AndroidX ExifInterface, Photo Picker, OpenCV Android SDK (4.10+),
web3j core, Reown AppKit Android; Foundry (forge/cast) para o contrato. Versões exatas fixadas em
`gradle/libs.versions.toml` na tarefa de setup (itens ⚠️ do research).

**UI/UX**: Material 3 com tema próprio (research R20); telas sem estado com previews de todos os
estados e screenshot tests (R21); diretrizes na skill `.claude/skills/hashlens-ui/` e em
`docs/referencia/ux.md`; textos somente em pt-BR em `strings.xml` (R24).

**Storage**: on-chain (`ImageRegistry` na Sepolia) como fonte da verdade; Room para o acervo local;
MediaStore (galeria) para os arquivos de imagem.

**Testing**: Foundry (`forge test`, fuzzing, gas report); JUnit 5 + Kotest assertions + MockK para
unidades JVM; Roborazzi para screenshot tests das telas; Robolectric quando necessário; AndroidX
Test/Compose UI Test para instrumentados.

**Target Platform**: Android 8.0+ (minSdk 26), targetSdk atual; StrongBox opcional (API 28+).

**Project Type**: mobile-app + smart contract (sem backend).

**Performance Goals**: identificadores de foto de 12 MP em ≤ 2 s (SC-006); verificação em ≤ 5 s
em 4G (SC-007); UI sem quedas abaixo de 60 fps durante processamento (trabalho em `Dispatchers.Default`).

**Constraints**: nenhum pixel sai do aparelho; nenhuma chave privada no app; leitura sem carteira;
pHash determinístico conforme [hashing-spec.md](./contracts/hashing-spec.md); prazo de 30 min
para confirmação com checagem on-chain antes do reenvio (R23); sem entrada por "Compartilhar"
(R16); custo de gás por registro medido e reportado.

**Scale/Scope**: protótipo (PoC); ~10 telas; base de testes produzida pela autora; testes
de escalabilidade do contrato até 10.000 registros (simulados em Foundry).

Nenhum item em aberto: as cinco clarificações de 2026-10-04 estão incorporadas (R4, R7, R16, R18,
R23–R25).

## Constitution Check

_GATE: Must pass before Phase 0 research. Re-check after Phase 1 design._

| Princípio                       | Verificação                                                                                                                                | Status |
| ------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------ | ------ |
| I. Integridade da cadeia        | Hash sobre bytes finais com releitura (R8); edição só de cópias exatas de registros `CONFIRMED` (R18); `originalId` herdado on-chain       | ✅     |
| II. Determinismo                | pHash especificado em `hashing-spec.md` v1; golden tests                                                                                   | ✅     |
| III. Chaves no custodiante      | Keystore não exportável, uma chave por carteira (R4); perda da chave gera novo vínculo, sem recuperação (R25); carteira externa via AppKit | ✅     |
| IV. Test-first                  | Testes precedem implementação para contrato, hashing, payload, classificador e máquina de estados com prazo (R23)                          | ✅     |
| V. Verificação aberta e honesta | Leitura via RPC sem carteira; textos fixos em `verification-result.md`; falha de rede ≠ "Não registrada"                                   | ✅     |
| VI. Privacidade                 | Só hashes on-chain; sem GPS no EXIF                                                                                                        | ✅     |
| VII. Simplicidade               | Sem backend, sem indexador, sem IPFS; verificação P-256 off-chain; sem revogação de aparelho; um só idioma                                 | ✅     |
| VIII. Experiência compreensível | `ux.md` com estados por tela; previews + Roborazzi antes da ligação; FR-032–039                                                            | ✅     |

**Re-check pós-design (Phase 1)**: ✅ sem violações. A chave por carteira (R4) corrige uma
inconsistência da referência (chave única por aparelho × `DeviceAlreadyRegistered`) sem mudar a
interface do contrato. Nenhuma entrada em _Complexity Tracking_.

## Project Structure

### Documentation (this feature)

```text
specs/002-registro-rastreamento-imagens/
├── spec.md
├── plan.md                      # Este arquivo
├── research.md                  # Phase 0
├── data-model.md                # Phase 1
├── quickstart.md                # Phase 1
├── contracts/                   # Phase 1
│   ├── IImageRegistry.sol       # Interface do contrato
│   ├── hashing-spec.md          # SHA-256, pHash v1, Hamming, operações
│   ├── signature-payload.md     # Payload da assinatura do aparelho
│   └── verification-result.md   # Classificação e genealogia (domínio ↔ UI)
├── checklists/
│   └── requirements.md
└── tasks.md                     # Phase 2 (/speckit-tasks)
```

Jornadas, telas, estados e glossário continuam em `docs/referencia/ux.md` (referenciado pela spec
e pelo CLAUDE.md).

### Source Code (repository root)

```text
docs/design/direcao-visual.md            # Direção visual aprovada pela autora

contracts/                               # Projeto Foundry
├── foundry.toml
├── src/ImageRegistry.sol
├── test/ImageRegistry.t.sol
├── test/ImageRegistryScale.t.sol
└── script/Deploy.s.sol

android/
├── settings.gradle.kts
├── gradle/libs.versions.toml
└── app/
    ├── build.gradle.kts
    └── src/
        ├── main/java/br/ufv/hashlens/
        │   ├── HashLensApp.kt                   # @HiltAndroidApp, init OpenCV
        │   ├── MainActivity.kt                  # sem intent-filter de compartilhamento (R16)
        │   ├── di/                              # Módulos Hilt
        │   ├── config/ChainConfig.kt            # chainId, RPC, contrato, prazo pendente (BuildConfig)
        │   ├── core/
        │   │   ├── hashing/                     # Sha256Hasher, PerceptualHasher, Hamming
        │   │   ├── crypto/                      # DeviceKeyManager (alias por carteira), SignaturePayload, SignatureCodec, DeviceSignatureVerifier
        │   │   └── imaging/                     # JpegEncoder, ImageDecoder, filters/
        │   ├── data/
        │   │   ├── chain/                       # RegistryReader, RegistryTxBuilder, ReceiptTracker, AbiMapper
        │   │   ├── wallet/                      # WalletSession (Reown AppKit)
        │   │   ├── gallery/                     # GalleryRepository (MediaStore)
        │   │   └── local/                       # Room: AppDatabase, entities, DAOs
        │   ├── domain/
        │   │   ├── model/                       # ImageRecord, EditOperation, VerificationResult, Genealogy
        │   │   └── usecase/                     # EnsureDeviceBound, CaptureAndRegister, RegisterLocalImage,
        │   │                                    # RetryRegistration, ImportForEdit, EditAndRegister,
        │   │                                    # VerifyImage, TraceGenealogy
        │   ├── work/PendingTxWorker.kt          # recibos + prazo de 30 min (R23)
        │   └── ui/
        │       ├── navigation/
        │       ├── theme/                       # Color, Type, Shape, Spacing, Theme
        │       ├── components/                  # StatusHeader, StepProgress, AlterationMeter, TechnicalDetails, AddressChip, EmptyState, ErrorState
        │       ├── preview/SampleData.kt
        │       ├── onboarding/
        │       ├── home/
        │       ├── wallet/                      # Conexão, saldo, rede
        │       ├── capture/
        │       ├── editor/
        │       ├── verify/
        │       ├── genealogy/
        │       └── library/                     # Acervo
        ├── main/res/values/strings.xml          # único idioma: pt-BR (R24)
        ├── test/java/br/ufv/hashlens/           # Unitários JVM
        ├── test/resources/                      # golden images, vetores de assinatura
        └── androidTest/java/br/ufv/hashlens/    # Instrumentados (Keystore, MediaStore, CameraX)
```

**Structure Decision**: monorepo com dois projetos independentes: `contracts/` (Foundry) e
`android/` (Gradle, módulo único `app` organizado por camadas). Sem backend (Princípio VII).
O ABI gerado pelo Foundry (`contracts/out/ImageRegistry.sol/ImageRegistry.json`) é copiado para
`android/app/src/main/assets/abi/` por tarefa de build.

## Key Flows (referência para implementação)

**Vínculo do aparelho (US1 cenário 6, R4/R25)**: `EnsureDeviceBound(carteira)` → alias
`hashlens-device-<carteira>` existe no Keystore? senão gera → procura a chave pública em
`getDevicesOf(carteira)` → se ausente, `registerDevice(publicKey)` pela carteira →
`DeviceBindingEntity(publicKeyHex, deviceId)`.

**Captura (US1)**: `CaptureScreen` → CameraX → `JpegEncoder` (rotação física, EXIF sem GPS, q=95)
→ `Sha256Hasher` + `PerceptualHasher` → `GalleryRepository.save` → releitura e conferência do
SHA → `LocalImageEntity(LOCAL_ONLY)` → `EnsureDeviceBound` → `SignaturePayload` + `DeviceKeyManager.sign`
→ `RegistryTxBuilder.registerCapture` → `WalletSession.sendTransaction` (`AWAITING_WALLET` →
`PENDING`, grava `submittedAt`) → `PendingTxWorker` lê recibo e evento `ImageRegistered` →
`CONFIRMED(recordId)`.

**Prazo e reenvio (US6, R23)**: `PendingTxWorker` marca `FAILED(TIMEOUT)` após 30 min sem recibo →
`RetryRegistration` consulta `getIdBySha256` → ≠ 0: `CONFIRMED` sem nova transação; = 0: reabre a
carteira com a mesma assinatura do aparelho.

**Edição (US3/US4)**: origem local `CONFIRMED` ou `ImportForEdit` (Photo Picker → SHA →
`getIdBySha256` ≠ 0; correspondência só por pHash é recusada, R18) → `EditorScreen` (filtros
OpenCV sobre `Mat`, pré-visualização em resolução reduzida, aplicação final em resolução cheia) →
mesmo pipeline da captura com `registerEdit(parentId, …, operations)`.

**Verificação (US2)**: Photo Picker dentro do app → conforme `contracts/verification-result.md`,
somente com `RegistryReader` (RPC).

**Genealogia (US5)**: `TraceGenealogyUseCase` sobe por `parentId` e desce por `getChildren`.

## Complexity Tracking

> Nenhuma violação da constituição. Seção intencionalmente vazia.
