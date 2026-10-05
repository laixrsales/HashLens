# HashLens

App Android (Kotlin + Jetpack Compose + OpenCV) que registra imagens na Ethereum Sepolia
(contrato Solidity/Foundry) para autenticação e rastreamento de alterações. Projeto de TCC (UFV).

- Princípios do projeto: `.specify/memory/constitution.md` (prevalece sobre o resto).
- Feature ativa: definida em `.specify/feature.json`.
- Documentos de referência originais (usados para gerar os artefatos do spec-kit): `docs/referencia/`.

## Branches

Formato obrigatório: `<tipo>/<NNN>-<slug>`

- `<tipo>`: `feature` (funcionalidade nova), `refactor` (reestruturação sem mudar comportamento),
  `bugfix` (correção), `style` (formatação/visual, sem mudar lógica), `chore` (alterações de rotina).
- `<NNN>`: 3 dígitos, sequência única para todo o repositório: o próximo número após o maior
  usado em qualquer branch (local ou remota) ou pasta de `specs/`.
- `<slug>`: 2 a 4 palavras em kebab-case, minúsculas, sem acentos.

Regras para o agente:

- Em `/speckit-specify` para `feature`, a extensão git já aplica o prefixo `feature/`.
- Para `refactor`, `bugfix` ou `style` passando pelo spec-kit, defina `GIT_BRANCH_NAME` com o
  nome completo (ex.: `GIT_BRANCH_NAME=bugfix/004-falha-releitura-galeria`).
- Para mudanças pequenas sem spec, crie a branch com `git switch -c <nome>` seguindo o formato.
- O hook `.githooks/pre-commit` bloqueia commits em branches com nome inválido (exceto `main`).

## Commits: somente a autora

Você (agente) NUNCA executa `git commit`, `git push`, `git merge`, `git rebase`, `git cherry-pick`,
`git revert` ou `git tag`, nem habilita auto-commit da extensão git do spec-kit. Criar e trocar de
branch (`git switch -c`, `git checkout -b`) é permitido. Ao terminar uma tarefa ou fase:

1. pare e resuma o que mudou (`git status` / `git diff --stat`);
2. sugira uma mensagem de commit no formato `<tipo>: <descrição>`;
3. aguarde a autora revisar e commitar por conta própria.

Esses comandos também estão bloqueados em `.claude/settings.json` (regras `deny` e hook
`.claude/hooks/block-git-write.sh`).

## Formatação

Um hook PostToolUse (`.claude/hooks/format-changed-file.sh`) formata cada arquivo editado:
ktlint (Kotlin), `forge fmt` (Solidity), Prettier (Markdown/JSON/YAML). Se o hook retornar
problemas do ktlint que ele não corrige sozinho, corrija-os antes de seguir.

## Interface (UI/UX)

Todo trabalho de interface segue a skill `.claude/skills/hashlens-ui/` e `docs/referencia/ux.md`.
Ordem obrigatória: direção visual aprovada pela autora → tokens do tema → componentes → tela sem
estado com previews de todos os estados + screenshots (Roborazzi) → revisão da autora → ligação ao
ViewModel. Ao terminar previews, pare e mostre os screenshots gerados para aprovação.

## Comandos

- Contrato: `cd contracts && forge build && forge test -vv`
- App (testes unitários): `cd android && ./gradlew :app:testDebugUnitTest`
- App (instrumentados, com aparelho): `cd android && ./gradlew :app:connectedDebugAndroidTest`
- Screenshots das telas: `cd android && ./gradlew :app:recordRoborazziDebug` (PNGs em `app/build/outputs/roborazzi/`)

Segredos (`local.properties`, chaves de deploy) nunca entram no Git.
