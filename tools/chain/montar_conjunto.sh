#!/usr/bin/env bash
# Monta no aparelho o conjunto rotulado da matriz de classificação (020/T061, 020 SC-007).
#
# O rótulo de cada arquivo vem direto do contrato, consultado aqui com `cast` sobre o SHA-256
# calculado no computador, e não do app: é o gabarito contra o qual o app é avaliado
# (android/app/src/androidTest/java/br/ufv/hashlens/evaluation/ClassificationMatrixTest.kt).
#
# - originais / versoes: fotos de Pictures/HashLens do aparelho que têm registro
#   (parentId 0 → originais; senão → versoes). Fotos sem registro (falha, pendente) ficam de fora.
#   Os arquivos de PASTA_EXTRA_REGISTRADAS, se definida, entram pelo mesmo critério (ex.: fotos
#   registradas por outro aparelho ou carteira).
# - nao_registrados: as N fotos mais recentes de DCIM/Camera (JPEG) sem registro, mais os
#   arquivos de PASTA_EXTRA_NAO_REGISTRADOS, se definida (ex.: cópias recomprimidas ou recortadas
#   de fotos registradas, que devem dar "Correspondência visual" ou "Não registrada").
# Não há grupo de adulterados: no contrato v2 um registro com assinatura do aparelho adulterada
# não existe, porque a rede o recusa (tools/chain/tentar_origens_nao_autorizadas.py,
# docs/resultados/recusas.md).
#
# Contrato e RPC: REGISTRY_ADDRESS e SEPOLIA_RPC_URL, ou, sem elas, registry.address e
# sepolia.rpcUrl de android/local.properties (o mesmo deploy do app instalado).
#
# Uso (a partir da raiz; aparelho conectado com o app de debug instalado):
#
#   tools/chain/montar_conjunto.sh 20
#
# Os arquivos são copiados para /sdcard/Android/data/br.ufv.hashlens/files/conjunto/ e nada sai
# do aparelho além de uma cópia temporária no computador, apagada no fim. A lista com os rótulos
# vai para docs/resultados/dados/conjunto-rotulado.csv.
set -euo pipefail

N_NAO_REGISTRADOS="${1:-20}"
RAIZ="$(cd "$(dirname "$0")/../.." && pwd)"
propriedade() { grep -E "^$1=" "$RAIZ/android/local.properties" 2>/dev/null | cut -d= -f2- | tr -d '[:space:]'; }
REGISTRY="${REGISTRY_ADDRESS:-$(propriedade registry.address)}"
SEPOLIA_RPC_URL="${SEPOLIA_RPC_URL:-$(propriedade sepolia.rpcUrl)}"
: "${REGISTRY:?defina REGISTRY_ADDRESS ou registry.address em android/local.properties}"
: "${SEPOLIA_RPC_URL:?defina SEPOLIA_RPC_URL ou sepolia.rpcUrl em android/local.properties}"
echo "contrato $REGISTRY"
DESTINO=/sdcard/Android/data/br.ufv.hashlens/files/conjunto
LISTA="$RAIZ/docs/resultados/dados/conjunto-rotulado.csv"
TEMP="$(mktemp -d)"
trap 'rm -rf "$TEMP"' EXIT

registro_de() { # id do registro com o SHA-256 do arquivo, ou 0
    local sha
    sha="$(sha256sum "$1" | cut -d' ' -f1)"
    cast call "$REGISTRY" "getIdBySha256(bytes32)(uint256)" "0x$sha" --rpc-url "$SEPOLIA_RPC_URL" | cut -d' ' -f1
}

pai_de() { # parentId do registro (4º campo de ImageRecord)
    cast call "$REGISTRY" \
        "getRecord(uint256)((uint256,bytes32,uint64,uint256,uint256,address,uint32,bytes32,bytes32,uint64,string))" \
        "$1" --rpc-url "$SEPOLIA_RPC_URL" | tr -d '()' | awk -F', ' '{ split($4, a, " "); print a[1] }'
}

adicionar() { # arquivo local, grupo, id do registro
    adb push "$1" "$DESTINO/$2/" >/dev/null 2>&1 || { echo "falha ao copiar $1 para o aparelho" >&2; exit 1; }
    echo "\"$(basename "$1")\",$2,$3" >>"$LISTA"
}

classificar_registradas() { # pasta local: originais ou versões conforme o contrato
    local arquivo id
    for arquivo in "$1"/*; do
        id="$(registro_de "$arquivo")"
        if [ "$id" = "0" ]; then
            echo "  $(basename "$arquivo"): sem registro, fica de fora"
        elif [ "$(pai_de "$id")" = "0" ]; then
            adicionar "$arquivo" originais "$id"
        else
            adicionar "$arquivo" versoes "$id"
        fi
    done
}

adb shell rm -rf "$DESTINO"
adb shell mkdir -p "$DESTINO/originais" "$DESTINO/versoes" "$DESTINO/nao_registrados"
echo "arquivo,grupo,registro" >"$LISTA"

echo "Fotos do HashLens (Pictures/HashLens):"
mkdir -p "$TEMP/hashlens"
adb pull /sdcard/Pictures/HashLens/. "$TEMP/hashlens" >/dev/null 2>&1 || { echo "falha ao ler Pictures/HashLens" >&2; exit 1; }
classificar_registradas "$TEMP/hashlens"

echo "Fotos da câmera do sistema (DCIM/Camera), até $N_NAO_REGISTRADOS sem registro:"
mkdir -p "$TEMP/camera"
incluidas=0
for remoto in $(adb shell 'ls -t /sdcard/DCIM/Camera/' | tr -d '\r' | grep -iE '\.jpe?g$'); do
    [ "$incluidas" -ge "$N_NAO_REGISTRADOS" ] && break
    adb pull "/sdcard/DCIM/Camera/$remoto" "$TEMP/camera/" >/dev/null 2>&1 || { echo "falha ao ler $remoto" >&2; exit 1; }
    if [ "$(registro_de "$TEMP/camera/$remoto")" = "0" ]; then
        adicionar "$TEMP/camera/$remoto" nao_registrados 0
        incluidas=$((incluidas + 1))
    fi
done

if [ -n "${PASTA_EXTRA_REGISTRADAS:-}" ]; then
    echo "Fotos registradas fora do app ($PASTA_EXTRA_REGISTRADAS):"
    classificar_registradas "$PASTA_EXTRA_REGISTRADAS"
fi

if [ -n "${PASTA_EXTRA_NAO_REGISTRADOS:-}" ]; then
    for arquivo in "$PASTA_EXTRA_NAO_REGISTRADOS"/*; do
        id="$(registro_de "$arquivo")"
        if [ "$id" = "0" ]; then adicionar "$arquivo" nao_registrados 0; else echo "  $(basename "$arquivo"): registrada (id $id), fica de fora"; fi
    done
fi

echo
tail -n +2 "$LISTA" | cut -d, -f2 | sort | uniq -c
echo "Lista com os rótulos: ${LISTA#"$RAIZ"/}"
