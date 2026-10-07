package br.ufv.hashlens.core.hashing

import br.ufv.hashlens.domain.model.Sha256
import java.io.InputStream
import java.security.MessageDigest
import javax.inject.Inject

/** Hash exato (hashing-spec §1): SHA-256 de todos os bytes do arquivo, lidos em blocos. */
class Sha256Hasher @Inject constructor() {
    /** Lê [input] até o fim sem fechá-lo; quem abriu o stream é quem o fecha. */
    fun hash(input: InputStream): Sha256 {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(BUFFER_SIZE)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            digest.update(buffer, 0, read)
        }
        return Sha256(digest.digest().toHexString())
    }

    private companion object {
        const val BUFFER_SIZE = 64 * 1024
    }
}
