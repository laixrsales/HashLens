package br.ufv.hashlens.ui.genealogy

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewFontScale
import androidx.compose.ui.tooling.preview.PreviewLightDark
import br.ufv.hashlens.R
import br.ufv.hashlens.domain.model.Genealogy
import br.ufv.hashlens.domain.model.GenealogyNode
import br.ufv.hashlens.domain.model.RecordView
import br.ufv.hashlens.domain.model.VerificationResult.Error.Reason
import br.ufv.hashlens.ui.components.ErrorState
import br.ufv.hashlens.ui.preview.SampleData
import br.ufv.hashlens.ui.preview.SampleData.Percent
import br.ufv.hashlens.ui.preview.SampleData.Records
import br.ufv.hashlens.ui.theme.HashLensTheme
import br.ufv.hashlens.ui.verify.VerifyTopBar
import java.time.ZoneId

/**
 * Histórico da imagem (ux.md §4.9; US5, FR-027, FR-028): a linha do tempo vertical da original até
 * a imagem consultada, destacada, com quem fez cada etapa, quando, as operações e quanto mudou. As
 * outras versões ficam recolhidas em cada nó ("+2 outras versões").
 *
 * @param zone fuso das datas; os previews fixam um para serem determinísticos.
 */
@Composable
fun GenealogyScreen(
    state: GenealogyUiState,
    onAction: (GenealogyAction) -> Unit,
    modifier: Modifier = Modifier,
    zone: ZoneId = ZoneId.systemDefault()
) {
    val spacing = HashLensTheme.spacing
    Scaffold(modifier = modifier, containerColor = MaterialTheme.colorScheme.background) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = spacing.lg)
        ) {
            VerifyTopBar(title = stringResource(R.string.genealogy_title), onBack = { onAction(GenealogyAction.Back) })
            Spacer(Modifier.height(spacing.lg))
            when (state) {
                GenealogyUiState.Loading -> Loading()
                is GenealogyUiState.Error -> ErrorState(
                    icon = Icons.Outlined.CloudOff,
                    title = stringResource(R.string.genealogy_error_title),
                    message = stringResource(
                        if (state.reason == Reason.NETWORK) {
                            R.string.verification_network_message
                        } else {
                            R.string.verification_unknown_message
                        }
                    ),
                    actionLabel = stringResource(R.string.action_try_again),
                    onAction = { onAction(GenealogyAction.Retry) }
                )
                is GenealogyUiState.Loaded -> Loaded(state, onAction, zone)
            }
            Spacer(Modifier.height(spacing.lg))
        }
    }
}

@Composable
private fun Loaded(state: GenealogyUiState.Loaded, onAction: (GenealogyAction) -> Unit, zone: ZoneId) {
    if (state.genealogy.truncated) {
        TruncatedNotice()
        Spacer(Modifier.height(HashLensTheme.spacing.xl))
    }
    val timeline = state.genealogy.timeline()
    timeline.forEachIndexed { index, entry ->
        TimelineNode(
            entry = entry,
            isFirst = index == 0,
            isLast = index == timeline.lastIndex,
            expanded = entry.node.record.id in state.expanded,
            thumbnail = state.thumbnails[entry.node.record.id],
            onAction = onAction,
            zone = zone
        )
    }
}

/** Esperas sempre com texto (direção visual §6). */
@Composable
private fun Loading() {
    val spacing = HashLensTheme.spacing
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = spacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(spacing.md)
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(HashLensTheme.sizes.minTouchTarget),
            strokeWidth = HashLensTheme.sizes.cornerMarkStroke
        )
        Text(
            stringResource(R.string.genealogy_loading),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

/** Limite de exibição excedido (verification-result §4): o que aparece e o que ficou de fora. */
@Composable
private fun TruncatedNotice() {
    val color = HashLensTheme.statusColors.pending
    val spacing = HashLensTheme.spacing
    Row(
        Modifier
            .fillMaxWidth()
            .background(color.container, MaterialTheme.shapes.medium)
            .padding(spacing.md),
        horizontalArrangement = Arrangement.spacedBy(spacing.md)
    ) {
        // Decorativo: o texto explica o aviso
        Icon(Icons.Outlined.WarningAmber, contentDescription = null, tint = color.content)
        Text(
            stringResource(R.string.genealogy_truncated, Genealogy.MAX_LEVELS, Genealogy.MAX_NODES),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@PreviewLightDark
@PreviewFontScale
@Composable
private fun GenealogySimplePreview() = GenealogyPreview(GenealogySamples.simpleChain)

@PreviewLightDark
@Composable
private fun GenealogyBranchesPreview() = GenealogyPreview(GenealogySamples.withBranches)

@Preview
@Composable
private fun GenealogyExpandedPreview() = GenealogyPreview(GenealogySamples.branchesExpanded)

@Preview
@Composable
private fun GenealogyTruncatedPreview() = GenealogyPreview(GenealogySamples.truncated)

@PreviewLightDark
@Composable
private fun GenealogyInvalidPreview() = GenealogyPreview(GenealogySamples.invalidSignature)

@Preview
@Composable
private fun GenealogyLoadingPreview() = GenealogyPreview(GenealogySamples.loading)

@Preview
@Composable
private fun GenealogyErrorPreview() = GenealogyPreview(GenealogySamples.network)

@Composable
private fun GenealogyPreview(state: GenealogyUiState) = HashLensTheme {
    GenealogyScreen(state, onAction = {}, zone = SampleData.ZONE)
}

/** Estados do Histórico da imagem (ux.md §4.9), compartilhados por previews e screenshot tests. */
object GenealogySamples {
    private fun node(
        record: RecordView,
        fromParent: Double?,
        fromOriginal: Double,
        children: List<GenealogyNode> = emptyList(),
        valid: Boolean = true
    ) = GenealogyNode(record, fromParent, fromOriginal, valid, children)

    // Original → versão (editor terceiro) → versão da versão; mais duas versões fora do caminho
    private val secondGeneration = node(Records.secondGeneration, Percent.FROM_PARENT, Percent.HEAVY)
    private val edited = node(Records.edited, Percent.EDITED, Percent.EDITED, listOf(secondGeneration))
    private val zero = node(Records.editedZero, Percent.ZERO, Percent.ZERO)
    private val otherBranch = node(Records.visualMatchCandidates.last(), Percent.VISUAL_MATCH, Percent.VISUAL_MATCH)
    private val branchedRoot = node(Records.original, null, Percent.ZERO, listOf(edited, zero, otherBranch))

    private val branched = Genealogy(branchedRoot, pathToQueried = listOf(1, 2, 4), truncated = false)

    val loading: GenealogyUiState = GenealogyUiState.Loading
    val network: GenealogyUiState = GenealogyUiState.Error(Reason.NETWORK)

    /**
     * Cadeia simples: original → versão consultada, com a foto da versão neste aparelho. A
     * transação da original não é conhecida: o nó aparece sem o link.
     */
    val simpleChain: GenealogyUiState
        get() = GenealogyUiState.Loaded(
            Genealogy(
                node(
                    Records.original.copy(txUrl = null),
                    null,
                    Percent.ZERO,
                    listOf(node(Records.edited, Percent.EDITED, Percent.EDITED))
                ),
                pathToQueried = listOf(1, 2),
                truncated = false
            ),
            thumbnails = mapOf(2L to SampleData.Photo.grayscale())
        )

    val withBranches: GenealogyUiState get() = GenealogyUiState.Loaded(branched)
    val branchesExpanded: GenealogyUiState get() = GenealogyUiState.Loaded(branched, expanded = setOf(1L))
    val truncated: GenealogyUiState get() = GenealogyUiState.Loaded(branched.copy(truncated = true))

    /** A versão intermediária tem assinatura que não confere. */
    val invalidSignature: GenealogyUiState
        get() = GenealogyUiState.Loaded(
            branched.copy(
                root = branchedRoot.copy(
                    children = listOf(edited.copy(signatureValid = false), zero, otherBranch)
                )
            )
        )
}
