package br.ufv.hashlens.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * Acervo local (data-model §2). A blockchain é a fonte da verdade; aqui ficam os arquivos do
 * aparelho, o andamento de cada registro e os vínculos de aparelho por carteira.
 */
@Database(entities = [LocalImageEntity::class, DeviceBindingEntity::class], version = 1)
abstract class AppDatabase : RoomDatabase() {
    abstract fun localImageDao(): LocalImageDao

    abstract fun deviceBindingDao(): DeviceBindingDao

    companion object {
        const val NAME = "hashlens.db"
    }
}
