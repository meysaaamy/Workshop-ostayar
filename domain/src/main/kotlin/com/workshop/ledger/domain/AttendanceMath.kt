package com.workshop.ledger.domain

import java.math.BigDecimal

sealed interface AttendanceValue {
    data object Full : AttendanceValue
    data object Half : AttendanceValue
    data object Absent : AttendanceValue
    data class Hours(val hours: BigDecimal) : AttendanceValue
    /** Raw legacy strings before import normalization; compute counts these as hourly. */
    enum class LegacyString : AttendanceValue { FULL, HALF, ABSENT }
}

/** Pure calculation on validated domain values; null represents invalid legacy hours. */
object AttendanceMath {
    fun fraction(value: AttendanceValue, overtimeFactor: BigDecimal): BigDecimal? = when (value) {
        AttendanceValue.Full -> BigDecimal.ONE
        AttendanceValue.Half -> BigDecimal("0.5")
        AttendanceValue.Absent, AttendanceValue.LegacyString.ABSENT -> BigDecimal.ZERO
        AttendanceValue.LegacyString.FULL -> BigDecimal.ONE
        AttendanceValue.LegacyString.HALF -> BigDecimal("0.5")
        is AttendanceValue.Hours -> {
            val hours = LegacyBinary64.decimal(value.hours)
            val zero = LegacyBinary64.zero
            val eight = LegacyBinary64.decimal(BigDecimal("8"))
            val max = LegacyBinary64.decimal(BigDecimal("24"))
            if (hours < zero || hours > max) null
            else if (hours <= eight) (hours / eight).toBigDecimal()
            else ((eight + (hours - eight) * LegacyBinary64.decimal(overtimeFactor)) / eight).toBigDecimal()
        }
    }

    /** The legacy engine applies the absence deduction to all users, including masters. */
    fun dayFraction(value: AttendanceValue, overtimeFactor: BigDecimal, absentPenalty: BigDecimal): BigDecimal? =
        if (value == AttendanceValue.Absent || value == AttendanceValue.LegacyString.ABSENT) (LegacyBinary64.zero - LegacyBinary64.decimal(absentPenalty)).toBigDecimal()
        else fraction(value, overtimeFactor)
}
