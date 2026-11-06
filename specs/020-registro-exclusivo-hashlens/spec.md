# Feature Specification: Registro Exclusivo pelo HashLens Genuíno

**Feature Branch**: `feature/020-registro-exclusivo-hashlens`

**Created**: 2026-11-06

**Status**: Draft

**Input**: User description: "Somente o app HashLens genuíno, em aparelho íntegro, consegue
registrar imagens. No vínculo, o app gera a chave com Android Key Attestation e manda a cadeia de
certificados a um serviço atestador mínimo. O atestador valida a cadeia e devolve uma autorização
EIP-712. O registerDevice reverte sem essa autorização, e o registerCapture/registerEdit reverte se
a assinatura P-256 não conferir, verificada on-chain pelo P256VERIFY (EIP-7951). A cadeia de
atestação fica num evento público, o contrato ganha novo deploy e a verificação passa a informar a
origem atestada. Base: constituição v2.0.0, princípio IX; feature anterior
specs/002-registro-rastreamento-imagens."

**Relação com a 002**: esta feature altera o vínculo do aparelho (002 FR-010, US1 cenário 6), o
registro (002 FR-011) e a verificação (002 FR-021, FR-022, FR-024). Tudo o que não é citado aqui
continua valendo como especificado na 002. Ela supera a premissa da 002 de que "a comprovação de
que a chave do aparelho reside em hardware genuíno está fora do escopo" e a de "não há backend
próprio" (constituição v2.0.0, princípios VII e IX).

## Clarifications

### Session 2026-11-06

- Q: Quais certificados de assinatura do aplicativo o atestador aceita? → A: Uma lista definida
  por deploy: o deploy de desenvolvimento aceita os certificados de debug e de release; o deploy
  de avaliação aceita só o de release (FR-003).
- Q: Como a verificação trata registros de um aparelho cujo certificado de atestação foi revogado
  depois do vínculo? → A: Continuam "Registrada", com aviso visível de que o certificado do
  aparelho foi revogado depois do vínculo (US3 cenário 5, FR-017).
- Q: O aplicativo continua lendo o contrato anterior? → A: Não. Lê só o novo contrato; o anterior
  fica como histórico da avaliação anterior, e a matriz de classificação é refeita no novo
  (FR-018, SC-007).

## User Scenarios & Testing _(mandatory)_

### User Story 1 - Só o HashLens genuíno consegue registrar (Priority: P1)

Uma pessoa que usa o HashLens original, instalado a partir do APK assinado pela autora, num
aparelho íntegro, vincula o aparelho e registra capturas e edições como antes. Qualquer outra
origem (outro aplicativo, script, aplicativo modificado ou chave gerada fora do aparelho) tem o
vínculo e o registro recusados pela própria rede, sem depender de verificação posterior.

**Why this priority**: é o requisito central do trabalho (constituição, princípio IX). Sem ele,
qualquer pessoa com uma carteira consegue publicar registros que a verificação apresenta como
autênticos.

**Independent Test**: num deploy novo do contrato, vincular e registrar pelo HashLens genuíno
(sucesso) e repetir as tentativas por um script com carteira própria (vínculo sem autorização,
autorização de outra carteira, autorização vencida, assinatura inválida), conferindo que cada uma
é recusada pela rede.

**Acceptance Scenarios**:

1. **Given** o HashLens genuíno num aparelho íntegro e uma carteira sem aparelho vinculado,
   **When** a pessoa faz o primeiro registro, **Then** o aparelho passa pela verificação de
   integridade, é vinculado à carteira e o registro é confirmado, sem passos novos para a pessoa
   além de aprovar as transações na carteira.
2. **Given** uma chave de aparelho sem autorização do atestador, **When** alguém tenta vinculá-la
   a uma carteira, **Then** a rede recusa o vínculo.
3. **Given** uma autorização emitida para outra carteira, outra chave, outra rede, outro contrato
   ou com prazo vencido, **When** alguém tenta usá-la num vínculo, **Then** a rede recusa o
   vínculo.
4. **Given** um aparelho vinculado, **When** alguém envia um registro de captura ou de edição cuja
   assinatura do aparelho não confere com a chave vinculada (dados alterados ou assinatura de
   outra chave), **Then** a rede recusa o registro.
5. **Given** um aplicativo diferente do HashLens (outro pacote ou o HashLens reassinado com outro
   certificado) num aparelho íntegro, **When** ele tenta obter autorização para sua chave,
   **Then** o atestador recusa e nenhum vínculo é possível.
6. **Given** uma autorização já usada num vínculo confirmado, **When** alguém tenta reutilizá-la,
   **Then** a rede recusa, porque a chave já está vinculada.

---

### User Story 2 - Aparelho sem integridade é informado com clareza (Priority: P2)

Quando o HashLens está num aparelho que não passa na verificação de integridade (bootloader
desbloqueado, sistema modificado, chave fora de hardware seguro, emulador ou certificado
revogado), a pessoa entende por que não pode registrar e o que continua disponível.

**Why this priority**: sem isso a barreira do US1 aparece como um erro genérico de rede e a pessoa
não sabe o que fazer; a verificação sem login continua sendo o uso principal para o público.

**Independent Test**: num aparelho ou emulador sem atestação válida, tentar registrar e conferir a
mensagem e o estado do item; em seguida, verificar uma imagem e confirmar que a verificação
funciona normalmente.

**Acceptance Scenarios**:

1. **Given** um aparelho que não passa na verificação de integridade, **When** a pessoa tenta
   registrar, **Then** o aplicativo informa em linguagem leiga que este aparelho não pode
   registrar imagens e por quê, sem abrir a carteira e sem cobrar taxa.
2. **Given** o atestador indisponível ou sem conexão, **When** a pessoa tenta vincular o aparelho,
   **Then** o aplicativo informa a falha temporária, permite tentar de novo, e a imagem fica na
   galeria como "não registrada", com reenvio possível sem nova captura.
3. **Given** um aparelho já vinculado, **When** o atestador está indisponível, **Then** novos
   registros nesse aparelho funcionam normalmente (o atestador só participa do vínculo).
4. **Given** um aparelho sem integridade, **When** a pessoa usa a verificação, a genealogia ou o
   acervo, **Then** essas funções ficam disponíveis como antes.

---

### User Story 3 - A verificação mostra a origem atestada e qualquer pessoa pode auditá-la (Priority: P3)

Ao verificar uma imagem registrada, a pessoa vê que o registro foi feito pelo HashLens genuíno num
aparelho atestado, com os dados da atestação nos detalhes técnicos. Qualquer pessoa consegue, com
dados públicos, refazer a checagem que o atestador fez no vínculo daquele aparelho.

**Why this priority**: dá visibilidade ao ganho do US1 e mantém a verificação aberta e honesta
(princípio V), mas o registro já está protegido sem ela.

**Independent Test**: verificar uma imagem registrada no novo contrato e conferir a indicação de
origem; depois, com os dados públicos do vínculo do aparelho, refazer a validação da atestação
fora do aplicativo e chegar à mesma conclusão.

**Acceptance Scenarios**:

1. **Given** um arquivo idêntico a um registro do novo contrato, **When** ele é verificado,
   **Then** o resultado indica, além da classificação da 002, que o registro veio do HashLens
   genuíno num aparelho atestado.
2. **Given** o resultado de uma verificação, **When** a pessoa abre os detalhes técnicos, **Then**
   vê o nível de segurança da chave (hardware seguro ou chip dedicado), o estado do sistema
   atestado e a data do vínculo do aparelho.
3. **Given** um aparelho vinculado, **When** um terceiro obtém os dados públicos do vínculo,
   **Then** consegue validar a cadeia de atestação até a raiz do Google e confirmar pacote,
   certificado do aplicativo, nível de segurança e chave pública, sem depender do atestador.
4. **Given** um registro cuja atestação do aparelho não confere ao ser revalidada pelo aplicativo
   (cadeia inválida, pacote ou certificado divergente, chave pública diferente da vinculada),
   **When** o arquivo é verificado, **Then** o resultado é "Registro adulterado".
5. **Given** um aparelho cujo certificado de atestação foi revogado pelo Google depois do vínculo,
   **When** um registro dele é verificado, **Then** a classificação da 002 é mantida, com um aviso
   visível de que o certificado do aparelho foi revogado depois do vínculo.

---

### Edge Cases

- A chave do aparelho é perdida (reinstalação, dados apagados): a nova chave passa por nova
  atestação e novo vínculo; registros antigos continuam válidos (002, mantido).
- O aparelho recebe atualização de sistema depois do vínculo: o vínculo continua válido; a
  atestação reflete o estado no momento do vínculo.
- A pessoa desbloqueia o bootloader depois do vínculo: o desbloqueio apaga os dados do aparelho,
  inclusive a chave; o vínculo antigo continua on-chain com seus registros, e um vínculo novo
  nesse estado é recusado.
- A autorização vence entre a emissão e o envio do vínculo (carteira demorou a aprovar): a rede
  recusa e o aplicativo pede uma nova autorização automaticamente, sem nova atestação manual.
- A pessoa troca de carteira entre a atestação e o vínculo: a autorização não serve para a outra
  carteira; o aplicativo cancela e recomeça o vínculo para a carteira atual.
- Aparelho sem suporte a atestação em hardware (muito antigo ou emulador): tratado como aparelho
  sem integridade (US2).
- Registros feitos no contrato anterior (sem atestação): o aplicativo lê só o novo contrato;
  arquivos registrados apenas no anterior aparecem como "Não registrada". O contrato anterior
  fica como histórico da avaliação anterior.
- Dois vínculos simultâneos da mesma chave: o segundo é recusado (chave já vinculada, 002).
- Chave de atestação de fabricante vazada (keybox) ou TEE comprometido: limite residual
  declarado; a atestação falsificada pode passar enquanto não for revogada pelo Google.

## Requirements _(mandatory)_

### Functional Requirements

**Atestação e vínculo**

- **FR-001**: O aplicativo MUST gerar a chave do aparelho com atestação de hardware, usando como
  desafio um valor ligado à carteira conectada e de uso único.
- **FR-002**: O aplicativo MUST enviar ao atestador apenas a cadeia de atestação, a carteira e o
  desafio; MUST NOT enviar imagens, hashes de imagem ou dados pessoais (princípios VI e VII).
- **FR-003**: O atestador MUST autorizar uma chave somente se todas as condições valerem: cadeia
  válida até uma raiz de atestação do Google; nenhum certificado da cadeia revogado; pacote
  `br.ufv.hashlens` com certificado de assinatura na lista do deploy (desenvolvimento: debug e
  release; avaliação: só release); chave em hardware seguro (TEE ou
  chip dedicado); boot verificado com bootloader travado; desafio emitido para aquela carteira e
  ainda não usado; chave restrita a assinatura.
- **FR-004**: A autorização MUST identificar a carteira, a chave pública do aparelho, a rede, o
  contrato e um prazo de validade de no máximo 15 minutos.
- **FR-005**: O vínculo do aparelho MUST ser recusado pela rede sem autorização válida do
  atestador cujo endereço foi definido no deploy, ou com autorização para outra carteira, chave,
  rede ou contrato, ou vencida.
- **FR-006**: O vínculo MUST publicar a cadeia de atestação completa, de forma que qualquer pessoa
  possa refazer a validação do FR-003 sem o atestador (exceto a consulta de revogação, que usa a
  lista pública do Google).
- **FR-007**: O atestador MUST NOT custodiar chaves de aparelho nem ter permissão para registrar
  imagens; ele só emite autorizações de vínculo.

**Registro**

- **FR-008**: O registro de captura e de edição MUST ser recusado pela rede quando a assinatura do
  aparelho não conferir com a chave vinculada para a mensagem formada pelos dados do registro
  (mesmo formato de mensagem da 002, `signature-payload.md`).
- **FR-009**: As demais regras de registro da 002 (hash exato único, pai confirmado, aparelho da
  própria carteira) MUST continuar valendo.

**Aplicativo**

- **FR-010**: Quando o atestador recusar a chave, o aplicativo MUST informar em linguagem leiga
  o motivo (aparelho modificado, sem hardware seguro, aplicativo não original ou certificado
  revogado), sem abrir a carteira, e manter a imagem como "não registrada".
- **FR-011**: Falha de comunicação com o atestador MUST ser diferenciada de recusa e permitir
  nova tentativa.
- **FR-012**: O progresso do vínculo MUST incluir a etapa de verificação de integridade do
  aparelho, antes de "Vinculando este aparelho…" (002 FR-035).

**Verificação**

- **FR-013**: A verificação MUST revalidar no aparelho, com os dados públicos, a cadeia de
  atestação do aparelho de cada registro exibido, além da assinatura (002 FR-022); falha em
  qualquer uma resulta em "Registro adulterado".
- **FR-014**: O resultado MUST indicar que o registro foi feito pelo HashLens genuíno em aparelho
  atestado e, nos detalhes técnicos, o nível de segurança da chave, o estado do sistema atestado e
  a data do vínculo.
- **FR-015**: O texto do resultado MUST afirmar apenas "registrado pelo HashLens genuíno segundo a
  atestação do Google", nunca que a cena fotografada é real (princípio V).
- **FR-016**: A tela "Como funciona" (002 FR-037) MUST mencionar, em uma frase, que só o HashLens
  original em aparelho íntegro consegue registrar.
- **FR-017**: A verificação MUST consultar a lista pública de revogação do Google para o aparelho
  de cada registro exibido; se o certificado foi revogado depois do vínculo, MUST manter a
  classificação e exibir aviso visível (ícone, título e frase, 002 FR-034) de que o certificado do
  aparelho foi revogado depois do vínculo. Falha ao consultar a lista MUST NOT mudar a
  classificação; o resultado informa que a revogação não pôde ser consultada.

**Implantação**

- **FR-018**: O contrato MUST ganhar novo deploy com o endereço do atestador, e o aplicativo e o
  `quickstart.md` MUST passar a usar o novo endereço. Há dois deploys: o de desenvolvimento e o de
  avaliação, cada um com sua lista de certificados aceitos (FR-003). O aplicativo MUST ler apenas
  o contrato configurado; o contrato anterior não é lido.
- **FR-019**: A chave do atestador e qualquer segredo dele MUST ficar fora do Git e do aplicativo.

### Key Entities _(include if feature involves data)_

- **Aparelho** (alterado da 002): além da chave pública e da carteira, guarda publicamente a
  cadeia de atestação do vínculo. Só existe se o atestador autorizou.
- **Atestação do Aparelho**: cadeia de certificados emitida pelo hardware do aparelho para a
  chave; contém pacote e certificado do aplicativo, nível de segurança da chave, estado do boot e
  o desafio.
- **Autorização de Vínculo**: declaração assinada pelo atestador que permite vincular uma chave a
  uma carteira, num contrato e rede específicos, até um prazo.
- **Atestador**: serviço mínimo que valida a atestação e emite autorizações; seu endereço fica
  fixo no contrato.
- **Desafio**: valor de uso único ligado à carteira, embutido na atestação para impedir reuso.

## Success Criteria _(mandatory)_

### Measurable Outcomes

- **SC-001**: 100% das tentativas de vínculo e registro de origens não autorizadas do conjunto de
  teste (ao menos: vínculo sem autorização, autorização de outra carteira, autorização vencida,
  autorização reutilizada, assinatura de outra chave, dados alterados após assinar, aplicativo
  reassinado) são recusadas pela rede.
- **SC-002**: 100% dos vínculos e registros feitos pelo HashLens genuíno nos aparelhos de teste
  íntegros são aceitos.
- **SC-003**: 100% dos aparelhos de teste sem integridade (emulador e, se disponível, aparelho com
  bootloader desbloqueado) são recusados, com mensagem específica e sem abrir a carteira.
- **SC-004**: A etapa de verificação de integridade acrescenta no máximo 10 s ao primeiro registro
  de um aparelho, em conexão 4G.
- **SC-005**: Registros seguintes num aparelho já vinculado não ficam mais lentos por causa da
  atestação (mesmo tempo de interação da 002 SC-008).
- **SC-006**: 100% dos registros do novo contrato mostram a origem atestada na verificação, e a
  validação independente dos dados públicos de cada aparelho de teste chega ao mesmo resultado do
  atestador.
- **SC-007**: A matriz de classificação da 002 (SC-001 a SC-004) continua com os mesmos
  resultados no novo contrato.

## Assumptions

- A rede Sepolia oferece a verificação de assinaturas P-256 nativa (pré-compilado da EIP-7951);
  a disponibilidade é confirmada antes do planejamento.
- O atestador é um serviço próprio mínimo, sem banco de dados de negócio, operado pela autora
  durante a avaliação; sua indisponibilidade só impede vínculos novos.
- A raiz de confiança é a atestação de chaves do Android e a lista pública de revogação do
  Google; vazamento de chaves de atestação de fabricante é limite residual declarado.
- A atestação não pede identificadores do aparelho (IMEI, número de série); a cadeia publicada
  não identifica a pessoa.
- Apenas aparelhos com atestação em hardware são aceitos; emuladores não registram.
- A verificação de integridade do Google Play (Play Integrity) não é usada: o aplicativo não é
  distribuído pela Play Store.
- Ataques de recaptura e câmeras virtuais em aparelho íntegro continuam fora do escopo (a
  atestação prova a origem do software, não a cena).
