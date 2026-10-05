# Contract: Especificação dos Identificadores

**Versão**: `v1`. Qualquer mudança neste documento é incompatível (Constituição, Princípio II).

## 1. Hash exato (SHA-256)

- Entrada: **todos os bytes do arquivo** exatamente como lidos do `ContentResolver`
  (incluindo cabeçalhos e EXIF).
- Saída: 32 bytes; representação textual em hex minúsculo (64 caracteres); on-chain como `bytes32`.

## 2. Hash perceptual (pHash v1)

Entrada: bytes do arquivo. Passos:

1. Decodificar para RGBA aplicando a orientação EXIF, se presente.
2. Converter para escala de cinza (`Imgproc.COLOR_RGBA2GRAY`, 8 bits).
3. Redimensionar para **32×32** com `Imgproc.INTER_AREA`.
4. Converter para `CV_32F` (valores 0–255, sem normalizar).
5. Aplicar `Core.dct` 2D.
6. Extrair o bloco **8×8** superior esquerdo (linhas 0–7, colunas 0–7), **incluindo** o termo DC.
7. Calcular a **mediana** dos 64 valores (média dos dois centrais).
8. Para cada valor, em ordem **linha a linha** (row-major), bit = 1 se `valor > mediana`, senão 0.
9. Empacotar os 64 bits com o primeiro bit como **mais significativo** → `uint64`.

Representação textual: 16 caracteres hex minúsculos (ex.: `9e4c398ab9d93331`).

> Espelha `imagehash.phash(hash_size=8, highfreq_factor=4)`. Divergências de alguns bits em
> relação a valores calculados em Python são esperadas (interpolação/decodificador) e não afetam
> o sistema, desde que registro e verificação usem esta implementação.

## 3. Distância de Hamming e percentual

```text
d(h1, h2) = popcount(h1 XOR h2)          // 0..64
percentual = d / 64 × 100                // arredondado a 1 casa decimal
```

Exibição: `0%` → "Sem alteração visual detectável"; demais → "≈ X% de alteração visual".

## 4. Serialização das operações de edição

Gramática (string ASCII, máx. 256 bytes):

```text
operations := op (";" op)*
op         := name [":" params]
params     := param ("," param)*
```

| Operação | `name` | Parâmetros | Exemplo |
|---|---|---|---|
| Brilho | `brightness` | delta inteiro com sinal, −100..+100 | `brightness:+30` |
| Contraste | `contrast` | fator, 2 casas, 0.50..2.00 | `contrast:1.25` |
| Preto e branco | `grayscale` | — | `grayscale` |
| Sépia | `sepia` | — | `sepia` |
| Desfoque | `blur` | kernel ímpar 3..25 | `blur:7` |
| Nitidez | `sharpen` | — | `sharpen` |
| Bordas | `edges` | low,high (0..255) | `edges:50,150` |
| Recorte | `crop` | x,y,w,h em frações, 3 casas | `crop:0.100,0.050,0.800,0.900` |
| Rotação | `rotate90` | voltas 1..3 (horário) | `rotate90:1` |

Operações são listadas na ordem em que foram aplicadas. Números usam `.` como separador decimal
independentemente do locale do aparelho.

## 5. Codificação JPEG de saída

Qualidade 95; pixels já rotacionados; EXIF `Orientation = 1`; sem tags GPS; `DateTimeOriginal`
e `Software = "HashLens/<versão>"` gravados **antes** do cálculo do SHA-256.
