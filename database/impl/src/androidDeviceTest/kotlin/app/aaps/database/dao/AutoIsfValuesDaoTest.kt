package app.aaps.database.dao

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.room.useReaderConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.aaps.database.AppDatabase
import app.aaps.database.di.databaseMigrations
import app.aaps.database.entities.AutoIsfValues
import app.aaps.database.entities.TABLE_AUTOISF_VALUES
import app.aaps.database.entities.TABLE_TOTAL_DAILY_DOSES
import kotlinx.coroutines.test.runTest
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AutoIsfValuesDaoTest {

    private val context: Context by lazy { ApplicationProvider.getApplicationContext() }
    private fun createDatabase() =
        Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .setDriver(BundledSQLiteDriver())
            .build()

    private suspend fun insertAndFind(database: AppDatabase) {
        val aiv1 = createAutoIsfValues()
        val id = database.autoIsfValuesDao.insert(aiv1)
        val aiv2 = database.autoIsfValuesDao.findById(id)
        Assert.assertTrue(aiv1.contentEqualsTo(aiv2!!))
    }

    private fun AutoIsfValues.contentEqualsTo(other: AutoIsfValues): Boolean =
        this === other || (
            timestamp == other.timestamp &&
                acceIsf == other.acceIsf &&
                bgIsf == other.bgIsf &&
                ppIsf == other.ppIsf &&
                driftIsf == other.driftIsf &&
                duraIsf == other.duraIsf &&
                finalIsf == other.finalIsf &&
                iobThEffective == other.iobThEffective &&
                isValid == other.isValid)

    @Test
    fun new_insertAndFind() = runTest {
        createDatabase().also { db ->
            insertAndFind(db)
            db.close()
        }
    }

    @Test
    fun migrate_fromUpstream_createsTableAndIndex() = runTest {
        val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), AppDatabase::class.java)
        val supportDb = helper.createDatabase(TEST_DB_NAME, 36)
        supportDb.close()
        Room.databaseBuilder(context, AppDatabase::class.java, TEST_DB_NAME)
            .setDriver(BundledSQLiteDriver())
            .addMigrations(*databaseMigrations)
            .build().also { db ->
                val objects = db.useReaderConnection { connection ->
                    connection.usePrepared("SELECT type, name FROM sqlite_master") { statement ->
                        buildList { while (statement.step()) add(statement.getText(0) to statement.getText(1)) }
                    }
                }
                Assert.assertTrue(objects.contains("table" to TABLE_AUTOISF_VALUES))
                Assert.assertTrue(objects.contains("index" to "index_autoIsfValues_timestamp"))
                insertAndFind(db)
                db.close()
            }
    }

    /**
     * A database of aisf 3.2.x: AAPS 3.4 schema 31, plus the autoIsfValues table with an index on `id`,
     * stored as version 32. Its version 32 is not the version 32 of AAPS 4.0, which added `carbInsulin`
     * to the TDD table. The upgrade must end in the same schema as a fresh database, or Room refuses it.
     */
    @Test
    fun migrate_fromAisf32_endsInTheSameSchema() = runTest {
        val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), AppDatabase::class.java)
        val supportDb = helper.createDatabase(TEST_DB_NAME, 31)
        supportDb.execSQL(
            "CREATE TABLE IF NOT EXISTS `$TABLE_AUTOISF_VALUES` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `timestamp` INTEGER NOT NULL, " +
                "`acceIsf` DOUBLE NOT NULL, `bgIsf` DOUBLE NOT NULL, `ppIsf` DOUBLE NOT NULL, `driftIsf` DOUBLE NOT NULL, `duraIsf` DOUBLE NOT NULL, " +
                "`finalIsf` DOUBLE NOT NULL, `iobThEffective` DOUBLE NOT NULL, `utcOffset` INTEGER NOT NULL, `version` INTEGER NOT NULL, " +
                "`dateCreated` INTEGER NOT NULL, `isValid` INTEGER NOT NULL, `referenceId` INTEGER, `nightscoutSystemId` TEXT, `nightscoutId` TEXT, " +
                "`pumpType` TEXT, `pumpSerial` TEXT, `temporaryId` INTEGER, `pumpId` INTEGER, `startId` INTEGER, `endId` INTEGER)"
        )
        supportDb.execSQL("CREATE INDEX IF NOT EXISTS `index_autoIsfValues_id` ON `$TABLE_AUTOISF_VALUES` (`id`)")
        supportDb.execSQL("CREATE INDEX IF NOT EXISTS `index_autoIsfValues_timestamp` ON `$TABLE_AUTOISF_VALUES` (`timestamp`)")
        supportDb.execSQL(
            "INSERT INTO `$TABLE_AUTOISF_VALUES` (timestamp, acceIsf, bgIsf, ppIsf, driftIsf, duraIsf, finalIsf, iobThEffective, utcOffset, version, dateCreated, isValid) " +
                "VALUES (1700000000000, 1.1, 1.2, 1.0, 1.0, 1.3, 1.25, 4.5, 0, 0, 0, 1)"
        )
        supportDb.version = 32
        supportDb.close()

        Room.databaseBuilder(context, AppDatabase::class.java, TEST_DB_NAME)
            .setDriver(BundledSQLiteDriver())
            .addMigrations(*databaseMigrations)
            .build().also { db ->
                val tddColumns = db.useReaderConnection { connection ->
                    connection.usePrepared("PRAGMA table_info(`$TABLE_TOTAL_DAILY_DOSES`)") { statement ->
                        buildList { while (statement.step()) add(statement.getText(1)) }
                    }
                }
                Assert.assertTrue(tddColumns.contains("carbInsulin"))
                // The old autoISF row is kept
                Assert.assertEquals(1, db.autoIsfValuesDao.getFromTime(0).size)
                insertAndFind(db)
                db.close()
            }
    }

    @Test
    fun getFromTimeToTime() = runTest {
        val db = createDatabase()
        try {
            val dao = db.autoIsfValuesDao
            val timestamp = System.currentTimeMillis()
            val aiv1 = createAutoIsfValues(timestamp = timestamp, finalIsf = 1.1)
            val aiv2 = createAutoIsfValues(timestamp = timestamp + 1, finalIsf = 1.2)
            val aiv3 = createAutoIsfValues(timestamp = timestamp + 2, finalIsf = 1.3)
            dao.insertNewEntry(aiv1)
            dao.insertNewEntry(aiv2)
            dao.insertNewEntry(aiv3)

            Assert.assertEquals(listOf(aiv1, aiv2, aiv3), dao.getFromTimeToTime(timestamp, timestamp + 2))
            Assert.assertEquals(listOf(aiv2, aiv3), dao.getFromTime(timestamp + 1))
            Assert.assertTrue(dao.getFromTimeToTime(timestamp + 3, timestamp + 10).isEmpty())
        } finally {
            db.close()
        }
    }

    companion object {

        private const val TEST_DB_NAME = "testDatabaseAutoIsf"

        fun createAutoIsfValues(timestamp: Long? = null, finalIsf: Double = 1.2) =
            AutoIsfValues(
                timestamp = timestamp ?: System.currentTimeMillis(),
                acceIsf = 1.1,
                bgIsf = 1.0,
                ppIsf = 1.0,
                driftIsf = 1.0,
                duraIsf = 1.0,
                finalIsf = finalIsf,
                iobThEffective = 3.0
            )
    }
}
