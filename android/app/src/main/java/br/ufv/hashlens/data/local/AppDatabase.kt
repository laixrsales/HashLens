package br.ufv.hashlens.data.local

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * Acervo local (data-model §2). A blockchain é a fonte da verdade; aqui ficam os arquivos do
 * aparelho, o andamento de cada registro e os vínculos de aparelho por carteira.
 *
 * Versões: 1 (002); 2 acrescenta `device_binding.failureReason`, anulável, sem perda de dados
 * (020 data-model §2.1).
 */
@Database(
    entities = [LocalImageEntity::class, DeviceBindingEntity::class],
    version = 2,
    autoMigrations = [AutoMigration(from = 1, to = 2)]
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun localImageDao(): LocalImageDao

    abstract fun deviceBindingDao(): DeviceBindingDao

    companion object {
        const val NAME = "hashlens.db"
    }
}
