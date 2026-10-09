package br.ufv.hashlens.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.Clock

/** Relógio injetável: os testes do prazo de pendência (research R23) usam um `Clock` fixo. */
@Module
@InstallIn(SingletonComponent::class)
object ClockModule {
    @Provides
    fun clock(): Clock = Clock.systemUTC()
}
