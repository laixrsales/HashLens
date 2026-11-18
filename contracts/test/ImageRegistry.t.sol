// SPDX-License-Identifier: MIT
pragma solidity ^0.8.24;

import {IImageRegistry} from "../src/IImageRegistry.sol";
import {ImageRegistry} from "../src/ImageRegistry.sol";
import {RegistryTestBase} from "./helpers/RegistryTestBase.sol";

contract ImageRegistryTest is RegistryTestBase {
    address internal alice = makeAddr("alice");
    address internal bob = makeAddr("bob");

    bytes32 internal constant SHA_A = keccak256("imagem-a");
    bytes32 internal constant SHA_B = keccak256("imagem-b");
    bytes32 internal constant SHA_C = keccak256("imagem-c");
    uint64 internal constant PHASH_A = 0x8f3ac1e07b52d496;
    uint64 internal constant PHASH_B = 0x8f3ac1e07b52d497;
    string internal constant OPS = "brightness:+30;grayscale";

    uint256 internal constant SEPOLIA = 11_155_111;
    uint64 internal constant NOW = 1_700_000_000;

    string internal constant AUTH_VECTOR = "test/vectors/device-authorization.json";
    string internal constant SIGNATURE_VECTORS = "../android/app/src/test/resources/signature/vectors.json";

    event DeviceRegistered(uint32 indexed deviceId, address indexed owner, bytes publicKey);
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

    function setUp() public {
        vm.warp(NOW);
        _setUpAttester();
        _deployRegistry();
    }

    // ───────────────────────────── Auxiliares ────────────────────────

    function _capture(address owner, uint32 deviceId, bytes32 sha, uint64 pHash) internal returns (uint256 id) {
        (bytes32 r, bytes32 s) = _sign(owner, deviceId, sha, pHash, 0, "");
        vm.prank(owner);
        id = registry.registerCapture(sha, pHash, deviceId, r, s);
    }

    function _edit(address editor, uint32 deviceId, uint256 parentId, bytes32 sha, string memory ops)
        internal
        returns (uint256 id)
    {
        (bytes32 r, bytes32 s) = _sign(editor, deviceId, sha, PHASH_B, parentId, ops);
        vm.prank(editor);
        id = registry.registerEdit(parentId, sha, PHASH_B, deviceId, r, s, ops);
    }

    function _repeat(bytes1 char, uint256 length) internal pure returns (string memory) {
        bytes memory out = new bytes(length);
        for (uint256 i = 0; i < length; i++) {
            out[i] = char;
        }
        return string(out);
    }

    /// Envia `registerDevice` como `wallet`, com prazo padrão e autorização do atestador.
    function _bind(address wallet, bytes memory key, bytes[] memory chain) internal returns (uint32) {
        uint64 deadline = NOW + AUTH_WINDOW;
        bytes memory authorization = _authorize(wallet, key, chain, deadline);
        vm.prank(wallet);
        return registry.registerDevice(key, chain, deadline, authorization);
    }

    function _expectBindRevert(
        address wallet,
        bytes memory key,
        bytes[] memory chain,
        uint64 deadline,
        bytes memory authorization,
        bytes memory revertData
    ) internal {
        vm.prank(wallet);
        vm.expectRevert(revertData);
        registry.registerDevice(key, chain, deadline, authorization);
    }

    function _expectInvalidAuthorization(
        address wallet,
        bytes memory key,
        bytes[] memory chain,
        uint64 deadline,
        bytes memory authorization
    ) internal {
        _expectBindRevert(
            wallet,
            key,
            chain,
            deadline,
            authorization,
            abi.encodeWithSelector(IImageRegistry.InvalidAuthorization.selector)
        );
    }

    function _hexToBytes(string memory hexString) internal pure returns (bytes memory) {
        return vm.parseBytes(string.concat("0x", hexString));
    }

    // ───────────────────────────── registerDevice v2 ─────────────────

    function test_registerDevice_valid_storesDeviceAndEmitsChain() public {
        bytes memory key = _p256Public(_p256Key(1));
        bytes[] memory chain = _chain(1);
        vm.roll(4242);

        vm.expectEmit(true, true, false, true, address(registry));
        emit DeviceRegistered(1, alice, key);
        vm.expectEmit(true, false, false, true, address(registry));
        emit DeviceAttested(1, chain);
        uint32 deviceId = _bind(alice, key, chain);

        assertEq(deviceId, 1);
        IImageRegistry.Device memory device = registry.getDevice(deviceId);
        assertEq(device.id, 1);
        assertEq(device.owner, alice);
        assertEq(device.publicKey, key);
        assertEq(device.registeredAt, NOW);
        assertEq(device.attestationHash, keccak256(abi.encode(chain)));
        assertEq(device.registeredBlock, 4242);
        assertEq(registry.getDevicesOf(alice)[0], deviceId);
    }

    function test_registerDevice_idsAreSequentialFromOne() public {
        assertEq(_device(alice, 1), 1);
        assertEq(_device(bob, 2), 2);
        assertEq(_device(alice, 3), 3);
    }

    function test_registerDevice_singleCertificateChain_isAccepted() public {
        bytes[] memory chain = new bytes[](1);
        chain[0] = hex"3082";
        uint32 deviceId = _bind(alice, _p256Public(_p256Key(1)), chain);
        assertEq(registry.getDevice(deviceId).attestationHash, keccak256(abi.encode(chain)));
    }

    function test_registerDevice_wrongLength_reverts() public {
        bytes memory shortKey = new bytes(64);
        shortKey[0] = 0x04;
        bytes memory longKey = abi.encodePacked(_p256Public(_p256Key(1)), bytes1(0x00));
        bytes[] memory chain = _chain(1);
        bytes memory err = abi.encodeWithSelector(IImageRegistry.InvalidPublicKey.selector);
        uint64 deadline = NOW + AUTH_WINDOW;

        _expectBindRevert(alice, shortKey, chain, deadline, _authorize(alice, shortKey, chain, deadline), err);
        _expectBindRevert(alice, longKey, chain, deadline, _authorize(alice, longKey, chain, deadline), err);
        _expectBindRevert(alice, "", chain, deadline, _authorize(alice, "", chain, deadline), err);
    }

    function test_registerDevice_wrongPrefix_reverts() public {
        bytes memory key = _p256Public(_p256Key(1));
        key[0] = 0x02; // formato comprimido não é aceito
        bytes[] memory chain = _chain(1);
        uint64 deadline = NOW + AUTH_WINDOW;
        _expectBindRevert(
            alice,
            key,
            chain,
            deadline,
            _authorize(alice, key, chain, deadline),
            abi.encodeWithSelector(IImageRegistry.InvalidPublicKey.selector)
        );
    }

    function test_registerDevice_emptyChain_reverts() public {
        bytes memory key = _p256Public(_p256Key(1));
        bytes[] memory chain = new bytes[](0);
        uint64 deadline = NOW + AUTH_WINDOW;
        _expectBindRevert(
            alice,
            key,
            chain,
            deadline,
            _authorize(alice, key, chain, deadline),
            abi.encodeWithSelector(IImageRegistry.EmptyAttestation.selector)
        );
    }

    function test_registerDevice_expiredAuthorization_reverts() public {
        bytes memory key = _p256Public(_p256Key(1));
        bytes[] memory chain = _chain(1);
        uint64 deadline = NOW - 1;
        _expectBindRevert(
            alice,
            key,
            chain,
            deadline,
            _authorize(alice, key, chain, deadline),
            abi.encodeWithSelector(IImageRegistry.AuthorizationExpired.selector, deadline)
        );
    }

    function test_registerDevice_deadlineEqualToNow_isAccepted() public {
        bytes memory key = _p256Public(_p256Key(1));
        bytes[] memory chain = _chain(1);
        bytes memory authorization = _authorize(alice, key, chain, NOW);
        vm.prank(alice);
        assertEq(registry.registerDevice(key, chain, NOW, authorization), 1);
    }

    function test_registerDevice_authorizationForOtherWallet_reverts() public {
        bytes memory key = _p256Public(_p256Key(1));
        bytes[] memory chain = _chain(1);
        uint64 deadline = NOW + AUTH_WINDOW;
        // Bob tenta usar a autorização emitida para Alice
        _expectInvalidAuthorization(bob, key, chain, deadline, _authorize(alice, key, chain, deadline));
    }

    function test_registerDevice_otherKey_reverts() public {
        bytes memory key = _p256Public(_p256Key(1));
        bytes memory otherKey = _p256Public(_p256Key(2));
        bytes[] memory chain = _chain(1);
        uint64 deadline = NOW + AUTH_WINDOW;
        _expectInvalidAuthorization(alice, otherKey, chain, deadline, _authorize(alice, key, chain, deadline));
    }

    function test_registerDevice_otherChain_reverts() public {
        bytes memory key = _p256Public(_p256Key(1));
        uint64 deadline = NOW + AUTH_WINDOW;
        bytes memory authorization = _authorize(alice, key, _chain(1), deadline);
        _expectInvalidAuthorization(alice, key, _chain(2), deadline, authorization);

        // Um certificado a mais no fim da cadeia também muda o hash
        bytes[] memory longer = new bytes[](4);
        bytes[] memory original = _chain(1);
        for (uint256 i = 0; i < 3; i++) {
            longer[i] = original[i];
        }
        longer[3] = "extra";
        _expectInvalidAuthorization(alice, key, longer, deadline, authorization);
    }

    function test_registerDevice_otherDeadline_reverts() public {
        bytes memory key = _p256Public(_p256Key(1));
        bytes[] memory chain = _chain(1);
        uint64 deadline = NOW + AUTH_WINDOW;
        // Estender o prazo invalida a assinatura
        _expectInvalidAuthorization(alice, key, chain, deadline + 1 hours, _authorize(alice, key, chain, deadline));
    }

    function test_registerDevice_authorizationForOtherNetwork_reverts() public {
        bytes memory key = _p256Public(_p256Key(1));
        bytes[] memory chain = _chain(1);
        uint64 deadline = NOW + AUTH_WINDOW;
        bytes memory authorization =
            _authorizeIn(attesterKey, _domainSeparator(1, address(registry)), alice, key, chain, deadline);
        _expectInvalidAuthorization(alice, key, chain, deadline, authorization);
    }

    function test_registerDevice_authorizationForOtherContract_reverts() public {
        bytes memory key = _p256Public(_p256Key(1));
        bytes[] memory chain = _chain(1);
        uint64 deadline = NOW + AUTH_WINDOW;
        ImageRegistry otherDeploy = new ImageRegistry(attesterAddress);
        bytes memory authorization = _authorizeIn(
            attesterKey, _domainSeparator(block.chainid, address(otherDeploy)), alice, key, chain, deadline
        );
        _expectInvalidAuthorization(alice, key, chain, deadline, authorization);
    }

    function test_registerDevice_signerIsNotAttester_reverts() public {
        bytes memory key = _p256Public(_p256Key(1));
        bytes[] memory chain = _chain(1);
        uint64 deadline = NOW + AUTH_WINDOW;
        (, uint256 intruderKey) = makeAddrAndKey("intruso");
        bytes memory authorization =
            _authorizeIn(intruderKey, _domainSeparator(block.chainid, address(registry)), alice, key, chain, deadline);
        _expectInvalidAuthorization(alice, key, chain, deadline, authorization);
    }

    function test_registerDevice_authorizationWithWrongLength_reverts() public {
        bytes memory key = _p256Public(_p256Key(1));
        bytes[] memory chain = _chain(1);
        uint64 deadline = NOW + AUTH_WINDOW;
        bytes memory authorization = _authorize(alice, key, chain, deadline);

        bytes memory short = new bytes(64);
        for (uint256 i = 0; i < 64; i++) {
            short[i] = authorization[i];
        }
        _expectInvalidAuthorization(alice, key, chain, deadline, short);
        _expectInvalidAuthorization(alice, key, chain, deadline, abi.encodePacked(authorization, bytes1(0x00)));
        _expectInvalidAuthorization(alice, key, chain, deadline, "");
    }

    function test_registerDevice_highS_reverts() public {
        bytes memory key = _p256Public(_p256Key(1));
        bytes[] memory chain = _chain(1);
        uint64 deadline = NOW + AUTH_WINDOW;
        bytes32 digest = keccak256(
            abi.encodePacked("\x19\x01", registry.domainSeparator(), _structHash(alice, key, chain, deadline))
        );
        (uint8 v, bytes32 r, bytes32 s) = vm.sign(attesterKey, digest);
        assertEq(ecrecover(digest, v, r, s), attesterAddress);

        // (r, n − s, v') é a mesma assinatura com s alto: o ecrecover recupera o atestador, mas o
        // contrato só aceita a forma low-S
        bytes32 highS = bytes32(SECP256K1_N - uint256(s));
        uint8 flippedV = v == 27 ? 28 : 27;
        assertEq(ecrecover(digest, flippedV, r, highS), attesterAddress);
        _expectInvalidAuthorization(alice, key, chain, deadline, abi.encodePacked(r, highS, flippedV));
    }

    function test_registerDevice_invalidV_reverts() public {
        bytes memory key = _p256Public(_p256Key(1));
        bytes[] memory chain = _chain(1);
        uint64 deadline = NOW + AUTH_WINDOW;
        bytes memory authorization = _authorize(alice, key, chain, deadline);
        authorization[64] = bytes1(uint8(0));
        _expectInvalidAuthorization(alice, key, chain, deadline, authorization);
    }

    function test_registerDevice_reusedAuthorization_reverts() public {
        bytes memory key = _p256Public(_p256Key(1));
        bytes[] memory chain = _chain(1);
        uint64 deadline = NOW + AUTH_WINDOW;
        bytes memory authorization = _authorize(alice, key, chain, deadline);
        vm.prank(alice);
        uint32 first = registry.registerDevice(key, chain, deadline, authorization);

        _expectBindRevert(
            alice,
            key,
            chain,
            deadline,
            authorization,
            abi.encodeWithSelector(IImageRegistry.DeviceAlreadyRegistered.selector, first)
        );
    }

    function test_registerDevice_sameKeyOtherWallet_reverts() public {
        bytes memory key = _p256Public(_p256Key(1));
        uint32 first = _bind(alice, key, _chain(1));

        // Mesmo com autorização própria, Bob não vincula uma chave que já é de Alice
        bytes[] memory chain = _chain(1);
        uint64 deadline = NOW + AUTH_WINDOW;
        _expectBindRevert(
            bob,
            key,
            chain,
            deadline,
            _authorize(bob, key, chain, deadline),
            abi.encodeWithSelector(IImageRegistry.DeviceAlreadyRegistered.selector, first)
        );
    }

    function test_registerDevice_sameWalletTwoKeys_listsBoth() public {
        uint32 first = _device(alice, 1);
        uint32 second = _device(alice, 2);

        uint32[] memory devices = registry.getDevicesOf(alice);
        assertEq(devices.length, 2);
        assertEq(devices[0], first);
        assertEq(devices[1], second);
        assertEq(registry.getDevicesOf(bob).length, 0);
    }

    function test_attesterAndDomainSeparator() public view {
        assertEq(registry.attester(), attesterAddress);
        assertEq(registry.domainSeparator(), _domainSeparator(block.chainid, address(registry)));
    }

    struct AuthVector {
        address attester;
        uint256 attesterKey;
        uint256 chainId;
        address verifyingContract;
        bytes publicKey;
        bytes[] chain;
        address wallet;
        uint64 deadline;
        bytes signature;
    }

    function _authVector() internal view returns (AuthVector memory v, string memory json) {
        json = vm.readFile(AUTH_VECTOR);
        v.attester = vm.parseJsonAddress(json, ".attester.address");
        v.attesterKey = vm.parseJsonUint(json, ".attester.privateKey");
        v.chainId = vm.parseJsonUint(json, ".domain.chainId");
        v.verifyingContract = vm.parseJsonAddress(json, ".domain.verifyingContract");
        v.publicKey = vm.parseJsonBytes(json, ".input.publicKey");
        v.chain = vm.parseJsonBytesArray(json, ".input.attestationChain");
        v.wallet = vm.parseJsonAddress(json, ".message.wallet");
        v.deadline = uint64(vm.parseJsonUint(json, ".message.deadline"));
        v.signature = vm.parseJsonBytes(json, ".signature");
    }

    /// Implanta o contrato no endereço e na rede do vetor e vincula a chave do vetor com a
    /// autorização gerada pelo `cast`.
    function _bindVectorDevice(AuthVector memory v) internal returns (IImageRegistry vectorRegistry, uint32 deviceId) {
        vm.chainId(v.chainId);
        deployCodeTo("ImageRegistry.sol:ImageRegistry", abi.encode(v.attester), v.verifyingContract);
        vectorRegistry = IImageRegistry(v.verifyingContract);
        vm.warp(v.deadline - 60);
        vm.prank(v.wallet);
        deviceId = vectorRegistry.registerDevice(v.publicKey, v.chain, v.deadline, v.signature);
    }

    /// O contrato aceita a autorização do vetor fixo (gerado com `cast`, o mesmo do atestador
    /// Kotlin): domínio, structHash e digest calculados igual nos três lados.
    function test_registerDevice_authorizationVector_matchesCast() public {
        (AuthVector memory v, string memory json) = _authVector();
        bytes32 structHash = _structHash(v.wallet, v.publicKey, v.chain, v.deadline);

        assertEq(vm.addr(v.attesterKey), v.attester);
        assertEq(keccak256(v.publicKey), vm.parseJsonBytes32(json, ".message.publicKeyHash"));
        assertEq(keccak256(abi.encode(v.chain)), vm.parseJsonBytes32(json, ".message.attestationHash"));
        assertEq(AUTHORIZATION_TYPEHASH, vm.parseJsonBytes32(json, ".typeHash"));
        assertEq(structHash, vm.parseJsonBytes32(json, ".structHash"));

        (IImageRegistry vectorRegistry, uint32 deviceId) = _bindVectorDevice(v);
        bytes32 separator = vectorRegistry.domainSeparator();
        assertEq(separator, vm.parseJsonBytes32(json, ".domainSeparator"));
        assertEq(keccak256(abi.encodePacked("\x19\x01", separator, structHash)), vm.parseJsonBytes32(json, ".digest"));
        assertEq(vectorRegistry.getDevice(deviceId).owner, v.wallet);
        assertEq(vectorRegistry.attester(), v.attester);
    }

    // ───────────────────────────── registerCapture ───────────────────

    function test_registerCapture_success_storesOriginalAndEmits() public {
        uint32 deviceId = _device(alice, 1);
        vm.warp(NOW + 100);
        (bytes32 r, bytes32 s) = _sign(alice, deviceId, SHA_A, PHASH_A, 0, "");

        vm.expectEmit(true, true, true, true, address(registry));
        emit ImageRegistered(1, SHA_A, 1, 0, PHASH_A, alice, deviceId);
        vm.prank(alice);
        uint256 id = registry.registerCapture(SHA_A, PHASH_A, deviceId, r, s);

        assertEq(id, 1);
        IImageRegistry.ImageRecord memory rec = registry.getRecord(id);
        assertEq(rec.id, 1);
        assertEq(rec.sha256Hash, SHA_A);
        assertEq(rec.pHash, PHASH_A);
        assertEq(rec.parentId, 0);
        assertEq(rec.originalId, 1);
        assertEq(rec.registrant, alice);
        assertEq(rec.deviceId, deviceId);
        assertEq(rec.sigR, r);
        assertEq(rec.sigS, s);
        assertEq(rec.timestamp, NOW + 100);
        assertEq(rec.operations, "");

        assertEq(registry.getIdBySha256(SHA_A), 1);
        assertEq(registry.totalRecords(), 1);
    }

    function test_registerCapture_zeroSha_reverts() public {
        uint32 deviceId = _device(alice, 1);
        (bytes32 r, bytes32 s) = _sign(alice, deviceId, bytes32(0), PHASH_A, 0, "");
        vm.prank(alice);
        vm.expectRevert(IImageRegistry.InvalidHash.selector);
        registry.registerCapture(bytes32(0), PHASH_A, deviceId, r, s);
    }

    function test_registerCapture_duplicateSha_reverts() public {
        uint32 deviceId = _device(alice, 1);
        uint256 first = _capture(alice, deviceId, SHA_A, PHASH_A);
        (bytes32 r, bytes32 s) = _sign(alice, deviceId, SHA_A, PHASH_B, 0, "");

        vm.prank(alice);
        vm.expectRevert(abi.encodeWithSelector(IImageRegistry.AlreadyRegistered.selector, first));
        registry.registerCapture(SHA_A, PHASH_B, deviceId, r, s);
    }

    function test_registerCapture_duplicateShaFromOtherWallet_reverts() public {
        uint256 first = _capture(alice, _device(alice, 1), SHA_A, PHASH_A);
        uint32 bobDevice = _device(bob, 2);
        (bytes32 r, bytes32 s) = _sign(bob, bobDevice, SHA_A, PHASH_A, 0, "");

        vm.prank(bob);
        vm.expectRevert(abi.encodeWithSelector(IImageRegistry.AlreadyRegistered.selector, first));
        registry.registerCapture(SHA_A, PHASH_A, bobDevice, r, s);
    }

    function test_registerCapture_unknownDevice_reverts() public {
        vm.prank(alice);
        vm.expectRevert(abi.encodeWithSelector(IImageRegistry.UnknownDevice.selector, uint32(7)));
        registry.registerCapture(SHA_A, PHASH_A, 7, bytes32(uint256(1)), bytes32(uint256(1)));
    }

    function test_registerCapture_deviceZero_reverts() public {
        vm.prank(alice);
        vm.expectRevert(abi.encodeWithSelector(IImageRegistry.UnknownDevice.selector, uint32(0)));
        registry.registerCapture(SHA_A, PHASH_A, 0, bytes32(uint256(1)), bytes32(uint256(1)));
    }

    function test_registerCapture_deviceOfOtherWallet_reverts() public {
        uint32 aliceDevice = _device(alice, 1);
        (bytes32 r, bytes32 s) = _sign(bob, aliceDevice, SHA_A, PHASH_A, 0, "");
        vm.prank(bob);
        vm.expectRevert(abi.encodeWithSelector(IImageRegistry.NotDeviceOwner.selector, aliceDevice, bob));
        registry.registerCapture(SHA_A, PHASH_A, aliceDevice, r, s);
    }

    function test_registerCapture_zeroPHash_isAccepted() public {
        uint256 id = _capture(alice, _device(alice, 1), SHA_A, 0);
        assertEq(registry.getRecord(id).pHash, 0);
        assertEq(registry.getIdsByPHash(0).length, 1);
    }

    function test_registerCapture_withOlderDeviceOfSameWallet_isAccepted() public {
        uint32 oldDevice = _device(alice, 1);
        _device(alice, 2); // nova chave após perda da antiga (R25); o vínculo antigo continua válido
        uint256 id = _capture(alice, oldDevice, SHA_A, PHASH_A);
        assertEq(registry.getRecord(id).deviceId, oldDevice);
    }

    // ───────────────────────────── Assinatura P-256 (T011) ───────────

    function _expectInvalidCapture(uint32 deviceId, bytes32 sha, uint64 pHash, bytes32 r, bytes32 s) internal {
        vm.prank(alice);
        vm.expectRevert(IImageRegistry.InvalidDeviceSignature.selector);
        registry.registerCapture(sha, pHash, deviceId, r, s);
    }

    function test_registerCapture_signatureOfOtherKey_reverts() public {
        uint32 deviceId = _device(alice, 1);
        (bytes32 r, bytes32 s) = _signWith(_p256Key(99), alice, deviceId, SHA_A, PHASH_A, 0, "");
        _expectInvalidCapture(deviceId, SHA_A, PHASH_A, r, s);
    }

    function test_registerCapture_signatureOfOtherDeviceOfSameWallet_reverts() public {
        uint32 first = _device(alice, 1);
        uint32 second = _device(alice, 2);
        // Assinado pela chave do aparelho 1, enviado como aparelho 2
        (bytes32 r, bytes32 s) = _sign(alice, first, SHA_A, PHASH_A, 0, "");
        _expectInvalidCapture(second, SHA_A, PHASH_A, r, s);
        // Chave do aparelho 2, mas o payload diz aparelho 1
        (r, s) = _signWith(deviceKeys[second], alice, first, SHA_A, PHASH_A, 0, "");
        _expectInvalidCapture(second, SHA_A, PHASH_A, r, s);
    }

    function test_registerCapture_alteredSha_reverts() public {
        uint32 deviceId = _device(alice, 1);
        (bytes32 r, bytes32 s) = _sign(alice, deviceId, SHA_A, PHASH_A, 0, "");
        _expectInvalidCapture(deviceId, SHA_B, PHASH_A, r, s);
    }

    function test_registerCapture_alteredPHash_reverts() public {
        uint32 deviceId = _device(alice, 1);
        (bytes32 r, bytes32 s) = _sign(alice, deviceId, SHA_A, PHASH_A, 0, "");
        _expectInvalidCapture(deviceId, SHA_A, PHASH_B, r, s);
    }

    function test_registerCapture_signedForOtherWallet_reverts() public {
        uint32 deviceId = _device(alice, 1);
        (bytes32 r, bytes32 s) = _sign(bob, deviceId, SHA_A, PHASH_A, 0, "");
        _expectInvalidCapture(deviceId, SHA_A, PHASH_A, r, s);
    }

    function test_registerCapture_signedAsEdit_reverts() public {
        uint32 deviceId = _device(alice, 1);
        // Assinatura de edição (parentId e operations) não serve para uma captura
        (bytes32 r, bytes32 s) = _sign(alice, deviceId, SHA_A, PHASH_A, 1, OPS);
        _expectInvalidCapture(deviceId, SHA_A, PHASH_A, r, s);
    }

    function test_registerCapture_highS_reverts() public {
        uint32 deviceId = _device(alice, 1);
        (bytes32 r, bytes32 s) = _sign(alice, deviceId, SHA_A, PHASH_A, 0, "");
        // (r, n − s) também é válida para P-256; o contrato só aceita a forma low-S (payload v1 §2)
        _expectInvalidCapture(deviceId, SHA_A, PHASH_A, r, bytes32(P256_N - uint256(s)));
    }

    function test_registerCapture_zeroSignature_reverts() public {
        uint32 deviceId = _device(alice, 1);
        _expectInvalidCapture(deviceId, SHA_A, PHASH_A, bytes32(0), bytes32(0));
        (bytes32 r, bytes32 s) = _sign(alice, deviceId, SHA_A, PHASH_A, 0, "");
        _expectInvalidCapture(deviceId, SHA_A, PHASH_A, bytes32(0), s);
        _expectInvalidCapture(deviceId, SHA_A, PHASH_A, r, bytes32(0));
    }

    function test_registerCapture_signatureFromOtherNetwork_reverts() public {
        uint32 deviceId = _device(alice, 1);
        uint256 currentChain = block.chainid;
        vm.chainId(1);
        (bytes32 r, bytes32 s) = _sign(alice, deviceId, SHA_A, PHASH_A, 0, "");
        vm.chainId(currentChain);
        _expectInvalidCapture(deviceId, SHA_A, PHASH_A, r, s);
    }

    function test_registerEdit_alteredFields_revert() public {
        uint32 deviceId = _device(alice, 1);
        uint256 original = _capture(alice, deviceId, SHA_A, PHASH_A);
        uint256 otherOriginal = _capture(alice, deviceId, SHA_C, PHASH_A);
        (bytes32 r, bytes32 s) = _sign(alice, deviceId, SHA_B, PHASH_B, original, OPS);

        // parentId trocado
        vm.prank(alice);
        vm.expectRevert(IImageRegistry.InvalidDeviceSignature.selector);
        registry.registerEdit(otherOriginal, SHA_B, PHASH_B, deviceId, r, s, OPS);

        // operations trocadas
        vm.prank(alice);
        vm.expectRevert(IImageRegistry.InvalidDeviceSignature.selector);
        registry.registerEdit(original, SHA_B, PHASH_B, deviceId, r, s, "grayscale");

        // sha e pHash trocados
        vm.prank(alice);
        vm.expectRevert(IImageRegistry.InvalidDeviceSignature.selector);
        registry.registerEdit(original, keccak256("outra"), PHASH_B, deviceId, r, s, OPS);
        vm.prank(alice);
        vm.expectRevert(IImageRegistry.InvalidDeviceSignature.selector);
        registry.registerEdit(original, SHA_B, PHASH_A, deviceId, r, s, OPS);

        // a assinatura certa continua aceita
        vm.prank(alice);
        registry.registerEdit(original, SHA_B, PHASH_B, deviceId, r, s, OPS);
    }

    function test_registerEdit_highS_reverts() public {
        uint32 deviceId = _device(alice, 1);
        uint256 original = _capture(alice, deviceId, SHA_A, PHASH_A);
        (bytes32 r, bytes32 s) = _sign(alice, deviceId, SHA_B, PHASH_B, original, OPS);
        vm.prank(alice);
        vm.expectRevert(IImageRegistry.InvalidDeviceSignature.selector);
        registry.registerEdit(original, SHA_B, PHASH_B, deviceId, r, bytes32(P256_N - uint256(s)), OPS);
    }

    function test_registerEdit_zeroSignature_reverts() public {
        uint32 deviceId = _device(alice, 1);
        uint256 original = _capture(alice, deviceId, SHA_A, PHASH_A);
        vm.prank(alice);
        vm.expectRevert(IImageRegistry.InvalidDeviceSignature.selector);
        registry.registerEdit(original, SHA_B, PHASH_B, deviceId, bytes32(0), bytes32(0), OPS);
    }

    /// Vetores do app (002 signature-payload §4, gerados por tools/signature/gerar_vetores.py): o
    /// contrato monta o mesmo payload de 195 bytes e aceita a assinatura que o app produziu.
    function test_signatureVectorsFromApp_areAccepted() public {
        string memory sigJson = vm.readFile(SIGNATURE_VECTORS);
        (AuthVector memory v,) = _authVector();
        // O vetor de autorização usa a mesma chave pública, carteira, contrato e rede
        assertEq(_hexToBytes(vm.parseJsonString(sigJson, ".publicKey")), v.publicKey);
        assertEq(vm.parseJsonAddress(sigJson, ".casos[0].campos.contract"), v.verifyingContract);
        assertEq(vm.parseJsonAddress(sigJson, ".casos[0].campos.registrant"), v.wallet);
        assertEq(vm.parseJsonUint(sigJson, ".casos[0].campos.chainId"), v.chainId);

        (IImageRegistry vectorRegistry, uint32 deviceId) = _bindVectorDevice(v);
        assertEq(deviceId, vm.parseJsonUint(sigJson, ".casos[0].campos.deviceId"));

        for (uint256 i = 0; i < 2; i++) {
            _registerSignatureCase(vectorRegistry, sigJson, string.concat(".casos[", vm.toString(i), "]"), v.wallet);
        }
        assertEq(vectorRegistry.totalRecords(), 2);
    }

    function _registerSignatureCase(IImageRegistry vectorRegistry, string memory json, string memory c, address wallet)
        internal
    {
        (bytes32 r, bytes32 s) =
            abi.decode(_hexToBytes(vm.parseJsonString(json, string.concat(c, ".assinaturaRaw"))), (bytes32, bytes32));
        bytes32 sha = bytes32(_hexToBytes(vm.parseJsonString(json, string.concat(c, ".campos.sha256"))));
        uint64 pHash =
            uint64(vm.parseUint(string.concat("0x", vm.parseJsonString(json, string.concat(c, ".campos.pHash")))));
        uint256 parentId = vm.parseJsonUint(json, string.concat(c, ".campos.parentId"));
        uint32 deviceId = uint32(vm.parseJsonUint(json, string.concat(c, ".campos.deviceId")));
        string memory ops = vm.parseJsonString(json, string.concat(c, ".campos.operations"));

        vm.prank(wallet);
        uint256 id = parentId == 0
            ? vectorRegistry.registerCapture(sha, pHash, deviceId, r, s)
            : vectorRegistry.registerEdit(parentId, sha, pHash, deviceId, r, s, ops);
        assertEq(vectorRegistry.getRecord(id).sha256Hash, sha);
    }

    // ───────────────────────────── registerEdit ──────────────────────

    function test_registerEdit_success_linksToParentAndEmits() public {
        uint32 deviceId = _device(alice, 1);
        uint256 original = _capture(alice, deviceId, SHA_A, PHASH_A);
        vm.warp(NOW + 200);

        vm.expectEmit(true, true, true, true, address(registry));
        emit ImageRegistered(2, SHA_B, original, original, PHASH_B, alice, deviceId);
        uint256 id = _edit(alice, deviceId, original, SHA_B, OPS);

        assertEq(id, 2);
        IImageRegistry.ImageRecord memory r = registry.getRecord(id);
        assertEq(r.parentId, original);
        assertEq(r.originalId, original);
        assertEq(r.registrant, alice);
        assertEq(r.sha256Hash, SHA_B);
        assertEq(r.pHash, PHASH_B);
        assertEq(r.operations, OPS);
        assertEq(r.timestamp, NOW + 200);

        uint256[] memory children = registry.getChildren(original);
        assertEq(children.length, 1);
        assertEq(children[0], id);
        assertEq(registry.getIdBySha256(SHA_B), id);
    }

    function test_registerEdit_inheritsOriginalIdAcrossGenerations() public {
        uint32 deviceId = _device(alice, 1);
        uint256 original = _capture(alice, deviceId, SHA_A, PHASH_A);
        uint256 child = _edit(alice, deviceId, original, SHA_B, OPS);
        uint256 grandchild = _edit(alice, deviceId, child, SHA_C, "rotate90");

        IImageRegistry.ImageRecord memory r = registry.getRecord(grandchild);
        assertEq(r.parentId, child);
        assertEq(r.originalId, original);
        assertEq(registry.getChildren(child)[0], grandchild);
    }

    function test_registerEdit_branches_listAllChildren() public {
        uint32 deviceId = _device(alice, 1);
        uint256 original = _capture(alice, deviceId, SHA_A, PHASH_A);
        uint256 first = _edit(alice, deviceId, original, SHA_B, OPS);
        uint256 second = _edit(alice, deviceId, original, SHA_C, "grayscale");

        uint256[] memory children = registry.getChildren(original);
        assertEq(children.length, 2);
        assertEq(children[0], first);
        assertEq(children[1], second);
    }

    function test_registerEdit_parentNotFound_reverts() public {
        uint32 deviceId = _device(alice, 1);
        (bytes32 r, bytes32 s) = _sign(alice, deviceId, SHA_B, PHASH_B, 42, OPS);
        vm.prank(alice);
        vm.expectRevert(abi.encodeWithSelector(IImageRegistry.ParentNotFound.selector, uint256(42)));
        registry.registerEdit(42, SHA_B, PHASH_B, deviceId, r, s, OPS);
    }

    function test_registerEdit_parentZero_reverts() public {
        uint32 deviceId = _device(alice, 1);
        (bytes32 r, bytes32 s) = _sign(alice, deviceId, SHA_B, PHASH_B, 0, OPS);
        vm.prank(alice);
        vm.expectRevert(abi.encodeWithSelector(IImageRegistry.ParentNotFound.selector, uint256(0)));
        registry.registerEdit(0, SHA_B, PHASH_B, deviceId, r, s, OPS);
    }

    function test_registerEdit_byThirdParty_setsRegistrantToEditor() public {
        uint256 original = _capture(alice, _device(alice, 1), SHA_A, PHASH_A);
        uint32 bobDevice = _device(bob, 2);

        // O evento identifica Bob como responsável pela edição, com o aparelho dele
        (bytes32 sigR, bytes32 sigS) = _sign(bob, bobDevice, SHA_B, PHASH_B, original, OPS);
        vm.expectEmit(true, true, true, true, address(registry));
        emit ImageRegistered(2, SHA_B, original, original, PHASH_B, bob, bobDevice);
        vm.prank(bob);
        uint256 id = registry.registerEdit(original, SHA_B, PHASH_B, bobDevice, sigR, sigS, OPS);

        IImageRegistry.ImageRecord memory r = registry.getRecord(id);
        assertEq(r.registrant, bob);
        assertEq(r.deviceId, bobDevice);
        assertEq(r.parentId, original);
        assertEq(r.originalId, original);
        assertEq(r.operations, OPS);
        // A original continua de Alice e passa a listar a edição de Bob
        assertEq(registry.getRecord(original).registrant, alice);
        assertEq(registry.getChildren(original)[0], id);
    }

    /// Genealogia mista (checkpoint US4): Bob edita a original de Alice e Alice edita a versão de Bob.
    function test_registerEdit_mixedEditors_keepEachRegistrantAndOriginal() public {
        uint32 aliceDevice = _device(alice, 1);
        uint32 bobDevice = _device(bob, 2);
        uint256 original = _capture(alice, aliceDevice, SHA_A, PHASH_A);

        uint256 bobVersion = _edit(bob, bobDevice, original, SHA_B, OPS);
        uint256 aliceVersion = _edit(alice, aliceDevice, bobVersion, SHA_C, "rotate90:1");

        IImageRegistry.ImageRecord memory byBob = registry.getRecord(bobVersion);
        IImageRegistry.ImageRecord memory byAlice = registry.getRecord(aliceVersion);
        assertEq(byBob.registrant, bob);
        assertEq(byAlice.registrant, alice);
        assertEq(byAlice.deviceId, aliceDevice);
        assertEq(byAlice.parentId, bobVersion);
        assertEq(byAlice.originalId, original);
        assertEq(registry.getChildren(bobVersion)[0], aliceVersion);
    }

    function test_registerEdit_deviceOfOtherWallet_reverts() public {
        uint32 aliceDevice = _device(alice, 1);
        uint256 original = _capture(alice, aliceDevice, SHA_A, PHASH_A);
        (bytes32 r, bytes32 s) = _sign(bob, aliceDevice, SHA_B, PHASH_B, original, OPS);
        vm.prank(bob);
        vm.expectRevert(abi.encodeWithSelector(IImageRegistry.NotDeviceOwner.selector, aliceDevice, bob));
        registry.registerEdit(original, SHA_B, PHASH_B, aliceDevice, r, s, OPS);
    }

    function test_registerEdit_duplicateSha_reverts() public {
        uint32 deviceId = _device(alice, 1);
        uint256 original = _capture(alice, deviceId, SHA_A, PHASH_A);
        (bytes32 r, bytes32 s) = _sign(alice, deviceId, SHA_A, PHASH_B, original, OPS);
        vm.prank(alice);
        vm.expectRevert(abi.encodeWithSelector(IImageRegistry.AlreadyRegistered.selector, original));
        registry.registerEdit(original, SHA_A, PHASH_B, deviceId, r, s, OPS);
    }

    function test_registerEdit_zeroSha_reverts() public {
        uint32 deviceId = _device(alice, 1);
        uint256 original = _capture(alice, deviceId, SHA_A, PHASH_A);
        (bytes32 r, bytes32 s) = _sign(alice, deviceId, bytes32(0), PHASH_B, original, OPS);
        vm.prank(alice);
        vm.expectRevert(IImageRegistry.InvalidHash.selector);
        registry.registerEdit(original, bytes32(0), PHASH_B, deviceId, r, s, OPS);
    }

    function test_registerEdit_emptyOperations_reverts() public {
        uint32 deviceId = _device(alice, 1);
        uint256 original = _capture(alice, deviceId, SHA_A, PHASH_A);
        (bytes32 r, bytes32 s) = _sign(alice, deviceId, SHA_B, PHASH_B, original, "");
        vm.prank(alice);
        vm.expectRevert(IImageRegistry.InvalidOperations.selector);
        registry.registerEdit(original, SHA_B, PHASH_B, deviceId, r, s, "");
    }

    function test_registerEdit_operations256Bytes_isAccepted() public {
        uint32 deviceId = _device(alice, 1);
        uint256 original = _capture(alice, deviceId, SHA_A, PHASH_A);
        string memory ops = _repeat("a", 256);
        uint256 id = _edit(alice, deviceId, original, SHA_B, ops);
        assertEq(registry.getRecord(id).operations, ops);
    }

    function test_registerEdit_operations257Bytes_reverts() public {
        uint32 deviceId = _device(alice, 1);
        uint256 original = _capture(alice, deviceId, SHA_A, PHASH_A);
        string memory ops = _repeat("a", 257);
        (bytes32 r, bytes32 s) = _sign(alice, deviceId, SHA_B, PHASH_B, original, ops);
        vm.prank(alice);
        vm.expectRevert(IImageRegistry.InvalidOperations.selector);
        registry.registerEdit(original, SHA_B, PHASH_B, deviceId, r, s, ops);
    }

    function test_registerEdit_operationsLimitCountsUtf8Bytes() public {
        uint32 deviceId = _device(alice, 1);
        uint256 original = _capture(alice, deviceId, SHA_A, PHASH_A);
        // 129 caracteres "ç" = 258 bytes UTF-8: acima do limite mesmo com menos de 256 caracteres
        bytes memory ops = new bytes(258);
        for (uint256 i = 0; i < 258; i += 2) {
            ops[i] = 0xc3;
            ops[i + 1] = 0xa7;
        }
        (bytes32 r, bytes32 s) = _sign(alice, deviceId, SHA_B, PHASH_B, original, string(ops));
        vm.prank(alice);
        vm.expectRevert(IImageRegistry.InvalidOperations.selector);
        registry.registerEdit(original, SHA_B, PHASH_B, deviceId, r, s, string(ops));
    }

    // ───────────────────────────── Leitura ───────────────────────────

    function test_getters_emptyRegistry() public view {
        assertEq(registry.totalRecords(), 0);
        assertEq(registry.getIdBySha256(SHA_A), 0);
        assertEq(registry.getIdsByPHash(PHASH_A).length, 0);
        assertEq(registry.getChildren(1).length, 0);
        assertEq(registry.getDevicesOf(alice).length, 0);
    }

    function test_getRecord_nonexistent_returnsEmpty() public view {
        IImageRegistry.ImageRecord memory r = registry.getRecord(99);
        assertEq(r.id, 0);
        assertEq(r.sha256Hash, bytes32(0));
        assertEq(r.registrant, address(0));
    }

    function test_getDevice_nonexistent_returnsEmpty() public view {
        IImageRegistry.Device memory d = registry.getDevice(5);
        assertEq(d.id, 0);
        assertEq(d.owner, address(0));
        assertEq(d.publicKey.length, 0);
        assertEq(d.attestationHash, bytes32(0));
        assertEq(d.registeredBlock, 0);
    }

    function test_getIdsByPHash_collisions_listAllInOrder() public {
        uint32 deviceId = _device(alice, 1);
        uint256 first = _capture(alice, deviceId, SHA_A, PHASH_A);
        uint256 second = _capture(alice, deviceId, SHA_B, PHASH_A);
        _capture(alice, deviceId, SHA_C, PHASH_B);

        uint256[] memory ids = registry.getIdsByPHash(PHASH_A);
        assertEq(ids.length, 2);
        assertEq(ids[0], first);
        assertEq(ids[1], second);
        assertEq(registry.getIdsByPHash(PHASH_B).length, 1);
    }

    function test_getIdsByPHash_includesEdits() public {
        uint32 deviceId = _device(alice, 1);
        uint256 original = _capture(alice, deviceId, SHA_A, PHASH_B);
        uint256 edit = _edit(alice, deviceId, original, SHA_B, OPS); // _edit usa PHASH_B

        uint256[] memory ids = registry.getIdsByPHash(PHASH_B);
        assertEq(ids.length, 2);
        assertEq(ids[1], edit);
    }

    function test_totalRecords_countsCapturesAndEdits() public {
        uint32 deviceId = _device(alice, 1);
        uint256 original = _capture(alice, deviceId, SHA_A, PHASH_A);
        _edit(alice, deviceId, original, SHA_B, OPS);
        assertEq(registry.totalRecords(), 2);
    }

    // ───────────────────────────── Fuzz ──────────────────────────────

    function testFuzz_registerCapture_roundTrip(bytes32 sha, uint64 pHash) public {
        vm.assume(sha != bytes32(0));
        uint32 deviceId = _device(alice, 1);
        (bytes32 sigR, bytes32 sigS) = _sign(alice, deviceId, sha, pHash, 0, "");

        vm.prank(alice);
        uint256 id = registry.registerCapture(sha, pHash, deviceId, sigR, sigS);

        IImageRegistry.ImageRecord memory r = registry.getRecord(id);
        assertEq(r.sha256Hash, sha);
        assertEq(r.pHash, pHash);
        assertEq(r.sigR, sigR);
        assertEq(r.sigS, sigS);
        assertEq(registry.getIdBySha256(sha), id);
        assertEq(registry.getIdsByPHash(pHash)[0], id);
    }

    /// Payload aleatório: a assinatura certa é aceita e qualquer campo alterado é recusado.
    function testFuzz_signaturePayload_onlyExactFieldsAccepted(
        uint256 keySeed,
        bytes32 sha,
        uint64 pHash,
        string calldata ops
    ) public {
        vm.assume(sha != bytes32(0));
        uint256 opsLength = bytes(ops).length;
        vm.assume(opsLength > 0 && opsLength < 256);
        uint32 deviceId = _device(alice, keySeed);
        uint256 original = _capture(alice, deviceId, keccak256(abi.encode("original", sha)), pHash);
        (bytes32 r, bytes32 s) = _sign(alice, deviceId, sha, pHash, original, ops);

        _expectInvalidEdit(original, keccak256(abi.encode(sha)), pHash, deviceId, r, s, ops);
        _expectInvalidEdit(original, sha, pHash ^ 1, deviceId, r, s, ops);
        _expectInvalidEdit(original, sha, pHash, deviceId, r, s, string.concat(ops, "x"));

        vm.prank(alice);
        uint256 id = registry.registerEdit(original, sha, pHash, deviceId, r, s, ops);
        assertEq(registry.getRecord(id).operations, ops);
    }

    function _expectInvalidEdit(
        uint256 parentId,
        bytes32 sha,
        uint64 pHash,
        uint32 deviceId,
        bytes32 r,
        bytes32 s,
        string memory ops
    ) internal {
        vm.prank(alice);
        vm.expectRevert(IImageRegistry.InvalidDeviceSignature.selector);
        registry.registerEdit(parentId, sha, pHash, deviceId, r, s, ops);
    }

    function testFuzz_ids_areSequential(uint8 count) public {
        uint32 deviceId = _device(alice, 1);
        uint256 n = bound(count, 1, 40);
        for (uint256 i = 1; i <= n; i++) {
            uint256 id = _capture(alice, deviceId, keccak256(abi.encode(i)), uint64(i));
            assertEq(id, i);
            assertEq(registry.getRecord(id).originalId, id);
        }
        assertEq(registry.totalRecords(), n);
    }

    function testFuzz_registerEdit_parentAlwaysPrecedesChild(uint8 depth) public {
        uint32 deviceId = _device(alice, 1);
        uint256 original = _capture(alice, deviceId, SHA_A, PHASH_A);
        uint256 parent = original;
        uint256 n = bound(depth, 1, 20);
        for (uint256 i = 0; i < n; i++) {
            uint256 id = _edit(alice, deviceId, parent, keccak256(abi.encode("edicao", i)), OPS);
            IImageRegistry.ImageRecord memory r = registry.getRecord(id);
            assertLt(r.parentId, r.id);
            assertEq(r.originalId, original);
            parent = id;
        }
    }

    function testFuzz_registerEdit_unknownParent_reverts(uint256 parentId) public {
        uint32 deviceId = _device(alice, 1);
        _capture(alice, deviceId, SHA_A, PHASH_A);
        vm.assume(parentId != 1);

        vm.prank(alice);
        vm.expectRevert(abi.encodeWithSelector(IImageRegistry.ParentNotFound.selector, parentId));
        registry.registerEdit(parentId, SHA_B, PHASH_B, deviceId, bytes32(uint256(1)), bytes32(uint256(1)), OPS);
    }

    function testFuzz_registerDevice_invalidLength_reverts(bytes calldata key) public {
        vm.assume(key.length != 65);
        bytes[] memory chain = _chain(1);
        uint64 deadline = NOW + AUTH_WINDOW;
        _expectBindRevert(
            alice,
            key,
            chain,
            deadline,
            _authorize(alice, key, chain, deadline),
            abi.encodeWithSelector(IImageRegistry.InvalidPublicKey.selector)
        );
    }

    function testFuzz_registerDevice_invalidPrefix_reverts(bytes1 prefix, bytes32 x, bytes32 y) public {
        vm.assume(prefix != 0x04);
        bytes memory key = abi.encodePacked(prefix, x, y);
        bytes[] memory chain = _chain(1);
        uint64 deadline = NOW + AUTH_WINDOW;
        _expectBindRevert(
            alice,
            key,
            chain,
            deadline,
            _authorize(alice, key, chain, deadline),
            abi.encodeWithSelector(IImageRegistry.InvalidPublicKey.selector)
        );
    }

    /// Qualquer carteira diferente da autorizada é recusada.
    function testFuzz_registerDevice_otherSender_reverts(address sender) public {
        vm.assume(sender != alice);
        bytes memory key = _p256Public(_p256Key(1));
        bytes[] memory chain = _chain(1);
        uint64 deadline = NOW + AUTH_WINDOW;
        _expectInvalidAuthorization(sender, key, chain, deadline, _authorize(alice, key, chain, deadline));
    }
}
