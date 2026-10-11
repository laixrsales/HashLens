package br.ufv.hashlens.ui.capture

import br.ufv.hashlens.ui.ScreenshotTest
import br.ufv.hashlens.ui.preview.SampleData
import org.junit.Test

/**
 * T055: Acompanhamento do registro em todos os estados de ux.md §4.5, mais novo vínculo de
 * aparelho (R25), falha por prazo (R23), foto que não confere na releitura (R8) e saldo
 * insuficiente (FR-003, T103).
 */
class RegistrationProgressScreenshotTest : ScreenshotTest() {
    private fun progress(name: String, state: RegistrationProgressUiState) = screenSnapshot("Registro_$name") {
        RegistrationProgressScreen(state, onAction = {}, zone = SampleData.ZONE)
    }

    @Test
    fun bindingDevice() = progress("vinculando_aparelho", RegistrationProgressSamples.bindingDevice)

    @Test
    fun newDeviceKey() = progress("novo_vinculo", RegistrationProgressSamples.newDeviceKey)

    @Test
    fun awaitingApproval() = progress("aguardando_aprovacao", RegistrationProgressSamples.awaitingApproval)

    @Test
    fun pending() = progress("pendente", RegistrationProgressSamples.pending)

    @Test
    fun confirmed() = progress("confirmado", RegistrationProgressSamples.confirmed)

    @Test
    fun rejected() = progress("rejeitado", RegistrationProgressSamples.rejected)

    @Test
    fun networkFailure() = progress("falha_rede", RegistrationProgressSamples.networkFailure)

    @Test
    fun timeout() = progress("falha_prazo", RegistrationProgressSamples.timeout)

    @Test
    fun hashMismatch() = progress("falha_releitura", RegistrationProgressSamples.hashMismatch)

    @Test
    fun reverted() = progress("falha_revertida", RegistrationProgressSamples.reverted)

    @Test
    fun saveFailed() = progress("falha_galeria", RegistrationProgressSamples.saveFailed)

    @Test
    fun insufficientFunds() = progress("saldo_insuficiente", RegistrationProgressSamples.insufficientFunds)

    @Test
    fun alreadyRegistered() = progress("ja_registrada", RegistrationProgressSamples.alreadyRegistered)
}
