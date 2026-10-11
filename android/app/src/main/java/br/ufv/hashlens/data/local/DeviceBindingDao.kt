package br.ufv.hashlens.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface DeviceBindingDao {
    /** @param walletAddress em minúsculas. */
    @Query("SELECT * FROM device_binding WHERE walletAddress = :walletAddress")
    suspend fun get(walletAddress: String): DeviceBindingEntity?

    /** Insere ou substitui o vínculo da carteira (chave nova após perda, R25). */
    @Upsert
    suspend fun upsert(binding: DeviceBindingEntity)
}
