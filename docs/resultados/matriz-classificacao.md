# Matriz de classificação e roteiro completo (T101)

Critérios de sucesso:

- **SC-001**: 100% dos arquivos idênticos a registros são classificados corretamente como
  "Registrada – original" ou "Registrada – versão alterada".
- **SC-002**: 100% dos arquivos não registrados são classificados como "Não registrada" ou
  "Correspondência visual", ou seja, sem nenhum falso positivo por hash exato.
- **SC-003**: 100% dos registros com assinatura do aparelho adulterada são classificados como
  "Registro adulterado".

> **Status:** roteiro e ferramentas prontos. Faltam a execução e os resultados (seções 1 e 3).

## Ferramentas

| O quê                                                                    | Para quê                                                                                                                                                                                             |
| ------------------------------------------------------------------------ | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `tools/chain/registrar_adulterados.py`                                   | Registra na Sepolia fotos com a assinatura do aparelho adulterada (`s` trocado por `s + 1`), com uma chave de aparelho de teste. Com `--controle`, registra uma foto com a assinatura correta.       |
| `tools/chain/montar_conjunto.sh`                                         | Copia as fotos para o aparelho, separadas por rótulo esperado. O rótulo vem do contrato, consultado com `cast` sobre o SHA-256 calculado no computador: é um gabarito independente do código do app. |
| `android/app/src/androidTest/.../evaluation/ClassificationMatrixTest.kt` | Verifica cada arquivo com o caso de uso real (`VerifyImageUseCase` contra a Sepolia), monta a matriz esperado × obtido e falha se faltar arquivo ou se algum critério ficar abaixo de 100%.          |

**Por que o controle.** O app classifica como "Registro adulterado" qualquer assinatura que não
confere, inclusive uma montada com o payload errado. A foto de controle passa pelo mesmo script, só
que sem alterar o `s`, e precisa sair como "Registrada – original". Isso mostra que o "adulterado"
das outras vem do `s` alterado.

## 1. Roteiro do quickstart

Executar a seção 4 de `specs/002-registro-rastreamento-imagens/quickstart.md` no aparelho, com o app
instalado (`./gradlew :app:installDebug`). As fotos registradas nesses passos (US1, US3 e US4) já
entram no conjunto rotulado.

| Seção do quickstart           | Resultado | Observações |
| ----------------------------- | --------- | ----------- |
| Primeiro uso, "Como funciona" |           |             |
| US1, captura e registro       |           |             |
| US2, verificação sem login    |           |             |
| US3, edição própria           |           |             |
| US4, edição de terceiro       |           |             |
| US5, genealogia               |           |             |
| US6, acervo e sessão          |           |             |

## 2. Conjunto rotulado

Mínimos: 20 arquivos idênticos a registros (originais + versões), 20 não registrados e 3
adulterados. Em 13/10/2026 o aparelho já tinha 5 fotos registradas em Pictures/HashLens (4
originais e 1 versão).

1. **Fotos registradas (≥ 20).** Registre pelo app mais fotos, até passar de 20 em
   Pictures/HashLens. Inclua algumas versões editadas (US3) e uma edição de foto de outra carteira
   (US4). Espere cada uma chegar a "Foto registrada". Fotos com falha ou pendentes ficam de fora
   automaticamente no passo 4.

2. **Adulteradas (≥ 3) e controle.** Tire 4 fotos novas com a câmera do sistema (não pelo
   HashLens) e copie-as para o computador. Separe uma para o controle:

   ```bash
   mkdir -p ~/hashlens-avaliacao/adulteradas ~/hashlens-avaliacao/controle
   # copie 3 fotos para adulteradas/ e 1 para controle/
   export SEPOLIA_RPC_URL=...              # não versionar
   export ADULTERADOR_PRIVATE_KEY=0x...    # carteira de teste com SepoliaETH (pode ser a de deploy)
   uv run tools/chain/registrar_adulterados.py --simular ~/hashlens-avaliacao/adulteradas/*   # confere, não envia
   uv run tools/chain/registrar_adulterados.py \
     --controle ~/hashlens-avaliacao/controle/* ~/hashlens-avaliacao/adulteradas/*
   ```

   Na primeira execução, o script vincula o aparelho de teste à carteira (1 transação a mais). As
   transações vão para `docs/resultados/dados/adulterados.csv`. Apague essas 4 fotos de DCIM/Camera
   do aparelho, para que o passo 4 não as pegue como não registradas.

3. **Cópias quase iguais (opcional, recomendado).** Cópias recomprimidas de fotos registradas são o
   caso mais difícil do SC-002: o arquivo é outro, mas a aparência é a mesma. Elas devem sair como
   "Correspondência visual" ou "Não registrada", nunca como registradas.

   ```bash
   mkdir -p ~/hashlens-avaliacao/registradas ~/hashlens-avaliacao/copias
   adb pull /sdcard/Pictures/HashLens/. ~/hashlens-avaliacao/registradas
   for f in ~/hashlens-avaliacao/registradas/*.jpg; do
     uv run --with pillow python -c \
       "import sys; from PIL import Image; Image.open(sys.argv[1]).save(sys.argv[2], quality=80)" \
       "$f" ~/hashlens-avaliacao/copias/"$(basename "$f" .jpg)_q80.jpg"
   done
   ```

4. **Montar no aparelho.** Este passo pega as 20 fotos mais recentes da câmera do sistema sem
   registro e soma as cópias e as adulteradas:

   ```bash
   PASTA_EXTRA_REGISTRADAS=~/hashlens-avaliacao/controle \
   PASTA_EXTRA_NAO_REGISTRADOS=~/hashlens-avaliacao/copias \
   PASTA_ADULTERADOS=~/hashlens-avaliacao/adulteradas \
     tools/chain/montar_conjunto.sh 20
   ```

   A lista com os rótulos fica em `docs/resultados/dados/conjunto-rotulado.csv`. As fotos pessoais
   só são lidas no computador para calcular o SHA-256 e são apagadas no fim. A lista guarda apenas
   o nome do arquivo.

5. **Classificar.** Com o aparelho desbloqueado:

   ```bash
   cd android && ./gradlew :app:installDebug :app:installDebugAndroidTest
   adb shell am instrument -w -r -e class br.ufv.hashlens.evaluation.ClassificationMatrixTest \
     br.ufv.hashlens.test/androidx.test.runner.AndroidJUnitRunner
   adb pull /sdcard/Android/data/br.ufv.hashlens/files/evaluation/matriz-classificacao.csv ../docs/resultados/dados/
   adb pull /sdcard/Android/data/br.ufv.hashlens/files/evaluation/matriz-classificacao.md /tmp/
   ```

   Não use `connectedDebugAndroidTest` aqui: ele reinstala o app e apaga o conjunto. Sem o
   conjunto, o teste é pulado.

## 3. Resultado

Colar aqui a matriz gerada em `matriz-classificacao.md`, com as linhas por grupo esperado, as
colunas por resultado obtido e o acerto por critério. Os dados por arquivo ficam em
[`dados/matriz-classificacao.csv`](dados/matriz-classificacao.csv).

_(pendente)_

## Limites

- **Origem dos registros:** as capturas vêm de um único aparelho e das carteiras da autora.
- **Não registrados:** são fotos da mesma câmera e cópias feitas por um único codificador (Pillow).
- **Adulteração:** só a do campo `s` da assinatura. Outros adulteramentos (`r`, chave pública ou
  dados do registro) levam ao mesmo resultado pelo mesmo caminho do código (a assinatura não
  confere), mas não foram exercitados na rede.
