<!--
Sync Impact Report
- Version change: (template) → 1.1.0 (inclui convenção de branches, formatação, commits exclusivos da autora e Princípio VIII)
- Added principles: I–VIII (all new)
- Added sections: Restrições Técnicas e de Segurança; Fluxo de Desenvolvimento
- Templates requiring updates: none (initial ratification)
- Follow-up TODOs: none
-->

# HashLens Constitution

> Projeto vinculado ao TCC "Arquitetura em Blockchain para Autenticação e Rastreamento de Alterações em Imagens"
> Laís Reis de Sales
> UFV, Ciência da Computação, 2026.

## Core Principles

### I. Integridade da cadeia de proveniência (NON-NEGOTIABLE)

Todo registro na blockchain DEVE corresponder exatamente aos bytes do arquivo que o usuário
efetivamente possui. O SHA-256 é calculado sobre o arquivo final (já com metadados) e confirmado
por releitura após a gravação na galeria, antes de qualquer assinatura ou transação.
Uma imagem editada DEVE sempre referenciar a imagem-pai (`parentId`) e a raiz da cadeia
(`originalId`). Nenhum fluxo pode registrar uma edição sem pai confirmado on-chain.

**Racional**: a proposta do trabalho só tem valor se o vínculo arquivo ↔ registro for exato e a
genealogia nunca for quebrada.

### II. Determinismo dos identificadores

O cálculo de SHA-256 e de pHash DEVE ser determinístico, documentado
(`contracts/hashing-spec.md`) e idêntico nos fluxos de registro e verificação. Qualquer mudança
no algoritmo de pHash (tamanho, interpolação, critério de limiar) é uma mudança incompatível e
exige novo deploy do contrato e nova versão MAJOR desta especificação.

**Racional**: a busca por pHash é por igualdade exata; uma variação de implementação tornaria
registros antigos inalcançáveis.

### III. Chaves nunca saem de seu custodiante

A chave privada do dispositivo é gerada no Android Keystore como não exportável (StrongBox
quando disponível). A chave da carteira permanece exclusivamente na carteira externa; o
aplicativo NUNCA solicita, recebe, armazena ou registra em log frases-semente ou chaves privadas.

**Racional**: as duas assinaturas (dispositivo e pessoa) só atestam algo se as chaves não puderem
ser copiadas.

### IV. Test-First no núcleo criptográfico e no contrato

Testes DEVEM ser escritos antes da implementação para: o contrato inteligente (Foundry), o
cálculo de hashes, a distância de Hamming, a montagem do payload de assinatura e o classificador
de verificação. Esses componentes exigem testes com vetores fixos (golden files).
Testes de UI são recomendados, mas não bloqueantes.

**Racional**: erros nesses componentes são silenciosos e irreversíveis depois que o registro está
na blockchain.

### V. Verificação aberta e honesta

A verificação DEVE funcionar sem login e sem custo. Os resultados DEVEM afirmar apenas o que o
sistema consegue provar: "não registrada" nunca é apresentado como "falsa", e "registrada"
nunca é apresentado como "verdadeira no mundo real" (ataques de recaptura estão fora do escopo).

**Racional**: o aplicativo é um instrumento de prova de registro, não um detector de
falsificação; comunicar além disso induziria o usuário a erro.

### VI. Privacidade por padrão

Nenhum pixel de imagem sai do dispositivo. Apenas hashes, assinaturas, identificadores e a
descrição das operações de edição são publicados. Metadados de localização (GPS) não são
gravados por padrão.

**Racional**: dados publicados em blockchain pública são permanentes e não podem ser apagados.

### VII. Simplicidade de prova de conceito

Sem backend próprio: o aplicativo fala diretamente com a carteira e com um nó RPC da Sepolia.
Preferir a solução mais simples que valide a arquitetura do TCC (YAGNI). Toda complexidade
adicional deve ser justificada na seção *Complexity Tracking* do plano.

### VIII. Experiência compreensível

A interface DEVE ser compreensível por quem não conhece blockchain: linguagem leiga na camada
principal, detalhes técnicos sob demanda, todos os estados de cada tela tratados, status
comunicados por ícone e texto, acessibilidade (leitor de tela, fonte ampliada, contraste) como
requisito. Nenhuma tela é ligada à lógica antes de ter previews de todos os seus estados revisados
pela autora; a direção visual é aprovada por ela antes da primeira tela.

**Racional**: a prova de registro só tem valor se as pessoas entenderem o que ela diz e o que ela
não diz; sem protótipo prévio, a revisão por previews substitui o protótipo.

## Restrições técnicas e de segurança

- **Plataforma**: Android, Kotlin, Jetpack Compose com Material 3, OpenCV (Android SDK oficial).
- **Diretrizes de interface**: skill de projeto `.claude/skills/hashlens-ui/` e `docs/referencia/ux.md`.
- **Blockchain**: Ethereum Sepolia Testnet (chainId 11155111); contrato em Solidity.
- **Assinatura do dispositivo**: ECDSA P-256 (secp256r1) com SHA-256, Android Keystore.
- **Assinatura da pessoa**: a transação assinada pela carteira (secp256k1); o contrato grava
  `msg.sender` como responsável pelo registro.
- **Dados de teste**: apenas imagens produzidas pela autora.
- **Segredos de configuração** (URL de RPC, Project ID da Reown, endereço do contrato) ficam em
  `local.properties` e NUNCA são versionados.

## Fluxo de desenvolvimento

- Fluxo spec-kit: `constitution → specify → clarify → plan → tasks → implement`.
- Branches seguem o formato `<tipo>/<NNN>-<slug>`, com `<tipo>` ∈ {`feature`, `refactor`,
  `bugfix`, `style`, `chore`}, `<NNN>` sequencial de 3 dígitos e `<slug>` em kebab-case
  (ex.: `feature/001-registro-rastreamento-imagens`, `bugfix/004-falha-releitura-galeria`).
  O hook Git `pre-commit` bloqueia commits em branches fora desse padrão (exceto `main`).
- **Commits são exclusivos da autora.** Agentes de IA não executam `git commit`, `push`, `merge`,
  `rebase`, `cherry-pick`, `revert` nem `tag`, e o auto-commit da extensão git do spec-kit permanece
  desativado. O agente para ao fim de cada tarefa ou fase e sugere a ação e ser feita e a mensagem 
  de commit.
- Features novas passam pelo fluxo spec-kit completo e têm pasta em `specs/`; `refactor`,
  `bugfix` e `style` pequenos podem dispensar spec, mas seguem a mesma numeração.
- Arquivos editados pelo agente são formatados automaticamente (hook PostToolUse do Claude Code):
  ktlint para Kotlin, `forge fmt` para Solidity, Prettier para Markdown/JSON/YAML.
- Toda alteração no contrato exige testes Foundry passando e novo endereço de deploy registrado em
  `quickstart.md`.
- Pull requests DEVEM citar os requisitos (FR-xxx) e histórias (USx) atendidos.

## Governance

Esta constituição prevalece sobre outras práticas do projeto. Emendas exigem: descrição da
mudança, justificativa, atualização do Sync Impact Report e incremento de versão semântica
(MAJOR para remoção/redefinição de princípio, MINOR para princípio ou seção nova, PATCH para
esclarecimentos). Toda revisão de plano (`plan.md`) DEVE executar o *Constitution Check*.

**Version**: 1.1.0 | **Ratified**: 2026-10-01 | **Last Amended**: 2026-10-04
