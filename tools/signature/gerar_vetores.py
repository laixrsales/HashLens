# /// script
# requires-python = ">=3.11"
# dependencies = ["cryptography>=44"]
# ///
"""Gera os vetores de teste da assinatura do aparelho (T016).

Implementação de referência, independente do app, de
specs/002-registro-rastreamento-imagens/contracts/signature-payload.md (v1): monta o payload de
195 bytes e assina com ECDSA P-256 / SHA-256 determinístico (RFC 6979), para que o arquivo gerado
seja sempre o mesmo.

Uso (a partir da raiz do repositório):

    uv run tools/signature/gerar_vetores.py

Saída: android/app/src/test/resources/signature/vectors.json. A chave privada é derivada de um
texto fixo e serve só para testes.
"""

from __future__ import annotations

import hashlib
import json
from pathlib import Path

from cryptography.exceptions import InvalidSignature
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import ec
from cryptography.hazmat.primitives.asymmetric.utils import (
    decode_dss_signature,
    encode_dss_signature,
)

RAIZ = Path(__file__).resolve().parents[2]
SAIDA = RAIZ / "android/app/src/test/resources/signature/vectors.json"

# Ordem da curva P-256 (secp256r1)
N = 0xFFFFFFFF00000000FFFFFFFFFFFFFFFFBCE6FAADA7179E84F3B9CAC2FC632551

DOMAIN = b"HASHLENS-IMG-v1"
CHAIN_ID = 11_155_111
CONTRACT = "0xF2f42B34414936e456c6AC10c4844f456CAD65Ba"  # ImageRegistry na Sepolia (T010)
REGISTRANT = "0x1234567890AbcdEF1234567890aBcdef12345678"  # carteira fictícia

# deviceId e parentId são os ids que o contrato atribui quando os dois casos são registrados em
# ordem num ImageRegistry novo (tools/chain/capturar_respostas.sh), para que as respostas
# capturadas tenham assinaturas válidas.
CASOS = [
    {
        "nome": "original",
        "deviceId": 1,
        "sha256": "55824746f88c258e4ef10a10027ba7751e85b67c866f63023bdf90c2207aa527",
        "pHash": "fe44819b7ec4c03b",
        "parentId": 0,
        "operations": "",
    },
    {
        "nome": "edicao",
        "deviceId": 1,
        "sha256": "9490e4ce9e39788e6b817908e68b8da1983a66b14a87b73d1435830fa6945bdf",
        "pHash": "fe44819b7ec4c03b",
        "parentId": 1,
        "operations": "brightness:+15;grayscale",
    },
]


def endereco(hex_: str) -> bytes:
    dados = bytes.fromhex(hex_.removeprefix("0x"))
    assert len(dados) == 20
    return dados


def payload(caso: dict) -> bytes:
    """signature-payload §1: concatenação big-endian, 195 bytes."""
    msg = (
        DOMAIN
        + CHAIN_ID.to_bytes(32, "big")
        + endereco(CONTRACT)
        + endereco(REGISTRANT)
        + caso["deviceId"].to_bytes(4, "big")
        + bytes.fromhex(caso["sha256"])
        + int(caso["pHash"], 16).to_bytes(8, "big")
        + caso["parentId"].to_bytes(32, "big")
        + hashlib.sha256(caso["operations"].encode("utf-8")).digest()
    )
    assert len(msg) == 195
    return msg


def raw(r: int, s: int) -> str:
    return (r.to_bytes(32, "big") + s.to_bytes(32, "big")).hex()


def main() -> None:
    semente = hashlib.sha256(b"HashLens chave de teste v1").digest()
    chave = ec.derive_private_key(int.from_bytes(semente, "big") % N, ec.SECP256R1())
    publica = chave.public_key()
    algoritmo = ec.ECDSA(hashes.SHA256(), deterministic_signing=True)

    casos = []
    for caso in CASOS:
        msg = payload(caso)
        r, s = decode_dss_signature(chave.sign(msg, algoritmo))
        s_baixo, s_alto = min(s, N - s), max(s, N - s)
        der_baixo = encode_dss_signature(r, s_baixo)
        der_alto = encode_dss_signature(r, s_alto)
        publica.verify(der_baixo, msg, algoritmo)
        publica.verify(der_alto, msg, algoritmo)  # ECDSA aceita s e n − s

        campos = {k: v for k, v in caso.items() if k != "nome"}
        casos.append(
            {
                "nome": caso["nome"],
                "campos": {"chainId": CHAIN_ID, "contract": CONTRACT, "registrant": REGISTRANT, **campos},
                "operationsHash": hashlib.sha256(caso["operations"].encode("utf-8")).hexdigest(),
                "payload": msg.hex(),
                "assinaturaDer": der_baixo.hex(),
                "assinaturaRaw": raw(r, s_baixo),
                "assinaturaDerSAlto": der_alto.hex(),
            }
        )

    # Mesma assinatura do caso "original" com o pHash trocado (último bit invertido): inválida
    adulterado = dict(CASOS[0], pHash=f"{int(CASOS[0]['pHash'], 16) ^ 1:016x}")
    msg_adulterada = payload(adulterado)
    try:
        publica.verify(bytes.fromhex(casos[0]["assinaturaDer"]), msg_adulterada, algoritmo)
        raise AssertionError("assinatura deveria ser inválida")
    except InvalidSignature:
        pass

    ponto = publica.public_bytes(serialization.Encoding.X962, serialization.PublicFormat.UncompressedPoint)
    vetores = {
        "signaturePayload": "v1",
        "geradoPor": "tools/signature/gerar_vetores.py",
        "observacao": (
            "Chave só de teste. assinaturaRaw é r‖s com low-S; assinaturaDerSAlto é a mesma "
            "assinatura com s = n − s, que deve ser normalizada para assinaturaRaw."
        ),
        "publicKey": ponto.hex(),
        "casos": casos,
        "pHashAlterado": {
            "pHash": adulterado["pHash"],
            "payload": msg_adulterada.hex(),
            "assinaturaRaw": casos[0]["assinaturaRaw"],
        },
    }
    SAIDA.parent.mkdir(parents=True, exist_ok=True)
    texto = json.dumps(vetores, ensure_ascii=False, indent=2) + "\n"
    SAIDA.write_text(texto, encoding="utf-8")
    print(texto)


if __name__ == "__main__":
    main()
