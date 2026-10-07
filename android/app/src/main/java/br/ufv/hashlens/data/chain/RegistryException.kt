package br.ufv.hashlens.data.chain

/**
 * Falhas na leitura do ImageRegistry. A verificação trata [Network] como "Não foi possível
 * verificar", nunca como "Não registrada" (spec, Edge Cases).
 */
sealed class RegistryException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    /** Sem conexão, tempo esgotado ou falha de HTTP. */
    class Network(cause: Throwable) : RegistryException("falha de rede ao consultar o contrato", cause)

    /** O nó respondeu com erro JSON-RPC (inclui chamada revertida). */
    class Rpc(val code: Int, message: String) : RegistryException("erro JSON-RPC $code: $message")

    /** O nó respondeu, mas o retorno não é um ABI válido para a função chamada. */
    class InvalidResponse(cause: Throwable) : RegistryException("resposta inválida do contrato", cause)
}
