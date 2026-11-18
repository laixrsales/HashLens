#!/usr/bin/env bash
# Captura respostas ABI reais das funções `view` do ImageRegistry (002/T027; v2 em 020/T023) e os
# recibos das transações de escrita (002/T050).
#
# Sobe um anvil local com o chainId da Sepolia, roda o construtor do ImageRegistry v2 no endereço
# usado nos vetores de assinatura (o separador EIP-712 é `immutable` e depende do endereço e da
# rede), com o atestador de teste de contracts/test/vectors/device-authorization.json. Vincula a
# chave dos vetores com uma autorização assinada na hora por essa chave de teste e a cadeia real de
# android/attestation/src/test/resources/attestation/real/ (o contrato não confere a cadeia, só o
# hash), registra os dois casos de
# android/app/src/test/resources/signature/vectors.json pela carteira dos vetores
# (anvil_impersonateAccount) e grava as respostas brutas de `cast call` em
# android/app/src/test/resources/chain/responses.json, com o `eth_getLogs` do evento DeviceAttested;
# os recibos (inclusive o de uma captura
# repetida, que reverte com AlreadyRegistered) vão para chain/receipts.json. Como contrato, carteira, deviceId e
# parentId batem com os vetores, as assinaturas dos registros capturados são válidas.
#
# Uso (a partir da raiz do repositório; requer Foundry e jq):
#
#     tools/chain/capturar_respostas.sh
#
# Não usa chave real nem rede pública (a chave do atestador é a de teste, pública no vetor). Os horários dos blocos são fixos, então a saída é sempre igual.

set -euo pipefail

RAIZ="$(cd "$(dirname "$0")/../.." && pwd)"
VETORES="$RAIZ/android/app/src/test/resources/signature/vectors.json"
VETOR_AUTORIZACAO="$RAIZ/contracts/test/vectors/device-authorization.json"
CADEIA_REAL="$RAIZ/android/attestation/src/test/resources/attestation/real/SM_M526B-tee.json"
SAIDA="$RAIZ/android/app/src/test/resources/chain/responses.json"
SAIDA_RECIBOS="$RAIZ/android/app/src/test/resources/chain/receipts.json"
PORTA=8547
RPC="http://127.0.0.1:$PORTA"
export PATH="$HOME/.foundry/bin:$PATH"

# Horários fixos dos blocos: 2026-10-06 21:00:00 UTC e minutos seguintes
T_APARELHO=1791320400
T_CAPTURA=1791320460
T_EDICAO=1791320520
T_REPETIDA=1791320580

anvil --port "$PORTA" --silent --chain-id 11155111 --timestamp "$((T_APARELHO - 60))" &
ANVIL_PID=$!
trap 'kill "$ANVIL_PID" 2>/dev/null || true' EXIT
until cast chain-id --rpc-url "$RPC" >/dev/null 2>&1; do sleep 0.2; done

vetor() { jq -r "$1" "$VETORES"; }
CONTRATO=$(vetor '.casos[0].campos.contract')
CARTEIRA=$(vetor '.casos[0].campos.registrant')
CHAVE_PUBLICA=0x$(vetor '.publicKey')

ATESTADOR=$(jq -r '.attester.address' "$VETOR_AUTORIZACAO")
CHAVE_ATESTADOR=$(jq -r '.attester.privateKey' "$VETOR_AUTORIZACAO")

# Construtor do ImageRegistry v2 rodando no próprio endereço dos vetores, como o deployCodeTo do
# forge: o código de criação vira o código da conta, um eth_call sem dados executa o construtor
# (address(this) e chainid certos) e devolve o código de execução, que substitui o de criação. O
# construtor só define `immutable`s, então não há estado a copiar.
CRIACAO=$(cd "$RAIZ/contracts" && forge inspect ImageRegistry bytecode)$(cast abi-encode "f(address)" "$ATESTADOR" | cut -c3-)
cast rpc anvil_setCode "$CONTRATO" "$CRIACAO" --rpc-url "$RPC" >/dev/null
EXECUCAO=$(cast call "$CONTRATO" --rpc-url "$RPC")
cast rpc anvil_setCode "$CONTRATO" "$EXECUCAO" --rpc-url "$RPC" >/dev/null
[[ "$(cast call "$CONTRATO" "attester()(address)" --rpc-url "$RPC")" == "$ATESTADOR" ]]

# Cadeia real em hex (DER, da folha para a raiz) e a autorização EIP-712 do atestador de teste
CADEIA="[$(python3 -c 'import base64,json,sys; print(",".join("0x"+base64.b64decode(c).hex() for c in json.load(open(sys.argv[1]))["chain"]))' "$CADEIA_REAL")]"
PRAZO=$((T_APARELHO + 900))
HASH_CADEIA=$(cast keccak "$(cast abi-encode "f(bytes[])" "$CADEIA")")
DADOS_TIPADOS=$(jq -n --arg c "$CONTRATO" --arg w "$CARTEIRA" --arg k "$(cast keccak "$CHAVE_PUBLICA")" \
    --arg h "$HASH_CADEIA" --argjson d "$PRAZO" '{
        types: {
            EIP712Domain: [{name: "name", type: "string"}, {name: "version", type: "string"},
                {name: "chainId", type: "uint256"}, {name: "verifyingContract", type: "address"}],
            DeviceAuthorization: [{name: "wallet", type: "address"}, {name: "publicKeyHash", type: "bytes32"},
                {name: "attestationHash", type: "bytes32"}, {name: "deadline", type: "uint64"}]
        },
        primaryType: "DeviceAuthorization",
        domain: {name: "HashLens ImageRegistry", version: "2", chainId: 11155111, verifyingContract: $c},
        message: {wallet: $w, publicKeyHash: $k, attestationHash: $h, deadline: $d}
    }')
AUTORIZACAO=$(cast wallet sign --data "$DADOS_TIPADOS" --private-key "$CHAVE_ATESTADOR")

cast rpc anvil_impersonateAccount "$CARTEIRA" --rpc-url "$RPC" >/dev/null
cast rpc anvil_setBalance "$CARTEIRA" 0x56BC75E2D63100000 --rpc-url "$RPC" >/dev/null

recibos='[]'

enviar() { # enviar <nome do recibo> <timestamp> <assinatura> <args...> (grava o recibo em $recibos)
    local nome=$1 ts=$2 recibo
    shift 2
    cast rpc evm_setNextBlockTimestamp "$ts" --rpc-url "$RPC" >/dev/null
    # --gas-limit fixo: sem estimativa, uma chamada que reverte também é minerada (recibo com status 0)
    recibo=$(cast send "$CONTRATO" "$@" --from "$CARTEIRA" --unlocked --rpc-url "$RPC" \
        --gas-limit 2000000 --json 2>/dev/null || true)
    recibos=$(jq --arg n "$nome" --argjson r "$recibo" \
        '. + [{nome: $n, status: $r.status, logs: [$r.logs[] | {address, topics, data}]}]' <<<"$recibos")
}

registrar() { # registrar <nome do recibo> <timestamp> <índice do caso>
    local nome=$1 ts=$2 i=$3 raw
    raw=$(vetor ".casos[$i].assinaturaRaw")
    local sha=0x$(vetor ".casos[$i].campos.sha256")
    local phash=0x$(vetor ".casos[$i].campos.pHash")
    local device=$(vetor ".casos[$i].campos.deviceId")
    local parent=$(vetor ".casos[$i].campos.parentId")
    local ops=$(vetor ".casos[$i].campos.operations")
    if [[ "$parent" == "0" ]]; then
        enviar "$nome" "$ts" "registerCapture(bytes32,uint64,uint32,bytes32,bytes32)" \
            "$sha" "$phash" "$device" "0x${raw:0:64}" "0x${raw:64:64}"
    else
        enviar "$nome" "$ts" "registerEdit(uint256,bytes32,uint64,uint32,bytes32,bytes32,string)" \
            "$parent" "$sha" "$phash" "$device" "0x${raw:0:64}" "0x${raw:64:64}" "$ops"
    fi
}

enviar "registerDevice" "$T_APARELHO" "registerDevice(bytes,bytes[],uint64,bytes)" \
    "$CHAVE_PUBLICA" "$CADEIA" "$PRAZO" "$AUTORIZACAO"
[[ "$(jq -r '.[-1].status' <<<"$recibos")" == "0x1" ]] || { echo "registerDevice falhou" >&2; exit 1; }
BLOCO_APARELHO=$(cast block-number --rpc-url "$RPC")
registrar "registerCapture original" "$T_CAPTURA" 0
registrar "registerEdit edicao" "$T_EDICAO" 1

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

# eth_getLogs do DeviceAttested do aparelho 1, como o app busca: só o bloco do vínculo, tópico deviceId
BLOCO_HEX=$(printf '0x%x' "$BLOCO_APARELHO")
TOPICO_EVENTO=$(cast keccak "DeviceAttested(uint32,bytes[])")
FILTRO=$(jq -cn --arg a "$CONTRATO" --arg b "$BLOCO_HEX" --arg t0 "$TOPICO_EVENTO" \
    --arg t1 "$(cast to-uint256 1)" '{address: $a, fromBlock: $b, toBlock: $b, topics: [$t0, $t1]}')
LOGS=$(cast rpc eth_getLogs "$FILTRO" --rpc-url "$RPC" | jq '[.[] | {address, topics, data, blockNumber}]')

# Depois das leituras, para não mudar as respostas: a mesma captura de novo reverte (AlreadyRegistered)
registrar "registerCapture repetida" "$T_REPETIDA" 0

mkdir -p "$(dirname "$SAIDA")"
jq -n --arg contrato "$CONTRATO" --arg carteira "$CARTEIRA" \
    --argjson tAparelho "$T_APARELHO" --argjson tCaptura "$T_CAPTURA" --argjson tEdicao "$T_EDICAO" \
    --argjson blocoAparelho "$BLOCO_APARELHO" --argjson filtro "$FILTRO" --argjson logs "$LOGS" \
    --argjson cadeia "$(jq '.chain' "$CADEIA_REAL")" --arg hashCadeia "$HASH_CADEIA" \
    --arg atestador "$ATESTADOR" --argjson respostas "$respostas" '{
        geradoPor: "tools/chain/capturar_respostas.sh",
        observacao: "Respostas brutas de eth_call e eth_getLogs (anvil, ImageRegistry v2). Registros 1 e 2 são os casos original e edicao de signature/vectors.json, com assinaturas válidas. O aparelho 1 foi vinculado com a cadeia real do SM-M526B (attestation/real/) e autorização do atestador de teste.",
        contrato: $contrato,
        carteira: $carteira,
        atestador: $atestador,
        timestamps: {aparelho: $tAparelho, captura: $tCaptura, edicao: $tEdicao},
        blocoAparelho: $blocoAparelho,
        cadeiaAparelho: $cadeia,
        attestationHash: $hashCadeia,
        respostas: $respostas,
        logs: [{nome: "DeviceAttested aparelho 1", filtro: $filtro, resposta: $logs}]
    }' >"$SAIDA"
jq -n --arg contrato "$CONTRATO" --argjson recibos "$recibos" '{
        geradoPor: "tools/chain/capturar_respostas.sh",
        observacao: "Recibos reais (anvil) de eth_getTransactionReceipt, só status e logs. registerCapture repetida reverte com AlreadyRegistered.",
        contrato: $contrato,
        recibos: $recibos
    }' >"$SAIDA_RECIBOS"
echo "Respostas gravadas em ${SAIDA#"$RAIZ"/}"
echo "Recibos gravados em ${SAIDA_RECIBOS#"$RAIZ"/}"
