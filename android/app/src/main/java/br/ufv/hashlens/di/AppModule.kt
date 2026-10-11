package br.ufv.hashlens.di

import android.content.Context
import androidx.work.WorkManager
import br.ufv.hashlens.data.wallet.PackageManagerWalletAppDetector
import br.ufv.hashlens.data.wallet.WalletAppDetector
import br.ufv.hashlens.work.PendingTxScheduler
import br.ufv.hashlens.work.WorkManagerPendingTxScheduler
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob

/**
 * Escopo da aplicação: trabalho que não pode morrer com a tela, como um registro esperando a
 * aprovação na carteira enquanto a pessoa sai do Acompanhamento.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

@Module
@InstallIn(SingletonComponent::class)
abstract class AppModule {
    @Binds
    abstract fun walletAppDetector(detector: PackageManagerWalletAppDetector): WalletAppDetector

    @Binds
    abstract fun pendingTxScheduler(scheduler: WorkManagerPendingTxScheduler): PendingTxScheduler

    companion object {
        @Provides
        @Singleton
        @ApplicationScope
        fun applicationScope(@DefaultDispatcher dispatcher: CoroutineDispatcher): CoroutineScope =
            CoroutineScope(SupervisorJob() + dispatcher)

        @Provides
        fun workManager(@ApplicationContext context: Context): WorkManager = WorkManager.getInstance(context)
    }
}
