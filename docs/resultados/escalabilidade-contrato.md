# Escalabilidade do contrato ImageRegistry (T093)

Seção 4.4.3 do TCC. Testes em `contracts/test/ImageRegistryScale.t.sol`; os valores abaixo são os de
`contracts/snapshots/ImageRegistryScale.json`, gerados por:

```bash
cd contracts && forge test --match-contract ImageRegistryScaleTest -vv
```

## Como foi medido

- Contrato compilado com solc 0.8.24, otimizador ligado (200 runs), EVM `cancun`; Foundry 1.8.4.
- Gás de **execução** da chamada ao contrato. Uma transação real soma 21.000 de base e o custo do
  calldata (~2.000 a 3.000 de gás para os argumentos de um registro).
- Armazenamento **frio** antes de cada medida (`vm.cool`), como no início de uma transação real.
- O laço que cria os 10.000 registros não aloca memória no contrato de teste (o ponteiro de memória
  livre volta ao valor anterior a cada registro). Sem isso, a memória do teste crescia 320 KB e a
  expansão de memória aparecia nas medidas seguintes como um falso aumento de custo (ver "Armadilha de
  medição").
- "Custo constante" = diferença ≤ 1% (`assertApproxEqRel`).

## Resultados

### Escrita: o custo não cresce com o tamanho do contrato

| Operação                                    | 1ª      | 2ª      | 1.000ª  | 10.000ª |
| ------------------------------------------- | ------- | ------- | ------- | ------- |
| `registerCapture` (pHash distintos)         | 302.686 | 283.074 | 283.074 | 283.074 |
| `registerCapture` (mesmo pHash, colidentes) | —       | 265.974 | 265.974 | —       |

A 1ª captura paga a criação do contador de registros (escrita de zero para não zero). Com pHash
repetido o registro sai mais barato porque a lista de ids daquele pHash já existe.

| Operação                                    | Medida A            | Medida B             |
| ------------------------------------------- | ------------------- | -------------------- |
| `registerEdit`, versões da mesma original   | 2ª versão: 353.554  | 200ª versão: 353.554 |
| `registerEdit`, cadeia de gerações (SC-005) | 2ª geração: 370.666 | 10ª geração: 373.157 |

O contrato guarda só o pai e a original de cada versão, sem percorrer a cadeia. A 10ª geração custou
0,7% a mais que a 2ª, dentro da tolerância de 1% do teste.

### Leitura de um registro: constante

| Leitura (`eth_call`) | Com 1.000 registros | Com 10.000 registros |
| -------------------- | ------------------- | -------------------- |
| `getIdBySha256`      | 5.619               | 5.619                |
| `getRecord`          | 27.369              | 27.374               |

No mesmo instante, com 10.000 registros, ler o 1º registro custa o mesmo que ler o 10.000º.

### Listas: crescem linearmente e cabem num `eth_call`

| `getIdsByPHash` (mesmo pHash) | Gás       |
| ----------------------------- | --------- |
| 10 ids                        | 26.991    |
| 100 ids                       | 235.707   |
| 1.000 ids                     | 2.338.362 |
| Por id adicional              | ≈ 2.336   |

Com o limite padrão de um `eth_call` no geth (`--rpc.gascap` = 50.000.000), uma lista de até
**≈ 21.400 ids com o mesmo pHash** pode ser lida numa única consulta. As 200 versões diretas de uma
original (o limite que o app exibe no Histórico) custam 467.726 em `getChildren`.

## Armadilha de medição

A primeira versão do teste mostrava a leitura de um registro 23% mais cara com 10.000 registros do que
com 1.000. A causa não era o contrato: cada chamada feita pelo Solidity no laço reservava 32 bytes de
memória no contrato de teste para o retorno e nunca os liberava, e os `abi.encode` usados para gerar os
hashes alocavam mais. Depois de 10.000 iterações, a memória do teste tinha 320 KB, e a expansão de
memória (custo quadrático) era cobrada nas medidas seguintes. Com o laço sem alocação, o custo ficou
idêntico em 1.000 e 10.000 registros. Também a primeira leitura de um registro no teste sai ~2.500 de gás
mais barata que as seguintes (ponto de memória diferente na decodificação), por isso cada leitura é medida
depois de uma chamada de aquecimento.
