package com.workshop.ledger.data.backup

import java.io.File
import java.math.BigDecimal
import stayar.parity.JsonReader

fun main(args: Array<String>) {
    require(args.size == 1)
    fun convert(v: Any?): JsonValue = when(v) {
        null -> JsonValue.Null
        is Boolean -> JsonValue.Bool(v)
        is String -> JsonValue.Text(v)
        is BigDecimal -> JsonValue.Number(v.toString())
        is List<*> -> JsonValue.Array(v.map(::convert))
        is Map<*, *> -> JsonValue.Object(v.entries.associate { (key,value) -> key as String to convert(value) })
        else -> error("Unsupported fixture")
    }
    val root=JsonReader(File(args[0]).readText(Charsets.UTF_8)).read() as Map<*, *>
    val cases=(root["cases"] as List<*>).map { it as Map<*, *> }.filter { it["fn"]=="clientValidate" }
    check(cases.size>=50)
    cases.forEachIndexed { index,c ->
        val input=convert((c["args"] as List<*>).single())
        val result=LegacyClientValidator.validate(input)
        check(result==c["result"]) { "clientValidate case $index: expected=${c["result"]} actual=$result" }
    }
    val unsupported=JsonValue.Object(mapOf("projects" to JsonValue.Array(listOf(JsonValue.Object(mapOf(
        "id" to JsonValue.Text("p"),"name" to JsonValue.Text("project"),"users" to JsonValue.Object(emptyMap())
    ))))))
    check(runCatching { LegacyClientValidator.validate(unsupported) }.exceptionOrNull() is UnsupportedLegacyValidationShape)
    println("Matched ${cases.size} legacy client validation messages; native validation, normalization and restore are not implemented.")
}
