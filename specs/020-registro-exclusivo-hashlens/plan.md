# Implementation Plan: Registro Exclusivo pelo HashLens Genuíno

**Branch**: `feature/020-registro-exclusivo-hashlens` | **Date**: 2026-11-06 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/020-registro-exclusivo-hashlens/spec.md`

## Summary

Só o HashLens genuíno, num aparelho íntegro, consegue registrar imagens. No vínculo, o app gera a
chave do aparelho no Keystore com um desafio público ligado à carteira (Android Key Attestation)
e envia a cadeia de certificados a um atestador mínimo em Kotlin/Ktor. O atestador aplica a mesma
política que o app usa na verificação, com a biblioteca oficial `android/keyattestation`: raiz do
Google, revogação, TEE ou StrongBox, boot verificado do fabricante, patch de até 24 meses, pacote
e certificado do APK da lista do deploy. Se tudo passa, ele devolve uma autorização EIP-712.
O `ImageRegistry` v2 só vincula com essa autorização, publica a cadeia no evento `DeviceAttested`
e verifica cada assinatura de registro on-chain pelo P256VERIFY (EIP-7951, já ativo na Sepolia).
A verificação no app revalida a atestação como defesa redundante, mostra a origem atestada e avisa
quando o certificado do aparelho foi revogado depois do vínculo. Há dois deploys: desenvolvimento
(debug e release) e avaliação (só release). Decisões em [research.md](./research.md).

## Technical Context

**Language/Version**: Kotlin 2.2+ (JDK 17) no app, no atestador e no módulo de atestação;
Solidity ^0.8.24 (solc 0.8.24) com `evm_version = "osaka"`.

**Primary Dependencies**: as da 002, mais: `android/keyattestation` (submódulo Git fixado em
commit, R4 ⚠️), Ktor server (Netty) e web3j core JVM no atestador, OkHttp com cache HTTP para a
lista de revogação no app; Foundry 1.8.4+ (cheatcodes `vm.signP256`, `vm.publicKeyP256`).

**UI/UX**: sem nova direção visual; reutiliza tema e componentes da 002 (`StepProgress`,
`StatusHeader`, `TechnicalDetails`, `ErrorState`). Estados novos seguem a ordem do CLAUDE.md:
componentes → telas sem estado com previews de todos os estados + Roborazzi → revisão da autora
→ ligação ao ViewModel.

**Storage**: on-chain (`ImageRegistry` v2, cadeia de atestação só em evento); Room com uma coluna
nova (`DeviceBindingEntity.failureReason`); atestador sem armazenamento.

**Testing**: Foundry (test-first: autorização, P-256, eventos, fuzz, gás); JUnit 5 + Kotest no
`:attestation` com cadeias de referência e no `:attester` com vetor EIP-712 fixo; MockK nos casos
de uso; Roborazzi nos estados novos; instrumentados no aparelho (vínculo real, captura da cadeia de
referência).

**Target Platform**: Android 8.0+ (minSdk 26) com atestação em hardware; atestador em JVM 17 no
computador da autora, exposto por `adb reverse` (desenvolvimento) ou túnel HTTPS (avaliação).

**Project Type**: mobile-app + smart contract + serviço atestador mínimo (exceção do princípio VII).

**Performance Goals**: etapa de integridade ≤ 10 s no primeiro registro em 4G (SC-004); registros
seguintes sem custo adicional (SC-005); verificação continua ≤ 5 s em 4G (`002/SC-007`), com cache
da validação por aparelho e da lista de revogação.

**Constraints**: atestador sem estado de negócio, sem imagens, sem hashes de imagem, sem dados
pessoais; chave do atestador e keystore de release fora do Git; cadeia publicada sem
identificadores do aparelho (R13); `eth_getLogs` só num bloco (`registeredBlock`); a verificação
continua sem login e sem custo.

**Scale/Scope**: 2 deploys do contrato; 1 endpoint no atestador; ~4 casos de uso alterados
(`EnsureDeviceBound`, `VerifyImage`, `TraceGenealogy`, `CaptureAndRegister`/`EditAndRegister` no
mapeamento de erros); 3 telas com estados novos (captura/edição, verificação, genealogia) e uma
frase na "Como funciona".

Nenhum item em aberto: as 6 clarificações da sessão de 2026-11-06 estão incorporadas (R7, R8, R12,
R15, R16), e a hospedagem do atestador foi decidida pela autora (R9).

## Constitution Check

_GATE: Must pass before Phase 0 research. Re-check after Phase 1 design._

| Princípio                       | Verificação                                                                                                                                                                 | Status           |
| ------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ---------------- |
| I. Integridade da cadeia        | Sem mudança no hash, na releitura nem na genealogia; `registerEdit` continua exigindo pai confirmado                                                                        | ✅               |
| II. Determinismo                | pHash e payload v1 inalterados; o contrato reproduz o payload com `abi.encodePacked` e os vetores da 002 continuam válidos (R1)                                             | ✅               |
| III. Chaves no custodiante      | Chave do aparelho continua no Keystore, agora com atestação; atestador nunca vê chave privada do aparelho; chave do atestador só no processo dele, por variável de ambiente | ✅               |
| IV. Test-first                  | Testes antes da implementação para contrato (autorização, P-256), política de atestação, EIP-712 e classificador (R14)                                                      | ✅               |
| V. Verificação aberta e honesta | Cadeia pública em evento, desafio público (R6), auditoria fora do app (SC-006); textos dizem "segundo a atestação do Google"; revogação não consultada não muda o resultado | ✅               |
| VI. Privacidade                 | Atestador recebe só cadeia, carteira e chave pública; sem atestação de identificadores; ligação de carteiras pela cadeia RKP declarada como limitação (R13)                 | ✅               |
| VII. Simplicidade               | Único serviço próprio é o atestador permitido pela emenda v2.0.0: sem banco, um endpoint, sem imagens. Ver _Complexity Tracking_                                            | ✅ (justificado) |
| VIII. Experiência compreensível | Recusas explicadas em linguagem leiga, sem abrir a carteira; nova etapa de progresso; estados novos com previews e aprovação antes da ligação                               | ✅               |
| IX. Origem exclusiva            | Contrato reverte sem autorização (R5) e com assinatura inválida (R1); política completa no atestador (R7); cadeia pública (R10); atestador sem poder de registrar imagens   | ✅               |

**Re-check pós-design (Phase 1)**: ✅ sem violações novas. Dois pontos da constituição foram
interpretados e ficam registrados: (1) princípio IX, "desafio emitido para aquela carteira e ainda
não usado": o desafio é público e ligado à carteira pelo conteúdo, e o uso único vem da unicidade
da chave no contrato (R6); (2) princípio III, "trocar a chave do atestador exige atualizar o
endereço no contrato": o endereço é `immutable`, então a troca é feita por novo deploy (R5);
leitura confirmada pela autora em 2026-11-06.

## Project Structure

### Documentation (this feature)

```text
specs/020-registro-exclusivo-hashlens/
├── spec.md
├── plan.md                        # Este arquivo
├── research.md                    # Phase 0
├── data-model.md                  # Phase 1
├── quickstart.md                  # Phase 1
├── contracts/                     # Phase 1
│   ├── IImageRegistry.sol         # Interface v2 do contrato
│   ├── device-authorization.md    # EIP-712 do atestador
│   ├── attestation-policy.md      # Desafio, regras e resultado da atestação
│   ├── attester-api.md            # POST /v1/authorize e mapeamento de erros no app
│   └── verification-result.md     # Delta da classificação e dos textos (002)
├── checklists/
│   └── requirements.md
└── tasks.md                       # Phase 2 (/speckit-tasks)
```

### Source Code (repository root)

```text
contracts/
├── foundry.toml                         # evm_version = "osaka"
├── src/IImageRegistry.sol               # v2
├── src/ImageRegistry.sol                # registerDevice com EIP-712; P256VERIFY nos registros
├── test/ImageRegistry.t.sol             # + autorização, P-256, eventos
├── test/ImageRegistryScale.t.sol        # ajustado à v2
├── test/vectors/device-authorization.json
└── script/Deploy.s.sol                  # ATTESTER_ADDRESS por variável de ambiente

android/
├── settings.gradle.kts                  # + :attestation, :attester, includeBuild do keyattestation
├── third_party/keyattestation/          # submódulo Git (android/keyattestation), commit fixo
├── attestation/                         # Kotlin/JVM, compartilhado
│   └── src/
│       ├── main/kotlin/br/ufv/hashlens/attestation/
│       │   ├── AttestationChallenge.kt  # montagem e leitura do desafio (policy §2)
│       │   ├── AttestationPolicy.kt     # regras da policy §3 sobre a biblioteca
│       │   ├── AttestationVerdict.kt
│       │   └── AttestationHash.kt       # keccak256(abi.encode(bytes[]))
│       └── test/                        # + resources/attestation/ (cadeias de referência)
├── attester/                            # Ktor, JVM
│   └── src/
│       ├── main/kotlin/br/ufv/hashlens/attester/
│       │   ├── Main.kt                  # configuração por ambiente, rotas
│       │   ├── AuthorizeRoute.kt        # POST /v1/authorize, GET /v1/health
│       │   ├── DeviceAuthorizationSigner.kt  # EIP-712 (web3j)
│       │   └── RevocationList.kt        # GET attestation/status com cache
│       └── test/                        # + resources/device-authorization.json
└── app/src/
    ├── main/java/br/ufv/hashlens/
    │   ├── config/ChainConfig.kt        # + attesterUrl, allowedCertDigests
    │   ├── core/crypto/DeviceKeyManager.kt        # geração com desafio; getAttestationChain
    │   ├── data/attester/AttesterClient.kt        # novo: POST /v1/authorize
    │   ├── data/attestation/RevocationRepository.kt  # novo: lista com cache HTTP
    │   ├── data/attestation/DeviceOriginVerifier.kt  # novo: evento + hash + política (§5)
    │   ├── data/chain/RegistryReader.kt           # Device v2, logs DeviceAttested
    │   ├── data/chain/RegistryTxBuilder.kt        # registerDevice v2
    │   ├── data/local/                            # failureReason + migração
    │   ├── domain/usecase/EnsureDeviceBoundUseCase.kt  # fluxo R11
    │   ├── domain/usecase/VerifyImageUseCase.kt   # valid(r) do verification-result §1
    │   ├── domain/usecase/TraceGenealogyUseCase.kt
    │   └── ui/                                    # capture, editor, verify, genealogy, onboarding
    ├── debug/res/xml/network_security_config.xml  # libera http://localhost só no debug
    ├── test/java/br/ufv/hashlens/                 # casos de uso com falsos (SC-008)
    └── androidTest/java/br/ufv/hashlens/          # vínculo real; captura de cadeia de referência

tools/chain/
├── tentar_origens_nao_autorizadas.py    # novo (R15)
└── registrar_adulterados.py             # removido (premissa da 002/R5 não vale mais)
```

**Structure Decision**: o monorepo continua com `contracts/` e `android/`. O atestador e o módulo
de atestação entram no build Gradle de `android/` como `:attester` e `:attestation`, para que app
e atestador usem o mesmo código de política (SC-006) sem um terceiro build. A biblioteca do Google
entra como submódulo em `android/third_party/`, onde já ficam dependências de terceiros (fontes).
O ABI do contrato v2 continua copiado para `android/app/src/main/assets/abi/`.

## Key Flows (referência para implementação)

**Vínculo (US1, R11)**: `EnsureDeviceBound(carteira)` → chave do alias em `getDevicesOf`? sim →
fim. Não → `DeviceKeyManager.regenerateWithChallenge(carteira, AttestationChallenge(chainId,
contrato, carteira, agora))` → `getAttestationChain` → `AttesterClient.authorize(carteira, chave,
cadeia)` (422 → `DeviceNotEligibleException`; rede/503 → `AttesterUnavailableException`) →
estimativa de gás e saldo → `registerDevice(chave, cadeia, deadline, autorização)` pela carteira →
evento `DeviceRegistered` → `CONFIRMED(deviceId)`. `AuthorizationExpired` → repete uma vez.

**Registro (US1)**: igual à 002. A diferença é que uma assinatura inválida agora reverte na
estimativa de gás (`InvalidDeviceSignature`) e cai no `FAILED(REVERTED)` já existente.

**Verificação (US3, R12)**: `VerifyImage` → para cada registro, `DeviceOriginVerifier.verify(dev)`
→ `RegistryReader.getAttestationChain(dev.id, dev.registeredBlock)` → hash → `AttestationPolicy`
com `referenceTime = registeredAt` → `RevocationRepository.status(serials)` → `DeviceOrigin` ou
`Tampered`.

**Recusas na avaliação (SC-001, R15)**: script → `cast send --gas-limit` forçado → recibo
`status = 0` → `recusas.csv`.

## Complexity Tracking

| Violation                                               | Why Needed                                                                                           | Simpler Alternative Rejected Because                                                                                       |
| ------------------------------------------------------- | ---------------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------- |
| Serviço próprio (`:attester`), exceção ao VII           | A política de atestação (X.509, raízes RSA e P-384, revogação) não cabe on-chain a custo viável (R3) | Validação on-chain completa: parser ASN.1 em Solidity, sem pré-compilado P-384 e sem acesso à revogação                    |
| Dois módulos Gradle novos (`:attestation`, `:attester`) | O mesmo código de política no atestador e no app, para decidirem igual (SC-006)                      | Atestador em outra linguagem: duas implementações de uma regra de segurança, com risco de divergência                      |
| Submódulo `android/keyattestation`                      | Biblioteca oficial recomendada pelo Android e não publicada no Maven (R4)                            | Parser próprio com Bouncy Castle: mais código de segurança próprio; fica como plano B se a biblioteca não rodar no Android |
| Dois deploys do contrato                                | Desenvolver com APK de debug sem enfraquecer a avaliação (clarificação Q1)                           | Um deploy aceitando debug e release: qualquer APK assinado com a chave de debug registraria na avaliação                   |
