#!/usr/bin/env bash
# Captura respostas ABI reais das funções `view` do ImageRegistry (T027).
#
# Sobe um anvil local, coloca o bytecode do ImageRegistry no endereço da Sepolia usado nos
# vetores de assinatura, registra o aparelho e os dois casos de
# android/app/src/test/resources/signature/vectors.json pela carteira dos vetores
# (anvil_impersonateAccount) e grava as respostas brutas de `cast call` em
# android/app/src/test/resources/chain/responses.json. Como contrato, carteira, deviceId e
# parentId batem com os vetores, as assinaturas dos registros capturados são válidas.
#
# Uso (a partir da raiz do repositório; requer Foundry e jq):
#
#     tools/chain/capturar_respostas.sh
#
# Não usa chave real nem rede pública. Os horários dos blocos são fixos, então a saída é sempre igual.

set -euo pipefail

RAIZ="$(cd "$(dirname "$0")/../.." && pwd)"
VETORES="$RAIZ/android/app/src/test/resources/signature/vectors.json"
SAIDA="$RAIZ/android/app/src/test/resources/chain/responses.json"
PORTA=8547
RPC="http://127.0.0.1:$PORTA"
export PATH="$HOME/.foundry/bin:$PATH"

# Horários fixos dos blocos: 2026-10-06 21:00:00 UTC e minutos seguintes
T_APARELHO=1791320400
T_CAPTURA=1791320460
T_EDICAO=1791320520

anvil --port "$PORTA" --silent --timestamp "$((T_APARELHO - 60))" &
ANVIL_PID=$!
trap 'kill "$ANVIL_PID" 2>/dev/null || true' EXIT
until cast chain-id --rpc-url "$RPC" >/dev/null 2>&1; do sleep 0.2; done

vetor() { jq -r "$1" "$VETORES"; }
CONTRATO=$(vetor '.casos[0].campos.contract')
CARTEIRA=$(vetor '.casos[0].campos.registrant')
CHAVE_PUBLICA=0x$(vetor '.publicKey')

# Bytecode do ImageRegistry no mesmo endereço da Sepolia (o contrato não tem construtor com estado)
# (deploy temporário pela primeira conta do anvil, já desbloqueada: nenhuma chave é usada)
BYTECODE=$(cd "$RAIZ/contracts" && forge inspect ImageRegistry bytecode)
CONTA_ANVIL=$(cast rpc eth_accounts --rpc-url "$RPC" | jq -r '.[0]')
TEMPORARIO=$(cast send --json --from "$CONTA_ANVIL" --unlocked --rpc-url "$RPC" --create "$BYTECODE" |
    jq -r .contractAddress)
cast rpc anvil_setCode "$CONTRATO" "$(cast code "$TEMPORARIO" --rpc-url "$RPC")" --rpc-url "$RPC" >/dev/null

cast rpc anvil_impersonateAccount "$CARTEIRA" --rpc-url "$RPC" >/dev/null
cast rpc anvil_setBalance "$CARTEIRA" 0x56BC75E2D63100000 --rpc-url "$RPC" >/dev/null

enviar() { # enviar <timestamp> <assinatura> <args...>
    local ts=$1
    shift
    cast rpc evm_setNextBlockTimestamp "$ts" --rpc-url "$RPC" >/dev/null
    cast send "$CONTRATO" "$@" --from "$CARTEIRA" --unlocked --rpc-url "$RPC" >/dev/null
}

registrar() { # registrar <timestamp> <índice do caso>
    local ts=$1 i=$2 raw
    raw=$(vetor ".casos[$i].assinaturaRaw")
    local sha=0x$(vetor ".casos[$i].campos.sha256")
    local phash=0x$(vetor ".casos[$i].campos.pHash")
    local device=$(vetor ".casos[$i].campos.deviceId")
    local parent=$(vetor ".casos[$i].campos.parentId")
    local ops=$(vetor ".casos[$i].campos.operations")
    if [[ "$parent" == "0" ]]; then
        enviar "$ts" "registerCapture(bytes32,uint64,uint32,bytes32,bytes32)" \
            "$sha" "$phash" "$device" "0x${raw:0:64}" "0x${raw:64:64}"
    else
        enviar "$ts" "registerEdit(uint256,bytes32,uint64,uint32,bytes32,bytes32,string)" \
            "$parent" "$sha" "$phash" "$device" "0x${raw:0:64}" "0x${raw:64:64}" "$ops"
    fi
}

enviar "$T_APARELHO" "registerDevice(bytes)" "$CHAVE_PUBLICA"
registrar "$T_CAPTURA" 0
registrar "$T_EDICAO" 1

# Cada chamada: nome, assinatura, argumento; grava o calldata e o retorno ABI bruto do eth_call
chamadas=(
    "getRecord 1|getRecord(uint256)|1"
    "getRecord 2|getRecord(uint256)|2"
    "getRecord inexistente|getRecord(uint256)|999"
    "getDevice 1|getDevice(uint32)|1"
    "getDevice inexistente|getDevice(uint32)|99"
    "getDevicesOf carteira|getDevicesOf(address)|$CARTEIRA"
    "getDevicesOf sem aparelhos|getDevicesOf(address)|0x00000000000000000000000000000000000000a1"
    "getIdBySha256 original|getIdBySha256(bytes32)|0x$(vetor '.casos[0].campos.sha256')"
    "getIdBySha256 inexistente|getIdBySha256(bytes32)|0x$(printf 'ab%.0s' {1..32})"
    "getIdsByPHash compartilhado|getIdsByPHash(uint64)|0x$(vetor '.casos[0].campos.pHash')"
    "getIdsByPHash inexistente|getIdsByPHash(uint64)|0x0000000000000001"
    "getChildren 1|getChildren(uint256)|1"
    "getChildren 2|getChildren(uint256)|2"
    "totalRecords|totalRecords()|"
)

respostas='[]'
for chamada in "${chamadas[@]}"; do
    IFS='|' read -r nome assinatura argumento <<<"$chamada"
    args=()
    [[ -n "$argumento" ]] && args=("$argumento")
    calldata=$(cast calldata "$assinatura" "${args[@]}")
    resposta=$(cast call "$CONTRATO" "$assinatura" "${args[@]}" --rpc-url "$RPC")
    respostas=$(jq --arg n "$nome" --arg f "$assinatura" --arg a "$argumento" --arg c "$calldata" --arg r "$resposta" \
        '. + [{nome: $n, funcao: $f, argumento: $a, calldata: $c, resposta: $r}]' <<<"$respostas")
done

mkdir -p "$(dirname "$SAIDA")"
jq -n --arg contrato "$CONTRATO" --arg carteira "$CARTEIRA" \
    --argjson tAparelho "$T_APARELHO" --argjson tCaptura "$T_CAPTURA" --argjson tEdicao "$T_EDICAO" \
    --argjson respostas "$respostas" '{
        geradoPor: "tools/chain/capturar_respostas.sh",
        observacao: "Respostas brutas de eth_call (anvil). Registros 1 e 2 são os casos original e edicao de signature/vectors.json, com assinaturas válidas.",
        contrato: $contrato,
        carteira: $carteira,
        timestamps: {aparelho: $tAparelho, captura: $tCaptura, edicao: $tEdicao},
        respostas: $respostas
    }' >"$SAIDA"
echo "Respostas gravadas em ${SAIDA#"$RAIZ"/}"
