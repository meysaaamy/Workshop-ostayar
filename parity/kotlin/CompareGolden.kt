package stayar.parity

import java.io.File
import java.math.BigDecimal
import java.security.MessageDigest

/** Compare JSON values structurally. Numbers compare exactly, with no epsilon or floating point. */
fun firstDifference(expected: Any?, actual: Any?, path: String = "$"): String? {
    val label = path
    if (expected is BigDecimal && actual is BigDecimal) {
        return if (expected.compareTo(actual) == 0) null else "$label: expected $expected, actual $actual"
    }
    if (expected is Map<*, *> && actual is Map<*, *>) {
        val missing = expected.keys - actual.keys
        val extra = actual.keys - expected.keys
        if (missing.isNotEmpty() || extra.isNotEmpty()) return "$label: missing keys $missing; extra keys $extra"
        for (key in expected.keys) {
            val diff = firstDifference(expected[key], actual[key], "$label.${key}")
            if (diff != null) return diff
        }
        return null
    }
    if (expected is List<*> && actual is List<*>) {
        if (expected.size != actual.size) return "$label: expected ${expected.size} elements, actual ${actual.size}"
        for (i in expected.indices) {
            val diff = firstDifference(expected[i], actual[i], "$label[$i]")
            if (diff != null) return diff
        }
        return null
    }
    return if (expected == actual) null else "$label: expected $expected, actual $actual"
}

private fun sha256(file: File) = MessageDigest.getInstance("SHA-256")
    .digest(file.readBytes()).joinToString("") { "%02x".format(it.toInt() and 255) }

/** results must contain one result per case, in order, bound to the exact golden file checksum. */
fun main(args: Array<String>) {
    require(args.size == 3) { "Usage: CompareGolden <golden.json> <actual.json> <legacy.apk>" }
    val goldenFile = File(args[0])
    val root = JsonReader(goldenFile.readText(Charsets.UTF_8)).read() as Map<*, *>
    val source = root["source"] as Map<*, *>
    require(source["apkSha256"] == sha256(File(args[2]))) { "APK SHA-256 mismatch" }
    val cases = root["cases"] as List<*>
    require(cases.isNotEmpty()) { "Empty golden cases" }
    val actual = JsonReader(File(args[1]).readText(Charsets.UTF_8)).read() as Map<*, *>
    require(actual["goldenSha256"] == sha256(goldenFile)) { "Actual results refer to a different golden file" }
    val results = actual["results"] as List<*>
    require(results.size == cases.size) { "Expected ${cases.size} results; got ${results.size}" }
    var failed = 0
    cases.forEachIndexed { i, item ->
        val case = item as Map<*, *>
        require(case.containsKey("result") && case["fn"] is String && case["args"] is List<*>) { "Invalid case $i" }
        val diff = firstDifference(case["result"], results[i])
        if (diff != null) {
            failed++
            if (failed <= 20) System.err.println("case[$i] ${case["fn"]}: $diff")
        }
    }
    check(failed == 0) { "$failed of ${cases.size} cases differ" }
    println("Compared ${cases.size} cases exactly. This confirms parity only if actual results came from the new engine.")
}
