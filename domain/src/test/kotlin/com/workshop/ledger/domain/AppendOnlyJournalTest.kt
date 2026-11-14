package com.workshop.ledger.domain

fun main() {
    val k = JournalKey("project", "record")
    fun created(id: String, key: JournalKey = k, payload: String = "original") =
        JournalEvent(id, key, 10, JournalChange.Created("expense", payload))
    fun amend(id: String, previous: String, reason: String = "اصلاح") =
        JournalEvent(id, k, 11, JournalChange.Amended(previous, "changed", reason))
    fun void(id: String, previous: String, reason: String = "ابطال") =
        JournalEvent(id, k, 12, JournalChange.Voided(previous, reason))
    fun ok(result: JournalReplay) = (result as JournalReplay.Success).journal
    fun reject(result: JournalReplay, reason: JournalFailure.Reason, index: Int) {
        check((result as JournalReplay.Rejected).failure == JournalFailure(index, reason))
    }
    val empty = AppendOnlyJournal.empty()
    val first = ok(empty.append(created("1")))
    val second = ok(first.append(amend("2", "1")))
    val third = ok(second.append(void("3", "2")))
    check(empty.events.isEmpty() && first.events.size == 1 && second.events.size == 2 && third.events.size == 3)
    check(first.records.getValue(k).snapshot == "original")
    check(second.records.getValue(k).snapshot == "changed")
    check(third.records.getValue(k).voided && third.records.getValue(k).snapshot == null)
    check(third.events[0].change == JournalChange.Created("expense", "original"))
    check(third.events[1].change == JournalChange.Amended("1", "changed", "اصلاح"))
    check(ok(AppendOnlyJournal.replay(third.events)).records == third.records)
    reject(first.append(created("1")), JournalFailure.Reason.DUPLICATE_EVENT_ID, 1)
    reject(first.append(created("4")), JournalFailure.Reason.RECORD_ALREADY_EXISTS, 1)
    reject(first.append(amend("4", "wrong")), JournalFailure.Reason.STALE_PREVIOUS_EVENT, 1)
    reject(first.append(amend("4", "1", "  ")), JournalFailure.Reason.BLANK_REASON, 1)
    reject(empty.append(amend("4", "1")), JournalFailure.Reason.RECORD_NOT_FOUND, 0)
    reject(third.append(amend("4", "3")), JournalFailure.Reason.RECORD_VOIDED, 3)
    reject(third.append(created("4")), JournalFailure.Reason.RECORD_ALREADY_EXISTS, 3)
    reject(empty.append(created(" ")), JournalFailure.Reason.BLANK_ID, 0)
    reject(empty.append(created("e\u0000x")), JournalFailure.Reason.INVALID_ID, 0)
    reject(first.append(amend("4", "1", "reason\u0000x")), JournalFailure.Reason.INVALID_REASON, 1)
    val other = JournalKey("other-project", "record")
    val scoped = ok(first.append(created("other", other)))
    check(scoped.records.size == 2 && scoped.records.getValue(other).snapshot == "original")
    val mutable = mutableListOf(created("source"))
    val copied = ok(AppendOnlyJournal.replay(mutable)); mutable.clear()
    check(copied.events.size == 1)
    check(runCatching { (copied.events as MutableList).clear() }.isFailure)
    check(runCatching { (copied.records as MutableMap).clear() }.isFailure)
    val events = ArrayList<JournalEvent>()
    repeat(10_000) { i ->
        val key = JournalKey("p", "r$i")
        events.add(created("id$i", key, "payload$i"))
    }
    check(ok(AppendOnlyJournal.replay(events)).records.size == 10_000)
    events.add(created("id42", JournalKey("different", "x")))
    reject(AppendOnlyJournal.replay(events), JournalFailure.Reason.DUPLICATE_EVENT_ID, 10_000)
    println("Checked immutable journal replay, corrections, voids, conflicts and 10000 records; no persistent ledger or atomic transaction tested.")
}
