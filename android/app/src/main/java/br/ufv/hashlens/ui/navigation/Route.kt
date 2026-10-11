package br.ufv.hashlens.ui.navigation

import br.ufv.hashlens.ui.verify.VerifyViewModel

/** Destinos do mapa de navegação (ux.md §3). Os argumentos entram com cada tela. */
enum class Route(val path: String) {
    Onboarding("onboarding"),
    Home("home"),
    Wallet("wallet"),
    Capture("capture"),
    RegistrationProgress("registration"),
    Editor("editor"),
    Verify("verify"),

    /** Resultado de um registro já conhecido (ex.: "Ver registro" no Acompanhamento). */
    VerificationResult("verification-result/{${VerifyViewModel.RECORD_ID}}"),
    Genealogy("genealogy"),
    Library("library")
    ;

    companion object {
        fun verificationResult(recordId: Long): String = "verification-result/$recordId"
    }
}
