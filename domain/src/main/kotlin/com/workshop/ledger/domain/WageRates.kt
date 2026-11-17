package com.workshop.ledger.domain

import java.time.LocalDate

/** Rates are already converted to whole toman during migration; dates are Gregorian in the new model. */
data class EffectiveWage(val userId: String, val effectiveDate: LocalDate, val rate: Long)

object WageRates {
    /** Latest rate not after the date, otherwise the earliest rate; same-date ties keep legacy input order. */
    fun forDate(userId: String, date: LocalDate, wages: List<EffectiveWage>): Long {
        val ordered = wages.filter { it.userId == userId }.sortedBy { it.effectiveDate }
        if (ordered.isEmpty()) return 0L
        var best: EffectiveWage? = null
        for (wage in ordered) {
            if (wage.effectiveDate > date) break
            best = wage
        }
        return (best ?: ordered.first()).rate
    }
}
