package br.ufv.hashlens.core.imaging

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.exifinterface.media.ExifInterface
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.ints.shouldBeLessThanOrEqual
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import java.io.ByteArrayInputStream
import java.io.File
import java.time.ZoneOffset
import java.time.ZonedDateTime
import kotlin.math.abs
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** T031: [JpegEncoder] (rotação física, q=95, EXIF mínimo, sem GPS) e [ImageDecoder] (aplica a orientação EXIF). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
// Application simples: o HashLensApp carrega o OpenCV nativo, que não existe na JVM
@Config(application = Application::class)
class JpegEncoderTest {
    private val context: Context = RuntimeEnvironment.getApplication()
    private val encoder = JpegEncoder(context)
    private val decoder = ImageDecoder()
    private val capturedAt = ZonedDateTime.of(2026, 10, 4, 14, 32, 5, 0, ZoneOffset.ofHours(-3))

    @Test
    fun `sem rotação mantém dimensões e quadrantes`() {
        val bitmap = decode(encoder.encode(quadrants(), rotationDegrees = 0, capturedAt))

        bitmap.width shouldBe WIDTH
        bitmap.height shouldBe HEIGHT
        bitmap.shouldHaveQuadrants(RED, GREEN, BLUE, YELLOW)
    }

    @Test
    fun `gira os pixels no sentido horário`() {
        val rotated90 = decode(encoder.encode(quadrants(), rotationDegrees = 90, capturedAt))
        rotated90.width shouldBe HEIGHT
        rotated90.height shouldBe WIDTH
        rotated90.shouldHaveQuadrants(BLUE, RED, YELLOW, GREEN)

        decode(encoder.encode(quadrants(), rotationDegrees = 180, capturedAt))
            .shouldHaveQuadrants(YELLOW, BLUE, GREEN, RED)
        decode(encoder.encode(quadrants(), rotationDegrees = 270, capturedAt))
            .shouldHaveQuadrants(GREEN, YELLOW, RED, BLUE)
    }

    @Test
    fun `grava Orientation 1, data, fuso e software, sem GPS`() {
        val exif = ExifInterface(ByteArrayInputStream(encoder.encode(quadrants(), rotationDegrees = 90, capturedAt)))

        exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, -1) shouldBe ExifInterface.ORIENTATION_NORMAL
        exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL) shouldBe "2026:10:04 14:32:05"
        exif.getAttribute(ExifInterface.TAG_OFFSET_TIME_ORIGINAL) shouldBe "-03:00"
        exif.getAttribute(ExifInterface.TAG_SOFTWARE) shouldBe JpegEncoder.SOFTWARE
        exif.latLong.shouldBeNull()
        GPS_TAGS.filter { exif.getAttribute(it) != null }.shouldBeEmpty()
    }

    @Test
    fun `codifica com qualidade 95`() {
        val source = quadrants()
        val reference = File.createTempFile("ref-", ".jpg", context.cacheDir).apply {
            outputStream().use { source.compress(Bitmap.CompressFormat.JPEG, JpegEncoder.QUALITY, it) }
        }

        val encoded = decode(encoder.encode(source, rotationDegrees = 0, capturedAt))

        encoded.sameAs(BitmapFactory.decodeFile(reference.path)) shouldBe true
    }

    @Test
    fun `não deixa arquivos temporários no cache`() {
        val before = context.cacheDir.list().orEmpty().toSet()
        encoder.encode(quadrants(), rotationDegrees = 90, capturedAt)
        context.cacheDir.list().orEmpty().toSet() shouldBe before
    }

    @Test
    fun `recusa rotação fora de 0, 90, 180 e 270`() {
        shouldThrow<IllegalArgumentException> { encoder.encode(quadrants(), rotationDegrees = 45, capturedAt) }
    }

    @Test
    fun `decodificador aplica a orientação EXIF`() {
        val upright = encoder.encode(quadrants(), rotationDegrees = 0, capturedAt)

        decoder.decode(withOrientation(upright, ExifInterface.ORIENTATION_ROTATE_90)).run {
            width shouldBe HEIGHT
            height shouldBe WIDTH
            shouldHaveQuadrants(BLUE, RED, YELLOW, GREEN)
        }
        decoder.decode(withOrientation(upright, ExifInterface.ORIENTATION_ROTATE_180))
            .shouldHaveQuadrants(YELLOW, BLUE, GREEN, RED)
        decoder.decode(withOrientation(upright, ExifInterface.ORIENTATION_ROTATE_270))
            .shouldHaveQuadrants(GREEN, YELLOW, RED, BLUE)
        decoder.decode(withOrientation(upright, ExifInterface.ORIENTATION_FLIP_HORIZONTAL))
            .shouldHaveQuadrants(GREEN, RED, YELLOW, BLUE)
        decoder.decode(withOrientation(upright, ExifInterface.ORIENTATION_FLIP_VERTICAL))
            .shouldHaveQuadrants(BLUE, YELLOW, RED, GREEN)
        decoder.decode(withOrientation(upright, ExifInterface.ORIENTATION_TRANSPOSE))
            .shouldHaveQuadrants(RED, BLUE, GREEN, YELLOW)
        decoder.decode(withOrientation(upright, ExifInterface.ORIENTATION_TRANSVERSE))
            .shouldHaveQuadrants(YELLOW, GREEN, BLUE, RED)
    }

    @Test
    fun `decodificador mantém imagem sem EXIF e recusa bytes inválidos`() {
        val reference = File.createTempFile("plain-", ".jpg", context.cacheDir).apply {
            outputStream().use { quadrants().compress(Bitmap.CompressFormat.JPEG, JpegEncoder.QUALITY, it) }
        }
        decoder.decode(reference.readBytes()).shouldHaveQuadrants(RED, GREEN, BLUE, YELLOW)

        shouldThrow<IllegalArgumentException> { decoder.decode(byteArrayOf(1, 2, 3)) }
    }

    /** Imagem [WIDTH]×[HEIGHT] com um quadrante de cada cor, para identificar rotações e espelhamentos. */
    private fun quadrants(): Bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888).apply {
        val canvas = Canvas(this)
        val paint = Paint()
        val halfW = WIDTH / 2f
        val halfH = HEIGHT / 2f
        listOf(RED to (0f to 0f), GREEN to (halfW to 0f), BLUE to (0f to halfH), YELLOW to (halfW to halfH))
            .forEach { (color, origin) ->
                paint.color = color
                canvas.drawRect(origin.first, origin.second, origin.first + halfW, origin.second + halfH, paint)
            }
    }

    private fun decode(bytes: ByteArray): Bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)

    private fun withOrientation(jpeg: ByteArray, orientation: Int): ByteArray {
        val file = File.createTempFile("orient-", ".jpg", context.cacheDir)
        file.writeBytes(jpeg)
        ExifInterface(file).apply {
            setAttribute(ExifInterface.TAG_ORIENTATION, orientation.toString())
            saveAttributes()
        }
        return file.readBytes().also { file.delete() }
    }

    /** Confere a cor no centro de cada quadrante, com tolerância para a compressão JPEG. */
    private fun Bitmap.shouldHaveQuadrants(topLeft: Int, topRight: Int, bottomLeft: Int, bottomRight: Int) {
        val x = listOf(width / 4, width * 3 / 4)
        val y = listOf(height / 4, height * 3 / 4)
        mapOf(
            "superior esquerdo" to (getPixel(x[0], y[0]) to topLeft),
            "superior direito" to (getPixel(x[1], y[0]) to topRight),
            "inferior esquerdo" to (getPixel(x[0], y[1]) to bottomLeft),
            "inferior direito" to (getPixel(x[1], y[1]) to bottomRight)
        ).forEach { (quadrant, colors) ->
            val (actual, expected) = colors
            withClue("quadrante $quadrant") { colorDistance(actual, expected) shouldBeLessThanOrEqual COLOR_TOLERANCE }
        }
    }

    private fun colorDistance(a: Int, b: Int): Int = maxOf(
        abs(Color.red(a) - Color.red(b)),
        abs(Color.green(a) - Color.green(b)),
        abs(Color.blue(a) - Color.blue(b))
    )

    private companion object {
        const val WIDTH = 64
        const val HEIGHT = 32
        const val COLOR_TOLERANCE = 40
        val RED = Color.rgb(220, 30, 30)
        val GREEN = Color.rgb(30, 200, 30)
        val BLUE = Color.rgb(30, 30, 220)
        val YELLOW = Color.rgb(230, 220, 30)
        val GPS_TAGS = listOf(
            ExifInterface.TAG_GPS_LATITUDE,
            ExifInterface.TAG_GPS_LONGITUDE,
            ExifInterface.TAG_GPS_ALTITUDE,
            ExifInterface.TAG_GPS_TIMESTAMP,
            ExifInterface.TAG_GPS_DATESTAMP
        )
    }
}
