# SC-006: tempo dos identificadores de uma foto (T094)

**Critério**: o cálculo dos identificadores de uma foto de 12 MP leva no máximo 2 s em aparelho
intermediário. **Resultado**: 171 ms (mediana), cerca de 12 vezes abaixo do limite. Atende.

Teste: `android/app/src/androidTest/java/br/ufv/hashlens/benchmark/HashingBenchmark.kt`. Dados brutos:
[`dados/sc-006-hashing.csv`](dados/sc-006-hashing.csv).

```bash
cd android
./gradlew :app:installDebug :app:installDebugAndroidTest
adb shell am instrument -w -r -e class br.ufv.hashlens.benchmark.HashingBenchmark \
  br.ufv.hashlens.test/androidx.test.runner.AndroidJUnitRunner
adb pull /sdcard/Android/data/br.ufv.hashlens/files/benchmark/hashing.csv
```

## Ambiente

- Aparelho: Samsung Galaxy M52 5G (SM-M526B), Snapdragon 778G (SM7325), Android 13. Faixa
  intermediária.
- Build de debug do app.

## Como foi medido

- Identificadores = SHA-256 ([`Sha256Hasher`]) + pHash v1 ([`PerceptualHasher`], que decodifica o JPEG
  com o OpenCV), sobre os bytes do arquivo, como o app faz depois de gravar a foto na galeria.
- Fotos sintéticas em 4:3, geradas com o OpenCV: ruído com semente fixa, suavizado (textura de foto),
  codificado em JPEG com a qualidade do app (95). Os arquivos ficaram com o tamanho de fotos reais
  (5,4 MB em 12 MP).
- Para cada tamanho: 2 execuções de aquecimento e 7 medidas; vale a mediana. O teste falha se a mediana
  de 12 MP passar de 2 s.

## Resultados

| Foto              | JPEG    | SHA-256 | pHash  | Total (mediana) | Total (máximo) |
| ----------------- | ------- | ------- | ------ | --------------- | -------------- |
| 2 MP (1632×1224)  | 0,9 MB  | 0 ms    | 24 ms  | 24 ms           | 26 ms          |
| 8 MP (3264×2448)  | 3,6 MB  | 2 ms    | 100 ms | 102 ms          | 107 ms         |
| 12 MP (4000×3000) | 5,4 MB  | 4 ms    | 167 ms | **171 ms**      | 172 ms         |
| 48 MP (8000×6000) | 21,8 MB | 16 ms   | 669 ms | 686 ms          | 688 ms         |

O tempo cresce de forma aproximadamente linear com o número de pixels e é quase todo do pHash; o
SHA-256 fica abaixo de 20 ms até em 48 MP.

## Limites da medida

- Mede só os identificadores. O registro completo da captura também codifica o JPEG, grava na galeria e
  relê o arquivo (R8); esses tempos não fazem parte do SC-006.
- Fotos sintéticas, sem EXIF de orientação: as fotos do app já são gravadas na orientação certa, então o
  pHash não gira a imagem. Uma foto de outra câmera com orientação 6 ou 8 acrescentaria a rotação.
- Um único aparelho; aparelhos de entrada devem levar mais tempo.

[`Sha256Hasher`]: ../../android/app/src/main/java/br/ufv/hashlens/core/hashing/Sha256Hasher.kt
[`PerceptualHasher`]: ../../android/app/src/main/java/br/ufv/hashlens/core/hashing/PerceptualHasher.kt
