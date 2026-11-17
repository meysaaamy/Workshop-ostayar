package com.workshop.ledger.data.migration

import java.math.BigDecimal
import java.io.File
import java.util.Locale

fun main(args: Array<String>) {
    fun text(s: String) = LegacyCell.Text(s)
    fun decimal(s: String) = LegacyCell.Decimal(BigDecimal(s))
    val originalAttendance = """{"t":"h","h":1.2500}"""
    val input = listOf(
        LegacyRow("projects", mapOf("id" to text("p"), "name" to text("کارگاه"))),
        LegacyRow("users", mapOf("id" to text("u"), "workFactor" to decimal("1.25"))),
        LegacyRow("categories", mapOf("name" to text("گروه"))),
        LegacyRow("wages", mapOf("effectiveDate" to text("1403/01/01"), "rate" to decimal("101.0"))),
        LegacyRow("attendance", mapOf("date" to text("1403/01/01"), "valueJson" to text(originalAttendance))),
        LegacyRow("expenses", mapOf("date" to text("1403/01/01"), "amount" to decimal("999999999999"))),
        LegacyRow("receipts", mapOf("dateFrom" to text("1399/12/30"), "dateTo" to text("1400/01/01"), "amount" to LegacyCell.Integer(1))),
        LegacyRow("payouts", mapOf("date" to text("1403/01/01"), "amount" to decimal("0.0"))),
        LegacyRow("transfers", mapOf("date" to text("1403/01/01"), "amount" to decimal("2"))),
        LegacyRow("adjustments", mapOf("date" to text("1403/01/01"), "amount" to decimal("3"), "futureColumn" to text("حفظ شود")))
    )
    val result = LegacyRowConversion.convert(input) as ConversionResult.Success
    check(result.rows.size == input.size)
    check(result.rows[3].values["effectiveDate"] == text("2024-03-20"))
    check(result.rows[3].values["rate"] == LegacyCell.Integer(101))
    check(result.rows[4].values["valueJson"] == text(originalAttendance))
    check(result.rows[6].values["dateFrom"] == text("2021-03-20"))
    check(result.rows[6].values["dateTo"] == text("2021-03-21"))
    check(result.rows[9].values["futureColumn"] == text("حفظ شود"))
    check(input[3].values["effectiveDate"] == text("1403/01/01"))
    check(input[3].values["rate"] == decimal("101.0"))
    for (amount in listOf("-1", "0.25", "1000000000000", "1E100")) {
        val row = LegacyRow("expenses", mapOf("date" to text("1403/01/01"), "amount" to decimal(amount)))
        check((LegacyRowConversion.convert(listOf(row)) as ConversionResult.Rejected).issues.single().reason == ConversionIssue.Reason.INVALID_AMOUNT)
    }
    for (date in listOf("1402/12/30", "1403/1/01", "1199/12/29", "1701/01/01", "not a date")) {
        val row = LegacyRow("attendance", mapOf("date" to text(date), "valueJson" to text(originalAttendance)))
        check((LegacyRowConversion.convert(listOf(row)) as ConversionResult.Rejected).issues.single().reason == ConversionIssue.Reason.INVALID_DATE)
    }
    val failures = LegacyRowConversion.convert(input + listOf(
        LegacyRow("wages", mapOf("rate" to LegacyCell.Null)), LegacyRow("unknown", emptyMap())
    )) as ConversionResult.Rejected
    check(failures.issues.size == 3) // failure exposes no partially converted rows
    check(failures.issues.map { it.reason }.toSet() == setOf(ConversionIssue.Reason.MISSING_COLUMN, ConversionIssue.Reason.INVALID_AMOUNT, ConversionIssue.Reason.UNKNOWN_TABLE))
    val mutable = linkedMapOf<String, LegacyCell>("id" to text("safe"))
    val row = LegacyRow("projects", mutable); mutable["id"] = text("changed")
    check(row.values["id"] == text("safe"))
    require(args.size == 1)
    var checkedDates = 0
    File(args[0]).useLines { lines ->
        lines.drop(1).forEach { line ->
            val numbers = line.removePrefix("[").removeSuffix("]").split(',').map(String::toInt)
            val j = String.format(Locale.ROOT, "%04d/%02d/%02d", numbers[0], numbers[1], numbers[2])
            val expected = String.format(Locale.ROOT, "%04d-%02d-%02d", numbers[3], numbers[4], numbers[5])
            val converted = LegacyRowConversion.convert(listOf(LegacyRow("attendance", mapOf("date" to text(j))))) as ConversionResult.Success
            check(converted.rows.single().values["date"] == text(expected)) { "Date mismatch: $j" }
            checkedDates++
        }
    }
    check(checkedDates == 182986)
    println("Checked legacy row conversion, fail-closed rejections and $checkedDates calendar reference dates in memory; no Room migration executed.")
}
