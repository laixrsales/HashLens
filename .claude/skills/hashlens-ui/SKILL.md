---
name: hashlens-ui
description: Diretrizes de UX e design visual do HashLens para Jetpack Compose (Material 3). Use SEMPRE que for criar ou alterar telas, componentes, tema, navegação, textos de interface (strings.xml) ou previews/screenshot tests do app Android.
---

# HashLens UI

Você é responsável pela experiência do HashLens: um app que prova quando e por quem uma foto foi
registrada e rastreia suas edições. A interface precisa transmitir confiança e precisão sem exigir
que ninguém entenda blockchain. Referências obrigatórias antes de qualquer tela:

- `docs/referencia/ux.md` — jornadas, inventário de telas, estados e glossário (fonte da verdade de UX)
- `docs/design/direcao-visual.md` — direção visual aprovada pela autora (se ainda não existir, crie primeiro; ver Processo)
- `specs/*/spec.md` — requisitos FR-032 a FR-037 e critérios SC-009 a SC-011

## Processo (siga na ordem)

1. **Direção visual (uma vez).** Antes da primeira tela, proponha em `docs/design/direcao-visual.md`:
   - Assunto e público: lente/câmera + prova/perícia; usuárias que fotografam e pessoas que verificam
     (jornalistas, leitores), em sua maioria leigas em blockchain.
   - Paleta: 4–6 cores nomeadas com hex, em versões clara e escura, com contraste AA verificado.
     As cores de status (registrada, alterada, não registrada, adulterado, pendente) são definidas aqui.
   - Tipografia: uma ou duas famílias com papéis claros, mapeadas para a escala do Material 3
     (`displayLarge` … `labelSmall`). Fonte via Google Fonts para Compose ou incluída em `res/font`.
   - Forma e espaçamento: escala de raios e de espaçamento (múltiplos de 4 dp).
   - Movimento: um único momento marcante (sugestão: a confirmação do registro) e transições que
     respondem a ações; respeitar "remover animações" do sistema.
   - Esboços em ASCII das telas Início e Resultado da verificação.
   - Depois, revise a proposta: se algum item for o padrão genérico que você daria a qualquer app
     parecido, troque e explique o porquê.
   - **Pare e peça aprovação da autora.** Não implemente telas sem a direção aprovada.
2. **Tokens.** Implemente a direção em `ui/theme/` (`Color.kt`, `Type.kt`, `Shape.kt`, `Spacing.kt`,
   `Theme.kt`) com `MaterialTheme` e um `CompositionLocal` para tokens extras (cores de status,
   espaçamentos). Telas nunca usam cores, tamanhos ou fontes literais.
3. **Componentes.** Em `ui/components/`, cada componente com `@Preview` de todos os estados,
   claro e escuro, e fonte em 200%.
4. **Telas com dados fictícios.** Cada tela é um composable sem estado (`XxxScreen(state, onAction)`)
   com `@Preview` de TODOS os estados listados em `ux.md` antes de existir ViewModel.
5. **Revisão visual.** Gere screenshots (Roborazzi: `./gradlew :app:recordRoborazziDebug`), abra os
   PNGs gerados e critique: hierarquia, legibilidade, alinhamento, consistência de vocabulário,
   estados de erro. Corrija e só então apresente à autora.
6. **Ligação.** Só depois da aprovação visual da tela, conecte ViewModel e casos de uso.

## Regras de experiência

- **Uma tarefa principal por tela**, com uma ação primária evidente. Ações secundárias são visualmente mais fracas.
- **Linguagem leiga na camada principal.** Hashes, pHash, endereço completo, ids e transação ficam em
  "Detalhes técnicos" (seção expansível). Na camada principal: "registrada por 0x12ab…9f3c",
  "em 4 out. 2026, 14:32", "≈ 9% de alteração visual".
- **Todo estado tem tela**: vazio, carregando, erro, sucesso, e os específicos de `ux.md`.
  Erros dizem o que aconteceu e o que fazer, sem pedir desculpas e sem ser vagos.
  Vazio é um convite à ação.
- **Status nunca só por cor**: ícone + título + frase. O mesmo status usa sempre o mesmo ícone,
  cor e palavra em todo o app (vocabulário em `ux.md`).
- **Esperas longas são transparentes**: confirmação na rede mostra etapas
  (salva na galeria → aprovação na carteira → enviado → confirmado) e deixa claro que a pessoa
  pode sair da tela; o acompanhamento continua no acervo.
- **Honestidade nos resultados** (Constituição V): nunca "verdadeira" ou "falsa"; use os títulos
  exatos de `contracts/verification-result.md`, com o rodapé obrigatório.
- **Carteira sem susto**: antes de abrir o app da carteira, explique em uma frase o que será pedido
  e o custo estimado em SepoliaETH (gratuito, de teste).
- **Mesma ação, mesmo nome** do botão à confirmação: "Registrar foto" → "Foto registrada".
- **Reversível antes de definitivo**: o editor permite desfazer até "Salvar e registrar"; o registro
  é permanente e isso é dito antes da confirmação.

## Regras de Compose / Android

- Material 3 (`androidx.compose.material3`), edge-to-edge com `WindowInsets` tratados, tema claro e
  escuro; cor dinâmica (Material You) desligada para preservar as cores de status.
- Alvos de toque ≥ 48 dp; `contentDescription` em todo ícone com significado (null nos decorativos);
  `Modifier.semantics { heading() }` nos títulos; ordem de foco lógica; nada quebra com fonte em 200%.
- Textos sempre em `res/values/strings.xml` (pt-BR), com plurais em `<plurals>`. Datas e números
  formatados pelo locale.
- Telas sem estado + `UiState` imutável + eventos (`onAction`) ; nada de lógica de negócio em composables.
- Previews com `@PreviewLightDark` e `@PreviewFontScale`, dados de `ui/preview/SampleData.kt`.
- Câmera e editor em tela cheia com controles na zona do polegar (parte inferior).

## Evite (padrões genéricos de app gerado)

- Tudo em cards idênticos com a mesma sombra; use hierarquia tipográfica e espaçamento antes de cards.
- Gradientes decorativos, ícones de "corrente/bloco/cubo" de cripto, verde-neon sobre preto.
- Rótulos em CAIXA ALTA espaçada acima de cada seção; separadores com "·" entre metadados.
- Animações de entrada em todos os elementos; spinners sem texto.
- Jargão na interface principal: "hash", "pHash", "tx", "mint", "on-chain", "Web3".

## Textos

Voz ativa, frases curtas, maiúscula só no início, verbos concretos nos botões ("Verificar imagem",
"Conectar carteira", "Salvar e registrar"). Cada texto faz um único trabalho. Use os termos do
glossário de `ux.md` e não crie sinônimos.
