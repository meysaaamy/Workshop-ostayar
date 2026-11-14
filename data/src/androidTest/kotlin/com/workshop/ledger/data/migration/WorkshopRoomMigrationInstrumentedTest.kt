package com.workshop.ledger.data.migration

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.workshop.ledger.data.legacy.WorkshopDatabase
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Synthetic SQL fixtures: actual Room migrations and validation, but not old-app fixtures. */
@RunWith(AndroidJUnit4::class)
class WorkshopRoomMigrationInstrumentedTest {
    private lateinit var context: Context
    private val name = "room-migration-test.db"
    @Before fun setup() { context=ApplicationProvider.getApplicationContext(); context.deleteDatabase(name) }
    @After fun cleanup() { context.deleteDatabase(name) }
    private fun fixture(version: Int, fractional: Boolean = false, badIndex: Boolean = false) {
        val lines=InstrumentationRegistry.getInstrumentation().context.assets.open("legacy-v2-schema.sql")
            .bufferedReader(Charsets.UTF_8).use { it.readLines().filter { line -> line.startsWith("CREATE ") } }
        context.openOrCreateDatabase(name,Context.MODE_PRIVATE,null).use { db ->
            lines.forEach(db::execSQL)
            db.execSQL("INSERT INTO projects VALUES ('p','کارگاه',1)")
            db.execSQL("INSERT INTO users VALUES ('p','u','نام','role','master',1.25,1,1,1,NULL)")
            db.execSQL("INSERT INTO wages VALUES ('p','w','u',?, '1403/01/01')", arrayOf(if (fractional) 101.25 else 101))
            db.execSQL("INSERT INTO attendance VALUES ('p','1403/01/02','u',?)", arrayOf("{\"h\":1.2500}"))
            if (badIndex) {
                db.execSQL("DROP INDEX index_wages_projectId_userId")
                db.execSQL("CREATE INDEX index_wages_projectId_userId ON wages(projectId)")
            }
            db.version=version
        }
    }
    private fun database() = Room.databaseBuilder(context,WorkshopDatabase::class.java,name)
        .addMigrations(LegacyRoomMigration1To2,LegacyRoomMigration2To3).allowMainThreadQueries().build()
    private fun verifySuccess(version: Int) {
        fixture(version)
        database().use { room ->
            val db=room.openHelper.writableDatabase
            db.query("SELECT effectiveDate,migration_effectiveDateIso,migration_rateToman FROM wages").use { c ->
                assertTrue(c.moveToFirst());assertEquals("1403/01/01",c.getString(0));assertEquals("2024-03-20",c.getString(1));assertEquals(101L,c.getLong(2))
            }
            db.query("SELECT valueJson,migration_dateIso FROM attendance").use { c ->
                assertTrue(c.moveToFirst());assertEquals("{\"h\":1.2500}",c.getString(0));assertEquals("2024-03-21",c.getString(1))
            }
            db.query("PRAGMA user_version").use { c -> assertTrue(c.moveToFirst()); assertEquals(3,c.getInt(0)) }
        }
        database().use { it.openHelper.writableDatabase }
    }
    @Test fun migratesSyntheticTwoToThree() = verifySuccess(2)
    @Test fun migratesSyntheticOneToThree() = verifySuccess(1)
    @Test fun createsAndReopensFreshRoomSchema() {
        database().use { it.openHelper.writableDatabase }
        database().use { it.openHelper.writableDatabase }
    }
    private fun assertFailedAndUnchanged(fractional: Boolean, badIndex: Boolean) {
        fixture(2,fractional,badIndex)
        database().use { db -> assertTrue(runCatching { db.openHelper.writableDatabase }.isFailure) }
        context.openOrCreateDatabase(name,Context.MODE_PRIVATE,null).use { db ->
            assertEquals(2,db.version)
            db.rawQuery("PRAGMA table_info(wages)",null).use { c ->
                val names=mutableListOf<String>();while(c.moveToNext()) names.add(c.getString(1))
                assertFalse(names.contains("migration_rateToman"))
            }
            db.rawQuery("SELECT valueJson FROM attendance",null).use { c -> assertTrue(c.moveToFirst());assertEquals("{\"h\":1.2500}",c.getString(0)) }
        }
    }
    @Test fun rejectsFractionWithoutDestruction() = assertFailedAndUnchanged(true,false)
    @Test fun roomSchemaValidationRejectsWrongIndexAndRollsBack() = assertFailedAndUnchanged(false,true)
}
