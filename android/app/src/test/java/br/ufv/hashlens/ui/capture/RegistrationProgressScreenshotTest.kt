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

    /** 020/T038: verificação da integridade do aparelho antes do vínculo (FR-012). */
    @Test
    fun verifyingDevice() = progress("verificando_integridade", RegistrationProgressSamples.verifyingDevice)

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

    /** 020/T045: falha temporária na verificação de integridade (FR-011), com "Tentar de novo". */
    @Test
    fun attesterUnavailable() = progress("integridade_indisponivel", RegistrationProgressSamples.attesterUnavailable)

    /** 020/T045: o atestador recusou o aparelho (FR-010), um motivo por captura. */
    @Test
    fun notEligibleModified() = progress("aparelho_modificado", RegistrationProgressSamples.notEligibleModified)

    @Test
    fun notEligibleOutdated() = progress("sistema_desatualizado", RegistrationProgressSamples.notEligibleOutdated)

    @Test
    fun notEligibleNoSecureHardware() =
        progress("sem_hardware_seguro", RegistrationProgressSamples.notEligibleNoSecureHardware)

    @Test
    fun notEligibleApp() = progress("app_nao_original", RegistrationProgressSamples.notEligibleApp)

    @Test
    fun notEligibleRevoked() = progress("certificado_revogado", RegistrationProgressSamples.notEligibleRevoked)
}
