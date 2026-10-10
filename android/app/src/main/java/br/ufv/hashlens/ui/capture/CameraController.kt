package br.ufv.hashlens.ui.capture

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceRequest
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.lifecycle.awaitInstance
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import kotlin.math.max
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * CameraX da tela de captura (research R15): visor ([surfaceRequest]) e foto em resolução cheia.
 * A rotação vai junto da foto e é aplicada fisicamente pelo `JpegEncoder`.
 */
class CameraController(private val context: Context) {
    private val _surfaceRequest = MutableStateFlow<SurfaceRequest?>(null)

    /** Superfície pedida pelo visor; o `CameraXViewfinder` desenha nela. */
    val surfaceRequest: StateFlow<SurfaceRequest?> = _surfaceRequest.asStateFlow()

    private val preview = Preview.Builder().build().apply {
        setSurfaceProvider { _surfaceRequest.value = it }
    }
    private val imageCapture = ImageCapture.Builder()
        .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
        .build()

    /**
     * Liga a câmera da [lens] ao ciclo de vida da tela.
     *
     * @return se o aparelho tem as duas câmeras (para mostrar "Trocar câmera").
     * @throws IllegalArgumentException se não houver câmera para a [lens].
     */
    suspend fun bind(owner: LifecycleOwner, lens: CameraLens): Boolean {
        val provider = ProcessCameraProvider.awaitInstance(context)
        val selector = if (lens ==
            CameraLens.Front
        ) {
            CameraSelector.DEFAULT_FRONT_CAMERA
        } else {
            CameraSelector.DEFAULT_BACK_CAMERA
        }
        provider.unbindAll()
        provider.bindToLifecycle(owner, selector, preview, imageCapture)
        return provider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) &&
            provider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)
    }

    fun setFlash(mode: FlashMode) {
        imageCapture.flashMode = when (mode) {
            FlashMode.Off -> ImageCapture.FLASH_MODE_OFF
            FlashMode.On -> ImageCapture.FLASH_MODE_ON
            FlashMode.Auto -> ImageCapture.FLASH_MODE_AUTO
        }
    }

    /** Tira a foto: [onPhoto] recebe o bitmap em resolução cheia e a rotação informada pela câmera. */
    fun takePicture(onPhoto: (Bitmap, Int) -> Unit, onError: (Exception) -> Unit) {
        imageCapture.takePicture(
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    val rotation = image.imageInfo.rotationDegrees
                    val bitmap = image.use { it.toBitmap() }
                    onPhoto(bitmap, rotation)
                }

                override fun onError(exception: ImageCaptureException) = onError(exception)
            }
        )
    }
}

/** Miniatura do Acompanhamento: lado maior com [maxSide] px, já na orientação certa. */
fun Bitmap.toThumbnail(rotationDegrees: Int, maxSide: Int = THUMBNAIL_MAX_SIDE): ImageBitmap {
    val scale = maxSide.toFloat() / max(width, height)
    val matrix = Matrix().apply {
        postScale(scale, scale)
        postRotate(rotationDegrees.toFloat())
    }
    val scaled = Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
    return scaled.asImageBitmap()
}

private const val THUMBNAIL_MAX_SIDE = 480
