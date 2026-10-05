#!/usr/bin/env bash
# Hook PreToolUse do Claude Code: impede o agente de gravar no histórico do Git.
# Commits, push, merge etc. são feitos somente pela autora.
set -u
INPUT="$(cat)"
PY="$(command -v python3 || command -v python || true)"
[ -n "$PY" ] || exit 0

CMD="$(printf '%s' "$INPUT" | "$PY" -c 'import json,sys
try:
    d = json.load(sys.stdin)
    print((d.get("tool_input") or {}).get("command", ""))
except Exception:
    print("")')"

if printf '%s' "$CMD" | grep -Eq '(^|[^[:alnum:]_-])git([[:space:]]+-[^[:space:]]+([[:space:]]+[^-[:space:]][^[:space:]]*)?)*[[:space:]]+(commit|push|merge|rebase|cherry-pick|revert|tag)([[:space:]]|$)'; then
  echo "Bloqueado: commits e demais gravações no histórico do Git são feitos somente pela autora." >&2
  echo "Pare aqui, resuma as mudanças (git status / git diff --stat) e sugira uma mensagem de commit." >&2
  exit 2
fi
exit 0
