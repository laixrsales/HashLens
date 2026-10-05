# Implementation Plan: Registro, Verificação e Rastreamento de Alterações em Imagens

**Branch**: `feature/001-registro-rastreamento-imagens` | **Date**: 2026-10-01 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/001-registro-rastreamento-imagens/spec.md`

## Summary

Aplicativo Android (Kotlin + Jetpack Compose) que captura fotos, grava-as na galeria e registra
seus identificadores (SHA-256 e pHash de 64 bits) em um contrato Solidity na Ethereum Sepolia,
com duas assinaturas: a do aparelho (ECDSA P-256 no Android Keystore, sobre um payload
determinístico) e a da pessoa (a própria transação assinada pela carteira conectada via Reown
AppKit). Imagens registradas, próprias ou de terceiros, podem ser editadas com filtros OpenCV;
cada versão é registrada com `parentId`, `originalId`, operações aplicadas e editor. A
verificação é pública e sem login: consulta por igualdade de SHA-256 e, em seguida, de pHash,
classificando o arquivo e calculando o percentual de alteração pela distância de Hamming em
relação à original. Detalhes de decisões em [research.md](./research.md).

## Technical Context

**Language/Version**: Kotlin 2.2+ (JDK 17) para o app; Solidity ^0.8.24 para o contrato.

**Primary Dependencies**: Jetpack Compose (BOM), Navigation Compose, Hilt, Kotlin Coroutines/Flow,
CameraX, Room, WorkManager, AndroidX ExifInterface, Photo Picker, OpenCV Android SDK (4.10+),
web3j core, Reown AppKit Android; Foundry (forge/cast) para o contrato.

**UI/UX**: Material 3 com tema próprio (research R20); telas sem estado com previews de todos os
estados e screenshot tests (R21); diretrizes na skill `.claude/skills/hashlens-ui/` e em `ux.md`.

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
pHash determinístico conforme [hashing-spec.md](./contracts/hashing-spec.md); custo de gás por
registro medido e reportado.

**Scale/Scope**: protótipo (PoC); ~10 telas; base de testes produzida pela autora; testes
de escalabilidade do contrato até 10.000 registros (simulados em Foundry).

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Princípio | Verificação | Status |
|---|---|---|
| I. Integridade da cadeia | Hash sobre bytes finais com releitura (R8); edição só de registros `CONFIRMED`; `originalId` herdado on-chain | ✅ |
| II. Determinismo | pHash especificado em `hashing-spec.md` v1; golden tests | ✅ |
| III. Chaves no custodiante | Keystore não exportável; carteira externa via AppKit | ✅ |
| IV. Test-first | Tarefas de teste precedem implementação para contrato, hashing, payload e classificador | ✅ |
| V. Verificação aberta e honesta | Leitura via RPC sem carteira; textos fixos em `verification-result.md` | ✅ |
| VI. Privacidade | Só hashes on-chain; sem GPS no EXIF | ✅ |
| VII. Simplicidade | Sem backend, sem indexador, sem IPFS; verificação P-256 off-chain | ✅ |
| VIII. Experiência compreensível | `ux.md` com estados por tela; previews + Roborazzi antes da ligação; FR-032–038 | ✅ |

**Re-check pós-design (Phase 1)**: ✅ sem violações. Nenhuma entrada em *Complexity Tracking*.

## Project Structure

### Documentation (this feature)

```text
specs/001-registro-rastreamento-imagens/
├── spec.md
├── plan.md                      # Este arquivo
├── research.md                  # Phase 0
├── data-model.md                # Phase 1
├── ux.md                        # Phase 1 (jornadas, telas, estados, glossário)
├── quickstart.md                # Phase 1
├── contracts/                   # Phase 1
│   ├── IImageRegistry.sol       # Interface do contrato
│   ├── hashing-spec.md          # SHA-256, pHash v1, Hamming, operações
│   ├── signature-payload.md     # Payload da assinatura do aparelho
│   └── verification-result.md   # Classificação e genealogia (domínio ↔ UI)
├── checklists/
│   └── requirements.md
└── tasks.md                     # Phase 2
```

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
        │   ├── HashLensApp.kt                     # @HiltAndroidApp, init OpenCV
        │   ├── MainActivity.kt
        │   ├── di/                              # Módulos Hilt
        │   ├── config/ChainConfig.kt            # chainId, RPC, contrato (BuildConfig)
        │   ├── core/
        │   │   ├── hashing/                     # Sha256Hasher, PerceptualHasher, Hamming
        │   │   ├── crypto/                      # DeviceKeyManager, SignaturePayload, SignatureCodec, DeviceSignatureVerifier
        │   │   └── imaging/                     # JpegEncoder, ImageDecoder, filters/
        │   ├── data/
        │   │   ├── chain/                       # RegistryReader, RegistryTxBuilder, ReceiptTracker, AbiMapper
        │   │   ├── wallet/                      # WalletSession (Reown AppKit)
        │   │   ├── gallery/                     # GalleryRepository (MediaStore)
        │   │   └── local/                       # Room: AppDatabase, entities, DAOs
        │   ├── domain/
        │   │   ├── model/                       # ImageRecord, EditOperation, VerificationResult, Genealogy
        │   │   └── usecase/                     # EnsureDeviceBound, CaptureAndRegister, RegisterLocalImage,
        │   │                                    # ImportForEdit, EditAndRegister, VerifyImage, TraceGenealogy
        │   ├── work/PendingTxWorker.kt
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
        ├── test/java/br/ufv/hashlens/             # Unitários JVM
        ├── test/resources/                      # golden images, vetores de assinatura
        └── androidTest/java/br/ufv/hashlens/      # Instrumentados (Keystore, MediaStore, CameraX)
```

**Structure Decision**: monorepo com dois projetos independentes: `contracts/` (Foundry) e
`android/` (Gradle, módulo único `app` organizado por camadas). Sem backend (Princípio VII).
O ABI gerado pelo Foundry (`contracts/out/ImageRegistry.sol/ImageRegistry.json`) é copiado para
`android/app/src/main/assets/abi/` por tarefa de build.

## Key Flows (referência para implementação)

**Captura (US1)**: `CaptureScreen` → CameraX → `JpegEncoder` (rotação física, EXIF sem GPS, q=95)
→ `Sha256Hasher` + `PerceptualHasher` → `GalleryRepository.save` → releitura e conferência do
SHA → `LocalImageEntity(LOCAL_ONLY)` → `EnsureDeviceBound` → `SignaturePayload` + `DeviceKeyManager.sign`
→ `RegistryTxBuilder.registerCapture` → `WalletSession.sendTransaction` (`AWAITING_WALLET` →
`PENDING`) → `PendingTxWorker` lê recibo e evento `ImageRegistered` → `CONFIRMED(recordId)`.

**Edição (US3/US4)**: origem local `CONFIRMED` ou `ImportForEdit` (Photo Picker → SHA →
`getIdBySha256` ≠ 0) → `EditorScreen` (filtros OpenCV sobre `Mat`, pré-visualização em resolução
reduzida, aplicação final em resolução cheia) → mesmo pipeline da captura com `registerEdit(parentId, …, operations)`.

**Verificação (US2)**: conforme `contracts/verification-result.md`, somente com `RegistryReader` (RPC).

**Genealogia (US5)**: `TraceGenealogyUseCase` sobe por `parentId` e desce por `getChildren`.

## Complexity Tracking

> Nenhuma violação da constituição. Seção intencionalmente vazia.
