package com.workshop.ledger.domain

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

/** Numeric presentation for the old CSV; finite inputs only. Does not calculate money. */
object LegacyReportNumber {
    private val boundary = BigDecimal("1E21")
    private fun binary(value: BigDecimal) = LegacyBinary64.decimal(value)

    /** Approximate JS String(number) via the shortest decimal that round-trips to binary64. */
    fun string(value: BigDecimal): String {
        val v = binary(value)
        if (v.compareTo(LegacyBinary64.zero) == 0) return "0"
        val exact = v.toBigDecimal()
        val sign = if (exact.signum() < 0) "-" else ""
        val magnitude = exact.abs()
        val target = binary(magnitude)
        var chosen: BigDecimal? = null
        for (digits in 1..17) {
            val rounded = magnitude.round(MathContext(digits, RoundingMode.HALF_EVEN))
            val step = rounded.ulp()
            val candidates = listOf(rounded, rounded.subtract(step), rounded.add(step))
                .filter { candidate -> candidate.signum() > 0 && runCatching { binary(candidate).compareTo(target) == 0 }.getOrDefault(false) }
            if (candidates.isNotEmpty()) {
                chosen = candidates.minWithOrNull(compareBy<BigDecimal> { it.subtract(magnitude).abs() }
                    .thenBy { it.stripTrailingZeros().unscaledValue().abs().mod(java.math.BigInteger.TWO).toInt() })
                break
            }
        }
        val n = (chosen ?: error("Cannot format legacy binary64")).stripTrailingZeros()
        val exponent = n.precision() - n.scale() - 1
        if (exponent in -6..20) return sign + n.toPlainString()
        val digits = n.unscaledValue().abs().toString()
        val mantissa = digits.substring(0, 1) + if (digits.length > 1) "." + digits.substring(1) else ""
        return sign + mantissa + "e" + (if (exponent >= 0) "+" else "") + exponent
    }

    /** Same shape as JavaScript String(+value.toFixed(digits)), including negative zero. */
    fun fixed(value: BigDecimal, digits: Int): String {
        require(digits in 0..100)
        val exact = binary(value).toBigDecimal()
        if (exact.abs() >= boundary) return string(exact)
        return string(exact.setScale(digits, RoundingMode.HALF_UP))
    }

    /** Multiply in the legacy binary arithmetic before rounding to two decimals. */
    fun percent(value: BigDecimal): String = fixed((binary(value) * binary(BigDecimal("100"))).toBigDecimal(), 2)
}
