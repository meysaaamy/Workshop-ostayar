package com.workshop.ledger.domain

import java.math.BigDecimal
import java.math.BigInteger

/**
 * Reproduce the legacy JavaScript arithmetic without floating-point primitives.
 * Decimal inputs are rounded to binary64 using integer ratios; each arithmetic operation is
 * separately rounded to nearest-even, as in JavaScript. Only finite values are supported.
 */
internal class LegacyBinary64 private constructor(private val significand: BigInteger, private val exponent: Int) : Comparable<LegacyBinary64> {
    companion object {
        private val ZERO = BigInteger.ZERO
        private val ONE = BigInteger.ONE
        private val TWO = BigInteger.TWO
        val zero = LegacyBinary64(ZERO, 0)

        fun decimal(value: BigDecimal): LegacyBinary64 {
            require(value.scale() in -10000..10000) { "Decimal scale exceeds supported bound" }
            val scale = value.scale()
            return if (scale >= 0) rational(value.unscaledValue(), BigInteger.TEN.pow(scale))
            else rational(value.unscaledValue() * BigInteger.TEN.pow(-scale), ONE)
        }
        fun integer(value: BigInteger): LegacyBinary64 = rational(value, ONE)

        private fun rational(numerator: BigInteger, denominator: BigInteger): LegacyBinary64 {
            require(denominator.signum() > 0)
            if (numerator.signum() == 0) return zero
            val negative = numerator.signum() < 0
            val n = numerator.abs()
            var k = n.bitLength() - denominator.bitLength()
            val less = if (k >= 0) n < denominator.shiftLeft(k) else n.shiftLeft(-k) < denominator
            if (less) k--
            if (k > 1023) throw ArithmeticException("Legacy binary64 overflow")
            val exponent = maxOf(k - 52, -1074)
            val nn = if (exponent < 0) n.shiftLeft(-exponent) else n
            val dd = if (exponent > 0) denominator.shiftLeft(exponent) else denominator
            val parts = nn.divideAndRemainder(dd)
            var q = parts[0]
            val cmp = (parts[1] * TWO).compareTo(dd)
            if (cmp > 0 || (cmp == 0 && q.testBit(0))) q += ONE
            if (q == ZERO) return zero
            if (exponent == 971 && q.bitLength() > 53) throw ArithmeticException("Legacy binary64 overflow")
            return LegacyBinary64(if (negative) -q else q, exponent)
        }
    }
    private fun ratio(): Pair<BigInteger, BigInteger> =
        if (exponent >= 0) significand.shiftLeft(exponent) to BigInteger.ONE
        else significand to BigInteger.ONE.shiftLeft(-exponent)

    operator fun plus(other: LegacyBinary64): LegacyBinary64 {
        val e = minOf(exponent, other.exponent)
        val n = significand.shiftLeft(exponent - e) + other.significand.shiftLeft(other.exponent - e)
        return if (e >= 0) rational(n.shiftLeft(e), BigInteger.ONE) else rational(n, BigInteger.ONE.shiftLeft(-e))
    }
    operator fun minus(other: LegacyBinary64): LegacyBinary64 = this + LegacyBinary64(-other.significand, other.exponent)
    operator fun times(other: LegacyBinary64): LegacyBinary64 {
        val n = significand * other.significand
        val e = exponent + other.exponent
        return if (e >= 0) rational(n.shiftLeft(e), BigInteger.ONE) else rational(n, BigInteger.ONE.shiftLeft(-e))
    }
    operator fun div(other: LegacyBinary64): LegacyBinary64 {
        require(other.significand.signum() != 0)
        var n = significand
        var d = other.significand
        if (d.signum() < 0) { n = -n; d = -d }
        val e = exponent - other.exponent
        return if (e >= 0) rational(n.shiftLeft(e), d) else rational(n, d.shiftLeft(-e))
    }
    private fun floorRatio(n: BigInteger, d: BigInteger): BigInteger {
        val (q, r) = n.divideAndRemainder(d)
        return if (n.signum() < 0 && r.signum() != 0) q - BigInteger.ONE else q
    }
    fun toBigDecimal(): BigDecimal = if (exponent >= 0) BigDecimal(significand.shiftLeft(exponent))
        else BigDecimal(significand * BigInteger.valueOf(5).pow(-exponent), -exponent)
    fun floor(): BigInteger { val (n, d) = ratio(); return floorRatio(n, d) }
    fun roundJs(): BigInteger { val (n, d) = ratio(); return floorRatio(n * BigInteger.TWO + d, d * BigInteger.TWO) }
    override fun compareTo(other: LegacyBinary64): Int {
        val e = minOf(exponent, other.exponent)
        return significand.shiftLeft(exponent - e).compareTo(other.significand.shiftLeft(other.exponent - e))
    }
}

object LargestRemainder {
    /** Nonnegative weights are the compute engine's workFinal values; sums and shares stay integral. */
    fun allocate(total: BigDecimal, weights: List<BigDecimal>): List<Long> {
        require(weights.all { it.signum() >= 0 }) { "Weights must be nonnegative" }
        val t = LegacyBinary64.decimal(total).roundJs()
        t.longValueExact() // reject totals that cannot be represented by the domain's Long money type
        val sum = weights.map(LegacyBinary64::decimal).fold(LegacyBinary64.zero) { a, b -> a + b }
        if (sum == LegacyBinary64.zero || sum.compareTo(LegacyBinary64.zero) == 0) return weights.map { 0L }
        val amount = t.abs()
        val raw = weights.map { LegacyBinary64.integer(amount) * LegacyBinary64.decimal(it) / sum }
        val floors = raw.map { it.floor() }.toMutableList()
        var remainder = amount - floors.fold(BigInteger.ZERO, BigInteger::add)
        val fractions = raw.mapIndexed { index, value -> value - LegacyBinary64.integer(floors[index]) }
        val order = weights.indices.sortedWith { a, b ->
            val cmp = fractions[b].compareTo(fractions[a])
            if (cmp != 0) cmp else a.compareTo(b)
        }
        for (index in order) {
            if (remainder.signum() > 0) { floors[index] += BigInteger.ONE; remainder -= BigInteger.ONE }
        }
        return floors.map { (if (t.signum() < 0) -it else it).longValueExact() }
    }
}
