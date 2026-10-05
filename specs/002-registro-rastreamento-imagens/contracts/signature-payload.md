# Contract: Payload da Assinatura do Aparelho

**Versão**: `v1` | Normativo para registro (app) e verificação (app ou terceiros).

## 1. Mensagem assinada

Concatenação binária, sem separadores, todos os inteiros em big-endian:

| #   | Campo            | Tamanho | Conteúdo                                                              |
| --- | ---------------- | ------- | --------------------------------------------------------------------- |
| 1   | `domain`         | 15 B    | ASCII `HASHLENS-IMG-v1`                                               |
| 2   | `chainId`        | 32 B    | `11155111` (Sepolia)                                                  |
| 3   | `contract`       | 20 B    | endereço do `ImageRegistry`                                           |
| 4   | `registrant`     | 20 B    | carteira que enviará a transação                                      |
| 5   | `deviceId`       | 4 B     | `uint32`                                                              |
| 6   | `sha256Hash`     | 32 B    | SHA-256 do arquivo                                                    |
| 7   | `pHash`          | 8 B     | `uint64`                                                              |
| 8   | `parentId`       | 32 B    | `uint256`, 0 para originais                                           |
| 9   | `operationsHash` | 32 B    | SHA-256 dos bytes UTF-8 de `operations` (string vazia para originais) |

Tamanho total: **195 bytes**.

## 2. Algoritmo

- Assinatura: `SHA256withECDSA` sobre a mensagem acima, com a chave P-256 do Android Keystore.
- Saída DER convertida para formato bruto `r ‖ s` (32 + 32 bytes, zero à esquerda).
- **Normalização low-S**: se `s > n/2`, usar `s = n − s` (n = ordem da curva P-256), para que
  cada mensagem tenha uma única representação registrada.

## 3. Verificação

1. Ler `ImageRecord` e `Device` do contrato.
2. Reconstruir a mensagem com `chainId` e endereço do contrato configurados, `registrant`,
   `deviceId`, `sha256Hash`, `pHash`, `parentId` e `operations` do registro.
3. Converter `r ‖ s` para DER e verificar com `SHA256withECDSA` e a `publicKey` do aparelho.
4. Válida → registro íntegro. Inválida ou chave malformada → **Registro adulterado**.

## 4. Vetores de teste

`app/src/test/resources/signature/` DEVE conter ao menos: um par chave/mensagem/assinatura
válido; mesma assinatura com `pHash` alterado (inválida); assinatura com `s` alto (deve ser
normalizada no registro e aceita na verificação).
