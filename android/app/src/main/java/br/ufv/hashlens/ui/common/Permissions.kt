package br.ufv.hashlens.ui.common

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import br.ufv.hashlens.R

/** Situação de uma permissão em tempo de execução, do ponto de vista da tela. */
enum class PermissionStatus {
    Granted,

    /** Ainda não pedida nesta sessão: a tela pede direto, sem explicação. */
    NotRequested,

    /** Negada, mas o sistema ainda mostra o pedido: explicar e oferecer pedir de novo. */
    Denied,

    /** Negada de vez ("não perguntar novamente"): explicar e levar às configurações. */
    PermanentlyDenied;

    /** Explicação a exibir para a câmera; `null` quando não há o que explicar. */
    val cameraExplanation: PermissionExplanation?
        get() = when (this) {
            Granted, NotRequested -> null
            Denied -> PermissionExplanation(
                title = R.string.camera_permission_title,
                message = R.string.camera_permission_message,
                action = R.string.camera_permission_allow,
                opensSettings = false
            )
            PermanentlyDenied -> PermissionExplanation(
                title = R.string.camera_permission_title,
                message = R.string.camera_permission_message_settings,
                action = R.string.permission_open_settings,
                opensSettings = true
            )
        }

    companion object {
        /**
         * O Android não distingue "nunca pedida" de "negada de vez" (`shouldShowRequestPermissionRationale`
         * é `false` nos dois casos); [requested] desfaz a ambiguidade.
         */
        fun of(granted: Boolean, requested: Boolean, shouldShowRationale: Boolean): PermissionStatus = when {
            granted -> Granted
            shouldShowRationale -> Denied
            requested -> PermanentlyDenied
            else -> NotRequested
        }
    }
}

/** Textos da explicação (ux.md §4.4) e a ação que a acompanha. */
data class PermissionExplanation(
    @StringRes val title: Int,
    @StringRes val message: Int,
    @StringRes val action: Int,
    /** `true`: a ação abre as configurações do app; `false`: pede a permissão de novo. */
    val opensSettings: Boolean
)

/** Estado de uma permissão para a tela: [status] atual, [request] e [openSettings]. */
@Stable
class PermissionState internal constructor(
    status: PermissionStatus,
    private val onRequest: () -> Unit,
    private val onOpenSettings: () -> Unit
) {
    var status: PermissionStatus by mutableStateOf(status)
        internal set

    fun request() = onRequest()

    fun openSettings() = onOpenSettings()

    /** Executa a ação da explicação atual (pedir de novo ou abrir as configurações). */
    fun onExplanationAction() = if (status == PermissionStatus.PermanentlyDenied) openSettings() else request()
}

/** Permissão da câmera para a tela de captura. Reavalia ao voltar das configurações. */
@Composable
fun rememberCameraPermissionState(): PermissionState = rememberPermissionState(Manifest.permission.CAMERA)

@Composable
fun rememberPermissionState(permission: String): PermissionState {
    val context = LocalContext.current
    var requested by rememberSaveable(permission) { mutableStateOf(false) }
    val currentStatus = { context.permissionStatus(permission, requested) }

    lateinit var state: PermissionState
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        requested = true
        state.status = currentStatus()
    }
    state = remember(permission) {
        PermissionState(
            status = currentStatus(),
            onRequest = { launcher.launch(permission) },
            onOpenSettings = { context.startActivity(appSettingsIntent(context.packageName)) }
        )
    }

    // A pessoa pode ter mudado a permissão nas configurações enquanto o app estava em segundo plano
    LifecycleResumeEffect(state) {
        state.status = currentStatus()
        onPauseOrDispose { }
    }
    return state
}

/** Tela de detalhes do app nas configurações do sistema, onde se concede a permissão negada de vez. */
fun appSettingsIntent(packageName: String): Intent =
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

private fun Context.permissionStatus(permission: String, requested: Boolean): PermissionStatus = PermissionStatus.of(
    granted = ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED,
    requested = requested,
    shouldShowRationale = findActivity()?.let {
        ActivityCompat.shouldShowRequestPermissionRationale(it, permission)
    } ?: false
)

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
