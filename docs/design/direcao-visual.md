# Direção visual do HashLens

**Status**: aprovada pela autora em 2026-10-09 (T033) | **Data**: 2026-10-09

Referências: `docs/referencia/ux.md` (telas, estados e vocabulário),
`.claude/skills/hashlens-ui/SKILL.md` (processo e regras) e
`specs/002-registro-rastreamento-imagens/contracts/verification-result.md` (títulos e rodapé).

## 1. Assunto e público

O HashLens é um **laudo de imagem no bolso**: diz quando e por quem um arquivo foi registrado e
quanto ele mudou desde então. Ele não diz se a cena é verdadeira. Quem usa:

- **quem fotografa** (autora, jornalista): quer registrar sem pensar em blockchain e saber que deu
  certo;
- **quem verifica** (leitor, editor de redação, perito): recebeu uma imagem, não tem conta e quer
  uma resposta clara em segundos, dizendo também o que o resultado **não** atesta que uma imagem é falsa,
  já que ela pode não estar registrada.

A metáfora visual vem da fotografia técnica e da perícia, não da cripto:

- **o cartão cinza e a escala de cor**, que o fotógrafo põe na cena para ter uma referência
  objetiva. O app é essa referência para o arquivo;
- **o laboratório de revelação**: uma foto "revelando" (pendente) e depois revelada (confirmada);
- **a régua de evidência** e as **cantoneiras** de enquadramento, que a perícia usa para medir e
  delimitar o que está sendo examinado.

Princípio que organiza tudo: **a cor é informação.** A interface é neutra (papel, grafite e
cinza), e a cor só aparece para indicar um status. Assim, o verde de "Registrada – original" chama
a atenção porque nada mais na tela é colorido.

## 2. Paleta

### 2.1 Base neutra

| Nome          | Papel                                                    | Claro     | Escuro    |
| ------------- | -------------------------------------------------------- | --------- | --------- |
| **Papel**     | fundo das telas                                          | `#F6F4EF` | `#141516` |
| **Papel 2**   | superfícies elevadas (sheet, campo, painel)              | `#ECE9E2` | `#1F2123` |
| **Grafite**   | texto principal, ícones, **ação primária** (botão cheio) | `#1C1D1F` | `#ECE9E2` |
| **Cinza 18%** | texto secundário, metadados                              | `#64656A` | `#A6A7AB` |
| **Contorno**  | divisores, bordas de campo e de botão secundário         | `#8A8B8F` | `#76777B` |

O fundo é um branco levemente quente, de papel fotográfico, e não o branco puro nem o lilás
padrão do Material 3. O botão primário é grafite sobre papel, em vez de uma cor de marca: o
destaque vem do peso, não do matiz.

### 2.2 Cores de status

Cada status tem uma **cor** (ícone, título e destaques) e um **fundo** (faixa do cabeçalho de
status e selos). Ícone e palavra seguem `ux.md` §5; a cor nunca aparece sem eles.

| Status (palavra na UI)          | Ícone          | Nome            | Cor claro | Fundo claro | Cor escuro | Fundo escuro |
| ------------------------------- | -------------- | --------------- | --------- | ----------- | ---------- | ------------ |
| Registrada – original           | `verified`     | Verde folha     | `#2E6A3A` | `#DCEBDC`   | `#8FCB97`  | `#1D3622`    |
| Registrada – versão alterada    | `difference`   | Cianótipo       | `#1E4F86` | `#DCE6F3`   | `#A3C3EE`  | `#192C43`    |
| Correspondência visual          | `image_search` | Violeta         | `#6B4596` | `#ECE2F6`   | `#C9AEEC`  | `#31254A`    |
| Registro adulterado             | `gpp_bad`      | Vermelho óxido  | `#AE2F29` | `#F8DFDC`   | `#FF9F95`  | `#47201C`    |
| Não registrada                  | `help`         | Cinza 18%       | `#5F6065` | `#E6E4DF`   | `#B4B5B9`  | `#2B2C2F`    |
| Registrando…                    | `schedule`     | Âmbar revelação | `#875200` | `#F6E6CC`   | `#EDB766`  | `#3A2A0F`    |
| Não registrada – tentar de novo | `error`        | Vermelho óxido  | `#AE2F29` | `#F8DFDC`   | `#FF9F95`  | `#47201C`    |

Por que cada uma:

- **Verde folha** para a original: o único verde do app, reservado para "este arquivo é idêntico
  a uma captura registrada" e para a confirmação do registro ("Foto registrada").
- **Cianótipo** para versões: o cianótipo é um processo fotográfico de **cópia**. Uma versão
  alterada é legítima e registrada, mas derivada; por isso não usa o verde da original.
- **Violeta** para correspondência visual: "parecida, mas não idêntica" não é sucesso nem alerta.
  Uma cor fora do eixo verde–vermelho evita que a pessoa leia o resultado como aprovação ou como
  fraude.
- **Cinza 18%** para "Não registrada": é a cor **neutra** por definição. Não usar vermelho aqui
  é uma decisão de honestidade (Constituição V): "nenhum registro encontrado" não significa que a
  imagem seja falsa.
- **Âmbar revelação** para "Registrando…": a luz de segurança do laboratório, enquanto a foto
  revela.
- **Vermelho óxido** para o que deu errado: adulteração (resultado da verificação) e falha de
  registro (acervo e acompanhamento). Os dois nunca aparecem na mesma tela e se distinguem pelo
  ícone e pela palavra. É também o `error` do Material 3, usado nas mensagens de erro.

### 2.3 Contraste verificado (WCAG 2.1)

Calculado com a fórmula de luminância relativa da WCAG (mínimos: 4,5:1 para texto, 3:1 para
componentes e ícones).

| Combinação                           | Claro         | Escuro        |
| ------------------------------------ | ------------- | ------------- |
| Grafite sobre Papel / Papel 2        | 15,35 / 13,91 | 15,08 / 13,32 |
| Cinza 18% sobre Papel / Papel 2      | 5,29 / 4,80   | 7,61 / 6,72   |
| Contorno sobre Papel                 | 3,10          | 4,09          |
| Botão primário (Papel sobre Grafite) | 15,35         | 15,08         |
| Cor de status sobre o próprio fundo  | 4,94 a 6,62   | 6,82 a 7,82   |
| Cor de status sobre Papel            | 5,71 a 7,59   | 8,92 a 10,10  |
| Grafite sobre fundo de status        | 13,28 a 13,74 | 10,80 a 11,68 |

Todas passam no AA. Os valores de cada par ficam como teste de unidade ao implementar os tokens
(T034), para que uma mudança de cor que quebre o contraste falhe no build.

**Cor dinâmica (Material You) desligada**: as cores de status não podem variar com o papel de
parede.

## 3. Tipografia

Uma família com duas variantes:

- **Atkinson Hyperlegible Next**: todo o texto da interface. Foi desenhada pelo Braille Institute
  para leitores com baixa visão. As letras que se confundem (`I l 1`, `O 0`, `rn m`) têm formas
  distintas, o que importa num app que pede para conferir endereços e números e que precisa
  funcionar com fonte em 200%.
- **Atkinson Hyperlegible Mono**: endereços (`0x12ab…9f3c`), identificadores, transação e
  percentuais na régua. Usa a mesma família, então não destoa, e os dígitos alinham em coluna.

As duas são livres (SIL Open Font License) e ficam **incluídas em `res/font`**, não baixadas pelo
Google Fonts: os screenshots do Roborazzi rodam sem rede e precisam da fonte real.

| Papel no M3         | Fonte | Peso     | Tamanho / altura | Uso                                                           |
| ------------------- | ----- | -------- | ---------------- | ------------------------------------------------------------- |
| `displaySmall`      | Next  | Bold     | 34 / 42          | pergunta do Início                                            |
| `headlineMedium`    | Next  | Bold     | 28 / 36          | título de status (Resultado, Acompanhamento)                  |
| `headlineSmall`     | Next  | SemiBold | 24 / 32          | título de tela                                                |
| `titleLarge`        | Next  | SemiBold | 20 / 28          | título de seção, sheet                                        |
| `titleMedium`       | Next  | SemiBold | 16 / 24          | item de lista, fato principal                                 |
| `titleSmall`        | Next  | SemiBold | 14 / 20          | rótulo de grupo                                               |
| `bodyLarge`         | Next  | Regular  | 17 / 26          | frase explicativa do status, textos corridos                  |
| `bodyMedium`        | Next  | Regular  | 15 / 22          | fatos, descrições                                             |
| `bodySmall`         | Next  | Regular  | 13 / 18          | rodapé obrigatório, legendas                                  |
| `labelLarge`        | Next  | SemiBold | 15 / 20          | botões                                                        |
| `labelMedium`       | Next  | SemiBold | 13 / 16          | selos de status, chip da carteira                             |
| `labelSmall`        | Next  | Medium   | 11 / 16          | marcas da régua                                               |
| `technical` (extra) | Mono  | Regular  | 14 / 20          | endereço, identificadores e transação nos "Detalhes técnicos" |

O corpo é um pouco maior que o padrão do M3 (17 e 15 em vez de 16 e 14) porque a frase que
explica o resultado é o conteúdo mais importante da tela.

## 4. Forma e espaçamento

**Raios**: pequenos, de instrumento e etiqueta, não de bolha.

| Token M3     | Raio  | Uso                                              |
| ------------ | ----- | ------------------------------------------------ |
| `extraSmall` | 2 dp  | marcas e cursor da régua                         |
| `small`      | 4 dp  | selos de status, miniaturas                      |
| `medium`     | 8 dp  | botões, campos, chip da carteira, painel técnico |
| `large`      | 12 dp | cabeçalho de status                              |
| `extraLarge` | 16 dp | topo dos bottom sheets                           |

Os botões têm 8 dp, não a pílula padrão do M3: parecem um controle de equipamento, e o botão
primário em grafite fica mais sóbrio.

**Espaçamento** (múltiplos de 4 dp), em `HashLensTheme.spacing`:

| Token  | Valor | Uso                                             |
| ------ | ----- | ----------------------------------------------- |
| `xs`   | 4 dp  | ícone ↔ texto do selo                           |
| `sm`   | 8 dp  | entre linhas de um mesmo fato                   |
| `md`   | 12 dp | entre itens de lista                            |
| `lg`   | 16 dp | margem lateral das telas; padding interno       |
| `xl`   | 24 dp | entre blocos de uma tela                        |
| `xxl`  | 32 dp | antes da ação primária; entre seções principais |
| `xxxl` | 48 dp | respiro do topo nas telas de resultado          |

Alvos de toque ≥ 48 dp; botão primário com 56 dp de altura.

**Hierarquia sem cards**: as telas são separadas por espaço e por **divisores finos** (1 dp,
Contorno), como num laudo. O único bloco com fundo é o **cabeçalho de status**, porque é a
resposta da tela.

## 5. Elementos de assinatura

Dois elementos aparecem só nas telas de registro e verificação e dão identidade ao app:

1. **Cantoneiras**: quatro marcas em "L" (2 dp, cor do status) nos cantos da miniatura da imagem
   examinada, no Resultado, no Acompanhamento e no visor da Câmera (em branco). Dizem "esta é a
   imagem sob exame" sem moldura nem sombra.
2. **Régua de alteração** (`AlterationMeter`): uma régua de evidência horizontal de 0 a 100%,
   com marcas a cada 10% e números em Mono, e um cursor na posição do percentual. O texto
   "≈ 12,5% de alteração visual" aparece sempre ao lado; a régua não tem rótulos como "pouco" ou
   "muito", porque o app mede e não julga. Em 0%, o cursor fica no zero e o texto segue
   `PercentFormat`.

## 6. Movimento

**O momento marcante: a revelação.** Quando o registro é confirmado (Acompanhamento → "Foto
registrada"), a miniatura, que ficou em tons de cinza com a borda âmbar durante "Registrando…",
**revela** para as cores reais em cerca de 600 ms. Ao mesmo tempo, as cantoneiras se fecham sobre
ela e mudam para o verde folha, e o título troca para "Foto registrada" com uma vibração curta
(`HapticFeedbackType.Confirm`). É a única animação de "celebração" do app.

Demais transições respondem a ações e são curtas:

- navegação entre telas: deslizar no eixo horizontal com fade, 250 ms;
- "Detalhes técnicos": expandir e recolher, 200 ms;
- etapas do Acompanhamento: a etapa concluída troca o ícone com um fade, 150 ms;
- cursor da régua: desliza até o valor, 300 ms, só na primeira exibição.

Nada entra animado por padrão (listas e textos aparecem prontos). Com **"Remover animações"**
ligado no sistema (escala de animação 0), todas as transições são instantâneas: a miniatura
aparece já colorida e o título muda de uma vez. A vibração continua, porque não é animação.

Indicadores de espera sempre com texto ("Consultando registros…"), nunca um spinner sozinho.

## 7. Esboços

### 7.1 Início (carteira conectada, com registros pendentes)

```text
┌──────────────────────────────────────┐
│ HashLens              [◉ 0x12ab…9f3c] │  chip da carteira (labelMedium, borda Contorno)
│                                      │
│                                      │
│ Esta imagem está                     │  displaySmall, Grafite
│ registrada?                          │
│                                      │
│ Descubra quando e por quem ela foi   │  bodyLarge, Cinza 18%
│ registrada. Não precisa de conta.    │
│                                      │
│ ┌──────────────────────────────────┐ │
│ │  ⌕  Verificar imagem             │ │  botão cheio Grafite, 56 dp
│ └──────────────────────────────────┘ │
│ Aceita JPEG, PNG e WebP.             │  bodySmall, Cinza 18%
│                                      │
│ ──────────────────────────────────── │  divisor
│ Suas fotos                           │  titleSmall
│                                      │
│ ◎  Registrar foto                  › │  titleMedium; 56 dp por linha
│    Fotografe e registre na hora      │  bodyMedium, Cinza 18%
│ ▦  Meu acervo                      › │
│    ◷ 2 registrando                   │  selo Âmbar revelação (ícone + texto)
│ ✎  Importar para editar            › │
│    Edite uma foto registrada         │
│                                      │
│                    Como funciona  ⓘ  │  botão de texto
└──────────────────────────────────────┘
```

A verificação é a ação primária (a única que não precisa de conta) e a única com botão cheio. As
ações de quem fotografa vêm abaixo, como lista, mais fracas visualmente. Sem carteira, o chip
diz "Conectar carteira"; em rede errada, o chip fica com contorno e ícone em Vermelho óxido e o
texto "Rede errada".

### 7.2 Resultado da verificação (Registrada – versão alterada)

```text
┌──────────────────────────────────────┐
│ ←  Resultado                         │  headlineSmall
│                                      │
│ ┌──────────────────────────────────┐ │  cabeçalho de status: fundo Cianótipo claro, raio 12
│ │ ⧉  Registrada – versão alterada  │ │  ícone difference + headlineMedium em Cianótipo
│ │                                  │ │
│ │ Versão editada registrada.       │ │  bodyLarge, Grafite
│ │ ≈ 12,5% de alteração visual em   │ │
│ │ relação à original.              │ │
│ └──────────────────────────────────┘ │
│                                      │
│   ⌜              ⌝                   │  miniatura com cantoneiras em Cianótipo
│      [ miniatura ]                   │
│   ⌞              ⌟                   │
│                                      │
│ Alteração em relação à original      │  titleSmall
│ 0   10  20  30  40  50 … 100         │  régua: marcas Mono (labelSmall)
│ ├──┼▲─┼───┼───┼───┼── … ─┤           │  cursor ▲ em 12,5%
│ ≈ 12,5% de alteração visual          │  bodyMedium
│ ──────────────────────────────────── │
│ Editada por      0x7c4e…a210         │  rótulo Cinza 18% / valor titleMedium
│ Em               4 out. 2026, 14:32  │
│ Original de      0x12ab…9f3c         │
│                  2 out. 2026, 09:05  │
│ Aparelho         nº 3                │
│ ──────────────────────────────────── │
│ ┌──────────────────────────────────┐ │
│ │  Ver histórico da imagem         │ │  botão contornado (secundário)
│ └──────────────────────────────────┘ │
│ Detalhes técnicos                  ⌄ │  expansível; conteúdo em Mono
│ ──────────────────────────────────── │
│ O registro comprova quando e por     │  rodapé obrigatório, bodySmall,
│ quem o arquivo foi registrado, não   │  Cinza 18%, sempre visível
│ que a cena retratada seja real.      │  ao fim da rolagem
└──────────────────────────────────────┘
```

Em "Registrada – original", o cabeçalho é verde e a régua some (não há alteração a medir). Em
"Não registrada", o cabeçalho é cinza, sem miniatura com cantoneiras coloridas, e a ação primária
vira "Verificar outra imagem".

## 8. Revisão crítica da proposta

Itens que saíram como o padrão genérico na primeira versão e foram trocados:

| Primeira ideia (genérica)                                      | Troca                                       | Por quê                                                                                                                       |
| -------------------------------------------------------------- | ------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------- |
| Cor primária azul "de confiança" em botões, links e cabeçalhos | Interface neutra; botão primário em Grafite | O azul de confiança está em todo app de banco e de cripto. Com a interface neutra, a cor fica reservada para o status.        |
| Vermelho para "Não registrada"                                 | Cinza 18%                                   | Vermelho sugere fraude, e o contrato de verificação diz que a ausência de registro não torna a imagem falsa (Constituição V). |
| Roboto (padrão) ou Inter                                       | Atkinson Hyperlegible Next + Mono           | Fonte com motivo: legibilidade para baixa visão e caracteres inconfundíveis ao conferir endereços.                            |
| Cards com sombra para cada fato do resultado                   | Divisores finos e espaço, como num laudo    | Cards iguais achatam a hierarquia; só a resposta (cabeçalho de status) ganha bloco.                                           |
| Botões em pílula (padrão M3)                                   | Raio de 8 dp                                | Pílula é o visual padrão de qualquer app M3; 8 dp combina com o tom de instrumento.                                           |
| Barra de progresso colorida para o percentual                  | Régua de evidência com marcas               | Barra de progresso sugere "quanto falta"; a régua sugere medida, que é o que o percentual é.                                  |
| Confete ou check animado ao confirmar                          | Revelação da miniatura (cinza → cor)        | Liga o pendente (âmbar, luz de segurança) ao confirmado e fala a língua da fotografia, sem festa.                             |
| Spinner genérico durante as esperas                            | Etapas nomeadas e texto em cada espera      | A pessoa sabe o que está acontecendo e que pode sair da tela (FR-035).                                                        |

Mantidos de propósito: verde para "original" (convenção forte de "confere", reforçada pelo ícone
`verified`) e vermelho para adulteração e falha (convenção de erro do sistema).

## 9. O que precisa da aprovação da autora

1. O conceito "cor é informação" (interface neutra, botão primário em grafite).
2. A paleta de status, em especial **Cinza para "Não registrada"** e **Violeta para
   "Correspondência visual"**.
3. A fonte **Atkinson Hyperlegible Next/Mono**, incluída no app.
4. Os elementos de assinatura (cantoneiras e régua) e a **revelação** como momento marcante.
5. Os esboços do Início (verificação como ação primária) e do Resultado.

Com a aprovação, o T034 implementa os tokens em `ui/theme/` (`Color.kt`, `Type.kt`, `Shape.kt`,
`Spacing.kt`, `Theme.kt`) e um teste de contraste com os pares da §2.3.
