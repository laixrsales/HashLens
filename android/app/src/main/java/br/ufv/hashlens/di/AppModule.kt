package br.ufv.hashlens.di

import android.content.Context
import androidx.work.WorkManager
import br.ufv.hashlens.core.metrics.CsvMetricsLogger
import br.ufv.hashlens.core.metrics.MetricsLogger
import br.ufv.hashlens.data.local.OnboardingPreferences
import br.ufv.hashlens.data.local.SharedOnboardingPreferences
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
import java.io.File
import java.time.Clock
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
    abstract fun onboardingPreferences(preferences: SharedOnboardingPreferences): OnboardingPreferences

    @Binds
    abstract fun pendingTxScheduler(scheduler: WorkManagerPendingTxScheduler): PendingTxScheduler

    companion object {
        @Provides
        @Singleton
        @ApplicationScope
        fun applicationScope(@DefaultDispatcher dispatcher: CoroutineDispatcher): CoroutineScope =
            CoroutineScope(SupervisorJob() + dispatcher)

        /**
         * Métricas do TCC (T096) na pasta de arquivos do app no armazenamento externo, que o `adb pull`
         * alcança sem root; sem armazenamento externo, na pasta interna.
         */
        @Provides
        @Singleton
        fun metricsLogger(
            @ApplicationContext context: Context,
            clock: Clock,
            @IoDispatcher ioDispatcher: CoroutineDispatcher,
            @ApplicationScope scope: CoroutineScope
        ): MetricsLogger = CsvMetricsLogger(
            directory = context.getExternalFilesDir(METRICS_DIR) ?: File(context.filesDir, METRICS_DIR),
            clock = clock,
            ioDispatcher = ioDispatcher,
            scope = scope
        )

        private const val METRICS_DIR = "metrics"

        @Provides
        fun workManager(@ApplicationContext context: Context): WorkManager = WorkManager.getInstance(context)
    }
}
