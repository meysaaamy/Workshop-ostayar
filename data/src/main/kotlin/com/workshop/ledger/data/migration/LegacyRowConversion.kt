package com.workshop.ledger.data.migration

import com.workshop.ledger.domain.InputConstraints
import com.workshop.ledger.domain.JalaliCalendar
import java.math.BigDecimal
import java.time.LocalDate
import java.util.Collections

/** Supported scalar SQLite values, with REAL supplied as a decimal numeric representation. */
sealed interface LegacyCell {
    data object Null : LegacyCell
    data class Text(val value: String) : LegacyCell
    data class Integer(val value: Long) : LegacyCell
    data class Decimal(val value: BigDecimal) : LegacyCell
}

/** One complete legacy row. Unknown columns are carried through verbatim. */
class LegacyRow(val table: String, values: Map<String, LegacyCell>) {
    val values: Map<String, LegacyCell> = Collections.unmodifiableMap(LinkedHashMap(values))
}

data class ConversionIssue(val table: String, val rowIndex: Int, val column: String, val reason: Reason) {
    enum class Reason { UNKNOWN_TABLE, MISSING_COLUMN, INVALID_DATE, INVALID_AMOUNT }
}
sealed interface ConversionResult {
    data class Success(val rows: List<LegacyRow>) : ConversionResult
    data class Rejected(val issues: List<ConversionIssue>) : ConversionResult
}

/**
 * A pure, fail-closed conversion step, not a Room migration and not a schema validator.
 * Callers must supply every original row and exact SQLite values; this cannot verify that
 * a caller did so. No data may be written until all rows have been accepted.
 */
object LegacyRowConversion {
    private data class Fields(val dates: List<String> = emptyList(), val money: List<String> = emptyList())
    private val fields = mapOf(
        "projects" to Fields(), "users" to Fields(), "categories" to Fields(),
        "wages" to Fields(listOf("effectiveDate"), listOf("rate")),
        "attendance" to Fields(listOf("date")),
        "expenses" to Fields(listOf("date"), listOf("amount")),
        "receipts" to Fields(listOf("dateFrom", "dateTo"), listOf("amount")),
        "payouts" to Fields(listOf("date"), listOf("amount")),
        "transfers" to Fields(listOf("date"), listOf("amount")),
        "adjustments" to Fields(listOf("date"), listOf("amount")),
    )
    private val datePattern = Regex("^[0-9]{4}/[0-9]{2}/[0-9]{2}$")

    fun convert(rows: List<LegacyRow>): ConversionResult {
        val converted = ArrayList<LegacyRow>(rows.size)
        val issues = ArrayList<ConversionIssue>()
        rows.forEachIndexed { index, row ->
            val spec = fields[row.table]
            if (spec == null) {
                issues.add(ConversionIssue(row.table, index, "", ConversionIssue.Reason.UNKNOWN_TABLE))
                return@forEachIndexed
            }
            val output = LinkedHashMap(row.values)
            for (column in spec.dates) {
                val cell = row.values[column]
                val iso = (cell as? LegacyCell.Text)?.value?.let(::toIso)
                if (iso == null) issues.add(ConversionIssue(row.table, index, column,
                    if (cell == null) ConversionIssue.Reason.MISSING_COLUMN else ConversionIssue.Reason.INVALID_DATE))
                else output[column] = LegacyCell.Text(iso)
            }
            for (column in spec.money) {
                val cell = row.values[column]
                val amount = when (cell) {
                    is LegacyCell.Integer -> cell.value
                    is LegacyCell.Decimal -> try { cell.value.longValueExact() } catch (_: ArithmeticException) { null }
                    else -> null
                }
                if (amount == null || !InputConstraints.isValidAmount(amount)) issues.add(ConversionIssue(row.table, index, column,
                    if (cell == null) ConversionIssue.Reason.MISSING_COLUMN else ConversionIssue.Reason.INVALID_AMOUNT))
                else output[column] = LegacyCell.Integer(amount)
            }
            converted.add(LegacyRow(row.table, output))
        }
        return if (issues.isEmpty()) ConversionResult.Success(Collections.unmodifiableList(converted))
               else ConversionResult.Rejected(Collections.unmodifiableList(issues))
    }

    private fun toIso(value: String): String? {
        if (!datePattern.matches(value)) return null
        val year = value.substring(0, 4).toInt()
        val month = value.substring(5, 7).toInt()
        val day = value.substring(8, 10).toInt()
        if (year !in 1200..1700 || !JalaliCalendar.isValidDate(year, month, day)) return null
        val g = JalaliCalendar.toGregorian(year, month, day)
        return LocalDate.of(g.year, g.month, g.day).toString()
    }
}
