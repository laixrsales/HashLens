# Contract: Autorização de Vínculo (EIP-712)

**Versão**: `v1` | Normativo para o atestador (emissão), o contrato (verificação) e terceiros
(auditoria). Decisão em [research.md](../research.md) R5.

## 1. Domínio

```text
EIP712Domain(string name,string version,uint256 chainId,address verifyingContract)

name              = "HashLens ImageRegistry"
version           = "2"
chainId           = 11155111 (Sepolia)
verifyingContract = endereço do ImageRegistry do deploy
```

`domainSeparator = keccak256(abi.encode(TYPEHASH_DOMAIN, keccak256(name), keccak256(version),
chainId, verifyingContract))`, exposto pelo contrato em `domainSeparator()`.

## 2. Mensagem

```text
DeviceAuthorization(address wallet,bytes32 publicKeyHash,bytes32 attestationHash,uint64 deadline)
```

| Campo             | Valor                                                                   |
| ----------------- | ----------------------------------------------------------------------- |
| `wallet`          | carteira que vai enviar `registerDevice` (`msg.sender`)                 |
| `publicKeyHash`   | `keccak256(publicKey)`, chave de 65 bytes `0x04 ‖ X ‖ Y`                |
| `attestationHash` | `keccak256(abi.encode(attestationChain))`, `attestationChain : bytes[]` |
| `deadline`        | segundos Unix; o atestador usa `agora + 15 min` (FR-004)                |

`structHash = keccak256(abi.encode(TYPEHASH, wallet, publicKeyHash, attestationHash, deadline))`
e `digest = keccak256("\x19\x01" ‖ domainSeparator ‖ structHash)`.

## 3. Assinatura

- secp256k1, chave do atestador; formato `r ‖ s ‖ v` (65 bytes, `v ∈ {27, 28}`).
- `s ≤ n/2` (secp256k1), senão `InvalidAuthorization`.

## 4. Verificação no contrato (`registerDevice`)

1. `publicKey` com 65 bytes e prefixo `0x04`, senão `InvalidPublicKey`.
2. `attestationChain.length > 0`, senão `EmptyAttestation`.
3. `deadline ≥ block.timestamp`, senão `AuthorizationExpired(deadline)`.
4. `ecrecover(digest, v, r, s) == attester` com `wallet = msg.sender`, senão
   `InvalidAuthorization`. Autorização emitida para outra carteira, chave, cadeia, rede ou contrato
   resulta num assinante diferente e cai aqui.
5. chave ainda não vinculada, senão `DeviceAlreadyRegistered` (impede reuso da autorização).

## 5. Vetores de teste

`contracts/test/vectors/device-authorization.json` e
`android/attester/src/test/resources/device-authorization.json` DEVEM conter o mesmo vetor fixo:
chave do atestador de teste, domínio, mensagem, `structHash`, `digest` e assinatura, gerados com
`cast` (`cast wallet sign --data` sobre o JSON tipado) e conferidos no Foundry e no Kotlin.
