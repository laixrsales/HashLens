// SPDX-License-Identifier: MIT
pragma solidity ^0.8.24;

import {ImageRegistry} from "../../src/ImageRegistry.sol";
import {Test} from "forge-std/Test.sol";

/// @notice Auxiliares comuns aos testes da v2 (feature 020): atestador de teste que assina
///         autorizações EIP-712 (contracts/device-authorization.md) e aparelhos com chave P-256 que
///         assinam o payload v1 (002 contracts/signature-payload.md), como o app.
abstract contract RegistryTestBase is Test {
    ImageRegistry internal registry;

    /// Chave do atestador só de teste.
    uint256 internal attesterKey;
    address internal attesterAddress;

    uint256 internal constant P256_N = 0xFFFFFFFF00000000FFFFFFFFFFFFFFFFBCE6FAADA7179E84F3B9CAC2FC632551;
    uint256 internal constant SECP256K1_N = 0xFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEBAAEDCE6AF48A03BBFD25E8CD0364141;

    bytes32 internal constant DOMAIN_TYPEHASH =
        keccak256("EIP712Domain(string name,string version,uint256 chainId,address verifyingContract)");
    bytes32 internal constant AUTHORIZATION_TYPEHASH =
        keccak256("DeviceAuthorization(address wallet,bytes32 publicKeyHash,bytes32 attestationHash,uint64 deadline)");

    /// Prazo padrão das autorizações dos testes: 15 min depois do instante atual (FR-004).
    uint64 internal constant AUTH_WINDOW = 15 minutes;

    /// Chave P-256 privada de cada aparelho vinculado pelos auxiliares.
    mapping(uint32 deviceId => uint256 privateKey) internal deviceKeys;

    function _setUpAttester() internal {
        (attesterAddress, attesterKey) = makeAddrAndKey("attester");
    }

    function _deployRegistry() internal {
        registry = new ImageRegistry(attesterAddress);
    }

    // ───────────────────────────── Aparelho (P-256) ──────────────────

    /// Chave P-256 privada determinística, no intervalo [1, n − 1].
    function _p256Key(uint256 seed) internal pure returns (uint256) {
        return uint256(keccak256(abi.encode("p256", seed))) % (P256_N - 1) + 1;
    }

    /// Chave pública não comprimida (65 bytes, 0x04 ‖ X ‖ Y).
    function _p256Public(uint256 privateKey) internal pure returns (bytes memory) {
        (uint256 x, uint256 y) = vm.publicKeyP256(privateKey);
        return abi.encodePacked(bytes1(0x04), x, y);
    }

    /// Cadeia de atestação fictícia (o contrato só calcula o hash e publica os bytes).
    function _chain(uint256 seed) internal pure returns (bytes[] memory chain) {
        chain = new bytes[](3);
        chain[0] = abi.encodePacked("folha", seed);
        chain[1] = abi.encodePacked("intermediaria", seed);
        chain[2] = "raiz";
    }

    // ───────────────────────────── Atestador (EIP-712) ───────────────

    function _domainSeparator(uint256 chainId, address verifyingContract) internal pure returns (bytes32) {
        return keccak256(
            abi.encode(DOMAIN_TYPEHASH, keccak256("HashLens ImageRegistry"), keccak256("2"), chainId, verifyingContract)
        );
    }

    function _structHash(address wallet, bytes memory publicKey, bytes[] memory chain, uint64 deadline)
        internal
        pure
        returns (bytes32)
    {
        return keccak256(
            abi.encode(AUTHORIZATION_TYPEHASH, wallet, keccak256(publicKey), keccak256(abi.encode(chain)), deadline)
        );
    }

    /// Assinatura `r ‖ s ‖ v` de `key` sobre a autorização, no domínio informado.
    function _authorizeIn(
        uint256 key,
        bytes32 domainSeparator,
        address wallet,
        bytes memory publicKey,
        bytes[] memory chain,
        uint64 deadline
    ) internal pure returns (bytes memory) {
        bytes32 digest = keccak256(
            abi.encodePacked("\x19\x01", domainSeparator, _structHash(wallet, publicKey, chain, deadline))
        );
        (uint8 v, bytes32 r, bytes32 s) = vm.sign(key, digest);
        return abi.encodePacked(r, s, v);
    }

    /// Autorização do atestador de teste para este contrato e esta rede.
    function _authorize(address wallet, bytes memory publicKey, bytes[] memory chain, uint64 deadline)
        internal
        view
        returns (bytes memory)
    {
        return _authorizeIn(
            attesterKey, _domainSeparator(block.chainid, address(registry)), wallet, publicKey, chain, deadline
        );
    }

    /// Vincula a `owner` um aparelho com chave P-256 derivada de `seed`, autorizado pelo atestador.
    function _device(address owner, uint256 seed) internal returns (uint32 deviceId) {
        uint256 privateKey = _p256Key(seed);
        bytes memory publicKey = _p256Public(privateKey);
        bytes[] memory chain = _chain(seed);
        uint64 deadline = uint64(block.timestamp) + AUTH_WINDOW;
        bytes memory authorization = _authorize(owner, publicKey, chain, deadline);

        vm.prank(owner);
        deviceId = registry.registerDevice(publicKey, chain, deadline, authorization);
        deviceKeys[deviceId] = privateKey;
    }

    // ───────────────────────────── Payload v1 ────────────────────────

    /// Mensagem de 195 bytes de signature-payload.md §1.
    function _payload(
        address registrant,
        uint32 deviceId,
        bytes32 sha256Hash,
        uint64 pHash,
        uint256 parentId,
        string memory operations
    ) internal view returns (bytes memory) {
        return abi.encodePacked(
            "HASHLENS-IMG-v1",
            block.chainid,
            address(registry),
            registrant,
            deviceId,
            sha256Hash,
            pHash,
            parentId,
            sha256(bytes(operations))
        );
    }

    /// Assina o payload v1 com a chave `privateKey` e normaliza para low-S, como o app.
    function _signWith(
        uint256 privateKey,
        address registrant,
        uint32 deviceId,
        bytes32 sha256Hash,
        uint64 pHash,
        uint256 parentId,
        string memory operations
    ) internal view returns (bytes32 r, bytes32 s) {
        bytes32 digest = sha256(_payload(registrant, deviceId, sha256Hash, pHash, parentId, operations));
        (r, s) = vm.signP256(privateKey, digest);
        if (uint256(s) > P256_N / 2) s = bytes32(P256_N - uint256(s));
    }

    /// Assinatura do aparelho `deviceId` (vinculado por `_device`) sobre o payload v1.
    function _sign(
        address registrant,
        uint32 deviceId,
        bytes32 sha256Hash,
        uint64 pHash,
        uint256 parentId,
        string memory operations
    ) internal view returns (bytes32 r, bytes32 s) {
        return _signWith(deviceKeys[deviceId], registrant, deviceId, sha256Hash, pHash, parentId, operations);
    }
}
