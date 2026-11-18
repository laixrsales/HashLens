#!/usr/bin/env python3
"""Remonta os JSONs de cadeia de atestação escritos no logcat por CaptureReferenceChainTest (020/T016).

Uso (na raiz do repositório):
    adb logcat -c
    (cd android && ./gradlew :app:connectedDebugAndroidTest \
        -Pandroid.testInstrumentationRunnerArguments.class=br.ufv.hashlens.attestation.CaptureReferenceChainTest)
    adb logcat -d -s AttestationT016:I | python3 tools/attestation/remontar_cadeia.py

Grava cada arquivo em android/attestation/src/test/resources/attestation/real/.
"""
import json
import re
import sys
from pathlib import Path

DESTINO = Path(__file__).resolve().parents[2] / "android/attestation/src/test/resources/attestation/real"
LINHA = re.compile(r"AttestationT016: (\S+\.json) (\d+)/(\d+) (.*)$")


def main() -> None:
    partes: dict[str, dict[int, str]] = {}
    totais: dict[str, int] = {}
    for linha in sys.stdin:
        m = LINHA.search(linha.rstrip("\n"))
        if not m:
            continue
        nome, n, total, texto = m.group(1), int(m.group(2)), int(m.group(3)), m.group(4)
        partes.setdefault(nome, {})[n] = texto
        totais[nome] = total
    if not partes:
        sys.exit("nenhuma cadeia encontrada no logcat (rodou adb logcat -c antes do teste?)")
    DESTINO.mkdir(parents=True, exist_ok=True)
    for nome, pedacos in partes.items():
        faltando = set(range(1, totais[nome] + 1)) - pedacos.keys()
        if faltando:
            sys.exit(f"{nome}: faltam os pedaços {sorted(faltando)}")
        dados = json.loads("".join(pedacos[i] for i in sorted(pedacos)))
        (DESTINO / nome).write_text(json.dumps(dados, indent=2, ensure_ascii=False) + "\n")
        print(f"{DESTINO / nome}: {len(dados['chain'])} certificados")


if __name__ == "__main__":
    main()
