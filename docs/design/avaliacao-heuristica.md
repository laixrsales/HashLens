# Avaliação heurística e percurso cognitivo (T098)

Avaliação de usabilidade **sem participantes** do HashLens, que cobre os requisitos de experiência
FR-032 a FR-038. Ela substitui o teste de usabilidade com 5 pessoas leigas em blockchain previsto
originalmente. Esse teste envolveria pesquisa com seres humanos e exigiria aprovação do comitê de
ética. Fica como limitação e trabalho futuro do TCC.

- **Data:** 13/10/2026.
- **Material avaliado:** os screenshots de todos os estados das 11 telas (Roborazzi, tema claro,
  escuro e fonte em 200%, em `android/app/build/outputs/roborazzi/` após
  `./gradlew :app:recordRoborazziDebug`), os textos de `strings.xml` e o app no aparelho.
- **Avaliação:** inspeção inicial feita com apoio do assistente de código (Claude), revisada pela
  autora.

## Métodos

1. **Avaliação heurística** com as 10 heurísticas de Nielsen (H1 a H10, abaixo). Cada problema
   recebe uma gravidade de 0 a 4, na escala de Nielsen:
   - 0: não é problema;
   - 1: cosmético;
   - 2: pequeno;
   - 3: grande;
   - 4: catástrofe.
2. **Percurso cognitivo** (Wharton et al.) nas duas jornadas que o teste com pessoas cobriria:
   verificar 3 imagens e entender o resultado, e fazer o primeiro registro. Em cada passo, a pessoa
   leiga é imaginada respondendo a quatro perguntas:
   - **Q1:** ela tenta fazer a coisa certa?
   - **Q2:** ela percebe que a ação está disponível?
   - **Q3:** ela liga a ação ao efeito que quer?
   - **Q4:** ela vê que avançou?

Heurísticas:

| Código | Heurística                                         |
| ------ | -------------------------------------------------- |
| H1     | Visibilidade do estado do sistema                  |
| H2     | Correspondência com o mundo real                   |
| H3     | Controle e liberdade                               |
| H4     | Consistência e padrões                             |
| H5     | Prevenção de erros                                 |
| H6     | Reconhecer em vez de lembrar                       |
| H7     | Flexibilidade e eficiência                         |
| H8     | Estética e design minimalista                      |
| H9     | Ajudar a reconhecer, diagnosticar e corrigir erros |
| H10    | Ajuda e documentação                               |

## Percurso cognitivo

### Jornada A: verificar 3 imagens e entender o resultado

Imagens: uma original registrada, uma versão editada registrada e uma cópia recomprimida de uma foto
registrada.

| Passo                                     | Q1  | Q2  | Q3  | Q4  | Observação                                                                                                                                                                                                       |
| ----------------------------------------- | --- | --- | --- | --- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Início → "Verificar imagem"               | Sim | Sim | Sim | Sim | A pergunta "Esta imagem está registrada?" é o título da tela e o botão principal responde a ela. Avisa que não precisa de conta.                                                                                 |
| "Escolher imagem" → seletor de fotos      | Sim | Sim | Sim | Sim | Formatos aceitos ditos antes de escolher (FR-040).                                                                                                                                                               |
| Analisando                                | —   | —   | —   | Sim | Etapas "Lendo a imagem" → "Consultando registros", com a miniatura (H1).                                                                                                                                         |
| Resultado 1: Registrada – original        | —   | —   | —   | Sim | Título, ícone e frase ("idêntico a uma captura registrada"), quem e quando. O rodapé diz o que o registro não prova.                                                                                             |
| Resultado 2: Registrada – versão alterada | —   | —   | —   | Sim | Régua e "≈ 12,5% de alteração visual", quem editou e de qual original. O que o percentual mede é explicado em "Detalhes técnicos" (P6).                                                                          |
| Resultado 3: Correspondência visual       | —   | —   | —   | Sim | Explica que o arquivo não é idêntico, mas parece igual, com o exemplo de cópia por app de mensagens (era "cópia recomprimida", corrigido em P1). A lista de registros parecidos e o histórico ajudam a entender. |
| "Verificar outra imagem"                  | Sim | Sim | Sim | Sim | Ação secundária presente em todos os resultados.                                                                                                                                                                 |

Conclusão: a jornada se completa sem ajuda. O risco estava em **interpretar** dois resultados
(Correspondência visual e Registro adulterado), não em navegar. Os textos deles foram revistos (P1, P2).

### Jornada B: primeiro registro

Pré-condição (premissa da spec): a pessoa já tem uma carteira com saldo de teste.

| Passo                                                | Q1  | Q2  | Q3      | Q4  | Observação                                                                                                                                  |
| ---------------------------------------------------- | --- | --- | ------- | --- | ------------------------------------------------------------------------------------------------------------------------------------------- |
| Início → "Registrar foto"                            | Sim | Sim | Sim     | Sim | Em "Suas fotos", com a frase "Fotografe e registre na hora".                                                                                |
| Folha "Conectar carteira"                            | Sim | Sim | Sim     | Sim | Explica em uma frase o que a carteira faz, que o HashLens não vê a senha e que custa só saldo de teste (FR-038).                            |
| Aprovar a conexão na carteira e voltar               | Sim | Sim | Parcial | Sim | Trocar de app é estranho para leigos. A folha diz "aprove … e volte para o HashLens" e tem "Cancelar".                                      |
| "Carteira conectada" → "Continuar"                   | Sim | Sim | Sim     | Sim | Mostra o endereço e o saldo de teste.                                                                                                       |
| Permitir câmera                                      | Sim | Sim | Sim     | Sim | Diz para que serve a câmera e quando ela fica ligada.                                                                                       |
| Câmera → disparo                                     | Sim | Sim | Sim     | Sim | O selo "A foto será registrada" confirma o modo.                                                                                            |
| Vínculo do aparelho (1ª vez) e aprovação na carteira | Sim | Sim | Parcial | Sim | Avisa antes que serão duas aprovações e que o vínculo só acontece uma vez. "Vincular este aparelho" é um conceito novo, mas está explicado. |
| Enviado → Confirmado                                 | —   | —   | —       | Sim | Etapas com estado, "você pode sair desta tela" (FR-035) e "Foto registrada" com data no fim (mesmo nome do botão).                          |

Conclusão: a jornada se completa sem ajuda **desde que a carteira já exista com saldo**. Conseguir a
carteira e o saldo de teste fica fora do app e é a maior barreira para leigos (P10).

## Problemas encontrados

| ID  | Heurística | Tela                                              | Problema                                                                                                                                      | Grav. | Situação                                                                                                                                                                                                                                               |
| --- | ---------- | ------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------- | ----- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| P1  | H2         | Resultado: Correspondência visual                 | "Pode ser uma cópia recomprimida": "recomprimida" é jargão.                                                                                   | 2     | **Corrigido** (texto e contrato `contracts/verification-result.md` §3): "Pode ser uma cópia enviada por um app de mensagens ou salva com outra qualidade."                                                                                             |
| P2  | H2, H9     | Resultado: Registro adulterado                    | "A assinatura do aparelho não confere com os dados registrados" não diz o que isso significa para quem verifica nem o que fazer.              | 3     | **Corrigido** (texto e contrato): "Os dados deste registro não conferem com a assinatura do aparelho que o fez. Não confie nas informações dele." A mesma frase vale no Histórico, e o Editor usa a mesma formulação.                                  |
| P3  | H2         | Folha da carteira e Registro: saldo insuficiente  | "Peça em um faucet": jargão sem explicação.                                                                                                   | 2     | **Corrigido**: "toque em "Obter saldo de teste", peça no site que abrir e volte".                                                                                                                                                                      |
| P4  | H4         | Resultado: Correspondência visual                 | Candidato que é versão alterada aparecia como "Registrada por", enquanto o resultado e o histórico dizem "Editada por" (glossário, ux.md §6). | 2     | **Corrigido**: "Editada por" nas versões.                                                                                                                                                                                                              |
| P5  | H2         | Resultado e Histórico                             | "Aparelho nº 3" não diz por que o número importa.                                                                                             | 1     | Mantido: o ux.md §4.8 pede o aparelho entre os fatos. Melhoria possível: "Aparelho de registro nº 3", ou uma frase em "Como funciona" dizendo que cada aparelho assina as fotos que tira.                                                              |
| P6  | H10        | Resultado: versão alterada, Histórico             | "≈ 12,5% de alteração visual" não diz o que mede (aparência geral, não pixels alterados).                                                     | 2     | **Corrigido**: nos resultados de versão alterada, "Detalhes técnicos" abre com "A alteração visual compara o identificador visual (pHash) deste arquivo com o da original: mede o quanto a aparência geral mudou, não quantos pixels foram alterados." |
| P7  | H6         | Acompanhamento do registro: enviado               | "Você pode sair desta tela; o registro continua" não diz onde acompanhar depois.                                                              | 1     | **Corrigido**: "… o registro continua e o andamento fica em Meu acervo."                                                                                                                                                                               |
| P8  | H8         | Acompanhamento do registro: aprovação na carteira | "Custa só saldo de teste. Custo estimado: … SepoliaETH (saldo de teste)": "saldo de teste" repetido.                                          | 1     | **Corrigido**: "Custo estimado: … SepoliaETH."                                                                                                                                                                                                         |
| P9  | H2         | Folha da carteira: nenhuma carteira               | "Compatível com WalletConnect": termo técnico.                                                                                                | 1     | Mantido: identifica quais carteiras funcionam, e o exemplo "como a MetaMask" vem junto.                                                                                                                                                                |
| P10 | H10        | Fora do app                                       | Para registrar, a pessoa precisa criar uma carteira e conseguir saldo de teste, e o app não orienta como.                                     | 3     | Fora do escopo (premissa da spec). Limitação do TCC; trabalho futuro: um guia "Como conseguir uma carteira de teste".                                                                                                                                  |
| P11 | H2         | Resultado, Histórico, Acervo                      | Quem registrou aparece como endereço de carteira ("0x12ab…9f3c"), que não identifica uma pessoa para um leigo.                                | 2     | Limitação do modelo (não há nomes na rede). Trabalho futuro: associar endereços a nomes verificados.                                                                                                                                                   |

## Pontos fortes observados

- **Status nunca só por cor (H1, H4, FR-034).** O mesmo ícone, cor e palavra valem em resultado,
  histórico e acervo.
- **Honestidade nos resultados.** "Não registrada" diz que "isso não significa que a imagem seja
  falsa", e o rodapé dos resultados positivos diz o que o registro não prova.
- **Termos técnicos em segundo plano (FR-033).** Hashes, transação e endereço completo ficam em
  "Detalhes técnicos".
- **Esperas transparentes (H1, FR-035).** O acompanhamento mostra 4 etapas, diz que dá para sair, e o
  Início mostra "2 registrando".
- **Erros com causa e próximo passo (H9, FR-032).** As falhas de registro dizem o que aconteceu, que
  "nada foi registrado em seu nome" e oferecem "Tentar de novo".
- **Carteira sem susto (FR-038).** O app explica antes de abrir a carteira, avisa sobre as duas
  aprovações no primeiro uso e mostra o custo estimado.
- **Primeiro uso (FR-037).** "Como funciona" cabe numa tela, pode ser pulada e reaberta pelo Início.

## Limitações

- **Avaliadores:** Nielsen recomenda 3 a 5 avaliadores independentes. Esta avaliação teve uma
  inspeção revisada por uma pessoa que conhece o app, o que tende a esconder problemas que um leigo
  encontraria.
- **Sem participantes:** inspeção não mede compreensão real. Por exemplo, se leigos interpretam
  "Registrada – original" como "foto verdadeira", apesar do rodapé. Isso é o que o teste com usuários
  mediria, e fica como trabalho futuro, com aprovação do comitê de ética.
- **Fora do app:** os passos na carteira (MetaMask) não foram avaliados, só a forma como o HashLens os
  anuncia.
