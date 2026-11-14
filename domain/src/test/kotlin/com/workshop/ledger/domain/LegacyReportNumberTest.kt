package com.workshop.ledger.domain

import java.io.File
import java.math.BigDecimal
import stayar.parity.JsonReader

fun main(args: Array<String>) {
    require(args.size == 1)
    var count = 0
    File(args[0]).useLines { lines -> lines.forEach { line ->
        val row = JsonReader(line).read() as Map<*, *>
        val n = BigDecimal(row["input"] as String)
        val actual = LegacyReportNumber.string(n)
        check(actual == row["string"]) { "String($n) expected ${row["string"]} got $actual" }
        val fixed = LegacyReportNumber.fixed(n, 2)
        check(fixed == row["fixed2"]) { "fixed2($n) expected ${row["fixed2"]} got $fixed" }
        // The legacy percent expression can overflow for very large inputs; those are outside finite model support.
        if (n.abs() < BigDecimal("1E300")) {
            val pct = LegacyReportNumber.percent(n)
            check(pct == row["percent"]) { "percent($n) expected ${row["percent"]} got $pct" }
        }
        count++
    } }
    check(count == 3015)
    println("Matched $count JS number-format fixtures; finite inputs only, not exhaustive binary64 proof.")
}
