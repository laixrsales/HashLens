# Auditoria de acessibilidade (T097)

Checklist de `docs/referencia/ux.md` §7, aplicado a todas as telas (FR-036). Feita em 13/10/2026 num
Samsung Galaxy M52 5G (Android 13).

## Resultado

| Item do checklist                               | Situação                             |
| ----------------------------------------------- | ------------------------------------ |
| Funciona com TalkBack: ordem, títulos, ícones   | Atende (automático e roteiro manual) |
| Nada corta ou sobrepõe com fonte em 200%        | Atende                               |
| Contraste AA no claro e no escuro               | Atende                               |
| Alvos de toque ≥ 48 dp                          | Atende, depois da correção no Acervo |
| Status nunca dependem só de cor                 | Atende                               |
| Animações respeitam "remover animações"         | Atende                               |
| Sem violações críticas no Accessibility Scanner | Atende: 0 ERROR                      |

## Como foi feita

**Teste automático.** `android/app/src/androidTest/java/br/ufv/hashlens/evaluation/AccessibilityAuditTest.kt`
roda no aparelho o Accessibility Test Framework (ATF 4.1.1). É o mesmo motor do Accessibility
Scanner, e checa:

- rótulos de itens clicáveis e de imagens;
- alvos de toque;
- contraste de texto e de imagens, medido na captura da tela;
- texto cortado;
- rótulos duplicados;
- ordem de travessia.

O teste usa a UiAutomation, como o Scanner. A checagem pronta do Compose (`enableAccessibilityChecks`)
exige Android 14 e por isso não foi usada.

- **Cobertura:** os 79 estados das 11 telas, ou seja, as mesmas amostras dos screenshot tests, de
  Como funciona até Carteira, incluindo a folha de conexão e a de opções do acervo.
- **Variantes:** cada estado em tema claro, escuro e com fonte em 200%.
- **Rolagem:** cada estado é checado no topo e a cada página de rolagem. No total foram 428 telas
  checadas, com 14.251 elementos avaliados.
- **Critério de falha:** o teste falha com qualquer resultado ERROR.
- **Controle positivo:** o teste `checksDetectViolations` desenha uma tela propositalmente ruim
  (botão de 16 dp sem rótulo e texto cinza-claro sobre branco) e exige que as três falhas sejam
  acusadas. Assim, um resultado vazio não pode vir de checagens que não enxergaram a tela.
- **Títulos:** o ATF não checa títulos marcados. O teste conta os nós com `heading()` em cada estado.

Dados: [`dados/acessibilidade.csv`](dados/acessibilidade.csv) (cada ERROR ou WARNING, com elemento e
mensagem) e [`dados/acessibilidade-cobertura.csv`](dados/acessibilidade-cobertura.csv) (elementos,
rótulos e títulos por estado, variante e página).

```bash
cd android && ./gradlew :app:installDebug :app:installDebugAndroidTest
adb shell am instrument -w -r -e class br.ufv.hashlens.evaluation.AccessibilityAuditTest \
  br.ufv.hashlens.test/androidx.test.runner.AndroidJUnitRunner
adb pull /sdcard/Android/data/br.ufv.hashlens/files/evaluation/acessibilidade.csv
adb pull /sdcard/Android/data/br.ufv.hashlens/files/evaluation/acessibilidade-cobertura.csv
```

O aparelho precisa estar desbloqueado durante toda a execução, que leva cerca de 10 minutos.

**Revisão no código e nos testes já existentes.**

- **Contraste dos tokens:** coberto por `ThemeContrastTest` (T034), que falha no build se um par de
  cores do tema cair abaixo de 4,5:1 (texto) ou 3:1 (componentes).
- **Status:** `StatusBadge` e `StatusHeader` sempre mostram ícone, título e frase. A cor nunca
  aparece sozinha.
- **Movimento:** todas as animações próprias (régua de alteração, detalhes técnicos, etapas,
  revelação do registro) usam `motionDuration`, que zera a duração com "remover animações". As únicas
  animações do Material 3 são os indicadores de progresso, sempre acompanhados de texto. Conferir no
  passo 7 do roteiro manual.
- **Fonte em 200%:** os screenshot tests já geram cada tela em 200%, rolada até o fim, e a auditoria
  repete isso no aparelho.

## Correção feita

**Acervo: chips de filtro com 32 dp para o TalkBack.** Com a grade rolada, os chips "Todas" e
"Com falha" apareciam com 32 dp de altura (84 ERROR).

- **Causa:** o Compose não recorta os limites de acessibilidade das células pelo clip da rolagem.
  Uma célula parcialmente rolada para cima informava limites que invadiam a faixa dos filtros. Como a
  grade vem depois dos filtros na ordem de desenho, ela "cobria" a parte de baixo dos chips.
- **Efeito:** com o TalkBack, tocar na parte de baixo de um filtro podia focar uma foto escondida.
- **Correção:** `zIndex(1f)` na barra de título e nos filtros (`LibraryScreen.kt`), que passam a ficar
  acima da grade. O visual não muda, porque a grade já era recortada na tela, e os screenshots ficaram
  iguais.

## Avisos (WARNING) analisados

A rodada final tem 0 ERROR e 80 WARNING. Nenhum dos avisos é violação:

| Aviso                                                                                                      | Ocorrências | Análise                                                                                                                                                                        |
| ---------------------------------------------------------------------------------------------------------- | ----------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| Contraste de texto no Editor: "Salvar e registrar" sem alterações e faixa de ferramentas durante um ajuste | 51          | Controles desabilitados de propósito. A WCAG 1.4.3 isenta componentes inativos.                                                                                                |
| Contraste de imagem na "Prévia da foto editada" (Editor)                                                   | 3           | O ATF mediu o conteúdo da foto, não um ícone. Falso positivo.                                                                                                                  |
| Contraste de "9 out. 2026, 12:20" no Acervo escuro                                                         | 2           | Data cortada na borda da rolagem (9 px de altura visível), e a medida pega o antisserrilhado. Inteira, a mesma data não é acusada; o par de cores está no `ThemeContrastTest`. |
| Rótulo repetido "Ver registro na rede" (Histórico)                                                         | 17          | Um por versão da cadeia. Cada um fica dentro do nó da sua versão, que o TalkBack lê antes (data e autor).                                                                      |
| Rótulo repetido "Tentar de novo" (Acervo)                                                                  | 4           | Um por foto com falha, dentro da célula da foto, como acima.                                                                                                                   |
| Rótulo repetido "Conectar carteira" (folha de conexão)                                                     | 3           | O título da folha e o botão têm o mesmo nome por regra ("mesma ação, mesmo nome", hashlens-ui).                                                                                |

Melhoria possível, sem ser exigência: dar aos botões repetidos uma descrição com contexto (ex.:
"Tentar de novo, foto de 9 out. 2026, 12:20").

Estados sem título marcado: Câmera (pronta e processando), Editor e a folha de opções do Acervo. Eles
não têm texto de título na tela: a câmera e o editor são tela cheia com ícones, e a folha começa pelo
selo de status. Todas as telas com barra de título e todos os estados de mensagem (vazio, erro, sem
permissão) marcam o título.

## Roteiro manual com TalkBack

O ATF não julga se a ordem de leitura faz sentido nem se as ações são compreensíveis. Por isso, este
roteiro foi feito no aparelho, com o app real: Configurações → Acessibilidade → TalkBack, navegando com
deslizar para a direita e tocando duas vezes para ativar.

1. **Início:** o título é lido primeiro. Em seguida vêm "Registrar foto" e "Verificar imagem".
2. **Verificar imagem → Resultado (uma foto registrada):** o status é lido antes dos dados. "Detalhes
   técnicos" anuncia se está recolhido ou expandido.
3. **Histórico da imagem:** as versões são lidas em ordem, cada uma com data e autor antes de "Ver
   registro na rede".
4. **Meu acervo:** os filtros anunciam se estão selecionados. Ao tocar numa foto, as opções abrem e o
   foco vai para a folha.
5. **Registrar foto:** o botão de captura tem nome. As etapas do acompanhamento são lidas com o
   estado de cada uma.
6. **Editor:** fechar, desfazer, refazer e cada ferramenta têm nome.
7. Repetir os passos 1 e 2 com a fonte do sistema no máximo e com "Remover animações" ligado.

**Resultado (13/10/2026, feito pela autora):** os 7 passos atenderam, sem correções necessárias.
