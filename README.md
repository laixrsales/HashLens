# HashLens

App Android que registra fotos na blockchain Ethereum (rede de testes Sepolia) para que qualquer pessoa
possa verificar depois se uma imagem é autêntica, se foi alterada e por quem. Trabalho de Conclusão de
Curso (UFV).

## Como funciona

- **Registrar.** O app tira a foto, grava na galeria e registra no contrato `ImageRegistry` dois
  identificadores do arquivo: o SHA-256, que identifica o arquivo exato, e um hash perceptual (pHash)
  de 64 bits, que identifica a aparência da imagem. O registro leva duas assinaturas: a do aparelho
  (ECDSA P-256 guardada no Android Keystore), conferida pelo próprio contrato, e a da pessoa, que é a
  transação assinada pela carteira conectada via Reown AppKit.
- **Só o HashLens genuíno registra.** Antes do primeiro registro, o aparelho é vinculado à carteira
  com a Android Key Attestation: um atestador confere que a chave está em hardware seguro, que o
  sistema não foi modificado e que o app é o HashLens original, e autoriza o vínculo. O contrato só
  aceita vínculos autorizados e registros assinados pela chave do aparelho vinculado. A cadeia de
  atestação fica publicada na rede, e qualquer pessoa pode refazer a conferência.
- **Editar com rastreabilidade.** Uma imagem registrada, própria ou de outra pessoa, pode ser editada
  com filtros (OpenCV). Cada versão é registrada apontando para a anterior e para a original, junto com
  as operações aplicadas e o endereço de quem editou.
- **Verificar.** A verificação é aberta, sem login e sem custo. O app procura o arquivo pelo SHA-256 e
  depois pelo pHash, e mostra um destes resultados: "Registrada – original", "Registrada – versão
  alterada" (com o percentual de alteração em relação à original), "Correspondência visual", "Registro
  adulterado" ou "Não registrada". Nos registros válidos, mostra também a origem atestada do
  aparelho (nível de segurança, sistema verificado, patch) e avisa se o certificado foi revogado
  depois do vínculo.
- **Genealogia e acervo.** O app mostra a árvore de versões de uma imagem e lista as fotos registradas
  no aparelho.

A imagem nunca sai do aparelho: só os hashes, as assinaturas e os dados do registro vão para a rede.
O único serviço próprio é o atestador, usado só no vínculo; ele não guarda chaves de aparelho nem
pode registrar imagens. Toda a interface é em português (Brasil).

## Atestador e deploys

O atestador (`android/attester/`, Kotlin com Ktor) roda no computador da autora: no desenvolvimento,
o aparelho o acessa por `adb reverse`; na avaliação, por um túnel HTTPS (ngrok com domínio fixo). Ele
só precisa estar no ar durante vínculos novos.

Há dois deploys do contrato, cada um com a sua chave de atestador: o de **desenvolvimento** aceita o
app assinado com o certificado de debug e o de release; o de **avaliação**, só o de release. Os
endereços estão na
[tabela de deploys](specs/020-registro-exclusivo-hashlens/quickstart.md#2-contrato). O contrato da
002 (`0xF2f42B34…65Ba`) não é mais lido pelo app.

## Estrutura do repositório

| Pasta        | Conteúdo                                                                                                                          |
| ------------ | --------------------------------------------------------------------------------------------------------------------------------- |
| `android/`   | App (Kotlin, Jetpack Compose, Hilt, Room, CameraX, OpenCV, web3j, Reown AppKit), Android 8.0+, atestador e validação da atestação |
| `contracts/` | Contrato `ImageRegistry` em Solidity, com testes e script de deploy (Foundry)                                                     |
| `specs/`     | Especificação, plano, tarefas e roteiro de validação da feature (spec-kit)                                                        |
| `docs/`      | Direção visual, documentos de referência e resultados para o TCC                                                                  |
| `tools/`     | Scripts que geram os dados de referência dos testes: imagens golden, vetores de assinatura, calldata e respostas do contrato      |

## Como rodar

O passo a passo completo está no
[quickstart da 020](specs/020-registro-exclusivo-hashlens/quickstart.md), que acrescenta o atestador
e os dois deploys ao [quickstart da 002](specs/002-registro-rastreamento-imagens/quickstart.md). Os
dois cobrem os pré-requisitos, o deploy do contrato, a configuração do app e o roteiro de validação
de cada história.

Resumo:

```bash
# Contrato
cd contracts && forge build && forge test -vv

# Atestador (variáveis ATTESTER_*, ver quickstart da 020 §3) e validação da atestação
cd android && ./gradlew :attestation:test :attester:test
./gradlew :attester:run                     # porta 8080
adb reverse tcp:8080 tcp:8080               # desenvolvimento: aparelho chega ao atestador local

# App: antes, crie android/local.properties (RPC, contrato, atestador, projectId da Reown; ver quickstart §4)
./gradlew :app:installDebug
./gradlew :app:testDebugUnitTest            # testes unitários
./gradlew :app:connectedDebugAndroidTest    # testes instrumentados (aparelho conectado)
```

`android/local.properties` e as chaves de deploy nunca entram no Git.

## Documentação

- [Especificação](specs/002-registro-rastreamento-imagens/spec.md): histórias de usuário, requisitos
  e critérios de sucesso. A [especificação da 020](specs/020-registro-exclusivo-hashlens/spec.md)
  acrescenta o vínculo atestado e a conferência da assinatura na rede.
- [Plano](specs/002-registro-rastreamento-imagens/plan.md) e
  [decisões técnicas](specs/002-registro-rastreamento-imagens/research.md).
- [Contrato](specs/002-registro-rastreamento-imagens/contracts/) e
  [modelo de dados](specs/002-registro-rastreamento-imagens/data-model.md).
- [Princípios do projeto](.specify/memory/constitution.md).
- [Direção visual](docs/design/direcao-visual.md) e [diretrizes de UX](docs/referencia/ux.md).
- [Resultados](docs/resultados/): desempenho, escalabilidade, recompressão, revisão de segurança,
  recusas de origens não autorizadas, gás v2 contra v1 e matriz de classificação.
