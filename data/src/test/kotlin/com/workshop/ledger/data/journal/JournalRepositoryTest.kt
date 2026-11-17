package com.workshop.ledger.data.journal

import com.workshop.ledger.domain.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

private class MemoryTransactions : JournalTransactions {
    private var committed = emptyList<JournalEvent>()
    var failAtInsert = -1
    var invalidSequenceAtInsert = -1
    var repeatedSequenceAtInsert = -1
    var transactions = 0
    override fun <T> transaction(block: (JournalTransaction) -> T): T = synchronized(this) {
        transactions++
        val working = committed.toMutableList()
        var inserts = 0
        val tx = object : JournalTransaction {
            override fun readAll(): List<JournalEvent> = working.toList()
            override fun insert(event: JournalEvent): Long {
                inserts++
                if (inserts == failAtInsert) error("injected failure")
                working.add(event)
                return if (inserts == invalidSequenceAtInsert) -1 else if (inserts == repeatedSequenceAtInsert) working.size.toLong() - 1 else working.size.toLong()
            }
        }
        val result = block(tx)
        committed = working.toList()
        result
    }
}
fun main() {
    val key = JournalKey("p", "r")
    fun created() = JournalEvent("e0", key, 1, JournalChange.Created("expense", "original"))
    fun amended(id: String, previous: String) = JournalEvent(id, key, 2, JournalChange.Amended(previous, "changed", "اصلاح"))
    val store = MemoryTransactions()
    val repo = JournalRepository(store)
    check(repo.append(emptyList()).isEmpty() && store.transactions == 0)
    check(repo.append(listOf(created())) == listOf(1L))
    check(repo.read().records.getValue(key).snapshot == "original")
    val failure = runCatching { repo.append(listOf(amended("e1", "e0"),amended("e2", "e0"))) }.exceptionOrNull()
    check(failure is JournalRejectedException && failure.failure.reason == JournalFailure.Reason.STALE_PREVIOUS_EVENT)
    check(repo.read().events.size == 1)
    store.failAtInsert=2
    check(runCatching { repo.append(listOf(amended("e1", "e0"),amended("e2", "e1"))) }.isFailure)
    check(repo.read().events.size == 1)
    store.failAtInsert=-1; store.invalidSequenceAtInsert=2
    check(runCatching { repo.append(listOf(amended("e1", "e0"),amended("e2", "e1"))) }.exceptionOrNull() is JournalInsertException)
    check(repo.read().events.size == 1)
    store.invalidSequenceAtInsert=-1; store.repeatedSequenceAtInsert=2
    check(runCatching { repo.append(listOf(amended("e1", "e0"),amended("e2", "e1"))) }.exceptionOrNull() is JournalInsertException)
    check(repo.read().events.size == 1)
    store.repeatedSequenceAtInsert=-1
    check(repo.append(listOf(amended("e1", "e0"),amended("e2", "e1"))) == listOf(2L,3L))
    check(repo.read().events.size == 3)
    // Serialized concurrent writers with the same head: at most one can succeed.
    val start=CountDownLatch(1); val pool=Executors.newFixedThreadPool(2)
    try {
        val jobs=(1..2).map { i -> pool.submit<Boolean> { start.await(); runCatching { repo.append(listOf(amended("race$i", "e2"))) }.isSuccess } }
        start.countDown()
        check(jobs.count { it.get(10, TimeUnit.SECONDS) } == 1)
        check(repo.read().events.size == 4)
    } finally { pool.shutdownNow() }
    println("Checked transactional journal orchestration with a rollback-capable fake; no Room or device transaction tested.")
}
