package com.workshop.ledger.domain

/** The legacy text-input policy, using whole toman only. No floating-point operations. */
object MoneyParser {
    const val MAX_MONEY = 999_999_999_999L
    data class Result(val value: Long, val has: Boolean, val decimalDigitsDiscarded: Boolean, val clamped: Boolean)
    private val limit = MAX_MONEY.toString()

    private fun normalized(c: Char): Char = when (c) {
        in '۰'..'۹' -> '0' + (c - '۰')
        in '٠'..'٩' -> '0' + (c - '٠')
        '٫' -> '.'
        '٬', '،' -> ','
        else -> c
    }
    // ECMAScript whitespace used by /\s/g in the legacy engine.
    private fun isJsWhitespace(c: Char): Boolean = c in '\u0009'..'\u000d' || c == '\u0020' || c == '\u00a0' ||
        c == '\u1680' || c in '\u2000'..'\u200a' || c == '\u2028' || c == '\u2029' ||
        c == '\u202f' || c == '\u205f' || c == '\u3000' || c == '\ufeff'

    fun parse(raw: String?): Result {
        val text = (raw ?: "").map(::normalized).filterNot(::isJsWhitespace).joinToString("")
        val dot = text.indexOf('.')
        val decimal = dot >= 0 && text.substring(dot + 1).any { it in '0'..'9' }
        val part = if (dot < 0) text else text.substring(0, dot)
        val digits = part.filter { it in '0'..'9' }
        if (digits.isEmpty()) return Result(0L, false, decimal, false)
        val canonical = digits.trimStart('0').ifEmpty { "0" }
        val exceeds = canonical.length > limit.length || (canonical.length == limit.length && canonical > limit)
        return Result(if (exceeds) MAX_MONEY else canonical.toLong(), true, decimal, exceeds)
    }
}
