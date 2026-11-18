#!/usr/bin/env bash
# Gera o calldata de referência das funções de escrita do ImageRegistry (002/T043; v2 em 020/T025).
#
# Usa `cast calldata` (Foundry) com os argumentos dos casos de
# android/app/src/test/resources/signature/vectors.json e, para o registerDevice v2, do vetor
# EIP-712 de contracts/test/vectors/device-authorization.json, e grava
# android/app/src/test/resources/chain/calldata.json. O RegistryTxBuilder do app deve produzir
# exatamente esses bytes para os mesmos argumentos.
#
# Uso (a partir da raiz do repositório; requer Foundry e jq):
#
#     tools/chain/gerar_calldata.sh
#
# Não acessa rede nem usa chave: só codificação ABI.

set -euo pipefail

RAIZ="$(cd "$(dirname "$0")/../.." && pwd)"
VETORES="$RAIZ/android/app/src/test/resources/signature/vectors.json"
VETOR_AUTORIZACAO="$RAIZ/contracts/test/vectors/device-authorization.json"
SAIDA="$RAIZ/android/app/src/test/resources/chain/calldata.json"
export PATH="$HOME/.foundry/bin:$PATH"

vetor() { jq -r "$1" "$VETORES"; }
CHAVE_PUBLICA=0x$(vetor '.publicKey')

caso() { # caso <índice> → variáveis do caso
    SHA=0x$(vetor ".casos[$1].campos.sha256")
    PHASH=0x$(vetor ".casos[$1].campos.pHash")
    DEVICE=$(vetor ".casos[$1].campos.deviceId")
    PARENT=$(vetor ".casos[$1].campos.parentId")
    OPERACOES=$(vetor ".casos[$1].campos.operations")
    RAW=$(vetor ".casos[$1].assinaturaRaw")
    SIG_R=0x${RAW:0:64}
    SIG_S=0x${RAW:64:64}
}

entrada() { # entrada <nome> <função> <calldata> <args JSON>
    jq -n --arg nome "$1" --arg funcao "$2" --arg calldata "$3" --argjson argumentos "$4" \
        '{nome: $nome, funcao: $funcao, argumentos: $argumentos, calldata: $calldata}'
}

F_DEVICE="registerDevice(bytes,bytes[],uint64,bytes)"
F_CAPTURE="registerCapture(bytes32,uint64,uint32,bytes32,bytes32)"
F_EDIT="registerEdit(uint256,bytes32,uint64,uint32,bytes32,bytes32,string)"

ENTRADAS=()
autorizacao() { jq -r "$1" "$VETOR_AUTORIZACAO"; }
CADEIA="[$(autorizacao '.input.attestationChain | join(",")')]"
PRAZO=$(autorizacao '.message.deadline')
ASSINATURA_ATESTADOR=$(autorizacao '.signature')
[[ "$(autorizacao '.input.publicKey')" == "$CHAVE_PUBLICA" ]]
ENTRADAS+=("$(entrada "registerDevice" "$F_DEVICE" \
    "$(cast calldata "$F_DEVICE" "$CHAVE_PUBLICA" "$CADEIA" "$PRAZO" "$ASSINATURA_ATESTADOR")" \
    "$(jq -n --arg k "$CHAVE_PUBLICA" --argjson c "$(autorizacao '.input.attestationChain')" --argjson d "$PRAZO" \
        --arg a "$ASSINATURA_ATESTADOR" '{publicKey: $k, attestationChain: $c, deadline: $d, authorization: $a}')")")

caso 0
ENTRADAS+=("$(entrada "registerCapture original" "$F_CAPTURE" \
    "$(cast calldata "$F_CAPTURE" "$SHA" "$PHASH" "$DEVICE" "$SIG_R" "$SIG_S")" \
    "$(jq -n --arg s "$SHA" --arg p "$PHASH" --argjson d "$DEVICE" --arg r "$RAW" \
        '{sha256: $s, pHash: $p, deviceId: $d, signature: $r}')")")

caso 1
ENTRADAS+=("$(entrada "registerEdit edicao" "$F_EDIT" \
    "$(cast calldata "$F_EDIT" "$PARENT" "$SHA" "$PHASH" "$DEVICE" "$SIG_R" "$SIG_S" "$OPERACOES")" \
    "$(jq -n --argjson pa "$PARENT" --arg s "$SHA" --arg p "$PHASH" --argjson d "$DEVICE" --arg r "$RAW" \
        --arg o "$OPERACOES" '{parentId: $pa, sha256: $s, pHash: $p, deviceId: $d, signature: $r, operations: $o}')")")

printf '%s\n' "${ENTRADAS[@]}" | jq -s \
    '{geradoPor: "tools/chain/gerar_calldata.sh", observacao: "cast calldata com os argumentos de signature/vectors.json e, no registerDevice, do vetor contracts/test/vectors/device-authorization.json.", calldata: .}' \
    >"$SAIDA"
echo "gravado: $SAIDA"
