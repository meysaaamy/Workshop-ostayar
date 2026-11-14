package com.workshop.ledger.domain

import java.io.File
import java.math.BigDecimal
import java.security.MessageDigest
import java.time.LocalDate
import stayar.parity.JsonReader
import stayar.parity.firstDifference

private fun hash(file: File): String = MessageDigest.getInstance("SHA-256")
    .digest(file.readBytes()).joinToString("") { "%02x".format(it.toInt() and 255) }
private fun integer(v: Any?): Int = (v as BigDecimal).intValueExact()

fun main(args: Array<String>) {
    require(args.size == 3) { "Usage: DomainParity <legacy.json> <calendar.jsonl> <legacy.apk>" }
    val apkHash = hash(File(args[2]))
    val source = JsonReader(File(args[0]).readText(Charsets.UTF_8)).read() as Map<*, *>
    check((source["source"] as Map<*, *>)["apkSha256"] == apkHash)
    val cases = (source["cases"] as List<*>).map { it as Map<*, *> }.filter { it["fn"] == "parseMoney" }
    check(cases.size >= 1000) { "Missing money fixture cases" }
    cases.forEachIndexed { index, c ->
        val raw = (c["args"] as List<*>)[0] as String?
        val parsed = MoneyParser.parse(raw)
        val actual = mapOf("v" to BigDecimal.valueOf(parsed.value), "has" to parsed.has,
            "dec" to parsed.decimalDigitsDiscarded, "clamped" to parsed.clamped)
        val difference = firstDifference(c["result"], actual)
        check(difference == null) { "parseMoney case $index input=$raw: $difference" }
    }
    fun dateFromLegacy(text: String): LocalDate {
        val (year, month, day) = text.split('/').map(String::toInt)
        val g = JalaliCalendar.toGregorian(year, month, day)
        return LocalDate.of(g.year, g.month, g.day)
    }
    val wageCases = (source["cases"] as List<*>).map { it as Map<*, *> }.filter { it["fn"] == "wageRateFor" }
    check(wageCases.size >= 300) { "Missing wage fixture cases" }
    wageCases.forEachIndexed { index, c ->
        val arguments = c["args"] as List<*>
        val project = arguments[3] as Map<*, *>
        val wages = (project["wages"] as List<*>).map {
            val w = it as Map<*, *>
            EffectiveWage(w["userId"] as String, dateFromLegacy(w["effectiveDate"] as String), (w["rate"] as BigDecimal).longValueExact())
        }
        val actual = WageRates.forDate(arguments[0] as String, dateFromLegacy(arguments[1] as String), wages)
        check(actual == (c["result"] as BigDecimal).longValueExact()) { "wageRateFor case $index expected ${c["result"]} got $actual" }
    }
    val attendanceCases = (source["cases"] as List<*>).map { it as Map<*, *> }.filter { it["fn"] == "attFraction" }
    check(attendanceCases.size >= 500)
    attendanceCases.forEachIndexed { index, c ->
        val arguments = c["args"] as List<*>
        val value = when (val input = arguments[0]) {
            is Map<*, *> -> AttendanceValue.Hours(input["h"] as BigDecimal)
            is BigDecimal -> when {
                input.compareTo(BigDecimal.ONE) == 0 -> AttendanceValue.Full
                input.compareTo(BigDecimal("0.5")) == 0 -> AttendanceValue.Half
                input.compareTo(BigDecimal.ZERO) == 0 -> AttendanceValue.Absent
                else -> error("Unsupported attendance fixture $input")
            }
            else -> error("Unsupported attendance fixture $input")
        }
        val actual = AttendanceMath.fraction(value, arguments[1] as BigDecimal)
        val expected = c["result"] as BigDecimal?
        if (expected == null) check(actual == null) { "attendance case $index: expected invalid, got $actual" }
        else check(actual != null && LegacyBinary64.decimal(actual).compareTo(LegacyBinary64.decimal(expected)) == 0) {
            "attendance case $index expected $expected got $actual"
        }
    }
    check(AttendanceMath.dayFraction(AttendanceValue.Absent,BigDecimal.ONE,BigDecimal("0.25"))!!.compareTo(BigDecimal("-0.25")) == 0)
    val allocations = (source["cases"] as List<*>).map { it as Map<*, *> }.filter { it["fn"] == "allocate" }
    check(allocations.size >= 1000)
    allocations.forEachIndexed { index, c ->
        val arguments = c["args"] as List<*>
        val total = arguments[0] as BigDecimal
        val weights = (arguments[1] as List<*>).map { it as BigDecimal }
        val actual = LargestRemainder.allocate(total, weights)
        val expected = (c["result"] as List<*>).map { (it as BigDecimal).longValueExact() }
        check(actual == expected) { "allocate case $index total=$total weights=$weights expected $expected got $actual" }
        if (weights.any { it.signum() != 0 }) check(actual.fold(java.math.BigInteger.ZERO) { a, b -> a + java.math.BigInteger.valueOf(b) } == LegacyBinary64.decimal(total).roundJs())
    }
    fun dateOrNull(text: String?): LocalDate? {
        if (text.isNullOrEmpty()) return null
        val match = Regex("^(\\d{4})/(\\d{2})/(\\d{2})$").matchEntire(text) ?: return null
        val y = match.groupValues[1].toInt(); val m = match.groupValues[2].toInt(); val d = match.groupValues[3].toInt()
        return if (JalaliCalendar.isValidDate(y,m,d)) dateFromLegacy(text) else null
    }
    val receipts = (source["cases"] as List<*>).map { it as Map<*, *> }.filter { it["fn"] == "receiptShare" }
    var receiptCount = 0
    for (c in receipts) {
        val args = c["args"] as List<*>
        val receipt = args[0] as Map<*, *>; val range = args[1] as Map<*, *>
        val start = dateOrNull(receipt["dateFrom"] as String?) ?: continue
        val end = dateOrNull(receipt["dateTo"] as String?) ?: continue
        val rawFrom = range["from"] as String?; val rawTo = range["to"] as String?
        val from = dateOrNull(rawFrom); val to = dateOrNull(rawTo)
        if ((!rawFrom.isNullOrEmpty() && from == null) || (!rawTo.isNullOrEmpty() && to == null)) continue
        val actual = ReceiptShares.share((receipt["amount"] as BigDecimal).longValueExact(), start, end, DateRange(from,to))
        check(actual == (c["result"] as BigDecimal).longValueExact()) { "receiptShare $args expected ${c["result"]} got $actual" }
        receiptCount++
    }
    check(receiptCount >= 500) { "Missing valid receipt cases: $receiptCount" }
    fun binary(v: String) = LegacyBinary64.decimal(BigDecimal(v))
    check(binary("-2.5").roundJs() == java.math.BigInteger.valueOf(-2))
    check(binary("2.5").roundJs() == java.math.BigInteger.valueOf(3))
    val eight = binary("8")
    val legacyWage = ((eight + binary("3") * binary("1.333")) / eight) * binary("4000")
    check(legacyWage.roundJs() == java.math.BigInteger.valueOf(5999))
    var count = 0
    File(args[1]).bufferedReader(Charsets.UTF_8).use { reader ->
        val header = JsonReader(reader.readLine() ?: error("Missing calendar header")).read() as Map<*, *>
        check(header["apkSha256"] == apkHash && header["format"] == "stayar-calendar-v1")
        while (true) {
            val line = reader.readLine() ?: break
            val r = (JsonReader(line).read() as List<*>).map(::integer)
            check(r.size == 9)
            check(JalaliCalendar.isValidDate(r[0], r[1], r[2]))
            val g = JalaliCalendar.toGregorian(r[0], r[1], r[2])
            check(listOf(g.year,g.month,g.day) == r.subList(3,6)) { "Gregorian mismatch for ${r.subList(0,3)}" }
            val j = JalaliCalendar.toJalali(g.year,g.month,g.day)
            check(listOf(j.year,j.month,j.day) == r.subList(6,9)) { "Jalali mismatch for ${r.subList(3,6)}" }
            count++
        }
    }
    check(count == 182986) { "Missing calendar cases: $count" }
    check(JalaliCalendar.isValidDate(1399,12,30) && JalaliCalendar.isValidDate(1403,12,30))
    check(!JalaliCalendar.isValidDate(1402,12,30))
    val edge = JalaliCalendar.toGregorian(3177,10,12)
    check(runCatching { JalaliCalendar.toJalali(edge.year,edge.month,edge.day) }.isFailure)
    val random = java.util.Random(0x517aL)
    val firstDay = LocalDate.of(2024,3,20)
    val lastDay = firstDay.plusDays(30)
    repeat(2000) {
        val total = 999_999_999_999L - random.nextInt(100_000)
        val weights = List(1 + random.nextInt(6)) { BigDecimal.valueOf(random.nextInt(10001).toLong(), 3) }
        val allocated = LargestRemainder.allocate(BigDecimal.valueOf(total), weights)
        val sum = allocated.fold(java.math.BigInteger.ZERO) { a, n -> a + java.math.BigInteger.valueOf(n) }
        check(sum == if (weights.any { it.signum() != 0 }) java.math.BigInteger.valueOf(total) else java.math.BigInteger.ZERO)
        val split = firstDay.plusDays(random.nextInt(30).toLong())
        val left = ReceiptShares.share(total,firstDay,lastDay,DateRange(firstDay,split))
        val right = ReceiptShares.share(total,firstDay,lastDay,DateRange(split.plusDays(1),lastDay))
        check(java.math.BigInteger.valueOf(left) + java.math.BigInteger.valueOf(right) == java.math.BigInteger.valueOf(total))
    }
    println("Matched ${cases.size} money-input cases, ${wageCases.size} wage-selection cases, ${attendanceCases.size} attendance cases, ${allocations.size} allocation cases, $receiptCount valid receipt cases and $count calendar dates against legacy golden data.")
    println("Additionally checked 2000 allocation and receipt-partition properties. This line covers isolated domain functions only; compute is checked separately by FinancialParity. Database, Android UI and APK remain untested.")
}
