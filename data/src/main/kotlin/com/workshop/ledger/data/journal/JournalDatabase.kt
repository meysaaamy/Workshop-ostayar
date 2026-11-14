package com.workshop.ledger.data.journal

import android.content.Context
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

/** Experimental development-only entity. This is not the legacy project database. */
@Entity(tableName = "journal_events", indices = [
    Index(value = ["event_id"], unique = true),
    Index(value = ["project_id", "record_id", "sequence"])
])
data class JournalEventEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "sequence") val sequence: Long = 0,
    @ColumnInfo(name = "event_id") val eventId: String,
    @ColumnInfo(name = "project_id") val projectId: String,
    @ColumnInfo(name = "record_id") val recordId: String,
    @ColumnInfo(name = "timestamp_millis") val timestampMillis: Long,
    @ColumnInfo(name = "action") val action: String,
    @ColumnInfo(name = "previous_event_id") val previousEventId: String?,
    @ColumnInfo(name = "record_kind") val recordKind: String?,
    @ColumnInfo(name = "snapshot") val snapshot: String?,
    @ColumnInfo(name = "reason") val reason: String?
)

@Dao
interface JournalEventDao {
    /** ABORT, never IGNORE/REPLACE; caller still needs an encompassing transaction. */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insert(event: JournalEventEntity): Long
    @Query("SELECT * FROM journal_events ORDER BY sequence")
    fun all(): List<JournalEventEntity>
}

@Database(entities = [JournalEventEntity::class], version = 1, exportSchema = true)
abstract class JournalDatabase : RoomDatabase() {
    abstract fun events(): JournalEventDao

    companion object {
        // Deliberately distinct from workshop-ledger.db. No migration or production integration.
        private const val DEVELOPMENT_DATABASE = "stayar-journal-development.db"
        fun openDevelopment(context: Context): JournalDatabase = Room.databaseBuilder(
            context.applicationContext, JournalDatabase::class.java, DEVELOPMENT_DATABASE
        ).addCallback(JournalIntegrityCallback(context.applicationContext)).build()
    }
}

/** Installs the tested triggers for a newly created journal, and fails closed if any are missing. */
class JournalIntegrityCallback(private val context: Context) : RoomDatabase.Callback() {
    private val required = setOf(
        "journal_validate_fields", "journal_reject_existing_event", "journal_reject_assigned_sequence",
        "journal_reject_nonpositive_sequence", "journal_reject_duplicate_record", "journal_reject_stale_change",
        "journal_reject_update", "journal_reject_delete"
    )
    override fun onCreate(db: SupportSQLiteDatabase) {
        val script = context.assets.open("journal-room-triggers.sql").bufferedReader(Charsets.UTF_8).use { it.readText() }
        val statements = script.split("-- stayar-statement").map(String::trim).filter(String::isNotEmpty)
        check(statements.size == required.size) { "Unexpected journal trigger set" }
        statements.forEach(db::execSQL)
        verifyTriggers(db)
    }
    override fun onOpen(db: SupportSQLiteDatabase) { verifyTriggers(db) }
    private fun verifyTriggers(db: SupportSQLiteDatabase) {
        val found = mutableSetOf<String>()
        db.query("SELECT name FROM sqlite_master WHERE type = 'trigger' AND tbl_name = 'journal_events'").use { cursor ->
            while (cursor.moveToNext()) found.add(cursor.getString(0))
        }
        check(found.containsAll(required)) { "Journal integrity triggers are missing" }
    }
}
