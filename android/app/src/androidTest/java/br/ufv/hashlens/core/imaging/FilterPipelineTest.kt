package br.ufv.hashlens.core.imaging

import androidx.test.ext.junit.runners.AndroidJUnit4
import br.ufv.hashlens.domain.model.EditOperation
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import org.junit.After
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith
import org.opencv.android.OpenCVLoader
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Point
import org.opencv.core.Rect
import org.opencv.core.Scalar
import org.opencv.imgproc.Imgproc

/**
 * T067: filtros da edição (hashing-spec §4) sobre `Mat` BGR de 8 bits.
 *
 * Instrumentado porque os filtros usam o OpenCV nativo do SDK Android. Para cada operação:
 * dimensões, 3 canais de 8 bits (para encadear e codificar em JPEG), determinismo e a imagem de
 * entrada intacta. Recorte e rotação mudam as dimensões; os demais as preservam.
 */
@RunWith(AndroidJUnit4::class)
class FilterPipelineTest {
    private val pipeline = FilterPipeline()
    private val created = mutableListOf<Mat>()

    @After
    fun release() = created.forEach(Mat::release)

    private fun Mat.tracked(): Mat = also { created += it }

    /**
     * 400 × 300 com conteúdo assimétrico: gradiente horizontal, um quadrado vermelho no canto
     * superior esquerdo e um retângulo azul no canto inferior direito (para conferir rotações).
     */
    private fun sample(width: Int = WIDTH, height: Int = HEIGHT): Mat {
        val image = Mat(height, width, CvType.CV_8UC3).tracked()
        for (x in 0 until width) {
            val level = (x * MAX_LEVEL / (width - 1)).toDouble()
            Imgproc.line(
                image,
                Point(x.toDouble(), 0.0),
                Point(x.toDouble(), height - 1.0),
                Scalar(level, level, level)
            )
        }
        Imgproc.rectangle(image, Rect(0, 0, MARK, MARK), RED, Imgproc.FILLED)
        Imgproc.rectangle(image, Rect(width - MARK * 2, height - MARK, MARK * 2, MARK), BLUE, Imgproc.FILLED)
        return image
    }

    private fun uniform(level: Double) = Mat(HEIGHT, WIDTH, CvType.CV_8UC3, Scalar(level, level, level)).tracked()

    private fun apply(source: Mat, vararg operations: EditOperation) =
        pipeline.apply(source, operations.toList()).tracked()

    private fun sameContent(a: Mat, b: Mat): Boolean =
        a.size() == b.size() && a.type() == b.type() && Core.norm(a, b, Core.NORM_INF) == 0.0

    private fun pixel(image: Mat, row: Int, col: Int): List<Int> = image.get(row, col).map { it.toInt() }

    @Test
    fun cadaFiltroDevolveTresCanaisDe8BitsDeFormaDeterministica() {
        val source = sample()
        val before = source.clone().tracked()

        assertSoftly {
            ALL_OPERATIONS.forEach { operation ->
                withClue(operation) {
                    val first = apply(source, operation)
                    val second = apply(source, operation)
                    first.type() shouldBe CvType.CV_8UC3
                    sameContent(first, second) shouldBe true
                    sameContent(source, before) shouldBe true
                }
            }
        }
    }

    @Test
    fun filtrosDeCorETexturaPreservamAsDimensoes() {
        val source = sample()

        assertSoftly {
            ALL_OPERATIONS.filterNot { it is EditOperation.Crop || it is EditOperation.Rotate90 }.forEach { operation ->
                withClue(operation) {
                    val result = apply(source, operation)
                    result.cols() shouldBe WIDTH
                    result.rows() shouldBe HEIGHT
                }
            }
        }
    }

    @Test
    fun recorteUsaFracoesDaImagemArredondadas() {
        // x = 0,1 × 400 = 40; y = 0,05 × 300 = 15; w = 0,8 × 400 = 320; h = 0,9 × 300 = 270
        val result = apply(sample(), EditOperation.Crop(0.1f, 0.05f, 0.8f, 0.9f))

        result.cols() shouldBe 320
        result.rows() shouldBe 270
        sameContent(result, sample().submat(Rect(40, 15, 320, 270)).tracked()) shouldBe true
    }

    @Test
    fun rotacaoGiraNoSentidoHorarioEmVoltasDe90Graus() {
        val source = sample()

        val quarter = apply(source, EditOperation.Rotate90(1))
        val half = apply(source, EditOperation.Rotate90(2))
        val threeQuarters = apply(source, EditOperation.Rotate90(3))

        assertSoftly {
            (quarter.cols() to quarter.rows()) shouldBe (HEIGHT to WIDTH)
            (half.cols() to half.rows()) shouldBe (WIDTH to HEIGHT)
            (threeQuarters.cols() to threeQuarters.rows()) shouldBe (HEIGHT to WIDTH)
            // O quadrado vermelho do canto superior esquerdo vai para o superior direito (horário)
            pixel(quarter, 1, HEIGHT - 2) shouldBe RED_PIXEL
            pixel(half, HEIGHT - 2, WIDTH - 2) shouldBe RED_PIXEL
            pixel(threeQuarters, WIDTH - 2, 1) shouldBe RED_PIXEL
            // Quatro quartos de volta voltam à imagem original
            sameContent(apply(quarter, EditOperation.Rotate90(3)), source) shouldBe true
        }
    }

    @Test
    fun operacoesSaoAplicadasNaOrdemDaLista() {
        val source = sample()

        val cropThenRotate = apply(source, EditOperation.Crop(0f, 0f, 0.5f, 1f), EditOperation.Rotate90(1))
        val rotateThenCrop = apply(source, EditOperation.Rotate90(1), EditOperation.Crop(0f, 0f, 0.5f, 1f))

        (cropThenRotate.cols() to cropThenRotate.rows()) shouldBe (HEIGHT to WIDTH / 2)
        (rotateThenCrop.cols() to rotateThenCrop.rows()) shouldBe (HEIGHT / 2 to WIDTH)
    }

    @Test
    fun listaVaziaDevolveUmaCopiaIgualAEntrada() {
        val source = sample()

        val result = pipeline.apply(source, emptyList()).tracked()

        sameContent(result, source) shouldBe true
        (result.nativeObj == source.nativeObj) shouldBe false
    }

    @Test
    fun brilhoSomaODeltaComSaturacao() {
        assertSoftly {
            pixel(apply(uniform(100.0), EditOperation.Brightness(30)), 10, 10) shouldBe listOf(130, 130, 130)
            pixel(apply(uniform(200.0), EditOperation.Brightness(100)), 10, 10) shouldBe listOf(255, 255, 255)
            pixel(apply(uniform(50.0), EditOperation.Brightness(-100)), 10, 10) shouldBe listOf(0, 0, 0)
        }
    }

    @Test
    fun pretoEBrancoEBordasDeixamOsTresCanaisIguais() {
        val source = sample()

        assertSoftly {
            listOf(EditOperation.Grayscale, EditOperation.Edges(50, 150)).forEach { operation ->
                withClue(operation) {
                    val channels = mutableListOf<Mat>().also { Core.split(apply(source, operation), it) }
                    channels.forEach { it.tracked() }
                    sameContent(channels[0], channels[1]) shouldBe true
                    sameContent(channels[1], channels[2]) shouldBe true
                }
            }
        }
    }

    @Test
    fun bordasMarcamSoPixelsBrancosOuPretos() {
        val edges = apply(sample(), EditOperation.Edges(50, 150))

        val values = (0 until edges.rows() step 7).flatMap { row ->
            (0 until edges.cols() step 7).flatMap { col -> pixel(edges, row, col) }
        }

        values.filterNot { it == 0 || it == MAX_LEVEL }.shouldBeEmpty()
    }

    @Test
    fun previaReduzOLadoMaiorEMantemAsProporcoesDoResultadoFinal() {
        val large = sample(width = 4000, height = 3000)
        val operations = listOf(EditOperation.Crop(0f, 0f, 0.5f, 0.5f), EditOperation.Rotate90(1))

        val preview = pipeline.preview(large, operations, maxSide = 1000).tracked()
        val full = pipeline.apply(large, operations).tracked()

        // Final: 2000 × 1500 recortado e girado → 1500 × 2000; a prévia sai a 1/4: 375 × 500
        (full.cols() to full.rows()) shouldBe (1500 to 2000)
        (preview.cols() to preview.rows()) shouldBe (375 to 500)
    }

    @Test
    fun previaDeImagemPequenaNaoAmplia() {
        val preview = pipeline.preview(sample(), emptyList(), maxSide = 1000).tracked()

        (preview.cols() to preview.rows()) shouldBe (WIDTH to HEIGHT)
    }

    companion object {
        private const val WIDTH = 400
        private const val HEIGHT = 300
        private const val MARK = 40
        private const val MAX_LEVEL = 255

        // BGR
        private val RED = Scalar(0.0, 0.0, 255.0)
        private val BLUE = Scalar(255.0, 0.0, 0.0)
        private val RED_PIXEL = listOf(0, 0, 255)

        /** Uma operação de cada filtro de hashing-spec §4. */
        private val ALL_OPERATIONS = listOf(
            EditOperation.Brightness(30),
            EditOperation.Contrast(1.25f),
            EditOperation.Grayscale,
            EditOperation.Sepia,
            EditOperation.Blur(7),
            EditOperation.Sharpen,
            EditOperation.Edges(50, 150),
            EditOperation.Crop(0.1f, 0.05f, 0.8f, 0.9f),
            EditOperation.Rotate90(1)
        )

        @JvmStatic
        @BeforeClass
        fun loadOpenCv() {
            check(OpenCVLoader.initLocal()) { "OpenCV nativo não carregou" }
        }
    }
}
