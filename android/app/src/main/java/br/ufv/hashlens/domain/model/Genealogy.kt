package br.ufv.hashlens.domain.model

/**
 * Nó da genealogia (verification-result §4; FR-028): um registro, quanto mudou e se a assinatura do
 * aparelho confere.
 *
 * @property percentFromParent alteração visual em relação ao pai; `null` para a original.
 * @property percentFromOriginal alteração visual em relação à original da cadeia (0 na própria original).
 * @property signatureValid falso quando a assinatura não confere ou o aparelho não existe; o nó
 *   continua na árvore, sinalizado.
 * @property children versões editadas diretamente a partir deste registro, na ordem de registro.
 */
data class GenealogyNode(
    val record: RecordView,
    val percentFromParent: Double?,
    val percentFromOriginal: Double,
    val signatureValid: Boolean,
    val children: List<GenealogyNode>
)

/**
 * Genealogia de um registro (verification-result §4; FR-027): a árvore a partir da original e o
 * caminho da original até o registro consultado.
 *
 * @property root a original, com as versões derivadas até o limite de exibição. Os nós do caminho
 *   estão sempre nela, mesmo além do limite.
 * @property pathToQueried ids da original até o registro consultado (a linha do tempo da tela).
 * @property truncated outras versões passaram de [MAX_LEVELS] níveis ou [MAX_NODES] nós e ficaram de
 *   fora; a tela avisa.
 */
data class Genealogy(val root: GenealogyNode, val pathToQueried: List<Long>, val truncated: Boolean) {
    companion object {
        /** Níveis exibidos, contando a original como o 1º. */
        const val MAX_LEVELS = 10

        /** Nós exibidos, percorridos em largura: os mais próximos da original entram primeiro. */
        const val MAX_NODES = 200
    }
}
