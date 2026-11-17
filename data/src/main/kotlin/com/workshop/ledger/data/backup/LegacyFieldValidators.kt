package com.workshop.ledger.data.backup

import com.workshop.ledger.domain.JalaliCalendar
import com.workshop.ledger.domain.LegacyNumberComparison
import java.math.BigDecimal

/** Selected legacy web validators. They do not validate or normalize a complete backup. */
object LegacyFieldValidators {
    private val zero = BigDecimal.ZERO
    private val half = BigDecimal("0.5")
    private val one = BigDecimal.ONE
    private val hours = BigDecimal("24")
    private val money = BigDecimal("999999999999")
    private val dateRegex = Regex("^[0-9]{4}/(0[1-9]|1[0-2])/(0[1-9]|[12][0-9]|3[01])$")

    /** cvDate from the web layer, not the different native validator. */
    fun validDate(value: JsonValue): Boolean {
        val s = (value as? JsonValue.Text)?.value ?: return false
        if (!dateRegex.matches(s)) return false
        val year = s.substring(0,4).toInt()
        return year in 1200..1700 && JalaliCalendar.isValidDate(year, s.substring(5,7).toInt(), s.substring(8,10).toInt())
    }

    internal fun number(value: JsonValue?): BigDecimal? {
        val raw = (value as? JsonValue.Number)?.raw ?: return null
        if (raw.length > 128) return null
        val decimal = raw.toBigDecimalOrNull() ?: return null
        if (decimal.signum() == 0) return zero
        val exponent = decimal.precision().toLong() - decimal.scale().toLong() - 1
        // Below this magnitude every binary64 value underflows to zero. Above it overflows.
        if (exponent < -325) return zero
        if (exponent > 308) return null
        return decimal
    }
    internal fun compare(value: BigDecimal, other: BigDecimal): Int? =
        try { LegacyNumberComparison.compare(value,other) } catch (_: ArithmeticException) { null } catch (_: IllegalArgumentException) { null }

    /** The old web validator allows fractional money; this is NOT the new Long input policy. */
    fun validAmount(value: JsonValue): Boolean {
        val v = number(value) ?: return false
        val lower = compare(v,zero) ?: return false
        val upper = compare(v,money) ?: return false
        return lower >= 0 && upper <= 0
    }

    /** Legacy imported hours include exact non-half-hour values such as 1.25. */
    fun validAttendance(value: JsonValue): Boolean {
        if (value is JsonValue.Number) {
            val v = number(value) ?: return false
            return listOf(zero,half,one).any { compare(v,it) == 0 }
        }
        val obj = value as? JsonValue.Object ?: return false
        if (obj.fields["t"] != JsonValue.Text("h")) return false
        val v = number(obj.fields["h"]) ?: return false
        val lower = compare(v,zero) ?: return false
        val upper = compare(v,hours) ?: return false
        return lower >= 0 && upper <= 0
    }
}
