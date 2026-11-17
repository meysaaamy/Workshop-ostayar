package com.workshop.ledger.data.migration

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Uses Android SQLiteOpenHelper on a synthetic v2 DB; not a Room schema validation test. */
@RunWith(AndroidJUnit4::class)
class LegacyMigration2To3InstrumentedTest {
    private lateinit var context: Context
    private val name = "migration-v2-instrumentation.db"
    @Before fun setUp() { context=ApplicationProvider.getApplicationContext(); context.deleteDatabase(name) }
    @After fun tearDown() { context.deleteDatabase(name) }
    private fun createV2(fractional: Boolean) {
        val sql=InstrumentationRegistry.getInstrumentation().context.assets.open("legacy-v2-schema.sql")
            .bufferedReader(Charsets.UTF_8).use { it.readLines().filter { line -> !line.startsWith("--") && line.isNotBlank() } }
        context.openOrCreateDatabase(name,Context.MODE_PRIVATE,null).use { db ->
            sql.forEach { db.execSQL(it) }
            db.execSQL("INSERT INTO projects VALUES ('p','پروژه',1)")
            db.execSQL("INSERT INTO users VALUES ('p','u','نام','role','master',1.25,1,1,1,NULL)")
            db.execSQL("INSERT INTO wages VALUES ('p','w','u',?, '1403/01/01')", arrayOf(if(fractional) 101.25 else 101))
            db.execSQL("INSERT INTO attendance VALUES ('p','1403/01/02','u',?)",arrayOf("{\"t\":\"h\",\"h\":1.2500}"))
            db.version=2
        }
    }
    private fun helper(): SupportSQLiteOpenHelper {
        val config=SupportSQLiteOpenHelper.Configuration.builder(context).name(name).callback(object : SupportSQLiteOpenHelper.Callback(3) {
            override fun onCreate(db: SupportSQLiteDatabase) { error("Expected a pre-existing v2 fixture") }
            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
                check(oldVersion==2 && newVersion==3); LegacyRoomMigration2To3.migrate(db)
            }
        }).build()
        return FrameworkSQLiteOpenHelperFactory().create(config)
    }
    @Test fun upgradesSupportedValuesWithoutRemovingOriginals() {
        createV2(false)
        helper().use { h ->
            val db=h.writableDatabase
            db.query("SELECT effectiveDate,migration_effectiveDateIso,rate,migration_rateToman FROM wages").use { c ->
                assertTrue(c.moveToFirst()); assertEquals("1403/01/01",c.getString(0)); assertEquals("2024-03-20",c.getString(1)); assertEquals(101L,c.getLong(3))
            }
            db.query("SELECT valueJson,migration_dateIso FROM attendance").use { c ->
                assertTrue(c.moveToFirst());assertEquals("{\"t\":\"h\",\"h\":1.2500}",c.getString(0));assertEquals("2024-03-21",c.getString(1))
            }
            db.query("PRAGMA user_version").use { c -> assertTrue(c.moveToFirst());assertEquals(3,c.getInt(0)) }
        }
    }
    @Test fun unsupportedFractionRollsBackAndKeepsVersionTwo() {
        createV2(true)
        helper().use { h -> assertTrue(runCatching { h.writableDatabase }.isFailure) }
        context.openOrCreateDatabase(name,Context.MODE_PRIVATE,null).use { db ->
            assertEquals(2,db.version)
            db.rawQuery("SELECT rate FROM wages",null).use { c -> assertTrue(c.moveToFirst());assertEquals("101.25",c.getString(0)) }
            db.rawQuery("PRAGMA table_info(wages)",null).use { c ->
                val names=mutableListOf<String>();while(c.moveToNext()) names.add(c.getString(1))
                assertFalse(names.contains("migration_rateToman"))
            }
        }
    }
}
