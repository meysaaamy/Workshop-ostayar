package com.workshop.ledger.data.backup

import java.io.File
import java.math.BigDecimal
import stayar.parity.JsonReader

fun main(args: Array<String>) {
    require(args.size == 2)
    fun convert(v: Any?): JsonValue = when(v) {
        null -> JsonValue.Null
        is Boolean -> JsonValue.Bool(v)
        is String -> JsonValue.Text(v)
        is BigDecimal -> JsonValue.Number(v.toString())
        is List<*> -> JsonValue.Array(v.map(::convert))
        is Map<*, *> -> JsonValue.Object(v.entries.associate { (k,value) -> k as String to convert(value) })
        else -> error("Unexpected fixture type")
    }
    val root=JsonReader(File(args[0]).readText(Charsets.UTF_8)).read() as Map<*, *>
    val cases=(root["cases"] as List<*>).map { it as Map<*, *> }
    val counts=mutableMapOf<String,Int>()
    for(c in cases) {
        val fn=c["fn"] as String
        if(fn !in setOf("cvDate","cvAmount","cvAttValue")) continue
        val value=convert((c["args"] as List<*>).single())
        val result=when(fn) {
            "cvDate" -> LegacyFieldValidators.validDate(value)
            "cvAmount" -> LegacyFieldValidators.validAmount(value)
            else -> LegacyFieldValidators.validAttendance(value)
        }
        check(result == c["result"]) { "$fn input=$value expected=${c["result"]} actual=$result" }
        counts[fn]=(counts[fn] ?: 0)+1
    }
    check((counts["cvDate"] ?: 0) >= 300 && (counts["cvAmount"] ?: 0) >= 500 && (counts["cvAttValue"] ?: 0) >= 500)
    val raw=JsonReader(File(args[1]).readText(Charsets.UTF_8)).read() as Map<*, *>
    for(item in raw["cases"] as List<*>) {
        val row=item as Map<*, *>;val number=JsonValue.Number(row["raw"] as String)
        check(LegacyFieldValidators.validAmount(number)==row["amount"]) { "amount ${row["raw"]}" }
        check(LegacyFieldValidators.validAttendance(number)==row["attendance"]) { "attendance ${row["raw"]}" }
        val objectHours=JsonValue.Object(mapOf("t" to JsonValue.Text("h"),"h" to number))
        check(LegacyFieldValidators.validAttendance(objectHours)==row["hours"]) { "hours ${row["raw"]}" }
    }
    check(!LegacyFieldValidators.validAmount(JsonValue.Number("not a number")))
    println("Matched legacy field validators: $counts plus ${(raw["cases"] as List<*>).size} raw numeric cases; not a complete backup validator.")
}
