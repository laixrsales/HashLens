package br.ufv.hashlens

import android.app.Application
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import br.ufv.hashlens.data.wallet.WalletSession
import br.ufv.hashlens.work.PendingTxScheduler
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import org.opencv.android.OpenCVLoader

@HiltAndroidApp
class HashLensApp :
    Application(),
    Configuration.Provider {
    @Inject
    lateinit var walletSession: WalletSession

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var pendingTxScheduler: PendingTxScheduler

    // Workers com injeção do Hilt (PendingTxWorker); o inicializador padrão está desligado no manifesto
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        // Carrega o OpenCV nativo na abertura, para o primeiro pHash não pagar esse custo. Uma falha
        // não derruba o app (captura e leitura funcionam sem ele): o PerceptualHasher recusa o uso.
        if (!OpenCVLoader.initLocal()) Log.e(TAG, "OpenCV nativo não carregou")
        walletSession.initialize(this)
        // T092: retoma o acompanhamento dos registros enviados (PENDING e FAILED(TIMEOUT), R23) a cada
        // abertura: um ciclo assim que houver rede e o periódico, se o sistema o tiver descartado (ex.:
        // app parado à força). Sem registros pendentes, o ciclo termina sem consultar a rede.
        pendingTxScheduler.trackPending()
    }

    private companion object {
        const val TAG = "HashLensApp"
    }
}
