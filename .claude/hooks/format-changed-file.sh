#!/usr/bin/env bash
# Hook PostToolUse do Claude Code: formata o arquivo que o Claude acabou de criar ou editar.
#   .kt / .kts            -> ktlint -F   (usa o .editorconfig da raiz)
#   .sol                  -> forge fmt   (usa contracts/foundry.toml)
#   .md / .json / .yml    -> prettier --write
# Ferramenta ausente = aviso e segue (exit 0).
# Erro de estilo que o ktlint não corrige sozinho = exit 2 (a mensagem volta para o Claude corrigir).
set -u

INPUT="$(cat)"

PY="$(command -v python3 || command -v python || true)"
if [ -z "$PY" ]; then
  echo "[format] Python não encontrado; formatação ignorada." >&2
  exit 0
fi

FILE="$(printf '%s' "$INPUT" | "$PY" -c 'import json,sys
try:
    d = json.load(sys.stdin)
    print((d.get("tool_input") or {}).get("file_path", ""))
except Exception:
    print("")')"

[ -n "$FILE" ] || exit 0
[ -f "$FILE" ] || exit 0

# Normaliza separadores do Windows para os testes de caminho abaixo
FILE_N="${FILE//\\//}"

# Pastas que não devem ser formatadas
case "$FILE_N" in
  */.specify/*|*/.claude/*|*/.git/*|*/build/*|*/.gradle/*|*/contracts/lib/*|*/contracts/out/*|*/contracts/cache/*|*/node_modules/*)
    exit 0 ;;
esac

PROJECT_DIR="${CLAUDE_PROJECT_DIR:-$(pwd)}"

case "$FILE_N" in
  *.kt|*.kts)
    if command -v ktlint >/dev/null 2>&1; then
      if ! OUT="$(ktlint -F "$FILE" 2>&1)"; then
        echo "[format] ktlint corrigiu o que pôde, mas restaram problemas em $FILE:" >&2
        echo "$OUT" >&2
        exit 2
      fi
    else
      echo "[format] ktlint não instalado; $FILE não foi formatado." >&2
    fi
    ;;
  *.sol)
    if command -v forge >/dev/null 2>&1; then
      if [ -d "$PROJECT_DIR/contracts" ]; then
        (cd "$PROJECT_DIR/contracts" && forge fmt "$FILE") >/dev/null 2>&1 || true
      else
        forge fmt "$FILE" >/dev/null 2>&1 || true
      fi
    else
      echo "[format] forge não instalado; $FILE não foi formatado." >&2
    fi
    ;;
  *.md|*.json|*.yml|*.yaml)
    if command -v prettier >/dev/null 2>&1; then
      prettier --write --log-level warn "$FILE" >/dev/null 2>&1 || true
    else
      echo "[format] prettier não instalado; $FILE não foi formatado." >&2
    fi
    ;;
esac

exit 0
