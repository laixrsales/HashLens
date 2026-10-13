# HashLens

App Android que registra fotos na blockchain Ethereum (rede de testes Sepolia) para que qualquer pessoa
possa verificar depois se uma imagem é autêntica, se foi alterada e por quem. Trabalho de Conclusão de
Curso (UFV).

## Como funciona

- **Registrar.** O app tira a foto, grava na galeria e registra no contrato `ImageRegistry` dois
  identificadores do arquivo: o SHA-256, que identifica o arquivo exato, e um hash perceptual (pHash)
  de 64 bits, que identifica a aparência da imagem. O registro leva duas assinaturas: a do aparelho
  (ECDSA P-256 guardada no Android Keystore) e a da pessoa, que é a transação assinada pela carteira
  conectada via Reown AppKit.
- **Editar com rastreabilidade.** Uma imagem registrada, própria ou de outra pessoa, pode ser editada
  com filtros (OpenCV). Cada versão é registrada apontando para a anterior e para a original, junto com
  as operações aplicadas e o endereço de quem editou.
- **Verificar.** A verificação é aberta, sem login e sem custo. O app procura o arquivo pelo SHA-256 e
  depois pelo pHash, e mostra um destes resultados: "Registrada – original", "Registrada – versão
  alterada" (com o percentual de alteração em relação à original), "Correspondência visual", "Registro
  adulterado" ou "Não registrada".
- **Genealogia e acervo.** O app mostra a árvore de versões de uma imagem e lista as fotos registradas
  no aparelho.

A imagem nunca sai do aparelho: só os hashes, as assinaturas e os dados do registro vão para a rede.
O app não tem servidor próprio, e toda a interface é em português (Brasil).

## Estrutura do repositório

| Pasta        | Conteúdo                                                                                                                     |
| ------------ | ---------------------------------------------------------------------------------------------------------------------------- |
| `android/`   | App (Kotlin, Jetpack Compose, Hilt, Room, CameraX, OpenCV, web3j, Reown AppKit), Android 8.0+                                |
| `contracts/` | Contrato `ImageRegistry` em Solidity, com testes e script de deploy (Foundry)                                                |
| `specs/`     | Especificação, plano, tarefas e roteiro de validação da feature (spec-kit)                                                   |
| `docs/`      | Direção visual, documentos de referência e resultados para o TCC                                                             |
| `tools/`     | Scripts que geram os dados de referência dos testes: imagens golden, vetores de assinatura, calldata e respostas do contrato |

## Como rodar

O passo a passo completo está no
[quickstart](specs/002-registro-rastreamento-imagens/quickstart.md). Ele cobre os pré-requisitos, o
deploy do contrato, a configuração do app e o roteiro de validação de cada história.

Resumo:

```bash
# Contrato
cd contracts && forge build && forge test -vv

# App: antes, crie android/local.properties (RPC, endereço do contrato, projectId da Reown; ver quickstart §3)
cd android && ./gradlew :app:installDebug
./gradlew :app:testDebugUnitTest            # testes unitários
./gradlew :app:connectedDebugAndroidTest    # testes instrumentados (aparelho conectado)
```

`android/local.properties` e as chaves de deploy nunca entram no Git.

## Documentação

- [Especificação](specs/002-registro-rastreamento-imagens/spec.md): histórias de usuário, requisitos
  e critérios de sucesso.
- [Plano](specs/002-registro-rastreamento-imagens/plan.md) e
  [decisões técnicas](specs/002-registro-rastreamento-imagens/research.md).
- [Contrato](specs/002-registro-rastreamento-imagens/contracts/) e
  [modelo de dados](specs/002-registro-rastreamento-imagens/data-model.md).
- [Princípios do projeto](.specify/memory/constitution.md).
- [Direção visual](docs/design/direcao-visual.md) e [diretrizes de UX](docs/referencia/ux.md).
- [Resultados](docs/resultados/): desempenho, escalabilidade, recompressão e revisão de segurança.
