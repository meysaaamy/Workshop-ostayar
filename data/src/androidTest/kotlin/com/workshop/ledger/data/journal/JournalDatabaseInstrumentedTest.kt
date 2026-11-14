package com.workshop.ledger.data.journal

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import android.content.Context
import com.workshop.ledger.domain.JournalChange
import com.workshop.ledger.domain.JournalEvent
import com.workshop.ledger.domain.JournalKey
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class JournalDatabaseInstrumentedTest {
    private lateinit var db: JournalDatabase
    private lateinit var context: Context
    @Before fun open() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, JournalDatabase::class.java)
            .addCallback(JournalIntegrityCallback(context)).allowMainThreadQueries().build()
    }
    @After fun close() { db.close() }
    private fun created(id: String = "e1") = JournalEventEntity(eventId=id,projectId="p",recordId="r",timestampMillis=1,
        action="created",previousEventId=null,recordKind="expense",snapshot="{}",reason=null)
    private fun amended(id: String = "e2", previous: String = "e1", reason: String = "اصلاح") = JournalEventEntity(
        eventId=id,projectId="p",recordId="r",timestampMillis=2,action="amended",previousEventId=previous,
        recordKind=null,snapshot="{\"amount\":2}",reason=reason)
    @Test fun keepsHistoryAndRejectsMutationAndReplace() {
        assertEquals(1L, db.events().insert(created()))
        assertEquals(2L, db.events().insert(amended()))
        val sqlite = db.openHelper.writableDatabase
        assertTrue(runCatching { sqlite.execSQL("UPDATE journal_events SET snapshot='bad' WHERE event_id='e1'") }.isFailure)
        assertTrue(runCatching { sqlite.execSQL("DELETE FROM journal_events") }.isFailure)
        assertTrue(runCatching { sqlite.execSQL("INSERT OR REPLACE INTO journal_events (event_id,project_id,record_id,timestamp_millis,action,record_kind,snapshot) VALUES ('e1','x','y',1,'created','expense','{}')") }.isFailure)
        assertTrue(runCatching { db.events().insert(amended("e3", "e1")) }.isFailure)
        assertEquals(listOf("e1", "e2"), db.events().all().map { it.eventId })
    }
    @Test fun rejectsInvalidFieldsAndRollsBack() {
        db.events().insert(created())
        for (reason in listOf("", " ", "\t", "\u2003", "a\u0000b"))
            assertTrue(runCatching { db.events().insert(amended(reason=reason)) }.isFailure)
        assertTrue(runCatching {
            db.runInTransaction {
                db.events().insert(amended())
                db.events().insert(amended("e3", "e1"))
            }
        }.isFailure)
        assertEquals(listOf("e1"), db.events().all().map { it.eventId })
    }
    @Test fun repositoryBatchRollbackOnStorageFailure() {
        val store = RoomJournalTransactions(db)
        val repository = JournalRepository(store)
        val key = JournalKey("p", "r")
        val initial = JournalEvent("e0", key, 1, JournalChange.Created("expense", "original"))
        val first = JournalEvent("e1", key, 2, JournalChange.Amended("e0", "first", "اصلاح"))
        val second = JournalEvent("e2", key, 3, JournalChange.Amended("e1", "second", "اصلاح"))
        repository.append(listOf(initial))
        val failingStore = object : JournalTransactions {
            override fun <T> transaction(block: (JournalTransaction) -> T): T = store.transaction { delegate ->
                var insertions = 0
                block(object : JournalTransaction {
                    override fun readAll(): List<JournalEvent> = delegate.readAll()
                    override fun insert(event: JournalEvent): Long {
                        if (++insertions == 2) error("injected write failure")
                        return delegate.insert(event)
                    }
                })
            }
        }
        assertTrue(runCatching { JournalRepository(failingStore).append(listOf(first, second)) }.isFailure)
        assertEquals(listOf("e0"), repository.read().events.map { it.eventId })
        assertEquals(2, repository.append(listOf(first, second)).size)
        assertEquals("second", repository.read().records.getValue(key).snapshot)
    }
    @Test fun missingTriggerCausesFailClosedCheck() {
        val sqlite = db.openHelper.writableDatabase
        sqlite.execSQL("DROP TRIGGER journal_reject_update")
        assertTrue(runCatching { JournalIntegrityCallback(context).onOpen(sqlite) }.isFailure)
    }
}
