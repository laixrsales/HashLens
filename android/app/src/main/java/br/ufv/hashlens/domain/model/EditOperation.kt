package br.ufv.hashlens.domain.model

/** Operação de edição (data-model §3); faixas válidas em hashing-spec §4. */
sealed interface EditOperation {
    /** Delta de brilho, −100..+100. */
    data class Brightness(val delta: Int) : EditOperation

    /** Fator de contraste, 0.50..2.00. */
    data class Contrast(val factor: Float) : EditOperation

    data object Grayscale : EditOperation

    data object Sepia : EditOperation

    /** Desfoque com kernel ímpar, 3..25. */
    data class Blur(val kernel: Int) : EditOperation

    data object Sharpen : EditOperation

    /** Detecção de bordas com limiares 0..255. */
    data class Edges(val low: Int, val high: Int) : EditOperation

    /** Recorte em frações da imagem (0..1), com 3 casas na serialização. */
    data class Crop(val x: Float, val y: Float, val w: Float, val h: Float) : EditOperation

    /** Rotação em voltas de 90° no sentido horário, 1..3. */
    data class Rotate90(val turns: Int) : EditOperation
}
