package com.workshop.ledger.data.export

import com.workshop.ledger.domain.FinancialResult
import com.workshop.ledger.domain.JalaliCalendar
import com.workshop.ledger.domain.LegacyReportNumber
import java.math.BigDecimal
import java.time.LocalDate

/** Recreates the original comprehensive financial CSV rows for supported, already-computed data. */
object FinancialCsvReport {
    private val months = listOf("فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور", "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند")
    private fun text(value: String) = CsvCell.Text(value)
    private fun number(value: Long) = CsvCell.Integer(value)
    private fun decimal(value: String) = CsvCell.Decimal.fromCanonical(value)
    internal fun human(d: LocalDate): String {
        val j = JalaliCalendar.toJalali(d.year, d.monthValue, d.dayOfMonth)
        return "${j.day} ${months[j.month - 1]} ${j.year}"
    }
    private fun natural(value: BigDecimal) = decimal(LegacyReportNumber.string(value))
    private fun fixed(value: BigDecimal) = decimal(LegacyReportNumber.fixed(value, 2))

    internal fun rangeText(range: com.workshop.ledger.domain.DateRange): String =
        (range.from?.let(::human) ?: "ابتدای کار") + " تا " + (range.to?.let(::human) ?: "امروز")

    /** Caller supplies the actual project label (including sandbox suffix) and legacy-formatted timestamp. */
    fun rows(result: FinancialResult, projectLabel: String, generatedAt: String): List<List<CsvCell>> {
        val range = rangeText(result.range)
        val rows = mutableListOf<List<CsvCell>>()
        fun line(vararg values: CsvCell) { rows.add(values.toList()) }
        fun labels(vararg values: String) { line(*values.map(::text).toTypedArray()) }
        fun metric(label: String, amount: Long) { line(text(label), number(amount)) }
        labels("گزارش جامع مالی — استایار")
        labels("پروژه", projectLabel); labels("بازه", range)
        labels("مبنای سود", if (result.basis == "cash") "نقدی" else "تعهدی"); labels("تاریخ تهیه", generatedAt)
        line(); labels("شاخص‌های کلیدی"); labels("شاخص", "مبلغ (تومان)")
        metric("مجموع واریزی", result.totalReceipts); metric("هزینه‌های مشترک", result.sharedExpenses)
        metric("هزینه‌های شخصی اوستاها", result.personalExpTotal); metric("کل دستمزد استحقاقی", result.totalWages)
        metric("پرداختی‌های نقدی", result.totalCashPayouts); metric("مساعده پرسنل", result.staffAssistance)
        metric("پاداش اوستاها", result.masterBonus); metric("جریمه اوستاها", result.masterPenalty); metric("مانده قابل تقسیم", result.profit)
        line(); labels("سهم اوستاها")
        labels("نام", "کارکرد (روز)", "ضریب", "کارکرد نهایی", "درصد سهم", "سهم پایه", "بازپرداخت هزینه شخصی", "سهم هزینه شخصی", "انتقال داده‌شده", "انتقال گرفته‌شده", "پیش‌برداشت", "مساعده", "پاداش", "جریمه", "تسویه نقدی", "مانده نهایی")
        result.masters.forEach { a -> line(text(a.user.name), fixed(a.days), natural(a.user.workFactor), fixed(a.workFinal), decimal(LegacyReportNumber.percent(a.percent)),
            number(a.share), number(a.reimburse), number(a.sharedPersonalExp), number(a.given), number(a.taken), number(a.advance), number(a.assistance),
            number(a.bonus), number(a.penalty), number(a.settle), number(a.finalBalance)) }
        line(); labels("تراز شاگردان و پرسنل سایر")
        labels("نام", "نوع", "روز کامل", "روز نصفه", "ساعتی", "ساعت اضافه‌کار", "دستمزد پایه", "پاداش", "جریمه", "دستمزد استحقاقی", "پرداختی", "مساعده", "مانده")
        result.staff.forEach { a ->
            val userType = when (a.user.type) { "apprentice" -> "شاگرد"; "custom" -> "پرسنل سایر"; else -> null }
            line(text(a.user.name), userType?.let(::text) ?: CsvCell.Empty, number(a.full.toLong()), number(a.half.toLong()), number(a.hourly.toLong()),
                natural(a.overtimeHours), number(a.baseWage), number(a.bonus), number(a.penalty), number(a.entitled), number(a.paid), number(a.assistance), number(a.debt))
        }
        return rows
    }
}
