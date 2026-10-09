package br.ufv.hashlens.ui.preview

import br.ufv.hashlens.R
import br.ufv.hashlens.config.ChainConfig
import br.ufv.hashlens.domain.model.EditOperation
import br.ufv.hashlens.domain.model.RecordView
import br.ufv.hashlens.ui.common.DateFormat
import br.ufv.hashlens.ui.components.ImageStatus
import br.ufv.hashlens.ui.components.ProgressStep
import br.ufv.hashlens.ui.components.StepState
import br.ufv.hashlens.ui.components.TechnicalItem
import br.ufv.hashlens.ui.components.WalletChipState
import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneId

/**
 * Dados fictícios para previews e screenshot tests, cobrindo os estados de `docs/referencia/ux.md`
 * (§4.1 a §4.11). Valores fixos (inclusive o fuso) para que os screenshots sejam determinísticos.
 *
 * Os `UiState` de cada tela nascem com a própria tela (T038, T055, T064, …) e montam seus estados
 * a partir daqui; [Screens] lista o que cada estado de `ux.md` usa.
 */
@Suppress("MagicNumber")
object SampleData {
    /** Fuso fixo dos previews; no app, o do aparelho. */
    val ZONE: ZoneId = ZoneId.of("America/Sao_Paulo")

    fun dateTime(instant: Instant): String = DateFormat.dateTime(instant, ZONE)

    object Wallets {
        /** Quem fotografa e registra a original. */
        const val AUTHOR = "0x12ab34cd56ef7890a1b2c3d4e5f60718293a9f3c"

        /** Editor terceiro, que registra uma versão da foto da autora. */
        const val EDITOR = "0x7c4e19b2d83f05a6c71e2b9d4f8a03c65e17a210"

        /** Registrante de outro candidato em correspondência visual. */
        const val OTHER = "0x3f90d2c6a1b74e85f02d9c3b6a71e4f8d5c2b0e8"
    }

    object Chain {
        val config = ChainConfig(
            rpcUrl = "",
            registryAddress = "0x5b3e8f2a9c1d4e7b6a0f3c8d2e9b1a4c7f6d0e35",
            reownProjectId = ""
        )

        /** Saldo de teste suficiente e baixo (ux.md §4.3 e §4.11). */
        val BALANCE_OK = BigDecimal("0.0421")
        val BALANCE_LOW = BigDecimal("0.00002")
    }

    object Percent {
        const val EDITED = 12.5
        const val ZERO = 0.0
        const val HEAVY = 43.8
        const val MAX = 100.0
        const val FROM_PARENT = 6.3
        const val VISUAL_MATCH = 3.1
    }

    object Records {
        private const val TX = "0x9a1f6c3e8b2d47f05e6a9c1b3d8f2e7a4c6b0d9e1f3a5c7e9b2d4f6a8c0e1b3d"

        private fun record(
            id: Long,
            registrant: String,
            timestamp: String,
            operations: List<EditOperation> = emptyList(),
            sha256: String = "a3f1".repeat(16),
            pHash: String = "c3d2e1f0a9b8c7d6"
        ) = RecordView(
            id = id,
            sha256 = sha256,
            pHash = pHash,
            registrant = registrant,
            deviceId = 3,
            timestamp = Instant.parse(timestamp),
            operations = operations,
            txUrl = Chain.config.transactionUrl(TX)
        )

        /** Registrada – original. */
        val original = record(1, Wallets.AUTHOR, "2026-10-02T12:05:00Z")

        /** Registrada – versão alterada, por um editor terceiro. */
        val edited = record(
            id = 2,
            registrant = Wallets.EDITOR,
            timestamp = "2026-10-04T17:32:00Z",
            operations = listOf(EditOperation.Grayscale, EditOperation.Brightness(30)),
            sha256 = "7be0".repeat(16),
            pHash = "c3d2e1f0a9b8c7f6"
        )

        /** Versão com 0% de alteração visual (ex.: recompressão registrada como edição). */
        val editedZero = record(
            id = 3,
            registrant = Wallets.AUTHOR,
            timestamp = "2026-10-05T10:15:00Z",
            operations = listOf(EditOperation.Contrast(1.05f)),
            sha256 = "51c9".repeat(16)
        )

        /** Versão da versão: terceira geração, com ramificação a partir da original. */
        val secondGeneration = record(
            id = 4,
            registrant = Wallets.EDITOR,
            timestamp = "2026-10-06T09:40:00Z",
            operations = listOf(EditOperation.Crop(0.1f, 0.1f, 0.8f, 0.8f)),
            sha256 = "e40d".repeat(16),
            pHash = "c3d2e1f0a9b8c6f6"
        )

        /** Registro adulterado: assinatura do aparelho não confere. */
        val tampered = record(5, Wallets.OTHER, "2026-10-03T15:00:00Z", sha256 = "0bad".repeat(16))

        /** Correspondência visual: dois registros visualmente iguais, em ordem cronológica. */
        val visualMatchCandidates =
            listOf(original, record(6, Wallets.OTHER, "2026-10-07T08:20:00Z", sha256 = "c0b1".repeat(16)))
    }

    object Technical {
        val original = items(Records.original)
        val edited = items(Records.edited)

        private fun items(record: RecordView) = listOf(
            TechnicalItem(R.string.technical_record, "#${record.id}"),
            TechnicalItem(R.string.technical_sha256, record.sha256),
            TechnicalItem(R.string.technical_phash, record.pHash),
            TechnicalItem(R.string.technical_wallet, record.registrant),
            TechnicalItem(R.string.technical_device, "#${record.deviceId}"),
            TechnicalItem(R.string.technical_transaction, record.txUrl.orEmpty().substringAfterLast('/'), record.txUrl)
        )
    }

    /** Etapas do Acompanhamento do registro (ux.md §4.5) e da análise da verificação (§4.7). */
    object Steps {
        private fun registration(
            saved: StepState,
            wallet: StepState,
            sent: StepState,
            confirmed: StepState,
            walletDetail: Int? = null,
            sentDetail: Int? = null
        ) = listOf(
            ProgressStep(R.string.step_saved, saved),
            ProgressStep(R.string.step_wallet, wallet, walletDetail),
            ProgressStep(R.string.step_sent, sent, sentDetail),
            ProgressStep(R.string.step_confirmed, confirmed)
        )

        /** Foto já salva; o vínculo do aparelho vem antes da aprovação do registro (plan.md, fluxo de captura). */
        private fun withDeviceBinding(detail: Int) = registration(
            StepState.Done,
            StepState.Waiting,
            StepState.Waiting,
            StepState.Waiting
        ).let { steps ->
            listOf(steps.first(), ProgressStep(R.string.step_device_binding, StepState.Active, detail)) + steps.drop(1)
        }

        /** Vinculando aparelho (1ª vez com a carteira): etapa extra entre salvar e aprovar. */
        val firstDeviceBinding = withDeviceBinding(R.string.step_device_binding_detail)

        /** Novo vínculo de aparelho: a chave do Keystore mudou (reinstalação, research R25). */
        val newDeviceBinding = withDeviceBinding(R.string.step_device_binding_new_key_detail)

        val awaitingApproval = registration(
            StepState.Done,
            StepState.Active,
            StepState.Waiting,
            StepState.Waiting,
            walletDetail = R.string.step_wallet_detail
        )

        val pending = registration(
            StepState.Done,
            StepState.Done,
            StepState.Active,
            StepState.Waiting,
            sentDetail = R.string.step_sent_detail
        )

        val confirmed = registration(StepState.Done, StepState.Done, StepState.Done, StepState.Done)

        val rejected = registration(
            StepState.Done,
            StepState.Failed,
            StepState.Waiting,
            StepState.Waiting,
            walletDetail = R.string.step_failed_rejected
        )

        val networkFailure = registration(
            StepState.Done,
            StepState.Done,
            StepState.Failed,
            StepState.Waiting,
            sentDetail = R.string.step_failed_network
        )

        /** Falhou por prazo: 30 min sem recibo e sem registro on-chain (research R23). */
        val timeoutFailure = registration(
            StepState.Done,
            StepState.Done,
            StepState.Failed,
            StepState.Waiting,
            sentDetail = R.string.step_failed_timeout
        )

        val registration = listOf(firstDeviceBinding, newDeviceBinding, awaitingApproval, pending, confirmed)
        val failures = listOf(rejected, networkFailure, timeoutFailure)

        val reading = listOf(
            ProgressStep(R.string.step_reading_image, StepState.Active),
            ProgressStep(R.string.step_querying_records, StepState.Waiting)
        )
        val querying = listOf(
            ProgressStep(R.string.step_reading_image, StepState.Done),
            ProgressStep(R.string.step_querying_records, StepState.Active)
        )
        val verification = listOf(reading, querying)
    }

    object Wallet {
        val disconnected: WalletChipState = WalletChipState.Disconnected
        val connected: WalletChipState = WalletChipState.Connected(Wallets.AUTHOR)
        val wrongNetwork: WalletChipState = WalletChipState.WrongNetwork(Wallets.AUTHOR)
    }

    /** Item do acervo (ux.md §4.10), até o `UiState` do acervo existir. */
    data class LibraryItem(val record: RecordView?, val status: ImageStatus, val fileAvailable: Boolean = true)

    object Library {
        val confirmed = LibraryItem(Records.original, ImageStatus.Original)
        val confirmedVersion = LibraryItem(Records.edited, ImageStatus.Edited)
        val pending = LibraryItem(record = null, status = ImageStatus.Pending)
        val failed = LibraryItem(record = null, status = ImageStatus.Failed)
        val fileRemoved = LibraryItem(Records.secondGeneration, ImageStatus.Edited, fileAvailable = false)

        val empty = emptyList<LibraryItem>()
        val mixed = listOf(pending, failed, confirmed, confirmedVersion, fileRemoved)
    }

    /** Histórico da imagem (ux.md §4.9): cadeia da original até a versão consultada e ramificações. */
    object Genealogy {
        val simpleChain = listOf(Records.original, Records.edited)
        val withBranches = listOf(Records.original, Records.edited, Records.secondGeneration)

        /** Versões fora do caminho, recolhidas como "+2 outras versões". */
        val hiddenBranches = listOf(Records.editedZero, Records.visualMatchCandidates.last())

        /** Limite de exibição: 10 níveis / 200 nós (verification-result §4). */
        const val LEVEL_LIMIT = 10
        const val NODE_LIMIT = 200
    }

    object Editor {
        /** Operações em edição, para desfazer/refazer e para a linha "Preto e branco, brilho +30". */
        val operations = listOf(EditOperation.Grayscale, EditOperation.Brightness(30))
        val allFilters = listOf(
            EditOperation.Brightness(30),
            EditOperation.Contrast(1.2f),
            EditOperation.Grayscale,
            EditOperation.Sepia,
            EditOperation.Blur(5),
            EditOperation.Sharpen,
            EditOperation.Edges(50, 150),
            EditOperation.Crop(0.1f, 0.1f, 0.8f, 0.8f),
            EditOperation.Rotate90(1)
        )

        /** Imagem de terceiro: faixa "Registrada originalmente por 0x12ab…9f3c". */
        val thirdPartyOriginal = Records.original
    }

    /**
     * Estados de cada tela em `ux.md` §4 e os dados que usam. Estados sem dado próprio (ex.: "inicial",
     * "carregando", "sem permissão") não precisam de nada daqui.
     */
    object Screens {
        val verificationResults = mapOf(
            ImageStatus.Original to Records.original,
            ImageStatus.Edited to Records.edited,
            ImageStatus.Tampered to Records.tampered
        )

        /** Acompanhamento: todos os estados, inclusive "já registrada" (link para [Records.original]). */
        val registrationProgress = mapOf(
            "vinculando aparelho (1ª vez)" to Steps.firstDeviceBinding,
            "novo vínculo de aparelho" to Steps.newDeviceBinding,
            "aguardando aprovação" to Steps.awaitingApproval,
            "pendente" to Steps.pending,
            "confirmado" to Steps.confirmed,
            "rejeitado na carteira" to Steps.rejected,
            "falhou na rede" to Steps.networkFailure,
            "falhou por prazo" to Steps.timeoutFailure
        )
        val alreadyRegistered = Records.original
    }
}
