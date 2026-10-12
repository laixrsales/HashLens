package br.ufv.hashlens.data.local

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** Se a pessoa já viu a tela "Como funciona" do primeiro uso (FR-037). */
interface OnboardingPreferences {
    fun hasSeen(): Boolean

    fun markSeen()
}

/**
 * [OnboardingPreferences] em `SharedPreferences`, privado do app. Apagar os dados do app volta ao
 * primeiro uso; o acervo e as chaves do aparelho ficam em outros lugares.
 */
class SharedOnboardingPreferences @Inject constructor(@ApplicationContext context: Context) : OnboardingPreferences {
    private val preferences = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    override fun hasSeen(): Boolean = preferences.getBoolean(KEY_SEEN, false)

    override fun markSeen() {
        preferences.edit().putBoolean(KEY_SEEN, true).apply()
    }

    companion object {
        /** Arquivo e chave; o roteiro do quickstart limpa só esta preferência para rever o primeiro uso. */
        const val FILE = "onboarding"
        const val KEY_SEEN = "como_funciona_vista"
    }
}
