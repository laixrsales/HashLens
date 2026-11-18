// SPDX-License-Identifier: MIT
pragma solidity ^0.8.24;

import {IImageRegistry} from "./IImageRegistry.sol";

/// @title ImageRegistry (v2)
/// @notice Registro público de imagens e da genealogia de suas versões, com origem exclusiva do
///         HashLens genuíno (features 002 e 020).
/// @dev O vínculo de um aparelho exige a autorização EIP-712 do atestador fixado no deploy
///      (020 research R5); a cadeia de atestação fica só no evento `DeviceAttested` (R10). Cada
///      registro traz a assinatura P-256 do aparelho sobre o payload v1 (002 signature-payload.md),
///      verificada aqui pelo pré-compilado P256VERIFY (EIP-7951, R1). Ids começam em 1; 0 é
///      "inexistente". Getters de ids inexistentes devolvem structs vazias (id == 0), sem reverter.
contract ImageRegistry is IImageRegistry {
    uint256 private constant PUBLIC_KEY_LENGTH = 65;
    bytes1 private constant UNCOMPRESSED_KEY_PREFIX = 0x04;
    uint256 private constant MAX_OPERATIONS_LENGTH = 256;
    uint256 private constant AUTHORIZATION_LENGTH = 65;

    /// n/2 de secp256k1 (assinatura do atestador) e de P-256 (assinatura do aparelho): low-S.
    uint256 private constant SECP256K1_HALF_N = 0x7FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF5D576E7357A4501DDFE92F46681B20A0;
    uint256 private constant P256_HALF_N = 0x7FFFFFFF800000007FFFFFFFFFFFFFFFDE737D56D38BCF4279DCE5617E3192A8;

    /// Pré-compilado P256VERIFY (EIP-7951): entrada `digest ‖ r ‖ s ‖ x ‖ y` (160 bytes), saída
    /// 32 bytes com valor 1 se válida e vazia se não.
    address private constant P256_VERIFY = address(0x100);

    bytes15 private constant SIGNATURE_DOMAIN = "HASHLENS-IMG-v1";

    bytes32 private constant DOMAIN_TYPEHASH =
        keccak256("EIP712Domain(string name,string version,uint256 chainId,address verifyingContract)");
    bytes32 private constant AUTHORIZATION_TYPEHASH =
        keccak256("DeviceAuthorization(address wallet,bytes32 publicKeyHash,bytes32 attestationHash,uint64 deadline)");

    /// @inheritdoc IImageRegistry
    address public immutable attester;

    /// @inheritdoc IImageRegistry
    bytes32 public immutable domainSeparator;

    uint256 private _recordCount;
    uint32 private _deviceCount;

    mapping(uint256 id => ImageRecord) private _records;
    mapping(bytes32 sha256Hash => uint256 id) private _idBySha256;
    mapping(uint64 pHash => uint256[] ids) private _idsByPHash;
    mapping(uint256 id => uint256[] childIds) private _childrenOf;

    mapping(uint32 deviceId => Device) private _devices;
    mapping(address owner => uint32[] deviceIds) private _devicesOf;
    mapping(bytes32 publicKeyHash => uint32 deviceId) private _deviceIdByKey;

    /// @param attester_ endereço da chave secp256k1 do atestador deste deploy. Trocar a chave exige
    ///        novo deploy (R5): não há função de administração.
    constructor(address attester_) {
        attester = attester_;
        domainSeparator = keccak256(
            abi.encode(
                DOMAIN_TYPEHASH, keccak256("HashLens ImageRegistry"), keccak256("2"), block.chainid, address(this)
            )
        );
    }

    // ───────────────────────────── Escrita ───────────────────────────

    /// @inheritdoc IImageRegistry
    function registerDevice(
        bytes calldata publicKey,
        bytes[] calldata attestationChain,
        uint64 deadline,
        bytes calldata authorization
    ) external returns (uint32 deviceId) {
        if (publicKey.length != PUBLIC_KEY_LENGTH || publicKey[0] != UNCOMPRESSED_KEY_PREFIX) {
            revert InvalidPublicKey();
        }
        if (attestationChain.length == 0) revert EmptyAttestation();
        if (deadline < block.timestamp) revert AuthorizationExpired(deadline);

        bytes32 keyHash = keccak256(publicKey);
        bytes32 attestationHash = keccak256(abi.encode(attestationChain));
        _checkAuthorization(keyHash, attestationHash, deadline, authorization);

        uint32 existing = _deviceIdByKey[keyHash];
        if (existing != 0) revert DeviceAlreadyRegistered(existing);

        deviceId = ++_deviceCount;
        _devices[deviceId] = Device({
            id: deviceId,
            owner: msg.sender,
            publicKey: publicKey,
            registeredAt: _now(),
            attestationHash: attestationHash,
            registeredBlock: _blockNumber()
        });
        _devicesOf[msg.sender].push(deviceId);
        _deviceIdByKey[keyHash] = deviceId;

        emit DeviceRegistered(deviceId, msg.sender, publicKey);
        emit DeviceAttested(deviceId, attestationChain);
    }

    /// @inheritdoc IImageRegistry
    function registerCapture(bytes32 sha256Hash, uint64 pHash, uint32 deviceId, bytes32 sigR, bytes32 sigS)
        external
        returns (uint256 id)
    {
        id = _register(0, 0, sha256Hash, pHash, deviceId, sigR, sigS, "");
    }

    /// @inheritdoc IImageRegistry
    function registerEdit(
        uint256 parentId,
        bytes32 sha256Hash,
        uint64 pHash,
        uint32 deviceId,
        bytes32 sigR,
        bytes32 sigS,
        string calldata operations
    ) external returns (uint256 id) {
        uint256 originalId = _records[parentId].originalId;
        if (originalId == 0) revert ParentNotFound(parentId);
        uint256 opsLength = bytes(operations).length;
        if (opsLength == 0 || opsLength > MAX_OPERATIONS_LENGTH) revert InvalidOperations();

        id = _register(parentId, originalId, sha256Hash, pHash, deviceId, sigR, sigS, operations);
        _childrenOf[parentId].push(id);
    }

    // ───────────────────────────── Leitura ───────────────────────────

    /// @inheritdoc IImageRegistry
    function getRecord(uint256 id) external view returns (ImageRecord memory) {
        return _records[id];
    }

    /// @inheritdoc IImageRegistry
    function getIdBySha256(bytes32 sha256Hash) external view returns (uint256) {
        return _idBySha256[sha256Hash];
    }

    /// @inheritdoc IImageRegistry
    function getIdsByPHash(uint64 pHash) external view returns (uint256[] memory) {
        return _idsByPHash[pHash];
    }

    /// @inheritdoc IImageRegistry
    function getChildren(uint256 id) external view returns (uint256[] memory) {
        return _childrenOf[id];
    }

    /// @inheritdoc IImageRegistry
    function getDevice(uint32 deviceId) external view returns (Device memory) {
        return _devices[deviceId];
    }

    /// @inheritdoc IImageRegistry
    function getDevicesOf(address owner) external view returns (uint32[] memory) {
        return _devicesOf[owner];
    }

    /// @inheritdoc IImageRegistry
    function totalRecords() external view returns (uint256) {
        return _recordCount;
    }

    // ───────────────────────────── Interno ───────────────────────────

    /// @dev device-authorization.md §4, passo 4: o atestador assinou esta carteira, esta chave, esta
    ///      cadeia e este prazo, neste contrato e nesta rede (o domínio). Qualquer campo trocado leva a
    ///      outro assinante recuperado.
    function _checkAuthorization(bytes32 keyHash, bytes32 attestationHash, uint64 deadline, bytes calldata auth)
        private
        view
    {
        if (auth.length != AUTHORIZATION_LENGTH) revert InvalidAuthorization();
        bytes32 r = bytes32(auth[0:32]);
        bytes32 s = bytes32(auth[32:64]);
        uint8 v = uint8(auth[64]);
        if (uint256(s) > SECP256K1_HALF_N) revert InvalidAuthorization();

        bytes32 structHash =
            keccak256(abi.encode(AUTHORIZATION_TYPEHASH, msg.sender, keyHash, attestationHash, deadline));
        bytes32 digest = keccak256(abi.encodePacked("\x19\x01", domainSeparator, structHash));
        address signer = ecrecover(digest, v, r, s);
        if (signer == address(0) || signer != attester) revert InvalidAuthorization();
    }

    /// @dev Validações comuns a capturas e edições. `originalId == 0` indica uma original,
    ///      cuja raiz é o próprio id.
    function _register(
        uint256 parentId,
        uint256 originalId,
        bytes32 sha256Hash,
        uint64 pHash,
        uint32 deviceId,
        bytes32 sigR,
        bytes32 sigS,
        string memory operations
    ) private returns (uint256 id) {
        if (sha256Hash == bytes32(0)) revert InvalidHash();
        uint256 existing = _idBySha256[sha256Hash];
        if (existing != 0) revert AlreadyRegistered(existing);
        address owner = _devices[deviceId].owner;
        if (owner == address(0)) revert UnknownDevice(deviceId);
        if (owner != msg.sender) revert NotDeviceOwner(deviceId, msg.sender);

        bytes32 digest = _payloadDigest(deviceId, sha256Hash, pHash, parentId, operations);
        _checkDeviceSignature(_devices[deviceId].publicKey, digest, sigR, sigS);

        id = ++_recordCount;
        if (originalId == 0) originalId = id;

        _records[id] = ImageRecord({
            id: id,
            sha256Hash: sha256Hash,
            pHash: pHash,
            parentId: parentId,
            originalId: originalId,
            registrant: msg.sender,
            deviceId: deviceId,
            sigR: sigR,
            sigS: sigS,
            timestamp: _now(),
            operations: operations
        });
        _idBySha256[sha256Hash] = id;
        _idsByPHash[pHash].push(id);

        emit ImageRegistered(id, sha256Hash, originalId, parentId, pHash, msg.sender, deviceId);
    }

    /// @dev SHA-256 da mensagem de 195 bytes de 002 signature-payload.md §1. A concatenação sem
    ///      separadores e em big-endian é exatamente o que `abi.encodePacked` produz.
    function _payloadDigest(
        uint32 deviceId,
        bytes32 sha256Hash,
        uint64 pHash,
        uint256 parentId,
        string memory operations
    ) private view returns (bytes32) {
        return sha256(
            abi.encodePacked(
                SIGNATURE_DOMAIN,
                block.chainid,
                address(this),
                msg.sender,
                deviceId,
                sha256Hash,
                pHash,
                parentId,
                sha256(bytes(operations))
            )
        );
    }

    /// @dev Assinatura `r ‖ s` da chave P-256 do aparelho sobre `digest`, só na forma low-S
    ///      (payload v1 §2). r = 0 ou s = 0 o próprio pré-compilado recusa.
    function _checkDeviceSignature(bytes storage publicKey, bytes32 digest, bytes32 r, bytes32 s) private view {
        if (uint256(s) > P256_HALF_N) revert InvalidDeviceSignature();
        bytes memory key = publicKey;
        bytes32 x;
        bytes32 y;
        assembly ("memory-safe") {
            // key: [tamanho 32 B][0x04][X 32 B][Y 32 B]
            x := mload(add(key, 33))
            y := mload(add(key, 65))
        }
        (bool ok, bytes memory output) = P256_VERIFY.staticcall(abi.encodePacked(digest, r, s, x, y));
        if (!ok || output.length != 32 || abi.decode(output, (uint256)) != 1) revert InvalidDeviceSignature();
    }

    /// @dev uint64 comporta `block.timestamp` por ~5,8e11 anos; o cast não trunca na prática.
    function _now() private view returns (uint64) {
        // forge-lint: disable-next-line(unsafe-typecast)
        return uint64(block.timestamp);
    }

    /// @dev uint64 comporta o número do bloco por bilhões de anos.
    function _blockNumber() private view returns (uint64) {
        // forge-lint: disable-next-line(unsafe-typecast)
        return uint64(block.number);
    }
}
