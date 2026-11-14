package com.workshop.ledger.data.export

import java.io.File
import java.math.BigDecimal
import java.util.Base64
import stayar.parity.JsonReader

fun main(args: Array<String>) {
    require(args.size == 2)
    fun convert(v: Any?): CsvCell = when (v) {
        null -> CsvCell.Empty
        is String -> CsvCell.Text(v)
        is BigDecimal -> {
            val canonical = v.stripTrailingZeros().toPlainString()
            if (canonical == "0") CsvCell.Integer(0)
            else if (v.stripTrailingZeros().scale() <= 0) CsvCell.Integer(v.longValueExact())
            else CsvCell.Decimal.fromCanonical(canonical)
        }
        else -> error("Unsupported fixture cell: $v")
    }
    val golden = JsonReader(File(args[0]).readText(Charsets.UTF_8)).read() as Map<*, *>
    val cases = (golden["cases"] as List<*>).map { it as Map<*, *> }.filter { it["fn"] == "csvCell" }
    check(cases.size == 16)
    cases.forEachIndexed { i,c ->
        val arg = (c["args"] as List<*>).single()
        check(LegacyCsv.cell(convert(arg)) == c["result"]) { "CSV cell fixture $i differs" }
    }
    val reports = JsonReader(File(args[1]).readText(Charsets.UTF_8)).read() as Map<*, *>
    val reportCases = reports["cases"] as List<*>
    check(reportCases.size == 2)
    reportCases.forEachIndexed { i, item ->
        val result = (item as Map<*, *>)["result"] as Map<*, *>
        val rows = (result["rows"] as List<*>).map { row -> (row as List<*>).map(::convert) }
        val expected = Base64.getDecoder().decode(result["contentBase64"] as String)
        check(LegacyCsv.encode(rows).contentEquals(expected)) { "CSV report byte fixture $i differs" }
    }
    val expected = byteArrayOf(0xef.toByte(), 0xbb.toByte(), 0xbf.toByte())
    check(LegacyCsv.encode(emptyList()).contentEquals(expected))
    check(LegacyCsv.cell(CsvCell.Text("-10")) == "'-10")
    check(LegacyCsv.cell(CsvCell.Integer(-10)) == "-10")
    for (unsafe in listOf("=1", "+1", "1\n=1", "-0", "1.0", "01", "NaN", " 1"))
        check(runCatching { CsvCell.Decimal.fromCanonical(unsafe) }.isFailure)
    println("Matched ${cases.size} legacy cells and the byte serialization of ${reportCases.size} captured legacy report row sets; this test does not verify report generation.")
}
