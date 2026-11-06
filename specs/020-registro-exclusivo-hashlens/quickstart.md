# Quickstart: Registro Exclusivo pelo HashLens Genuíno

Guia para preparar os dois deploys, rodar o atestador e validar cada história. Complementa
`specs/002-registro-rastreamento-imagens/quickstart.md` (pré-requisitos, RPC, Wi-Fi), que continua
valendo.

## 1. Pré-requisitos adicionais

- Aparelho físico com Android 8.0+ e patch de segurança com até 24 meses, bootloader travado e
  sistema original. Emulador só serve para validar a recusa (SC-003).
- Duas chaves secp256k1 de atestador, de teste: uma de desenvolvimento e uma de avaliação
  (`cast wallet new`). Nunca no Git.
- Keystore de release da autora (research R8), fora do Git, com o SHA-256 do certificado anotado:
  `keytool -list -v -keystore <arquivo> | grep SHA256`. O do debug sai de
  `./gradlew :app:signingReport`.
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

| Data | Deploy          | Endereço `ImageRegistry` | Bloco | Atestador | Commit |
| ---- | --------------- | ------------------------ | ----- | --------- | ------ |
|      | desenvolvimento |                          |       |           |        |
|      | avaliação       |                          |       |           |        |

O contrato da 002 (`0xF2f42B34…65Ba`) deixa de ser lido (clarificação Q3).

## 3. Atestador

```bash
cd android
export ATTESTER_PRIVATE_KEY=0x...            # do deploy correspondente
export ATTESTER_CHAIN_ID=11155111
export ATTESTER_CONTRACT=0x...               # ImageRegistry do mesmo deploy
export ATTESTER_ALLOWED_CERT_DIGESTS=<debug>,<release>   # avaliação: só <release>
./gradlew :attester:test
./gradlew :attester:run                      # porta 8080
curl -s localhost:8080/v1/health             # confere chainId, contrato e atestador
```

- **Desenvolvimento**: `adb reverse tcp:8080 tcp:8080` e `attester.url=http://localhost:8080`.
  Refazer o `adb reverse` sempre que o aparelho reconectar.
- **Avaliação**: subir o túnel apontando para `localhost:8080` e usar o endereço HTTPS em
  `attester.url`. Com túnel rápido, o endereço muda a cada execução: atualizar `local.properties`
  e recompilar o APK de release.

## 4. Aplicativo

`android/local.properties` ganha:

```properties
registry.address=0x...            # do deploy em uso
registry.deployBlock=...
attester.url=http://localhost:8080
attester.allowedCertDigests=<debug>,<release>
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
   Esperado: todas as tentativas mineradas e revertidas, com o erro esperado em
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
3. Auditoria independente: `./gradlew :attester:audit --args="<deviceId>"` lê o evento e repete a política fora do app; resultado igual ao do
   atestador (SC-006).

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
