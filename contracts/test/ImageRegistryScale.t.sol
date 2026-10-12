// SPDX-License-Identifier: MIT
pragma solidity ^0.8.24;

import {IImageRegistry} from "../src/IImageRegistry.sol";
import {ImageRegistry} from "../src/ImageRegistry.sol";
import {Test, console2} from "forge-std/Test.sol";

/// T093: escalabilidade do ImageRegistry até 10.000 registros (plan "Scale/Scope", seção 4.4.3 do TCC).
///
/// O que se espera de um registro on-chain: o custo de registrar e de consultar um registro não cresce com
/// o tamanho do contrato (tudo é mapping indexado, sem varreduras). Só as listas — ids com o mesmo pHash e
/// versões de um registro — crescem, e a leitura delas custa proporcional ao tamanho; o teste mede até onde
/// cabem no limite de gás de um `eth_call` (`--rpc.gascap` padrão do geth: 50 milhões).
///
/// Medidas com o armazenamento frio (`vm.cool`), como no início de uma transação real. São gás de
/// execução da chamada: sem os 21.000 de toda transação e sem o custo do calldata. Os valores vão para
/// `snapshots/ImageRegistryScale.json` (TCC) e aparecem com `forge test --match-contract Scale -vv`.
contract ImageRegistryScaleTest is Test {
    ImageRegistry internal registry;
    uint32 internal deviceId;

    address internal alice = makeAddr("alice");

    bytes32 internal constant SIG_R = bytes32(uint256(0x1111));
    bytes32 internal constant SIG_S = bytes32(uint256(0x2222));
    uint64 internal constant SHARED_PHASH = 0x8f3ac1e07b52d496;
    string internal constant GROUP = "ImageRegistryScale";

    /// Limite de gás de um `eth_call` no geth (`--rpc.gascap`, padrão 50.000.000).
    uint256 internal constant RPC_GAS_CAP = 50_000_000;

    /// Tolerância para "custo constante": 1% (o caminho de código é o mesmo; sobra só arredondamento).
    uint256 internal constant SAME_COST = 0.01e18;

    function setUp() public {
        registry = new ImageRegistry();
        vm.prank(alice);
        deviceId = registry.registerDevice(abi.encodePacked(bytes1(0x04), keccak256("x"), keccak256("y")));
        vm.startPrank(alice);
    }

    // ───────────────────────────── Auxiliares ────────────────────────

    // Sem alocar memória (espaço de rascunho 0x00–0x3f): o laço de 10.000 registros não pode crescer a
    // memória do contrato de teste, senão a expansão de memória (quadrática) entra nas medidas seguintes

    /// SHA-256 fictício e distinto por imagem.
    function _sha(uint256 i) internal pure returns (bytes32 hash) {
        assembly ("memory-safe") {
            mstore(0x00, i)
            mstore(0x20, 1)
            hash := keccak256(0x00, 0x40)
        }
    }

    /// pHash distinto por imagem (sem colisão).
    function _pHash(uint256 i) internal pure returns (uint64) {
        bytes32 hash;
        assembly ("memory-safe") {
            mstore(0x00, i)
            mstore(0x20, 2)
            hash := keccak256(0x00, 0x40)
        }
        return uint64(uint256(hash));
    }

    /// Registra a captura [i] e devolve a memória que a chamada reservou para o retorno (32 bytes que o
    /// Solidity não libera): em 10.000 registros seriam 320 KB, e a expansão de memória entraria nas medidas.
    function _capture(uint256 i, uint64 pHash) internal returns (uint256 id) {
        uint256 freePointer;
        assembly ("memory-safe") {
            freePointer := mload(0x40)
        }
        id = registry.registerCapture(_sha(i), pHash, deviceId, SIG_R, SIG_S);
        assembly ("memory-safe") {
            mstore(0x40, freePointer)
        }
    }

    function _edit(uint256 parentId, uint256 i) internal returns (uint256 id) {
        uint256 freePointer;
        assembly ("memory-safe") {
            freePointer := mload(0x40)
        }
        id = registry.registerEdit(parentId, _sha(i), _pHash(i), deviceId, SIG_R, SIG_S, "grayscale");
        assembly ("memory-safe") {
            mstore(0x40, freePointer)
        }
    }

    /// Gás de execução de registrar a captura [i], com o armazenamento frio.
    function _measureCapture(uint256 i, uint64 pHash) internal returns (uint256 gasUsed) {
        vm.cool(address(registry));
        uint256 before = gasleft();
        _capture(i, pHash);
        gasUsed = before - gasleft();
    }

    function _measureEdit(uint256 parentId, uint256 i) internal returns (uint256 gasUsed) {
        vm.cool(address(registry));
        uint256 before = gasleft();
        _edit(parentId, i);
        gasUsed = before - gasleft();
    }

    function _record(string memory name, uint256 value) internal {
        vm.snapshotValue(GROUP, name, value);
        console2.log(name, value);
    }

    // ───────────────────────────── Escrita ───────────────────────────

    /// Registrar a 10.000ª captura custa o mesmo que a 2ª (a 1ª paga a criação do contador).
    function test_scale_registerCapture_costDoesNotGrowUpTo10k() public {
        uint256 first = _measureCapture(1, _pHash(1));
        uint256 second = _measureCapture(2, _pHash(2));
        for (uint256 i = 3; i < 1000; i++) {
            _capture(i, _pHash(i));
        }
        uint256 at1k = _measureCapture(1000, _pHash(1000));
        for (uint256 i = 1001; i < 10_000; i++) {
            _capture(i, _pHash(i));
        }
        uint256 at10k = _measureCapture(10_000, _pHash(10_000));

        assertEq(registry.totalRecords(), 10_000);
        assertApproxEqRel(at1k, second, SAME_COST);
        assertApproxEqRel(at10k, second, SAME_COST);

        _record("registerCapture_1", first);
        _record("registerCapture_2", second);
        _record("registerCapture_1000", at1k);
        _record("registerCapture_10000", at10k);
    }

    // ───────────────────────────── Leitura ───────────────────────────

    /// Com 1.000 e com 10.000 registros, achar um registro pelo SHA-256 e lê-lo custa o mesmo, e
    /// devolve o registro certo do começo, do meio e do fim.
    function test_scale_lookupAndRead_costDoesNotGrowUpTo10k() public {
        for (uint256 i = 1; i <= 1000; i++) {
            _capture(i, _pHash(i));
        }
        (uint256 lookupAt1k, uint256 readAt1k) = _measureLookupAndRead(1000);
        for (uint256 i = 1001; i <= 10_000; i++) {
            _capture(i, _pHash(i));
        }
        (uint256 lookupAt10k, uint256 readAt10k) = _measureLookupAndRead(10_000);

        // No mesmo instante, o primeiro registro custa o mesmo que o último
        (uint256 lookupFirst, uint256 readFirst) = _measureLookupAndRead(1);
        assertApproxEqRel(lookupFirst, lookupAt10k, SAME_COST);
        assertApproxEqRel(readFirst, readAt10k, SAME_COST);

        assertEq(registry.getIdBySha256(_sha(1)), 1);
        assertEq(registry.getIdBySha256(_sha(5000)), 5000);
        assertEq(registry.getIdBySha256(_sha(10_000)), 10_000);
        assertEq(registry.getRecord(5000).sha256Hash, _sha(5000));
        assertApproxEqRel(lookupAt10k, lookupAt1k, SAME_COST);
        assertApproxEqRel(readAt10k, readAt1k, SAME_COST);

        _record("getIdBySha256_with_1000", lookupAt1k);
        _record("getIdBySha256_with_10000", lookupAt10k);
        _record("getRecord_with_1000", readAt1k);
        _record("getRecord_with_10000", readAt10k);
    }

    /// Mede com uma chamada de aquecimento antes: a primeira leitura do teste decodifica o registro num
    /// ponto de memória diferente e sai ~2.500 de gás mais barata, sem relação com o contrato.
    function _measureLookupAndRead(uint256 id) internal returns (uint256 lookup, uint256 read) {
        registry.getIdBySha256(_sha(id));
        registry.getRecord(id);

        vm.cool(address(registry));
        uint256 before = gasleft();
        assertEq(registry.getIdBySha256(_sha(id)), id);
        lookup = before - gasleft();

        vm.cool(address(registry));
        before = gasleft();
        assertEq(registry.getRecord(id).id, id);
        read = before - gasleft();
    }

    // ───────────────────────────── pHash colidentes ──────────────────

    /// 1.000 imagens com o mesmo pHash (ex.: cópias visualmente iguais): registrar continua com custo
    /// constante, a lista volta completa e em ordem, e lê-la cresce linearmente, bem abaixo do limite de
    /// um `eth_call`.
    function test_scale_collidingPHash_listGrowsLinearlyAndFitsInEthCall() public {
        uint256 second;
        uint256 lastWrite;
        uint256[3] memory sizes = [uint256(10), 100, 1000];
        uint256[3] memory reads;
        uint256 next = 0;
        for (uint256 i = 1; i <= 1000; i++) {
            if (i == 2) {
                second = _measureCapture(i, SHARED_PHASH);
            } else if (i == 1000) {
                lastWrite = _measureCapture(i, SHARED_PHASH);
            } else {
                _capture(i, SHARED_PHASH);
            }
            if (i == sizes[next]) {
                reads[next] = _measureIdsByPHash(i);
                if (next < 2) next++;
            }
        }

        uint256[] memory ids = registry.getIdsByPHash(SHARED_PHASH);
        assertEq(ids.length, 1000);
        assertEq(ids[0], 1);
        assertEq(ids[999], 1000);
        assertApproxEqRel(lastWrite, second, SAME_COST);
        // Linear: 100 vezes mais ids custa entre 50 e 150 vezes mais (o custo fixo da chamada pesa pouco)
        assertGt(reads[2], reads[0] * 50);
        assertLt(reads[2], reads[0] * 150);
        assertLt(reads[2], RPC_GAS_CAP);

        uint256 perId = (reads[2] - reads[1]) / 900;
        _record("registerCapture_same_pHash_2", second);
        _record("registerCapture_same_pHash_1000", lastWrite);
        _record("getIdsByPHash_10", reads[0]);
        _record("getIdsByPHash_100", reads[1]);
        _record("getIdsByPHash_1000", reads[2]);
        _record("getIdsByPHash_gas_per_id", perId);
        _record("getIdsByPHash_max_ids_in_eth_call", RPC_GAS_CAP / perId);
    }

    function _measureIdsByPHash(uint256 expected) internal returns (uint256 gasUsed) {
        vm.cool(address(registry));
        uint256 before = gasleft();
        uint256[] memory ids = registry.getIdsByPHash(SHARED_PHASH);
        gasUsed = before - gasleft();
        assertEq(ids.length, expected);
    }

    // ───────────────────────────── Genealogia ────────────────────────

    /// 200 versões diretas da mesma original (o limite de nós que o app exibe): registrar a 200ª custa o
    /// mesmo que a 2ª, e a lista de versões cabe com folga num `eth_call`.
    function test_scale_childrenOfOneOriginal_upTo200() public {
        uint256 original = _capture(1, _pHash(1));
        _measureEdit(original, 2);
        uint256 second = _measureEdit(original, 3);
        for (uint256 i = 4; i < 201; i++) {
            _edit(original, i);
        }
        uint256 last = _measureEdit(original, 201);

        vm.cool(address(registry));
        uint256 before = gasleft();
        uint256[] memory children = registry.getChildren(original);
        uint256 read = before - gasleft();

        assertEq(children.length, 200);
        assertApproxEqRel(last, second, SAME_COST);
        assertLt(read, RPC_GAS_CAP);

        _record("registerEdit_child_2", second);
        _record("registerEdit_child_200", last);
        _record("getChildren_200", read);
    }

    /// Cadeia de 10 gerações (SC-005): a 10ª versão custa o mesmo que a 2ª — o contrato guarda só o pai e
    /// a original, sem percorrer a cadeia — e cada registro aponta para a original.
    function test_scale_tenGenerationChain_costDoesNotDependOnDepth() public {
        uint256 original = _capture(1, _pHash(1));
        uint256 parent = original;
        uint256 atGeneration2;
        uint256 atGeneration10;
        for (uint256 generation = 2; generation <= 10; generation++) {
            uint256 gasUsed = _measureEdit(parent, generation);
            if (generation == 2) atGeneration2 = gasUsed;
            if (generation == 10) atGeneration10 = gasUsed;
            parent = registry.totalRecords();
        }

        IImageRegistry.ImageRecord memory deepest = registry.getRecord(parent);
        assertEq(deepest.originalId, original);
        assertEq(deepest.parentId, parent - 1);
        assertApproxEqRel(atGeneration10, atGeneration2, SAME_COST);

        _record("registerEdit_generation_2", atGeneration2);
        _record("registerEdit_generation_10", atGeneration10);
    }
}
