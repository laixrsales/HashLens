// SPDX-License-Identifier: MIT
pragma solidity ^0.8.24;

/// @title IImageRegistry
/// @notice Contrato público de registro e rastreamento de imagens (feature 001).
/// @dev Fonte da verdade da arquitetura. Ids iniciam em 1; o valor 0 representa "inexistente"
///      (equivalente a `parentId == null` no TCC). A assinatura P-256 do aparelho NÃO é
///      verificada on-chain (research R5); apenas sua presença e o vínculo deviceId ↔ msg.sender.
interface IImageRegistry {
    // ───────────────────────────── Tipos ─────────────────────────────

    struct Device {
        uint32 id;
        address owner;
        bytes publicKey;     // 65 bytes, P-256 não comprimida (0x04 ‖ X ‖ Y)
        uint64 registeredAt;
    }

    struct ImageRecord {
        uint256 id;
        bytes32 sha256Hash;
        uint64 pHash;
        uint256 parentId;    // 0 para originais
        uint256 originalId;  // == id para originais
        address registrant;  // autor (original) ou editor (versão) = msg.sender
        uint32 deviceId;
        bytes32 sigR;
        bytes32 sigS;
        uint64 timestamp;
        string operations;   // "" para originais
    }

    // ───────────────────────────── Eventos ───────────────────────────

    event DeviceRegistered(uint32 indexed deviceId, address indexed owner, bytes publicKey);

    event ImageRegistered(
        uint256 indexed id,
        bytes32 indexed sha256Hash,
        uint256 indexed originalId,
        uint256 parentId,
        uint64 pHash,
        address registrant,
        uint32 deviceId
    );

    // ───────────────────────────── Erros ─────────────────────────────

    error InvalidPublicKey();
    error DeviceAlreadyRegistered(uint32 existingDeviceId);
    error UnknownDevice(uint32 deviceId);
    error NotDeviceOwner(uint32 deviceId, address caller);
    error InvalidHash();
    error AlreadyRegistered(uint256 existingId);
    error MissingSignature();
    error ParentNotFound(uint256 parentId);
    error InvalidOperations();

    // ───────────────────────────── Escrita ───────────────────────────

    /// @notice Vincula uma chave pública de aparelho à carteira chamadora.
    /// @return deviceId novo identificador do aparelho.
    function registerDevice(bytes calldata publicKey) external returns (uint32 deviceId);

    /// @notice Registra uma captura original.
    /// @dev Reverte com InvalidHash, AlreadyRegistered, UnknownDevice, NotDeviceOwner, MissingSignature.
    function registerCapture(
        bytes32 sha256Hash,
        uint64 pHash,
        uint32 deviceId,
        bytes32 sigR,
        bytes32 sigS
    ) external returns (uint256 id);

    /// @notice Registra uma versão editada de um registro existente (de qualquer carteira).
    /// @dev originalId é herdado do pai. operations: 1..256 bytes, formato em hashing-spec.md §4.
    function registerEdit(
        uint256 parentId,
        bytes32 sha256Hash,
        uint64 pHash,
        uint32 deviceId,
        bytes32 sigR,
        bytes32 sigS,
        string calldata operations
    ) external returns (uint256 id);

    // ───────────────────────────── Leitura (sem gás) ─────────────────

    function getRecord(uint256 id) external view returns (ImageRecord memory);

    /// @return id do registro, ou 0 se não existir.
    function getIdBySha256(bytes32 sha256Hash) external view returns (uint256);

    /// @return ids de todos os registros com exatamente este pHash (pode ser vazio).
    function getIdsByPHash(uint64 pHash) external view returns (uint256[] memory);

    function getChildren(uint256 id) external view returns (uint256[] memory);

    function getDevice(uint32 deviceId) external view returns (Device memory);

    function getDevicesOf(address owner) external view returns (uint32[] memory);

    function totalRecords() external view returns (uint256);
}
