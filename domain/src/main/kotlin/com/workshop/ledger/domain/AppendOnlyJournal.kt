package com.workshop.ledger.domain

import java.util.Collections

/** Identity is scoped to a project; identifiers are supplied by the caller. */
data class JournalKey(val projectId: String, val recordId: String)

/** Snapshot is opaque and must be validated by the future record-specific layer. */
sealed interface JournalChange {
    data class Created(val recordKind: String, val snapshot: String) : JournalChange
    data class Amended(val previousEventId: String, val snapshot: String, val reason: String) : JournalChange
    data class Voided(val previousEventId: String, val reason: String) : JournalChange
}

data class JournalEvent(val eventId: String, val key: JournalKey, val timestampMillis: Long, val change: JournalChange)
data class JournalRecord(val key: JournalKey, val recordKind: String, val latestEventId: String, val snapshot: String?, val voided: Boolean)
data class JournalFailure(val index: Int, val reason: Reason) {
    enum class Reason { BLANK_ID, INVALID_ID, INVALID_REASON, DUPLICATE_EVENT_ID, RECORD_ALREADY_EXISTS, RECORD_NOT_FOUND, RECORD_VOIDED, STALE_PREVIOUS_EVENT, BLANK_REASON }
}
sealed interface JournalReplay {
    data class Success(val journal: AppendOnlyJournal) : JournalReplay
    data class Rejected(val failure: JournalFailure) : JournalReplay
}

/**
 * Pure history model, without persistence or synchronization. It does not authenticate the
 * history or validate financial snapshots. Atomic persistence and conflict handling must be
 * implemented in a database transaction by the eventual repository.
 */
class AppendOnlyJournal private constructor(events: List<JournalEvent>, records: Map<JournalKey, JournalRecord>) {
    val events: List<JournalEvent> = Collections.unmodifiableList(ArrayList(events))
    val records: Map<JournalKey, JournalRecord> = Collections.unmodifiableMap(LinkedHashMap(records))

    /** Returns a new validated history; the receiver is never modified. */
    fun append(event: JournalEvent): JournalReplay = replay(events + event)

    companion object {
        fun empty(): AppendOnlyJournal = AppendOnlyJournal(emptyList(), emptyMap())

        /** Replays in supplied order. A failure never exposes a partially replayed journal. */
        fun replay(events: List<JournalEvent>): JournalReplay {
            val ids = HashSet<String>()
            val records = LinkedHashMap<JournalKey, JournalRecord>()
            for ((index, event) in events.withIndex()) {
                fun reject(reason: JournalFailure.Reason) = JournalReplay.Rejected(JournalFailure(index, reason))
                if (event.eventId.isBlank() || event.key.projectId.isBlank() || event.key.recordId.isBlank())
                    return reject(JournalFailure.Reason.BLANK_ID)
                if (listOf(event.eventId, event.key.projectId, event.key.recordId).any { '\u0000' in it })
                    return reject(JournalFailure.Reason.INVALID_ID)
                if (!ids.add(event.eventId)) return reject(JournalFailure.Reason.DUPLICATE_EVENT_ID)
                val existing = records[event.key]
                when (val change = event.change) {
                    is JournalChange.Created -> {
                        if (change.recordKind.isBlank()) return reject(JournalFailure.Reason.BLANK_ID)
                        if ('\u0000' in change.recordKind) return reject(JournalFailure.Reason.INVALID_ID)
                        if (existing != null) return reject(JournalFailure.Reason.RECORD_ALREADY_EXISTS)
                        records[event.key] = JournalRecord(event.key, change.recordKind, event.eventId, change.snapshot, false)
                    }
                    is JournalChange.Amended -> {
                        if (existing == null) return reject(JournalFailure.Reason.RECORD_NOT_FOUND)
                        if (existing.voided) return reject(JournalFailure.Reason.RECORD_VOIDED)
                        if (existing.latestEventId != change.previousEventId) return reject(JournalFailure.Reason.STALE_PREVIOUS_EVENT)
                        if (change.reason.isBlank()) return reject(JournalFailure.Reason.BLANK_REASON)
                        if ('\u0000' in change.reason) return reject(JournalFailure.Reason.INVALID_REASON)
                        records[event.key] = existing.copy(latestEventId = event.eventId, snapshot = change.snapshot)
                    }
                    is JournalChange.Voided -> {
                        if (existing == null) return reject(JournalFailure.Reason.RECORD_NOT_FOUND)
                        if (existing.voided) return reject(JournalFailure.Reason.RECORD_VOIDED)
                        if (existing.latestEventId != change.previousEventId) return reject(JournalFailure.Reason.STALE_PREVIOUS_EVENT)
                        if (change.reason.isBlank()) return reject(JournalFailure.Reason.BLANK_REASON)
                        if ('\u0000' in change.reason) return reject(JournalFailure.Reason.INVALID_REASON)
                        records[event.key] = existing.copy(latestEventId = event.eventId, snapshot = null, voided = true)
                    }
                }
            }
            return JournalReplay.Success(AppendOnlyJournal(events, records))
        }
    }
}
