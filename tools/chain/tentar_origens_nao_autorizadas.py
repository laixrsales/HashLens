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
"""Tenta vincular e registrar por fora do HashLens genuíno e grava as recusas (020 R15, SC-001).

Cada tentativa vai para a Sepolia com `cast send --gas-limit` (sem estimativa), para que a
transação seja minerada e revertida: o recibo com `status 0` é a evidência pública da recusa.
O erro obtido vem de repetir a mesma chamada com `cast call` no estado do bloco anterior.
Basta uma carteira, a dona do aparelho: a tentativa 2 (outra carteira) é só simulada com
`cast call --from` um endereço qualquer, sem chave e sem gasto, e sai com `status simulada`.

Tentativas, a partir de um aparelho já vinculado pelo app (`--device`) e de um registro dele
(`--registro`):

1. `vinculo_sem_autorizacao`: chave P-256 nova, cadeia real, autorização vazia → `InvalidAuthorization`.
2. `vinculo_autorizacao_outra_carteira`: a transação real do vínculo vinda de outra carteira
   → `InvalidAuthorization` (a autorização vale só para quem a pediu). Simulada, sem envio.
3. `vinculo_autorizacao_reutilizada`: a mesma transação real, de novo pela carteira dona
   → `DeviceAlreadyRegistered`.
4. `registro_assinatura_outra_chave`: payload certo assinado por outra chave P-256
   → `InvalidDeviceSignature`.
5. `registro_dados_alterados`: a assinatura real de `--registro` com outro SHA-256 e pHash
   → `InvalidDeviceSignature`.
6. `vinculo_autorizacao_vencida`: a transação real do vínculo depois do prazo → `AuthorizationExpired`.
   Se o prazo (15 min após o vínculo) ainda não venceu, o script espera.

As tentativas 2 e 3 precisam da autorização ainda no prazo: rode o script até ~10 min depois do
vínculo feito pelo app. Com `--foto`, as tentativas 4 e 5 usam o SHA-256 e o pHash da foto
(tools/golden); sem ela, valores aleatórios.

Uso (a partir da raiz do repositório):

    export SEPOLIA_RPC_URL=...                 # não versionar
    export REGISTRY_ADDRESS=0x...              # ImageRegistry do deploy (quickstart §2)
    read -s CARTEIRA_PRIVATE_KEY && export CARTEIRA_PRIVATE_KEY              # dona do --device
    uv run tools/chain/tentar_origens_nao_autorizadas.py --device 3 --registro 12 --foto nova.jpg

    uv run tools/chain/tentar_origens_nao_autorizadas.py --simular --device 3 --registro 12

O RPC só lê estado e logs de blocos recentes (~128 blocos, cerca de 25 min): rode o script logo
depois do vínculo.

`--simular` só lê a rede: mostra cada tentativa e o erro que o contrato devolve hoje (`cast
call`), sem chaves e sem enviar nada. Saída: docs/resultados/dados/recusas.csv (arquivo,
tentativa, erro esperado, erro obtido, transação, bloco, status).
"""

from __future__ import annotations

import argparse
import csv
import hashlib
import importlib.util
import json
import os
import re
import secrets
import subprocess
import sys
import time
from dataclasses import dataclass
from pathlib import Path

from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import ec
from cryptography.hazmat.primitives.asymmetric.utils import decode_dss_signature

RAIZ = Path(__file__).resolve().parents[2]
SAIDA = RAIZ / "docs/resultados/dados/recusas.csv"
COLUNAS = ["arquivo", "tentativa", "erro_esperado", "erro_obtido", "transacao", "bloco", "status"]

# Ordem da curva P-256 (secp256r1)
N = 0xFFFFFFFF00000000FFFFFFFFFFFFFFFFBCE6FAADA7179E84F3B9CAC2FC632551
DOMAIN = b"HASHLENS-IMG-v1"
CHAIN_ID = 11_155_111
# Gás fixo das tentativas: sobra para registerDevice com a cadeia (~4 KB) e para o P256VERIFY
GAS_LIMIT = "1500000"
# Margem mínima de prazo para as tentativas que precisam da autorização ainda válida
MARGEM_PRAZO_S = 120
CARTEIRA_SIMULADA_OUTRA = "0x000000000000000000000000000000000000dEaD"

SIG_REGISTER_DEVICE = "registerDevice(bytes,bytes[],uint64,bytes)"
SIG_REGISTER_CAPTURE = "registerCapture(bytes32,uint64,uint32,bytes32,bytes32)"
TIPO_DEVICE = "(uint32,address,bytes,uint64,bytes32,uint64)"
# Erros do IImageRegistry, para traduzir o seletor quando o cast não decodifica
ERROS = [
    "InvalidPublicKey()", "DeviceAlreadyRegistered(uint32)", "UnknownDevice(uint32)",
    "NotDeviceOwner(uint32,address)", "InvalidHash()", "AlreadyRegistered(uint256)",
    "ParentNotFound(uint256)", "InvalidOperations()", "EmptyAttestation()",
    "AuthorizationExpired(uint64)", "InvalidAuthorization()", "InvalidDeviceSignature()",
]
TIPO_RECORD = "(uint256,bytes32,uint64,uint256,uint256,address,uint32,bytes32,bytes32,uint64,string)"


@dataclass
class Tentativa:
    nome: str
    erro_esperado: str
    remetente: str  # "dona" ou "outra"
    calldata: str
    arquivo: str = "-"
    no_prazo: bool = False  # só mostra o erro esperado com a autorização ainda válida


@dataclass
class Vinculo:
    device_id: int
    dona: str
    chave_publica: str
    cadeia: list[str]
    prazo: int
    autorizacao: str
    calldata: str
    transacao: str


# ───────────────────────────── cast ─────────────────────────────


def cast(*args: str, falha_ok: bool = False) -> subprocess.CompletedProcess[str]:
    resultado = subprocess.run(["cast", *args], check=False, capture_output=True, text=True)
    if resultado.returncode != 0 and not falha_ok:
        # Nunca repete os argumentos (podem conter a chave privada) nem a URL do RPC (tem a chave de API).
        # Com `--json`, o cast 1.x põe o erro na saída padrão.
        mensagem = (resultado.stderr.strip() or resultado.stdout.strip())[:600]
        rpc = os.environ.get("SEPOLIA_RPC_URL")
        if rpc:
            mensagem = mensagem.replace(rpc, "<rpc>")
        sys.exit(f"cast {args[0]} falhou:\n{mensagem}")
    return resultado


def cast_json(*args: str):
    """Saída `--json` do cast; o 1.x embrulha em `{"schema_version", "success", "data"}`."""
    saida = json.loads(cast(*args, "--json").stdout)
    if isinstance(saida, dict) and "schema_version" in saida:
        if not saida.get("success", True):
            sys.exit(f"cast {args[0]} falhou: {saida.get('errors')}")
        return saida["data"]
    return saida


def ler(rpc: str, registro: str, assinatura: str, *argumentos: str) -> str:
    return cast("call", registro, assinatura, *argumentos, "--rpc-url", rpc).stdout.strip()


def decodificar(tipo: str, dados: str) -> list:
    return cast_json("abi-decode", f"f()({tipo})", dados)[0]


def erro_da_chamada(rpc: str, registro: str, remetente: str, calldata: str, bloco: str = "latest") -> str:
    """Erro do contrato para esta chamada (ex.: `UnknownDevice(999)`), ou `sem erro`."""
    resultado = cast(
        "call", registro, calldata, "--from", remetente, "--rpc-url", rpc, "--block", bloco, falha_ok=True
    )
    if resultado.returncode == 0:
        return "sem erro"
    texto = resultado.stderr
    if decodificado := re.search(r"execution reverted: ([A-Za-z]\w*\([^)]*\))", texto):
        # O cast anota números grandes: "AuthorizationExpired(1795041544 [1.795e9])"
        return re.sub(r" \[[^\]]*\]", "", decodificado.group(1))
    if dados := re.search(r'data: "(0x[0-9a-fA-F]*)"', texto):
        seletor = dados.group(1)[:10].lower()
        for erro in ERROS:
            if cast("sig", erro).stdout.strip().lower() == seletor:
                return erro if erro.endswith("()") else f"{erro.split('(')[0]}({dados.group(1)})"
        return dados.group(1)
    return texto.strip().splitlines()[0]


def enviar_forcado(rpc: str, registro: str, chave: str, calldata: str) -> tuple[str, int, int]:
    """Envia sem estimativa e espera o recibo; devolve (hash, bloco, status)."""
    tx = cast(
        "send", registro, calldata, "--gas-limit", GAS_LIMIT, "--async", "--rpc-url", rpc, "--private-key", chave
    ).stdout.strip()
    recibo = cast_json("receipt", tx, "--confirmations", "1", "--rpc-url", rpc)
    return tx, int(recibo["blockNumber"], 0), int(recibo["status"], 0)


# ───────────────────────────── dados ─────────────────────────────


def carregar_golden():
    """sha256/phash de tools/golden/gerar_golden.py (hashing-spec v1)."""
    sys.dont_write_bytecode = True
    spec = importlib.util.spec_from_file_location("gerar_golden", RAIZ / "tools/golden/gerar_golden.py")
    modulo = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(modulo)
    return modulo


def ler_vinculo(rpc: str, registro: str, device_id: int) -> Vinculo:
    """Aparelho e a transação real do vínculo (evento `DeviceRegistered` no bloco do vínculo)."""
    device = decodificar(TIPO_DEVICE, ler(rpc, registro, "getDevice(uint32)", str(device_id)))
    if int(device[0]) == 0:
        sys.exit(f"aparelho {device_id} não existe neste contrato")
    bloco = str(device[5])
    topico = "0x" + device_id.to_bytes(32, "big").hex()
    logs = cast_json(
        "logs",
        "--from-block", bloco, "--to-block", bloco, "--address", registro,
        "DeviceRegistered(uint32 indexed,address indexed,bytes)", topico,
        "--rpc-url", rpc,
    )
    if not logs:
        sys.exit(f"evento DeviceRegistered do aparelho {device_id} não encontrado no bloco {bloco}")
    transacao = logs[0]["transactionHash"]
    calldata = cast("tx", transacao, "input", "--rpc-url", rpc).stdout.strip()
    chave, cadeia, prazo, autorizacao = cast_json("decode-calldata", SIG_REGISTER_DEVICE, calldata)
    return Vinculo(device_id, device[1], chave, cadeia, int(prazo), autorizacao, calldata, transacao)


def ler_registro(rpc: str, registro: str, record_id: int, device_id: int) -> tuple[str, str]:
    """(r, s) da assinatura real de um registro do aparelho."""
    record = decodificar(TIPO_RECORD, ler(rpc, registro, "getRecord(uint256)", str(record_id)))
    if int(record[0]) == 0:
        sys.exit(f"registro {record_id} não existe neste contrato")
    if int(record[6]) != device_id:
        sys.exit(f"registro {record_id} é do aparelho {record[6]}, não do {device_id}")
    return record[7], record[8]


def chave_publica(chave: ec.EllipticCurvePrivateKey) -> str:
    publica = chave.public_key().public_bytes(serialization.Encoding.X962, serialization.PublicFormat.UncompressedPoint)
    return "0x" + publica.hex()


def payload(registro: str, registrante: str, device_id: int, sha256: str, phash: str) -> bytes:
    """002 signature-payload §1 para uma captura original: parentId 0 e operations vazio."""
    msg = (
        DOMAIN
        + CHAIN_ID.to_bytes(32, "big")
        + bytes.fromhex(registro.removeprefix("0x"))
        + bytes.fromhex(registrante.removeprefix("0x"))
        + device_id.to_bytes(4, "big")
        + bytes.fromhex(sha256)
        + int(phash, 16).to_bytes(8, "big")
        + (0).to_bytes(32, "big")
        + hashlib.sha256(b"").digest()
    )
    assert len(msg) == 195
    return msg


def assinar(chave: ec.EllipticCurvePrivateKey, msg: bytes) -> tuple[str, str]:
    """r e s em hex de 32 bytes, low-S: a recusa vem da chave, não da forma da assinatura."""
    r, s = decode_dss_signature(chave.sign(msg, ec.ECDSA(hashes.SHA256())))
    return "0x" + r.to_bytes(32, "big").hex(), "0x" + min(s, N - s).to_bytes(32, "big").hex()


def calldata(assinatura: str, *argumentos: str) -> str:
    return cast("calldata", assinatura, *argumentos).stdout.strip()


def hashes_da_foto(foto: Path | None) -> tuple[str, str, str]:
    """(arquivo, SHA-256, pHash) da foto, ou aleatórios sem foto."""
    if foto is None:
        return "-", secrets.token_hex(32), secrets.token_hex(8)
    golden = carregar_golden()
    dados = foto.read_bytes()
    return foto.name, golden.sha256(dados), golden.phash(dados)


# ───────────────────────────── tentativas ─────────────────────────────


def tentativas_de_vinculo(vinculo: Vinculo) -> list[Tentativa]:
    chave_nova = chave_publica(ec.generate_private_key(ec.SECP256R1()))
    sem_autorizacao = calldata(
        SIG_REGISTER_DEVICE,
        chave_nova,
        "[" + ",".join(vinculo.cadeia) + "]",
        str(int(time.time()) + 600),
        "0x" + "00" * 65,
    )
    return [
        Tentativa("vinculo_sem_autorizacao", "InvalidAuthorization()", "dona", sem_autorizacao),
        Tentativa(
            "vinculo_autorizacao_outra_carteira", "InvalidAuthorization()", "outra", vinculo.calldata, no_prazo=True
        ),
        Tentativa(
            "vinculo_autorizacao_reutilizada",
            f"DeviceAlreadyRegistered({vinculo.device_id})",
            "dona",
            vinculo.calldata,
            no_prazo=True,
        ),
    ]


def tentativas_de_registro(
    registro: str, vinculo: Vinculo, assinatura_real: tuple[str, str], foto: Path | None
) -> list[Tentativa]:
    arquivo, sha256, phash = hashes_da_foto(foto)
    outra_chave = ec.generate_private_key(ec.SECP256R1())
    r, s = assinar(outra_chave, payload(registro, vinculo.dona, vinculo.device_id, sha256, phash))
    argumentos = ["0x" + sha256, str(int(phash, 16)), str(vinculo.device_id)]
    return [
        Tentativa(
            "registro_assinatura_outra_chave",
            "InvalidDeviceSignature()",
            "dona",
            calldata(SIG_REGISTER_CAPTURE, *argumentos, r, s),
            arquivo,
        ),
        Tentativa(
            "registro_dados_alterados",
            "InvalidDeviceSignature()",
            "dona",
            calldata(SIG_REGISTER_CAPTURE, *argumentos, *assinatura_real),
            arquivo,
        ),
    ]


def esperar_prazo(prazo: int) -> None:
    while (falta := prazo - int(time.time()) + 30) > 0:
        print(f"  aguardando o prazo da autorização vencer: {falta} s", flush=True)
        time.sleep(min(falta, 60))


def gravar(linhas: list[list[str]]) -> None:
    SAIDA.parent.mkdir(parents=True, exist_ok=True)
    novo = not SAIDA.exists()
    with SAIDA.open("a", newline="") as arquivo:
        escritor = csv.writer(arquivo)
        if novo:
            escritor.writerow(COLUNAS)
        escritor.writerows(linhas)
    print(f"gravado em {SAIDA.relative_to(RAIZ)}")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--device", type=int, required=True, help="deviceId vinculado pelo app")
    parser.add_argument("--registro", type=int, required=True, help="id de um registro desse aparelho")
    parser.add_argument("--foto", type=Path, help="foto nova para as tentativas de registro")
    parser.add_argument("--simular", action="store_true", help="só lê a rede e mostra os erros, sem enviar")
    args = parser.parse_args()

    rpc = os.environ.get("SEPOLIA_RPC_URL") or sys.exit("defina SEPOLIA_RPC_URL")
    registro = os.environ.get("REGISTRY_ADDRESS") or sys.exit("defina REGISTRY_ADDRESS (ImageRegistry do deploy)")

    vinculo = ler_vinculo(rpc, registro, args.device)
    assinatura_real = ler_registro(rpc, registro, args.registro, args.device)
    falta = vinculo.prazo - int(time.time())
    print(f"contrato {registro}, aparelho {vinculo.device_id} de {vinculo.dona}")
    print(f"vínculo {vinculo.transacao}; prazo da autorização em {falta} s")
    if falta < MARGEM_PRAZO_S and not args.simular:
        sys.exit(
            "a autorização do vínculo já venceu ou está para vencer: vincule um aparelho novo pelo app "
            "e rode o script em até ~10 min (tentativas 2 e 3)"
        )

    enderecos = {"dona": vinculo.dona, "outra": CARTEIRA_SIMULADA_OUTRA}
    chave = None
    if not args.simular:
        chave = os.environ.get("CARTEIRA_PRIVATE_KEY") or sys.exit("defina CARTEIRA_PRIVATE_KEY")
        endereco = cast("wallet", "address", "--private-key", chave).stdout.strip()
        if endereco.lower() != vinculo.dona.lower():
            sys.exit(f"CARTEIRA_PRIVATE_KEY é de {endereco}, mas o aparelho é de {vinculo.dona}")

    tentativas = tentativas_de_vinculo(vinculo) + tentativas_de_registro(registro, vinculo, assinatura_real, args.foto)
    vencida = Tentativa(
        "vinculo_autorizacao_vencida", f"AuthorizationExpired({vinculo.prazo})", "dona", vinculo.calldata
    )

    linhas, divergentes = [], 0
    for tentativa in [*tentativas, vencida]:
        remetente = enderecos[tentativa.remetente]
        if args.simular:
            obtido = erro_da_chamada(rpc, registro, remetente, tentativa.calldata)
            if tentativa is vencida and falta > 0:
                print(f"{tentativa.nome}: só depois do prazo (hoje: {obtido})")
                continue
            if tentativa.no_prazo and falta <= 0:
                print(f"{tentativa.nome}: só no prazo da autorização (hoje: {obtido})")
                continue
            ok = obtido == tentativa.erro_esperado
            divergentes += not ok
            print(f"{tentativa.nome}: esperado {tentativa.erro_esperado}, hoje {obtido}{'' if ok else '  ← DIVERGE'}")
            continue
        if tentativa is vencida:
            esperar_prazo(vinculo.prazo)
        if tentativa.remetente == "outra":
            # Uma carteira só (prova de conceito): a chamada de outra carteira é simulada, sem envio
            bloco = int(cast("block-number", "--rpc-url", rpc).stdout.strip())
            tx, status = "-", "simulada"
            obtido = erro_da_chamada(rpc, registro, remetente, tentativa.calldata, str(bloco))
        else:
            tx, bloco, status = enviar_forcado(rpc, registro, chave, tentativa.calldata)
            obtido = erro_da_chamada(rpc, registro, remetente, tentativa.calldata, str(bloco - 1))
        ok = status in (0, "simulada") and obtido == tentativa.erro_esperado
        divergentes += not ok
        print(f"{tentativa.nome}: status {status}, {obtido}, bloco {bloco}, tx {tx}{'' if ok else '  ← DIVERGE'}")
        linhas.append([tentativa.arquivo, tentativa.nome, tentativa.erro_esperado, obtido, tx, bloco, status])

    if linhas:
        gravar(linhas)
    if divergentes:
        sys.exit(f"{divergentes} tentativa(s) fora do esperado")


if __name__ == "__main__":
    main()
