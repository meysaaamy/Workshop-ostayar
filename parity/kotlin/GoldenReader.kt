package stayar.parity

import java.io.File
import java.math.BigDecimal
import java.security.MessageDigest

/** Minimal strict JSON reader for the parity fixtures. Numbers never pass through floating point. */
class JsonReader(private val text: String) {
    private var pos = 0
    private fun ws() { while (pos < text.length && text[pos] in " \t\r\n") pos++ }
    private fun take(c: Char): Boolean { ws(); if (pos < text.length && text[pos] == c) { pos++; return true }; return false }
    private fun fail(): Nothing = error("Invalid JSON at offset $pos")
    fun read(): Any? { val result = value(); ws(); if (pos != text.length) fail(); return result }
    private fun value(): Any? {
        ws(); if (pos >= text.length) fail()
        return when (text[pos]) {
            '{' -> obj()
            '[' -> array()
            '"' -> string()
            't' -> keyword("true", true)
            'f' -> keyword("false", false)
            'n' -> keyword("null", null)
            '-', in '0'..'9' -> number()
            else -> fail()
        }
    }
    private fun keyword(word: String, result: Any?): Any? {
        if (!text.startsWith(word, pos)) fail()
        pos += word.length; return result
    }
    private fun obj(): Map<String, Any?> {
        check(take('{')); val out = linkedMapOf<String, Any?>()
        if (take('}')) return out
        do {
            ws(); if (pos >= text.length || text[pos] != '"') fail()
            val key = string(); if (!take(':') || key in out) fail()
            out[key] = value()
        } while (take(','))
        if (!take('}')) fail(); return out
    }
    private fun array(): List<Any?> {
        check(take('[')); val out = mutableListOf<Any?>()
        if (take(']')) return out
        do { out.add(value()) } while (take(','))
        if (!take(']')) fail(); return out
    }
    private fun string(): String {
        check(text[pos++] == '"'); val result = StringBuilder()
        while (pos < text.length) {
            val c = text[pos++]
            when {
                c == '"' -> return result.toString()
                c == '\\' -> {
                    if (pos >= text.length) fail()
                    when (val e = text[pos++]) {
                        '"', '\\', '/' -> result.append(e)
                        'b' -> result.append('\b')
                        'f' -> result.append('\u000c')
                        'n' -> result.append('\n')
                        'r' -> result.append('\r')
                        't' -> result.append('\t')
                        'u' -> {
                            if (pos + 4 > text.length) fail()
                            val n = text.substring(pos, pos + 4).toIntOrNull(16) ?: fail()
                            result.append(n.toChar()); pos += 4
                        }
                        else -> fail()
                    }
                }
                c.code < 32 -> fail()
                else -> result.append(c)
            }
        }
        fail()
    }
    private fun number(): BigDecimal {
        val start = pos
        if (text[pos] == '-') pos++
        if (pos >= text.length) fail()
        if (text[pos] == '0') pos++ else {
            if (text[pos] !in '1'..'9') fail()
            while (pos < text.length && text[pos] in '0'..'9') pos++
        }
        if (pos < text.length && text[pos] == '.') {
            pos++; val first = pos
            while (pos < text.length && text[pos] in '0'..'9') pos++
            if (pos == first) fail()
        }
        if (pos < text.length && text[pos] in "eE") {
            pos++; if (pos < text.length && text[pos] in "+-") pos++
            val first = pos
            while (pos < text.length && text[pos] in '0'..'9') pos++
            if (pos == first) fail()
        }
        return text.substring(start, pos).toBigDecimal()
    }
}

private fun sha256(file: File): String = MessageDigest.getInstance("SHA-256")
    .digest(file.readBytes()).joinToString("") { "%02x".format(it.toInt() and 255) }

fun main(args: Array<String>) {
    require(args.size == 2) { "Usage: GoldenReader <golden.json> <legacy.apk>" }
    // Reader sanity checks include escaped Unicode, null, nested values and malformed JSON.
    check(JsonReader("""["\u06f1",1.25e2,null]""").read() == listOf("۱", BigDecimal("1.25e2"), null))
    for (bad in listOf("01", "[1,]", "{\"a\":1,\"a\":2}", "true false", "1e")) {
        check(runCatching { JsonReader(bad).read() }.isFailure) { "Accepted invalid JSON: $bad" }
    }
    val root = JsonReader(File(args[0]).readText(Charsets.UTF_8)).read() as Map<*, *>
    val source = root["source"] as Map<*, *>
    check(source["apkSha256"] == sha256(File(args[1]))) { "APK SHA-256 mismatch" }
    val cases = root["cases"] as List<*>
    require(cases.isNotEmpty())
    val counts = sortedMapOf<String, Int>()
    cases.forEachIndexed { index, item ->
        val case = item as? Map<*, *> ?: error("case $index is not an object")
        val fn = case["fn"] as? String ?: error("case $index has no function")
        check(case["args"] is List<*> && case.containsKey("result")) { "invalid case $index" }
        counts[fn] = (counts[fn] ?: 0) + 1
    }
    println("Read ${cases.size} legacy cases: $counts")
    println("Fixture structure and APK hash verified. No Kotlin engine comparison was performed.")
}
