package br.ufv.hashlens.ui.capture

import br.ufv.hashlens.data.local.FailureReason
import br.ufv.hashlens.domain.model.NotEligibleReason
import br.ufv.hashlens.domain.usecase.CaptureResult
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.Test

/**
 * 020/T043: a recusa do atestador e a falha temporária viram estados próprios do Acompanhamento
 * (FR-010, FR-011), nunca o erro genérico de rede; só a falha temporária oferece "Tentar de novo".
 */
class CaptureMappingsTest {
    @Test
    fun `cada motivo de recusa vira o estado de aparelho não elegível com o mesmo motivo`() {
        NotEligibleReason.entries.forEach { reason ->
            CaptureResult.NotEligible(LOCAL_ID, reason).toStage() shouldBe RegistrationStage.DeviceNotEligible(reason)
        }
    }

    @Test
    fun `recusa do aparelho não oferece Tentar de novo`() {
        NotEligibleReason.entries.forEach { reason ->
            RegistrationStage.DeviceNotEligible(reason).canRetry shouldBe false
        }
    }

    @Test
    fun `atestador indisponível vira falha temporária, com Tentar de novo`() {
        val stage = CaptureResult.AttesterUnavailable(LOCAL_ID).toStage()

        stage shouldBe RegistrationStage.Failed(RegistrationFailure.AttesterUnavailable)
        stage?.canRetry shouldBe true
    }

    @Test
    fun `falha temporária do atestador é diferente da falha de rede`() {
        CaptureResult.AttesterUnavailable(LOCAL_ID).toStage() shouldBe
            RegistrationStage.Failed(RegistrationFailure.AttesterUnavailable)
        CaptureResult.Failed(LOCAL_ID, FailureReason.NETWORK).toStage() shouldBe
            RegistrationStage.Failed(RegistrationFailure.Network)
    }

    @Test
    fun `falhas sem arquivo confiável pedem nova foto, não reenvio`() {
        RegistrationStage.Failed(RegistrationFailure.HashMismatch).canRetry shouldBe false
        RegistrationStage.Failed(RegistrationFailure.SaveFailed).canRetry shouldBe false
        RegistrationStage.Failed(RegistrationFailure.Rejected).canRetry shouldBe true
    }

    @Test
    fun `resultados que seguem acompanhando a rede não têm etapa final`() {
        CaptureResult.Submitted(LOCAL_ID, "0xabc").toStage().shouldBeNull()
        CaptureResult.Confirmed(LOCAL_ID, 7).toStage().shouldBeNull()
        CaptureResult.WrongWallet(LOCAL_ID, "0x1234").toStage().shouldBeNull()
    }

    private companion object {
        const val LOCAL_ID = 42L
    }
}
