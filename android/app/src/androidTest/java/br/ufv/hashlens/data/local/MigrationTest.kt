package br.ufv.hashlens.data.local

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 020/T026: a migração 1 → 2 do banco acrescenta `device_binding.failureReason` (anulável) sem
 * perder vínculos.
 *
 * O banco v1 é criado com o SQL do schema exportado (`app/schemas/.../1.json`, assets do teste) e
 * aberto pelo Room v2, que roda a migração e valida o resultado contra as entidades atuais. Não usa
 * o `MigrationTestHelper` do `room-testing` 2.8.5: ele exige `kotlinx-serialization` 1.8, e o app
 * fixa a 1.7.3 (`AbstractMethodError` ao ler o schema).
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context: Context = instrumentation.targetContext

    @Before
    @After
    fun deleteDatabase() {
        context.deleteDatabase(DB)
    }

    @Test
    fun migra1Para2SemPerderDados() {
        createVersion1 { db ->
            db.execSQL(
                "INSERT INTO device_binding (walletAddress, publicKeyHex, deviceId, txHash, status) " +
                    "VALUES ('$WALLET', '$KEY', 7, '$TX', 'CONFIRMED')"
            )
        }

        withVersion2 { database ->
            database.openHelper.readableDatabase.query(
                "SELECT walletAddress, publicKeyHex, deviceId, txHash, status, failureReason FROM device_binding"
            ).use { c ->
                c.count shouldBe 1
                c.moveToFirst()
                c.getString(0) shouldBe WALLET
                c.getString(1) shouldBe KEY
                c.getInt(2) shouldBe 7
                c.getString(3) shouldBe TX
                c.getString(4) shouldBe "CONFIRMED"
                c.isNull(5) shouldBe true
            }
            database.openHelper.readableDatabase.version shouldBe 2
        }
    }

    @Test
    fun bancoMigradoGravaELeOMotivoDaFalha() {
        createVersion1 {}

        withVersion2 { database ->
            val dao = database.deviceBindingDao()
            val failed = DeviceBindingEntity(
                walletAddress = WALLET,
                publicKeyHex = KEY,
                status = RegistrationStatus.FAILED,
                failureReason = BindingFailure.NOT_ELIGIBLE_MODIFIED
            )
            runBlocking {
                dao.upsert(failed)
                dao.get(WALLET) shouldBe failed
            }
        }
    }

    /** Banco na versão 1, com as tabelas e os índices do schema exportado da 002. */
    private fun createVersion1(fill: (SQLiteDatabase) -> Unit) {
        val schema = instrumentation.context.assets
            .open("br.ufv.hashlens.data.local.AppDatabase/1.json")
            .bufferedReader()
            .use { JSONObject(it.readText()) }
            .getJSONObject("database")
        val db = SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(DB).apply { parentFile?.mkdirs() }, null)
        try {
            val entities = schema.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val entity = entities.getJSONObject(i)
                val table = entity.getString("tableName")
                db.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                val indices = entity.optJSONArray("indices") ?: continue
                for (j in 0 until indices.length()) {
                    db.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table))
                }
            }
            val setup = schema.getJSONArray("setupQueries")
            for (i in 0 until setup.length()) db.execSQL(setup.getString(i))
            db.execSQL(
                "INSERT OR REPLACE INTO room_master_table (id, identity_hash) VALUES (42, '${schema.getString(
                    "identityHash"
                )}')"
            )
            fill(db)
            db.version = 1
        } finally {
            db.close()
        }
    }

    private fun withVersion2(block: (AppDatabase) -> Unit) {
        val database = Room.databaseBuilder(context, AppDatabase::class.java, DB).build()
        try {
            block(database)
        } finally {
            database.close()
        }
    }

    private companion object {
        const val DB = "migration-test.db"
        const val WALLET = "0x1234567890abcdef1234567890abcdef12345678"
        val KEY = "04" + "ab".repeat(64)
        const val TX = "0xcd"
    }
}
