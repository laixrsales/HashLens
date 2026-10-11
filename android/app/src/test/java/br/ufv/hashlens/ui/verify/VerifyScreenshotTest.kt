package br.ufv.hashlens.ui.verify

import br.ufv.hashlens.ui.ScreenshotTest
import br.ufv.hashlens.ui.preview.SampleData
import org.junit.Test

/**
 * T064: Verificar imagem (ux.md §4.7: inicial, analisando, arquivo não suportado) e Resultado da
 * verificação (§4.8: os seis resultados, mais versão alterada com 0%).
 */
class VerifyScreenshotTest : ScreenshotTest() {
    private fun verify(name: String, state: VerifyUiState) = screenSnapshot("Verificar_$name") {
        VerifyScreen(state, onAction = {})
    }

    private fun result(name: String, state: VerificationResultUiState) = screenSnapshot("Resultado_$name") {
        VerificationResultScreen(state, onAction = {}, zone = SampleData.ZONE)
    }

    @Test
    fun idle() = verify("inicial", VerifySamples.idle)

    @Test
    fun reading() = verify("lendo", VerifySamples.reading)

    @Test
    fun querying() = verify("consultando", VerifySamples.querying)

    @Test
    fun unsupported() = verify("nao_suportado", VerifySamples.unsupported)

    @Test
    fun original() = result("original", VerificationResultSamples.original)

    @Test
    fun edited() = result("versao_alterada", VerificationResultSamples.edited)

    @Test
    fun editedZero() = result("versao_alterada_zero", VerificationResultSamples.editedZero)

    @Test
    fun visualMatch() = result("correspondencia_visual", VerificationResultSamples.visualMatch)

    @Test
    fun tampered() = result("adulterado", VerificationResultSamples.tampered)

    @Test
    fun notRegistered() = result("nao_registrada", VerificationResultSamples.notRegistered)

    @Test
    fun network() = result("sem_conexao", VerificationResultSamples.network)
}
