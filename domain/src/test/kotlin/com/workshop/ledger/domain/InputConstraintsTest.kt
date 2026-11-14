package com.workshop.ledger.domain

import java.math.BigDecimal

fun main() {
    fun dec(s: String) = BigDecimal(s)
    fun rejects(action: () -> Unit) { check(runCatching(action).exceptionOrNull() is IllegalArgumentException) }
    for (amount in listOf(0L, 1L, InputConstraints.MAX_AMOUNT)) check(InputConstraints.isValidAmount(amount))
    for (amount in listOf(-1L, InputConstraints.MAX_AMOUNT + 1, Long.MIN_VALUE, Long.MAX_VALUE)) check(!InputConstraints.isValidAmount(amount))
    for (i in 0..48) {
        val h=BigDecimal(i).divide(dec("2")); val saved=RecordedHours.newInput(h)
        check(saved.isHalfHour && saved.source == RecordedHours.Source.NEW)
        check(RecordedHours.newInput(dec(saved.decimalForStorage())).exactHours == h)
    }
    for (raw in listOf("0", "1.25", "1.2500", "0.01", "23.999", "24")) {
        val original=dec(raw); val saved=RecordedHours.legacy(original)
        check(saved.source == RecordedHours.Source.LEGACY)
        check(dec(saved.decimalForStorage()) == original)
        check(saved.asAttendance().hours == original)
        if (!saved.isHalfHour) rejects { RecordedHours.newInput(original) }
    }
    for (raw in listOf("-0.01", "24.01")) {
        rejects { RecordedHours.newInput(dec(raw)) }; rejects { RecordedHours.legacy(dec(raw)) }
    }
    val value=RecordedHours.legacy(dec("1.25")).asAttendance()
    check(AttendanceMath.fraction(value, BigDecimal.ONE)!!.compareTo(dec("0.15625")) == 0)
    println("Checked amount boundaries, all 49 half-hour inputs, and exact legacy-hour round trips; no persistence or migration tested.")
}
