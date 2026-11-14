package com.workshop.ledger.data.export

import com.workshop.ledger.domain.*
import java.math.BigDecimal
import java.time.LocalDate
import java.util.Locale

/** The legacy calculation model deliberately omits these report-only fields. */
data class AuditReceipt(val record: FinancialReceipt, val description: String?)
data class AuditExpense(val record: FinancialExpense, val description: String?, val categoryId: String?)
data class AuditPayout(val record: FinancialPayout, val description: String?)
data class AuditTransfer(val record: FinancialTransfer, val description: String?)
data class AuditAdjustment(val record: FinancialAdjustment, val description: String?)
data class AuditCategory(val id: String, val name: String)
data class AuditDetails(
    val receipts: List<AuditReceipt>, val expenses: List<AuditExpense>, val payouts: List<AuditPayout>,
    val transfers: List<AuditTransfer>, val adjustments: List<AuditAdjustment>, val categories: List<AuditCategory>
)

/** Generates the legacy detailed-audit rows for valid date snapshots and supported attendance values. */
object AuditCsvReport {
    private fun txt(s: String?) = if (s == null) CsvCell.Empty else CsvCell.Text(s)
    private fun num(n: Long) = CsvCell.Integer(n)
    private fun jalali(d: LocalDate): String {
        val j = JalaliCalendar.toJalali(d.year, d.monthValue, d.dayOfMonth)
        return String.format(Locale.ROOT, "%04d/%02d/%02d", j.year, j.month, j.day)
    }
    private fun label(v: AttendanceValue): String = when (v) {
        AttendanceValue.Full -> "کامل"
        AttendanceValue.Half -> "نصفه"
        AttendanceValue.Absent -> "غایب"
        is AttendanceValue.Hours -> "${LegacyReportNumber.string(v.hours)} ساعت"
        is AttendanceValue.LegacyString -> "—"
    }
    private fun status(active: Boolean) = if (active) "فعال" else "غیرفعال"

    /** Metadata and calculation records must correspond one-for-one and in the same order. */
    fun rows(project: FinancialProject, details: AuditDetails, projectLabel: String, range: DateRange = project.settings.range): List<List<CsvCell>> {
        require(details.receipts.map { it.record } == project.receipts && details.expenses.map { it.record } == project.expenses &&
            details.payouts.map { it.record } == project.payouts && details.transfers.map { it.record } == project.transfers &&
            details.adjustments.map { it.record } == project.adjustments) { "Audit metadata does not match project snapshot" }
        val overtime = project.settings.overtimeFactor.let { if (it >= BigDecimal.ONE) it else BigDecimal.ONE }
        val penalty = project.settings.absentPenalty.let { if (it >= BigDecimal.ZERO && it <= BigDecimal("3")) it else BigDecimal("0.5") }
        val rows = mutableListOf<List<CsvCell>>()
        fun line(vararg cells: CsvCell) { rows.add(cells.toList()) }
        fun labels(vararg cells: String) { line(*cells.map { txt(it) }.toTypedArray()) }
        fun inRange(d: LocalDate) = (range.from == null || d >= range.from) && (range.to == null || d <= range.to)
        fun user(id: String?) = project.users.firstOrNull { it.id == id }?.name ?: "— (حذف‌شده)"
        fun category(id: String?) = details.categories.firstOrNull { it.id == id }?.name ?: ""
        fun add(a: Long, b: Long) = Math.addExact(a, b)
        var totalReceipts=0L; var totalExpenses=0L; var totalPayouts=0L
        labels("ریز حسابرسی — $projectLabel", "بازه: ${FinancialCsvReport.rangeText(range)}")
        labels("فقط ثبت‌های داخل بازهٔ گزارش آمده است. مبلغ‌ها به تومان‌اند و ستون «روز کار» فقط برای حضور است.")
        labels("نوع", "تاریخ", "شرح", "دسته", "پرداخت‌کننده / شخص", "مبلغ (تومان)", "روز کار", "وضعیت", "سهم واریزی در بازه (تومان)")
        details.receipts.filter { r -> (range.from == null || r.record.end >= range.from) && (range.to == null || r.record.start <= range.to) }.forEach {
            val r=it.record
            val part = if (r.active) ReceiptShares.share(r.amount,r.start,r.end,range) else null
            if (part != null) totalReceipts=add(totalReceipts,part)
            line(txt("واریزی"), txt("${jalali(r.start)} تا ${jalali(r.end)}"), txt(it.description), txt(""), txt(""), num(r.amount), txt(""), txt(status(r.active)), part?.let(::num) ?: CsvCell.Empty)
        }
        details.expenses.filter { inRange(it.record.date) }.forEach {
            val r=it.record; if(r.active) totalExpenses=add(totalExpenses,r.amount)
            val payer=if(!r.payerId.isNullOrEmpty() && r.payerId!="shared") user(r.payerId) else "صندوق مشترک"
            line(txt("هزینه"),txt(jalali(r.date)),txt(it.description),txt(category(it.categoryId)),txt(payer),num(r.amount),txt(""),txt(status(r.active)))
        }
        details.payouts.filter { inRange(it.record.date) }.forEach {
            val r=it.record; if(r.active) totalPayouts=add(totalPayouts,r.amount)
            line(txt("پرداختی"),txt(jalali(r.date)),txt(it.description),txt(""),txt(user(r.userId)),num(r.amount),txt(""),txt(status(r.active)))
        }
        details.transfers.filter { inRange(it.record.date) }.forEach {
            val r=it.record; val from=if(r.fromId.isNullOrEmpty()) "صندوق" else user(r.fromId)
            line(txt(if(r.kind=="advance") "پیش‌برداشت" else "انتقال"),txt(jalali(r.date)),txt(it.description),txt(""),txt("$from ← ${user(r.toId)}"),num(r.amount),txt(""),txt(status(r.active)))
        }
        val kinds=mapOf("bonus" to "پاداش", "penalty" to "جریمه", "assistance" to "مساعده", "settle" to "تسویه نقدی")
        details.adjustments.filter { inRange(it.record.date) }.forEach {
            val r=it.record
            line(txt(kinds[r.kind] ?: r.kind),txt(jalali(r.date)),txt(it.description),txt(""),txt(user(r.userId)),num(r.amount),txt(""),txt(status(r.active)))
        }
        project.attendance.toSortedMap().filterKeys(::inRange).forEach { (day, values) -> values.forEach { (id, value) ->
            val fraction=AttendanceMath.dayFraction(value,overtime,penalty)
            val formatted=if(fraction == null) CsvCell.Empty else CsvCell.Decimal.fromCanonical(LegacyReportNumber.fixed(fraction,3))
            line(txt("حضور"),txt(jalali(day)),txt(label(value)),txt(""),txt(user(id)),txt(""),formatted,txt(if(fraction == null) "نامعتبر" else "معتبر"))
        } }
        line(); labels("جمع (فقط ردیف‌های فعال)")
        for ((name, total) in listOf("واریزی‌ها (سهم در بازه)" to totalReceipts, "هزینه‌ها" to totalExpenses, "پرداختی‌ها" to totalPayouts))
            line(txt(name),txt(""),txt(""),txt(""),txt(""),num(total))
        return rows
    }
}
