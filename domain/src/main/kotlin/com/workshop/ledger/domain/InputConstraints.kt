package com.workshop.ledger.domain

import java.math.BigDecimal

/** Rules for new user input; import validation and legacy repair are separate concerns. */
object InputConstraints {
    const val MAX_AMOUNT = 999_999_999_999L

    fun isValidAmount(value: Long): Boolean = value in 0..MAX_AMOUNT
}

/**
 * Lossless representation of valid legacy hourly attendance. [exactHours] must be persisted
 * as a decimal string, not a floating-point column. Source is retained so an editor can
 * distinguish legacy values from new half-hour input without silently quantizing them.
 * This type alone does not implement persistence or migration.
 */
class RecordedHours private constructor(val exactHours: BigDecimal, val source: Source) {
    enum class Source { NEW, LEGACY }

    fun asAttendance(): AttendanceValue.Hours = AttendanceValue.Hours(exactHours)
    fun decimalForStorage(): String = exactHours.toPlainString()
    val isHalfHour: Boolean get() = exactHours.multiply(TWO).stripTrailingZeros().scale() <= 0

    companion object {
        private val TWO = BigDecimal("2")
        private val MAX = BigDecimal("24")

        /** New UI input is constrained to whole half-hours, including zero and 24. */
        fun newInput(hours: BigDecimal): RecordedHours {
            requireValidRange(hours)
            require(hours.multiply(TWO).stripTrailingZeros().scale() <= 0) { "New hours must be in half-hour increments" }
            return RecordedHours(hours, Source.NEW)
        }

        /** Import/migration retains the original decimal precision, including non-half hours. */
        fun legacy(hours: BigDecimal): RecordedHours {
            requireValidRange(hours)
            return RecordedHours(hours, Source.LEGACY)
        }

        private fun requireValidRange(hours: BigDecimal) {
            require(hours >= BigDecimal.ZERO && hours <= MAX) { "Hours must be between 0 and 24" }
        }
    }
}
