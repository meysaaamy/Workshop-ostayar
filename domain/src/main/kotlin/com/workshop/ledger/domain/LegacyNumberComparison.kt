package com.workshop.ledger.domain

import java.math.BigDecimal

/** Compare finite JavaScript-number values after legacy binary64 conversion, without float primitives. */
object LegacyNumberComparison {
    fun compare(value: BigDecimal, other: BigDecimal): Int =
        LegacyBinary64.decimal(value).compareTo(LegacyBinary64.decimal(other))
}
