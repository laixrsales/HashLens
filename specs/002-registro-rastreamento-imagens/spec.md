# Feature Specification: Registro, Verificação e Rastreamento de Alterações em Imagens

**Feature Branch**: `feature/002-registro-rastreamento-imagens`

**Created**: 2026-10-04

**Status**: Draft

**Input**: User description: "Aplicativo mobile que captura uma imagem, salvando-a na galeria e
a registra em uma blockchain (Sepolia testnet) segundo a arquitetura proposta. Imagens registradas
podem ser editadas no aplicativo com filtros, mantendo a cadeia de rastreamento de alterações e
identificando quem editou. Qualquer pessoa pode verificar, sem login, se uma imagem está registrada,
se é original ou uma versão alterada e qual o percentual de alteração (distância de Hamming).
O login é feito com uma carteira virtual que possua tokens Sepolia."

## Clarifications

### Session 2026-10-01

- Q: A verificação exibe a imagem original ao lado da imagem enviada? → A: Não. A verificação
  exibe apenas os dados do registro e o percentual de alteração se for editada; as imagens não são
  exibidas lado a lado.
- Q: A busca por hash perceptual é por similaridade ou por igualdade? → A: Por igualdade exata.
  A distância de Hamming é usada para medir o quanto uma versão registrada difere da original
  da sua cadeia.
- Q: Em que momento o hash é calculado em relação à gravação na galeria? → A: Sobre os bytes
  finais do arquivo gravado na galeria (com metadados), confirmados por releitura.
- Q: Serão mantidas assinatura do dispositivo e assinatura da pessoa? → A: Sim, ambas. A do
  dispositivo atesta o aparelho; a da carteira atesta a pessoa.
- Q: A verificação exige login? → A: Não. Verificação é aberta, sem login e sem custo.
- Q: Terceiros podem editar imagens registradas por outra pessoa? → A: Sim, e o sistema deve
  identificar quem realizou cada edição.

### Session 2026-10-04

- Q: Quais arquivos recebidos podem ser abertos no editor e registrados como nova versão? → A:
  Apenas cópias exatas de um registro confirmado; arquivos com apenas correspondência visual são
  recusados, mesmo quando há um único registro correspondente.
- Q: Quando um registro pendente passa a "falhou"? → A: Após 30 minutos sem confirmação da rede;
  antes de permitir o reenvio, o sistema consulta se o arquivo foi registrado nesse intervalo.
- Q: A verificação pode ser iniciada pelo "Compartilhar" de outros aplicativos? → A: Não. A imagem
  é escolhida apenas dentro do aplicativo, pelo seletor de fotos do aparelho.
- Q: O que acontece se a chave do aparelho for perdida (reinstalação, dados apagados, novo
  aparelho)? → A: Os registros antigos continuam válidos; uma nova chave é criada e vinculada à
  carteira no próximo registro; uma carteira pode ter vários aparelhos vinculados.
- Q: Em quais idiomas a interface estará disponível? → A: Apenas português (Brasil); outros
  idiomas estão fora do escopo.

## User Scenarios & Testing _(mandatory)_

### User Story 1 - Capturar e registrar uma imagem (Priority: P1)

O usuário conecta sua carteira (com saldo de tokens de teste), tira uma foto pela câmera do
aplicativo e a imagem é salva na galeria e registrada na blockchain como imagem original,
vinculada à sua carteira e ao seu aparelho. Ela acompanha o andamento do registro até a confirmação.

**Why this priority**: é a base de toda a proposta; sem registro não há o que verificar nem rastrear.
Sozinha, já entrega a "prova de existência" de uma imagem.

**Independent Test**: conectar uma carteira de teste, capturar uma foto e confirmar que o registro
aparece no explorador de blocos com os identificadores calculados a partir do arquivo presente na galeria.

**Acceptance Scenarios**:

1. **Given** um usuário sem sessão, **When** ele abre a função de captura, **Then** o aplicativo
   solicita a conexão de uma carteira antes de permitir a captura.
2. **Given** uma carteira conectada em uma rede diferente da Sepolia, **When** o usuário tenta
   capturar, **Then** o aplicativo solicita a troca para a Sepolia e bloqueia a captura até lá.
3. **Given** uma carteira conectada à Sepolia com saldo suficiente, **When** o usuário captura
   uma foto e aprova a transação na carteira, **Then** a imagem é salva na galeria, seu registro
   aparece como "pendente" e, após a confirmação na rede, como "confirmado", com link para o
   explorador de blocos.
4. **Given** uma carteira com saldo insuficiente para a taxa, **When** o usuário tenta registrar,
   **Then** o aplicativo informa o saldo necessário e indica onde obter tokens de teste.
5. **Given** que o usuário rejeitou a transação na carteira, **When** ele volta ao aplicativo,
   **Then** a imagem permanece na galeria com status "não registrada" e pode ter o registro
   reenviado sem nova captura.
6. **Given** a primeira captura em um aparelho, **When** o registro é solicitado, **Then** o
   aparelho é previamente vinculado à carteira conectada (uma única vez por par aparelho/carteira).

---

### User Story 2 - Verificar a autenticidade de uma imagem (Priority: P2)

Qualquer pessoa, sem login, seleciona uma imagem da galeria e descobre se ela está registrada
na blockchain, se é a captura original ou uma versão editada registrada e, neste caso, qual o
percentual de alteração em relação à original, além de quem a registrou e quando.

**Why this priority**: é o valor entregue ao público alvo (jornalistas, peritos, leitores); depende
apenas de registros existentes, não de login.

**Independent Test**: com registros já existentes no contrato (criados pela US1 ou por script),
verificar arquivos idênticos, arquivos recomprimidos e arquivos não registrados, conferindo cada
classificação.

**Acceptance Scenarios**:

1. **Given** um arquivo idêntico a uma captura registrada, **When** ele é verificado, **Then** o
   resultado é "Registrada – original", com autor, aparelho, data/hora e percentual de alteração 0%.
2. **Given** um arquivo idêntico a uma versão editada registrada, **When** ele é verificado,
   **Then** o resultado é "Registrada – versão alterada", com o percentual de alteração em relação
   à original, o autor da original e o responsável pela edição.
3. **Given** um arquivo que não é idêntico a nenhum registro, mas cujo hash perceptual é igual ao
   de um ou mais registros (ex.: cópia recomprimida por um aplicativo de mensagens), **When** ele
   é verificado, **Then** o resultado é "Correspondência visual", listando os registros
   correspondentes e informando que o arquivo não é uma cópia exata.
4. **Given** um arquivo sem correspondência exata nem perceptual, **When** ele é verificado,
   **Then** o resultado é "Não registrada", com a observação de que isso não significa que a
   imagem seja falsa.
5. **Given** um registro cuja assinatura de aparelho não confere, **When** um arquivo
   correspondente é verificado, **Then** o resultado é "Registro adulterado".
6. **Given** um usuário sem carteira conectada, **When** ele acessa a verificação, **Then** a
   função está disponível normalmente.

---

### User Story 3 - Editar uma imagem própria mantendo a rastreabilidade (Priority: P3)

O usuário abre uma de suas imagens registradas, aplica filtros (ex.: brilho, contraste, preto e
branco) e salva. A nova versão é gravada na galeria como um novo arquivo e registrada na
blockchain vinculada à imagem de onde se originou e à original da cadeia.

**Why this priority**: introduz o rastreamento de alterações, diferencial do trabalho, mas depende
da US1.

**Independent Test**: editar uma imagem registrada, confirmar o registro e verificar o novo
arquivo pela US2, obtendo "versão alterada" com o vínculo correto à original.

**Acceptance Scenarios**:

1. **Given** uma imagem com registro confirmado, **When** o usuário aplica filtros, **Then** o
   aplicativo exibe a pré-visualização e permite desfazer antes de salvar.
2. **Given** uma edição salva e aprovada na carteira, **When** o registro é confirmado, **Then**
   ele referencia a imagem-pai e a original, e lista as operações aplicadas.
3. **Given** uma imagem com registro ainda pendente, **When** o usuário tenta editá-la, **Then**
   o aplicativo informa que é preciso aguardar a confirmação.
4. **Given** uma versão editada confirmada, **When** o usuário a edita novamente, **Then** a nova
   versão referencia a versão editada como pai e mantém a mesma original.
5. **Given** uma imagem que não foi registrada pelo sistema, **When** o usuário tenta abri-la no
   editor, **Then** a edição é recusada com explicação.

---

### User Story 4 - Editar uma imagem registrada por outra pessoa (Priority: P4)

Um usuário recebe uma imagem registrada por outra pessoa, importa-a no aplicativo e, se ela for de fato
uma imagem registrada, a edita. A versão resultante entra na cadeia da imagem original,
identificando-o como responsável pela edição.

**Why this priority**: amplia a rastreabilidade para cenários reais de circulação mas reutiliza o
fluxo da US3 e a verificação da US2.

**Independent Test**: com duas carteiras, registrar uma imagem com a carteira A, importar o
arquivo com a carteira B, editá-lo e verificar que a genealogia mostra A como autora da original
e B como responsável pela edição.

**Acceptance Scenarios**:

1. **Given** um arquivo idêntico a um registro confirmado de outra carteira, **When** o usuário
   o importa para edição, **Then** o aplicativo identifica o registro e libera o editor.
2. **Given** um arquivo sem registro exato, **When** o usuário tenta importá-lo para edição,
   **Then** a importação é recusada, explicando que apenas cópias exatas de imagens registradas
   podem ser editadas.
3. **Given** uma edição de terceiro confirmada, **When** a nova versão é verificada, **Then** o
   resultado mostra o autor da original e o responsável pela edição como pessoas distintas.

---

### User Story 5 - Consultar a genealogia de uma imagem (Priority: P5)

A partir de um resultado de verificação ou de uma imagem do acervo, o usuário visualiza a linha
do tempo da imagem: da captura original até a versão consultada, com quem fez cada etapa, quando,
quais operações foram aplicadas e o percentual de alteração em relação ao pai e à original.
Também pode ver as demais versões derivadas da mesma original.

**Why this priority**: materializa a "auditoria completa da genealogia" proposta; depende de US1–US3.

**Independent Test**: criar uma cadeia de pelo menos três gerações e conferir que a linha do tempo
reconstruída corresponde à ordem e aos responsáveis reais.

**Acceptance Scenarios**:

1. **Given** uma versão registrada de terceira geração, **When** o usuário abre a genealogia,
   **Then** são exibidos os três registros em ordem, da original até a versão consultada.
2. **Given** uma original com várias versões derivadas, **When** o usuário abre a genealogia da
   original, **Then** todas as versões derivadas são listadas como árvore.
3. **Given** qualquer nó da genealogia, **When** exibido, **Then** mostra responsável, aparelho,
   data/hora, operações aplicadas, percentual em relação ao pai e à original, e link para a transação.

---

### User Story 6 - Gerenciar o acervo e a sessão (Priority: P6)

O usuário vê a lista de imagens que capturou ou editou no aplicativo, agrupadas por original,
com o status de cada registro, reenvia registros que falharam, consulta seu saldo e desconecta
a carteira.

**Why this priority**: conveniência e robustez operacional; o núcleo funciona sem ela.

**Independent Test**: provocar uma falha de registro (rejeitar transação), reabrir o aplicativo e
reenviar pelo acervo; desconectar a carteira e verificar o bloqueio de captura/edição.

**Acceptance Scenarios**:

1. **Given** registros pendentes e o aplicativo encerrado, **When** ele é reaberto, **Then** o
   status de cada transação é atualizado automaticamente.
2. **Given** um registro com falha, **When** o usuário escolhe reenviar, **Then** o mesmo arquivo
   é registrado sem nova captura ou edição.
3. **Given** uma carteira conectada, **When** o usuário desconecta, **Then** captura e edição
   ficam bloqueadas e a verificação continua disponível.

---

### Edge Cases

- A imagem é apagada da galeria antes de o registro ser confirmado: o registro continua válido;
  o acervo marca o arquivo local como indisponível.
- O arquivo na galeria é alterado por outro aplicativo (ex.: edição automática ou backup que
  recomprime): a verificação deixa de encontrar correspondência exata e pode retornar
  "Correspondência visual".
- Uma edição produz hash perceptual idêntico ao do pai (alteração imperceptível): o registro é
  aceito e o percentual exibido é 0%, com indicação de que o arquivo difere da original.
- Dois registros distintos compartilham o mesmo hash perceptual: a verificação por
  correspondência visual lista todos.
- Tentativa de registrar um arquivo cujo hash exato já está registrado: o registro é recusado e
  o usuário é direcionado ao registro existente.
- O aplicativo é encerrado com transação enviada e não confirmada: o acompanhamento é retomado
  na próxima abertura.
- A transação fica 30 minutos sem confirmação (descartada ou presa na rede): o registro passa a
  "falhou" e pode ser reenviado; se ela for confirmada depois disso, a consulta anterior ao reenvio
  detecta o registro e evita duplicidade.
- O nó da rede está indisponível durante a verificação: o aplicativo informa a falha de conexão,
  sem retornar "Não registrada".
- Arquivo enviado para verificação em formato não suportado ou corrompido: mensagem de erro
  específica, sem consulta à rede.
- Imagem de altíssima resolução: o processamento ocorre sem travar a interface e com indicação
  de progresso.
- Permissão de câmera ou de acesso a fotos negada: o aplicativo explica o motivo e oferece abrir
  as configurações.
- A carteira troca de conta ou de rede durante uma operação: a operação é cancelada antes do
  envio e o usuário é avisado.
- A chave do aparelho é perdida (reinstalação, dados apagados ou novo aparelho): os registros
  anteriores continuam válidos e atribuídos ao aparelho antigo; o próximo registro vincula uma
  nova chave à carteira.

## Requirements _(mandatory)_

### Functional Requirements

**Sessão e carteira**

- **FR-001**: O sistema MUST permitir conectar uma carteira externa compatível com Ethereum e
  exibir o endereço conectado de forma abreviada.
- **FR-002**: O sistema MUST exigir a rede Sepolia para operações de escrita e solicitar a troca
  de rede quando necessário.
- **FR-003**: O sistema MUST exibir o saldo de tokens Sepolia e bloquear registros quando o saldo
  estimado for insuficiente, indicando como obter tokens de teste.
- **FR-004**: O sistema MUST permitir desconectar a carteira.
- **FR-005**: O sistema MUST NOT solicitar, armazenar ou transmitir chaves privadas ou frases-semente
  da carteira.

**Captura e registro**

- **FR-006**: O sistema MUST permitir capturar fotos exclusivamente pela câmera do próprio
  aplicativo para registro como imagem original.
- **FR-007**: O sistema MUST salvar cada imagem capturada ou editada na galeria do aparelho como
  arquivo independente.
- **FR-008**: O sistema MUST calcular o hash criptográfico exato sobre os bytes finais do arquivo
  salvo na galeria e confirmar, por releitura, que o arquivo gravado corresponde ao hash antes do registro.
- **FR-009**: O sistema MUST calcular o hash perceptual de 64 bits da imagem final.
- **FR-010**: O sistema MUST assinar os dados do registro com uma chave exclusiva do aparelho,
  não exportável, e vincular essa chave à carteira do usuário antes do primeiro registro. Uma
  carteira MAY ter vários aparelhos vinculados; se a chave do aparelho for perdida, o sistema MUST
  criar e vincular uma nova chave no próximo registro, sem invalidar registros anteriores.
- **FR-011**: O sistema MUST registrar na blockchain: hash exato, hash perceptual, assinatura do
  aparelho, aparelho utilizado, responsável (carteira que assinou a transação), data/hora,
  imagem-pai e imagem original da cadeia.
- **FR-012**: O sistema MUST recusar o registro de um hash exato já registrado.
- **FR-013**: O sistema MUST exibir o status de cada registro (não enviado, aguardando
  aprovação, pendente, confirmado, falhou) e o link para a transação no explorador de blocos.
- **FR-014**: O sistema MUST retomar o acompanhamento de transações pendentes após reinício do
  aplicativo e permitir reenviar registros que falharam. Um registro enviado que permanecer sem
  confirmação por 30 minutos MUST passar a "falhou"; antes de reenviar, o sistema MUST consultar
  se o hash exato já foi registrado e, nesse caso, marcar o registro como "confirmado" em vez de
  reenviar.

**Edição**

- **FR-015**: O sistema MUST permitir editar apenas imagens cujo arquivo corresponda exatamente a
  um registro confirmado (capturas e edições próprias, ou arquivos importados de terceiros).
  Arquivos com apenas correspondência visual MUST ser recusados no editor, mesmo quando houver um
  único registro correspondente.
- **FR-016**: O sistema MUST oferecer, no mínimo, os filtros: brilho, contraste, preto e branco,
  sépia, desfoque, nitidez, detecção de bordas, recorte e rotação de 90°, com pré-visualização e
  desfazer.
- **FR-017**: O sistema MUST registrar cada versão editada referenciando a imagem-pai e a original
  da cadeia, a lista de operações aplicadas e a carteira responsável pela edição.
- **FR-018**: O sistema MUST permitir que qualquer usuário autenticado edite imagens registradas
  por outras carteiras, identificando-o como responsável pela edição.

**Verificação**

- **FR-019**: O sistema MUST permitir verificar imagens sem login e sem custo. A imagem a verificar
  é escolhida dentro do aplicativo, pelo seletor de fotos do aparelho; receber imagens
  compartilhadas por outros aplicativos está fora do escopo.
- **FR-020**: O sistema MUST verificar primeiro por igualdade de hash exato e, se não houver
  correspondência, por igualdade de hash perceptual.
- **FR-021**: O sistema MUST classificar o resultado em: Registrada – original; Registrada –
  versão alterada; Correspondência visual; Registro adulterado; Não registrada.
- **FR-022**: O sistema MUST validar a assinatura do aparelho de todo registro exibido.
- **FR-023**: O sistema MUST exibir o percentual de alteração de uma versão em relação à original
  da cadeia, calculado como (distância de Hamming entre os hashes perceptuais ÷ 64) × 100.
- **FR-024**: O sistema MUST exibir no resultado o autor da original, o responsável pela versão
  (quando diferente), o aparelho, a data/hora e o link da transação.
- **FR-025**: O sistema MUST diferenciar falha de comunicação com a rede de "Não registrada".
- **FR-026**: O sistema MUST NOT exibir as imagens original e consultada lado a lado (fora do escopo).

**Genealogia e acervo**

- **FR-027**: O sistema MUST reconstruir a cadeia de uma versão até a original e listar todas as
  versões derivadas de uma original.
- **FR-028**: Para cada nó da genealogia, o sistema MUST exibir responsável, aparelho, data/hora,
  operações aplicadas e percentual de alteração em relação ao pai e à original.
- **FR-029**: O sistema MUST manter um acervo local das imagens capturadas ou editadas no
  aparelho, agrupadas por original.

**Privacidade**

- **FR-030**: O sistema MUST NOT publicar ou enviar a terceiros o conteúdo das imagens; apenas
  identificadores e metadados de registro.
- **FR-031**: O sistema MUST NOT gravar localização nos arquivos por padrão.

**Experiência do usuário** (detalhamento de telas e estados em `docs/referencia/ux.md`)

- **FR-032**: Toda tela MUST tratar os estados vazio, carregando, erro e sucesso aplicáveis; mensagens
  de erro MUST dizer o que aconteceu e qual o próximo passo.
- **FR-033**: Termos técnicos (identificadores da imagem, endereço completo, transação) MUST ficar em
  uma área de detalhes técnicos expansível; a camada principal usa linguagem leiga e o glossário de `ux.md`.
- **FR-034**: Cada status MUST ser comunicado por ícone, título e frase, nunca apenas por cor, e com o
  mesmo vocabulário em todo o aplicativo.
- **FR-035**: Esperas pela confirmação da rede MUST exibir progresso por etapas e informar que o
  usuário pode sair da tela sem perder o acompanhamento.
- **FR-036**: A interface MUST ser utilizável com leitor de tela, com fonte ampliada em até 200%, em
  tema claro e escuro e com alvos de toque no tamanho mínimo recomendado pela plataforma.
- **FR-037**: No primeiro uso, o sistema MUST explicar em uma única tela, que pode ser pulada, o que é
  registrar, o que é verificar uma imagem e como editar uma imagem.
- **FR-038**: Antes de abrir a carteira, o sistema MUST informar em uma frase o que será solicitado e
  que o custo é em tokens de teste Sepolia; antes de registrar uma edição, MUST avisar que o registro é
  permanente.
- **FR-039**: A interface MUST estar em português (Brasil); outros idiomas estão fora do escopo.

### Key Entities _(include if feature involves data)_

- **Registro de Imagem**: entrada imutável na blockchain que representa uma versão de imagem.
  Contém identificador sequencial, hash exato, hash perceptual, aparelho, assinatura do aparelho,
  responsável, data/hora, imagem-pai (vazia para originais), original da cadeia e operações aplicadas.
- **Aparelho**: chave pública de um aparelho vinculada a uma carteira; atesta em qual aparelho o
  registro foi produzido. Uma carteira pode ter vários aparelhos; vínculos não são revogados.
- **Responsável**: carteira que assinou a transação do registro; autor (para originais) ou editor
  (para versões).
- **Cadeia (Genealogia)**: árvore de registros que compartilham a mesma original.
- **Imagem Local**: arquivo na galeria acompanhado do estado do seu registro no aparelho.
- **Resultado de Verificação**: classificação de um arquivo consultado, com registros
  correspondentes e percentual de alteração.

## Success Criteria _(mandatory)_

### Measurable Outcomes

- **SC-001**: 100% dos arquivos idênticos a registros do conjunto de teste são classificados
  corretamente como original ou versão alterada.
- **SC-002**: 100% dos arquivos não registrados do conjunto de teste são classificados como
  "Não registrada" ou "Correspondência visual" (nenhum falso positivo por hash exato).
- **SC-003**: 100% dos registros com assinatura de aparelho adulterada são classificados como
  "Registro adulterado".
- **SC-004**: Ao menos 90% das cópias recomprimidas (qualidade JPEG ≥ 70) de imagens registradas
  são reconhecidas como "Correspondência visual".
- **SC-005**: A genealogia é reconstruída corretamente para cadeias de até 10 gerações.
- **SC-006**: O cálculo dos identificadores de uma foto de 12 MP leva no máximo 2 s em aparelho
  intermediário.
- **SC-007**: O resultado de uma verificação é exibido em até 5 s em conexão 4G.
- **SC-008**: O usuário conclui o fluxo captura → transação aprovada em até 30 s de interação
  (sem contar o tempo de confirmação da rede, que é medido e reportado à parte).

## Assumptions

- O usuário já possui uma carteira compatível com conexão remota de aplicativos instalada no
  aparelho e tokens de teste da Sepolia.
- O hash exato é SHA-256 e o hash perceptual é o pHash baseado em DCT de 64 bits.
- Apenas imagens registradas podem ser editadas (inclusive por terceiros); arquivos com apenas
  correspondência visual não podem ser editados.
- A edição de terceiros não exige autorização do autor original; a blockchain é pública e a
  autoria fica preservada na genealogia.
- Ataques de recaptura (fotografar uma tela ou outra imagem) e a comprovação de que a chave do
  aparelho reside em hardware genuíno estão fora do escopo.
- Plataforma alvo: Android. iOS fora do escopo.
- Não há backend próprio; as imagens ficam apenas no aparelho e na galeria.
- Não existe protótipo prévio: o inventário de telas e estados em `docs/referencia/ux.md` faz esse
  papel, e a direção visual é proposta e aprovada pela autora no início da implementação.
