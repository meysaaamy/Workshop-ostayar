package stayar.migrationtest

import com.workshop.ledger.data.migration.LegacyRoomMigration2To3
import java.math.BigDecimal

/** Host-side dry run on an in-memory copy, never writes the supplied database. */
fun main(args: Array<String>) {
    require(args.size == 2) { "Expected SQLite worker and offline database copy" }
    Bridge(args[0]).use { db ->
        db.request("{\"op\":\"init_external\",\"path\":${quoted(args[1])}}")
        val check = db.queryRows("PRAGMA quick_check")
        require(check == listOf(listOf("ok"))) { "SQLite quick_check failed" }
        val version = ((db.queryRows("PRAGMA user_version").single() as List<*>).single() as BigDecimal).intValueExact()
        require(version == 2) { "Only legacy schema version 2 is supported by this dry run" }
        val before = db.request("{\"op\":\"legacy_digest\"}")
        db.execSQL("BEGIN")
        try {
            LegacyRoomMigration2To3.migrate(db)
            check(before == db.request("{\"op\":\"legacy_digest\"}")) { "Original row values changed" }
        } finally {
            db.execSQL("ROLLBACK")
        }
        check(before == db.request("{\"op\":\"legacy_digest\"}")) { "Rollback did not preserve source rows" }
        println("Host dry run succeeded on an in-memory SQLite copy; original rows retained. Room/Android schema validation was not run.")
    }
}
