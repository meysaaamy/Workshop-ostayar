package stayar.journalparity

import com.workshop.ledger.domain.*
import java.math.BigDecimal
import stayar.parity.JsonReader

/** Test-only stdin bridge; emits only acceptance, never record payloads. */
fun main() {
    System.`in`.bufferedReader(Charsets.UTF_8).useLines { lines -> lines.forEach { line ->
        val objects = JsonReader(line).read() as List<*>
        val events = objects.map { raw ->
            val x = raw as Map<*, *>
            val change = when (x["action"]) {
                "created" -> JournalChange.Created(x["kind"] as String, x["snapshot"] as String)
                "amended" -> JournalChange.Amended(x["previous"] as String, x["snapshot"] as String, x["reason"] as String)
                "voided" -> JournalChange.Voided(x["previous"] as String, x["reason"] as String)
                else -> error("Unsupported test action")
            }
            JournalEvent(x["event"] as String, JournalKey(x["project"] as String, x["record"] as String),
                (x["time"] as BigDecimal).longValueExact(), change)
        }
        println(if (AppendOnlyJournal.replay(events) is JournalReplay.Success) "A" else "R")
    } }
}
