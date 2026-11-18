// SPDX-License-Identifier: MIT
pragma solidity ^0.8.24;

/// @title IImageRegistry (v2)
/// @notice Registro e rastreamento de imagens com origem exclusiva do HashLens genuíno (feature 020).
/// @dev Alterações em relação à v1 (feature 002), marcadas com [v2]:
///      - `registerDevice` exige autorização EIP-712 do atestador (`attester`, fixo no deploy) e
///        publica a cadeia de atestação no evento `DeviceAttested` (research R5, R10).
///      - `registerCapture` e `registerEdit` verificam a assinatura P-256 do aparelho on-chain pelo
///        pré-compilado P256VERIFY (EIP-7951, 0x100), com low-S (research R1).
///      - `MissingSignature` sai; entram os erros de autorização e de assinatura.
///      O restante (ids a partir de 1, 0 = inexistente, getters sem reverter) é igual à v1.
interface IImageRegistry {
    // ───────────────────────────── Tipos ─────────────────────────────

    struct Device {
        uint32 id;
        address owner;
        bytes publicKey; // 65 bytes, P-256 não comprimida (0x04 ‖ X ‖ Y)
        uint64 registeredAt;
        bytes32 attestationHash; // [v2] keccak256(abi.encode(attestationChain))
        uint64 registeredBlock; // [v2] bloco do evento DeviceAttested
    }

    struct ImageRecord {
        uint256 id;
        bytes32 sha256Hash;
        uint64 pHash;
        uint256 parentId; // 0 para originais
        uint256 originalId; // == id para originais
        address registrant; // autor (original) ou editor (versão) = msg.sender
        uint32 deviceId;
        bytes32 sigR;
        bytes32 sigS;
        uint64 timestamp;
        string operations; // "" para originais
    }

    // ───────────────────────────── Eventos ───────────────────────────

    event DeviceRegistered(uint32 indexed deviceId, address indexed owner, bytes publicKey);

    /// @notice [v2] Cadeia de atestação do vínculo, DER, da folha para a raiz (FR-006).
    event DeviceAttested(uint32 indexed deviceId, bytes[] attestationChain);

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
    error ParentNotFound(uint256 parentId);
    error InvalidOperations();

    error EmptyAttestation(); // [v2] cadeia vazia
    error AuthorizationExpired(uint64 deadline); // [v2] deadline < block.timestamp
    error InvalidAuthorization(); // [v2] assinatura malformada, s alto ou assinante ≠ attester
    error InvalidDeviceSignature(); // [v2] P-256 inválida ou s > n/2

    // ───────────────────────────── Escrita ───────────────────────────

    /// @notice [v2] Vincula uma chave de aparelho atestada à carteira chamadora.
    /// @param publicKey 65 bytes, P-256 não comprimida.
    /// @param attestationChain certificados DER da folha para a raiz, como entregues pelo Keystore.
    /// @param deadline prazo da autorização (segundos Unix).
    /// @param authorization assinatura EIP-712 do atestador, 65 bytes (r ‖ s ‖ v),
    ///        sobre DeviceAuthorization(msg.sender, keccak256(publicKey),
    ///        keccak256(abi.encode(attestationChain)), deadline). Ver device-authorization.md.
    /// @dev Reverte com InvalidPublicKey, EmptyAttestation, AuthorizationExpired,
    ///      InvalidAuthorization, DeviceAlreadyRegistered.
    function registerDevice(
        bytes calldata publicKey,
        bytes[] calldata attestationChain,
        uint64 deadline,
        bytes calldata authorization
    ) external returns (uint32 deviceId);

    /// @notice Registra uma captura original.
    /// @dev Reverte com InvalidHash, AlreadyRegistered, UnknownDevice, NotDeviceOwner,
    ///      InvalidDeviceSignature [v2].
    function registerCapture(bytes32 sha256Hash, uint64 pHash, uint32 deviceId, bytes32 sigR, bytes32 sigS)
        external
        returns (uint256 id);

    /// @notice Registra uma versão editada de um registro existente (de qualquer carteira).
    /// @dev originalId é herdado do pai. operations: 1..256 bytes (002 hashing-spec.md §4).
    ///      Reverte também com InvalidDeviceSignature [v2].
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

    /// @notice [v2] Endereço do atestador definido no deploy.
    function attester() external view returns (address);

    /// @notice [v2] Separador de domínio EIP-712 deste contrato.
    function domainSeparator() external view returns (bytes32);

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
