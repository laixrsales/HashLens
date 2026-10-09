package br.ufv.hashlens.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Acervo local (Room). O `AppDatabase` e os DAOs são criados em T047 e fornecidos aqui
 * (`Room.databaseBuilder(...)` como `@Singleton`, um `@Provides` por DAO).
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule
