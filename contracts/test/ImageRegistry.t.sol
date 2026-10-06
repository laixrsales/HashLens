// SPDX-License-Identifier: MIT
pragma solidity ^0.8.24;

import {IImageRegistry} from "../src/IImageRegistry.sol";
import {ImageRegistry} from "../src/ImageRegistry.sol";
import {Test} from "forge-std/Test.sol";

contract ImageRegistryTest is Test {
    ImageRegistry internal registry;

    address internal alice = makeAddr("alice");
    address internal bob = makeAddr("bob");

    bytes32 internal constant SHA_A = keccak256("imagem-a");
    bytes32 internal constant SHA_B = keccak256("imagem-b");
    bytes32 internal constant SHA_C = keccak256("imagem-c");
    uint64 internal constant PHASH_A = 0x8f3ac1e07b52d496;
    uint64 internal constant PHASH_B = 0x8f3ac1e07b52d497;
    bytes32 internal constant SIG_R = bytes32(uint256(0x1111));
    bytes32 internal constant SIG_S = bytes32(uint256(0x2222));
    string internal constant OPS = "brightness:+30;grayscale";

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

    function setUp() public {
        registry = new ImageRegistry();
    }

    // ───────────────────────────── Auxiliares ────────────────────────

    /// Chave P-256 não comprimida fictícia (65 bytes, prefixo 0x04).
    function _pubKey(uint256 seed) internal pure returns (bytes memory) {
        return abi.encodePacked(bytes1(0x04), keccak256(abi.encode(seed, 1)), keccak256(abi.encode(seed, 2)));
    }

    function _device(address owner, uint256 seed) internal returns (uint32 deviceId) {
        vm.prank(owner);
        deviceId = registry.registerDevice(_pubKey(seed));
    }

    function _capture(address owner, uint32 deviceId, bytes32 sha, uint64 pHash) internal returns (uint256 id) {
        vm.prank(owner);
        id = registry.registerCapture(sha, pHash, deviceId, SIG_R, SIG_S);
    }

    function _edit(address editor, uint32 deviceId, uint256 parentId, bytes32 sha, string memory ops)
        internal
        returns (uint256 id)
    {
        vm.prank(editor);
        id = registry.registerEdit(parentId, sha, PHASH_B, deviceId, SIG_R, SIG_S, ops);
    }

    function _repeat(bytes1 char, uint256 length) internal pure returns (string memory) {
        bytes memory out = new bytes(length);
        for (uint256 i = 0; i < length; i++) {
            out[i] = char;
        }
        return string(out);
    }

    // ───────────────────────────── registerDevice ────────────────────

    function test_registerDevice_valid_storesDeviceAndEmits() public {
        bytes memory key = _pubKey(1);
        vm.warp(1_700_000_000);

        vm.expectEmit(true, true, false, true, address(registry));
        emit DeviceRegistered(1, alice, key);
        vm.prank(alice);
        uint32 deviceId = registry.registerDevice(key);

        assertEq(deviceId, 1);
        IImageRegistry.Device memory device = registry.getDevice(deviceId);
        assertEq(device.id, 1);
        assertEq(device.owner, alice);
        assertEq(device.publicKey, key);
        assertEq(device.registeredAt, 1_700_000_000);
    }

    function test_registerDevice_idsAreSequentialFromOne() public {
        assertEq(_device(alice, 1), 1);
        assertEq(_device(bob, 2), 2);
        assertEq(_device(alice, 3), 3);
    }

    function test_registerDevice_wrongLength_reverts() public {
        bytes memory shortKey = new bytes(64);
        shortKey[0] = 0x04;
        vm.prank(alice);
        vm.expectRevert(IImageRegistry.InvalidPublicKey.selector);
        registry.registerDevice(shortKey);

        bytes memory longKey = abi.encodePacked(_pubKey(1), bytes1(0x00));
        vm.prank(alice);
        vm.expectRevert(IImageRegistry.InvalidPublicKey.selector);
        registry.registerDevice(longKey);
    }

    function test_registerDevice_wrongPrefix_reverts() public {
        bytes memory key = _pubKey(1);
        key[0] = 0x02; // formato comprimido não é aceito
        vm.prank(alice);
        vm.expectRevert(IImageRegistry.InvalidPublicKey.selector);
        registry.registerDevice(key);
    }

    function test_registerDevice_emptyKey_reverts() public {
        vm.prank(alice);
        vm.expectRevert(IImageRegistry.InvalidPublicKey.selector);
        registry.registerDevice("");
    }

    function test_registerDevice_duplicateKeySameWallet_reverts() public {
        uint32 first = _device(alice, 1);
        vm.prank(alice);
        vm.expectRevert(abi.encodeWithSelector(IImageRegistry.DeviceAlreadyRegistered.selector, first));
        registry.registerDevice(_pubKey(1));
    }

    function test_registerDevice_sameKeyOtherWallet_reverts() public {
        uint32 first = _device(alice, 1);
        vm.prank(bob);
        vm.expectRevert(abi.encodeWithSelector(IImageRegistry.DeviceAlreadyRegistered.selector, first));
        registry.registerDevice(_pubKey(1));
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

    // ───────────────────────────── registerCapture ───────────────────

    function test_registerCapture_success_storesOriginalAndEmits() public {
        uint32 deviceId = _device(alice, 1);
        vm.warp(1_700_000_100);

        vm.expectEmit(true, true, true, true, address(registry));
        emit ImageRegistered(1, SHA_A, 1, 0, PHASH_A, alice, deviceId);
        uint256 id = _capture(alice, deviceId, SHA_A, PHASH_A);

        assertEq(id, 1);
        IImageRegistry.ImageRecord memory r = registry.getRecord(id);
        assertEq(r.id, 1);
        assertEq(r.sha256Hash, SHA_A);
        assertEq(r.pHash, PHASH_A);
        assertEq(r.parentId, 0);
        assertEq(r.originalId, 1);
        assertEq(r.registrant, alice);
        assertEq(r.deviceId, deviceId);
        assertEq(r.sigR, SIG_R);
        assertEq(r.sigS, SIG_S);
        assertEq(r.timestamp, 1_700_000_100);
        assertEq(r.operations, "");

        assertEq(registry.getIdBySha256(SHA_A), 1);
        assertEq(registry.totalRecords(), 1);
    }

    function test_registerCapture_zeroSha_reverts() public {
        uint32 deviceId = _device(alice, 1);
        vm.prank(alice);
        vm.expectRevert(IImageRegistry.InvalidHash.selector);
        registry.registerCapture(bytes32(0), PHASH_A, deviceId, SIG_R, SIG_S);
    }

    function test_registerCapture_duplicateSha_reverts() public {
        uint32 deviceId = _device(alice, 1);
        uint256 first = _capture(alice, deviceId, SHA_A, PHASH_A);

        vm.prank(alice);
        vm.expectRevert(abi.encodeWithSelector(IImageRegistry.AlreadyRegistered.selector, first));
        registry.registerCapture(SHA_A, PHASH_B, deviceId, SIG_R, SIG_S);
    }

    function test_registerCapture_duplicateShaFromOtherWallet_reverts() public {
        uint256 first = _capture(alice, _device(alice, 1), SHA_A, PHASH_A);
        uint32 bobDevice = _device(bob, 2);

        vm.prank(bob);
        vm.expectRevert(abi.encodeWithSelector(IImageRegistry.AlreadyRegistered.selector, first));
        registry.registerCapture(SHA_A, PHASH_A, bobDevice, SIG_R, SIG_S);
    }

    function test_registerCapture_unknownDevice_reverts() public {
        vm.prank(alice);
        vm.expectRevert(abi.encodeWithSelector(IImageRegistry.UnknownDevice.selector, uint32(7)));
        registry.registerCapture(SHA_A, PHASH_A, 7, SIG_R, SIG_S);
    }

    function test_registerCapture_deviceZero_reverts() public {
        vm.prank(alice);
        vm.expectRevert(abi.encodeWithSelector(IImageRegistry.UnknownDevice.selector, uint32(0)));
        registry.registerCapture(SHA_A, PHASH_A, 0, SIG_R, SIG_S);
    }

    function test_registerCapture_deviceOfOtherWallet_reverts() public {
        uint32 aliceDevice = _device(alice, 1);
        vm.prank(bob);
        vm.expectRevert(abi.encodeWithSelector(IImageRegistry.NotDeviceOwner.selector, aliceDevice, bob));
        registry.registerCapture(SHA_A, PHASH_A, aliceDevice, SIG_R, SIG_S);
    }

    function test_registerCapture_missingSigR_reverts() public {
        uint32 deviceId = _device(alice, 1);
        vm.prank(alice);
        vm.expectRevert(IImageRegistry.MissingSignature.selector);
        registry.registerCapture(SHA_A, PHASH_A, deviceId, bytes32(0), SIG_S);
    }

    function test_registerCapture_missingSigS_reverts() public {
        uint32 deviceId = _device(alice, 1);
        vm.prank(alice);
        vm.expectRevert(IImageRegistry.MissingSignature.selector);
        registry.registerCapture(SHA_A, PHASH_A, deviceId, SIG_R, bytes32(0));
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

    // ───────────────────────────── registerEdit ──────────────────────

    function test_registerEdit_success_linksToParentAndEmits() public {
        uint32 deviceId = _device(alice, 1);
        uint256 original = _capture(alice, deviceId, SHA_A, PHASH_A);
        vm.warp(1_700_000_200);

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
        assertEq(r.timestamp, 1_700_000_200);

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
        vm.prank(alice);
        vm.expectRevert(abi.encodeWithSelector(IImageRegistry.ParentNotFound.selector, uint256(42)));
        registry.registerEdit(42, SHA_B, PHASH_B, deviceId, SIG_R, SIG_S, OPS);
    }

    function test_registerEdit_parentZero_reverts() public {
        uint32 deviceId = _device(alice, 1);
        vm.prank(alice);
        vm.expectRevert(abi.encodeWithSelector(IImageRegistry.ParentNotFound.selector, uint256(0)));
        registry.registerEdit(0, SHA_B, PHASH_B, deviceId, SIG_R, SIG_S, OPS);
    }

    function test_registerEdit_byThirdParty_setsRegistrantToEditor() public {
        uint256 original = _capture(alice, _device(alice, 1), SHA_A, PHASH_A);
        uint32 bobDevice = _device(bob, 2);

        uint256 id = _edit(bob, bobDevice, original, SHA_B, OPS);

        IImageRegistry.ImageRecord memory r = registry.getRecord(id);
        assertEq(r.registrant, bob);
        assertEq(r.deviceId, bobDevice);
        assertEq(r.originalId, original);
        assertEq(registry.getRecord(original).registrant, alice);
    }

    function test_registerEdit_deviceOfOtherWallet_reverts() public {
        uint32 aliceDevice = _device(alice, 1);
        uint256 original = _capture(alice, aliceDevice, SHA_A, PHASH_A);
        vm.prank(bob);
        vm.expectRevert(abi.encodeWithSelector(IImageRegistry.NotDeviceOwner.selector, aliceDevice, bob));
        registry.registerEdit(original, SHA_B, PHASH_B, aliceDevice, SIG_R, SIG_S, OPS);
    }

    function test_registerEdit_duplicateSha_reverts() public {
        uint32 deviceId = _device(alice, 1);
        uint256 original = _capture(alice, deviceId, SHA_A, PHASH_A);
        vm.prank(alice);
        vm.expectRevert(abi.encodeWithSelector(IImageRegistry.AlreadyRegistered.selector, original));
        registry.registerEdit(original, SHA_A, PHASH_B, deviceId, SIG_R, SIG_S, OPS);
    }

    function test_registerEdit_zeroSha_reverts() public {
        uint32 deviceId = _device(alice, 1);
        uint256 original = _capture(alice, deviceId, SHA_A, PHASH_A);
        vm.prank(alice);
        vm.expectRevert(IImageRegistry.InvalidHash.selector);
        registry.registerEdit(original, bytes32(0), PHASH_B, deviceId, SIG_R, SIG_S, OPS);
    }

    function test_registerEdit_missingSignature_reverts() public {
        uint32 deviceId = _device(alice, 1);
        uint256 original = _capture(alice, deviceId, SHA_A, PHASH_A);
        vm.prank(alice);
        vm.expectRevert(IImageRegistry.MissingSignature.selector);
        registry.registerEdit(original, SHA_B, PHASH_B, deviceId, bytes32(0), bytes32(0), OPS);
    }

    function test_registerEdit_emptyOperations_reverts() public {
        uint32 deviceId = _device(alice, 1);
        uint256 original = _capture(alice, deviceId, SHA_A, PHASH_A);
        vm.prank(alice);
        vm.expectRevert(IImageRegistry.InvalidOperations.selector);
        registry.registerEdit(original, SHA_B, PHASH_B, deviceId, SIG_R, SIG_S, "");
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
        vm.prank(alice);
        vm.expectRevert(IImageRegistry.InvalidOperations.selector);
        registry.registerEdit(original, SHA_B, PHASH_B, deviceId, SIG_R, SIG_S, _repeat("a", 257));
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
        vm.prank(alice);
        vm.expectRevert(IImageRegistry.InvalidOperations.selector);
        registry.registerEdit(original, SHA_B, PHASH_B, deviceId, SIG_R, SIG_S, string(ops));
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

    function testFuzz_registerCapture_roundTrip(bytes32 sha, uint64 pHash, bytes32 sigR, bytes32 sigS) public {
        vm.assume(sha != bytes32(0) && sigR != bytes32(0) && sigS != bytes32(0));
        uint32 deviceId = _device(alice, 1);

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
        registry.registerEdit(parentId, SHA_B, PHASH_B, deviceId, SIG_R, SIG_S, OPS);
    }

    function testFuzz_registerDevice_invalidLength_reverts(bytes calldata key) public {
        vm.assume(key.length != 65);
        vm.prank(alice);
        vm.expectRevert(IImageRegistry.InvalidPublicKey.selector);
        registry.registerDevice(key);
    }

    function testFuzz_registerDevice_invalidPrefix_reverts(bytes1 prefix, bytes32 x, bytes32 y) public {
        vm.assume(prefix != 0x04);
        vm.prank(alice);
        vm.expectRevert(IImageRegistry.InvalidPublicKey.selector);
        registry.registerDevice(abi.encodePacked(prefix, x, y));
    }
}
