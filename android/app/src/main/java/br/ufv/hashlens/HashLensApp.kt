package br.ufv.hashlens

import android.app.Application
import android.util.Log
import br.ufv.hashlens.data.wallet.WalletSession
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import org.opencv.android.OpenCVLoader

@HiltAndroidApp
class HashLensApp : Application() {
    @Inject
    lateinit var walletSession: WalletSession

    override fun onCreate() {
        super.onCreate()
        // Carrega o OpenCV nativo na abertura, para o primeiro pHash não pagar esse custo. Uma falha
        // não derruba o app (captura e leitura funcionam sem ele): o PerceptualHasher recusa o uso.
        if (!OpenCVLoader.initLocal()) Log.e(TAG, "OpenCV nativo não carregou")
        walletSession.initialize(this)
    }

    private companion object {
        const val TAG = "HashLensApp"
    }
}
