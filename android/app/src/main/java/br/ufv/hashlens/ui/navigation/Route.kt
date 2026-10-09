package br.ufv.hashlens.ui.navigation

/** Destinos do mapa de navegação (ux.md §3). Os argumentos entram com cada tela. */
enum class Route(val path: String) {
    Onboarding("onboarding"),
    Home("home"),
    Wallet("wallet"),
    Capture("capture"),
    RegistrationProgress("registration"),
    Editor("editor"),
    Verify("verify"),
    VerificationResult("verification-result"),
    Genealogy("genealogy"),
    Library("library")
}
