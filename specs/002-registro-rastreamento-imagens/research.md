# Research: Registro, Verificação e Rastreamento de Alterações em Imagens

**Feature**: `002-registro-rastreamento-imagens` | **Date**: 2026-10-04

Formato por item: **Decision** / **Rationale** / **Alternatives considered**.
Itens marcados com ⚠️ dependem de versões de bibliotecas que devem ser confirmadas no momento do
setup (as versões mudam com frequência).

---

## R1. Conexão com a carteira

- **Decision**: Reown AppKit para Android (antigo WalletConnect/Web3Modal), com integração
  Jetpack Compose. ⚠️ confirmar versão estável atual.
- **Rationale**: conecta a carteiras já instaladas (MetaMask, Rainbow, Trust etc.) sem que o app
  toque em chaves privadas (Constituição III); suporta troca de rede e `eth_sendTransaction`.
- **Alternatives considered**: MetaMask SDK Android (prende o usuário a uma carteira); carteira
  embutida no app (viola o Princípio III); login social com carteira custodial (introduz
  intermediário, contrário à proposta do TCC).

## R2. Cliente Ethereum (leitura, ABI, recibos)

- **Decision**: web3j (módulo core) para codificar/decodificar ABI, ler o contrato via JSON-RPC
  (`eth_call`), estimar gás e acompanhar recibos. O envio da transação é feito pela carteira via
  AppKit com o `data` codificado pelo web3j. ⚠️ confirmar versão compatível com Android.
- **Rationale**: biblioteca madura em JVM; permite gerar wrappers tipados a partir do ABI.
- **Alternatives considered**: KEthereum (menos ativo); chamadas JSON-RPC manuais com OkHttp
  (mais código e risco de erro na codificação ABI).

## R3. Ferramental do contrato

- **Decision**: Solidity ^0.8.24 com Foundry (`forge` para testes e fuzzing, `forge script` para
  deploy na Sepolia, `cast` para inspeção).
- **Rationale**: testes escritos em Solidity, rápidos, com fuzzing nativo (Princípio IV);
  relatório de gás útil para os testes de escalabilidade.
- **Alternatives considered**: Hardhat (testes em JS/TS, outro ecossistema além do Kotlin);
  Remix (sem testes automatizados reproduzíveis).

## R4. Chave do aparelho

- **Decision**: par EC P-256 (secp256r1) gerado no Android Keystore, **um alias por carteira**
  (`hashlens-device-<endereço em minúsculas>`), não exportável,
  `PURPOSE_SIGN`, digest SHA-256; StrongBox quando disponível (`setIsStrongBoxBacked(true)` com
  fallback para TEE). Sem exigir autenticação biométrica por assinatura no MVP.
- **Rationale**: o Keystore não suporta secp256k1; P-256 é a curva com suporte em hardware.
  A chave nunca sai do aparelho (Princípio III).
- **Por que um alias por carteira**: o contrato rejeita uma chave pública já registrada
  (`DeviceAlreadyRegistered`, data-model §1.1). Com alias único, a segunda carteira conectada no
  mesmo aparelho (US1 cenário 6, "par aparelho/carteira"; US4 com carteiras A e B) nunca
  conseguiria vincular o aparelho. Com uma chave por carteira, cada par aparelho/carteira tem seu
  próprio `deviceId`, e a mesma chave nunca atesta duas pessoas.
- **Alternatives considered**: alias fixo por aparelho (bloqueia a segunda carteira, ver acima);
  permitir no contrato a mesma chave em várias carteiras (enfraquece o vínculo aparelho ↔ pessoa);
  chave secp256k1 em software (compatível com `ecrecover`, mas exportável e sem proteção de
  hardware); exigir biometria por assinatura (fricção adicional sem ganho relevante na PoC).

## R5. Verificação da assinatura do aparelho

- **Decision**: verificação off-chain, no aplicativo (`java.security.Signature`,
  `SHA256withECDSA`), usando a chave pública registrada on-chain para o aparelho. A assinatura é
  armazenada on-chain no formato bruto `r || s` (2 × 32 bytes).
- **Rationale**: mantém o contrato simples e barato; a verificação é pública e reproduzível por
  qualquer pessoa com os dados on-chain.
- **Alternatives considered**: verificação on-chain via pré-compilado P-256 (EIP-7951 / RIP-7212)
  — não adotada na PoC para não depender da disponibilidade do pré-compilado na rede e não
  aumentar o custo de gás; registrada como trabalho futuro. Biblioteca P-256 em Solidity pura
  (gás muito alto).

## R6. Assinatura da pessoa

- **Decision**: a própria transação, assinada pela carteira (ECDSA secp256k1), atesta a pessoa;
  o contrato grava `msg.sender` como `registrant` de cada registro.
- **Rationale**: a rede já valida essa assinatura; uma segunda assinatura da carteira sobre o
  mesmo conteúdo seria redundante e adicionaria uma segunda aprovação na carteira.
- **Alternatives considered**: `personal_sign` (EIP-191) adicional armazenado no registro — mais
  gás, mais fricção, mesma garantia.

## R7. Vínculo aparelho ↔ pessoa e proteção contra replay

- **Decision**: (a) cada carteira registra cada chave de aparelho uma única vez (`registerDevice`),
  pode ter vários aparelhos vinculados (clarificação de 2026-10-04, ver R25), e o
  contrato só aceita registros com `deviceId` pertencente a `msg.sender`; (b) o payload assinado
  pelo aparelho inclui domínio, `chainId`, endereço do contrato, carteira, hashes, pai e
  `deviceId` (ver `contracts/signature-payload.md`).
- **Rationale**: impede que uma pessoa use o aparelho de outra e que uma assinatura seja
  reaproveitada em outra rede, contrato ou carteira.
- **Alternatives considered**: gravar a chave pública em cada registro (mais gás, sem vínculo
  explícito com a carteira).

## R8. Ordem: galeria → hash

- **Decision**: (1) gerar em memória os bytes JPEG finais, já com EXIF; (2) calcular SHA-256 e
  pHash desses bytes; (3) gravar exatamente esses bytes via MediaStore; (4) reler o arquivo pelo
  `ContentResolver` e recalcular o SHA-256; (5) somente se igual, assinar e enviar a transação.
- **Rationale**: atende à clarificação "hash sobre o arquivo da galeria" e detecta qualquer
  alteração introduzida na gravação (Princípio I). Gravar primeiro e só depois calcular também
  funciona, mas sem uma referência para detectar divergências.
- **Alternatives considered**: hash do bitmap antes da codificação (não corresponderia ao
  arquivo); hash sem releitura (risco silencioso).

## R9. Implementação do pHash

- **Decision**: implementação própria em Kotlin com OpenCV core, espelhando o algoritmo do
  `imagehash.phash` (Python): cinza → redimensiona 32×32 → DCT → bloco 8×8 de baixa frequência →
  bit = valor > mediana. Especificação normativa em `contracts/hashing-spec.md`.
- **Rationale**: o módulo `img_hash` do OpenCV pertence ao `opencv_contrib` e não está no SDK
  Android oficial. Implementar o algoritmo explicitamente garante determinismo (Princípio II).
- **Alternatives considered**: compilar o OpenCV com `contrib` para Android (build pesado e
  frágil); aHash/dHash (menos robustos a compressão).

## R10. Orientação e decodificação

- **Decision**: os pixels são rotacionados fisicamente no momento da captura e o EXIF
  `Orientation` é gravado como 1. Na verificação, a decodificação aplica a orientação EXIF, se
  houver, antes do pHash.
- **Rationale**: evita que a mesma imagem gere pHashes diferentes conforme a orientação.
- **Alternatives considered**: ignorar EXIF (falhas em arquivos de terceiros).

## R11. Busca por pHash on-chain

- **Decision**: `mapping(uint64 => uint256[])` com consulta por igualdade exata (clarificação de
  2026-10-01).
- **Rationale**: consulta O(1) via `eth_call`, sem indexador externo.
- **Consequência**: cópias com qualquer bit de pHash diferente não são encontradas; versões
  editadas no próprio app são encontradas por SHA-256 (têm registro próprio).
- **Alternatives considered**: busca por similaridade (varredura de eventos off-chain e cálculo
  de Hamming contra todos os registros) — descartada pela autora; pode ser trabalho futuro.

## R12. Operações de edição

- **Decision**: string curta em armazenamento no registro, formato `op[:param];op[:param]`
  (ex.: `brightness:+30;grayscale;crop:0.1,0.1,0.8,0.8`).
- **Rationale**: leitura direta via `getRecord`, sem consulta de logs; custo aceitável em testnet.
- **Alternatives considered**: apenas em evento (mais barato, mas exige `eth_getLogs`); código
  numérico por filtro (menos legível).

## R13. Duplicidade

- **Decision**: o contrato rejeita SHA-256 já registrado (`AlreadyRegistered`); o app verifica
  antes de enviar.
- **Rationale**: garante unicidade de `sha256 → recordId`, base do fluxo de verificação.

## R14. Leitura sem login

- **Decision**: nó RPC configurável (Alchemy/Infura ou público) usado pelo web3j para todas as
  leituras; nenhuma carteira é necessária para verificar e consultar genealogia.
- **Rationale**: leitura não consome gás (clarificação de 2026-10-01).

## R15. Captura

- **Decision**: CameraX (`ImageCapture`), saída em memória, codificação JPEG qualidade 95 pelo
  próprio app.
- **Rationale**: controle total dos bytes finais (R8).

## R16. Arquitetura do app

- **Decision**: MVVM + casos de uso; Hilt (DI); Kotlin Coroutines/Flow; Room para o acervo local;
  WorkManager para acompanhar transações pendentes; Navigation Compose; Photo Picker para seleção.
  O app **não** declara `intent-filter` de `ACTION_SEND`/`ACTION_VIEW` para imagens: a verificação
  começa apenas pelo Photo Picker dentro do app (clarificação de 2026-10-04, FR-019).
- **Rationale**: padrão Android moderno, testável por camada.

## R17. Percentual de alteração

- **Decision**: `percent = hamming(pHashVersão, pHashOriginal) / 64 × 100`, uma casa decimal;
  na genealogia também em relação ao pai.
- **Rationale**: definido pelo usuário.

## R18. Elegibilidade para edição de terceiros

- **Decision (confirmada na clarificação de 2026-10-04)**: somente arquivos com correspondência
  exata de SHA-256 a um registro confirmado podem ser editados; correspondência visual é recusada
  mesmo com um único registro correspondente (FR-015).
- **Rationale**: a edição precisa de um `parentId` inequívoco; correspondência visual pode
  retornar vários registros e o arquivo não é o registrado.
- **Alternatives considered**: permitir edição de correspondência visual escolhendo um dos
  registros como pai (amplia o uso com arquivos recebidos por mensageiros, mas enfraquece a
  garantia do vínculo).

## R19. Limitação conhecida: atestação do aparelho

- **Decision**: fora do escopo do MVP. Sem Android Key Attestation, o sistema prova "mesma chave
  vinculada à carteira", não "chave em hardware de um aparelho Android genuíno".
- **Trabalho futuro**: registrar o hash da cadeia de certificados de atestação no `registerDevice`.

## R20. Sistema de design

- **Decision**: Material 3 para Compose com tema próprio (paleta, tipografia, formas e
  espaçamentos definidos em `docs/design/direcao-visual.md`), cor dinâmica desligada e tokens extras
  (cores de status, espaçamentos) via `CompositionLocal`.
- **Rationale**: componentes acessíveis e familiares no Android, com identidade própria; cor
  dinâmica alteraria as cores de status, que precisam ser estáveis (FR-034).
- **Alternatives considered**: Material 3 sem personalização (genérico); design system do zero
  (custo alto, perde acessibilidade pronta).

## R21. Protótipo por previews e screenshot tests

- **Decision**: telas sem estado com `@Preview` de cada estado (claro/escuro, fonte 200%) e
  screenshot tests com Roborazzi (JVM, sem emulador). Os PNGs servem para revisão da autora e para
  o agente criticar o próprio resultado. ⚠️ confirmar versão do Roborazzi.
- **Rationale**: substitui o protótipo inexistente com custo baixo; as previews viram regressão visual.
- **Alternatives considered**: Paparazzi (equivalente; Roborazzi integra melhor com Robolectric já usado).

## R22. Diretrizes de UI para o agente

- **Decision**: skill de projeto `.claude/skills/hashlens-ui/SKILL.md`, carregada pelo Claude Code
  sempre que houver trabalho de interface, adaptando princípios de design visual para Compose/Material 3
  e incorporando as regras de UX do projeto.
- **Rationale**: skills genéricas de frontend são orientadas a web (HTML/CSS) e a identidade visual;
  não cobrem estados, fluxos, convenções Android nem as regras de honestidade dos resultados.

## R23. Prazo de confirmação e reenvio seguro

- **Decision**: ao receber o `txHash`, o app grava `submittedAt`. O `PendingTxWorker` (WorkManager,
  periódico e na abertura do app) consulta o recibo; sem recibo após **30 min**, o registro passa
  a `FAILED` com motivo `TIMEOUT`. Antes de qualquer reenvio, o app chama
  `getIdBySha256(sha)`: se ≠ 0, o registro local vai direto para `CONFIRMED` com esse `recordId`
  (a transação "perdida" foi confirmada depois do prazo); só se = 0 abre a carteira de novo.
  A mesma assinatura do aparelho (`signatureHex`) é reutilizada no reenvio, que por isso exige a
  mesma carteira do registro (o payload inclui o `registrant`); com outra carteira conectada, o
  reenvio é bloqueado com pedido para conectar a original. O worker também
  reconcilia pelo hash antes de qualquer `FAILED` (recibo com status 0 ou prazo estourado): se
  `getIdBySha256(sha) ≠ 0`, o registro vai a `CONFIRMED`. Isso cobre a transação antiga confirmada
  depois do reenvio, quando a nova reverte com `AlreadyRegistered` (data-model §2.3).
- **Rationale**: clarificação de 2026-10-04 (FR-014). Na Sepolia a confirmação costuma levar
  menos de 1 min; 30 min separa atraso de descarte, e a consulta prévia evita revert
  `AlreadyRegistered` e cobrança de gás inútil.
- **Alternatives considered**: sem prazo (registro preso em "pendente"); 10 min com reenvio
  imediato (risco de duplicidade); depender de o nó informar descarte (nós públicos não garantem
  essa informação).

## R24. Idioma da interface

- **Decision**: somente português (Brasil). Todos os textos ficam em `res/values/strings.xml`
  (sem pasta de outro idioma), vocabulário do glossário de `docs/referencia/ux.md`. Números de
  percentual exibidos com vírgula decimal; a serialização on-chain continua com ponto
  (hashing-spec §4).
- **Rationale**: clarificação de 2026-10-04 (FR-039); público e avaliação do TCC no Brasil.
- **Alternatives considered**: português e inglês, com ou sem troca manual (custo de tradução e
  de revisão das telas em dois idiomas sem ganho para a avaliação).

## R25. Perda da chave do aparelho

- **Decision**: o `EnsureDeviceBoundUseCase` roda antes de cada registro: se o alias da carteira
  não existe no Keystore, gera uma nova chave; em seguida procura em `getDevicesOf(carteira)` um
  aparelho com a mesma chave pública e, se não houver, chama `registerDevice`. Registros antigos
  continuam válidos e atribuídos ao `deviceId` antigo, cuja chave pública segue on-chain. Vínculos
  nunca são revogados.
- **Rationale**: clarificação de 2026-10-04 (FR-010). A chave não é exportável (Princípio III),
  então não há recuperação; o novo vínculo é o único caminho e não invalida o passado.
- **Alternatives considered**: revogar o aparelho antigo ao vincular um novo (exigiria função e
  estado extra no contrato sem necessidade na PoC); limitar a um aparelho por carteira (forçaria
  uma carteira nova a cada reinstalação).
