# SC-004: cópias recomprimidas reconhecidas (T095)

**Critério**: ao menos 90% das cópias recomprimidas (qualidade JPEG ≥ 70) de imagens registradas são
reconhecidas como "Correspondência visual" (research R11).

**Resultado da avaliação controlada**: 36 de 36 cópias (100%) mantiveram o pHash do arquivo original.
Atende. Cópia real (pelo Drive, sem recompressão): ver a seção própria abaixo.

Teste: `android/app/src/androidTest/java/br/ufv/hashlens/evaluation/RecompressionMatchTest.kt`. Dados
brutos: [`dados/sc-004-recompressao.csv`](dados/sc-004-recompressao.csv).

```bash
cd android
./gradlew :app:installDebug :app:installDebugAndroidTest
adb shell am instrument -w -r -e class br.ufv.hashlens.evaluation.RecompressionMatchTest \
  br.ufv.hashlens.test/androidx.test.runner.AndroidJUnitRunner
adb pull /sdcard/Android/data/br.ufv.hashlens/files/evaluation/sc-004-recompressao.csv
```

## Por que igualdade exata

A Correspondência visual consulta o contrato pelo pHash com igualdade exata (`getIdsByPHash`, R11).
Uma cópia só é reconhecida se o pHash dela for igual, bit a bit, ao do arquivo registrado; um único bit
diferente (distância de Hamming 1) já a torna "Não registrada". Por isso o teste conta igualdade exata,
e a tabela mostra também a distância de Hamming de cada cópia.

## Avaliação controlada

- Aparelho: Samsung Galaxy M52 5G (SM-M526B), Android 13.
- Imagens: as 6 imagens golden de `android/app/src/test/resources/golden/` (fotos de 4624×3468): 2 cenas originais e 4 derivadas de uma delas (edição leve, recompressão em q70,
  preto e branco, orientação EXIF trocada). Cada uma faz o papel de um arquivo registrado.
- Cópia: como um app de mensagens costuma fazer. A foto é decodificada já na orientação do EXIF e
  recodificada em JPEG pelo codificador do Android (`Bitmap.compress`), no mesmo tamanho e sem EXIF.
- Qualidades: 70, 75, 80, 85, 90 e 95.

| Imagem                        | q70 | q75 | q80 | q85 | q90 | q95 |
| ----------------------------- | --- | --- | --- | --- | --- | --- |
| originais/20261006_205911.jpg | 0   | 0   | 0   | 0   | 0   | 0   |
| originais/20261006_205922.jpg | 0   | 0   | 0   | 0   | 0   | 0   |
| edicao_leve.jpg               | 0   | 0   | 0   | 0   | 0   | 0   |
| comprimida_q70.jpg            | 0   | 0   | 0   | 0   | 0   | 0   |
| preto_branco.jpg              | 0   | 0   | 0   | 0   | 0   | 0   |
| orientacao_exif_1.jpg         | 0   | 0   | 0   | 0   | 0   | 0   |

Distância de Hamming entre o pHash da cópia e o do arquivo registrado (0 = reconhecida).

## Cópias reais por app de mensagens

Roteiro US2, passo 3, do `specs/002-registro-rastreamento-imagens/quickstart.md`: enviar um arquivo
registrado por um app de mensagens, salvar a cópia recebida e verificá-la no app.

O envio por app de mensagens não foi feito. Como substituto, a cópia foi enviada pelo Google Drive em
13/10/2026:

| Arquivo registrado                       | Meio         | Original               | Cópia recebida         | Resultado da verificação     |
| ---------------------------------------- | ------------ | ---------------------- | ---------------------- | ---------------------------- |
| HashLens_20261013_174350_545_editada.jpg | Google Drive | 3024×4032, 3.910.247 B | 3024×4032, 3.910.247 B | Registrada – versão alterada |

A cópia saiu idêntica byte a byte ao arquivo registrado (mesmo SHA-256), por isso foi reconhecida pelo
hash exato, como a própria versão editada registrada, e não por "Correspondência visual". O Drive preserva
o arquivo e não o recomprime, então esse envio confirma o caminho do arquivo idêntico, mas não testa
recompressão. Para SC-004 vale a avaliação controlada acima.

## Limites

- Amostra pequena: 36 cópias de 2 cenas. O conjunto rotulado da T101 (≥ 20 arquivos) amplia a base.
- Só recompressão. Apps que também reduzem a resolução (o WhatsApp costuma reduzir fotos grandes) mudam
  o arquivo de outra forma, e esse caso não foi medido: a única cópia real veio pelo Drive, sem alteração.
- Um único codificador JPEG (o do Android neste aparelho).
