// SPDX-License-Identifier: MIT
pragma solidity ^0.8.24;

import {ImageRegistry} from "../src/ImageRegistry.sol";
import {Script, console} from "forge-std/Script.sol";

/// @notice Implanta o ImageRegistry. Uso (ver quickstart.md §2):
///         forge script script/Deploy.s.sol --rpc-url $SEPOLIA_RPC_URL --broadcast
/// @dev A chave de deploy vem de DEPLOYER_PRIVATE_KEY (apenas testnet; nunca versionar). Sem essa
///      variável, usa a conta passada ao forge (ex.: --account deployer, do keystore do Foundry).
contract Deploy is Script {
    function run() external returns (ImageRegistry registry) {
        uint256 deployerKey = vm.envOr("DEPLOYER_PRIVATE_KEY", uint256(0));

        if (deployerKey != 0) vm.startBroadcast(deployerKey);
        else vm.startBroadcast();
        registry = new ImageRegistry();
        vm.stopBroadcast();

        console.log("ImageRegistry implantado em", address(registry));
        console.log("chainId", block.chainid);
    }
}
