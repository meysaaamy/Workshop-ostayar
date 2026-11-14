package stayar.migrationtest

import androidx.sqlite.db.Cursor
import androidx.sqlite.db.SupportSQLiteDatabase
import com.workshop.ledger.data.migration.LegacyRoomMigration2To3
import java.io.Closeable
import java.math.BigDecimal
import stayar.parity.JsonReader

private fun quoted(s: String): String = buildString {
    append('"')
    for(c in s) when(c) {
        '"' -> append("\\\""); '\\' -> append("\\\\"); '\n' -> append("\\n"); '\r' -> append("\\r"); '\t' -> append("\\t")
        else -> if(c.code<32) append("\\u%04x".format(c.code)) else append(c)
    }
    append('"')
}
private class Bridge(worker: String): SupportSQLiteDatabase, Closeable {
    private val process=ProcessBuilder("python3",worker).redirectError(ProcessBuilder.Redirect.INHERIT).start()
    private val input=process.outputStream.bufferedWriter(Charsets.UTF_8)
    private val output=process.inputStream.bufferedReader(Charsets.UTF_8)
    fun request(json: String): Any? {
        input.write(json);input.newLine();input.flush()
        val line=output.readLine() ?: error("SQLite worker exited")
        val response=JsonReader(line).read() as Map<*, *>
        if(response["ok"] != true) error("SQLite operation failed: ${response["error"]}")
        return response["result"]
    }
    fun init(kind: String) { request("{\"op\":\"init\",\"kind\":${quoted(kind)}}") }
    override fun execSQL(sql: String) { request("{\"op\":\"exec\",\"sql\":${quoted(sql)}}") }
    override fun execSQL(sql: String, bindArgs: Array<out Any?>) {
        val params=bindArgs.joinToString(",") { v -> when(v) { null -> "null"; is String -> quoted(v); is Long -> v.toString(); else -> error("Unsupported bind type") } }
        request("{\"op\":\"exec\",\"sql\":${quoted(sql)},\"args\":[$params]}")
    }
    override fun query(sql: String): Cursor {
        val rows=request("{\"op\":\"query\",\"sql\":${quoted(sql)}}") as List<*>
        val data=rows.map { it as List<*> }
        return object : Cursor {
            var index=-1
            override fun moveToNext(): Boolean { index++; return index<data.size }
            override fun getString(index: Int) = data[this.index][index] as String
            override fun getLong(index: Int) = (data[this.index][index] as BigDecimal).longValueExact()
            override fun isNull(index: Int) = data[this.index][index] == null
            override fun close() { }
        }
    }
    fun queryRows(sql: String) = request("{\"op\":\"query\",\"sql\":${quoted(sql)}}") as List<*>
    override fun close() { input.close();output.close();check(process.waitFor()==0) }
}
fun main(args: Array<String>) {
    require(args.size==1)
    Bridge(args[0]).use { db ->
        db.init("valid")
        val beforeDigest=db.request("{\"op\":\"legacy_digest\"}")
        val beforeTables=listOf("projects","users","wages","attendance","expenses","receipts","payouts","transfers","adjustments","categories","extra_old_table")
            .associateWith { table -> db.queryRows("SELECT count(*) FROM `$table`") }
        db.execSQL("BEGIN")
        LegacyRoomMigration2To3.migrate(db)
        db.execSQL("COMMIT")
        val after=beforeTables.keys.associateWith { table -> db.queryRows("SELECT count(*) FROM `$table`") }
        check(beforeTables==after)
        check(beforeDigest == db.request("{\"op\":\"legacy_digest\"}"))
        fun first(sql: String): List<*> = db.queryRows(sql).single() as List<*>
        check(first("SELECT effectiveDate,migration_effectiveDateIso,rate,migration_rateToman FROM wages") == listOf("1403/01/01","2024-03-20",BigDecimal("101.0"),BigDecimal("101")))
        check(first("SELECT date,migration_dateIso,valueJson FROM attendance") == listOf("1403/01/02","2024-03-21","{\"t\":\"h\",\"h\":1.2500}"))
        check(first("SELECT dateFrom,migration_dateFromIso,dateTo,migration_dateToIso,migration_amountToman FROM receipts") == listOf("1399/12/30","2021-03-20","1403/01/03","2024-03-22",BigDecimal("999999999999")))
        for(table in listOf("expenses","payouts","transfers","adjustments")) {
            check((first("SELECT migration_dateIso FROM `$table`")[0] as String).startsWith("2024-03-2"))
            check((first("SELECT typeof(migration_amountToman) FROM `$table`")[0] as String)=="integer")
        }
        check(db.request("{\"op\":\"factor_roundtrip\"}")==true)
        check(first("SELECT hex(payload) FROM extra_old_table")==listOf("00FF"))
        check(first("PRAGMA user_version")==listOf(BigDecimal("2")))
        for(kind in listOf("fraction","invalid_date","invalid_factor","blob_amount","target_exists","unexpected_trigger","late_failure")) {
            db.init(kind)
            val original = db.request("{\"op\":\"legacy_digest\"}")
            db.execSQL("BEGIN")
            val failure=runCatching { LegacyRoomMigration2To3.migrate(db) }.exceptionOrNull()
            check(failure!=null) { "Expected rejection for $kind" }
            db.execSQL("ROLLBACK")
            check(first("PRAGMA user_version")==listOf(BigDecimal("2")))
            check(original == db.request("{\"op\":\"legacy_digest\"}"))
            val columns=db.queryRows("PRAGMA table_info(wages)").map { (it as List<*>)[1] }
            check("migration_effectiveDateIso" !in columns && "migration_rateToman" !in columns)
            check(first("SELECT count(*) FROM wages")==listOf(BigDecimal.ONE))
            check(first("SELECT valueJson FROM attendance")==listOf("{\"t\":\"h\",\"h\":1.2500}"))
        }
        db.init("many_factors")
        val beforeFactors=db.request("{\"op\":\"legacy_digest\"}")
        db.execSQL("BEGIN"); LegacyRoomMigration2To3.migrate(db); db.execSQL("COMMIT")
        check(db.request("{\"op\":\"factor_roundtrip\"}")==true)
        check(beforeFactors == db.request("{\"op\":\"legacy_digest\"}"))
    }
    println("Executed actual Kotlin 2→3 migration against host SQLite via test bridge; retention and simulated rollback passed. Room/Android remains untested.")
}
