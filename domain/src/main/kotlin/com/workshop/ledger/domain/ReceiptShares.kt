package com.workshop.ledger.domain

import java.math.BigInteger
import java.time.LocalDate
import java.time.temporal.ChronoUnit

data class DateRange(val from: LocalDate? = null, val to: LocalDate? = null)

/** Apportion an inclusive receipt interval exactly as the legacy cumulative JavaScript rounding does. */
object ReceiptShares {
    fun share(amount: Long, start: LocalDate, end: LocalDate, range: DateRange): Long {
        if (amount == 0L) return 0
        if (range.from != null && end < range.from) return 0
        if (range.to != null && start > range.to) return 0
        // Legacy fallback for an unreadable/inverted receipt interval. Invalid dates cannot be LocalDate.
        if (end < start) return amount
        val days = ChronoUnit.DAYS.between(start, end) + 1
        val from = if (range.from != null && range.from > start) range.from else start
        val to = if (range.to != null && range.to < end) range.to else end
        if (to < from) return 0
        val k0 = ChronoUnit.DAYS.between(start, from)
        val k1 = ChronoUnit.DAYS.between(start, to) + 1
        if (k0 == 0L && k1 == days) return amount
        val original = LegacyBinary64.integer(BigInteger.valueOf(amount))
        val divisor = LegacyBinary64.integer(BigInteger.valueOf(days))
        fun accumulated(k: Long): BigInteger =
            (original * LegacyBinary64.integer(BigInteger.valueOf(k)) / divisor).roundJs()
        return (accumulated(k1) - accumulated(k0)).longValueExact()
    }
}
