# /// script
# requires-python = ">=3.11,<3.14"
# dependencies = [
#   "cryptography>=44",
#   "opencv-python-headless~=4.14.0",
#   "numpy>=2",
#   "pillow>=11",
#   "imagehash>=4.3",
# ]
# ///
"""Registra na Sepolia capturas com assinatura do aparelho adulterada (T101, SC-003).

Para cada foto: calcula SHA-256 e pHash (implementação de referência de tools/golden), monta o
payload de specs/002-registro-rastreamento-imagens/contracts/signature-payload.md (v1), assina
com uma chave P-256 de teste, troca o `s` por `s + 1` e envia `registerCapture` com `cast send`.
O contrato aceita (ele não verifica a assinatura, research R5), e o app deve classificar a foto
como "Registro adulterado".

A chave do aparelho é derivada de um texto fixo (só para testes, como em tools/signature) e é
vinculada à carteira que envia, com `registerDevice`, na primeira execução.

Com `--controle FOTO`, registra também uma foto com a assinatura correta: o app deve mostrá-la
como "Registrada – original". Isso prova que o resultado "adulterado" das outras vem do `s`
alterado, e não de um payload montado errado.

Uso (a partir da raiz do repositório; a carteira precisa de saldo de teste):

    export SEPOLIA_RPC_URL=...              # não versionar
    export ADULTERADOR_PRIVATE_KEY=0x...    # carteira de teste (pode ser a de deploy)
    uv run tools/chain/registrar_adulterados.py --controle controle.jpg a.jpg b.jpg c.jpg

    uv run tools/chain/registrar_adulterados.py --simular a.jpg b.jpg c.jpg   # não envia nada

Saída: uma linha por foto em docs/resultados/dados/adulterados.csv (arquivo, tipo, SHA-256,
pHash, id do registro, transação). As fotos não podem estar registradas antes.
"""

from __future__ import annotations

import argparse
import csv
import hashlib
import importlib.util
import json
import os
import re
import subprocess
import sys
from pathlib import Path

from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import ec
from cryptography.hazmat.primitives.asymmetric.utils import decode_dss_signature

RAIZ = Path(__file__).resolve().parents[2]
SAIDA = RAIZ / "docs/resultados/dados/adulterados.csv"

# Ordem da curva P-256 (secp256r1)
N = 0xFFFFFFFF00000000FFFFFFFFFFFFFFFFBCE6FAADA7179E84F3B9CAC2FC632551
DOMAIN = b"HASHLENS-IMG-v1"
CHAIN_ID = 11_155_111
# ImageRegistry na Sepolia (quickstart §2); outro deploy: REGISTRY_ADDRESS
REGISTRY = os.environ.get("REGISTRY_ADDRESS", "0xF2f42B34414936e456c6AC10c4844f456CAD65Ba")
CARTEIRA_SIMULADA = "0x000000000000000000000000000000000000dEaD"


def carregar_golden():
    """Reaproveita sha256/phash de tools/golden/gerar_golden.py (hashing-spec v1)."""
    sys.dont_write_bytecode = True  # sem __pycache__ em tools/golden
    caminho = RAIZ / "tools/golden/gerar_golden.py"
    spec = importlib.util.spec_from_file_location("gerar_golden", caminho)
    modulo = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(modulo)
    return modulo


def chave_do_aparelho() -> ec.EllipticCurvePrivateKey:
    semente = hashlib.sha256(b"HashLens chave adulterada v1").digest()
    return ec.derive_private_key(int.from_bytes(semente, "big") % N, ec.SECP256R1())


def chave_publica(chave: ec.EllipticCurvePrivateKey) -> bytes:
    """65 bytes, 0x04 ‖ X ‖ Y (data-model: Device.publicKey)."""
    return chave.public_key().public_bytes(serialization.Encoding.X962, serialization.PublicFormat.UncompressedPoint)


def payload(registrante: str, device_id: int, sha256: str, phash: str) -> bytes:
    """signature-payload §1 para uma captura original: parentId 0 e operations vazio."""
    msg = (
        DOMAIN
        + CHAIN_ID.to_bytes(32, "big")
        + bytes.fromhex(REGISTRY.removeprefix("0x"))
        + bytes.fromhex(registrante.removeprefix("0x"))
        + device_id.to_bytes(4, "big")
        + bytes.fromhex(sha256)
        + int(phash, 16).to_bytes(8, "big")
        + (0).to_bytes(32, "big")
        + hashlib.sha256(b"").digest()
    )
    assert len(msg) == 195
    return msg


def assinar(chave: ec.EllipticCurvePrivateKey, msg: bytes) -> tuple[int, int]:
    """r e s com normalização low-S (signature-payload §2)."""
    r, s = decode_dss_signature(chave.sign(msg, ec.ECDSA(hashes.SHA256())))
    return r, min(s, N - s)


def cast(*args: str) -> str:
    resultado = subprocess.run(["cast", *args], capture_output=True, text=True)
    if resultado.returncode != 0:
        sys.exit(f"cast {args[0]} falhou:\n{resultado.stderr.strip()}")
    return resultado.stdout.strip()


def enviar(rpc: str, chave_carteira: str, assinatura: str, *argumentos: str) -> str:
    """cast send; devolve o hash da transação, ou encerra se ela reverter."""
    saida = cast(
        "send", REGISTRY, assinatura, *argumentos, "--rpc-url", rpc, "--private-key", chave_carteira, "--json"
    )
    recibo = json.loads(saida)
    if recibo.get("status") not in ("0x1", 1, "1"):
        sys.exit(f"transação revertida: {recibo.get('transactionHash')}")
    return recibo["transactionHash"]


def id_por_sha256(rpc: str, sha256: str) -> int:
    saida = cast("call", REGISTRY, "getIdBySha256(bytes32)(uint256)", "0x" + sha256, "--rpc-url", rpc)
    return int(saida.split()[0])


def aparelho_vinculado(rpc: str, carteira: str, publica: bytes) -> int | None:
    """deviceId da carteira com esta chave pública, se já vinculado."""
    ids = cast("call", REGISTRY, "getDevicesOf(address)(uint32[])", carteira, "--rpc-url", rpc)
    for device_id in re.findall(r"\d+", ids):
        aparelho = cast(
            "call", REGISTRY, "getDevice(uint32)((uint32,address,bytes,uint64))", device_id, "--rpc-url", rpc
        )
        if publica.hex() in aparelho.lower():
            return int(device_id)
    return None


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("fotos", nargs="+", type=Path, help="fotos a registrar com assinatura adulterada")
    parser.add_argument("--controle", type=Path, help="uma foto a registrar com a assinatura correta")
    parser.add_argument("--simular", action="store_true", help="calcula e mostra tudo, sem enviar transações")
    args = parser.parse_args()

    golden = carregar_golden()
    chave = chave_do_aparelho()
    publica = chave_publica(chave)

    if args.simular:
        rpc, chave_carteira, carteira = None, None, CARTEIRA_SIMULADA
        device_id = 1
    else:
        rpc = os.environ.get("SEPOLIA_RPC_URL") or sys.exit("defina SEPOLIA_RPC_URL")
        chave_carteira = os.environ.get("ADULTERADOR_PRIVATE_KEY") or sys.exit("defina ADULTERADOR_PRIVATE_KEY")
        carteira = cast("wallet", "address", "--private-key", chave_carteira)
        device_id = aparelho_vinculado(rpc, carteira, publica)
        if device_id is None:
            tx = enviar(rpc, chave_carteira, "registerDevice(bytes)", "0x" + publica.hex())
            device_id = aparelho_vinculado(rpc, carteira, publica)
            print(f"aparelho de teste vinculado: deviceId {device_id} (tx {tx})")
    print(f"carteira {carteira}, aparelho {device_id}, contrato {REGISTRY}")

    fotos = [(foto, "adulterado") for foto in args.fotos]
    if args.controle:
        fotos.insert(0, (args.controle, "controle"))

    linhas = []
    for foto, tipo in fotos:
        dados = foto.read_bytes()
        sha256, phash = golden.sha256(dados), golden.phash(dados)
        r, s = assinar(chave, payload(carteira, device_id, sha256, phash))
        if tipo == "adulterado":
            s = s + 1  # a assinatura deixa de conferir com o payload
        argumentos = [
            "0x" + sha256,
            str(int(phash, 16)),
            str(device_id),
            "0x" + r.to_bytes(32, "big").hex(),
            "0x" + s.to_bytes(32, "big").hex(),
        ]
        if args.simular:
            print(f"{foto.name} ({tipo}): registerCapture({', '.join(argumentos)})")
            continue
        if (existente := id_por_sha256(rpc, sha256)) != 0:
            sys.exit(f"{foto.name} já está registrada (id {existente}); use uma foto nova")
        tx = enviar(rpc, chave_carteira, "registerCapture(bytes32,uint64,uint32,bytes32,bytes32)", *argumentos)
        registro = id_por_sha256(rpc, sha256)
        print(f"{foto.name} ({tipo}): registro {registro}, tx {tx}")
        linhas.append([foto.name, tipo, sha256, phash, registro, tx])

    if linhas:
        novo = not SAIDA.exists()
        with SAIDA.open("a", newline="") as arquivo:
            escritor = csv.writer(arquivo)
            if novo:
                escritor.writerow(["arquivo", "tipo", "sha256", "phash", "registro", "transacao"])
            escritor.writerows(linhas)
        print(f"gravado em {SAIDA.relative_to(RAIZ)}")


if __name__ == "__main__":
    main()
