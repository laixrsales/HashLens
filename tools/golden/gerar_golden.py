# /// script
# requires-python = ">=3.11,<3.14"
# dependencies = [
#   "opencv-python-headless~=4.14.0",
#   "numpy>=2",
#   "pillow>=11",
#   "imagehash>=4.3",
# ]
# ///
"""Gera as variações golden e o expected.json (T011).

Implementação de referência dos identificadores de
specs/002-registro-rastreamento-imagens/contracts/hashing-spec.md (v1), usada para
produzir os valores esperados dos testes T012/T013 do app.

Uso (a partir da raiz do repositório):

    uv run tools/golden/gerar_golden.py

Entradas: as fotos da autora em android/app/src/test/resources/golden/originais/ (nunca
são alteradas). Saídas: as variações e o expected.json em android/app/src/test/resources/golden/.
"""

from __future__ import annotations

import hashlib
import json
import struct
from pathlib import Path

import cv2
import imagehash
import numpy as np
from PIL import Image

RAIZ = Path(__file__).resolve().parents[2]
GOLDEN = RAIZ / "android/app/src/test/resources/golden"
ORIGINAL = GOLDEN / "originais/20261006_205911.jpg"
OUTRO_ANGULO = GOLDEN / "originais/20261006_205922.jpg"

EXIF_ORIENTATION = 0x0112
JPEG_QUALIDADE_APP = 95  # hashing-spec §5
JPEG_QUALIDADE_COMPRIMIDA = 70  # menor qualidade de SC-004
BRILHO_EDICAO_LEVE = 15


# --- Leitura -------------------------------------------------------------------------------------


def orientacao_exif(dados: bytes) -> int:
    """Valor da tag EXIF Orientation (1 se ausente)."""
    import io

    return Image.open(io.BytesIO(dados)).getexif().get(EXIF_ORIENTATION, 1)


def aplicar_orientacao(img: np.ndarray, orientacao: int) -> np.ndarray:
    """Transforma os pixels conforme a tag EXIF Orientation (valores 1 a 8)."""
    if orientacao in (2, 4, 5, 7):
        img = cv2.flip(img, 1)
    rotacao = {
        3: cv2.ROTATE_180,
        4: cv2.ROTATE_180,
        5: cv2.ROTATE_90_COUNTERCLOCKWISE,
        6: cv2.ROTATE_90_CLOCKWISE,
        7: cv2.ROTATE_90_CLOCKWISE,
        8: cv2.ROTATE_90_COUNTERCLOCKWISE,
    }.get(orientacao)
    return img if rotacao is None else cv2.rotate(img, rotacao)


def decodificar(dados: bytes) -> np.ndarray:
    """hashing-spec §2 passo 1: decodifica aplicando a orientação EXIF (em BGR)."""
    flags = cv2.IMREAD_COLOR | cv2.IMREAD_IGNORE_ORIENTATION
    img = cv2.imdecode(np.frombuffer(dados, np.uint8), flags)
    if img is None:
        raise ValueError("não foi possível decodificar a imagem")
    return aplicar_orientacao(img, orientacao_exif(dados))


# --- Identificadores (hashing-spec v1) -----------------------------------------------------------


def sha256(dados: bytes) -> str:
    """hashing-spec §1: todos os bytes do arquivo, hex minúsculo."""
    return hashlib.sha256(dados).hexdigest()


def phash(dados: bytes) -> str:
    """hashing-spec §2, passos 1 a 9."""
    cinza = cv2.cvtColor(decodificar(dados), cv2.COLOR_BGR2GRAY)
    pequena = cv2.resize(cinza, (32, 32), interpolation=cv2.INTER_AREA)
    dct = cv2.dct(pequena.astype(np.float32))
    bloco = dct[:8, :8].flatten()  # row-major, inclui o termo DC
    mediana = float(np.median(bloco))  # média dos dois centrais
    valor = 0
    for coef in bloco:
        valor = (valor << 1) | int(coef > mediana)
    return f"{valor:016x}"


def phash_imagehash(caminho: Path) -> str:
    """imagehash.phash, apenas para comparação (o spec admite poucos bits de diferença)."""
    from PIL import ImageOps

    return str(imagehash.phash(ImageOps.exif_transpose(Image.open(caminho))))


def hamming(a: str, b: str) -> int:
    return (int(a, 16) ^ int(b, 16)).bit_count()


# --- Variações -----------------------------------------------------------------------------------


def salvar_jpeg(caminho: Path, img: np.ndarray, qualidade: int) -> None:
    """Grava JPEG sem metadados (pixels já na orientação correta)."""
    ok, buf = cv2.imencode(".jpg", img, [cv2.IMWRITE_JPEG_QUALITY, qualidade])
    if not ok:
        raise ValueError(f"falha ao codificar {caminho.name}")
    caminho.write_bytes(buf.tobytes())


def trocar_orientacao(dados: bytes, nova: int) -> bytes:
    """Reescreve só o valor da tag Orientation no APP1/EXIF; os demais bytes ficam iguais."""
    app1 = dados.find(b"Exif\x00\x00")
    if app1 < 0:
        raise ValueError("arquivo sem EXIF")
    tiff = app1 + 6
    ordem = ">" if dados[tiff : tiff + 2] == b"MM" else "<"
    (ifd0,) = struct.unpack_from(ordem + "I", dados, tiff + 4)
    (n,) = struct.unpack_from(ordem + "H", dados, tiff + ifd0)
    for i in range(n):
        entrada = tiff + ifd0 + 2 + 12 * i
        (tag,) = struct.unpack_from(ordem + "H", dados, entrada)
        if tag == EXIF_ORIENTATION:
            saida = bytearray(dados)
            struct.pack_into(ordem + "H", saida, entrada + 8, nova)
            return bytes(saida)
    raise ValueError("tag Orientation não encontrada")


def gerar_variacoes() -> dict[str, str]:
    """Cria as variações a partir do original e devolve {arquivo relativo: descrição}."""
    dados = ORIGINAL.read_bytes()
    img = decodificar(dados)

    leve = cv2.convertScaleAbs(img, alpha=1.0, beta=BRILHO_EDICAO_LEVE)
    salvar_jpeg(GOLDEN / "edicao_leve.jpg", leve, JPEG_QUALIDADE_APP)
    salvar_jpeg(GOLDEN / "comprimida_q70.jpg", img, JPEG_QUALIDADE_COMPRIMIDA)
    cinza = cv2.cvtColor(img, cv2.COLOR_BGR2GRAY)
    salvar_jpeg(GOLDEN / "preto_branco.jpg", cinza, JPEG_QUALIDADE_APP)
    (GOLDEN / "orientacao_exif_1.jpg").write_bytes(trocar_orientacao(dados, 1))

    rel = lambda p: p.relative_to(GOLDEN).as_posix()  # noqa: E731
    return {
        rel(ORIGINAL): (
            "original da câmera, sem alterações (EXIF Orientation = "
            f"{orientacao_exif(dados)})"
        ),
        "edicao_leve.jpg": f"original com brilho +{BRILHO_EDICAO_LEVE}, JPEG q{JPEG_QUALIDADE_APP}",
        "comprimida_q70.jpg": f"original recodificado em JPEG q{JPEG_QUALIDADE_COMPRIMIDA}",
        "preto_branco.jpg": f"original em escala de cinza, JPEG q{JPEG_QUALIDADE_APP}",
        "orientacao_exif_1.jpg": (
            "bytes idênticos ao original, exceto a tag EXIF Orientation trocada para 1 "
            "(mesmos pixels, exibidos sem rotação)"
        ),
        rel(OUTRO_ANGULO): "mesma cena fotografada de outro ângulo",
    }


def main() -> None:
    arquivos = gerar_variacoes()
    phash_original = phash(ORIGINAL.read_bytes())

    saida = {}
    for nome, descricao in arquivos.items():
        caminho = GOLDEN / nome
        dados = caminho.read_bytes()
        p = phash(dados)
        saida[nome] = {
            "descricao": descricao,
            "sha256": sha256(dados),
            "phash": p,
            "hammingAoOriginal": hamming(p, phash_original),
            "phashImagehash": phash_imagehash(caminho),
        }

    expected = {
        "hashingSpec": "v1",
        "geradoPor": "tools/golden/gerar_golden.py",
        "opencv": cv2.__version__,
        "observacao": (
            "sha256 e phash são os valores esperados; phashImagehash (Python imagehash) é só "
            "referência e pode divergir em poucos bits (hashing-spec §2)."
        ),
        "arquivos": saida,
    }
    texto = json.dumps(expected, ensure_ascii=False, indent=2) + "\n"
    (GOLDEN / "expected.json").write_text(texto, encoding="utf-8")
    print(texto)


if __name__ == "__main__":
    main()
