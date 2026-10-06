// SPDX-License-Identifier: MIT
pragma solidity ^0.8.24;

import {IImageRegistry} from "./IImageRegistry.sol";

/// @title ImageRegistry
/// @notice Registro público de imagens e da genealogia de suas versões (feature 002).
/// @dev A assinatura P-256 do aparelho é verificada off-chain (research R5); aqui só se exige
///      sua presença e que o aparelho pertença a `msg.sender` (R7). Ids começam em 1; 0 é
///      "inexistente". Getters de ids inexistentes devolvem structs vazias (id == 0), sem reverter.
contract ImageRegistry is IImageRegistry {
    uint256 private constant PUBLIC_KEY_LENGTH = 65;
    bytes1 private constant UNCOMPRESSED_KEY_PREFIX = 0x04;
    uint256 private constant MAX_OPERATIONS_LENGTH = 256;

    uint256 private _recordCount;
    uint32 private _deviceCount;

    mapping(uint256 id => ImageRecord) private _records;
    mapping(bytes32 sha256Hash => uint256 id) private _idBySha256;
    mapping(uint64 pHash => uint256[] ids) private _idsByPHash;
    mapping(uint256 id => uint256[] childIds) private _childrenOf;

    mapping(uint32 deviceId => Device) private _devices;
    mapping(address owner => uint32[] deviceIds) private _devicesOf;
    mapping(bytes32 publicKeyHash => uint32 deviceId) private _deviceIdByKey;

    // ───────────────────────────── Escrita ───────────────────────────

    /// @inheritdoc IImageRegistry
    function registerDevice(bytes calldata publicKey) external returns (uint32 deviceId) {
        if (publicKey.length != PUBLIC_KEY_LENGTH || publicKey[0] != UNCOMPRESSED_KEY_PREFIX) {
            revert InvalidPublicKey();
        }
        bytes32 keyHash = keccak256(publicKey);
        uint32 existing = _deviceIdByKey[keyHash];
        if (existing != 0) revert DeviceAlreadyRegistered(existing);

        deviceId = ++_deviceCount;
        _devices[deviceId] = Device({id: deviceId, owner: msg.sender, publicKey: publicKey, registeredAt: _now()});
        _devicesOf[msg.sender].push(deviceId);
        _deviceIdByKey[keyHash] = deviceId;

        emit DeviceRegistered(deviceId, msg.sender, publicKey);
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
        if (sigR == bytes32(0) || sigS == bytes32(0)) revert MissingSignature();

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

    /// @dev uint64 comporta `block.timestamp` por ~5,8e11 anos; o cast não trunca na prática.
    function _now() private view returns (uint64) {
        // forge-lint: disable-next-line(unsafe-typecast)
        return uint64(block.timestamp);
    }
}
