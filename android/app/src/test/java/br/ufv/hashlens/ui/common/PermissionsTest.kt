package br.ufv.hashlens.ui.common

import br.ufv.hashlens.R
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/** T032: estado da permissão a partir do que o Android informa, e a explicação de cada estado. */
class PermissionsTest {
    @Test
    fun `concedida prevalece sobre o resto`() {
        PermissionStatus.of(granted = true, requested = true, shouldShowRationale = true) shouldBe
            PermissionStatus.Granted
    }

    @Test
    fun `nunca pedida pede direto, sem explicação`() {
        val status = PermissionStatus.of(granted = false, requested = false, shouldShowRationale = false)
        status shouldBe PermissionStatus.NotRequested
        status.cameraExplanation.shouldBeNull()
    }

    @Test
    fun `negada com rationale explica e pede de novo`() {
        val status = PermissionStatus.of(granted = false, requested = true, shouldShowRationale = true)
        status shouldBe PermissionStatus.Denied
        status.cameraExplanation?.action shouldBe R.string.camera_permission_allow
        status.cameraExplanation?.opensSettings shouldBe false
    }

    @Test
    fun `negada de vez explica e leva às configurações`() {
        val status = PermissionStatus.of(granted = false, requested = true, shouldShowRationale = false)
        status shouldBe PermissionStatus.PermanentlyDenied
        status.cameraExplanation?.action shouldBe R.string.permission_open_settings
        status.cameraExplanation?.opensSettings shouldBe true
    }
}
