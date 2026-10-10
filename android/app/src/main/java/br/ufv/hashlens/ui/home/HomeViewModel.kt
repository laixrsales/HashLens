package br.ufv.hashlens.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.ufv.hashlens.data.local.LocalImageDao
import br.ufv.hashlens.data.local.RegistrationStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/** Indicador "N registrando" do Início (ux.md §4.2). A carteira fica com o `WalletViewModel`. */
@HiltViewModel
class HomeViewModel @Inject constructor(images: LocalImageDao) : ViewModel() {
    val pendingCount: StateFlow<Int> = images
        .observeCountByStatus(listOf(RegistrationStatus.AWAITING_WALLET, RegistrationStatus.PENDING))
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), 0)

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
