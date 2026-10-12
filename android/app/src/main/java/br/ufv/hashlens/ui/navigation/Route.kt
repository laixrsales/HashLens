package br.ufv.hashlens.ui.navigation

import br.ufv.hashlens.ui.editor.EditorViewModel
import br.ufv.hashlens.ui.genealogy.GenealogyViewModel
import br.ufv.hashlens.ui.verify.VerifyViewModel

/** Destinos do mapa de navegação (ux.md §3). Os argumentos entram com cada tela. */
enum class Route(val path: String) {
    Onboarding("onboarding"),
    Home("home"),

    /** Tela Carteira (US6), aberta pelo chip do Início com uma conta conectada. */
    Wallet("wallet"),
    Capture("capture"),
    RegistrationProgress("registration"),

    /** Editor de uma imagem do acervo. */
    Editor("editor/{${EditorViewModel.PARENT_LOCAL_ID}}"),

    /** "Importar para editar" (US4): o Editor começa pelo Photo Picker. */
    ImportForEdit("import-for-edit"),
    Verify("verify"),

    /** Resultado de um registro já conhecido (ex.: "Ver registro" no Acompanhamento). */
    VerificationResult("verification-result/{${VerifyViewModel.RECORD_ID}}"),

    /** Histórico da imagem de um registro (US5), aberto pelo Resultado da verificação e pelo acervo. */
    Genealogy("genealogy/{${GenealogyViewModel.RECORD_ID}}"),
    Library("library")
    ;

    companion object {
        fun verificationResult(recordId: Long): String = "verification-result/$recordId"

        fun editor(localId: Long): String = "editor/$localId"

        fun genealogy(recordId: Long): String = "genealogy/$recordId"
    }
}
