# Matriz de classificação no contrato v2 (020/T061)

Refaz a matriz da 002 (T101) no deploy de **avaliação** da 020 (020 SC-007): `ImageRegistry`
`0xEd920f008400E817601a9Afa51d86dD975A1e6D3`, cujo atestador aceita só o APK de release (tabela de
deploys em `specs/020-registro-exclusivo-hashlens/quickstart.md` §2).

Critérios de sucesso (002, com a mudança do SC-003 pela 020 SC-007):

- **SC-001**: 100% dos arquivos idênticos a registros são classificados corretamente como
  "Registrada – original" ou "Registrada – versão alterada".
- **SC-002**: 100% dos arquivos não registrados são classificados como "Não registrada" ou
  "Correspondência visual", ou seja, sem nenhum falso positivo por hash exato.
- **SC-003**: no contrato v2, 100% das tentativas de registrar com assinatura do aparelho
  adulterada são recusadas pela rede, com cada transação revertida como evidência. Medido em
  [`recusas.md`](recusas.md), não por esta matriz. A classificação "Registro adulterado" de dados
  adulterados é coberta pela 020 SC-008, com um leitor falso do contrato nos testes unitários.

> **Status:** executada em 23/11/2026 no deploy de avaliação. SC-001 e SC-002 em 100%, e 22 de 22
> cópias recomprimidas como "Correspondência visual" (002 SC-004). Resultado na seção 3.

## Ferramentas

| O quê                                                                    | Para quê                                                                                                                                                                                                                                                          |
| ------------------------------------------------------------------------ | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `tools/chain/montar_conjunto.sh`                                         | Copia as fotos para o aparelho, separadas por rótulo esperado. O rótulo vem do contrato, consultado com `cast` sobre o SHA-256 calculado no computador: é um gabarito independente do código do app. Lê o contrato e o RPC de `android/local.properties`.         |
| `android/app/src/androidTest/.../evaluation/ClassificationMatrixTest.kt` | Verifica cada arquivo com o caso de uso real (`VerifyImageUseCase` contra a Sepolia, com a revalidação da atestação de cada aparelho), monta a matriz esperado × obtido e falha se faltar arquivo ou se algum critério ficar abaixo de 100%.                      |
| `tools/chain/tentar_origens_nao_autorizadas.py`                          | SC-003 na v2: tenta vincular e registrar por fora do HashLens genuíno, inclusive com assinatura de outra chave ou com dados alterados. Cada transação é minerada e revertida, e o resultado vai para `dados/recusas.csv` e [`recusas.md`](recusas.md) (020/T060). |

## 1. Registros no deploy de avaliação

Com o APK de release instalado (`./gradlew :app:assembleRelease` com `local.properties` no deploy
de avaliação), o atestador de avaliação no ar e o túnel ngrok (quickstart §3):

1. **Fotos registradas (≥ 20).** Capture pelo HashLens até passar de 20 fotos registradas, com
   algumas versões editadas. Espere cada uma chegar a "Foto registrada". Fotos com falha ou
   pendentes ficam de fora automaticamente. As fotos de `Pictures/HashLens` registradas em outros
   contratos também ficam de fora, porque não têm registro neste.
2. **Fotos não registradas (≥ 20).** Tire 20 fotos ou mais com a câmera do sistema
   (`DCIM/Camera`), fora do HashLens.

## 2. Conjunto rotulado e classificação

A partir daqui, o app de debug substitui o release no aparelho (assinaturas diferentes), o que
apaga o vínculo de avaliação. Por isso, só depois de todas as capturas. Com o debug instalado (passo

1. e o atestador de avaliação no ar, aproveite para fazer o passo 4 do roteiro US1: uma captura deve
   ser recusada como "aplicativo não original" ([`recusas.md`](recusas.md)).

1) **App de debug no mesmo deploy.** Com `local.properties` ainda no deploy de avaliação:

   ```bash
   adb uninstall br.ufv.hashlens
   cd android && ./gradlew :app:installDebug :app:installDebugAndroidTest && cd ..
   ```

   O `attester.allowedCertDigests` de avaliação tem só o release, que é o certificado com que os
   aparelhos deste contrato foram vinculados: é com ele que o teste revalida a atestação.

2) **Cópias quase iguais.** Cópias recomprimidas de fotos registradas são o caso mais difícil do
   SC-002: o arquivo é outro, mas a aparência é a mesma. Elas devem sair como "Correspondência
   visual" ou "Não registrada", nunca como registradas.

   ```bash
   mkdir -p ~/hashlens-avaliacao/registradas ~/hashlens-avaliacao/copias
   adb pull /sdcard/Pictures/HashLens/. ~/hashlens-avaliacao/registradas
   for f in ~/hashlens-avaliacao/registradas/*.jpg; do
     uv run --with pillow python -I -c \
       "import sys; from PIL import Image; Image.open(sys.argv[1]).save(sys.argv[2], quality=80)" \
       "$f" ~/hashlens-avaliacao/copias/"$(basename "$f" .jpg)_q80.jpg"
   done
   ```

3) **Montar no aparelho.** Pega as 20 fotos mais recentes da câmera do sistema sem registro e soma
   as cópias:

   ```bash
   PASTA_EXTRA_NAO_REGISTRADOS=~/hashlens-avaliacao/copias tools/chain/montar_conjunto.sh 20
   ```

   A lista com os rótulos fica em `docs/resultados/dados/conjunto-rotulado.csv`. As fotos pessoais
   só são lidas no computador para calcular o SHA-256 e são apagadas no fim. A lista guarda apenas
   o nome do arquivo.

4) **Classificar.** Com o aparelho desbloqueado:

   ```bash
   cd android
   adb shell am instrument -w -r -e class br.ufv.hashlens.evaluation.ClassificationMatrixTest \
     br.ufv.hashlens.test/androidx.test.runner.AndroidJUnitRunner
   adb pull /sdcard/Android/data/br.ufv.hashlens/files/evaluation/matriz-classificacao.csv ../docs/resultados/dados/
   adb shell cat /sdcard/Android/data/br.ufv.hashlens/files/evaluation/matriz-classificacao.md
   ```

   Não use `connectedDebugAndroidTest` aqui: ele reinstala o app e apaga o conjunto. Sem o
   conjunto, o teste é pulado.

## 3. Resultado

Executada em 23/11/2026 no Samsung Galaxy M52 5G (Android 13), pelo Wi-Fi, com o app de debug
no deploy de avaliação. O conjunto tem 78 arquivos: 12 originais e 10 versões registradas pelo
release neste contrato, e 56 não registrados (20 fotos da câmera do sistema e 36 cópias
recomprimidas em JPEG q80 das fotos de `Pictures/HashLens`). Dados por arquivo em
[`dados/matriz-classificacao.csv`](dados/matriz-classificacao.csv) e rótulos em
[`dados/conjunto-rotulado.csv`](dados/conjunto-rotulado.csv).

| Esperado \ Obtido | Registrada – original | Registrada – versão alterada | Correspondência visual | Registro adulterado | Não registrada | Formato não suportado | Não foi possível verificar | Total |
| ----------------- | --------------------- | ---------------------------- | ---------------------- | ------------------- | -------------- | --------------------- | -------------------------- | ----- |
| originais         | 12                    | 0                            | 0                      | 0                   | 0              | 0                     | 0                          | 12    |
| versoes           | 0                     | 10                           | 0                      | 0                   | 0              | 0                     | 0                          | 10    |
| nao_registrados   | 0                     | 0                            | 22                     | 0                   | 34             | 0                     | 0                          | 56    |

- **SC-001**: 22 de 22 arquivos registrados classificados corretamente (**100%**).
- **SC-002**: 56 de 56 não registrados como "Não registrada" ou "Correspondência visual", sem
  nenhum falso positivo por hash exato (**100%**).
- **SC-003 (002)**: no contrato v2, medido pelas recusas na rede: 6 de 6 tentativas recusadas, e o
  app de debug recusado como "aplicativo não original" ([`recusas.md`](recusas.md)).
- **SC-004 (002)**: as 22 cópias recomprimidas de fotos registradas neste contrato saíram como
  "Correspondência visual" (**100%**; o critério pede ao menos 90%). As outras 14 cópias são de
  fotos registradas em outros contratos e saíram como "Não registrada", como esperado. As 20 fotos
  da câmera também saíram como "Não registrada".
- **Tempo da verificação** (referência): mediana de 1.030 ms e máximo de 3.252 ms por arquivo. O
  máximo é o do primeiro arquivo verificado, provavelmente porque inclui abrir a conexão e
  buscar e revalidar a atestação do aparelho pela primeira vez.

## Limites

- **Origem dos registros:** as capturas vêm de um único aparelho e de uma única carteira da autora.
- **Não registrados:** são fotos da mesma câmera e cópias feitas por um único codificador (Pillow).
- **Adulteração:** no contrato v2 não há registro adulterado para classificar. A recusa na rede
  está em [`recusas.md`](recusas.md), e a classificação de dados adulterados (assinatura inválida,
  cadeia ou `attestationHash` divergentes) foi exercitada só com o leitor falso dos testes
  unitários (020 SC-008).
