package br.ufv.hashlens.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.security.KeyStore

/**
 * Criptografia do aparelho. Hashers e [br.ufv.hashlens.core.crypto.DeviceSignatureVerifier] têm
 * construtor `@Inject`; aqui fica só o que precisa de fábrica.
 */
@Module
@InstallIn(SingletonComponent::class)
object CryptoModule {
    /** Android Keystore, onde vivem as chaves do aparelho, uma por carteira (research R4). */
    @Provides
    fun androidKeyStore(): KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
}
