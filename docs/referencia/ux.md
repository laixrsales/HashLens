# UX: HashLens — Jornadas, Telas e Estados

**Feature**: `001-registro-rastreamento-imagens` | **Date**: 2026-10-04

Este documento substitui o protótipo: define o que cada tela mostra, quais ações oferece e quais
estados precisa tratar. A aparência (cores, tipos, formas) fica em `docs/design/direcao-visual.md`,
criado no início da implementação e aprovado pela autora.

## 1. Pessoas

| Pessoa | Contexto | O que precisa |
|---|---|---|
| **Autora** (fotógrafa, jornalista) | Registra fotos no momento da captura | Registrar rápido, saber que deu certo, não lidar com detalhes técnicos |
| **Verificador** (leitor, editor de redação, perito) | Recebeu uma imagem e quer saber se ela é confiável | Resposta clara em segundos, sem login, com o que o resultado significa e o que não significa |
| **Editor terceiro** | Recebeu uma imagem registrada e precisa ajustá-la | Editar mantendo o vínculo com a original e o crédito da autora |

Premissa: a maioria não conhece blockchain. Os termos técnicos existem, mas ficam em segundo plano.

## 2. Jornadas principais

1. **Verificar uma imagem (sem conta)**: Início → Verificar imagem → escolher foto → analisando →
   Resultado → (opcional) Histórico da imagem.
2. **Primeiro registro**: Início → Registrar foto → explicação da carteira → conectar carteira →
   (vincular aparelho, 1ª vez) → câmera → captura → aprovar na carteira → acompanhamento do registro → confirmado.
3. **Registro recorrente**: Início → Registrar foto → câmera → captura → aprovar na carteira →
   pode sair; o acervo mostra o andamento.
4. **Editar e registrar versão**: Acervo (ou Importar) → foto confirmada → Editor → Salvar e
   registrar → acompanhamento.
5. **Investigar histórico**: Resultado ou Acervo → Histórico → tocar em uma versão → detalhes.

## 3. Mapa de navegação

```text
Início ─┬─ Verificar imagem ── Resultado ── Histórico da imagem ── Detalhe da versão
        ├─ Registrar foto ─ [Conectar carteira] ─ Câmera ─ Acompanhamento do registro
        ├─ Meu acervo ─┬─ Acompanhamento do registro
        │              ├─ Editor ─ Acompanhamento do registro
        │              └─ Histórico da imagem
        ├─ Importar para editar ─ Editor - Acompanhamento do registro
        └─ Carteira (chip no topo) ── Conta e rede / Desconectar
Primeiro uso: "Como funciona" (uma tela, pulável) antes do Início
```

Navegação inferior não é necessária: o Início é o hub com três destinos principais.

## 4. Inventário de telas e estados

Para cada tela: **objetivo**, **conteúdo**, **ação primária** e **estados obrigatórios**
(todos com `@Preview`).

### 4.1 Como funciona (primeiro uso)
- Objetivo: explicar em uma tela o que é registrar, o que é verificar, critérios para edição e filtros disponíveis.
- Conteúdo: três ideias curtas com ilustração simples; aviso de que é uma rede de testes; formatos
  aceitos (JPEG, PNG e WebP) e não aceitos (HEIC/HEIF), em uma linha (FR-040).
- Ação primária: "Começar". Secundária: "Pular".
- Estados: único.

### 4.2 Início
- Objetivo: escolher o que fazer.
- Conteúdo: "Verificar imagem" (destaque, disponível sem conta), "Registrar foto", "Meu acervo",
  "Importar para editar"; chip da carteira no topo.
- Estados: sem carteira; carteira conectada; carteira em rede errada (aviso no chip); registros
  pendentes (indicador no acervo).

### 4.3 Conectar carteira (sheet)
- Objetivo: conectar sem susto.
- Conteúdo: por que a carteira é necessária (assinar o registro em seu nome), que custa apenas
  tokens de teste, botão de conectar.
- Estados: inicial; aguardando a carteira; conectado; rede errada (botão "Trocar para Sepolia");
  saldo insuficiente (link para faucet); carteira não instalada; conexão recusada.

### 4.4 Câmera
- Objetivo: fotografar.
- Conteúdo: preview em tela cheia, disparador, troca de câmera, flash; indicação discreta "a foto
  será registrada".
- Estados: pronta; sem permissão (explicação + "Abrir configurações"); processando a foto;
  erro de câmera.

### 4.5 Acompanhamento do registro
- Objetivo: mostrar o andamento e dar segurança para sair da tela.
- Conteúdo: miniatura; etapas (Salva na galeria → Aprovação na carteira → Enviado → Confirmado);
  "Você pode sair desta tela; o registro continua."; ao confirmar, data/hora e "Ver detalhes técnicos".
- Estados: vinculando aparelho (1ª vez); aguardando aprovação; pendente; confirmado (momento de
  movimento marcante); rejeitado na carteira ("Tentar de novo"); falhou na rede ("Tentar de novo");
  já registrada (link para o registro existente).

### 4.6 Editor
- Objetivo: ajustar a foto e registrar a versão.
- Conteúdo: imagem grande; faixa de filtros na parte inferior; controle do parâmetro do filtro
  ativo; desfazer/refazer; comparação antes/depois (toque e segure); "Salvar e registrar";
  em imagem de terceiro, faixa "Registrada originalmente por 0x12ab…9f3c".
- Estados: carregando imagem; editando; sem alterações (botão salvar desabilitado); processando
  em resolução cheia; imagem não elegível (explicação); aviso de permanência antes de registrar.

### 4.7 Verificar imagem
- Objetivo: escolher a imagem a verificar.
- Conteúdo: explicação de uma linha; formatos aceitos (JPEG, PNG e WebP); "Escolher imagem"
  (Photo Picker).
- Estados: inicial; analisando (etapas: lendo a imagem → consultando registros); arquivo não
  suportado (nomeia os formatos aceitos e informa que HEIC não é aceito, FR-040).

### 4.8 Resultado da verificação
- Objetivo: responder "esta imagem está registrada?" em um relance.
- Hierarquia: (1) status com ícone e título; (2) frase explicando o que significa; (3) fatos:
  registrada por, quando, aparelho; em versões, alteração em relação à original (medidor) e quem
  editou; (4) "Ver histórico da imagem"; (5) "Detalhes técnicos" expansível; (6) rodapé obrigatório.
- Estados: Registrada – original; Registrada – versão alterada (com 0% tratado como "sem alteração
  visual detectável"); Correspondência visual (lista de candidatos); Registro adulterado;
  Não registrada; Sem conexão.

### 4.9 Histórico da imagem
- Objetivo: mostrar a genealogia.
- Conteúdo: linha do tempo vertical da original até a versão consultada (destacada); ramificações
  recolhidas com contagem ("+2 outras versões"); cada nó: miniatura se existir no aparelho,
  quem, quando, operações em linguagem leiga ("Preto e branco, brilho +30"), alteração vs. pai e vs. original.
- Estados: carregando; cadeia simples; com ramificações; limite excedido (aviso); nó com assinatura inválida.

### 4.10 Meu acervo
- Objetivo: ver e gerenciar as fotos registradas neste aparelho.
- Conteúdo: grade agrupada pela original; selo de status em cada foto; filtros (todas, pendentes, com falha).
- Estados: vazio ("Nenhuma foto registrada ainda" + "Registrar foto"); com itens; pendentes;
  falhas ("Tentar de novo"); arquivo removido da galeria.

### 4.11 Carteira
- Conteúdo: endereço abreviado (copiar), rede, saldo, link do faucet, "Desconectar".
- Estados: conectada; rede errada; saldo baixo.

## 5. Vocabulário visual de status

| Status | Palavra na UI | Ícone (Material Symbols, sugestão) | Uso |
|---|---|---|---|
| Original registrada | Registrada – original | `verified` | Resultado, acervo |
| Versão registrada | Registrada – versão alterada | `difference` | Resultado, histórico |
| Correspondência visual | Correspondência visual | `image_search` | Resultado |
| Adulterado | Registro adulterado | `gpp_bad` | Resultado, histórico |
| Não registrada | Não registrada | `help` | Resultado |
| Pendente | Registrando… | `schedule` | Acompanhamento, acervo |
| Falhou | Não registrada – tentar de novo | `error` | Acompanhamento, acervo |

As cores de cada status são definidas na direção visual e nunca aparecem sem ícone e palavra.

## 6. Glossário da interface

| Conceito técnico | Termo na interface |
|---|---|
| Registro on-chain | registro |
| Transação | registro na rede (detalhes técnicos: "transação") |
| `registrant` em original | registrada por |
| `registrant` em versão | editada por |
| `originalId` | original |
| `parentId` | versão anterior |
| Genealogia | histórico da imagem |
| Distância de Hamming / percentual | alteração visual |
| SHA-256 / pHash | (somente em detalhes técnicos) identificador do arquivo / identificador visual |
| Aparelho (deviceId) | aparelho |
| SepoliaETH | saldo de teste |

## 7. Checklist de acessibilidade (por tela)

- [ ] Funciona com TalkBack: ordem de leitura lógica, títulos marcados, ícones descritos
- [ ] Nada corta ou sobrepõe com fonte em 200%
- [ ] Contraste AA (4,5:1 texto, 3:1 componentes) no claro e no escuro
- [ ] Alvos de toque ≥ 48 dp
- [ ] Status nunca dependem só de cor
- [ ] Animações respeitam "remover animações"
- [ ] Sem violações críticas no Accessibility Scanner
