package app.aaps.database

import androidx.room.Room
import androidx.room.useReaderConnection
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import app.aaps.database.di.databaseMigrations
import app.aaps.database.entities.TABLE_AUTOISF_VALUES
import app.aaps.database.entities.TABLE_TOTAL_DAILY_DOSES
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test

/**
 * autoISF: the database upgrade of aisf users and of AAPS users must end in the same schema.
 *
 * aisf 3.2.x (based on AAPS 3.4) stored its `autoIsfValues` table as database version 32. AAPS 4.0 also
 * has a version 32, but there it added the column `carbInsulin` to the TDD table. Room starts an aisf
 * database at the migration 32 -> 33 and would never add that column, and then refuses the database at
 * its schema check. These tests open real database files with Room on the JVM, so the schema check of
 * Room runs exactly as on a phone.
 *
 * The old databases are built from the schema JSON files that Room exported for each version.
 */
class AutoIsfMigrationTest {

    private val directory = createTempDirectory("aaps-autoisf-migration-test")
    private val path = "$directory/aaps.db"

    @AfterTest
    fun tearDown() {
        directory.toFile().deleteRecursively()
    }

    @Test
    fun `a database of aisf 3_2 (version 32 with autoIsfValues) is upgraded`() = runTest {
        BundledSQLiteDriver().open(path).also { connection ->
            createFromSchema(connection, 31)
            // What aisf 3.2.x added in its version 32
            connection.execSQL(
                "CREATE TABLE IF NOT EXISTS `$TABLE_AUTOISF_VALUES` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `timestamp` INTEGER NOT NULL, " +
                    "`acceIsf` DOUBLE NOT NULL, `bgIsf` DOUBLE NOT NULL, `ppIsf` DOUBLE NOT NULL, `driftIsf` DOUBLE NOT NULL, `duraIsf` DOUBLE NOT NULL, " +
                    "`finalIsf` DOUBLE NOT NULL, `iobThEffective` DOUBLE NOT NULL, `utcOffset` INTEGER NOT NULL, `version` INTEGER NOT NULL, " +
                    "`dateCreated` INTEGER NOT NULL, `isValid` INTEGER NOT NULL, `referenceId` INTEGER, `nightscoutSystemId` TEXT, `nightscoutId` TEXT, " +
                    "`pumpType` TEXT, `pumpSerial` TEXT, `temporaryId` INTEGER, `pumpId` INTEGER, `startId` INTEGER, `endId` INTEGER)"
            )
            connection.execSQL("CREATE INDEX IF NOT EXISTS `index_autoIsfValues_id` ON `$TABLE_AUTOISF_VALUES` (`id`)")
            connection.execSQL("CREATE INDEX IF NOT EXISTS `index_autoIsfValues_timestamp` ON `$TABLE_AUTOISF_VALUES` (`timestamp`)")
            connection.execSQL(
                "INSERT INTO `$TABLE_AUTOISF_VALUES` (timestamp, acceIsf, bgIsf, ppIsf, driftIsf, duraIsf, finalIsf, iobThEffective, utcOffset, version, dateCreated, isValid) " +
                    "VALUES (1700000000000, 1.1, 1.2, 1.0, 1.0, 1.3, 1.25, 4.5, 0, 0, 0, 1)"
            )
            connection.execSQL("PRAGMA user_version = 32")
            connection.close()
        }

        val database = openWithRoom()
        try {
            // The old AutoISF row is still there, and Room accepted the schema to get it
            val rows = database.autoIsfValuesDao.getFromTime(0)
            assertThat(rows).hasSize(1)
            assertThat(rows[0].finalIsf).isEqualTo(1.25)
            assertThat(columnsOf(database, TABLE_TOTAL_DAILY_DOSES)).contains("carbInsulin")
            assertThat(indexesOf(database, TABLE_AUTOISF_VALUES)).doesNotContain("index_autoIsfValues_id")
        } finally {
            database.close()
        }
    }

    @Test
    fun `a database of AAPS 4_0 (version 36) gets the autoIsfValues table`() = runTest {
        BundledSQLiteDriver().open(path).also { connection ->
            createFromSchema(connection, 36)
            connection.execSQL("PRAGMA user_version = 36")
            connection.close()
        }

        val database = openWithRoom()
        try {
            assertThat(database.autoIsfValuesDao.getFromTime(0)).isEmpty()
            assertThat(indexesOf(database, TABLE_AUTOISF_VALUES)).contains("index_autoIsfValues_timestamp")
        } finally {
            database.close()
        }
    }

    @Test
    fun `a database of AAPS 3_4 (version 31) is upgraded`() = runTest {
        BundledSQLiteDriver().open(path).also { connection ->
            createFromSchema(connection, 31)
            connection.execSQL("PRAGMA user_version = 31")
            connection.close()
        }

        val database = openWithRoom()
        try {
            assertThat(database.autoIsfValuesDao.getFromTime(0)).isEmpty()
            assertThat(columnsOf(database, TABLE_TOTAL_DAILY_DOSES)).contains("carbInsulin")
        } finally {
            database.close()
        }
    }

    private fun openWithRoom(): AppDatabase =
        Room.databaseBuilder<AppDatabase>(name = path)
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .addMigrations(*databaseMigrations)
            .build()

    /** All tables and indexes of the schema that Room exported for [version]. */
    private fun createFromSchema(connection: SQLiteConnection, version: Int) {
        val file = File("src/androidDeviceTest/assets/app.aaps.database.AppDatabase/$version.json")
        val database = Json.parseToJsonElement(file.readText()).jsonObject.getValue("database").jsonObject
        database.getValue("entities").jsonArray.forEach { element ->
            val entity = element.jsonObject
            val table = entity.getValue("tableName").jsonPrimitive.content
            connection.execSQL(entity.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}", table))
            entity["indices"]?.jsonArray?.forEach { index ->
                connection.execSQL(index.jsonObject.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}", table))
            }
        }
        database.getValue("setupQueries").jsonArray.forEach { connection.execSQL(it.jsonPrimitive.content) }
    }

    private suspend fun columnsOf(database: AppDatabase, table: String): List<String> =
        database.useReaderConnection { connection ->
            connection.usePrepared("PRAGMA table_info(`$table`)") { statement ->
                buildList { while (statement.step()) add(statement.getText(1)) }
            }
        }

    private suspend fun indexesOf(database: AppDatabase, table: String): List<String> =
        database.useReaderConnection { connection ->
            connection.usePrepared("SELECT name FROM sqlite_master WHERE type = 'index' AND tbl_name = '$table'") { statement ->
                buildList { while (statement.step()) add(statement.getText(0)) }
            }
        }
}
