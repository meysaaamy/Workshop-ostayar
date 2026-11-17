package stayar.parity

import java.io.File
import java.security.MessageDigest

private fun fileHash(file: File): String {
    val hash = MessageDigest.getInstance("SHA-256")
    file.inputStream().buffered().use { input ->
        val buffer = ByteArray(65536)
        while (true) {
            val n = input.read(buffer)
            if (n < 0) break
            hash.update(buffer, 0, n)
        }
    }
    return hash.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
}

/** Streaming comparator: each result row is [gy,gm,gd,jy,jm,jd] for the corresponding golden input. */
fun main(args: Array<String>) {
    require(args.size == 3) { "Usage: CompareCalendar <golden.jsonl> <actual.jsonl> <legacy.apk>" }
    val golden = File(args[0]); val actual = File(args[1]); val apk = File(args[2])
    golden.bufferedReader(Charsets.UTF_8).use { expected ->
        actual.bufferedReader(Charsets.UTF_8).use { got ->
            val source = JsonReader(expected.readLine() ?: error("Missing golden header")).read() as Map<*, *>
            require(source["format"] == "stayar-calendar-v1") { "Unknown calendar format" }
            require(source["apkSha256"] == fileHash(apk)) { "APK SHA-256 mismatch" }
            val resultHeader = JsonReader(got.readLine() ?: error("Missing actual header")).read() as Map<*, *>
            require(resultHeader["goldenSha256"] == fileHash(golden)) { "Actual results refer to a different golden file" }
            var count = 0; var differences = 0
            while (true) {
                val line = expected.readLine() ?: break
                count++
                val value = got.readLine() ?: error("Missing actual row $count")
                val original = JsonReader(line).read() as List<*>
                require(original.size == 9) { "Invalid golden row $count" }
                val candidate = JsonReader(value).read() as List<*>
                val difference = firstDifference(original.subList(3, 9), candidate)
                if (difference != null) {
                    differences++
                    if (differences <= 20) System.err.println("row $count input ${original.subList(0,3)}: $difference")
                }
            }
            require(count > 0) { "Calendar golden is empty" }
            require(got.readLine() == null) { "Actual results contain extra rows" }
            check(differences == 0) { "$differences of $count calendar cases differ" }
            println("Compared $count calendar cases exactly. This is parity only when actual results come from the new calendar engine.")
        }
    }
}
