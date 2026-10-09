package br.ufv.hashlens.testing

import androidx.room.Room
import br.ufv.hashlens.data.local.AppDatabase
import org.robolectric.RuntimeEnvironment

/** Banco Room em memória para testes com Robolectric; cada chamada cria um banco vazio. */
fun inMemoryDatabase(): AppDatabase =
    Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), AppDatabase::class.java)
        .allowMainThreadQueries()
        .build()
