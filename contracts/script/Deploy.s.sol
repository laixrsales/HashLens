// SPDX-License-Identifier: MIT
pragma solidity ^0.8.24;

import {ImageRegistry} from "../src/ImageRegistry.sol";
import {Script, console} from "forge-std/Script.sol";

/// @notice Implanta o ImageRegistry v2. Uso (ver specs/020-registro-exclusivo-hashlens/quickstart.md §2):
///         ATTESTER_ADDRESS=0x... forge script script/Deploy.s.sol --rpc-url $SEPOLIA_RPC_URL --broadcast
/// @dev ATTESTER_ADDRESS é o endereço da chave do atestador deste deploy (desenvolvimento ou
///      avaliação, 020 research R5/R8); fica fixo no contrato. A chave de deploy vem de
///      DEPLOYER_PRIVATE_KEY (apenas testnet; nunca versionar). Sem essa variável, usa a conta
///      passada ao forge (ex.: --account deployer, do keystore do Foundry).
contract Deploy is Script {
    function run() external returns (ImageRegistry registry) {
        address attester = vm.envAddress("ATTESTER_ADDRESS");
        require(attester != address(0), "ATTESTER_ADDRESS vazio");
        uint256 deployerKey = _deployerKey();

        if (deployerKey != 0) {
            console.log("carteira de deploy", vm.addr(deployerKey));
            vm.startBroadcast(deployerKey);
        } else {
            console.log("DEPLOYER_PRIVATE_KEY ausente: usando a conta passada ao forge (--account/--sender)");
            vm.startBroadcast();
        }
        registry = new ImageRegistry(attester);
        vm.stopBroadcast();

        console.log("ImageRegistry implantado em", address(registry));
        console.log("atestador", registry.attester());
        console.log("chainId", block.chainid);
        console.log("bloco", block.number);
    }

    /// @dev Aceita a chave com ou sem `0x` (a MetaMask exporta sem); 0 se a variável não existir.
    function _deployerKey() private view returns (uint256) {
        string memory raw = vm.envOr("DEPLOYER_PRIVATE_KEY", string(""));
        bytes memory b = bytes(raw);
        if (b.length == 0) return 0;
        bool prefixed = b.length >= 2 && b[0] == "0" && (b[1] == "x" || b[1] == "X");
        return vm.parseUint(prefixed ? raw : string.concat("0x", raw));
    }
}
