# Quickstart: Registro Exclusivo pelo HashLens Genuíno

Guia para preparar os dois deploys, rodar o atestador e validar cada história. Complementa
`specs/002-registro-rastreamento-imagens/quickstart.md` (pré-requisitos, RPC, Wi-Fi), que continua
valendo.

## 1. Pré-requisitos adicionais

- Aparelho físico com Android 8.0+ e patch de segurança com até 24 meses, bootloader travado e
  sistema original. Emulador só serve para validar a recusa (SC-003).
- Duas chaves secp256k1 de atestador, de teste: uma de desenvolvimento e uma de avaliação
  (`cast wallet new`). Nunca no Git.
- Keystore de release da autora (research R8), fora do Git (`*.jks` e `*.keystore` já estão no
  `.gitignore`). Para criar, fora do repositório:
  `keytool -genkeypair -v -keystore ~/hashlens-release.jks -alias hashlens -keyalg EC -groupname secp256r1 -validity 10000`.
  O SHA-256 dos certificados de debug e de release sai de `./gradlew :app:signingReport` (linhas
  `SHA-256`, `Variant: debug` e `Variant: release`). Em `attester.allowedCertDigests` e
  `ATTESTER_ALLOWED_CERT_DIGESTS` vale o hex com ou sem `:`; o build normaliza para minúsculas
  sem separador.
- Para a avaliação: túnel HTTPS (Cloudflare Tunnel ou ngrok, research R9).

## 2. Contrato

```bash
cd contracts
forge test -vv                 # evm_version = "osaka" (research R2); deve passar 100%
forge test --gas-report        # anotar registerDevice, registerCapture, registerEdit (gás v2)

export ATTESTER_ADDRESS=0x...  # endereço da chave do atestador do deploy
forge script script/Deploy.s.sol --rpc-url $SEPOLIA_RPC_URL --broadcast --gas-estimate-multiplier 800
```

Fazer dois deploys, cada um com o seu `ATTESTER_ADDRESS`, e registrar na tabela:

| Data       | Deploy          | Endereço `ImageRegistry`                     | Bloco    | Atestador                                    | Commit    |
| ---------- | --------------- | -------------------------------------------- | -------- | -------------------------------------------- | --------- |
| 2026-11-18 | desenvolvimento | `0xfBa7aeDa5e979F88336b35e81A1780b4655BAe6E` | 12163212 | `0x308F1744DFA756A4ADdfd852D2BeAAF13C1F586e` | `562550e` |
| 2026-11-23 | avaliação       | `0xEd920f008400E817601a9Afa51d86dD975A1e6D3` | 12197165 | `0x447F456DbFd9A5Df5a8a7Bd8089D9b3B693EA21D` | `562550e` |

O contrato da 002 (`0xF2f42B34…65Ba`) deixa de ser lido (clarificação Q3).

- São três carteiras diferentes: a do **atestador** (`cast wallet new`; só o endereço entra no
  deploy, em `ATTESTER_ADDRESS`, e ela não precisa de saldo), a de **deploy** (chave em
  `DEPLOYER_PRIVATE_KEY`; é ela que paga o gás e precisa de SepoliaETH) e a da pessoa no app. Com a
  chave do atestador em `DEPLOYER_PRIVATE_KEY`, o forge falha com `insufficient funds ... have 0`.
- `DEPLOYER_PRIVATE_KEY` vale com ou sem `0x` (a MetaMask exporta sem). Para não deixar a chave no
  histórico do terminal: `read -s DEPLOYER_PRIVATE_KEY && export DEPLOYER_PRIVATE_KEY`. O script
  mostra a `carteira de deploy` antes de enviar.
- Como na 002, a Sepolia cobra a criação bem acima da simulação local: o deploy de desenvolvimento
  usou 10.916.050 de gás (tx `0x07c5a1ea…5a54`); por isso o `--gas-estimate-multiplier 800`.

## 3. Atestador

```bash
cd android
read -s -p "ATTESTER_PRIVATE_KEY: " ATTESTER_PRIVATE_KEY && echo   # do deploy correspondente
export ATTESTER_PRIVATE_KEY
export ATTESTER_CHAIN_ID=11155111
export ATTESTER_CONTRACT=0x...               # ImageRegistry do mesmo deploy
export ATTESTER_ALLOWED_CERT_DIGESTS=<debug>,<release>   # avaliação: só <release>
./gradlew :attester:test
./gradlew :attester:run                      # porta 8080
curl -s localhost:8080/v1/health             # confere chainId, contrato e atestador
```

- **Desenvolvimento**: `adb reverse tcp:8080 tcp:8080` e `attester.url=http://localhost:8080`.
  Refazer o `adb reverse` sempre que o aparelho reconectar.
- **Avaliação**: ngrok com domínio estático (research R9), em outro terminal, com o atestador
  já no ar:

  ```bash
  ngrok http --url=ambulance-geologist-lake.ngrok-free.dev 8080
  curl -s https://ambulance-geologist-lake.ngrok-free.dev/v1/health   # mesmo JSON do localhost
  ```

  No `local.properties`, `attester.url=https://ambulance-geologist-lake.ngrok-free.dev`. O endereço
  é fixo, e o APK de release não precisa ser recompilado a cada execução. Instalação e authtoken
  (uma vez só): pacote `ngrok` do repositório apt oficial, depois
  `read -rs -p "Authtoken: " T; echo; ngrok config add-authtoken "$T"; unset T`.

## 4. Aplicativo

`android/local.properties` ganha:

```properties
registry.address=0x...            # do deploy em uso
registry.deployBlock=...
attester.url=http://localhost:8080
attester.allowedCertDigests=<debug>,<release>
# Nó só para eventos antigos (cadeia de atestação na verificação, transações do histórico).
# Opcional; o padrão é este. O Chainstack gratuito recusa eth_getLogs com mais de ~128 blocos.
sepolia.logsRpcUrl=https://sepolia.gateway.tenderly.co
# Assinatura de release; sem release.storeFile, o APK de release sai sem assinatura
release.storeFile=/home/<você>/hashlens-release.jks
release.storePassword=...
release.keyAlias=hashlens
release.keyPassword=...
```

```bash
./gradlew :attestation:test :attester:test :app:testDebugUnitTest
./gradlew :app:connectedDebugAndroidTest     # inclui vínculo real no deploy de desenvolvimento
./gradlew :app:assembleRelease               # APK de avaliação, assinado com a chave de release
```

## 5. Roteiro de validação

### US1 — Só o HashLens genuíno registra

1. Deploy de desenvolvimento, build de debug, aparelho íntegro, carteira sem vínculo: capturar uma
   foto. Esperado: etapa "Verificando a integridade do aparelho" e depois "Vinculando este
   aparelho…"; duas transações na carteira (vínculo e registro); registro confirmado.
2. No explorador, conferir o evento `DeviceAttested` com a cadeia e `getDevice(id)` com
   `attestationHash` e `registeredBlock`.
3. Deploy de avaliação: `uv run tools/chain/tentar_origens_nao_autorizadas.py` (research R15).
   Esperado: todas as tentativas mineradas e revertidas (a de outra carteira, simulada), com o erro esperado em
   `docs/resultados/dados/recusas.csv` (SC-001).
4. APK de debug apontando para o atestador de avaliação: o vínculo é recusado como "aplicativo
   não original" (SC-001, app reassinado).

### US2 — Aparelho sem integridade

1. Emulador: tentar registrar. Esperado: mensagem de aparelho sem hardware seguro, carteira não
   abre, imagem "não registrada" (SC-003).
2. Atestador parado, carteira sem vínculo: tentar registrar. Esperado: falha temporária com
   "Tentar de novo"; ao subir o atestador, o reenvio funciona sem nova captura.
3. Atestador parado, aparelho já vinculado: registrar outra foto. Esperado: registro normal.
4. No emulador: verificação, genealogia e acervo funcionam.

### US3 — Origem atestada na verificação

1. Verificar uma foto do passo US1.1: linha de origem e detalhes técnicos (nível de segurança,
   sistema verificado, patch, data do vínculo).
2. Sem internet só para `android.googleapis.com` (ou com o cache vazio e o domínio bloqueado):
   classificação mantida, nota de revogação não consultada.
3. Auditoria independente (SC-006): lê `getDevice` e o evento `DeviceAttested`, confere o hash e
   repete a política fora do app, sem a chave do atestador. Resultado esperado igual ao do
   atestador (`ACEITO`, com nível, sistema e patch; saída 0).

   ```bash
   cd android
   export AUDIT_RPC_URL=https://sepolia.gateway.tenderly.co   # nó com histórico de eventos
   export AUDIT_CONTRACT=0x...                                # ImageRegistry do deploy
   export AUDIT_CHAIN_ID=11155111
   export AUDIT_ALLOWED_CERT_DIGESTS=<debug>,<release>        # os mesmos do atestador
   ./gradlew :attester:audit --args="<deviceId>"
   ```

   Sem `AUDIT_CONTRACT`, `AUDIT_CHAIN_ID` ou `AUDIT_ALLOWED_CERT_DIGESTS`, valem os `ATTESTER_*`.
   O nó precisa servir `eth_getLogs` de blocos antigos: o Chainstack gratuito recusa blocos com
   mais de ~128 de idade ("Archive ... requests are not available").

### Matriz de classificação (SC-007, SC-008)

Refazer a matriz da 002 no deploy de avaliação (`tools/chain/montar_conjunto.sh` e
`ClassificationMatrixTest`), com a linha do SC-003 substituída pelas recusas de US1.3. O SC-008
sai dos testes de `VerifyImageUseCase` com leitor falso.

## 6. Dados para o capítulo de resultados

- Gás de `registerDevice`, `registerCapture` e `registerEdit` na v2 contra a v1.
- Tempo da etapa de integridade no primeiro registro (SC-004) e de registros seguintes (SC-005).
- Tabela de recusas (SC-001) com os hashes das transações revertidas.
- Limites declarados: keybox vazada ou TEE comprometido; ligação entre carteiras no mesmo
  aparelho pela cadeia RKP (research R13); recaptura.
