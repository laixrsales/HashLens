package br.ufv.hashlens.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface LocalImageDao {
    /** @return o `localId` gerado. Falha se o SHA-256 já estiver no acervo (índice único). */
    @Insert
    suspend fun insert(image: LocalImageEntity): Long

    @Update
    suspend fun update(image: LocalImageEntity)

    @Query("SELECT * FROM local_image WHERE localId = :localId")
    suspend fun getById(localId: Long): LocalImageEntity?

    @Query("SELECT * FROM local_image WHERE sha256Hex = :sha256Hex")
    suspend fun getBySha256(sha256Hex: String): LocalImageEntity?

    @Query("SELECT * FROM local_image WHERE status IN (:statuses) ORDER BY createdAt")
    suspend fun getByStatus(statuses: List<RegistrationStatus>): List<LocalImageEntity>

    @Query("SELECT * FROM local_image WHERE localId = :localId")
    fun observeById(localId: Long): Flow<LocalImageEntity?>

    /** Acervo, mais recentes primeiro. */
    @Query("SELECT * FROM local_image ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<LocalImageEntity>>
}
