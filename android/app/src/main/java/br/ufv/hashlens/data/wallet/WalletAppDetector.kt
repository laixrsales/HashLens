package br.ufv.hashlens.data.wallet

import android.content.Context
import android.content.Intent
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** Há alguma carteira compatível com WalletConnect instalada? (folha "Conectar carteira", ux.md §4.3) */
fun interface WalletAppDetector {
    fun hasWalletApp(): Boolean
}

/** Carteiras WalletConnect atendem links `wc:`; o manifesto declara a consulta em `<queries>`. */
class PackageManagerWalletAppDetector @Inject constructor(@ApplicationContext private val context: Context) :
    WalletAppDetector {
    override fun hasWalletApp(): Boolean =
        context.packageManager.queryIntentActivities(Intent(Intent.ACTION_VIEW, Uri.parse("wc:")), 0).isNotEmpty()
}
