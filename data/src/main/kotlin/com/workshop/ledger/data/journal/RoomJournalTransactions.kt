package com.workshop.ledger.data.journal

import com.workshop.ledger.domain.JournalChange
import com.workshop.ledger.domain.JournalEvent
import com.workshop.ledger.domain.JournalKey
import java.util.concurrent.Callable

/** Adapter for the isolated experimental journal database; never opens the legacy database. */
class RoomJournalTransactions(private val database: JournalDatabase) : JournalTransactions {
    override fun <T> transaction(block: (JournalTransaction) -> T): T = database.runInTransaction(Callable {
        val dao = database.events()
        block(object : JournalTransaction {
            override fun readAll(): List<JournalEvent> {
                var previousSequence = 0L
                return dao.all().map { entity ->
                    check(entity.sequence > previousSequence) { "Invalid journal sequence" }
                    previousSequence = entity.sequence
                    entity.toEvent()
                }
            }
            override fun insert(event: JournalEvent): Long = dao.insert(event.toEntity())
        })
    })
}

private fun JournalEvent.toEntity(): JournalEventEntity = when (val c = change) {
    is JournalChange.Created -> JournalEventEntity(eventId=eventId,projectId=key.projectId,recordId=key.recordId,timestampMillis=timestampMillis,
        action="created",previousEventId=null,recordKind=c.recordKind,snapshot=c.snapshot,reason=null)
    is JournalChange.Amended -> JournalEventEntity(eventId=eventId,projectId=key.projectId,recordId=key.recordId,timestampMillis=timestampMillis,
        action="amended",previousEventId=c.previousEventId,recordKind=null,snapshot=c.snapshot,reason=c.reason)
    is JournalChange.Voided -> JournalEventEntity(eventId=eventId,projectId=key.projectId,recordId=key.recordId,timestampMillis=timestampMillis,
        action="voided",previousEventId=c.previousEventId,recordKind=null,snapshot=null,reason=c.reason)
}

/** Fail closed if an existing row does not have the required shape. */
private fun JournalEventEntity.toEvent(): JournalEvent {
    val change = when (action) {
        "created" -> {
            check(previousEventId == null && reason == null && recordKind != null && snapshot != null) { "Invalid created row" }
            JournalChange.Created(recordKind, snapshot)
        }
        "amended" -> {
            check(previousEventId != null && reason != null && recordKind == null && snapshot != null) { "Invalid amended row" }
            JournalChange.Amended(previousEventId, snapshot, reason)
        }
        "voided" -> {
            check(previousEventId != null && reason != null && recordKind == null && snapshot == null) { "Invalid voided row" }
            JournalChange.Voided(previousEventId, reason)
        }
        else -> error("Unknown journal action")
    }
    return JournalEvent(eventId, JournalKey(projectId, recordId), timestampMillis, change)
}
