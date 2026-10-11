package br.ufv.hashlens.data.wallet

/**
 * Falhas ao falar com a carteira. A UI distingue [Rejected] (a pessoa recusou; registro vai a
 * `FAILED(REJECTED)`) das demais, que não são decisão da pessoa.
 */
sealed class WalletException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    /** Nenhuma carteira conectada. */
    class NotConnected : WalletException("nenhuma carteira conectada")

    /** A carteira está em outra rede; [chainId] nulo quando ela não informou. */
    class WrongNetwork(val chainId: Long?) : WalletException("carteira fora da Sepolia: $chainId")

    /** A pessoa recusou o pedido na carteira (EIP-1193 `4001`). */
    class Rejected : WalletException("pedido recusado na carteira")

    /** O pedido expirou sem resposta da carteira. */
    class Expired : WalletException("pedido expirou sem resposta da carteira")

    /** A carteira respondeu com erro JSON-RPC diferente de recusa, ou com um retorno inesperado. */
    class Failed(val code: Int?, message: String) : WalletException("erro da carteira ${code ?: "-"}: $message")

    /** O SDK da carteira não está pronto ou não conseguiu enviar o pedido (sem conexão com o relay). */
    class Unavailable(cause: Throwable? = null) : WalletException("carteira indisponível", cause)
}
