package com.workshop.ledger.data.journal

import com.workshop.ledger.domain.AppendOnlyJournal
import com.workshop.ledger.domain.JournalEvent
import com.workshop.ledger.domain.JournalFailure
import com.workshop.ledger.domain.JournalReplay
import java.util.Collections

/** Implementation must run the entire callback in one exclusive, rollback-capable write transaction. */
interface JournalTransactions {
    fun <T> transaction(block: (JournalTransaction) -> T): T
}
interface JournalTransaction {
    fun readAll(): List<JournalEvent>
    /** Ordinary INSERT with ABORT semantics; returns the positive stored sequence, never IGNORE. */
    fun insert(event: JournalEvent): Long
}
class JournalRejectedException(val failure: JournalFailure) : IllegalArgumentException("Journal rejected event at index ${failure.index}: ${failure.reason}")
class JournalInsertException : IllegalStateException("Journal insertion did not return a strictly increasing positive sequence")

/**
 * Transactional orchestration for an append-only store. It validates the whole history before
 * writing and never hides store errors. Snapshot content itself is not a validated ledger record.
 */
class JournalRepository(private val transactions: JournalTransactions) {
    fun read(): AppendOnlyJournal = transactions.transaction { tx -> valid(tx.readAll()) }

    /** All new events are committed together, or none are. Empty input performs no transaction. */
    fun append(events: List<JournalEvent>): List<Long> {
        if (events.isEmpty()) return emptyList()
        val pending = events.toList()
        return transactions.transaction { tx ->
            valid(tx.readAll() + pending)
            var previousSequence = 0L
            val inserted = pending.map { event ->
                val sequence = tx.insert(event)
                if (sequence <= previousSequence) throw JournalInsertException()
                previousSequence = sequence
                sequence
            }
            Collections.unmodifiableList(inserted)
        }
    }
    private fun valid(events: List<JournalEvent>): AppendOnlyJournal = when (val replay = AppendOnlyJournal.replay(events)) {
        is JournalReplay.Success -> replay.journal
        is JournalReplay.Rejected -> throw JournalRejectedException(replay.failure)
    }
}
