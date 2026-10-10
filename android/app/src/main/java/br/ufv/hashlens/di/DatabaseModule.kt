package br.ufv.hashlens.di

import android.content.Context
import androidx.room.Room
import br.ufv.hashlens.data.local.AppDatabase
import br.ufv.hashlens.data.local.DeviceBindingDao
import br.ufv.hashlens.data.local.LocalImageDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Acervo local (Room). */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun appDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.NAME).build()

    @Provides
    fun localImageDao(db: AppDatabase): LocalImageDao = db.localImageDao()

    @Provides
    fun deviceBindingDao(db: AppDatabase): DeviceBindingDao = db.deviceBindingDao()
}
