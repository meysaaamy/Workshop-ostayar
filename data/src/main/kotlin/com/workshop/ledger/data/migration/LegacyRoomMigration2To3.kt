package com.workshop.ledger.data.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.workshop.ledger.domain.JalaliCalendar
import java.util.Locale

/**
 * Experimental additive migration for the APK v2 table layout, registered in the transitional Room database.
 * Room must call it within its migration transaction, and its v3 schema must validate
 * the result. Original columns, attendance JSON, unknown tables and DataStore are untouched.
 */
object LegacyRoomMigration2To3 : Migration(2, 3) {
    private const val MAX_AMOUNT = 999_999_999_999L
    private val datePattern = Regex("^[0-9]{4}/[0-9]{2}/[0-9]{2}$")
    private data class DateColumn(val table: String, val legacy: String, val new: String)
    private data class MoneyColumn(val table: String, val legacy: String, val new: String)
    private data class DatePlan(val column: DateColumn, val dates: List<Pair<String, String>>)
    private val dates = listOf(
        DateColumn("wages", "effectiveDate", "migration_effectiveDateIso"),
        DateColumn("attendance", "date", "migration_dateIso"),
        DateColumn("expenses", "date", "migration_dateIso"),
        DateColumn("receipts", "dateFrom", "migration_dateFromIso"),
        DateColumn("receipts", "dateTo", "migration_dateToIso"),
        DateColumn("payouts", "date", "migration_dateIso"),
        DateColumn("transfers", "date", "migration_dateIso"),
        DateColumn("adjustments", "date", "migration_dateIso")
    )
    private val amounts = listOf(
        MoneyColumn("wages", "rate", "migration_rateToman"),
        MoneyColumn("expenses", "amount", "migration_amountToman"),
        MoneyColumn("receipts", "amount", "migration_amountToman"),
        MoneyColumn("payouts", "amount", "migration_amountToman"),
        MoneyColumn("transfers", "amount", "migration_amountToman"),
        MoneyColumn("adjustments", "amount", "migration_amountToman")
    )

    /** A migration error deliberately contains no personal data or record values. */
    class Rejected(val table: String, val column: String, val issue: String) : IllegalStateException("Migration rejected $table.$column: $issue")

    override fun migrate(db: SupportSQLiteDatabase) {
        // Unexpected triggers could mutate preserved data when compatibility columns are filled.
        // Do not run UPDATE on such a database without a reviewed migration plan.
        val triggerCount = count(db, "SELECT COUNT(*) FROM sqlite_master WHERE type = 'trigger' AND tbl_name IN " +
            "('users','wages','attendance','expenses','receipts','payouts','transfers','adjustments')")
        if (triggerCount != 0L) throw Rejected("sqlite_master", "trigger", "unexpected trigger on migrated table")
        // Reject incompatible values before making any change. Room still supplies the transaction.
        val plans = dates.map { date ->
            val list = mutableListOf<Pair<String, String>>()
            db.query("SELECT DISTINCT typeof(`${date.legacy}`), `${date.legacy}` FROM `${date.table}`").use { cursor ->
                while (cursor.moveToNext()) {
                    if (cursor.getString(0) != "text" || cursor.isNull(1)) throw Rejected(date.table,date.legacy,"date is not text")
                    val raw = cursor.getString(1)
                    val iso = toIso(raw) ?: throw Rejected(date.table,date.legacy,"invalid or unsupported date")
                    list.add(raw to iso)
                }
            }
            DatePlan(date,list)
        }
        for (m in amounts) {
            val invalid = "SELECT COUNT(*) FROM `${m.table}` WHERE typeof(`${m.legacy}`) NOT IN ('integer','real') " +
                "OR `${m.legacy}` < 0 OR `${m.legacy}` > $MAX_AMOUNT OR CAST(`${m.legacy}` AS INTEGER) != `${m.legacy}`"
            if (count(db,invalid) != 0L) throw Rejected(m.table,m.legacy,"amount is not a supported whole value")
        }
        // Existing REAL values remain in the legacy column. SQLite's alternate-form-2 with
        // 26 significant digits is used for a round-trippable decimal; validate on Android.
        val badFactors = "SELECT COUNT(*) FROM `users` WHERE typeof(`workFactor`) NOT IN ('integer','real') " +
            "OR `workFactor` > 1.7976931348623157e308 OR `workFactor` < -1.7976931348623157e308"
        if (count(db,badFactors) != 0L) throw Rejected("users","workFactor","non-finite or non-numeric factor")
        val required = dates.map { it.table to it.new } + amounts.map { it.table to it.new } + ("users" to "migration_workFactorDecimal")
        required.forEach { (table, name) ->
            db.query("PRAGMA table_info(`$table`)").use { c ->
                while(c.moveToNext()) if(c.getString(1) == name) throw Rejected(table,name,"target column already exists")
            }
        }
        dates.forEach { d -> db.execSQL("ALTER TABLE `${d.table}` ADD COLUMN `${d.new}` TEXT NOT NULL DEFAULT ''") }
        amounts.forEach { m -> db.execSQL("ALTER TABLE `${m.table}` ADD COLUMN `${m.new}` INTEGER NOT NULL DEFAULT 0") }
        db.execSQL("ALTER TABLE `users` ADD COLUMN `migration_workFactorDecimal` TEXT NOT NULL DEFAULT ''")
        for (plan in plans) for ((raw,iso) in plan.dates) {
            val d=plan.column
            db.execSQL("UPDATE `${d.table}` SET `${d.new}` = ? WHERE `${d.legacy}` = ?", arrayOf(iso,raw))
        }
        amounts.forEach { m -> db.execSQL("UPDATE `${m.table}` SET `${m.new}` = CAST(`${m.legacy}` AS INTEGER)") }
        db.execSQL("UPDATE `users` SET `migration_workFactorDecimal` = printf('%!.26g', `workFactor`)")
        // No user_version change here; Room is responsible for schema validation and versioning.
    }
    private fun count(db: SupportSQLiteDatabase, sql: String): Long = db.query(sql).use { c ->
        check(c.moveToNext()); c.getLong(0)
    }
    private fun toIso(raw: String): String? {
        if (!datePattern.matches(raw)) return null
        val year=raw.substring(0,4).toInt(); val month=raw.substring(5,7).toInt(); val day=raw.substring(8,10).toInt()
        if(year !in 1200..1700 || !JalaliCalendar.isValidDate(year,month,day)) return null
        val g=JalaliCalendar.toGregorian(year,month,day)
        return String.format(Locale.ROOT,"%04d-%02d-%02d",g.year,g.month,g.day)
    }
}
